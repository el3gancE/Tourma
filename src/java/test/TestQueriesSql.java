package test;

import java.io.File;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;

public class TestQueriesSql {
    public static void main(String[] args) {
        String dbUrl = "jdbc:sqlserver://localhost:1433;databaseName=tourma_db;encrypt=false;trustServerCertificate=true;";
        String user = "sa";
        String pass = "123";

        try {
            Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
            File sqlFile = new File("web/database/queries.sql");
            String content = Files.readString(sqlFile.toPath());

            // Split by GO or double newlines/sections
            String[] batches = content.split("(?i)\\bGO\\b");
            System.out.println("Total batches separated by GO: " + batches.length);

            try (Connection conn = DriverManager.getConnection(dbUrl, user, pass);
                 Statement stmt = conn.createStatement()) {

                int batchIdx = 0;
                for (String batch : batches) {
                    batchIdx++;
                    String trimmed = batch.trim();
                    if (trimmed.isEmpty()) continue;

                    System.out.println("==================================================");
                    System.out.println("Executing Batch #" + batchIdx + ":");
                    // Print first 2 lines
                    String[] lines = trimmed.split("\n");
                    for (int i = 0; i < Math.min(3, lines.length); i++) {
                        System.out.println("  " + lines[i].trim());
                    }

                    try {
                        boolean hasResultSet = stmt.execute(trimmed);
                        if (hasResultSet) {
                            int rsCount = 0;
                            do {
                                rsCount++;
                                try (ResultSet rs = stmt.getResultSet()) {
                                    ResultSetMetaData meta = rs.getMetaData();
                                    int colCount = meta.getColumnCount();
                                    int rowCount = 0;
                                    while (rs.next()) {
                                        rowCount++;
                                    }
                                    System.out.println("  -> Result set #" + rsCount + ": " + rowCount + " rows, " + colCount + " columns.");
                                }
                            } while (stmt.getMoreResults());
                        } else {
                            System.out.println("  -> Executed successfully (no result set, update count = " + stmt.getUpdateCount() + ")");
                        }
                    } catch (Exception ex) {
                        System.err.println("  [ERROR in Batch #" + batchIdx + "]: " + ex.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
