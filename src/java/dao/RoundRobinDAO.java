package dao;

import model.Match;
import model.Team;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * High-Performance, Database-Driven DAO for Round Robin Tournament Format.
 * 100% Database-Driven with automatic round robin schedule generator and SQL-based standings.
 */
public class RoundRobinDAO extends DBContext {

    public static class RRStandingRow {
        public String teamId;
        public String teamName;
        public int seed;
        public int matchesPlayed;
        public int wins;
        public int draws;
        public int losses;
        public int goalsScored;
        public int goalsConceded;
        public int goalDifference;
        public int points;
        public int rank;
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

    public String getMatchesJsonForFrontend(String tournamentId, int stageOrder) {
        if (tournamentId == null || tournamentId.trim().isEmpty()) return "[]";

        ensureRoundRobinInitialized(tournamentId, stageOrder);

        String sql = "SELECT m.*, "
                + "t1.raw_name AS t1_name, t1.original_seed AS t1_seed, "
                + "t2.raw_name AS t2_name, t2.original_seed AS t2_seed, "
                + "tw.raw_name AS winner_name "
                + "FROM matches m "
                + "LEFT JOIN tournament_stages s ON m.stage_id = s.id "
                + "LEFT JOIN teams t1 ON m.team1_id = t1.id "
                + "LEFT JOIN teams t2 ON m.team2_id = t2.id "
                + "LEFT JOIN teams tw ON m.winner_id = tw.id "
                + "WHERE m.tournament_id = ? AND (s.stage_order = ? OR (s.stage_order IS NULL AND ? = 1) OR m.stage_id LIKE '%_S' + CAST(? AS VARCHAR) + '%' OR m.stage_id = 'STAGE_' + CAST(? AS VARCHAR)) "
                + "ORDER BY m.round_number ASC, m.match_order ASC, m.id ASC";

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

    public synchronized void ensureRoundRobinInitialized(String tournamentId, int stageOrder) {
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
                    return; // Already initialized!
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

        ParticipantDAO pDao = new ParticipantDAO();
        List<Team> teams = pDao.getTeamsByTournamentId(tournamentId);
        if (teams == null || teams.size() < 2) return;

        initializeRoundRobinScheduleInDB(tournamentId, stageOrder, teams);
    }

    private void initializeRoundRobinScheduleInDB(String tournamentId, int stageOrder, List<Team> teams) {
        List<Team> teamList = new ArrayList<>(teams);
        boolean hasDummy = false;
        if (teamList.size() % 2 != 0) {
            teamList.add(null); // Dummy team for BYE
            hasDummy = true;
        }

        int numTeams = teamList.size();
        int numRounds = numTeams - 1;
        int matchesPerRound = numTeams / 2;

        String stageId = lookupOrCreateStageId(tournamentId, stageOrder);
        int matchSeq = 1;

        String insertSql = "INSERT INTO matches (id, tournament_id, stage_id, round_number, match_order, match_code, bracket_type, "
                + "team1_id, team2_id, is_bye, status) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                for (int r = 0; r < numRounds; r++) {
                    int roundNumber = r + 1;
                    for (int m = 0; m < matchesPerRound; m++) {
                        Team t1 = teamList.get(m);
                        Team t2 = teamList.get(numTeams - 1 - m);

                        if (t1 == null || t2 == null) {
                            continue; // Skip match against BYE dummy
                        }

                        String matchId = "M_" + tournamentId + "_RR_R" + roundNumber + "_" + (m + 1);
                        ps.setString(1, matchId);
                        ps.setString(2, tournamentId);
                        ps.setString(3, stageId);
                        ps.setInt(4, roundNumber);
                        ps.setInt(5, matchSeq++);
                        ps.setString(6, "Vòng " + roundNumber + " - Trận " + (m + 1));
                        ps.setString(7, "ROUND_ROBIN");
                        ps.setString(8, t1.getId());
                        ps.setString(9, t2.getId());
                        ps.setBoolean(10, false);
                        ps.setString(11, "READY");
                        ps.addBatch();
                    }

                    // Rotate teams (keep element 0 fixed, rotate others)
                    Team last = teamList.remove(teamList.size() - 1);
                    teamList.add(1, last);
                }
                ps.executeBatch();
            }
            conn.commit();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public List<RRStandingRow> getRoundRobinStandings(String tournamentId, int stageOrder) {
        List<RRStandingRow> standings = new ArrayList<>();
        if (tournamentId == null || tournamentId.trim().isEmpty()) return standings;

        String sql = "SELECT t.id AS team_id, t.raw_name AS team_name, t.original_seed, "
                + "ISNULL(stats.mp, 0) AS matches_played, "
                + "ISNULL(stats.w, 0) AS wins, "
                + "ISNULL(stats.d, 0) AS draws, "
                + "ISNULL(stats.l, 0) AS losses, "
                + "ISNULL(stats.gf, 0) AS goals_scored, "
                + "ISNULL(stats.ga, 0) AS goals_conceded, "
                + "(ISNULL(stats.gf, 0) - ISNULL(stats.ga, 0)) AS goal_difference, "
                + "ISNULL(stats.pts, 0) AS points "
                + "FROM teams t "
                + "LEFT JOIN ("
                + "  SELECT t_id, "
                + "         COUNT(*) AS mp, "
                + "         SUM(CASE WHEN is_win = 1 THEN 1 ELSE 0 END) AS w, "
                + "         SUM(CASE WHEN is_draw = 1 THEN 1 ELSE 0 END) AS d, "
                + "         SUM(CASE WHEN is_loss = 1 THEN 1 ELSE 0 END) AS l, "
                + "         SUM(gf) AS gf, "
                + "         SUM(ga) AS ga, "
                + "         SUM(CASE WHEN is_win = 1 THEN 3 WHEN is_draw = 1 THEN 1 ELSE 0 END) AS pts "
                + "  FROM ("
                + "    SELECT m.team1_id AS t_id, m.score1 AS gf, m.score2 AS ga, "
                + "           CASE WHEN m.score1 > m.score2 THEN 1 ELSE 0 END AS is_win, "
                + "           CASE WHEN m.score1 = m.score2 THEN 1 ELSE 0 END AS is_draw, "
                + "           CASE WHEN m.score1 < m.score2 THEN 1 ELSE 0 END AS is_loss "
                + "    FROM matches m "
                + "    LEFT JOIN tournament_stages s ON m.stage_id = s.id "
                + "    WHERE m.tournament_id = ? AND (m.status = 'FINISHED' OR m.status = 'COMPLETED') AND m.score1 IS NOT NULL AND m.score2 IS NOT NULL "
                + "      AND (s.stage_order = ? OR (s.stage_order IS NULL AND ? = 1) OR m.stage_id LIKE '%_S' + CAST(? AS VARCHAR) + '%' OR m.stage_id = 'STAGE_' + CAST(? AS VARCHAR)) "
                + "    UNION ALL "
                + "    SELECT m.team2_id AS t_id, m.score2 AS gf, m.score1 AS ga, "
                + "           CASE WHEN m.score2 > m.score1 THEN 1 ELSE 0 END AS is_win, "
                + "           CASE WHEN m.score2 = m.score1 THEN 1 ELSE 0 END AS is_draw, "
                + "           CASE WHEN m.score2 < m.score1 THEN 1 ELSE 0 END AS is_loss "
                + "    FROM matches m "
                + "    LEFT JOIN tournament_stages s ON m.stage_id = s.id "
                + "    WHERE m.tournament_id = ? AND (m.status = 'FINISHED' OR m.status = 'COMPLETED') AND m.score1 IS NOT NULL AND m.score2 IS NOT NULL "
                + "      AND (s.stage_order = ? OR (s.stage_order IS NULL AND ? = 1) OR m.stage_id LIKE '%_S' + CAST(? AS VARCHAR) + '%' OR m.stage_id = 'STAGE_' + CAST(? AS VARCHAR)) "
                + "  ) sub GROUP BY t_id"
                + ") stats ON t.id = stats.t_id "
                + "WHERE t.tournament_id = ? "
                + "ORDER BY points DESC, goal_difference DESC, goals_scored DESC, t.original_seed ASC";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tournamentId);
            ps.setInt(2, stageOrder);
            ps.setInt(3, stageOrder);
            ps.setInt(4, stageOrder);
            ps.setInt(5, stageOrder);

            ps.setString(6, tournamentId);
            ps.setInt(7, stageOrder);
            ps.setInt(8, stageOrder);
            ps.setInt(9, stageOrder);
            ps.setInt(10, stageOrder);

            ps.setString(11, tournamentId);
            try (ResultSet rs = ps.executeQuery()) {
                int rk = 1;
                while (rs.next()) {
                    RRStandingRow row = new RRStandingRow();
                    row.teamId = rs.getString("team_id");
                    row.teamName = rs.getString("team_name");
                    row.seed = rs.getInt("original_seed");
                    row.matchesPlayed = rs.getInt("matches_played");
                    row.wins = rs.getInt("wins");
                    row.draws = rs.getInt("draws");
                    row.losses = rs.getInt("losses");
                    row.goalsScored = rs.getInt("goals_scored");
                    row.goalsConceded = rs.getInt("goals_conceded");
                    row.goalDifference = rs.getInt("goal_difference");
                    row.points = rs.getInt("points");
                    row.rank = rk++;
                    standings.add(row);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return standings;
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
            ps.setString(5, "ROUND_ROBIN");
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
