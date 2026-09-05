package controller;

import dao.RoundRobinDAO;
import dao.TournamentDAO;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import model.Tournament;

/**
 * Controller Servlet for Round Robin Tournament Format.
 * Handles page routing, DB restore, batch sync, and AJAX match score saving.
 */
@WebServlet(name = "RoundRobinServlet", urlPatterns = {"/round-robin", "/common/round-robin"})
public class RoundRobinServlet extends HttpServlet {

    private final RoundRobinDAO roundRobinDAO = new RoundRobinDAO();
    private final TournamentDAO tournamentDAO = new TournamentDAO();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String tournamentId = request.getParameter("id");
        if (tournamentId == null || tournamentId.trim().isEmpty()) {
            tournamentId = "demo";
        }

        String stageParam = request.getParameter("stage");
        int stage = (stageParam != null && "2".equals(stageParam.trim())) ? 2 : 1;

        Tournament tournament = tournamentDAO.getTournamentById(tournamentId);
        if (tournament == null) {
            tournament = new Tournament();
            tournament.setId(tournamentId);
            tournament.setName("Giải Đấu Vòng Tròn Tính Điểm");
            tournament.setFormat("ROUND_ROBIN");
        }

        String dbMatchesJson = roundRobinDAO.getMatchesJsonForFrontend(tournamentId, stage);

        request.setAttribute("tournament", tournament);
        request.setAttribute("dbMatchesJson", dbMatchesJson);
        request.getRequestDispatcher("/common/round-robin.jsp").forward(request, response);
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
                action = "updateScore";
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

            if ("batchSync".equalsIgnoreCase(action)) {
                String matchesJson = request.getParameter("matchesJson");
                if (matchesJson == null || matchesJson.trim().isEmpty()) {
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
                    boolean ok = roundRobinDAO.syncRoundRobinMatches(tournamentId, stage, matchesJson);
                    out.print("{\"status\":\"" + (ok ? "success" : "error") + "\",\"message\":\"" + (ok ? "Đồng bộ Round Robin thành công!" : "Lỗi lưu Round Robin vào DB!") + "\"}");
                } else {
                    out.print("{\"status\":\"error\",\"message\":\"Thiếu tournamentId hoặc dữ liệu matchesJson!\"}");
                }
                return;
            }

            if ("reset".equalsIgnoreCase(action)) {
                if (tournamentId != null && !tournamentId.trim().isEmpty()) {
                    boolean ok = roundRobinDAO.resetRoundRobinMatches(tournamentId, stage);
                    out.print("{\"status\":\"" + (ok ? "success" : "error") + "\",\"message\":\"" + (ok ? "Đã reset Round Robin trong CSDL" : "Lỗi reset") + "\"}");
                } else {
                    out.print("{\"status\":\"error\",\"message\":\"Thiếu tournamentId!\"}");
                }
                return;
            }

            // Update score
            String matchIdStr = request.getParameter("matchId");
            String score1Str = request.getParameter("team1Score");
            String score2Str = request.getParameter("team2Score");
            String winner = request.getParameter("winner");
            String team1Name = request.getParameter("team1Name");
            String team2Name = request.getParameter("team2Name");

            if (matchIdStr != null && score1Str != null && score2Str != null) {
                int matchId = 0;
                try {
                    matchId = Integer.parseInt(matchIdStr.trim());
                } catch (NumberFormatException e) {
                    java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+)$").matcher(matchIdStr.trim());
                    if (m.find()) {
                        matchId = Integer.parseInt(m.group(1));
                    }
                }
                Integer s1 = Integer.parseInt(score1Str.trim());
                Integer s2 = Integer.parseInt(score2Str.trim());

                boolean success;
                if (tournamentId != null && !tournamentId.trim().isEmpty()) {
                    success = roundRobinDAO.updateMatchScore(tournamentId, stage, matchId, s1, s2, winner, team1Name, team2Name);
                } else {
                    success = roundRobinDAO.updateMatchScore(matchIdStr, score1Str, score2Str, winner);
                }

                out.print("{\"success\": " + success + ", \"matchId\": \"" + matchIdStr + "\"}");
            } else {
                out.print("{\"status\":\"error\",\"message\":\"Dữ liệu không hợp lệ!\"}");
            }
        } catch (Exception e) {
            out.print("{\"status\":\"error\",\"message\":\"Lỗi: " + e.getMessage() + "\"}");
        }
    }
}
