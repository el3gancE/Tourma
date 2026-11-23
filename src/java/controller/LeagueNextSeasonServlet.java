package controller;

import dao.LeagueSeasonDAO;
import dao.LeagueSeasonResultDAO;
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
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import model.Series;
import service.LeagueSeasonTransition;

@WebServlet("/league/season/next")
public class LeagueNextSeasonServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        handle(request, response, false);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        request.setCharacterEncoding("UTF-8");
        handle(request, response, true);
    }

    private void handle(HttpServletRequest request, HttpServletResponse response, boolean submitted)
            throws ServletException, IOException {
        Series series = new SeriesDAO().getSeriesById(request.getParameter("id"));
        if (series == null || !"LEAGUE_SYSTEM".equals(series.getRankingModel())) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        String seriesId = series.getId();
        try {
            LeagueSeasonDAO seasons = new LeagueSeasonDAO();
            var list = seasons.list(seriesId);
            if (list.isEmpty()) {
                response.sendError(HttpServletResponse.SC_CONFLICT, "Chưa có mùa đã chốt.");
                return;
            }
            var latest = list.get(0);
            String latestId = (String) latest.get("id");
            int number = (Integer) latest.get("number");
            boolean editing = number > 1 && "DRAFT".equals(latest.get("status"));
            if ((!editing && !"COMPLETED".equals(latest.get("status"))) || (editing && list.size() < 2)) {
                response.sendError(HttpServletResponse.SC_CONFLICT, "Hãy chốt mùa đang diễn ra trước.");
                return;
            }
            if (submitted && !latestId.equals(request.getParameter("latestId"))) {
                response.sendError(HttpServletResponse.SC_CONFLICT, "Mùa hiện tại đã thay đổi. Hãy mở lại trang thiết lập.");
                return;
            }
            var previous = editing ? list.get(1) : latest;
            var old = new LeagueSeasonResultDAO().review(seriesId, (String) previous.get("id"));
            if (!old.closed() || old.results().isEmpty()) {
                response.sendError(HttpServletResponse.SC_CONFLICT, "Mùa trước chưa có kết quả chuyển hạng đã lưu.");
                return;
            }
            int nextNumber = editing ? number : number + 1;
            var config = seasons.getSetup(seriesId, editing ? number : (Integer) previous.get("number"), editing);
            if (config == null || config.legs().isEmpty()) {
                response.sendError(HttpServletResponse.SC_CONFLICT, "Thiếu cấu hình hạng đấu.");
                return;
            }
            int divisionCount = config.legs().size();
            Map<String, String> registered = new LinkedHashMap<>();
            for (String name : new LeagueSeriesTeamDAO().list(seriesId).keySet()) {
                registered.put(LeagueSeriesTeamDAO.teamKey(name), name);
            }
            Set<String> withdrawals = new HashSet<>();
            if (submitted) {
                String[] excluded = request.getParameterValues("withdraw");
                if (excluded != null) withdrawals.addAll(Arrays.asList(excluded));
            } else if (editing) {
                withdrawals.addAll(seasons.withdrawals(latestId));
            }
            withdrawals.retainAll(registered.keySet());
            Map<String, String> available = new LinkedHashMap<>(registered);
            available.keySet().removeAll(withdrawals);
            var placements = LeagueSeasonTransition.plan(old.results(), available, divisionCount);
            String revision = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(placements.toString().getBytes(StandardCharsets.UTF_8)));

            String name = editing ? config.name() : "Mùa " + nextNumber;
            boolean cup = config.cup();
            List<Integer> legs = config.legs();
            List<Integer> slots = config.slots();
            String message = null;
            if (submitted) {
                name = request.getParameter("seasonName");
                cup = "on".equals(request.getParameter("cup"));
                legs = parse(request.getParameterValues("legs"), divisionCount, 1, 10);
                slots = parse(request.getParameterValues("slots"), divisionCount, 0, Integer.MAX_VALUE);
                if (name == null || name.isBlank() || name.strip().length() > 255 || slots.get(divisionCount - 1) != 0) {
                    throw new IllegalArgumentException("Tên mùa hoặc số suất chuyển hạng không hợp lệ.");
                }
                if ("save".equals(request.getParameter("intent"))) {
                    if (!revision.equals(request.getParameter("revision"))) {
                        message = "Danh sách đã đổi. Hãy kiểm tra lại trước khi lưu.";
                    } else {
                        int[] counts = new int[divisionCount];
                        Map<String, Integer> assignments = new LinkedHashMap<>();
                        for (var placement : placements) {
                            counts[placement.level() - 1]++;
                            assignments.put(placement.name(), placement.level());
                        }
                        for (int i = 0; i < counts.length; i++) {
                            long moving = (long) slots.get(i) + (i == 0 ? 0 : slots.get(i - 1));
                            if (counts[i] < 2 || moving > counts[i]) {
                                message = "Hạng " + (i + 1) + " cần ít nhất 2 đội và đủ chỗ cho các suất chuyển hạng.";
                                break;
                            }
                        }
                        if (message == null) {
                            if (!seasons.saveSetup(seriesId, latestId, nextNumber, editing, name.strip(), cup,
                                    legs.stream().mapToInt(Integer::intValue).toArray(),
                                    slots.stream().mapToInt(Integer::intValue).toArray(), assignments, withdrawals)) {
                                response.sendError(HttpServletResponse.SC_CONFLICT, "Mùa hoặc danh sách đội đã thay đổi.");
                                return;
                            }
                            response.sendRedirect(request.getContextPath() + "/league/dashboard?id="
                                    + URLEncoder.encode(seriesId, StandardCharsets.UTF_8) + "#league-seasons");
                            return;
                        }
                    }
                }
            }
            request.setAttribute("leagueSeries", series);
            request.setAttribute("leagueSection", "seasons");
            request.setAttribute("leagueSeasons", list);
            request.setAttribute("leagueLatestSeason", latest);
            request.setAttribute("nextNumber", nextNumber);
            request.setAttribute("previousName", previous.get("name"));
            request.setAttribute("latestId", latestId);
            request.setAttribute("revision", revision);
            request.setAttribute("seasonName", name);
            request.setAttribute("seasonCup", cup);
            request.setAttribute("seasonLegs", legs);
            request.setAttribute("seasonSlots", slots);
            request.setAttribute("registeredTeams", registered);
            request.setAttribute("withdrawals", withdrawals.stream().collect(java.util.stream.Collectors.toMap(key -> key, key -> true)));
            request.setAttribute("placements", placements);
            request.setAttribute("setupMessage", message);
        } catch (IllegalArgumentException e) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
            return;
        } catch (Exception e) {
            throw new ServletException("Không thể thiết lập mùa kế tiếp.", e);
        }
        request.getRequestDispatcher("/WEB-INF/views/league/next-season.jsp").forward(request, response);
    }

    private List<Integer> parse(String[] values, int count, int min, int max) {
        if (values == null || values.length != count) throw new IllegalArgumentException("Thiếu cấu hình hạng đấu.");
        return Arrays.stream(values).map(value -> {
            int number = Integer.parseInt(value);
            if (number < min || number > max) throw new IllegalArgumentException("Số lượt hoặc số suất không hợp lệ.");
            return number;
        }).toList();
    }
}
