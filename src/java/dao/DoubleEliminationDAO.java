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
 * Dedicated DAO for Double Elimination Tournament Format Operations.
 * Manages Upper Bracket, Lower Bracket, Grand Finals, and Drop-down linkages.
 */
public class DoubleEliminationDAO extends DBContext {

    /**
     * Fetch all Double Elimination matches for a tournament, grouped by Bracket Type / Round
     * @param tournamentId ID of the tournament
     * @return Map containing "upper", "lower", and "grandFinal" lists
     */
    public Map<String, Object> getDoubleEliminationData(int tournamentId) {
        Map<String, Object> dataMap = new HashMap<>();
        List<Match> matchList = getMatchesByTournamentId(tournamentId);

        Map<Integer, List<Match>> upperMap = new HashMap<>();
        Map<Integer, List<Match>> lowerMap = new HashMap<>();
        List<Match> grandFinals = new ArrayList<>();

        if (matchList != null) {
            for (Match m : matchList) {
                String bracketType = m.getBracketType(); // UPPER, LOWER, GRAND_FINAL
                if ("LOWER".equalsIgnoreCase(bracketType)) {
                    lowerMap.computeIfAbsent(m.getRoundNumber(), k -> new ArrayList<>()).add(m);
                } else if ("GRAND_FINAL".equalsIgnoreCase(bracketType) || "GRAND_FINALS".equalsIgnoreCase(bracketType)) {
                    grandFinals.add(m);
                } else {
                    upperMap.computeIfAbsent(m.getRoundNumber(), k -> new ArrayList<>()).add(m);
                }
            }
        }

        dataMap.put("upperMap", upperMap);
        dataMap.put("lowerMap", lowerMap);
        dataMap.put("grandFinals", grandFinals);
        return dataMap;
    }

    /**
     * Query matches from database table
     */
    public List<Match> getMatchesByTournamentId(int tournamentId) {
        List<Match> list = new ArrayList<>();
        String sql = "SELECT * FROM matches WHERE tournament_id = ? ORDER BY round_number ASC, match_number ASC";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, tournamentId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Match m = new Match();
                    m.setId(rs.getInt("id"));
                    m.setTournamentId(rs.getInt("tournament_id"));
                    m.setRoundNumber(rs.getInt("round_number"));
                    m.setMatchNumber(rs.getInt("match_number"));
                    
                    int t1Id = rs.getInt("team1_id");
                    if (!rs.wasNull()) m.setTeam1Id(t1Id);
                    m.setTeam1Name(rs.getString("team1_name"));
                    int t1Seed = rs.getInt("team1_seed");
                    if (!rs.wasNull()) m.setTeam1Seed(t1Seed);
                    int t1Score = rs.getInt("team1_score");
                    if (!rs.wasNull()) m.setTeam1Score(t1Score);

                    int t2Id = rs.getInt("team2_id");
                    if (!rs.wasNull()) m.setTeam2Id(t2Id);
                    m.setTeam2Name(rs.getString("team2_name"));
                    int t2Seed = rs.getInt("team2_seed");
                    if (!rs.wasNull()) m.setTeam2Seed(t2Seed);
                    int t2Score = rs.getInt("team2_score");
                    if (!rs.wasNull()) m.setTeam2Score(t2Score);

                    int winnerId = rs.getInt("winner_team_id");
                    if (!rs.wasNull()) m.setWinnerTeamId(winnerId);

                    int nextMatchId = rs.getInt("next_match_id");
                    if (!rs.wasNull()) m.setNextMatchId(nextMatchId);
                    m.setNextMatchSlot(rs.getInt("next_match_slot"));

                    m.setStatus(rs.getString("status"));
                    list.add(m);
                }
            }
        } catch (Exception e) {
            // DB fallback
        }
        return list;
    }

    /**
     * Update match score in database
     */
    public boolean updateMatchScore(int matchId, int score1, int score2, String winnerFlag) {
        String updateSql = "UPDATE matches SET team1_score = ?, team2_score = ?, status = 'COMPLETED' WHERE id = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(updateSql)) {
            ps.setInt(1, score1);
            ps.setInt(2, score2);
            ps.setInt(3, matchId);
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            return false;
        }
    }
}
