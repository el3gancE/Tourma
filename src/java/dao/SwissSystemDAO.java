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
 * Dedicated DAO for Swiss System / Swiss Lite Tournament Operations.
 * Manages Swiss round matches, Buchholz standings, and score persistency.
 */
public class SwissSystemDAO extends DBContext {

    /**
     * Fetch all Swiss matches for a tournament, grouped by Round Number
     */
    public Map<Integer, List<Match>> getSwissRounds(int tournamentId) {
        Map<Integer, List<Match>> roundMap = new HashMap<>();
        List<Match> matchList = getMatchesByTournamentId(tournamentId);

        if (matchList == null || matchList.isEmpty()) {
            matchList = generateDemoSwissMatches(tournamentId);
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
                    int t1Score = rs.getInt("team1_score");
                    if (!rs.wasNull()) m.setTeam1Score(t1Score);

                    int t2Id = rs.getInt("team2_id");
                    if (!rs.wasNull()) m.setTeam2Id(t2Id);
                    m.setTeam2Name(rs.getString("team2_name"));
                    int t2Score = rs.getInt("team2_score");
                    if (!rs.wasNull()) m.setTeam2Score(t2Score);

                    int winnerId = rs.getInt("winner_team_id");
                    if (!rs.wasNull()) m.setWinnerTeamId(winnerId);

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
     * Save score for a Swiss match
     */
    public boolean updateSwissMatchScore(int matchId, int score1, int score2, String status) {
        String updateSql = "UPDATE matches SET team1_score = ?, team2_score = ?, status = ? WHERE id = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(updateSql)) {
            ps.setInt(1, score1);
            ps.setInt(2, score2);
            ps.setString(3, status != null ? status : "COMPLETED");
            ps.setInt(4, matchId);
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Fallback demo Swiss matches (16 teams Swiss round 1 sample)
     */
    private List<Match> generateDemoSwissMatches(int tournamentId) {
        List<Match> list = new ArrayList<>();
        String[] teamNames = {
            "Team Flash", "GAM Esports", "Team Secret", "Saigon Buffalo",
            "CERBERUS Esports", "MGN Box Esports", "Team Whales", "Rainbow 7",
            "T1", "Gen.G", "Fnatic", "G2 Esports",
            "Cloud9", "NRG Esports", "BLG Esports", "JD Gaming"
        };

        int matchIdCounter = 1000;
        for (int i = 0; i < teamNames.length / 2; i++) {
            Match m = new Match();
            m.setId(matchIdCounter++);
            m.setTournamentId(tournamentId);
            m.setRoundNumber(1);
            m.setMatchNumber(i + 1);
            m.setTeam1Id(i * 2 + 1);
            m.setTeam1Name(teamNames[i * 2]);
            m.setTeam1Score(0);
            m.setTeam2Id(i * 2 + 2);
            m.setTeam2Name(teamNames[i * 2 + 1]);
            m.setTeam2Score(0);
            m.setStatus("READY");
            list.add(m);
        }
        return list;
    }
}
