package controller;

import dao.TournamentDAO;
import model.Tournament;
import java.io.IOException;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@WebServlet(name = "ManageGroupServlet", urlPatterns = {"/manage-group", "/ManageGroupServlet"})
public class ManageGroupServlet extends HttpServlet {

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
            request.setAttribute("dbGroupAssignments", tournament.getGroupAssignments());
        }

        request.getRequestDispatcher("/common/manage-group.jsp").forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        request.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");

        String tournamentId = request.getParameter("tournamentId");
        if (tournamentId == null || tournamentId.trim().isEmpty()) {
            tournamentId = request.getParameter("id");
        }

        String groupAssignmentsJson = request.getParameter("groupAssignments");
        if (tournamentId != null && groupAssignmentsJson != null && !groupAssignmentsJson.trim().isEmpty()) {
            TournamentDAO tDao = new TournamentDAO();
            boolean ok = tDao.saveGroupAssignments(tournamentId.trim(), groupAssignmentsJson);
            response.getWriter().print("{\"status\":\"" + (ok ? "success" : "error") + "\",\"message\":\"" + (ok ? "Đã lưu chia bảng vào CSDL!" : "Lỗi lưu chia bảng!") + "\"}");
            return;
        }

        doGet(request, response);
    }
}
