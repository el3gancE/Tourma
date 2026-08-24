package controller;

import dao.TournamentDAO;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import model.Tournament;

/**
 * Controller Servlet for My Tournaments Screen (Giải Đấu Của Tôi)
 * Queries SQL Server database via TournamentDAO and forwards to /common/my-tournaments.jsp
 */
@WebServlet(name = "MyTournamentsServlet", urlPatterns = {"/my-tournaments", "/my-tournaments.jsp"})
public class MyTournamentsServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        
        TournamentDAO dao = new TournamentDAO();
        List<Tournament> tournamentList = dao.getAllTournaments();
        
        request.setAttribute("tournamentList", tournamentList);
        request.getRequestDispatcher("/common/my-tournaments.jsp").forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        doGet(request, response);
    }
}
