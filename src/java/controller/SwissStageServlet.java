package controller;

import dao.ParticipantDAO;
import dao.SwissSystemDAO;
import dao.TournamentDAO;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.Map;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import model.Match;
import model.Team;
import model.Tournament;

/**
 * Controller for Swiss System Tournament Stage Page & AJAX Score Updates.
 * Loads DB Teams and Matches via ParticipantDAO & SwissSystemDAO.
 */
@WebServlet(name = "SwissStageServlet", urlPatterns = {"/common/swiss-stage"})
public class SwissStageServlet extends HttpServlet {

    private final SwissSystemDAO swissDAO = new SwissSystemDAO();
    private final TournamentDAO tournamentDAO = new TournamentDAO();
    private final ParticipantDAO participantDAO = new ParticipantDAO();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        
        String idParam = request.getParameter("id");
        String tournamentId = (idParam != null && !idParam.trim().isEmpty()) ? idParam : "demo";

        int intTourneyId = 1;
        try {
            intTourneyId = Integer.parseInt(tournamentId);
        } catch (NumberFormatException e) {
            intTourneyId = 1;
        }

        // Fetch tournament details from DB
        Tournament tournament = tournamentDAO.getTournamentById(tournamentId);
        if (tournament == null) {
            tournament = new Tournament();
            tournament.setId(tournamentId);
            tournament.setName("Giải Đấu Thể Thức Swiss System");
            tournament.setFormat("SWISS_LITE");
        }

        // Fetch participant teams from DB
        List<Team> dbTeamsList = participantDAO.getTeamsByTournamentId(tournamentId);

        // Fetch round map for Swiss system from DB
        Map<Integer, List<Match>> roundMap = swissDAO.getSwissRounds(intTourneyId);

        request.setAttribute("tournament", tournament);
        request.setAttribute("dbTeamsList", dbTeamsList);
        request.setAttribute("roundMap", roundMap);

        // Forward to swiss-stage.jsp
        request.getRequestDispatcher("/common/swiss-stage.jsp").forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        
        response.setContentType("application/json;charset=UTF-8");
        PrintWriter out = response.getWriter();

        try {
            String matchIdStr = request.getParameter("matchId");
            String score1Str = request.getParameter("team1Score");
            String score2Str = request.getParameter("team2Score");
            String status = request.getParameter("status");

            if (matchIdStr != null && score1Str != null && score2Str != null) {
                int matchId = Integer.parseInt(matchIdStr);
                int score1 = Integer.parseInt(score1Str);
                int score2 = Integer.parseInt(score2Str);

                boolean success = swissDAO.updateSwissMatchScore(matchId, score1, score2, status);

                if (success) {
                    out.print("{\"status\":\"success\",\"message\":\"Cập nhật tỷ số Swiss thành công!\"}");
                } else {
                    out.print("{\"status\":\"error\",\"message\":\"Không thể lưu tỷ số Swiss vào CSDL!\"}");
                }
            } else {
                out.print("{\"status\":\"error\",\"message\":\"Dữ liệu không hợp lệ!\"}");
            }
        } catch (Exception e) {
            out.print("{\"status\":\"error\",\"message\":\"Lỗi hệ thống: " + e.getMessage() + "\"}");
        }
    }
}
