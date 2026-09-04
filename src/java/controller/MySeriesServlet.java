package controller;

import dao.SeriesDAO;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import model.Series;

/**
 * Controller Servlet for My Series Screen (Chuỗi Giải Của Tôi)
 * Queries SQL Server database via SeriesDAO and forwards to /common/my-series.jsp
 */
@WebServlet(name = "MySeriesServlet", urlPatterns = {"/my-series", "/my-series.jsp"})
public class MySeriesServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        
        SeriesDAO dao = new SeriesDAO();
        List<Series> seriesList = dao.getAllSeries();
        
        request.setAttribute("seriesList", seriesList);
        request.getRequestDispatcher("/common/my-series.jsp").forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String action = request.getParameter("action");
        String seriesId = request.getParameter("seriesId");

        if ("delete".equalsIgnoreCase(action) && seriesId != null && !seriesId.trim().isEmpty()) {
            SeriesDAO dao = new SeriesDAO();
            dao.deleteSeries(seriesId.trim());
            response.sendRedirect(request.getContextPath() + "/my-series");
            return;
        }

        doGet(request, response);
    }
}
