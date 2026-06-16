package com.velocityorm.core.query;

import java.lang.invoke.SerializedLambda;
import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class LambdaExpressionParser {
    private static final ConcurrentMap<Class<?>, String> cache = new ConcurrentHashMap<>();

    public static <T, R> String getPropertyName(PropertyFunc<T, R> func) {
        return cache.computeIfAbsent(func.getClass(), clazz -> {
            try {
                Method writeReplace = clazz.getDeclaredMethod("writeReplace");
                writeReplace.setAccessible(true);
                SerializedLambda lambda = (SerializedLambda) writeReplace.invoke(func);
                String implMethodName = lambda.getImplMethodName();
                
                String propertyName = implMethodName;
                if (propertyName.startsWith("get") && propertyName.length() > 3) {
                    propertyName = decapitalize(propertyName.substring(3));
                } else if (propertyName.startsWith("is") && propertyName.length() > 2) {
                    propertyName = decapitalize(propertyName.substring(2));
                }
                return propertyName;
            } catch (Exception e) {
                throw new RuntimeException("Failed to resolve property name from method reference lambda", e);
            }
        });
    }

    private static String decapitalize(String name) {
        if (name == null || name.isEmpty()) {
            return name;
        }
        if (name.length() > 1 && Character.isUpperCase(name.charAt(1)) && Character.isUpperCase(name.charAt(0))) {
            return name;
        }
        char[] chars = name.toCharArray();
        chars[0] = Character.toLowerCase(chars[0]);
        return new String(chars);
    }
}
