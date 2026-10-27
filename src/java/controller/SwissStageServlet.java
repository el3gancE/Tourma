package controller;

import dao.SwissSystemDAO;
import dao.TournamentDAO;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import model.Tournament;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

/**
 * Controller for Swiss Stage Tournament Page & Server-Driven Standings.
 */
@WebServlet(name = "SwissStageServlet", urlPatterns = {"/swiss-stage", "/common/swiss-stage"})
public class SwissStageServlet extends HttpServlet {

    private final SwissSystemDAO swissSystemDAO = new SwissSystemDAO();
    private final TournamentDAO tournamentDAO = new TournamentDAO();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        String idParam = request.getParameter("id");
        String tournamentId = (idParam != null && !idParam.trim().isEmpty()) ? idParam.trim() : "demo";

        String stageParam = request.getParameter("stage");
        int currentStage = 1;
        if (stageParam != null && "2".equals(stageParam.trim())) {
            currentStage = 2;
        }

        Tournament tournament = tournamentDAO.getTournamentById(tournamentId);
        if (tournament == null) {
            tournament = new Tournament();
            tournament.setId(tournamentId);
            tournament.setName("Giải Đấu Swiss Stage");
            tournament.setFormat("SWISS_LITE");
        }

        String dbMatchesJson = swissSystemDAO.getMatchesJsonForFrontend(tournamentId, currentStage);
        List<SwissSystemDAO.SwissStandingRow> standings = swissSystemDAO.getSwissStandings(tournamentId, currentStage);

        request.setAttribute("tournament", tournament);
        request.setAttribute("tournamentId", tournamentId);
        request.setAttribute("currentStage", currentStage);
        request.setAttribute("dbMatchesJson", dbMatchesJson);
        request.setAttribute("standings", standings);
        request.setAttribute("dbStage2Teams", tournament.getStage2Teams());
        request.setAttribute("dbMultiStageConfig", tournament.getMultiStageConfig());

        request.getRequestDispatcher("/common/swiss-stage.jsp").forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        request.setCharacterEncoding("UTF-8");
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");
        PrintWriter out = response.getWriter();

        try {
            String action = request.getParameter("action");
            String tournamentId = request.getParameter("tournamentId");
            String stageParam = request.getParameter("stage");
            int stage = (stageParam != null && "2".equals(stageParam.trim())) ? 2 : 1;

            if ("reset".equalsIgnoreCase(action) || "resetBracket".equalsIgnoreCase(action)) {
                if (tournamentId != null && !tournamentId.trim().isEmpty()) {
                    boolean ok = swissSystemDAO.resetBracketMatches(tournamentId.trim(), stage);
                    out.print("{\"status\":\"" + (ok ? "success" : "error") + "\",\"message\":\"" + (ok ? "Đã đặt lại toàn bộ trận đấu Swiss!" : "Lỗi khi đặt lại trận đấu!") + "\"}");
                } else {
                    out.print("{\"status\":\"error\",\"message\":\"Thiếu tournamentId!\"}");
                }
                return;
            }

            if ("saveStage2Teams".equalsIgnoreCase(action)) {
                String stage2TeamsJson = request.getParameter("stage2Teams");
                if (tournamentId != null && stage2TeamsJson != null) {
                    boolean ok = tournamentDAO.saveStage2Teams(tournamentId, stage2TeamsJson);
                    out.print("{\"status\":\"" + (ok ? "success" : "error") + "\",\"message\":\"" + (ok ? "Đã lưu danh sách Vòng 2 vào CSDL!" : "Lỗi lưu Vòng 2!") + "\"}");
                } else {
                    out.print("{\"status\":\"error\",\"message\":\"Thiếu dữ liệu!\"}");
                }
                return;
            }

            if ("saveMultiStageConfig".equalsIgnoreCase(action)) {
                String multiConfigJson = request.getParameter("multiConfig");
                if (tournamentId != null && multiConfigJson != null) {
                    boolean ok = tournamentDAO.saveMultiStageConfig(tournamentId, multiConfigJson);
                    out.print("{\"status\":\"" + (ok ? "success" : "error") + "\",\"message\":\"" + (ok ? "Đã lưu cấu hình Multi-Stage!" : "Lỗi lưu cấu hình!") + "\"}");
                } else {
                    out.print("{\"status\":\"error\",\"message\":\"Thiếu dữ liệu!\"}");
                }
                return;
            }

            if ("saveStage1Status".equalsIgnoreCase(action)) {
                String stage1Status = request.getParameter("stage1Status");
                if (tournamentId != null && stage1Status != null) {
                    boolean ok = tournamentDAO.updateTournamentStage1Status(tournamentId, stage1Status);
                    out.print("{\"status\":\"" + (ok ? "success" : "error") + "\",\"message\":\"" + (ok ? "Đã cập nhật trạng thái Stage 1!" : "Lỗi cập nhật Stage 1!") + "\"}");
                } else {
                    out.print("{\"status\":\"error\",\"message\":\"Thiếu dữ liệu!\"}");
                }
                return;
            }

            if ("randomRound".equalsIgnoreCase(action)) {
                int roundNumber = 1;
                try {
                    String rParam = request.getParameter("round");
                    if (rParam == null) rParam = request.getParameter("roundNumber");
                    if (rParam != null) roundNumber = Integer.parseInt(rParam.trim());
                } catch (Exception ignore) {}

                int maxScore = 3;
                try {
                    String sParam = request.getParameter("targetScore");
                    if (sParam == null) sParam = request.getParameter("maxScore");
                    if (sParam != null) maxScore = Integer.parseInt(sParam.trim());
                } catch (Exception ignore) {}

                boolean ok = swissSystemDAO.randomRoundInDB(tournamentId, stage, roundNumber, maxScore);
                String matchesJson = swissSystemDAO.getMatchesJsonForFrontend(tournamentId, stage);
                out.print("{\"status\":\"" + (ok ? "success" : "error") + "\",\"message\":\"" + (ok ? "Đã random Vòng " + roundNumber + " thành công!" : "Lỗi khi random Vòng " + roundNumber) + "\",\"matchesData\":" + matchesJson + "}");
                return;
            }

            if ("randomAll".equalsIgnoreCase(action)) {
                int maxScore = 3;
                try {
                    String sParam = request.getParameter("targetScore");
                    if (sParam == null) sParam = request.getParameter("maxScore");
                    if (sParam != null) maxScore = Integer.parseInt(sParam.trim());
                } catch (Exception ignore) {}

                boolean ok = swissSystemDAO.randomAllInDB(tournamentId, stage, maxScore);
                String matchesJson = swissSystemDAO.getMatchesJsonForFrontend(tournamentId, stage);
                out.print("{\"status\":\"" + (ok ? "success" : "error") + "\",\"message\":\"" + (ok ? "Đã random toàn bộ 5 vòng Swiss thành công!" : "Lỗi khi random toàn bộ Swiss") + "\",\"matchesData\":" + matchesJson + "}");
                return;
            }

            if ("updateScore".equalsIgnoreCase(action)) {
                String matchId = request.getParameter("matchId");
                if (matchId == null || matchId.trim().isEmpty()) matchId = request.getParameter("matchKey");

                int score1 = 0;
                try {
                    String s1 = request.getParameter("score1");
                    if (s1 == null) s1 = request.getParameter("team1Score");
                    if (s1 != null) score1 = Integer.parseInt(s1.trim());
                } catch (Exception ignore) {}

                int score2 = 0;
                try {
                    String s2 = request.getParameter("score2");
                    if (s2 == null) s2 = request.getParameter("team2Score");
                    if (s2 != null) score2 = Integer.parseInt(s2.trim());
                } catch (Exception ignore) {}

                String winnerId = request.getParameter("winner");
                if (winnerId == null) winnerId = request.getParameter("winnerId");

                boolean ok = swissSystemDAO.saveMatchScore(tournamentId, stage, matchId, score1, score2, winnerId);
                String matchesJson = swissSystemDAO.getMatchesJsonForFrontend(tournamentId, stage);
                out.print("{\"status\":\"" + (ok ? "success" : "error") + "\",\"message\":\"" + (ok ? "Cập nhật tỉ số thành công!" : "Lỗi cập nhật tỉ số!") + "\",\"matchesData\":" + matchesJson + "}");
                return;
            }

            if ("batchSync".equalsIgnoreCase(action)) {
                String matchesJson = request.getParameter("matchesJson");
                boolean ok = swissSystemDAO.batchSyncMatches(tournamentId, stage, matchesJson);
                String updatedJson = swissSystemDAO.getMatchesJsonForFrontend(tournamentId, stage);
                out.print("{\"status\":\"" + (ok ? "success" : "error") + "\",\"message\":\"" + (ok ? "Đã đồng bộ kết quả vào CSDL!" : "Lỗi khi đồng bộ CSDL!") + "\",\"matchesData\":" + updatedJson + "}");
                return;
            }

            if ("getMatches".equalsIgnoreCase(action)) {
                String matchesJson = swissSystemDAO.getMatchesJsonForFrontend(tournamentId, stage);
                out.print("{\"status\":\"success\",\"matchesData\":" + matchesJson + "}");
                return;
            }

            out.print("{\"status\":\"error\",\"message\":\"Hành động không hợp lệ: " + action + "\"}");

        } catch (Exception e) {
            e.printStackTrace();
            out.print("{\"status\":\"error\",\"message\":\"Lỗi server: " + e.getMessage() + "\"}");
        }
    }
}
