package com.velocityorm.core.tx;

/**
 * @author sandeepkumarjakkaraju
 */
public class SessionContext {
    private static final ThreadLocal<Session> currentSession = new ThreadLocal<>();

    public static Session getCurrentSession() {
        return currentSession.get();
    }

    public static void setCurrentSession(Session session) {
        currentSession.set(session);
    }

    public static boolean hasCurrentSession() {
        return currentSession.get() != null;
    }

    public static void clear() {
        currentSession.remove();
    }
}
