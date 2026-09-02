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

/**
 * Controller Servlet for Step 1: Creating a Sub-Tournament in a Series
 * Pattern: /rolling/create-tournament
 */
@WebServlet(name = "RollingCreateTournamentServlet", urlPatterns = {"/rolling/create-tournament"})
public class RollingCreateTournamentServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        String seriesId = request.getParameter("seriesId");
        SeriesDAO seriesDAO = new SeriesDAO();
        TournamentDAO tournamentDAO = new TournamentDAO();

        List<Series> allSeries = seriesDAO.getAllSeries();
        Series selectedSeries = null;

        if (seriesId != null && !seriesId.trim().isEmpty()) {
            selectedSeries = seriesDAO.getSeriesById(seriesId.trim());
        }

        if (selectedSeries == null && allSeries != null && !allSeries.isEmpty()) {
            for (Series s : allSeries) {
                if ("ROLLING_WINDOW".equalsIgnoreCase(s.getRankingModel())) {
                    selectedSeries = s;
                    break;
                }
            }
            if (selectedSeries == null) {
                selectedSeries = allSeries.get(0);
            }
        }

        int nextIndex = 1;
        if (selectedSeries != null) {
            List<Tournament> existingTourneys = tournamentDAO.getTournamentsBySeriesId(selectedSeries.getId());
            nextIndex = existingTourneys.size() + 1;
        }

        request.setAttribute("allSeries", allSeries);
        request.setAttribute("selectedSeries", selectedSeries);
        request.setAttribute("nextIndex", nextIndex);

        request.getRequestDispatcher("/common/rolling/rolling-create-tournament.jsp").forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        String seriesId = request.getParameter("seriesId");
        String name = request.getParameter("name");
        String tierName = request.getParameter("tierName");

        SeriesDAO seriesDAO = new SeriesDAO();
        TournamentDAO tournamentDAO = new TournamentDAO();

        if (seriesId == null || seriesId.trim().isEmpty()) {
            List<Series> allSeries = seriesDAO.getAllSeries();
            if (allSeries != null && !allSeries.isEmpty()) {
                seriesId = allSeries.get(0).getId();
            }
        }

        List<Tournament> existingTourneys = tournamentDAO.getTournamentsBySeriesId(seriesId);
        int indexInSeries = (existingTourneys != null) ? (existingTourneys.size() + 1) : 1;

        String tournamentId = "TOURNEY_" + System.currentTimeMillis();

        // Create new sub-tournament record
        Tournament t = new Tournament();
        t.setId(tournamentId);
        t.setName(name);
        t.setSeriesId(seriesId);
        t.setTierName(tierName != null && !tierName.trim().isEmpty() ? tierName.trim() : "S");
        t.setTournamentIndexInSeries(indexInSeries);
        t.setPhaseNumber(1);
        t.setStatus("DRAFT");
        t.setFormat("KNOCKOUT");
        t.setTournamentType("SINGLE_STAGE");

        try {
            boolean created = tournamentDAO.createSubTournamentInSeries(t);

            if (created) {
                // Proceed to Step 2: Configure Format
                response.sendRedirect(request.getContextPath() + "/common/configure-tournament-format.jsp?id=" + tournamentId + "&seriesId=" + seriesId);
            } else {
                request.setAttribute("errorMessage", "Không thể khởi tạo giải đấu con. Vui lòng kiểm tra lại thông tin.");
                doGet(request, response);
            }
        } catch (Exception e) {
            request.setAttribute("errorMessage", "Lỗi DB: " + e.getMessage());
            doGet(request, response);
        }
    }
}
