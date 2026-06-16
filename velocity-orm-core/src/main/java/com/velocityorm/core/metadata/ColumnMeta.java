package com.velocityorm.core.metadata;

/**
 * @author sandeepkumarjakkaraju
 */
public class ColumnMeta {
    private final String columnName;
    private final String fieldName;
    private final Class<?> fieldType;
    private final boolean nullable;
    private final int length;
    private final boolean unique;
    private final boolean isId;
    private final boolean isVersion;
    private final boolean isCreatedAt;
    private final boolean isUpdatedAt;
    private final boolean isEncrypted;

    public ColumnMeta(String columnName, String fieldName, Class<?> fieldType, boolean nullable, int length,
                      boolean unique, boolean isId, boolean isVersion, boolean isCreatedAt, boolean isUpdatedAt, boolean isEncrypted) {
        this.columnName = columnName;
        this.fieldName = fieldName;
        this.fieldType = fieldType;
        this.nullable = nullable;
        this.length = length;
        this.unique = unique;
        this.isId = isId;
        this.isVersion = isVersion;
        this.isCreatedAt = isCreatedAt;
        this.isUpdatedAt = isUpdatedAt;
        this.isEncrypted = isEncrypted;
    }

    public String getColumnName() { return columnName; }
    public String getFieldName() { return fieldName; }
    public Class<?> getFieldType() { return fieldType; }
    public boolean isNullable() { return nullable; }
    public int getLength() { return length; }
    public boolean isUnique() { return unique; }
    public boolean isId() { return isId; }
    public boolean isVersion() { return isVersion; }
    public boolean isCreatedAt() { return isCreatedAt; }
    public boolean isUpdatedAt() { return isUpdatedAt; }
    public boolean isEncrypted() { return isEncrypted; }
}
