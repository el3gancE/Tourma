package controller;

import dao.DBContext;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@WebServlet("/api/h2h")
public class H2hServlet extends HttpServlet {
    private static final String HISTORY_SQL = """
            SELECT t.name, m.round_number, m.match_code,
                   COALESCE(s.stage_order, 1) AS stage_order,
                   m.score1, m.score2, m.penalty1, m.penalty2,
                   CASE WHEN LTRIM(RTRIM(a.raw_name)) COLLATE Latin1_General_100_CI_AS = ?
                        THEN 1 ELSE 0 END AS first_is_left,
                   CASE WHEN m.winner_id = m.team1_id THEN 1
                        WHEN m.winner_id = m.team2_id THEN 2
                        WHEN m.score1 > m.score2 THEN 1
                        WHEN m.score2 > m.score1 THEN 2
                        WHEN m.penalty1 >= 0 AND m.penalty2 >= 0 AND m.penalty1 > m.penalty2 THEN 1
                        WHEN m.penalty1 >= 0 AND m.penalty2 >= 0 AND m.penalty2 > m.penalty1 THEN 2
                        ELSE 0 END AS winner_side
            FROM tournaments t
            JOIN matches m ON m.tournament_id = t.id
            JOIN teams a ON a.id = m.team1_id AND a.tournament_id = t.id
            JOIN teams b ON b.id = m.team2_id AND b.tournament_id = t.id
            LEFT JOIN tournament_stages s ON s.id = m.stage_id AND s.tournament_id = t.id
            WHERE %s = ? AND ISNULL(m.is_bye, 0) = 0 AND m.team1_id <> m.team2_id
              AND UPPER(m.status) IN ('FINISHED', 'COMPLETED', 'DONE')
              AND m.score1 >= 0 AND m.score2 >= 0
              AND ((LTRIM(RTRIM(a.raw_name)) COLLATE Latin1_General_100_CI_AS = ?
                    AND LTRIM(RTRIM(b.raw_name)) COLLATE Latin1_General_100_CI_AS = ?)
                OR (LTRIM(RTRIM(a.raw_name)) COLLATE Latin1_General_100_CI_AS = ?
                    AND LTRIM(RTRIM(b.raw_name)) COLLATE Latin1_General_100_CI_AS = ?))
            ORDER BY t.tournament_index_in_series DESC, t.created_at DESC,
                     stage_order DESC, m.round_number DESC, m.match_order DESC, m.id DESC
            OPTION (LOOP JOIN, MAXDOP 1)
            """;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        request.setCharacterEncoding("UTF-8");
        String id = parameter(request, "id");
        String first = parameter(request, "first");
        String second = parameter(request, "second");
        if (id.isEmpty() || id.length() > 50 || first.isEmpty() || second.isEmpty()
                || first.length() > 255 || second.length() > 255 || first.equalsIgnoreCase(second)) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        Map<String, Object> result;
        try (Connection connection = new DBContext().getConnection();
                PreparedStatement scope = connection.prepareStatement("SELECT series_id FROM tournaments WHERE id = ?")) {
            scope.setString(1, id);
            String seriesId;
            try (ResultSet row = scope.executeQuery()) {
                if (!row.next()) {
                    response.sendError(HttpServletResponse.SC_NOT_FOUND);
                    return;
                }
                seriesId = row.getString(1);
            }
            result = history(connection, id, seriesId, first, second);
        } catch (Exception error) {
            log("Cannot read head-to-head history", error);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            return;
        }
        response.setHeader("Cache-Control", "no-store");
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(SetupJson.encode(result));
    }

    private static Map<String, Object> history(Connection connection, String id, String seriesId,
            String first, String second) throws java.sql.SQLException {
        boolean series = seriesId != null && !seriesId.isBlank();
        List<Map<String, Object>> matches = new ArrayList<>();
        int firstWins = 0;
        int secondWins = 0;
        int draws = 0;
        try (PreparedStatement query = connection.prepareStatement(
                HISTORY_SQL.formatted(series ? "t.series_id" : "t.id"))) {
            query.setNString(1, first);
            query.setString(2, series ? seriesId : id);
            query.setNString(3, first);
            query.setNString(4, second);
            query.setNString(5, second);
            query.setNString(6, first);
            try (ResultSet rows = query.executeQuery()) {
                while (rows.next()) {
                    boolean left = rows.getBoolean("first_is_left");
                    int winner = rows.getInt("winner_side");
                    int winnerPosition = winner == 0 ? 0 : ((winner == 1) == left ? 1 : 2);
                    if (winnerPosition == 0) draws++;
                    else if (winnerPosition == 1) firstWins++;
                    else secondWins++;

                    Map<String, Object> match = new LinkedHashMap<>();
                    match.put("tournament", rows.getString("name"));
                    String code = rows.getString("match_code");
                    match.put("round", "Giai đoạn " + rows.getInt("stage_order")
                            + " · Vòng " + rows.getInt("round_number")
                            + (code == null || code.isBlank() ? "" : " · " + code));
                    match.put("firstScore", rows.getInt(left ? "score1" : "score2"));
                    match.put("winnerPosition", winnerPosition);
                    match.put("secondScore", rows.getInt(left ? "score2" : "score1"));
                    match.put("firstPenalty", penalty(rows, left ? "penalty1" : "penalty2"));
                    match.put("secondPenalty", penalty(rows, left ? "penalty2" : "penalty1"));
                    matches.add(match);
                }
            }
        }
        return Map.of("scope", series ? "Series" : "giải này", "firstWins", firstWins,
                "secondWins", secondWins, "draws", draws, "matches", matches);
    }

    private static Integer penalty(ResultSet row, String column) throws java.sql.SQLException {
        int value = row.getInt(column);
        return row.wasNull() || value < 0 ? null : value;
    }

    private static String parameter(HttpServletRequest request, String name) {
        String value = request.getParameter(name);
        return value == null ? "" : value.trim();
    }
}
