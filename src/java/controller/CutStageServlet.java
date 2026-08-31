package controller;

import dao.ParticipantDAO;
import dao.SingleEliminationDAO;
import dao.TournamentDAO;
import model.Match;
import model.Team;
import model.Tournament;
import service.CountAdvanceTeamService;
import service.SECutService;
import service.DECutService;
import service.GSCutService;
import service.RRCutService;
import service.SwissCutService;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.Map;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * CutStageServlet
 * Centralized Servlet Controller for executing Cut Stage transitions.
 * Supports Stage 1 = Single Elimination (SE to SE, SE to DE, SE to RR).
 */
@WebServlet(name = "CutStageServlet", urlPatterns = {"/api/cut-stage", "/cut-stage"})
public class CutStageServlet extends HttpServlet {

    private final SingleEliminationDAO singleEliminationDAO = new SingleEliminationDAO();
    private final ParticipantDAO participantDAO = new ParticipantDAO();
    private final TournamentDAO tournamentDAO = new TournamentDAO();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        
        response.setContentType("application/json;charset=UTF-8");
        PrintWriter out = response.getWriter();
        out.print("{\"status\":\"info\",\"message\":\"CutStageServlet API is active. Send POST request to execute cut stage.\"}");
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json;charset=UTF-8");
        PrintWriter out = response.getWriter();

        try {
            String tournamentId = request.getParameter("tournamentId");
            String stage1Format = request.getParameter("stage1Format");
            String cutTargetStr = request.getParameter("cutTarget");
            String targetFormat = request.getParameter("targetFormat");

            if (tournamentId == null || tournamentId.trim().isEmpty()) {
                out.print("{\"status\":\"error\",\"message\":\"Thiếu ID giải đấu (tournamentId)!\"}");
                return;
            }

            int cutTarget = 4;
            if (cutTargetStr != null && !cutTargetStr.trim().isEmpty()) {
                try {
                    cutTarget = Integer.parseInt(cutTargetStr.trim());
                } catch (NumberFormatException e) {
                    cutTarget = 4;
                }
            }

            int intTourneyId = 1;
            try {
                intTourneyId = Integer.parseInt(tournamentId);
            } catch (NumberFormatException e) {
                intTourneyId = 1;
            }

            // Default stage1Format to SINGLE_ELIMINATION if not specified
            if (stage1Format == null || stage1Format.trim().isEmpty()) {
                stage1Format = "SINGLE_ELIMINATION";
            }
            if (targetFormat == null || targetFormat.trim().isEmpty()) {
                targetFormat = "SINGLE_ELIMINATION";
            }

            // HANDLE STAGE 1 = SINGLE ELIMINATION
            if ("SINGLE_ELIMINATION".equalsIgnoreCase(stage1Format) || "SE".equalsIgnoreCase(stage1Format)) {

                // 1. Fetch participant teams from DB
                List<Team> dbTeams = participantDAO.getTeamsByTournamentId(tournamentId);
                int totalTeams = (dbTeams != null && !dbTeams.isEmpty()) ? dbTeams.size() : 16;

                // Validate cutTarget
                if (!CountAdvanceTeamService.isValidKnockoutCutTarget(totalTeams, cutTarget)) {
                    out.print("{\"status\":\"error\",\"message\":\"Số đội đi tiếp (" + cutTarget + ") không hợp lệ cho giải " + totalTeams + " đội!\"}");
                    return;
                }

                // 2. Calculate stopping round R_stop
                int stoppingRound = CountAdvanceTeamService.calculateStoppingRoundSE(totalTeams, cutTarget);

                // 3. Fetch Stage 1 SE matches
                List<Match> matchesList = singleEliminationDAO.getMatchesByTournamentId(intTourneyId);

                // 4. Check if stopping round is finished
                boolean roundFinished = SECutService.isStoppingRoundFinished(matchesList, stoppingRound);
                if (!roundFinished) {
                    out.print("{\"status\":\"error\",\"message\":\"Vòng dừng (Vòng " + stoppingRound + ") chưa thi đấu hoàn tất 100%! Chưa thể cắt Stage.\",\"stoppingRound\":" + stoppingRound + "}");
                    return;
                }

                // 5. Extract qualified winning teams
                List<Team> qualifiedTeams = SECutService.extractQualifiedTeams(matchesList, stoppingRound);

                // 6. Randomly pair/shuffle teams for Stage 2 (100% Random rule for SE Stage 1)
                List<Team> stage2ShuffledTeams = SECutService.pairForStage2Random(qualifiedTeams);

                // Build JSON Response
                StringBuilder json = new StringBuilder();
                json.append("{");
                json.append("\"status\":\"success\",");
                json.append("\"message\":\"Thực hiện Cut Stage thành công!\",");
                json.append("\"tournamentId\":\"").append(tournamentId).append("\",");
                json.append("\"stage1Format\":\"SINGLE_ELIMINATION\",");
                json.append("\"stoppingRound\":").append(stoppingRound).append(",");
                json.append("\"cutTarget\":").append(cutTarget).append(",");
                json.append("\"targetFormat\":\"").append(targetFormat).append("\",");
                json.append("\"qualifiedCount\":").append(stage2ShuffledTeams.size()).append(",");
                json.append("\"qualifiedTeams\":[");

                for (int i = 0; i < stage2ShuffledTeams.size(); i++) {
                    Team t = stage2ShuffledTeams.get(i);
                    json.append("{");
                    json.append("\"id\":\"").append(t.getId() != null ? t.getId() : "").append("\",");
                    json.append("\"name\":\"").append(t.getName() != null ? t.getName() : "").append("\",");
                    json.append("\"seed\":").append(i + 1);
                    json.append("}");
                    if (i < stage2ShuffledTeams.size() - 1) json.append(",");
                }

                json.append("]");
                json.append("}");

                out.print(json.toString());
            } else if ("DOUBLE_ELIMINATION".equalsIgnoreCase(stage1Format) || "DE".equalsIgnoreCase(stage1Format)) {
                List<Team> dbTeams = participantDAO.getTeamsByTournamentId(tournamentId);
                int totalTeams = (dbTeams != null && !dbTeams.isEmpty()) ? dbTeams.size() : 16;
                
                int ubStopRound = DECutService.calculateUbStoppingRound(totalTeams, cutTarget);
                int lbStopRound = DECutService.calculateLbStoppingRound(ubStopRound);
                
                List<Match> matchesList = singleEliminationDAO.getMatchesByTournamentId(intTourneyId);
                boolean roundFinished = DECutService.isCutStageFinished(matchesList, ubStopRound, lbStopRound);
                if (!roundFinished) {
                    out.print("{\"status\":\"error\",\"message\":\"Các trận vòng dừng (UB " + ubStopRound + ", LB " + lbStopRound + ") chưa hoàn tất!\"}");
                    return;
                }
                
                List<Team> qualifiedTeams = DECutService.extractQualifiedTeams(matchesList, ubStopRound, lbStopRound);
                
                StringBuilder json = new StringBuilder();
                json.append("{");
                json.append("\"status\":\"success\",");
                json.append("\"message\":\"Thực hiện Cut Stage Double Elimination thành công!\",");
                json.append("\"tournamentId\":\"").append(tournamentId).append("\",");
                json.append("\"stage1Format\":\"DOUBLE_ELIMINATION\",");
                json.append("\"ubStoppingRound\":").append(ubStopRound).append(",");
                json.append("\"lbStoppingRound\":").append(lbStopRound).append(",");
                json.append("\"cutTarget\":").append(cutTarget).append(",");
                json.append("\"targetFormat\":\"").append(targetFormat).append("\",");
                json.append("\"qualifiedCount\":").append(qualifiedTeams.size()).append(",");
                json.append("\"qualifiedTeams\":[");
                for (int i = 0; i < qualifiedTeams.size(); i++) {
                    Team t = qualifiedTeams.get(i);
                    json.append("{");
                    json.append("\"id\":\"").append(t.getId() != null ? t.getId() : "").append("\",");
                    json.append("\"name\":\"").append(t.getName() != null ? t.getName() : "").append("\",");
                    json.append("\"seed\":").append(i + 1);
                    json.append("}");
                    if (i < qualifiedTeams.size() - 1) json.append(",");
                }
                json.append("]}");
                out.print(json.toString());
            } else {
                out.print("{\"status\":\"error\",\"message\":\"Thể thức Stage 1 '" + stage1Format + "' chưa được hỗ trợ!\"}");
            }

        } catch (Exception e) {
            e.printStackTrace();
            out.print("{\"status\":\"error\",\"message\":\"Lỗi hệ thống Cut Stage: " + e.getMessage() + "\"}");
        }
    }
}
