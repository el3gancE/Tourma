package test;

import dao.DBContext;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

public class InspectAllTableColumns {
    public static void main(String[] args) {
        try {
            DBContext db = new DBContext();
            Connection conn = db.getConnection();
            Statement st = conn.createStatement();
            
            String sql = "SELECT t.name AS table_name, c.name AS column_name, ty.name AS type_name, c.max_length, c.is_nullable "
                       + "FROM sys.tables t "
                       + "JOIN sys.columns c ON t.object_id = c.object_id "
                       + "JOIN sys.types ty ON c.user_type_id = ty.user_type_id "
                       + "ORDER BY t.name, c.column_id";
            ResultSet rs = st.executeQuery(sql);
            String currTbl = "";
            while (rs.next()) {
                String tbl = rs.getString("table_name");
                if (!tbl.equals(currTbl)) {
                    System.out.println("\n=== TABLE: " + tbl + " ===");
                    currTbl = tbl;
                }
                System.out.println("   " + rs.getString("column_name") + " (" + rs.getString("type_name") + ")");
            }
            conn.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
