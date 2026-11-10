package dao;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/** Bounded leases, transaction cleanup and statement deadlines for every DAO. */
public final class JdbcConnectionPool implements AutoCloseable {
    @FunctionalInterface
    public interface Factory { Connection open() throws SQLException; }
    private static final Logger LOG = Logger.getLogger(JdbcConnectionPool.class.getName());
    private final Factory factory;
    private final Semaphore permits;
    private final BlockingQueue<Connection> idle;
    private final int capacity, querySeconds;
    private final long acquireMillis;
    private final AtomicBoolean stopped = new AtomicBoolean();

    public JdbcConnectionPool(Factory factory, int capacity, long acquireMillis, int querySeconds) {
        if (capacity < 1 || acquireMillis < 1 || querySeconds < 1) throw new IllegalArgumentException("Invalid pool limits");
        this.factory = factory;
        this.capacity = capacity;
        this.acquireMillis = acquireMillis;
        this.querySeconds = querySeconds;
        permits = new Semaphore(capacity, true);
        idle = new ArrayBlockingQueue<>(capacity);
    }

    public String snapshot() {
        return "active=" + (capacity - permits.availablePermits()) + "/" + capacity
                + ", idle=" + idle.size() + ", waiting=" + permits.getQueueLength();
    }

    public Connection borrow() throws SQLException {
        if (stopped.get()) throw new SQLNonTransientConnectionException("Database pool is closed", "08003");
        try {
            if (!permits.tryAcquire(acquireMillis, TimeUnit.MILLISECONDS)) {
                LOG.warning("DB pool exhausted: " + snapshot());
                throw new SQLTransientConnectionException("Database busy: timed out waiting for a connection", "08001");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SQLTransientConnectionException("Interrupted waiting for database", "08001", e);
        }
        Connection physical = null;
        try {
            if (stopped.get()) throw new SQLNonTransientConnectionException("Database pool is closed", "08003");
            while ((physical = idle.poll()) != null) {
                try { if (!physical.isClosed() && physical.isValid(2)) break; }
                catch (SQLException ignored) { }
                dispose(physical);
                physical = null;
            }
            if (physical == null) physical = factory.open();
            return lease(physical);
        } catch (SQLException | RuntimeException e) {
            dispose(physical);
            permits.release();
            throw e;
        }
    }

    private Connection lease(Connection physical) throws SQLException {
        int isolation = physical.getTransactionIsolation();
        String catalog = physical.getCatalog();
        boolean readOnly = physical.isReadOnly();
        AtomicBoolean closed = new AtomicBoolean();
        Set<Statement> statements = Collections.newSetFromMap(new IdentityHashMap<>());
        Connection[] logical = new Connection[1];
        logical[0] = (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]{Connection.class}, (proxy, method, args) -> {
            String name = method.getName();
            if (method.getDeclaringClass() == Object.class) return objectMethod(proxy, method, args);
            if (name.equals("close")) {
                if (!closed.compareAndSet(false, true)) return null;
                boolean reusable = true;
                try {
                    // Roll back before returning a lease, including when the caller threw.
                    if (!physical.getAutoCommit()) physical.rollback();
                    for (Statement statement : new ArrayList<>(statements)) {
                        try { statement.close(); } catch (SQLException e) { reusable = false; }
                    }
                    statements.clear();
                    physical.setAutoCommit(true);
                    if (physical.getTransactionIsolation() != isolation) physical.setTransactionIsolation(isolation);
                    if (!Objects.equals(physical.getCatalog(), catalog)) physical.setCatalog(catalog);
                    if (physical.isReadOnly() != readOnly) physical.setReadOnly(readOnly);
                    physical.clearWarnings();
                    if (!reusable || physical.isClosed() || stopped.get() || !idle.offer(physical)) dispose(physical);
                    // Shutdown can race with the offer above.
                    else if (stopped.get() && idle.remove(physical)) dispose(physical);
                } catch (SQLException e) {
                    dispose(physical);
                    LOG.warning("Discarded database connection after cleanup failure: " + e.getClass().getSimpleName());
                } finally { permits.release(); }
                return null;
            }
            if (name.equals("isClosed")) return closed.get() || physical.isClosed();
            if (closed.get()) throw new SQLException("Connection lease is closed", "08003");
            if (name.equals("unwrap") && ((Class<?>)args[0]).isInstance(proxy)) return proxy;
            if (name.equals("isWrapperFor") && ((Class<?>)args[0]).isInstance(proxy)) return true;
            Object value = invoke(physical, method, args);
            if (value instanceof Statement && (name.equals("prepareStatement") || name.equals("createStatement") || name.equals("prepareCall"))) {
                Statement statement = (Statement)value;
                try { statement.setQueryTimeout(querySeconds); }
                catch (SQLException e) { dispose(statement); throw e; }
                statements.add(statement);
                return wrapStatement(statement, logical[0], closed, statements);
            }
            return value;
        });
        return logical[0];
    }

    private Statement wrapStatement(Statement physical, Connection connection, AtomicBoolean leaseClosed, Set<Statement> statements) {
        Class<?> api = physical instanceof CallableStatement ? CallableStatement.class
                : physical instanceof PreparedStatement ? PreparedStatement.class : Statement.class;
        AtomicBoolean closed = new AtomicBoolean();
        return (Statement) Proxy.newProxyInstance(Statement.class.getClassLoader(), new Class<?>[]{api}, (proxy, method, args) -> {
            String name = method.getName();
            if (method.getDeclaringClass() == Object.class) return objectMethod(proxy, method, args);
            if (name.equals("close")) {
                if (closed.compareAndSet(false, true)) { try { physical.close(); } finally { statements.remove(physical); } }
                return null;
            }
            if (name.equals("isClosed")) return leaseClosed.get() || closed.get() || physical.isClosed();
            if (leaseClosed.get() || closed.get()) throw new SQLException("Statement lease is closed", "07000");
            if (name.equals("getConnection")) return connection;
            if (name.equals("unwrap") && ((Class<?>)args[0]).isInstance(proxy)) return proxy;
            if (name.equals("isWrapperFor") && ((Class<?>)args[0]).isInstance(proxy)) return true;
            long start = System.nanoTime();
            try { return invoke(physical, method, args); }
            catch (SQLException e) {
                LOG.warning("DB operation failed: " + name + ", state=" + e.getSQLState() + ", code=" + e.getErrorCode());
                throw e;
            } finally {
                long millis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
                if (name.startsWith("execute") && millis >= 1000) LOG.warning("Slow DB operation: " + name + " " + millis + "ms; " + snapshot());
            }
        });
    }

    private static Object objectMethod(Object proxy, Method method, Object[] args) {
        switch (method.getName()) {
            case "equals": return proxy == args[0];
            case "hashCode": return System.identityHashCode(proxy);
            default: return "Pooled JDBC lease";
        }
    }

    private static Object invoke(Object target, Method method, Object[] args) throws Throwable {
        try { return method.invoke(target, args); }
        catch (InvocationTargetException e) { throw e.getCause(); }
    }

    private static void dispose(AutoCloseable value) {
        if (value != null) try { value.close(); } catch (Exception ignored) { }
    }

    @Override public void close() {
        stopped.set(true);
        Connection connection;
        while ((connection = idle.poll()) != null) dispose(connection);
        // In-flight leases are disposed when returned, rather than reused.
    }
}
