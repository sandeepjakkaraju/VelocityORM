package com.velocityorm.nativeorm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * Advanced native optimizations to outperform standard JDBC.
 */
public class NativeDataProcessor {
    private static final Logger logger = LoggerFactory.getLogger(NativeDataProcessor.class);
    private static boolean isNativeLoaded = false;

    static {
        try {
            System.loadLibrary("velocity_orm_native");
            isNativeLoaded = true;
            logger.info("Successfully loaded Rust native library 'velocity_orm_native' for Data Processing.");
        } catch (UnsatisfiedLinkError e) {
            isNativeLoaded = false;
            logger.warn("Native library 'velocity_orm_native' not found. Falling back to Java.");
        }
    }

    public static long[] decodeLongBatch(byte[] input, int count) {
        if (isNativeLoaded) {
            long[] result = decodeLongBatchNative(input, count);
            if (logger.isDebugEnabled()) {
                logger.debug("[Rust-Native] Decoded batch of {} longs from {} bytes", count, input.length);
            }
            return result;
        }
        // Pure Java Fallback
        long[] result = new long[count];
        for (int i = 0; i < count; i++) {
            int start = i * 8;
            if (start + 8 <= input.length) {
                result[i] = ((long) (input[start] & 0xFF) << 56) |
                            ((long) (input[start + 1] & 0xFF) << 48) |
                            ((long) (input[start + 2] & 0xFF) << 40) |
                            ((long) (input[start + 3] & 0xFF) << 32) |
                            ((long) (input[start + 4] & 0xFF) << 24) |
                            ((input[start + 5] & 0xFF) << 16) |
                            ((input[start + 6] & 0xFF) << 8) |
                            (input[start + 7] & 0xFF);
            }
        }
        return result;
    }

    private static native long[] decodeLongBatchNative(byte[] input, int count);

    public static String buildBulkInsertValues(int rows, int cols) {
        if (isNativeLoaded) {
            String sql = buildBulkInsertValuesNative(rows, cols);
            if (logger.isDebugEnabled()) {
                logger.debug("[Rust-Native] Generated bulk insert SQL for {} rows, {} columns", rows, cols);
            }
            return sql;
        }
        // Pure Java Fallback
        StringBuilder sb = new StringBuilder(rows * (cols * 3));
        for (int r = 0; r < rows; r++) {
            sb.append("(");
            for (int c = 0; c < cols; c++) {
                sb.append("?");
                if (c < cols - 1) {
                    sb.append(",");
                }
            }
            sb.append(")");
            if (r < rows - 1) {
                sb.append(",");
            }
        }
        return sb.toString();
    }

    private static native String buildBulkInsertValuesNative(int rows, int cols);

    public static long[] parseIsoTimestamps(String[] timestamps) {
        if (isNativeLoaded) {
            long[] result = parseIsoTimestampsNative(timestamps);
            if (logger.isDebugEnabled()) {
                logger.debug("[Rust-Native] Vector-parsed {} ISO timestamps", timestamps.length);
            }
            return result;
        }
        // Pure Java Fallback
        long[] result = new long[timestamps.length];
        for (int i = 0; i < timestamps.length; i++) {
            try {
                result[i] = LocalDateTime.parse(timestamps[i]).toInstant(ZoneOffset.UTC).toEpochMilli();
            } catch (Exception e) {
                result[i] = 0;
            }
        }
        return result;
    }

    private static native long[] parseIsoTimestampsNative(String[] timestamps);
}
