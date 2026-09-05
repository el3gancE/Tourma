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
        List<SeriesStanding> standingsList = null;
        List<service.RollingWindowPointService.RollingStandingDTO> standingsDTOList = null;

        if (series != null) {
            seriesId = series.getId();
            service.RollingWindowPointService serviceEngine = service.RollingWindowPointService.getInstance();
            standingsDTOList = serviceEngine.calculateSeriesStandingsWithExpiry(seriesId);
            tournamentsList = seriesDAO.getTournamentsBySeriesId(seriesId);
            standingsList = seriesDAO.getStandingsBySeriesId(seriesId);
        }

        request.setAttribute("series", series);
        request.setAttribute("tournamentsList", tournamentsList);
        request.setAttribute("standingsList", standingsList);
        request.setAttribute("standingsDTOList", standingsDTOList);

        request.getRequestDispatcher("/common/rolling/rolling-standings.jsp").forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        doGet(request, response);
    }
}
