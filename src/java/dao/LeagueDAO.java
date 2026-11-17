package dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import model.Season;
import model.SeriesDivision;
import model.Tournament;
import model.SeriesStanding;

public class LeagueDAO {

    public List<SeriesDivision> getDivisionsBySeriesId(String seriesId) {
        List<SeriesDivision> list = new ArrayList<>();
        if (seriesId == null || seriesId.trim().isEmpty()) return list;
        String sql = "SELECT * FROM series_divisions WHERE series_id = ? ORDER BY division_level ASC";
        DBContext db = new DBContext();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, seriesId.trim());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    SeriesDivision d = new SeriesDivision(
                        rs.getString("id"),
                        rs.getString("series_id"),
                        rs.getInt("division_level"),
                        rs.getString("name"),
                        rs.getInt("team_quota"),
                        rs.getInt("promotion_slots"),
                        rs.getInt("relegation_slots"),
                        rs.getTimestamp("created_at")
                    );
                    list.add(d);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    public List<Season> getSeasonsBySeriesId(String seriesId) {
        List<Season> list = new ArrayList<>();
        if (seriesId == null || seriesId.trim().isEmpty()) return list;
        String sql = "SELECT * FROM seasons WHERE series_id = ? ORDER BY season_number DESC";
        DBContext db = new DBContext();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, seriesId.trim());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Season s = new Season(
                        rs.getString("id"),
                        rs.getString("series_id"),
                        rs.getInt("season_number"),
                        rs.getString("name"),
                        rs.getString("status"),
                        rs.getBoolean("has_special_cup"),
                        rs.getString("special_cup_tournament_id"),
                        rs.getTimestamp("created_at"),
                        rs.getTimestamp("completed_at")
                    );
                    list.add(s);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    public Season getSeasonById(String seasonId) {
        if (seasonId == null || seasonId.trim().isEmpty()) return null;
        String sql = "SELECT * FROM seasons WHERE id = ?";
        DBContext db = new DBContext();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, seasonId.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new Season(
                        rs.getString("id"),
                        rs.getString("series_id"),
                        rs.getInt("season_number"),
                        rs.getString("name"),
                        rs.getString("status"),
                        rs.getBoolean("has_special_cup"),
                        rs.getString("special_cup_tournament_id"),
                        rs.getTimestamp("created_at"),
                        rs.getTimestamp("completed_at")
                    );
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public List<Tournament> getTournamentsBySeasonId(String seasonId) {
        List<Tournament> list = new ArrayList<>();
        if (seasonId == null || seasonId.trim().isEmpty()) return list;
        String sql = "SELECT t.*, (SELECT COUNT(1) FROM teams WHERE tournament_id = t.id) AS team_count " +
                     "FROM tournaments t WHERE t.season_id = ? ORDER BY t.division_level ASC, t.created_at ASC";
        DBContext db = new DBContext();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, seasonId.trim());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Tournament t = new Tournament();
                    t.setId(rs.getString("id"));
                    t.setSeriesId(rs.getString("series_id"));
                    t.setName(rs.getString("name"));
                    t.setTournamentType(rs.getString("tournament_type"));
                    t.setDivisionLevel(rs.getInt("division_level"));
                    t.setSeasonNumber(rs.getInt("season_number"));
                    t.setDivisionName(rs.getString("division_name"));
                    t.setStatus(rs.getString("status"));
                    t.setChampionName(rs.getString("champion_name"));
                    t.setTeamCount(rs.getInt("team_count"));
                    t.setCreatedAt(rs.getTimestamp("created_at"));
                    list.add(t);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    public List<SeriesStanding> getDivisionStandings(String seriesId, int seasonNumber, int divisionLevel) {
        List<SeriesStanding> list = new ArrayList<>();
        String sql = "SELECT * FROM series_standings WHERE series_id = ? AND season_number = ? AND division_level = ? ORDER BY points DESC, goal_diff DESC, goals_for DESC";
        DBContext db = new DBContext();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, seriesId);
            ps.setInt(2, seasonNumber);
            ps.setInt(3, divisionLevel);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    SeriesStanding st = new SeriesStanding();
                    st.setId(rs.getString("id"));
                    st.setSeriesId(rs.getString("series_id"));
                    st.setSeasonNumber(rs.getInt("season_number"));
                    st.setDivisionLevel(rs.getInt("division_level"));
                    st.setNormalizedTeamName(rs.getString("normalized_team_name"));
                    st.setMatchesPlayed(rs.getInt("matches_played"));
                    st.setWins(rs.getInt("wins"));
                    st.setDraws(rs.getInt("draws"));
                    st.setLosses(rs.getInt("losses"));
                    st.setGoalsFor(rs.getInt("goals_for"));
                    st.setGoalsAgainst(rs.getInt("goals_against"));
                    st.setGoalDiff(rs.getInt("goal_diff"));
                    st.setTotalRollingPoints(rs.getInt("points"));
                    st.setPromotionStatus(rs.getString("promotion_status"));
                    st.setRankOverall(rs.getInt("rank_overall"));
                    list.add(st);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }
}
