package controller;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import service.JsonParser;
import service.MatchPersistenceService;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * High-performance, clean RESTful Servlet Endpoint for Match Score Updates & Tournament Conclusion.
 * Route: /api/match-update, /api/tournament-finish
 */
@WebServlet(name = "MatchUpdateServlet", urlPatterns = {"/api/match-update", "/api/tournament-finish"})
public class MatchUpdateServlet extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        request.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");

        String servletPath = request.getServletPath();

        // 1. Handle Tournament Finish
        if ("/api/tournament-finish".equalsIgnoreCase(servletPath)) {
            handleTournamentFinish(request, response);
            return;
        }

        // 2. Handle Match Score Update
        handleMatchUpdate(request, response);
    }

    private void handleMatchUpdate(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String tournamentId = request.getParameter("tournamentId");
        String matchId = request.getParameter("matchId");
        String score1Str = request.getParameter("score1");
        String score2Str = request.getParameter("score2");
        String winnerTeamId = request.getParameter("winnerTeamId");

        // Also check if sent as JSON body
        if (tournamentId == null || matchId == null) {
            String body = readRequestBody(request);
            if (body != null && !body.isEmpty()) {
                Map<String, Object> json = JsonParser.parseObject(body);
                if (json != null) {
                    if (tournamentId == null && json.get("tournamentId") != null) tournamentId = String.valueOf(json.get("tournamentId"));
                    if (matchId == null && json.get("matchId") != null) matchId = String.valueOf(json.get("matchId"));
                    if (score1Str == null && json.get("score1") != null) score1Str = String.valueOf(json.get("score1"));
                    if (score2Str == null && json.get("score2") != null) score2Str = String.valueOf(json.get("score2"));
                    if (winnerTeamId == null && json.get("winnerTeamId") != null) winnerTeamId = String.valueOf(json.get("winnerTeamId"));

                    // Check if batch list sent
                    if (json.get("matches") instanceof List) {
                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> list = (List<Map<String, Object>>) json.get("matches");
                        int count = MatchPersistenceService.getInstance().syncMatchesList(tournamentId, list);
                        response.getWriter().write("{\"success\":" + (count > 0) + ",\"updatedCount\":" + count + "}");
                        return;
                    }
                }
            }
        }

        Integer score1 = parseInteger(score1Str);
        Integer score2 = parseInteger(score2Str);

        boolean success = MatchPersistenceService.getInstance().updateSingleMatch(tournamentId, matchId, score1, score2, winnerTeamId);
        response.getWriter().write("{\"success\":" + success + ",\"tournamentId\":\"" + (tournamentId != null ? tournamentId : "") + "\",\"matchId\":\"" + (matchId != null ? matchId : "") + "\"}");
    }

    private void handleTournamentFinish(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String tournamentId = request.getParameter("tournamentId");
        String championName = request.getParameter("championName");

        if (tournamentId == null) {
            String body = readRequestBody(request);
            if (body != null && !body.isEmpty()) {
                Map<String, Object> json = JsonParser.parseObject(body);
                if (json != null) {
                    if (json.get("tournamentId") != null) tournamentId = String.valueOf(json.get("tournamentId"));
                    if (json.get("championName") != null) championName = String.valueOf(json.get("championName"));
                }
            }
        }

        boolean success = MatchPersistenceService.getInstance().finishTournament(tournamentId, championName);
        response.getWriter().write("{\"success\":" + success + ",\"tournamentId\":\"" + (tournamentId != null ? tournamentId : "") + "\",\"championName\":\"" + (championName != null ? championName : "") + "\"}");
    }

    private Integer parseInteger(String val) {
        if (val == null || val.trim().isEmpty()) return null;
        try {
            return Integer.parseInt(val.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private String readRequestBody(HttpServletRequest request) {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = request.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        } catch (Exception ignore) {}
        return sb.toString();
    }
}
