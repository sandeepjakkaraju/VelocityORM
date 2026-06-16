package com.velocityorm.test.entity;

import com.velocityorm.core.metadata.ColumnMeta;
import com.velocityorm.core.metadata.EntityMeta;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.ArrayList;

public class UserMeta implements EntityMeta<User, java.lang.Long> {
    private final List<ColumnMeta> columns = new ArrayList<>();

    public UserMeta() {
        columns.add(new ColumnMeta("id", "id", java.lang.Long.class, true, 255, false, true, false, false, false, false));
        columns.add(new ColumnMeta("name", "name", java.lang.String.class, true, 255, false, false, false, false, false, false));
        columns.add(new ColumnMeta("email", "email", java.lang.String.class, true, 255, false, false, false, false, false, true));
    }

    @Override
    public Class<User> getEntityClass() { return User.class; }

    @Override
    public String getTableName() { return "users"; }

    @Override
    public String getIdColumnName() { return "id"; }

    @Override
    public String getIdFieldName() { return "id"; }

    @Override
    public Class<?> getIdType() { return java.lang.Long.class; }

    @Override
    public boolean isIdGenerated() { return true; }

    @Override
    public List<ColumnMeta> getColumns() { return columns; }

    @Override
    public ColumnMeta getColumnByFieldName(String fieldName) {
        for (ColumnMeta col : columns) {
            if (col.getFieldName().equals(fieldName)) return col;
        }
        return null;
    }

    @Override
    public java.lang.Long getId(User entity) {
        return entity.getId();
    }

    @Override
    public void setId(User entity, java.lang.Long id) {
        entity.setId(id);
    }

    @Override
    public Object getVersion(User entity) {
        return null;
    }

    @Override
    public void setVersion(User entity, Object version) {
    }

    @Override
    public void setCreatedAt(User entity, Object timestamp) {
    }

    @Override
    public void setUpdatedAt(User entity, Object timestamp) {
    }

    public java.lang.String getName(User entity) {
        return entity.getName();
    }

    public java.lang.String getEmail(User entity) {
        return entity.getEmail();
    }

    @Override
    public User mapRow(ResultSet rs) throws SQLException {
        User entity = new User();
        entity.setId(rs.getObject("id") != null ? rs.getLong("id") : null);
        entity.setName(rs.getString("name"));
        entity.setEmail(rs.getString("email"));
        return entity;
    }

    @Override
    public void bindInsert(PreparedStatement ps, User entity) throws SQLException {
        int idx = 1;
        ps.setObject(idx++, entity.getName());
        ps.setObject(idx++, entity.getEmail());
    }

    @Override
    public void bindUpdate(PreparedStatement ps, User entity) throws SQLException {
        int idx = 1;
        ps.setObject(idx++, entity.getId());
        ps.setObject(idx++, entity.getName());
        ps.setObject(idx++, entity.getEmail());
    }
}
