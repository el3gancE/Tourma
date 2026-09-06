package test;

import dao.DBContext;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class TestEveryQueryIndividually {
    public static void main(String[] args) {
        try {
            DBContext db = new DBContext();
            Connection conn = db.getConnection();
            Statement st = conn.createStatement();
            
            String fileContent = Files.readString(Paths.get("web/database/queries.sql"));
            String[] lines = fileContent.split("\r?\n");
            
            StringBuilder currentQuery = new StringBuilder();
            int queryStartLine = 1;
            int totalTested = 0;
            int totalErrors = 0;
            
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i].trim();
                
                if (line.startsWith("--") || line.isEmpty() || line.startsWith("/*") || line.endsWith("*/")) {
                    continue;
                }
                
                if (line.equalsIgnoreCase("USE tourma_db;") || line.equalsIgnoreCase("GO")) {
                    continue;
                }
                
                if (currentQuery.length() == 0) {
                    queryStartLine = i + 1;
                }
                currentQuery.append(lines[i]).append("\n");
                
                if (line.endsWith(";")) {
                    String sql = currentQuery.toString().trim();
                    currentQuery.setLength(0);
                    
                    // Only test SELECT statements to prevent modifying database
                    if (sql.toUpperCase().startsWith("SELECT") || sql.toUpperCase().startsWith("DECLARE")) {
                        totalTested++;
                        try {
                            boolean hasRs = st.execute(sql);
                            if (hasRs) {
                                ResultSet rs = st.getResultSet();
                                ResultSetMetaData md = rs.getMetaData();
                                int cols = md.getColumnCount();
                                int rows = 0;
                                while (rs.next()) rows++;
                                rs.close();
                                System.out.println("Line " + queryStartLine + " [PASS]: " + cols + " cols, " + rows + " rows returned.");
                            } else {
                                System.out.println("Line " + queryStartLine + " [PASS]: executed successfully.");
                            }
                        } catch (Exception ex) {
                            totalErrors++;
                            System.err.println("Line " + queryStartLine + " [FAIL]: " + ex.getMessage());
                            System.err.println("SQL: " + sql + "\n");
                        }
                    }
                }
            }
            
            conn.close();
            System.out.println("\nSummary: " + totalTested + " tested, " + totalErrors + " errors.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
