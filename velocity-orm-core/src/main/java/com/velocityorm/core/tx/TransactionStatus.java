package com.velocityorm.core.tx;

import java.sql.Savepoint;

/**
 * @author sandeepkumarjakkaraju
 */
public class TransactionStatus {
    private final Session session;
    private final boolean isNewSession;
    private final boolean isNewTransaction;
    private final Savepoint savepoint;
    private final Session suspendedSession;
    private boolean rollbackOnly = false;

    public TransactionStatus(Session session, boolean isNewSession, boolean isNewTransaction, Savepoint savepoint, Session suspendedSession) {
        this.session = session;
        this.isNewSession = isNewSession;
        this.isNewTransaction = isNewTransaction;
        this.savepoint = savepoint;
        this.suspendedSession = suspendedSession;
    }

    public Session getSession() {
        return session;
    }

    public boolean isNewSession() {
        return isNewSession;
    }

    public boolean isNewTransaction() {
        return isNewTransaction;
    }

    public Savepoint getSavepoint() {
        return savepoint;
    }

    public Session getSuspendedSession() {
        return suspendedSession;
    }

    public boolean isRollbackOnly() {
        return rollbackOnly;
    }

    public void setRollbackOnly(boolean rollbackOnly) {
        this.rollbackOnly = rollbackOnly;
    }
}
