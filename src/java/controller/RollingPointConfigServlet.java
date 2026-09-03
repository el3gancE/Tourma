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
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import model.Series;
import model.Team;
import model.Tournament;

/**
 * Controller Servlet for Step 4: Sub-Tournament Point Configuration
 * Pattern: /rolling/point-config
 */
@WebServlet(name = "RollingPointConfigServlet", urlPatterns = {"/rolling/point-config"})
public class RollingPointConfigServlet extends HttpServlet {

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
        int teamCount = currentTeams != null ? currentTeams.size() : 0;

        List<String> stageFormats = tournamentDAO.getStageFormats(tournament.getId());
        String s1F = (stageFormats != null && stageFormats.size() > 0) ? stageFormats.get(0) : null;
        String s2F = (stageFormats != null && stageFormats.size() > 1) ? stageFormats.get(1) : null;

        request.setAttribute("tournament", tournament);
        request.setAttribute("series", series);
        request.setAttribute("teamCount", teamCount);
        request.setAttribute("stage1Format", s1F);
        request.setAttribute("stage2Format", s2F);

        request.getRequestDispatcher("/common/rolling/rolling-point-config.jsp").forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        String tournamentId = request.getParameter("tournamentId");
        String seriesId = request.getParameter("seriesId");

        TournamentDAO tournamentDAO = new TournamentDAO();

        int champPoints = 0;
        try {
            String pStr = request.getParameter("champPoints");
            if (pStr != null && !pStr.trim().isEmpty()) {
                champPoints = Integer.parseInt(pStr.trim());
            }
        } catch (Exception ignore) {}

        // Collect point inputs for all positions: point_pos_*
        Map<String, Integer> configMap = new LinkedHashMap<>();
        configMap.put("1", champPoints);
        Enumeration<String> paramNames = request.getParameterNames();
        while (paramNames.hasMoreElements()) {
            String pName = paramNames.nextElement();
            if (pName.startsWith("point_pos_")) {
                String posKey = pName.replace("point_pos_", "");
                int val = 0;
                try {
                    String vStr = request.getParameter(pName);
                    if (vStr != null && !vStr.trim().isEmpty()) {
                        val = Integer.parseInt(vStr.trim());
                    }
                } catch (Exception ignore) {}
                configMap.put(posKey, val);
            }
        }

        // Build simple JSON format: {"1":1000,"2":700,"3-4":450}
        StringBuilder json = new StringBuilder("{");
        int idx = 0;
        for (Map.Entry<String, Integer> entry : configMap.entrySet()) {
            if (idx > 0) json.append(",");
            json.append("\"").append(entry.getKey()).append("\":").append(entry.getValue());
            idx++;
        }
        json.append("}");

        tournamentDAO.saveTournamentPointsConfig(tournamentId, champPoints, json.toString());

        if (seriesId != null && !seriesId.trim().isEmpty()) {
            SeriesDAO seriesDAO = new SeriesDAO();
            seriesDAO.recalculateSeriesStandings(seriesId.trim());
        }

        // Redirect directly to Sub-Tournament Management / Overview Screen
        Tournament t = tournamentDAO.getTournamentById(tournamentId);
        String redirectUrl = request.getContextPath() + "/common/configure-tournament-format.jsp?id=" + tournamentId + "&seriesId=" + (seriesId != null ? seriesId : "");
        if (t != null && t.getFormat() != null) {
            String fmt = t.getFormat().toUpperCase();
            if ("SINGLE_ELIMINATION".equals(fmt)) {
                redirectUrl = request.getContextPath() + "/common/single-elimination.jsp?id=" + tournamentId;
            } else if ("DOUBLE_ELIMINATION".equals(fmt)) {
                redirectUrl = request.getContextPath() + "/common/double-elimination.jsp?id=" + tournamentId;
            } else if ("ROUND_ROBIN".equals(fmt)) {
                redirectUrl = request.getContextPath() + "/common/round-robin.jsp?id=" + tournamentId;
            } else if ("GROUP_STAGE".equals(fmt)) {
                redirectUrl = request.getContextPath() + "/common/group-stage.jsp?id=" + tournamentId;
            } else if ("SWISS_LITE".equals(fmt) || "SWISS".equals(fmt)) {
                redirectUrl = request.getContextPath() + "/common/swiss-stage.jsp?id=" + tournamentId;
            }
        }

        response.sendRedirect(redirectUrl);
    }
}
