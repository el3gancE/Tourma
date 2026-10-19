package controller;

import dao.ParticipantDAO;
import dao.TournamentDAO;
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
        if (!new TournamentDAO().saveTopSeedCount(tournamentId, request.getParameter("topSeedCount"))) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Không thể lưu Top Seed vào DB.");
            return;
        }

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
                boolean isRoundRobin = "ROUND_ROBIN".equalsIgnoreCase(format);
                if (!isRoundRobin && tournamentId != null) {
                    try {
                        model.Tournament t = new dao.TournamentDAO().getTournamentById(tournamentId);
                        if (t != null && "ROUND_ROBIN".equalsIgnoreCase(t.getFormat())) {
                            isRoundRobin = true;
                        }
                    } catch (Exception ignore) {}
                }
                if (isRoundRobin && teamNames.size() > 24) {
                    teamNames = new ArrayList<>(teamNames.subList(0, 24));
                }
                ParticipantDAO dao = new ParticipantDAO();
                dao.saveTournamentTeams(tournamentId, teamNames);

                // Also persist teams as JSON array to tournaments.teams_json
                // so rolling standings engine can read from DB instead of localStorage
                try {
                    StringBuilder sb = new StringBuilder("[");
                    for (int i = 0; i < teamNames.size(); i++) {
                        if (i > 0) sb.append(",");
                        sb.append("\"").append(teamNames.get(i).replace("\\", "\\\\").replace("\"", "\\\"")).append("\"");
                    }
                    sb.append("]");
                    new dao.TournamentDAO().saveTeamsJson(tournamentId, sb.toString());
                } catch (Exception ignore) {}
            }
        }

        // Determine effective format and seriesId
        String effectiveFormat = format;
        String seriesId = request.getParameter("seriesId");
        if (tournamentId != null && !tournamentId.trim().isEmpty()) {
            try {
                dao.TournamentDAO tDao = new dao.TournamentDAO();
                model.Tournament t = tDao.getTournamentById(tournamentId);
                if (t != null) {
                    if (seriesId == null || seriesId.trim().isEmpty()) {
                        seriesId = t.getSeriesId();
                    }
                    if (effectiveFormat == null || effectiveFormat.trim().isEmpty()) {
                        if ("MULTI_STAGE".equalsIgnoreCase(t.getTournamentType())) {
                            List<String> sFmts = tDao.getStageFormats(tournamentId);
                            if (sFmts != null && !sFmts.isEmpty()) {
                                effectiveFormat = sFmts.get(0);
                            }
                        }
                        if (effectiveFormat == null || effectiveFormat.trim().isEmpty()) {
                            effectiveFormat = t.getFormat();
                        }
                    }
                }
            } catch (Exception ignore) {}
        }

        // For formats that require group division (GROUP_STAGE and GSL), proceed to manage-group.jsp
        if (effectiveFormat != null && ("GROUP_STAGE".equalsIgnoreCase(effectiveFormat) || "GSL".equalsIgnoreCase(effectiveFormat))) {
            String redirectUrl = request.getContextPath() + "/common/manage-group.jsp?id=" + tournamentId + "&format=" + effectiveFormat.trim().toUpperCase();
            if (seriesId != null && !seriesId.trim().isEmpty()) {
                redirectUrl += "&seriesId=" + seriesId.trim();
            }
            response.sendRedirect(redirectUrl);
            return;
        }

        // Forward or redirect to tournament bracket view
        response.sendRedirect(request.getContextPath() + "/tournament-bracket.jsp?id=" + tournamentId + "&format=" + (effectiveFormat != null ? effectiveFormat : format));
    }
}
