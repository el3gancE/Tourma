package dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import model.Series;

/**
 * Data Access Object for Series table
 */
public class SeriesDAO {

    public List<Series> getAllSeries() {
        List<Series> list = new ArrayList<>();
        String sql = "SELECT * FROM series ORDER BY created_at DESC";
        DBContext db = new DBContext();
        
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            
            while (rs.next()) {
                Series s = new Series(
                    rs.getString("id"),
                    rs.getString("name"),
                    rs.getString("ranking_model"),
                    rs.getInt("phase_size"),
                    rs.getInt("current_phase"),
                    rs.getInt("initial_points"),
                    rs.getDouble("initial_elo"),
                    rs.getString("status"),
                    rs.getTimestamp("created_at")
                );
                list.add(s);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    public Series getSeriesById(String id) {
        String sql = "SELECT * FROM series WHERE id = ?";
        DBContext db = new DBContext();
        
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new Series(
                        rs.getString("id"),
                        rs.getString("name"),
                        rs.getString("ranking_model"),
                        rs.getInt("phase_size"),
                        rs.getInt("current_phase"),
                        rs.getInt("initial_points"),
                        rs.getDouble("initial_elo"),
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

    public boolean insertSeries(Series s) {
        String sql = "INSERT INTO series (id, name, ranking_model, phase_size, current_phase, initial_points, initial_elo, status) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        DBContext db = new DBContext();
        
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setString(1, s.getId());
            ps.setString(2, s.getName());
            ps.setString(3, s.getRankingModel());
            ps.setInt(4, s.getPhaseSize());
            ps.setInt(5, s.getCurrentPhase() > 0 ? s.getCurrentPhase() : 1);
            ps.setInt(6, s.getInitialPoints());
            ps.setDouble(7, s.getInitialElo());
            ps.setString(8, s.getStatus() != null ? s.getStatus() : "ACTIVE");
            
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }
}
