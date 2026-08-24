package controller;

import dao.SeriesDAO;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import model.Series;

/**
 * Controller Servlet for Series Creation (Rolling Window & FIFA Elo Rating)
 * Immediately inserts the new Series record into SQL Server database.
 * Uses Jakarta EE 10 (jakarta.servlet.*) for Apache Tomcat 10+ compatibility.
 */
@WebServlet(name = "CreateSeriesServlet", urlPatterns = {"/create-series", "/create-series.jsp"})
public class CreateSeriesServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        // Forward directly to JSP View inside web/common/create-series.jsp
        request.getRequestDispatcher("/common/create-series.jsp").forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        request.setCharacterEncoding("UTF-8");
        response.setContentType("text/html;charset=UTF-8");

        try {
            // 1. Retrieve Form Parameters
            String name = request.getParameter("name");
            String description = request.getParameter("description");
            String rankingModel = request.getParameter("rankingModel"); // ROLLING_WINDOW or FIFA_ELO
            String phaseSizeStr = request.getParameter("phaseSize");
            String initialEloStr = request.getParameter("initialElo");

            // Validation
            if (name == null || name.trim().isEmpty()) {
                request.setAttribute("errorMessage", "Tên chuỗi giải không được để trống.");
                request.getRequestDispatcher("/common/create-series.jsp").forward(request, response);
                return;
            }

            if (rankingModel == null || rankingModel.trim().isEmpty()) {
                rankingModel = "ROLLING_WINDOW";
            }

            int phaseSize = 10;
            if (phaseSizeStr != null && !phaseSizeStr.trim().isEmpty()) {
                try { phaseSize = Integer.parseInt(phaseSizeStr); } catch (Exception ignored) {}
            }

            double initialElo = 1000.0;
            if (initialEloStr != null && !initialEloStr.trim().isEmpty()) {
                try { initialElo = Double.parseDouble(initialEloStr); } catch (Exception ignored) {}
            }

            // 2. Generate Unique Series ID
            String seriesId = "S_" + UUID.randomUUID().toString().substring(0, 8);

            // 3. Create Series Entity & Save to Database
            Series s = new Series();
            s.setId(seriesId);
            s.setName(name.trim());
            s.setRankingModel(rankingModel);
            s.setPhaseSize(phaseSize);
            s.setCurrentPhase(1);
            s.setInitialPoints(0);
            s.setInitialElo(initialElo);
            s.setStatus("ACTIVE");

            SeriesDAO dao = new SeriesDAO();
            boolean inserted = dao.insertSeries(s);

            if (inserted) {
                System.out.println("=== SERIES SAVED TO DB ===");
                System.out.println("ID: " + seriesId);
                System.out.println("Name: " + name);
            } else {
                System.err.println("Failed to insert series into database: " + seriesId);
            }

            // 4. Redirect to My Series List
            response.sendRedirect(request.getContextPath() + "/my-series");

        } catch (Exception e) {
            e.printStackTrace();
            request.setAttribute("errorMessage", "Lỗi trong quá trình khởi tạo chuỗi giải: " + e.getMessage());
            request.getRequestDispatcher("/common/create-series.jsp").forward(request, response);
        }
    }
}
