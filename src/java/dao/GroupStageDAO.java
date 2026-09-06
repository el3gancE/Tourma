package dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import model.Match;

/**
 * Dedicated Data Access Object for Group Stage Format.
 * Manages matches partitioned into groups (A, B, C...),
 * and SQL Server 'matches' table persistence.
 */
public class GroupStageDAO extends DBContext {

    public static class GSMatchDTO {
        public String matchId;
        public String groupKey;
        public int roundNumber;
        public int matchNumber;
        public String team1Name;
        public Integer team1Score;
        public String team2Name;
        public Integer team2Score;
        public String winnerId; // "team1", "team2", or "draw"
        public String status;
    }

    /**
     * Get JSON array of Group Stage matches for frontend engine
     */
    public String getMatchesJsonForFrontend(String tournamentId, int stageOrder) {
        if (tournamentId == null || tournamentId.trim().isEmpty()) {
            return "[]";
        }

        try {
            ensureGroupsAutoSynced(tournamentId, stageOrder);
        } catch (Exception ignore) {}

        String sql = "SELECT m.*, "
                + "t1.raw_name AS t1_name, t1.original_seed AS t1_seed, "
                + "t2.raw_name AS t2_name, t2.original_seed AS t2_seed, "
                + "tw.raw_name AS winner_name "
                + "FROM matches m "
                + "JOIN tournament_stages s ON m.stage_id = s.id "
                + "LEFT JOIN teams t1 ON m.team1_id = t1.id "
                + "LEFT JOIN teams t2 ON m.team2_id = t2.id "
                + "LEFT JOIN teams tw ON m.winner_id = tw.id "
                + "WHERE m.tournament_id = ? AND s.stage_order = ? "
                + "ORDER BY m.round_number ASC, m.id ASC";

        StringBuilder sb = new StringBuilder("[");
        int count = 0;

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tournamentId);
            ps.setInt(2, stageOrder);
            try (ResultSet rs = ps.executeQuery()) {
                int seq = 1;
                while (rs.next()) {
                    if (count > 0) sb.append(",");
                    count++;

                    String rawId = rs.getString("id");
                    int roundNumber = rs.getInt("round_number");
                    String matchCode = rs.getString("match_code");
                    if (matchCode == null) matchCode = "";

                    // Extract group key, e.g. "Bảng A - Trận #1"
                    String groupKey = "A";
                    Matcher gMatcher = Pattern.compile("Bảng\\s+([A-Za-z0-9]+)").matcher(matchCode);
                    if (gMatcher.find()) {
                        groupKey = gMatcher.group(1);
                    }

                    int matchNumber = seq;
                    Matcher numMatcher = Pattern.compile("#(\\d+)").matcher(matchCode);
                    if (numMatcher.find()) {
                        matchNumber = Integer.parseInt(numMatcher.group(1));
                    }

                    String t1Name = rs.getString("t1_name");
                    int s1Val = rs.getInt("score1");
                    String s1 = rs.wasNull() ? "" : String.valueOf(s1Val);

                    String t2Name = rs.getString("t2_name");
                    int s2Val = rs.getInt("score2");
                    String s2 = rs.wasNull() ? "" : String.valueOf(s2Val);

                    String winnerIdCol = rs.getString("winner_id");
                    String winnerSlot = "";
                    if (winnerIdCol != null) {
                        if (winnerIdCol.equals(rs.getString("team1_id"))) {
                            winnerSlot = "team1";
                        } else if (winnerIdCol.equals(rs.getString("team2_id"))) {
                            winnerSlot = "team2";
                        } else {
                            winnerSlot = "draw";
                        }
                    }

                    String status = rs.getString("status");
                    if ("FINISHED".equalsIgnoreCase(status)) {
                        status = "COMPLETED";
                    }

                    String matchId = "GS_" + groupKey + "_" + matchNumber;
                    if (rawId != null && rawId.contains("_M")) {
                        matchId = rawId.substring(rawId.indexOf("_M") + 2);
                    }

                    sb.append("{");
                    sb.append("\"matchId\":\"").append(escapeJson(matchId)).append("\",");
                    sb.append("\"groupKey\":\"").append(escapeJson(groupKey)).append("\",");
                    sb.append("\"roundNumber\":").append(roundNumber).append(",");
                    sb.append("\"matchNumber\":").append(matchNumber).append(",");
                    sb.append("\"team1\":{\"name\":\"").append(escapeJson(t1Name != null ? t1Name : "")).append("\",\"score\":\"").append(escapeJson(s1)).append("\"},");
                    sb.append("\"team2\":{\"name\":\"").append(escapeJson(t2Name != null ? t2Name : "")).append("\",\"score\":\"").append(escapeJson(s2)).append("\"},");
                    sb.append("\"winnerId\":").append(winnerSlot.isEmpty() ? "null" : "\"" + winnerSlot + "\"").append(",");
                    sb.append("\"status\":\"").append(escapeJson(status != null ? status : "SCHEDULED")).append("\"");
                    sb.append("}");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        sb.append("]");
        return (count > 0) ? sb.toString() : "[]";
    }

    /**
     * Batch Sync / Persist full Group Stage fixtures to 'matches' table
     */
    public boolean syncGroupMatches(String tournamentId, int stageOrder, String matchesDataJson) {
        List<GSMatchDTO> list = parseGSMatchJson(matchesDataJson);
        if (list.isEmpty()) {
            return false;
        }

        try (Connection conn = getConnection()) {
            String stageId = getOrCreateStageId(conn, tournamentId, stageOrder);
            Map<String, String> teamMap = getTeamNameToIdMap(conn, tournamentId);

            String mergeSql = "MERGE INTO matches AS target "
                    + "USING (SELECT ? AS id, ? AS tournament_id, ? AS stage_id, ? AS round_number, ? AS match_code, "
                    + "              ? AS bracket_type, ? AS team1_id, ? AS team2_id, ? AS score1, ? AS score2, "
                    + "              ? AS winner_id, ? AS status, ? AS group_id) AS source "
                    + "ON (target.id = source.id) "
                    + "WHEN MATCHED THEN "
                    + "    UPDATE SET "
                    + "        target.round_number = source.round_number, "
                    + "        target.match_code = source.match_code, "
                    + "        target.team1_id = source.team1_id, "
                    + "        target.team2_id = source.team2_id, "
                    + "        target.score1 = source.score1, "
                    + "        target.score2 = source.score2, "
                    + "        target.winner_id = source.winner_id, "
                    + "        target.status = source.status, "
                    + "        target.group_id = source.group_id "
                    + "WHEN NOT MATCHED THEN "
                    + "    INSERT (id, tournament_id, stage_id, round_number, match_code, bracket_type, "
                    + "            team1_id, team2_id, score1, score2, winner_id, status, group_id) "
                    + "    VALUES (source.id, source.tournament_id, source.stage_id, source.round_number, source.match_code, source.bracket_type, "
                    + "            source.team1_id, source.team2_id, source.score1, source.score2, source.winner_id, source.status, source.group_id);";

            try (PreparedStatement ps = conn.prepareStatement(mergeSql)) {
                for (GSMatchDTO m : list) {
                    String matchDbId = tournamentId + "_S" + stageOrder + "_M" + m.matchId;
                    String t1Id = getOrCreateTeamId(conn, tournamentId, teamMap, m.team1Name);
                    String t2Id = getOrCreateTeamId(conn, tournamentId, teamMap, m.team2Name);
                    String winnerId = "team1".equalsIgnoreCase(m.winnerId) ? t1Id : ("team2".equalsIgnoreCase(m.winnerId) ? t2Id : null);

                    String status = ("COMPLETED".equalsIgnoreCase(m.status) || "FINISHED".equalsIgnoreCase(m.status) || (m.team1Score != null && m.team2Score != null)) ? "FINISHED" : "PENDING";
                    String groupKey = (m.groupKey != null && !m.groupKey.trim().isEmpty()) ? m.groupKey.trim() : "A";
                    String matchCode = "Bảng " + groupKey + " - Trận #" + m.matchNumber;

                    String groupId = ensureGroupExists(conn, stageId, groupKey);
                    if (t1Id != null) ensureGroupTeamExists(conn, groupId, t1Id);
                    if (t2Id != null) ensureGroupTeamExists(conn, groupId, t2Id);

                    ps.setString(1, matchDbId);
                    ps.setString(2, tournamentId);
                    ps.setString(3, stageId);
                    ps.setInt(4, m.roundNumber);
                    ps.setString(5, matchCode);
                    ps.setString(6, "GROUP");
                    setNullableString(ps, 7, t1Id);
                    setNullableString(ps, 8, t2Id);
                    setNullableInt(ps, 9, m.team1Score);
                    setNullableInt(ps, 10, m.team2Score);
                    setNullableString(ps, 11, winnerId);
                    ps.setString(12, status);
                    ps.setString(13, groupId);
                    ps.addBatch();
                }
                ps.executeBatch();
            }

            // Immediately recalculate group standings & trigger Series standings
            recalculateAndSaveGroupStandings(tournamentId, stageOrder);
            triggerSeriesRecalculationIfLinked(tournamentId);

            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Update score and status for a Group Stage match
     */
    public boolean updateGroupMatchScore(String tournamentId, int stageOrder, String matchId,
                                         Integer score1, Integer score2, String winnerFlag,
                                         String team1Name, String team2Name) {
        if (tournamentId == null || tournamentId.trim().isEmpty()) {
            return false;
        }

        try (Connection conn = getConnection()) {
            String stageId = getOrCreateStageId(conn, tournamentId, stageOrder);
            Map<String, String> teamMap = getTeamNameToIdMap(conn, tournamentId);
            String matchDbId = tournamentId + "_S" + stageOrder + "_M" + matchId;
            String t1Id = getOrCreateTeamId(conn, tournamentId, teamMap, team1Name);
            String t2Id = getOrCreateTeamId(conn, tournamentId, teamMap, team2Name);

            String winnerId = null;
            if ("team1".equalsIgnoreCase(winnerFlag)) {
                winnerId = t1Id;
            } else if ("team2".equalsIgnoreCase(winnerFlag)) {
                winnerId = t2Id;
            } else if (winnerFlag != null && !winnerFlag.trim().isEmpty() && !"draw".equalsIgnoreCase(winnerFlag)) {
                winnerId = lookupTeamId(teamMap, winnerFlag);
            }
            if (winnerId == null && score1 != null && score2 != null) {
                winnerId = (score1 > score2) ? t1Id : ((score2 > score1) ? t2Id : null);
            }

            String targetGroupId = null;
            if (t1Id != null) {
                String findGSql = "SELECT group_id FROM group_teams WHERE team_id = ? AND group_id IN (SELECT id FROM groups WHERE stage_id = ?)";
                try (PreparedStatement psG = conn.prepareStatement(findGSql)) {
                    psG.setString(1, t1Id);
                    psG.setString(2, stageId);
                    try (ResultSet rsG = psG.executeQuery()) {
                        if (rsG.next()) targetGroupId = rsG.getString(1);
                    }
                }
            }

            String updateSql = "UPDATE matches SET "
                    + "score1 = ?, score2 = ?, status = 'FINISHED', "
                    + "team1_id = COALESCE(?, team1_id), "
                    + "team2_id = COALESCE(?, team2_id), "
                    + "winner_id = ?, "
                    + "group_id = COALESCE(?, group_id) "
                    + "WHERE id = ? OR id LIKE ?";

            int updated = 0;
            try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                setNullableInt(ps, 1, score1);
                setNullableInt(ps, 2, score2);
                setNullableString(ps, 3, t1Id);
                setNullableString(ps, 4, t2Id);
                setNullableString(ps, 5, winnerId);
                setNullableString(ps, 6, targetGroupId);
                ps.setString(7, matchDbId);
                ps.setString(8, "%_M" + matchId);
                updated = ps.executeUpdate();
            }

            if (updated == 0) {
                String insertSql = "INSERT INTO matches (id, tournament_id, stage_id, round_number, match_code, bracket_type, team1_id, team2_id, score1, score2, winner_id, status, group_id) "
                        + "VALUES (?, ?, ?, 1, ?, 'GROUP', ?, ?, ?, ?, ?, 'FINISHED', ?)";
                try (PreparedStatement psIns = conn.prepareStatement(insertSql)) {
                    psIns.setString(1, matchDbId);
                    psIns.setString(2, tournamentId);
                    psIns.setString(3, stageId);
                    psIns.setString(4, "Trận #" + matchId);
                    setNullableString(psIns, 5, t1Id);
                    setNullableString(psIns, 6, t2Id);
                    setNullableInt(psIns, 7, score1);
                    setNullableInt(psIns, 8, score2);
                    setNullableString(psIns, 9, winnerId);
                    setNullableString(psIns, 10, targetGroupId);
                    psIns.executeUpdate();
                }
            }

            // Immediately recalculate group standings & trigger Series standings
            recalculateAndSaveGroupStandings(tournamentId, stageOrder);
            triggerSeriesRecalculationIfLinked(tournamentId);

            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Synchronize Group Assignments into 'groups' and 'group_teams' tables
     */
    public boolean syncGroupsAndGroupTeams(String tournamentId, int stageOrder, String groupAssignmentsJson) {
        if (tournamentId == null || tournamentId.trim().isEmpty() || groupAssignmentsJson == null || groupAssignmentsJson.trim().isEmpty()) {
            return false;
        }

        Map<String, List<String>> groupMap = parseGroupAssignments(groupAssignmentsJson);
        if (groupMap.isEmpty()) return false;

        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try {
                String stageId = getOrCreateStageId(conn, tournamentId, stageOrder);
                Map<String, String> teamMap = getTeamNameToIdMap(conn, tournamentId);

                int advanceCount = 2;
                String qStage = "SELECT advancing_teams_count FROM tournament_stages WHERE id = ?";
                try (PreparedStatement ps = conn.prepareStatement(qStage)) {
                    ps.setString(1, stageId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            int adv = rs.getInt(1);
                            if (!rs.wasNull() && adv > 0) advanceCount = adv;
                        }
                    }
                } catch (Exception ignore) {}

                String mergeGroupSql = "MERGE INTO groups AS target "
                        + "USING (SELECT ? AS id, ? AS stage_id, ? AS group_name, ? AS qualified_slots_count) AS source "
                        + "ON (target.id = source.id) "
                        + "WHEN MATCHED THEN "
                        + "    UPDATE SET target.group_name = source.group_name, target.qualified_slots_count = source.qualified_slots_count "
                        + "WHEN NOT MATCHED THEN "
                        + "    INSERT (id, stage_id, group_name, qualified_slots_count) "
                        + "    VALUES (source.id, source.stage_id, source.group_name, source.qualified_slots_count);";

                String mergeGTGroupSql = "MERGE INTO group_teams AS target "
                        + "USING (SELECT ? AS id, ? AS group_id, ? AS team_id) AS source "
                        + "ON (target.group_id = source.group_id AND target.team_id = source.team_id) "
                        + "WHEN NOT MATCHED THEN "
                        + "    INSERT (id, group_id, team_id, points, wins, draws, losses, goals_scored, goals_conceded, goal_difference, rank_in_group) "
                        + "    VALUES (source.id, source.group_id, source.team_id, 0, 0, 0, 0, 0, 0, 0, ?);";

                try (PreparedStatement psG = conn.prepareStatement(mergeGroupSql);
                     PreparedStatement psGT = conn.prepareStatement(mergeGTGroupSql)) {

                    for (Map.Entry<String, List<String>> entry : groupMap.entrySet()) {
                        String gKey = entry.getKey().trim();
                        String cleanKey = gKey.replace("Bảng", "").replace("bảng", "").trim();
                        if (cleanKey.isEmpty()) cleanKey = gKey;
                        String groupId = stageId + "_G_" + cleanKey;
                        if (groupId.length() > 50) {
                            groupId = "G_" + Math.abs(groupId.hashCode());
                        }
                        String groupName = gKey.startsWith("Bảng") ? gKey : ("Bảng " + cleanKey);

                        psG.setString(1, groupId);
                        psG.setString(2, stageId);
                        psG.setString(3, groupName);
                        psG.setInt(4, advanceCount);
                        psG.addBatch();

                        List<String> teams = entry.getValue();
                        int seedIdx = 1;
                        for (String tName : teams) {
                            String teamId = getOrCreateTeamId(conn, tournamentId, teamMap, tName);
                            if (teamId != null) {
                                String gtId = groupId + "_" + teamId;
                                if (gtId.length() > 50) {
                                    gtId = "GT_" + Math.abs(gtId.hashCode()) + "_" + seedIdx;
                                }
                                psGT.setString(1, gtId);
                                psGT.setString(2, groupId);
                                psGT.setString(3, teamId);
                                psGT.setInt(4, seedIdx++);
                                psGT.addBatch();
                            }
                        }
                    }
                    psG.executeBatch();
                    psGT.executeBatch();
                }

                conn.commit();

                // Recalculate standings immediately based on any finished matches
                recalculateAndSaveGroupStandings(tournamentId, stageOrder);

                return true;
            } catch (Exception e) {
                conn.rollback();
                e.printStackTrace();
                return false;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Recalculate Group Stage Standings (points, wins, draws, losses, GD, rank)
     * and persist directly into 'group_teams' table.
     */
    public boolean recalculateAndSaveGroupStandings(String tournamentId, int stageOrder) {
        if (tournamentId == null || tournamentId.trim().isEmpty()) return false;

        try (Connection conn = getConnection()) {
            String stageId = getOrCreateStageId(conn, tournamentId, stageOrder);

            // Fetch stage rules (win_points, draw_points, loss_points)
            int winPts = 3, drawPts = 1, lossPts = 0;
            String ruleSql = "SELECT win_points, draw_points, loss_points FROM tournament_stages WHERE id = ?";
            try (PreparedStatement ps = conn.prepareStatement(ruleSql)) {
                ps.setString(1, stageId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        winPts = rs.getInt("win_points");
                        if (rs.wasNull()) winPts = 3;
                        drawPts = rs.getInt("draw_points");
                        if (rs.wasNull()) drawPts = 1;
                        lossPts = rs.getInt("loss_points");
                        if (rs.wasNull()) lossPts = 0;
                    }
                }
            } catch (Exception ignore) {}

            // Get all groups for this stage
            List<String> groupIds = new ArrayList<>();
            String gSql = "SELECT id FROM groups WHERE stage_id = ?";
            try (PreparedStatement ps = conn.prepareStatement(gSql)) {
                ps.setString(1, stageId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        groupIds.add(rs.getString("id"));
                    }
                }
            }

            if (groupIds.isEmpty()) {
                return false;
            }

            class TeamStats {
                String teamId;
                int matchesPlayed = 0;
                int wins = 0;
                int draws = 0;
                int losses = 0;
                int goalsScored = 0;
                int goalsConceded = 0;
                int points = 0;
                int originalSeed = 999;
            }

            String teamSeedSql = "SELECT id, original_seed FROM teams WHERE tournament_id = ?";
            Map<String, Integer> seedMap = new HashMap<>();
            try (PreparedStatement ps = conn.prepareStatement(teamSeedSql)) {
                ps.setString(1, tournamentId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        seedMap.put(rs.getString("id"), rs.getInt("original_seed"));
                    }
                }
            }

            String updateGtSql = "UPDATE group_teams SET "
                    + "points = ?, wins = ?, draws = ?, losses = ?, "
                    + "goals_scored = ?, goals_conceded = ?, goal_difference = ?, rank_in_group = ? "
                    + "WHERE group_id = ? AND team_id = ?";

            try (PreparedStatement psUpdate = conn.prepareStatement(updateGtSql)) {
                for (String gId : groupIds) {
                    Map<String, TeamStats> statsMap = new LinkedHashMap<>();
                    String gtSql = "SELECT team_id FROM group_teams WHERE group_id = ?";
                    try (PreparedStatement ps = conn.prepareStatement(gtSql)) {
                        ps.setString(1, gId);
                        try (ResultSet rs = ps.executeQuery()) {
                            while (rs.next()) {
                                String tId = rs.getString("team_id");
                                TeamStats ts = new TeamStats();
                                ts.teamId = tId;
                                ts.originalSeed = seedMap.getOrDefault(tId, 999);
                                statsMap.put(tId, ts);
                            }
                        }
                    }

                    if (statsMap.isEmpty()) continue;

                    String gKey = "A";
                    int idx = gId.lastIndexOf("_G_");
                    if (idx >= 0) {
                        gKey = gId.substring(idx + 3);
                    }

                    // Query finished matches for this group
                    String matchSql = "SELECT team1_id, team2_id, score1, score2, winner_id FROM matches "
                            + "WHERE tournament_id = ? AND stage_id = ? "
                            + "AND (group_id = ? OR match_code LIKE ? OR (team1_id IN (SELECT team_id FROM group_teams WHERE group_id = ?) AND team2_id IN (SELECT team_id FROM group_teams WHERE group_id = ?))) "
                            + "AND (status = 'FINISHED' OR status = 'COMPLETED')";

                    try (PreparedStatement ps = conn.prepareStatement(matchSql)) {
                        ps.setString(1, tournamentId);
                        ps.setString(2, stageId);
                        ps.setString(3, gId);
                        ps.setString(4, "%Bảng " + gKey + "%");
                        ps.setString(5, gId);
                        ps.setString(6, gId);
                        try (ResultSet rs = ps.executeQuery()) {
                            while (rs.next()) {
                                String t1 = rs.getString("team1_id");
                                String t2 = rs.getString("team2_id");
                                int s1 = rs.getInt("score1");
                                if (rs.wasNull()) s1 = 0;
                                int s2 = rs.getInt("score2");
                                if (rs.wasNull()) s2 = 0;
                                String winnerId = rs.getString("winner_id");

                                if (t1 != null && statsMap.containsKey(t1) && t2 != null && statsMap.containsKey(t2)) {
                                    TeamStats st1 = statsMap.get(t1);
                                    TeamStats st2 = statsMap.get(t2);

                                    st1.matchesPlayed++;
                                    st2.matchesPlayed++;
                                    st1.goalsScored += s1;
                                    st1.goalsConceded += s2;
                                    st2.goalsScored += s2;
                                    st2.goalsConceded += s1;

                                    if (s1 > s2 || (winnerId != null && winnerId.equals(t1))) {
                                        st1.wins++;
                                        st2.losses++;
                                    } else if (s2 > s1 || (winnerId != null && winnerId.equals(t2))) {
                                        st2.wins++;
                                        st1.losses++;
                                    } else {
                                        st1.draws++;
                                        st2.draws++;
                                    }
                                }
                            }
                        }
                    }

                    // Compute points and sort
                    List<TeamStats> sortedList = new ArrayList<>(statsMap.values());
                    for (TeamStats ts : sortedList) {
                        ts.points = ts.wins * winPts + ts.draws * drawPts + ts.losses * lossPts;
                    }

                    sortedList.sort((a, b) -> {
                        if (b.points != a.points) return Integer.compare(b.points, a.points);
                        int gdA = a.goalsScored - a.goalsConceded;
                        int gdB = b.goalsScored - b.goalsConceded;
                        if (gdB != gdA) return Integer.compare(gdB, gdA);
                        if (b.goalsScored != a.goalsScored) return Integer.compare(b.goalsScored, a.goalsScored);
                        return Integer.compare(a.originalSeed, b.originalSeed);
                    });

                    // Batch update group_teams
                    int rank = 1;
                    for (TeamStats ts : sortedList) {
                        int gd = ts.goalsScored - ts.goalsConceded;
                        psUpdate.setInt(1, ts.points);
                        psUpdate.setInt(2, ts.wins);
                        psUpdate.setInt(3, ts.draws);
                        psUpdate.setInt(4, ts.losses);
                        psUpdate.setInt(5, ts.goalsScored);
                        psUpdate.setInt(6, ts.goalsConceded);
                        psUpdate.setInt(7, gd);
                        psUpdate.setInt(8, rank++);
                        psUpdate.setString(9, gId);
                        psUpdate.setString(10, ts.teamId);
                        psUpdate.addBatch();
                    }
                }
                psUpdate.executeBatch();
            }

            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Trigger Series Standings recalculation if tournament belongs to a Series
     */
    public void triggerSeriesRecalculationIfLinked(String tournamentId) {
        if (tournamentId == null || tournamentId.trim().isEmpty()) return;
        try {
            TournamentDAO tDao = new TournamentDAO();
            String seriesId = tDao.getSeriesIdByTournamentId(tournamentId);
            if (seriesId != null && !seriesId.trim().isEmpty()) {
                new SeriesDAO().recalculateSeriesStandings(seriesId.trim());
            }
        } catch (Exception ignore) {}
    }

    public String ensureGroupExists(Connection conn, String stageId, String groupKey) throws SQLException {
        if (groupKey == null || groupKey.trim().isEmpty()) groupKey = "A";
        String cleanKey = groupKey.replace("Bảng", "").replace("bảng", "").trim();
        if (cleanKey.isEmpty()) cleanKey = groupKey.trim();
        String groupId = stageId + "_G_" + cleanKey;
        if (groupId.length() > 50) {
            groupId = "G_" + Math.abs(groupId.hashCode());
        }
        String groupName = groupKey.startsWith("Bảng") ? groupKey : ("Bảng " + cleanKey);

        String checkSql = "SELECT id FROM groups WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(checkSql)) {
            ps.setString(1, groupId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return groupId;
            }
        }

        String insertSql = "INSERT INTO groups (id, stage_id, group_name, qualified_slots_count) VALUES (?, ?, ?, 2)";
        try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
            ps.setString(1, groupId);
            ps.setString(2, stageId);
            ps.setString(3, groupName);
            ps.executeUpdate();
        }
        return groupId;
    }

    public void ensureGroupTeamExists(Connection conn, String groupId, String teamId) throws SQLException {
        if (groupId == null || teamId == null) return;
        String checkSql = "SELECT id FROM group_teams WHERE group_id = ? AND team_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(checkSql)) {
            ps.setString(1, groupId);
            ps.setString(2, teamId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return;
            }
        }
        String gtId = groupId + "_" + teamId;
        if (gtId.length() > 50) {
            gtId = "GT_" + Math.abs(gtId.hashCode()) + "_" + (int)(Math.random() * 1000);
        }
        String insSql = "INSERT INTO group_teams (id, group_id, team_id, points, wins, draws, losses, goals_scored, goals_conceded, goal_difference, rank_in_group) "
                + "VALUES (?, ?, ?, 0, 0, 0, 0, 0, 0, 0, 0)";
        try (PreparedStatement ps = conn.prepareStatement(insSql)) {
            ps.setString(1, gtId);
            ps.setString(2, groupId);
            ps.setString(3, teamId);
            ps.executeUpdate();
        } catch (Exception ignore) {}
    }

    private String getOrCreateTeamId(Connection conn, String tournamentId, Map<String, String> teamMap, String teamName) throws SQLException {
        String tId = lookupTeamId(teamMap, teamName);
        if (tId != null) return tId;
        if (teamName == null || teamName.trim().isEmpty() || "BYE".equalsIgnoreCase(teamName.trim())) return null;

        String norm = teamName.trim().toLowerCase();
        String newId = "TEAM_" + tournamentId + "_" + System.currentTimeMillis() + "_" + (int)(Math.random()*1000);
        if (newId.length() > 50) {
            newId = "T_" + Math.abs(newId.hashCode());
        }
        String insertSql = "INSERT INTO teams (id, tournament_id, raw_name, normalized_name, original_seed, status) VALUES (?, ?, ?, ?, 99, 'ACTIVE')";
        try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
            ps.setString(1, newId);
            ps.setString(2, tournamentId);
            ps.setString(3, teamName.trim());
            ps.setString(4, norm);
            ps.executeUpdate();
        }
        teamMap.put(norm, newId);
        return newId;
    }

    public Map<String, List<String>> parseGroupAssignments(String json) {
        Map<String, List<String>> map = new LinkedHashMap<>();
        if (json == null || json.trim().isEmpty()) return map;

        Pattern groupPattern = Pattern.compile("\"([^\"]+)\"\\s*:\\s*\\[([^\\]]*)\\]");
        Matcher m = groupPattern.matcher(json);
        while (m.find()) {
            String groupKey = m.group(1).trim();
            String listContent = m.group(2).trim();
            List<String> teamNames = new ArrayList<>();
            if (!listContent.isEmpty()) {
                Pattern namePattern = Pattern.compile("\"name\"\\s*:\\s*\"([^\"]+)\"");
                Matcher nm = namePattern.matcher(listContent);
                boolean foundObj = false;
                while (nm.find()) {
                    foundObj = true;
                    teamNames.add(nm.group(1).trim());
                }
                if (!foundObj) {
                    Pattern strPattern = Pattern.compile("\"([^\"]+)\"");
                    Matcher sm = strPattern.matcher(listContent);
                    while (sm.find()) {
                        teamNames.add(sm.group(1).trim());
                    }
                }
            }
            map.put(groupKey, teamNames);
        }
        return map;
    }

    /**
     * Reset all Group Stage matches for a tournament stage
     */
    public boolean resetGroupMatches(String tournamentId, int stageOrder) {
        if (tournamentId == null || tournamentId.trim().isEmpty()) {
            return false;
        }

        try (Connection conn = getConnection()) {
            String stageId = getOrCreateStageId(conn, tournamentId, stageOrder);
            String delSql = "DELETE FROM matches WHERE tournament_id = ? AND stage_id = ?";
            try (PreparedStatement ps = conn.prepareStatement(delSql)) {
                ps.setString(1, tournamentId);
                ps.setString(2, stageId);
                ps.executeUpdate();
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    // =========================================================================
    // HELPER METHODS
    // =========================================================================

    private void ensureTournamentExists(Connection conn, String tournamentId) throws SQLException {
        if (tournamentId == null || tournamentId.trim().isEmpty()) return;
        String checkSql = "SELECT id FROM tournaments WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(checkSql)) {
            ps.setString(1, tournamentId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return;
            }
        }
        String insertSql = "INSERT INTO tournaments (id, name, tournament_type, status) VALUES (?, ?, 'SINGLE_STAGE', 'DRAFT')";
        try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
            ps.setString(1, tournamentId);
            ps.setString(2, tournamentId);
            ps.executeUpdate();
        }
    }

    private String getOrCreateStageId(Connection conn, String tournamentId, int stageOrder) throws SQLException {
        ensureTournamentExists(conn, tournamentId);
        String queryStage = "SELECT id FROM tournament_stages WHERE tournament_id = ? AND stage_order = ?";
        try (PreparedStatement ps = conn.prepareStatement(queryStage)) {
            ps.setString(1, tournamentId);
            ps.setInt(2, stageOrder);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("id");
                }
            }
        }

        String stageId = tournamentId + "_S" + stageOrder;
        String stageName = (stageOrder == 2) ? "Giai đoạn 2 (Group Stage)" : "Giai đoạn 1 (Group Stage)";
        String insertStage = "INSERT INTO tournament_stages (id, tournament_id, stage_order, stage_name, format, status) "
                + "VALUES (?, ?, ?, ?, 'GROUP_STAGE', 'PENDING')";
        try (PreparedStatement ps = conn.prepareStatement(insertStage)) {
            ps.setString(1, stageId);
            ps.setString(2, tournamentId);
            ps.setInt(3, stageOrder);
            ps.setString(4, stageName);
            ps.executeUpdate();
        }
        return stageId;
    }

    private Map<String, String> getTeamNameToIdMap(Connection conn, String tournamentId) throws SQLException {
        Map<String, String> map = new HashMap<>();
        String sql = "SELECT id, raw_name, original_seed FROM teams WHERE tournament_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tournamentId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String id = rs.getString("id");
                    String rawName = rs.getString("raw_name");
                    int seed = rs.getInt("original_seed");

                    if (rawName != null && !rawName.trim().isEmpty()) {
                        map.put(rawName.trim().toLowerCase(), id);
                    }
                    if (!rs.wasNull()) {
                        map.put("seed_" + seed, id);
                    }
                }
            }
        }
        return map;
    }

    private String lookupTeamId(Map<String, String> teamMap, String teamName) {
        if (teamMap == null || teamMap.isEmpty() || teamName == null || teamName.trim().isEmpty() || "BYE".equalsIgnoreCase(teamName.trim())) {
            return null;
        }
        String norm = teamName.trim().toLowerCase();
        return teamMap.get(norm);
    }

    private void setNullableString(PreparedStatement ps, int paramIndex, String val) throws SQLException {
        if (val != null && !val.trim().isEmpty()) ps.setString(paramIndex, val.trim());
        else ps.setNull(paramIndex, Types.VARCHAR);
    }

    private void setNullableInt(PreparedStatement ps, int paramIndex, Integer val) throws SQLException {
        if (val != null) ps.setInt(paramIndex, val);
        else ps.setNull(paramIndex, Types.INTEGER);
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\b", "\\b")
                .replace("\f", "\\f")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private List<GSMatchDTO> parseGSMatchJson(String json) {
        List<GSMatchDTO> list = new ArrayList<>();
        if (json == null || json.trim().isEmpty()) return list;

        Pattern objPattern = Pattern.compile("\\{([^{}]+(?:\\{[^{}]*\\}[^{}]*)*)\\}");
        Matcher objMatcher = objPattern.matcher(json);

        while (objMatcher.find()) {
            String block = objMatcher.group(1);
            GSMatchDTO dto = new GSMatchDTO();

            dto.matchId = parseJsonString(block, "matchId", "GS_A_" + (list.size() + 1));
            dto.groupKey = parseJsonString(block, "groupKey", "A");
            dto.roundNumber = parseJsonInt(block, "roundNumber", 1);
            dto.matchNumber = parseJsonInt(block, "matchNumber", list.size() + 1);

            String t1Block = extractSubObject(block, "team1");
            if (t1Block != null) {
                dto.team1Name = parseJsonString(t1Block, "name", null);
                dto.team1Score = parseJsonNullableInt(t1Block, "score");
            }
            if (dto.team1Score == null) {
                dto.team1Score = parseJsonNullableInt(block, "team1Score");
            }

            String t2Block = extractSubObject(block, "team2");
            if (t2Block != null) {
                dto.team2Name = parseJsonString(t2Block, "name", null);
                dto.team2Score = parseJsonNullableInt(t2Block, "score");
            }
            if (dto.team2Score == null) {
                dto.team2Score = parseJsonNullableInt(block, "team2Score");
            }

            dto.winnerId = parseJsonString(block, "winnerId", null);
            dto.status = parseJsonString(block, "status", "PENDING");

            list.add(dto);
        }

        return list;
    }

    private String extractSubObject(String src, String key) {
        Pattern p = Pattern.compile("\"" + key + "\"\\s*:\\s*\\{([^}]*)\\}");
        Matcher m = p.matcher(src);
        if (m.find()) return m.group(1);
        return null;
    }

    private String parseJsonString(String src, String key, String defaultVal) {
        Pattern p = Pattern.compile("\"" + key + "\"\\s*:\\s*\"([^\"]*)\"");
        Matcher m = p.matcher(src);
        if (m.find()) return m.group(1);
        return defaultVal;
    }

    private int parseJsonInt(String src, String key, int defaultVal) {
        Pattern p = Pattern.compile("\"" + key + "\"\\s*:\\s*(\\d+)");
        Matcher m = p.matcher(src);
        if (m.find()) {
            try { return Integer.parseInt(m.group(1)); } catch (Exception ignore) {}
        }
        return defaultVal;
    }

    private Integer parseJsonNullableInt(String src, String key) {
        Pattern p = Pattern.compile("\"" + key + "\"\\s*:\\s*\"?(\\d+)\"?");
        Matcher m = p.matcher(src);
        if (m.find()) {
            try { return Integer.parseInt(m.group(1)); } catch (Exception ignore) {}
        }
        return null;
    }

    public void ensureGroupsAutoSynced(String tournamentId, int stageOrder) {
        if (tournamentId == null || tournamentId.trim().isEmpty()) return;
        try (Connection conn = getConnection()) {
            String stageId = getOrCreateStageId(conn, tournamentId, stageOrder);
            String checkSql = "SELECT COUNT(*) FROM groups WHERE stage_id = ?";
            try (PreparedStatement ps = conn.prepareStatement(checkSql)) {
                ps.setString(1, stageId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next() && rs.getInt(1) > 0) {
                        return;
                    }
                }
            }

            String q = "SELECT group_assignments FROM tournaments WHERE id = ?";
            String gaJson = null;
            try (PreparedStatement ps = conn.prepareStatement(q)) {
                ps.setString(1, tournamentId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) gaJson = rs.getString(1);
                }
            }
            if (gaJson != null && !gaJson.trim().isEmpty() && !gaJson.trim().equals("{}")) {
                syncGroupsAndGroupTeams(tournamentId, stageOrder, gaJson);
            }
        } catch (Exception ignore) {}
    }
}
