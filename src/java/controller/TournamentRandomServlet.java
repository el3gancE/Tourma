package controller;

import dao.DBContext;
import dao.SingleEliminationDAO;
import dao.DoubleEliminationDAO;
import dao.RoundRobinDAO;
import dao.SwissSystemDAO;
import dao.GroupStageDAO;
import dao.GSLStageDAO;
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
import java.util.Collections;
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
        String rawGroupId = params.get("rawGroupId");

        String bracketType = params.get("bracketType");
        Integer targetWinScore = parseInteger(params.get("targetScore"));
        if (targetWinScore == null)
            targetWinScore = parseInteger(params.get("winScore"));

        try {
            RandomResult result;
            if ("resetRound".equalsIgnoreCase(action)) {
                result = executeResetRound(tournamentId, stage, roundNumber, groupId, bracketType);
            } else if ("resetGroup".equalsIgnoreCase(action)) {
                result = executeResetGroup(tournamentId, stage, groupId, rawGroupId);
            } else {
                result = executeRandom(tournamentId, stage, action, roundNumber, groupId, rawGroupId, bracketType, targetWinScore);
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

    public static String formatDisplayGroupName(String raw) {
        if (raw == null || raw.trim().isEmpty())
            return "Bảng A";
        String s = raw.trim();
        if (s.startsWith("Bảng ") || s.startsWith("Bảng")) {
            return s;
        }
        if (s.startsWith("Group ")) {
            return "Bảng " + s.substring(6).trim();
        }

        java.util.regex.Pattern p = java.util.regex.Pattern.compile("(?:B_NG_|GRP_.*_B_NG_|GRP_.*_G|GRP_.*_|Group_|Group|Bảng_|Bảng|G)?([A-Za-z]+|[0-9]+)$", java.util.regex.Pattern.CASE_INSENSITIVE);
        java.util.regex.Matcher m = p.matcher(s);
        if (m.find() && m.group(1) != null && !m.group(1).isEmpty()) {
            return "Bảng " + m.group(1).toUpperCase();
        }

        return s;
    }

    private List<String> getGroupFilterPatterns(String groupId, String rawGroupId) {
        List<String> patterns = new ArrayList<>();
        if (groupId != null && !groupId.trim().isEmpty()) {
            String g = groupId.trim();
            if (!patterns.contains(g)) patterns.add(g);
            String disp = formatDisplayGroupName(g);
            if (!patterns.contains(disp)) patterns.add(disp);
            String cleanPrefix = g.replaceAll("[^a-zA-Z0-9]", "_").toUpperCase();
            if (!patterns.contains(cleanPrefix)) patterns.add(cleanPrefix);

            java.util.regex.Matcher m = java.util.regex.Pattern.compile("([A-Za-z0-9]+)$").matcher(g);
            if (m.find()) {
                String suf = m.group(1).toUpperCase();
                if (!patterns.contains(suf)) patterns.add(suf);
                String bng = "B_NG_" + suf;
                if (!patterns.contains(bng)) patterns.add(bng);
                String grpName = "Bảng " + suf;
                if (!patterns.contains(grpName)) patterns.add(grpName);
                String grpEn = "Group " + suf;
                if (!patterns.contains(grpEn)) patterns.add(grpEn);
            }
        }
        if (rawGroupId != null && !rawGroupId.trim().isEmpty()) {
            String r = rawGroupId.trim();
            if (!patterns.contains(r)) patterns.add(r);
            String rDisp = formatDisplayGroupName(r);
            if (!patterns.contains(rDisp)) patterns.add(rDisp);
        }
        return patterns;
    }

    /**
     * Core Sequential Multi-Pass Batch Execution (100% Deadlock-Free)
     * For 'randomGroup' or 'randomAll', simulates subsequent rounds automatically
     * until all playable matches in scope are 100% completed.
     */
    public synchronized RandomResult executeRandom(String tournamentId, int stageOrder, String action,
            Integer roundNumber, String groupId, String bracketType,
            Integer targetWinScore) {
        return executeRandom(tournamentId, stageOrder, action, roundNumber, groupId, null, bracketType, targetWinScore);
    }

    public synchronized RandomResult executeRandom(String tournamentId, int stageOrder, String action,
            Integer roundNumber, String groupId, String rawGroupId, String bracketType,
            Integer targetWinScore) {
        int maxRetries = 3;
        while (maxRetries-- > 0) {
            RandomResult res = tryExecuteRandom(tournamentId, stageOrder, action, roundNumber, groupId, rawGroupId, bracketType, targetWinScore);
            if (res.success || res.message == null || (!res.message.contains("deadlock") && !res.message.contains("1205"))) {
                return res;
            }
            try {
                Thread.sleep(80 + random.nextInt(120));
            } catch (InterruptedException ignore) {
            }
        }
        return tryExecuteRandom(tournamentId, stageOrder, action, roundNumber, groupId, rawGroupId, bracketType, targetWinScore);
    }

    private RandomResult tryExecuteRandom(String tournamentId, int stageOrder, String action,
            Integer roundNumber, String groupId, String rawGroupId, String bracketType,
            Integer targetWinScore) {
        RandomResult res = new RandomResult();
        DBContext db = new DBContext();

        // 1. Resolve Tournament format
        TournamentDAO tourneyDao = new TournamentDAO();
        Tournament t = tourneyDao.getTournamentById(tournamentId);
        String format = (t != null && t.getFormat() != null) ? t.getFormat().trim().toUpperCase()
                : "SINGLE_ELIMINATION";

        if (format.contains("SWISS")) {
            SwissSystemDAO swissDao = new SwissSystemDAO();
            boolean ok;
            if ("randomRound".equalsIgnoreCase(action)) {
                ok = swissDao.randomRoundInDB(tournamentId, stageOrder, (roundNumber != null ? roundNumber : 1), (targetWinScore != null ? targetWinScore : 3));
            } else {
                ok = swissDao.randomAllInDB(tournamentId, stageOrder, (targetWinScore != null ? targetWinScore : 3));
            }
            res.success = ok;
            res.message = ok ? "Đã tạo tỉ số ngẫu nhiên cho thể thức Swiss thành công!" : "Lỗi khi random thể thức Swiss!";
            res.matchesJson = swissDao.getMatchesJsonForFrontend(tournamentId, stageOrder);
            return res;
        }

        boolean allowDraw = format.contains("ROUND") || format.contains("ROBIN") || format.contains("GROUP");

        int totalUpdated = 0;
        int maxPasses = "randomRound".equalsIgnoreCase(action) ? 1 : 15;

        try (Connection conn = db.getConnection()) {
            conn.setAutoCommit(false);

            String updateMatchSql = "UPDATE matches SET score1 = ?, score2 = ?, winner_id = ?, loser_id = ?, status = 'FINISHED' WHERE id = ?";
            String advSql = "UPDATE matches SET %SLOT% = ?, status = CASE WHEN (%OTHER_SLOT% IS NOT NULL) THEN 'READY' ELSE status END "
                    + "WHERE tournament_id = ? AND (id = ? OR id LIKE '%[_]' + ? OR id LIKE '%[_]S1[_]' + ? OR id LIKE '%[_]S2[_]' + ? OR match_code = ? OR match_code = 'Match #' + ? OR CAST(match_order AS VARCHAR) = ?)";

            while (maxPasses-- > 0) {
                StringBuilder sql = new StringBuilder("SELECT m.id, m.round_number, m.bracket_type, m.team1_id, m.team2_id, "
                        + "m.next_match_id, m.next_slot, m.loser_next_match_id, m.loser_next_slot, m.group_id "
                        + "FROM matches m WITH (NOLOCK) "
                        + "LEFT JOIN tournament_stages s WITH (NOLOCK) ON m.stage_id = s.id "
                        + "LEFT JOIN groups g WITH (NOLOCK) ON (m.group_id = g.id OR m.group_id = g.group_name) "
                        + "WHERE m.tournament_id = ? "
                        + "AND (s.stage_order = ? OR (s.stage_order IS NULL AND ? = 1) OR m.stage_id LIKE '%_S' + CAST(? AS VARCHAR) + '%' OR m.stage_id = 'STAGE_' + CAST(? AS VARCHAR)) "
                        + "AND m.team1_id IS NOT NULL AND m.team2_id IS NOT NULL "
                        + "AND (m.score1 IS NULL OR m.status != 'FINISHED') "
                        + "AND (m.is_bye IS NULL OR m.is_bye = 0) ");

                List<String> groupPatterns = ("randomGroup".equalsIgnoreCase(action)) ? getGroupFilterPatterns(groupId, rawGroupId) : Collections.emptyList();

                if ("randomRound".equalsIgnoreCase(action) && roundNumber != null) {
                    sql.append("AND m.round_number = ? ");
                } else if (!groupPatterns.isEmpty()) {
                    sql.append("AND (");
                    for (int gi = 0; gi < groupPatterns.size(); gi++) {
                        if (gi > 0) sql.append(" OR ");
                        sql.append("(m.group_id = ? OR g.group_name = ? OR g.id = ? OR m.group_id LIKE ? OR m.id LIKE ?)");
                    }
                    sql.append(") ");
                }

                if (bracketType != null && !bracketType.trim().isEmpty()) {
                    String bt = bracketType.trim().toUpperCase();
                    if (bt.contains("UPPER") || bt.contains("WINNER")) {
                        sql.append("AND (m.bracket_type LIKE '%WINNER%' OR m.bracket_type LIKE '%UPPER%' OR m.bracket_type = 'MAIN') ");
                    } else if (bt.contains("LOWER") || bt.contains("LOSER")) {
                        sql.append("AND (m.bracket_type LIKE '%LOSER%' OR m.bracket_type LIKE '%LOWER%') ");
                    } else if (bt.contains("GRAND")) {
                        sql.append("AND m.bracket_type LIKE '%GRAND%' ");
                    } else {
                        sql.append("AND m.bracket_type = ? ");
                    }
                }

                sql.append("ORDER BY m.round_number ASC, LEN(m.id) ASC, m.id ASC");

                List<MatchToRandom> currentPassMatches = new ArrayList<>();
                try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
                    int pIdx = 1;
                    ps.setString(pIdx++, tournamentId);
                    ps.setInt(pIdx++, stageOrder);
                    ps.setInt(pIdx++, stageOrder);
                    ps.setInt(pIdx++, stageOrder);
                    ps.setInt(pIdx++, stageOrder);

                    if ("randomRound".equalsIgnoreCase(action) && roundNumber != null) {
                        ps.setInt(pIdx++, roundNumber);
                    } else if (!groupPatterns.isEmpty()) {
                        for (String pat : groupPatterns) {
                            ps.setString(pIdx++, pat);
                            ps.setString(pIdx++, pat);
                            ps.setString(pIdx++, pat);
                            ps.setString(pIdx++, "%" + pat + "%");
                            ps.setString(pIdx++, "%" + pat + "%");
                        }
                    }

                    if (bracketType != null && !bracketType.trim().isEmpty()) {
                        String bt = bracketType.trim().toUpperCase();
                        if (!bt.contains("UPPER") && !bt.contains("WINNER") && !bt.contains("LOWER") && !bt.contains("LOSER") && !bt.contains("GRAND")) {
                            ps.setString(pIdx++, bracketType.trim());
                        }
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
                            currentPassMatches.add(item);
                        }
                    }
                }

                if (currentPassMatches.isEmpty()) {
                    break;
                }

                for (MatchToRandom m : currentPassMatches) {
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
                        int drawScore = random.nextInt(4);
                        s1 = drawScore;
                        s2 = drawScore;
                        winnerId = null;
                        loserId = null;
                    } else {
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

                    // Advance loser to loser_next_match_id
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

                    if (m.groupId != null && !m.groupId.trim().isEmpty()) {
                        MatchUpdateServlet.updateGroupTableStandings(conn, m.groupId);
                    }

                    totalUpdated++;
                }

                if ("randomRound".equalsIgnoreCase(action)) {
                    break;
                }
            }

            conn.commit();
            service.RollingWindowPointService.clearAllCaches();

            res.success = true;
            res.updatedCount = totalUpdated;
            res.message = totalUpdated > 0 ? ("Đã tạo tỉ số ngẫu nhiên cho " + totalUpdated + " trận đấu thành công!") : "Không có trận đấu nào khả dụng cần random.";

            // Fetch fresh matches JSON to return to frontend
            if (format.contains("GSL")) {
                res.matchesJson = new GSLStageDAO().getMatchesJsonForFrontend(tournamentId, stageOrder);
            } else if (format.contains("DOUBLE") || format.contains("DE")) {
                res.matchesJson = new DoubleEliminationDAO().getMatchesJsonForFrontend(tournamentId, stageOrder);
            } else if (format.contains("SINGLE")) {
                res.matchesJson = new SingleEliminationDAO().getMatchesJsonForFrontend(tournamentId, stageOrder);
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

    public synchronized RandomResult executeResetGroup(String tournamentId, int stageOrder, String groupId) {
        return executeResetGroup(tournamentId, stageOrder, groupId, null);
    }

    public synchronized RandomResult executeResetGroup(String tournamentId, int stageOrder, String groupId, String rawGroupId) {
        int maxRetries = 3;
        while (maxRetries-- > 0) {
            RandomResult res = tryExecuteResetGroup(tournamentId, stageOrder, groupId, rawGroupId);
            if (res.success || res.message == null || (!res.message.contains("deadlock") && !res.message.contains("1205"))) {
                return res;
            }
            try {
                Thread.sleep(80 + random.nextInt(120));
            } catch (InterruptedException ignore) {
            }
        }
        return tryExecuteResetGroup(tournamentId, stageOrder, groupId, rawGroupId);
    }

    private RandomResult tryExecuteResetGroup(String tournamentId, int stageOrder, String groupId, String rawGroupId) {
        RandomResult res = new RandomResult();
        if (groupId == null || groupId.trim().isEmpty()) {
            res.message = "Thiếu groupId!";
            return res;
        }

        TournamentDAO tourneyDao = new TournamentDAO();
        Tournament t = tourneyDao.getTournamentById(tournamentId);
        String format = (t != null && t.getFormat() != null) ? t.getFormat().trim().toUpperCase()
                : "SINGLE_ELIMINATION";

        DBContext db = new DBContext();
        try (Connection conn = db.getConnection()) {
            conn.setAutoCommit(false);

            List<String> groupPatterns = getGroupFilterPatterns(groupId, rawGroupId);
            StringBuilder selSql = new StringBuilder("SELECT m.id, m.round_number, m.bracket_type, m.is_bye, m.team1_id, m.team2_id, "
                    + "m.next_match_id, m.next_slot, m.loser_next_match_id, m.loser_next_slot, m.group_id "
                    + "FROM matches m "
                    + "LEFT JOIN tournament_stages s ON m.stage_id = s.id "
                    + "LEFT JOIN groups g ON (m.group_id = g.id OR m.group_id = g.group_name) "
                    + "WHERE m.tournament_id = ? "
                    + "AND (s.stage_order = ? OR (s.stage_order IS NULL AND ? = 1) OR m.stage_id LIKE '%_S' + CAST(? AS VARCHAR) + '%' OR m.stage_id = 'STAGE_' + CAST(? AS VARCHAR)) ");

            if (!groupPatterns.isEmpty()) {
                selSql.append("AND (");
                for (int gi = 0; gi < groupPatterns.size(); gi++) {
                    if (gi > 0) selSql.append(" OR ");
                    selSql.append("(m.group_id = ? OR g.group_name = ? OR g.id = ? OR m.group_id LIKE ? OR m.id LIKE ?)");
                }
                selSql.append(") ");
            }

            selSql.append("ORDER BY m.round_number ASC, LEN(m.id) ASC, m.id ASC");

            List<MatchToRandom> groupMatches = new ArrayList<>();
            try (PreparedStatement psSel = conn.prepareStatement(selSql.toString())) {
                int p = 1;
                psSel.setString(p++, tournamentId);
                psSel.setInt(p++, stageOrder);
                psSel.setInt(p++, stageOrder);
                psSel.setInt(p++, stageOrder);
                psSel.setInt(p++, stageOrder);

                if (!groupPatterns.isEmpty()) {
                    for (String pat : groupPatterns) {
                        psSel.setString(p++, pat);
                        psSel.setString(p++, pat);
                        psSel.setString(p++, pat);
                        psSel.setString(p++, "%" + pat + "%");
                        psSel.setString(p++, "%" + pat + "%");
                    }
                }

                try (ResultSet rs = psSel.executeQuery()) {
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
                        groupMatches.add(item);
                    }
                }
            }

            if (groupMatches.isEmpty()) {
                res.success = true;
                res.message = "Không tìm thấy trận đấu nào của bảng: " + groupId;
                if (format.contains("GSL")) {
                    res.matchesJson = new GSLStageDAO().getMatchesJsonForFrontend(tournamentId, stageOrder);
                } else if (format.contains("DOUBLE") || format.contains("DE")) {
                    res.matchesJson = new DoubleEliminationDAO().getMatchesJsonForFrontend(tournamentId, stageOrder);
                } else if (format.contains("SINGLE")) {
                    res.matchesJson = new SingleEliminationDAO().getMatchesJsonForFrontend(tournamentId, stageOrder);
                } else if (format.contains("ROUND") || format.contains("ROBIN")) {
                    res.matchesJson = new RoundRobinDAO().getMatchesJsonForFrontend(tournamentId, stageOrder);
                } else if (format.contains("SWISS")) {
                    res.matchesJson = new SwissSystemDAO().getMatchesJsonForFrontend(tournamentId, stageOrder);
                } else if (format.contains("GROUP")) {
                    res.matchesJson = new GroupStageDAO().getMatchesJsonForFrontend(tournamentId, stageOrder);
                }
                return res;
            }

            String updateRound1Sql = "UPDATE matches SET score1 = NULL, score2 = NULL, penalty1 = NULL, penalty2 = NULL, "
                    + "winner_id = NULL, loser_id = NULL, "
                    + "status = CASE WHEN team1_id IS NOT NULL AND team2_id IS NOT NULL THEN 'READY' ELSE 'PENDING' END "
                    + "WHERE id = ? AND (is_bye IS NULL OR is_bye = 0)";

            String updateDownstreamSql = "UPDATE matches SET team1_id = NULL, team2_id = NULL, score1 = NULL, score2 = NULL, penalty1 = NULL, penalty2 = NULL, "
                    + "winner_id = NULL, loser_id = NULL, status = 'PENDING' "
                    + "WHERE id = ? AND (is_bye IS NULL OR is_bye = 0)";

            boolean isRoundRobin = format.contains("ROUND") || format.contains("ROBIN");

            for (MatchToRandom gm : groupMatches) {
                String bt = (gm.bracketType != null) ? gm.bracketType.toUpperCase() : "";
                boolean isInitial = isRoundRobin || (gm.roundNumber == 1 && (bt.contains("WINNER") || bt.contains("UPPER") || bt.equals("MAIN")));

                if (isInitial) {
                    try (PreparedStatement psUp = conn.prepareStatement(updateRound1Sql)) {
                        psUp.setString(1, gm.id);
                        psUp.executeUpdate();
                    }
                } else {
                    try (PreparedStatement psUp = conn.prepareStatement(updateDownstreamSql)) {
                        psUp.setString(1, gm.id);
                        psUp.executeUpdate();
                    }
                }

                if (gm.groupId != null && !gm.groupId.trim().isEmpty()) {
                    MatchUpdateServlet.updateGroupTableStandings(conn, gm.groupId);
                }
            }

            String resetTourneySql = "UPDATE tournaments SET status = 'DRAFT', champion_name = NULL WHERE id = ? AND status = 'COMPLETED'";
            try (PreparedStatement psT = conn.prepareStatement(resetTourneySql)) {
                psT.setString(1, tournamentId);
                psT.executeUpdate();
            } catch (Exception ignore) {
            }

            conn.commit();
            service.RollingWindowPointService.clearAllCaches();

            res.success = true;
            res.updatedCount = groupMatches.size();
            res.message = "Đã đặt lại kết quả của " + groupId + " thành công!";

            if (format.contains("GSL")) {
                res.matchesJson = new GSLStageDAO().getMatchesJsonForFrontend(tournamentId, stageOrder);
            } else if (format.contains("DOUBLE") || format.contains("DE")) {
                res.matchesJson = new DoubleEliminationDAO().getMatchesJsonForFrontend(tournamentId, stageOrder);
            } else if (format.contains("SINGLE")) {
                res.matchesJson = new SingleEliminationDAO().getMatchesJsonForFrontend(tournamentId, stageOrder);
            } else if (format.contains("ROUND") || format.contains("ROBIN")) {
                res.matchesJson = new RoundRobinDAO().getMatchesJsonForFrontend(tournamentId, stageOrder);
            } else if (format.contains("SWISS")) {
                res.matchesJson = new SwissSystemDAO().getMatchesJsonForFrontend(tournamentId, stageOrder);
            } else if (format.contains("GROUP")) {
                res.matchesJson = new GroupStageDAO().getMatchesJsonForFrontend(tournamentId, stageOrder);
            }

        } catch (Exception e) {
            e.printStackTrace();
            res.message = "Lỗi đặt lại bảng đấu: " + e.getMessage();
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

        if (format.contains("SWISS")) {
            SwissSystemDAO swissDao = new SwissSystemDAO();
            boolean ok = swissDao.resetBracketMatches(tournamentId, stageOrder);
            res.success = ok;
            res.message = ok ? "Đã đặt lại trận đấu Swiss thành công!" : "Lỗi khi đặt lại Swiss!";
            res.matchesJson = swissDao.getMatchesJsonForFrontend(tournamentId, stageOrder);
            return res;
        }

        DBContext db = new DBContext();
        try (Connection conn = db.getConnection()) {
            conn.setAutoCommit(false);

            // 1. Fetch only playable non-BYE matches of this round to reset
            StringBuilder selSql = new StringBuilder("SELECT m.id, m.is_bye, m.team1_id, m.team2_id, m.next_match_id, m.next_slot, m.loser_next_match_id, m.loser_next_slot, m.group_id "
                    + "FROM matches m "
                    + "LEFT JOIN tournament_stages s ON m.stage_id = s.id "
                    + "WHERE m.tournament_id = ? AND m.round_number = ? "
                    + "AND (s.stage_order = ? OR (s.stage_order IS NULL AND ? = 1) OR m.stage_id LIKE '%_S' + CAST(? AS VARCHAR) + '%' OR m.stage_id = 'STAGE_' + CAST(? AS VARCHAR)) "
                    + "AND (m.is_bye IS NULL OR m.is_bye = 0)");

            if (groupId != null && !groupId.trim().isEmpty()) {
                selSql.append(" AND m.group_id = ?");
            }
            if (bracketType != null && !bracketType.trim().isEmpty()) {
                String bt = bracketType.trim().toUpperCase();
                if (bt.contains("UPPER") || bt.contains("WINNER")) {
                    selSql.append(" AND (m.bracket_type LIKE '%WINNER%' OR m.bracket_type LIKE '%UPPER%' OR m.bracket_type = 'MAIN')");
                } else if (bt.contains("LOWER") || bt.contains("LOSER")) {
                    selSql.append(" AND (m.bracket_type LIKE '%LOSER%' OR m.bracket_type LIKE '%LOWER%')");
                } else if (bt.contains("GRAND")) {
                    selSql.append(" AND m.bracket_type LIKE '%GRAND%'");
                } else {
                    selSql.append(" AND m.bracket_type = ?");
                }
            }

            List<MatchToRandom> roundMatches = new ArrayList<>();
            try (PreparedStatement psSel = conn.prepareStatement(selSql.toString())) {
                int p = 1;
                psSel.setString(p++, tournamentId);
                psSel.setInt(p++, roundNumber);
                psSel.setInt(p++, stageOrder);
                psSel.setInt(p++, stageOrder);
                psSel.setInt(p++, stageOrder);
                psSel.setInt(p++, stageOrder);
                if (groupId != null && !groupId.trim().isEmpty())
                    psSel.setString(p++, groupId.trim());
                if (bracketType != null && !bracketType.trim().isEmpty()) {
                    String bt = bracketType.trim().toUpperCase();
                    if (!bt.contains("UPPER") && !bt.contains("WINNER") && !bt.contains("LOWER") && !bt.contains("LOSER") && !bt.contains("GRAND")) {
                        psSel.setString(p++, bracketType.trim());
                    }
                }

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
                if (format.contains("DOUBLE") || format.contains("DE")) {
                    res.matchesJson = new DoubleEliminationDAO().getMatchesJsonForFrontend(tournamentId, stageOrder);
                } else if (format.contains("SINGLE")) {
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
            if (format.contains("GSL")) {
                res.matchesJson = new GSLStageDAO().getMatchesJsonForFrontend(tournamentId, stageOrder);
            } else if (format.contains("DOUBLE") || format.contains("DE")) {
                res.matchesJson = new DoubleEliminationDAO().getMatchesJsonForFrontend(tournamentId, stageOrder);
            } else if (format.contains("SINGLE")) {
                res.matchesJson = new SingleEliminationDAO().getMatchesJsonForFrontend(tournamentId, stageOrder);
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

        String selSql = "SELECT id, next_match_id, next_slot, loser_next_match_id, loser_next_slot FROM matches WHERE tournament_id = ? "
                + "AND (id = ? OR id LIKE '%[_]' + ? OR id LIKE '%[_]UB[_]' + ? OR id LIKE '%[_]LB[_]' + ? OR id LIKE '%[_]GF[_]' + ? OR match_code = ? OR match_code = 'Match #' + ? OR CAST(match_order AS VARCHAR) = ?)";
        String dbNextId = null;
        String downstreamNextId = null;
        String downstreamNextSlot = null;
        String downstreamLoserNextId = null;
        String downstreamLoserNextSlot = null;

        try (PreparedStatement psSel = conn.prepareStatement(selSql)) {
            psSel.setString(1, tournamentId);
            psSel.setString(2, nextMatchId);
            psSel.setString(3, nextMatchId);
            psSel.setString(4, nextMatchId);
            psSel.setString(5, nextMatchId);
            psSel.setString(6, nextMatchId);
            psSel.setString(7, nextMatchId);
            psSel.setString(8, nextMatchId);
            psSel.setString(9, nextMatchId);
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
