package com.velocityorm.core.query;

import com.velocityorm.core.dialect.Dialect;
import com.velocityorm.core.metadata.ColumnMeta;
import com.velocityorm.core.metadata.EntityMeta;
import com.velocityorm.core.tx.Session;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * @author sandeepkumarjakkaraju
 */
public class Query<T> {
    private final EntityMeta<T, ?> meta;
    private final Dialect dialect;
    private final Session session;

    private final List<Criterion> criteria = new ArrayList<>();
    private final List<Order> orderBy = new ArrayList<>();
    private Integer limitVal;
    private Integer offsetVal;

    private String currentProperty;
    private boolean isAnd = true;

    public Query(EntityMeta<T, ?> meta, Dialect dialect, Session session) {
        this.meta = meta;
        this.dialect = dialect;
        this.session = session;
    }

    public Query<T> where(PropertyFunc<T, ?> func) {
        this.currentProperty = LambdaExpressionParser.getPropertyName(func);
        this.isAnd = true;
        return this;
    }

    public Query<T> and(PropertyFunc<T, ?> func) {
        this.currentProperty = LambdaExpressionParser.getPropertyName(func);
        this.isAnd = true;
        return this;
    }

    public Query<T> or(PropertyFunc<T, ?> func) {
        this.currentProperty = LambdaExpressionParser.getPropertyName(func);
        this.isAnd = false;
        return this;
    }

    private void addCriterion(String operator, Object value) {
        ColumnMeta col = meta.getColumnByFieldName(currentProperty);
        if (col == null) {
            throw new IllegalArgumentException("Unknown property: " + currentProperty);
        }
        criteria.add(new Criterion(col.getColumnName(), operator, value, isAnd));
        currentProperty = null;
    }

    public Query<T> eq(Object value) {
        addCriterion("=", value);
        return this;
    }

    public Query<T> ne(Object value) {
        addCriterion("<>", value);
        return this;
    }

    public Query<T> gt(Object value) {
        addCriterion(">", value);
        return this;
    }

    public Query<T> gte(Object value) {
        addCriterion(">=", value);
        return this;
    }

    public Query<T> lt(Object value) {
        addCriterion("<", value);
        return this;
    }

    public Query<T> lte(Object value) {
        addCriterion("<=", value);
        return this;
    }

    public Query<T> like(Object value) {
        addCriterion("LIKE", value);
        return this;
    }

    public Query<T> in(Collection<?> values) {
        addCriterion("IN", values);
        return this;
    }

    public Query<T> between(Object val1, Object val2) {
        addCriterion("BETWEEN", List.of(val1, val2));
        return this;
    }

    public Query<T> isNull() {
        addCriterion("IS NULL", null);
        return this;
    }

    public Query<T> isNotNull() {
        addCriterion("IS NOT NULL", null);
        return this;
    }

    public Query<T> orderBy(PropertyFunc<T, ?> func, Direction direction) {
        String prop = LambdaExpressionParser.getPropertyName(func);
        ColumnMeta col = meta.getColumnByFieldName(prop);
        if (col == null) {
            throw new IllegalArgumentException("Unknown property: " + prop);
        }
        orderBy.add(new Order(col.getColumnName(), direction));
        return this;
    }

    public Query<T> limit(int limit) {
        this.limitVal = limit;
        return this;
    }

    public Query<T> offset(int offset) {
        this.offsetVal = offset;
        return this;
    }

    public List<T> list() throws SQLException {
        String sql = buildSelectSql();
        List<T> results = new ArrayList<>();
        
        try (PreparedStatement ps = session.getConnection().prepareStatement(sql)) {
            int paramIndex = 1;
            for (Criterion crit : criteria) {
                if (crit.value != null) {
                    if (crit.value instanceof Collection) {
                        for (Object val : (Collection<?>) crit.value) {
                            ps.setObject(paramIndex++, val);
                        }
                    } else if (crit.value instanceof List && "BETWEEN".equals(crit.operator)) {
                        for (Object val : (List<?>) crit.value) {
                            ps.setObject(paramIndex++, val);
                        }
                    } else {
                        ps.setObject(paramIndex++, crit.value);
                    }
                }
            }
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    T entity = meta.mapRow(rs);
                    Object id = meta.getId(entity);
                    session.getL1Cache().put(meta.getEntityClass(), id, entity);
                    results.add(entity);
                }
            }
        }
        return results;
    }

    public Optional<T> one() throws SQLException {
        limit(1);
        List<T> list = list();
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public long count() throws SQLException {
        String sql = buildCountSql();
        try (PreparedStatement ps = session.getConnection().prepareStatement(sql)) {
            int paramIndex = 1;
            for (Criterion crit : criteria) {
                if (crit.value != null) {
                    if (crit.value instanceof Collection) {
                        for (Object val : (Collection<?>) crit.value) {
                            ps.setObject(paramIndex++, val);
                        }
                    } else if (crit.value instanceof List && "BETWEEN".equals(crit.operator)) {
                        for (Object val : (List<?>) crit.value) {
                            ps.setObject(paramIndex++, val);
                        }
                    } else {
                        ps.setObject(paramIndex++, crit.value);
                    }
                }
            }
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        }
        return 0;
    }

    private String buildSelectSql() {
        StringBuilder sb = new StringBuilder();
        sb.append("SELECT * FROM ").append(dialect.quoteIdentifier(meta.getTableName()));
        appendWhereClause(sb);
        appendOrderByClause(sb);
        
        String sql = sb.toString();
        if (limitVal != null && offsetVal != null) {
            sql = dialect.paginate(sql, limitVal, offsetVal);
        } else if (limitVal != null) {
            sql = dialect.paginate(sql, limitVal, 0);
        }
        return sql;
    }

    private String buildCountSql() {
        StringBuilder sb = new StringBuilder();
        sb.append("SELECT COUNT(*) FROM ").append(dialect.quoteIdentifier(meta.getTableName()));
        appendWhereClause(sb);
        return sb.toString();
    }

    private void appendWhereClause(StringBuilder sb) {
        if (criteria.isEmpty()) return;
        sb.append(" WHERE ");
        for (int i = 0; i < criteria.size(); i++) {
            Criterion crit = criteria.get(i);
            if (i > 0) {
                sb.append(crit.isAnd ? " AND " : " OR ");
            }
            sb.append(dialect.quoteIdentifier(crit.columnName)).append(" ");
            if ("IN".equals(crit.operator)) {
                int size = ((Collection<?>) crit.value).size();
                String placeholders = size == 0 ? "" : String.join(",", java.util.Collections.nCopies(size, "?"));
                sb.append("IN (").append(placeholders).append(")");
            } else if ("BETWEEN".equals(crit.operator)) {
                sb.append("BETWEEN ? AND ?");
            } else if (crit.value == null) {
                sb.append(crit.operator);
            } else {
                sb.append(crit.operator).append(" ?");
            }
        }
    }

    private void appendOrderByClause(StringBuilder sb) {
        if (orderBy.isEmpty()) return;
        sb.append(" ORDER BY ");
        List<String> orders = new ArrayList<>();
        for (Order o : orderBy) {
            orders.add(dialect.quoteIdentifier(o.columnName) + " " + o.direction.name());
        }
        sb.append(String.join(", ", orders));
    }

    public enum Direction {
        ASC, DESC
    }

    private static class Criterion {
        final String columnName;
        final String operator;
        final Object value;
        final boolean isAnd;

        Criterion(String columnName, String operator, Object value, boolean isAnd) {
            this.columnName = columnName;
            this.operator = operator;
            this.value = value;
            this.isAnd = isAnd;
        }
    }

    private static class Order {
        final String columnName;
        final Direction direction;

        Order(String columnName, Direction direction) {
            this.columnName = columnName;
            this.direction = direction;
        }
    }
}
