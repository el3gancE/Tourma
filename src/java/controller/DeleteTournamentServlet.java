package controller;

import dao.TournamentDAO;
import model.Tournament;
import service.RollingWindowPointService;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Controller Servlet for Deleting Tournaments
 */
@WebServlet(name = "DeleteTournamentServlet", urlPatterns = {"/delete-tournament", "/DeleteTournamentServlet"})
public class DeleteTournamentServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        processRequest(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        processRequest(request, response);
    }

    private void processRequest(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String id = request.getParameter("id");
        if (id == null || id.trim().isEmpty()) {
            id = request.getParameter("tournamentId");
        }
        String seriesId = request.getParameter("seriesId");

        if (id != null && !id.trim().isEmpty()) {
            String tid = id.trim();
            TournamentDAO dao = new TournamentDAO();
            if (seriesId == null || seriesId.trim().isEmpty()) {
                Tournament t = dao.getTournamentById(tid);
                if (t != null && t.getSeriesId() != null && !t.getSeriesId().trim().isEmpty()) {
                    seriesId = t.getSeriesId().trim();
                }
            }

            dao.deleteTournament(tid);

            if (seriesId != null && !seriesId.trim().isEmpty()) {
                try {
                    RollingWindowPointService.getInstance().recalculateAndPersistStandings(seriesId.trim());
                } catch (Exception ignore) {}
            }
        }

        String accept = request.getHeader("Accept");
        String xRequestedWith = request.getHeader("X-Requested-With");
        if ((accept != null && accept.contains("application/json")) || "XMLHttpRequest".equalsIgnoreCase(xRequestedWith)) {
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("{\"success\":true,\"message\":\"Tournament deleted successfully\"}");
            return;
        }

        if (seriesId != null && !seriesId.trim().isEmpty()) {
            response.sendRedirect(request.getContextPath() + "/rolling/tournament-list?id=" + seriesId.trim());
        } else {
            response.sendRedirect(request.getContextPath() + "/my-tournaments");
        }
    }
}

