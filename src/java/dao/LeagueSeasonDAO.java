package dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.UUID;

public class LeagueSeasonDAO {
    public record Setup(String name, boolean cup, List<Integer> legs, List<Integer> slots,
            Map<String, Integer> assignments) {}
    private record Entry(String name, String key) {}
    public record Competition(String tournamentId, String label, String seasonId) {}

    public List<Competition> listSiblingCompetitions(String seriesId, String tournamentId) throws Exception {
        String sql = """
                WITH current_season AS (
                    SELECT s.id, s.special_cup_tournament_id
                    FROM dbo.seasons s
                    WHERE s.series_id = ? AND (s.special_cup_tournament_id = ? OR EXISTS (
                        SELECT 1 FROM dbo.league_season_divisions d
                        WHERE d.season_id = s.id AND d.tournament_id = ?
                    ))
                )
                SELECT d.tournament_id, d.division_level, 0 AS is_cup, s.id AS season_id
                FROM current_season s JOIN dbo.league_season_divisions d ON d.season_id = s.id
                JOIN dbo.tournaments t ON t.id = d.tournament_id
                UNION ALL
                SELECT s.special_cup_tournament_id, 0, 1, s.id
                FROM current_season s JOIN dbo.tournaments t ON t.id = s.special_cup_tournament_id
                ORDER BY is_cup, division_level
                """;
        List<Competition> competitions = new ArrayList<>();
        try (Connection conn = new DBContext().getConnection();
             PreparedStatement statement = conn.prepareStatement(sql)) {
            statement.setString(1, seriesId);
            statement.setString(2, tournamentId);
            statement.setString(3, tournamentId);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    String label = rows.getBoolean("is_cup") ? "Cúp SE" : "Hạng " + rows.getInt("division_level");
                    competitions.add(new Competition(rows.getString("tournament_id"), label, rows.getString("season_id")));
                }
            }
        }
        return competitions;
    }

    public List<Map<String, Object>> list(String seriesId) throws Exception {
        List<Map<String, Object>> seasons = new ArrayList<>();
        try (Connection conn = new DBContext().getConnection();
             PreparedStatement query = conn.prepareStatement(
                     "SELECT id, season_number, name, status, has_special_cup, special_cup_tournament_id FROM dbo.seasons "
                     + "WHERE series_id = ? ORDER BY season_number DESC")) {
            query.setString(1, seriesId);
            try (ResultSet rows = query.executeQuery()) {
                while (rows.next()) {
                    String status = rows.getString("status");
                    seasons.add(Map.of("id", rows.getString("id"), "number", rows.getInt("season_number"),
                            "name", rows.getNString("name"), "status", status == null ? "" : status,
                            "cup", rows.getBoolean("has_special_cup"),
                            "cupId", java.util.Objects.toString(rows.getString("special_cup_tournament_id"), "")));
                }
            }
        }
        return seasons;
    }

    public Map<String, List<Map<String, Object>>> listDivisions(String seriesId) throws Exception {
        String sql = "SELECT d.season_id, d.division_level, d.round_robin_legs, d.tournament_id, COUNT(e.team_key) AS team_count "
                + "FROM dbo.seasons s JOIN dbo.league_season_divisions d ON d.season_id = s.id "
                + "LEFT JOIN dbo.league_season_entries e ON e.season_id = d.season_id AND e.division_level = d.division_level "
                + "WHERE s.series_id = ? GROUP BY d.season_id, d.division_level, d.round_robin_legs, d.tournament_id ORDER BY d.division_level";
        Map<String, List<Map<String, Object>>> divisions = new LinkedHashMap<>();
        try (Connection conn = new DBContext().getConnection();
             PreparedStatement statement = conn.prepareStatement(sql)) {
            statement.setString(1, seriesId);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    divisions.computeIfAbsent(rows.getString("season_id"), ignored -> new ArrayList<>())
                            .add(Map.of("level", rows.getInt("division_level"), "legs", rows.getInt("round_robin_legs"),
                                    "teamCount", rows.getInt("team_count"),
                                    "tournamentId", java.util.Objects.toString(rows.getString("tournament_id"), "")));
                }
            }
        }
        return divisions;
    }

    public Setup getFirstSetup(String seriesId) throws Exception {
        return getSetup(seriesId, 1, true);
    }

    public Setup getSetup(String seriesId, int number, boolean draftOnly) throws Exception {
        String sql = "SELECT s.name, s.has_special_cup, d.division_level, d.round_robin_legs, "
                + "d.exchange_slots_below, e.team_key FROM dbo.seasons s "
                + "LEFT JOIN dbo.league_season_divisions d ON d.season_id = s.id "
                + "LEFT JOIN dbo.league_season_entries e ON e.season_id = d.season_id AND e.division_level = d.division_level "
                + "WHERE s.series_id = ? AND s.season_number = ? "
                + (draftOnly ? "AND s.status = 'DRAFT' " : "") + "ORDER BY d.division_level";
        try (Connection conn = new DBContext().getConnection();
             PreparedStatement query = conn.prepareStatement(sql)) {
            query.setString(1, seriesId);
            query.setInt(2, number);
            try (ResultSet rows = query.executeQuery()) {
                if (!rows.next()) return null;
                List<Integer> legs = new ArrayList<>();
                List<Integer> slots = new ArrayList<>();
                Map<String, Integer> assignments = new java.util.HashMap<>();
                Setup setup = new Setup(rows.getNString(1), rows.getBoolean(2), legs, slots, assignments);
                int last = 0;
                do {
                    int level = rows.getInt(3);
                    if (level > last) {
                        legs.add(rows.getInt(4));
                        slots.add(rows.getInt(5));
                        last = level;
                    }
                    String key = rows.getNString(6);
                    if (key != null) assignments.put(key, level);
                } while (rows.next());
                return setup;
            }
        }
    }

    public void openSeason(String seriesId, String seasonId) throws Exception {
        try (Connection conn = new DBContext().getConnection()) {
            conn.setAutoCommit(false);
            try {
                String name;
                int number;
                boolean cup;
                try (PreparedStatement lock = conn.prepareStatement(
                        "SELECT name, season_number, status, has_special_cup FROM dbo.seasons WITH (UPDLOCK, HOLDLOCK) "
                        + "WHERE id = ? AND series_id = ?")) {
                    lock.setString(1, seasonId);
                    lock.setString(2, seriesId);
                    try (ResultSet row = lock.executeQuery()) {
                        if (!row.next()) throw new IllegalArgumentException("Không tìm thấy mùa giải.");
                        String status = row.getString("status");
                        if ("ACTIVE".equals(status)) {
                            conn.commit();
                            return;
                        }
                        if (!"DRAFT".equals(status)) throw new IllegalArgumentException("Mùa này không thể mở.");
                        name = row.getNString("name");
                        number = row.getInt("season_number");
                        cup = row.getBoolean("has_special_cup");
                    }
                }
                Map<Integer, Integer> divisions = new LinkedHashMap<>();
                Map<Integer, List<Entry>> teams = new LinkedHashMap<>();
                try (PreparedStatement select = conn.prepareStatement(
                        "SELECT d.division_level, d.round_robin_legs, e.team_name, e.team_key "
                        + "FROM dbo.league_season_divisions d LEFT JOIN dbo.league_season_entries e "
                        + "ON e.season_id=d.season_id AND e.division_level=d.division_level "
                        + "WHERE d.season_id=? ORDER BY d.division_level, e.team_name, e.team_key")) {
                    select.setString(1, seasonId);
                    try (ResultSet rows = select.executeQuery()) {
                        while (rows.next()) {
                            int level = rows.getInt(1);
                            divisions.put(level, rows.getInt(2));
                            List<Entry> entries = teams.computeIfAbsent(level, unused -> new ArrayList<>());
                            String team = rows.getNString(3);
                            if (team != null) entries.add(new Entry(team, rows.getNString(4)));
                        }
                    }
                }
                if (divisions.isEmpty()) throw new IllegalArgumentException("Hãy thiết lập các hạng trước khi mở mùa.");
                List<Entry> allTeams = new ArrayList<>();
                try (PreparedStatement link = conn.prepareStatement(
                        "UPDATE dbo.league_season_divisions SET tournament_id=? WHERE season_id=? AND division_level=?")) {
                    for (Map.Entry<Integer, Integer> division : divisions.entrySet()) {
                        int level = division.getKey();
                        int legs = division.getValue();
                        List<Entry> entries = teams.get(level);
                        if (legs < 1 || legs > 10 || entries.size() < 2) {
                            throw new IllegalArgumentException("Hạng " + level + " cần ít nhất 2 đội và từ 1 đến 10 lượt.");
                        }
                        String label = "Hạng " + level;
                        String id = createCompetition(conn, seriesId, name, number, level, label,
                                "ROUND_ROBIN", legs, entries);
                        link.setString(1, id);
                        link.setString(2, seasonId);
                        link.setInt(3, level);
                        link.executeUpdate();
                        allTeams.addAll(entries);
                    }
                }
                String cupId = cup ? createCompetition(conn, seriesId, name, number, 0, "Cúp SE",
                        "SINGLE_ELIMINATION", 1, allTeams) : null;
                try (PreparedStatement activate = conn.prepareStatement(
                        "UPDATE dbo.seasons SET status='ACTIVE', special_cup_tournament_id=? WHERE id=?")) {
                    activate.setString(1, cupId);
                    activate.setString(2, seasonId);
                    activate.executeUpdate();
                }
                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                throw e;
            }
        }
        SeriesDAO.clearSeriesCaches();
        TournamentDAO.clearTournamentCaches();
    }

    private String createCompetition(Connection conn, String seriesId, String seasonName, int seasonNumber,
            int level, String label, String format, int legs, List<Entry> entries) throws Exception {
        String id = "L_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        String stageId = id + "_S1";
        try (PreparedStatement tournament = conn.prepareStatement(
                "INSERT dbo.tournaments (id, series_id, name, tournament_type, series_event_type, division_level, "
                + "season_number, division_name, advancing_seats_count, status, multi_stage_config) "
                + "VALUES (?, ?, LEFT(?, 255 - LEN(?)) + ?, 'SINGLE_STAGE', 'NONE', ?, ?, ?, 0, 'ONGOING', ?)");
             PreparedStatement stage = conn.prepareStatement(
                     "INSERT dbo.tournament_stages (id, tournament_id, stage_order, stage_name, format) VALUES (?, ?, 1, ?, ?)");
             PreparedStatement team = conn.prepareStatement(
                     "INSERT dbo.teams (id, tournament_id, raw_name, normalized_name, original_seed, status) "
                     + "VALUES (?, ?, ?, ?, ?, 'ACTIVE')")) {
            String suffix = " · " + label;
            tournament.setString(1, id);
            tournament.setString(2, seriesId);
            tournament.setNString(3, seasonName);
            tournament.setNString(4, suffix);
            tournament.setNString(5, suffix);
            tournament.setInt(6, level);
            tournament.setInt(7, seasonNumber);
            tournament.setNString(8, label);
            tournament.setString(9, "{\"legsCount\":" + legs + "}");
            tournament.executeUpdate();
            stage.setString(1, stageId);
            stage.setString(2, id);
            stage.setNString(3, label);
            stage.setString(4, format);
            stage.executeUpdate();
            int seed = 0;
            for (Entry entry : entries) {
                team.setString(1, id + "_T" + ++seed);
                team.setString(2, id);
                team.setNString(3, entry.name());
                team.setNString(4, entry.key());
                team.setInt(5, seed);
                team.addBatch();
            }
            team.executeBatch();
        }
        return id;
    }

    public boolean saveFirst(String seriesId, boolean editing, String name, boolean cup, int[] legs, int[] slots,
            Map<String, Integer> teams) throws Exception {
        return saveSetup(seriesId, null, 1, editing, name, cup, legs, slots, teams, Set.of());
    }

    public Set<String> withdrawals(String seasonId) throws Exception {
        Set<String> keys = new HashSet<>();
        try (Connection conn = new DBContext().getConnection();
             PreparedStatement query = conn.prepareStatement("SELECT team_key FROM dbo.league_season_withdrawals WHERE season_id=?")) {
            query.setString(1, seasonId);
            try (ResultSet rows = query.executeQuery()) {
                while (rows.next()) keys.add(rows.getNString(1));
            }
        }
        return keys;
    }

    public boolean saveSetup(String seriesId, String latestId, int number, boolean editing, String name, boolean cup,
            int[] legs, int[] slots, Map<String, Integer> teams, Set<String> withdrawals) throws Exception {
        try (Connection conn = new DBContext().getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement lock = conn.prepareStatement(
                    "SELECT id FROM dbo.series WITH (UPDLOCK, HOLDLOCK) WHERE id = ? AND ranking_model = 'LEAGUE_SYSTEM'");
                 PreparedStatement existing = conn.prepareStatement(
                         "SELECT id, status, season_number FROM dbo.seasons WITH (UPDLOCK, HOLDLOCK) WHERE series_id = ? ORDER BY season_number DESC");
                 PreparedStatement season = conn.prepareStatement(
                         "INSERT dbo.seasons (id, series_id, season_number, name, status, has_special_cup) VALUES (?, ?, ?, ?, 'DRAFT', ?)");
                 PreparedStatement division = conn.prepareStatement(
                         "INSERT dbo.league_season_divisions (season_id, division_level, round_robin_legs, exchange_slots_below) VALUES (?, ?, ?, ?)");
                 PreparedStatement entry = conn.prepareStatement(
                         "INSERT dbo.league_season_entries (season_id, team_key, team_name, division_level) "
                         + "SELECT ?, team_key, team_name, ? FROM dbo.league_series_teams WITH (HOLDLOCK) WHERE series_id = ? AND team_key = ?")) {
                lock.setString(1, seriesId);
                try (ResultSet rows = lock.executeQuery()) {
                    if (!rows.next()) {
                        conn.rollback();
                        return false;
                    }
                }
                existing.setString(1, seriesId);
                String id = null;
                try (ResultSet rows = existing.executeQuery()) {
                    if (rows.next()) {
                        String actualId = rows.getString(1);
                        String status = rows.getString(2);
                        int actualNumber = rows.getInt(3);
                        boolean valid = editing
                                ? "DRAFT".equals(status) && actualNumber == number && (latestId == null || latestId.equals(actualId))
                                : "COMPLETED".equals(status) && actualNumber == number - 1 && actualId.equals(latestId);
                        if (!valid) {
                            conn.rollback();
                            return false;
                        }
                        if (editing) id = actualId;
                    } else if (editing || number != 1) {
                        conn.rollback();
                        return false;
                    }
                }
                if (id == null) {
                    if (editing) {
                        conn.rollback();
                        return false;
                    }
                    id = UUID.randomUUID().toString();
                    season.setString(1, id);
                    season.setString(2, seriesId);
                    season.setInt(3, number);
                    season.setNString(4, name);
                    season.setBoolean(5, cup);
                    season.executeUpdate();
                } else {
                    try (PreparedStatement update = conn.prepareStatement("UPDATE dbo.seasons SET name = ?, has_special_cup = ? WHERE id = ?");
                         PreparedStatement clear = conn.prepareStatement("DELETE FROM dbo.league_season_divisions WHERE season_id = ?")) {
                        update.setNString(1, name);
                        update.setBoolean(2, cup);
                        update.setString(3, id);
                        update.executeUpdate();
                        clear.setString(1, id);
                        clear.executeUpdate();
                    }
                }
                for (int i = 0; i < legs.length; i++) {
                    division.setString(1, id);
                    division.setInt(2, i + 1);
                    division.setInt(3, legs[i]);
                    division.setInt(4, slots[i]);
                    division.addBatch();
                }
                division.executeBatch();
                for (Map.Entry<String, Integer> team : teams.entrySet()) {
                    entry.setString(1, id);
                    entry.setInt(2, team.getValue());
                    entry.setString(3, seriesId);
                    entry.setNString(4, LeagueSeriesTeamDAO.teamKey(team.getKey()));
                    if (entry.executeUpdate() != 1) {
                        conn.rollback();
                        return false;
                    }
                }
                if (number > 1) {
                    try (PreparedStatement clear = conn.prepareStatement("DELETE FROM dbo.league_season_withdrawals WHERE season_id=?");
                         PreparedStatement insert = conn.prepareStatement("INSERT dbo.league_season_withdrawals (season_id,team_key) VALUES (?,?)")) {
                        clear.setString(1, id);
                        clear.executeUpdate();
                        for (String key : withdrawals) {
                            insert.setString(1, id);
                            insert.setNString(2, key);
                            insert.addBatch();
                        }
                        insert.executeBatch();
                    }
                }
                conn.commit();
                return true;
            } catch (Exception e) {
                conn.rollback();
                throw e;
            }
        }
    }
}
