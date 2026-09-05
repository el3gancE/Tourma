package dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import model.Tournament;

/**
 * Data Access Object for Tournaments table
 */
public class TournamentDAO {

    public List<Tournament> getAllTournaments() {
        List<Tournament> list = new ArrayList<>();
        String sql = "SELECT t.*, " +
                "(SELECT TOP 1 format FROM tournament_stages WHERE tournament_id = t.id ORDER BY stage_order ASC) AS stage_format, "
                +
                "(SELECT TOP 1 tm.raw_name FROM matches m JOIN teams tm ON m.winner_id = tm.id WHERE m.tournament_id = t.id AND m.winner_id IS NOT NULL ORDER BY m.round_number DESC) AS db_champion_name "
                +
                "FROM tournaments t ORDER BY t.created_at DESC";
        DBContext db = new DBContext();

        try (Connection conn = db.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                Tournament t = new Tournament(
                        rs.getString("id"),
                        rs.getString("series_id"),
                        rs.getString("name"),
                        rs.getString("tournament_type"),
                        rs.getString("series_event_type"),
                        rs.getString("tier_name"),
                        rs.getInt("tournament_index_in_series"),
                        rs.getInt("phase_number"),
                        rs.getInt("max_teams_per_group"),
                        rs.getInt("advancing_seats_count"),
                        rs.getString("linked_qualifier_tournament_id"),
                        rs.getString("status"),
                        rs.getTimestamp("created_at"));
                try {
                    String fmt = rs.getString("stage_format");
                    if (fmt != null && !fmt.trim().isEmpty()) {
                        t.setFormat(fmt.trim());
                    }
                } catch (Exception ignore) {
                }
                try {
                    String champ = rs.getString("db_champion_name");
                    if (champ != null && !champ.trim().isEmpty()) {
                        t.setChampionName(champ.trim());
                    }
                } catch (Exception ignore) {
                }
                try {
                    t.setSeriesPointsConfig(rs.getString("series_points_config"));
                } catch (Exception ignore) {
                }
                try {
                    t.setGroupAssignments(rs.getString("group_assignments"));
                } catch (Exception ignore) {
                }
                try {
                    t.setStage2Teams(rs.getString("stage2_teams"));
                } catch (Exception ignore) {
                }
                try {
                    t.setMultiStageConfig(rs.getString("multi_stage_config"));
                } catch (Exception ignore) {
                }
                list.add(t);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    public Tournament getTournamentById(String id) {
        String sql = "SELECT t.*, " +
                "(SELECT TOP 1 format FROM tournament_stages WHERE tournament_id = t.id ORDER BY stage_order ASC) AS stage_format, "
                +
                "(SELECT TOP 1 tm.raw_name FROM matches m JOIN teams tm ON m.winner_id = tm.id WHERE m.tournament_id = t.id AND m.winner_id IS NOT NULL ORDER BY m.round_number DESC) AS db_champion_name "
                +
                "FROM tournaments t WHERE t.id = ?";
        DBContext db = new DBContext();

        try (Connection conn = db.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Tournament t = new Tournament(
                            rs.getString("id"),
                            rs.getString("series_id"),
                            rs.getString("name"),
                            rs.getString("tournament_type"),
                            rs.getString("series_event_type"),
                            rs.getString("tier_name"),
                            rs.getInt("tournament_index_in_series"),
                            rs.getInt("phase_number"),
                            rs.getInt("max_teams_per_group"),
                            rs.getInt("advancing_seats_count"),
                            rs.getString("linked_qualifier_tournament_id"),
                            rs.getString("status"),
                            rs.getTimestamp("created_at"));
                    try {
                        String fmt = rs.getString("stage_format");
                        if (fmt != null && !fmt.trim().isEmpty()) {
                            t.setFormat(fmt.trim());
                        }
                    } catch (Exception ignore) {
                    }
                    try {
                        String champ = rs.getString("db_champion_name");
                        if (champ != null && !champ.trim().isEmpty()) {
                            t.setChampionName(champ.trim());
                        }
                    } catch (Exception ignore) {
                    }
                    try {
                        t.setSeriesRewardPoints(rs.getInt("series_reward_points"));
                    } catch (Exception ignore) {
                    }
                    try {
                        t.setSeriesPointsConfig(rs.getString("series_points_config"));
                    } catch (Exception ignore) {
                    }
                    try {
                        t.setGroupAssignments(rs.getString("group_assignments"));
                    } catch (Exception ignore) {
                    }
                    try {
                        t.setStage2Teams(rs.getString("stage2_teams"));
                    } catch (Exception ignore) {
                    }
                    try {
                        t.setMultiStageConfig(rs.getString("multi_stage_config"));
                    } catch (Exception ignore) {
                    }
                    return t;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public List<Tournament> getTournamentsBySeriesId(String seriesId) {
        List<Tournament> list = new ArrayList<>();
        if (seriesId == null || seriesId.trim().isEmpty()) return list;
        String sql = "SELECT t.*, " +
                     "(SELECT TOP 1 format FROM tournament_stages WHERE tournament_id = t.id ORDER BY stage_order ASC) AS stage_format, " +
                     "(SELECT TOP 1 tm.raw_name FROM matches m JOIN teams tm ON m.winner_id = tm.id WHERE m.tournament_id = t.id AND m.winner_id IS NOT NULL ORDER BY m.round_number DESC) AS db_champion_name " +
                     "FROM tournaments t WHERE t.series_id = ? ORDER BY t.tournament_index_in_series ASC, t.created_at ASC";
        DBContext db = new DBContext();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, seriesId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Tournament t = new Tournament(
                        rs.getString("id"),
                        rs.getString("series_id"),
                        rs.getString("name"),
                        rs.getString("tournament_type"),
                        rs.getString("series_event_type"),
                        rs.getString("tier_name"),
                        rs.getInt("tournament_index_in_series"),
                        rs.getInt("phase_number"),
                        rs.getInt("max_teams_per_group"),
                        rs.getInt("advancing_seats_count"),
                        rs.getString("linked_qualifier_tournament_id"),
                        rs.getString("status"),
                        rs.getTimestamp("created_at")
                    );
                    try {
                        String fmt = rs.getString("stage_format");
                        if (fmt != null && !fmt.trim().isEmpty()) t.setFormat(fmt.trim());
                    } catch (Exception ignore) {}
                    try {
                        String champ = rs.getString("db_champion_name");
                        if (champ != null && !champ.trim().isEmpty()) t.setChampionName(champ.trim());
                    } catch (Exception ignore) {}
                    try {
                        t.setSeriesRewardPoints(rs.getInt("series_reward_points"));
                    } catch (Exception ignore) {}
                    try {
                        t.setSeriesPointsConfig(rs.getString("series_points_config"));
                    } catch (Exception ignore) {}
                    try {
                        t.setGroupAssignments(rs.getString("group_assignments"));
                    } catch (Exception ignore) {}
                    try {
                        t.setStage2Teams(rs.getString("stage2_teams"));
                    } catch (Exception ignore) {}
                    try {
                        t.setMultiStageConfig(rs.getString("multi_stage_config"));
                    } catch (Exception ignore) {}
                    list.add(t);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    public boolean insertTournament(Tournament t) {
        String sql = "INSERT INTO tournaments (id, series_id, name, tournament_type, series_event_type, " +
                "tier_name, tournament_index_in_series, phase_number, max_teams_per_group, " +
                "advancing_seats_count, linked_qualifier_tournament_id, status) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        DBContext db = new DBContext();

        try (Connection conn = db.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, t.getId());
            ps.setString(2, t.getSeriesId());
            ps.setString(3, t.getName());
            ps.setString(4, t.getTournamentType());
            ps.setString(5, t.getSeriesEventType());
            ps.setString(6, t.getTierName());
            ps.setInt(7, t.getTournamentIndexInSeries());
            ps.setInt(8, t.getPhaseNumber());
            ps.setInt(9, t.getMaxTeamsPerGroup());
            ps.setInt(10, t.getAdvancingSeatsCount());
            ps.setString(11, t.getLinkedQualifierTournamentId());
            ps.setString(12, t.getStatus() != null ? t.getStatus() : "DRAFT");

            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    public boolean saveOrUpdateStageFormat(String tournamentId, String format) {
        if (tournamentId == null || format == null)
            return false;
        String cleanFmt = format.trim().toUpperCase();
        if (!cleanFmt.equals("SINGLE_ELIMINATION") && !cleanFmt.equals("DOUBLE_ELIMINATION") &&
                !cleanFmt.equals("ROUND_ROBIN") && !cleanFmt.equals("SWISS_LITE") && !cleanFmt.equals("GROUP_STAGE")) {
            cleanFmt = "SINGLE_ELIMINATION";
        }

        String checkSql = "SELECT id FROM tournament_stages WHERE tournament_id = ? AND stage_order = 1";
        String updateSql = "UPDATE tournament_stages SET format = ? WHERE tournament_id = ? AND stage_order = 1";
        String insertSql = "INSERT INTO tournament_stages (id, tournament_id, stage_order, stage_name, format, status) VALUES (?, ?, 1, ?, ?, 'PENDING')";
        DBContext db = new DBContext();

        try (Connection conn = db.getConnection()) {
            boolean exists = false;
            try (PreparedStatement psCheck = conn.prepareStatement(checkSql)) {
                psCheck.setString(1, tournamentId);
                try (ResultSet rs = psCheck.executeQuery()) {
                    if (rs.next())
                        exists = true;
                }
            }

            if (exists) {
                try (PreparedStatement psUpdate = conn.prepareStatement(updateSql)) {
                    psUpdate.setString(1, cleanFmt);
                    psUpdate.setString(2, tournamentId);
                    return psUpdate.executeUpdate() > 0;
                }
            } else {
                try (PreparedStatement psInsert = conn.prepareStatement(insertSql)) {
                    String stageId = "STG_" + java.util.UUID.randomUUID().toString().substring(0, 8);
                    psInsert.setString(1, stageId);
                    psInsert.setString(2, tournamentId);
                    psInsert.setString(3, "Stage 1: Main");
                    psInsert.setString(4, cleanFmt);
                    return psInsert.executeUpdate() > 0;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * Updates the advancingSeatsCount field of a tournament.
     * Used when format configuration is saved to persist cut target to DB.
     */
    public boolean updateAdvancingSeatsCount(String tournamentId, int advancingSeatsCount) {
        if (tournamentId == null || advancingSeatsCount < 1)
            return false;
        String sql = "UPDATE tournaments SET advancing_seats_count = ? WHERE id = ?";
        DBContext db = new DBContext();
        try (Connection conn = db.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, advancingSeatsCount);
            ps.setString(2, tournamentId);
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * Updates tournament_type field (SINGLE_STAGE or MULTI_STAGE).
     */
    public boolean updateTournamentType(String tournamentId, String tournamentType) {
        if (tournamentId == null || tournamentType == null)
            return false;
        if (!tournamentType.equals("SINGLE_STAGE") && !tournamentType.equals("MULTI_STAGE"))
            return false;
        String sql = "UPDATE tournaments SET tournament_type = ? WHERE id = ?";
        DBContext db = new DBContext();
        try (Connection conn = db.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tournamentType);
            ps.setString(2, tournamentId);
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    public boolean deleteTournament(String id) {
        String sqlStages = "DELETE FROM tournament_stages WHERE tournament_id = ?";
        String sqlMatches = "DELETE FROM matches WHERE tournament_id = ?";
        String sqlTeams = "DELETE FROM teams WHERE tournament_id = ?";
        String sqlTourney = "DELETE FROM tournaments WHERE id = ?";
        DBContext db = new DBContext();

        try (Connection conn = db.getConnection()) {
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement ps = conn.prepareStatement(sqlStages)) {
                    ps.setString(1, id);
                    ps.executeUpdate();
                } catch (Exception ignore) {
                }

                try (PreparedStatement ps = conn.prepareStatement(sqlMatches)) {
                    ps.setString(1, id);
                    ps.executeUpdate();
                } catch (Exception ignore) {
                }

                try (PreparedStatement ps = conn.prepareStatement(sqlTeams)) {
                    ps.setString(1, id);
                    ps.executeUpdate();
                } catch (Exception ignore) {
                }

                try (PreparedStatement ps = conn.prepareStatement(sqlTourney)) {
                    ps.setString(1, id);
                    int rows = ps.executeUpdate();
                    conn.commit();
                    return rows > 0;
                }
            } catch (Exception ex) {
                conn.rollback();
                throw ex;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    public boolean createSubTournamentInSeries(Tournament t) {
        if (t == null || t.getId() == null || t.getName() == null) return false;
        String sql = "INSERT INTO tournaments (id, series_id, name, tournament_type, tier_name, tournament_index_in_series, phase_number, status) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        DBContext db = new DBContext();
        
        String tier = t.getTierName();
        if (tier != null) {
            tier = tier.replace("Tier ", "").trim();
        }
        if (tier == null || (!tier.equals("S") && !tier.equals("A") && !tier.equals("B") && !tier.equals("C") && !tier.equals("D"))) {
            tier = "S";
        }

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, t.getId());
            ps.setString(2, t.getSeriesId());
            ps.setString(3, t.getName());
            ps.setString(4, t.getTournamentType() != null ? t.getTournamentType() : "SINGLE_STAGE");
            ps.setString(5, tier);
            ps.setInt(6, t.getTournamentIndexInSeries() > 0 ? t.getTournamentIndexInSeries() : 1);
            ps.setInt(7, t.getPhaseNumber() > 0 ? t.getPhaseNumber() : 1);
            ps.setString(8, t.getStatus() != null ? t.getStatus() : "DRAFT");
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public boolean saveTournamentPointsConfig(String tournamentId, int rewardPoints, String pointsConfigJson) {
        if (tournamentId == null || tournamentId.trim().isEmpty()) return false;
        String sql = "UPDATE tournaments SET series_reward_points = ?, series_points_config = ? WHERE id = ?";
        DBContext db = new DBContext();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, rewardPoints);
            ps.setString(2, pointsConfigJson);
            ps.setString(3, tournamentId.trim());
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    public boolean updateTournamentFormatAndType(String tournamentId, String format, String tournamentType, String stage1Format, String stage2Format, int advancingSeatsCount) {
        if (tournamentId == null || tournamentId.trim().isEmpty()) return false;
        if (format == null || format.trim().isEmpty()) format = "SINGLE_ELIMINATION";
        if (tournamentType == null || tournamentType.trim().isEmpty()) tournamentType = "SINGLE_STAGE";

        String sqlTourney = (advancingSeatsCount > 0)
            ? "UPDATE tournaments SET tournament_type = ?, advancing_seats_count = ? WHERE id = ?"
            : "UPDATE tournaments SET tournament_type = ? WHERE id = ?";

        DBContext db = new DBContext();
        try (Connection conn = db.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(sqlTourney)) {
                if (advancingSeatsCount > 0) {
                    ps.setString(1, tournamentType.trim().toUpperCase());
                    ps.setInt(2, advancingSeatsCount);
                    ps.setString(3, tournamentId.trim());
                } else {
                    ps.setString(1, tournamentType.trim().toUpperCase());
                    ps.setString(2, tournamentId.trim());
                }
                ps.executeUpdate();
            }

            String sqlDeleteStages = "DELETE FROM tournament_stages WHERE tournament_id = ?";
            try (PreparedStatement ps = conn.prepareStatement(sqlDeleteStages)) {
                ps.setString(1, tournamentId.trim());
                ps.executeUpdate();
            }

            if ("MULTI_STAGE".equalsIgnoreCase(tournamentType)) {
                String s1 = (stage1Format != null && !stage1Format.trim().isEmpty()) ? stage1Format.trim().toUpperCase() : "GROUP_STAGE";
                String s2 = (stage2Format != null && !stage2Format.trim().isEmpty()) ? stage2Format.trim().toUpperCase() : "SINGLE_ELIMINATION";

                String sqlInsert1 = "INSERT INTO tournament_stages (id, tournament_id, stage_order, stage_name, format) VALUES (?, ?, 1, N'Stage 1', ?)";
                try (PreparedStatement ps = conn.prepareStatement(sqlInsert1)) {
                    ps.setString(1, "STAGE_1_" + System.currentTimeMillis());
                    ps.setString(2, tournamentId.trim());
                    ps.setString(3, s1);
                    ps.executeUpdate();
                }

                String sqlInsert2 = "INSERT INTO tournament_stages (id, tournament_id, stage_order, stage_name, format) VALUES (?, ?, 2, N'Stage 2', ?)";
                try (PreparedStatement ps = conn.prepareStatement(sqlInsert2)) {
                    ps.setString(1, "STAGE_2_" + System.currentTimeMillis());
                    ps.setString(2, tournamentId.trim());
                    ps.setString(3, s2);
                    ps.executeUpdate();
                }
            } else {
                String sqlInsertStage = "INSERT INTO tournament_stages (id, tournament_id, stage_order, stage_name, format) VALUES (?, ?, 1, N'Main Stage', ?)";
                try (PreparedStatement ps = conn.prepareStatement(sqlInsertStage)) {
                    ps.setString(1, "STAGE_" + System.currentTimeMillis());
                    ps.setString(2, tournamentId.trim());
                    ps.setString(3, format.trim().toUpperCase());
                    ps.executeUpdate();
                }
            }
            conn.commit();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    public boolean updateTournamentFormatAndType(String tournamentId, String format, String tournamentType, String stage1Format, String stage2Format) {
        return updateTournamentFormatAndType(tournamentId, format, tournamentType, stage1Format, stage2Format, 0);
    }

    public boolean updateTournamentFormatAndType(String tournamentId, String format, String tournamentType) {
        return updateTournamentFormatAndType(tournamentId, format, tournamentType, null, null, 0);
    }

    public List<String> getStageFormats(String tournamentId) {
        List<String> list = new ArrayList<>();
        if (tournamentId == null || tournamentId.trim().isEmpty()) return list;
        String sql = "SELECT format FROM tournament_stages WHERE tournament_id = ? ORDER BY stage_order ASC";
        DBContext db = new DBContext();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tournamentId.trim());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String f = rs.getString("format");
                    if (f != null) list.add(f.trim().toUpperCase());
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    public boolean saveGroupAssignments(String tournamentId, String groupAssignmentsJson) {
        if (tournamentId == null || tournamentId.trim().isEmpty()) return false;
        String sql = "UPDATE tournaments SET group_assignments = ? WHERE id = ?";
        DBContext db = new DBContext();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, groupAssignmentsJson);
            ps.setString(2, tournamentId.trim());
            boolean ok = ps.executeUpdate() > 0;
            if (ok) {
                try {
                    new dao.GroupStageDAO().syncGroupsAndGroupTeams(tournamentId.trim(), 1, groupAssignmentsJson);
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
            return ok;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    public String getSeriesIdByTournamentId(String tournamentId) {
        if (tournamentId == null || tournamentId.trim().isEmpty()) return null;
        String sql = "SELECT series_id FROM tournaments WHERE id = ?";
        DBContext db = new DBContext();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tournamentId.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("series_id");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public String getGroupAssignments(String tournamentId) {
        if (tournamentId == null || tournamentId.trim().isEmpty()) return null;
        String sql = "SELECT group_assignments FROM tournaments WHERE id = ?";
        DBContext db = new DBContext();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tournamentId.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("group_assignments");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public boolean saveStage2Teams(String tournamentId, String stage2TeamsJson) {
        if (tournamentId == null || tournamentId.trim().isEmpty()) return false;
        String sql = "UPDATE tournaments SET stage2_teams = ? WHERE id = ?";
        DBContext db = new DBContext();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, stage2TeamsJson);
            ps.setString(2, tournamentId.trim());
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    public String getStage2Teams(String tournamentId) {
        if (tournamentId == null || tournamentId.trim().isEmpty()) return null;
        String sql = "SELECT stage2_teams FROM tournaments WHERE id = ?";
        DBContext db = new DBContext();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tournamentId.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("stage2_teams");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public boolean saveMultiStageConfig(String tournamentId, String multiStageConfigJson) {
        if (tournamentId == null || tournamentId.trim().isEmpty()) return false;
        String sql = "UPDATE tournaments SET multi_stage_config = ? WHERE id = ?";
        DBContext db = new DBContext();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, multiStageConfigJson);
            ps.setString(2, tournamentId.trim());
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    public String getMultiStageConfig(String tournamentId) {
        if (tournamentId == null || tournamentId.trim().isEmpty()) return null;
        String sql = "SELECT multi_stage_config FROM tournaments WHERE id = ?";
        DBContext db = new DBContext();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tournamentId.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("multi_stage_config");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}
