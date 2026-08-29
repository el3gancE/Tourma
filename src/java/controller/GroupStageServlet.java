package controller;

import dao.TournamentDAO;
import model.Tournament;
import java.io.IOException;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@WebServlet(name = "GroupStageServlet", urlPatterns = {"/group-stage", "/GroupStageServlet"})
public class GroupStageServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String tournamentId = request.getParameter("id");
        if (tournamentId == null || tournamentId.trim().isEmpty()) {
            tournamentId = "demo";
        }

        TournamentDAO tDao = new TournamentDAO();
        Tournament tournament = tDao.getTournamentById(tournamentId);

        if (tournament != null) {
            request.setAttribute("tournament", tournament);
        }

        request.getRequestDispatcher("/common/group-stage.jsp").forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        doGet(request, response);
    }
}
