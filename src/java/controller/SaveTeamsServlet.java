package controller;

import dao.ParticipantDAO;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Controller Servlet for saving tournament teams (Step 3: Team Input & Seed Setup).
 * Immediately persists team records & seeds into SQL Server database via ParticipantDAO.
 */
@WebServlet(name = "SaveTeamsServlet", urlPatterns = {"/save-teams"})
public class SaveTeamsServlet extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        request.setCharacterEncoding("UTF-8");
        response.setContentType("text/html;charset=UTF-8");

        String tournamentId = request.getParameter("id");
        String format = request.getParameter("format");
        String teamListRaw = request.getParameter("teamListRaw");

        if (tournamentId != null && format != null && !format.trim().isEmpty()) {
            dao.TournamentDAO tDao = new dao.TournamentDAO();
            tDao.saveOrUpdateStageFormat(tournamentId, format.trim());
        }

        if (tournamentId != null && teamListRaw != null && !teamListRaw.trim().isEmpty()) {
            String[] lines = teamListRaw.split("\n");
            List<String> teamNames = new ArrayList<>();
            for (String line : lines) {
                String clean = line.trim().replaceAll("^[0-9]+[\\.\\-\\)\\s]+", "").trim().replaceAll("\\s+", " ");
                if (!clean.isEmpty()) {
                    teamNames.add(clean);
                }
            }

            if (!teamNames.isEmpty()) {
                if (teamNames.size() > 24) {
                    teamNames = new ArrayList<>(teamNames.subList(0, 24));
                }
                ParticipantDAO dao = new ParticipantDAO();
                dao.saveTournamentTeams(tournamentId, teamNames);
            }
        }

        // Forward or redirect to tournament bracket view
        response.sendRedirect(request.getContextPath() + "/tournament-bracket.jsp?id=" + tournamentId + "&format=" + format);
    }
}
