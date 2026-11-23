package controller;

import dao.LeagueSeasonDAO;
import dao.LeagueSeasonResultDAO;
import dao.SeriesDAO;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import model.Series;

@WebServlet(name = "LeagueDashboardServlet", urlPatterns = {"/league/dashboard", "/league/season/dashboard"})
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
        try {
            LeagueSeasonDAO seasons = new LeagueSeasonDAO();
            LeagueSeasonResultDAO results = new LeagueSeasonResultDAO();
            var seasonList = seasons.list(series.getId());
            request.setAttribute("leagueSeasons", seasonList);
            if (!seasonList.isEmpty()) request.setAttribute("leagueLatestSeason", seasonList.get(0));
            String reviewId = request.getParameter("reviewSeason");
            if (reviewId != null && !reviewId.isBlank()) {
                var review = results.review(series.getId(), reviewId);
                request.setAttribute("leagueReviewName", review.name());
                request.setAttribute("leagueReviewClosed", review.closed());
                request.setAttribute("leagueReviewPending", review.pending());
                request.setAttribute("leagueResults", review.results());
                request.setAttribute("leagueRevision", review.revision());
                request.setAttribute("leagueReviewId", reviewId);
                request.setAttribute("leagueCurrentSeasonId", reviewId);
                request.getRequestDispatcher("/WEB-INF/views/league/close-season.jsp").forward(request, response);
                return;
            }
            if ("/league/season/dashboard".equals(request.getServletPath())) {
                String selectedId = request.getParameter("seasonId");
                var selected = seasonList.stream().filter(season -> season.get("id").equals(selectedId))
                        .findFirst().orElseThrow(() -> new IllegalArgumentException("Không tìm thấy mùa thuộc chuỗi giải này."));
                request.setAttribute("leagueSeasonDashboard", selected);
                request.setAttribute("leagueCurrentSeasonId", selectedId);
                request.setAttribute("leagueCurrentDivisions", seasons.listDivisions(series.getId()).get(selectedId));
                if ("ACTIVE".equals(selected.get("status"))) {
                    request.setAttribute("leagueCurrentPending", results.pending(selectedId));
                }
            }
        } catch (IllegalArgumentException e) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, e.getMessage());
            return;
        } catch (IllegalStateException e) {
            response.sendError(HttpServletResponse.SC_CONFLICT, e.getMessage());
            return;
        } catch (Exception e) {
            throw new ServletException("Không thể tải danh sách mùa League.", e);
        }
        request.getRequestDispatcher("/WEB-INF/views/league/dashboard.jsp").forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        request.setCharacterEncoding("UTF-8");
        Series series = new SeriesDAO().getSeriesById(request.getParameter("id"));
        String seasonId = request.getParameter("seasonId");
        if (series == null || !"LEAGUE_SYSTEM".equals(series.getRankingModel())) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        String action = request.getParameter("action");
        if ((!"openSeason".equals(action) && !"closeSeason".equals(action)) || seasonId == null || seasonId.isBlank()) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }
        try {
            if ("closeSeason".equals(action)) {
                new LeagueSeasonResultDAO().close(series.getId(), seasonId, request.getParameter("revision"));
            } else {
                new LeagueSeasonDAO().openSeason(series.getId(), seasonId);
            }
            response.sendRedirect(request.getContextPath() + "/league/season/dashboard?id="
                    + URLEncoder.encode(series.getId(), StandardCharsets.UTF_8) + "&seasonId="
                    + URLEncoder.encode(seasonId, StandardCharsets.UTF_8));
        } catch (IllegalArgumentException | IllegalStateException e) {
            response.sendError(HttpServletResponse.SC_CONFLICT, e.getMessage());
        } catch (Exception e) {
            throw new ServletException("Không thể cập nhật mùa League.", e);
        }
    }
}
