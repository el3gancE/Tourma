package dao;
import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** One shared win/loss snapshot per Series; score/participant changes invalidate it. */
public final class MatchStatisticsDAO {
    private static final Map<String,Entry> CACHE = new ConcurrentHashMap<>();
    private static final AtomicLong GENERATION = new AtomicLong();
    private static final Object[] LOCKS = new Object[16];
    static { Arrays.setAll(LOCKS, i -> new Object()); }
    private static class Entry {
        final Map<String,int[]> data;
        final long generation;
        Entry(Map<String,int[]> data, long generation) { this.data=data; this.generation=generation; }
        boolean valid(long version) { return generation==version; }
    }
    public static void clearCache() { GENERATION.incrementAndGet(); CACHE.clear(); }
    public static Map<String,int[]> get(Connection conn,String seriesId) throws SQLException {
        if(seriesId==null || seriesId.trim().isEmpty()) return Collections.emptyMap();
        String key=seriesId.trim();
        Entry cached=CACHE.get(key);
        if(cached!=null && cached.valid(GENERATION.get()))return cached.data;
        synchronized(LOCKS[(key.hashCode() & Integer.MAX_VALUE) % LOCKS.length]) {
            long generation=GENERATION.get();
            cached=CACHE.get(key);
            if(cached!=null && cached.valid(generation))return cached.data;
            Map<String,int[]> data=load(conn,key);
            if(generation==GENERATION.get())CACHE.put(key,new Entry(data,generation));
            return data;
        }
    }
    public static Map<String, int[]> load(Connection conn, String seriesId) throws java.sql.SQLException {
        // Stream each match once. SQL aggregation/hash joins required a memory grant
        // and stalled profile requests under server memory pressure.
        String sql = "SELECT m.team1_id, m.team2_id, CASE "
                + "WHEN m.winner_id IN (m.team1_id, m.team2_id) THEN m.winner_id "
                + "WHEN m.winner_id IS NULL THEN CASE "
                + "WHEN m.score1 > m.score2 THEN m.team1_id WHEN m.score2 > m.score1 THEN m.team2_id "
                + "WHEN m.penalty1 >= 0 AND m.penalty2 >= 0 AND m.penalty1 > m.penalty2 THEN m.team1_id "
                + "WHEN m.penalty1 >= 0 AND m.penalty2 >= 0 AND m.penalty2 > m.penalty1 THEN m.team2_id END "
                + "END AS winner_id FROM matches m JOIN tournaments t ON t.id = m.tournament_id "
                + "JOIN teams a ON a.id = m.team1_id AND a.tournament_id = m.tournament_id "
                + "JOIN teams b ON b.id = m.team2_id AND b.tournament_id = m.tournament_id "
                + "WHERE t.series_id = ? AND ISNULL(m.is_bye, 0) = 0 AND m.team1_id <> m.team2_id "
                + "AND UPPER(m.status) IN ('FINISHED', 'COMPLETED', 'DONE') "
                + "AND m.score1 >= 0 AND m.score2 >= 0 OPTION (LOOP JOIN, MAXDOP 1)";
        Map<String, int[]> stats = new HashMap<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, seriesId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String first = rs.getString("team1_id");
                    String second = rs.getString("team2_id");
                    String winner = rs.getString("winner_id");
                    int[] firstStats = stats.computeIfAbsent(first, key -> new int[2]);
                    int[] secondStats = stats.computeIfAbsent(second, key -> new int[2]);
                    if (winner != null) {
                        firstStats[winner.equals(first) ? 0 : 1]++;
                        secondStats[winner.equals(second) ? 0 : 1]++;
                    }
                }
            }
        }
        return stats;
    }
}
