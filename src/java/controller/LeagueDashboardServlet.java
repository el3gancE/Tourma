package controller;

import dao.SeriesDAO;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import model.Series;

@WebServlet(name = "LeagueDashboardServlet", urlPatterns = "/league/dashboard")
public class LeagueDashboardServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        Series series = new SeriesDAO().getSeriesById(request.getParameter("id"));
        if (series == null || !"LEAGUE_SYSTEM".equals(series.getRankingModel())) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "Không tìm thấy chuỗi giải League.");
            return;
        }

        request.setAttribute("leagueSeries", series);
        request.getRequestDispatcher("/WEB-INF/views/league/dashboard.jsp").forward(request, response);
    }
}
