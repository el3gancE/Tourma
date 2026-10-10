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

    private static final int MAX_POOL_SIZE = 25;
    private static final java.util.concurrent.BlockingQueue<Connection> POOL = new java.util.concurrent.ArrayBlockingQueue<>(MAX_POOL_SIZE);

    static {
        try {
            Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    private static volatile boolean schemaMigrated = false;

    private static Connection createPhysicalConnection() throws SQLException {
        Connection conn = DriverManager.getConnection(URL, userID, password);
        if (!schemaMigrated) {
            synchronized (DBContext.class) {
                if (!schemaMigrated) {
                    try (java.sql.Statement st = conn.createStatement()) {
                        st.executeUpdate("IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID(N'tournaments') AND name = 'champion_name') " +
                                        "BEGIN ALTER TABLE tournaments ADD champion_name NVARCHAR(255) NULL; END");
                        st.executeUpdate("IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID(N'tournaments') AND name = 'teams_json') " +
                                        "BEGIN ALTER TABLE tournaments ADD teams_json NVARCHAR(MAX) NULL; END");
                        schemaMigrated = true;
                    } catch (Exception ignore) {}
                }
            }
        }
        return conn;
    }

    public Connection getConnection() throws Exception {
        Connection realConn = null;
        while (!POOL.isEmpty()) {
            Connection candidate = POOL.poll();
            if (candidate != null) {
                try {
                    if (!candidate.isClosed()) {
                        realConn = candidate;
                        break;
                    } else {
                        try { candidate.close(); } catch (Exception ignore) {}
                    }
                } catch (Exception e) {
                    try { candidate.close(); } catch (Exception ignore) {}
                }
            }
        }

        if (realConn == null) {
            realConn = createPhysicalConnection();
        }

        final Connection underlying = realConn;
        return (Connection) java.lang.reflect.Proxy.newProxyInstance(
            Connection.class.getClassLoader(),
            new Class<?>[]{Connection.class},
            new java.lang.reflect.InvocationHandler() {
                private boolean isClosed = false;

                @Override
                public Object invoke(Object proxy, java.lang.reflect.Method method, Object[] args) throws Throwable {
                    String mName = method.getName();
                    if ("close".equals(mName)) {
                        if (!isClosed) {
                            isClosed = true;
                            try {
                                if (!underlying.isClosed()) {
                                    if (!underlying.getAutoCommit()) {
                                        underlying.rollback();
                                        underlying.setAutoCommit(true);
                                    }
                                    if (!POOL.offer(underlying)) {
                                        underlying.close();
                                    }
                                }
                            } catch (Exception ex) {
                                try { underlying.close(); } catch (Exception ignore) {}
                            }
                        }
                        return null;
                    }
                    if ("isClosed".equals(mName)) {
                        return isClosed || underlying.isClosed();
                    }
                    return method.invoke(underlying, args);
                }
            }
        );
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
