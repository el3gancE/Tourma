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
 * Dedicated DAO for Double Elimination Tournament Format Operations.
 * Manages Upper Bracket, Lower Bracket, Grand Finals, and Drop-down linkages
 * persisted into SQL Server 'matches' table.
 */
public class DoubleEliminationDAO extends DBContext {

    /**
     * DTO for incoming Double Elimination match from frontend
     */
    public static class DEMatchDTO {
        public int matchId;
        public String bracketType; // "UPPER", "LOWER", "GRAND_FINAL"
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
     * Fetch all Double Elimination matches for a tournament, grouped by Bracket Type / Round
     */
    public Map<String, Object> getDoubleEliminationData(int tournamentId) {
        return getDoubleEliminationData(String.valueOf(tournamentId));
    }

    public Map<String, Object> getDoubleEliminationData(String tournamentId) {
        Map<String, Object> dataMap = new HashMap<>();
        List<Match> matchList = getMatchesByTournamentId(tournamentId);

        Map<Integer, List<Match>> upperMap = new HashMap<>();
        Map<Integer, List<Match>> lowerMap = new HashMap<>();
        List<Match> grandFinals = new ArrayList<>();

        if (matchList != null) {
            for (Match m : matchList) {
                String bracketType = m.getBracketType();
                if ("LOSER_BRACKET".equalsIgnoreCase(bracketType) || "LOWER".equalsIgnoreCase(bracketType)) {
                    lowerMap.computeIfAbsent(m.getRoundNumber(), k -> new ArrayList<>()).add(m);
                } else if ("GRAND_FINAL".equalsIgnoreCase(bracketType) || "GRAND_FINALS".equalsIgnoreCase(bracketType)) {
                    grandFinals.add(m);
                } else {
                    upperMap.computeIfAbsent(m.getRoundNumber(), k -> new ArrayList<>()).add(m);
                }
            }
        }

        dataMap.put("upperMap", upperMap);
        dataMap.put("lowerMap", lowerMap);
        dataMap.put("grandFinals", grandFinals);
        return dataMap;
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
                    m.setRoundNumber(rs.getInt("round_number"));
                    m.setMatchNumber(numId);
                    m.setBracketType(rs.getString("bracket_type"));

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
     * Get JSON array of Double Elimination matches for frontend JavaScript engine
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
                    String bType = rs.getString("bracket_type");
                    String clientBType = "UPPER";
                    if ("LOSER_BRACKET".equalsIgnoreCase(bType)) clientBType = "LOWER";
                    else if ("GRAND_FINAL".equalsIgnoreCase(bType)) clientBType = "GRAND_FINAL";

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
                    sb.append("\"bracketType\":\"").append(clientBType).append("\",");
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
     * Batch Sync / Persist full Double Elimination bracket structure to 'matches' table.
     */
    public boolean syncBracketMatches(String tournamentId, int stageOrder, String matchesDataJson) {
        List<DEMatchDTO> list = parseDEMatchesJson(matchesDataJson);
        if (list.isEmpty()) {
            return false;
        }

        try (Connection conn = getConnection()) {
            String stageId = getOrCreateStageId(conn, tournamentId, stageOrder);
            Map<String, String> teamMap = getTeamNameToIdMap(conn, tournamentId);

            String mergeSql = "MERGE INTO matches AS target "
                    + "USING (SELECT ? AS id, ? AS tournament_id, ? AS stage_id, ? AS round_number, ? AS match_code, "
                    + "              ? AS bracket_type, ? AS team1_id, ? AS team2_id, ? AS score1, ? AS score2, "
                    + "              ? AS winner_id, ? AS next_match_id, ? AS next_slot, ? AS is_bye, ? AS status) AS source "
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
                    + "        target.next_match_id = source.next_match_id, "
                    + "        target.next_slot = source.next_slot, "
                    + "        target.is_bye = source.is_bye, "
                    + "        target.status = source.status "
                    + "WHEN NOT MATCHED THEN "
                    + "    INSERT (id, tournament_id, stage_id, round_number, match_code, bracket_type, "
                    + "            team1_id, team2_id, score1, score2, winner_id, next_match_id, next_slot, is_bye, status) "
                    + "    VALUES (source.id, source.tournament_id, source.stage_id, source.round_number, source.match_code, source.bracket_type, "
                    + "            source.team1_id, source.team2_id, source.score1, source.score2, source.winner_id, source.next_match_id, source.next_slot, source.is_bye, source.status);";

            // Pass 1: Upsert all matches with next_match_id = NULL
            try (PreparedStatement ps1 = conn.prepareStatement(mergeSql)) {
                for (DEMatchDTO m : list) {
                    String matchDbId = buildMatchDbId(tournamentId, stageOrder, m.matchId);
                    String t1Id = lookupTeamId(teamMap, m.team1Name, m.team1Seed);
                    String t2Id = lookupTeamId(teamMap, m.team2Name, m.team2Seed);
                    String winnerId = "team1".equalsIgnoreCase(m.winnerId) ? t1Id : ("team2".equalsIgnoreCase(m.winnerId) ? t2Id : null);
                    String status = ("COMPLETED".equalsIgnoreCase(m.status) || "FINISHED".equalsIgnoreCase(m.status) || (m.team1Score != null && m.team2Score != null)) ? "FINISHED" : "PENDING";
                    String nextSlotStr = (m.nextMatchSlot != null && m.nextMatchSlot == 2) ? "SLOT_2" : ((m.nextMatchSlot != null && m.nextMatchSlot == 1) ? "SLOT_1" : null);

                    String dbBracketType = "WINNER_BRACKET";
                    if ("LOWER".equalsIgnoreCase(m.bracketType) || "LOSER_BRACKET".equalsIgnoreCase(m.bracketType)) {
                        dbBracketType = "LOSER_BRACKET";
                    } else if ("GRAND_FINAL".equalsIgnoreCase(m.bracketType) || "GRAND_FINALS".equalsIgnoreCase(m.bracketType)) {
                        dbBracketType = "GRAND_FINAL";
                    }

                    ps1.setString(1, matchDbId);
                    ps1.setString(2, tournamentId);
                    ps1.setString(3, stageId);
                    ps1.setInt(4, m.roundNumber);
                    ps1.setString(5, "Trận #" + m.matchNumber);
                    ps1.setString(6, dbBracketType);
                    setNullableString(ps1, 7, t1Id);
                    setNullableString(ps1, 8, t2Id);
                    setNullableInt(ps1, 9, m.team1Score);
                    setNullableInt(ps1, 10, m.team2Score);
                    setNullableString(ps1, 11, winnerId);
                    ps1.setNull(12, Types.VARCHAR); // Null initially
                    setNullableString(ps1, 13, nextSlotStr);
                    ps1.setBoolean(14, m.isBye);
                    ps1.setString(15, status);
                    ps1.addBatch();
                }
                ps1.executeBatch();
            }

            // Pass 2: Link next_match_id
            String updateNextSql = "UPDATE matches SET next_match_id = ? WHERE id = ?";
            try (PreparedStatement ps2 = conn.prepareStatement(updateNextSql)) {
                for (DEMatchDTO m : list) {
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
     * Real-time Update match score in database and advance winner
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

            // 1. Update match score and status
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
                        + "VALUES (?, ?, ?, 1, ?, 'WINNER_BRACKET', ?, ?, ?, ?, ?, 'FINISHED')";
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

            // 2. Advance winner to next match if present
            if (winnerId != null) {
                String selectNextSql = "SELECT next_match_id, next_slot FROM matches WHERE id = ? OR id LIKE ?";
                String nextMatchId = null;
                String nextSlot = null;

                try (PreparedStatement psSel = conn.prepareStatement(selectNextSql)) {
                    psSel.setString(1, matchDbId);
                    psSel.setString(2, "%_M" + matchId);
                    try (ResultSet rs = psSel.executeQuery()) {
                        if (rs.next()) {
                            nextMatchId = rs.getString("next_match_id");
                            nextSlot = rs.getString("next_slot");
                        }
                    }
                }

                if (nextMatchId != null && !nextMatchId.trim().isEmpty()) {
                    String advSql;
                    if ("SLOT_2".equalsIgnoreCase(nextSlot)) {
                        advSql = "UPDATE matches SET team2_id = ? WHERE id = ?";
                    } else {
                        advSql = "UPDATE matches SET team1_id = ? WHERE id = ?";
                    }
                    try (PreparedStatement psAdv = conn.prepareStatement(advSql)) {
                        psAdv.setString(1, winnerId);
                        psAdv.setString(2, nextMatchId);
                        psAdv.executeUpdate();
                    }
                }
            }

            try {
                String sId = new TournamentDAO().getSeriesIdByTournamentId(tournamentId);
                if (sId != null && !sId.trim().isEmpty()) {
                    new SeriesDAO().recalculateSeriesStandings(sId.trim());
                }
            } catch (Exception ignore) {}

            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Backward-compatible updateMatchScore
     */
    public boolean updateMatchScore(int matchId, int score1, int score2, String winnerFlag) {
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
     * Reset bracket matches for a stage
     */
    public boolean resetBracketMatches(String tournamentId, int stageOrder) {
        if (tournamentId == null || tournamentId.trim().isEmpty()) {
            return false;
        }

        try (Connection conn = getConnection()) {
            String stageId = getOrCreateStageId(conn, tournamentId, stageOrder);

            // Step 1: Break FK references between matches
            String breakFkSql = "UPDATE matches SET next_match_id = NULL WHERE tournament_id = ? AND stage_id = ?";
            try (PreparedStatement psFk = conn.prepareStatement(breakFkSql)) {
                psFk.setString(1, tournamentId);
                psFk.setString(2, stageId);
                psFk.executeUpdate();
            }

            // Step 2: Delete matches for this stage
            String delSql = "DELETE FROM matches WHERE tournament_id = ? AND stage_id = ?";
            try (PreparedStatement psDel = conn.prepareStatement(delSql)) {
                psDel.setString(1, tournamentId);
                psDel.setString(2, stageId);
                psDel.executeUpdate();
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
        String stageName = (stageOrder == 2) ? "Giai đoạn 2 (Double Elimination)" : "Giai đoạn 1 (Double Elimination)";
        String insertStage = "INSERT INTO tournament_stages (id, tournament_id, stage_order, stage_name, format, status) "
                + "VALUES (?, ?, ?, ?, 'DOUBLE_ELIMINATION', 'PENDING')";
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

    private String lookupTeamId(Map<String, String> teamMap, String teamName, Integer teamSeed) {
        if (teamMap == null || teamMap.isEmpty()) return null;

        if (teamName != null && !teamName.trim().isEmpty()) {
            String norm = teamName.trim().toLowerCase();
            if (norm.startsWith("hạt giống #")) {
                try {
                    int s = Integer.parseInt(norm.replace("hạt giống #", "").trim());
                    String id = teamMap.get("seed_" + s);
                    if (id != null) return id;
                } catch (Exception ignore) {}
            }
            String direct = teamMap.get(norm);
            if (direct != null) return direct;
        }

        if (teamSeed != null && teamSeed > 0) {
            String seedMatch = teamMap.get("seed_" + teamSeed);
            if (seedMatch != null) return seedMatch;
        }

        return null;
    }

    private String buildMatchDbId(String tournamentId, int stageOrder, int matchId) {
        return tournamentId + "_S" + stageOrder + "_M" + matchId;
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

    /**
     * Minimal JSON parser for Double Elimination Match list
     */
    private List<DEMatchDTO> parseDEMatchesJson(String json) {
        List<DEMatchDTO> list = new ArrayList<>();
        if (json == null || json.trim().isEmpty()) return list;

        Pattern objPattern = Pattern.compile("\\{([^{}]+(?:\\{[^{}]*\\}[^{}]*)*)\\}");
        Matcher objMatcher = objPattern.matcher(json);

        while (objMatcher.find()) {
            String block = objMatcher.group(1);
            DEMatchDTO dto = new DEMatchDTO();

            dto.matchId = parseJsonInt(block, "matchId", parseJsonInt(block, "id", 0));
            dto.bracketType = parseJsonString(block, "bracketType", "UPPER");
            dto.roundNumber = parseJsonInt(block, "roundNumber", 1);
            dto.matchNumber = parseJsonInt(block, "matchNumber", dto.matchId);

            String t1Block = extractSubObject(block, "team1");
            if (t1Block != null) {
                dto.team1Name = parseJsonString(t1Block, "name", null);
                dto.team1Seed = parseJsonNullableInt(t1Block, "seed");
                dto.team1Score = parseJsonNullableInt(t1Block, "score");
            }

            String t2Block = extractSubObject(block, "team2");
            if (t2Block != null) {
                dto.team2Name = parseJsonString(t2Block, "name", null);
                dto.team2Seed = parseJsonNullableInt(t2Block, "seed");
                dto.team2Score = parseJsonNullableInt(t2Block, "score");
            }

            dto.winnerId = parseJsonString(block, "winnerId", null);
            dto.nextMatchId = parseJsonNullableInt(block, "nextMatchId");
            dto.nextMatchSlot = parseJsonNullableInt(block, "nextMatchSlot");
            dto.isBye = parseJsonBoolean(block, "isBye", false);
            dto.status = parseJsonString(block, "status", "PENDING");

            if (dto.matchId > 0) {
                list.add(dto);
            }
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
        Pattern p = Pattern.compile("\"" + key + "\"\\s*:\\s*\"?([^\",}]+)\"?");
        Matcher m = p.matcher(src);
        if (m.find()) {
            String val = m.group(1).trim();
            try { return Integer.parseInt(val); } catch (Exception ignore) {}
            Matcher numM = Pattern.compile("(\\d+)$").matcher(val);
            if (numM.find()) {
                try { return Integer.parseInt(numM.group(1)); } catch (Exception ignore) {}
            }
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

    private boolean parseJsonBoolean(String src, String key, boolean defaultVal) {
        Pattern p = Pattern.compile("\"" + key + "\"\\s*:\\s*(true|false)");
        Matcher m = p.matcher(src);
        if (m.find()) {
            return "true".equalsIgnoreCase(m.group(1));
        }
        return defaultVal;
    }
}
