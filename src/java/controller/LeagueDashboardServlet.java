package controller;

import dao.LeagueDAO;
import dao.SeriesDAO;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import model.Season;
import model.Series;
import model.SeriesDivision;
import model.SeriesStanding;
import model.Tournament;

@WebServlet(name = "LeagueDashboardServlet", urlPatterns = {"/league/dashboard"})
public class LeagueDashboardServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        String seriesId = request.getParameter("id");
        if (seriesId == null || seriesId.trim().isEmpty()) {
            seriesId = request.getParameter("seriesId");
        }

        SeriesDAO seriesDAO = new SeriesDAO();
        LeagueDAO leagueDAO = new LeagueDAO();

        Series series = null;
        if (seriesId != null && !seriesId.trim().isEmpty()) {
            series = seriesDAO.getSeriesById(seriesId.trim());
        }

        if (series == null) {
            List<Series> allSeries = seriesDAO.getAllSeries();
            for (Series s : allSeries) {
                if ("LEAGUE_SYSTEM".equalsIgnoreCase(s.getRankingModel())) {
                    series = s;
                    break;
                }
            }
        }

        Season currentSeason = null;
        List<Season> seasonsList = null;
        List<SeriesDivision> divisionsList = null;
        List<Tournament> tournamentsList = null;
        Map<Integer, List<SeriesStanding>> divisionStandingsMap = new HashMap<>();

        if (series != null) {
            seriesId = series.getId();
            seasonsList = leagueDAO.getSeasonsBySeriesId(seriesId);
            divisionsList = leagueDAO.getDivisionsBySeriesId(seriesId);

            String seasonParam = request.getParameter("seasonId");
            if (seasonParam != null && !seasonParam.trim().isEmpty()) {
                currentSeason = leagueDAO.getSeasonById(seasonParam.trim());
            }

            if (currentSeason == null && seasonsList != null && !seasonsList.isEmpty()) {
                currentSeason = seasonsList.get(0);
            }

            if (currentSeason != null) {
                tournamentsList = leagueDAO.getTournamentsBySeasonId(currentSeason.getId());

                if (divisionsList != null) {
                    for (SeriesDivision div : divisionsList) {
                        List<SeriesStanding> divStandings = leagueDAO.getDivisionStandings(
                            seriesId,
                            currentSeason.getSeasonNumber(),
                            div.getDivisionLevel()
                        );
                        divisionStandingsMap.put(div.getDivisionLevel(), divStandings);
                    }
                }
            }
        }

        request.setAttribute("series", series);
        request.setAttribute("seasonsList", seasonsList);
        request.setAttribute("currentSeason", currentSeason);
        request.setAttribute("divisionsList", divisionsList);
        request.setAttribute("tournamentsList", tournamentsList);
        request.setAttribute("divisionStandingsMap", divisionStandingsMap);

        request.getRequestDispatcher("/common/league/league-dashboard.jsp").forward(request, response);
    }
}
