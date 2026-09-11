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
import model.Series;
import model.SeriesStanding;
import model.Tournament;

/**
 * Controller Servlet for Series Standings Screen
 * Pattern: /rolling/standings
 */
@WebServlet(name = "RollingStandingsServlet", urlPatterns = {"/rolling/standings"})
public class RollingStandingsServlet extends HttpServlet {

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
        List<model.PartnerParticipant> partnerList = null;
        List<service.RollingWindowPointService.RollingStandingDTO> standingsDTOList = null;
        List<java.util.Map<String, Integer>> serverTourneyPoints = null;
        List<java.util.Map<String, Boolean>> serverTourneyParticipation = null;

        if (series != null) {
            seriesId = series.getId();
            service.RollingWindowPointService serviceEngine = service.RollingWindowPointService.getInstance();
            standingsDTOList = serviceEngine.calculateSeriesStandingsWithExpiry(seriesId);
            tournamentsList = seriesDAO.getTournamentsBySeriesId(seriesId);
            partnerList = seriesDAO.getPartnerParticipantsBySeriesId(seriesId);
            serverTourneyPoints = serviceEngine.getTourneyPointsPerTournament(seriesId);
            serverTourneyParticipation = serviceEngine.getTourneyParticipationPerTournament(seriesId);
        }

        request.setAttribute("series", series);
        request.setAttribute("tournamentsList", tournamentsList);
        request.setAttribute("partnerList", partnerList);
        request.setAttribute("standingsDTOList", standingsDTOList);
        request.setAttribute("serverTourneyPoints", serverTourneyPoints);
        request.setAttribute("serverTourneyParticipation", serverTourneyParticipation);

        request.getRequestDispatcher("/common/rolling/rolling-standings.jsp").forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        request.setCharacterEncoding("UTF-8");
        String action = request.getParameter("action");
        if ("syncClientStandings".equalsIgnoreCase(action)) {
            response.setContentType("application/json;charset=UTF-8");
            String seriesId = request.getParameter("seriesId");
            String standingsJson = request.getParameter("standingsJson");
            String historyJson = request.getParameter("historyJson");
            boolean success = false;
            if (seriesId != null && standingsJson != null) {
                success = service.RollingWindowPointService.getInstance().saveClientStandings(seriesId, standingsJson, historyJson);
            }
            response.getWriter().write("{\"success\":" + success + "}");
            return;
        }
        doGet(request, response);
    }
}
