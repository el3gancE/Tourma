package dao;

import model.Match;
import model.Team;
import model.Tournament;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Types;
import java.util.*;

/**
 * High-Performance, Database-Driven DAO for Group Stage Tournament Format.
 * 100% Database-Driven:
 * - Manages groups, group_teams rankings, and group fixtures in SQL Server.
 * - Real-time standings recalculation (MP, W, D, L, GF, GA, GD, PTS, Rank)
 * - Auto stage advancement pipeline into stage2_teams.
 */
public class GroupStageDAO extends DBContext {

    public static class GroupDTO {
        public String groupId;
        public String groupName;
        public int qualifiedSlots;
        public List<GroupTeamDTO> teams = new ArrayList<>();
        public List<Match> matches = new ArrayList<>();
    }

    public static class GroupTeamDTO {
        public String teamId;
        public String teamName;
        public int seedInGroup;
        public int matchesPlayed;
        public int wins;
        public int draws;
        public int losses;
        public int goalsScored;
        public int goalsConceded;
        public int goalDifference;
        public int points;
        public int rankInGroup;
    }

    public boolean syncGroupsAndGroupTeams(String tournamentId, int stageOrder, String groupAssignmentsJson) {
        if (tournamentId == null || groupAssignmentsJson == null || groupAssignmentsJson.trim().isEmpty()) return false;
        
        ParticipantDAO pDao = new ParticipantDAO();
        List<Team> allTeams = pDao.getTeamsByTournamentId(tournamentId);
        if (allTeams == null || allTeams.isEmpty()) return false;

        Map<String, List<Team>> groups = parseGroupAssignments(groupAssignmentsJson, allTeams);
        if (groups == null || groups.isEmpty()) return false;

        String stageId = lookupOrCreateStageId(tournamentId, stageOrder);

        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);

            // 1. Delete old matches, group_teams, and groups for this stage
            try (PreparedStatement psDelM = conn.prepareStatement("DELETE FROM matches WHERE tournament_id = ? AND stage_id = ?")) {
                psDelM.setString(1, tournamentId);
                psDelM.setString(2, stageId);
                psDelM.executeUpdate();
            }

            try (PreparedStatement psDelGT = conn.prepareStatement("DELETE gt FROM group_teams gt JOIN groups g ON gt.group_id = g.id WHERE g.stage_id = ?")) {
                psDelGT.setString(1, stageId);
                psDelGT.executeUpdate();
            }

            try (PreparedStatement psDelG = conn.prepareStatement("DELETE FROM groups WHERE stage_id = ?")) {
                psDelG.setString(1, stageId);
                psDelG.executeUpdate();
            }

            // 2. Re-create groups, group_teams, and matches
            initializeGroupsAndFixturesFromMap(conn, tournamentId, stageId, groups);

            conn.commit();
            service.RollingWindowPointService.clearAllCaches();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    // ------------------------------------------------------------------------
    // 1. INITIALIZATION & GROUP FIXTURES
    // ------------------------------------------------------------------------

    public synchronized void ensureGroupStageInitialized(String tournamentId, int stageOrder) {
        if (tournamentId == null || tournamentId.trim().isEmpty()) return;

        String stageId = lookupOrCreateStageId(tournamentId, stageOrder);

        String checkSql = "SELECT COUNT(*) FROM groups WHERE stage_id = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(checkSql)) {
            ps.setString(1, stageId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next() && rs.getInt(1) > 0) {
                    return; // Groups already exist in DB!
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

        ParticipantDAO pDao = new ParticipantDAO();
        List<Team> teams = pDao.getTeamsByTournamentId(tournamentId);
        if (teams == null || teams.size() < 2) {
            teams = generateDefaultTeams(tournamentId, 16);
        }

        TournamentDAO tDao = new TournamentDAO();
        Tournament tourney = tDao.getTournamentById(tournamentId);

        Map<String, List<Team>> groups = resolveGroupAssignments(tournamentId, teams, tourney);
        if (groups == null || groups.isEmpty()) return;

        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            initializeGroupsAndFixturesFromMap(conn, tournamentId, stageId, groups);
            conn.commit();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private Map<String, List<Team>> resolveGroupAssignments(String tournamentId, List<Team> allTeams, Tournament tourney) {
        // 1. Prioritize user-configured group_assignments saved in DB
        String assignmentsJson = (tourney != null) ? tourney.getGroupAssignments() : null;
        if (assignmentsJson != null && !assignmentsJson.trim().isEmpty() && !assignmentsJson.trim().equals("{}")) {
            Map<String, List<Team>> parsed = parseGroupAssignments(assignmentsJson, allTeams);
            if (parsed != null && !parsed.isEmpty()) {
                return parsed;
            }
        }

        // 2. Strict Business Logic: Do NOT auto-divide into 4-team groups. User must manually create groups in manage-group.
        return new LinkedHashMap<>();
    }

    private Map<String, List<Team>> parseGroupAssignments(String json, List<Team> allTeams) {
        Map<String, List<Team>> result = new LinkedHashMap<>();
        if (json == null || json.trim().isEmpty() || json.trim().equals("{}")) {
            return result;
        }

        Map<String, Team> teamLookup = new HashMap<>();
        for (Team t : allTeams) {
            if (t.getId() != null) {
                teamLookup.put(String.valueOf(t.getId()).trim().toLowerCase(), t);
            }
            if (t.getName() != null) {
                teamLookup.put(t.getName().trim().toLowerCase(), t);
            }
            if (t.getRawName() != null) {
                teamLookup.put(t.getRawName().trim().toLowerCase(), t);
            }
        }

        try {
            java.util.regex.Pattern groupPattern = java.util.regex.Pattern.compile("\"([^\"]+)\"\\s*:\\s*\\[([^\\]]*)\\]");
            java.util.regex.Matcher matcher = groupPattern.matcher(json);

            while (matcher.find()) {
                String rawKey = matcher.group(1).trim();
                String groupName = rawKey;
                if (groupName.length() <= 2 && !groupName.startsWith("Bảng")) {
                    groupName = "Bảng " + groupName.toUpperCase();
                }

                String arrayContent = matcher.group(2).trim();
                List<Team> groupTeams = new ArrayList<>();

                if (!arrayContent.isEmpty()) {
                    if (arrayContent.contains("{")) {
                        java.util.regex.Pattern objPattern = java.util.regex.Pattern.compile("\\{([^\\}]+)\\}");
                        java.util.regex.Matcher objMatcher = objPattern.matcher(arrayContent);
                        while (objMatcher.find()) {
                            String objBody = objMatcher.group(1);
                            String teamId = extractFieldFromJson(objBody, "id");
                            String teamName = extractFieldFromJson(objBody, "name");
                            if (teamName == null) teamName = extractFieldFromJson(objBody, "rawName");

                            Team found = null;
                            if (teamId != null) found = teamLookup.get(teamId.toLowerCase());
                            if (found == null && teamName != null) found = teamLookup.get(teamName.toLowerCase());

                            if (found != null) {
                                groupTeams.add(found);
                            } else if (teamName != null && !teamName.isEmpty()) {
                                Team placeholder = new Team();
                                placeholder.setId(teamId != null ? teamId : ("TM_" + System.currentTimeMillis()));
                                placeholder.setRawName(teamName);
                                placeholder.setNormalizedName(teamName);
                                groupTeams.add(placeholder);
                            }
                        }
                    } else {
                        String[] tokens = arrayContent.split(",");
                        for (String tk : tokens) {
                            String cleanTk = tk.replace("\"", "").replace("'", "").trim();
                            if (cleanTk.isEmpty()) continue;
                            Team found = teamLookup.get(cleanTk.toLowerCase());
                            if (found != null) {
                                groupTeams.add(found);
                            } else {
                                Team placeholder = new Team();
                                placeholder.setRawName(cleanTk);
                                placeholder.setNormalizedName(cleanTk);
                                groupTeams.add(placeholder);
                            }
                        }
                    }
                }

                if (!groupTeams.isEmpty()) {
                    result.put(groupName, groupTeams);
                }
            }
        } catch (Exception ignore) {}
        return result;
    }

    private String extractFieldFromJson(String objBody, String fieldName) {
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("\"" + fieldName + "\"\\s*:\\s*(?:\"([^\"]*)\"|([^,}\\s]+))");
        java.util.regex.Matcher m = p.matcher(objBody);
        if (m.find()) {
            String val = m.group(1);
            if (val == null) val = m.group(2);
            return (val != null) ? val.trim() : null;
        }
        return null;
    }

    private List<Team> generateDefaultTeams(String tournamentId, int count) {
        List<Team> list = new ArrayList<>();
        String insertTeamSql = "INSERT INTO teams (id, tournament_id, raw_name, normalized_name, original_seed) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(insertTeamSql)) {
            for (int i = 1; i <= count; i++) {
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

    private void initializeGroupsAndFixturesFromMap(Connection conn, String tournamentId, String stageId, Map<String, List<Team>> groups) throws Exception {
        String insGroupSql = "INSERT INTO groups (id, stage_id, group_name, qualified_slots_count) VALUES (?, ?, ?, ?)";
        String insGroupTeamSql = "INSERT INTO group_teams (id, group_id, team_id, seed_in_group, rank_in_group) VALUES (?, ?, ?, ?, ?)";
        String insMatchSql = "INSERT INTO matches (id, tournament_id, stage_id, group_id, round_number, match_order, match_code, bracket_type, "
                + "team1_id, team2_id, is_bye, status) VALUES (?, ?, ?, ?, ?, ?, ?, 'GROUP_STAGE', ?, ?, 0, 'READY')";

        int matchSeq = 1;

        for (Map.Entry<String, List<Team>> entry : groups.entrySet()) {
            String gName = entry.getKey();
            String gSuffix = gName.replaceAll("[^a-zA-Z0-9]", "_").toUpperCase();
            if (gSuffix.isEmpty()) gSuffix = "GA";
            String gId = "GRP_" + tournamentId + "_" + gSuffix;

            try (PreparedStatement psG = conn.prepareStatement(insGroupSql)) {
                psG.setString(1, gId);
                psG.setString(2, stageId);
                psG.setString(3, gName);
                psG.setInt(4, 2);
                psG.executeUpdate();
            }

            List<Team> grpTeams = entry.getValue();
            for (int t = 0; t < grpTeams.size(); t++) {
                Team tm = grpTeams.get(t);
                try (PreparedStatement psGT = conn.prepareStatement(insGroupTeamSql)) {
                    psGT.setString(1, "GT_" + gId + "_" + (t + 1));
                    psGT.setString(2, gId);
                    psGT.setString(3, tm.getId());
                    psGT.setInt(4, t + 1);
                    psGT.setInt(5, t + 1);
                    psGT.executeUpdate();
                }
            }

            // Generate round robin fixtures for this group
            int n = grpTeams.size();
            int rNum = 1;
            for (int i = 0; i < n; i++) {
                for (int j = i + 1; j < n; j++) {
                    Team t1 = grpTeams.get(i);
                    Team t2 = grpTeams.get(j);
                    String mId = "M_" + tournamentId + "_G_" + gSuffix + "_" + (rNum);

                    try (PreparedStatement psM = conn.prepareStatement(insMatchSql)) {
                        psM.setString(1, mId);
                        psM.setString(2, tournamentId);
                        psM.setString(3, stageId);
                        psM.setString(4, gId);
                        psM.setInt(5, rNum);
                        psM.setInt(6, matchSeq++);
                        psM.setString(7, gName + " - Trận " + rNum);
                        psM.setString(8, t1.getId());
                        psM.setString(9, t2.getId());
                        psM.executeUpdate();
                    }
                    rNum++;
                }
            }
        }
    }

    // ------------------------------------------------------------------------
    // 2. DATA QUERIES (GROUPS, STANDINGS, MATCHES JSON)
    // ------------------------------------------------------------------------

    public List<GroupDTO> getGroupsWithTeams(String tournamentId, int stageOrder) {
        List<GroupDTO> groups = new ArrayList<>();
        if (tournamentId == null || tournamentId.trim().isEmpty()) return groups;

        ensureGroupStageInitialized(tournamentId, stageOrder);

        String stageId = lookupOrCreateStageId(tournamentId, stageOrder);

        String sql = "SELECT g.id AS group_id, g.group_name, g.qualified_slots_count, "
                + "gt.team_id, t.raw_name AS team_name, gt.seed_in_group, "
                + "gt.matches_played, gt.wins, gt.draws, gt.losses, "
                + "gt.goals_scored, gt.goals_conceded, gt.goal_difference, gt.points, gt.rank_in_group "
                + "FROM groups g "
                + "LEFT JOIN group_teams gt ON g.id = gt.group_id "
                + "LEFT JOIN teams t ON gt.team_id = t.id "
                + "WHERE g.stage_id = ? "
                + "ORDER BY g.group_name ASC, gt.rank_in_group ASC, gt.points DESC, gt.seed_in_group ASC";

        Map<String, GroupDTO> groupMap = new HashMap<>();

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, stageId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String gId = rs.getString("group_id");
                    GroupDTO g = groupMap.get(gId);
                    if (g == null) {
                        g = new GroupDTO();
                        g.groupId = gId;
                        g.groupName = rs.getString("group_name");
                        g.qualifiedSlots = rs.getInt("qualified_slots_count");
                        groupMap.put(gId, g);
                        groups.add(g);
                    }

                    String tmId = rs.getString("team_id");
                    if (tmId != null) {
                        GroupTeamDTO gt = new GroupTeamDTO();
                        gt.teamId = tmId;
                        gt.teamName = rs.getString("team_name");
                        gt.seedInGroup = rs.getInt("seed_in_group");
                        gt.matchesPlayed = rs.getInt("matches_played");
                        gt.wins = rs.getInt("wins");
                        gt.draws = rs.getInt("draws");
                        gt.losses = rs.getInt("losses");
                        gt.goalsScored = rs.getInt("goals_scored");
                        gt.goalsConceded = rs.getInt("goals_conceded");
                        gt.goalDifference = rs.getInt("goal_difference");
                        gt.points = rs.getInt("points");
                        gt.rankInGroup = rs.getInt("rank_in_group");
                        g.teams.add(gt);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return groups;
    }

    public String getMatchesJsonForFrontend(String tournamentId, int stageOrder) {
        if (tournamentId == null || tournamentId.trim().isEmpty()) return "[]";

        ensureGroupStageInitialized(tournamentId, stageOrder);

        String sql = "SELECT m.*, "
                + "t1.raw_name AS t1_name, t1.original_seed AS t1_seed, "
                + "t2.raw_name AS t2_name, t2.original_seed AS t2_seed, "
                + "tw.raw_name AS winner_name, g.group_name "
                + "FROM matches m "
                + "LEFT JOIN groups g ON m.group_id = g.id "
                + "LEFT JOIN tournament_stages s ON m.stage_id = s.id "
                + "LEFT JOIN teams t1 ON m.team1_id = t1.id "
                + "LEFT JOIN teams t2 ON m.team2_id = t2.id "
                + "LEFT JOIN teams tw ON m.winner_id = tw.id "
                + "WHERE m.tournament_id = ? AND (s.stage_order = ? OR (s.stage_order IS NULL AND ? = 1) OR m.stage_id LIKE '%_S' + CAST(? AS VARCHAR) + '%' OR m.stage_id = 'STAGE_' + CAST(? AS VARCHAR)) "
                + "ORDER BY g.group_name ASC, m.round_number ASC, m.id ASC";

        StringBuilder sb = new StringBuilder("[");
        int count = 0;

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tournamentId);
            ps.setInt(2, stageOrder);
            ps.setInt(3, stageOrder);
            ps.setInt(4, stageOrder);
            ps.setInt(5, stageOrder);
            try (ResultSet rs = ps.executeQuery()) {
                int seq = 1;
                while (rs.next()) {
                    if (count > 0) sb.append(",");
                    count++;

                    String rawId = rs.getString("id");
                    int matchOrder = rs.getInt("match_order");
                    int matchNum = (matchOrder > 0) ? matchOrder : seq++;
                    int roundNumber = rs.getInt("round_number");
                    String grpName = rs.getString("group_name");
                    String grpId = rs.getString("group_id");

                    // Extract single letter group key (e.g., "A", "B")
                    String groupKey = "A";
                    if (grpName != null && grpName.contains(" ")) {
                        String[] parts = grpName.split(" ");
                        if (parts.length > 1) groupKey = parts[parts.length - 1];
                    } else if (grpId != null && grpId.contains("_")) {
                        String[] parts = grpId.split("_");
                        if (parts.length > 0) groupKey = parts[parts.length - 1];
                    }

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
                        else if ("draw".equalsIgnoreCase(winnerIdCol)) winnerSlot = "draw";
                    }

                    boolean isBye = rs.getBoolean("is_bye");
                    String status = rs.getString("status");
                    if ("FINISHED".equalsIgnoreCase(status)) status = "COMPLETED";

                    sb.append("{")
                            .append("\"matchId\":\"").append(escapeJson(rawId)).append("\",")
                            .append("\"id\":\"").append(escapeJson(rawId)).append("\",")
                            .append("\"rawId\":\"").append(escapeJson(rawId)).append("\",")
                            .append("\"matchKey\":\"").append(escapeJson(rawId)).append("\",")
                            .append("\"groupId\":\"").append(escapeJson(grpId != null ? grpId : "")).append("\",")
                            .append("\"groupName\":\"").append(escapeJson(grpName != null ? grpName : "")).append("\",")
                            .append("\"groupKey\":\"").append(escapeJson(groupKey)).append("\",")
                            .append("\"roundNumber\":").append(roundNumber).append(",")
                            .append("\"roundIndex\":").append(roundNumber).append(",")
                            .append("\"matchNumber\":").append(matchNum).append(",")
                            .append("\"score1\":\"").append(escapeJson(s1)).append("\",")
                            .append("\"score2\":\"").append(escapeJson(s2)).append("\",")
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

    // ------------------------------------------------------------------------
    // 3. MATCH SCORING & STANDINGS RECALCULATION
    // ------------------------------------------------------------------------

    public boolean saveMatchScore(String tournamentId, int stageOrder, String matchId, int score1, int score2, String winnerId) {
        if (tournamentId == null || matchId == null) return false;

        String findSql = "SELECT id, group_id, team1_id, team2_id FROM matches WHERE tournament_id = ? AND "
                + "(id = ? OR id LIKE '%[_]' + ? OR id LIKE '%' + ?)";
        String updateSql = "UPDATE matches SET score1 = ?, score2 = ?, winner_id = ?, loser_id = ?, status = 'FINISHED' WHERE id = ?";

        try (Connection conn = getConnection()) {
            String actualMatchId = null;
            String groupId = null;
            String actualT1 = null;
            String actualT2 = null;

            try (PreparedStatement psFind = conn.prepareStatement(findSql)) {
                psFind.setString(1, tournamentId);
                psFind.setString(2, matchId);
                psFind.setString(3, matchId);
                psFind.setString(4, matchId);
                try (ResultSet rs = psFind.executeQuery()) {
                    if (rs.next()) {
                        actualMatchId = rs.getString("id");
                        groupId = rs.getString("group_id");
                        actualT1 = rs.getString("team1_id");
                        actualT2 = rs.getString("team2_id");
                    }
                }
            }

            if (actualMatchId == null) return false;

            String resolvedWinner = null;
            String resolvedLoser = null;
            if ("team1".equalsIgnoreCase(winnerId)) {
                resolvedWinner = actualT1;
                resolvedLoser = actualT2;
            } else if ("team2".equalsIgnoreCase(winnerId)) {
                resolvedWinner = actualT2;
                resolvedLoser = actualT1;
            } else if ("draw".equalsIgnoreCase(winnerId) || score1 == score2) {
                resolvedWinner = null;
                resolvedLoser = null;
            } else if (winnerId != null && !winnerId.trim().isEmpty() && !"null".equalsIgnoreCase(winnerId)) {
                resolvedWinner = winnerId.trim();
                if (resolvedWinner.equals(actualT1)) resolvedLoser = actualT2;
                else if (resolvedWinner.equals(actualT2)) resolvedLoser = actualT1;
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
                if (resolvedWinner != null && !resolvedWinner.isEmpty()) ps.setString(3, resolvedWinner);
                else ps.setNull(3, Types.VARCHAR);

                if (resolvedLoser != null && !resolvedLoser.isEmpty()) ps.setString(4, resolvedLoser);
                else ps.setNull(4, Types.VARCHAR);

                ps.setString(5, actualMatchId);
                ps.executeUpdate();
            }

            if (groupId != null) {
                recalculateGroupStandings(conn, groupId);
            }

            checkAndFinishGroupStageInDB(tournamentId, stageOrder);
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

    public void recalculateGroupStandings(Connection conn, String groupId) {
        if (groupId == null || groupId.trim().isEmpty()) return;

        String selectMatchesSql = "SELECT team1_id, team2_id, score1, score2, winner_id, status FROM matches "
                + "WHERE group_id = ? AND status = 'FINISHED' AND score1 IS NOT NULL AND score2 IS NOT NULL";

        String selectTeamsSql = "SELECT team_id, seed_in_group FROM group_teams WHERE group_id = ?";
        String updateGroupTeamSql = "UPDATE group_teams SET matches_played = ?, wins = ?, draws = ?, losses = ?, "
                + "goals_scored = ?, goals_conceded = ?, goal_difference = ?, points = ?, rank_in_group = ? WHERE group_id = ? AND team_id = ?";

        Map<String, GroupTeamDTO> teamStats = new HashMap<>();

        try (PreparedStatement psT = conn.prepareStatement(selectTeamsSql)) {
            psT.setString(1, groupId);
            try (ResultSet rsT = psT.executeQuery()) {
                while (rsT.next()) {
                    GroupTeamDTO gt = new GroupTeamDTO();
                    gt.teamId = rsT.getString("team_id");
                    gt.seedInGroup = rsT.getInt("seed_in_group");
                    teamStats.put(gt.teamId, gt);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

        try (PreparedStatement psM = conn.prepareStatement(selectMatchesSql)) {
            psM.setString(1, groupId);
            try (ResultSet rsM = psM.executeQuery()) {
                while (rsM.next()) {
                    String t1 = rsM.getString("team1_id");
                    String t2 = rsM.getString("team2_id");
                    int s1 = rsM.getInt("score1");
                    int s2 = rsM.getInt("score2");

                    GroupTeamDTO st1 = teamStats.get(t1);
                    GroupTeamDTO st2 = teamStats.get(t2);

                    if (st1 != null && st2 != null) {
                        st1.matchesPlayed++;
                        st2.matchesPlayed++;
                        st1.goalsScored += s1;
                        st1.goalsConceded += s2;
                        st2.goalsScored += s2;
                        st2.goalsConceded += s1;

                        if (s1 > s2) {
                            st1.wins++;
                            st1.points += 3;
                            st2.losses++;
                        } else if (s2 > s1) {
                            st2.wins++;
                            st2.points += 3;
                            st1.losses++;
                        } else {
                            st1.draws++;
                            st1.points += 1;
                            st2.draws++;
                            st2.points += 1;
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

        // Sort teams by points DESC -> GD DESC -> GF DESC -> seed ASC
        List<GroupTeamDTO> sortedList = new ArrayList<>(teamStats.values());
        for (GroupTeamDTO gt : sortedList) {
            gt.goalDifference = gt.goalsScored - gt.goalsConceded;
        }

        sortedList.sort((a, b) -> {
            if (b.points != a.points) return b.points - a.points;
            if (b.goalDifference != a.goalDifference) return b.goalDifference - a.goalDifference;
            if (b.goalsScored != a.goalsScored) return b.goalsScored - a.goalsScored;
            return a.seedInGroup - b.seedInGroup;
        });

        int rank = 1;
        try (PreparedStatement psU = conn.prepareStatement(updateGroupTeamSql)) {
            for (GroupTeamDTO gt : sortedList) {
                gt.rankInGroup = rank++;
                psU.setInt(1, gt.matchesPlayed);
                psU.setInt(2, gt.wins);
                psU.setInt(3, gt.draws);
                psU.setInt(4, gt.losses);
                psU.setInt(5, gt.goalsScored);
                psU.setInt(6, gt.goalsConceded);
                psU.setInt(7, gt.goalDifference);
                psU.setInt(8, gt.points);
                psU.setInt(9, gt.rankInGroup);
                psU.setString(10, groupId);
                psU.setString(11, gt.teamId);
                psU.addBatch();
            }
            psU.executeBatch();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void checkAndFinishGroupStageInDB(String tournamentId, int stageOrder) {
        if (tournamentId == null || tournamentId.trim().isEmpty()) return;

        String checkSql = "SELECT COUNT(*) FROM matches m "
                + "LEFT JOIN tournament_stages s ON m.stage_id = s.id "
                + "WHERE m.tournament_id = ? AND (s.stage_order = ? OR (s.stage_order IS NULL AND ? = 1)) "
                + "AND (m.status != 'FINISHED' OR m.score1 IS NULL OR m.score2 IS NULL)";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(checkSql)) {
            ps.setString(1, tournamentId);
            ps.setInt(2, stageOrder);
            ps.setInt(3, stageOrder);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next() && rs.getInt(1) == 0) {
                    // ALL MATCHES IN GROUP STAGE COMPLETED!
                    finishGroupStageAdvancement(tournamentId, stageOrder);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void finishGroupStageAdvancement(String tournamentId, int stageOrder) {
        List<GroupDTO> groups = getGroupsWithTeams(tournamentId, stageOrder);
        if (groups.isEmpty()) return;

        List<GroupTeamDTO> qualifiedList = new ArrayList<>();

        // Group Stage advancement logic: Top 2 from each group (or qualifiedSlots count)
        for (GroupDTO g : groups) {
            int slots = g.qualifiedSlots > 0 ? g.qualifiedSlots : 2;
            for (int i = 0; i < Math.min(slots, g.teams.size()); i++) {
                qualifiedList.add(g.teams.get(i));
            }
        }

        StringBuilder jsonSb = new StringBuilder("[");
        for (int i = 0; i < qualifiedList.size(); i++) {
            if (i > 0) jsonSb.append(",");
            GroupTeamDTO gt = qualifiedList.get(i);
            jsonSb.append("{\"id\":\"").append(escapeJson(gt.teamId))
                    .append("\",\"name\":\"").append(escapeJson(gt.teamName))
                    .append("\",\"seed\":").append(i + 1).append("}");
        }
        jsonSb.append("]");

        TournamentDAO tDao = new TournamentDAO();
        tDao.saveStage2Teams(tournamentId, jsonSb.toString());
        tDao.updateTournamentStage1Status(tournamentId, "COMPLETED");
    }

    // ------------------------------------------------------------------------
    // 4. RANDOM & RESET OPERATIONS
    // ------------------------------------------------------------------------

    public boolean randomGroupMatchesInDB(String tournamentId, int stageOrder, String groupId, int maxScore) {
        if (tournamentId == null) return false;
        ensureGroupStageInitialized(tournamentId, stageOrder);

        String selSql = "SELECT id, team1_id, team2_id, group_id FROM matches "
                + "WHERE tournament_id = ? " + (groupId != null ? "AND group_id = ? " : "") + "AND (score1 IS NULL OR status != 'FINISHED')";
        String updateSql = "UPDATE matches SET score1 = ?, score2 = ?, winner_id = ?, loser_id = ?, status = 'FINISHED' WHERE id = ?";

        Random rand = new Random();
        int maxS = maxScore > 0 ? maxScore : 4;

        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            List<String[]> matchesToUpdate = new ArrayList<>();
            Set<String> affectedGroups = new HashSet<>();

            try (PreparedStatement psSel = conn.prepareStatement(selSql)) {
                psSel.setString(1, tournamentId);
                if (groupId != null) psSel.setString(2, groupId);
                try (ResultSet rs = psSel.executeQuery()) {
                    while (rs.next()) {
                        matchesToUpdate.add(new String[]{
                                rs.getString("id"),
                                rs.getString("team1_id"),
                                rs.getString("team2_id"),
                                rs.getString("group_id")
                        });
                        affectedGroups.add(rs.getString("group_id"));
                    }
                }
            }

            try (PreparedStatement psUp = conn.prepareStatement(updateSql)) {
                for (String[] m : matchesToUpdate) {
                    int s1 = rand.nextInt(maxS + 1);
                    int s2 = rand.nextInt(maxS + 1);
                    String winId = null;
                    String loseId = null;

                    if (s1 > s2) {
                        winId = m[1];
                        loseId = m[2];
                    } else if (s2 > s1) {
                        winId = m[2];
                        loseId = m[1];
                    }

                    psUp.setInt(1, s1);
                    psUp.setInt(2, s2);
                    if (winId != null) psUp.setString(3, winId); else psUp.setNull(3, Types.VARCHAR);
                    if (loseId != null) psUp.setString(4, loseId); else psUp.setNull(4, Types.VARCHAR);
                    psUp.setString(5, m[0]);
                    psUp.addBatch();
                }
                psUp.executeBatch();
            }

            // Recalculate standings for all affected groups
            for (String gId : affectedGroups) {
                if (gId != null) recalculateGroupStandings(conn, gId);
            }

            conn.commit();
            checkAndFinishGroupStageInDB(tournamentId, stageOrder);
            service.RollingWindowPointService.clearAllCaches();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean randomAllMatchesInDB(String tournamentId, int stageOrder, int maxScore) {
        return randomGroupMatchesInDB(tournamentId, stageOrder, null, maxScore);
    }

    public boolean resetGroupMatchesInDB(String tournamentId, int stageOrder, String groupId) {
        if (tournamentId == null || tournamentId.trim().isEmpty()) return false;
        String resetMatchesSql = "UPDATE matches SET score1 = NULL, score2 = NULL, penalty1 = NULL, penalty2 = NULL, winner_id = NULL, loser_id = NULL, status = 'SCHEDULED' "
                + "WHERE tournament_id = ? " + (groupId != null ? "AND group_id = ? " : "");

        String resetGroupTeamsSql = "UPDATE gt SET matches_played = 0, wins = 0, draws = 0, losses = 0, "
                + "goals_scored = 0, goals_conceded = 0, goal_difference = 0, points = 0, rank_in_group = seed_in_group "
                + "FROM group_teams gt JOIN groups g ON gt.group_id = g.id "
                + "WHERE g.tournament_id = ? " + (groupId != null ? "AND g.id = ? " : "");

        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement psM = conn.prepareStatement(resetMatchesSql)) {
                psM.setString(1, tournamentId);
                if (groupId != null) psM.setString(2, groupId);
                psM.executeUpdate();
            }
            try (PreparedStatement psGT = conn.prepareStatement(resetGroupTeamsSql)) {
                psGT.setString(1, tournamentId);
                if (groupId != null) psGT.setString(2, groupId);
                psGT.executeUpdate();
            }
            conn.commit();
            service.RollingWindowPointService.clearAllCaches();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean resetBracketMatches(String tournamentId, int stageOrder) {
        if (tournamentId == null || tournamentId.trim().isEmpty()) return false;
        String sql = "UPDATE m SET m.score1 = NULL, m.score2 = NULL, m.penalty1 = NULL, m.penalty2 = NULL, "
                + "m.winner_id = NULL, m.loser_id = NULL, m.status = 'READY' "
                + "FROM matches m "
                + "LEFT JOIN tournament_stages s ON m.stage_id = s.id "
                + "WHERE m.tournament_id = ? AND (s.stage_order = ? OR (s.stage_order IS NULL AND ? = 1) OR m.stage_id LIKE '%_S' + CAST(? AS VARCHAR) + '%' OR m.stage_id = 'STAGE_' + CAST(? AS VARCHAR))";

        String resetGroupTeamsSql = "UPDATE gt SET matches_played = 0, wins = 0, draws = 0, losses = 0, "
                + "goals_scored = 0, goals_conceded = 0, goal_difference = 0, points = 0, rank_in_group = seed_in_group "
                + "FROM group_teams gt JOIN groups g ON gt.group_id = g.id "
                + "JOIN tournament_stages s ON g.stage_id = s.id "
                + "WHERE s.tournament_id = ? AND (s.stage_order = ? OR (s.stage_order IS NULL AND ? = 1))";

        String resetTourneySql = "UPDATE tournaments SET status = 'DRAFT', champion_name = NULL, stage2_teams = NULL, stage1_status = 'PENDING' WHERE id = ?";

        try (Connection conn = getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, tournamentId);
                ps.setInt(2, stageOrder);
                ps.setInt(3, stageOrder);
                ps.setInt(4, stageOrder);
                ps.setInt(5, stageOrder);
                ps.executeUpdate();
            }

            try (PreparedStatement psGT = conn.prepareStatement(resetGroupTeamsSql)) {
                psGT.setString(1, tournamentId);
                psGT.setInt(2, stageOrder);
                psGT.setInt(3, stageOrder);
                psGT.executeUpdate();
            }

            try (PreparedStatement psT = conn.prepareStatement(resetTourneySql)) {
                psT.setString(1, tournamentId);
                psT.executeUpdate();
            } catch (Exception ignore) {}

            service.RollingWindowPointService.clearAllCaches();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
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
            ps.setString(5, "GROUP_STAGE");
            ps.executeUpdate();
            return newStageId;
        } catch (Exception ignore) {}

        return "STAGE_1";
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

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }
}
