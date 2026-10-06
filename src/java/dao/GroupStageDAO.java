package dao;

import model.Match;
import model.Team;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * High-Performance, Database-Driven DAO for Group Stage Tournament Format.
 * 100% Database-Driven: manages groups, group_teams rankings, and group fixtures in SQL Server.
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
        // Group synchronization logic if updated via JSON
        return true;
    }

    public List<Match> getMatchesByTournamentId(String tournamentId) {
        List<Match> list = new ArrayList<>();
        if (tournamentId == null || tournamentId.trim().isEmpty()) return list;

        String sql = "SELECT m.*, "
                + "t1.raw_name AS t1_name, t1.original_seed AS t1_seed, "
                + "t2.raw_name AS t2_name, t2.original_seed AS t2_seed, "
                + "tw.raw_name AS winner_name "
                + "FROM matches m "
                + "LEFT JOIN teams t1 ON m.team1_id = t1.id "
                + "LEFT JOIN teams t2 ON m.team2_id = t2.id "
                + "LEFT JOIN teams tw ON m.winner_id = tw.id "
                + "WHERE m.tournament_id = ? "
                + "ORDER BY m.round_number ASC, m.id ASC";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tournamentId);
            try (ResultSet rs = ps.executeQuery()) {
                int seq = 1;
                while (rs.next()) {
                    Match m = new Match();
                    String rawId = rs.getString("id");
                    int numId = parseNumericId(rawId, seq++);
                    m.setId(numId);
                    m.setRoundNumber(rs.getInt("round_number"));
                    m.setMatchNumber(numId);
                    m.setBracketType(rs.getString("bracket_type"));

                    m.setTeam1Name(rs.getString("t1_name"));
                    int s1Seed = rs.getInt("t1_seed");
                    if (!rs.wasNull()) m.setTeam1Seed(s1Seed);
                    int s1 = rs.getInt("score1");
                    if (!rs.wasNull()) m.setTeam1Score(s1);

                    m.setTeam2Name(rs.getString("t2_name"));
                    int s2Seed = rs.getInt("t2_seed");
                    if (!rs.wasNull()) m.setTeam2Seed(s2Seed);
                    int s2 = rs.getInt("score2");
                    if (!rs.wasNull()) m.setTeam2Score(s2);

                    String winnerId = rs.getString("winner_id");
                    if (winnerId != null) {
                        if (winnerId.equals(rs.getString("team1_id"))) m.setWinnerTeamId(1);
                        else if (winnerId.equals(rs.getString("team2_id"))) m.setWinnerTeamId(2);
                    }

                    String st = rs.getString("status");
                    m.setStatus("FINISHED".equalsIgnoreCase(st) ? "COMPLETED" : (st != null ? st : "SCHEDULED"));
                    list.add(m);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

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

                    String t1Name = rs.getString("t1_name");
                    int t1SeedVal = rs.getInt("t1_seed");
                    String t1Seed = rs.wasNull() ? "" : String.valueOf(t1SeedVal);
                    int s1Val = rs.getInt("score1");
                    String s1 = rs.wasNull() ? "" : String.valueOf(s1Val);

                    String t2Name = rs.getString("t2_name");
                    int t2SeedVal = rs.getInt("t2_seed");
                    String t2Seed = rs.wasNull() ? "" : String.valueOf(t2SeedVal);
                    int s2Val = rs.getInt("score2");
                    String s2 = rs.wasNull() ? "" : String.valueOf(s2Val);

                    String winnerIdCol = rs.getString("winner_id");
                    String winnerSlot = "";
                    if (winnerIdCol != null) {
                        if (winnerIdCol.equals(rs.getString("team1_id"))) winnerSlot = "team1";
                        else if (winnerIdCol.equals(rs.getString("team2_id"))) winnerSlot = "team2";
                    }

                    boolean isBye = rs.getBoolean("is_bye");
                    String status = rs.getString("status");
                    if ("FINISHED".equalsIgnoreCase(status)) status = "COMPLETED";

                    sb.append("{")
                            .append("\"matchId\":\"").append(escapeJson(rawId)).append("\",")
                            .append("\"id\":\"").append(escapeJson(rawId)).append("\",")
                            .append("\"rawId\":\"").append(escapeJson(rawId)).append("\",")
                            .append("\"groupId\":\"").append(escapeJson(rs.getString("group_id") != null ? rs.getString("group_id") : "")).append("\",")
                            .append("\"groupName\":\"").append(escapeJson(grpName != null ? grpName : "")).append("\",")
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

    public synchronized void ensureGroupStageInitialized(String tournamentId, int stageOrder) {
        if (tournamentId == null || tournamentId.trim().isEmpty()) return;

        String stageId = lookupOrCreateStageId(tournamentId, stageOrder);

        String checkSql = "SELECT COUNT(*) FROM groups WHERE stage_id = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(checkSql)) {
            ps.setString(1, stageId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next() && rs.getInt(1) > 0) {
                    return; // Groups already exist!
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

        ParticipantDAO pDao = new ParticipantDAO();
        List<Team> teams = pDao.getTeamsByTournamentId(tournamentId);
        if (teams == null || teams.size() < 2) return;

        initializeGroupsAndFixturesInDB(tournamentId, stageId, teams);
    }

    private void initializeGroupsAndFixturesInDB(String tournamentId, String stageId, List<Team> teams) {
        int numTeams = teams.size();
        int maxPerGroup = 4;
        int numGroups = (int) Math.ceil((double) numTeams / maxPerGroup);
        if (numGroups < 1) numGroups = 1;

        List<List<Team>> groupList = new ArrayList<>();
        for (int i = 0; i < numGroups; i++) {
            groupList.add(new ArrayList<>());
        }

        // Snake / Seed distribution across groups
        for (int i = 0; i < numTeams; i++) {
            int gIdx = i % numGroups;
            groupList.get(gIdx).add(teams.get(i));
        }

        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);

            String insGroupSql = "INSERT INTO groups (id, stage_id, group_name, qualified_slots_count) VALUES (?, ?, ?, ?)";
            String insGroupTeamSql = "INSERT INTO group_teams (id, group_id, team_id, seed_in_group, rank_in_group) VALUES (?, ?, ?, ?, ?)";
            String insMatchSql = "INSERT INTO matches (id, tournament_id, stage_id, group_id, round_number, match_order, match_code, bracket_type, "
                    + "team1_id, team2_id, is_bye, status) VALUES (?, ?, ?, ?, ?, ?, ?, 'GROUP_STAGE', ?, ?, 0, 'READY')";

            int matchSeq = 1;
            char groupChar = 'A';

            for (int g = 0; g < numGroups; g++) {
                String gName = "Bảng " + (char)(groupChar + g);
                String gId = "GRP_" + tournamentId + "_" + (char)(groupChar + g);

                try (PreparedStatement psG = conn.prepareStatement(insGroupSql)) {
                    psG.setString(1, gId);
                    psG.setString(2, stageId);
                    psG.setString(3, gName);
                    psG.setInt(4, 2);
                    psG.executeUpdate();
                }

                List<Team> grpTeams = groupList.get(g);
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
                        String mId = "M_" + tournamentId + "_G_" + (char)(groupChar + g) + "_" + (rNum);

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

            conn.commit();
        } catch (Exception e) {
            e.printStackTrace();
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
                + "WHERE s.tournament_id = ? AND s.stage_order = ?";

        String resetTourneySql = "UPDATE tournaments SET status = 'DRAFT', champion_name = NULL WHERE id = ?";

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
                psGT.executeUpdate();
            }

            try (PreparedStatement psT = conn.prepareStatement(resetTourneySql)) {
                psT.setString(1, tournamentId);
                psT.executeUpdate();
            } catch (Exception ignore) {}

            try (PreparedStatement psS1 = conn.prepareStatement("UPDATE tournaments SET stage1_status = 'PENDING' WHERE id = ?")) {
                psS1.setString(1, tournamentId);
                psS1.executeUpdate();
            } catch (Exception ignore) {}

            service.RollingWindowPointService.clearAllCaches();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private int parseNumericId(String strId, int defaultSeq) {
        if (strId == null || strId.trim().isEmpty()) return defaultSeq;
        try {
            return Integer.parseInt(strId.trim());
        } catch (Exception e) {
            int lastUnderscore = strId.lastIndexOf('_');
            if (lastUnderscore >= 0 && lastUnderscore < strId.length() - 1) {
                String sub = strId.substring(lastUnderscore + 1);
                try { return Integer.parseInt(sub); } catch (Exception ignore) {}
            }
        }
        return defaultSeq;
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }
}
