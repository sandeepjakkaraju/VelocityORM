package com.velocityorm.core.tx;

import com.velocityorm.core.annotation.Transactional;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.implementation.bind.annotation.AllArguments;
import net.bytebuddy.implementation.bind.annotation.Origin;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;
import net.bytebuddy.matcher.ElementMatchers;

import java.lang.reflect.Method;

public class TransactionalProxyFactory {
    private final TransactionManager txManager;

    public TransactionalProxyFactory(TransactionManager txManager) {
        this.txManager = txManager;
    }

    @SuppressWarnings("unchecked")
    public <T> T createProxy(T instance) {
        Class<?> clazz = instance.getClass();
        try {
            // Create subclass proxy using MethodDelegation
            Class<?> proxyClass = new ByteBuddy()
                    .subclass(clazz)
                    .method(ElementMatchers.isAnnotatedWith(Transactional.class)
                            .or(ElementMatchers.isDeclaredBy(clazz).and(r -> clazz.isAnnotationPresent(Transactional.class))))
                    .intercept(MethodDelegation.to(new Interceptor(txManager, instance)))
                    .make()
                    .load(clazz.getClassLoader())
                    .getLoaded();
            
            // Try to find matching constructor, default constructor is expected or subclass is instantiated
            return (T) proxyClass.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            // Fallback or wrapper proxy if subclassing is constrained
            return instance;
        }
    }

    public static class Interceptor {
        private final TransactionManager txManager;
        private final Object target;

        public Interceptor(TransactionManager txManager, Object target) {
            this.txManager = txManager;
            this.target = target;
        }

        @RuntimeType
        public Object intercept(@Origin Method method, @AllArguments Object[] args) throws Throwable {
            Transactional transactional = method.getAnnotation(Transactional.class);
            if (transactional == null) {
                transactional = method.getDeclaringClass().getAnnotation(Transactional.class);
            }

            TransactionStatus status = txManager.getTransaction(transactional);
            try {
                method.setAccessible(true);
                Object result = method.invoke(target, args);
                txManager.commit(status);
                return result;
            } catch (Throwable ex) {
                Throwable actualEx = ex;
                if (ex instanceof java.lang.reflect.InvocationTargetException) {
                    actualEx = ((java.lang.reflect.InvocationTargetException) ex).getTargetException();
                }
                
                boolean shouldRollback = false;
                if (transactional != null) {
                    for (Class<? extends Throwable> rollbackClass : transactional.rollbackFor()) {
                        if (rollbackClass.isInstance(actualEx)) {
                            shouldRollback = true;
                            break;
                        }
                    }
                } else {
                    shouldRollback = true;
                }
                
                if (shouldRollback) {
                    txManager.rollback(status, actualEx);
                } else {
                    txManager.commit(status);
                }
                throw actualEx;
            }
        }
    }
}
