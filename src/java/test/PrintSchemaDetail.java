package test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

public class PrintSchemaDetail {
    public static void main(String[] args) {
        String dbUrl = "jdbc:sqlserver://localhost:1433;databaseName=tourma_db;encrypt=false;trustServerCertificate=true;";
        String user = "sa";
        String pass = "123";

        try (Connection conn = DriverManager.getConnection(dbUrl, user, pass);
             Statement stmt = conn.createStatement()) {

            String sql = "SELECT TABLE_NAME, COLUMN_NAME, DATA_TYPE, CHARACTER_MAXIMUM_LENGTH, IS_NULLABLE " +
                         "FROM INFORMATION_SCHEMA.COLUMNS " +
                         "ORDER BY TABLE_NAME, ORDINAL_POSITION";

            ResultSet rs = stmt.executeQuery(sql);
            String currentTable = "";
            while (rs.next()) {
                String table = rs.getString("TABLE_NAME");
                if (!table.equals(currentTable)) {
                    currentTable = table;
                    System.out.println("\n--- Table: " + currentTable + " ---");
                }
                String col = rs.getString("COLUMN_NAME");
                String type = rs.getString("DATA_TYPE");
                String maxLen = rs.getString("CHARACTER_MAXIMUM_LENGTH");
                String isNull = rs.getString("IS_NULLABLE");
                System.out.printf("  %-25s %-15s len=%-6s null=%s\n", col, type, maxLen == null ? "" : maxLen, isNull);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
