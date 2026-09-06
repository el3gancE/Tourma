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

        DBContext db = new DBContext();
        String sql = "SELECT m.*, ts.stage_order, ts.format as stage_format, " +
                     "t1.raw_name AS t1_name, t1.normalized_name AS t1_norm, " +
                     "t2.raw_name AS t2_name, t2.normalized_name AS t2_norm, " +
                     "w.raw_name AS winner_name, w.normalized_name AS winner_norm " +
                     "FROM matches m " +
                     "LEFT JOIN tournament_stages ts ON m.stage_id = ts.id " +
                     "LEFT JOIN teams t1 ON m.team1_id = t1.id " +
                     "LEFT JOIN teams t2 ON m.team2_id = t2.id " +
                     "LEFT JOIN teams w ON m.winner_id = w.id " +
                     "WHERE m.tournament_id = ? " +
                     "ORDER BY ISNULL(ts.stage_order, 1) DESC, m.round_number DESC";

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tournamentId.trim());
            try (ResultSet rs = ps.executeQuery()) {
                int maxRoundStage2 = -1;
                int highestStageOrder = 1;

                List<Map<String, Object>> rows = new ArrayList<>();
                while (rs.next()) {
                    Map<String, Object> row = new HashMap<>();
                    int stgOrder = rs.getInt("stage_order");
                    if (rs.wasNull()) stgOrder = 1;
                    if (stgOrder > highestStageOrder) highestStageOrder = stgOrder;

                    row.put("stage_order", stgOrder);
                    row.put("stage_format", rs.getString("stage_format"));
                    row.put("round_number", rs.getInt("round_number"));
                    row.put("bracket_type", rs.getString("bracket_type"));
                    row.put("team1_id", rs.getString("team1_id"));
                    row.put("team2_id", rs.getString("team2_id"));
                    row.put("t1_name", rs.getString("t1_name"));
                    row.put("t2_name", rs.getString("t2_name"));
                    row.put("winner_id", rs.getString("winner_id"));
                    row.put("winner_name", rs.getString("winner_name"));
                    row.put("score1", rs.getObject("score1"));
                    row.put("score2", rs.getObject("score2"));
                    rows.add(row);
                }

                // Process highest stage (Stage 2 in Multi-Stage, or Stage 1 in Single Stage)
                for (Map<String, Object> r : rows) {
                    int stgOrder = (int) r.get("stage_order");
                    if (stgOrder == highestStageOrder) {
                        int rNum = (int) r.get("round_number");
                        if (maxRoundStage2 == -1) maxRoundStage2 = rNum;

                        String t1 = (String) r.get("team1_id");
                        String t2 = (String) r.get("team2_id");
                        String t1Name = (String) r.get("t1_name");
                        String t2Name = (String) r.get("t2_name");
                        String winner = (String) r.get("winner_id");
                        String winnerName = (String) r.get("winner_name");

                        if (winner == null) {
                            Object s1Obj = r.get("score1");
                            Object s2Obj = r.get("score2");
                            if (s1Obj != null && s2Obj != null) {
                                int s1 = (Integer) s1Obj;
                                int s2 = (Integer) s2Obj;
                                if (s1 != s2) {
                                    winner = (s1 > s2) ? t1 : t2;
                                    winnerName = (s1 > s2) ? t1Name : t2Name;
                                }
                            }
                        }

                        if (winner == null) continue;

                        int diff = maxRoundStage2 - rNum;
                        if (diff == 0) {
                            // Champion = 1
                            if (winner != null) placementMap.putIfAbsent(winner, 1);
                            if (winnerName != null) {
                                placementMap.putIfAbsent(winnerName.trim().toLowerCase(), 1);
                            }

                            // Runner-up = 2
                            String loser = winner.equalsIgnoreCase(t1) ? t2 : t1;
                            String loserName = winner.equalsIgnoreCase(t1) ? t2Name : t1Name;
                            if (loser != null) placementMap.putIfAbsent(loser, 2);
                            if (loserName != null) {
                                placementMap.putIfAbsent(loserName.trim().toLowerCase(), 2);
                            }
                        } else if (diff >= 1) {
                            String loser = winner.equalsIgnoreCase(t1) ? t2 : t1;
                            String loserName = winner.equalsIgnoreCase(t1) ? t2Name : t1Name;
                            int pos = (int) Math.pow(2, diff) + 1;
                            if (loser != null) placementMap.putIfAbsent(loser, pos);
                            if (loserName != null) {
                                placementMap.putIfAbsent(loserName.trim().toLowerCase(), pos);
                            }
                        }
                    }
                }

                // Find max LB round in Stage 1 if Stage 1 has LB matches
                int maxLbRoundStage1 = 0;
                for (Map<String, Object> r : rows) {
                    int stgOrder = (int) r.get("stage_order");
                    if (stgOrder < highestStageOrder) {
                        String bType = (String) r.get("bracket_type");
                        if ("LOSER_BRACKET".equalsIgnoreCase(bType) || "LB".equalsIgnoreCase(bType)) {
                            int rNum = (int) r.get("round_number");
                            if (rNum > maxLbRoundStage1) maxLbRoundStage1 = rNum;
                        }
                    }
                }

                // If Multi-Stage (highestStageOrder > 1), also process Stage 1 eliminated teams
                if (highestStageOrder > 1) {
                    for (Map<String, Object> r : rows) {
                        int stgOrder = (int) r.get("stage_order");
                        if (stgOrder < highestStageOrder) {
                            int rNum = (int) r.get("round_number");
                            String bType = (String) r.get("bracket_type");
                            String t1 = (String) r.get("team1_id");
                            String t2 = (String) r.get("team2_id");
                            String t1Name = (String) r.get("t1_name");
                            String t2Name = (String) r.get("t2_name");
                            String winner = (String) r.get("winner_id");
                            String winnerName = (String) r.get("winner_name");

                            if (winner == null) {
                                Object s1Obj = r.get("score1");
                                Object s2Obj = r.get("score2");
                                if (s1Obj != null && s2Obj != null) {
                                    int s1 = (Integer) s1Obj;
                                    int s2 = (Integer) s2Obj;
                                    if (s1 != s2) {
                                        winner = (s1 > s2) ? t1 : t2;
                                        winnerName = (s1 > s2) ? t1Name : t2Name;
                                    }
                                }
                            }
                            if (winner == null) continue;

                            String loser = winner.equalsIgnoreCase(t1) ? t2 : t1;
                            String loserName = winner.equalsIgnoreCase(t1) ? t2Name : t1Name;

                            // If this was a DOUBLE_ELIMINATION stage with LB matches:
                            if (maxLbRoundStage1 > 0) {
                                // Loser of WINNER_BRACKET drops to LB, NOT eliminated!
                                if (!"LOSER_BRACKET".equalsIgnoreCase(bType) && !"LB".equalsIgnoreCase(bType)) {
                                    continue;
                                }
                                // Only losers of LOSER_BRACKET are eliminated in Stage 1 DE:
                                if (loser != null || loserName != null) {
                                    String lKey = (loserName != null) ? loserName.trim().toLowerCase() : null;
                                    // If rNum == maxLbRoundStage1 -> Loser's Qualification (pos 65)
                                    // If rNum == maxLbRoundStage1 - 1 -> LB Round 1 (pos 97)
                                    // If rNum <= maxLbRoundStage1 - 2 -> LB earlier rounds (pos 129)
                                    int pos = 65;
                                    if (rNum == maxLbRoundStage1) {
                                        pos = 65;
                                    } else if (rNum == maxLbRoundStage1 - 1) {
                                        pos = 97;
                                    } else {
                                        pos = 129;
                                    }
                                    if (loser != null) placementMap.putIfAbsent(loser, pos);
                                    if (lKey != null) placementMap.putIfAbsent(lKey, pos);
                                }
                            } else {
                                // Stage 1 SE or other format
                                if (loser != null || loserName != null) {
                                    String lKey = (loserName != null) ? loserName.trim().toLowerCase() : null;
                                    int pos = 65;
                                    if (loser != null) placementMap.putIfAbsent(loser, pos);
                                    if (lKey != null) placementMap.putIfAbsent(lKey, pos);
                                }
                            }
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
