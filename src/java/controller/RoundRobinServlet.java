package controller;

import dao.RoundRobinDAO;
import dao.TournamentDAO;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import model.Tournament;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

/**
 * Controller for Round Robin Tournament Page & Server-Driven Standings.
 */
@WebServlet(name = "RoundRobinServlet", urlPatterns = {"/round-robin", "/common/round-robin"})
public class RoundRobinServlet extends HttpServlet {

    private final RoundRobinDAO roundRobinDAO = new RoundRobinDAO();
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

        Tournament tournament = tournamentDAO.getTournamentById(tournamentId);
        if (tournament == null) {
            tournament = new Tournament();
            tournament.setId(tournamentId);
            tournament.setName("Giải Đấu Vòng Tròn");
            tournament.setFormat("ROUND_ROBIN");
        }

        String dbMatchesJson = roundRobinDAO.getMatchesJsonForFrontend(tournamentId, currentStage);
        List<RoundRobinDAO.RRStandingRow> standings = roundRobinDAO.getRoundRobinStandings(tournamentId, currentStage);

        request.setAttribute("tournament", tournament);
        request.setAttribute("tournamentId", tournamentId);
        request.setAttribute("currentStage", currentStage);
        request.setAttribute("dbMatchesJson", dbMatchesJson);
        request.setAttribute("standings", standings);
        request.setAttribute("dbStage2Teams", tournament.getStage2Teams());
        request.setAttribute("dbMultiStageConfig", tournament.getMultiStageConfig());

        request.getRequestDispatcher("/common/round-robin.jsp").forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        request.setCharacterEncoding("UTF-8");
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");
        PrintWriter out = response.getWriter();

        try {
            String action = request.getParameter("action");
            String tournamentId = request.getParameter("tournamentId");
            String stageParam = request.getParameter("stage");
            int stage = (stageParam != null && "2".equals(stageParam.trim())) ? 2 : 1;

            if ("reset".equalsIgnoreCase(action) || "resetBracket".equalsIgnoreCase(action)) {
                if (tournamentId != null && !tournamentId.trim().isEmpty()) {
                    boolean ok = roundRobinDAO.resetBracketMatches(tournamentId.trim(), stage);
                    out.print("{\"status\":\"" + (ok ? "success" : "error") + "\",\"message\":\"" + (ok ? "Đã đặt lại toàn bộ trận đấu!" : "Lỗi khi đặt lại trận đấu!") + "\"}");
                } else {
                    out.print("{\"status\":\"error\",\"message\":\"Thiếu tournamentId!\"}");
                }
                return;
            }

            if ("saveStage2Teams".equalsIgnoreCase(action)) {
                String stage2TeamsJson = request.getParameter("stage2Teams");
                if (tournamentId != null && stage2TeamsJson != null) {
                    boolean ok = tournamentDAO.saveStage2Teams(tournamentId, stage2TeamsJson);
                    out.print("{\"status\":\"" + (ok ? "success" : "error") + "\",\"message\":\"" + (ok ? "Đã lưu danh sách Vòng 2 vào CSDL!" : "Lỗi lưu Vòng 2!") + "\"}");
                } else {
                    out.print("{\"status\":\"error\",\"message\":\"Thiếu dữ liệu!\"}");
                }
                return;
            }

            if ("saveMultiStageConfig".equalsIgnoreCase(action)) {
                String multiConfigJson = request.getParameter("multiConfig");
                if (tournamentId != null && multiConfigJson != null) {
                    boolean ok = tournamentDAO.saveMultiStageConfig(tournamentId, multiConfigJson);
                    out.print("{\"status\":\"" + (ok ? "success" : "error") + "\",\"message\":\"" + (ok ? "Đã lưu cấu hình Multi-Stage!" : "Lỗi lưu cấu hình!") + "\"}");
                } else {
                    out.print("{\"status\":\"error\",\"message\":\"Thiếu dữ liệu!\"}");
                }
                return;
            }

            if ("saveStage1Status".equalsIgnoreCase(action)) {
                String stage1Status = request.getParameter("stage1Status");
                if (tournamentId != null && stage1Status != null) {
                    boolean ok = tournamentDAO.updateTournamentStage1Status(tournamentId, stage1Status);
                    out.print("{\"status\":\"" + (ok ? "success" : "error") + "\",\"message\":\"" + (ok ? "Đã cập nhật trạng thái Stage 1!" : "Lỗi cập nhật Stage 1!") + "\"}");
                } else {
                    out.print("{\"status\":\"error\",\"message\":\"Thiếu dữ liệu!\"}");
                }
                return;
            }

            out.print("{\"status\":\"error\",\"message\":\"Hành động không hợp lệ: " + action + "\"}");

        } catch (Exception e) {
            e.printStackTrace();
            out.print("{\"status\":\"error\",\"message\":\"Lỗi server: " + e.getMessage() + "\"}");
        }
    }
}
