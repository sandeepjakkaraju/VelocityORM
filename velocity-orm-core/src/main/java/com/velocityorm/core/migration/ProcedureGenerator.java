package com.velocityorm.core.migration;

import com.velocityorm.core.dialect.Dialect;
import com.velocityorm.core.metadata.EntityMeta;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collection;
import java.util.List;

/**
 * @author sandeepkumarjakkaraju
 */
public class ProcedureGenerator {
    private static final Logger log = LoggerFactory.getLogger(ProcedureGenerator.class);

    private final Dialect dialect;

    public ProcedureGenerator(Dialect dialect) {
        this.dialect = dialect;
    }

    public void generateProcedures(Connection conn, Collection<EntityMeta<?, ?>> metas) throws SQLException {
        log.info("Starting stored procedure generation for dialect: {}", dialect.getName());
        try (Statement stmt = conn.createStatement()) {
            for (EntityMeta<?, ?> meta : metas) {
                generateProc(conn, stmt, meta, "insert", dialect.generateInsertProcedure(meta));
                generateProc(conn, stmt, meta, "update", dialect.generateUpdateProcedure(meta));
                generateProc(conn, stmt, meta, "delete", dialect.generateDeleteProcedure(meta));
                generateProc(conn, stmt, meta, "get", dialect.generateGetProcedure(meta));
                generateProc(conn, stmt, meta, "batch_insert", dialect.generateBatchInsertProcedure(meta));
            }
        }
    }

    private void generateProc(Connection conn, Statement stmt, EntityMeta<?, ?> meta, String action, List<String> ddlList) throws SQLException {
        String procName = "sp_" + meta.getTableName() + "_" + action;
        // Check if procedure exists
        if (!dialect.procedureExists(conn, procName)) {
            log.info("Procedure '{}' does not exist. Creating...", procName);
            for (String ddl : ddlList) {
                log.debug("Executing DDL: {}", ddl);
                try {
                    stmt.execute(ddl);
                } catch (SQLException e) {
                    // Try execution in block, some ddl commands like DROP might fail if not exists
                    log.debug("Ddl sub-command error (ignorable if clean drop/create): {}", e.getMessage());
                }
            }
        } else {
            log.info("Procedure '{}' already exists. Skipping.", procName);
        }
    }
}
