package controller;

import dao.DBContext;
import dao.SingleEliminationDAO;
import dao.DoubleEliminationDAO;
import dao.RoundRobinDAO;
import dao.SwissSystemDAO;
import dao.GroupStageDAO;
import dao.TournamentDAO;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import model.Tournament;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * ============================================================================
 * TOURMA - CENTRALIZED TOURNAMENT RANDOM SERVLET (TournamentRandomServlet.java)
 * Unified, 100% Deadlock-Free Batch Random Score Generator for All Formats:
 * - Single Elimination (SE)
 * - Double Elimination (DE)
 * - Round Robin (RR)
 * - Swiss System
 * - Group Stage
 * ============================================================================
 */
@WebServlet(name = "TournamentRandomServlet", urlPatterns = { "/api/tournament-random", "/tournament-random",
        "/api/random-matches" })
public class TournamentRandomServlet extends HttpServlet {

    private final Random random = new Random();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        doPost(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        request.setCharacterEncoding("UTF-8");
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");

        PrintWriter out = response.getWriter();
        Map<String, String> params = extractParameters(request);

        String action = params.getOrDefault("action", "randomRound");
        String tournamentId = params.get("tournamentId");
        if (tournamentId == null || tournamentId.trim().isEmpty()) {
            tournamentId = params.get("tourneyId");
        }
        if (tournamentId == null || tournamentId.trim().isEmpty()) {
            tournamentId = params.get("id");
        }

        if (tournamentId == null || tournamentId.trim().isEmpty()) {
            out.print("{\"status\":\"error\",\"message\":\"Thiếu tournamentId!\"}");
            return;
        }

        tournamentId = tournamentId.trim();

        int stage = 1;
        try {
            String stageParam = params.get("stage");
            if (stageParam != null)
                stage = Integer.parseInt(stageParam.trim());
        } catch (Exception ignore) {
        }

        Integer roundNumber = parseInteger(params.get("roundNumber"));
        if (roundNumber == null)
            roundNumber = parseInteger(params.get("round"));

        String groupId = params.get("groupId");
        if (groupId == null)
            groupId = params.get("group");

        String bracketType = params.get("bracketType");
        Integer targetWinScore = parseInteger(params.get("targetScore"));
        if (targetWinScore == null)
            targetWinScore = parseInteger(params.get("winScore"));

        try {
            RandomResult result;
            if ("resetRound".equalsIgnoreCase(action)) {
                result = executeResetRound(tournamentId, stage, roundNumber, groupId, bracketType);
            } else {
                result = executeRandom(tournamentId, stage, action, roundNumber, groupId, bracketType, targetWinScore);
            }
            if (result.success) {
                out.print("{"
                        + "\"status\":\"success\","
                        + "\"message\":\"" + escapeJson(result.message) + "\","
                        + "\"tournamentId\":\"" + escapeJson(tournamentId) + "\","
                        + "\"updatedCount\":" + result.updatedCount + ","
                        + "\"matchesData\":" + (result.matchesJson != null ? result.matchesJson : "[]")
                        + "}");
            } else {
                out.print("{\"status\":\"error\",\"message\":\"" + escapeJson(result.message) + "\"}");
            }
        } catch (Exception e) {
            e.printStackTrace();
            out.print("{\"status\":\"error\",\"message\":\"Lỗi thực thi: " + escapeJson(e.getMessage()) + "\"}");
        }
    }

    public static class RandomResult {
        public boolean success = false;
        public String message = "";
        public int updatedCount = 0;
        public String matchesJson = "[]";
    }

    private static class MatchToRandom {
        String id;
        int roundNumber;
        String bracketType;
        String team1Id;
        String team2Id;
        String nextMatchId;
        String nextSlot;
        String loserNextMatchId;
        String loserNextSlot;
        String groupId;
    }

    /**
     * Core Sequential Batch Execution (100% Deadlock-Free)
     */
    public synchronized RandomResult executeRandom(String tournamentId, int stageOrder, String action,
            Integer roundNumber, String groupId, String bracketType,
            Integer targetWinScore) {
        RandomResult res = new RandomResult();
        DBContext db = new DBContext();

        // 1. Resolve Tournament format
        TournamentDAO tourneyDao = new TournamentDAO();
        Tournament t = tourneyDao.getTournamentById(tournamentId);
        String format = (t != null && t.getFormat() != null) ? t.getFormat().trim().toUpperCase()
                : "SINGLE_ELIMINATION";

        boolean allowDraw = format.contains("ROUND") || format.contains("ROBIN") || format.contains("GROUP");

        // 2. Fetch playable matches for requested scope
        StringBuilder sql = new StringBuilder("SELECT m.id, m.round_number, m.bracket_type, m.team1_id, m.team2_id, "
                + "m.next_match_id, m.next_slot, m.loser_next_match_id, m.loser_next_slot, m.group_id "
                + "FROM matches m "
                + "LEFT JOIN tournament_stages s ON m.stage_id = s.id "
                + "WHERE m.tournament_id = ? "
                + "AND (s.stage_order = ? OR (s.stage_order IS NULL AND ? = 1) OR m.stage_id LIKE '%_S' + CAST(? AS VARCHAR) + '%' OR m.stage_id = 'STAGE_' + CAST(? AS VARCHAR)) "
                + "AND m.team1_id IS NOT NULL AND m.team2_id IS NOT NULL "
                + "AND (m.is_bye IS NULL OR m.is_bye = 0) ");

        if ("randomRound".equalsIgnoreCase(action) && roundNumber != null) {
            sql.append("AND m.round_number = ? ");
        } else if ("randomGroup".equalsIgnoreCase(action) && groupId != null && !groupId.trim().isEmpty()) {
            sql.append("AND m.group_id = ? ");
        }

        if (bracketType != null && !bracketType.trim().isEmpty()) {
            sql.append("AND m.bracket_type = ? ");
        }

        sql.append("ORDER BY m.round_number ASC, LEN(m.id) ASC, m.id ASC");

        List<MatchToRandom> matchList = new ArrayList<>();

        try (Connection conn = db.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            int pIdx = 1;
            ps.setString(pIdx++, tournamentId);
            ps.setInt(pIdx++, stageOrder);
            ps.setInt(pIdx++, stageOrder);
            ps.setInt(pIdx++, stageOrder);
            ps.setInt(pIdx++, stageOrder);

            if ("randomRound".equalsIgnoreCase(action) && roundNumber != null) {
                ps.setInt(pIdx++, roundNumber);
            } else if ("randomGroup".equalsIgnoreCase(action) && groupId != null && !groupId.trim().isEmpty()) {
                ps.setString(pIdx++, groupId.trim());
            }

            if (bracketType != null && !bracketType.trim().isEmpty()) {
                ps.setString(pIdx++, bracketType.trim());
            }

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    MatchToRandom item = new MatchToRandom();
                    item.id = rs.getString("id");
                    item.roundNumber = rs.getInt("round_number");
                    item.bracketType = rs.getString("bracket_type");
                    item.team1Id = rs.getString("team1_id");
                    item.team2Id = rs.getString("team2_id");
                    item.nextMatchId = rs.getString("next_match_id");
                    item.nextSlot = rs.getString("next_slot");
                    item.loserNextMatchId = rs.getString("loser_next_match_id");
                    item.loserNextSlot = rs.getString("loser_next_slot");
                    item.groupId = rs.getString("group_id");
                    matchList.add(item);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            res.message = "Lỗi truy vấn danh sách trận: " + e.getMessage();
            return res;
        }

        if (matchList.isEmpty()) {
            res.success = true;
            res.message = "Không có trận đấu nào khả dụng cần random.";
            res.updatedCount = 0;
            return res;
        }

        // 3. Process matches in single transaction sequentially
        try (Connection conn = db.getConnection()) {
            conn.setAutoCommit(false);

            String updateMatchSql = "UPDATE matches SET score1 = ?, score2 = ?, winner_id = ?, loser_id = ?, status = 'FINISHED' WHERE id = ?";
            String advSql = "UPDATE matches SET %SLOT% = ?, status = CASE WHEN (%OTHER_SLOT% IS NOT NULL) THEN 'READY' ELSE status END "
                    + "WHERE tournament_id = ? AND (id = ? OR id LIKE '%[_]' + ? OR id LIKE '%[_]S1[_]' + ? OR id LIKE '%[_]S2[_]' + ? OR match_code = ? OR match_code = 'Match #' + ? OR CAST(match_order AS VARCHAR) = ?)";

            for (MatchToRandom m : matchList) {
                // Generate realistic score
                int s1, s2;
                String winnerId = null;
                String loserId = null;

                if (targetWinScore != null && targetWinScore > 0) {
                    boolean t1Wins = random.nextBoolean();
                    int loserScore = random.nextInt(targetWinScore);
                    s1 = t1Wins ? targetWinScore : loserScore;
                    s2 = t1Wins ? loserScore : targetWinScore;
                    winnerId = t1Wins ? m.team1Id : m.team2Id;
                    loserId = t1Wins ? m.team2Id : m.team1Id;
                } else if (allowDraw && random.nextDouble() < 0.22) {
                    // Draw match for Round Robin / Group Stage
                    int drawScore = random.nextInt(4); // 0-0, 1-1, 2-2, 3-3
                    s1 = drawScore;
                    s2 = drawScore;
                    winnerId = null;
                    loserId = null;
                } else {
                    // Decisive victory
                    int winScore = (random.nextDouble() < 0.75) ? (random.nextInt(4) + 2) : (random.nextInt(4) + 6);
                    int loseScore = random.nextInt(winScore);
                    boolean t1Wins = random.nextBoolean();
                    s1 = t1Wins ? winScore : loseScore;
                    s2 = t1Wins ? loseScore : winScore;
                    winnerId = t1Wins ? m.team1Id : m.team2Id;
                    loserId = t1Wins ? m.team2Id : m.team1Id;
                }

                // Update match record
                try (PreparedStatement psUp = conn.prepareStatement(updateMatchSql)) {
                    psUp.setInt(1, s1);
                    psUp.setInt(2, s2);
                    if (winnerId != null)
                        psUp.setString(3, winnerId);
                    else
                        psUp.setNull(3, Types.VARCHAR);
                    if (loserId != null)
                        psUp.setString(4, loserId);
                    else
                        psUp.setNull(4, Types.VARCHAR);
                    psUp.setString(5, m.id);
                    psUp.executeUpdate();
                }

                // Advance winner to next_match_id
                if (winnerId != null && m.nextMatchId != null && !m.nextMatchId.trim().isEmpty()) {
                    boolean isSlot2 = "SLOT_2".equalsIgnoreCase(m.nextSlot) || "2".equals(m.nextSlot);
                    String slotCol = isSlot2 ? "team2_id" : "team1_id";
                    String otherSlotCol = isSlot2 ? "team1_id" : "team2_id";
                    String query = advSql.replace("%SLOT%", slotCol).replace("%OTHER_SLOT%", otherSlotCol);

                    try (PreparedStatement psAdv = conn.prepareStatement(query)) {
                        psAdv.setString(1, winnerId);
                        psAdv.setString(2, tournamentId);
                        psAdv.setString(3, m.nextMatchId);
                        psAdv.setString(4, m.nextMatchId);
                        psAdv.setString(5, m.nextMatchId);
                        psAdv.setString(6, m.nextMatchId);
                        psAdv.setString(7, m.nextMatchId);
                        psAdv.setString(8, m.nextMatchId);
                        psAdv.setString(9, m.nextMatchId);
                        psAdv.executeUpdate();
                    }
                }

                // Advance loser to loser_next_match_id (for Double Elimination Lower Bracket)
                if (loserId != null && m.loserNextMatchId != null && !m.loserNextMatchId.trim().isEmpty()) {
                    boolean isSlot2 = "SLOT_2".equalsIgnoreCase(m.loserNextSlot) || "2".equals(m.loserNextSlot);
                    String slotCol = isSlot2 ? "team2_id" : "team1_id";
                    String otherSlotCol = isSlot2 ? "team1_id" : "team2_id";
                    String query = advSql.replace("%SLOT%", slotCol).replace("%OTHER_SLOT%", otherSlotCol);

                    try (PreparedStatement psDrop = conn.prepareStatement(query)) {
                        psDrop.setString(1, loserId);
                        psDrop.setString(2, tournamentId);
                        psDrop.setString(3, m.loserNextMatchId);
                        psDrop.setString(4, m.loserNextMatchId);
                        psDrop.setString(5, m.loserNextMatchId);
                        psDrop.setString(6, m.loserNextMatchId);
                        psDrop.setString(7, m.loserNextMatchId);
                        psDrop.setString(8, m.loserNextMatchId);
                        psDrop.setString(9, m.loserNextMatchId);
                        psDrop.executeUpdate();
                    }
                }

                // Update Group Standings if match belongs to a group
                if (m.groupId != null && !m.groupId.trim().isEmpty()) {
                    MatchUpdateServlet.updateGroupTableStandings(conn, m.groupId);
                }
            }

            conn.commit();
            res.success = true;
            res.updatedCount = matchList.size();
            res.message = "Đã tạo tỉ số ngẫu nhiên cho " + matchList.size() + " trận đấu thành công!";

            // Fetch fresh matches JSON to return to frontend
            if (format.contains("SINGLE")) {
                res.matchesJson = new SingleEliminationDAO().getMatchesJsonForFrontend(tournamentId, stageOrder);
            } else if (format.contains("DOUBLE")) {
                res.matchesJson = new DoubleEliminationDAO().getMatchesJsonForFrontend(tournamentId, stageOrder);
            } else if (format.contains("ROUND") || format.contains("ROBIN")) {
                res.matchesJson = new RoundRobinDAO().getMatchesJsonForFrontend(tournamentId, stageOrder);
            } else if (format.contains("SWISS")) {
                res.matchesJson = new SwissSystemDAO().getMatchesJsonForFrontend(tournamentId, stageOrder);
            } else if (format.contains("GROUP")) {
                res.matchesJson = new GroupStageDAO().getMatchesJsonForFrontend(tournamentId, stageOrder);
            }

        } catch (Exception e) {
            e.printStackTrace();
            res.message = "Lỗi cập nhật CSDL: " + e.getMessage();
            return res;
        }

        return res;
    }

    public synchronized RandomResult executeResetRound(String tournamentId, int stageOrder, Integer roundNumber,
            String groupId, String bracketType) {
        RandomResult res = new RandomResult();
        if (roundNumber == null) {
            res.message = "Thiếu roundNumber!";
            return res;
        }

        TournamentDAO tourneyDao = new TournamentDAO();
        Tournament t = tourneyDao.getTournamentById(tournamentId);
        String format = (t != null && t.getFormat() != null) ? t.getFormat().trim().toUpperCase()
                : "SINGLE_ELIMINATION";

        DBContext db = new DBContext();
        try (Connection conn = db.getConnection()) {
            conn.setAutoCommit(false);

            // 1. Fetch only playable non-BYE matches of this round to reset
            String selSql = "SELECT m.id, m.is_bye, m.team1_id, m.team2_id, m.next_match_id, m.next_slot, m.loser_next_match_id, m.loser_next_slot, m.group_id "
                    + "FROM matches m "
                    + "LEFT JOIN tournament_stages s ON m.stage_id = s.id "
                    + "WHERE m.tournament_id = ? AND m.round_number = ? "
                    + "AND (s.stage_order = ? OR (s.stage_order IS NULL AND ? = 1) OR m.stage_id LIKE '%_S' + CAST(? AS VARCHAR) + '%' OR m.stage_id = 'STAGE_' + CAST(? AS VARCHAR)) "
                    + "AND (m.is_bye IS NULL OR m.is_bye = 0)"
                    + (groupId != null && !groupId.trim().isEmpty() ? " AND m.group_id = ?" : "")
                    + (bracketType != null && !bracketType.trim().isEmpty() ? " AND m.bracket_type = ?" : "");

            List<MatchToRandom> roundMatches = new ArrayList<>();
            try (PreparedStatement psSel = conn.prepareStatement(selSql)) {
                int p = 1;
                psSel.setString(p++, tournamentId);
                psSel.setInt(p++, roundNumber);
                psSel.setInt(p++, stageOrder);
                psSel.setInt(p++, stageOrder);
                psSel.setInt(p++, stageOrder);
                psSel.setInt(p++, stageOrder);
                if (groupId != null && !groupId.trim().isEmpty())
                    psSel.setString(p++, groupId.trim());
                if (bracketType != null && !bracketType.trim().isEmpty())
                    psSel.setString(p++, bracketType.trim());

                try (ResultSet rs = psSel.executeQuery()) {
                    while (rs.next()) {
                        MatchToRandom item = new MatchToRandom();
                        item.id = rs.getString("id");
                        item.team1Id = rs.getString("team1_id");
                        item.team2Id = rs.getString("team2_id");
                        item.nextMatchId = rs.getString("next_match_id");
                        item.nextSlot = rs.getString("next_slot");
                        item.loserNextMatchId = rs.getString("loser_next_match_id");
                        item.loserNextSlot = rs.getString("loser_next_slot");
                        item.groupId = rs.getString("group_id");
                        roundMatches.add(item);
                    }
                }
            }

            if (roundMatches.isEmpty()) {
                res.success = true;
                res.message = "Không tìm thấy trận đấu nào cần đặt lại của Vòng " + roundNumber;
                // Still return fresh JSON
                if (format.contains("SINGLE")) {
                    res.matchesJson = new SingleEliminationDAO().getMatchesJsonForFrontend(tournamentId, stageOrder);
                }
                return res;
            }

            // 2. Clear scores and winners for all non-BYE matches of this round
            String updateRoundSql = "UPDATE matches SET score1 = NULL, score2 = NULL, penalty1 = NULL, penalty2 = NULL, "
                    + "winner_id = NULL, loser_id = NULL, "
                    + "status = CASE WHEN team1_id IS NOT NULL AND team2_id IS NOT NULL THEN 'READY' ELSE 'PENDING' END "
                    + "WHERE id = ? AND (is_bye IS NULL OR is_bye = 0)";

            for (MatchToRandom rm : roundMatches) {
                try (PreparedStatement psUp = conn.prepareStatement(updateRoundSql)) {
                    psUp.setString(1, rm.id);
                    psUp.executeUpdate();
                }

                // Clear downstream matches
                if (rm.nextMatchId != null) {
                    clearDownstreamMatch(conn, tournamentId, rm.nextMatchId, rm.nextSlot);
                }
                if (rm.loserNextMatchId != null) {
                    clearDownstreamMatch(conn, tournamentId, rm.loserNextMatchId, rm.loserNextSlot);
                }

                if (rm.groupId != null && !rm.groupId.trim().isEmpty()) {
                    MatchUpdateServlet.updateGroupTableStandings(conn, rm.groupId);
                }
            }

            // 3. Reset tournament status to DRAFT if completed
            String resetTourneySql = "UPDATE tournaments SET status = 'DRAFT', champion_name = NULL WHERE id = ? AND status = 'COMPLETED'";
            try (PreparedStatement psT = conn.prepareStatement(resetTourneySql)) {
                psT.setString(1, tournamentId);
                psT.executeUpdate();
            } catch (Exception ignore) {
            }

            conn.commit();
            service.RollingWindowPointService.clearAllCaches();

            // 4. Ensure all BYE winners remain properly linked downstream in DB
            if (format.contains("SINGLE")) {
                new SingleEliminationDAO().syncByeAdvancementsInDB(tournamentId);
            }

            res.success = true;
            res.updatedCount = roundMatches.size();
            res.message = "Đã đặt lại kết quả Vòng " + roundNumber + " thành công!";

            // 5. Fetch fresh matches JSON
            if (format.contains("SINGLE")) {
                res.matchesJson = new SingleEliminationDAO().getMatchesJsonForFrontend(tournamentId, stageOrder);
            } else if (format.contains("DOUBLE")) {
                res.matchesJson = new DoubleEliminationDAO().getMatchesJsonForFrontend(tournamentId, stageOrder);
            } else if (format.contains("ROUND") || format.contains("ROBIN")) {
                res.matchesJson = new RoundRobinDAO().getMatchesJsonForFrontend(tournamentId, stageOrder);
            } else if (format.contains("SWISS")) {
                res.matchesJson = new SwissSystemDAO().getMatchesJsonForFrontend(tournamentId, stageOrder);
            } else if (format.contains("GROUP")) {
                res.matchesJson = new GroupStageDAO().getMatchesJsonForFrontend(tournamentId, stageOrder);
            }

        } catch (Exception e) {
            e.printStackTrace();
            res.message = "Lỗi đặt lại vòng đấu: " + e.getMessage();
            return res;
        }

        return res;
    }

    private void clearDownstreamMatch(Connection conn, String tournamentId, String nextMatchId, String nextSlot) {
        if (nextMatchId == null || nextMatchId.trim().isEmpty() || tournamentId == null)
            return;
        String slotCol = ("SLOT_2".equalsIgnoreCase(nextSlot) || "2".equals(nextSlot)) ? "team2_id" : "team1_id";

        String selSql = "SELECT id, next_match_id, next_slot, loser_next_match_id, loser_next_slot FROM matches WHERE tournament_id = ? AND (id = ? OR id LIKE '%[_]' + ?)";
        String dbNextId = null;
        String downstreamNextId = null;
        String downstreamNextSlot = null;
        String downstreamLoserNextId = null;
        String downstreamLoserNextSlot = null;

        try (PreparedStatement psSel = conn.prepareStatement(selSql)) {
            psSel.setString(1, tournamentId);
            psSel.setString(2, nextMatchId);
            psSel.setString(3, nextMatchId);
            try (ResultSet rs = psSel.executeQuery()) {
                if (rs.next()) {
                    dbNextId = rs.getString("id");
                    downstreamNextId = rs.getString("next_match_id");
                    downstreamNextSlot = rs.getString("next_slot");
                    downstreamLoserNextId = rs.getString("loser_next_match_id");
                    downstreamLoserNextSlot = rs.getString("loser_next_slot");
                }
            }
        } catch (Exception ignore) {
        }

        if (dbNextId == null)
            return;

        String updateSql = "UPDATE matches SET " + slotCol
                + " = NULL, score1 = NULL, score2 = NULL, penalty1 = NULL, penalty2 = NULL, winner_id = NULL, loser_id = NULL, status = 'PENDING' WHERE id = ?";
        try (PreparedStatement psUp = conn.prepareStatement(updateSql)) {
            psUp.setString(1, dbNextId);
            psUp.executeUpdate();
        } catch (Exception ignore) {
        }

        if (downstreamNextId != null && !downstreamNextId.trim().isEmpty()) {
            clearDownstreamMatch(conn, tournamentId, downstreamNextId, downstreamNextSlot);
        }
        if (downstreamLoserNextId != null && !downstreamLoserNextId.trim().isEmpty()) {
            clearDownstreamMatch(conn, tournamentId, downstreamLoserNextId, downstreamLoserNextSlot);
        }
    }

    private Integer parseInteger(String val) {
        if (val == null || val.trim().isEmpty())
            return null;
        try {
            return Integer.parseInt(val.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private String escapeJson(String s) {
        if (s == null)
            return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }

    private Map<String, String> extractParameters(HttpServletRequest request) {
        Map<String, String> map = new HashMap<>();

        for (Map.Entry<String, String[]> entry : request.getParameterMap().entrySet()) {
            if (entry.getValue() != null && entry.getValue().length > 0) {
                map.put(entry.getKey(), entry.getValue()[0]);
            }
        }

        String contentType = request.getContentType();
        if (contentType != null && contentType.toLowerCase().contains("application/json")) {
            try {
                StringBuilder sb = new StringBuilder();
                try (BufferedReader reader = request.getReader()) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                    }
                }
                String jsonStr = sb.toString().trim();
                if (jsonStr.startsWith("{") && jsonStr.endsWith("}")) {
                    String content = jsonStr.substring(1, jsonStr.length() - 1);
                    String[] pairs = content.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");
                    for (String pair : pairs) {
                        String[] kv = pair.split(":(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");
                        if (kv.length == 2) {
                            String k = kv[0].trim().replace("\"", "");
                            String v = kv[1].trim().replace("\"", "");
                            if (!"null".equalsIgnoreCase(v)) {
                                map.put(k, v);
                            }
                        }
                    }
                }
            } catch (Exception ignore) {
            }
        }

        return map;
    }
}
