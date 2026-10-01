package dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import model.Match;
import model.Team;
import service.JsonParser;
import service.MatchPersistenceService;

/**
 * High-performance, clean DAO for Double Elimination Tournament Format Operations.
 * Manages Upper Bracket, Lower Bracket, Grand Finals, and Drop-down links 100% in SQL Server.
 */
public class DoubleEliminationDAO extends DBContext {

    public static class DEMatchDTO {
        public int matchId;
        public String bracketType;
        public int roundNumber;
        public int matchNumber;
        public String team1Name;
        public Integer team1Seed;
        public Integer team1Score;
        public String team2Name;
        public Integer team2Seed;
        public Integer team2Score;
        public String winnerId;
        public Integer nextMatchId;
        public Integer nextMatchSlot;
        public Integer dropToMatchId;
        public Integer dropToMatchSlot;
        public boolean isBye;
        public String status;
    }

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

    public List<Match> getMatchesByTournamentId(int tournamentId) {
        return getMatchesByTournamentId(String.valueOf(tournamentId));
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

                    String nextId = rs.getString("next_match_id");
                    if (nextId != null) m.setNextMatchId(parseNumericMatchId(nextId, 0));
                    String nextSlot = rs.getString("next_slot");
                    m.setNextMatchSlot("SLOT_2".equalsIgnoreCase(nextSlot) ? 2 : 1);

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
                    String bType = rs.getString("bracket_type");
                    String clientBType = "UPPER";
                    if ("LOSER_BRACKET".equalsIgnoreCase(bType) || "LOWER".equalsIgnoreCase(bType)) clientBType = "LOWER";
                    else if ("GRAND_FINAL".equalsIgnoreCase(bType) || "GRAND_FINALS".equalsIgnoreCase(bType)) clientBType = "GRAND_FINAL";

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

                    String nextIdStr = rs.getString("next_match_id");
                    Integer nextMatchId = (nextIdStr != null && !nextIdStr.isEmpty()) ? parseNumericMatchId(nextIdStr, 0) : null;
                    String nextSlot = rs.getString("next_slot");
                    int nextSlotNum = "SLOT_2".equalsIgnoreCase(nextSlot) ? 2 : 1;

                    String dropIdStr = rs.getString("loser_next_match_id");
                    Integer dropMatchId = (dropIdStr != null && !dropIdStr.isEmpty()) ? parseNumericMatchId(dropIdStr, 0) : null;
                    String dropSlot = rs.getString("loser_next_slot");
                    int dropSlotNum = "SLOT_2".equalsIgnoreCase(dropSlot) ? 2 : 1;

                    boolean isBye = rs.getBoolean("is_bye");
                    String status = rs.getString("status");
                    if ("FINISHED".equalsIgnoreCase(status)) status = "COMPLETED";

                    sb.append("{")
                      .append("\"matchId\":").append(matchId).append(",")
                      .append("\"id\":").append(matchId).append(",")
                      .append("\"bracketType\":\"").append(clientBType).append("\",")
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
                      .append("\"nextMatchId\":").append(nextMatchId != null ? nextMatchId : "null").append(",")
                      .append("\"nextMatchSlot\":").append(nextSlotNum).append(",")
                      .append("\"dropToMatchId\":").append(dropMatchId != null ? dropMatchId : "null").append(",")
                      .append("\"dropToMatchSlot\":").append(dropSlotNum).append(",")
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

    public boolean syncBracketMatches(String tournamentId, int stageOrder, String matchesJson) {
        if (tournamentId == null || matchesJson == null) return false;
        List<Object> list = JsonParser.parseList(matchesJson);
        if (list == null || list.isEmpty()) return false;

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

    public boolean updateMatchScoreAndAdvance(String tournamentId, int stageOrder, String matchId, Integer score1, Integer score2, String winnerFlag, String team1Name, String team2Name) {
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

    public boolean updateMatchScoreAndAdvance(String tournamentId, int stageOrder, int matchId, Integer score1, Integer score2, String winnerFlag, String team1Name, String team2Name) {
        return updateMatchScoreAndAdvance(tournamentId, stageOrder, String.valueOf(matchId), score1, score2, winnerFlag, team1Name, team2Name);
    }

    public boolean updateMatchScore(String matchId, Integer score1, Integer score2, String winnerFlag) {
        return updateMatchScoreAndAdvance(null, 1, matchId, score1, score2, winnerFlag, null, null);
    }

    public boolean updateMatchScore(int matchId, Integer score1, Integer score2, String winnerFlag) {
        return updateMatchScoreAndAdvance(null, 1, String.valueOf(matchId), score1, score2, winnerFlag, null, null);
    }

    public boolean resetBracketMatches(String tournamentId, int stageOrder) {
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
