package dao;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

public class LeagueSeasonResultDAO {
    public static boolean isClosedTournament(Connection conn, String tournamentId) throws Exception {
        // Caller holds the tournament lock, which season closing also acquires before writing its state.
        String sql = "SELECT 1 FROM dbo.seasons s WITH (READUNCOMMITTED) WHERE s.status='COMPLETED' "
                + "AND (s.special_cup_tournament_id=? OR EXISTS (SELECT 1 FROM dbo.league_season_divisions d "
                + "WITH (READUNCOMMITTED) WHERE d.season_id=s.id AND d.tournament_id=?))";
        try (PreparedStatement query = conn.prepareStatement(sql)) {
            query.setString(1, tournamentId);
            query.setString(2, tournamentId);
            try (ResultSet rows = query.executeQuery()) {
                return rows.next();
            }
        }
    }

    private record Season(String name, String status) {}
    private record Event(int level, int legs, int slots, String tournamentId, int teams, boolean complete) {
        String label() { return level == 0 ? "Cúp SE" : "Hạng " + level; }
    }
    public record Result(String key, String name, int level, int next, int rank, int points,
                         int played, int wins, int draws, int losses, int goalsFor, int goalsAgainst) {
        public String getName() { return name; }
        public int getLevel() { return level; }
        public int getNext() { return next; }
        public int getRank() { return rank; }
        public int getPoints() { return points; }
    }
    public record Review(String name, boolean closed, List<String> pending, List<Result> results, String revision) {}

    public List<String> pending(String seasonId) throws Exception {
        try (Connection conn = new DBContext().getConnection()) {
            return pending(events(conn, seasonId, false));
        }
    }

    public Review review(String seriesId, String seasonId) throws Exception {
        try (Connection conn = new DBContext().getConnection()) {
            return review(conn, seriesId, seasonId, false);
        }
    }

    public void close(String seriesId, String seasonId, String revision) throws Exception {
        try (Connection conn = new DBContext().getConnection()) {
            conn.setAutoCommit(false);
            try {
                Review review = review(conn, seriesId, seasonId, true);
                if (review.closed()) {
                    conn.commit();
                    return;
                }
                if (!review.pending().isEmpty()) {
                    throw new IllegalStateException("Chưa hoàn tất: " + String.join(", ", review.pending()) + ".");
                }
                if (!review.revision().equals(revision)) {
                    throw new IllegalStateException("Kết quả đã thay đổi. Hãy mở lại trang chốt mùa để kiểm tra.");
                }
                try (PreparedStatement insert = conn.prepareStatement(
                        "INSERT dbo.league_season_results (season_id, team_key, final_rank, points, played, wins, draws, losses, "
                        + "goals_for, goals_against, next_division_level) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
                     PreparedStatement finish = conn.prepareStatement(
                             "UPDATE dbo.seasons SET status='COMPLETED', completed_at=GETDATE() WHERE id=?")) {
                    for (Result row : review.results()) {
                        insert.setString(1, seasonId);
                        insert.setNString(2, row.key());
                        insert.setInt(3, row.rank());
                        insert.setInt(4, row.points());
                        insert.setInt(5, row.played());
                        insert.setInt(6, row.wins());
                        insert.setInt(7, row.draws());
                        insert.setInt(8, row.losses());
                        insert.setInt(9, row.goalsFor());
                        insert.setInt(10, row.goalsAgainst());
                        insert.setInt(11, row.next());
                        insert.addBatch();
                    }
                    insert.executeBatch();
                    finish.setString(1, seasonId);
                    finish.executeUpdate();
                }
                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                throw e;
            }
        }
    }

    private Review review(Connection conn, String seriesId, String seasonId, boolean lock) throws Exception {
        Season season;
        String hints = lock ? " WITH (UPDLOCK,HOLDLOCK)" : "";
        try (PreparedStatement query = conn.prepareStatement(
                "SELECT name,status FROM dbo.seasons" + hints + " WHERE id=? AND series_id=?")) {
            query.setString(1, seasonId);
            query.setString(2, seriesId);
            try (ResultSet row = query.executeQuery()) {
                if (!row.next()) throw new IllegalArgumentException("Không tìm thấy mùa giải.");
                season = new Season(row.getNString(1), row.getString(2));
            }
        }
        if ("COMPLETED".equals(season.status())) {
            return new Review(season.name(), true, List.of(), savedResults(conn, seasonId), "");
        }
        if (!"ACTIVE".equals(season.status())) throw new IllegalStateException("Mùa giải chưa được mở.");
        List<Event> events = events(conn, seasonId, lock);
        List<String> pending = pending(events);
        if (!pending.isEmpty()) return new Review(season.name(), false, pending, List.of(), "");

        List<Result> results = new ArrayList<>();
        RoundRobinDAO roundRobin = new RoundRobinDAO();
        int up = 0;
        for (Event event : events) {
            if (event.level() == 0) continue;
            var standings = roundRobin.getRoundRobinStandings(conn, event.tournamentId(), 1);
            if (standings.size() != event.teams() || (long) up + event.slots() > standings.size()) {
                throw new IllegalStateException("BXH hoặc số suất của " + event.label() + " không hợp lệ.");
            }
            int expectedPlayed = (event.teams() - 1) * event.legs();
            for (var row : standings) {
                if (row.matchesPlayed != expectedPlayed) {
                    throw new IllegalStateException("BXH của " + event.label() + " chưa đủ kết quả.");
                }
                int next = row.rank <= up ? event.level() - 1
                        : row.rank > standings.size() - event.slots() ? event.level() + 1 : event.level();
                results.add(new Result(LeagueSeriesTeamDAO.teamKey(row.teamName), row.teamName, event.level(), next,
                        row.rank, row.points, row.matchesPlayed, row.wins, row.draws, row.losses, row.goalsScored, row.goalsConceded));
            }
            up = event.slots();
        }
        String revision = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(results.toString().getBytes(StandardCharsets.UTF_8)));
        return new Review(season.name(), false, List.of(), results, revision);
    }

    private List<Event> events(Connection conn, String seasonId, boolean lock) throws Exception {
        String hints = lock ? " WITH (UPDLOCK,HOLDLOCK)" : "";
        String sql = """
                WITH competitions AS (
                    SELECT d.division_level AS level, d.round_robin_legs AS legs, d.exchange_slots_below AS slots,
                           d.tournament_id, (SELECT COUNT(*) FROM dbo.league_season_entries e
                               WHERE e.season_id=d.season_id AND e.division_level=d.division_level) AS teams
                    FROM dbo.league_season_divisions d WHERE d.season_id=?
                    UNION ALL
                    SELECT 0, 1, 0, s.special_cup_tournament_id,
                           (SELECT COUNT(*) FROM dbo.league_season_entries e WHERE e.season_id=s.id)
                    FROM dbo.seasons s WHERE s.id=? AND s.has_special_cup=1
                )
                SELECT c.*, t.status, games.total,
                    CASE WHEN c.level=0 THEN games.with_winner ELSE games.finished END AS finished
                FROM competitions c LEFT JOIN dbo.tournaments t
                """ + hints + """
                  ON t.id=c.tournament_id
                OUTER APPLY (
                    SELECT COUNT_BIG(*) AS total, COUNT_BIG(CASE WHEN m.status IN ('FINISHED','COMPLETED')
                        AND m.score1 IS NOT NULL AND m.score2 IS NOT NULL THEN 1 END) AS finished,
                        COUNT_BIG(CASE WHEN m.status IN ('FINISHED','COMPLETED') AND m.score1 IS NOT NULL
                            AND m.score2 IS NOT NULL AND m.winner_id IS NOT NULL THEN 1 END) AS with_winner
                    FROM dbo.matches m WHERE m.tournament_id=t.id AND ISNULL(m.is_bye,0)=0
                ) games
                ORDER BY CASE WHEN c.level=0 THEN 1 ELSE 0 END, c.level
                """;
        List<Event> events = new ArrayList<>();
        try (PreparedStatement query = conn.prepareStatement(sql)) {
            query.setString(1, seasonId);
            query.setString(2, seasonId);
            try (ResultSet rows = query.executeQuery()) {
                while (rows.next()) {
                    int n = rows.getInt("teams"), legs = rows.getInt("legs"), level = rows.getInt("level");
                    long expected = level == 0 ? n - 1L : (long) n * (n - 1) * legs / 2;
                    String status = rows.getString("status");
                    boolean complete = n >= 2 && rows.getLong("total") == expected && rows.getLong("finished") == expected
                            && ("COMPLETED".equals(status) || "FINISHED".equals(status));
                    events.add(new Event(level, legs, rows.getInt("slots"), rows.getString("tournament_id"), n, complete));
                }
            }
        }
        return events;
    }

    private List<String> pending(List<Event> events) {
        if (events.stream().noneMatch(event -> event.level() > 0)) return List.of("Các hạng đấu");
        return events.stream().filter(event -> !event.complete()).map(Event::label).toList();
    }

    private List<Result> savedResults(Connection conn, String seasonId) throws Exception {
        List<Result> results = new ArrayList<>();
        String sql = "SELECT r.team_key, e.team_name, e.division_level, r.next_division_level, r.final_rank, r.points, "
                + "r.played, r.wins, r.draws, r.losses, r.goals_for, r.goals_against "
                + "FROM dbo.league_season_results r JOIN dbo.league_season_entries e ON e.season_id=r.season_id AND e.team_key=r.team_key "
                + "WHERE r.season_id=? ORDER BY e.division_level,r.final_rank";
        try (PreparedStatement query = conn.prepareStatement(sql)) {
            query.setString(1, seasonId);
            try (ResultSet rows = query.executeQuery()) {
                while (rows.next()) {
                    results.add(new Result(rows.getNString(1), rows.getNString(2), rows.getInt(3), rows.getInt(4),
                            rows.getInt(5), rows.getInt(6), rows.getInt(7), rows.getInt(8), rows.getInt(9), rows.getInt(10),
                            rows.getInt(11), rows.getInt(12)));
                }
            }
        }
        return results;
    }
}
