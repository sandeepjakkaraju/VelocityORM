package com.velocityorm.nativeorm;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JNI Wrapper for Rust-based high-performance row identity hashing.
 */
public class NativeRowIdentity {
    private static final Logger logger = LoggerFactory.getLogger(NativeRowIdentity.class);
    private static boolean isNativeLoaded = false;

    static {
        try {
            System.loadLibrary("velocity_orm_native");
            isNativeLoaded = true;
            System.out.println("@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@");
            System.out.println("SUCCESSFULLY LOADED RUST NATIVE LIBRARY!");
            System.out.println("@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@");
        } catch (UnsatisfiedLinkError e) {
            System.err.println("Native library 'velocity_orm_native' not found. Falling back to Java implementation.");
            isNativeLoaded = false;
        }
    }

    public static long calculateRowHash(String input) {
        if (isNativeLoaded) {
            long hash = calculateRowHashNative(input);
            if (logger.isDebugEnabled()) {
                logger.debug("[Rust-Native] Calculated hash: {}", hash);
            }
            return hash;
        }
        // Pure Java Fallback (Standard 32-bit hash extended to 64-bit)
        return (long) input.hashCode();
    }

    private static native long calculateRowHashNative(String input);

    public static long calculateBulkHash(byte[] data) {
        if (isNativeLoaded) {
            return calculateBulkHashNative(data);
        }
        // Pure Java Fallback
        long hash = 7;
        for (byte b : data) {
            hash = hash * 31 + b;
        }
        return hash;
    }

    private static native long calculateBulkHashNative(byte[] data);

    /**
     * Helper to check if row has changed by comparing current data hash with previous hash.
     */
    public static boolean hasRowChanged(String rowData, long previousHash) {
        return calculateRowHash(rowData) != previousHash;
    }
}
