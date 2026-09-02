package controller;

import dao.SeriesDAO;
import dao.TournamentDAO;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import model.PartnerParticipant;
import model.Series;
import model.SeriesStanding;
import model.Tournament;

/**
 * Controller Servlet for Rolling Window Series Dashboard Screen
 * Pattern: /rolling/dashboard
 */
@WebServlet(name = "RollingDashboardServlet", urlPatterns = {"/rolling/dashboard"})
public class RollingDashboardServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        String seriesId = request.getParameter("id");
        
        SeriesDAO seriesDAO = new SeriesDAO();
        TournamentDAO tournamentDAO = new TournamentDAO();

        Series series = null;
        if (seriesId != null && !seriesId.trim().isEmpty()) {
            series = seriesDAO.getSeriesById(seriesId.trim());
        }

        // If no ID provided or not found, pick the first ROLLING_WINDOW series or display default state
        if (series == null) {
            List<Series> allSeries = seriesDAO.getAllSeries();
            for (Series s : allSeries) {
                if ("ROLLING_WINDOW".equalsIgnoreCase(s.getRankingModel())) {
                    series = s;
                    break;
                }
            }
            if (series == null && !allSeries.isEmpty()) {
                series = allSeries.get(0);
            }
        }

        List<Tournament> tournamentsList = null;
        List<SeriesStanding> standingsList = null;
        List<PartnerParticipant> partnerList = null;

        if (series != null) {
            seriesId = series.getId();
            tournamentsList = tournamentDAO.getTournamentsBySeriesId(seriesId);
            standingsList = seriesDAO.getStandingsBySeriesId(seriesId);
            partnerList = seriesDAO.getPartnerParticipantsBySeriesId(seriesId);
        }

        request.setAttribute("series", series);
        request.setAttribute("tournamentsList", tournamentsList);
        request.setAttribute("standingsList", standingsList);
        request.setAttribute("partnerList", partnerList);

        request.getRequestDispatcher("/common/rolling/rolling-dashboard.jsp").forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String action = request.getParameter("action");
        String seriesId = request.getParameter("seriesId");
        SeriesDAO seriesDAO = new SeriesDAO();

        if ("addPartner".equalsIgnoreCase(action)) {
            String teamName = request.getParameter("teamName");
            String customPartnerId = request.getParameter("partnerId");
            int initialPoints = 0;
            try {
                initialPoints = Integer.parseInt(request.getParameter("initialPoints"));
            } catch (Exception ignore) {}

            seriesDAO.addPartnerParticipant(seriesId, teamName, customPartnerId, initialPoints);
            response.sendRedirect(request.getContextPath() + "/rolling/dashboard?id=" + seriesId + "&tab=partners");
            return;
        } else if ("deletePartner".equalsIgnoreCase(action)) {
            String partnerId = request.getParameter("partnerId");
            seriesDAO.deletePartnerParticipant(partnerId, seriesId);
            response.sendRedirect(request.getContextPath() + "/rolling/dashboard?id=" + seriesId + "&tab=partners");
            return;
        }

        doGet(request, response);
    }
}
