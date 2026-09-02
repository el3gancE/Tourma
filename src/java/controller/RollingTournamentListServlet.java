package controller;

import dao.ParticipantDAO;
import dao.SeriesDAO;
import dao.TournamentDAO;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import model.Series;
import model.Team;
import model.Tournament;

/**
 * Controller Servlet for Sub-Tournament List Screen in Rolling Series
 * Pattern: /rolling/tournament-list
 */
@WebServlet(name = "RollingTournamentListServlet", urlPatterns = {"/rolling/tournament-list"})
public class RollingTournamentListServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        String seriesId = request.getParameter("id");
        if (seriesId == null || seriesId.trim().isEmpty()) {
            seriesId = request.getParameter("seriesId");
        }

        SeriesDAO seriesDAO = new SeriesDAO();
        TournamentDAO tournamentDAO = new TournamentDAO();
        ParticipantDAO participantDAO = new ParticipantDAO();

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
        Map<String, Integer> teamCountMap = new HashMap<>();

        if (series != null) {
            seriesId = series.getId();
            tournamentsList = tournamentDAO.getTournamentsBySeriesId(seriesId);

            if (tournamentsList != null) {
                for (Tournament t : tournamentsList) {
                    List<Team> teams = participantDAO.getTeamsByTournamentId(t.getId());
                    int count = (teams != null) ? teams.size() : 0;
                    teamCountMap.put(t.getId(), count);
                }
            }
        }

        request.setAttribute("series", series);
        request.setAttribute("tournamentsList", tournamentsList);
        request.setAttribute("teamCountMap", teamCountMap);

        request.getRequestDispatcher("/common/rolling/rolling-tournament-list.jsp").forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String action = request.getParameter("action");
        String tournamentId = request.getParameter("tournamentId");
        String seriesId = request.getParameter("seriesId");

        if ("delete".equalsIgnoreCase(action) && tournamentId != null && !tournamentId.trim().isEmpty()) {
            TournamentDAO tournamentDAO = new TournamentDAO();
            tournamentDAO.deleteTournament(tournamentId.trim());

            if (seriesId != null && !seriesId.trim().isEmpty()) {
                service.RollingWindowPointService.getInstance().recalculateAndPersistStandings(seriesId.trim());
            }
            response.sendRedirect(request.getContextPath() + "/rolling/tournament-list?id=" + (seriesId != null ? seriesId : ""));
            return;
        }

        doGet(request, response);
    }
}
