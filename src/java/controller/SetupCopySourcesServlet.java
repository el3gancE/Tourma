package controller;

import dao.TournamentDAO;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import model.Tournament;

@WebServlet("/setup/copy-sources")
public class SetupCopySourcesServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String tournamentId = request.getParameter("id");
        TournamentDAO tDao = new TournamentDAO();
        Tournament destination = tournamentId == null || tournamentId.isBlank()
                ? null : tDao.getTournamentById(tournamentId.trim(), false);
        if (destination == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        String seriesIdParam = destination.getSeriesId();
        List<Tournament> availableTournaments = new ArrayList<>();
        List<Tournament> allTourneys = seriesIdParam != null && !seriesIdParam.trim().isEmpty()
                ? tDao.getTournamentsBySeriesId(seriesIdParam.trim()) : tDao.getAllTournaments();
        if (allTourneys != null) {
            for (Tournament t : allTourneys) {
                if (!destination.getId().equalsIgnoreCase(t.getId())) {
                    availableTournaments.add(t);
                }
            }
        }
        if (seriesIdParam != null && !seriesIdParam.trim().isEmpty()) {
            availableTournaments.sort((a, b) -> {
                int order = Integer.compare(b.getTournamentIndexInSeries(), a.getTournamentIndexInSeries());
                if (order != 0) return order;
                if (a.getCreatedAt() == null && b.getCreatedAt() != null) return 1;
                if (a.getCreatedAt() != null && b.getCreatedAt() == null) return -1;
                if (a.getCreatedAt() != null) {
                    int created = b.getCreatedAt().compareTo(a.getCreatedAt());
                    if (created != 0) return created;
                }
                return a.getId().compareTo(b.getId());
            });
        }
        request.setAttribute("copySources", availableTournaments);
        request.setAttribute("copySourcesSeries", seriesIdParam != null && !seriesIdParam.isBlank());
        response.setHeader("Cache-Control", "no-store");
        request.getRequestDispatcher("/WEB-INF/setup-copy-sources.jsp").forward(request, response);
    }
}
