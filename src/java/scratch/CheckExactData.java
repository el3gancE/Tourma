package scratch;

import dao.DBContext;
import service.RollingWindowPointService;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class CheckExactData {
    public static void main(String[] args) {
        System.out.println("Calling recalculateAndPersistStandings for S_9b4717cd...");
        boolean ok = RollingWindowPointService.getInstance().recalculateAndPersistStandings("S_9b4717cd");
        System.out.println("Recalculate result: " + ok);

        DBContext db = new DBContext();
        try (Connection conn = db.getConnection()) {
            System.out.println("\n=== SERIES STANDINGS FOR PONGERS ===");
            String sqlS = "SELECT normalized_team_name, rank_overall, total_rolling_points FROM series_standings WHERE normalized_team_name LIKE '%pongers%'";
            try (PreparedStatement ps = conn.prepareStatement(sqlS);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    System.out.println(rs.getString(1) + " | Rank=" + rs.getInt(2) + " | Points=" + rs.getInt(3));
                }
            }

            System.out.println("\n=== SERIES TOURNAMENT HISTORY FOR PONGERS ===");
            String sqlH = "SELECT tournament_id, tournament_rank, points_earned FROM series_tournament_history WHERE normalized_team_name LIKE '%pongers%'";
            try (PreparedStatement ps = conn.prepareStatement(sqlH);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    System.out.println(rs.getString(1) + " | Rank=" + rs.getInt(2) + " | Points=" + rs.getInt(3));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

