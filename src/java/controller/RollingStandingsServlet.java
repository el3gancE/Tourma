package controller;

import dao.SeriesDAO;
import dao.TournamentDAO;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import model.Series;
import model.Tournament;
import service.RollingWindowPointService;
import service.RollingWindowPointService.RollingStandingDTO;

/**
 * Controller Servlet for Series Standings Screen
 * Pattern: /rolling/standings
 */
@WebServlet(name = "RollingStandingsServlet", urlPatterns = {"/rolling/standings"})
public class RollingStandingsServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        String seriesId = request.getParameter("id");
        if (seriesId == null || seriesId.trim().isEmpty()) seriesId = request.getParameter("seriesId");
        SeriesDAO seriesDAO = new SeriesDAO();
        TournamentDAO tournamentDAO = new TournamentDAO();

        Series series = null;
        if (seriesId != null && !seriesId.trim().isEmpty()) {
            series = seriesDAO.getSeriesById(seriesId.trim());
        }

        if ("snapshot".equalsIgnoreCase(request.getParameter("action"))) {
            if (series == null || !"ROLLING_WINDOW".equalsIgnoreCase(series.getRankingModel())) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
            writeSnapshot(request, response, series);
            return;
        }

        if (series == null) {
            List<Series> allSeries = seriesDAO.getAllSeries();
            for (Series s : allSeries) {
                if ("ROLLING_WINDOW".equalsIgnoreCase(s.getRankingModel())) {
                    series = s;
                    break;
                }
            }
            if (series == null && !allSeries.isEmpty()) {
                series = allSeries.get(0);
            }
        }

        List<Tournament> tournamentsList = null;
        List<model.PartnerParticipant> partnerList = null;
        List<service.RollingWindowPointService.RollingStandingDTO> standingsDTOList = null;
        List<java.util.Map<String, Integer>> serverTourneyPoints = null;
        List<java.util.Map<String, Boolean>> serverTourneyParticipation = null;
        java.util.Map<String, List<String>> stageFormatsMap = new java.util.HashMap<>();

        if (series != null) {
            seriesId = series.getId();
            service.RollingWindowPointService serviceEngine = service.RollingWindowPointService.getInstance();
            if ("ROLLING_WINDOW".equalsIgnoreCase(series.getRankingModel())
                    && !serviceEngine.recalculateAndPersistStandings(seriesId)) {
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Không thể lưu bảng xếp hạng vào DB.");
                return;
            }
            serviceEngine.preloadSeriesData(seriesId);
            standingsDTOList = serviceEngine.calculateSeriesStandingsWithExpiry(seriesId);
            tournamentsList = seriesDAO.getTournamentsBySeriesId(seriesId);
            partnerList = seriesDAO.getPartnerParticipantsBySeriesId(seriesId);
            serverTourneyPoints = serviceEngine.getTourneyPointsPerTournament(seriesId);
            serverTourneyParticipation = serviceEngine.getTourneyParticipationPerTournament(seriesId);

            stageFormatsMap = tournamentDAO.getStageFormatsBySeriesId(seriesId);
        }

        request.setAttribute("series", series);
        request.setAttribute("tournamentsList", tournamentsList);
        request.setAttribute("partnerList", partnerList);
        request.setAttribute("standingsDTOList", standingsDTOList);
        request.setAttribute("serverTourneyPoints", serverTourneyPoints);
        request.setAttribute("serverTourneyParticipation", serverTourneyParticipation);
        request.setAttribute("stageFormatsMap", stageFormatsMap);

        request.getRequestDispatcher("/common/rolling/rolling-standings.jsp").forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        request.setCharacterEncoding("UTF-8");
        String action = request.getParameter("action");
        if ("updateSettings".equalsIgnoreCase(action) || "updatePhaseSize".equalsIgnoreCase(action)) {
            String seriesId = request.getParameter("seriesId");
            if (seriesId == null || seriesId.trim().isEmpty()) {
                seriesId = request.getParameter("id");
            }
            String name = request.getParameter("name");
            String phaseSizeStr = request.getParameter("phaseSize");
            String status = request.getParameter("status");
            int phaseSize = 3;
            try {
                if (phaseSizeStr != null && !phaseSizeStr.trim().isEmpty()) {
                    phaseSize = Integer.parseInt(phaseSizeStr.trim());
                }
            } catch (Exception ignore) {}

            SeriesDAO seriesDAO = new SeriesDAO();
            if (seriesId != null && !seriesId.trim().isEmpty()) {
                Series current = seriesDAO.getSeriesById(seriesId.trim());
                if (name == null || name.trim().isEmpty()) {
                    if (current != null) name = current.getName();
                }
                if (status == null || status.trim().isEmpty()) {
                    if (current != null) status = current.getStatus();
                }
                seriesDAO.updateSeriesSettings(seriesId.trim(), name, phaseSize, status);
            }
            response.sendRedirect(request.getContextPath() + "/rolling/standings?id=" + (seriesId != null ? seriesId.trim() : ""));
            return;
        } else if ("syncClientStandings".equalsIgnoreCase(action) || "recalculate".equalsIgnoreCase(action)) {
            response.setContentType("application/json;charset=UTF-8");
            String seriesId = request.getParameter("seriesId");
            // Older tabs may still submit client totals. Never consume those values.
            boolean success = RollingWindowPointService.getInstance().recalculateAndPersistStandings(seriesId);
            if (!success) response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("{\"success\":" + success + "}");
            return;
        }
        doGet(request, response);
    }

    private void writeSnapshot(HttpServletRequest request, HttpServletResponse response, Series series) throws IOException {
        List<Tournament> tournaments = new SeriesDAO().getTournamentsBySeriesId(series.getId());
        Integer milestone = null;
        String raw = request.getParameter("milestone");
        if (raw != null && !"LATEST".equalsIgnoreCase(raw)) {
            try {
                milestone = Integer.valueOf(raw);
                if (milestone < 0 || milestone >= tournaments.size()) throw new NumberFormatException();
            } catch (NumberFormatException e) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid standings milestone");
                return;
            }
        }
        List<RollingStandingDTO> standings = RollingWindowPointService.getInstance()
                .calculateSeriesStandingsWithExpiry(series.getId(), milestone);
        StringBuilder json = new StringBuilder("{\"targetIdx\":")
                .append(milestone == null ? tournaments.size() - 1 : milestone).append(",\"teamDataArray\":[");
        for (int i = 0; i < standings.size(); i++) {
            RollingStandingDTO dto = standings.get(i);
            if (i > 0) json.append(',');
            json.append("{\"name\":").append(jsonString(dto.getTeamName()))
                .append(",\"rank\":").append(dto.getRank())
                .append(",\"rankChange\":").append(dto.getRankChange())
                .append(",\"totalPts\":").append(dto.getTotalActivePoints())
                .append(",\"lastPts\":").append(dto.getLastTourneyPoints())
                .append(",\"droppedPts\":").append(dto.getDroppedTourneyPoints())
                .append(",\"activeTourneys\":").append(dto.getActiveTourneysCount()).append('}');
        }
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Cache-Control", "no-store");
        response.getWriter().write(json.append("]}").toString());
    }

    private static String jsonString(String value) {
        if (value == null) return "null";
        StringBuilder json = new StringBuilder("\"");
        for (char c : value.toCharArray()) {
            if (c == '"' || c == '\\') json.append('\\').append(c);
            else if (c < 32) json.append(String.format("\\u%04x", (int) c));
            else json.append(c);
        }
        return json.append('"').toString();
    }
}
