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
            tournament = getTournamentByIdSafe(tournamentId.trim());
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

        List<String> stageFormats = getStageFormatsSafe(tournament.getId());
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

        saveTournamentPointsConfigSafe(tournamentId, champPoints, json.toString());

        if (seriesId != null && !seriesId.trim().isEmpty()) {
            SeriesDAO seriesDAO = new SeriesDAO();
            seriesDAO.recalculateSeriesStandings(seriesId.trim());
        }

        // Redirect directly to Sub-Tournament Management / Overview Screen
        Tournament t = getTournamentByIdSafe(tournamentId);
        String redirectUrl = request.getContextPath() + "/common/configure-tournament-format.jsp?id=" + tournamentId + "&seriesId=" + (seriesId != null ? seriesId : "");
        if (t != null) {
            String fmt = (t.getFormat() != null) ? t.getFormat().toUpperCase() : "SINGLE_ELIMINATION";
            String tourneyType = (t.getTournamentType() != null) ? t.getTournamentType().toUpperCase() : "SINGLE_STAGE";
            List<String> stageFormats = getStageFormatsSafe(tournamentId);
            String s1F = (stageFormats != null && !stageFormats.isEmpty()) ? stageFormats.get(0).toUpperCase() : fmt;

            String clientFmt = request.getParameter("clientFormat");
            String clientS1Fmt = request.getParameter("clientStage1Format");
            if (clientFmt != null && !clientFmt.trim().isEmpty()) {
                fmt = clientFmt.trim().toUpperCase();
            }
            if (clientS1Fmt != null && !clientS1Fmt.trim().isEmpty()) {
                s1F = clientS1Fmt.trim().toUpperCase();
            }

            boolean isGroupStage = "GROUP_STAGE".equals(fmt) || "GROUP_STAGE".equals(s1F);

            if (isGroupStage) {
                redirectUrl = request.getContextPath() + "/common/manage-group.jsp?id=" + tournamentId + "&seriesId=" + (seriesId != null ? seriesId : "") + "&format=GROUP_STAGE";
            } else if ("SINGLE_ELIMINATION".equals(s1F) || "SINGLE_ELIMINATION".equals(fmt)) {
                redirectUrl = request.getContextPath() + "/common/single-elimination.jsp?id=" + tournamentId;
            } else if ("DOUBLE_ELIMINATION".equals(s1F) || "DOUBLE_ELIMINATION".equals(fmt)) {
                redirectUrl = request.getContextPath() + "/common/double-elimination.jsp?id=" + tournamentId;
            } else if ("ROUND_ROBIN".equals(s1F) || "ROUND_ROBIN".equals(fmt)) {
                redirectUrl = request.getContextPath() + "/common/round-robin.jsp?id=" + tournamentId;
            } else if ("SWISS_LITE".equals(s1F) || "SWISS".equals(s1F) || "SWISS_LITE".equals(fmt) || "SWISS".equals(fmt)) {
                redirectUrl = request.getContextPath() + "/common/swiss-stage.jsp?id=" + tournamentId;
            }
        }

        response.sendRedirect(redirectUrl);
    }

    private Tournament getTournamentByIdSafe(String id) {
        if (id == null || id.trim().isEmpty()) return null;
        String sql = "SELECT t.*, (SELECT TOP 1 format FROM tournament_stages WHERE tournament_id = t.id ORDER BY stage_order ASC) AS stage_format FROM tournaments t WHERE t.id = ?";
        dao.DBContext db = new dao.DBContext();
        try (java.sql.Connection conn = db.getConnection();
             java.sql.PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id.trim());
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Tournament t = new Tournament(
                        rs.getString("id"),
                        rs.getString("series_id"),
                        rs.getString("name"),
                        rs.getString("tournament_type"),
                        rs.getString("series_event_type"),
                        rs.getString("tier_name"),
                        rs.getInt("tournament_index_in_series"),
                        rs.getInt("phase_number"),
                        rs.getInt("max_teams_per_group"),
                        rs.getInt("advancing_seats_count"),
                        rs.getString("linked_qualifier_tournament_id"),
                        rs.getString("status"),
                        rs.getTimestamp("created_at")
                    );
                    try {
                        String fmt = rs.getString("stage_format");
                        if (fmt != null && !fmt.trim().isEmpty()) {
                            t.setFormat(fmt.trim());
                        }
                    } catch (Exception ignore) {}
                    try {
                        t.setSeriesRewardPoints(rs.getInt("series_reward_points"));
                    } catch (Exception ignore) {}
                    try {
                        t.setSeriesPointsConfig(rs.getString("series_points_config"));
                    } catch (Exception ignore) {}
                    return t;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private boolean saveTournamentPointsConfigSafe(String tournamentId, int rewardPoints, String pointsConfigJson) {
        if (tournamentId == null || tournamentId.trim().isEmpty()) return false;
        String sql = "UPDATE tournaments SET series_reward_points = ?, series_points_config = ? WHERE id = ?";
        dao.DBContext db = new dao.DBContext();
        try (java.sql.Connection conn = db.getConnection();
             java.sql.PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, rewardPoints);
            ps.setString(2, pointsConfigJson);
            ps.setString(3, tournamentId.trim());
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    private List<String> getStageFormatsSafe(String tournamentId) {
        List<String> list = new ArrayList<>();
        if (tournamentId == null || tournamentId.trim().isEmpty()) return list;
        String sql = "SELECT format FROM tournament_stages WHERE tournament_id = ? ORDER BY stage_order ASC";
        dao.DBContext db = new dao.DBContext();
        try (java.sql.Connection conn = db.getConnection();
             java.sql.PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tournamentId);
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(rs.getString("format"));
                }
            }
        } catch (Exception ignore) {}
        return list;
    }
}
