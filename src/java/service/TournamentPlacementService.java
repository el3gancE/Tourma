package service;

import dao.DBContext;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Dedicated Service for calculating exact tournament placements (1st, 2nd, 3-4, 5-8, LQ, LR1, etc.)
 * directly from the 'matches' table in SQL Server.
 * Serves as the Single Source of Truth for tournament rankings.
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

    /**
     * Internal representation of a match row from database
     */
    private static class MatchRow {
        String id;
        int stageOrder;
        String stageFormat;
        int roundNumber;
        String bracketType;
        String team1Id;
        String team2Id;
        String t1Name;
        String t2Name;
        String winnerId;
        String winnerName;
        Integer score1;
        Integer score2;
        String status;
    }

    /**
     * Calculates placements for all participating teams in a tournament.
     * Returns a Map where key is team ID or lowercase team name, and value is placement integer
     * (1 = Champion, 2 = Runner-up, 3/4 = Semi-finals, 5-8 = Quarter-finals, 65 = LQ / Stage 1 LB cut, 97 = LR1, etc.)
     */
    public Map<String, Integer> getTournamentPlacements(String tournamentId) {
        Map<String, Integer> placementMap = new HashMap<>();
        if (tournamentId == null || tournamentId.trim().isEmpty()) return placementMap;

        DBContext db = new DBContext();
        String sql = "SELECT m.*, ts.stage_order, ts.format as stage_format, " +
                     "t.tournament_type, " +
                     "(SELECT TOP 1 format FROM tournament_stages WHERE tournament_id = t.id ORDER BY stage_order ASC) AS tourney_format, " +
                     "t1.raw_name AS t1_name, t1.normalized_name AS t1_norm, " +
                     "t2.raw_name AS t2_name, t2.normalized_name AS t2_norm, " +
                     "w.raw_name AS winner_name, w.normalized_name AS winner_norm " +
                     "FROM matches m " +
                     "LEFT JOIN tournaments t ON m.tournament_id = t.id " +
                     "LEFT JOIN tournament_stages ts ON m.stage_id = ts.id " +
                     "LEFT JOIN teams t1 ON m.team1_id = t1.id " +
                     "LEFT JOIN teams t2 ON m.team2_id = t2.id " +
                     "LEFT JOIN teams w ON m.winner_id = w.id " +
                     "WHERE m.tournament_id = ? " +
                     "ORDER BY ISNULL(ts.stage_order, 1) DESC, m.round_number DESC";

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tournamentId.trim());
            try (ResultSet rs = ps.executeQuery()) {
                int highestStageOrder = 1;
                String tourneyType = "SINGLE_STAGE";
                String tourneyFormat = "SINGLE_ELIMINATION";

                List<MatchRow> rows = new ArrayList<>();
                java.util.Set<String> distinctTeams = new java.util.HashSet<>();

                while (rs.next()) {
                    MatchRow row = new MatchRow();
                    int stgOrder = rs.getInt("stage_order");
                    if (rs.wasNull()) {
                        String rawId = rs.getString("id");
                        String stgId = rs.getString("stage_id");
                        if ((rawId != null && rawId.contains("_S2_")) || (stgId != null && stgId.contains("_S2_"))) {
                            stgOrder = 2;
                        } else {
                            stgOrder = 1;
                        }
                    }
                    if (stgOrder > highestStageOrder) highestStageOrder = stgOrder;

                    String tType = rs.getString("tournament_type");
                    if (tType != null && !tType.trim().isEmpty()) tourneyType = tType.trim();
                    String tFmt = rs.getString("tourney_format");
                    if (tFmt != null && !tFmt.trim().isEmpty()) tourneyFormat = tFmt.trim();

                    row.id = rs.getString("id");
                    row.stageOrder = stgOrder;
                    row.stageFormat = rs.getString("stage_format");
                    row.roundNumber = rs.getInt("round_number");
                    row.bracketType = rs.getString("bracket_type");
                    row.team1Id = rs.getString("team1_id");
                    row.team2Id = rs.getString("team2_id");
                    row.t1Name = rs.getString("t1_name");
                    row.t2Name = rs.getString("t2_name");
                    row.winnerId = rs.getString("winner_id");
                    row.winnerName = rs.getString("winner_name");

                    int s1 = rs.getInt("score1");
                    row.score1 = rs.wasNull() ? null : s1;
                    int s2 = rs.getInt("score2");
                    row.score2 = rs.wasNull() ? null : s2;
                    row.status = rs.getString("status");

                    if (row.t1Name != null && !row.t1Name.isEmpty()) distinctTeams.add(row.t1Name.trim().toLowerCase());
                    if (row.t2Name != null && !row.t2Name.isEmpty()) distinctTeams.add(row.t2Name.trim().toLowerCase());

                    // Resolve winner if not explicitly set but scores exist
                    if (row.winnerId == null && row.score1 != null && row.score2 != null && !row.score1.equals(row.score2)) {
                        if (row.score1 > row.score2) {
                            row.winnerId = row.team1Id;
                            row.winnerName = row.t1Name;
                        } else {
                            row.winnerId = row.team2Id;
                            row.winnerName = row.t2Name;
                        }
                    }

                    rows.add(row);
                }

                boolean isMultiStage = "MULTI_STAGE".equalsIgnoreCase(tourneyType) || highestStageOrder > 1;

                if (isMultiStage && highestStageOrder > 1) {
                    processMultiStageTournament(rows, highestStageOrder, placementMap);
                } else if (isMultiStage) {
                    processMultiStageDraft(rows, placementMap);
                } else if (isDoubleElimination(tourneyFormat, rows)) {
                    processSingleStageDoubleElimination(rows, placementMap);
                } else {
                    processSingleStageElimination(rows, distinctTeams.size(), placementMap);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return placementMap;
    }

    private boolean isDoubleElimination(String tourneyFormat, List<MatchRow> rows) {
        if (tourneyFormat != null && tourneyFormat.toUpperCase().contains("DOUBLE")) return true;
        for (MatchRow r : rows) {
            if (isLoserBracket(r.bracketType)) return true;
        }
        return false;
    }

    private boolean isLoserBracket(String bType) {
        if (bType == null) return false;
        String b = bType.toUpperCase().trim();
        return b.contains("LOSER") || b.contains("LB") || b.contains("LOWER");
    }

    private boolean isGrandFinal(String bType) {
        if (bType == null) return false;
        String b = bType.toUpperCase().trim();
        return b.contains("GRAND_FINAL") || b.contains("GRAND_FINALS") || b.contains("GF");
    }

    private void assignPlacement(Map<String, Integer> map, String teamId, String teamName, int pos) {
        if (pos <= 0) return;
        if (teamId != null && !teamId.trim().isEmpty()) {
            Integer existing = map.get(teamId);
            if (existing == null || pos < existing) {
                map.put(teamId, pos);
            }
        }
        if (teamName != null && !teamName.trim().isEmpty()) {
            String k = teamName.trim().toLowerCase();
            Integer existing = map.get(k);
            if (existing == null || pos < existing) {
                map.put(k, pos);
            }
        }
    }

    /**
     * Process Multi-Stage Tournament where Stage 2 (Final Stage) is present
     */
    private void processMultiStageTournament(List<MatchRow> rows, int highestStageOrder, Map<String, Integer> map) {
        // 1. Process Stage 2 (Final Stage)
        int maxRoundStage2 = -1;
        int maxLbRoundStage2 = 0;
        java.util.Set<String> s2Teams = new java.util.HashSet<>();
        for (MatchRow r : rows) {
            if (r.stageOrder == highestStageOrder) {
                if (r.roundNumber > maxRoundStage2) maxRoundStage2 = r.roundNumber;
                if (isLoserBracket(r.bracketType) && r.roundNumber > maxLbRoundStage2) {
                    maxLbRoundStage2 = r.roundNumber;
                }
                if (r.t1Name != null && !r.t1Name.isEmpty()) s2Teams.add(r.t1Name.trim().toLowerCase());
                if (r.t2Name != null && !r.t2Name.isEmpty()) s2Teams.add(r.t2Name.trim().toLowerCase());
            }
        }

        int expectedS2Rounds = (s2Teams.size() >= 2) ? (int) Math.ceil(Math.log(s2Teams.size()) / Math.log(2)) : 1;
        int totalS2Rounds = Math.max(maxRoundStage2, expectedS2Rounds);

        for (MatchRow r : rows) {
            if (r.stageOrder == highestStageOrder && r.winnerId != null) {
                String loserId = r.winnerId.equalsIgnoreCase(r.team1Id) ? r.team2Id : r.team1Id;
                String loserName = r.winnerId.equalsIgnoreCase(r.team1Id) ? r.t2Name : r.t1Name;

                if (maxLbRoundStage2 > 0) {
                    // Stage 2 is Double Elimination
                    if (isGrandFinal(r.bracketType) || (r.roundNumber == maxRoundStage2 && !isLoserBracket(r.bracketType))) {
                        assignPlacement(map, r.winnerId, r.winnerName, 1);
                        assignPlacement(map, loserId, loserName, 2);
                    } else if (isLoserBracket(r.bracketType)) {
                        int pos;
                        if (r.roundNumber == maxLbRoundStage2) pos = 3;
                        else if (r.roundNumber == maxLbRoundStage2 - 1) pos = 4;
                        else {
                            int k = (maxLbRoundStage2 - r.roundNumber) / 2;
                            pos = (int) Math.pow(2, k + 2) + 1;
                        }
                        assignPlacement(map, loserId, loserName, pos);
                    }
                } else {
                    // Stage 2 is Single Elimination
                    int diff = Math.max(0, totalS2Rounds - r.roundNumber);
                    if (diff == 0 && r.roundNumber == totalS2Rounds) {
                        assignPlacement(map, r.winnerId, r.winnerName, 1);
                        assignPlacement(map, loserId, loserName, 2);
                    } else {
                        int pos = (int) Math.pow(2, diff) + 1;
                        assignPlacement(map, loserId, loserName, pos);
                    }
                }
            }
        }

        // 2. Process Stage 1 Eliminated Teams
        int maxLbRoundStage1 = 0;
        boolean isStage1Swiss = false;
        boolean isStage1Group = false;

        for (MatchRow r : rows) {
            if (r.stageOrder < highestStageOrder) {
                if (isLoserBracket(r.bracketType)) {
                    if (r.roundNumber > maxLbRoundStage1) maxLbRoundStage1 = r.roundNumber;
                } else if (r.bracketType != null && r.bracketType.toUpperCase().contains("SWISS")) {
                    isStage1Swiss = true;
                } else if (r.bracketType != null && r.bracketType.toUpperCase().contains("GROUP")) {
                    isStage1Group = true;
                }
            }
        }

        if (maxLbRoundStage1 > 0) {
            // Stage 1 is Double Elimination: Only losers in Loser Bracket are eliminated from tournament!
            for (MatchRow r : rows) {
                if (r.stageOrder < highestStageOrder && r.winnerId != null && isLoserBracket(r.bracketType)) {
                    String loserId = r.winnerId.equalsIgnoreCase(r.team1Id) ? r.team2Id : r.team1Id;
                    String loserName = r.winnerId.equalsIgnoreCase(r.team1Id) ? r.t2Name : r.t1Name;

                    int pos;
                    if (r.roundNumber == maxLbRoundStage1) {
                        pos = 65; // Loser's Qualification (LQ)
                    } else if (r.roundNumber == maxLbRoundStage1 - 1) {
                        pos = 97; // Losers Round 1 (LR1)
                    } else {
                        pos = 129; // Earlier LB rounds
                    }
                    assignPlacement(map, loserId, loserName, pos);
                }
            }
        } else if (isStage1Swiss) {
            // Stage 1 is Swiss System: Tally wins and losses
            Map<String, int[]> swissStats = new HashMap<>(); // teamKey -> [wins, losses]
            Map<String, String> teamIdToName = new HashMap<>();

            for (MatchRow r : rows) {
                if (r.stageOrder < highestStageOrder && r.winnerId != null) {
                    String winnerId = r.winnerId;
                    String loserId = winnerId.equalsIgnoreCase(r.team1Id) ? r.team2Id : r.team1Id;
                    String winnerName = winnerId.equalsIgnoreCase(r.team1Id) ? r.t1Name : r.t2Name;
                    String loserName = winnerId.equalsIgnoreCase(r.team1Id) ? r.t2Name : r.t1Name;

                    if (winnerId != null) {
                        swissStats.computeIfAbsent(winnerId, k -> new int[2])[0]++;
                        teamIdToName.put(winnerId, winnerName);
                    }
                    if (loserId != null) {
                        swissStats.computeIfAbsent(loserId, k -> new int[2])[1]++;
                        teamIdToName.put(loserId, loserName);
                    }
                }
            }

            for (Map.Entry<String, int[]> entry : swissStats.entrySet()) {
                String tId = entry.getKey();
                int[] record = entry.getValue();
                int wins = record[0];
                int losses = record[1];

                // If team is already placed in Stage 2 (top rank), skip
                if (map.containsKey(tId) && map.get(tId) < 9) continue;

                int pos = 65;
                if (losses >= 3) {
                    if (wins == 2) pos = 9;  // swiss_2-3
                    else if (wins == 1) pos = 12; // swiss_1-3
                    else if (wins == 0) pos = 15; // swiss_0-3
                }
                assignPlacement(map, tId, teamIdToName.get(tId), pos);
            }
        } else {
            // Stage 1 Single Elimination / Group Stage
            for (MatchRow r : rows) {
                if (r.stageOrder < highestStageOrder && r.winnerId != null) {
                    String loserId = r.winnerId.equalsIgnoreCase(r.team1Id) ? r.team2Id : r.team1Id;
                    String loserName = r.winnerId.equalsIgnoreCase(r.team1Id) ? r.t2Name : r.t1Name;
                    assignPlacement(map, loserId, loserName, 65);
                }
            }
        }
    }

    /**
     * Process Multi-Stage Tournament when only Stage 1 exists (Stage 2 pending)
     */
    private void processMultiStageDraft(List<MatchRow> rows, Map<String, Integer> map) {
        int maxLbRound = 0;
        for (MatchRow r : rows) {
            if (isLoserBracket(r.bracketType) && r.roundNumber > maxLbRound) {
                maxLbRound = r.roundNumber;
            }
        }

        for (MatchRow r : rows) {
            if (r.winnerId != null) {
                String loserId = r.winnerId.equalsIgnoreCase(r.team1Id) ? r.team2Id : r.team1Id;
                String loserName = r.winnerId.equalsIgnoreCase(r.team1Id) ? r.t2Name : r.t1Name;

                if (maxLbRound > 0) {
                    if (!isLoserBracket(r.bracketType)) continue;
                    int pos = (r.roundNumber == maxLbRound) ? 65 : (r.roundNumber == maxLbRound - 1 ? 97 : 129);
                    assignPlacement(map, loserId, loserName, pos);
                } else {
                    assignPlacement(map, loserId, loserName, 65);
                }
            }
        }
    }

    /**
     * Process Single-Stage Double Elimination Tournament
     */
    private void processSingleStageDoubleElimination(List<MatchRow> rows, Map<String, Integer> map) {
        int maxRound = 0;
        int maxLbRound = 0;
        for (MatchRow r : rows) {
            if (r.roundNumber > maxRound) maxRound = r.roundNumber;
            if (isLoserBracket(r.bracketType) && r.roundNumber > maxLbRound) {
                maxLbRound = r.roundNumber;
            }
        }

        for (MatchRow r : rows) {
            if (r.winnerId == null) continue;
            String loserId = r.winnerId.equalsIgnoreCase(r.team1Id) ? r.team2Id : r.team1Id;
            String loserName = r.winnerId.equalsIgnoreCase(r.team1Id) ? r.t2Name : r.t1Name;

            if (isGrandFinal(r.bracketType) || (r.roundNumber == maxRound && !isLoserBracket(r.bracketType))) {
                assignPlacement(map, r.winnerId, r.winnerName, 1);
                assignPlacement(map, loserId, loserName, 2);
            } else if (isLoserBracket(r.bracketType)) {
                int pos;
                if (r.roundNumber == maxLbRound) pos = 3;
                else if (r.roundNumber == maxLbRound - 1) pos = 4;
                else {
                    int k = (maxLbRound - r.roundNumber) / 2;
                    pos = (int) Math.pow(2, k + 2) + 1;
                }
                assignPlacement(map, loserId, loserName, pos);
            }
        }
    }

    /**
     * Process Single-Stage Single Elimination Tournament
     */
    private void processSingleStageElimination(List<MatchRow> rows, int distinctTeamCount, Map<String, Integer> map) {
        int maxRound = 0;
        for (MatchRow r : rows) {
            if (r.roundNumber > maxRound) maxRound = r.roundNumber;
        }

        int expectedRounds = (distinctTeamCount >= 2) ? (int) Math.ceil(Math.log(distinctTeamCount) / Math.log(2)) : 1;
        int totalRounds = Math.max(maxRound, expectedRounds);

        for (MatchRow r : rows) {
            if (r.winnerId == null) continue;
            String loserId = r.winnerId.equalsIgnoreCase(r.team1Id) ? r.team2Id : r.team1Id;
            String loserName = r.winnerId.equalsIgnoreCase(r.team1Id) ? r.t2Name : r.t1Name;

            int diff = Math.max(0, totalRounds - r.roundNumber);
            if (diff == 0 && r.roundNumber == totalRounds) {
                assignPlacement(map, r.winnerId, r.winnerName, 1);
                assignPlacement(map, loserId, loserName, 2);
            } else {
                int pos = (int) Math.pow(2, diff) + 1;
                assignPlacement(map, loserId, loserName, pos);
            }
        }
    }
}
