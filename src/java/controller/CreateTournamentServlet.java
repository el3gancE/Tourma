package controller;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;

/**
 * Controller Servlet for Tournament Creation (Step 1: Name & Description)
 * Uses Jakarta EE 10 (jakarta.servlet.*) for Apache Tomcat 10+ compatibility.
 */
@WebServlet(name = "CreateTournamentServlet", urlPatterns = {"/create-tournament", "/create-tournament.jsp"})
public class CreateTournamentServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        // Forward directly to JSP View inside web/common/create-tournament.jsp
        request.getRequestDispatcher("/common/create-tournament.jsp").forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        request.setCharacterEncoding("UTF-8");
        response.setContentType("text/html;charset=UTF-8");

        try {
            // 1. Retrieve Name & Description Parameters
            String name = request.getParameter("name");
            String description = request.getParameter("description");

            // Validation
            if (name == null || name.trim().isEmpty()) {
                request.setAttribute("errorMessage", "Tên giải đấu không được để trống.");
                request.getRequestDispatcher("/common/create-tournament.jsp").forward(request, response);
                return;
            }

            // 2. Generate Unique Tournament ID
            String tournamentId = "T_" + UUID.randomUUID().toString().substring(0, 8);

            // Log / Debug output
            System.out.println("=== TOURNAMENT STEP 1 CREATED ===");
            System.out.println("ID: " + tournamentId);
            System.out.println("Name: " + name);
            System.out.println("Description: " + description);

            // 3. Redirect to Step 2: Format Configuration Screen (web/common/configure-tournament-format.jsp)
            response.sendRedirect(request.getContextPath() + "/common/configure-tournament-format.jsp?id=" + tournamentId);

        } catch (Exception e) {
            e.printStackTrace();
            request.setAttribute("errorMessage", "Lỗi trong quá trình khởi tạo giải đấu: " + e.getMessage());
            request.getRequestDispatcher("/common/create-tournament.jsp").forward(request, response);
        }
    }
}
