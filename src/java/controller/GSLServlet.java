package controller;

import dao.GSLStageDAO;
import dao.ParticipantDAO;
import dao.TournamentDAO;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import model.Team;
import model.Tournament;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

/**
 * Controller for GSL Format (Dual Tournament Multi-Group Viewports).
 */
@WebServlet(name = "GSLServlet", urlPatterns = {"/gsl", "/common/gsl", "/gsl-stage", "/common/gsl-stage"})
public class GSLServlet extends HttpServlet {

    private final GSLStageDAO gslStageDAO = new GSLStageDAO();
    private final TournamentDAO tournamentDAO = new TournamentDAO();
    private final ParticipantDAO participantDAO = new ParticipantDAO();

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
            tournament.setName("Giải Đấu GSL Format");
            tournament.setFormat("GSL");
        }

        // Auto initialize GSL matches in DB if not yet created
        gslStageDAO.ensureGSLInitialized(tournamentId, currentStage);

        String dbMatchesJson = gslStageDAO.getMatchesJsonForFrontend(tournamentId, currentStage);
        List<Team> teams = participantDAO.getTeamsByTournamentId(tournamentId);

        request.setAttribute("tournament", tournament);
        request.setAttribute("tournamentId", tournamentId);
        request.setAttribute("currentStage", currentStage);
        request.setAttribute("dbMatchesJson", dbMatchesJson);
        request.setAttribute("teams", teams);
        request.setAttribute("dbStage2Teams", tournament.getStage2Teams());
        request.setAttribute("dbMultiStageConfig", tournament.getMultiStageConfig());

        request.getRequestDispatcher("/common/gsl.jsp").forward(request, response);
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
                    boolean ok = gslStageDAO.resetBracketMatches(tournamentId.trim(), stage);
                    out.print("{\"status\":\"" + (ok ? "success" : "error") + "\",\"message\":\"" + (ok ? "Đã đặt lại toàn bộ bảng đấu GSL!" : "Lỗi khi đặt lại bảng đấu!") + "\"}");
                } else {
                    out.print("{\"status\":\"error\",\"message\":\"Thiếu tournamentId!\"}");
                }
                return;
            }

            out.print("{\"status\":\"error\",\"message\":\"Hành động không hợp lệ!\"}");
        } catch (Exception e) {
            out.print("{\"status\":\"error\",\"message\":\"" + e.getMessage() + "\"}");
        }
    }
}
