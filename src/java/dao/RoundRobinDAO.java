package dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import model.Match;

/**
 * Dedicated Data Access Object for Round Robin Tournament Format.
 * Manages fixtures, score updates, and points configuration persistence.
 */
public class RoundRobinDAO extends DBContext {

    /**
     * Fetch all Round Robin matches for a tournament, grouped by Round Number
     */
    public Map<Integer, List<Match>> getRoundsMap(String tournamentId) {
        Map<Integer, List<Match>> roundMap = new HashMap<>();
        List<Match> matchList = getMatchesByTournamentId(tournamentId);

        for (Match m : matchList) {
            roundMap.computeIfAbsent(m.getRoundNumber(), k -> new ArrayList<>()).add(m);
        }
        return roundMap;
    }

    /**
     * Fetch matches from database
     */
    public List<Match> getMatchesByTournamentId(String tournamentId) {
        List<Match> list = new ArrayList<>();
        String sql = "SELECT * FROM matches WHERE tournament_id = ? ORDER BY round_number ASC, match_number ASC";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tournamentId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Match m = new Match();
                    m.setId(rs.getInt("id"));
                    m.setRoundNumber(rs.getInt("round_number"));
                    m.setMatchNumber(rs.getInt("match_number"));
                    m.setTeam1Name(rs.getString("team1_name"));
                    m.setTeam2Name(rs.getString("team2_name"));

                    int t1Score = rs.getInt("team1_score");
                    if (!rs.wasNull()) m.setTeam1Score(t1Score);

                    int t2Score = rs.getInt("team2_score");
                    if (!rs.wasNull()) m.setTeam2Score(t2Score);

                    m.setStatus(rs.getString("status"));
                    list.add(m);
                }
            }
        } catch (Exception ignore) {
            // DB fallback
        }
        return list;
    }

    /**
     * Update match score and status
     */
    public boolean updateMatchScore(String matchId, String team1Score, String team2Score, String winner) {
        String updateSql = "UPDATE matches SET team1_score = ?, team2_score = ?, status = 'COMPLETED' WHERE id = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(updateSql)) {
            ps.setString(1, team1Score);
            ps.setString(2, team2Score);
            ps.setString(3, matchId);
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            return false;
        }
    }
}
