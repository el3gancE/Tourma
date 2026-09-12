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
 * Dedicated DAO for Swiss System Tournament Format.
 * Manages Swiss round matches (33 matches across 5 rounds), Buchholz standings,
 * and SQL Server 'matches' table persistence.
 */
public class SwissSystemDAO extends DBContext {

    public static class SWMatchDTO {
        public String matchKey;
        public int roundIndex;
        public int matchNumber;
        public String recordPool;
        public String team1Name;
        public Integer team1Score;
        public String team2Name;
        public Integer team2Score;
        public String winnerId;
        public String status;
    }

    /**
     * Fetch all Swiss matches for a tournament, grouped by Round Number
     */
    public Map<Integer, List<Match>> getSwissRounds(int tournamentId) {
        return getSwissRounds(String.valueOf(tournamentId));
    }

    public Map<Integer, List<Match>> getSwissRounds(String tournamentId) {
        Map<Integer, List<Match>> roundMap = new HashMap<>();
        List<Match> matchList = getMatchesByTournamentId(tournamentId);

        for (Match m : matchList) {
            roundMap.computeIfAbsent(m.getRoundNumber(), k -> new ArrayList<>()).add(m);
        }

        return roundMap;
    }

    /**
     * Query matches from database table
     */
    public List<Match> getMatchesByTournamentId(String tournamentId) {
        List<Match> list = new ArrayList<>();
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
                    m.setId(parseNumericMatchId(rawId, seq++));
                    m.setRoundNumber(rs.getInt("round_number"));
                    m.setTeam1Name(rs.getString("t1_name"));
                    m.setTeam2Name(rs.getString("t2_name"));

                    int s1 = rs.getInt("score1");
                    if (!rs.wasNull()) m.setTeam1Score(s1);

                    int s2 = rs.getInt("score2");
                    if (!rs.wasNull()) m.setTeam2Score(s2);

                    String status = rs.getString("status");
                    m.setStatus("FINISHED".equalsIgnoreCase(status) ? "COMPLETED" : (status != null ? status : "SCHEDULED"));
                    list.add(m);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    /**
     * Get JSON array of Swiss matches for frontend engine
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

                    // Extract pool key if present in match_code, e.g. "Vòng 1 (0-0) #1"
                    String poolKey = "0-0";
                    Matcher poolMatcher = Pattern.compile("\\(([^)]+)\\)").matcher(matchCode);
                    if (poolMatcher.find()) {
                        poolKey = poolMatcher.group(1);
                    }

                    int matchNumber = seq;
                    Matcher numMatcher = Pattern.compile("#(\\d+)").matcher(matchCode);
                    if (numMatcher.find()) {
                        matchNumber = Integer.parseInt(numMatcher.group(1));
                    }

                    String t1Name = rs.getString("t1_name");
                    int s1Val = rs.getInt("score1");
                    int s1 = rs.wasNull() ? 0 : s1Val;

                    String t2Name = rs.getString("t2_name");
                    int s2Val = rs.getInt("score2");
                    int s2 = rs.wasNull() ? 0 : s2Val;

                    String winnerIdCol = rs.getString("winner_id");
                    String winnerSlot = "";
                    if (winnerIdCol != null) {
                        if (winnerIdCol.equals(rs.getString("team1_id"))) {
                            winnerSlot = "team1";
                        } else if (winnerIdCol.equals(rs.getString("team2_id"))) {
                            winnerSlot = "team2";
                        }
                    }

                    String status = rs.getString("status");
                    if ("FINISHED".equalsIgnoreCase(status)) {
                        status = "COMPLETED";
                    }

                    String matchKey = "R" + roundNumber + "_" + poolKey + "_M" + matchNumber;
                    if (rawId != null && rawId.contains("_M")) {
                        matchKey = rawId.substring(rawId.indexOf("_M") + 2);
                    }

                    sb.append("{");
                    sb.append("\"matchKey\":\"").append(escapeJson(matchKey)).append("\",");
                    sb.append("\"roundIndex\":").append(roundNumber).append(",");
                    sb.append("\"recordPool\":\"").append(escapeJson(poolKey)).append("\",");
                    sb.append("\"matchNumber\":").append(matchNumber).append(",");
                    sb.append("\"team1\":{\"name\":\"").append(escapeJson(t1Name != null ? t1Name : "TBD")).append("\"},");
                    sb.append("\"team2\":{\"name\":\"").append(escapeJson(t2Name != null ? t2Name : "TBD")).append("\"},");
                    sb.append("\"team1Score\":").append(s1).append(",");
                    sb.append("\"team2Score\":").append(s2).append(",");
                    sb.append("\"winnerId\":").append(winnerSlot.isEmpty() ? "null" : "\"" + winnerSlot + "\"").append(",");
                    sb.append("\"status\":\"").append(escapeJson(status != null ? status : "PENDING")).append("\"");
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
     * Batch Sync / Persist full Swiss stage structure to 'matches' table
     */
    public boolean syncSwissMatches(String tournamentId, int stageOrder, String matchesDataJson) {
        List<SWMatchDTO> list = parseSWMatchJson(matchesDataJson);
        if (list.isEmpty()) {
            return false;
        }

        try (Connection conn = getConnection()) {
            String stageId = getOrCreateStageId(conn, tournamentId, stageOrder);
            Map<String, String> teamMap = getTeamNameToIdMap(conn, tournamentId);

            String mergeSql = "MERGE INTO matches AS target "
                    + "USING (SELECT ? AS id, ? AS tournament_id, ? AS stage_id, ? AS round_number, ? AS match_code, "
                    + "              ? AS bracket_type, ? AS team1_id, ? AS team2_id, ? AS score1, ? AS score2, "
                    + "              ? AS winner_id, ? AS loser_id, ? AS status) AS source "
                    + "ON (target.id = source.id) "
                    + "WHEN MATCHED THEN "
                    + "    UPDATE SET "
                    + "        target.round_number = source.round_number, "
                    + "        target.match_code = source.match_code, "
                    + "        target.bracket_type = source.bracket_type, "
                    + "        target.team1_id = source.team1_id, "
                    + "        target.team2_id = source.team2_id, "
                    + "        target.score1 = source.score1, "
                    + "        target.score2 = source.score2, "
                    + "        target.winner_id = source.winner_id, "
                    + "        target.loser_id = source.loser_id, "
                    + "        target.status = source.status "
                    + "WHEN NOT MATCHED THEN "
                    + "    INSERT (id, tournament_id, stage_id, round_number, match_code, bracket_type, "
                    + "            team1_id, team2_id, score1, score2, winner_id, loser_id, status) "
                    + "    VALUES (source.id, source.tournament_id, source.stage_id, source.round_number, source.match_code, source.bracket_type, "
                    + "            source.team1_id, source.team2_id, source.score1, source.score2, source.winner_id, source.loser_id, source.status);";

            try (PreparedStatement ps = conn.prepareStatement(mergeSql)) {
                for (SWMatchDTO m : list) {
                    String matchDbId = tournamentId + "_S" + stageOrder + "_M" + m.matchKey;
                    String t1Id = lookupTeamId(teamMap, m.team1Name);
                    String t2Id = lookupTeamId(teamMap, m.team2Name);
                    String winnerId = "team1".equalsIgnoreCase(m.winnerId) ? t1Id : ("team2".equalsIgnoreCase(m.winnerId) ? t2Id : null);
                    String loserId = (winnerId != null) ? (winnerId.equals(t1Id) ? t2Id : (winnerId.equals(t2Id) ? t1Id : null)) : null;

                    String status = "PENDING";
                    if ("COMPLETED".equalsIgnoreCase(m.status) || "FINISHED".equalsIgnoreCase(m.status) || "DONE".equalsIgnoreCase(m.status)) {
                        status = "FINISHED";
                    } else if ("READY".equalsIgnoreCase(m.status)) {
                        status = "READY";
                    }

                    String matchCode = "Vòng " + m.roundIndex + " (" + (m.recordPool != null ? m.recordPool : "0-0") + ") #" + m.matchNumber;

                    ps.setString(1, matchDbId);
                    ps.setString(2, tournamentId);
                    ps.setString(3, stageId);
                    ps.setInt(4, m.roundIndex);
                    ps.setString(5, matchCode);
                    ps.setString(6, "SWISS");
                    setNullableString(ps, 7, t1Id);
                    setNullableString(ps, 8, t2Id);
                    setNullableInt(ps, 9, m.team1Score);
                    setNullableInt(ps, 10, m.team2Score);
                    setNullableString(ps, 11, winnerId);
                    setNullableString(ps, 12, loserId);
                    ps.setString(13, status);
                    ps.addBatch();
                }
                ps.executeBatch();
            }

            syncStageParticipants(conn, tournamentId, stageId, list, teamMap);

            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private void syncStageParticipants(Connection conn, String tournamentId, String stageId, List<SWMatchDTO> list, Map<String, String> teamMap) {
        String stagePartSql = "MERGE INTO stage_participants AS target "
                + "USING (SELECT ? AS id, ? AS tournament_id, ? AS stage_id, ? AS team_id, ? AS seed_in_stage) AS source "
                + "ON (target.stage_id = source.stage_id AND target.team_id = source.team_id) "
                + "WHEN NOT MATCHED THEN "
                + "    INSERT (id, tournament_id, stage_id, team_id, seed_in_stage, qualification_source, status) "
                + "    VALUES (source.id, source.tournament_id, source.stage_id, source.team_id, source.seed_in_stage, 'AUTO_SEED', 'ACTIVE');";
        try (PreparedStatement ps = conn.prepareStatement(stagePartSql)) {
            java.util.Set<String> seenTeams = new java.util.HashSet<>();
            int seed = 1;
            for (SWMatchDTO m : list) {
                if (m.team1Name != null && !m.team1Name.trim().isEmpty() && !"BYE".equalsIgnoreCase(m.team1Name)) {
                    String t1Id = lookupTeamId(teamMap, m.team1Name);
                    if (t1Id != null && seenTeams.add(t1Id)) {
                        ps.setString(1, stageId + "_" + t1Id);
                        ps.setString(2, tournamentId);
                        ps.setString(3, stageId);
                        ps.setString(4, t1Id);
                        ps.setInt(5, seed++);
                        ps.addBatch();
                    }
                }
                if (m.team2Name != null && !m.team2Name.trim().isEmpty() && !"BYE".equalsIgnoreCase(m.team2Name)) {
                    String t2Id = lookupTeamId(teamMap, m.team2Name);
                    if (t2Id != null && seenTeams.add(t2Id)) {
                        ps.setString(1, stageId + "_" + t2Id);
                        ps.setString(2, tournamentId);
                        ps.setString(3, stageId);
                        ps.setString(4, t2Id);
                        ps.setInt(5, seed++);
                        ps.addBatch();
                    }
                }
            }
            ps.executeBatch();
        } catch (Exception ignore) {}
    }

    /**
     * Update score for a Swiss match
     */
    public boolean updateSwissMatchScore(String tournamentId, int stageOrder, String matchKey,
                                         Integer score1, Integer score2, String status,
                                         String team1Name, String team2Name, String winner) {
        if (tournamentId == null || tournamentId.trim().isEmpty()) {
            return false;
        }

        try (Connection conn = getConnection()) {
            String stageId = getOrCreateStageId(conn, tournamentId, stageOrder);
            Map<String, String> teamMap = getTeamNameToIdMap(conn, tournamentId);
            String matchDbId = tournamentId + "_S" + stageOrder + "_M" + matchKey;
            String t1Id = lookupTeamId(teamMap, team1Name);
            String t2Id = lookupTeamId(teamMap, team2Name);

            String winnerId = null;
            if ("team1".equalsIgnoreCase(winner)) {
                winnerId = t1Id;
            } else if ("team2".equalsIgnoreCase(winner)) {
                winnerId = t2Id;
            } else if (winner != null && !winner.trim().isEmpty()) {
                winnerId = lookupTeamId(teamMap, winner);
            }
            if (winnerId == null && score1 != null && score2 != null) {
                winnerId = (score1 > score2) ? t1Id : ((score2 > score1) ? t2Id : null);
            }
            String loserId = (winnerId != null) ? (winnerId.equals(t1Id) ? t2Id : (winnerId.equals(t2Id) ? t1Id : null)) : null;

            String dbStatus = ("COMPLETED".equalsIgnoreCase(status) || "FINISHED".equalsIgnoreCase(status) || (score1 != null && score2 != null)) ? "FINISHED" : "READY";

            String updateSql = "UPDATE matches SET "
                    + "score1 = ?, score2 = ?, status = ?, "
                    + "team1_id = COALESCE(?, team1_id), "
                    + "team2_id = COALESCE(?, team2_id), "
                    + "winner_id = ?, "
                    + "loser_id = ?, "
                    + "bracket_type = 'SWISS' "
                    + "WHERE id = ? OR id LIKE ?";

            int updated = 0;
            try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                setNullableInt(ps, 1, score1);
                setNullableInt(ps, 2, score2);
                ps.setString(3, dbStatus);
                setNullableString(ps, 4, t1Id);
                setNullableString(ps, 5, t2Id);
                setNullableString(ps, 6, winnerId);
                setNullableString(ps, 7, loserId);
                ps.setString(8, matchDbId);
                ps.setString(9, "%_M" + matchKey);
                updated = ps.executeUpdate();
            }

            if (updated == 0) {
                String insertSql = "INSERT INTO matches (id, tournament_id, stage_id, round_number, match_code, bracket_type, team1_id, team2_id, score1, score2, winner_id, loser_id, status) "
                        + "VALUES (?, ?, ?, 1, ?, 'SWISS', ?, ?, ?, ?, ?, ?, ?)";
                try (PreparedStatement psIns = conn.prepareStatement(insertSql)) {
                    psIns.setString(1, matchDbId);
                    psIns.setString(2, tournamentId);
                    psIns.setString(3, stageId);
                    psIns.setString(4, "Trận #" + matchKey);
                    setNullableString(psIns, 5, t1Id);
                    setNullableString(psIns, 6, t2Id);
                    setNullableInt(psIns, 7, score1);
                    setNullableInt(psIns, 8, score2);
                    setNullableString(psIns, 9, winnerId);
                    setNullableString(psIns, 10, loserId);
                    psIns.setString(11, dbStatus);
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
     * Backward-compatible simple updateSwissMatchScore
     */
    public boolean updateSwissMatchScore(int matchId, int score1, int score2, String status) {
        String dbStatus = ("COMPLETED".equalsIgnoreCase(status) || "FINISHED".equalsIgnoreCase(status)) ? "FINISHED" : "READY";
        String updateSql = "UPDATE matches SET score1 = ?, score2 = ?, status = ? WHERE id LIKE ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(updateSql)) {
            ps.setInt(1, score1);
            ps.setInt(2, score2);
            ps.setString(3, dbStatus);
            ps.setString(4, "%_M" + matchId);
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Reset Swiss matches for a stage
     */
    public boolean resetSwissMatches(String tournamentId, int stageOrder) {
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
        String stageName = (stageOrder == 2) ? "Giai đoạn 2 (Swiss System)" : "Giai đoạn 1 (Swiss System)";
        String insertStage = "INSERT INTO tournament_stages (id, tournament_id, stage_order, stage_name, format, status) "
                + "VALUES (?, ?, ?, ?, 'SWISS_LITE', 'PENDING')";
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
        if (teamMap == null || teamMap.isEmpty() || teamName == null || teamName.trim().isEmpty() || "TBD".equalsIgnoreCase(teamName.trim())) {
            return null;
        }
        String norm = teamName.trim().toLowerCase();
        return teamMap.get(norm);
    }

    private int parseNumericMatchId(String str, int defaultVal) {
        if (str == null || str.trim().isEmpty()) return defaultVal;
        try {
            return Integer.parseInt(str.trim());
        } catch (NumberFormatException e) {
            Matcher m = Pattern.compile("(\\d+)$").matcher(str.trim());
            if (m.find()) {
                return Integer.parseInt(m.group(1));
            }
        }
        return defaultVal;
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

    private List<SWMatchDTO> parseSWMatchJson(String json) {
        List<SWMatchDTO> list = new ArrayList<>();
        if (json == null || json.trim().isEmpty()) return list;

        Pattern objPattern = Pattern.compile("\\{([^{}]+(?:\\{[^{}]*\\}[^{}]*)*)\\}");
        Matcher objMatcher = objPattern.matcher(json);

        while (objMatcher.find()) {
            String block = objMatcher.group(1);
            SWMatchDTO dto = new SWMatchDTO();

            dto.matchKey = parseJsonString(block, "matchKey", null);
            if (dto.matchKey == null) {
                dto.matchKey = parseJsonString(block, "id", "M" + (list.size() + 1));
            }
            dto.roundIndex = parseJsonInt(block, "roundIndex", parseJsonInt(block, "roundNumber", 1));
            dto.matchNumber = parseJsonInt(block, "matchNumber", list.size() + 1);
            dto.recordPool = parseJsonString(block, "recordPool", "0-0");

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
