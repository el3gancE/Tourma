package controller;

import dao.SeriesDAO;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import model.PartnerParticipant;
import model.Series;

/**
 * Controller Servlet for Rolling Window Series Partner Team List Screen
 * Pattern: /rolling/team-list
 */
@WebServlet(name = "RollingTeamListServlet", urlPatterns = {"/rolling/team-list"})
public class RollingTeamListServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        String seriesId = request.getParameter("id");
        SeriesDAO seriesDAO = new SeriesDAO();

        Series series = null;
        if (seriesId != null && !seriesId.trim().isEmpty()) {
            series = seriesDAO.getSeriesById(seriesId.trim());
        }

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

        List<PartnerParticipant> partnerList = null;
        if (series != null) {
            seriesId = series.getId();
            partnerList = seriesDAO.getPartnerParticipantsBySeriesId(seriesId);
        }

        request.setAttribute("series", series);
        request.setAttribute("partnerList", partnerList);

        request.getRequestDispatcher("/common/rolling/rolling-team-list.jsp").forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String action = request.getParameter("action");
        String seriesId = request.getParameter("seriesId");
        SeriesDAO seriesDAO = new SeriesDAO();

        if ("bulkAddPartners".equalsIgnoreCase(action)) {
            String rawText = request.getParameter("bulkTeamNames");
            int initialPoints = 0;

            if (rawText != null && !rawText.trim().isEmpty()) {
                String[] lines = rawText.split("\\r?\\n");
                java.util.List<String> teamNames = new java.util.ArrayList<>();
                for (String line : lines) {
                    if (line != null && !line.trim().isEmpty()) {
                        teamNames.add(line.trim());
                    }
                }
                seriesDAO.bulkAddPartnerParticipants(seriesId, teamNames, initialPoints);
            }
            response.sendRedirect(request.getContextPath() + "/rolling/team-list?id=" + seriesId);
            return;
        } else if ("addPartner".equalsIgnoreCase(action)) {
            String teamName = request.getParameter("teamName");
            String customPartnerId = request.getParameter("partnerId");
            int initialPoints = 0;
            try {
                initialPoints = Integer.parseInt(request.getParameter("initialPoints"));
            } catch (Exception ignore) {}

            seriesDAO.addPartnerParticipant(seriesId, teamName, customPartnerId, initialPoints);
            response.sendRedirect(request.getContextPath() + "/rolling/team-list?id=" + seriesId);
            return;
        } else if ("deletePartner".equalsIgnoreCase(action)) {
            String partnerId = request.getParameter("partnerId");
            seriesDAO.deletePartnerParticipant(partnerId, seriesId);
            response.sendRedirect(request.getContextPath() + "/rolling/team-list?id=" + seriesId);
            return;
        }

        doGet(request, response);
    }
}
