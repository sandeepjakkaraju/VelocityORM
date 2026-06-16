package com.velocityorm.core.metadata;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * @author sandeepkumarjakkaraju
 */
public interface EntityMeta<T, ID> {
    Class<T> getEntityClass();
    String getTableName();
    String getIdColumnName();
    String getIdFieldName();
    Class<?> getIdType();
    boolean isIdGenerated();
    
    List<ColumnMeta> getColumns();
    ColumnMeta getColumnByFieldName(String fieldName);
    
    ID getId(T entity);
    void setId(T entity, ID id);
    
    Object getVersion(T entity);
    void setVersion(T entity, Object version);
    
    void setCreatedAt(T entity, Object timestamp);
    void setUpdatedAt(T entity, Object timestamp);
    
    T mapRow(ResultSet rs) throws SQLException;
    
    // Bind entity properties into parameters (e.g. for insert/update queries)
    void bindInsert(PreparedStatement ps, T entity) throws SQLException;
    void bindUpdate(PreparedStatement ps, T entity) throws SQLException;
}
