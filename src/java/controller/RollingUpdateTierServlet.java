package controller;

import dao.TournamentDAO;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * Controller Servlet for updating a sub-tournament tier (S, A, B, C, D) via AJAX or form submission.
 * URL Patterns: /rolling/update-tier, /tournament/update-tier
 */
@WebServlet(name = "RollingUpdateTierServlet", urlPatterns = {"/rolling/update-tier", "/tournament/update-tier"})
public class RollingUpdateTierServlet extends HttpServlet {

    private final TournamentDAO tournamentDAO = new TournamentDAO();

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
        request.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");

        String tournamentId = request.getParameter("tournamentId");
        if (tournamentId == null || tournamentId.trim().isEmpty()) {
            tournamentId = request.getParameter("id");
        }

        String tierName = request.getParameter("tierName");
        if (tierName == null || tierName.trim().isEmpty()) {
            tierName = request.getParameter("tier");
        }

        try (PrintWriter out = response.getWriter()) {
            if (tournamentId == null || tournamentId.trim().isEmpty() || tierName == null || tierName.trim().isEmpty()) {
                out.print("{\"status\":\"error\",\"message\":\"Thiếu thông tin tournamentId hoặc tierName!\"}");
                return;
            }

            String cleanTier = tierName.replace("Tier ", "").trim().toUpperCase();
            if (!cleanTier.equals("S") && !cleanTier.equals("A") && !cleanTier.equals("B") && !cleanTier.equals("C") && !cleanTier.equals("D")) {
                out.print("{\"status\":\"error\",\"message\":\"Tier không hợp lệ! (Chỉ chấp nhận S, A, B, C, D)\"}");
                return;
            }

            boolean ok = tournamentDAO.updateTournamentTier(tournamentId.trim(), cleanTier);
            if (ok) {
                out.print("{\"status\":\"success\",\"tournamentId\":\"" + tournamentId.trim() + "\",\"tier\":\"" + cleanTier + "\"}");
            } else {
                out.print("{\"status\":\"error\",\"message\":\"Không thể cập nhật Tier trong cơ sở dữ liệu!\"}");
            }
        } catch (Exception e) {
            e.printStackTrace();
            try (PrintWriter out = response.getWriter()) {
                out.print("{\"status\":\"error\",\"message\":\"" + e.getMessage() + "\"}");
            }
        }
    }
}
