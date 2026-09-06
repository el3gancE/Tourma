package test;

import dao.DBContext;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

public class InspectMatchesInDB {
    public static void main(String[] args) {
        DBContext db = new DBContext();
        try (Connection conn = db.getConnection();
             Statement stmt = conn.createStatement()) {

            String sql = "SELECT t.id, t.name, COUNT(m.id) AS total_matches, " +
                         "SUM(CASE WHEN m.winner_id IS NOT NULL THEN 1 ELSE 0 END) AS finished_matches " +
                         "FROM tournaments t " +
                         "LEFT JOIN matches m ON t.id = m.tournament_id " +
                         "WHERE t.series_id = 'S_bc6c7365' " +
                         "GROUP BY t.id, t.name, t.tournament_index_in_series " +
                         "ORDER BY t.tournament_index_in_series ASC";

            ResultSet rs = stmt.executeQuery(sql);
            while (rs.next()) {
                System.out.printf("Tournament: %-25s (ID: %s) -> Total Matches: %d, Finished Matches: %d\n",
                        rs.getString("name"), rs.getString("id"), rs.getInt("total_matches"), rs.getInt("finished_matches"));
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
