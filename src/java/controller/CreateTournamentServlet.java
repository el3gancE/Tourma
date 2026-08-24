package controller;

import dao.TournamentDAO;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import model.Tournament;

/**
 * Controller Servlet for Tournament Creation (Step 1: Name & Description)
 * Immediately inserts the new Tournament record into SQL Server database.
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

            // 3. Create Tournament Entity & Save to Database
            Tournament t = new Tournament();
            t.setId(tournamentId);
            t.setSeriesId(null); // Standalone tournament
            t.setName(name.trim());
            t.setTournamentType("SINGLE_STAGE"); // Default to Single Stage initially
            t.setSeriesEventType("NONE");
            t.setTierName(null);
            t.setTournamentIndexInSeries(1);
            t.setPhaseNumber(1);
            t.setMaxTeamsPerGroup(4);
            t.setAdvancingSeatsCount(16);
            t.setLinkedQualifierTournamentId(null);
            t.setStatus("DRAFT");

            TournamentDAO dao = new TournamentDAO();
            boolean inserted = dao.insertTournament(t);

            if (inserted) {
                System.out.println("=== TOURNAMENT STEP 1 SAVED TO DB ===");
                System.out.println("ID: " + tournamentId);
                System.out.println("Name: " + name);
            } else {
                System.err.println("Failed to insert tournament into database: " + tournamentId);
            }

            // 4. Redirect to Step 2: Format Configuration Screen (web/common/configure-tournament-format.jsp)
            response.sendRedirect(request.getContextPath() + "/common/configure-tournament-format.jsp?id=" + tournamentId);

        } catch (Exception e) {
            e.printStackTrace();
            request.setAttribute("errorMessage", "Lỗi trong quá trình khởi tạo giải đấu: " + e.getMessage());
            request.getRequestDispatcher("/common/create-tournament.jsp").forward(request, response);
        }
    }
}
