package test;

import dao.DBContext;
import dao.ParticipantDAO;
import dao.SeriesDAO;
import model.Team;
import model.Tournament;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.Map;

public class CheckMatches {
    public static void main(String[] args) {
        try {
            DBContext db = new DBContext();
            Connection conn = db.getConnection();

            String tourneyId = "TOURNEY_1788600858654"; // A3
            System.out.println("=== TOURNAMENT A3: " + tourneyId + " ===");

            // 1. Teams
            ParticipantDAO pDao = new ParticipantDAO();
            List<Team> teams = pDao.getTeamsByTournamentId(tourneyId);
            System.out.println("Teams count in A3: " + teams.size());
            for (int i = 0; i < Math.min(5, teams.size()); i++) {
                Team t = teams.get(i);
                System.out.println("  Team: ID=" + t.getId() + ", Name=" + t.getRawName() + ", Seed=" + t.getOriginalSeed());
            }

            // 2. Matches
            String matchSql = "SELECT * FROM matches WHERE tournament_id = ? ORDER BY round_number DESC, match_number ASC";
            PreparedStatement ps = conn.prepareStatement(matchSql);
            ps.setString(1, tourneyId);
            ResultSet rs = ps.executeQuery();
            int mCount = 0;
            while (rs.next()) {
                mCount++;
                if (mCount <= 10) {
                    System.out.println("  Match: ID=" + rs.getString("id") + ", R=" + rs.getInt("round_number") + ", M=" + rs.getInt("match_number") + 
                                       ", T1=" + rs.getString("team1_id") + " (" + rs.getString("team1_name") + "), T2=" + rs.getString("team2_id") + " (" + rs.getString("team2_name") + ")" +
                                       ", S1=" + rs.getObject("score1") + ", S2=" + rs.getObject("score2") +
                                       ", WinnerID=" + rs.getString("winner_id") + ", WinnerTeamID=" + rs.getString("winner_team_id") +
                                       ", Status=" + rs.getString("status"));
                }
            }
            System.out.println("Total matches in DB for A3: " + mCount);

            // 3. Placements
            Map<String, Integer> placements = pDao.getTournamentPlacements(tourneyId);
            System.out.println("Placements returned by pDao (" + placements.size() + "):");
            for (String k : placements.keySet()) {
                System.out.println("  Placement: " + k + " => " + placements.get(k));
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
