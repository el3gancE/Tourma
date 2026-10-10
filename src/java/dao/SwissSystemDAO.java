package dao;

import model.Team;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Types;
import java.util.*;

/**
 * ============================================================================
 * TOURMA - HIGH-PERFORMANCE DATABASE-DRIVEN DAO FOR SWISS SYSTEM TOURNAMENT
 * 100% Database-Driven:
 * - Dynamic generation of Rounds 1 through 5 in `matches` table
 * - Non-rematch deterministic backtracking pool pairing
 * - Full Buchholz & Tiebreaker SQL/Java standings computation
 * - Sequential Batch Random Score Generation & Reset
 * ============================================================================
 */
public class SwissSystemDAO extends DBContext {

    public static class SwissStandingRow {
        public String teamId;
        public String teamName;
        public int seed;
        public int wins;
        public int losses;
        public int matchesPlayed;
        public int scoresFor;
        public int scoresAgainst;
        public int goalDifference;
        public int buchholz;
        public int rank;
        public boolean qualified;
        public boolean eliminated;
        public String statusText;
    }

    public static class SwissMatch {
        public String id;
        public int roundNumber;
        public int matchOrder;
        public String pool; // e.g. "0-0", "1-0", "2-0"
        public String team1Id;
        public String team1Name;
        public int team1Seed;
        public Integer score1;
        public String team2Id;
        public String team2Name;
        public int team2Seed;
        public Integer score2;
        public String winnerId;
        public String status;
        public boolean isBye;
    }

    private static class TeamStats {
        String teamId;
        String teamName;
        int seed;
        int wins = 0;
        int losses = 0;
        int matchesPlayed = 0;
        int scoresFor = 0;
        int scoresAgainst = 0;
        int diff = 0;
        int buchholz = 0;
        int points = 0; // 3 per win
        List<String> opponents = new ArrayList<>();
        boolean qualified = false;
        boolean eliminated = false;
    }

    // ------------------------------------------------------------------------
    // 1. INITIALIZATION & ROUND PROGRESSION
    // ------------------------------------------------------------------------

    public synchronized void ensureSwissRoundsInitialized(String tournamentId, int stageOrder) {
        if (tournamentId == null || tournamentId.trim().isEmpty()) return;

        String checkSql = "SELECT COUNT(*) FROM matches m "
                + "LEFT JOIN tournament_stages s ON m.stage_id = s.id "
                + "WHERE m.tournament_id = ? AND (s.stage_order = ? OR (s.stage_order IS NULL AND ? = 1) OR m.stage_id LIKE '%_S' + CAST(? AS VARCHAR) + '%' OR m.stage_id = 'STAGE_' + CAST(? AS VARCHAR))";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(checkSql)) {
            ps.setString(1, tournamentId);
            ps.setInt(2, stageOrder);
            ps.setInt(3, stageOrder);
            ps.setInt(4, stageOrder);
            ps.setInt(5, stageOrder);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next() && rs.getInt(1) > 0) {
                    // Check if subsequent rounds need to be generated based on completed matches
                    checkAndGenerateNextSwissRoundInDB(tournamentId, stageOrder);
                    return;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

        ParticipantDAO pDao = new ParticipantDAO();
        List<Team> teams = pDao.getTeamsByTournamentId(tournamentId);
        if (teams == null || teams.isEmpty()) {
            teams = generateDefault16Teams(tournamentId);
        }

        initializeSwissRound1InDB(tournamentId, stageOrder, teams);
    }

    private List<Team> generateDefault16Teams(String tournamentId) {
        List<Team> list = new ArrayList<>();
        String insertTeamSql = "INSERT INTO teams (id, tournament_id, raw_name, normalized_name, original_seed) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(insertTeamSql)) {
            for (int i = 1; i <= 16; i++) {
                String tId = "TM_" + tournamentId + "_" + i;
                String tName = "Đội " + i;
                ps.setString(1, tId);
                ps.setString(2, tournamentId);
                ps.setString(3, tName);
                ps.setString(4, tName);
                ps.setInt(5, i);
                ps.addBatch();

                Team tm = new Team();
                tm.setId(tId);
                tm.setRawName(tName);
                tm.setOriginalSeed(i);
                list.add(tm);
            }
            ps.executeBatch();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    private void initializeSwissRound1InDB(String tournamentId, int stageOrder, List<Team> teams) {
        int n = teams.size();
        if (n < 2) return;
        String stageId = lookupOrCreateStageId(tournamentId, stageOrder);

        // Seeding for Round 1: Top half vs Bottom half (1 vs 9, 2 vs 10, ... 8 vs 16)
        int half = n / 2;
        String insertSql = "INSERT INTO matches (id, tournament_id, stage_id, group_id, round_number, match_order, match_code, bracket_type, "
                + "team1_id, team2_id, is_bye, status) "
                + "VALUES (?, ?, ?, ?, 1, ?, ?, 'SWISS', ?, ?, 0, 'READY')";

        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            String poolGroupId = lookupOrCreatePoolGroupId(conn, tournamentId, stageId, "0-0");

            try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                for (int i = 0; i < half; i++) {
                    Team t1 = teams.get(i);
                    Team t2 = teams.get(i + half);
                    String matchId = "M_" + tournamentId + "_SW_R1_" + (i + 1);

                    ps.setString(1, matchId);
                    ps.setString(2, tournamentId);
                    ps.setString(3, stageId);
                    if (poolGroupId != null) ps.setString(4, poolGroupId); else ps.setNull(4, Types.VARCHAR);
                    ps.setInt(5, i + 1);
                    ps.setString(6, "Round 1 - Trận " + (i + 1));
                    ps.setString(7, t1.getId());
                    ps.setString(8, t2.getId());
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            conn.commit();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String lookupOrCreatePoolGroupId(Connection conn, String tournamentId, String stageId, String poolKey) {
        String cleanPool = (poolKey != null && !poolKey.trim().isEmpty()) ? poolKey.trim() : "0-0";
        String gId = "GRP_" + tournamentId + "_P_" + cleanPool.replace("-", "_");
        String checkSql = "SELECT id FROM groups WHERE id = ?";
        try (PreparedStatement psCheck = conn.prepareStatement(checkSql)) {
            psCheck.setString(1, gId);
            try (ResultSet rs = psCheck.executeQuery()) {
                if (rs.next()) return rs.getString("id");
            }
        } catch (Exception ignore) {}

        String insSql = "INSERT INTO groups (id, stage_id, group_name, qualified_slots_count) VALUES (?, ?, ?, 2)";
        try (PreparedStatement psIns = conn.prepareStatement(insSql)) {
            psIns.setString(1, gId);
            psIns.setString(2, stageId);
            psIns.setString(3, cleanPool);
            psIns.executeUpdate();
            return gId;
        } catch (Exception ignore) {}

        return null;
    }

    /**
     * Checks completed rounds and automatically pairs & inserts the next round into `matches` table.
     */
    public synchronized void checkAndGenerateNextSwissRoundInDB(String tournamentId, int stageOrder) {
        if (tournamentId == null || tournamentId.trim().isEmpty()) return;

        List<SwissMatch> matches = getSwissMatchesFromDB(tournamentId, stageOrder);
        if (matches.isEmpty()) return;

        // Group completed matches by round
        Map<Integer, List<SwissMatch>> roundMatches = new HashMap<>();
        for (SwissMatch m : matches) {
            roundMatches.computeIfAbsent(m.roundNumber, k -> new ArrayList<>()).add(m);
        }

        // 1. Check Round 1 completion -> Generate Round 2
        List<SwissMatch> r1 = roundMatches.get(1);
        if (r1 != null && r1.size() == 8 && isRoundComplete(r1)) {
            List<SwissMatch> r2 = roundMatches.get(2);
            if (r2 == null || r2.isEmpty()) {
                generateAndInsertRoundInDB(tournamentId, stageOrder, 2, matches);
                matches = getSwissMatchesFromDB(tournamentId, stageOrder);
                roundMatches.clear();
                for (SwissMatch m : matches) roundMatches.computeIfAbsent(m.roundNumber, k -> new ArrayList<>()).add(m);
            }
        }

        // 2. Check Round 2 completion -> Generate Round 3
        List<SwissMatch> r2 = roundMatches.get(2);
        if (r2 != null && r2.size() == 8 && isRoundComplete(r2)) {
            List<SwissMatch> r3 = roundMatches.get(3);
            if (r3 == null || r3.isEmpty()) {
                generateAndInsertRoundInDB(tournamentId, stageOrder, 3, matches);
                matches = getSwissMatchesFromDB(tournamentId, stageOrder);
                roundMatches.clear();
                for (SwissMatch m : matches) roundMatches.computeIfAbsent(m.roundNumber, k -> new ArrayList<>()).add(m);
            }
        }

        // 3. Check Round 3 completion -> Generate Round 4
        List<SwissMatch> r3 = roundMatches.get(3);
        if (r3 != null && r3.size() == 8 && isRoundComplete(r3)) {
            List<SwissMatch> r4 = roundMatches.get(4);
            if (r4 == null || r4.isEmpty()) {
                generateAndInsertRoundInDB(tournamentId, stageOrder, 4, matches);
                matches = getSwissMatchesFromDB(tournamentId, stageOrder);
                roundMatches.clear();
                for (SwissMatch m : matches) roundMatches.computeIfAbsent(m.roundNumber, k -> new ArrayList<>()).add(m);
            }
        }

        // 4. Check Round 4 completion -> Generate Round 5
        List<SwissMatch> r4 = roundMatches.get(4);
        if (r4 != null && r4.size() == 6 && isRoundComplete(r4)) {
            List<SwissMatch> r5 = roundMatches.get(5);
            if (r5 == null || r5.isEmpty()) {
                generateAndInsertRoundInDB(tournamentId, stageOrder, 5, matches);
                matches = getSwissMatchesFromDB(tournamentId, stageOrder);
                roundMatches.clear();
                for (SwissMatch m : matches) roundMatches.computeIfAbsent(m.roundNumber, k -> new ArrayList<>()).add(m);
            }
        }

        // 5. Check Round 5 completion -> Stage 1 Finish & Save Stage 2 Teams
        List<SwissMatch> r5 = roundMatches.get(5);
        if (r5 != null && r5.size() == 3 && isRoundComplete(r5)) {
            finishSwissStageInDB(tournamentId, stageOrder, matches);
        }
    }

    private boolean isRoundComplete(List<SwissMatch> roundList) {
        if (roundList == null || roundList.isEmpty()) return false;
        for (SwissMatch m : roundList) {
            if (!"COMPLETED".equalsIgnoreCase(m.status) && !"FINISHED".equalsIgnoreCase(m.status)) {
                return false;
            }
            if (m.score1 == null || m.score2 == null) {
                return false;
            }
        }
        return true;
    }

    private void generateAndInsertRoundInDB(String tournamentId, int stageOrder, int roundToGen, List<SwissMatch> allMatches) {
        ParticipantDAO pDao = new ParticipantDAO();
        List<Team> teams = pDao.getTeamsByTournamentId(tournamentId);
        if (teams == null || teams.isEmpty()) return;

        Map<String, TeamStats> statsMap = computeTeamStats(teams, allMatches);

        // Group teams into record pools (only active teams with wins < 3 and losses < 3)
        Map<String, List<TeamStats>> pools = new LinkedHashMap<>();
        for (TeamStats ts : statsMap.values()) {
            if (ts.wins < 3 && ts.losses < 3) {
                String rec = ts.wins + "-" + ts.losses;
                pools.computeIfAbsent(rec, k -> new ArrayList<>()).add(ts);
            }
        }

        String stageId = lookupOrCreateStageId(tournamentId, stageOrder);
        String insertSql = "INSERT INTO matches (id, tournament_id, stage_id, group_id, round_number, match_order, match_code, bracket_type, "
                + "team1_id, team2_id, is_bye, status) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, 'SWISS', ?, ?, 0, 'READY')";

        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                int matchOrder = 1;
                for (Map.Entry<String, List<TeamStats>> entry : pools.entrySet()) {
                    String poolKey = entry.getKey();
                    String poolGroupId = lookupOrCreatePoolGroupId(conn, tournamentId, stageId, poolKey);
                    List<TeamStats> poolTeams = entry.getValue();

                    List<TeamStats[]> pairs = pairPool(poolTeams);
                    for (TeamStats[] pair : pairs) {
                        String matchId = "M_" + tournamentId + "_SW_R" + roundToGen + "_" + matchOrder;
                        ps.setString(1, matchId);
                        ps.setString(2, tournamentId);
                        ps.setString(3, stageId);
                        if (poolGroupId != null) ps.setString(4, poolGroupId); else ps.setNull(4, Types.VARCHAR);
                        ps.setInt(5, roundToGen);
                        ps.setInt(6, matchOrder);
                        ps.setString(7, "Round " + roundToGen + " - Trận " + matchOrder);
                        ps.setString(8, pair[0].teamId);
                        ps.setString(9, pair[1].teamId);
                        ps.addBatch();
                        matchOrder++;
                    }
                }
                ps.executeBatch();
            }
            conn.commit();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private List<TeamStats[]> pairPool(List<TeamStats> pool) {
        List<TeamStats[]> pairs = new ArrayList<>();
        if (pool == null || pool.size() < 2) return pairs;

        // Sort by Buchholz DESC -> Diff DESC -> ScoresFor DESC -> Seed ASC
        pool.sort((a, b) -> {
            if (b.buchholz != a.buchholz) return b.buchholz - a.buchholz;
            if (b.diff != a.diff) return b.diff - a.diff;
            if (b.scoresFor != a.scoresFor) return b.scoresFor - a.scoresFor;
            return a.seed - b.seed;
        });

        boolean[] used = new boolean[pool.size()];
        if (!backtrackPair(pool, used, pairs)) {
            // Fallback: sequential pairing if non-rematch permutation is impossible
            pairs.clear();
            for (int i = 0; i < pool.size() - 1; i += 2) {
                pairs.add(new TeamStats[]{pool.get(i), pool.get(i + 1)});
            }
        }
        return pairs;
    }

    private boolean backtrackPair(List<TeamStats> pool, boolean[] used, List<TeamStats[]> result) {
        int first = -1;
        for (int i = 0; i < pool.size(); i++) {
            if (!used[i]) {
                first = i;
                break;
            }
        }
        if (first == -1) return true; // All paired!

        used[first] = true;
        TeamStats t1 = pool.get(first);

        for (int j = first + 1; j < pool.size(); j++) {
            if (!used[j]) {
                TeamStats t2 = pool.get(j);
                if (!t1.opponents.contains(t2.teamId) && !t2.opponents.contains(t1.teamId)) {
                    used[j] = true;
                    result.add(new TeamStats[]{t1, t2});

                    if (backtrackPair(pool, used, result)) {
                        return true;
                    }

                    result.remove(result.size() - 1);
                    used[j] = false;
                }
            }
        }

        used[first] = false;
        return false;
    }

    private Map<String, TeamStats> computeTeamStats(List<Team> teams, List<SwissMatch> matches) {
        Map<String, TeamStats> statsMap = new HashMap<>();
        for (Team tm : teams) {
            TeamStats st = new TeamStats();
            st.teamId = tm.getId();
            st.teamName = tm.getRawName() != null ? tm.getRawName() : tm.getId();
            st.seed = tm.getOriginalSeed();
            statsMap.put(tm.getId(), st);
        }

        // Process completed matches
        for (SwissMatch m : matches) {
            if (!"COMPLETED".equalsIgnoreCase(m.status) && !"FINISHED".equalsIgnoreCase(m.status)) continue;
            if (m.score1 == null || m.score2 == null) continue;

            String t1Id = m.team1Id;
            String t2Id = m.team2Id;
            int s1 = m.score1;
            int s2 = m.score2;

            TeamStats st1 = statsMap.get(t1Id);
            TeamStats st2 = statsMap.get(t2Id);

            if (st1 != null && st2 != null) {
                st1.matchesPlayed++;
                st2.matchesPlayed++;
                st1.opponents.add(st2.teamId);
                st2.opponents.add(st1.teamId);

                st1.scoresFor += s1;
                st1.scoresAgainst += s2;
                st2.scoresFor += s2;
                st2.scoresAgainst += s1;

                if (s1 > s2) {
                    st1.wins++;
                    st1.points += 3;
                    st2.losses++;
                } else if (s2 > s1) {
                    st2.wins++;
                    st2.points += 3;
                    st1.losses++;
                }
            }
        }

        // Compute Buchholz & Qualified/Eliminated status
        for (TeamStats st : statsMap.values()) {
            st.diff = st.scoresFor - st.scoresAgainst;
            int bSum = 0;
            for (String oppId : st.opponents) {
                TeamStats opp = statsMap.get(oppId);
                if (opp != null) bSum += opp.points;
            }
            st.buchholz = bSum;
            if (st.wins >= 3) st.qualified = true;
            else if (st.losses >= 3) st.eliminated = true;
        }

        return statsMap;
    }

    private void finishSwissStageInDB(String tournamentId, int stageOrder, List<SwissMatch> allMatches) {
        ParticipantDAO pDao = new ParticipantDAO();
        List<Team> teams = pDao.getTeamsByTournamentId(tournamentId);
        if (teams == null || teams.isEmpty()) return;

        Map<String, TeamStats> statsMap = computeTeamStats(teams, allMatches);
        List<TeamStats> qualifiedList = new ArrayList<>();
        for (TeamStats st : statsMap.values()) {
            if (st.qualified) qualifiedList.add(st);
        }

        // Sort qualified teams: 3-0 > 3-1 > 3-2 -> Buchholz DESC -> Diff DESC
        qualifiedList.sort((a, b) -> {
            if (a.losses != b.losses) return a.losses - b.losses; // 0 losses (3-0) < 1 loss (3-1) < 2 losses (3-2)
            if (b.buchholz != a.buchholz) return b.buchholz - a.buchholz;
            return b.diff - a.diff;
        });

        StringBuilder jsonSb = new StringBuilder("[");
        for (int i = 0; i < Math.min(8, qualifiedList.size()); i++) {
            if (i > 0) jsonSb.append(",");
            TeamStats st = qualifiedList.get(i);
            jsonSb.append("{\"id\":\"").append(escapeJson(st.teamId))
                    .append("\",\"name\":\"").append(escapeJson(st.teamName))
                    .append("\",\"seed\":").append(i + 1).append("}");
        }
        jsonSb.append("]");

        TournamentDAO tDao = new TournamentDAO();
        tDao.saveStage2Teams(tournamentId, jsonSb.toString());
        tDao.updateTournamentStage1Status(tournamentId, "COMPLETED");
    }

    // ------------------------------------------------------------------------
    // 2. FRONTEND JSON & MATCH QUERIES
    // ------------------------------------------------------------------------

    public String getMatchesJsonForFrontend(String tournamentId, int stageOrder) {
        if (tournamentId == null || tournamentId.trim().isEmpty()) return "[]";

        ensureSwissRoundsInitialized(tournamentId, stageOrder);

        String sql = "SELECT m.*, "
                + "ISNULL(g.group_name, ISNULL(m.group_id, '0-0')) AS display_pool, "
                + "t1.raw_name AS t1_name, t1.original_seed AS t1_seed, "
                + "t2.raw_name AS t2_name, t2.original_seed AS t2_seed, "
                + "tw.raw_name AS winner_name "
                + "FROM matches m "
                + "LEFT JOIN groups g ON (m.group_id = g.id OR m.group_id = g.group_name) "
                + "LEFT JOIN tournament_stages s ON m.stage_id = s.id "
                + "LEFT JOIN teams t1 ON m.team1_id = t1.id "
                + "LEFT JOIN teams t2 ON m.team2_id = t2.id "
                + "LEFT JOIN teams tw ON m.winner_id = tw.id "
                + "WHERE m.tournament_id = ? AND (s.stage_order = ? OR s.stage_order IS NULL OR ? = 1) "
                + "ORDER BY m.round_number ASC, ISNULL(m.match_order, 999) ASC, m.id ASC";

        StringBuilder sb = new StringBuilder("[");
        int count = 0;

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tournamentId);
            ps.setInt(2, stageOrder);
            ps.setInt(3, stageOrder);
            try (ResultSet rs = ps.executeQuery()) {
                int seq = 1;
                while (rs.next()) {
                    if (count > 0) sb.append(",");
                    count++;

                    String rawId = rs.getString("id");
                    int matchOrder = rs.getInt("match_order");
                    int matchNum = (matchOrder > 0) ? matchOrder : seq++;
                    int roundNumber = rs.getInt("round_number");
                    String pool = rs.getString("display_pool");
                    if (pool == null || pool.trim().isEmpty()) pool = "0-0";

                    String t1Id = rs.getString("team1_id");
                    String t1Name = rs.getString("t1_name");
                    int t1SeedVal = rs.getInt("t1_seed");
                    String t1Seed = rs.wasNull() ? "" : String.valueOf(t1SeedVal);
                    int s1Val = rs.getInt("score1");
                    String s1 = rs.wasNull() ? "" : String.valueOf(s1Val);

                    String t2Id = rs.getString("team2_id");
                    String t2Name = rs.getString("t2_name");
                    int t2SeedVal = rs.getInt("t2_seed");
                    String t2Seed = rs.wasNull() ? "" : String.valueOf(t2SeedVal);
                    int s2Val = rs.getInt("score2");
                    String s2 = rs.wasNull() ? "" : String.valueOf(s2Val);

                    String winnerIdCol = rs.getString("winner_id");
                    String winnerSlot = "";
                    if (winnerIdCol != null && !winnerIdCol.trim().isEmpty()) {
                        if (winnerIdCol.equals(t1Id) || "team1".equalsIgnoreCase(winnerIdCol)) winnerSlot = "team1";
                        else if (winnerIdCol.equals(t2Id) || "team2".equalsIgnoreCase(winnerIdCol)) winnerSlot = "team2";
                    }

                    boolean isBye = rs.getBoolean("is_bye");
                    String status = rs.getString("status");
                    if ("FINISHED".equalsIgnoreCase(status)) status = "COMPLETED";

                    sb.append("{")
                            .append("\"matchId\":\"").append(escapeJson(rawId)).append("\",")
                            .append("\"matchKey\":\"").append(escapeJson(rawId)).append("\",")
                            .append("\"id\":\"").append(escapeJson(rawId)).append("\",")
                            .append("\"rawId\":\"").append(escapeJson(rawId)).append("\",")
                            .append("\"roundIndex\":").append(roundNumber).append(",")
                            .append("\"roundNumber\":").append(roundNumber).append(",")
                            .append("\"matchNumber\":").append(matchNum).append(",")
                            .append("\"recordPool\":\"").append(escapeJson(pool)).append("\",")
                            .append("\"pool\":\"").append(escapeJson(pool)).append("\",")
                            .append("\"team1Score\":\"").append(escapeJson(s1)).append("\",")
                            .append("\"team2Score\":\"").append(escapeJson(s2)).append("\",")
                            .append("\"team1\":{")
                            .append("\"id\":\"").append(escapeJson(t1Id != null ? t1Id : "")).append("\",")
                            .append("\"name\":\"").append(escapeJson(t1Name != null ? t1Name : "")).append("\",")
                            .append("\"seed\":\"").append(escapeJson(t1Seed)).append("\",")
                            .append("\"score\":\"").append(escapeJson(s1)).append("\"")
                            .append("},")
                            .append("\"team2\":{")
                            .append("\"id\":\"").append(escapeJson(t2Id != null ? t2Id : "")).append("\",")
                            .append("\"name\":\"").append(escapeJson(t2Name != null ? t2Name : "")).append("\",")
                            .append("\"seed\":\"").append(escapeJson(t2Seed)).append("\",")
                            .append("\"score\":\"").append(escapeJson(s2)).append("\"")
                            .append("},")
                            .append("\"score1\":\"").append(escapeJson(s1)).append("\",")
                            .append("\"score2\":\"").append(escapeJson(s2)).append("\",")
                            .append("\"winnerId\":").append(winnerSlot.isEmpty() ? "null" : "\"" + winnerSlot + "\"").append(",")
                            .append("\"isBye\":").append(isBye).append(",")
                            .append("\"status\":\"").append(escapeJson(status != null ? status : "SCHEDULED")).append("\"")
                            .append("}");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        sb.append("]");
        return sb.toString();
    }

    public List<SwissMatch> getSwissMatchesFromDB(String tournamentId, int stageOrder) {
        List<SwissMatch> list = new ArrayList<>();
        if (tournamentId == null || tournamentId.trim().isEmpty()) return list;

        String sql = "SELECT m.*, "
                + "ISNULL(g.group_name, ISNULL(m.group_id, '0-0')) AS display_pool, "
                + "t1.raw_name AS t1_name, t1.original_seed AS t1_seed, "
                + "t2.raw_name AS t2_name, t2.original_seed AS t2_seed, "
                + "tw.raw_name AS winner_name "
                + "FROM matches m "
                + "LEFT JOIN groups g ON (m.group_id = g.id OR m.group_id = g.group_name) "
                + "LEFT JOIN tournament_stages s ON m.stage_id = s.id "
                + "LEFT JOIN teams t1 ON m.team1_id = t1.id "
                + "LEFT JOIN teams t2 ON m.team2_id = t2.id "
                + "LEFT JOIN teams tw ON m.winner_id = tw.id "
                + "WHERE m.tournament_id = ? AND (s.stage_order = ? OR s.stage_order IS NULL OR ? = 1) "
                + "ORDER BY m.round_number ASC, ISNULL(m.match_order, 999) ASC, m.id ASC";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tournamentId);
            ps.setInt(2, stageOrder);
            ps.setInt(3, stageOrder);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    SwissMatch m = new SwissMatch();
                    m.id = rs.getString("id");
                    m.roundNumber = rs.getInt("round_number");
                    m.matchOrder = rs.getInt("match_order");
                    m.pool = rs.getString("display_pool");
                    if (m.pool == null || m.pool.trim().isEmpty()) m.pool = "0-0";

                    m.team1Id = rs.getString("team1_id");
                    m.team1Name = rs.getString("t1_name");
                    int s1Seed = rs.getInt("t1_seed");
                    if (!rs.wasNull()) m.team1Seed = s1Seed;
                    int s1 = rs.getInt("score1");
                    if (!rs.wasNull()) m.score1 = s1;

                    m.team2Id = rs.getString("team2_id");
                    m.team2Name = rs.getString("t2_name");
                    int s2Seed = rs.getInt("t2_seed");
                    if (!rs.wasNull()) m.team2Seed = s2Seed;
                    int s2 = rs.getInt("score2");
                    if (!rs.wasNull()) m.score2 = s2;

                    m.winnerId = rs.getString("winner_id");
                    String st = rs.getString("status");
                    m.status = "FINISHED".equalsIgnoreCase(st) ? "COMPLETED" : (st != null ? st : "SCHEDULED");
                    m.isBye = rs.getBoolean("is_bye");

                    list.add(m);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    // ------------------------------------------------------------------------
    // 3. BUCHHOLZ STANDINGS TABLE
    // ------------------------------------------------------------------------

    public List<SwissStandingRow> getSwissStandings(String tournamentId, int stageOrder) {
        List<SwissStandingRow> list = new ArrayList<>();
        if (tournamentId == null || tournamentId.trim().isEmpty()) return list;

        ParticipantDAO pDao = new ParticipantDAO();
        List<Team> teams = pDao.getTeamsByTournamentId(tournamentId);
        if (teams == null || teams.isEmpty()) return list;

        List<SwissMatch> matches = getSwissMatchesFromDB(tournamentId, stageOrder);
        Map<String, TeamStats> statsMap = computeTeamStats(teams, matches);

        List<TeamStats> sorted = new ArrayList<>(statsMap.values());
        sorted.sort((a, b) -> {
            if (a.qualified && !b.qualified) return -1;
            if (!a.qualified && b.qualified) return 1;
            if (a.qualified && b.qualified) {
                if (a.losses != b.losses) return a.losses - b.losses;
                if (b.buchholz != a.buchholz) return b.buchholz - a.buchholz;
                return b.diff - a.diff;
            }
            if (a.eliminated && !b.eliminated) return 1;
            if (!a.eliminated && b.eliminated) return -1;

            if (b.wins != a.wins) return b.wins - a.wins;
            if (a.losses != b.losses) return a.losses - b.losses;
            if (b.buchholz != a.buchholz) return b.buchholz - a.buchholz;
            if (b.diff != a.diff) return b.diff - a.diff;
            return b.scoresFor - a.scoresFor;
        });

        int rk = 1;
        for (TeamStats st : sorted) {
            SwissStandingRow row = new SwissStandingRow();
            row.teamId = st.teamId;
            row.teamName = st.teamName;
            row.seed = st.seed;
            row.wins = st.wins;
            row.losses = st.losses;
            row.matchesPlayed = st.matchesPlayed;
            row.scoresFor = st.scoresFor;
            row.scoresAgainst = st.scoresAgainst;
            row.goalDifference = st.diff;
            row.buchholz = st.buchholz;
            row.rank = rk++;
            row.qualified = st.qualified;
            row.eliminated = st.eliminated;
            row.statusText = st.qualified ? "QUALIFIED" : (st.eliminated ? "ELIMINATED" : "Đang thi đấu");
            list.add(row);
        }

        return list;
    }

    // ------------------------------------------------------------------------
    // 4. MATCH SCORING, RESET & RANDOMIZATION
    // ------------------------------------------------------------------------

    public boolean saveMatchScore(String tournamentId, int stageOrder, String matchId, int score1, int score2, String winnerId) {
        if (tournamentId == null || matchId == null) return false;

        // Parse potential round number and match order from matchId (e.g. "R1_M1", "R2_M3", "M_T_05731a1b_SW_R1_1")
        int parsedRound = -1;
        int parsedOrder = -1;
        try {
            String cleanKey = matchId.toUpperCase();
            if (cleanKey.matches(".*R\\d+.*")) {
                int rIdx = cleanKey.indexOf('R');
                if (rIdx != -1) {
                    int afterR = rIdx + 1;
                    StringBuilder rDigits = new StringBuilder();
                    while (afterR < cleanKey.length() && Character.isDigit(cleanKey.charAt(afterR))) {
                        rDigits.append(cleanKey.charAt(afterR));
                        afterR++;
                    }
                    if (rDigits.length() > 0) {
                        parsedRound = Integer.parseInt(rDigits.toString());
                    }
                    int mIdx = cleanKey.indexOf('M', afterR);
                    if (mIdx == -1) mIdx = cleanKey.indexOf('_', afterR);
                    if (mIdx != -1) {
                        int afterM = mIdx + 1;
                        StringBuilder mDigits = new StringBuilder();
                        while (afterM < cleanKey.length() && Character.isDigit(cleanKey.charAt(afterM))) {
                            mDigits.append(cleanKey.charAt(afterM));
                            afterM++;
                        }
                        if (mDigits.length() > 0) {
                            parsedOrder = Integer.parseInt(mDigits.toString());
                        }
                    }
                }
            }
        } catch (Exception ignore) {}

        String findSql = "SELECT id, team1_id, team2_id FROM matches WHERE tournament_id = ? AND "
                + "(id = ? OR id LIKE '%[_]' + ? OR id LIKE '%' + ? "
                + " OR (round_number = ? AND match_order = ?))";
        String updateSql = "UPDATE matches SET score1 = ?, score2 = ?, winner_id = ?, loser_id = ?, status = 'FINISHED' WHERE id = ?";

        try (Connection conn = getConnection()) {
            String actualMatchId = null;
            String actualT1 = null;
            String actualT2 = null;

            try (PreparedStatement psFind = conn.prepareStatement(findSql)) {
                psFind.setString(1, tournamentId);
                psFind.setString(2, matchId);
                psFind.setString(3, matchId);
                psFind.setString(4, matchId);
                psFind.setInt(5, parsedRound > 0 ? parsedRound : -999);
                psFind.setInt(6, parsedOrder > 0 ? parsedOrder : -999);
                try (ResultSet rs = psFind.executeQuery()) {
                    if (rs.next()) {
                        actualMatchId = rs.getString("id");
                        actualT1 = rs.getString("team1_id");
                        actualT2 = rs.getString("team2_id");
                    }
                }
            }

            if (actualMatchId == null) {
                return false;
            }

            String resolvedWinner = null;
            String resolvedLoser = null;
            if ("team1".equalsIgnoreCase(winnerId)) {
                resolvedWinner = actualT1;
                resolvedLoser = actualT2;
            } else if ("team2".equalsIgnoreCase(winnerId)) {
                resolvedWinner = actualT2;
                resolvedLoser = actualT1;
            } else if (winnerId != null && !winnerId.trim().isEmpty() && !"null".equalsIgnoreCase(winnerId)) {
                resolvedWinner = winnerId.trim();
                if (resolvedWinner.equals(actualT1)) {
                    resolvedLoser = actualT2;
                } else if (resolvedWinner.equals(actualT2)) {
                    resolvedLoser = actualT1;
                }
            } else if (score1 > score2) {
                resolvedWinner = actualT1;
                resolvedLoser = actualT2;
            } else if (score2 > score1) {
                resolvedWinner = actualT2;
                resolvedLoser = actualT1;
            }

            try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                ps.setInt(1, score1);
                ps.setInt(2, score2);
                if (resolvedWinner != null && !resolvedWinner.isEmpty()) {
                    ps.setString(3, resolvedWinner);
                } else {
                    ps.setNull(3, Types.VARCHAR);
                }
                if (resolvedLoser != null && !resolvedLoser.isEmpty()) {
                    ps.setString(4, resolvedLoser);
                } else {
                    ps.setNull(4, Types.VARCHAR);
                }
                ps.setString(5, actualMatchId);
                ps.executeUpdate();
            }

            checkAndGenerateNextSwissRoundInDB(tournamentId, stageOrder);
            service.RollingWindowPointService.clearAllCaches();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean batchSyncMatches(String tournamentId, int stageOrder, String matchesJson) {
        if (tournamentId == null || matchesJson == null || matchesJson.trim().isEmpty()) return false;
        try {
            // Simple fast JSON parse for batch match objects
            String clean = matchesJson.trim();
            if (clean.startsWith("[")) clean = clean.substring(1);
            if (clean.endsWith("]")) clean = clean.substring(0, clean.length() - 1);

            String[] rawItems = clean.split("\\}\\s*,\\s*\\{");
            for (String item : rawItems) {
                String obj = item.replace("{", "").replace("}", "");
                String mKey = extractJsonString(obj, "matchKey");
                if (mKey == null || mKey.isEmpty()) mKey = extractJsonString(obj, "matchId");
                if (mKey == null || mKey.isEmpty()) continue;

                String s1Str = extractJsonString(obj, "team1Score");
                if (s1Str == null) s1Str = extractJsonString(obj, "score1");
                String s2Str = extractJsonString(obj, "team2Score");
                if (s2Str == null) s2Str = extractJsonString(obj, "score2");
                String st = extractJsonString(obj, "status");
                String win = extractJsonString(obj, "winnerId");

                if (("COMPLETED".equalsIgnoreCase(st) || "FINISHED".equalsIgnoreCase(st)) && s1Str != null && s2Str != null) {
                    try {
                        int s1 = Integer.parseInt(s1Str.trim());
                        int s2 = Integer.parseInt(s2Str.trim());
                        saveMatchScore(tournamentId, stageOrder, mKey, s1, s2, win);
                    } catch (Exception ignore) {}
                }
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private String extractJsonString(String source, String key) {
        String pattern = "\"" + key + "\":";
        int idx = source.indexOf(pattern);
        if (idx == -1) {
            pattern = key + ":";
            idx = source.indexOf(pattern);
        }
        if (idx == -1) return null;

        int start = idx + pattern.length();
        while (start < source.length() && (source.charAt(start) == ' ' || source.charAt(start) == '\"')) {
            start++;
        }
        int end = start;
        while (end < source.length() && source.charAt(end) != '\"' && source.charAt(end) != ',' && source.charAt(end) != '}') {
            end++;
        }
        if (start < end) {
            String val = source.substring(start, end).trim();
            if ("null".equalsIgnoreCase(val)) return null;
            return val;
        }
        return null;
    }

    public boolean resetBracketMatches(String tournamentId, int stageOrder) {
        if (tournamentId == null || tournamentId.trim().isEmpty()) return false;
        String tId = tournamentId.trim();

        // 1. Break links first
        String nullLinksSql = "UPDATE matches SET next_match_id = NULL, loser_next_match_id = NULL WHERE tournament_id = ?";
        try (Connection conn = getConnection();
             PreparedStatement psNull = conn.prepareStatement(nullLinksSql)) {
            psNull.setString(1, tId);
            psNull.executeUpdate();
        } catch (Exception ignore) {}

        // 2. Delete all matches for this tournament & stage
        String delSql = "DELETE m FROM matches m "
                + "LEFT JOIN tournament_stages s ON m.stage_id = s.id "
                + "WHERE m.tournament_id = ? AND (s.stage_order = ? OR s.stage_order IS NULL OR ? = 1)";

        try (Connection conn = getConnection();
             PreparedStatement psDel = conn.prepareStatement(delSql)) {
            psDel.setString(1, tId);
            psDel.setInt(2, stageOrder);
            psDel.setInt(3, stageOrder);
            psDel.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }

        // 3. Reset tournaments table metadata
        String resetTourneySql = "UPDATE tournaments SET status = 'DRAFT', champion_name = NULL, stage2_teams = NULL, stage1_status = 'PENDING' WHERE id = ?";
        try (Connection conn = getConnection();
             PreparedStatement psT = conn.prepareStatement(resetTourneySql)) {
            psT.setString(1, tId);
            psT.executeUpdate();
        } catch (Exception ignore) {}

        // 4. Re-initialize fresh Swiss Round 1 in DB
        ParticipantDAO pDao = new ParticipantDAO();
        List<Team> teams = pDao.getTeamsByTournamentId(tId);
        if (teams == null || teams.isEmpty()) {
            teams = generateDefault16Teams(tId);
        }
        initializeSwissRound1InDB(tId, stageOrder, teams);

        service.RollingWindowPointService.clearAllCaches();
        return true;
    }

    public boolean randomRoundInDB(String tournamentId, int stageOrder, int roundNumber, int targetScore) {
        if (tournamentId == null) return false;
        ensureSwissRoundsInitialized(tournamentId, stageOrder);

        String selSql = "SELECT id, team1_id, team2_id FROM matches WHERE tournament_id = ? AND round_number = ? AND (score1 IS NULL OR status != 'FINISHED')";
        String updateSql = "UPDATE matches SET score1 = ?, score2 = ?, winner_id = ?, status = 'FINISHED' WHERE id = ?";

        Random rand = new Random();
        int maxS = targetScore > 0 ? targetScore : 3;

        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            List<String[]> matchesToUpdate = new ArrayList<>();

            try (PreparedStatement psSel = conn.prepareStatement(selSql)) {
                psSel.setString(1, tournamentId);
                psSel.setInt(2, roundNumber);
                try (ResultSet rs = psSel.executeQuery()) {
                    while (rs.next()) {
                        matchesToUpdate.add(new String[]{rs.getString("id"), rs.getString("team1_id"), rs.getString("team2_id")});
                    }
                }
            }

            try (PreparedStatement psUp = conn.prepareStatement(updateSql)) {
                for (String[] m : matchesToUpdate) {
                    boolean t1Win = rand.nextBoolean();
                    int winS = maxS;
                    int loseS = rand.nextInt(Math.max(1, maxS));
                    if (loseS >= winS) loseS = winS - 1;

                    int s1 = t1Win ? winS : loseS;
                    int s2 = t1Win ? loseS : winS;
                    String winId = t1Win ? m[1] : m[2];

                    psUp.setInt(1, s1);
                    psUp.setInt(2, s2);
                    psUp.setString(3, winId);
                    psUp.setString(4, m[0]);
                    psUp.addBatch();
                }
                psUp.executeBatch();
            }

            conn.commit();
            checkAndGenerateNextSwissRoundInDB(tournamentId, stageOrder);
            service.RollingWindowPointService.clearAllCaches();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean randomAllInDB(String tournamentId, int stageOrder, int targetScore) {
        for (int r = 1; r <= 5; r++) {
            randomRoundInDB(tournamentId, stageOrder, r, targetScore);
        }
        return true;
    }

    private String lookupOrCreateStageId(String tournamentId, int stageOrder) {
        String selectSql = "SELECT TOP 1 id FROM tournament_stages WHERE tournament_id = ? AND stage_order = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(selectSql)) {
            ps.setString(1, tournamentId);
            ps.setInt(2, stageOrder);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("id");
            }
        } catch (Exception ignore) {}

        String newStageId = "STG_" + tournamentId + "_S" + stageOrder;
        String insertSql = "INSERT INTO tournament_stages (id, tournament_id, stage_order, stage_name, format) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(insertSql)) {
            ps.setString(1, newStageId);
            ps.setString(2, tournamentId);
            ps.setInt(3, stageOrder);
            ps.setString(4, "Stage " + stageOrder);
            ps.setString(5, "SWISS_LITE");
            ps.executeUpdate();
            return newStageId;
        } catch (Exception ignore) {}

        return "STAGE_1";
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }
}
