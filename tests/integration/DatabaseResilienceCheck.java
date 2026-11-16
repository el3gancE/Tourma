import dao.DBContext;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;

/** Read-only contention plus a session-local temp-table rollback check. */
public class DatabaseResilienceCheck {
    public static void main(String[] args) throws Exception {
        DBContext db = new DBContext();
        try (Connection c = db.getConnection(); Statement s = c.createStatement()) {
            if (s.getQueryTimeout() != 20) throw new AssertionError("Missing default query timeout");
            s.execute("CREATE TABLE #tourma_pool_rollback (value int)");
            c.setAutoCommit(false);
            s.executeUpdate("INSERT INTO #tourma_pool_rollback VALUES (1)");
            // Deliberately return an open transaction; the pool must roll it back.
        }
        try (Connection c = db.getConnection(); Statement s = c.createStatement()) {
            if (!c.getAutoCommit()) throw new AssertionError("Auto-commit was not reset");
            try (ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM #tourma_pool_rollback")) {
                rs.next(); if (rs.getInt(1) != 0) throw new AssertionError("Transaction was not rolled back");
            }
            s.execute("DROP TABLE #tourma_pool_rollback");
        }
        try (Connection c = db.getConnection(); Statement s = c.createStatement()) {
            s.setQueryTimeout(1);
            long start = System.nanoTime();
            boolean timedOut = false;
            try { s.execute("WAITFOR DELAY '00:00:03'"); }
            catch (SQLException expected) {
                // The bundled older JDBC driver reports a SQLServerException here.
                timedOut = expected.getMessage().toLowerCase(Locale.ROOT).contains("timed out");
                if (!timedOut) throw expected;
            }
            if (!timedOut || TimeUnit.NANOSECONDS.toSeconds(System.nanoTime()-start) >= 10)
                throw new AssertionError("Query cancellation did not bound the wait");
            try (Statement next = c.createStatement(); ResultSet rs = next.executeQuery("SELECT 1")) {
                if (!rs.next()) throw new AssertionError("Connection unusable after cancellation");
            }
        }
        ExecutorService workers = Executors.newFixedThreadPool(4);
        List<Future<Long>> results = new ArrayList<>();
        try {
            for (int i=0;i<12;i++) results.add(workers.submit(() -> {
                long start = System.nanoTime();
                try (Connection c = db.getConnection(); PreparedStatement s = c.prepareStatement("SELECT 1")) {
                    try (ResultSet rs = s.executeQuery()) {
                        if (!rs.next() || rs.getInt(1) != 1) throw new AssertionError("Read failed");
                    }
                }
                return TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start);
            }));
            List<Long> millis = new ArrayList<>();
            for (Future<Long> result : results) millis.add(result.get(30, TimeUnit.SECONDS));
            Collections.sort(millis);
            System.out.println("PASS: rollback/reset, real query cancellation and 12 reads / 4 workers; median="
                    + millis.get(6) + "ms max=" + millis.get(11) + "ms; " + DBContext.poolSnapshot());
        } finally { workers.shutdownNow(); }
    }
}
