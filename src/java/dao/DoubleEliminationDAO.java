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
 * High-Performance, Database-Driven DAO for Double Elimination Tournament Format.
 * Manages Upper Bracket (Winner), Lower Bracket (Loser), and Grand Finals 100% in SQL Server.
 */
public class DoubleEliminationDAO extends DBContext {

    public List<Match> getMatchesByTournamentId(int tournamentId) {
        return getMatchesByTournamentId(String.valueOf(tournamentId));
    }

    public Map<String, Object> getDoubleEliminationData(int tournamentId) {
        return getDoubleEliminationData(String.valueOf(tournamentId));
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

                    String nextId = rs.getString("next_match_id");
                    if (nextId != null) m.setNextMatchId(parseNumericId(nextId, 0));
                    String nextSlot = rs.getString("next_slot");
                    m.setNextMatchSlot("SLOT_2".equalsIgnoreCase(nextSlot) ? 2 : 1);

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

    public Map<String, Object> getDoubleEliminationData(String tournamentId) {
        Map<String, Object> dataMap = new HashMap<>();
        List<Match> matchList = getMatchesByTournamentId(tournamentId);

        Map<Integer, List<Match>> upperMap = new HashMap<>();
        Map<Integer, List<Match>> lowerMap = new HashMap<>();
        List<Match> grandFinals = new ArrayList<>();

        if (matchList != null) {
            for (Match m : matchList) {
                String bracketType = m.getBracketType();
                if ("LOSER_BRACKET".equalsIgnoreCase(bracketType) || "LOWER".equalsIgnoreCase(bracketType)) {
                    lowerMap.computeIfAbsent(m.getRoundNumber(), k -> new ArrayList<>()).add(m);
                } else if ("GRAND_FINAL".equalsIgnoreCase(bracketType) || "GRAND_FINALS".equalsIgnoreCase(bracketType) || "GRAND_FINAL_RESET".equalsIgnoreCase(bracketType)) {
                    grandFinals.add(m);
                } else {
                    upperMap.computeIfAbsent(m.getRoundNumber(), k -> new ArrayList<>()).add(m);
                }
            }
        }

        dataMap.put("upperMap", upperMap);
        dataMap.put("lowerMap", lowerMap);
        dataMap.put("grandFinals", grandFinals);
        return dataMap;
    }

    public String getMatchesJsonForFrontend(String tournamentId, int stageOrder) {
        if (tournamentId == null || tournamentId.trim().isEmpty()) return "[]";

        // Auto-initialize DE bracket tree in DB if none exists
        ensureBracketInitialized(tournamentId, stageOrder);

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
                + "ORDER BY m.round_number ASC, m.id ASC";

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
                    String bracketType = rs.getString("bracket_type");

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

                    String nextIdStr = rs.getString("next_match_id");
                    String nextSlot = rs.getString("next_slot");
                    int nextSlotNum = "SLOT_2".equalsIgnoreCase(nextSlot) ? 2 : 1;

                    String dropIdStr = rs.getString("loser_next_match_id");
                    String dropSlot = rs.getString("loser_next_slot");
                    int dropSlotNum = "SLOT_2".equalsIgnoreCase(dropSlot) ? 2 : 1;

                    boolean isBye = rs.getBoolean("is_bye");
                    String status = rs.getString("status");
                    if ("FINISHED".equalsIgnoreCase(status)) status = "COMPLETED";

                    sb.append("{")
                            .append("\"matchId\":\"").append(escapeJson(rawId)).append("\",")
                            .append("\"id\":\"").append(escapeJson(rawId)).append("\",")
                            .append("\"rawId\":\"").append(escapeJson(rawId)).append("\",")
                            .append("\"bracketType\":\"").append(escapeJson(bracketType != null ? bracketType : "WINNER_BRACKET")).append("\",")
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
                            .append("\"nextMatchId\":").append(nextIdStr != null && !nextIdStr.isEmpty() ? "\"" + escapeJson(nextIdStr) + "\"" : "null").append(",")
                            .append("\"nextMatchSlot\":").append(nextSlotNum).append(",")
                            .append("\"loserNextMatchId\":").append(dropIdStr != null && !dropIdStr.isEmpty() ? "\"" + escapeJson(dropIdStr) + "\"" : "null").append(",")
                            .append("\"loserNextSlot\":").append(dropSlotNum).append(",")
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

    public synchronized void ensureBracketInitialized(String tournamentId, int stageOrder) {
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
        if (teams == null || teams.isEmpty()) return;

        initializeDoubleEliminationInDB(tournamentId, stageOrder, teams);
    }

    private void initializeDoubleEliminationInDB(String tournamentId, int stageOrder, List<Team> teams) {
        int n = teams.size();
        if (n < 2) return;

        int bracketSize = 1;
        while (bracketSize < n) bracketSize *= 2;
        int upperRounds = (int) (Math.log(bracketSize) / Math.log(2));

        String stageId = lookupOrCreateStageId(tournamentId, stageOrder);
        int[] seeds = generateSeedingArray(bracketSize);
        Map<Integer, Team> seedToTeam = new HashMap<>();
        for (int i = 0; i < teams.size(); i++) {
            seedToTeam.put(i + 1, teams.get(i));
        }

        List<DeNode> allMatches = new ArrayList<>();
        int matchSeq = 1;

        // 1. Build Upper Bracket Matches
        Map<Integer, List<DeNode>> upperRoundNodes = new HashMap<>();
        int currUpperMatches = bracketSize / 2;
        for (int r = 1; r <= upperRounds; r++) {
            List<DeNode> roundNodes = new ArrayList<>();
            for (int m = 0; m < currUpperMatches; m++) {
                DeNode node = new DeNode();
                node.id = "M_" + tournamentId + "_UB_" + matchSeq;
                node.matchNumber = matchSeq;
                node.roundNumber = r;
                node.tournamentId = tournamentId;
                node.stageId = stageId;
                node.matchCode = "UB R" + r + " #" + (m + 1);
                node.bracketType = "WINNER_BRACKET";

                if (r == 1) {
                    Team t1 = seedToTeam.get(seeds[m * 2]);
                    Team t2 = seedToTeam.get(seeds[m * 2 + 1]);
                    node.team1Id = (t1 != null) ? t1.getId() : null;
                    node.team2Id = (t2 != null) ? t2.getId() : null;
                    node.status = (node.team1Id != null && node.team2Id != null) ? "READY" : "PENDING";
                } else {
                    node.status = "PENDING";
                }

                roundNodes.add(node);
                allMatches.add(node);
                matchSeq++;
            }

            if (r > 1) {
                List<DeNode> prevNodes = upperRoundNodes.get(r - 1);
                for (int i = 0; i < prevNodes.size(); i++) {
                    DeNode prev = prevNodes.get(i);
                    DeNode next = roundNodes.get(i / 2);
                    prev.nextMatchId = next.id;
                    prev.nextSlot = (i % 2 == 0) ? "SLOT_1" : "SLOT_2";
                }
            }

            upperRoundNodes.put(r, roundNodes);
            currUpperMatches /= 2;
        }

        // 2. Build Lower Bracket Matches
        int totalLowerRounds = (upperRounds - 1) * 2;
        Map<Integer, List<DeNode>> lowerRoundNodes = new HashMap<>();
        int currLowerMatches = bracketSize / 4;
        if (currLowerMatches < 1) currLowerMatches = 1;

        for (int lr = 1; lr <= totalLowerRounds; lr++) {
            List<DeNode> roundNodes = new ArrayList<>();
            for (int m = 0; m < currLowerMatches; m++) {
                DeNode node = new DeNode();
                node.id = "M_" + tournamentId + "_LB_" + matchSeq;
                node.matchNumber = matchSeq;
                node.roundNumber = lr;
                node.tournamentId = tournamentId;
                node.stageId = stageId;
                node.matchCode = "LB R" + lr + " #" + (m + 1);
                node.bracketType = "LOSER_BRACKET";
                node.status = "PENDING";

                roundNodes.add(node);
                allMatches.add(node);
                matchSeq++;
            }

            if (lr > 1) {
                List<DeNode> prevNodes = lowerRoundNodes.get(lr - 1);
                for (int i = 0; i < prevNodes.size(); i++) {
                    DeNode prev = prevNodes.get(i);
                    if (lr % 2 == 1) {
                        // Halving round
                        DeNode next = roundNodes.get(i / 2);
                        prev.nextMatchId = next.id;
                        prev.nextSlot = (i % 2 == 0) ? "SLOT_1" : "SLOT_2";
                    } else {
                        // Minor/Major drop round
                        DeNode next = roundNodes.get(i);
                        prev.nextMatchId = next.id;
                        prev.nextSlot = "SLOT_1";
                    }
                }
            }

            lowerRoundNodes.put(lr, roundNodes);
            if (lr % 2 == 1 && currLowerMatches > 1) {
                currLowerMatches /= 2;
            }
        }

        // 3. Link Upper Bracket Losers to Lower Bracket
        List<DeNode> ubR1 = upperRoundNodes.get(1);
        List<DeNode> lbR1 = lowerRoundNodes.get(1);
        if (ubR1 != null && lbR1 != null) {
            for (int i = 0; i < ubR1.size(); i++) {
                DeNode ubNode = ubR1.get(i);
                if (i / 2 < lbR1.size()) {
                    DeNode lbNode = lbR1.get(i / 2);
                    ubNode.loserNextMatchId = lbNode.id;
                    ubNode.loserNextSlot = (i % 2 == 0) ? "SLOT_1" : "SLOT_2";
                }
            }
        }

        // 4. Build Grand Final
        DeNode gf = new DeNode();
        gf.id = "M_" + tournamentId + "_GF_" + matchSeq;
        gf.matchNumber = matchSeq;
        gf.roundNumber = upperRounds + 1;
        gf.tournamentId = tournamentId;
        gf.stageId = stageId;
        gf.matchCode = "Trận Chung Kết";
        gf.bracketType = "GRAND_FINAL";
        gf.status = "PENDING";
        allMatches.add(gf);

        // Winner of Upper Bracket Final goes to GF Slot 1
        List<DeNode> ubFinal = upperRoundNodes.get(upperRounds);
        if (ubFinal != null && !ubFinal.isEmpty()) {
            ubFinal.get(0).nextMatchId = gf.id;
            ubFinal.get(0).nextSlot = "SLOT_1";
        }

        // Winner of Lower Bracket Final goes to GF Slot 2
        List<DeNode> lbFinal = lowerRoundNodes.get(totalLowerRounds);
        if (lbFinal != null && !lbFinal.isEmpty()) {
            lbFinal.get(0).nextMatchId = gf.id;
            lbFinal.get(0).nextSlot = "SLOT_2";
        }

        // Sort teams by original seed
        List<Team> sortedTeams = new ArrayList<>(teams);
        sortedTeams.sort((a, b) -> Integer.compare(a.getOriginalSeed(), b.getOriginalSeed()));
        for (int i = 0; i < sortedTeams.size(); i++) {
            seedToTeam.put(sortedTeams.get(i).getOriginalSeed() > 0 ? sortedTeams.get(i).getOriginalSeed() : (i + 1), sortedTeams.get(i));
        }

        // Insert into database in 2 passes to guarantee zero foreign key constraint conflicts
        String insertSql = "INSERT INTO matches (id, tournament_id, stage_id, round_number, match_order, match_code, bracket_type, "
                + "team1_id, team2_id, is_bye, status) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        String updateLinksSql = "UPDATE matches SET next_match_id = ?, next_slot = ?, loser_next_match_id = ?, loser_next_slot = ? WHERE id = ?";

        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            
            // Pass 1: Insert all match records
            try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                for (DeNode node : allMatches) {
                    ps.setString(1, node.id);
                    ps.setString(2, node.tournamentId);
                    ps.setString(3, node.stageId);
                    ps.setInt(4, node.roundNumber);
                    ps.setInt(5, node.matchNumber);
                    ps.setString(6, node.matchCode);
                    ps.setString(7, node.bracketType);
                    if (node.team1Id != null) ps.setString(8, node.team1Id); else ps.setNull(8, Types.VARCHAR);
                    if (node.team2Id != null) ps.setString(9, node.team2Id); else ps.setNull(9, Types.VARCHAR);
                    ps.setBoolean(10, false);
                    ps.setString(11, node.status);
                    ps.addBatch();
                }
                ps.executeBatch();
            }

            // Pass 2: Update next_match_id & loser_next_match_id links now that all match IDs exist in DB
            try (PreparedStatement psUp = conn.prepareStatement(updateLinksSql)) {
                for (DeNode node : allMatches) {
                    if (node.nextMatchId != null || node.loserNextMatchId != null) {
                        if (node.nextMatchId != null) psUp.setString(1, node.nextMatchId); else psUp.setNull(1, Types.VARCHAR);
                        if (node.nextSlot != null) psUp.setString(2, node.nextSlot); else psUp.setNull(2, Types.VARCHAR);
                        if (node.loserNextMatchId != null) psUp.setString(3, node.loserNextMatchId); else psUp.setNull(3, Types.VARCHAR);
                        if (node.loserNextSlot != null) psUp.setString(4, node.loserNextSlot); else psUp.setNull(4, Types.VARCHAR);
                        psUp.setString(5, node.id);
                        psUp.addBatch();
                    }
                }
                psUp.executeBatch();
            }

            conn.commit();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static class DeNode {
        String id;
        int matchNumber;
        int roundNumber;
        String tournamentId;
        String stageId;
        String matchCode;
        String bracketType;
        String team1Id;
        String team2Id;
        String nextMatchId;
        String nextSlot;
        String loserNextMatchId;
        String loserNextSlot;
        String status;
    }

    private int[] generateSeedingArray(int bracketSize) {
        int[] rounds = new int[]{1, 2};
        while (rounds.length < bracketSize) {
            int nextLen = rounds.length * 2;
            int[] next = new int[nextLen];
            for (int i = 0; i < rounds.length; i++) {
                next[i * 2] = rounds[i];
                next[i * 2 + 1] = nextLen + 1 - rounds[i];
            }
            rounds = next;
        }
        return rounds;
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
            ps.setString(5, "DOUBLE_ELIMINATION");
            ps.executeUpdate();
            return newStageId;
        } catch (Exception ignore) {}

        return "STAGE_1";
    }

    public boolean resetBracketMatches(String tournamentId, int stageOrder) {
        if (tournamentId == null || tournamentId.trim().isEmpty()) return false;
        String sql = "UPDATE m SET m.score1 = NULL, m.score2 = NULL, m.penalty1 = NULL, m.penalty2 = NULL, "
                + "m.winner_id = NULL, m.loser_id = NULL, "
                + "m.team1_id = CASE WHEN m.bracket_type = 'WINNER_BRACKET' AND m.round_number = 1 THEN m.team1_id ELSE NULL END, "
                + "m.team2_id = CASE WHEN m.bracket_type = 'WINNER_BRACKET' AND m.round_number = 1 THEN m.team2_id ELSE NULL END, "
                + "m.status = CASE WHEN m.bracket_type = 'WINNER_BRACKET' AND m.round_number = 1 AND m.team1_id IS NOT NULL AND m.team2_id IS NOT NULL THEN 'READY' ELSE 'PENDING' END "
                + "FROM matches m "
                + "LEFT JOIN tournament_stages s ON m.stage_id = s.id "
                + "WHERE m.tournament_id = ? AND (s.stage_order = ? OR (s.stage_order IS NULL AND ? = 1) OR m.stage_id LIKE '%_S' + CAST(? AS VARCHAR) + '_%')";

        String resetTourneySql = "UPDATE tournaments SET status = 'DRAFT', champion_name = NULL WHERE id = ?";

        try (Connection conn = getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, tournamentId);
                ps.setInt(2, stageOrder);
                ps.setInt(3, stageOrder);
                ps.setInt(4, stageOrder);
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
