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
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
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

        SeriesDAO seriesDAO = new SeriesDAO();
        ParticipantDAO participantDAO = new ParticipantDAO();

        String advSeatsParam = request.getParameter("advancingSeatsCount");
        if (advSeatsParam == null || advSeatsParam.trim().isEmpty()) {
            advSeatsParam = request.getParameter("advanceCount");
        }
        int advSeats = 0;
        if (advSeatsParam != null && !advSeatsParam.trim().isEmpty()) {
            try {
                advSeats = Integer.parseInt(advSeatsParam.trim());
            } catch (Exception ignore) {}
        }

        String fmt = request.getParameter("format");
        if (fmt == null || fmt.trim().isEmpty()) fmt = request.getParameter("selectedFormat");
        String tType = request.getParameter("tournamentType");
        if (tType == null || tType.trim().isEmpty()) tType = request.getParameter("selectedTournamentType");
        if (tType == null || tType.trim().isEmpty()) tType = request.getParameter("type");

        String s1F = request.getParameter("stage1Format");
        String s2F = request.getParameter("stage2Format");

        if (tournamentId != null && ((fmt != null && !fmt.trim().isEmpty()) || advSeats > 0)) {
            updateTournamentFormatAndTypeSafe(tournamentId.trim(), fmt, tType, s1F, s2F, advSeats);
        }

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

        String advSeatsParam = request.getParameter("advancingSeatsCount");
        if (advSeatsParam == null || advSeatsParam.trim().isEmpty()) {
            advSeatsParam = request.getParameter("advanceCount");
        }
        int advSeats = 0;
        if (advSeatsParam != null && !advSeatsParam.trim().isEmpty()) {
            try {
                advSeats = Integer.parseInt(advSeatsParam.trim());
            } catch (Exception ignore) {}
        }

        String fmt = request.getParameter("selectedFormat");
        if (fmt == null || fmt.trim().isEmpty()) fmt = request.getParameter("format");
        String tType = request.getParameter("selectedTournamentType");
        if (tType == null || tType.trim().isEmpty()) tType = request.getParameter("type");

        if (tournamentId != null && ((fmt != null && !fmt.trim().isEmpty()) || advSeats > 0)) {
            updateTournamentFormatAndTypeSafe(tournamentId, fmt, tType, null, null, advSeats);
        }

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
        } else if ("saveTeamsOrder".equalsIgnoreCase(action)) {
            String orderedNamesRaw = request.getParameter("orderedTeamNames");
            if (orderedNamesRaw != null && !orderedNamesRaw.trim().isEmpty() && tournamentId != null) {
                String[] lines = orderedNamesRaw.split("\\r?\\n");
                List<String> teamList = new ArrayList<>();
                for (String line : lines) {
                    if (line != null && !line.trim().isEmpty()) {
                        teamList.add(line.trim());
                    }
                }
                if (!teamList.isEmpty()) {
                    participantDAO.saveTournamentTeams(tournamentId, teamList);
                }
            }
            String nextUrl = request.getParameter("nextUrl");
            if (nextUrl != null && !nextUrl.trim().isEmpty()) {
                response.sendRedirect(nextUrl);
                return;
            }
            response.sendRedirect(request.getContextPath() + "/rolling/point-config?id=" + tournamentId + "&seriesId=" + (seriesId != null ? seriesId : ""));
            return;
        }

        response.sendRedirect(request.getContextPath() + "/rolling/tournament-teams?id=" + tournamentId + "&seriesId=" + (seriesId != null ? seriesId : ""));
    }

    private Tournament getTournamentByIdSafe(String id) {
        if (id == null || id.trim().isEmpty()) return null;
        String sql = "SELECT t.*, (SELECT TOP 1 format FROM tournament_stages WHERE tournament_id = t.id ORDER BY stage_order ASC) AS stage_format, " +
                     "(SELECT TOP 1 tm.raw_name FROM matches m JOIN teams tm ON m.winner_id = tm.id WHERE m.tournament_id = t.id AND m.winner_id IS NOT NULL ORDER BY m.round_number DESC) AS db_champion_name " +
                     "FROM tournaments t WHERE t.id = ?";
        dao.DBContext db = new dao.DBContext();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id.trim());
            try (ResultSet rs = ps.executeQuery()) {
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
                        String champ = rs.getString("db_champion_name");
                        if (champ != null && !champ.trim().isEmpty()) {
                            t.setChampionName(champ.trim());
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

    private boolean updateTournamentFormatAndTypeSafe(String tournamentId, String format, String tournamentType, String stage1Format, String stage2Format, int advancingSeatsCount) {
        if (tournamentId == null || tournamentId.trim().isEmpty()) return false;
        if (format == null || format.trim().isEmpty()) format = "SINGLE_ELIMINATION";
        if (tournamentType == null || tournamentType.trim().isEmpty()) tournamentType = "SINGLE_STAGE";

        String sqlTourney = (advancingSeatsCount > 0)
            ? "UPDATE tournaments SET tournament_type = ?, advancing_seats_count = ? WHERE id = ?"
            : "UPDATE tournaments SET tournament_type = ? WHERE id = ?";
        dao.DBContext db = new dao.DBContext();
        try (Connection conn = db.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(sqlTourney)) {
                if (advancingSeatsCount > 0) {
                    ps.setString(1, tournamentType.trim().toUpperCase());
                    ps.setInt(2, advancingSeatsCount);
                    ps.setString(3, tournamentId.trim());
                } else {
                    ps.setString(1, tournamentType.trim().toUpperCase());
                    ps.setString(2, tournamentId.trim());
                }
                ps.executeUpdate();
            }

            String sqlDeleteStages = "DELETE FROM tournament_stages WHERE tournament_id = ?";
            try (PreparedStatement ps = conn.prepareStatement(sqlDeleteStages)) {
                ps.setString(1, tournamentId.trim());
                ps.executeUpdate();
            }

            if ("MULTI_STAGE".equalsIgnoreCase(tournamentType)) {
                String s1 = (stage1Format != null && !stage1Format.trim().isEmpty()) ? stage1Format.trim().toUpperCase() : "GROUP_STAGE";
                String s2 = (stage2Format != null && !stage2Format.trim().isEmpty()) ? stage2Format.trim().toUpperCase() : "SINGLE_ELIMINATION";

                String sqlInsert1 = "INSERT INTO tournament_stages (id, tournament_id, stage_order, stage_name, format) VALUES (?, ?, 1, N'Stage 1', ?)";
                try (PreparedStatement ps = conn.prepareStatement(sqlInsert1)) {
                    ps.setString(1, "STAGE_1_" + System.currentTimeMillis());
                    ps.setString(2, tournamentId.trim());
                    ps.setString(3, s1);
                    ps.executeUpdate();
                }

                String sqlInsert2 = "INSERT INTO tournament_stages (id, tournament_id, stage_order, stage_name, format) VALUES (?, ?, 2, N'Stage 2', ?)";
                try (PreparedStatement ps = conn.prepareStatement(sqlInsert2)) {
                    ps.setString(1, "STAGE_2_" + System.currentTimeMillis());
                    ps.setString(2, tournamentId.trim());
                    ps.setString(3, s2);
                    ps.executeUpdate();
                }
            } else {
                String sqlInsertStage = "INSERT INTO tournament_stages (id, tournament_id, stage_order, stage_name, format) VALUES (?, ?, 1, N'Main Stage', ?)";
                try (PreparedStatement ps = conn.prepareStatement(sqlInsertStage)) {
                    ps.setString(1, "STAGE_" + System.currentTimeMillis());
                    ps.setString(2, tournamentId.trim());
                    ps.setString(3, format.trim().toUpperCase());
                    ps.executeUpdate();
                }
            }
            conn.commit();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }
}
