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
                     "(SELECT TOP 1 format FROM tournament_stages WHERE tournament_id = t.id ORDER BY stage_order ASC) AS stage_format, " +
                     "(SELECT TOP 1 tm.raw_name FROM matches m JOIN teams tm ON m.winner_id = tm.id WHERE m.tournament_id = t.id AND m.winner_id IS NOT NULL ORDER BY m.round_number DESC) AS db_champion_name " +
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
                    rs.getTimestamp("created_at")
                );
                try {
                    String fmt = rs.getString("stage_format");
                    if (fmt != null && !fmt.trim().isEmpty()) {
                        t.setFormat(fmt.trim());
                    }
                } catch (Exception ignore) {}
                try {
                    String champ = rs.getString("db_champion_name");
                    if (champ != null && !champ.trim().isEmpty()) {
                        t.setChampionName(champ.trim());
                    }
                } catch (Exception ignore) {}
                list.add(t);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    public Tournament getTournamentById(String id) {
        String sql = "SELECT t.*, " +
                     "(SELECT TOP 1 format FROM tournament_stages WHERE tournament_id = t.id ORDER BY stage_order ASC) AS stage_format, " +
                     "(SELECT TOP 1 tm.raw_name FROM matches m JOIN teams tm ON m.winner_id = tm.id WHERE m.tournament_id = t.id AND m.winner_id IS NOT NULL ORDER BY m.round_number DESC) AS db_champion_name " +
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
                        rs.getTimestamp("created_at")
                    );
                    try {
                        String fmt = rs.getString("stage_format");
                        if (fmt != null && !fmt.trim().isEmpty()) {
                            t.setFormat(fmt.trim());
                        }
                    } catch (Exception ignore) {}
                    try {
                        String champ = rs.getString("db_champion_name");
                        if (champ != null && !champ.trim().isEmpty()) {
                            t.setChampionName(champ.trim());
                        }
                    } catch (Exception ignore) {}
                    return t;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
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
        if (tournamentId == null || format == null) return false;
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
                    if (rs.next()) exists = true;
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
                } catch (Exception ignore) {}

                try (PreparedStatement ps = conn.prepareStatement(sqlMatches)) {
                    ps.setString(1, id);
                    ps.executeUpdate();
                } catch (Exception ignore) {}

                try (PreparedStatement ps = conn.prepareStatement(sqlTeams)) {
                    ps.setString(1, id);
                    ps.executeUpdate();
                } catch (Exception ignore) {}

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
}
