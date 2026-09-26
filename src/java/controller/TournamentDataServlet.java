package controller;

import dao.TournamentDAO;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import service.JsonParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.Map;

/**
 * REST API endpoint for persisting auxiliary tournament data to DB.
 * Replaces localStorage as the source of truth for teams_json and champion_name.
 *
 * Routes:
 *   POST /api/tournament-data?action=saveTeams        — save teams_json
 *   POST /api/tournament-data?action=saveChampion     — save champion_name
 *   GET  /api/tournament-data?action=getTeams&id=...  — read teams_json
 */
@WebServlet(name = "TournamentDataServlet", urlPatterns = {"/api/tournament-data"})
public class TournamentDataServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        request.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");

        String action = request.getParameter("action");
        String tournamentId = request.getParameter("id");
        if (tournamentId == null) tournamentId = request.getParameter("tournamentId");

        if ("getTeams".equalsIgnoreCase(action) && tournamentId != null && !tournamentId.trim().isEmpty()) {
            TournamentDAO dao = new TournamentDAO();
            String json = dao.getTeamsJson(tournamentId.trim());
            if (json != null && !json.trim().isEmpty()) {
                response.getWriter().write("{\"success\":true,\"teamsJson\":" + json + "}");
            } else {
                response.getWriter().write("{\"success\":false,\"teamsJson\":null}");
            }
            return;
        }

        response.getWriter().write("{\"success\":false,\"error\":\"Unknown GET action\"}");
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        request.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");

        String action = request.getParameter("action");
        String tournamentId = request.getParameter("tournamentId");
        String teamsJson = request.getParameter("teamsJson");
        String championName = request.getParameter("championName");

        // Also accept JSON body
        if (action == null || tournamentId == null) {
            String body = readBody(request);
            if (body != null && !body.isEmpty()) {
                Map<String, Object> parsed = JsonParser.parseObject(body);
                if (parsed != null) {
                    if (action == null && parsed.get("action") != null)
                        action = String.valueOf(parsed.get("action"));
                    if (tournamentId == null && parsed.get("tournamentId") != null)
                        tournamentId = String.valueOf(parsed.get("tournamentId"));
                    if (teamsJson == null && parsed.get("teamsJson") != null)
                        teamsJson = String.valueOf(parsed.get("teamsJson"));
                    if (championName == null && parsed.get("championName") != null)
                        championName = String.valueOf(parsed.get("championName"));
                }
            }
        }

        if (tournamentId == null || tournamentId.trim().isEmpty()) {
            response.getWriter().write("{\"success\":false,\"error\":\"Missing tournamentId\"}");
            return;
        }

        TournamentDAO dao = new TournamentDAO();
        boolean ok = false;

        if ("saveTeams".equalsIgnoreCase(action) && teamsJson != null && !teamsJson.trim().isEmpty()) {
            ok = dao.saveTeamsJson(tournamentId.trim(), teamsJson.trim());

        } else if ("saveChampion".equalsIgnoreCase(action) && championName != null) {
            ok = dao.updateTournamentChampion(tournamentId.trim(), championName.trim());

        } else if ("saveAll".equalsIgnoreCase(action)) {
            // Convenience: save both at once
            if (teamsJson != null && !teamsJson.trim().isEmpty()) {
                ok = dao.saveTeamsJson(tournamentId.trim(), teamsJson.trim());
            }
            if (championName != null && !championName.trim().isEmpty()) {
                ok = dao.updateTournamentChampion(tournamentId.trim(), championName.trim()) || ok;
            }

        } else {
            response.getWriter().write("{\"success\":false,\"error\":\"Unknown action: " + action + "\"}");
            return;
        }

        response.getWriter().write("{\"success\":" + ok + ",\"tournamentId\":\"" + tournamentId.trim() + "\"}");
    }

    private String readBody(HttpServletRequest request) {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = request.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
        } catch (Exception ignore) {}
        return sb.toString();
    }
}
