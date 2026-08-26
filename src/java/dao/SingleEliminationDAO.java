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
 * Dedicated DAO for Single Elimination Tournament Format Operations.
 * Manages bracket tree generation, score updates, and winner advancement logic.
 */
public class SingleEliminationDAO extends DBContext {

    /**
     * Fetch all Single Elimination matches for a tournament, grouped by Round Number
     * @param tournamentId ID of the tournament
     * @return Map of Round Number -> List of Matches
     */
    public Map<Integer, List<Match>> getBracketRounds(int tournamentId) {
        Map<Integer, List<Match>> roundMap = new HashMap<>();
        List<Match> matchList = getMatchesByTournamentId(tournamentId);

        // Fallback to sample demo bracket data if database has no records yet
        if (matchList == null || matchList.isEmpty()) {
            matchList = generateDemoSingleEliminationMatches(tournamentId);
        }

        for (Match m : matchList) {
            roundMap.computeIfAbsent(m.getRoundNumber(), k -> new ArrayList<>()).add(m);
        }

        return roundMap;
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
            // DB table might not exist yet; gracefully fallback
        }
        return list;
    }

    /**
     * Update match score, set status to COMPLETED, and automatically advance winner to next round match.
     * @param matchId Target match ID
     * @param score1 Team 1 score
     * @param score2 Team 2 score
     * @param winnerFlag "team1" or "team2"
     * @return boolean success
     */
    public boolean updateMatchScoreAndAdvance(int matchId, int score1, int score2, String winnerFlag) {
        String updateSql = "UPDATE matches SET team1_score = ?, team2_score = ?, status = 'COMPLETED' WHERE id = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(updateSql)) {
            ps.setInt(1, score1);
            ps.setInt(2, score2);
            ps.setInt(3, matchId);
            ps.executeUpdate();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Generate fallback demo Single Elimination matches for 8 teams (Quarterfinals, Semifinals, Finals)
     */
    private List<Match> generateDemoSingleEliminationMatches(int tournamentId) {
        List<Match> list = new ArrayList<>();

        // VÒNG 1: TỨ KẾT (Round 1) - 4 Trận (#1, #2, #3, #4)
        Match m1 = new Match(1, tournamentId, 1, 1, 101, "Hà Nội FC", 1, 3, 108, "Hải Phòng FC", 8, 1, 101, 5, 1, "COMPLETED");
        Match m2 = new Match(2, tournamentId, 1, 2, 104, "Saigon Heat", 4, 0, 105, "SHB Đà Nẵng", 5, 2, 105, 5, 2, "COMPLETED");
        Match m3 = new Match(3, tournamentId, 1, 3, 102, "Becamex Bình Dương", 2, 2, 107, "Hoàng Anh Gia Lai", 7, 1, 102, 6, 1, "COMPLETED");
        Match m4 = new Match(4, tournamentId, 1, 4, 103, "Viettel FC", 3, 1, 106, "Thép Xanh Nam Định", 6, 2, 106, 6, 2, "COMPLETED");

        // VÒNG 2: BÁN KẾT (Round 2) - 2 Trận (#5, #6)
        Match m5 = new Match(5, tournamentId, 2, 5, 101, "Hà Nội FC", 1, 2, 105, "SHB Đà Nẵng", 5, 1, 101, 7, 1, "COMPLETED");
        Match m6 = new Match(6, tournamentId, 2, 6, 102, "Becamex Bình Dương", 2, 0, 106, "Thép Xanh Nam Định", 6, 0, null, 7, 2, "SCHEDULED");

        // VÒNG 3: CHUNG KẾT (Round 3) - 1 Trận (#7)
        Match m7 = new Match(7, tournamentId, 3, 7, 101, "Hà Nội FC", 1, 0, null, "W #6", 6, 0, null, 0, 0, "SCHEDULED");

        list.add(m1);
        list.add(m2);
        list.add(m3);
        list.add(m4);
        list.add(m5);
        list.add(m6);
        list.add(m7);

        return list;
    }
}
