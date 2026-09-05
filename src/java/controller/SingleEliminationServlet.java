package controller;

import dao.SingleEliminationDAO;
import dao.TournamentDAO;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.Map;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import model.Match;
import model.Tournament;

/**
 * Controller for Single Elimination Tournament Bracket Page & AJAX Database Persistence.
 * Maps to both /single-elimination and /common/single-elimination to guarantee 0 404 errors.
 */
@WebServlet(name = "SingleEliminationServlet", urlPatterns = {"/single-elimination", "/common/single-elimination"})
public class SingleEliminationServlet extends HttpServlet {

    private final SingleEliminationDAO singleEliminationDAO = new SingleEliminationDAO();
    private final TournamentDAO tournamentDAO = new TournamentDAO();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        
        String idParam = request.getParameter("id");
        String tournamentId = (idParam != null && !idParam.trim().isEmpty()) ? idParam.trim() : "demo";

        String stageParam = request.getParameter("stage");
        int currentStage = 1;
        if (stageParam != null && "2".equals(stageParam.trim())) {
            currentStage = 2;
        }

        // Fetch tournament details from DB
        Tournament tournament = tournamentDAO.getTournamentById(tournamentId);
        if (tournament == null) {
            tournament = new Tournament();
            tournament.setId(tournamentId);
            tournament.setName("Giải Đấu Single Elimination");
            tournament.setFormat("SINGLE_ELIMINATION");
        }

        // Fetch matches from DB for this stage
        String dbMatchesJson = singleEliminationDAO.getMatchesJsonForFrontend(tournamentId, currentStage);
        Map<Integer, List<Match>> roundMap = singleEliminationDAO.getBracketRounds(tournamentId);

        request.setAttribute("tournament", tournament);
        request.setAttribute("roundMap", roundMap);
        request.setAttribute("dbMatchesJson", dbMatchesJson);

        // Forward to single-elimination.jsp
        request.getRequestDispatcher("/common/single-elimination.jsp").forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        
        response.setContentType("application/json;charset=UTF-8");
        PrintWriter out = response.getWriter();

        try {
            String action = request.getParameter("action");
            String tournamentId = request.getParameter("tournamentId");
            String stageParam = request.getParameter("stage");
            int stage = (stageParam != null && "2".equals(stageParam.trim())) ? 2 : 1;

            // ACTION 1: Batch Sync entire bracket structure to DB
            if ("batchSync".equalsIgnoreCase(action) || "sync".equalsIgnoreCase(action)) {
                String matchesData = request.getParameter("matchesData");
                if (tournamentId != null && matchesData != null) {
                    boolean ok = singleEliminationDAO.syncBracketMatches(tournamentId, stage, matchesData);
                    if (ok) {
                        out.print("{\"status\":\"success\",\"message\":\"Đồng bộ kết quả nhánh đấu vào CSDL thành công!\"}");
                    } else {
                        out.print("{\"status\":\"error\",\"message\":\"Không thể đồng bộ nhánh đấu vào CSDL!\"}");
                    }
                } else {
                    out.print("{\"status\":\"error\",\"message\":\"Dữ liệu batchSync thiếu tournamentId hoặc matchesData!\"}");
                }
                return;
            }

            // ACTION 2: Reset bracket matches in DB
            if ("reset".equalsIgnoreCase(action)) {
                if (tournamentId != null) {
                    boolean ok = singleEliminationDAO.resetBracketMatches(tournamentId, stage);
                    out.print("{\"status\":\"" + (ok ? "success" : "error") + "\",\"message\":\"" + (ok ? "Reset CSDL thành công!" : "Lỗi reset CSDL!") + "\"}");
                } else {
                    out.print("{\"status\":\"error\",\"message\":\"Thiếu tournamentId!\"}");
                }
                return;
            }

            // ACTION 3: Update single match score (default / updateScore)
            String matchIdStr = request.getParameter("matchId");
            String score1Str = request.getParameter("team1Score");
            String score2Str = request.getParameter("team2Score");
            String winnerFlag = request.getParameter("winner");
            String team1Name = request.getParameter("team1Name");
            String team2Name = request.getParameter("team2Name");

            if (matchIdStr != null) {
                int matchId = Integer.parseInt(matchIdStr.trim());
                Integer score1 = (score1Str != null && !score1Str.trim().isEmpty()) ? Integer.parseInt(score1Str.trim()) : null;
                Integer score2 = (score2Str != null && !score2Str.trim().isEmpty()) ? Integer.parseInt(score2Str.trim()) : null;

                boolean success = false;
                if (tournamentId != null && !tournamentId.trim().isEmpty()) {
                    success = singleEliminationDAO.updateMatchScoreAndAdvance(tournamentId, stage, matchId, score1, score2, winnerFlag, team1Name, team2Name);
                } else {
                    success = singleEliminationDAO.updateMatchScoreAndAdvance(matchId, score1 != null ? score1 : 0, score2 != null ? score2 : 0, winnerFlag);
                }

                if (success) {
                    out.print("{\"status\":\"success\",\"message\":\"Cập nhật tỷ số trận đấu vào CSDL thành công!\"}");
                } else {
                    out.print("{\"status\":\"error\",\"message\":\"Không thể lưu tỷ số vào CSDL!\"}");
                }
            } else {
                out.print("{\"status\":\"error\",\"message\":\"Dữ liệu gửi lên không hợp lệ!\"}");
            }
        } catch (Exception e) {
            out.print("{\"status\":\"error\",\"message\":\"Lỗi hệ thống: " + e.getMessage() + "\"}");
        }
    }
}
