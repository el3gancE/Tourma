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
                + "ORDER BY m.round_number ASC, LEN(m.id) ASC, m.id ASC";

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
                    boolean isOrderNull = rs.wasNull();
                    boolean isBye = rs.getBoolean("is_bye");
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

                    if (roundNumber == 1 && ("WINNER_BRACKET".equalsIgnoreCase(bracketType) || "MAIN".equalsIgnoreCase(bracketType))) {
                        if (t1Name != null && (t2Name == null || t2Name.trim().isEmpty())) {
                            t2Name = "BYE";
                            isBye = true;
                        } else if (t2Name != null && (t1Name == null || t1Name.trim().isEmpty())) {
                            t1Name = "BYE";
                            isBye = true;
                        }
                    }

                    Integer matchNum = null;
                    if (!isBye) {
                        if (!isOrderNull && matchOrder > 0) {
                            matchNum = matchOrder;
                        } else {
                            matchNum = seq++;
                        }
                    }

                    String winnerIdCol = rs.getString("winner_id");
                    String winnerSlot = "";
                    if (winnerIdCol != null && !winnerIdCol.trim().isEmpty()) {
                        String wid = winnerIdCol.trim();
                        if (wid.equalsIgnoreCase("team1") || wid.equals("1") || wid.equals(rs.getString("team1_id"))) {
                            winnerSlot = "team1";
                        } else if (wid.equalsIgnoreCase("team2") || wid.equals("2") || wid.equals(rs.getString("team2_id"))) {
                            winnerSlot = "team2";
                        }
                    }
                    if (winnerSlot.isEmpty() && !s1.isEmpty() && !s2.isEmpty()) {
                        if (s1Val > s2Val) winnerSlot = "team1";
                        else if (s2Val > s1Val) winnerSlot = "team2";
                    }

                    String nextIdStr = rs.getString("next_match_id");
                    String nextSlot = rs.getString("next_slot");
                    int nextSlotNum = "SLOT_2".equalsIgnoreCase(nextSlot) ? 2 : 1;

                    String dropIdStr = rs.getString("loser_next_match_id");
                    String dropSlot = rs.getString("loser_next_slot");
                    int dropSlotNum = "SLOT_2".equalsIgnoreCase(dropSlot) ? 2 : 1;

                    String status = rs.getString("status");
                    if ("FINISHED".equalsIgnoreCase(status) || "DONE".equalsIgnoreCase(status) || "COMPLETED".equalsIgnoreCase(status) || !winnerSlot.isEmpty() || (!s1.isEmpty() && !s2.isEmpty())) {
                        status = "COMPLETED";
                    } else if (status == null || status.trim().isEmpty()) {
                        status = "SCHEDULED";
                    }

                    sb.append("{")
                            .append("\"matchId\":\"").append(escapeJson(rawId)).append("\",")
                            .append("\"id\":\"").append(escapeJson(rawId)).append("\",")
                            .append("\"rawId\":\"").append(escapeJson(rawId)).append("\",")
                            .append("\"bracketType\":\"").append(escapeJson(bracketType != null ? bracketType : "WINNER_BRACKET")).append("\",")
                            .append("\"roundNumber\":").append(roundNumber).append(",")
                            .append("\"matchNumber\":").append(matchNum != null ? matchNum : "null").append(",")
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

        ParticipantDAO pDao = new ParticipantDAO();
        List<Team> teams = pDao.getTeamsByTournamentId(tournamentId);
        if (teams == null || teams.isEmpty()) return;

        String checkSql = "SELECT COUNT(*), "
                + "SUM(CASE WHEN bracket_type IN ('LOSER_BRACKET', 'LOWER', 'LB') THEN 1 ELSE 0 END) AS lb_count "
                + "FROM matches m "
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
                if (rs.next()) {
                    int totalCount = rs.getInt(1);
                    int lbCount = rs.getInt(2);
                    if (totalCount > 0 && (lbCount > 0 || teams.size() < 3)) {
                        return; // Already initialized!
                    }
                    if (totalCount > 0) {
                        // Stale matches detected (e.g. from SE initialization): delete unplayed matches to recreate DE bracket
                        try (PreparedStatement psUnlink = conn.prepareStatement(
                                "UPDATE matches SET next_match_id = NULL, loser_next_match_id = NULL WHERE tournament_id = ?")) {
                            psUnlink.setString(1, tournamentId);
                            psUnlink.executeUpdate();
                        }
                        try (PreparedStatement psDel = conn.prepareStatement(
                                "DELETE FROM matches WHERE tournament_id = ? AND (status IS NULL OR status = 'PENDING' OR status = 'SCHEDULED' OR status = 'READY')")) {
                            psDel.setString(1, tournamentId);
                            psDel.executeUpdate();
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

        initializeDoubleEliminationInDB(tournamentId, stageOrder, teams);
    }

    private void initializeDoubleEliminationInDB(String tournamentId, int stageOrder, List<Team> teams) {
        int n = teams.size();
        if (n < 2) return;

        int bracketSize = 1;
        while (bracketSize < n) bracketSize *= 2;
        int upperRounds = (int) (Math.log(bracketSize) / Math.log(2));
        int totalLowerRounds = (upperRounds - 1) * 2;

        String stageId = lookupOrCreateStageId(tournamentId, stageOrder);
        int[] seeds = generateSeedingArray(bracketSize);
        Map<Integer, Team> seedToTeam = new HashMap<>();
        for (int i = 0; i < teams.size(); i++) {
            Team tm = teams.get(i);
            int seed = tm.getOriginalSeed() > 0 ? tm.getOriginalSeed() : (i + 1);
            seedToTeam.put(seed, tm);
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
                node.roundNumber = r;
                node.tournamentId = tournamentId;
                node.stageId = stageId;
                node.matchCode = (r == upperRounds) ? "UB Final" : ("UB R" + r + " #" + (m + 1));
                node.bracketType = "WINNER_BRACKET";

                if (r == 1) {
                    Team t1 = seedToTeam.get(seeds[m * 2]);
                    Team t2 = seedToTeam.get(seeds[m * 2 + 1]);
                    node.team1Id = (t1 != null) ? t1.getId() : null;
                    node.team2Id = (t2 != null) ? t2.getId() : null;
                    boolean isBye = (node.team1Id == null || node.team2Id == null);
                    node.isBye = isBye;
                    if (isBye) {
                        node.status = "FINISHED";
                        node.winnerId = (node.team1Id != null) ? node.team1Id : node.team2Id;
                    } else {
                        node.status = (node.team1Id != null && node.team2Id != null) ? "READY" : "PENDING";
                    }
                } else {
                    node.isBye = false;
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
        List<Integer> lbMatchesPerRound = new ArrayList<>();
        int matchCountInLb = bracketSize / 4;
        if (matchCountInLb < 1) matchCountInLb = 1;
        for (int lr = 1; lr <= totalLowerRounds; lr++) {
            lbMatchesPerRound.add(matchCountInLb);
            if (lr % 2 == 0) {
                matchCountInLb = Math.max(1, matchCountInLb / 2);
            }
        }

        Map<Integer, List<DeNode>> lowerRoundNodes = new HashMap<>();
        for (int lr = 1; lr <= totalLowerRounds; lr++) {
            int mCount = lbMatchesPerRound.get(lr - 1);
            boolean isMajorRound = (lr % 2 == 0);
            List<DeNode> roundNodes = new ArrayList<>();

            for (int m = 0; m < mCount; m++) {
                DeNode node = new DeNode();
                node.id = "M_" + tournamentId + "_LB_" + matchSeq;
                node.roundNumber = lr;
                node.tournamentId = tournamentId;
                node.stageId = stageId;
                node.matchCode = (lr == totalLowerRounds) ? "LB Final" : ("LB R" + lr + " #" + (m + 1));
                node.bracketType = "LOSER_BRACKET";
                node.status = "PENDING";
                node.isBye = false;

                roundNodes.add(node);
                allMatches.add(node);
                matchSeq++;
            }

            if (lr > 1) {
                List<DeNode> prevNodes = lowerRoundNodes.get(lr - 1);
                if (isMajorRound) {
                    // Major round: Winner of preceding LB round goes to Slot 1
                    for (int i = 0; i < prevNodes.size(); i++) {
                        if (i < roundNodes.size()) {
                            DeNode prev = prevNodes.get(i);
                            DeNode next = roundNodes.get(i);
                            prev.nextMatchId = next.id;
                            prev.nextSlot = "SLOT_1";
                        }
                    }
                } else {
                    // Minor round: Winners of preceding LB round pair up (Slot 1 & Slot 2)
                    for (int i = 0; i < prevNodes.size(); i++) {
                        if (i / 2 < roundNodes.size()) {
                            DeNode prev = prevNodes.get(i);
                            DeNode next = roundNodes.get(i / 2);
                            prev.nextMatchId = next.id;
                            prev.nextSlot = (i % 2 == 0) ? "SLOT_1" : "SLOT_2";
                        }
                    }
                }
            }

            lowerRoundNodes.put(lr, roundNodes);
        }

        // 3. Link Drop Downs from Upper Bracket to Lower Bracket
        // LB Round 1 Drop Downs:
        List<DeNode> ubR1 = upperRoundNodes.get(1);
        List<DeNode> lbR1 = lowerRoundNodes.get(1);
        if (ubR1 != null && lbR1 != null) {
            int totalUbR1 = ubR1.size();
            for (int k = 0; k < lbR1.size(); k++) {
                DeNode ubM1 = ubR1.get(k);
                DeNode ubM2 = ubR1.get(totalUbR1 - 1 - k);
                DeNode lbNode = lbR1.get(k);

                boolean b1 = (ubM1 == null || ubM1.isBye);
                boolean b2 = (ubM2 == null || ubM2.isBye);

                if (ubM1 != null && lbNode != null && !ubM1.isBye) {
                    ubM1.loserNextMatchId = lbNode.id;
                    ubM1.loserNextSlot = "SLOT_1";
                }
                if (ubM2 != null && lbNode != null && !ubM2.isBye) {
                    ubM2.loserNextMatchId = lbNode.id;
                    ubM2.loserNextSlot = "SLOT_2";
                }

                if (b1 && b2) {
                    // Double-BYE: no team drops into either slot
                    lbNode.isBye = true;
                    lbNode.status = "FINISHED";
                    lbNode.winnerId = null;
                } else if (b1 || b2) {
                    // Single-BYE: exactly 1 team will drop in and auto-advance
                    lbNode.isBye = true;
                    lbNode.status = "PENDING";
                } else {
                    lbNode.isBye = false;
                    lbNode.status = "PENDING";
                }
            }
        }

        // UB Round 2+ losers drop to Major LB rounds with Branch Cross-Over
        for (int ur = 2; ur <= upperRounds; ur++) {
            int targetLbRound = (ur - 1) * 2;
            List<DeNode> ubMatches = upperRoundNodes.get(ur);
            List<DeNode> lbMajorMatches = lowerRoundNodes.get(targetLbRound);
            List<DeNode> prevLbMatches = lowerRoundNodes.get(targetLbRound - 1);

            if (ubMatches != null && lbMajorMatches != null) {
                int mCount = ubMatches.size();
                for (int u = 0; u < mCount; u++) {
                    int targetIdx = u;
                    if (mCount >= 4) {
                        int halfM = mCount / 2;
                        if (u < halfM) {
                            targetIdx = halfM - 1 - u;
                        } else {
                            targetIdx = u;
                        }
                    } else if (mCount == 2) {
                        targetIdx = (u == 0) ? 1 : 0;
                    } else {
                        targetIdx = 0;
                    }

                    if (targetIdx < lbMajorMatches.size()) {
                        DeNode ubNode = ubMatches.get(u);
                        DeNode lbNode = lbMajorMatches.get(targetIdx);
                        ubNode.loserNextMatchId = lbNode.id;
                        ubNode.loserNextSlot = "SLOT_2";

                        // If slot 1 comes from a double-BYE match (no team arrives from previous LB round),
                        // this major round match is also a single-BYE (the UB loser auto-advances).
                        if (prevLbMatches != null && targetIdx < prevLbMatches.size()) {
                            DeNode prevLb = prevLbMatches.get(targetIdx);
                            if (prevLb != null && prevLb.isBye && prevLb.winnerId == null) {
                                lbNode.isBye = true;
                                lbNode.status = "PENDING";
                            }
                        }
                    }
                }
            }
        }

        // 4. Build Grand Final
        DeNode gf = new DeNode();
        gf.id = "M_" + tournamentId + "_GF_" + matchSeq;
        gf.roundNumber = upperRounds + 1;
        gf.tournamentId = tournamentId;
        gf.stageId = stageId;
        gf.matchCode = "Trận Chung Kết";
        gf.bracketType = "GRAND_FINAL";
        gf.status = "PENDING";
        gf.isBye = false;
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

        // Contiguous numbering: ONLY playable non-BYE matches get match numbers 1, 2, 3...
        int playableCounter = 1;
        // 1. UB Round 1
        if (upperRoundNodes.get(1) != null) {
            for (DeNode node : upperRoundNodes.get(1)) {
                if (node.isBye) {
                    node.matchNumber = null;
                    node.matchCode = "BYE";
                } else {
                    node.matchNumber = playableCounter++;
                    node.matchCode = "UB R1 #" + node.matchNumber;
                }
            }
        }
        // 2. LB Round 1
        if (lowerRoundNodes.get(1) != null) {
            for (DeNode node : lowerRoundNodes.get(1)) {
                if (node.isBye) {
                    node.matchNumber = null;
                    node.matchCode = "BYE";
                } else {
                    node.matchNumber = playableCounter++;
                    node.matchCode = "LB R1 #" + node.matchNumber;
                }
            }
        }
        // 3. Subsequent rounds in play order
        for (int k = 2; k <= upperRounds; k++) {
            // UB Round k
            if (upperRoundNodes.get(k) != null) {
                for (DeNode node : upperRoundNodes.get(k)) {
                    if (node.isBye) {
                        node.matchNumber = null;
                        node.matchCode = "BYE";
                    } else {
                        node.matchNumber = playableCounter++;
                        node.matchCode = (k == upperRounds) ? "UB Final" : ("UB R" + k + " #" + node.matchNumber);
                    }
                }
            }
            // LB Even Round (receives UB drop)
            int lbEven = (k - 1) * 2;
            if (lowerRoundNodes.get(lbEven) != null) {
                for (DeNode node : lowerRoundNodes.get(lbEven)) {
                    if (node.isBye) {
                        node.matchNumber = null;
                        node.matchCode = "BYE";
                    } else {
                        node.matchNumber = playableCounter++;
                        node.matchCode = (lbEven == totalLowerRounds) ? "LB Final" : ("LB R" + lbEven + " #" + node.matchNumber);
                    }
                }
            }
            // LB Odd Round (intra-LB round)
            int lbOdd = (k - 1) * 2 + 1;
            if (lbOdd <= totalLowerRounds && lowerRoundNodes.get(lbOdd) != null) {
                for (DeNode node : lowerRoundNodes.get(lbOdd)) {
                    if (node.isBye) {
                        node.matchNumber = null;
                        node.matchCode = "BYE";
                    } else {
                        node.matchNumber = playableCounter++;
                        node.matchCode = (lbOdd == totalLowerRounds) ? "LB Final" : ("LB R" + lbOdd + " #" + node.matchNumber);
                    }
                }
            }
        }
        // Grand Final
        gf.matchNumber = playableCounter++;
        gf.matchCode = "Trận Chung Kết";

        // Insert into database in 2 passes to guarantee zero foreign key constraint conflicts
        String insertSql = "INSERT INTO matches (id, tournament_id, stage_id, round_number, match_order, match_code, bracket_type, "
                + "team1_id, team2_id, is_bye, status) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        String updateLinksSql = "UPDATE matches SET next_match_id = ?, next_slot = ?, loser_next_match_id = ?, loser_next_slot = ?, winner_id = ? WHERE id = ?";

        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            
            // Pass 1: Insert all match records
            try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                for (DeNode node : allMatches) {
                    ps.setString(1, node.id);
                    ps.setString(2, node.tournamentId);
                    ps.setString(3, node.stageId);
                    ps.setInt(4, node.roundNumber);
                    if (node.matchNumber != null) ps.setInt(5, node.matchNumber); else ps.setNull(5, Types.INTEGER);
                    ps.setString(6, node.matchCode);
                    ps.setString(7, node.bracketType);
                    if (node.team1Id != null) ps.setString(8, node.team1Id); else ps.setNull(8, Types.VARCHAR);
                    if (node.team2Id != null) ps.setString(9, node.team2Id); else ps.setNull(9, Types.VARCHAR);
                    ps.setBoolean(10, node.isBye);
                    ps.setString(11, node.status);
                    ps.addBatch();
                }
                ps.executeBatch();
            }

            // Pass 2: Update next_match_id & loser_next_match_id links now that all match IDs exist in DB
            try (PreparedStatement psUp = conn.prepareStatement(updateLinksSql)) {
                for (DeNode node : allMatches) {
                    if (node.nextMatchId != null || node.loserNextMatchId != null || node.winnerId != null) {
                        if (node.nextMatchId != null) psUp.setString(1, node.nextMatchId); else psUp.setNull(1, Types.VARCHAR);
                        if (node.nextSlot != null) psUp.setString(2, node.nextSlot); else psUp.setNull(2, Types.VARCHAR);
                        if (node.loserNextMatchId != null) psUp.setString(3, node.loserNextMatchId); else psUp.setNull(3, Types.VARCHAR);
                        if (node.loserNextSlot != null) psUp.setString(4, node.loserNextSlot); else psUp.setNull(4, Types.VARCHAR);
                        if (node.winnerId != null) psUp.setString(5, node.winnerId); else psUp.setNull(5, Types.VARCHAR);
                        psUp.setString(6, node.id);
                        psUp.addBatch();
                    }
                }
                psUp.executeBatch();
            }

            // Pass 3: Propagate BYE winners to next round matches
            for (DeNode node : allMatches) {
                if (node.isBye && node.winnerId != null && node.nextMatchId != null) {
                    String slotCol = ("SLOT_2".equalsIgnoreCase(node.nextSlot)) ? "team2_id" : "team1_id";
                    String otherSlotCol = ("SLOT_2".equalsIgnoreCase(node.nextSlot)) ? "team1_id" : "team2_id";
                    String advSql = "UPDATE matches SET " + slotCol + " = ?, status = CASE WHEN (" + otherSlotCol + " IS NOT NULL) THEN 'READY' ELSE status END WHERE id = ?";
                    try (PreparedStatement psAdv = conn.prepareStatement(advSql)) {
                        psAdv.setString(1, node.winnerId);
                        psAdv.setString(2, node.nextMatchId);
                        psAdv.executeUpdate();
                    }
                }
            }

            conn.commit();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static class DeNode {
        String id;
        Integer matchNumber;
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
        boolean isBye;
        String winnerId;
        String status;
    }

    private int[] generateSeedingArray(int bracketSize) {
        int[] seeds = new int[]{1, 2};
        while (seeds.length < bracketSize) {
            int nextLen = seeds.length * 2;
            int sum = nextLen + 1;
            int[] next = new int[nextLen];
            for (int i = 0; i < seeds.length; i++) {
                int s = seeds[i];
                int comp = sum - s;
                if (i % 2 == 0) {
                    next[i * 2] = s;
                    next[i * 2 + 1] = comp;
                } else {
                    next[i * 2] = comp;
                    next[i * 2 + 1] = s;
                }
            }
            seeds = next;
        }
        int[] result = new int[bracketSize];
        for (int i = 0; i < bracketSize / 2; i++) {
            int a = seeds[i * 2];
            int b = seeds[i * 2 + 1];
            result[i * 2] = Math.min(a, b);
            result[i * 2 + 1] = Math.max(a, b);
        }
        return result;
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
        String tId = tournamentId.trim();

        // 1. Break self-referencing foreign keys first
        String nullLinksSql = "UPDATE matches SET next_match_id = NULL, loser_next_match_id = NULL WHERE tournament_id = ?";
        try (Connection conn = getConnection();
             PreparedStatement psNull = conn.prepareStatement(nullLinksSql)) {
            psNull.setString(1, tId);
            psNull.executeUpdate();
        } catch (Exception ignore) {}

        // 2. Delete all existing matches for this tournament and stage
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

        // 3. Reset tournament overall status
        String resetTourneySql = "UPDATE tournaments SET status = 'DRAFT', champion_name = NULL WHERE id = ?";
        try (Connection conn = getConnection();
             PreparedStatement psT = conn.prepareStatement(resetTourneySql)) {
            psT.setString(1, tId);
            psT.executeUpdate();
        } catch (Exception ignore) {}

        try (Connection conn = getConnection();
             PreparedStatement psS1 = conn.prepareStatement("UPDATE tournaments SET stage1_status = 'PENDING' WHERE id = ?")) {
            psS1.setString(1, tId);
            psS1.executeUpdate();
        } catch (Exception ignore) {}

        // 4. Rebuild 100% fresh Double Elimination match tree
        ParticipantDAO pDao = new ParticipantDAO();
        List<Team> teams = pDao.getTeamsByTournamentId(tId);
        if (teams != null && !teams.isEmpty()) {
            initializeDoubleEliminationInDB(tId, stageOrder, teams);
        }

        service.RollingWindowPointService.clearAllCaches();
        return true;
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
