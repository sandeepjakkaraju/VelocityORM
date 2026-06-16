package com.velocityorm.core.migration;

import com.velocityorm.core.dialect.Dialect;
import com.velocityorm.core.metadata.ColumnMeta;
import com.velocityorm.core.metadata.EntityMeta;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collection;

public class SchemaGenerator {
    private static final Logger log = LoggerFactory.getLogger(SchemaGenerator.class);

    private final Dialect dialect;

    public SchemaGenerator(Dialect dialect) {
        this.dialect = dialect;
    }

    public void generateSchema(Connection conn, Collection<EntityMeta<?, ?>> metas) throws SQLException {
        log.info("Starting schema generation for dialect: {}", dialect.getName());
        try (Statement stmt = conn.createStatement()) {
            for (EntityMeta<?, ?> meta : metas) {
                if (!dialect.tableExists(conn, meta.getTableName())) {
                    log.info("Table '{}' does not exist. Generating schema...", meta.getTableName());
                    String createTableSql = dialect.generateCreateTable(meta);
                    log.debug("Executing SQL: {}", createTableSql);
                    stmt.execute(createTableSql);

                    // Generate indexes for unique columns (other than primary key)
                    for (ColumnMeta col : meta.getColumns()) {
                        if (col.isUnique() && !col.isId()) {
                            String indexName = "idx_" + meta.getTableName() + "_" + col.getColumnName() + "_uniq";
                            String createIndexSql = dialect.generateCreateIndex(meta.getTableName(), col.getColumnName(), indexName, true);
                            log.debug("Executing SQL: {}", createIndexSql);
                            try {
                                stmt.execute(createIndexSql);
                            } catch (SQLException e) {
                                log.warn("Failed to create index '{}': {}", indexName, e.getMessage());
                            }
                        }
                    }
                } else {
                    log.info("Table '{}' already exists. Skipping creation.", meta.getTableName());
                }
            }
        }
    }
}
