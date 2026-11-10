package dao;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * DBContext for MS SQL Server connection in Tourma project.
 */
public class DBContext {
    private static final String serverName = "localhost";
    private static final String dbName = "tourma";
    private static final String portNumber = "1433";
    private static final String userID = "sa";
    private static final String password = "123";
    private static final String URL = "jdbc:sqlserver://" + serverName + ":" + portNumber + ";databaseName=" + dbName + ";encrypt=true;trustServerCertificate=true;";

    private static final JdbcConnectionPool POOL = new JdbcConnectionPool(
            DBContext::createPhysicalConnection,
            positiveProperty("tourma.db.poolSize", 12),
            positiveProperty("tourma.db.acquireTimeoutMs", 5000),
            positiveProperty("tourma.db.queryTimeoutSeconds", 20));

    private static int positiveProperty(String name, int fallback) {
        int value = Integer.getInteger(name, fallback);
        return value > 0 ? value : fallback;
    }

    public static String poolSnapshot() { return POOL.snapshot(); }
    public static void closePool() { POOL.close(); }

    static {
        try {
            Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    private static volatile boolean schemaMigrated = false;

    private static Connection createPhysicalConnection() throws SQLException {
        Connection conn = DriverManager.getConnection(URL
                + "loginTimeout=" + positiveProperty("tourma.db.loginTimeoutSeconds", 5)
                + ";socketTimeout=" + positiveProperty("tourma.db.socketTimeoutMs", 45000) + ";", userID, password);
        if (!schemaMigrated) {
            synchronized (DBContext.class) {
                if (!schemaMigrated) {
                    try (java.sql.Statement st = conn.createStatement()) {
                        st.setQueryTimeout(20);
                        st.executeUpdate("IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID(N'tournaments') AND name = 'champion_name') " +
                                        "BEGIN ALTER TABLE tournaments ADD champion_name NVARCHAR(255) NULL; END");
                        st.executeUpdate("IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID(N'tournaments') AND name = 'teams_json') " +
                                        "BEGIN ALTER TABLE tournaments ADD teams_json NVARCHAR(MAX) NULL; END");
                        st.executeUpdate("IF COL_LENGTH('tournaments', 'top_seed_count') IS NULL " +
                                        "BEGIN ALTER TABLE tournaments ADD top_seed_count INT NULL; END");
                        st.executeUpdate("IF COL_LENGTH('tournaments', 'classic_draw') IS NULL " +
                                        "BEGIN ALTER TABLE tournaments ADD classic_draw BIT NOT NULL DEFAULT 0; END");
                        schemaMigrated = true;
                    } catch (Exception ignore) {}
                }
            }
        }
        return conn;
    }

    public Connection getConnection() throws Exception {
        return POOL.borrow();
    }

    public void closeConnection(Connection con, PreparedStatement ps, ResultSet rs) throws SQLException {
        if (rs != null && !rs.isClosed()) {
            rs.close();
        }
        if (ps != null && !ps.isClosed()) {
            ps.close();
        }
        if (con != null && !con.isClosed()) {
            con.close();
        }
    }    
}
