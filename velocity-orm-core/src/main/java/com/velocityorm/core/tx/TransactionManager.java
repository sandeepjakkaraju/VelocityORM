package com.velocityorm.core.tx;

import com.velocityorm.core.annotation.Transactional;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * @author sandeepkumarjakkaraju
 */
public class TransactionManager {
    private final DataSource dataSource;

    public TransactionManager(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public TransactionStatus getTransaction(Transactional definition) throws SQLException {
        Transactional.Propagation propagation = definition != null ? definition.propagation() : Transactional.Propagation.REQUIRED;
        boolean readOnly = definition != null && definition.readOnly();

        Session currentSession = SessionContext.getCurrentSession();

        if (currentSession != null) {
            if (propagation == Transactional.Propagation.REQUIRED) {
                return new TransactionStatus(currentSession, false, false, null, null);
            } else if (propagation == Transactional.Propagation.REQUIRES_NEW) {
                // Suspend current
                SessionContext.clear();
                Session newSession = createNewSession(readOnly);
                SessionContext.setCurrentSession(newSession);
                return new TransactionStatus(newSession, true, true, null, currentSession);
            } else if (propagation == Transactional.Propagation.NESTED) {
                if (currentSession.getConnection().getMetaData().supportsSavepoints()) {
                    var sp = currentSession.getConnection().setSavepoint();
                    return new TransactionStatus(currentSession, false, false, sp, null);
                } else {
                    throw new SQLException("Nested transactions not supported: Database does not support savepoints");
                }
            }
        }

        // No transaction exists
        Session newSession = createNewSession(readOnly);
        SessionContext.setCurrentSession(newSession);
        return new TransactionStatus(newSession, true, true, null, null);
    }

    private Session createNewSession(boolean readOnly) throws SQLException {
        Connection conn = dataSource.getConnection();
        conn.setAutoCommit(false);
        if (readOnly) {
            conn.setReadOnly(true);
        }
        Session session = new Session(conn);
        session.setInTransaction(true);
        session.setReadOnly(readOnly);
        return session;
    }

    public void commit(TransactionStatus status) throws SQLException {
        Session session = status.getSession();
        
        if (status.isRollbackOnly()) {
            rollback(status, new SQLException("Transaction marked as rollback-only"));
            return;
        }

        if (status.getSavepoint() != null) {
            try {
                session.getConnection().releaseSavepoint(status.getSavepoint());
            } catch (SQLException e) {
                // Some databases don't require releasing savepoints, ignore
            }
        } else if (status.isNewTransaction()) {
            try {
                session.getConnection().commit();
            } finally {
                cleanupSession(status);
            }
        }
    }

    public void rollback(TransactionStatus status, Throwable ex) throws SQLException {
        Session session = status.getSession();

        if (status.getSavepoint() != null) {
            session.getConnection().rollback(status.getSavepoint());
        } else if (status.isNewTransaction()) {
            try {
                session.getConnection().rollback();
            } finally {
                cleanupSession(status);
            }
        } else {
            status.setRollbackOnly(true);
        }
    }

    private void cleanupSession(TransactionStatus status) {
        if (status.isNewSession()) {
            try {
                status.getSession().close();
            } catch (Exception e) {
                // ignore
            }
            SessionContext.clear();
        }
        if (status.getSuspendedSession() != null) {
            SessionContext.setCurrentSession(status.getSuspendedSession());
        }
    }
}
