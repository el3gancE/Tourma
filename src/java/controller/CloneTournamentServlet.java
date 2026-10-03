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
        String requestedWith = request.getHeader("X-Requested-With");
        boolean isAjax = "XMLHttpRequest".equalsIgnoreCase(requestedWith) || "true".equalsIgnoreCase(request.getParameter("ajax"));

        if (sourceTournamentId == null || sourceTournamentId.trim().isEmpty()) {
            if (isAjax) {
                response.setContentType("application/json;charset=UTF-8");
                try (PrintWriter out = response.getWriter()) {
                    out.print("{\"status\":\"error\",\"message\":\"Thiếu mã giải đấu nguồn!\"}");
                }
                return;
            } else {
                response.sendRedirect(request.getContextPath() + "/my-tournaments?error=missing_id");
                return;
            }
        }

        Tournament srcTourney = tournamentDAO.getTournamentById(sourceTournamentId.trim());
        int targetTeamCount = (srcTourney != null) ? srcTourney.getTeamCount() : 0;
        String srcName = (srcTourney != null && srcTourney.getName() != null) ? srcTourney.getName() : "";

        // Enforce no copying of team rosters as required
        boolean copyTeams = false;

        Tournament cloned = tournamentDAO.cloneTournament(sourceTournamentId.trim(), newName, copyTeams);

        if (cloned != null) {
            String redirectUrl;
            StringBuilder sb = new StringBuilder(request.getContextPath()).append("/common/configure-tournament-format.jsp");
            sb.append("?id=").append(cloned.getId());
            if (cloned.getSeriesId() != null && !cloned.getSeriesId().trim().isEmpty()) {
                sb.append("&seriesId=").append(cloned.getSeriesId().trim());
            }
            if (targetTeamCount > 0) {
                sb.append("&targetTeamCount=").append(targetTeamCount);
            }
            sb.append("&copiedFrom=").append(java.net.URLEncoder.encode(sourceTournamentId.trim(), "UTF-8"));
            if (!srcName.isEmpty()) {
                sb.append("&copiedSourceTourneyName=").append(java.net.URLEncoder.encode(srcName, "UTF-8"));
            }
            redirectUrl = sb.toString();

            if (isAjax) {
                response.setContentType("application/json;charset=UTF-8");
                try (PrintWriter out = response.getWriter()) {
                    out.print("{\"status\":\"success\",\"newTournamentId\":\"" + cloned.getId() + "\",\"targetTeamCount\":" + targetTeamCount + ",\"redirectUrl\":\"" + redirectUrl + "\"}");
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
