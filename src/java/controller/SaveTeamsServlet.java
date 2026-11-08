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
        TournamentDAO configurationDAO = new TournamentDAO();
        model.Tournament savedTournament = configurationDAO.getTournamentById(tournamentId, false);
        boolean standaloneSetup = savedTournament != null
                && (savedTournament.getSeriesId() == null || savedTournament.getSeriesId().trim().isEmpty())
                && request.getParameter("tournamentType") != null;
        List<String> setupTeams = new ArrayList<>();
        if (standaloneSetup) {
            if (teamListRaw != null) for (String line : teamListRaw.split("\\r?\\n")) {
                String clean = line.trim().replaceAll("^[0-9]+[\\.\\-\\)\\s]+", "").trim().replaceAll("\\s+", " ");
                if (!clean.isEmpty()) setupTeams.add(clean);
            }
            java.util.Set<String> formats = java.util.Set.of("SINGLE_ELIMINATION", "DOUBLE_ELIMINATION", "ROUND_ROBIN", "GROUP_STAGE", "GSL", "SWISS_LITE");
            String setupType = request.getParameter("tournamentType");
            boolean multi = "MULTI_STAGE".equals(setupType);
            if ((!multi && !"SINGLE_STAGE".equals(setupType)) || !formats.contains(format == null ? "" : format)
                    || (multi && (!formats.contains(request.getParameter("stage1Format") == null ? "" : request.getParameter("stage1Format"))
                    || !formats.contains(request.getParameter("stage2Format") == null ? "" : request.getParameter("stage2Format"))))
                    || setupTeams.size() < 2 || new java.util.HashSet<>(setupTeams).size() != setupTeams.size()
                    || ("ROUND_ROBIN".equals(format) && setupTeams.size() > 24)
                    || ("SWISS_LITE".equals(format) && setupTeams.size() != 16)) {
                response.sendError(400, "Thể thức hoặc danh sách đội không hợp lệ.");
                return;
            }
            String copySource = request.getParameter("copiedFrom");
            if (copySource != null && !copySource.trim().isEmpty()) {
                model.Tournament source = configurationDAO.getTournamentById(copySource, false);
                if (!configurationDAO.canCopyTournamentConfig(copySource, tournamentId) || source == null
                        || (source.getTeamCount() > 0 && source.getTeamCount() != setupTeams.size())) {
                    response.sendError(400, "Nguồn sao chép hoặc số đội không hợp lệ.");
                    return;
                }
            }
            List<String> previousTeams = new ArrayList<>();
            for (model.Team team : new ParticipantDAO().getTeamsByTournamentId(tournamentId)) previousTeams.add(team.getRawName());
            if ((configurationDAO.hasStageMatches(tournamentId, 1) || configurationDAO.hasStageMatches(tournamentId, 2))
                    && !previousTeams.equals(setupTeams)) {
                response.sendError(409, "Giải đã có lịch. Không thể thay đổi danh sách hoặc thứ tự đội tại đây.");
                return;
            }
            String type = request.getParameter("tournamentType");
            String stage1 = request.getParameter("stage1Format");
            String stage2 = request.getParameter("stage2Format");
            int advancing = 0;
            try { advancing = Integer.parseInt(request.getParameter("advancingSeatsCount")); }
            catch (Exception ignored) { }
            if (!configurationDAO.updateTournamentFormatAndType(tournamentId, format, type,
                    stage1, stage2, advancing, request.getParameter("multiConfig"))) {
                response.sendError(409, "Không lưu được cấu hình. Stage đã có lịch không thể đổi cấu trúc.");
                return;
            }
            String copiedFrom = request.getParameter("copiedFrom");
            if (copiedFrom != null && !copiedFrom.trim().isEmpty()
                    && !configurationDAO.copyPointsConfigFromTournament(copiedFrom, tournamentId, false)) {
                response.sendError(409, "Không lưu được cấu hình sao chép.");
                return;
            }
        }
        if (!new TournamentDAO().saveTopSeedCount(tournamentId, request.getParameter("topSeedCount"))
                || !new TournamentDAO().saveClassicDraw(tournamentId, request.getParameter("classicDraw"))) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Không thể lưu cấu hình phân cặp vào DB.");
            return;
        }

        if (!standaloneSetup && tournamentId != null && format != null && !format.trim().isEmpty()) {
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
                List<String> previousNames = new ArrayList<>();
                if (standaloneSetup) for (model.Team team : dao.getTeamsByTournamentId(tournamentId)) previousNames.add(team.getRawName());
                if ((!standaloneSetup || !previousNames.equals(teamNames)) && !dao.saveTournamentTeams(tournamentId, teamNames)) {
                    response.sendError(409, "Không lưu được danh sách đội.");
                    return;
                }

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
