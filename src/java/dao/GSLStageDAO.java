package dao;

import model.Match;
import model.Team;
import model.Tournament;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * High-Performance, Database-Driven DAO for GSL Format (Dual Tournament /
 * Multi-Group Scaled Double Elimination).
 * Supports 4, 8, 16, 32 teams per group with power-of-two group counts and
 * advancing slots.
 */
public class GSLStageDAO extends DBContext {

    public String getMatchesJsonForFrontend(String tournamentId, int stageOrder) {
        StringBuilder sb = new StringBuilder("[");
        if (tournamentId == null || tournamentId.trim().isEmpty()) {
            sb.append("]");
            return sb.toString();
        }

        String sql = "SELECT m.*, "
                + "ISNULL(g.group_name, ISNULL(m.group_id, 'Bảng A')) AS display_group_name, "
                + "t1.raw_name AS t1_name, t1.original_seed AS t1_seed, "
                + "t2.raw_name AS t2_name, t2.original_seed AS t2_seed, "
                + "tw.raw_name AS winner_name "
                + "FROM matches m "
                + "LEFT JOIN groups g ON (m.group_id = g.id OR m.group_id = g.group_name) "
                + "LEFT JOIN teams t1 ON m.team1_id = t1.id "
                + "LEFT JOIN teams t2 ON m.team2_id = t2.id "
                + "LEFT JOIN teams tw ON m.winner_id = tw.id "
                + "LEFT JOIN tournament_stages s ON m.stage_id = s.id "
                + "WHERE m.tournament_id = ? AND (s.stage_order = ? OR (s.stage_order IS NULL AND ? = 1) OR m.stage_id LIKE '%_S' + CAST(? AS VARCHAR) + '%' OR m.stage_id = 'STAGE_' + CAST(? AS VARCHAR)) "
                + "ORDER BY LEN(ISNULL(g.group_name, m.group_id)) ASC, ISNULL(g.group_name, m.group_id) ASC, m.round_number ASC, ISNULL(m.match_order, 999999) ASC, LEN(m.id) ASC, m.id ASC";

        try (Connection conn = getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tournamentId);
            ps.setInt(2, stageOrder);
            ps.setInt(3, stageOrder);
            ps.setInt(4, stageOrder);
            ps.setInt(5, stageOrder);

            try (ResultSet rs = ps.executeQuery()) {
                boolean first = true;
                int seq = 1;
                while (rs.next()) {
                    if (!first)
                        sb.append(",");
                    first = false;

                    String rawId = rs.getString("id");
                    String rawGroupId = rs.getString("display_group_name");
                    String groupId = formatDisplayGroupName(rawGroupId);
                    if (groupId == null || groupId.trim().isEmpty())
                        groupId = "Bảng A";
                    int matchNum = rs.getInt("match_order");
                    if (rs.wasNull() || matchNum <= 0)
                        matchNum = seq++;

                    int roundNumber = rs.getInt("round_number");
                    String bracketType = rs.getString("bracket_type");

                    String t1Name = rs.getString("t1_name");
                    String t1Seed = "";
                    int seed1Val = rs.getInt("t1_seed");
                    if (!rs.wasNull() && seed1Val > 0)
                        t1Seed = "#" + seed1Val;

                    String t2Name = rs.getString("t2_name");
                    String t2Seed = "";
                    int seed2Val = rs.getInt("t2_seed");
                    if (!rs.wasNull() && seed2Val > 0)
                        t2Seed = "#" + seed2Val;

                    String s1 = "";
                    int s1Val = rs.getInt("score1");
                    if (!rs.wasNull())
                        s1 = String.valueOf(s1Val);

                    String s2 = "";
                    int s2Val = rs.getInt("score2");
                    if (!rs.wasNull())
                        s2 = String.valueOf(s2Val);

                    String winnerIdCol = rs.getString("winner_id");
                    String winnerSlot = "";
                    if (winnerIdCol != null && !winnerIdCol.trim().isEmpty()) {
                        String wid = winnerIdCol.trim();
                        if (wid.equalsIgnoreCase("team1") || wid.equals("1") || wid.equals(rs.getString("team1_id"))) {
                            winnerSlot = "team1";
                        } else if (wid.equalsIgnoreCase("team2") || wid.equals("2")
                                || wid.equals(rs.getString("team2_id"))) {
                            winnerSlot = "team2";
                        }
                    }
                    if (winnerSlot.isEmpty() && !s1.isEmpty() && !s2.isEmpty()) {
                        if (s1Val > s2Val)
                            winnerSlot = "team1";
                        else if (s2Val > s1Val)
                            winnerSlot = "team2";
                    }

                    String nextIdStr = rs.getString("next_match_id");
                    String nextSlot = rs.getString("next_slot");
                    int nextSlotNum = "SLOT_2".equalsIgnoreCase(nextSlot) ? 2 : 1;

                    String dropIdStr = rs.getString("loser_next_match_id");
                    String dropSlot = rs.getString("loser_next_slot");
                    int dropSlotNum = "SLOT_2".equalsIgnoreCase(dropSlot) ? 2 : 1;

                    boolean isBye = rs.getBoolean("is_bye");
                    String status = rs.getString("status");
                    if ("FINISHED".equalsIgnoreCase(status) || "DONE".equalsIgnoreCase(status)
                            || "COMPLETED".equalsIgnoreCase(status) || !winnerSlot.isEmpty()
                            || (!s1.isEmpty() && !s2.isEmpty())) {
                        status = "COMPLETED";
                    } else if (status == null || status.trim().isEmpty()) {
                        status = "SCHEDULED";
                    }

                    sb.append("{")
                            .append("\"matchId\":\"").append(escapeJson(rawId)).append("\",")
                            .append("\"id\":\"").append(escapeJson(rawId)).append("\",")
                            .append("\"rawId\":\"").append(escapeJson(rawId)).append("\",")
                            .append("\"groupId\":\"").append(escapeJson(groupId)).append("\",")
                            .append("\"bracketType\":\"")
                            .append(escapeJson(bracketType != null ? bracketType : "WINNER_BRACKET")).append("\",")
                            .append("\"roundNumber\":").append(roundNumber).append(",")
                            .append("\"matchNumber\":").append(matchNum).append(",")
                            .append("\"team1\":{")
                            .append("\"name\":\"").append(escapeJson(t1Name != null ? t1Name : "")).append("\",")
                            .append("\"seed\":\"").append(escapeJson(t1Seed)).append("\",")
                            .append("\"score\":\"").append(escapeJson(s1)).append("\"")
                            .append("},")
                            .append("\"team2\":{")
                            .append("\"name\":\"").append(escapeJson(t2Name != null ? t2Name : "")).append("\",")
                            .append("\"seed\":\"").append(escapeJson(t2Seed)).append("\",")
                            .append("\"score\":\"").append(escapeJson(s2)).append("\"")
                            .append("},")
                            .append("\"winnerId\":").append(winnerSlot.isEmpty() ? "null" : "\"" + winnerSlot + "\"")
                            .append(",")
                            .append("\"nextMatchId\":")
                            .append(nextIdStr != null && !nextIdStr.isEmpty() ? "\"" + escapeJson(nextIdStr) + "\""
                                    : "null")
                            .append(",")
                            .append("\"nextMatchSlot\":").append(nextSlotNum).append(",")
                            .append("\"loserNextMatchId\":")
                            .append(dropIdStr != null && !dropIdStr.isEmpty() ? "\"" + escapeJson(dropIdStr) + "\""
                                    : "null")
                            .append(",")
                            .append("\"loserNextSlot\":").append(dropSlotNum).append(",")
                            .append("\"isBye\":").append(isBye).append(",")
                            .append("\"status\":\"").append(escapeJson(status != null ? status : "SCHEDULED"))
                            .append("\"")
                            .append("}");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        sb.append("]");
        return sb.toString();
    }

    public synchronized void ensureGSLInitialized(String tournamentId, int stageOrder) {
        if (tournamentId == null || tournamentId.trim().isEmpty())
            return;

        ParticipantDAO pDao = new ParticipantDAO();
        List<Team> allTeams = pDao.getTeamsByTournamentId(tournamentId);
        if (allTeams == null || allTeams.isEmpty())
            return;

        TournamentDAO tDao = new TournamentDAO();
        Tournament tourney = tDao.getTournamentById(tournamentId);

        Map<String, List<Team>> groups = resolveGroupAssignments(tournamentId, allTeams, tourney);
        if (groups == null || groups.isEmpty())
            return;

        int expectedGroups = groups.size();

        // Check if DB already contains valid GSL matches for ALL expected groups
        String checkSql = "SELECT COUNT(*) AS total_matches, "
                + "COUNT(DISTINCT ISNULL(g.group_name, m.group_id)) AS distinct_groups, "
                + "SUM(CASE WHEN m.group_id IS NULL OR m.group_id = '' THEN 1 ELSE 0 END) AS null_groups, "
                + "SUM(CASE WHEN m.id LIKE '%_UB_%' OR m.id LIKE '%_LB_%' THEN 1 ELSE 0 END) AS gsl_matches "
                + "FROM matches m "
                + "LEFT JOIN groups g ON (m.group_id = g.id OR m.group_id = g.group_name) "
                + "LEFT JOIN tournament_stages s ON m.stage_id = s.id "
                + "WHERE m.tournament_id = ? AND (s.stage_order = ? OR (s.stage_order IS NULL AND ? = 1) OR m.stage_id LIKE '%_S' + CAST(? AS VARCHAR) + '%' OR m.stage_id = 'STAGE_' + CAST(? AS VARCHAR))";

        boolean alreadyInitialized = false;
        try (Connection conn = getConnection();
                PreparedStatement ps = conn.prepareStatement(checkSql)) {
            ps.setString(1, tournamentId);
            ps.setInt(2, stageOrder);
            ps.setInt(3, stageOrder);
            ps.setInt(4, stageOrder);
            ps.setInt(5, stageOrder);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    int total = rs.getInt("total_matches");
                    int distinctGrp = rs.getInt("distinct_groups");
                    int nullGrp = rs.getInt("null_groups");
                    int gslCount = rs.getInt("gsl_matches");

                    if (total > 0 && distinctGrp >= expectedGroups && nullGrp == 0 && gslCount > 0) {
                        alreadyInitialized = true;
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (alreadyInitialized) {
            return;
        }

        int numGroups = groups.size();
        int advancePerGroup = 2; // Default GSL top 2
        if (tourney != null && tourney.getAdvancingSeatsCount() > 0) {
            advancePerGroup = Math.max(1, tourney.getAdvancingSeatsCount() / Math.max(1, numGroups));
        }

        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try {
                String stageId = getOrCreateStageId(conn, tournamentId, stageOrder);

                // Clear any incomplete / stale matches before generating new GSL bracket
                try (PreparedStatement psUnlink = conn.prepareStatement(
                        "UPDATE matches SET next_match_id = NULL, loser_next_match_id = NULL WHERE tournament_id = ?")) {
                    psUnlink.setString(1, tournamentId);
                    psUnlink.executeUpdate();
                }

                try (PreparedStatement psDel = conn.prepareStatement(
                        "DELETE FROM matches WHERE tournament_id = ?")) {
                    psDel.setString(1, tournamentId);
                    psDel.executeUpdate();
                }

                int globalMatchSeq = 1;
                for (Map.Entry<String, List<Team>> entry : groups.entrySet()) {
                    String groupKey = entry.getKey();
                    List<Team> groupTeams = entry.getValue();
                    if (groupTeams == null || groupTeams.size() < 2)
                        continue;

                    String gPrefix = groupKey.replaceAll("[^a-zA-Z0-9]", "_").toUpperCase();
                    if (gPrefix.isEmpty())
                        gPrefix = "GA";
                    String gId = "GRP_" + tournamentId + "_" + gPrefix;

                    // 1. Sync groups table
                    String insGroupSql = "IF NOT EXISTS (SELECT 1 FROM groups WHERE id = ?) "
                            + "INSERT INTO groups (id, stage_id, group_name, qualified_slots_count) VALUES (?, ?, ?, ?) "
                            + "ELSE UPDATE groups SET stage_id = ?, group_name = ?, qualified_slots_count = ? WHERE id = ?";
                    try (PreparedStatement psG = conn.prepareStatement(insGroupSql)) {
                        psG.setString(1, gId);
                        psG.setString(2, gId);
                        psG.setString(3, stageId);
                        psG.setString(4, groupKey);
                        psG.setInt(5, advancePerGroup);
                        psG.setString(6, stageId);
                        psG.setString(7, groupKey);
                        psG.setInt(8, advancePerGroup);
                        psG.setString(9, gId);
                        psG.executeUpdate();
                    }

                    // 2. Sync group_teams table
                    try (PreparedStatement psDelGT = conn
                            .prepareStatement("DELETE FROM group_teams WHERE group_id = ?")) {
                        psDelGT.setString(1, gId);
                        psDelGT.executeUpdate();
                    }
                    try (PreparedStatement psGT = conn.prepareStatement(
                            "INSERT INTO group_teams (id, group_id, team_id, seed_in_group, rank_in_group) VALUES (?, ?, ?, ?, ?)")) {
                        for (int tIdx = 0; tIdx < groupTeams.size(); tIdx++) {
                            Team gt = groupTeams.get(tIdx);
                            if (gt.getId() == null)
                                continue;
                            psGT.setString(1, "GT_" + gId + "_" + (tIdx + 1));
                            psGT.setString(2, gId);
                            psGT.setString(3, gt.getId());
                            psGT.setInt(4, tIdx + 1);
                            psGT.setInt(5, tIdx + 1);
                            psGT.addBatch();
                        }
                        psGT.executeBatch();
                    }

                    // 3. Generate matches linked to gId
                    globalMatchSeq = generateAndInsertGSLGroupMatches(conn, tournamentId, stageId, gId, groupKey,
                            groupTeams, advancePerGroup, globalMatchSeq);
                }

                conn.commit();
            } catch (Exception ex) {
                conn.rollback();
                ex.printStackTrace();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static String getGroupSuffix(int index) {
        if (index < 26) {
            return String.valueOf((char) ('A' + index));
        }
        int first = (index - 26) / 26;
        int second = (index - 26) % 26;
        return "" + (char) ('A' + first) + (char) ('A' + second);
    }

    public static String getGroupNameForIndex(int index) {
        return "Bảng " + getGroupSuffix(index);
    }

    public static String formatDisplayGroupName(String raw) {
        if (raw == null || raw.trim().isEmpty())
            return "Bảng A";
        String s = raw.trim();
        if (s.startsWith("Bảng ") || s.startsWith("Bảng")) {
            return s;
        }
        if (s.startsWith("Group ")) {
            return "Bảng " + s.substring(6).trim();
        }

        java.util.regex.Pattern p = java.util.regex.Pattern.compile("(?:B_NG_|GRP_.*_B_NG_|GRP_.*_G|GRP_.*_|Group_|Group|Bảng_|Bảng|G)?([A-Za-z]+|[0-9]+)$", java.util.regex.Pattern.CASE_INSENSITIVE);
        java.util.regex.Matcher m = p.matcher(s);
        if (m.find() && m.group(1) != null && !m.group(1).isEmpty()) {
            return "Bảng " + m.group(1).toUpperCase();
        }

        return s;
    }

    private Map<String, List<Team>> resolveGroupAssignments(String tournamentId, List<Team> allTeams,
            Tournament tourney) {
        Map<String, List<Team>> groupMap = new LinkedHashMap<>();

        // 1. Check if group_assignments JSON exists on tournament
        String assignmentsJson = (tourney != null) ? tourney.getGroupAssignments() : null;
        if (assignmentsJson != null && !assignmentsJson.trim().isEmpty() && !assignmentsJson.trim().equals("{}")) {
            Map<String, List<Team>> parsed = parseGroupAssignments(assignmentsJson, allTeams);
            boolean isValid = (parsed != null && parsed.size() >= 2);
            if (isValid) {
                for (List<Team> gList : parsed.values()) {
                    if (gList == null || (gList.size() != 4 && gList.size() != 8 && gList.size() != 16 && gList.size() != 32)) {
                        isValid = false;
                        break;
                    }
                }
            }
            if (isValid) {
                return parsed;
            }
        }

        // 2. Strict Business Logic: Do NOT auto-divide into groups. User must manually create groups in manage-group.
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
            java.util.regex.Pattern groupPattern = java.util.regex.Pattern
                    .compile("\"([^\"]+)\"\\s*:\\s*\\[([^\\]]*)\\]");
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
                            if (teamName == null)
                                teamName = extractFieldFromJson(objBody, "rawName");

                            Team found = null;
                            if (teamId != null)
                                found = teamLookup.get(teamId.toLowerCase());
                            if (found == null && teamName != null)
                                found = teamLookup.get(teamName.toLowerCase());

                            if (found != null) {
                                groupTeams.add(found);
                            } else if (teamName != null && !teamName.isEmpty()) {
                                Team placeholder = new Team();
                                placeholder.setId(teamId);
                                placeholder.setRawName(teamName);
                                placeholder.setNormalizedName(teamName);
                                groupTeams.add(placeholder);
                            }
                        }
                    } else {
                        String[] tokens = arrayContent.split(",");
                        for (String tk : tokens) {
                            String cleanTk = tk.replace("\"", "").replace("'", "").trim();
                            if (cleanTk.isEmpty())
                                continue;
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
        } catch (Exception ignore) {
        }
        return result;
    }

    private String extractFieldFromJson(String objBody, String fieldName) {
        java.util.regex.Pattern p = java.util.regex.Pattern
                .compile("\"" + fieldName + "\"\\s*:\\s*(?:\"([^\"]*)\"|([^,}\\s]+))");
        java.util.regex.Matcher m = p.matcher(objBody);
        if (m.find()) {
            String val = m.group(1);
            if (val == null)
                val = m.group(2);
            return (val != null) ? val.trim() : null;
        }
        return null;
    }

    private static class GslMatchNode {
        String id;
        String tournamentId;
        String stageId;
        String groupId;
        int roundNumber;
        String bracketType;
        int matchOrder;
        String team1Id;
        String team2Id;
        String nextMatchId;
        String nextSlot;
        String loserNextMatchId;
        String loserNextSlot;
        boolean isBye;
        String status;
    }

    private GslMatchNode createNode(String id, String tournamentId, String stageId, String groupId,
            int roundNumber, String bracketType, int matchOrder, String team1Id, String team2Id,
            String nextMatchId, String nextSlot, String loserNextMatchId, String loserNextSlot, boolean isBye) {
        GslMatchNode node = new GslMatchNode();
        node.id = id;
        node.tournamentId = tournamentId;
        node.stageId = stageId;
        node.groupId = groupId;
        node.roundNumber = roundNumber;
        node.bracketType = bracketType;
        node.matchOrder = matchOrder;
        node.team1Id = team1Id;
        node.team2Id = team2Id;
        node.nextMatchId = nextMatchId;
        node.nextSlot = nextSlot;
        node.loserNextMatchId = loserNextMatchId;
        node.loserNextSlot = loserNextSlot;
        node.isBye = isBye;
        node.status = isBye ? "FINISHED" : "PENDING";
        return node;
    }

    private int generateAndInsertGSLGroupMatches(Connection conn, String tournamentId, String stageId,
            String groupId, String groupName, List<Team> teams, int advanceCount, int startSeq) throws Exception {
        int n = teams.size();
        int bracketSize = nextPowerOfTwo(n);
        if (bracketSize < 4)
            bracketSize = 4;
        String gPrefix = groupName.replaceAll("[^a-zA-Z0-9]", "_").toUpperCase();
        if (gPrefix.isEmpty())
            gPrefix = "GA";

        List<GslMatchNode> groupNodes = new ArrayList<>();

        if (bracketSize == 4 && advanceCount <= 1) {
            String m1Id = tournamentId + "_" + gPrefix + "_UB_R1_M1";
            String m2Id = tournamentId + "_" + gPrefix + "_UB_R1_M2";
            String m3Id = tournamentId + "_" + gPrefix + "_UB_R2_M1";
            String m4Id = tournamentId + "_" + gPrefix + "_LB_R1_M1";
            String m5Id = tournamentId + "_" + gPrefix + "_LB_R2_M1";
            String m6Id = tournamentId + "_" + gPrefix + "_GF_R1_M1";

            Team t1 = n > 0 ? teams.get(0) : null;
            Team t2 = n > 1 ? teams.get(1) : null;
            Team t3 = n > 2 ? teams.get(2) : null;
            Team t4 = n > 3 ? teams.get(3) : null;

            groupNodes.add(createNode(m1Id, tournamentId, stageId, groupId, 1, "WINNER_BRACKET", startSeq++,
                    t1 != null ? t1.getId() : null, t4 != null ? t4.getId() : null,
                    m3Id, "SLOT_1", m4Id, "SLOT_1", (t1 == null || t4 == null)));
            groupNodes.add(createNode(m2Id, tournamentId, stageId, groupId, 1, "WINNER_BRACKET", startSeq++,
                    t2 != null ? t2.getId() : null, t3 != null ? t3.getId() : null,
                    m3Id, "SLOT_2", m4Id, "SLOT_2", (t2 == null || t3 == null)));
            groupNodes.add(createNode(m3Id, tournamentId, stageId, groupId, 2, "WINNER_BRACKET", startSeq++,
                    null, null, m6Id, "SLOT_1", m5Id, "SLOT_1", false));
            groupNodes.add(createNode(m4Id, tournamentId, stageId, groupId, 1, "LOSER_BRACKET", startSeq++,
                    null, null, m5Id, "SLOT_2", null, null, false));
            groupNodes.add(createNode(m5Id, tournamentId, stageId, groupId, 2, "LOSER_BRACKET", startSeq++,
                    null, null, m6Id, "SLOT_2", null, null, false));
            groupNodes.add(createNode(m6Id, tournamentId, stageId, groupId, 3, "GRAND_FINAL", startSeq++,
                    null, null, null, null, null, null, false));
        } else if (bracketSize == 4) {
            String m1Id = tournamentId + "_" + gPrefix + "_UB_R1_M1";
            String m2Id = tournamentId + "_" + gPrefix + "_UB_R1_M2";
            String m3Id = tournamentId + "_" + gPrefix + "_UB_R2_M1";
            String m4Id = tournamentId + "_" + gPrefix + "_LB_R1_M1";
            String m5Id = tournamentId + "_" + gPrefix + "_LB_R2_M1";

            Team t1 = n > 0 ? teams.get(0) : null;
            Team t2 = n > 1 ? teams.get(1) : null;
            Team t3 = n > 2 ? teams.get(2) : null;
            Team t4 = n > 3 ? teams.get(3) : null;

            groupNodes.add(createNode(m1Id, tournamentId, stageId, groupId, 1, "WINNER_BRACKET", startSeq++,
                    t1 != null ? t1.getId() : null, t4 != null ? t4.getId() : null,
                    m3Id, "SLOT_1", m4Id, "SLOT_1", (t1 == null || t4 == null)));
            groupNodes.add(createNode(m2Id, tournamentId, stageId, groupId, 1, "WINNER_BRACKET", startSeq++,
                    t2 != null ? t2.getId() : null, t3 != null ? t3.getId() : null,
                    m3Id, "SLOT_2", m4Id, "SLOT_2", (t2 == null || t3 == null)));
            groupNodes.add(createNode(m3Id, tournamentId, stageId, groupId, 2, "WINNER_BRACKET", startSeq++,
                    null, null, null, null, m5Id, "SLOT_1", false));
            groupNodes.add(createNode(m4Id, tournamentId, stageId, groupId, 1, "LOSER_BRACKET", startSeq++,
                    null, null, m5Id, "SLOT_2", null, null, false));
            groupNodes.add(createNode(m5Id, tournamentId, stageId, groupId, 2, "LOSER_BRACKET", startSeq++,
                    null, null, null, null, null, null, false));
        } else {
            // Scaled Double Elimination for 8, 16, 32 teams per group
            int ubRounds = (int) (Math.log(bracketSize) / Math.log(2));
            int cutRoundsToPlay = (advanceCount > 0 && advanceCount < bracketSize)
                    ? (int) (Math.log(bracketSize / advanceCount) / Math.log(2))
                    : ubRounds;
            if (cutRoundsToPlay < 1)
                cutRoundsToPlay = 1;

            int matchesInUbRound = bracketSize / 2;
            for (int r = 1; r <= cutRoundsToPlay; r++) {
                for (int m = 1; m <= matchesInUbRound; m++) {
                    String mId = tournamentId + "_" + gPrefix + "_UB_R" + r + "_M" + m;
                    String nextMId = (r < cutRoundsToPlay)
                            ? (tournamentId + "_" + gPrefix + "_UB_R" + (r + 1) + "_M" + ((m + 1) / 2))
                            : null;
                    String nextSlot = (m % 2 == 1) ? "SLOT_1" : "SLOT_2";
                    String dropMId = tournamentId + "_" + gPrefix + "_LB_R" + r + "_M" + m;
                    String dropSlot = "SLOT_1";

                    String t1Id = null;
                    String t2Id = null;
                    boolean isBye = false;

                    if (r == 1) {
                        int seed1 = getSeedingIndex(m * 2 - 1, bracketSize);
                        int seed2 = getSeedingIndex(m * 2, bracketSize);
                        Team tm1 = (seed1 <= n) ? teams.get(seed1 - 1) : null;
                        Team tm2 = (seed2 <= n) ? teams.get(seed2 - 1) : null;
                        t1Id = tm1 != null ? String.valueOf(tm1.getId()) : null;
                        t2Id = tm2 != null ? String.valueOf(tm2.getId()) : null;
                        isBye = (tm1 == null || tm2 == null);
                    }

                    groupNodes.add(createNode(mId, tournamentId, stageId, groupId, r, "WINNER_BRACKET", startSeq++,
                            t1Id, t2Id, nextMId, nextSlot, dropMId, dropSlot, isBye));
                }
                matchesInUbRound /= 2;
            }

            int totalLbRounds = (cutRoundsToPlay * 2) - 1;
            int lbMatchesInRound = bracketSize / 4;
            if (lbMatchesInRound < 1)
                lbMatchesInRound = 1;

            for (int lr = 1; lr <= totalLbRounds; lr++) {
                for (int lm = 1; lm <= lbMatchesInRound; lm++) {
                    String lbId = tournamentId + "_" + gPrefix + "_LB_R" + lr + "_M" + lm;
                    String nextLbId = (lr < totalLbRounds)
                            ? (tournamentId + "_" + gPrefix + "_LB_R" + (lr + 1) + "_M"
                                    + (lr % 2 == 1 ? lm : ((lm + 1) / 2)))
                            : null;
                    String nextLbSlot = (lr % 2 == 1) ? "SLOT_2" : ((lm % 2 == 1) ? "SLOT_1" : "SLOT_2");

                    groupNodes.add(createNode(lbId, tournamentId, stageId, groupId, lr, "LOSER_BRACKET", startSeq++,
                            null, null, nextLbId, nextLbSlot, null, null, false));
                }
                if (lr % 2 == 0)
                    lbMatchesInRound /= 2;
                if (lbMatchesInRound < 1)
                    lbMatchesInRound = 1;
            }
        }

        // Pass 1: Insert all match records with next/loser IDs as NULL to prevent foreign key conflicts
        String insertSql = "INSERT INTO matches (id, tournament_id, stage_id, group_id, round_number, bracket_type, match_order, "
                + "team1_id, team2_id, is_bye, status) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
            for (GslMatchNode node : groupNodes) {
                ps.setString(1, node.id);
                ps.setString(2, node.tournamentId);
                ps.setString(3, node.stageId);
                ps.setString(4, node.groupId);
                ps.setInt(5, node.roundNumber);
                ps.setString(6, node.bracketType);
                ps.setInt(7, node.matchOrder);
                if (node.team1Id != null)
                    ps.setString(8, node.team1Id);
                else
                    ps.setNull(8, Types.VARCHAR);
                if (node.team2Id != null)
                    ps.setString(9, node.team2Id);
                else
                    ps.setNull(9, Types.VARCHAR);
                ps.setBoolean(10, node.isBye);
                ps.setString(11, node.status);
                ps.addBatch();
            }
            ps.executeBatch();
        }

        // Pass 2: Update next_match_id & loser_next_match_id links now that all match IDs exist in DB
        String updateLinksSql = "UPDATE matches SET next_match_id = ?, next_slot = ?, loser_next_match_id = ?, loser_next_slot = ? WHERE id = ?";
        try (PreparedStatement psUp = conn.prepareStatement(updateLinksSql)) {
            for (GslMatchNode node : groupNodes) {
                if (node.nextMatchId != null || node.loserNextMatchId != null) {
                    if (node.nextMatchId != null)
                        psUp.setString(1, node.nextMatchId);
                    else
                        psUp.setNull(1, Types.VARCHAR);
                    if (node.nextSlot != null)
                        psUp.setString(2, node.nextSlot);
                    else
                        psUp.setNull(2, Types.VARCHAR);
                    if (node.loserNextMatchId != null)
                        psUp.setString(3, node.loserNextMatchId);
                    else
                        psUp.setNull(3, Types.VARCHAR);
                    if (node.loserNextSlot != null)
                        psUp.setString(4, node.loserNextSlot);
                    else
                        psUp.setNull(4, Types.VARCHAR);
                    psUp.setString(5, node.id);
                    psUp.addBatch();
                }
            }
            psUp.executeBatch();
        }

        return startSeq;
    }

    public boolean resetBracketMatches(String tournamentId, int stageOrder) {
        if (tournamentId == null || tournamentId.trim().isEmpty())
            return false;
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try {
                // Delete all matches for this GSL tournament and re-initialize
                try (PreparedStatement psUnlink = conn.prepareStatement(
                        "UPDATE matches SET next_match_id = NULL, loser_next_match_id = NULL WHERE tournament_id = ?")) {
                    psUnlink.setString(1, tournamentId);
                    psUnlink.executeUpdate();
                }

                try (PreparedStatement psDel = conn.prepareStatement(
                        "DELETE FROM matches WHERE tournament_id = ?")) {
                    psDel.setString(1, tournamentId);
                    psDel.executeUpdate();
                }

                conn.commit();
                ensureGSLInitialized(tournamentId, stageOrder);
                return true;
            } catch (Exception ex) {
                conn.rollback();
                ex.printStackTrace();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    private String getOrCreateStageId(Connection conn, String tournamentId, int stageOrder) {
        String stageId = tournamentId + "_S" + stageOrder;
        String sql = "SELECT id FROM tournament_stages WHERE tournament_id = ? AND stage_order = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tournamentId);
            ps.setInt(2, stageOrder);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next())
                    return rs.getString("id");
            }
        } catch (Exception ignore) {
        }

        String insertSql = "INSERT INTO tournament_stages (id, tournament_id, stage_order, format, status) VALUES (?, ?, ?, 'GSL', 'ACTIVE')";
        try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
            ps.setString(1, stageId);
            ps.setString(2, tournamentId);
            ps.setInt(3, stageOrder);
            ps.executeUpdate();
        } catch (Exception ignore) {
        }
        return stageId;
    }

    private int nextPowerOfTwo(int n) {
        int count = 1;
        while (count < n)
            count <<= 1;
        return count;
    }

    private int getSeedingIndex(int matchSlot, int totalSlots) {
        if (matchSlot == 1)
            return 1;
        if (matchSlot == 2)
            return totalSlots;
        if (matchSlot == 3)
            return totalSlots / 2;
        if (matchSlot == 4)
            return totalSlots / 2 + 1;
        return matchSlot;
    }

    private String escapeJson(Object val) {
        if (val == null)
            return "";
        return String.valueOf(val).replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r",
                "\\r");
    }
}
