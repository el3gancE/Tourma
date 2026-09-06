package controller;

import dao.ParticipantDAO;
import dao.SwissSystemDAO;
import dao.TournamentDAO;
import java.io.BufferedReader;
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
import model.Team;
import model.Tournament;

/**
 * Controller for Swiss System Tournament Stage Page & AJAX Score Updates / Batch Sync.
 */
@WebServlet(name = "SwissStageServlet", urlPatterns = {"/swiss-stage", "/common/swiss-stage"})
public class SwissStageServlet extends HttpServlet {

    private final SwissSystemDAO swissDAO = new SwissSystemDAO();
    private final TournamentDAO tournamentDAO = new TournamentDAO();
    private final ParticipantDAO participantDAO = new ParticipantDAO();

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
            tournament.setName("Giải Đấu Thể Thức Swiss System");
            tournament.setFormat("SWISS_LITE");
        }

        // Fetch participant teams from DB
        List<Team> dbTeamsList = participantDAO.getTeamsByTournamentId(tournamentId);

        // Fetch round map for Swiss system from DB
        Map<Integer, List<Match>> roundMap = swissDAO.getSwissRounds(tournamentId);

        // Fetch preloaded matches JSON directly from database
        String dbMatchesJson = swissDAO.getMatchesJsonForFrontend(tournamentId, stage);

        request.setAttribute("tournament", tournament);
        request.setAttribute("dbTeamsList", dbTeamsList);
        request.setAttribute("roundMap", roundMap);
        request.setAttribute("dbMatchesJson", dbMatchesJson);
        request.setAttribute("dbStage2Teams", tournament.getStage2Teams());
        request.setAttribute("dbMultiStageConfig", tournament.getMultiStageConfig());

        // Forward to swiss-stage.jsp
        request.getRequestDispatcher("/common/swiss-stage.jsp").forward(request, response);
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
                    boolean ok = swissDAO.syncSwissMatches(tournamentId, stage, matchesJson);
                    out.print("{\"status\":\"" + (ok ? "success" : "error") + "\",\"message\":\"" + (ok ? "Đồng bộ Swiss thành công!" : "Lỗi lưu Swiss vào DB!") + "\"}");
                } else {
                    out.print("{\"status\":\"error\",\"message\":\"Thiếu tournamentId hoặc dữ liệu matchesJson!\"}");
                }
                return;
            }

            if ("reset".equalsIgnoreCase(action)) {
                if (tournamentId != null && !tournamentId.trim().isEmpty()) {
                    boolean ok = swissDAO.resetSwissMatches(tournamentId, stage);
                    out.print("{\"status\":\"" + (ok ? "success" : "error") + "\",\"message\":\"" + (ok ? "Đã đặt lại Swiss trong CSDL" : "Lỗi reset") + "\"}");
                } else {
                    out.print("{\"status\":\"error\",\"message\":\"Thiếu tournamentId!\"}");
                }
                return;
            }

            // Update score
            String matchKey = request.getParameter("matchKey");
            if (matchKey == null || matchKey.trim().isEmpty()) {
                matchKey = request.getParameter("matchId");
            }

            String score1Str = request.getParameter("team1Score");
            String score2Str = request.getParameter("team2Score");
            String status = request.getParameter("status");
            String team1Name = request.getParameter("team1Name");
            String team2Name = request.getParameter("team2Name");
            String winner = request.getParameter("winner");

            if (matchKey != null && score1Str != null && score2Str != null) {
                int score1 = Integer.parseInt(score1Str);
                int score2 = Integer.parseInt(score2Str);

                boolean success;
                if (tournamentId != null && !tournamentId.trim().isEmpty()) {
                    success = swissDAO.updateSwissMatchScore(tournamentId, stage, matchKey, score1, score2, status, team1Name, team2Name, winner);
                } else {
                    int numId = 1;
                    try { numId = Integer.parseInt(matchKey.replaceAll("\\D+", "")); } catch (Exception ignore) {}
                    success = swissDAO.updateSwissMatchScore(numId, score1, score2, status);
                }

                if (success) {
                    out.print("{\"status\":\"success\",\"message\":\"Cập nhật tỷ số Swiss thành công!\"}");
                } else {
                    out.print("{\"status\":\"error\",\"message\":\"Không thể lưu tỷ số Swiss vào CSDL!\"}");
                }
            } else {
                out.print("{\"status\":\"error\",\"message\":\"Dữ liệu không hợp lệ!\"}");
            }
        } catch (Exception e) {
            out.print("{\"status\":\"error\",\"message\":\"Lỗi hệ thống: " + e.getMessage() + "\"}");
        }
    }
}
