package controller;

import dao.LeagueSeasonDAO;
import dao.LeagueSeriesTeamDAO;
import dao.SeriesDAO;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import model.Series;

@WebServlet("/league/season/create")
public class LeagueCreateSeasonServlet extends HttpServlet {
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

    private void handle(HttpServletRequest request, HttpServletResponse response, boolean submit)
            throws ServletException, IOException {
        Series series = new SeriesDAO().getSeriesById(request.getParameter("id"));
        if (series == null || !"LEAGUE_SYSTEM".equals(series.getRankingModel())) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        String seasonsUrl = request.getContextPath() + "/league/dashboard?id="
                + URLEncoder.encode(series.getId(), StandardCharsets.UTF_8) + "#league-seasons";
        request.setAttribute("leagueSeries", series);
        request.setAttribute("leagueSection", "seasons");
        LeagueSeasonDAO seasons = new LeagueSeasonDAO();
        try {
            if (!submit) {
                var history = seasons.list(series.getId());
                request.setAttribute("leagueSeasons", history);
                if (!history.isEmpty()) {
                    var latest = history.get(0);
                    request.setAttribute("leagueLatestSeason", latest);
                    if ("COMPLETED".equals(latest.get("status"))
                            || ("DRAFT".equals(latest.get("status")) && (Integer) latest.get("number") > 1)) {
                        request.getRequestDispatcher("/league/season/next").forward(request, response);
                        return;
                    }
                }
            }
            LeagueSeasonDAO.Setup setup = seasons.getFirstSetup(series.getId());
            List<Map<String, Object>> existingSeasons = setup == null ? seasons.list(series.getId()) : List.of();
            if (!existingSeasons.isEmpty()) {
                if (submit) {
                    response.sendError(HttpServletResponse.SC_CONFLICT, "Mùa đầu tiên không còn ở trạng thái thiết lập.");
                    return;
                }
                request.setAttribute("leagueReadOnlySeasons", existingSeasons);
                request.getRequestDispatcher("/WEB-INF/views/league/create-season.jsp").forward(request, response);
                return;
            }
            var teamDates = new LeagueSeriesTeamDAO().listInRegistrationOrder(series.getId());
            List<String> teams = List.copyOf(teamDates.keySet());
            if (submit) {
                String name = request.getParameter("seasonName");
                String[] legValues = request.getParameterValues("legs");
                String[] slotValues = request.getParameterValues("slots");
                if (name == null || name.isBlank() || name.strip().length() > 255 || legValues == null
                        || slotValues == null || legValues.length != slotValues.length
                        || legValues.length > teams.size() / 2) {
                    response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Tên mùa hoặc số hạng không hợp lệ.");
                    return;
                }
                int[] legs = new int[legValues.length];
                int[] slots = new int[legs.length];
                int[] counts = new int[legs.length];
                Map<String, Integer> assignments = new LinkedHashMap<>();
                for (int i = 0; i < legs.length; i++) {
                    legs[i] = Integer.parseInt(legValues[i]);
                    slots[i] = Integer.parseInt(slotValues[i]);
                    if (legs[i] < 1 || legs[i] > 10 || slots[i] < 0 || (i == legs.length - 1 && slots[i] != 0)) {
                        throw new IllegalArgumentException("Cấu hình số lượt hoặc suất chuyển hạng không hợp lệ.");
                    }
                }
                List<String> unassigned = new ArrayList<>();
                for (String team : teams) {
                    String selection = request.getParameter("division:" + team);
                    if (selection == null || selection.isEmpty()) {
                        unassigned.add(team);
                        continue;
                    }
                    int level = Integer.parseInt(selection);
                    if (level < 1 || level > legs.length) {
                        throw new IllegalArgumentException("Mỗi đội phải được phân vào một hạng.");
                    }
                    assignments.put(team, level);
                    counts[level - 1]++;
                }
                if (!unassigned.isEmpty()) {
                    int emptyLevel = 0;
                    for (int i = 0; i < counts.length; i++) {
                        if (counts[i] == 0) {
                            if (emptyLevel != 0) {
                                throw new IllegalArgumentException("Còn nhiều hạng trống. Hãy phân đội trước khi lưu.");
                            }
                            emptyLevel = i + 1;
                        }
                    }
                    if (emptyLevel == 0 || unassigned.size() < 2) {
                        throw new IllegalArgumentException("Mỗi đội phải được phân vào một hạng.");
                    }
                    for (String team : unassigned) {
                        assignments.put(team, emptyLevel);
                    }
                    counts[emptyLevel - 1] = unassigned.size();
                }
                for (int i = 0; i < counts.length; i++) {
                    long moving = (long) slots[i] + (i == 0 ? 0 : slots[i - 1]);
                    if (counts[i] < 2 || moving > counts[i]) {
                        throw new IllegalArgumentException("Mỗi hạng cần ít nhất 2 đội và các suất chuyển hạng không được chồng nhau.");
                    }
                }
                boolean cup = "on".equals(request.getParameter("cup"));
                boolean editing = "true".equals(request.getParameter("editing"));
                if (!seasons.saveFirst(series.getId(), editing, name.strip(), cup, legs, slots, assignments)) {
                    response.sendError(HttpServletResponse.SC_CONFLICT, "Danh sách đội hoặc mùa giải đã thay đổi. Hãy tải lại trang.");
                    return;
                }
                response.sendRedirect(seasonsUrl);
                return;
            }
            request.setAttribute("leagueTeams", teams);
            request.setAttribute("leagueTeamDates", teamDates);
            if (setup != null) {
                Map<String, Integer> savedLevels = new LinkedHashMap<>();
                for (String team : teams) {
                    savedLevels.put(team, setup.assignments().get(LeagueSeriesTeamDAO.teamKey(team)));
                }
                request.setAttribute("leagueSavedLevels", savedLevels);
                request.setAttribute("leagueSeasonName", setup.name());
                request.setAttribute("leagueSeasonCup", setup.cup());
                request.setAttribute("leagueSeasonLegs", setup.legs());
                request.setAttribute("leagueSeasonSlots", setup.slots());
            }
        } catch (NumberFormatException e) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Số lượt, số suất hoặc hạng đấu không hợp lệ.");
            return;
        } catch (IllegalArgumentException e) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
            return;
        } catch (Exception e) {
            throw new ServletException("Không thể tải hoặc lưu mùa League.", e);
        }
        request.getRequestDispatcher("/WEB-INF/views/league/create-season.jsp").forward(request, response);
    }
}
