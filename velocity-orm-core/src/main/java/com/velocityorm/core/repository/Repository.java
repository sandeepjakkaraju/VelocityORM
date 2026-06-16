package com.velocityorm.core.repository;

import com.velocityorm.core.query.Query;
import java.sql.SQLException;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface Repository<T, ID> {
    T save(T entity) throws SQLException;
    T update(T entity) throws SQLException;
    void delete(ID id) throws SQLException;
    Optional<T> findById(ID id) throws SQLException;
    List<T> findAll() throws SQLException;
    void batchInsert(Collection<T> entities) throws SQLException;
    void batchUpdate(Collection<T> entities) throws SQLException;
    Query<T> query();
}
