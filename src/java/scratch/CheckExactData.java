package scratch;

import dao.DBContext;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;

public class CheckExactData {
    public static void main(String[] args) {
        DBContext db = new DBContext();
        try (Connection conn = db.getConnection()) {
            System.out.println("=== TOURNAMENTS TABLE ===");
            String sqlT = "SELECT * FROM tournaments ORDER BY created_at ASC";
            try (PreparedStatement psT = conn.prepareStatement(sqlT);
                 ResultSet rsT = psT.executeQuery()) {
                ResultSetMetaData md = rsT.getMetaData();
                for (int i = 1; i <= md.getColumnCount(); i++) {
                    System.out.print(md.getColumnName(i) + " | ");
                }
                System.out.println();
                while (rsT.next()) {
                    System.out.println(rsT.getString("id") + " | " + rsT.getString("name") + " | Type=" + rsT.getString("tournament_type") + " | Tier=" + rsT.getString("tier_name") + " | Status=" + rsT.getString("status"));
                }
            }

            System.out.println("\n=== SERIES TOURNAMENT HISTORY COLUMNS ===");
            String sqlH = "SELECT TOP 5 * FROM series_tournament_history";
            try (PreparedStatement psH = conn.prepareStatement(sqlH);
                 ResultSet rsH = psH.executeQuery()) {
                ResultSetMetaData mdH = rsH.getMetaData();
                for (int i = 1; i <= mdH.getColumnCount(); i++) {
                    System.out.print(mdH.getColumnName(i) + " | ");
                }
                System.out.println();
                while (rsH.next()) {
                    for (int i = 1; i <= mdH.getColumnCount(); i++) {
                        System.out.print(rsH.getString(i) + " | ");
                    }
                    System.out.println();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
