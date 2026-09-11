package controller;

import dao.DBContext;
import dao.ParticipantDAO;
import dao.SeriesDAO;
import dao.TournamentDAO;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import model.Team;
import model.Tournament;
import service.RollingWindowPointService;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Endpoint for syncing tournament matches and full bracket results from client/localStorage directly into SQL Server.
 * Route: /api/sync-tournament-matches
 */
@WebServlet(name = "BatchMatchSyncServlet", urlPatterns = {"/api/sync-tournament-matches"})
public class BatchMatchSyncServlet extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        request.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");

        String action = request.getParameter("action");
        String seriesId = request.getParameter("seriesId");
        String tournamentId = request.getParameter("tournamentId");
        String matchesJson = request.getParameter("matchesJson");
        String batchJson = request.getParameter("batchJson");

        boolean success = false;
        int updatedMatchesCount = 0;

        try {
            if ("syncTournamentMatches".equalsIgnoreCase(action) && tournamentId != null && matchesJson != null) {
                updatedMatchesCount = syncMatchesForTournament(tournamentId.trim(), matchesJson);
                success = (updatedMatchesCount > 0);
            } else if ("syncBatchTournaments".equalsIgnoreCase(action) && batchJson != null) {
                updatedMatchesCount = syncBatchTournaments(batchJson);
                success = (updatedMatchesCount > 0);
            }

            if (seriesId != null && !seriesId.trim().isEmpty()) {
                RollingWindowPointService.getInstance().recalculateAndPersistStandings(seriesId.trim());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        response.getWriter().write("{\"success\":" + success + ",\"updatedMatches\":" + updatedMatchesCount + "}");
    }

    public static int syncMatchesForTournament(String tournamentId, String matchesJson) {
        if (tournamentId == null || tournamentId.trim().isEmpty() || matchesJson == null || matchesJson.trim().isEmpty()) {
            return 0;
        }

        ParticipantDAO pDao = new ParticipantDAO();
        List<Team> teams = pDao.getTeamsByTournamentId(tournamentId.trim());
        Map<String, String> teamNameToIdMap = new HashMap<>();
        if (teams != null) {
            for (Team tm : teams) {
                if (tm.getId() != null) {
                    teamNameToIdMap.put(tm.getId().toLowerCase().trim(), tm.getId());
                }
                if (tm.getRawName() != null) {
                    teamNameToIdMap.put(tm.getRawName().toLowerCase().trim(), tm.getId());
                }
                if (tm.getNormalizedName() != null) {
                    teamNameToIdMap.put(tm.getNormalizedName().toLowerCase().trim(), tm.getId());
                }
            }
        }

        int updatedCount = 0;
        Pattern p = Pattern.compile("\\{([^}]+)\\}");
        Matcher m = p.matcher(matchesJson);

        DBContext db = new DBContext();
        try (Connection conn = db.getConnection()) {
            conn.setAutoCommit(false);

            String updateSql = "UPDATE matches SET " +
                    "team1_id = COALESCE(?, team1_id), " +
                    "team2_id = COALESCE(?, team2_id), " +
                    "score1 = ?, score2 = ?, winner_id = ?, status = 'FINISHED' " +
                    "WHERE tournament_id = ? AND (id = ? OR id LIKE ? OR (round_number = ? AND (team1_id = ? OR team2_id = ?)))";

            try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                while (m.find()) {
                    String block = m.group(1);
                    String mId = extractJsonString(block, "id");
                    if (mId == null) mId = extractJsonString(block, "matchId");

                    String t1 = extractJsonString(block, "team1");
                    if (t1 == null) t1 = extractJsonString(block, "team1Name");
                    String t2 = extractJsonString(block, "team2");
                    if (t2 == null) t2 = extractJsonString(block, "team2Name");
                    String winner = extractJsonString(block, "winner");
                    if (winner == null) winner = extractJsonString(block, "winnerId");

                    int s1 = extractJsonInt(block, "score1", -1);
                    if (s1 < 0) s1 = extractJsonInt(block, "team1Score", -1);
                    int s2 = extractJsonInt(block, "score2", -1);
                    if (s2 < 0) s2 = extractJsonInt(block, "team2Score", -1);

                    int rnd = extractJsonInt(block, "round", 1);
                    if (rnd == 1) rnd = extractJsonInt(block, "roundNumber", 1);

                    String t1Id = resolveTeamId(teamNameToIdMap, t1);
                    String t2Id = resolveTeamId(teamNameToIdMap, t2);
                    String winnerId = resolveTeamId(teamNameToIdMap, winner);

                    if (winnerId == null && s1 >= 0 && s2 >= 0 && s1 != s2) {
                        winnerId = (s1 > s2) ? t1Id : t2Id;
                    }

                    if (s1 >= 0 && s2 >= 0 && winnerId != null) {
                        ps.setString(1, t1Id);
                        ps.setString(2, t2Id);
                        ps.setInt(3, s1);
                        ps.setInt(4, s2);
                        ps.setString(5, winnerId);
                        ps.setString(6, tournamentId.trim());
                        ps.setString(7, mId != null ? mId : "");
                        ps.setString(8, mId != null ? ("%" + mId) : "%NOT_MATCH%");
                        ps.setInt(9, rnd);
                        ps.setString(10, t1Id != null ? t1Id : "NONE");
                        ps.setString(11, t2Id != null ? t2Id : "NONE");
                        ps.addBatch();
                        updatedCount++;
                    }
                }
                ps.executeBatch();
            }

            conn.commit();
            RollingWindowPointService.getInstance().clearAllCaches();
        } catch (Exception e) {
            e.printStackTrace();
        }

        return updatedCount;
    }

    public static int syncBatchTournaments(String batchJson) {
        if (batchJson == null || batchJson.trim().isEmpty()) return 0;
        int totalUpdated = 0;
        Pattern p = Pattern.compile("\"tournamentId\"\\s*:\\s*\"([^\"]+)\"\\s*,\\s*\"matches\"\\s*:\\s*(\\[[^\\]]+\\])");
        Matcher m = p.matcher(batchJson);
        while (m.find()) {
            String tId = m.group(1);
            String mJson = m.group(2);
            totalUpdated += syncMatchesForTournament(tId, mJson);
        }
        return totalUpdated;
    }

    private static String resolveTeamId(Map<String, String> teamNameToIdMap, String rawName) {
        if (rawName == null || rawName.trim().isEmpty()) return null;
        String clean = rawName.trim().toLowerCase();
        if (teamNameToIdMap.containsKey(clean)) {
            return teamNameToIdMap.get(clean);
        }
        for (Map.Entry<String, String> entry : teamNameToIdMap.entrySet()) {
            if (clean.contains(entry.getKey()) || entry.getKey().contains(clean)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static String extractJsonString(String block, String key) {
        Pattern p = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"([^\"]+)\"");
        Matcher m = p.matcher(block);
        if (m.find()) return m.group(1);
        return null;
    }

    private static int extractJsonInt(String block, String key, int defaultVal) {
        Pattern p = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*([0-9]+)");
        Matcher m = p.matcher(block);
        if (m.find()) {
            try { return Integer.parseInt(m.group(1)); } catch (Exception ignore) {}
        }
        return defaultVal;
    }
}
