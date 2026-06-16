package com.velocityorm.core.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.velocityorm.core.VelocityORM;
import com.velocityorm.core.cache.CacheProvider;
import com.velocityorm.core.dialect.Dialect;
import com.velocityorm.core.dialect.PostgresDialect;
import com.velocityorm.core.metadata.ColumnMeta;
import com.velocityorm.core.metadata.EntityMeta;
import com.velocityorm.core.query.Query;
import com.velocityorm.core.security.EncryptionService;
import com.velocityorm.core.tx.Session;
import com.velocityorm.core.tx.SessionContext;

import java.sql.*;
import java.util.*;

public abstract class BaseRepository<T, ID> implements Repository<T, ID> {
    protected final VelocityORM orm;
    protected final EntityMeta<T, ID> meta;
    protected final Dialect dialect;
    protected final CacheProvider l2Cache;
    protected final EncryptionService encryption;
    private final ObjectMapper objectMapper = new ObjectMapper();

    protected BaseRepository(VelocityORM orm, EntityMeta<T, ID> meta) {
        this.orm = orm;
        this.meta = meta;
        this.dialect = orm.getDialect();
        this.l2Cache = orm.getCacheProvider();
        this.encryption = orm.getEncryptionService();
    }

    protected Session getSession() throws SQLException {
        Session session = SessionContext.getCurrentSession();
        if (session == null) {
            // Non-transactional execution, open a temporary session/connection
            Connection conn = orm.getDataSource().getConnection();
            session = new Session(conn);
            SessionContext.setCurrentSession(session);
        }
        return session;
    }

    private void releaseSession(Session session) {
        if (!session.isInTransaction()) {
            try {
                session.close();
            } catch (Exception e) {
                // ignore
            }
            SessionContext.clear();
        }
    }

    @Override
    public T save(T entity) throws SQLException {
        Session session = getSession();
        try {
            ID id = meta.getId(entity);
            if (id == null || (meta.isIdGenerated() && !isRecordExists(session.getConnection(), id))) {
                return insert(session, entity);
            } else {
                return update(session, entity);
            }
        } finally {
            releaseSession(session);
        }
    }

    private boolean isRecordExists(Connection conn, ID id) {
        String sql = "SELECT 1 FROM " + dialect.quoteIdentifier(meta.getTableName()) + " WHERE " + dialect.quoteIdentifier(meta.getIdColumnName()) + " = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            return false;
        }
    }

    private T insert(Session session, T entity) throws SQLException {
        Connection conn = session.getConnection();
        String sql = dialect.getInsertCallStatement(meta);
        
        try (CallableStatement cs = conn.prepareCall(sql)) {
            // Bind parameters
            int paramIndex = 1;
            
            // If ID is generated, register OUT/INOUT parameter
            if (meta.isIdGenerated()) {
                cs.registerOutParameter(paramIndex++, getSqlType(meta.getIdType()));
            } else {
                cs.setObject(paramIndex++, meta.getId(entity));
            }
            
            for (ColumnMeta col : meta.getColumns()) {
                if (col.isId()) continue;
                Object val = getFieldValue(entity, col);
                cs.setObject(paramIndex++, val);
            }
            
            cs.execute();
            
            if (meta.isIdGenerated()) {
                Object generatedId = cs.getObject(1);
                // Cast and set generated ID
                meta.setId(entity, castId(generatedId));
            }
            
            ID id = meta.getId(entity);
            // Put in L1 Cache
            session.getL1Cache().put(meta.getEntityClass(), id, entity);
            // Put in L2 Cache
            if (l2Cache != null) {
                l2Cache.put(meta.getTableName(), id, entity);
            }
            return entity;
        }
    }

    @Override
    public T update(T entity) throws SQLException {
        Session session = getSession();
        try {
            return update(session, entity);
        } finally {
            releaseSession(session);
        }
    }

    private T update(Session session, T entity) throws SQLException {
        Connection conn = session.getConnection();
        String sql = dialect.getUpdateCallStatement(meta);
        
        try (CallableStatement cs = conn.prepareCall(sql)) {
            int paramIndex = 1;
            cs.setObject(paramIndex++, meta.getId(entity));
            
            for (ColumnMeta col : meta.getColumns()) {
                if (col.isId()) continue;
                Object val = getFieldValue(entity, col);
                cs.setObject(paramIndex++, val);
            }
            
            cs.execute();
            
            ID id = meta.getId(entity);
            // Evict/Update caches
            session.getL1Cache().put(meta.getEntityClass(), id, entity);
            if (l2Cache != null) {
                l2Cache.put(meta.getTableName(), id, entity);
            }
            return entity;
        }
    }

    @Override
    public void delete(ID id) throws SQLException {
        Session session = getSession();
        try {
            Connection conn = session.getConnection();
            String sql = dialect.getDeleteCallStatement(meta);
            try (CallableStatement cs = conn.prepareCall(sql)) {
                cs.setObject(1, id);
                cs.execute();
            }
            // Evict from caches
            session.getL1Cache().remove(meta.getEntityClass(), id);
            if (l2Cache != null) {
                l2Cache.evict(meta.getTableName(), id);
            }
        } finally {
            releaseSession(session);
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public Optional<T> findById(ID id) throws SQLException {
        Session session = getSession();
        try {
            // 1. Check L1 Cache
            T cached = session.getL1Cache().get(meta.getEntityClass(), id);
            if (cached != null) {
                return Optional.of(cached);
            }
            
            // 2. Check L2 Cache
            if (l2Cache != null) {
                T l2Cached = (T) l2Cache.get(meta.getTableName(), id);
                if (l2Cached != null) {
                    session.getL1Cache().put(meta.getEntityClass(), id, l2Cached);
                    return Optional.of(l2Cached);
                }
            }
            
            // 3. Database fetch via stored procedure
            Connection conn = session.getConnection();
            String sql = dialect.getGetCallStatement(meta);
            
            try (CallableStatement cs = conn.prepareCall(sql)) {
                cs.setObject(1, id);
                
                // For PostgreSQL: sp_get has INOUT parameters for each column
                if (dialect instanceof PostgresDialect) {
                    cs.registerOutParameter(1, getSqlType(meta.getIdType()));
                    int paramIndex = 2;
                    for (ColumnMeta col : meta.getColumns()) {
                        if (col.isId()) continue;
                        cs.registerOutParameter(paramIndex++, getSqlType(col.getFieldType()));
                    }
                    
                    cs.execute();
                    
                    if (cs.getObject(1) == null) {
                        return Optional.empty();
                    }
                    
                    // Construct entity from OUT parameters
                    T entity = meta.getEntityClass().getDeclaredConstructor().newInstance();
                    meta.setId(entity, id);
                    int resIndex = 2;
                    for (ColumnMeta col : meta.getColumns()) {
                        if (col.isId()) continue;
                        Object val = cs.getObject(resIndex++);
                        setFieldValue(entity, col, val);
                    }
                    
                    session.getL1Cache().put(meta.getEntityClass(), id, entity);
                    if (l2Cache != null) {
                        l2Cache.put(meta.getTableName(), id, entity);
                    }
                    return Optional.of(entity);
                } 
                // For Oracle: get returns SYS_REFCURSOR as the second parameter
                else if (orm.getDialect().getName().equals("Oracle")) {
                    cs.registerOutParameter(2, -10); // OracleTypes.CURSOR is -10
                    cs.execute();
                    try (ResultSet rs = (ResultSet) cs.getObject(2)) {
                        if (rs.next()) {
                            T entity = mapRowAndDecrypt(rs);
                            session.getL1Cache().put(meta.getEntityClass(), id, entity);
                            if (l2Cache != null) {
                                l2Cache.put(meta.getTableName(), id, entity);
                            }
                            return Optional.of(entity);
                        }
                    }
                }
                // For MySQL and SQL Server: CALL returns ResultSet directly
                else {
                    boolean hasResults = cs.execute();
                    if (hasResults) {
                        try (ResultSet rs = cs.getResultSet()) {
                            if (rs.next()) {
                                T entity = mapRowAndDecrypt(rs);
                                session.getL1Cache().put(meta.getEntityClass(), id, entity);
                                if (l2Cache != null) {
                                    l2Cache.put(meta.getTableName(), id, entity);
                                }
                                return Optional.of(entity);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                throw new SQLException("Failed to fetch entity from database", e);
            }
            return Optional.empty();
        } finally {
            releaseSession(session);
        }
    }

    @Override
    public List<T> findAll() throws SQLException {
        return query().list();
    }

    @Override
    public Query<T> query() {
        try {
            return new Query<>(meta, dialect, getSession());
        } catch (SQLException e) {
            throw new RuntimeException("Failed to acquire session for query builder", e);
        }
    }

    @Override
    public void batchInsert(Collection<T> entities) throws SQLException {
        if (entities == null || entities.isEmpty()) return;
        Session session = getSession();
        try {
            Connection conn = session.getConnection();
            String sql = dialect.getBatchInsertCallStatement(meta);
            
            try (CallableStatement cs = conn.prepareCall(sql)) {
                if (dialect instanceof PostgresDialect) {
                    // Postgres unnest(arrays)
                    int colsSize = meta.isIdGenerated() ? meta.getColumns().size() - 1 : meta.getColumns().size();
                    int paramIndex = 1;
                    
                    if (!meta.isIdGenerated()) {
                        Object[] ids = entities.stream().map(meta::getId).toArray();
                        cs.setArray(paramIndex++, conn.createArrayOf(getPgTypeName(meta.getIdType()), ids));
                    }
                    for (ColumnMeta col : meta.getColumns()) {
                        if (col.isId()) continue;
                        Object[] vals = entities.stream().map(e -> getFieldValue(e, col)).toArray();
                        cs.setArray(paramIndex++, conn.createArrayOf(getPgTypeName(col.getFieldType()), vals));
                    }
                    cs.execute();
                } else {
                    // MySQL/SQLServer/Oracle pass JSON string
                    List<Map<String, Object>> jsonList = new ArrayList<>();
                    for (T entity : entities) {
                        Map<String, Object> map = new HashMap<>();
                        if (!meta.isIdGenerated()) {
                            map.put(meta.getIdColumnName(), meta.getId(entity));
                        }
                        for (ColumnMeta col : meta.getColumns()) {
                            if (col.isId()) continue;
                            map.put(col.getColumnName(), getFieldValue(entity, col));
                        }
                        jsonList.add(map);
                    }
                    String json = objectMapper.writeValueAsString(jsonList);
                    cs.setString(1, json);
                    cs.execute();
                }
            } catch (Exception e) {
                throw new SQLException("Failed execution of batchInsert procedure", e);
            }
        } finally {
            releaseSession(session);
        }
    }

    @Override
    public void batchUpdate(Collection<T> entities) throws SQLException {
        if (entities == null || entities.isEmpty()) return;
        Session session = getSession();
        try {
            // standard JDBC batching for update procedure
            Connection conn = session.getConnection();
            String sql = dialect.getUpdateCallStatement(meta);
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                for (T entity : entities) {
                    int paramIndex = 1;
                    ps.setObject(paramIndex++, meta.getId(entity));
                    for (ColumnMeta col : meta.getColumns()) {
                        if (col.isId()) continue;
                        ps.setObject(paramIndex++, getFieldValue(entity, col));
                    }
                    ps.addBatch();
                }
                ps.executeBatch();
            }
        } finally {
            releaseSession(session);
        }
    }

    private T mapRowAndDecrypt(ResultSet rs) throws SQLException {
        T entity = meta.mapRow(rs);
        for (ColumnMeta col : meta.getColumns()) {
            if (col.isEncrypted()) {
                String encryptedVal = rs.getString(col.getColumnName());
                if (encryptedVal != null && encryption != null) {
                    setFieldValue(entity, col, encryption.decrypt(encryptedVal));
                }
            }
        }
        return entity;
    }

    private Object getFieldValue(T entity, ColumnMeta col) {
        // We will call the generated getters or use reflection fallback in base
        try {
            // The generated Meta handles bindings without reflection, but in base, we support encryption
            Object val = meta.getClass().getMethod("get" + capitalize(col.getFieldName()), meta.getEntityClass())
                             .invoke(meta, entity);
            if (col.isEncrypted() && val != null && encryption != null) {
                return encryption.encrypt(val.toString());
            }
            return val;
        } catch (Exception e) {
            // Fallback via reflection on entity directly
            try {
                var field = meta.getEntityClass().getDeclaredField(col.getFieldName());
                field.setAccessible(true);
                Object val = field.get(entity);
                if (col.isEncrypted() && val != null && encryption != null) {
                    return encryption.encrypt(val.toString());
                }
                return val;
            } catch (Exception ex) {
                return null;
            }
        }
    }

    private void setFieldValue(T entity, ColumnMeta col, Object value) {
        try {
            var field = meta.getEntityClass().getDeclaredField(col.getFieldName());
            field.setAccessible(true);
            field.set(entity, value);
        } catch (Exception e) {
            // ignore
        }
    }

    @SuppressWarnings("unchecked")
    private ID castId(Object obj) {
        Class<?> target = meta.getIdType();
        if (obj == null) return null;
        if (target == Long.class || target == long.class) {
            return (ID) Long.valueOf(obj.toString());
        } else if (target == Integer.class || target == int.class) {
            return (ID) Integer.valueOf(obj.toString());
        } else if (target == String.class) {
            return (ID) obj.toString();
        }
        return (ID) obj;
    }

    private int getSqlType(Class<?> type) {
        if (type == Long.class || type == long.class) return Types.BIGINT;
        if (type == Integer.class || type == int.class) return Types.INTEGER;
        if (type == String.class) return Types.VARCHAR;
        return Types.OTHER;
    }

    private String getPgTypeName(Class<?> type) {
        if (type == Long.class || type == long.class) return "bigint";
        if (type == Integer.class || type == int.class) return "integer";
        if (type == Double.class || type == double.class) return "double precision";
        if (type == Boolean.class || type == boolean.class) return "boolean";
        return "varchar";
    }

    private String capitalize(String name) {
        return name.substring(0, 1).toUpperCase() + name.substring(1);
    }
}
