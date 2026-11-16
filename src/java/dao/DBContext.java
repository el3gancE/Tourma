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
    private static final String URL = "jdbc:sqlserver://" + serverName + ":" + portNumber + ";databaseName=" + dbName + ";encrypt=true;trustServerCertificate=true;sendStringParametersAsUnicode=false;";

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

    private static void ensureIndex(java.sql.Statement st, String indexName, String createSql) {
        try {
            st.executeUpdate("IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = '" + indexName + "') " + createSql);
        } catch (Exception ignore) {}
    }

    private static Connection createPhysicalConnection() throws SQLException {
        Connection conn = DriverManager.getConnection(URL
                + "loginTimeout=" + positiveProperty("tourma.db.loginTimeoutSeconds", 5)
                + ";socketTimeout=" + positiveProperty("tourma.db.socketTimeoutMs", 45000) + ";", userID, password);
        if (!schemaMigrated) {
            synchronized (DBContext.class) {
                if (!schemaMigrated) {
                    try (java.sql.Statement st = conn.createStatement()) {
                        st.setQueryTimeout(20);
                        st.execute("SET ANSI_NULLS ON; SET QUOTED_IDENTIFIER ON;");
                        st.executeUpdate("IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'schema_migrations') " +
                                        "CREATE TABLE schema_migrations (version VARCHAR(50) PRIMARY KEY, description NVARCHAR(255) NOT NULL, applied_at DATETIME DEFAULT GETDATE());");
                        st.executeUpdate("IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID(N'tournaments') AND name = 'champion_name') " +
                                        "BEGIN ALTER TABLE tournaments ADD champion_name NVARCHAR(255) NULL; END");
                        st.executeUpdate("IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID(N'tournaments') AND name = 'teams_json') " +
                                        "BEGIN ALTER TABLE tournaments ADD teams_json NVARCHAR(MAX) NULL; END");
                        st.executeUpdate("IF COL_LENGTH('tournaments', 'top_seed_count') IS NULL " +
                                        "BEGIN ALTER TABLE tournaments ADD top_seed_count INT NULL; END");
                        st.executeUpdate("IF COL_LENGTH('tournaments', 'classic_draw') IS NULL " +
                                        "BEGIN ALTER TABLE tournaments ADD classic_draw BIT NOT NULL DEFAULT 0; END");
                        ensureIndex(st, "idx_matches_tourney_perf", "CREATE NONCLUSTERED INDEX idx_matches_tourney_perf ON matches(tournament_id, is_bye) INCLUDE (status, winner_id, loser_id, score1, score2, penalty1, penalty2, team1_id, team2_id, stage_id);");
                        ensureIndex(st, "idx_series_history_tourney", "CREATE NONCLUSTERED INDEX idx_series_history_tourney ON series_tournament_history(tournament_id);");
                        ensureIndex(st, "idx_tournaments_linked", "CREATE NONCLUSTERED INDEX idx_tournaments_linked ON tournaments(linked_qualifier_tournament_id) WHERE linked_qualifier_tournament_id IS NOT NULL;");
                        ensureIndex(st, "idx_stage_participants_tourney", "CREATE NONCLUSTERED INDEX idx_stage_participants_tourney ON stage_participants(tournament_id);");
                        ensureIndex(st, "idx_matches_team2", "CREATE NONCLUSTERED INDEX idx_matches_team2 ON matches(team2_id) WHERE team2_id IS NOT NULL;");
                        ensureIndex(st, "idx_matches_winner", "CREATE NONCLUSTERED INDEX idx_matches_winner ON matches(winner_id) WHERE winner_id IS NOT NULL;");
                        ensureIndex(st, "idx_matches_loser", "CREATE NONCLUSTERED INDEX idx_matches_loser ON matches(loser_id) WHERE loser_id IS NOT NULL;");
                        ensureIndex(st, "idx_matches_group", "CREATE NONCLUSTERED INDEX idx_matches_group ON matches(group_id) WHERE group_id IS NOT NULL;");
                        ensureIndex(st, "idx_matches_stage", "CREATE NONCLUSTERED INDEX idx_matches_stage ON matches(stage_id) WHERE stage_id IS NOT NULL;");
                        ensureIndex(st, "idx_teams_stage", "CREATE NONCLUSTERED INDEX idx_teams_stage ON teams(current_stage_id) WHERE current_stage_id IS NOT NULL;");
                        ensureIndex(st, "idx_teams_tourney_seed", "CREATE NONCLUSTERED INDEX idx_teams_tourney_seed ON teams(tournament_id, original_seed);");
                        ensureIndex(st, "idx_group_teams_perf", "CREATE NONCLUSTERED INDEX idx_group_teams_perf ON group_teams(group_id) INCLUDE (team_id, rank_in_group);");
                        ensureIndex(st, "idx_matches_next_match", "CREATE NONCLUSTERED INDEX idx_matches_next_match ON matches(next_match_id) WHERE next_match_id IS NOT NULL;");
                        ensureIndex(st, "idx_matches_loser_next_match", "CREATE NONCLUSTERED INDEX idx_matches_loser_next_match ON matches(loser_next_match_id) WHERE loser_next_match_id IS NOT NULL;");
                        ensureIndex(st, "idx_group_teams_team", "CREATE NONCLUSTERED INDEX idx_group_teams_team ON group_teams(team_id);");
                        ensureIndex(st, "idx_partner_participants_series", "CREATE NONCLUSTERED INDEX idx_partner_participants_series ON partner_participants(series_id, created_at);");
                        ensureIndex(st, "idx_teams_partner_participant", "CREATE NONCLUSTERED INDEX idx_teams_partner_participant ON teams(partner_participant_id) WHERE partner_participant_id IS NOT NULL;");
                        ensureIndex(st, "idx_series_standings_partner", "CREATE NONCLUSTERED INDEX idx_series_standings_partner ON series_standings(partner_participant_id);");
                        ensureIndex(st, "idx_stage_participants_team", "CREATE NONCLUSTERED INDEX idx_stage_participants_team ON stage_participants(team_id);");
                        st.executeUpdate("IF NOT EXISTS (SELECT 1 FROM schema_migrations WHERE version = '001') INSERT INTO schema_migrations (version, description) VALUES ('001', N'Khởi tạo 13 bảng cốt lõi và tracking schema_migrations');");
                        st.executeUpdate("IF NOT EXISTS (SELECT 1 FROM schema_migrations WHERE version = '002') INSERT INTO schema_migrations (version, description) VALUES ('002', N'Bổ sung các cột champion_name, teams_json, top_seed_count, classic_draw vào tournaments');");
                        st.executeUpdate("IF NOT EXISTS (SELECT 1 FROM schema_migrations WHERE version = '003') INSERT INTO schema_migrations (version, description) VALUES ('003', N'Tạo các chỉ mục bao phủ (Covering Indexes) cho matches, teams, group_teams, tournaments');");
                        st.executeUpdate("IF NOT EXISTS (SELECT 1 FROM schema_migrations WHERE version = '004') INSERT INTO schema_migrations (version, description) VALUES ('004', N'Tạo chỉ mục trên toàn bộ các cột khóa ngoại (Foreign Keys) để tối ưu Cascade Delete');");
                        schemaMigrated = true;
                    } catch (Exception e) {
                        java.util.logging.Logger.getLogger(DBContext.class.getName()).warning("Auto migration check: " + e.getMessage());
                    }
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
