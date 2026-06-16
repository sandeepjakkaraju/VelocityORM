package com.velocityorm.reactive;

import com.velocityorm.core.VelocityORM;
import com.velocityorm.core.metadata.ColumnMeta;
import com.velocityorm.core.metadata.EntityMeta;
import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.Statement;
import java.util.Optional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class ReactiveRepositoryImpl<T, ID> implements ReactiveRepository<T, ID> {
    private final VelocityORM orm;
    private final ConnectionFactory connectionFactory;
    private final EntityMeta<T, ID> meta;

    public ReactiveRepositoryImpl(VelocityORM orm, ConnectionFactory connectionFactory, EntityMeta<T, ID> meta) {
        this.orm = orm;
        this.connectionFactory = connectionFactory;
        this.meta = meta;
    }

    private Mono<Connection> getConnection() {
        return Mono.from(connectionFactory.create());
    }

    private String convertToR2dbcSql(String sql) {
        if ("PostgreSQL".equals(orm.getDialect().getName())) {
            StringBuilder sb = new StringBuilder();
            int paramCount = 1;
            for (int i = 0; i < sql.length(); i++) {
                if (sql.charAt(i) == '?') {
                    sb.append("$").append(paramCount++);
                } else {
                    sb.append(sql.charAt(i));
                }
            }
            return sb.toString();
        }
        return sql;
    }

    @Override
    public Mono<T> findByIdReactive(ID id) {
        String sql = orm.getDialect().getGetCallStatement(meta);
        String r2dbcSql = convertToR2dbcSql(sql);

        return getConnection().flatMap(conn -> {
            Statement stmt = conn.createStatement(r2dbcSql);
            stmt.bind(0, id);
            
            // For PostgreSQL, sp_get INOUT params register columns. R2DBC returns them as rows.
            return Mono.from(stmt.execute())
                    .flatMap(result -> Mono.from(result.map((row, metadata) -> {
                        try {
                            if ("PostgreSQL".equals(orm.getDialect().getName())) {
                                Object returnedId = row.get(meta.getIdColumnName());
                                if (returnedId == null) {
                                    return Optional.<T>empty();
                                }
                            }
                            T entity = meta.getEntityClass().getDeclaredConstructor().newInstance();
                            meta.setId(entity, id);
                            for (ColumnMeta col : meta.getColumns()) {
                                if (col.isId()) continue;
                                Object val = row.get(col.getColumnName());
                                setFieldValue(entity, col, val);
                            }
                            return Optional.of(entity);
                        } catch (Exception e) {
                            throw new RuntimeException("Failed to map R2DBC row to entity", e);
                        }
                    })))
                    .flatMap(opt -> opt.isPresent() ? Mono.just(opt.get()) : Mono.empty())
                    .doFinally(signal -> Mono.from(conn.close()).subscribe());
        });
    }

    @Override
    public Mono<T> saveReactive(T entity) {
        ID id = meta.getId(entity);
        if (id == null) {
            String sql = orm.getDialect().getInsertCallStatement(meta);
            String r2dbcSql = convertToR2dbcSql(sql);
            
            return getConnection().flatMap(conn -> {
                Statement stmt = conn.createStatement(r2dbcSql);
                int bindIndex = 0;
                
                // If ID is database-generated, bind is skipped or set to default
                if (meta.isIdGenerated()) {
                    // For PG, INOUT param binds at 0, or standard inserts
                    // Let's bind null or let database generate it
                    stmt.bindNull(bindIndex++, meta.getIdType());
                } else {
                    stmt.bind(bindIndex++, meta.getId(entity));
                }
                
                for (ColumnMeta col : meta.getColumns()) {
                    if (col.isId()) continue;
                    Object val = getFieldValue(entity, col);
                    if (val == null) {
                        stmt.bindNull(bindIndex++, col.getFieldType());
                    } else {
                        stmt.bind(bindIndex++, val);
                    }
                }
                
                return Mono.from(stmt.execute())
                        .flatMap(result -> Mono.from(result.map((row, metadata) -> {
                            if (meta.isIdGenerated()) {
                                Object generatedId = row.get(0);
                                if (generatedId != null) {
                                    meta.setId(entity, castId(generatedId));
                                }
                            }
                            return entity;
                        })))
                        .switchIfEmpty(Mono.just(entity)) // if no result, return original entity
                        .doFinally(signal -> Mono.from(conn.close()).subscribe());
            });
        } else {
            return updateReactive(entity);
        }
    }

    @Override
    public Mono<T> updateReactive(T entity) {
        String sql = orm.getDialect().getUpdateCallStatement(meta);
        String r2dbcSql = convertToR2dbcSql(sql);

        return getConnection().flatMap(conn -> {
            Statement stmt = conn.createStatement(r2dbcSql);
            int bindIndex = 0;
            stmt.bind(bindIndex++, meta.getId(entity));
            
            for (ColumnMeta col : meta.getColumns()) {
                if (col.isId()) continue;
                Object val = getFieldValue(entity, col);
                if (val == null) {
                    stmt.bindNull(bindIndex++, col.getFieldType());
                } else {
                    stmt.bind(bindIndex++, val);
                }
            }
            
            return Mono.from(stmt.execute())
                    .then(Mono.just(entity))
                    .doFinally(signal -> Mono.from(conn.close()).subscribe());
        });
    }

    @Override
    public Mono<Void> deleteReactive(ID id) {
        String sql = orm.getDialect().getDeleteCallStatement(meta);
        String r2dbcSql = convertToR2dbcSql(sql);

        return getConnection().flatMap(conn -> {
            Statement stmt = conn.createStatement(r2dbcSql);
            stmt.bind(0, id);
            return Mono.from(stmt.execute())
                    .then()
                    .doFinally(signal -> Mono.from(conn.close()).subscribe());
        });
    }

    @Override
    public Flux<T> findAllReactive() {
        String sql = "SELECT * FROM " + orm.getDialect().quoteIdentifier(meta.getTableName());
        return getConnection().flatMapMany(conn -> {
            Statement stmt = conn.createStatement(sql);
            return Flux.from(stmt.execute())
                    .flatMap(result -> result.map((row, metadata) -> {
                        try {
                            T entity = meta.getEntityClass().getDeclaredConstructor().newInstance();
                            for (ColumnMeta col : meta.getColumns()) {
                                Object val = row.get(col.getColumnName());
                                setFieldValue(entity, col, val);
                            }
                            return entity;
                        } catch (Exception e) {
                            throw new RuntimeException("Failed to map R2DBC row to entity", e);
                        }
                    }))
                    .doFinally(signal -> Mono.from(conn.close()).subscribe());
        });
    }

    private Object getFieldValue(T entity, ColumnMeta col) {
        try {
            var field = meta.getEntityClass().getDeclaredField(col.getFieldName());
            field.setAccessible(true);
            return field.get(entity);
        } catch (Exception e) {
            return null;
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
        }
        return (ID) obj;
    }
}
