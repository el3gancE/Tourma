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
            ps.setString(1, seriesId.trim());
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
                    try {
                        t.setSeriesRewardPoints(rs.getInt("series_reward_points"));
                    } catch (Exception ignore) {}
                    try {
                        t.setSeriesPointsConfig(rs.getString("series_points_config"));
                    } catch (Exception ignore) {}
                    list.add(t);
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
            return false;
        }
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

    public boolean recalculateSeriesStandings(String seriesId) {
        return service.RollingWindowPointService.getInstance().recalculateAndPersistStandings(seriesId);
    }
}
