package controller;

import dao.DoubleEliminationDAO;
import dao.TournamentDAO;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Map;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import model.Tournament;

/**
 * Controller for Double Elimination Tournament Bracket Page & AJAX Score Updates / Batch Sync.
 */
@WebServlet(name = "DoubleEliminationServlet", urlPatterns = {"/double-elimination", "/common/double-elimination"})
public class DoubleEliminationServlet extends HttpServlet {

    private final DoubleEliminationDAO doubleEliminationDAO = new DoubleEliminationDAO();
    private final TournamentDAO tournamentDAO = new TournamentDAO();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        
        String idParam = request.getParameter("id");
        String tournamentId = (idParam != null && !idParam.trim().isEmpty()) ? idParam : "demo";

        String stageParam = request.getParameter("stage");
        int stage = (stageParam != null && "2".equals(stageParam.trim())) ? 2 : 1;

        // Fetch tournament details from DB
        Tournament tournament = tournamentDAO.getTournamentById(tournamentId);
        if (tournament == null) {
            tournament = new Tournament();
            tournament.setId(tournamentId);
            tournament.setName("Giải Đấu Double Elimination");
            tournament.setFormat("DOUBLE_ELIMINATION");
        }

        // Fetch double elimination bracket data
        Map<String, Object> deData = doubleEliminationDAO.getDoubleEliminationData(tournamentId);

        // Fetch preloaded matches JSON directly from database
        String dbMatchesJson = doubleEliminationDAO.getMatchesJsonForFrontend(tournamentId, stage);

        request.setAttribute("tournament", tournament);
        request.setAttribute("deData", deData);
        request.setAttribute("dbMatchesJson", dbMatchesJson);
        request.setAttribute("dbStage2Teams", tournament.getStage2Teams());
        request.setAttribute("dbMultiStageConfig", tournament.getMultiStageConfig());

        // Forward to double-elimination.jsp
        request.getRequestDispatcher("/common/double-elimination.jsp").forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        
        request.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");
        PrintWriter out = response.getWriter();

        try {
            String action = request.getParameter("action");
            if (action == null || action.trim().isEmpty()) {
                action = "updateScore"; // Default action
            }

            String tournamentId = request.getParameter("tournamentId");
            if (tournamentId == null || tournamentId.trim().isEmpty()) {
                tournamentId = request.getParameter("id");
            }

            String stageParam = request.getParameter("stage");
            int stage = 1;
            if (stageParam != null && !stageParam.trim().isEmpty()) {
                try { stage = Integer.parseInt(stageParam.trim()); } catch (NumberFormatException ignore) {}
            }

            if ("saveStage2Teams".equalsIgnoreCase(action)) {
                String stage2TeamsJson = request.getParameter("stage2Teams");
                if (tournamentId != null && stage2TeamsJson != null && !stage2TeamsJson.trim().isEmpty()) {
                    boolean ok = tournamentDAO.saveStage2Teams(tournamentId, stage2TeamsJson);
                    out.print("{\"status\":\"" + (ok ? "success" : "error") + "\",\"message\":\"" + (ok ? "Đã lưu danh sách Vòng 2 vào CSDL!" : "Lỗi lưu Vòng 2!") + "\"}");
                } else {
                    out.print("{\"status\":\"error\",\"message\":\"Thiếu tournamentId hoặc stage2Teams!\"}");
                }
                return;
            }

            if ("saveMultiStageConfig".equalsIgnoreCase(action)) {
                String multiConfigJson = request.getParameter("multiConfig");
                if (tournamentId != null && multiConfigJson != null && !multiConfigJson.trim().isEmpty()) {
                    boolean ok = tournamentDAO.saveMultiStageConfig(tournamentId, multiConfigJson);
                    out.print("{\"status\":\"" + (ok ? "success" : "error") + "\",\"message\":\"" + (ok ? "Đã lưu cấu hình Multi-Stage vào CSDL!" : "Lỗi lưu cấu hình!") + "\"}");
                } else {
                    out.print("{\"status\":\"error\",\"message\":\"Thiếu tournamentId hoặc multiConfig!\"}");
                }
                return;
            }

            if ("batchSync".equalsIgnoreCase(action)) {
                String matchesJson = request.getParameter("matchesJson");
                if (matchesJson == null || matchesJson.trim().isEmpty()) {
                    // Try reading raw body if sent via JSON payload
                    StringBuilder sb = new StringBuilder();
                    try (BufferedReader reader = request.getReader()) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            sb.append(line);
                        }
                    }
                    matchesJson = sb.toString();
                }

                if (tournamentId != null && matchesJson != null && !matchesJson.trim().isEmpty()) {
                    boolean ok = doubleEliminationDAO.syncBracketMatches(tournamentId, stage, matchesJson);
                    if (ok) {
                        out.print("{\"status\":\"success\",\"message\":\"Đồng bộ Double Elimination vào CSDL thành công!\"}");
                    } else {
                        out.print("{\"status\":\"error\",\"message\":\"Không thể lưu cấu trúc Double Elimination vào CSDL!\"}");
                    }
                } else {
                    out.print("{\"status\":\"error\",\"message\":\"Thiếu tournamentId hoặc dữ liệu matchesJson!\"}");
                }
                return;
            }

            if ("reset".equalsIgnoreCase(action)) {
                if (tournamentId != null && !tournamentId.trim().isEmpty()) {
                    boolean ok = doubleEliminationDAO.resetBracketMatches(tournamentId, stage);
                    out.print("{\"status\":\"" + (ok ? "success" : "error") + "\",\"message\":\"" + (ok ? "Đã đặt lại nhánh đấu trong CSDL" : "Lỗi reset") + "\"}");
                } else {
                    out.print("{\"status\":\"error\",\"message\":\"Thiếu tournamentId!\"}");
                }
                return;
            }

            // Update score action
            String matchIdStr = request.getParameter("matchId");
            String score1Str = request.getParameter("team1Score");
            String score2Str = request.getParameter("team2Score");
            String winnerFlag = request.getParameter("winner");
            String team1Name = request.getParameter("team1Name");
            String team2Name = request.getParameter("team2Name");

            if (matchIdStr != null && score1Str != null && score2Str != null) {
                int matchId = 0;
                try {
                    matchId = Integer.parseInt(matchIdStr.trim());
                } catch (NumberFormatException nfe) {
                    java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+)$").matcher(matchIdStr.trim());
                    if (m.find()) {
                        matchId = Integer.parseInt(m.group(1));
                    }
                }
                int score1 = Integer.parseInt(score1Str.trim());
                int score2 = Integer.parseInt(score2Str.trim());

                boolean success;
                if (tournamentId != null && !tournamentId.trim().isEmpty()) {
                    success = doubleEliminationDAO.updateMatchScoreAndAdvance(tournamentId, stage, matchId, score1, score2, winnerFlag, team1Name, team2Name);
                } else {
                    success = doubleEliminationDAO.updateMatchScore(matchId, score1, score2, winnerFlag);
                }

                if (success) {
                    out.print("{\"status\":\"success\",\"message\":\"Cập nhật tỷ số Double Elimination thành công!\"}");
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
