package dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import model.Match;

/**
 * Dedicated Data Access Object for Group Stage Format.
 * Manages matches partitioned into groups (A, B, C...),
 * and SQL Server 'matches' table persistence.
 */
public class GroupStageDAO extends DBContext {

    public static class GSMatchDTO {
        public String matchId;
        public String groupKey;
        public int roundNumber;
        public int matchNumber;
        public String team1Name;
        public Integer team1Score;
        public String team2Name;
        public Integer team2Score;
        public String winnerId; // "team1", "team2", or "draw"
        public String status;
    }

    /**
     * Get JSON array of Group Stage matches for frontend engine
     */
    public String getMatchesJsonForFrontend(String tournamentId, int stageOrder) {
        if (tournamentId == null || tournamentId.trim().isEmpty()) {
            return "[]";
        }

        String sql = "SELECT m.*, "
                + "t1.raw_name AS t1_name, t1.original_seed AS t1_seed, "
                + "t2.raw_name AS t2_name, t2.original_seed AS t2_seed, "
                + "tw.raw_name AS winner_name "
                + "FROM matches m "
                + "JOIN tournament_stages s ON m.stage_id = s.id "
                + "LEFT JOIN teams t1 ON m.team1_id = t1.id "
                + "LEFT JOIN teams t2 ON m.team2_id = t2.id "
                + "LEFT JOIN teams tw ON m.winner_id = tw.id "
                + "WHERE m.tournament_id = ? AND s.stage_order = ? "
                + "ORDER BY m.round_number ASC, m.id ASC";

        StringBuilder sb = new StringBuilder("[");
        int count = 0;

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tournamentId);
            ps.setInt(2, stageOrder);
            try (ResultSet rs = ps.executeQuery()) {
                int seq = 1;
                while (rs.next()) {
                    if (count > 0) sb.append(",");
                    count++;

                    String rawId = rs.getString("id");
                    int roundNumber = rs.getInt("round_number");
                    String matchCode = rs.getString("match_code");
                    if (matchCode == null) matchCode = "";

                    // Extract group key, e.g. "Bảng A - Trận #1"
                    String groupKey = "A";
                    Matcher gMatcher = Pattern.compile("Bảng\\s+([A-Za-z0-9]+)").matcher(matchCode);
                    if (gMatcher.find()) {
                        groupKey = gMatcher.group(1);
                    }

                    int matchNumber = seq;
                    Matcher numMatcher = Pattern.compile("#(\\d+)").matcher(matchCode);
                    if (numMatcher.find()) {
                        matchNumber = Integer.parseInt(numMatcher.group(1));
                    }

                    String t1Name = rs.getString("t1_name");
                    int s1Val = rs.getInt("score1");
                    String s1 = rs.wasNull() ? "" : String.valueOf(s1Val);

                    String t2Name = rs.getString("t2_name");
                    int s2Val = rs.getInt("score2");
                    String s2 = rs.wasNull() ? "" : String.valueOf(s2Val);

                    String winnerIdCol = rs.getString("winner_id");
                    String winnerSlot = "";
                    if (winnerIdCol != null) {
                        if (winnerIdCol.equals(rs.getString("team1_id"))) {
                            winnerSlot = "team1";
                        } else if (winnerIdCol.equals(rs.getString("team2_id"))) {
                            winnerSlot = "team2";
                        } else {
                            winnerSlot = "draw";
                        }
                    }

                    String status = rs.getString("status");
                    if ("FINISHED".equalsIgnoreCase(status)) {
                        status = "COMPLETED";
                    }

                    String matchId = "GS_" + groupKey + "_" + matchNumber;
                    if (rawId != null && rawId.contains("_M")) {
                        matchId = rawId.substring(rawId.indexOf("_M") + 2);
                    }

                    sb.append("{");
                    sb.append("\"matchId\":\"").append(escapeJson(matchId)).append("\",");
                    sb.append("\"groupKey\":\"").append(escapeJson(groupKey)).append("\",");
                    sb.append("\"roundNumber\":").append(roundNumber).append(",");
                    sb.append("\"matchNumber\":").append(matchNumber).append(",");
                    sb.append("\"team1\":{\"name\":\"").append(escapeJson(t1Name != null ? t1Name : "")).append("\",\"score\":\"").append(escapeJson(s1)).append("\"},");
                    sb.append("\"team2\":{\"name\":\"").append(escapeJson(t2Name != null ? t2Name : "")).append("\",\"score\":\"").append(escapeJson(s2)).append("\"},");
                    sb.append("\"winnerId\":").append(winnerSlot.isEmpty() ? "null" : "\"" + winnerSlot + "\"").append(",");
                    sb.append("\"status\":\"").append(escapeJson(status != null ? status : "SCHEDULED")).append("\"");
                    sb.append("}");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        sb.append("]");
        return (count > 0) ? sb.toString() : "[]";
    }

    /**
     * Batch Sync / Persist full Group Stage fixtures to 'matches' table
     */
    public boolean syncGroupMatches(String tournamentId, int stageOrder, String matchesDataJson) {
        List<GSMatchDTO> list = parseGSMatchJson(matchesDataJson);
        if (list.isEmpty()) {
            return false;
        }

        try (Connection conn = getConnection()) {
            String stageId = getOrCreateStageId(conn, tournamentId, stageOrder);
            Map<String, String> teamMap = getTeamNameToIdMap(conn, tournamentId);

            String mergeSql = "MERGE INTO matches AS target "
                    + "USING (SELECT ? AS id, ? AS tournament_id, ? AS stage_id, ? AS round_number, ? AS match_code, "
                    + "              ? AS bracket_type, ? AS team1_id, ? AS team2_id, ? AS score1, ? AS score2, "
                    + "              ? AS winner_id, ? AS status) AS source "
                    + "ON (target.id = source.id) "
                    + "WHEN MATCHED THEN "
                    + "    UPDATE SET "
                    + "        target.round_number = source.round_number, "
                    + "        target.match_code = source.match_code, "
                    + "        target.team1_id = source.team1_id, "
                    + "        target.team2_id = source.team2_id, "
                    + "        target.score1 = source.score1, "
                    + "        target.score2 = source.score2, "
                    + "        target.winner_id = source.winner_id, "
                    + "        target.status = source.status "
                    + "WHEN NOT MATCHED THEN "
                    + "    INSERT (id, tournament_id, stage_id, round_number, match_code, bracket_type, "
                    + "            team1_id, team2_id, score1, score2, winner_id, status) "
                    + "    VALUES (source.id, source.tournament_id, source.stage_id, source.round_number, source.match_code, source.bracket_type, "
                    + "            source.team1_id, source.team2_id, source.score1, source.score2, source.winner_id, source.status);";

            try (PreparedStatement ps = conn.prepareStatement(mergeSql)) {
                for (GSMatchDTO m : list) {
                    String matchDbId = tournamentId + "_S" + stageOrder + "_M" + m.matchId;
                    String t1Id = lookupTeamId(teamMap, m.team1Name);
                    String t2Id = lookupTeamId(teamMap, m.team2Name);
                    String winnerId = "team1".equalsIgnoreCase(m.winnerId) ? t1Id : ("team2".equalsIgnoreCase(m.winnerId) ? t2Id : null);

                    String status = ("COMPLETED".equalsIgnoreCase(m.status) || "FINISHED".equalsIgnoreCase(m.status) || (m.team1Score != null && m.team2Score != null)) ? "FINISHED" : "PENDING";
                    String matchCode = "Bảng " + (m.groupKey != null ? m.groupKey : "A") + " - Trận #" + m.matchNumber;

                    ps.setString(1, matchDbId);
                    ps.setString(2, tournamentId);
                    ps.setString(3, stageId);
                    ps.setInt(4, m.roundNumber);
                    ps.setString(5, matchCode);
                    ps.setString(6, "GROUP");
                    setNullableString(ps, 7, t1Id);
                    setNullableString(ps, 8, t2Id);
                    setNullableInt(ps, 9, m.team1Score);
                    setNullableInt(ps, 10, m.team2Score);
                    setNullableString(ps, 11, winnerId);
                    ps.setString(12, status);
                    ps.addBatch();
                }
                ps.executeBatch();
            }

            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Update score and status for a Group Stage match
     */
    public boolean updateGroupMatchScore(String tournamentId, int stageOrder, String matchId,
                                         Integer score1, Integer score2, String winnerFlag,
                                         String team1Name, String team2Name) {
        if (tournamentId == null || tournamentId.trim().isEmpty()) {
            return false;
        }

        try (Connection conn = getConnection()) {
            String stageId = getOrCreateStageId(conn, tournamentId, stageOrder);
            Map<String, String> teamMap = getTeamNameToIdMap(conn, tournamentId);
            String matchDbId = tournamentId + "_S" + stageOrder + "_M" + matchId;
            String t1Id = lookupTeamId(teamMap, team1Name);
            String t2Id = lookupTeamId(teamMap, team2Name);

            String winnerId = null;
            if ("team1".equalsIgnoreCase(winnerFlag)) {
                winnerId = t1Id;
            } else if ("team2".equalsIgnoreCase(winnerFlag)) {
                winnerId = t2Id;
            } else if (winnerFlag != null && !winnerFlag.trim().isEmpty() && !"draw".equalsIgnoreCase(winnerFlag)) {
                winnerId = lookupTeamId(teamMap, winnerFlag);
            }
            if (winnerId == null && score1 != null && score2 != null) {
                winnerId = (score1 > score2) ? t1Id : ((score2 > score1) ? t2Id : null);
            }

            String updateSql = "UPDATE matches SET "
                    + "score1 = ?, score2 = ?, status = 'FINISHED', "
                    + "team1_id = COALESCE(?, team1_id), "
                    + "team2_id = COALESCE(?, team2_id), "
                    + "winner_id = ? "
                    + "WHERE id = ? OR id LIKE ?";

            int updated = 0;
            try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                setNullableInt(ps, 1, score1);
                setNullableInt(ps, 2, score2);
                setNullableString(ps, 3, t1Id);
                setNullableString(ps, 4, t2Id);
                setNullableString(ps, 5, winnerId);
                ps.setString(6, matchDbId);
                ps.setString(7, "%_M" + matchId);
                updated = ps.executeUpdate();
            }

            if (updated == 0) {
                String insertSql = "INSERT INTO matches (id, tournament_id, stage_id, round_number, match_code, bracket_type, team1_id, team2_id, score1, score2, winner_id, status) "
                        + "VALUES (?, ?, ?, 1, ?, 'GROUP', ?, ?, ?, ?, ?, 'FINISHED')";
                try (PreparedStatement psIns = conn.prepareStatement(insertSql)) {
                    psIns.setString(1, matchDbId);
                    psIns.setString(2, tournamentId);
                    psIns.setString(3, stageId);
                    psIns.setString(4, "Trận #" + matchId);
                    setNullableString(psIns, 5, t1Id);
                    setNullableString(psIns, 6, t2Id);
                    setNullableInt(psIns, 7, score1);
                    setNullableInt(psIns, 8, score2);
                    setNullableString(psIns, 9, winnerId);
                    psIns.executeUpdate();
                }
            }

            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Reset all Group Stage matches for a tournament stage
     */
    public boolean resetGroupMatches(String tournamentId, int stageOrder) {
        if (tournamentId == null || tournamentId.trim().isEmpty()) {
            return false;
        }

        try (Connection conn = getConnection()) {
            String stageId = getOrCreateStageId(conn, tournamentId, stageOrder);
            String delSql = "DELETE FROM matches WHERE tournament_id = ? AND stage_id = ?";
            try (PreparedStatement ps = conn.prepareStatement(delSql)) {
                ps.setString(1, tournamentId);
                ps.setString(2, stageId);
                ps.executeUpdate();
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    // =========================================================================
    // HELPER METHODS
    // =========================================================================

    private void ensureTournamentExists(Connection conn, String tournamentId) throws SQLException {
        if (tournamentId == null || tournamentId.trim().isEmpty()) return;
        String checkSql = "SELECT id FROM tournaments WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(checkSql)) {
            ps.setString(1, tournamentId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return;
            }
        }
        String insertSql = "INSERT INTO tournaments (id, name, tournament_type, status) VALUES (?, ?, 'SINGLE_STAGE', 'DRAFT')";
        try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
            ps.setString(1, tournamentId);
            ps.setString(2, tournamentId);
            ps.executeUpdate();
        }
    }

    private String getOrCreateStageId(Connection conn, String tournamentId, int stageOrder) throws SQLException {
        ensureTournamentExists(conn, tournamentId);
        String queryStage = "SELECT id FROM tournament_stages WHERE tournament_id = ? AND stage_order = ?";
        try (PreparedStatement ps = conn.prepareStatement(queryStage)) {
            ps.setString(1, tournamentId);
            ps.setInt(2, stageOrder);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("id");
                }
            }
        }

        String stageId = tournamentId + "_S" + stageOrder;
        String stageName = (stageOrder == 2) ? "Giai đoạn 2 (Group Stage)" : "Giai đoạn 1 (Group Stage)";
        String insertStage = "INSERT INTO tournament_stages (id, tournament_id, stage_order, stage_name, format, status) "
                + "VALUES (?, ?, ?, ?, 'GROUP_STAGE', 'PENDING')";
        try (PreparedStatement ps = conn.prepareStatement(insertStage)) {
            ps.setString(1, stageId);
            ps.setString(2, tournamentId);
            ps.setInt(3, stageOrder);
            ps.setString(4, stageName);
            ps.executeUpdate();
        }
        return stageId;
    }

    private Map<String, String> getTeamNameToIdMap(Connection conn, String tournamentId) throws SQLException {
        Map<String, String> map = new HashMap<>();
        String sql = "SELECT id, raw_name, original_seed FROM teams WHERE tournament_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tournamentId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String id = rs.getString("id");
                    String rawName = rs.getString("raw_name");
                    int seed = rs.getInt("original_seed");

                    if (rawName != null && !rawName.trim().isEmpty()) {
                        map.put(rawName.trim().toLowerCase(), id);
                    }
                    if (!rs.wasNull()) {
                        map.put("seed_" + seed, id);
                    }
                }
            }
        }
        return map;
    }

    private String lookupTeamId(Map<String, String> teamMap, String teamName) {
        if (teamMap == null || teamMap.isEmpty() || teamName == null || teamName.trim().isEmpty() || "BYE".equalsIgnoreCase(teamName.trim())) {
            return null;
        }
        String norm = teamName.trim().toLowerCase();
        return teamMap.get(norm);
    }

    private void setNullableString(PreparedStatement ps, int paramIndex, String val) throws SQLException {
        if (val != null && !val.trim().isEmpty()) ps.setString(paramIndex, val.trim());
        else ps.setNull(paramIndex, Types.VARCHAR);
    }

    private void setNullableInt(PreparedStatement ps, int paramIndex, Integer val) throws SQLException {
        if (val != null) ps.setInt(paramIndex, val);
        else ps.setNull(paramIndex, Types.INTEGER);
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\b", "\\b")
                .replace("\f", "\\f")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private List<GSMatchDTO> parseGSMatchJson(String json) {
        List<GSMatchDTO> list = new ArrayList<>();
        if (json == null || json.trim().isEmpty()) return list;

        Pattern objPattern = Pattern.compile("\\{([^{}]+(?:\\{[^{}]*\\}[^{}]*)*)\\}");
        Matcher objMatcher = objPattern.matcher(json);

        while (objMatcher.find()) {
            String block = objMatcher.group(1);
            GSMatchDTO dto = new GSMatchDTO();

            dto.matchId = parseJsonString(block, "matchId", "GS_A_" + (list.size() + 1));
            dto.groupKey = parseJsonString(block, "groupKey", "A");
            dto.roundNumber = parseJsonInt(block, "roundNumber", 1);
            dto.matchNumber = parseJsonInt(block, "matchNumber", list.size() + 1);

            String t1Block = extractSubObject(block, "team1");
            if (t1Block != null) {
                dto.team1Name = parseJsonString(t1Block, "name", null);
                dto.team1Score = parseJsonNullableInt(t1Block, "score");
            }
            if (dto.team1Score == null) {
                dto.team1Score = parseJsonNullableInt(block, "team1Score");
            }

            String t2Block = extractSubObject(block, "team2");
            if (t2Block != null) {
                dto.team2Name = parseJsonString(t2Block, "name", null);
                dto.team2Score = parseJsonNullableInt(t2Block, "score");
            }
            if (dto.team2Score == null) {
                dto.team2Score = parseJsonNullableInt(block, "team2Score");
            }

            dto.winnerId = parseJsonString(block, "winnerId", null);
            dto.status = parseJsonString(block, "status", "PENDING");

            list.add(dto);
        }

        return list;
    }

    private String extractSubObject(String src, String key) {
        Pattern p = Pattern.compile("\"" + key + "\"\\s*:\\s*\\{([^}]*)\\}");
        Matcher m = p.matcher(src);
        if (m.find()) return m.group(1);
        return null;
    }

    private String parseJsonString(String src, String key, String defaultVal) {
        Pattern p = Pattern.compile("\"" + key + "\"\\s*:\\s*\"([^\"]*)\"");
        Matcher m = p.matcher(src);
        if (m.find()) return m.group(1);
        return defaultVal;
    }

    private int parseJsonInt(String src, String key, int defaultVal) {
        Pattern p = Pattern.compile("\"" + key + "\"\\s*:\\s*(\\d+)");
        Matcher m = p.matcher(src);
        if (m.find()) {
            try { return Integer.parseInt(m.group(1)); } catch (Exception ignore) {}
        }
        return defaultVal;
    }

    private Integer parseJsonNullableInt(String src, String key) {
        Pattern p = Pattern.compile("\"" + key + "\"\\s*:\\s*\"?(\\d+)\"?");
        Matcher m = p.matcher(src);
        if (m.find()) {
            try { return Integer.parseInt(m.group(1)); } catch (Exception ignore) {}
        }
        return null;
    }
}
