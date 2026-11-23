package dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public class LeagueSeriesTeamDAO {
    public int count(String seriesId) throws Exception {
        try (Connection conn = new DBContext().getConnection();
             PreparedStatement statement = conn.prepareStatement(
                     "SELECT COUNT(*) FROM dbo.league_series_teams WHERE series_id = ?")) {
            statement.setString(1, seriesId);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? rows.getInt(1) : 0;
            }
        }
    }


    public static String teamKey(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    public Map<String, Date> list(String seriesId) throws Exception {
        return list(seriesId, false);
    }

    public Map<String, Date> listInRegistrationOrder(String seriesId) throws Exception {
        return list(seriesId, true);
    }

    private Map<String, Date> list(String seriesId, boolean orderByRegistration) throws Exception {
        String order = orderByRegistration ? "registered_at, registration_order" : "team_name";
        String sql = "SELECT team_name, registered_at FROM dbo.league_series_teams WHERE series_id = ? ORDER BY " + order;
        Map<String, Date> teams = new LinkedHashMap<>();
        try (Connection conn = new DBContext().getConnection();
             PreparedStatement statement = conn.prepareStatement(sql)) {
            statement.setString(1, seriesId);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    teams.put(rows.getNString(1), rows.getTimestamp(2));
                }
            }
        }
        return teams;
    }

    public void add(String seriesId, Map<String, Date> teams) throws Exception {
        if (teams.isEmpty()) {
            return;
        }
        String sql = "INSERT INTO dbo.league_series_teams (series_id, team_key, team_name, registered_at) "
                + "SELECT ?, ?, ?, ? WHERE NOT EXISTS (SELECT 1 FROM dbo.league_series_teams "
                + "WITH (UPDLOCK, HOLDLOCK) WHERE series_id = ? AND team_key = ?)";
        try (Connection conn = new DBContext().getConnection();
             PreparedStatement statement = conn.prepareStatement(sql)) {
            conn.setAutoCommit(false);
            try {
                for (Map.Entry<String, Date> team : teams.entrySet()) {
                    String key = teamKey(team.getKey());
                    statement.setString(1, seriesId);
                    statement.setNString(2, key);
                    statement.setNString(3, team.getKey());
                    Date date = team.getValue();
                    statement.setTimestamp(4, date == null ? null : new Timestamp(date.getTime()));
                    statement.setString(5, seriesId);
                    statement.setNString(6, key);
                    statement.addBatch();
                }
                statement.executeBatch();
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        }
    }

    public void remove(String seriesId, String name) throws Exception {
        String sql = "DELETE FROM dbo.league_series_teams WHERE series_id = ? AND team_key = ?";
        try (Connection conn = new DBContext().getConnection();
             PreparedStatement statement = conn.prepareStatement(sql)) {
            statement.setString(1, seriesId);
            statement.setNString(2, teamKey(name));
            statement.executeUpdate();
        }
    }
}
