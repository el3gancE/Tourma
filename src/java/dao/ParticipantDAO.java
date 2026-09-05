package dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    public boolean addTeamsToTournament(String tournamentId, List<String> teamNames) {
        if (tournamentId == null || teamNames == null || teamNames.isEmpty()) {
            return false;
        }
        List<Team> existing = getTeamsByTournamentId(tournamentId);
        int currentCount = existing.size();

        String insertSql = "INSERT INTO teams (id, tournament_id, raw_name, normalized_name, original_seed, status) VALUES (?, ?, ?, ?, ?, ?)";
        DBContext db = new DBContext();

        try (Connection conn = db.getConnection();
             PreparedStatement insertPs = conn.prepareStatement(insertSql)) {

            int seed = currentCount + 1;
            int addedCount = 0;
            for (String rawName : teamNames) {
                if (rawName == null || rawName.trim().isEmpty()) continue;
                String clean = rawName.trim();

                boolean exists = false;
                for (Team t : existing) {
                    if (t.getRawName() != null && t.getRawName().equalsIgnoreCase(clean)) {
                        exists = true;
                        break;
                    }
                }
                if (!exists) {
                    String teamId = "TM_" + UUID.randomUUID().toString().substring(0, 8);
                    insertPs.setString(1, teamId);
                    insertPs.setString(2, tournamentId);
                    insertPs.setString(3, clean);
                    insertPs.setString(4, clean.toLowerCase());
                    insertPs.setInt(5, seed++);
                    insertPs.setString(6, "ACTIVE");
                    insertPs.addBatch();
                    addedCount++;
                }
            }
            if (addedCount > 0) {
                insertPs.executeBatch();
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    public Map<String, Integer> getTournamentPlacements(String tournamentId) {
        Map<String, Integer> placementMap = new HashMap<>();
        if (tournamentId == null || tournamentId.trim().isEmpty()) return placementMap;

        Map<String, String> idToNameMap = new HashMap<>();
        List<Team> teams = getTeamsByTournamentId(tournamentId);
        if (teams != null) {
            for (Team t : teams) {
                if (t.getId() != null && t.getRawName() != null) {
                    idToNameMap.put(t.getId(), t.getRawName().trim().toLowerCase());
                }
            }
        }

        DBContext db = new DBContext();
        String sql = "SELECT * FROM matches WHERE tournament_id = ? ORDER BY round_number DESC";

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tournamentId);
            try (ResultSet rs = ps.executeQuery()) {
                int maxRound = -1;
                while (rs.next()) {
                    int rNum = rs.getInt("round_number");
                    String t1 = rs.getString("team1_id");
                    String t2 = rs.getString("team2_id");
                    
                    String winner = null;
                    try { winner = rs.getString("winner_id"); } catch (Exception ignore) {}
                    if (winner == null) {
                        try { winner = rs.getString("winner_team_id"); } catch (Exception ignore) {}
                    }

                    int s1 = -1, s2 = -1;
                    try { s1 = rs.getInt("score1"); if (rs.wasNull()) s1 = -1; } catch (Exception ignore) {}
                    if (s1 == -1) {
                        try { s1 = rs.getInt("team1_score"); if (rs.wasNull()) s1 = -1; } catch (Exception ignore) {}
                    }
                    try { s2 = rs.getInt("score2"); if (rs.wasNull()) s2 = -1; } catch (Exception ignore) {}
                    if (s2 == -1) {
                        try { s2 = rs.getInt("team2_score"); if (rs.wasNull()) s2 = -1; } catch (Exception ignore) {}
                    }

                    if (winner == null && s1 >= 0 && s2 >= 0 && s1 != s2) {
                        winner = (s1 > s2) ? t1 : t2;
                    }

                    if (winner == null) {
                        continue;
                    }

                    if (maxRound == -1) {
                        maxRound = rNum;
                    }

                    if (rNum == maxRound) {
                        if (!placementMap.containsKey(winner)) {
                            placementMap.put(winner, 1);
                            if (idToNameMap.containsKey(winner)) placementMap.put(idToNameMap.get(winner), 1);
                        }

                        String loser = winner.equalsIgnoreCase(t1) ? t2 : t1;
                        if (loser != null && !placementMap.containsKey(loser)) {
                            placementMap.put(loser, 2);
                            if (idToNameMap.containsKey(loser)) placementMap.put(idToNameMap.get(loser), 2);
                        }
                    } else if (rNum == maxRound - 1 && maxRound > 1) {
                        String loser = winner.equalsIgnoreCase(t1) ? t2 : t1;
                        if (loser != null && !placementMap.containsKey(loser)) {
                            placementMap.put(loser, 3);
                            if (idToNameMap.containsKey(loser)) placementMap.put(idToNameMap.get(loser), 3);
                        }
                    } else if (rNum == maxRound - 2 && maxRound > 2) {
                        String loser = winner.equalsIgnoreCase(t1) ? t2 : t1;
                        if (loser != null && !placementMap.containsKey(loser)) {
                            placementMap.put(loser, 5);
                            if (idToNameMap.containsKey(loser)) placementMap.put(idToNameMap.get(loser), 5);
                        }
                    } else if (rNum <= maxRound - 3) {
                        String loser = winner.equalsIgnoreCase(t1) ? t2 : t1;
                        if (loser != null && !placementMap.containsKey(loser)) {
                            placementMap.put(loser, 9);
                            if (idToNameMap.containsKey(loser)) placementMap.put(idToNameMap.get(loser), 9);
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return placementMap;
    }
}
