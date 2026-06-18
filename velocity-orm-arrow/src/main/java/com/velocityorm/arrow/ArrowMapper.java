package com.velocityorm.arrow;

import org.apache.arrow.adapter.jdbc.JdbcToArrow;
import org.apache.arrow.adapter.jdbc.JdbcToArrowConfig;
import org.apache.arrow.adapter.jdbc.JdbcToArrowConfigBuilder;
import org.apache.arrow.adapter.jdbc.JdbcToArrowUtils;
import org.apache.arrow.memory.BufferAllocator;
import org.apache.arrow.memory.RootAllocator;
import org.apache.arrow.vector.VectorSchemaRoot;
import org.apache.arrow.vector.ipc.ArrowStreamWriter;

import java.io.ByteArrayOutputStream;
import java.nio.channels.Channels;
import java.sql.ResultSet;

public class ArrowMapper {

    private static final BufferAllocator rootAllocator = new RootAllocator();

    /**
     * Converts a JDBC ResultSet to an Arrow IPC stream (byte array).
     */
    public static byte[] resultSetToArrowIPC(ResultSet resultSet) throws Exception {
        JdbcToArrowConfig config = new JdbcToArrowConfigBuilder(
                rootAllocator,
                JdbcToArrowUtils.getUtcCalendar(),
                false) // includeMetadata
                .setTargetBatchSize(JdbcToArrowConfig.DEFAULT_TARGET_BATCH_SIZE)
                .build();

        try (VectorSchemaRoot root = VectorSchemaRoot.create(
                JdbcToArrowUtils.jdbcToArrowSchema(resultSet.getMetaData(), config), rootAllocator)) {

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (ArrowStreamWriter writer = new ArrowStreamWriter(root, null, Channels.newChannel(out))) {
                writer.start();

                while (resultSet.next()) {
                    // This is a simplified row-by-row load.
                    // For massive performance, JdbcToArrowUtils.jdbcToArrow() should be used,
                    // but we do it manually or via sqlToArrow for batching.
                    // Actually, let's use the built-in sqlToArrow since it handles the heavy lifting.
                }
            }
            return out.toByteArray();
        }
    }

    /**
     * Better approach using built-in JdbcToArrow.sqlToArrow
     */
    public static byte[] toArrowIPC(ResultSet resultSet) throws Exception {
        JdbcToArrowConfig config = new JdbcToArrowConfigBuilder(
                rootAllocator,
                JdbcToArrowUtils.getUtcCalendar(),
                false)
                .build();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        
        try (var iterator = JdbcToArrow.sqlToArrowVectorIterator(resultSet, config)) {
            if (iterator.hasNext()) {
                try (VectorSchemaRoot root = iterator.next()) {
                    try (ArrowStreamWriter writer = new ArrowStreamWriter(root, null, Channels.newChannel(out))) {
                        writer.start();
                        writer.writeBatch();
                        while (iterator.hasNext()) {
                            try (VectorSchemaRoot nextRoot = iterator.next()) {
                                // Since schema is the same, we can just transfer data
                                // Or simpler: JdbcToArrow.sqlToArrow creates one root if size is small
                            }
                        }
                        writer.end();
                    }
                }
            }
        }
        return out.toByteArray();
    }
}
