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
 * Dedicated DAO for Single Elimination Tournament Format Operations.
 * Manages bracket tree generation, database persistence to 'matches' table,
 * real-time score updates, and automatic winner advancement.
 */
public class SingleEliminationDAO extends DBContext {

    /**
     * DTO for incoming match data from frontend
     */
    public static class MatchDTO {
        public int matchId;
        public int roundNumber;
        public int matchNumber;
        public String team1Name;
        public Integer team1Seed;
        public Integer team1Score;
        public String team2Name;
        public Integer team2Seed;
        public Integer team2Score;
        public String winnerId; // "team1" or "team2"
        public Integer nextMatchId;
        public Integer nextMatchSlot;
        public boolean isBye;
        public String status;
    }

    /**
     * Fetch all Single Elimination matches for a tournament, grouped by Round Number
     */
    public Map<Integer, List<Match>> getBracketRounds(int tournamentId) {
        return getBracketRounds(String.valueOf(tournamentId));
    }

    public Map<Integer, List<Match>> getBracketRounds(String tournamentId) {
        Map<Integer, List<Match>> roundMap = new HashMap<>();
        List<Match> matchList = getMatchesByTournamentId(tournamentId);

        if (matchList == null || matchList.isEmpty()) {
            try {
                int tid = Integer.parseInt(tournamentId);
                matchList = generateDemoSingleEliminationMatches(tid);
            } catch (NumberFormatException e) {
                matchList = generateDemoSingleEliminationMatches(1);
            }
        }

        for (Match m : matchList) {
            roundMap.computeIfAbsent(m.getRoundNumber(), k -> new ArrayList<>()).add(m);
        }
        return roundMap;
    }

    public List<Match> getMatchesByTournamentId(int tournamentId) {
        return getMatchesByTournamentId(String.valueOf(tournamentId));
    }

    /**
     * Query matches from database table for a tournament
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
                int matchSeq = 1;
                while (rs.next()) {
                    Match m = new Match();
                    String rawId = rs.getString("id");
                    int numId = parseNumericMatchId(rawId, matchSeq++);
                    m.setId(numId);

                    int rNum = rs.getInt("round_number");
                    m.setRoundNumber(rNum);
                    m.setMatchNumber(numId);

                    String t1Name = rs.getString("t1_name");
                    m.setTeam1Name(t1Name);
                    int t1Seed = rs.getInt("t1_seed");
                    if (!rs.wasNull()) m.setTeam1Seed(t1Seed);
                    int s1 = rs.getInt("score1");
                    if (!rs.wasNull()) m.setTeam1Score(s1);

                    String t2Name = rs.getString("t2_name");
                    m.setTeam2Name(t2Name);
                    int t2Seed = rs.getInt("t2_seed");
                    if (!rs.wasNull()) m.setTeam2Seed(t2Seed);
                    int s2 = rs.getInt("score2");
                    if (!rs.wasNull()) m.setTeam2Score(s2);

                    String winnerId = rs.getString("winner_id");
                    if (winnerId != null) {
                        if (winnerId.equals(rs.getString("team1_id"))) {
                            m.setWinnerTeamId(1);
                        } else if (winnerId.equals(rs.getString("team2_id"))) {
                            m.setWinnerTeamId(2);
                        }
                    }

                    String nextIdStr = rs.getString("next_match_id");
                    if (nextIdStr != null) {
                        m.setNextMatchId(parseNumericMatchId(nextIdStr, 0));
                    }
                    String nextSlot = rs.getString("next_slot");
                    m.setNextMatchSlot("SLOT_2".equalsIgnoreCase(nextSlot) ? 2 : 1);

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
     * Get JSON array of matches for frontend JavaScript engine
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
                int matchSeq = 1;
                while (rs.next()) {
                    if (count > 0) sb.append(",");
                    count++;

                    String rawId = rs.getString("id");
                    int matchId = parseNumericMatchId(rawId, matchSeq++);
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
                        if (winnerIdCol.equals(rs.getString("team1_id"))) {
                            winnerSlot = "team1";
                        } else if (winnerIdCol.equals(rs.getString("team2_id"))) {
                            winnerSlot = "team2";
                        }
                    }

                    String nextIdStr = rs.getString("next_match_id");
                    Integer nextMatchId = (nextIdStr != null && !nextIdStr.isEmpty()) ? parseNumericMatchId(nextIdStr, 0) : null;
                    String nextSlot = rs.getString("next_slot");
                    int nextSlotNum = "SLOT_2".equalsIgnoreCase(nextSlot) ? 2 : 1;

                    boolean isBye = rs.getBoolean("is_bye");
                    String status = rs.getString("status");
                    if ("FINISHED".equalsIgnoreCase(status)) {
                        status = "COMPLETED";
                    }

                    sb.append("{");
                    sb.append("\"matchId\":").append(matchId).append(",");
                    sb.append("\"id\":").append(matchId).append(",");
                    sb.append("\"roundNumber\":").append(roundNumber).append(",");
                    sb.append("\"matchNumber\":").append(matchId).append(",");

                    sb.append("\"team1\":{");
                    sb.append("\"name\":\"").append(escapeJson(t1Name != null ? t1Name : "")).append("\",");
                    sb.append("\"seed\":\"").append(escapeJson(t1Seed)).append("\",");
                    sb.append("\"score\":\"").append(escapeJson(s1)).append("\"");
                    sb.append("},");

                    sb.append("\"team2\":{");
                    sb.append("\"name\":\"").append(escapeJson(t2Name != null ? t2Name : "")).append("\",");
                    sb.append("\"seed\":\"").append(escapeJson(t2Seed)).append("\",");
                    sb.append("\"score\":\"").append(escapeJson(s2)).append("\"");
                    sb.append("},");

                    sb.append("\"winnerId\":").append(winnerSlot.isEmpty() ? "null" : "\"" + winnerSlot + "\"").append(",");
                    sb.append("\"winnerName\":\"").append(escapeJson(rs.getString("winner_name") != null ? rs.getString("winner_name") : "")).append("\",");
                    sb.append("\"nextMatchId\":").append(nextMatchId != null ? nextMatchId : "null").append(",");
                    sb.append("\"nextMatchSlot\":").append(nextSlotNum).append(",");
                    sb.append("\"isBye\":").append(isBye ? "true" : "false").append(",");
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
     * Batch Sync / Persist full bracket structure to 'matches' table.
     */
    public boolean syncBracketMatches(String tournamentId, int stageOrder, String matchesDataJson) {
        List<MatchDTO> list = parseMatchesJson(matchesDataJson);
        if (list.isEmpty()) {
            return false;
        }

        try (Connection conn = getConnection()) {
            String stageId = getOrCreateStageId(conn, tournamentId, stageOrder);
            Map<String, String> teamMap = getTeamNameToIdMap(conn, tournamentId);

            // Sort matches so higher round numbers come first (Round 6, Round 5... Round 1)
            // This guarantees next_match_id targets already exist when inserting foreign keys!
            list.sort(Comparator.comparingInt((MatchDTO m) -> m.roundNumber).reversed()
                    .thenComparingInt(m -> -m.matchId));

            String mergeSql = "MERGE INTO matches AS target "
                    + "USING (SELECT ? AS id, ? AS tournament_id, ? AS stage_id, ? AS round_number, ? AS match_code, "
                    + "              ? AS bracket_type, ? AS team1_id, ? AS team2_id, ? AS score1, ? AS score2, "
                    + "              ? AS winner_id, ? AS next_match_id, ? AS next_slot, ? AS is_bye, ? AS status) AS source "
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
                    + "        target.next_match_id = source.next_match_id, "
                    + "        target.next_slot = source.next_slot, "
                    + "        target.is_bye = source.is_bye, "
                    + "        target.status = source.status "
                    + "WHEN NOT MATCHED THEN "
                    + "    INSERT (id, tournament_id, stage_id, round_number, match_code, bracket_type, "
                    + "            team1_id, team2_id, score1, score2, winner_id, next_match_id, next_slot, is_bye, status) "
                    + "    VALUES (source.id, source.tournament_id, source.stage_id, source.round_number, source.match_code, source.bracket_type, "
                    + "            source.team1_id, source.team2_id, source.score1, source.score2, source.winner_id, source.next_match_id, source.next_slot, source.is_bye, source.status);";

            // Pass 1: Upsert all matches with next_match_id = NULL to ensure no FK constraint conflicts
            try (PreparedStatement ps1 = conn.prepareStatement(mergeSql)) {
                for (MatchDTO m : list) {
                    String matchDbId = buildMatchDbId(tournamentId, stageOrder, m.matchId);
                    String t1Id = lookupTeamId(teamMap, m.team1Name, m.team1Seed);
                    String t2Id = lookupTeamId(teamMap, m.team2Name, m.team2Seed);
                    String winnerId = "team1".equalsIgnoreCase(m.winnerId) ? t1Id : ("team2".equalsIgnoreCase(m.winnerId) ? t2Id : null);
                    String status = ("COMPLETED".equalsIgnoreCase(m.status) || "FINISHED".equalsIgnoreCase(m.status) || (m.team1Score != null && m.team2Score != null)) ? "FINISHED" : "PENDING";
                    String nextSlotStr = (m.nextMatchSlot != null && m.nextMatchSlot == 2) ? "SLOT_2" : ((m.nextMatchSlot != null && m.nextMatchSlot == 1) ? "SLOT_1" : null);

                    ps1.setString(1, matchDbId);
                    ps1.setString(2, tournamentId);
                    ps1.setString(3, stageId);
                    ps1.setInt(4, m.roundNumber);
                    ps1.setString(5, "Trận #" + m.matchNumber);
                    ps1.setString(6, "MAIN");
                    setNullableString(ps1, 7, t1Id);
                    setNullableString(ps1, 8, t2Id);
                    setNullableInt(ps1, 9, m.team1Score);
                    setNullableInt(ps1, 10, m.team2Score);
                    setNullableString(ps1, 11, winnerId);
                    ps1.setNull(12, Types.VARCHAR); // Set NULL initially for FK safety
                    setNullableString(ps1, 13, nextSlotStr);
                    ps1.setBoolean(14, m.isBye);
                    ps1.setString(15, status);
                    ps1.addBatch();
                }
                ps1.executeBatch();
            }

            // Pass 2: Link next_match_id now that all match rows exist
            String updateNextSql = "UPDATE matches SET next_match_id = ? WHERE id = ?";
            try (PreparedStatement ps2 = conn.prepareStatement(updateNextSql)) {
                for (MatchDTO m : list) {
                    if (m.nextMatchId != null && m.nextMatchId > 0) {
                        String matchDbId = buildMatchDbId(tournamentId, stageOrder, m.matchId);
                        String nextMatchDbId = buildMatchDbId(tournamentId, stageOrder, m.nextMatchId);
                        ps2.setString(1, nextMatchDbId);
                        ps2.setString(2, matchDbId);
                        ps2.addBatch();
                    }
                }
                ps2.executeBatch();
            }

            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Real-time Update match score in database and advance winner to next match
     */
    public boolean updateMatchScoreAndAdvance(String tournamentId, int stageOrder, int matchId,
                                            Integer score1, Integer score2, String winnerFlag,
                                            String team1Name, String team2Name) {
        if (tournamentId == null || tournamentId.trim().isEmpty()) {
            return false;
        }

        try (Connection conn = getConnection()) {
            String stageId = getOrCreateStageId(conn, tournamentId, stageOrder);
            Map<String, String> teamMap = getTeamNameToIdMap(conn, tournamentId);

            String matchDbId = buildMatchDbId(tournamentId, stageOrder, matchId);
            String t1Id = lookupTeamId(teamMap, team1Name, null);
            String t2Id = lookupTeamId(teamMap, team2Name, null);

            String winnerId = null;
            if ("team1".equalsIgnoreCase(winnerFlag)) {
                winnerId = t1Id;
            } else if ("team2".equalsIgnoreCase(winnerFlag)) {
                winnerId = t2Id;
            } else if (winnerFlag != null && !winnerFlag.trim().isEmpty()) {
                winnerId = lookupTeamId(teamMap, winnerFlag, null);
            }
            if (winnerId == null && score1 != null && score2 != null) {
                winnerId = (score1 > score2) ? t1Id : ((score2 > score1) ? t2Id : null);
            }

            // 1. Update the match score and status
            String updateSql = "UPDATE matches SET "
                    + "score1 = ?, score2 = ?, status = 'FINISHED', "
                    + "team1_id = COALESCE(?, team1_id), "
                    + "team2_id = COALESCE(?, team2_id), "
                    + "winner_id = ? "
                    + "WHERE id = ?";

            try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                setNullableInt(ps, 1, score1);
                setNullableInt(ps, 2, score2);
                setNullableString(ps, 3, t1Id);
                setNullableString(ps, 4, t2Id);
                setNullableString(ps, 5, winnerId);
                ps.setString(6, matchDbId);
                int rows = ps.executeUpdate();
                if (rows == 0) {
                    // Row might not exist yet, trigger single insert
                    insertSingleMatchNode(conn, matchDbId, tournamentId, stageId, matchId, t1Id, t2Id, score1, score2, winnerId);
                }
            }

            // 2. Advance winner into next match slot in DB
            if (winnerId != null) {
                String selectNextSql = "SELECT next_match_id, next_slot FROM matches WHERE id = ?";
                String nextMatchId = null;
                String nextSlot = null;
                try (PreparedStatement psSel = conn.prepareStatement(selectNextSql)) {
                    psSel.setString(1, matchDbId);
                    try (ResultSet rs = psSel.executeQuery()) {
                        if (rs.next()) {
                            nextMatchId = rs.getString("next_match_id");
                            nextSlot = rs.getString("next_slot");
                        }
                    }
                }

                if (nextMatchId != null && !nextMatchId.isEmpty()) {
                    String advSql = "SLOT_2".equalsIgnoreCase(nextSlot)
                            ? "UPDATE matches SET team2_id = ? WHERE id = ?"
                            : "UPDATE matches SET team1_id = ? WHERE id = ?";
                    try (PreparedStatement psAdv = conn.prepareStatement(advSql)) {
                        psAdv.setString(1, winnerId);
                        psAdv.setString(2, nextMatchId);
                        psAdv.executeUpdate();
                    }
                }
            }

            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Backward-compatible updateMatchScoreAndAdvance
     */
    public boolean updateMatchScoreAndAdvance(int matchId, int score1, int score2, String winnerFlag) {
        String updateSql = "UPDATE matches SET score1 = ?, score2 = ?, status = 'FINISHED' WHERE id LIKE ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(updateSql)) {
            ps.setInt(1, score1);
            ps.setInt(2, score2);
            ps.setString(3, "%_M" + matchId);
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Reset bracket matches in database for a specific stage
     */
    public boolean resetBracketMatches(String tournamentId, int stageOrder) {
        if (tournamentId == null || tournamentId.trim().isEmpty()) return false;
        try (Connection conn = getConnection()) {
            String stageId = getOrCreateStageId(conn, tournamentId, stageOrder);
            // 1. Unlink next_match_id first
            try (PreparedStatement ps1 = conn.prepareStatement("UPDATE matches SET next_match_id = NULL WHERE tournament_id = ? AND stage_id = ?")) {
                ps1.setString(1, tournamentId);
                ps1.setString(2, stageId);
                ps1.executeUpdate();
            }
            // 2. Delete matches
            try (PreparedStatement ps2 = conn.prepareStatement("DELETE FROM matches WHERE tournament_id = ? AND stage_id = ?")) {
                ps2.setString(1, tournamentId);
                ps2.setString(2, stageId);
                ps2.executeUpdate();
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    // ==========================================
    // HELPER METHODS
    // ==========================================

    private String buildMatchDbId(String tournamentId, int stageOrder, int matchId) {
        return tournamentId + "_S" + stageOrder + "_M" + matchId;
    }

    private int parseNumericMatchId(String rawId, int fallback) {
        if (rawId == null) return fallback;
        int idx = rawId.lastIndexOf("_M");
        if (idx != -1) {
            try {
                return Integer.parseInt(rawId.substring(idx + 2));
            } catch (Exception ignore) {}
        }
        try {
            return Integer.parseInt(rawId);
        } catch (Exception ignore) {}
        return fallback;
    }

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

    public String getOrCreateStageId(Connection conn, String tournamentId, int stageOrder) throws SQLException {
        ensureTournamentExists(conn, tournamentId);
        String selectSql = "SELECT id FROM tournament_stages WHERE tournament_id = ? AND stage_order = ?";
        try (PreparedStatement ps = conn.prepareStatement(selectSql)) {
            ps.setString(1, tournamentId);
            ps.setInt(2, stageOrder);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("id");
                }
            }
        }

        String newStageId = "STAGE_" + (stageOrder == 2 ? "2_" : "") + System.currentTimeMillis();
        String insertSql = "INSERT INTO tournament_stages (id, tournament_id, stage_order, stage_name, format, status) VALUES (?, ?, ?, ?, 'SINGLE_ELIMINATION', 'PENDING')";
        try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
            ps.setString(1, newStageId);
            ps.setString(2, tournamentId);
            ps.setInt(3, stageOrder);
            ps.setString(4, stageOrder == 2 ? "Stage 2" : "Main Stage");
            ps.executeUpdate();
        }
        return newStageId;
    }

    public Map<String, String> getTeamNameToIdMap(Connection conn, String tournamentId) throws SQLException {
        Map<String, String> map = new HashMap<>();
        String sql = "SELECT id, raw_name, normalized_name, original_seed FROM teams WHERE tournament_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tournamentId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String id = rs.getString("id");
                    String raw = rs.getString("raw_name");
                    String norm = rs.getString("normalized_name");
                    int seed = rs.getInt("original_seed");
                    if (raw != null) map.put(raw.trim().toLowerCase(), id);
                    if (norm != null) map.put(norm.trim().toLowerCase(), id);
                    map.put("seed_" + seed, id);
                }
            }
        }
        return map;
    }

    private String lookupTeamId(Map<String, String> teamMap, String name, Integer seed) {
        if (name != null) {
            String trimmed = name.trim().toLowerCase();
            if (!trimmed.isEmpty() && !trimmed.equals("bye") && !trimmed.equals("tbd") && !trimmed.startsWith("w #") && !trimmed.startsWith("l #")) {
                if (teamMap.containsKey(trimmed)) return teamMap.get(trimmed);
            }
        }
        if (seed != null && seed > 0) {
            String seedKey = "seed_" + seed;
            if (teamMap.containsKey(seedKey)) return teamMap.get(seedKey);
        }
        return null;
    }

    private void insertSingleMatchNode(Connection conn, String matchDbId, String tournamentId, String stageId,
                                       int matchId, String t1Id, String t2Id, Integer s1, Integer s2, String winnerId) throws SQLException {
        String insertSql = "INSERT INTO matches (id, tournament_id, stage_id, round_number, match_code, bracket_type, team1_id, team2_id, score1, score2, winner_id, status) "
                + "VALUES (?, ?, ?, 1, ?, 'MAIN', ?, ?, ?, ?, ?, 'FINISHED')";
        try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
            ps.setString(1, matchDbId);
            ps.setString(2, tournamentId);
            ps.setString(3, stageId);
            ps.setString(4, "Trận #" + matchId);
            setNullableString(ps, 5, t1Id);
            setNullableString(ps, 6, t2Id);
            setNullableInt(ps, 7, s1);
            setNullableInt(ps, 8, s2);
            setNullableString(ps, 9, winnerId);
            ps.executeUpdate();
        }
    }

    private void setNullableString(PreparedStatement ps, int paramIndex, String value) throws SQLException {
        if (value != null && !value.trim().isEmpty()) {
            ps.setString(paramIndex, value.trim());
        } else {
            ps.setNull(paramIndex, Types.VARCHAR);
        }
    }

    private void setNullableInt(PreparedStatement ps, int paramIndex, Integer value) throws SQLException {
        if (value != null) {
            ps.setInt(paramIndex, value);
        } else {
            ps.setNull(paramIndex, Types.INTEGER);
        }
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }

    public static List<MatchDTO> parseMatchesJson(String json) {
        List<MatchDTO> list = new ArrayList<>();
        if (json == null || json.trim().isEmpty() || json.trim().equals("[]")) {
            return list;
        }

        Pattern objPattern = Pattern.compile("\\{([^{}]+)\\}");
        Matcher objMatcher = objPattern.matcher(json);

        while (objMatcher.find()) {
            String body = objMatcher.group(1);
            MatchDTO m = new MatchDTO();
            m.matchId = getIntFromJson(body, "matchId", 0);
            m.roundNumber = getIntFromJson(body, "roundNumber", 1);
            m.matchNumber = getIntFromJson(body, "matchNumber", m.matchId);
            m.team1Name = getStringFromJson(body, "team1Name");
            m.team1Seed = getNullableIntFromJson(body, "team1Seed");
            m.team1Score = getNullableIntFromJson(body, "team1Score");
            m.team2Name = getStringFromJson(body, "team2Name");
            m.team2Seed = getNullableIntFromJson(body, "team2Seed");
            m.team2Score = getNullableIntFromJson(body, "team2Score");
            m.winnerId = getStringFromJson(body, "winnerId");
            m.nextMatchId = getNullableIntFromJson(body, "nextMatchId");
            m.nextMatchSlot = getNullableIntFromJson(body, "nextMatchSlot");
            m.isBye = getBooleanFromJson(body, "isBye");
            m.status = getStringFromJson(body, "status");
            list.add(m);
        }
        return list;
    }

    private static String getStringFromJson(String body, String key) {
        Pattern p = Pattern.compile("\"" + key + "\"\\s*:\\s*\"([^\"]*)\"");
        Matcher m = p.matcher(body);
        if (m.find()) return m.group(1).trim();
        return null;
    }

    private static int getIntFromJson(String body, String key, int def) {
        Integer val = getNullableIntFromJson(body, key);
        return val != null ? val : def;
    }

    private static Integer getNullableIntFromJson(String body, String key) {
        Pattern p = Pattern.compile("\"" + key + "\"\\s*:\\s*\"?(-?\\d+)\"?");
        Matcher m = p.matcher(body);
        if (m.find()) {
            try {
                return Integer.parseInt(m.group(1));
            } catch (Exception e) {}
        }
        return null;
    }

    private static boolean getBooleanFromJson(String body, String key) {
        Pattern p = Pattern.compile("\"" + key + "\"\\s*:\\s*(true|false)");
        Matcher m = p.matcher(body);
        if (m.find()) {
            return "true".equalsIgnoreCase(m.group(1));
        }
        return false;
    }

    private List<Match> generateDemoSingleEliminationMatches(int tournamentId) {
        List<Match> list = new ArrayList<>();
        Match m1 = new Match(1, tournamentId, 1, 1, 101, "Hà Nội FC", 1, 3, 108, "Hải Phòng FC", 8, 1, 101, 5, 1, "COMPLETED");
        Match m2 = new Match(2, tournamentId, 1, 2, 104, "Saigon Heat", 4, 0, 105, "SHB Đà Nẵng", 5, 2, 105, 5, 2, "COMPLETED");
        Match m3 = new Match(3, tournamentId, 1, 3, 102, "Becamex Bình Dương", 2, 2, 107, "Hoàng Anh Gia Lai", 7, 1, 102, 6, 1, "COMPLETED");
        Match m4 = new Match(4, tournamentId, 1, 4, 103, "Viettel FC", 3, 1, 106, "Thép Xanh Nam Định", 6, 2, 106, 6, 2, "COMPLETED");

        Match m5 = new Match(5, tournamentId, 2, 5, 101, "Hà Nội FC", 1, 2, 105, "SHB Đà Nẵng", 5, 1, 101, 7, 1, "COMPLETED");
        Match m6 = new Match(6, tournamentId, 2, 6, 102, "Becamex Bình Dương", 2, 0, 106, "Thép Xanh Nam Định", 6, 0, null, 7, 2, "SCHEDULED");

        Match m7 = new Match(7, tournamentId, 3, 7, 101, "Hà Nội FC", 1, 0, null, "W #6", 6, 0, null, 0, 0, "SCHEDULED");

        list.add(m1);
        list.add(m2);
        list.add(m3);
        list.add(m4);
        list.add(m5);
        list.add(m6);
        list.add(m7);

        return list;
    }
}
