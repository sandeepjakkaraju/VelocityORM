package com.velocityorm.core.dialect;

import com.velocityorm.core.metadata.ColumnMeta;
import com.velocityorm.core.metadata.EntityMeta;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class OracleDialect implements Dialect {

    @Override
    public String getName() {
        return "Oracle";
    }

    private String getSqlType(ColumnMeta col) {
        Class<?> type = col.getFieldType();
        if (col.isEncrypted()) {
            return "VARCHAR2(1000)";
        }
        if (type == String.class) {
            return "VARCHAR2(" + col.getLength() + ")";
        } else if (type == Long.class || type == long.class) {
            return "NUMBER(19)";
        } else if (type == Integer.class || type == int.class) {
            return "NUMBER(10)";
        } else if (type == Double.class || type == double.class) {
            return "NUMBER(19, 4)";
        } else if (type == Boolean.class || type == boolean.class) {
            return "NUMBER(1)";
        } else if (type == java.math.BigDecimal.class) {
            return "NUMBER(19, 4)";
        } else if (type == java.time.LocalDateTime.class || type == java.util.Date.class) {
            return "TIMESTAMP";
        }
        return "VARCHAR2(255)";
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
                    colDef.append(" GENERATED AS IDENTITY");
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
        return "CREATE " + (unique ? "UNIQUE " : "") + "INDEX " + 
               quoteIdentifier(indexName) + " ON " + quoteIdentifier(tableName) + " (" + quoteIdentifier(columnName) + ")";
    }

    @Override
    public List<String> generateInsertProcedure(EntityMeta<?, ?> meta) {
        String procName = "sp_" + meta.getTableName() + "_insert";
        StringBuilder sb = new StringBuilder();
        sb.append("CREATE OR REPLACE PROCEDURE ").append(procName).append("(");
        
        List<String> params = new ArrayList<>();
        if (meta.isIdGenerated()) {
            params.add("p_" + meta.getIdColumnName() + " OUT " + getProcedureParamType(meta.getColumnByFieldName(meta.getIdFieldName())));
        } else {
            params.add("p_" + meta.getIdColumnName() + " IN " + getProcedureParamType(meta.getColumnByFieldName(meta.getIdFieldName())));
        }
        for (ColumnMeta col : meta.getColumns()) {
            if (col.isId()) continue;
            params.add("p_" + col.getColumnName() + " IN " + getProcedureParamType(col));
        }
        sb.append(String.join(", ", params)).append(") AS\nBEGIN\n");
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
        sb.append(String.join(", ", vals)).append(")");
        
        if (meta.isIdGenerated()) {
            sb.append(" RETURNING ").append(quoteIdentifier(meta.getIdColumnName())).append(" INTO p_").append(meta.getIdColumnName());
        }
        sb.append(";\nEND;");
        
        return List.of(sb.toString());
    }

    @Override
    public List<String> generateUpdateProcedure(EntityMeta<?, ?> meta) {
        String procName = "sp_" + meta.getTableName() + "_update";
        StringBuilder sb = new StringBuilder();
        sb.append("CREATE OR REPLACE PROCEDURE ").append(procName).append("(");
        
        List<String> params = new ArrayList<>();
        params.add("p_" + meta.getIdColumnName() + " IN " + getProcedureParamType(meta.getColumnByFieldName(meta.getIdFieldName())));
        for (ColumnMeta col : meta.getColumns()) {
            if (col.isId()) continue;
            params.add("p_" + col.getColumnName() + " IN " + getProcedureParamType(col));
        }
        sb.append(String.join(", ", params)).append(") AS\nBEGIN\n");
        sb.append("    UPDATE ").append(quoteIdentifier(meta.getTableName())).append(" SET ");
        
        List<String> updates = new ArrayList<>();
        for (ColumnMeta col : meta.getColumns()) {
            if (col.isId()) continue;
            updates.add(quoteIdentifier(col.getColumnName()) + " = p_" + col.getColumnName());
        }
        sb.append(String.join(", ", updates));
        sb.append(" WHERE ").append(quoteIdentifier(meta.getIdColumnName())).append(" = p_").append(meta.getIdColumnName()).append(";\nEND;");
        
        return List.of(sb.toString());
    }

    @Override
    public List<String> generateDeleteProcedure(EntityMeta<?, ?> meta) {
        String procName = "sp_" + meta.getTableName() + "_delete";
        String idParamType = getProcedureParamType(meta.getColumnByFieldName(meta.getIdFieldName()));
        String sql = "CREATE OR REPLACE PROCEDURE " + procName + "(p_" + meta.getIdColumnName() + " IN " + idParamType + ") AS\n" +
                     "BEGIN\n" +
                     "    DELETE FROM " + quoteIdentifier(meta.getTableName()) + " WHERE " + quoteIdentifier(meta.getIdColumnName()) + " = p_" + meta.getIdColumnName() + ";\n" +
                     "END;";
        return List.of(sql);
    }

    @Override
    public List<String> generateGetProcedure(EntityMeta<?, ?> meta) {
        String procName = "sp_" + meta.getTableName() + "_get";
        String idParamType = getProcedureParamType(meta.getColumnByFieldName(meta.getIdFieldName()));
        String sql = "CREATE OR REPLACE PROCEDURE " + procName + "(\n" +
                     "    p_" + meta.getIdColumnName() + " IN " + idParamType + ",\n" +
                     "    p_out OUT SYS_REFCURSOR\n" +
                     ") AS\n" +
                     "BEGIN\n" +
                     "    OPEN p_out FOR SELECT * FROM " + quoteIdentifier(meta.getTableName()) + " WHERE " + quoteIdentifier(meta.getIdColumnName()) + " = p_" + meta.getIdColumnName() + ";\n" +
                     "END;";
        return List.of(sql);
    }

    @Override
    public List<String> generateBatchInsertProcedure(EntityMeta<?, ?> meta) {
        String procName = "sp_" + meta.getTableName() + "_batch_insert";
        StringBuilder sb = new StringBuilder();
        sb.append("CREATE OR REPLACE PROCEDURE ").append(procName).append("(p_data IN CLOB) AS\nBEGIN\n");
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
        sb.append("\n    )) jt;\nEND;");
        
        return List.of(sb.toString());
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
        return "{call sp_" + meta.getTableName() + "_get(?, ?)}"; // takes id and returns refcursor
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
        return "\"" + name.toUpperCase() + "\"";
    }

    @Override
    public boolean tableExists(Connection conn, String tableName) throws SQLException {
        try (var rs = conn.getMetaData().getTables(null, null, tableName.toUpperCase(), new String[]{"TABLE"})) {
            return rs.next();
        }
    }

    @Override
    public boolean procedureExists(Connection conn, String procedureName) throws SQLException {
        try (var rs = conn.getMetaData().getProcedures(null, null, procedureName.toUpperCase())) {
            return rs.next();
        }
    }
}
