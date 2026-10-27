package controller;

import dao.TournamentDAO;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.List;
import model.Tournament;

/**
 * Controller Servlet for My Tournaments Screen (Giải Đấu Của Tôi)
 * Queries SQL Server database via TournamentDAO and forwards to /common/my-tournaments.jsp
 */
@WebServlet(name = "MyTournamentsServlet", urlPatterns = {"/my-tournaments", "/my-tournaments.jsp"})
public class MyTournamentsServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        
        TournamentDAO dao = new TournamentDAO();
        if ("matches".equals(request.getParameter("action"))) {
            // Resolve on click from fresh DB state, including a Stage 1 unlock in another tab.
            Tournament tournament = dao.getTournamentById(request.getParameter("id"), false);
            if (tournament == null) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND, "Không tìm thấy giải đấu.");
                return;
            }
            response.setHeader("Cache-Control", "no-store");
            response.sendRedirect(response.encodeRedirectURL(request.getContextPath() + matchUrl(tournament)));
            return;
        }
        TournamentDAO.TournamentPage page = dao.getTournamentPage(null, request.getParameter("q"),
                request.getParameter("filter"), request.getParameter("page"));
        List<Tournament> tournamentList = page.getTournaments();
        
        request.setAttribute("tournamentList", tournamentList);
        request.setAttribute("pageInfo", page);
        request.getRequestDispatcher("/common/my-tournaments.jsp").forward(request, response);
    }

    private String matchUrl(Tournament tournament) {
        boolean completed = "COMPLETED".equalsIgnoreCase(tournament.getStatus())
                || "FINISHED".equalsIgnoreCase(tournament.getStatus());
        int stage = "MULTI_STAGE".equalsIgnoreCase(tournament.getTournamentType())
                && (tournament.isStage1Locked() || completed) ? 2 : 1;
        String format = stage == 2 ? tournament.getStage2Format() : tournament.getFormat();
        if (format == null || format.trim().isEmpty()) format = "SINGLE_ELIMINATION";
        format = format.trim().toUpperCase(Locale.ROOT);
        String page;
        switch (format) {
            case "DOUBLE_ELIMINATION": page = "double-elimination.jsp"; break;
            case "ROUND_ROBIN": page = "round-robin.jsp"; break;
            case "GROUP_STAGE": page = "group-stage.jsp"; break;
            case "GSL": page = "gsl.jsp"; break;
            case "SWISS":
            case "SWISS_LITE": page = "swiss-stage.jsp"; format = "SWISS_LITE"; break;
            default: page = "single-elimination.jsp"; format = "SINGLE_ELIMINATION";
        }
        String url = "/common/" + page + "?id=" + URLEncoder.encode(tournament.getId(), StandardCharsets.UTF_8)
                + "&format=" + format + "&stage=" + stage;
        if (tournament.getSeriesId() != null && !tournament.getSeriesId().trim().isEmpty()) {
            url += "&seriesId=" + URLEncoder.encode(tournament.getSeriesId(), StandardCharsets.UTF_8);
        }
        return url;
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        doGet(request, response);
    }
}
