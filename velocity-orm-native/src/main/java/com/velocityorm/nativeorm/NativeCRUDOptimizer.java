package com.velocityorm.nativeorm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.lang.reflect.Field;

/**
 * Native CRUD optimizations for high-performance data access.
 */
public class NativeCRUDOptimizer {
    private static final Logger logger = LoggerFactory.getLogger(NativeCRUDOptimizer.class);
    private static boolean isNativeLoaded = false;
    
    // Java Fallback cache
    private static final Map<Long, Long> javaDirtyCache = new ConcurrentHashMap<>();

    static {
        try {
            System.loadLibrary("velocity_orm_native");
            isNativeLoaded = true;
            logger.info("Successfully loaded Rust native library 'velocity_orm_native' for CRUD Optimizations.");
        } catch (UnsatisfiedLinkError e) {
            isNativeLoaded = false;
            logger.warn("Native library 'velocity_orm_native' not found. Falling back to Java heap for CRUD tracking.");
        }
    }

    public static void markClean(long entityId, long hash) {
        if (isNativeLoaded) {
            markCleanNative(entityId, hash);
            if (logger.isTraceEnabled()) {
                logger.trace("[Rust-Native] Off-heap markClean: ID={}, Hash={}", entityId, hash);
            }
            return;
        }
        // Pure Java Fallback
        javaDirtyCache.put(entityId, hash);
    }

    private static native void markCleanNative(long entityId, long hash);

    public static boolean isDirty(long entityId, long currentHash) {
        if (isNativeLoaded) {
            boolean dirty = isDirtyNative(entityId, currentHash);
            if (logger.isDebugEnabled() && dirty) {
                logger.debug("[Rust-Native] Off-heap isDirty check: ID={} is DIRTY", entityId);
            }
            return dirty;
        }
        // Pure Java Fallback
        Long savedHash = javaDirtyCache.get(entityId);
        if (savedHash == null) {
            return true;
        }
        return savedHash != currentHash;
    }

    private static native boolean isDirtyNative(long entityId, long currentHash);

    public static void injectStringField(Object target, String fieldName, String value) {
        if (isNativeLoaded) {
            injectStringFieldNative(target, fieldName, value);
            if (logger.isTraceEnabled()) {
                logger.trace("[Rust-Native] Direct field injection (String): field={}", fieldName);
            }
            return;
        }
        // Pure Java Fallback
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            logger.error("Fallback injection failed", e);
        }
    }

    private static native void injectStringFieldNative(Object target, String fieldName, String value);

    public static void injectLongField(Object target, String fieldName, long value) {
        if (isNativeLoaded) {
            injectLongFieldNative(target, fieldName, value);
            if (logger.isTraceEnabled()) {
                logger.trace("[Rust-Native] Direct field injection (Long): field={}", fieldName);
            }
            return;
        }
        // Pure Java Fallback
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            logger.error("Fallback injection failed", e);
        }
    }

    private static native void injectLongFieldNative(Object target, String fieldName, long value);

    public static String fastJoinIds(long[] ids) {
        if (isNativeLoaded) {
            String result = fastJoinIdsNative(ids);
            if (logger.isDebugEnabled()) {
                logger.debug("[Rust-Native] Fast-joined {} IDs for batch query", ids.length);
            }
            return result;
        }
        // Pure Java Fallback
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ids.length; i++) {
            sb.append(ids[i]);
            if (i < ids.length - 1) {
                sb.append(",");
            }
        }
        return sb.toString();
    }

    private static native String fastJoinIdsNative(long[] ids);
}
