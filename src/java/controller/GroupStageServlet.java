package controller;

import dao.GroupStageDAO;
import dao.TournamentDAO;
import model.Tournament;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Controller for Group Stage Page & AJAX Score Updates / Batch Sync.
 */
@WebServlet(name = "GroupStageServlet", urlPatterns = {"/group-stage", "/common/group-stage", "/GroupStageServlet"})
public class GroupStageServlet extends HttpServlet {

    private final GroupStageDAO groupStageDAO = new GroupStageDAO();
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
            tournament.setName("Giải Đấu Vòng Bảng");
            tournament.setFormat("GROUP_STAGE");
        }

        String dbMatchesJson = groupStageDAO.getMatchesJsonForFrontend(tournamentId, stage);
        String dbGroupAssignments = tournament.getGroupAssignments();

        request.setAttribute("tournament", tournament);
        request.setAttribute("dbMatchesJson", dbMatchesJson);
        request.setAttribute("dbGroupAssignments", dbGroupAssignments);
        request.getRequestDispatcher("/common/group-stage.jsp").forward(request, response);
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

            if ("saveGroupAssignments".equalsIgnoreCase(action)) {
                String groupAssignmentsJson = request.getParameter("groupAssignments");
                if (groupAssignmentsJson == null || groupAssignmentsJson.trim().isEmpty()) {
                    StringBuilder sb = new StringBuilder();
                    try (BufferedReader reader = request.getReader()) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            sb.append(line);
                        }
                    }
                    groupAssignmentsJson = sb.toString();
                }
                if (tournamentId != null && groupAssignmentsJson != null && !groupAssignmentsJson.trim().isEmpty()) {
                    boolean ok = tournamentDAO.saveGroupAssignments(tournamentId, groupAssignmentsJson);
                    out.print("{\"status\":\"" + (ok ? "success" : "error") + "\",\"message\":\"" + (ok ? "Đã lưu chia bảng vào CSDL!" : "Lỗi lưu chia bảng!") + "\"}");
                } else {
                    out.print("{\"status\":\"error\",\"message\":\"Thiếu tournamentId hoặc dữ liệu groupAssignments!\"}");
                }
                return;
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
                    boolean ok = groupStageDAO.syncGroupMatches(tournamentId, stage, matchesJson);
                    out.print("{\"status\":\"" + (ok ? "success" : "error") + "\",\"message\":\"" + (ok ? "Đồng bộ Group Stage thành công!" : "Lỗi lưu Group Stage vào DB!") + "\"}");
                } else {
                    out.print("{\"status\":\"error\",\"message\":\"Thiếu tournamentId hoặc dữ liệu matchesJson!\"}");
                }
                return;
            }

            if ("reset".equalsIgnoreCase(action)) {
                if (tournamentId != null && !tournamentId.trim().isEmpty()) {
                    boolean ok = groupStageDAO.resetGroupMatches(tournamentId, stage);
                    out.print("{\"status\":\"" + (ok ? "success" : "error") + "\",\"message\":\"" + (ok ? "Đã reset Group Stage trong CSDL" : "Lỗi reset") + "\"}");
                } else {
                    out.print("{\"status\":\"error\",\"message\":\"Thiếu tournamentId!\"}");
                }
                return;
            }

            // Update score
            String matchId = request.getParameter("matchId");
            String score1Str = request.getParameter("team1Score");
            String score2Str = request.getParameter("team2Score");
            String winner = request.getParameter("winner");
            String team1Name = request.getParameter("team1Name");
            String team2Name = request.getParameter("team2Name");

            if (matchId != null && score1Str != null && score2Str != null) {
                Integer s1 = Integer.parseInt(score1Str);
                Integer s2 = Integer.parseInt(score2Str);

                boolean success = groupStageDAO.updateGroupMatchScore(tournamentId, stage, matchId, s1, s2, winner, team1Name, team2Name);
                if (success) {
                    out.print("{\"status\":\"success\",\"message\":\"Cập nhật tỷ số Group Stage thành công!\"}");
                } else {
                    out.print("{\"status\":\"error\",\"message\":\"Không thể lưu tỷ số Group Stage vào CSDL!\"}");
                }
            } else {
                out.print("{\"status\":\"error\",\"message\":\"Dữ liệu không hợp lệ!\"}");
            }
        } catch (Exception e) {
            out.print("{\"status\":\"error\",\"message\":\"Lỗi hệ thống: " + e.getMessage() + "\"}");
        }
    }
}
