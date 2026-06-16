package com.velocityorm.core.dialect;

import com.velocityorm.core.metadata.ColumnMeta;
import com.velocityorm.core.metadata.EntityMeta;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class MySQLDialect implements Dialect {

    @Override
    public String getName() {
        return "MySQL";
    }

    private String getSqlType(ColumnMeta col) {
        Class<?> type = col.getFieldType();
        if (col.isEncrypted()) {
            return "VARCHAR(1000)";
        }
        if (type == String.class) {
            return "VARCHAR(" + col.getLength() + ")";
        } else if (type == Long.class || type == long.class) {
            return "BIGINT";
        } else if (type == Integer.class || type == int.class) {
            return "INT";
        } else if (type == Double.class || type == double.class) {
            return "DOUBLE";
        } else if (type == Boolean.class || type == boolean.class) {
            return "TINYINT(1)";
        } else if (type == java.math.BigDecimal.class) {
            return "DECIMAL(19, 4)";
        } else if (type == java.time.LocalDateTime.class || type == java.util.Date.class) {
            return "DATETIME";
        }
        return "VARCHAR(255)";
    }

    private String getProcedureParamType(ColumnMeta col) {
        return getSqlType(col);
    }

    @Override
    public String generateCreateTable(EntityMeta<?, ?> meta) {
        StringBuilder sb = new StringBuilder();
        sb.append("CREATE TABLE IF NOT EXISTS ").append(quoteIdentifier(meta.getTableName())).append(" (");
        
        List<String> colDefs = new ArrayList<>();
        for (ColumnMeta col : meta.getColumns()) {
            StringBuilder colDef = new StringBuilder();
            colDef.append(quoteIdentifier(col.getColumnName())).append(" ").append(getSqlType(col));
            if (col.isId()) {
                colDef.append(" PRIMARY KEY");
                if (meta.isIdGenerated()) {
                    colDef.append(" AUTO_INCREMENT");
                }
            } else {
                if (!col.isNullable()) {
                    colDef.append(" NOT NULL");
                }
                if (col.isUnique()) {
                    colDef.append(" UNIQUE");
                }
            }
            colDefs.add(colDef.toString());
        }
        sb.append(String.join(", ", colDefs));
        sb.append(")");
        return sb.toString();
    }

    @Override
    public String generateCreateIndex(String tableName, String columnName, String indexName, boolean unique) {
        // MySQL doesn't natively support "IF NOT EXISTS" inside CREATE INDEX on older versions, 
        // but we can generate a standard CREATE INDEX statement or use ALTER TABLE.
        // For our framework, generating standard CREATE INDEX statement is sufficient, 
        // and we handle already-exists via exception catch or metadata check at runtime.
        return "CREATE " + (unique ? "UNIQUE " : "") + "INDEX " + 
               quoteIdentifier(indexName) + " ON " + quoteIdentifier(tableName) + " (" + quoteIdentifier(columnName) + ")";
    }

    @Override
    public List<String> generateInsertProcedure(EntityMeta<?, ?> meta) {
        String procName = "sp_" + meta.getTableName() + "_insert";
        StringBuilder sb = new StringBuilder();
        sb.append("CREATE PROCEDURE ").append(procName).append("(");
        
        List<String> params = new ArrayList<>();
        if (meta.isIdGenerated()) {
            params.add("OUT p_" + meta.getIdColumnName() + " " + getProcedureParamType(meta.getColumnByFieldName(meta.getIdFieldName())));
        } else {
            params.add("IN p_" + meta.getIdColumnName() + " " + getProcedureParamType(meta.getColumnByFieldName(meta.getIdFieldName())));
        }
        for (ColumnMeta col : meta.getColumns()) {
            if (col.isId()) continue;
            params.add("IN p_" + col.getColumnName() + " " + getProcedureParamType(col));
        }
        sb.append(String.join(", ", params)).append(")\nBEGIN\n");
        sb.append("    INSERT INTO ").append(quoteIdentifier(meta.getTableName())).append(" (");
        
        List<String> cols = new ArrayList<>();
        List<String> vals = new ArrayList<>();
        if (!meta.isIdGenerated()) {
            cols.add(quoteIdentifier(meta.getIdColumnName()));
            vals.add("p_" + meta.getIdColumnName());
        }
        for (ColumnMeta col : meta.getColumns()) {
            if (col.isId()) continue;
            cols.add(quoteIdentifier(col.getColumnName()));
            vals.add("p_" + col.getColumnName());
        }
        
        sb.append(String.join(", ", cols)).append(") VALUES (");
        sb.append(String.join(", ", vals)).append(");\n");
        
        if (meta.isIdGenerated()) {
            sb.append("    SET p_").append(meta.getIdColumnName()).append(" = LAST_INSERT_ID();\n");
        }
        sb.append("END");
        
        return List.of("DROP PROCEDURE IF EXISTS " + procName, sb.toString());
    }

    @Override
    public List<String> generateUpdateProcedure(EntityMeta<?, ?> meta) {
        String procName = "sp_" + meta.getTableName() + "_update";
        StringBuilder sb = new StringBuilder();
        sb.append("CREATE PROCEDURE ").append(procName).append("(");
        
        List<String> params = new ArrayList<>();
        params.add("IN p_" + meta.getIdColumnName() + " " + getProcedureParamType(meta.getColumnByFieldName(meta.getIdFieldName())));
        for (ColumnMeta col : meta.getColumns()) {
            if (col.isId()) continue;
            params.add("IN p_" + col.getColumnName() + " " + getProcedureParamType(col));
        }
        sb.append(String.join(", ", params)).append(")\nBEGIN\n");
        sb.append("    UPDATE ").append(quoteIdentifier(meta.getTableName())).append(" SET ");
        
        List<String> updates = new ArrayList<>();
        for (ColumnMeta col : meta.getColumns()) {
            if (col.isId()) continue;
            updates.add(quoteIdentifier(col.getColumnName()) + " = p_" + col.getColumnName());
        }
        sb.append(String.join(", ", updates));
        sb.append(" WHERE ").append(quoteIdentifier(meta.getIdColumnName())).append(" = p_").append(meta.getIdColumnName()).append(";\nEND");
        
        return List.of("DROP PROCEDURE IF EXISTS " + procName, sb.toString());
    }

    @Override
    public List<String> generateDeleteProcedure(EntityMeta<?, ?> meta) {
        String procName = "sp_" + meta.getTableName() + "_delete";
        String idParamType = getProcedureParamType(meta.getColumnByFieldName(meta.getIdFieldName()));
        String sql = "CREATE PROCEDURE " + procName + "(IN p_" + meta.getIdColumnName() + " " + idParamType + ")\n" +
                     "BEGIN\n" +
                     "    DELETE FROM " + quoteIdentifier(meta.getTableName()) + " WHERE " + quoteIdentifier(meta.getIdColumnName()) + " = p_" + meta.getIdColumnName() + ";\n" +
                     "END";
        return List.of("DROP PROCEDURE IF EXISTS " + procName, sql);
    }

    @Override
    public List<String> generateGetProcedure(EntityMeta<?, ?> meta) {
        String procName = "sp_" + meta.getTableName() + "_get";
        String idParamType = getProcedureParamType(meta.getColumnByFieldName(meta.getIdFieldName()));
        String sql = "CREATE PROCEDURE " + procName + "(IN p_" + meta.getIdColumnName() + " " + idParamType + ")\n" +
                     "BEGIN\n" +
                     "    SELECT * FROM " + quoteIdentifier(meta.getTableName()) + " WHERE " + quoteIdentifier(meta.getIdColumnName()) + " = p_" + meta.getIdColumnName() + ";\n" +
                     "END";
        return List.of("DROP PROCEDURE IF EXISTS " + procName, sql);
    }

    @Override
    public List<String> generateBatchInsertProcedure(EntityMeta<?, ?> meta) {
        String procName = "sp_" + meta.getTableName() + "_batch_insert";
        StringBuilder sb = new StringBuilder();
        sb.append("CREATE PROCEDURE ").append(procName).append("(IN p_data JSON)\nBEGIN\n");
        sb.append("    INSERT INTO ").append(quoteIdentifier(meta.getTableName())).append(" (");
        
        List<String> cols = new ArrayList<>();
        List<String> jsonCols = new ArrayList<>();
        if (!meta.isIdGenerated()) {
            ColumnMeta idCol = meta.getColumnByFieldName(meta.getIdFieldName());
            cols.add(quoteIdentifier(meta.getIdColumnName()));
            jsonCols.add(quoteIdentifier(meta.getIdColumnName()) + " " + getProcedureParamType(idCol) + " PATH '$." + meta.getIdColumnName() + "'");
        }
        for (ColumnMeta col : meta.getColumns()) {
            if (col.isId()) continue;
            cols.add(quoteIdentifier(col.getColumnName()));
            jsonCols.add(quoteIdentifier(col.getColumnName()) + " " + getProcedureParamType(col) + " PATH '$." + col.getColumnName() + "'");
        }
        
        sb.append(String.join(", ", cols)).append(")\n");
        sb.append("    SELECT ").append(String.join(", ", cols)).append("\n");
        sb.append("    FROM JSON_TABLE(p_data, '$[*]' COLUMNS(\n        ");
        sb.append(String.join(",\n        ", jsonCols));
        sb.append("\n    )) jt;\nEND");
        
        return List.of("DROP PROCEDURE IF EXISTS " + procName, sb.toString());
    }

    @Override
    public String getInsertCallStatement(EntityMeta<?, ?> meta) {
        int paramCount = meta.getColumns().size();
        return "CALL sp_" + meta.getTableName() + "_insert(" + getPlaceholders(paramCount) + ")";
    }

    @Override
    public String getUpdateCallStatement(EntityMeta<?, ?> meta) {
        int paramCount = meta.getColumns().size();
        return "CALL sp_" + meta.getTableName() + "_update(" + getPlaceholders(paramCount) + ")";
    }

    @Override
    public String getDeleteCallStatement(EntityMeta<?, ?> meta) {
        return "CALL sp_" + meta.getTableName() + "_delete(?)";
    }

    @Override
    public String getGetCallStatement(EntityMeta<?, ?> meta) {
        return "CALL sp_" + meta.getTableName() + "_get(?)";
    }

    @Override
    public String getBatchInsertCallStatement(EntityMeta<?, ?> meta) {
        return "CALL sp_" + meta.getTableName() + "_batch_insert(?)";
    }

    @Override
    public String paginate(String sql, int limit, int offset) {
        return sql + " LIMIT " + limit + " OFFSET " + offset;
    }

    @Override
    public String quoteIdentifier(String name) {
        return "`" + name + "`";
    }

    @Override
    public boolean tableExists(Connection conn, String tableName) throws SQLException {
        try (var rs = conn.getMetaData().getTables(null, null, null, new String[]{"TABLE"})) {
            while (rs.next()) {
                if (tableName.equalsIgnoreCase(rs.getString("TABLE_NAME"))) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean procedureExists(Connection conn, String procedureName) throws SQLException {
        try (var rs = conn.getMetaData().getProcedures(null, null, null)) {
            while (rs.next()) {
                if (procedureName.equalsIgnoreCase(rs.getString("PROCEDURE_NAME"))) {
                    return true;
                }
            }
        }
        return false;
    }
}
