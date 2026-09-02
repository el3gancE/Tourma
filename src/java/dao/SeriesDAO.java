package dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import model.Series;
import model.Tournament;

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
                        rs.getTimestamp("created_at"));
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
                            rs.getTimestamp("created_at"));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public boolean insertSeries(Series s) {
        String sql = "INSERT INTO series (id, name, ranking_model, phase_size, current_phase, initial_points, initial_elo, status) "
                +
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

    public List<model.SeriesStanding> getStandingsBySeriesId(String seriesId) {
        List<model.SeriesStanding> list = new ArrayList<>();
        if (seriesId == null || seriesId.trim().isEmpty()) return list;
        String sql = "SELECT * FROM series_standings WHERE series_id = ? ORDER BY total_rolling_points DESC, rank_overall ASC";
        DBContext db = new DBContext();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, seriesId);
            try (ResultSet rs = ps.executeQuery()) {
                int r = 1;
                while (rs.next()) {
                    model.SeriesStanding st = new model.SeriesStanding(
                        rs.getString("id"),
                        rs.getString("series_id"),
                        rs.getInt("phase_number"),
                        rs.getString("normalized_team_name"),
                        rs.getInt("total_rolling_points"),
                        r++,
                        rs.getTimestamp("updated_at")
                    );
                    list.add(st);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    public List<model.PartnerParticipant> getPartnerParticipantsBySeriesId(String seriesId) {
        List<model.PartnerParticipant> list = new ArrayList<>();
        if (seriesId == null || seriesId.trim().isEmpty()) return list;
        String sql = "SELECT * FROM partner_participants WHERE series_id = ? ORDER BY created_at ASC";
        DBContext db = new DBContext();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, seriesId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    model.PartnerParticipant p = new model.PartnerParticipant(
                        rs.getString("id"),
                        rs.getString("series_id"),
                        rs.getString("name"),
                        rs.getString("group_name"),
                        rs.getTimestamp("created_at")
                    );
                    list.add(p);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    public boolean addPartnerParticipant(String seriesId, String teamName, String customPartnerId, int initialPoints) {
        if (seriesId == null || teamName == null || teamName.trim().isEmpty()) return false;
        String partnerId = (customPartnerId != null && !customPartnerId.trim().isEmpty()) ? customPartnerId.trim() : ("PARTNER_" + System.currentTimeMillis() + "_" + (int)(Math.random()*1000));
        String standingId = "ST_" + System.currentTimeMillis() + "_" + (int)(Math.random()*1000);
        
        DBContext db = new DBContext();
        String sqlPartner = "INSERT INTO partner_participants (id, series_id, name, group_name) VALUES (?, ?, ?, ?)";
        String sqlStanding = "INSERT INTO series_standings (id, series_id, phase_number, normalized_team_name, partner_participant_id, total_rolling_points) VALUES (?, ?, 1, ?, ?, ?)";

        try (Connection conn = db.getConnection()) {
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement psP = conn.prepareStatement(sqlPartner)) {
                    psP.setString(1, partnerId);
                    psP.setString(2, seriesId);
                    psP.setString(3, teamName.trim());
                    psP.setString(4, "General");
                    psP.executeUpdate();
                }

                try (PreparedStatement psS = conn.prepareStatement(sqlStanding)) {
                    psS.setString(1, standingId);
                    psS.setString(2, seriesId);
                    psS.setString(3, teamName.trim());
                    psS.setString(4, partnerId);
                    psS.setInt(5, initialPoints);
                    psS.executeUpdate();
                }

                conn.commit();
                return true;
            } catch (Exception e) {
                conn.rollback();
                e.printStackTrace();
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    public int bulkAddPartnerParticipants(String seriesId, List<String> teamNames, int initialPoints) {
        if (seriesId == null || teamNames == null || teamNames.isEmpty()) return 0;
        int count = 0;
        DBContext db = new DBContext();
        String sqlPartner = "INSERT INTO partner_participants (id, series_id, name, group_name) VALUES (?, ?, ?, ?)";
        String sqlStanding = "INSERT INTO series_standings (id, series_id, phase_number, normalized_team_name, partner_participant_id, total_rolling_points) VALUES (?, ?, 1, ?, ?, ?)";

        try (Connection conn = db.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement psP = conn.prepareStatement(sqlPartner);
                 PreparedStatement psS = conn.prepareStatement(sqlStanding)) {
                
                long now = System.currentTimeMillis();
                int idx = 0;
                for (String rawName : teamNames) {
                    if (rawName == null || rawName.trim().isEmpty()) continue;
                    String tName = rawName.trim();
                    idx++;
                    String partnerId = "PARTNER_" + now + "_" + idx;
                    String standingId = "ST_" + now + "_" + idx;

                    psP.setString(1, partnerId);
                    psP.setString(2, seriesId);
                    psP.setString(3, tName);
                    psP.setString(4, "General");
                    psP.addBatch();

                    psS.setString(1, standingId);
                    psS.setString(2, seriesId);
                    psS.setString(3, tName);
                    psS.setString(4, partnerId);
                    psS.setInt(5, initialPoints);
                    psS.addBatch();

                    count++;
                }

                psP.executeBatch();
                psS.executeBatch();
                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                e.printStackTrace();
                count = 0;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return count;
    }

    public boolean deletePartnerParticipant(String partnerId, String seriesId) {
        if (partnerId == null || partnerId.trim().isEmpty()) return false;
        DBContext db = new DBContext();
        String sqlStanding = "DELETE FROM series_standings WHERE partner_participant_id = ?";
        String sqlPartner = "DELETE FROM partner_participants WHERE id = ?";
        try (Connection conn = db.getConnection()) {
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement psS = conn.prepareStatement(sqlStanding)) {
                    psS.setString(1, partnerId);
                    psS.executeUpdate();
                }
                try (PreparedStatement psP = conn.prepareStatement(sqlPartner)) {
                    psP.setString(1, partnerId);
                    psP.executeUpdate();
                }
                conn.commit();
                return true;
            } catch (Exception e) {
                conn.rollback();
                e.printStackTrace();
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    public boolean recalculateSeriesStandings(String seriesId) {
        return service.RollingWindowPointService.getInstance().recalculateAndPersistStandings(seriesId);
    }
}
