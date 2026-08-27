package controller;

import dao.TournamentDAO;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Controller Servlet for Deleting Tournaments
 */
@WebServlet(name = "DeleteTournamentServlet", urlPatterns = {"/delete-tournament"})
public class DeleteTournamentServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        processRequest(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        processRequest(request, response);
    }

    private void processRequest(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String id = request.getParameter("id");
        if (id != null && !id.trim().isEmpty()) {
            TournamentDAO dao = new TournamentDAO();
            dao.deleteTournament(id.trim());
        }

        response.sendRedirect(request.getContextPath() + "/my-tournaments");
    }
}
