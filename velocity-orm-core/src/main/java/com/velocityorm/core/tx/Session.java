package com.velocityorm.core.tx;

import com.velocityorm.core.cache.L1Cache;
import java.sql.Connection;

/**
 * @author sandeepkumarjakkaraju
 */
public class Session implements AutoCloseable {
    private final Connection connection;
    private final L1Cache l1Cache;
    private boolean inTransaction;
    private boolean readOnly;

    public Session(Connection connection) {
        this.connection = connection;
        this.l1Cache = new L1Cache();
        this.inTransaction = false;
        this.readOnly = false;
    }

    public Connection getConnection() {
        return connection;
    }

    public L1Cache getL1Cache() {
        return l1Cache;
    }

    public boolean isInTransaction() {
        return inTransaction;
    }

    public void setInTransaction(boolean inTransaction) {
        this.inTransaction = inTransaction;
    }

    public boolean isReadOnly() {
        return readOnly;
    }

    public void setReadOnly(boolean readOnly) {
        this.readOnly = readOnly;
    }

    @Override
    public void close() throws Exception {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
        l1Cache.clear();
    }
}
