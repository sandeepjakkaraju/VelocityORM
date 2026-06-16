package com.velocityorm.core.dialect;

import com.velocityorm.core.metadata.EntityMeta;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * @author sandeepkumarjakkaraju
 */
public interface Dialect {
    String getName();
    
    // Schema generation
    String generateCreateTable(EntityMeta<?, ?> meta);
    String generateCreateIndex(String tableName, String columnName, String indexName, boolean unique);
    
    // Procedure DDL generators
    List<String> generateInsertProcedure(EntityMeta<?, ?> meta);
    List<String> generateUpdateProcedure(EntityMeta<?, ?> meta);
    List<String> generateDeleteProcedure(EntityMeta<?, ?> meta);
    List<String> generateGetProcedure(EntityMeta<?, ?> meta);
    List<String> generateBatchInsertProcedure(EntityMeta<?, ?> meta);
    
    // Stored Procedure invocation SQL templates
    String getInsertCallStatement(EntityMeta<?, ?> meta);
    String getUpdateCallStatement(EntityMeta<?, ?> meta);
    String getDeleteCallStatement(EntityMeta<?, ?> meta);
    String getGetCallStatement(EntityMeta<?, ?> meta);
    String getBatchInsertCallStatement(EntityMeta<?, ?> meta);
    
    // Query building helpers
    String paginate(String sql, int limit, int offset);
    String quoteIdentifier(String name);
    
    
    // Check if table or procedure exists
    boolean tableExists(Connection conn, String tableName) throws SQLException;
    boolean procedureExists(Connection conn, String procedureName) throws SQLException;

    default String getPlaceholders(int count) {
        if (count <= 0) return "";
        return String.join(",", java.util.Collections.nCopies(count, "?"));
    }
}

