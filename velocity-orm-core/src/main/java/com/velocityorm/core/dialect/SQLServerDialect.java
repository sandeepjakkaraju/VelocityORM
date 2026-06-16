package com.velocityorm.core.dialect;

import com.velocityorm.core.metadata.ColumnMeta;
import com.velocityorm.core.metadata.EntityMeta;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author sandeepkumarjakkaraju
 */
public class SQLServerDialect implements Dialect {

    @Override
    public String getName() {
        return "SQLServer";
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
            return "FLOAT";
        } else if (type == Boolean.class || type == boolean.class) {
            return "BIT";
        } else if (type == java.math.BigDecimal.class) {
            return "DECIMAL(19, 4)";
        } else if (type == java.time.LocalDateTime.class || type == java.util.Date.class) {
            return "DATETIME2";
        }
        return "VARCHAR(255)";
    }

    private String getProcedureParamType(ColumnMeta col) {
        return getSqlType(col);
    }

    @Override
    public String generateCreateTable(EntityMeta<?, ?> meta) {
        StringBuilder sb = new StringBuilder();
        sb.append("CREATE TABLE ").append(quoteIdentifier(meta.getTableName())).append(" (");
        
        List<String> colDefs = new ArrayList<>();
        for (ColumnMeta col : meta.getColumns()) {
            StringBuilder colDef = new StringBuilder();
            colDef.append(quoteIdentifier(col.getColumnName())).append(" ").append(getSqlType(col));
            if (col.isId()) {
                colDef.append(" PRIMARY KEY");
                if (meta.isIdGenerated()) {
                    colDef.append(" IDENTITY(1,1)");
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
        // SQL Server does not support CREATE INDEX IF NOT EXISTS natively on older versions,
        // but we can wrap it or verify via metadata. Wrapping it here as a standard statement is sufficient.
        return "CREATE " + (unique ? "UNIQUE " : "") + "INDEX " + 
               quoteIdentifier(indexName) + " ON " + quoteIdentifier(tableName) + " (" + quoteIdentifier(columnName) + ")";
    }

    @Override
    public List<String> generateInsertProcedure(EntityMeta<?, ?> meta) {
        String procName = "sp_" + meta.getTableName() + "_insert";
        StringBuilder sb = new StringBuilder();
        sb.append("CREATE PROCEDURE ").append(procName).append("\n");
        
        List<String> params = new ArrayList<>();
        if (meta.isIdGenerated()) {
            params.add("@p_" + meta.getIdColumnName() + " " + getProcedureParamType(meta.getColumnByFieldName(meta.getIdFieldName())) + " OUTPUT");
        } else {
            params.add("@p_" + meta.getIdColumnName() + " " + getProcedureParamType(meta.getColumnByFieldName(meta.getIdFieldName())));
        }
        for (ColumnMeta col : meta.getColumns()) {
            if (col.isId()) continue;
            params.add("@p_" + col.getColumnName() + " " + getProcedureParamType(col));
        }
        sb.append(String.join(",\n", params)).append("\nAS\nBEGIN\n");
        sb.append("    INSERT INTO ").append(quoteIdentifier(meta.getTableName())).append(" (");
        
        List<String> cols = new ArrayList<>();
        List<String> vals = new ArrayList<>();
        if (!meta.isIdGenerated()) {
            cols.add(quoteIdentifier(meta.getIdColumnName()));
            vals.add("@p_" + meta.getIdColumnName());
        }
        for (ColumnMeta col : meta.getColumns()) {
            if (col.isId()) continue;
            cols.add(quoteIdentifier(col.getColumnName()));
            vals.add("@p_" + col.getColumnName());
        }
        
        sb.append(String.join(", ", cols)).append(") VALUES (");
        sb.append(String.join(", ", vals)).append(");\n");
        
        if (meta.isIdGenerated()) {
            sb.append("    SET @p_").append(meta.getIdColumnName()).append(" = SCOPE_IDENTITY();\n");
        }
        sb.append("END");
        
        return List.of(
            "IF OBJECT_ID('" + procName + "', 'P') IS NOT NULL DROP PROCEDURE " + procName,
            sb.toString()
        );
    }

    @Override
    public List<String> generateUpdateProcedure(EntityMeta<?, ?> meta) {
        String procName = "sp_" + meta.getTableName() + "_update";
        StringBuilder sb = new StringBuilder();
        sb.append("CREATE PROCEDURE ").append(procName).append("\n");
        
        List<String> params = new ArrayList<>();
        params.add("@p_" + meta.getIdColumnName() + " " + getProcedureParamType(meta.getColumnByFieldName(meta.getIdFieldName())));
        for (ColumnMeta col : meta.getColumns()) {
            if (col.isId()) continue;
            params.add("@p_" + col.getColumnName() + " " + getProcedureParamType(col));
        }
        sb.append(String.join(",\n", params)).append("\nAS\nBEGIN\n");
        sb.append("    UPDATE ").append(quoteIdentifier(meta.getTableName())).append(" SET ");
        
        List<String> updates = new ArrayList<>();
        for (ColumnMeta col : meta.getColumns()) {
            if (col.isId()) continue;
            updates.add(quoteIdentifier(col.getColumnName()) + " = @p_" + col.getColumnName());
        }
        sb.append(String.join(", ", updates));
        sb.append(" WHERE ").append(quoteIdentifier(meta.getIdColumnName())).append(" = @p_").append(meta.getIdColumnName()).append(";\nEND");
        
        return List.of(
            "IF OBJECT_ID('" + procName + "', 'P') IS NOT NULL DROP PROCEDURE " + procName,
            sb.toString()
        );
    }

    @Override
    public List<String> generateDeleteProcedure(EntityMeta<?, ?> meta) {
        String procName = "sp_" + meta.getTableName() + "_delete";
        String idParamType = getProcedureParamType(meta.getColumnByFieldName(meta.getIdFieldName()));
        String sql = "CREATE PROCEDURE " + procName + "(@p_" + meta.getIdColumnName() + " " + idParamType + ")\n" +
                     "AS\n" +
                     "BEGIN\n" +
                     "    DELETE FROM " + quoteIdentifier(meta.getTableName()) + " WHERE " + quoteIdentifier(meta.getIdColumnName()) + " = @p_" + meta.getIdColumnName() + ";\n" +
                     "END";
        return List.of(
            "IF OBJECT_ID('" + procName + "', 'P') IS NOT NULL DROP PROCEDURE " + procName,
            sql
        );
    }

    @Override
    public List<String> generateGetProcedure(EntityMeta<?, ?> meta) {
        String procName = "sp_" + meta.getTableName() + "_get";
        String idParamType = getProcedureParamType(meta.getColumnByFieldName(meta.getIdFieldName()));
        String sql = "CREATE PROCEDURE " + procName + "(@p_" + meta.getIdColumnName() + " " + idParamType + ")\n" +
                     "AS\n" +
                     "BEGIN\n" +
                     "    SELECT * FROM " + quoteIdentifier(meta.getTableName()) + " WHERE " + quoteIdentifier(meta.getIdColumnName()) + " = @p_" + meta.getIdColumnName() + ";\n" +
                     "END";
        return List.of(
            "IF OBJECT_ID('" + procName + "', 'P') IS NOT NULL DROP PROCEDURE " + procName,
            sql
        );
    }

    @Override
    public List<String> generateBatchInsertProcedure(EntityMeta<?, ?> meta) {
        String procName = "sp_" + meta.getTableName() + "_batch_insert";
        StringBuilder sb = new StringBuilder();
        sb.append("CREATE PROCEDURE ").append(procName).append("(@p_data NVARCHAR(MAX))\nAS\nBEGIN\n");
        sb.append("    INSERT INTO ").append(quoteIdentifier(meta.getTableName())).append(" (");
        
        List<String> cols = new ArrayList<>();
        List<String> jsonCols = new ArrayList<>();
        if (!meta.isIdGenerated()) {
            ColumnMeta idCol = meta.getColumnByFieldName(meta.getIdFieldName());
            cols.add(quoteIdentifier(meta.getIdColumnName()));
            jsonCols.add(quoteIdentifier(meta.getIdColumnName()) + " " + getProcedureParamType(idCol) + " '$." + meta.getIdColumnName() + "'");
        }
        for (ColumnMeta col : meta.getColumns()) {
            if (col.isId()) continue;
            cols.add(quoteIdentifier(col.getColumnName()));
            jsonCols.add(quoteIdentifier(col.getColumnName()) + " " + getProcedureParamType(col) + " '$." + col.getColumnName() + "'");
        }
        
        sb.append(String.join(", ", cols)).append(")\n");
        sb.append("    SELECT ").append(String.join(", ", cols)).append("\n");
        sb.append("    FROM OPENJSON(@p_data)\n");
        sb.append("    WITH (\n        ");
        sb.append(String.join(",\n        ", jsonCols));
        sb.append("\n    );\nEND");
        
        return List.of(
            "IF OBJECT_ID('" + procName + "', 'P') IS NOT NULL DROP PROCEDURE " + procName,
            sb.toString()
        );
    }

    @Override
    public String getInsertCallStatement(EntityMeta<?, ?> meta) {
        int paramCount = meta.getColumns().size();
        return "{call sp_" + meta.getTableName() + "_insert(" + getPlaceholders(paramCount) + ")}";
    }

    @Override
    public String getUpdateCallStatement(EntityMeta<?, ?> meta) {
        int paramCount = meta.getColumns().size();
        return "{call sp_" + meta.getTableName() + "_update(" + getPlaceholders(paramCount) + ")}";
    }

    @Override
    public String getDeleteCallStatement(EntityMeta<?, ?> meta) {
        return "{call sp_" + meta.getTableName() + "_delete(?)}";
    }

    @Override
    public String getGetCallStatement(EntityMeta<?, ?> meta) {
        return "{call sp_" + meta.getTableName() + "_get(?)}";
    }

    @Override
    public String getBatchInsertCallStatement(EntityMeta<?, ?> meta) {
        return "{call sp_" + meta.getTableName() + "_batch_insert(?)}";
    }

    @Override
    public String paginate(String sql, int limit, int offset) {
        return sql + " OFFSET " + offset + " ROWS FETCH NEXT " + limit + " ROWS ONLY";
    }

    @Override
    public String quoteIdentifier(String name) {
        return "[" + name + "]";
    }

    @Override
    public boolean tableExists(Connection conn, String tableName) throws SQLException {
        try (var rs = conn.getMetaData().getTables(null, null, tableName, new String[]{"TABLE"})) {
            return rs.next();
        }
    }

    @Override
    public boolean procedureExists(Connection conn, String procedureName) throws SQLException {
        try (var rs = conn.getMetaData().getProcedures(null, null, procedureName)) {
            return rs.next();
        }
    }
}
