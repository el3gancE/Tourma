package service;

import dao.DBContext;
import model.Team;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * High-Performance, Database-Driven Service for calculating tournament placements (1st, 2nd, Top 4, Top 8...)
 * directly from the 'matches' table in SQL Server.
 */
public class TournamentPlacementService {

    private static TournamentPlacementService instance;

    private TournamentPlacementService() {}

    public static synchronized TournamentPlacementService getInstance() {
        if (instance == null) {
            instance = new TournamentPlacementService();
        }
        return instance;
    }

    private static class MatchInfo {
        String id;
        int stageOrder;
        String stageFormat;
        int roundNumber;
        String bracketType;
        String team1Id;
        String team2Id;
        String t1Name;
        String t2Name;
        String t1Norm;
        String t2Norm;
        String winnerId;
        String winnerName;
        String winnerNorm;
        String loserId;
        String loserName;
        String loserNorm;
        Integer score1;
        Integer score2;
        String status;
    }

    /**
     * Preloads placements for all tournaments in a series in a single bulk query.
     */
    public Map<String, Map<String, Integer>> preloadSeriesPlacements(String seriesId) {
        Map<String, Map<String, Integer>> seriesPlacements = new HashMap<>();
        if (seriesId == null || seriesId.trim().isEmpty()) return seriesPlacements;
        String sid = seriesId.trim();

        DBContext db = new DBContext();
        String sql = "SELECT m.*, ts.stage_order, ts.format AS stage_format, "
                + "t1.raw_name AS t1_name, t1.normalized_name AS t1_norm, "
                + "t2.raw_name AS t2_name, t2.normalized_name AS t2_norm, "
                + "tw.raw_name AS winner_name, tw.normalized_name AS winner_norm, "
                + "tl.raw_name AS loser_name, tl.normalized_name AS loser_norm "
                + "FROM matches m "
                + "LEFT JOIN tournament_stages ts ON m.stage_id = ts.id "
                + "LEFT JOIN teams t1 ON m.team1_id = t1.id "
                + "LEFT JOIN teams t2 ON m.team2_id = t2.id "
                + "LEFT JOIN teams tw ON m.winner_id = tw.id "
                + "LEFT JOIN teams tl ON m.loser_id = tl.id "
                + "WHERE m.tournament_id IN (SELECT id FROM tournaments WHERE series_id = ?) "
                + "ORDER BY m.tournament_id, ISNULL(ts.stage_order, 1) DESC, m.round_number DESC";

        Map<String, List<MatchInfo>> tourneyMatches = new HashMap<>();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, sid);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String tid = rs.getString("tournament_id");
                    if (tid == null) continue;
                    MatchInfo m = parseMatchInfo(rs);
                    tourneyMatches.computeIfAbsent(tid.trim(), k -> new ArrayList<>()).add(m);
                }
            }

            for (Map.Entry<String, List<MatchInfo>> entry : tourneyMatches.entrySet()) {
                String tid = entry.getKey();
                Map<String, Integer> pMap = computePlacements(tid, entry.getValue(), conn);
                seriesPlacements.put(tid, pMap);
                RollingWindowPointService.getInstance().putCachedTournamentPlacements(tid, pMap);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return seriesPlacements;
    }

    /**
     * Calculates placements for a single tournament from DB matches.
     */
    public Map<String, Integer> getTournamentPlacements(String tournamentId) {
        Map<String, Integer> placementMap = new HashMap<>();
        if (tournamentId == null || tournamentId.trim().isEmpty()) return placementMap;
        String tid = tournamentId.trim();

        DBContext db = new DBContext();
        String sql = "SELECT m.*, ts.stage_order, ts.format AS stage_format, "
                + "t1.raw_name AS t1_name, t1.normalized_name AS t1_norm, "
                + "t2.raw_name AS t2_name, t2.normalized_name AS t2_norm, "
                + "tw.raw_name AS winner_name, tw.normalized_name AS winner_norm, "
                + "tl.raw_name AS loser_name, tl.normalized_name AS loser_norm "
                + "FROM matches m "
                + "LEFT JOIN tournament_stages ts ON m.stage_id = ts.id "
                + "LEFT JOIN teams t1 ON m.team1_id = t1.id "
                + "LEFT JOIN teams t2 ON m.team2_id = t2.id "
                + "LEFT JOIN teams tw ON m.winner_id = tw.id "
                + "LEFT JOIN teams tl ON m.loser_id = tl.id "
                + "WHERE m.tournament_id = ? "
                + "ORDER BY ISNULL(ts.stage_order, 1) DESC, m.round_number DESC";

        List<MatchInfo> matches = new ArrayList<>();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tid);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    matches.add(parseMatchInfo(rs));
                }
            }
            placementMap = computePlacements(tid, matches, conn);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return placementMap;
    }

    private MatchInfo parseMatchInfo(ResultSet rs) throws Exception {
        MatchInfo m = new MatchInfo();
        m.id = rs.getString("id");
        m.stageOrder = rs.getInt("stage_order");
        if (rs.wasNull()) m.stageOrder = 1;
        m.stageFormat = rs.getString("stage_format");
        m.roundNumber = rs.getInt("round_number");
        m.bracketType = rs.getString("bracket_type");
        m.team1Id = rs.getString("team1_id");
        m.team2Id = rs.getString("team2_id");
        m.t1Name = rs.getString("t1_name");
        m.t2Name = rs.getString("t2_name");
        m.t1Norm = rs.getString("t1_norm");
        m.t2Norm = rs.getString("t2_norm");
        m.winnerId = rs.getString("winner_id");
        m.winnerName = rs.getString("winner_name");
        m.winnerNorm = rs.getString("winner_norm");
        m.loserId = rs.getString("loser_id");
        m.loserName = rs.getString("loser_name");
        m.loserNorm = rs.getString("loser_norm");
        int s1 = rs.getInt("score1");
        m.score1 = rs.wasNull() ? null : s1;
        int s2 = rs.getInt("score2");
        m.score2 = rs.wasNull() ? null : s2;
        m.status = rs.getString("status");
        return m;
    }

    private Map<String, Integer> computePlacements(String tournamentId, List<MatchInfo> matches, Connection conn) {
        Map<String, Integer> placements = new HashMap<>();
        if (matches == null || matches.isEmpty()) return placements;

        // Group matches by stage
        int maxStage = 1;
        for (MatchInfo m : matches) {
            if (m.stageOrder > maxStage) maxStage = m.stageOrder;
        }

        // Process final stage knockout / round robin
        int maxRound = 0;
        MatchInfo grandFinal = null;
        MatchInfo thirdPlaceMatch = null;
        List<MatchInfo> finalStageMatches = new ArrayList<>();

        for (MatchInfo m : matches) {
            if (m.stageOrder == maxStage) {
                finalStageMatches.add(m);
                if (m.roundNumber > maxRound) maxRound = m.roundNumber;
                if ("GRAND_FINAL".equalsIgnoreCase(m.bracketType) || "GRAND_FINAL_RESET".equalsIgnoreCase(m.bracketType)) {
                    grandFinal = m;
                }
                if ("THIRD_PLACE".equalsIgnoreCase(m.bracketType)) {
                    thirdPlaceMatch = m;
                }
            }
        }

        // 1. Double Elimination handling
        boolean isDE = finalStageMatches.stream().anyMatch(m -> "LOSER_BRACKET".equalsIgnoreCase(m.bracketType));
        if (isDE) {
            computeDoubleEliminationPlacements(finalStageMatches, placements);
        } else {
            // 2. Single Elimination handling
            computeSingleEliminationPlacements(finalStageMatches, maxRound, grandFinal, thirdPlaceMatch, placements);
        }

        // Fill remaining active teams with base placement if not placed
        fillRemainingTeams(tournamentId, placements, conn);

        return placements;
    }

    private void computeSingleEliminationPlacements(List<MatchInfo> matches, int maxRound, MatchInfo grandFinal, MatchInfo thirdPlace, Map<String, Integer> placements) {
        // Find champion and runner-up from the highest round match
        MatchInfo finalMatch = grandFinal;
        if (finalMatch == null) {
            for (MatchInfo m : matches) {
                if (m.roundNumber == maxRound && !"THIRD_PLACE".equalsIgnoreCase(m.bracketType)) {
                    finalMatch = m;
                    break;
                }
            }
        }

        if (finalMatch != null && finalMatch.winnerNorm != null) {
            putPlacement(placements, finalMatch.winnerNorm, 1);
            if (finalMatch.loserNorm != null) {
                putPlacement(placements, finalMatch.loserNorm, 2);
            }
        }

        // 3rd place match if exists
        if (thirdPlace != null && thirdPlace.winnerNorm != null) {
            putPlacement(placements, thirdPlace.winnerNorm, 3);
            if (thirdPlace.loserNorm != null) {
                putPlacement(placements, thirdPlace.loserNorm, 4);
            }
        }

        // Losers of each preceding round
        for (MatchInfo m : matches) {
            if ("THIRD_PLACE".equalsIgnoreCase(m.bracketType)) continue;
            int r = m.roundNumber;
            int roundsFromFinal = maxRound - r;

            int rankForLoser;
            if (roundsFromFinal == 1) {
                rankForLoser = 3; // Semi-final losers (Top 4)
            } else if (roundsFromFinal == 2) {
                rankForLoser = 5; // Quarter-final losers (Top 8)
            } else if (roundsFromFinal == 3) {
                rankForLoser = 9; // Round of 16 losers (Top 16)
            } else {
                rankForLoser = (int) Math.pow(2, roundsFromFinal) + 1;
            }

            if (m.loserNorm != null && !placements.containsKey(m.loserNorm.toLowerCase())) {
                putPlacement(placements, m.loserNorm, rankForLoser);
            }
        }
    }

    private void computeDoubleEliminationPlacements(List<MatchInfo> matches, Map<String, Integer> placements) {
        // Grand Final
        for (MatchInfo m : matches) {
            if ("GRAND_FINAL".equalsIgnoreCase(m.bracketType) || "GRAND_FINAL_RESET".equalsIgnoreCase(m.bracketType)) {
                if (m.winnerNorm != null) putPlacement(placements, m.winnerNorm, 1);
                if (m.loserNorm != null) putPlacement(placements, m.loserNorm, 2);
            }
        }

        // Lower Bracket rounds descending
        int maxLowerRound = 0;
        for (MatchInfo m : matches) {
            if ("LOSER_BRACKET".equalsIgnoreCase(m.bracketType) && m.roundNumber > maxLowerRound) {
                maxLowerRound = m.roundNumber;
            }
        }

        for (MatchInfo m : matches) {
            if ("LOSER_BRACKET".equalsIgnoreCase(m.bracketType)) {
                int dist = maxLowerRound - m.roundNumber;
                int rankForLoser;
                if (dist == 0) rankForLoser = 3; // Loser Finals loser = 3rd
                else if (dist <= 2) rankForLoser = 4; // Top 4
                else if (dist <= 4) rankForLoser = 5; // Top 6 / Top 8
                else rankForLoser = 9; // Top 12+

                if (m.loserNorm != null && !placements.containsKey(m.loserNorm.toLowerCase())) {
                    putPlacement(placements, m.loserNorm, rankForLoser);
                }
            }
        }
    }

    private void fillRemainingTeams(String tournamentId, Map<String, Integer> placements, Connection conn) {
        try {
            String sql = "SELECT raw_name, normalized_name, original_seed FROM teams WHERE tournament_id = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, tournamentId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String norm = rs.getString("normalized_name");
                        if (norm != null && !placements.containsKey(norm.trim().toLowerCase())) {
                            int seed = rs.getInt("original_seed");
                            placements.put(norm.trim().toLowerCase(), seed > 0 ? Math.max(seed, 16) : 16);
                        }
                    }
                }
            }
        } catch (Exception ignore) {}
    }

    private void putPlacement(Map<String, Integer> map, String teamNorm, int rank) {
        if (teamNorm == null || teamNorm.trim().isEmpty()) return;
        String key = teamNorm.trim().toLowerCase();
        if (!map.containsKey(key) || map.get(key) > rank) {
            map.put(key, rank);
        }
    }
}
