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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import model.PartnerParticipant;
import model.Series;
import model.Team;
import model.Tournament;

/**
 * Controller Servlet for Step 3: Sub-Tournament Teams Management
 * Pattern: /rolling/tournament-teams
 */
@WebServlet(name = "RollingTournamentTeamsServlet", urlPatterns = {"/rolling/tournament-teams"})
public class RollingTournamentTeamsServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        String tournamentId = request.getParameter("id");
        if (tournamentId == null || tournamentId.trim().isEmpty()) {
            tournamentId = request.getParameter("tournamentId");
        }

        TournamentDAO tournamentDAO = new TournamentDAO();
        SeriesDAO seriesDAO = new SeriesDAO();
        ParticipantDAO participantDAO = new ParticipantDAO();

        Tournament tournament = null;
        if (tournamentId != null && !tournamentId.trim().isEmpty()) {
            tournament = tournamentDAO.getTournamentById(tournamentId.trim());
        }

        if (tournament == null) {
            response.sendRedirect(request.getContextPath() + "/my-series");
            return;
        }

        String seriesId = tournament.getSeriesId();
        Series series = null;
        if (seriesId != null && !seriesId.trim().isEmpty()) {
            series = seriesDAO.getSeriesById(seriesId);
        }

        List<Team> currentTeams = participantDAO.getTeamsByTournamentId(tournament.getId());
        List<PartnerParticipant> partnerList = new ArrayList<>();
        List<model.SeriesStanding> standingsList = new ArrayList<>();
        if (seriesId != null && !seriesId.trim().isEmpty()) {
            partnerList = seriesDAO.getPartnerParticipantsBySeriesId(seriesId);
            standingsList = seriesDAO.getStandingsBySeriesId(seriesId);
        }

        request.setAttribute("tournament", tournament);
        request.setAttribute("series", series);
        request.setAttribute("currentTeams", currentTeams);
        request.setAttribute("partnerList", partnerList);
        request.setAttribute("standingsList", standingsList);

        request.getRequestDispatcher("/common/rolling/rolling-tournament-teams.jsp").forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        String action = request.getParameter("action");
        String tournamentId = request.getParameter("tournamentId");
        String seriesId = request.getParameter("seriesId");

        ParticipantDAO participantDAO = new ParticipantDAO();

        if ("addPartnerTeams".equalsIgnoreCase(action)) {
            String[] selectedTeamNames = request.getParameterValues("selectedTeamNames");
            if (selectedTeamNames != null && selectedTeamNames.length > 0 && tournamentId != null) {
                participantDAO.addTeamsToTournament(tournamentId, Arrays.asList(selectedTeamNames));
            }
        } else if ("bulkAdd".equalsIgnoreCase(action)) {
            String bulkText = request.getParameter("teamNamesText");
            if (bulkText != null && !bulkText.trim().isEmpty() && tournamentId != null) {
                String[] lines = bulkText.split("\\r?\\n");
                List<String> teamList = new ArrayList<>();
                for (String line : lines) {
                    if (line != null && !line.trim().isEmpty()) {
                        teamList.add(line.trim());
                    }
                }
                if (!teamList.isEmpty()) {
                    participantDAO.addTeamsToTournament(tournamentId, teamList);
                }
            }
        } else if ("removeTeam".equalsIgnoreCase(action)) {
            String teamId = request.getParameter("teamId");
            if (teamId != null && !teamId.trim().isEmpty()) {
                participantDAO.deleteTeamById(teamId.trim());
            }
        } else if ("bulkRemoveTeams".equalsIgnoreCase(action)) {
            String[] deleteTeamIds = request.getParameterValues("deleteTeamIds");
            if (deleteTeamIds != null && deleteTeamIds.length > 0) {
                for (String tid : deleteTeamIds) {
                    if (tid != null && !tid.trim().isEmpty()) {
                        participantDAO.deleteTeamById(tid.trim());
                    }
                }
            }
        }

        response.sendRedirect(request.getContextPath() + "/rolling/tournament-teams?id=" + tournamentId + "&seriesId=" + (seriesId != null ? seriesId : ""));
    }
}
