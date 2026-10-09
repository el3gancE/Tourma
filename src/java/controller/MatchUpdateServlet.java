package controller;

import dao.DBContext;
import dao.ParticipantDAO;
import dao.TournamentDAO;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import model.Team;
import model.Tournament;
import service.RollingWindowPointService;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Types;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * High-Performance, Unified Servlet for Match Result Updates and Bracket Advancement.
 * 100% Database-Driven with zero reliance on localStorage.
 */
@WebServlet(name = "MatchUpdateServlet", urlPatterns = {"/api/match-update", "/match-update"})
public class MatchUpdateServlet extends HttpServlet {

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

        // Support both application/json body and standard form parameters
        Map<String, String> params = extractParameters(request);

        String action = params.getOrDefault("action", "updateMatch");
        String tournamentId = params.get("tournamentId");
        if (tournamentId == null || tournamentId.trim().isEmpty()) {
            tournamentId = params.get("tourneyId");
        }

        try {
            if ("finishTournament".equalsIgnoreCase(action)) {
                String championName = params.get("championName");
                if (championName == null) championName = params.get("champion");
                boolean ok = handleFinishTournament(tournamentId, championName);
                out.print("{\"status\":\"" + (ok ? "success" : "error") + "\",\"message\":\"" + (ok ? "Đã hoàn thành giải đấu!" : "Lỗi hoàn thành giải đấu!") + "\"}");
                return;
            }

            if ("unlockTournament".equalsIgnoreCase(action)) {
                boolean ok = handleUnlockTournament(tournamentId);
                out.print("{\"status\":\"" + (ok ? "success" : "error") + "\",\"message\":\"" + (ok ? "Đã mở khóa giải đấu!" : "Lỗi mở khóa giải đấu!") + "\"}");
                return;
            }

            if ("resetMatch".equalsIgnoreCase(action)) {
                String matchId = params.get("matchId");
                if (matchId == null) matchId = params.get("id");
                boolean ok = handleResetMatch(tournamentId, matchId);
                out.print("{\"status\":\"" + (ok ? "success" : "error") + "\",\"message\":\"" + (ok ? "Đã đặt lại trận đấu!" : "Lỗi đặt lại trận đấu!") + "\"}");
                return;
            }

            if ("reset".equalsIgnoreCase(action) || "resetBracket".equalsIgnoreCase(action) || "resetTournament".equalsIgnoreCase(action)) {
                int stage = parseInteger(params.get("stage")) != null ? parseInteger(params.get("stage")) : 1;
                TournamentDAO tDao = new TournamentDAO();
                Tournament tourney = tDao.getTournamentById(tournamentId);
                String fmt = (tourney != null && tourney.getFormat() != null) ? tourney.getFormat().trim().toUpperCase() : "SINGLE_ELIMINATION";

                boolean ok = false;
                if (fmt.contains("GSL")) {
                    ok = new dao.GSLStageDAO().resetBracketMatches(tournamentId, stage);
                } else if (fmt.contains("DOUBLE")) {
                    ok = new dao.DoubleEliminationDAO().resetBracketMatches(tournamentId, stage);
                } else if (fmt.contains("ROUND") || fmt.contains("ROBIN")) {
                    ok = new dao.RoundRobinDAO().resetBracketMatches(tournamentId, stage);
                } else if (fmt.contains("SWISS")) {
                    ok = new dao.SwissSystemDAO().resetBracketMatches(tournamentId, stage);
                } else if (fmt.contains("GROUP")) {
                    ok = new dao.GroupStageDAO().resetBracketMatches(tournamentId, stage);
                } else {
                    ok = new dao.SingleEliminationDAO().resetBracketMatches(tournamentId, stage);
                }

                out.print("{\"status\":\"" + (ok ? "success" : "error") + "\",\"message\":\"" + (ok ? "Đã đặt lại toàn bộ giải đấu!" : "Lỗi khi đặt lại giải đấu!") + "\"}");
                return;
            }

            // Default: updateMatch
            String matchId = params.get("matchId");
            if (matchId == null) matchId = params.get("id");

            Integer score1 = parseInteger(params.get("score1"));
            if (score1 == null) score1 = parseInteger(params.get("team1Score"));

            Integer score2 = parseInteger(params.get("score2"));
            if (score2 == null) score2 = parseInteger(params.get("team2Score"));

            Integer penalty1 = parseInteger(params.get("penalty1"));
            Integer penalty2 = parseInteger(params.get("penalty2"));

            String winnerFlag = params.get("winner");
            if (winnerFlag == null) winnerFlag = params.get("winnerId");

            String team1Name = params.get("team1Name");
            String team2Name = params.get("team2Name");
            String groupIdParam = params.get("groupId");
            if (groupIdParam == null) groupIdParam = params.get("group");

            MatchUpdateResult result = handleUpdateMatch(tournamentId, matchId, score1, score2, penalty1, penalty2, winnerFlag, team1Name, team2Name, parseInteger(params.get("stage")), groupIdParam);

            if (result.success) {
                out.print("{"
                        + "\"status\":\"success\","
                        + "\"message\":\"Cập nhật kết quả thành công!\","
                        + "\"matchId\":\"" + escapeJson(result.matchId) + "\","
                        + "\"winnerId\":\"" + escapeJson(result.winnerId != null ? result.winnerId : "") + "\","
                        + "\"winnerName\":\"" + escapeJson(result.winnerName != null ? result.winnerName : "") + "\","
                        + "\"loserId\":\"" + escapeJson(result.loserId != null ? result.loserId : "") + "\","
                        + "\"nextMatchId\":\"" + escapeJson(result.nextMatchId != null ? result.nextMatchId : "") + "\","
                        + "\"nextSlot\":\"" + escapeJson(result.nextSlot != null ? result.nextSlot : "") + "\","
                        + "\"loserNextMatchId\":\"" + escapeJson(result.loserNextMatchId != null ? result.loserNextMatchId : "") + "\","
                        + "\"loserNextSlot\":\"" + escapeJson(result.loserNextSlot != null ? result.loserNextSlot : "") + "\""
                        + "}");
            } else {
                out.print("{\"status\":\"error\",\"message\":\"" + escapeJson(result.errorMessage) + "\"}");
            }

        } catch (Exception e) {
            e.printStackTrace();
            out.print("{\"status\":\"error\",\"message\":\"Lỗi server: " + escapeJson(e.getMessage()) + "\"}");
        }
    }

    public static class MatchUpdateResult {
        public boolean success;
        public String errorMessage = "";
        public String matchId = "";
        public String winnerId = null;
        public String winnerName = null;
        public String loserId = null;
        public String nextMatchId = null;
        public String nextSlot = null;
        public String loserNextMatchId = null;
        public String loserNextSlot = null;
    }

    /**
     * Core Database Operation: Updates match score, resolves winner/loser, advances bracket, and recalculates group stats.
     */
    public MatchUpdateResult handleUpdateMatch(String tournamentId, String matchId, Integer score1, Integer score2,
                                              Integer penalty1, Integer penalty2, String winnerFlag,
                                              String team1Name, String team2Name) {
        return handleUpdateMatch(tournamentId, matchId, score1, score2, penalty1, penalty2, winnerFlag, team1Name, team2Name, 1, null);
    }

    public MatchUpdateResult handleUpdateMatch(String tournamentId, String matchId, Integer score1, Integer score2,
                                              Integer penalty1, Integer penalty2, String winnerFlag,
                                              String team1Name, String team2Name, Integer stage) {
        return handleUpdateMatch(tournamentId, matchId, score1, score2, penalty1, penalty2, winnerFlag, team1Name, team2Name, stage, null);
    }

    public MatchUpdateResult handleUpdateMatch(String tournamentId, String matchId, Integer score1, Integer score2,
                                              Integer penalty1, Integer penalty2, String winnerFlag,
                                              String team1Name, String team2Name, Integer stage, String groupHint) {
        MatchUpdateResult res = new MatchUpdateResult();
        if (matchId == null || matchId.trim().isEmpty()) {
            res.errorMessage = "Thiếu matchId!";
            return res;
        }

        int currentStage = (stage != null && stage > 0) ? stage : 1;
        String mIdClean = matchId.trim();
        String lastPart = mIdClean;
        int lastUnderscore = mIdClean.lastIndexOf('_');
        if (lastUnderscore != -1 && lastUnderscore < mIdClean.length() - 1) {
            lastPart = mIdClean.substring(lastUnderscore + 1);
        }

        DBContext db = new DBContext();

        try (Connection conn = db.getConnection()) {
            conn.setAutoCommit(false);

            // Auto-initialize bracket in DB if needed according to tournament format
            if (tournamentId != null && !tournamentId.trim().isEmpty()) {
                try {
                    Tournament tourney = new TournamentDAO().getTournamentById(tournamentId.trim());
                    String fmt = (tourney != null && tourney.getFormat() != null) ? tourney.getFormat().trim().toUpperCase() : "";
                    if (fmt.contains("GSL")) {
                        new dao.GSLStageDAO().ensureGSLInitialized(tournamentId.trim(), currentStage);
                    } else if (fmt.contains("DOUBLE") || fmt.contains("DE")) {
                        new dao.DoubleEliminationDAO().ensureBracketInitialized(tournamentId.trim(), currentStage);
                    } else if (fmt.contains("ROUND") || fmt.contains("ROBIN")) {
                        new dao.RoundRobinDAO().ensureRoundRobinInitialized(tournamentId.trim(), currentStage);
                    } else if (fmt.contains("SWISS")) {
                        new dao.SwissSystemDAO().ensureSwissRoundsInitialized(tournamentId.trim(), currentStage);
                    } else if (fmt.contains("GROUP")) {
                        new dao.GroupStageDAO().ensureGroupStageInitialized(tournamentId.trim(), currentStage);
                    } else {
                        new dao.SingleEliminationDAO().ensureBracketInitialized(tournamentId.trim(), currentStage);
                    }
                } catch (Exception ignore) {}
            }

            boolean hasValidTourneyId = (tournamentId != null && !tournamentId.trim().isEmpty() && !"demo".equalsIgnoreCase(tournamentId.trim()));

            String selectSql = "SELECT m.*, "
                    + "t1.raw_name AS t1_name, t2.raw_name AS t2_name "
                    + "FROM matches m "
                    + "LEFT JOIN teams t1 ON m.team1_id = t1.id "
                    + "LEFT JOIN teams t2 ON m.team2_id = t2.id "
                    + "WHERE (m.id = ? OR m.id = ? OR m.id LIKE '%[_]' + ? OR m.id LIKE '%[_]UB[_]' + ? OR m.id LIKE '%[_]LB[_]' + ? OR m.id LIKE '%[_]GF[_]' + ? "
                    + "OR m.id LIKE '%[_]S1[_]' + ? OR m.id LIKE '%[_]S2[_]' + ? "
                    + "OR m.match_code = ? OR m.match_code = 'Match #' + ? OR CAST(m.match_order AS VARCHAR) = ? OR CAST(m.match_order AS VARCHAR) = ?)"
                    + (hasValidTourneyId ? " AND m.tournament_id = ?" : "");

            String dbMatchId = null;
            String resolvedTourneyId = tournamentId;
            String stageId = null;
            String groupId = null;
            String team1Id = null;
            String team2Id = null;
            String t1Name = team1Name;
            String t2Name = team2Name;
            String nextMatchId = null;
            String nextSlot = null;
            String loserNextMatchId = null;
            String loserNextSlot = null;

            // 1. Try EXACT MATCH by match ID first!
            String exactSql = "SELECT m.*, t1.raw_name AS t1_name, t2.raw_name AS t2_name "
                    + "FROM matches m "
                    + "LEFT JOIN teams t1 ON m.team1_id = t1.id "
                    + "LEFT JOIN teams t2 ON m.team2_id = t2.id "
                    + "WHERE (m.id = ? OR m.id = ?)"
                    + (hasValidTourneyId ? " AND m.tournament_id = ?" : "");

            try (PreparedStatement psExact = conn.prepareStatement(exactSql)) {
                psExact.setString(1, mIdClean);
                psExact.setString(2, (hasValidTourneyId ? (tournamentId.trim() + "_" + mIdClean) : mIdClean));
                if (hasValidTourneyId) {
                    psExact.setString(3, tournamentId.trim());
                }
                try (ResultSet rs = psExact.executeQuery()) {
                    if (rs.next()) {
                        dbMatchId = rs.getString("id");
                        resolvedTourneyId = rs.getString("tournament_id");
                        stageId = rs.getString("stage_id");
                        groupId = rs.getString("group_id");
                        team1Id = rs.getString("team1_id");
                        team2Id = rs.getString("team2_id");
                        if (t1Name == null) t1Name = rs.getString("t1_name");
                        if (t2Name == null) t2Name = rs.getString("t2_name");
                        nextMatchId = rs.getString("next_match_id");
                        nextSlot = rs.getString("next_slot");
                        loserNextMatchId = rs.getString("loser_next_match_id");
                        loserNextSlot = rs.getString("loser_next_slot");
                    }
                }
            }

            // 2. Try match with group filter if groupHint provided
            if (dbMatchId == null && groupHint != null && !groupHint.trim().isEmpty()) {
                String grpClean = groupHint.trim();
                String groupMatchSql = "SELECT m.*, t1.raw_name AS t1_name, t2.raw_name AS t2_name "
                        + "FROM matches m "
                        + "LEFT JOIN groups g ON (m.group_id = g.id OR m.group_id = g.group_name) "
                        + "LEFT JOIN teams t1 ON m.team1_id = t1.id "
                        + "LEFT JOIN teams t2 ON m.team2_id = t2.id "
                        + "WHERE (g.id = ? OR g.group_name = ? OR m.group_id = ? OR m.group_id LIKE '%' + ? + '%') "
                        + "AND (m.match_code = ? OR m.match_code = 'Match #' + ? OR CAST(m.match_order AS VARCHAR) = ? OR m.id LIKE '%[_]' + ?) "
                        + (hasValidTourneyId ? " AND m.tournament_id = ?" : "")
                        + " ORDER BY CASE WHEN m.id = ? THEN 0 ELSE 1 END";
                try (PreparedStatement psGrp = conn.prepareStatement(groupMatchSql)) {
                    psGrp.setString(1, grpClean);
                    psGrp.setString(2, grpClean);
                    psGrp.setString(3, grpClean);
                    psGrp.setString(4, grpClean);
                    psGrp.setString(5, mIdClean);
                    psGrp.setString(6, lastPart);
                    psGrp.setString(7, mIdClean);
                    psGrp.setString(8, mIdClean);
                    if (hasValidTourneyId) {
                        psGrp.setString(9, tournamentId.trim());
                        psGrp.setString(10, mIdClean);
                    } else {
                        psGrp.setString(9, mIdClean);
                    }
                    try (ResultSet rs = psGrp.executeQuery()) {
                        if (rs.next()) {
                            dbMatchId = rs.getString("id");
                            resolvedTourneyId = rs.getString("tournament_id");
                            stageId = rs.getString("stage_id");
                            groupId = rs.getString("group_id");
                            team1Id = rs.getString("team1_id");
                            team2Id = rs.getString("team2_id");
                            if (t1Name == null) t1Name = rs.getString("t1_name");
                            if (t2Name == null) t2Name = rs.getString("t2_name");
                            nextMatchId = rs.getString("next_match_id");
                            nextSlot = rs.getString("next_slot");
                            loserNextMatchId = rs.getString("loser_next_match_id");
                            loserNextSlot = rs.getString("loser_next_slot");
                        }
                    }
                }
            }

            // 3. Fallback to match_order or suffix ONLY IF exact match not found
            if (dbMatchId == null) {
                String fallbackSql = "SELECT m.*, t1.raw_name AS t1_name, t2.raw_name AS t2_name "
                        + "FROM matches m "
                        + "LEFT JOIN teams t1 ON m.team1_id = t1.id "
                        + "LEFT JOIN teams t2 ON m.team2_id = t2.id "
                        + "WHERE (m.match_code = ? OR m.match_code = 'Match #' + ? OR CAST(m.match_order AS VARCHAR) = ? OR m.id LIKE '%[_]' + ?) "
                        + (hasValidTourneyId ? " AND m.tournament_id = ?" : "")
                        + " ORDER BY CASE WHEN m.id = ? THEN 0 ELSE 1 END";

                try (PreparedStatement psSel = conn.prepareStatement(fallbackSql)) {
                    psSel.setString(1, mIdClean);
                    psSel.setString(2, lastPart);
                    psSel.setString(3, mIdClean);
                    psSel.setString(4, mIdClean);
                    if (hasValidTourneyId) {
                        psSel.setString(5, tournamentId.trim());
                        psSel.setString(6, mIdClean);
                    } else {
                        psSel.setString(5, mIdClean);
                    }
                    try (ResultSet rs = psSel.executeQuery()) {
                        if (rs.next()) {
                            dbMatchId = rs.getString("id");
                            resolvedTourneyId = rs.getString("tournament_id");
                            stageId = rs.getString("stage_id");
                            groupId = rs.getString("group_id");
                            team1Id = rs.getString("team1_id");
                            team2Id = rs.getString("team2_id");
                            if (t1Name == null) t1Name = rs.getString("t1_name");
                            if (t2Name == null) t2Name = rs.getString("t2_name");
                            nextMatchId = rs.getString("next_match_id");
                            nextSlot = rs.getString("next_slot");
                            loserNextMatchId = rs.getString("loser_next_match_id");
                            loserNextSlot = rs.getString("loser_next_slot");
                        }
                    }
                }
            }

            if (dbMatchId == null) {
                res.errorMessage = "Không tìm thấy trận đấu trong CSDL: " + mIdClean;
                conn.rollback();
                return res;
            }

            // 2. Resolve team IDs if missing but names are present
            if ((team1Id == null || team2Id == null) && resolvedTourneyId != null) {
                ParticipantDAO pDao = new ParticipantDAO();
                List<Team> tourneyTeams = pDao.getTeamsByTournamentId(resolvedTourneyId);
                if (tourneyTeams != null) {
                    for (Team tm : tourneyTeams) {
                        if (team1Id == null && t1Name != null && matchesName(t1Name, tm)) {
                            team1Id = tm.getId();
                        }
                        if (team2Id == null && t2Name != null && matchesName(t2Name, tm)) {
                            team2Id = tm.getId();
                        }
                    }
                }
            }

            // 3. Determine Winner and Loser IDs
            String winnerId = null;
            String loserId = null;
            String winnerName = null;

            if (winnerFlag != null && !winnerFlag.trim().isEmpty()) {
                String wf = winnerFlag.trim();
                if ("team1".equalsIgnoreCase(wf) || "1".equals(wf)) {
                    winnerId = team1Id;
                    loserId = team2Id;
                    winnerName = t1Name;
                } else if ("team2".equalsIgnoreCase(wf) || "2".equals(wf)) {
                    winnerId = team2Id;
                    loserId = team1Id;
                    winnerName = t2Name;
                } else if (wf.equals(team1Id)) {
                    winnerId = team1Id;
                    loserId = team2Id;
                    winnerName = t1Name;
                } else if (wf.equals(team2Id)) {
                    winnerId = team2Id;
                    loserId = team1Id;
                    winnerName = t2Name;
                }
            }

            // If winner not explicitly selected, derive from scores
            if (winnerId == null && score1 != null && score2 != null) {
                if (score1 > score2) {
                    winnerId = team1Id;
                    loserId = team2Id;
                    winnerName = t1Name;
                } else if (score2 > score1) {
                    winnerId = team2Id;
                    loserId = team1Id;
                    winnerName = t2Name;
                } else if (penalty1 != null && penalty2 != null) {
                    if (penalty1 > penalty2) {
                        winnerId = team1Id;
                        loserId = team2Id;
                        winnerName = t1Name;
                    } else if (penalty2 > penalty1) {
                        winnerId = team2Id;
                        loserId = team1Id;
                        winnerName = t2Name;
                    }
                }
            }

            // 4. Update match in matches table
            String matchStatus = (score1 != null && score2 != null) ? "FINISHED" : ((team1Id != null && team2Id != null) ? "READY" : "PENDING");
            String updateSql = "UPDATE matches SET "
                    + "team1_id = ?, team2_id = ?, score1 = ?, score2 = ?, penalty1 = ?, penalty2 = ?, "
                    + "winner_id = ?, loser_id = ?, status = ? "
                    + "WHERE id = ?";

            try (PreparedStatement psUp = conn.prepareStatement(updateSql)) {
                if (team1Id != null) psUp.setString(1, team1Id); else psUp.setNull(1, Types.VARCHAR);
                if (team2Id != null) psUp.setString(2, team2Id); else psUp.setNull(2, Types.VARCHAR);
                if (score1 != null) psUp.setInt(3, score1); else psUp.setNull(3, Types.INTEGER);
                if (score2 != null) psUp.setInt(4, score2); else psUp.setNull(4, Types.INTEGER);
                if (penalty1 != null) psUp.setInt(5, penalty1); else psUp.setNull(5, Types.INTEGER);
                if (penalty2 != null) psUp.setInt(6, penalty2); else psUp.setNull(6, Types.INTEGER);
                if (winnerId != null) psUp.setString(7, winnerId); else psUp.setNull(7, Types.VARCHAR);
                if (loserId != null) psUp.setString(8, loserId); else psUp.setNull(8, Types.VARCHAR);
                psUp.setString(9, matchStatus);
                psUp.setString(10, dbMatchId);
                psUp.executeUpdate();
            }

            // 5. Advance Winner to next_match_id (Exact Match Priority!)
            if (winnerId != null && nextMatchId != null && !nextMatchId.trim().isEmpty() && resolvedTourneyId != null) {
                String nextClean = nextMatchId.trim();
                String slotCol = ("SLOT_2".equalsIgnoreCase(nextSlot) || "2".equals(nextSlot)) ? "team2_id" : "team1_id";
                String otherSlotCol = slotCol.equals("team1_id") ? "team2_id" : "team1_id";

                String exactAdvSql = "UPDATE matches SET " + slotCol + " = ?, "
                        + "status = CASE WHEN (" + otherSlotCol + " IS NOT NULL) THEN 'READY' ELSE status END "
                        + "WHERE tournament_id = ? AND id = ?";
                int updatedCount = 0;
                try (PreparedStatement psAdvExact = conn.prepareStatement(exactAdvSql)) {
                    psAdvExact.setString(1, winnerId);
                    psAdvExact.setString(2, resolvedTourneyId);
                    psAdvExact.setString(3, nextClean);
                    updatedCount = psAdvExact.executeUpdate();
                }

                if (updatedCount == 0) {
                    String advSql = "UPDATE matches SET " + slotCol + " = ?, "
                            + "status = CASE WHEN (" + otherSlotCol + " IS NOT NULL) THEN 'READY' ELSE status END "
                            + "WHERE tournament_id = ? AND (id LIKE '%[_]' + ? OR match_code = ?)"
                            + (groupId != null ? " AND (group_id = ? OR group_id LIKE '%' + ? + '%')" : "");
                    try (PreparedStatement psAdv = conn.prepareStatement(advSql)) {
                        psAdv.setString(1, winnerId);
                        psAdv.setString(2, resolvedTourneyId);
                        psAdv.setString(3, nextClean);
                        psAdv.setString(4, nextClean);
                        if (groupId != null) {
                            psAdv.setString(5, groupId);
                            psAdv.setString(6, groupId);
                        }
                        psAdv.executeUpdate();
                    }
                }
            }

            // 6. Advance Loser to loser_next_match_id (Double Elimination Lower Bracket Drop - Exact Match Priority!)
            if (loserId != null && loserNextMatchId != null && !loserNextMatchId.trim().isEmpty() && resolvedTourneyId != null) {
                String dropClean = loserNextMatchId.trim();
                String slotCol = ("SLOT_2".equalsIgnoreCase(loserNextSlot) || "2".equals(loserNextSlot)) ? "team2_id" : "team1_id";
                String otherSlotCol = slotCol.equals("team1_id") ? "team2_id" : "team1_id";

                String exactDropSql = "UPDATE matches SET " + slotCol + " = ?, "
                        + "status = CASE WHEN (" + otherSlotCol + " IS NOT NULL) THEN 'READY' ELSE status END "
                        + "WHERE tournament_id = ? AND id = ?";
                int droppedCount = 0;
                try (PreparedStatement psDropExact = conn.prepareStatement(exactDropSql)) {
                    psDropExact.setString(1, loserId);
                    psDropExact.setString(2, resolvedTourneyId);
                    psDropExact.setString(3, dropClean);
                    droppedCount = psDropExact.executeUpdate();
                }

                if (droppedCount == 0) {
                    String dropSql = "UPDATE matches SET " + slotCol + " = ?, "
                            + "status = CASE WHEN (" + otherSlotCol + " IS NOT NULL) THEN 'READY' ELSE status END "
                            + "WHERE tournament_id = ? AND (id LIKE '%[_]' + ? OR match_code = ?)"
                            + (groupId != null ? " AND (group_id = ? OR group_id LIKE '%' + ? + '%')" : "");
                    try (PreparedStatement psDrop = conn.prepareStatement(dropSql)) {
                        psDrop.setString(1, loserId);
                        psDrop.setString(2, resolvedTourneyId);
                        psDrop.setString(3, dropClean);
                        psDrop.setString(4, dropClean);
                        if (groupId != null) {
                            psDrop.setString(5, groupId);
                            psDrop.setString(6, groupId);
                        }
                        psDrop.executeUpdate();
                    }
                }
            }

            // 7. Update Group Standings if match belongs to a Group
            if (groupId != null && !groupId.trim().isEmpty()) {
                updateGroupTableStandings(conn, groupId);
            }

            conn.commit();

            // Clear in-memory caches to reflect new match state immediately
            RollingWindowPointService.clearAllCaches();

            res.success = true;
            res.matchId = dbMatchId;
            res.winnerId = winnerId;
            res.winnerName = winnerName;
            res.loserId = loserId;
            res.nextMatchId = nextMatchId;
            res.nextSlot = nextSlot;
            res.loserNextMatchId = loserNextMatchId;
            res.loserNextSlot = loserNextSlot;

        } catch (Exception e) {
            e.printStackTrace();
            res.errorMessage = "Database error: " + e.getMessage();
        }

        return res;
    }

    /**
     * Resets a match: clears score, winner, loser and resets status to PENDING/READY.
     */
    public boolean handleResetMatch(String tournamentId, String matchId) {
        if (matchId == null || matchId.trim().isEmpty()) return false;
        String mIdClean = matchId.trim();
        String lastPart = mIdClean;
        int lastUnderscore = mIdClean.lastIndexOf('_');
        if (lastUnderscore != -1 && lastUnderscore < mIdClean.length() - 1) {
            lastPart = mIdClean.substring(lastUnderscore + 1);
        }

        String selectSql = "SELECT id, tournament_id, team1_id, team2_id, next_match_id, next_slot, loser_next_match_id, loser_next_slot, group_id "
                + "FROM matches WHERE ("
                + "id = ? OR id = ? OR id LIKE '%[_]' + ? OR id LIKE '%[_]UB[_]' + ? OR id LIKE '%[_]LB[_]' + ? OR id LIKE '%[_]GF[_]' + ? "
                + "OR id LIKE '%[_]S1[_]' + ? OR id LIKE '%[_]S2[_]' + ? OR match_code = ? OR match_code = 'Match #' + ? "
                + "OR CAST(match_order AS VARCHAR) = ? OR CAST(match_order AS VARCHAR) = ?)"
                + (tournamentId != null && !tournamentId.trim().isEmpty() ? " AND tournament_id = ?" : "");

        DBContext db = new DBContext();
        try (Connection conn = db.getConnection()) {
            conn.setAutoCommit(false);

            String dbMatchId = null;
            String resolvedTourneyId = tournamentId;
            String team1Id = null;
            String team2Id = null;
            String nextMatchId = null;
            String nextSlot = null;
            String loserNextMatchId = null;
            String loserNextSlot = null;
            String groupId = null;

            try (PreparedStatement psSel = conn.prepareStatement(selectSql)) {
                psSel.setString(1, mIdClean);
                psSel.setString(2, lastPart);
                psSel.setString(3, lastPart);
                psSel.setString(4, lastPart);
                psSel.setString(5, lastPart);
                psSel.setString(6, lastPart);
                psSel.setString(7, lastPart);
                psSel.setString(8, lastPart);
                psSel.setString(9, mIdClean);
                psSel.setString(10, lastPart);
                psSel.setString(11, lastPart);
                psSel.setString(12, mIdClean);
                if (tournamentId != null && !tournamentId.trim().isEmpty()) {
                    psSel.setString(13, tournamentId.trim());
                }
                try (ResultSet rs = psSel.executeQuery()) {
                    if (rs.next()) {
                        dbMatchId = rs.getString("id");
                        resolvedTourneyId = rs.getString("tournament_id");
                        team1Id = rs.getString("team1_id");
                        team2Id = rs.getString("team2_id");
                        nextMatchId = rs.getString("next_match_id");
                        nextSlot = rs.getString("next_slot");
                        loserNextMatchId = rs.getString("loser_next_match_id");
                        loserNextSlot = rs.getString("loser_next_slot");
                        groupId = rs.getString("group_id");
                    }
                }
            }

            if (dbMatchId == null) {
                conn.rollback();
                return false;
            }

            String newStatus = (team1Id != null && team2Id != null) ? "READY" : "PENDING";
            String updateSql = "UPDATE matches SET score1 = NULL, score2 = NULL, penalty1 = NULL, penalty2 = NULL, "
                    + "winner_id = NULL, loser_id = NULL, status = ? WHERE id = ?";
            try (PreparedStatement psUp = conn.prepareStatement(updateSql)) {
                psUp.setString(1, newStatus);
                psUp.setString(2, dbMatchId);
                psUp.executeUpdate();
            }

            // If advanced winner was propagated, clear it from next match
            if (nextMatchId != null && !nextMatchId.trim().isEmpty() && resolvedTourneyId != null) {
                String nextClean = nextMatchId.trim();
                String nextLast = nextClean;
                int nIdx = nextClean.lastIndexOf('_');
                if (nIdx != -1 && nIdx < nextClean.length() - 1) {
                    nextLast = nextClean.substring(nIdx + 1);
                }

                String slotCol = ("SLOT_2".equalsIgnoreCase(nextSlot) || "2".equals(nextSlot)) ? "team2_id" : "team1_id";
                String clearAdvSql = "UPDATE matches SET " + slotCol + " = NULL, status = 'PENDING' "
                        + "WHERE tournament_id = ? AND ("
                        + "id = ? OR id = ? OR id LIKE '%[_]' + ? OR id LIKE '%[_]UB[_]' + ? OR id LIKE '%[_]LB[_]' + ? OR id LIKE '%[_]GF[_]' + ? "
                        + "OR id LIKE '%[_]S1[_]' + ? OR id LIKE '%[_]S2[_]' + ? OR match_code = ? OR match_code = 'Match #' + ? "
                        + "OR CAST(match_order AS VARCHAR) = ? OR CAST(match_order AS VARCHAR) = ?)";
                try (PreparedStatement psClear = conn.prepareStatement(clearAdvSql)) {
                    psClear.setString(1, resolvedTourneyId);
                    psClear.setString(2, nextClean);
                    psClear.setString(3, nextLast);
                    psClear.setString(4, nextLast);
                    psClear.setString(5, nextLast);
                    psClear.setString(6, nextLast);
                    psClear.setString(7, nextLast);
                    psClear.setString(8, nextLast);
                    psClear.setString(9, nextLast);
                    psClear.setString(10, nextClean);
                    psClear.setString(11, nextLast);
                    psClear.setString(12, nextLast);
                    psClear.setString(13, nextClean);
                    psClear.executeUpdate();
                }
            }

            // If loser was dropped to loser bracket, clear it
            if (loserNextMatchId != null && !loserNextMatchId.trim().isEmpty() && resolvedTourneyId != null) {
                String dropClean = loserNextMatchId.trim();
                String dropLast = dropClean;
                int dIdx = dropClean.lastIndexOf('_');
                if (dIdx != -1 && dIdx < dropClean.length() - 1) {
                    dropLast = dropClean.substring(dIdx + 1);
                }

                String slotCol = ("SLOT_2".equalsIgnoreCase(loserNextSlot) || "2".equals(loserNextSlot)) ? "team2_id" : "team1_id";
                String clearDropSql = "UPDATE matches SET " + slotCol + " = NULL, status = 'PENDING' "
                        + "WHERE tournament_id = ? AND ("
                        + "id = ? OR id = ? OR id LIKE '%[_]' + ? OR id LIKE '%[_]UB[_]' + ? OR id LIKE '%[_]LB[_]' + ? OR id LIKE '%[_]GF[_]' + ? "
                        + "OR id LIKE '%[_]S1[_]' + ? OR id LIKE '%[_]S2[_]' + ? OR match_code = ? OR match_code = 'Match #' + ? "
                        + "OR CAST(match_order AS VARCHAR) = ? OR CAST(match_order AS VARCHAR) = ?)";
                try (PreparedStatement psClearDrop = conn.prepareStatement(clearDropSql)) {
                    psClearDrop.setString(1, resolvedTourneyId);
                    psClearDrop.setString(2, dropClean);
                    psClearDrop.setString(3, dropLast);
                    psClearDrop.setString(4, dropLast);
                    psClearDrop.setString(5, dropLast);
                    psClearDrop.setString(6, dropLast);
                    psClearDrop.setString(7, dropLast);
                    psClearDrop.setString(8, dropLast);
                    psClearDrop.setString(9, dropLast);
                    psClearDrop.setString(10, dropClean);
                    psClearDrop.setString(11, dropLast);
                    psClearDrop.setString(12, dropLast);
                    psClearDrop.setString(13, dropClean);
                    psClearDrop.executeUpdate();
                }
            }

            if (groupId != null && !groupId.trim().isEmpty()) {
                updateGroupTableStandings(conn, groupId);
            }

            conn.commit();
            RollingWindowPointService.clearAllCaches();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Completes a tournament and records champion name in database.
     */
    public boolean handleFinishTournament(String tournamentId, String championName) {
        if (tournamentId == null || tournamentId.trim().isEmpty()) return false;
        TournamentDAO tDao = new TournamentDAO();
        boolean ok = tDao.updateTournamentStatus(tournamentId.trim(), "COMPLETED");
        if (championName != null && !championName.trim().isEmpty()) {
            tDao.updateTournamentChampion(tournamentId.trim(), championName.trim());
        }
        RollingWindowPointService.clearAllCaches();
        return ok;
    }

    /**
     * Unlocks a tournament and resets status back to DRAFT with no champion.
     */
    public boolean handleUnlockTournament(String tournamentId) {
        if (tournamentId == null || tournamentId.trim().isEmpty()) return false;
        TournamentDAO tDao = new TournamentDAO();
        boolean ok = tDao.updateTournamentStatus(tournamentId.trim(), "DRAFT");
        tDao.updateTournamentChampion(tournamentId.trim(), null);
        RollingWindowPointService.clearAllCaches();
        return ok;
    }

    /**
     * Recalculates stats and standings for all teams in a group based on finished matches.
     */
    public static void updateGroupTableStandings(Connection conn, String groupId) {
        if (groupId == null || groupId.trim().isEmpty()) return;
        try {
            // Recalculate stats for each group_team
            String calcSql = "UPDATE gt SET "
                    + "gt.matches_played = ISNULL(stats.mp, 0), "
                    + "gt.wins = ISNULL(stats.w, 0), "
                    + "gt.draws = ISNULL(stats.d, 0), "
                    + "gt.losses = ISNULL(stats.l, 0), "
                    + "gt.goals_scored = ISNULL(stats.gf, 0), "
                    + "gt.goals_conceded = ISNULL(stats.ga, 0), "
                    + "gt.goal_difference = ISNULL(stats.gf, 0) - ISNULL(stats.ga, 0), "
                    + "gt.points = ISNULL(stats.pts, 0) "
                    + "FROM group_teams gt "
                    + "LEFT JOIN ("
                    + "  SELECT t_id, "
                    + "         COUNT(*) AS mp, "
                    + "         SUM(CASE WHEN is_win = 1 THEN 1 ELSE 0 END) AS w, "
                    + "         SUM(CASE WHEN is_draw = 1 THEN 1 ELSE 0 END) AS d, "
                    + "         SUM(CASE WHEN is_loss = 1 THEN 1 ELSE 0 END) AS l, "
                    + "         SUM(gf) AS gf, "
                    + "         SUM(ga) AS ga, "
                    + "         SUM(CASE WHEN is_win = 1 THEN 3 WHEN is_draw = 1 THEN 1 ELSE 0 END) AS pts "
                    + "  FROM ("
                    + "    SELECT team1_id AS t_id, score1 AS gf, score2 AS ga, "
                    + "           CASE WHEN score1 > score2 THEN 1 ELSE 0 END AS is_win, "
                    + "           CASE WHEN score1 = score2 THEN 1 ELSE 0 END AS is_draw, "
                    + "           CASE WHEN score1 < score2 THEN 1 ELSE 0 END AS is_loss "
                    + "    FROM matches WHERE group_id = ? AND status = 'FINISHED' AND score1 IS NOT NULL AND score2 IS NOT NULL "
                    + "    UNION ALL "
                    + "    SELECT team2_id AS t_id, score2 AS gf, score1 AS ga, "
                    + "           CASE WHEN score2 > score1 THEN 1 ELSE 0 END AS is_win, "
                    + "           CASE WHEN score2 = score1 THEN 1 ELSE 0 END AS is_draw, "
                    + "           CASE WHEN score2 < score1 THEN 1 ELSE 0 END AS is_loss "
                    + "    FROM matches WHERE group_id = ? AND status = 'FINISHED' AND score1 IS NOT NULL AND score2 IS NOT NULL "
                    + "  ) sub GROUP BY t_id"
                    + ") stats ON gt.team_id = stats.t_id "
                    + "WHERE gt.group_id = ?";

            try (PreparedStatement psCalc = conn.prepareStatement(calcSql)) {
                psCalc.setString(1, groupId);
                psCalc.setString(2, groupId);
                psCalc.setString(3, groupId);
                psCalc.executeUpdate();
            }

            // Update rank_in_group based on points, goal_difference, goals_scored
            String rankSql = "WITH RankedGroup AS ("
                    + "  SELECT id, ROW_NUMBER() OVER ("
                    + "    ORDER BY points DESC, goal_difference DESC, goals_scored DESC, seed_in_group ASC"
                    + "  ) AS new_rank "
                    + "  FROM group_teams WHERE group_id = ?"
                    + ") "
                    + "UPDATE gt SET gt.rank_in_group = rg.new_rank "
                    + "FROM group_teams gt JOIN RankedGroup rg ON gt.id = rg.id";

            try (PreparedStatement psRank = conn.prepareStatement(rankSql)) {
                psRank.setString(1, groupId);
                psRank.executeUpdate();
            }
        } catch (Exception e) {
            System.err.println("[MatchUpdateServlet] updateGroupTableStandings error: " + e.getMessage());
        }
    }

    private boolean matchesName(String name, Team tm) {
        if (name == null || tm == null) return false;
        String n = name.trim().toLowerCase();
        if (tm.getId() != null && String.valueOf(tm.getId()).trim().toLowerCase().equals(n)) return true;
        if (tm.getName() != null && tm.getName().trim().toLowerCase().equals(n)) return true;
        if (tm.getRawName() != null && tm.getRawName().trim().toLowerCase().equals(n)) return true;
        if (tm.getNormalizedName() != null && tm.getNormalizedName().trim().toLowerCase().equals(n)) return true;
        return false;
    }

    private Integer parseInteger(String val) {
        if (val == null || val.trim().isEmpty()) return null;
        try {
            return Integer.parseInt(val.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }

    private Map<String, String> extractParameters(HttpServletRequest request) {
        Map<String, String> map = new HashMap<>();

        // 1. Get standard query & form parameters
        for (Map.Entry<String, String[]> entry : request.getParameterMap().entrySet()) {
            if (entry.getValue() != null && entry.getValue().length > 0) {
                map.put(entry.getKey(), entry.getValue()[0]);
            }
        }

        // 2. Fallback: Parse application/json payload if body contains JSON
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
                    parseSimpleJsonMap(jsonStr, map);
                }
            } catch (Exception ignore) {}
        }

        return map;
    }

    private void parseSimpleJsonMap(String json, Map<String, String> map) {
        String content = json.substring(1, json.length() - 1);
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
}
