package controller;

import dao.SeriesConfigurationTemplateDAO;
import dao.SeriesDAO;
import dao.TournamentDAO;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Set;
import model.Series;
import model.Tournament;

@WebServlet("/rolling/config-templates")
public class SeriesConfigurationTemplateServlet extends HttpServlet {
    private static final Set<String> FORMATS = Set.of("ROUND_ROBIN", "SINGLE_ELIMINATION", "DOUBLE_ELIMINATION",
            "GROUP_STAGE", "GSL", "SWISS_LITE");
    private static final Set<String> TIERS = Set.of("S", "A", "B", "C", "D");

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        Tournament tournament = getRollingTournament(request.getParameter("id"));
        if (tournament == null) {
            respond(response, 404, "Không tìm thấy giải thuộc Rolling Series.");
            return;
        }
        try {
            response.setContentType("application/json;charset=UTF-8");
            response.setHeader("Cache-Control", "no-store");
            response.getWriter().write(new SeriesConfigurationTemplateDAO().listJson(tournament.getSeriesId()));
        } catch (Exception e) {
            throw new ServletException("Cannot load configuration templates", e);
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        request.setCharacterEncoding("UTF-8");
        Tournament tournament = getRollingTournament(request.getParameter("id"));
        if (tournament == null) {
            respond(response, 404, "Không tìm thấy giải thuộc Rolling Series.");
            return;
        }
        var templates = new SeriesConfigurationTemplateDAO();
        String seriesId = tournament.getSeriesId();
        String action = request.getParameter("action");
        try {
            if ("save".equals(action)) {
                save(request, tournament, templates);
            } else if ("apply".equals(action)) {
                templates.apply(seriesId, tournament.getId(), request.getParameter("templateId"));
            } else if ("clear".equals(action)) {
                templates.clearApplied(seriesId, tournament.getId());
            } else {
                respond(response, 400, "Thao tác không hợp lệ.");
                return;
            }
            response.setStatus(HttpServletResponse.SC_NO_CONTENT);
        } catch (IllegalArgumentException e) {
            respond(response, 400, e.getMessage());
        } catch (IllegalStateException e) {
            respond(response, 409, e.getMessage());
        } catch (SQLException e) {
            if (e.getErrorCode() == 2601 || e.getErrorCode() == 2627) {
                respond(response, 409, "Series đã có mẫu cùng tên.");
            } else {
                getServletContext().log("Cannot store configuration template", e);
                respond(response, 409, "Không thể lưu hoặc áp dụng mẫu cấu hình cho giải này.");
            }
        } catch (Exception e) {
            throw new ServletException("Cannot manage configuration template", e);
        }
    }

    private void save(HttpServletRequest request, Tournament tournament, SeriesConfigurationTemplateDAO templates) throws Exception {
        String name = request.getParameter("name");
        String type = request.getParameter("type");
        boolean multi = "MULTI_STAGE".equals(type);
        String first = request.getParameter("stage1");
        String second = multi ? request.getParameter("stage2") : null;
        String config = request.getParameter("config");
        String tier = request.getParameter("tier");
        if (name == null || name.isBlank() || name.strip().length() > 120 || (!multi && !"SINGLE_STAGE".equals(type))
                || first == null || !FORMATS.contains(first) || (multi && (second == null || !FORMATS.contains(second)))
                || config == null || config.isBlank() || config.length() > 65536 || tier == null || !TIERS.contains(tier)) {
            throw new IllegalArgumentException("Tên mẫu hoặc cấu hình thể thức không hợp lệ.");
        }
        int teamCount = parseNumber(request.getParameter("teamCount"), 2, Integer.MAX_VALUE);
        int seats = multi ? parseNumber(request.getParameter("seats"), 0, teamCount - 1) : 0;
        String top = request.getParameter("topSeeds");
        Integer topSeeds = top == null || top.isBlank() ? null : parseNumber(top, 0, 999);
        templates.save(tournament.getSeriesId(), tournament.getId(), name.strip(), type, first, second,
                teamCount, seats, config, tier, topSeeds, "true".equals(request.getParameter("classicDraw")));
    }

    private int parseNumber(String value, int minimum, int maximum) {
        try {
            int result = Integer.parseInt(value);
            if (result >= minimum && result <= maximum) return result;
        } catch (NumberFormatException ignored) {}
        throw new IllegalArgumentException("Số đội, số suất đi tiếp hoặc số seed không hợp lệ.");
    }

    private Tournament getRollingTournament(String id) {
        Tournament tournament = new TournamentDAO().getTournamentById(id, false);
        if (tournament == null || tournament.getSeriesId() == null) return null;
        Series series = new SeriesDAO().getSeriesById(tournament.getSeriesId());
        return series != null && "ROLLING_WINDOW".equals(series.getRankingModel()) ? tournament : null;
    }

    private void respond(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("text/plain;charset=UTF-8");
        response.getWriter().write(message);
    }
}
