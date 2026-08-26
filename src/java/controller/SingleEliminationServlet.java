package controller;

import dao.SingleEliminationDAO;
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
import model.Tournament;

/**
 * Controller for Single Elimination Tournament Bracket Page & AJAX Score Updates.
 */
@WebServlet(name = "SingleEliminationServlet", urlPatterns = {"/single-elimination"})
public class SingleEliminationServlet extends HttpServlet {

    private final SingleEliminationDAO singleEliminationDAO = new SingleEliminationDAO();
    private final TournamentDAO tournamentDAO = new TournamentDAO();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        
        String idParam = request.getParameter("id");
        int tournamentId = 1; // Default demo ID
        if (idParam != null && !idParam.trim().isEmpty()) {
            try {
                tournamentId = Integer.parseInt(idParam);
            } catch (NumberFormatException e) {
                tournamentId = 1;
            }
        }

        // Fetch tournament details
        Tournament tournament = tournamentDAO.getTournamentById(tournamentId);
        if (tournament == null) {
            tournament = new Tournament();
            tournament.setId(tournamentId);
            tournament.setTournamentName("Giải Đấu Vô Địch Loại Trực Tiếp (Single Elimination)");
            tournament.setFormat("SINGLE_ELIMINATION");
        }

        // Fetch bracket rounds map
        Map<Integer, List<Match>> roundMap = singleEliminationDAO.getBracketRounds(tournamentId);

        request.setAttribute("tournament", tournament);
        request.setAttribute("roundMap", roundMap);

        // Forward to single-elimination.jsp
        request.getRequestDispatcher("/common/single-elimination.jsp").forward(request, response);
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
            String winnerFlag = request.getParameter("winner");

            if (matchIdStr != null && score1Str != null && score2Str != null) {
                int matchId = Integer.parseInt(matchIdStr);
                int score1 = Integer.parseInt(score1Str);
                int score2 = Integer.parseInt(score2Str);

                boolean success = singleEliminationDAO.updateMatchScoreAndAdvance(matchId, score1, score2, winnerFlag);

                if (success) {
                    out.print("{\"status\":\"success\",\"message\":\"Cập nhật tỷ số thành công!\"}");
                } else {
                    out.print("{\"status\":\"error\",\"message\":\"Không thể lưu tỷ số vào CSDL!\"}");
                }
            } else {
                out.print("{\"status\":\"error\",\"message\":\"Dữ liệu gửi lên không hợp lệ!\"}");
            }
        } catch (Exception e) {
            out.print("{\"status\":\"error\",\"message\":\"Lỗi hệ thống: " + e.getMessage() + "\"}");
        }
    }
}
