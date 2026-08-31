package dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import model.Team;

/**
 * Participant / Team Data Access Object for querying & persisting tournament teams
 */
public class ParticipantDAO {

    public List<Team> getTeamsByTournamentId(String tournamentId) {
        List<Team> list = new ArrayList<>();
        String sql = "SELECT * FROM teams WHERE tournament_id = ? ORDER BY original_seed ASC";
        DBContext db = new DBContext();

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, tournamentId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Team t = new Team(
                        rs.getString("id"),
                        rs.getString("tournament_id"),
                        rs.getString("partner_participant_id"),
                        rs.getString("raw_name"),
                        rs.getString("normalized_name"),
                        rs.getInt("original_seed"),
                        rs.getString("current_stage_id"),
                        rs.getString("status"),
                        rs.getTimestamp("created_at")
                    );
                    list.add(t);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    public List<Team> getTeamsByTournamentId(int tournamentId) {
        return getTeamsByTournamentId(String.valueOf(tournamentId));
    }

    public boolean saveTournamentTeams(String tournamentId, List<String> teamNames) {
        if (tournamentId == null || teamNames == null || teamNames.isEmpty()) {
            return false;
        }

        DBContext db = new DBContext();
        String deleteSql = "DELETE FROM teams WHERE tournament_id = ?";
        String insertSql = "INSERT INTO teams (id, tournament_id, raw_name, normalized_name, original_seed, status) " +
                           "VALUES (?, ?, ?, ?, ?, ?)";

        try (Connection conn = db.getConnection()) {
            conn.setAutoCommit(false);

            // 1. Delete existing team records for this tournament
            try (PreparedStatement deletePs = conn.prepareStatement(deleteSql)) {
                deletePs.setString(1, tournamentId);
                deletePs.executeUpdate();
            }

            // 2. Insert new team records with original seeds
            try (PreparedStatement insertPs = conn.prepareStatement(insertSql)) {
                for (int i = 0; i < teamNames.size(); i++) {
                    String rawName = teamNames.get(i).trim();
                    if (rawName.isEmpty()) continue;

                    String teamId = "TM_" + UUID.randomUUID().toString().substring(0, 8);
                    String normalized = rawName.toLowerCase();
                    int seedNum = i + 1;

                    insertPs.setString(1, teamId);
                    insertPs.setString(2, tournamentId);
                    insertPs.setString(3, rawName);
                    insertPs.setString(4, normalized);
                    insertPs.setInt(5, seedNum);
                    insertPs.setString(6, "ACTIVE");
                    insertPs.addBatch();
                }
                insertPs.executeBatch();
            }

            conn.commit();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    public boolean deleteTeamById(String teamId) {
        String sql = "DELETE FROM teams WHERE id = ?";
        DBContext db = new DBContext();

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, teamId);
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }
}
