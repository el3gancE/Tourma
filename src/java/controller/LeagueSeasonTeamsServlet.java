package controller;

import dao.LeagueSeriesTeamDAO;
import dao.SeriesDAO;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import model.Series;

@WebServlet("/league/season/teams")
public class LeagueSeasonTeamsServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        handle(request, response, false);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        request.setCharacterEncoding("UTF-8");
        handle(request, response, true);
    }

    @SuppressWarnings("unchecked")
    private void handle(HttpServletRequest request, HttpServletResponse response, boolean update)
            throws ServletException, IOException {
        SeriesDAO dao = new SeriesDAO();
        Series series = dao.getSeriesById(request.getParameter("id"));
        if (series == null || !"LEAGUE_SYSTEM".equals(series.getRankingModel())) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        String legacyTeamsKey = "leagueSeasonTeams:" + series.getId();
        String legacyDatesKey = legacyTeamsKey + ":dates";
        LeagueSeriesTeamDAO roster = new LeagueSeriesTeamDAO();
        try {
            HttpSession session = request.getSession(false);
            if (session != null && session.getAttribute(legacyTeamsKey) instanceof List<?>) {
                List<String> oldTeams = (List<String>) session.getAttribute(legacyTeamsKey);
                Map<String, Date> oldDates = (Map<String, Date>) session.getAttribute(legacyDatesKey);
                Map<String, Date> recovered = new LinkedHashMap<>();
                for (String team : oldTeams) {
                    recovered.put(team, oldDates == null ? null : oldDates.get(team));
                }
                roster.add(series.getId(), recovered);
                session.removeAttribute(legacyTeamsKey);
                session.removeAttribute(legacyDatesKey);
            }

            if (update) {
                String action = request.getParameter("action");
                if ("add".equals(action)) {
                    String text = request.getParameter("bulkTeamNames");
                    Map<String, Date> additions = new LinkedHashMap<>();
                    Date registeredAt = new Date();
                    if (text != null) {
                        for (String name : text.lines().map(String::strip).filter(line -> !line.isEmpty()).toList()) {
                            if (name.length() > 255 || LeagueSeriesTeamDAO.teamKey(name).length() > 255) {
                                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Tên đội tối đa 255 ký tự.");
                                return;
                            }
                            additions.put(name, registeredAt);
                        }
                    }
                    roster.add(series.getId(), additions);
                } else if ("remove".equals(action)) {
                    String[] submittedTeams = request.getParameterValues("team");
                    if (submittedTeams == null || submittedTeams.length != 1) {
                        response.sendError(HttpServletResponse.SC_BAD_REQUEST);
                        return;
                    }
                    roster.remove(series.getId(), submittedTeams[0]);
                } else {
                    response.sendError(HttpServletResponse.SC_BAD_REQUEST);
                    return;
                }
                response.sendRedirect(request.getContextPath() + "/league/season/teams?id="
                        + URLEncoder.encode(series.getId(), StandardCharsets.UTF_8));
                return;
            }

            Map<String, Date> teams = roster.list(series.getId());
            request.setAttribute("leagueSeries", series);
            request.setAttribute("leagueSection", "teams");
            var seasons = new dao.LeagueSeasonDAO().list(series.getId());
            request.setAttribute("leagueSeasons", seasons);
            if (!seasons.isEmpty()) request.setAttribute("leagueLatestSeason", seasons.get(0));
            request.setAttribute("leagueTeams", List.copyOf(teams.keySet()));
            request.setAttribute("leagueTeamDates", teams);
        } catch (Exception e) {
            throw new ServletException("Không thể lưu hoặc tải danh sách đội League.", e);
        }
        request.getRequestDispatcher("/WEB-INF/views/league/season-teams.jsp").forward(request, response);
    }

}
