package dao;

import java.lang.reflect.Proxy;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Deterministic pool lifecycle and contention checks; no database required. */
public class JdbcConnectionPoolTest {
    static class Physical {
        boolean closed, valid = true, autoCommit = true, readOnly, failRollback;
        int rollbacks, statementCloses, timeout, isolation = Connection.TRANSACTION_READ_COMMITTED;
        String catalog = "original";
        final Connection connection = (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{Connection.class}, (p,m,a) -> {
            switch (m.getName()) {
                case "close": closed = true; return null;
                case "isClosed": return closed;
                case "isValid": return valid;
                case "getAutoCommit": return autoCommit;
                case "setAutoCommit": autoCommit = (boolean)a[0]; return null;
                case "getTransactionIsolation": return isolation;
                case "setTransactionIsolation": isolation = (int)a[0]; return null;
                case "getCatalog": return catalog;
                case "setCatalog": catalog = (String)a[0]; return null;
                case "isReadOnly": return readOnly;
                case "setReadOnly": readOnly = (boolean)a[0]; return null;
                case "rollback": rollbacks++; if (failRollback) throw new SQLException("rollback failed"); return null;
                case "prepareStatement": case "createStatement": case "prepareCall": return statement(m.getName());
                default: return null;
            }
        });
        Statement statement(String kind) {
            Class<?> api = kind.equals("prepareCall") ? CallableStatement.class : kind.equals("prepareStatement") ? PreparedStatement.class : Statement.class;
            AtomicBoolean statementClosed = new AtomicBoolean();
            return (Statement) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{api}, (p,m,a) -> {
                switch (m.getName()) {
                    case "setQueryTimeout": timeout = (int)a[0]; return null;
                    case "getQueryTimeout": return timeout;
                    case "close": if (statementClosed.compareAndSet(false,true)) statementCloses++; return null;
                    case "isClosed": return statementClosed.get();
                    case "executeQuery": throw new SQLTimeoutException("deliberate timeout", "HYT00");
                    default: return null;
                }
            });
        }
    }
    static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    @FunctionalInterface interface Action { void run() throws Exception; }
    static void fails(Class<? extends Exception> type, Action action) throws Exception {
        try { action.run(); } catch (Exception e) { if (type.isInstance(e)) return; throw e; }
        throw new AssertionError("Expected " + type.getSimpleName());
    }
    public static void main(String[] args) throws Exception {
        List<Physical> physical = new ArrayList<>();
        JdbcConnectionPool pool = new JdbcConnectionPool(() -> { Physical p = new Physical(); physical.add(p); return p.connection; }, 2, 75, 20);
        Connection a = pool.borrow(), b = pool.borrow();
        fails(SQLTransientConnectionException.class, pool::borrow);
        check(physical.size() == 2, "Exceeded physical connection cap");
        a.setAutoCommit(false);
        a.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
        a.setCatalog("changed"); a.setReadOnly(true);
        PreparedStatement statement = a.prepareStatement("SELECT 1");
        check(statement.getQueryTimeout() == 20, "Missing statement deadline");
        check(statement.getConnection() == a, "Statement exposes physical connection");
        fails(SQLTimeoutException.class, statement::executeQuery);
        a.close(); a.close();
        check(physical.get(0).rollbacks == 1 && physical.get(0).autoCommit, "Uncommitted transaction leaked");
        check(physical.get(0).isolation == Connection.TRANSACTION_READ_COMMITTED && physical.get(0).catalog.equals("original") && !physical.get(0).readOnly, "Session settings leaked");
        check(physical.get(0).statementCloses == 1, "Unclosed statement leaked");
        fails(SQLException.class, () -> a.prepareStatement("SELECT 1"));
        fails(SQLException.class, statement::executeQuery);
        Connection c = pool.borrow();
        fails(SQLTransientConnectionException.class, pool::borrow);
        c.close(); b.close();
        physical.get(0).valid = false;
        Connection d = pool.borrow();
        check(physical.get(0).closed, "Broken idle connection reused");
        d.close();

        Physical bad = new Physical(); bad.failRollback = true;
        AtomicInteger opens = new AtomicInteger();
        JdbcConnectionPool cleanup = new JdbcConnectionPool(() -> opens.getAndIncrement() == 0 ? bad.connection : new Physical().connection, 1, 75, 20);
        Connection failed = cleanup.borrow(); failed.setAutoCommit(false); failed.close();
        try (Connection ignored = cleanup.borrow()) { check(bad.closed && opens.get() == 2, "Failed cleanup reused or leaked permit"); }
        AtomicInteger attempts = new AtomicInteger();
        JdbcConnectionPool factoryFailure = new JdbcConnectionPool(() -> { if (attempts.getAndIncrement() == 0) throw new SQLException("open failed"); return new Physical().connection; }, 1, 75, 20);
        fails(SQLException.class, factoryFailure::borrow);
        try (Connection ignored = factoryFailure.borrow()) { }

        AtomicInteger created = new AtomicInteger(), active = new AtomicInteger(), peak = new AtomicInteger();
        JdbcConnectionPool concurrent = new JdbcConnectionPool(() -> { created.incrementAndGet(); return new Physical().connection; }, 3, 2000, 20);
        ExecutorService workers = Executors.newFixedThreadPool(8);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int i=0;i<40;i++) futures.add(workers.submit(() -> {
                try (Connection ignored = concurrent.borrow()) {
                    peak.accumulateAndGet(active.incrementAndGet(), Math::max);
                    try { Thread.sleep(5); } finally { active.decrementAndGet(); }
                } catch (Exception e) { throw new RuntimeException(e); }
            }));
            for (Future<?> future : futures) future.get(5, TimeUnit.SECONDS);
        } finally { workers.shutdownNow(); }
        check(created.get() <= 3 && peak.get() <= 3, "Concurrent borrowers exceeded limit");
        check(concurrent.snapshot().contains("active=0/3"), "Lease permit leaked");
        concurrent.close();
        fails(SQLNonTransientConnectionException.class, concurrent::borrow);
        Physical retired = new Physical();
        JdbcConnectionPool shutdown = new JdbcConnectionPool(() -> retired.connection,1,75,20);
        Connection inFlight = shutdown.borrow(); shutdown.close(); inFlight.close();
        check(retired.closed,"In-flight connection reused after redeploy");
        System.out.println("PASS: bounded contention, double-close, rollback/reset, broken connections, statement deadlines, SQL exceptions and 40 concurrent leases");
    }
}
