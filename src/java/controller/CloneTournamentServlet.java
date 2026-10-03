package controller;

import dao.TournamentDAO;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import model.Tournament;

/**
 * Controller Servlet for Cloning / Duplicating an existing tournament configuration.
 * Maps to /clone-tournament and /tournament/clone.
 */
@WebServlet(name = "CloneTournamentServlet", urlPatterns = {"/clone-tournament", "/tournament/clone"})
public class CloneTournamentServlet extends HttpServlet {

    private final TournamentDAO tournamentDAO = new TournamentDAO();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.sendRedirect(request.getContextPath() + "/my-tournaments");
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        request.setCharacterEncoding("UTF-8");
        response.setCharacterEncoding("UTF-8");

        String sourceTournamentId = request.getParameter("sourceTournamentId");
        if (sourceTournamentId == null || sourceTournamentId.trim().isEmpty()) {
            sourceTournamentId = request.getParameter("id");
        }

        String newName = request.getParameter("newName");
        String copyTeamsParam = request.getParameter("copyTeams");
        boolean copyTeams = "true".equalsIgnoreCase(copyTeamsParam) || "on".equalsIgnoreCase(copyTeamsParam);
        String redirectContext = request.getParameter("redirectContext");

        boolean isAjax = "XMLHttpRequest".equalsIgnoreCase(request.getHeader("X-Requested-With"))
                || "json".equalsIgnoreCase(request.getParameter("format"));

        if (sourceTournamentId == null || sourceTournamentId.trim().isEmpty()) {
            if (isAjax) {
                response.setContentType("application/json;charset=UTF-8");
                try (PrintWriter out = response.getWriter()) {
                    out.print("{\"status\":\"error\",\"message\":\"Thiếu mã giải đấu nguồn!\"}");
                }
            } else {
                response.sendRedirect(request.getContextPath() + "/my-tournaments?error=missing_id");
            }
            return;
        }

        Tournament cloned = tournamentDAO.cloneTournament(sourceTournamentId.trim(), newName, copyTeams);

        if (cloned != null) {
            String redirectUrl;
            if ("series".equalsIgnoreCase(redirectContext) && cloned.getSeriesId() != null && !cloned.getSeriesId().trim().isEmpty()) {
                redirectUrl = request.getContextPath() + "/rolling/tournament-list?id=" + cloned.getSeriesId().trim();
            } else {
                redirectUrl = request.getContextPath() + "/my-tournaments";
            }

            if (isAjax) {
                response.setContentType("application/json;charset=UTF-8");
                try (PrintWriter out = response.getWriter()) {
                    out.print("{\"status\":\"success\",\"newTournamentId\":\"" + cloned.getId() + "\",\"redirectUrl\":\"" + redirectUrl + "\"}");
                }
            } else {
                response.sendRedirect(redirectUrl);
            }
        } else {
            if (isAjax) {
                response.setContentType("application/json;charset=UTF-8");
                try (PrintWriter out = response.getWriter()) {
                    out.print("{\"status\":\"error\",\"message\":\"Không thể sao chép giải đấu. Vui lòng thử lại!\"}");
                }
            } else {
                response.sendRedirect(request.getContextPath() + "/my-tournaments?error=clone_failed");
            }
        }
    }
}
