package dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public class SeriesConfigurationTemplateDAO {
    public record Applied(String name, int teamCount) {
        public String getName() { return name; }
        public int getTeamCount() { return teamCount; }
    }

    public String listJson(String seriesId) throws Exception {
        String sql = "SELECT id, name, team_count AS teamCount, stage1_format AS stage1, stage2_format AS stage2 "
                + "FROM dbo.series_configuration_templates WHERE series_id=? ORDER BY name,id FOR JSON PATH";
        try (Connection conn = new DBContext().getConnection();
             PreparedStatement query = conn.prepareStatement(sql)) {
            query.setString(1, seriesId);
            StringBuilder json = new StringBuilder();
            try (ResultSet rows = query.executeQuery()) {
                while (rows.next()) json.append(rows.getString(1));
            }
            return json.length() == 0 ? "[]" : json.toString();
        }
    }

    public Applied getApplied(String tournamentId) throws Exception {
        try (Connection conn = new DBContext().getConnection();
             PreparedStatement query = conn.prepareStatement(
                     "SELECT setup_template_name, setup_template_team_count FROM dbo.tournaments WHERE id=?")) {
            query.setString(1, tournamentId);
            try (ResultSet rows = query.executeQuery()) {
                if (!rows.next() || rows.getNString(1) == null) return null;
                return new Applied(rows.getNString(1), rows.getInt(2));
            }
        }
    }

    public void save(String seriesId, String tournamentId, String name, String type, String stage1, String stage2,
            int teamCount, int seats, String config, String tier, Integer topSeedCount, boolean classicDraw) throws Exception {
        String id = UUID.randomUUID().toString();
        String sql = """
                INSERT dbo.series_configuration_templates
                    (id,series_id,name,tournament_type,stage1_format,stage2_format,team_count,advancing_seats,
                     format_config,tier_name,top_seed_count,classic_draw,reward_points,points_config)
                SELECT ?,t.series_id,?,?,?,?,?,?,?,?,?,?,t.series_reward_points,t.series_points_config
                FROM dbo.tournaments t WITH (HOLDLOCK) WHERE t.id=? AND t.series_id=?
                """;
        try (Connection conn = new DBContext().getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement insert = conn.prepareStatement(sql);
                 PreparedStatement points = conn.prepareStatement(
                         "INSERT dbo.series_configuration_template_points "
                         + "SELECT ?,rank_position,rank_label,points_awarded FROM dbo.tournament_placement_points WITH (HOLDLOCK) WHERE tournament_id=?")) {
                insert.setString(1, id);
                insert.setNString(2, name);
                insert.setString(3, type);
                insert.setString(4, stage1);
                insert.setString(5, stage2);
                insert.setInt(6, teamCount);
                insert.setInt(7, seats);
                insert.setNString(8, config);
                insert.setString(9, tier);
                insert.setObject(10, topSeedCount, java.sql.Types.INTEGER);
                insert.setBoolean(11, classicDraw);
                insert.setString(12, tournamentId);
                insert.setString(13, seriesId);
                if (insert.executeUpdate() != 1) throw new IllegalStateException("Không tìm thấy giải nguồn.");
                points.setString(1, id);
                points.setString(2, tournamentId);
                points.executeUpdate();
                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                throw e;
            }
        }
    }

    public void apply(String seriesId, String tournamentId, String templateId) throws Exception {
        try (Connection conn = new DBContext().getConnection()) {
            conn.setAutoCommit(false);
            try {
                assertEditable(conn, seriesId, tournamentId);
                String stage1;
                String stage2;
                try (PreparedStatement query = conn.prepareStatement(
                        "SELECT stage1_format,stage2_format FROM dbo.series_configuration_templates WITH (HOLDLOCK) WHERE id=? AND series_id=?")) {
                    query.setString(1, templateId);
                    query.setString(2, seriesId);
                    try (ResultSet rows = query.executeQuery()) {
                        if (!rows.next()) throw new IllegalArgumentException("Không tìm thấy mẫu trong Series này.");
                        stage1 = rows.getString(1);
                        stage2 = rows.getString(2);
                    }
                }
                String update = """
                        UPDATE t SET tournament_type=p.tournament_type,advancing_seats_count=p.advancing_seats,
                            multi_stage_config=p.format_config,tier_name=p.tier_name,top_seed_count=p.top_seed_count,
                            classic_draw=p.classic_draw,series_reward_points=p.reward_points,series_points_config=p.points_config,
                            setup_template_name=p.name,setup_template_team_count=p.team_count
                        FROM dbo.tournaments t JOIN dbo.series_configuration_templates p ON p.series_id=t.series_id
                        WHERE t.id=? AND p.id=?
                        """;
                try (PreparedStatement query = conn.prepareStatement(update)) {
                    query.setString(1, tournamentId);
                    query.setString(2, templateId);
                    query.executeUpdate();
                }
                saveStage(conn, tournamentId, 1, stage1);
                if (stage2 != null) {
                    saveStage(conn, tournamentId, 2, stage2);
                } else {
                    try (PreparedStatement remove = conn.prepareStatement(
                            "DELETE dbo.tournament_stages WHERE tournament_id=? AND stage_order>1")) {
                        remove.setString(1, tournamentId);
                        remove.executeUpdate();
                    }
                }
                try (PreparedStatement remove = conn.prepareStatement("DELETE dbo.tournament_placement_points WHERE tournament_id=?");
                     PreparedStatement insert = conn.prepareStatement(
                             "INSERT dbo.tournament_placement_points (id,tournament_id,rank_position,rank_label,points_awarded) "
                             + "SELECT CONCAT('tpp_',NEWID()),?,rank_position,rank_label,points_awarded "
                             + "FROM dbo.series_configuration_template_points WHERE template_id=?")) {
                    remove.setString(1, tournamentId);
                    remove.executeUpdate();
                    insert.setString(1, tournamentId);
                    insert.setString(2, templateId);
                    insert.executeUpdate();
                }
                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                throw e;
            }
        }
        TournamentDAO.clearTournamentCache(tournamentId);
        SeriesDAO.clearSeriesCaches();
        service.RollingWindowPointService.clearAllCaches();
    }

    public void clearApplied(String seriesId, String tournamentId) throws Exception {
        try (Connection conn = new DBContext().getConnection();
             PreparedStatement query = conn.prepareStatement(
                     "UPDATE dbo.tournaments SET setup_template_name=NULL, setup_template_team_count=NULL WHERE id=? AND series_id=?")) {
            query.setString(1, tournamentId);
            query.setString(2, seriesId);
            query.executeUpdate();
        }
    }

    private void assertEditable(Connection conn, String seriesId, String tournamentId) throws SQLException {
        String sql = """
                SELECT 1 FROM dbo.tournaments t WITH (UPDLOCK,HOLDLOCK)
                WHERE t.id=? AND t.series_id=? AND COALESCE(t.status,'DRAFT')='DRAFT'
                    AND NOT EXISTS (SELECT 1 FROM dbo.matches m WITH (HOLDLOCK) WHERE m.tournament_id=t.id)
                    AND NOT EXISTS (SELECT 1 FROM dbo.groups g WITH (HOLDLOCK)
                        JOIN dbo.tournament_stages s ON s.id=g.stage_id WHERE s.tournament_id=t.id)
                """;
        try (PreparedStatement query = conn.prepareStatement(sql)) {
            query.setString(1, tournamentId);
            query.setString(2, seriesId);
            try (ResultSet rows = query.executeQuery()) {
                if (!rows.next()) throw new IllegalStateException("Chỉ áp dụng mẫu cho giải chưa có lịch thi đấu hoặc chia bảng.");
            }
        }
    }

    private void saveStage(Connection conn, String tournamentId, int stage, String format) throws SQLException {
        try (PreparedStatement update = conn.prepareStatement("UPDATE dbo.tournament_stages SET format=? WHERE tournament_id=? AND stage_order=?");
             PreparedStatement insert = conn.prepareStatement(
                     "INSERT dbo.tournament_stages (id,tournament_id,stage_order,stage_name,format) VALUES (?,?,?,?,?)")) {
            update.setString(1, format);
            update.setString(2, tournamentId);
            update.setInt(3, stage);
            if (update.executeUpdate() == 0) {
                insert.setString(1, UUID.randomUUID().toString());
                insert.setString(2, tournamentId);
                insert.setInt(3, stage);
                insert.setString(4, "Stage " + stage);
                insert.setString(5, format);
                insert.executeUpdate();
            }
        }
    }
}
