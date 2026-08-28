package controller;

import dao.RoundRobinDAO;
import dao.TournamentDAO;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import model.Tournament;

/**
 * Controller Servlet for Round Robin Tournament Format.
 * Handles page routing and AJAX match score saving.
 */
@WebServlet(name = "RoundRobinServlet", urlPatterns = {"/round-robin"})
public class RoundRobinServlet extends HttpServlet {

    private final RoundRobinDAO roundRobinDAO = new RoundRobinDAO();
    private final TournamentDAO tournamentDAO = new TournamentDAO();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String tournamentId = request.getParameter("id");
        if (tournamentId == null || tournamentId.trim().isEmpty()) {
            tournamentId = "demo";
        }

        Tournament tournament = tournamentDAO.getTournamentById(tournamentId);
        if (tournament == null) {
            tournament = new Tournament();
            tournament.setId(tournamentId);
            tournament.setName("Giải Đấu Vòng Tròn Tính Điểm");
            tournament.setFormat("ROUND_ROBIN");
        }

        request.setAttribute("tournament", tournament);
        request.getRequestDispatcher("/common/round-robin.jsp").forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        request.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");

        String matchId = request.getParameter("matchId");
        String team1Score = request.getParameter("team1Score");
        String team2Score = request.getParameter("team2Score");
        String winner = request.getParameter("winner");

        boolean success = roundRobinDAO.updateMatchScore(matchId, team1Score, team2Score, winner);

        try (PrintWriter out = response.getWriter()) {
            out.print("{\"success\": " + success + ", \"matchId\": \"" + matchId + "\"}");
            out.flush();
        }
    }
}
