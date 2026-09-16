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
            tournamentsList = seriesDAO.getTournamentsBySeriesId(seriesId);
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
        request.setCharacterEncoding("UTF-8");
        response.setContentType("text/html;charset=UTF-8");

        String action = request.getParameter("action");
        String seriesId = request.getParameter("seriesId");
        if (seriesId == null || seriesId.trim().isEmpty()) {
            seriesId = request.getParameter("id");
        }
        SeriesDAO seriesDAO = new SeriesDAO();

        if ("updateSettings".equalsIgnoreCase(action) || "updatePhaseSize".equalsIgnoreCase(action)) {
            String name = request.getParameter("name");
            String phaseSizeStr = request.getParameter("phaseSize");
            String status = request.getParameter("status");
            int phaseSize = 3;
            try {
                if (phaseSizeStr != null && !phaseSizeStr.trim().isEmpty()) {
                    phaseSize = Integer.parseInt(phaseSizeStr.trim());
                }
            } catch (Exception ignore) {}

            if (seriesId != null && !seriesId.trim().isEmpty()) {
                Series current = seriesDAO.getSeriesById(seriesId.trim());
                if (name == null || name.trim().isEmpty()) {
                    if (current != null) name = current.getName();
                }
                if (status == null || status.trim().isEmpty()) {
                    if (current != null) status = current.getStatus();
                }
                seriesDAO.updateSeriesSettings(seriesId.trim(), name, phaseSize, status);
            }
            response.sendRedirect(request.getContextPath() + "/rolling/dashboard?id=" + (seriesId != null ? seriesId.trim() : ""));
            return;
        } else if ("addPartner".equalsIgnoreCase(action)) {
            String teamName = request.getParameter("teamName");
            String customPartnerId = request.getParameter("partnerId");
            int initialPoints = 0;
            try {
                initialPoints = Integer.parseInt(request.getParameter("initialPoints"));
            } catch (Exception ignore) {}

            if (seriesId != null && !seriesId.trim().isEmpty()) {
                seriesDAO.addPartnerParticipant(seriesId.trim(), teamName, customPartnerId, initialPoints);
            }
            dao.SeriesDAO.clearSeriesCaches();
            service.RollingWindowPointService.clearAllCaches();
            response.sendRedirect(request.getContextPath() + "/rolling/dashboard?id=" + (seriesId != null ? seriesId.trim() : "") + "&tab=partners");
            return;
        } else if ("deletePartner".equalsIgnoreCase(action)) {
            String partnerId = request.getParameter("partnerId");
            if (seriesId != null && !seriesId.trim().isEmpty()) {
                seriesDAO.deletePartnerParticipant(partnerId, seriesId.trim());
            }
            dao.SeriesDAO.clearSeriesCaches();
            service.RollingWindowPointService.clearAllCaches();
            response.sendRedirect(request.getContextPath() + "/rolling/dashboard?id=" + (seriesId != null ? seriesId.trim() : "") + "&tab=partners");
            return;
        }

        doGet(request, response);
    }
}
