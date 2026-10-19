package controller;

import dao.TournamentDAO;

import java.io.IOException;
import java.io.PrintWriter;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * CutStageServlet
 * Centralized Servlet Controller for executing Cut Stage transitions.
 * Shared qualification and stage transition API for SE, GSL and DE.
 */
@WebServlet(name = "CutStageServlet", urlPatterns = {"/api/cut-stage", "/cut-stage"})
public class CutStageServlet extends HttpServlet {

    private final TournamentDAO tournamentDAO = new TournamentDAO();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        
        response.setContentType("application/json;charset=UTF-8");
        PrintWriter out = response.getWriter();
        out.print("{\"status\":\"info\",\"message\":\"CutStageServlet API is active. Send POST request to execute cut stage.\"}");
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        request.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");
        PrintWriter out = response.getWriter();

        try {
            String id = request.getParameter("tournamentId");
            if (id == null || id.trim().isEmpty()) throw new IllegalArgumentException("Thiếu ID giải đấu.");
            id = id.trim();
            String action = request.getParameter("action");
            if ("resetStage".equals(action)) {
                int stage = Integer.parseInt(request.getParameter("stage"));
                boolean ok = tournamentDAO.resetStageMatches(id, stage);
                out.print("{\"status\":\"" + (ok ? "success" : "error") + "\"}");
                return;
            }
            String teams;
            if ("finalizeStage1".equals(action)) {
                boolean ok = tournamentDAO.saveCutStageTeams(id, request.getParameter("stage2Teams"), true);
                out.print("{\"status\":\"" + (ok ? "success" : "error") + "\",\"message\":\"" + (ok ? "Đã xác nhận kết quả." : "Chưa thể chuyển stage: kiểm tra kết quả, số đội hoặc nhánh Stage 2 đã tồn tại.") + "\"}");
                return;
            }
            teams = tournamentDAO.getQualifiedCutTeamsJson(id);
            out.print("{\"status\":\"success\",\"qualifiedTeams\":" + teams + "}");
        } catch (Exception e) {
            response.setStatus(400);
            out.print("{\"status\":\"error\",\"message\":\"Chưa thể cut stage. Kiểm tra cấu hình và kết quả trận đấu.\"}");
        }
    }
}
