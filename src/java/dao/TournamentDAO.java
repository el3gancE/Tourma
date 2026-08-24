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
        String sql = "SELECT * FROM tournaments ORDER BY created_at DESC";
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
                list.add(t);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    public Tournament getTournamentById(String id) {
        String sql = "SELECT * FROM tournaments WHERE id = ?";
        DBContext db = new DBContext();
        
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new Tournament(
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
}
