package dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import model.Match;
import model.Team;
import service.JsonParser;
import service.MatchPersistenceService;

/**
 * Clean, high-performance DAO for Round Robin Tournament Format.
 * 100% Database-Driven with zero regex overhead and fast query execution.
 */
public class RoundRobinDAO extends DBContext {

    public static class RRMatchDTO {
        public int matchId;
        public int roundNumber;
        public int matchNumber;
        public String team1Name;
        public Integer team1Seed;
        public Integer team1Score;
        public String team2Name;
        public Integer team2Seed;
        public Integer team2Score;
        public String winnerId;
        public boolean isBye;
        public String status;
    }

    public List<Match> getMatchesByTournamentId(String tournamentId) {
        List<Match> list = new ArrayList<>();
        if (tournamentId == null || tournamentId.trim().isEmpty()) return list;

        String sql = "SELECT m.*, "
                + "t1.raw_name AS t1_name, t1.original_seed AS t1_seed, "
                + "t2.raw_name AS t2_name, t2.original_seed AS t2_seed, "
                + "tw.raw_name AS winner_name "
                + "FROM matches m "
                + "LEFT JOIN teams t1 ON m.team1_id = t1.id "
                + "LEFT JOIN teams t2 ON m.team2_id = t2.id "
                + "LEFT JOIN teams tw ON m.winner_id = tw.id "
                + "WHERE m.tournament_id = ? "
                + "ORDER BY m.round_number ASC, m.id ASC";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tournamentId);
            try (ResultSet rs = ps.executeQuery()) {
                int seq = 1;
                while (rs.next()) {
                    Match m = new Match();
                    String rawId = rs.getString("id");
                    int numId = parseNumericMatchId(rawId, seq++);
                    m.setId(numId);
                    m.setRoundNumber(rs.getInt("round_number"));
                    m.setMatchNumber(numId);
                    m.setBracketType(rs.getString("bracket_type"));

                    m.setTeam1Name(rs.getString("t1_name"));
                    int s1Seed = rs.getInt("t1_seed");
                    if (!rs.wasNull()) m.setTeam1Seed(s1Seed);
                    int s1 = rs.getInt("score1");
                    if (!rs.wasNull()) m.setTeam1Score(s1);

                    m.setTeam2Name(rs.getString("t2_name"));
                    int s2Seed = rs.getInt("t2_seed");
                    if (!rs.wasNull()) m.setTeam2Seed(s2Seed);
                    int s2 = rs.getInt("score2");
                    if (!rs.wasNull()) m.setTeam2Score(s2);

                    String winnerId = rs.getString("winner_id");
                    if (winnerId != null) {
                        if (winnerId.equals(rs.getString("team1_id"))) m.setWinnerTeamId(1);
                        else if (winnerId.equals(rs.getString("team2_id"))) m.setWinnerTeamId(2);
                    }

                    String st = rs.getString("status");
                    m.setStatus("FINISHED".equalsIgnoreCase(st) ? "COMPLETED" : (st != null ? st : "SCHEDULED"));
                    list.add(m);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    public String getMatchesJsonForFrontend(String tournamentId, int stageOrder) {
        if (tournamentId == null || tournamentId.trim().isEmpty()) return "[]";

        String sql = "SELECT m.*, "
                + "t1.raw_name AS t1_name, t1.original_seed AS t1_seed, "
                + "t2.raw_name AS t2_name, t2.original_seed AS t2_seed, "
                + "tw.raw_name AS winner_name "
                + "FROM matches m "
                + "LEFT JOIN tournament_stages s ON m.stage_id = s.id "
                + "LEFT JOIN teams t1 ON m.team1_id = t1.id "
                + "LEFT JOIN teams t2 ON m.team2_id = t2.id "
                + "LEFT JOIN teams tw ON m.winner_id = tw.id "
                + "WHERE m.tournament_id = ? AND (s.stage_order = ? OR (s.stage_order IS NULL AND ? = 1) OR m.stage_id LIKE '%_S' + CAST(? AS VARCHAR) + '_%') "
                + "ORDER BY m.round_number ASC, LEN(m.id) ASC, m.id ASC";

        StringBuilder sb = new StringBuilder("[");
        int count = 0;

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tournamentId);
            ps.setInt(2, stageOrder);
            ps.setInt(3, stageOrder);
            ps.setInt(4, stageOrder);
            try (ResultSet rs = ps.executeQuery()) {
                int seq = 1;
                while (rs.next()) {
                    if (count > 0) sb.append(",");
                    count++;

                    String rawId = rs.getString("id");
                    int matchId = parseNumericMatchId(rawId, seq++);
                    int roundNumber = rs.getInt("round_number");

                    String t1Name = rs.getString("t1_name");
                    int t1SeedVal = rs.getInt("t1_seed");
                    String t1Seed = rs.wasNull() ? "" : String.valueOf(t1SeedVal);
                    int s1Val = rs.getInt("score1");
                    String s1 = rs.wasNull() ? "" : String.valueOf(s1Val);

                    String t2Name = rs.getString("t2_name");
                    int t2SeedVal = rs.getInt("t2_seed");
                    String t2Seed = rs.wasNull() ? "" : String.valueOf(t2SeedVal);
                    int s2Val = rs.getInt("score2");
                    String s2 = rs.wasNull() ? "" : String.valueOf(s2Val);

                    String winnerIdCol = rs.getString("winner_id");
                    String winnerSlot = "";
                    if (winnerIdCol != null) {
                        if (winnerIdCol.equals(rs.getString("team1_id"))) winnerSlot = "team1";
                        else if (winnerIdCol.equals(rs.getString("team2_id"))) winnerSlot = "team2";
                    }

                    boolean isBye = rs.getBoolean("is_bye");
                    String status = rs.getString("status");
                    if ("FINISHED".equalsIgnoreCase(status)) status = "COMPLETED";

                    sb.append("{")
                      .append("\"matchId\":").append(matchId).append(",")
                      .append("\"id\":").append(matchId).append(",")
                      .append("\"roundNumber\":").append(roundNumber).append(",")
                      .append("\"matchNumber\":").append(matchId).append(",")
                      .append("\"team1\":{")
                      .append("\"name\":\"").append(escapeJson(t1Name != null ? t1Name : "")).append("\",")
                      .append("\"seed\":\"").append(escapeJson(t1Seed)).append("\",")
                      .append("\"score\":\"").append(escapeJson(s1)).append("\"")
                      .append("},")
                      .append("\"team2\":{")
                      .append("\"name\":\"").append(escapeJson(t2Name != null ? t2Name : "")).append("\",")
                      .append("\"seed\":\"").append(escapeJson(t2Seed)).append("\",")
                      .append("\"score\":\"").append(escapeJson(s2)).append("\"")
                      .append("},")
                      .append("\"winnerId\":").append(winnerSlot.isEmpty() ? "null" : "\"" + winnerSlot + "\"").append(",")
                      .append("\"isBye\":").append(isBye).append(",")
                      .append("\"status\":\"").append(escapeJson(status != null ? status : "SCHEDULED")).append("\"")
                      .append("}");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        sb.append("]");
        return sb.toString();
    }

    public boolean syncRoundRobinMatches(String tournamentId, int stageOrder, String matchesJson) {
        if (tournamentId == null || matchesJson == null) return false;
        List<Object> list = JsonParser.parseList(matchesJson);
        if (list == null || list.isEmpty()) return false;

        // Step 1: Resolve or create the stage_id for this tournament + stageOrder
        String stageId = resolveOrCreateStageId(tournamentId, stageOrder);
        if (stageId == null) {
            // Fallback: pure UPDATE path via MatchPersistenceService
            List<Map<String, Object>> mapList = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> m = (Map<String, Object>) item;
                    mapList.add(m);
                }
            }
            return MatchPersistenceService.getInstance().syncMatchesList(tournamentId, mapList) > 0;
        }

        // Step 2: Resolve team name → DB id lookup
        ParticipantDAO pDao = new ParticipantDAO();
        List<Team> teams = pDao.getTeamsByTournamentId(tournamentId);
        Map<String, String> teamLookup = new HashMap<>();
        if (teams != null) {
            for (Team tm : teams) {
                if (tm.getId() != null) teamLookup.put(tm.getId().toLowerCase().trim(), tm.getId());
                if (tm.getRawName() != null) teamLookup.put(tm.getRawName().toLowerCase().trim(), tm.getId());
                if (tm.getNormalizedName() != null) teamLookup.put(tm.getNormalizedName().toLowerCase().trim(), tm.getId());
            }
        }

        // Step 3: UPSERT each match using MERGE (T-SQL)
        // match_code pattern for RR: "RR_<tournamentId>_S<stageOrder>_M<matchId>"
        String mergeSql =
            "MERGE matches AS tgt " +
            "USING (SELECT ? AS tournament_id, ? AS stage_id, ? AS match_code, ? AS round_number) AS src " +
            "    ON tgt.tournament_id = src.tournament_id AND tgt.match_code = src.match_code " +
            "WHEN MATCHED THEN " +
            "    UPDATE SET tgt.team1_id = COALESCE(?, tgt.team1_id), " +
            "               tgt.team2_id = COALESCE(?, tgt.team2_id), " +
            "               tgt.score1 = ?, tgt.score2 = ?, tgt.winner_id = ?, " +
            "               tgt.status = ? " +
            "WHEN NOT MATCHED THEN " +
            "    INSERT (id, tournament_id, stage_id, round_number, match_code, bracket_type, " +
            "            team1_id, team2_id, score1, score2, winner_id, is_bye, status) " +
            "    VALUES (?, src.tournament_id, src.stage_id, src.round_number, src.match_code, 'MAIN', " +
            "            ?, ?, ?, ?, ?, 0, ?);";

        int count = 0;
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(mergeSql)) {
                for (Object item : list) {
                    if (!(item instanceof Map)) continue;
                    @SuppressWarnings("unchecked")
                    Map<String, Object> m = (Map<String, Object>) item;

                    Object rawId = m.get("matchId");
                    if (rawId == null) rawId = m.get("id");
                    if (rawId == null) continue;
                    String matchIdStr = String.valueOf(rawId).trim();

                    int roundNumber = 1;
                    Object rn = m.get("roundNumber");
                    if (rn == null) rn = m.get("round");
                    if (rn != null) {
                        try { roundNumber = Integer.parseInt(String.valueOf(rn).trim()); } catch (Exception ignored) {}
                    }

                    String matchCode = "RR_" + tournamentId + "_S" + stageOrder + "_M" + matchIdStr;
                    String newId = matchCode;

                    // Resolve team names
                    String t1Name = getNestedString(m, "team1", "name");
                    if (t1Name == null) t1Name = getString(m, "team1Name");
                    String t2Name = getNestedString(m, "team2", "name");
                    if (t2Name == null) t2Name = getString(m, "team2Name");

                    String t1Id = (t1Name != null) ? teamLookup.get(t1Name.toLowerCase().trim()) : null;
                    String t2Id = (t2Name != null) ? teamLookup.get(t2Name.toLowerCase().trim()) : null;

                    // Resolve scores
                    Integer s1 = getNestedInt(m, "team1", "score");
                    if (s1 == null) s1 = getIntObj(m, "team1Score");
                    Integer s2 = getNestedInt(m, "team2", "score");
                    if (s2 == null) s2 = getIntObj(m, "team2Score");

                    // Resolve winner
                    String winnerSlot = getString(m, "winnerId");
                    String winnerId = null;
                    if ("team1".equalsIgnoreCase(winnerSlot) && t1Id != null) winnerId = t1Id;
                    else if ("team2".equalsIgnoreCase(winnerSlot) && t2Id != null) winnerId = t2Id;
                    else if (s1 != null && s2 != null) {
                        if (s1 > s2 && t1Id != null) winnerId = t1Id;
                        else if (s2 > s1 && t2Id != null) winnerId = t2Id;
                    }

                    String status = (s1 != null && s2 != null) ? "FINISHED" : "PENDING";

                    // MERGE params: USING clause
                    ps.setString(1, tournamentId);
                    ps.setString(2, stageId);
                    ps.setString(3, matchCode);
                    ps.setInt(4, roundNumber);
                    // WHEN MATCHED UPDATE params
                    if (t1Id != null) ps.setString(5, t1Id); else ps.setNull(5, Types.VARCHAR);
                    if (t2Id != null) ps.setString(6, t2Id); else ps.setNull(6, Types.VARCHAR);
                    if (s1 != null) ps.setInt(7, s1); else ps.setNull(7, Types.INTEGER);
                    if (s2 != null) ps.setInt(8, s2); else ps.setNull(8, Types.INTEGER);
                    if (winnerId != null) ps.setString(9, winnerId); else ps.setNull(9, Types.VARCHAR);
                    ps.setString(10, status);
                    // WHEN NOT MATCHED INSERT params
                    ps.setString(11, newId);
                    if (t1Id != null) ps.setString(12, t1Id); else ps.setNull(12, Types.VARCHAR);
                    if (t2Id != null) ps.setString(13, t2Id); else ps.setNull(13, Types.VARCHAR);
                    if (s1 != null) ps.setInt(14, s1); else ps.setNull(14, Types.INTEGER);
                    if (s2 != null) ps.setInt(15, s2); else ps.setNull(15, Types.INTEGER);
                    if (winnerId != null) ps.setString(16, winnerId); else ps.setNull(16, Types.VARCHAR);
                    ps.setString(17, status);

                    ps.addBatch();
                    count++;
                }
                ps.executeBatch();
            }
            conn.commit();
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        return count > 0;
    }

    /**
     * Look up the stage_id for the given tournament + stageOrder from tournament_stages.
     * If none exists, creates a new ROUND_ROBIN stage entry and returns its id.
     */
    private String resolveOrCreateStageId(String tournamentId, int stageOrder) {
        if (tournamentId == null) return null;
        // Try to find existing stage
        String selectSql = "SELECT TOP 1 id FROM tournament_stages WHERE tournament_id = ? AND stage_order = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(selectSql)) {
            ps.setString(1, tournamentId);
            ps.setInt(2, stageOrder);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("id");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        // No stage found → create one
        String newStageId = tournamentId + "_S" + stageOrder + "_RR";
        String stageName = (stageOrder == 1) ? "Stage 1: Round Robin" : ("Stage " + stageOrder + ": Round Robin");
        String insertSql = "INSERT INTO tournament_stages (id, tournament_id, stage_order, stage_name, format, status) " +
                           "VALUES (?, ?, ?, ?, 'ROUND_ROBIN', 'ONGOING')";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(insertSql)) {
            ps.setString(1, newStageId);
            ps.setString(2, tournamentId);
            ps.setInt(3, stageOrder);
            ps.setString(4, stageName);
            ps.executeUpdate();
            return newStageId;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private String getString(Map<String, Object> m, String key) {
        if (m == null || !m.containsKey(key)) return null;
        Object v = m.get(key);
        return (v != null) ? String.valueOf(v).trim() : null;
    }

    @SuppressWarnings("unchecked")
    private String getNestedString(Map<String, Object> m, String outerKey, String innerKey) {
        if (m == null) return null;
        Object outer = m.get(outerKey);
        if (outer instanceof Map) {
            Object inner = ((Map<String, Object>) outer).get(innerKey);
            return (inner != null) ? String.valueOf(inner).trim() : null;
        }
        return null;
    }

    private Integer getIntObj(Map<String, Object> m, String key) {
        if (m == null || !m.containsKey(key)) return null;
        Object v = m.get(key);
        if (v == null) return null;
        try {
            if (v instanceof Number) return ((Number) v).intValue();
            String s = String.valueOf(v).trim();
            return s.isEmpty() ? null : Integer.parseInt(s);
        } catch (Exception e) { return null; }
    }

    @SuppressWarnings("unchecked")
    private Integer getNestedInt(Map<String, Object> m, String outerKey, String innerKey) {
        if (m == null) return null;
        Object outer = m.get(outerKey);
        if (outer instanceof Map) {
            Object inner = ((Map<String, Object>) outer).get(innerKey);
            if (inner == null) return null;
            try {
                if (inner instanceof Number) return ((Number) inner).intValue();
                String s = String.valueOf(inner).trim();
                return s.isEmpty() ? null : Integer.parseInt(s);
            } catch (Exception e) { return null; }
        }
        return null;
    }


    public boolean updateMatchScore(String tournamentId, int stageOrder, String matchId, Integer score1, Integer score2, String winnerFlag, String team1Name, String team2Name) {
        ParticipantDAO pDao = new ParticipantDAO();
        List<Team> teams = pDao.getTeamsByTournamentId(tournamentId);
        String winnerId = null;
        if (teams != null && winnerFlag != null) {
            String targetName = "team1".equalsIgnoreCase(winnerFlag) ? team1Name : team2Name;
            if (targetName != null) {
                for (Team t : teams) {
                    if (targetName.equalsIgnoreCase(t.getRawName()) || targetName.equalsIgnoreCase(t.getNormalizedName())) {
                        winnerId = t.getId();
                        break;
                    }
                }
            }
        }
        return MatchPersistenceService.getInstance().updateSingleMatch(tournamentId, matchId, score1, score2, winnerId);
    }

    public boolean updateMatchScore(String tournamentId, int stageOrder, int matchId, Integer score1, Integer score2, String winnerFlag, String team1Name, String team2Name) {
        return updateMatchScore(tournamentId, stageOrder, String.valueOf(matchId), score1, score2, winnerFlag, team1Name, team2Name);
    }

    public boolean updateMatchScore(String matchIdStr, String score1Str, String score2Str, String winner) {
        Integer s1 = null, s2 = null;
        try { if (score1Str != null && !score1Str.trim().isEmpty()) s1 = Integer.parseInt(score1Str.trim()); } catch (Exception ignore) {}
        try { if (score2Str != null && !score2Str.trim().isEmpty()) s2 = Integer.parseInt(score2Str.trim()); } catch (Exception ignore) {}
        return updateMatchScore(null, 1, matchIdStr, s1, s2, winner, null, null);
    }

    public boolean resetRoundRobinMatches(String tournamentId, int stageOrder) {
        if (tournamentId == null || tournamentId.trim().isEmpty()) return false;
        String sql = "UPDATE m SET m.score1 = NULL, m.score2 = NULL, m.winner_id = NULL, m.status = 'PENDING' "
                + "FROM matches m "
                + "LEFT JOIN tournament_stages s ON m.stage_id = s.id "
                + "WHERE m.tournament_id = ? AND (s.stage_order = ? OR (s.stage_order IS NULL AND ? = 1) OR m.stage_id LIKE '%_S' + CAST(? AS VARCHAR) + '_%')";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tournamentId);
            ps.setInt(2, stageOrder);
            ps.setInt(3, stageOrder);
            ps.setInt(4, stageOrder);
            return ps.executeUpdate() >= 0;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private int parseNumericMatchId(String strId, int defaultSeq) {
        if (strId == null || strId.trim().isEmpty()) return defaultSeq;
        try {
            return Integer.parseInt(strId.trim());
        } catch (Exception e) {
            String digits = strId.replaceAll("\\D+", "");
            if (!digits.isEmpty()) {
                try { return Integer.parseInt(digits); } catch (Exception ignore) {}
            }
        }
        return defaultSeq;
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }
}
