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
    private static class DeInput {
        DeNode source;
        boolean loser;
        DeInput(DeNode source, boolean loser) { this.source = source; this.loser = loser; }
    }

    /** Resolve BYEs from completed feeders, including empty branches, inside the caller's transaction. */
    public void syncByeAdvancementsInDB(Connection conn, String tournamentId, int stageOrder) throws Exception {
        String stageId = null;
        try (PreparedStatement ps = conn.prepareStatement("SELECT id FROM tournament_stages WHERE tournament_id = ? AND stage_order = ?")) {
            ps.setString(1, tournamentId); ps.setInt(2, stageOrder);
            try (ResultSet rs = ps.executeQuery()) { if (rs.next()) stageId = rs.getString(1); else if (stageOrder != 1) return; }
        }
        syncStageByes(conn, tournamentId, stageId);
    }

    public void syncByeAdvancementsInDB(String tournamentId, int stageOrder) {
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            syncByeAdvancementsInDB(conn, tournamentId, stageOrder);
            conn.commit();
        } catch (Exception e) { throw new IllegalStateException("Không thể đồng bộ nhánh DE mà vẫn bảo toàn kết quả.", e); }
    }

    private void syncStageByes(Connection conn, String tournamentId, String stageId) throws Exception {
        Map<String, DeNode> nodes = new java.util.LinkedHashMap<>();
        Map<String, List<Object>> original = new HashMap<>();
        try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM matches WITH (UPDLOCK, HOLDLOCK) WHERE tournament_id = ? AND (stage_id = ? OR (stage_id IS NULL AND ? IS NULL))")) {
            ps.setString(1, tournamentId); ps.setString(2, stageId); ps.setString(3, stageId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    DeNode node = new DeNode(); node.id = rs.getString("id");
                    node.team1Id = rs.getString("team1_id"); node.team2Id = rs.getString("team2_id");
                    node.winnerId = rs.getString("winner_id"); node.status = rs.getString("status"); node.isBye = rs.getBoolean("is_bye");
                    node.nextMatchId = rs.getString("next_match_id"); node.nextSlot = rs.getString("next_slot");
                    node.loserNextMatchId = rs.getString("loser_next_match_id"); node.loserNextSlot = rs.getString("loser_next_slot");
                    node.hasScore = rs.getObject("score1") != null || rs.getObject("score2") != null;
                    nodes.put(node.id, node); original.put(node.id, deState(node));
                }
            }
        }
        resolveDeByes(nodes);
        String sql = "UPDATE matches SET team1_id = ?, team2_id = ?, winner_id = ?, loser_id = ?, is_bye = ?, status = ? WHERE id = ? AND tournament_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (DeNode node : nodes.values()) {
                if (original.get(node.id).equals(deState(node))) continue;
                String loser = node.winnerId == null || node.isBye ? null
                    : node.winnerId.equals(node.team1Id) ? node.team2Id : node.team1Id;
                ps.setString(1, node.team1Id); ps.setString(2, node.team2Id); ps.setString(3, node.winnerId); ps.setString(4, loser);
                ps.setBoolean(5, node.isBye); ps.setString(6, node.status); ps.setString(7, node.id); ps.setString(8, tournamentId); ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private static void resolveDeByes(Map<String, DeNode> nodes) {
        Map<String, DeInput[]> incoming = new HashMap<>();
        for (DeNode source : nodes.values()) {
            addDeInput(incoming, nodes, source.nextMatchId, source.nextSlot, source, false);
            addDeInput(incoming, nodes, source.loserNextMatchId, source.loserNextSlot, source, true);
        }
        boolean changed;
        int passes = 0;
        do {
            changed = false;
            for (DeNode node : nodes.values()) {
                List<Object> before = deState(node);
                DeInput[] inputs = incoming.getOrDefault(node.id, new DeInput[2]);
                boolean firstReady = deInputResolved(inputs[0]);
                boolean secondReady = deInputResolved(inputs[1]);
                if (inputs[0] != null) node.team1Id = acceptDeInput(node, node.team1Id, firstReady ? deInputTeam(inputs[0]) : null);
                if (inputs[1] != null) node.team2Id = acceptDeInput(node, node.team2Id, secondReady ? deInputTeam(inputs[1]) : null);
                if (firstReady && secondReady) {
                    boolean bye = node.team1Id == null || node.team2Id == null;
                    if (bye && node.hasScore) throw new IllegalStateException("Trận đã có tỉ số nhưng chưa đủ đội. Cần kiểm tra kết quả trước khi tiếp tục.");
                    if (!bye && node.isBye && !node.hasScore) node.winnerId = null;
                    node.isBye = bye;
                    if (bye) {
                        node.winnerId = node.team1Id != null ? node.team1Id : node.team2Id;
                        node.status = "FINISHED";
                    } else if (node.winnerId == null) {
                        node.status = "READY";
                    } else {
                        if (!node.winnerId.equals(node.team1Id) && !node.winnerId.equals(node.team2Id)) {
                            throw new IllegalStateException("Đội thắng DE không thuộc trận đấu.");
                        }
                        node.status = "FINISHED";
                    }
                } else if (node.isBye) {
                    node.winnerId = null;
                    node.status = "PENDING";
                }
                if (!before.equals(deState(node))) changed = true;
            }
            if (++passes > nodes.size() + 1) throw new IllegalStateException("Nhánh DE có liên kết vòng.");
        } while (changed);
    }

    private static List<Object> deState(DeNode node) {
        return java.util.Arrays.asList(node.team1Id, node.team2Id, node.winnerId, node.isBye, node.status);
    }

    private static void addDeInput(Map<String, DeInput[]> incoming, Map<String, DeNode> nodes,
            String target, String slot, DeNode source, boolean loser) {
        if (target == null) return;
        if (!nodes.containsKey(target)) throw new IllegalStateException("Liên kết DE vượt ra ngoài stage hiện tại.");
        int index = "SLOT_2".equalsIgnoreCase(slot) || "2".equals(slot) ? 1 : 0;
        DeInput[] inputs = incoming.computeIfAbsent(target, key -> new DeInput[2]);
        if (inputs[index] != null) throw new IllegalStateException("Hai trận DE cùng đẩy đội vào một slot.");
        inputs[index] = new DeInput(source, loser);
    }

    private static boolean deInputResolved(DeInput input) {
        if (input == null) return true;
        DeNode source = input.source;
        return source.winnerId != null || (source.team1Id == null && source.team2Id == null && "FINISHED".equalsIgnoreCase(source.status));
    }

    private static String deInputTeam(DeInput input) {
        DeNode source = input.source;
        if (!input.loser) return source.winnerId;
        if (source.winnerId == null || source.team1Id == null || source.team2Id == null) return null;
        return source.winnerId.equals(source.team1Id) ? source.team2Id : source.team1Id;
    }

    private static String acceptDeInput(DeNode target, String previous, String incoming) {
        if (!java.util.Objects.equals(previous, incoming) && (target.hasScore || (target.winnerId != null && !target.isBye))) {
            throw new IllegalStateException("Kết quả vòng sau không khớp đội từ vòng trước. Cần reset các trận liên quan trước.");
        }
        return incoming;
    }

    /** A changed winner cannot silently replace opponents in matches already played. */
    public void assertResultChangeAllowed(Connection conn, String tournamentId, String stageId, String matchId) throws Exception {
        Map<String, String[]> graph = new HashMap<>();
        try (PreparedStatement ps = conn.prepareStatement("SELECT id, next_match_id, loser_next_match_id, score1, score2, winner_id, is_bye FROM matches WITH (UPDLOCK, HOLDLOCK) WHERE tournament_id = ? AND (stage_id = ? OR (stage_id IS NULL AND ? IS NULL))")) {
            ps.setString(1, tournamentId); ps.setString(2, stageId); ps.setString(3, stageId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) graph.put(rs.getString(1), new String[]{rs.getString(2), rs.getString(3),
                    rs.getObject(4) != null || rs.getObject(5) != null || (rs.getString(6) != null && !rs.getBoolean(7)) ? "played" : ""});
            }
        }
        java.util.ArrayDeque<String> pending = new java.util.ArrayDeque<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        pending.add(matchId);
        while (!pending.isEmpty()) {
            String current = pending.remove();
            if (!seen.add(current)) continue;
            String[] node = graph.get(current);
            if (node == null) throw new IllegalStateException("Liên kết DE không thuộc stage hiện tại.");
            if (!current.equals(matchId) && "played".equals(node[2])) {
                throw new IllegalStateException("Vòng sau đã có kết quả. Hãy reset các trận liên quan trước khi đổi đội thắng.");
            }
            if (node[0] != null) pending.add(node[0]);
            if (node[1] != null) pending.add(node[1]);
        }
    }

    /** Explicit match reset clears only dependent results and the slots fed by them. */
    public void resetMatchAndDependants(Connection conn, String tournamentId, String stageId, String matchId) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement("SELECT t.stage1_status, s.stage_order FROM tournaments t JOIN tournament_stages s ON s.tournament_id = t.id WHERE t.id = ? AND s.id = ?")) {
            ps.setString(1, tournamentId); ps.setString(2, stageId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next() && rs.getInt(2) == 1 && ("LOCKED".equalsIgnoreCase(rs.getString(1)) || "COMPLETED".equalsIgnoreCase(rs.getString(1)))) {
                    throw new IllegalStateException("Cần mở khóa Stage 1 trước khi reset kết quả trận.");
                }
            }
        }
        Map<String, String[]> graph = new HashMap<>();
        try (PreparedStatement ps = conn.prepareStatement("SELECT id, next_match_id, next_slot, loser_next_match_id, loser_next_slot FROM matches WITH (UPDLOCK, HOLDLOCK) WHERE tournament_id = ? AND (stage_id = ? OR (stage_id IS NULL AND ? IS NULL))")) {
            ps.setString(1, tournamentId); ps.setString(2, stageId); ps.setString(3, stageId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) graph.put(rs.getString(1), new String[]{rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5)});
            }
        }
        Map<String, boolean[]> affected = new java.util.LinkedHashMap<>();
        java.util.ArrayDeque<String> pending = new java.util.ArrayDeque<>();
        affected.put(matchId, new boolean[2]); pending.add(matchId);
        while (!pending.isEmpty()) {
            String current = pending.remove();
            String[] links = graph.get(current);
            if (links == null) throw new IllegalStateException("Liên kết DE vượt ra ngoài stage hiện tại.");
            for (int edge = 0; edge < 4; edge += 2) {
                if (links[edge] == null) continue;
                boolean fresh = !affected.containsKey(links[edge]);
                boolean[] slots = affected.computeIfAbsent(links[edge], key -> new boolean[2]);
                slots["SLOT_2".equalsIgnoreCase(links[edge + 1]) || "2".equals(links[edge + 1]) ? 1 : 0] = true;
                if (fresh) pending.add(links[edge]);
            }
        }
        String sql = "UPDATE matches SET team1_id = CASE WHEN ? = 1 THEN NULL ELSE team1_id END, team2_id = CASE WHEN ? = 1 THEN NULL ELSE team2_id END, score1 = NULL, score2 = NULL, penalty1 = NULL, penalty2 = NULL, winner_id = NULL, loser_id = NULL, status = 'PENDING' WHERE id = ? AND tournament_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (Map.Entry<String, boolean[]> item : affected.entrySet()) {
                ps.setInt(1, item.getValue()[0] ? 1 : 0); ps.setInt(2, item.getValue()[1] ? 1 : 0);
                ps.setString(3, item.getKey()); ps.setString(4, tournamentId); ps.addBatch();
            }
            ps.executeBatch();
        }
        syncStageByes(conn, tournamentId, stageId);
    }

    public List<Match> getMatchesByTournamentId(int tournamentId) {
        return getMatchesByTournamentId(String.valueOf(tournamentId));
    }

    public Map<String, Object> getDoubleEliminationData(int tournamentId) {
        return getDoubleEliminationData(String.valueOf(tournamentId));
    }

    public List<Match> getMatchesByTournamentId(String tournamentId) {
        return getMatchesByTournamentId(tournamentId, 1);
    }

    public List<Match> getMatchesByTournamentId(String tournamentId, int stageOrder) {
        List<Match> list = new ArrayList<>();
        if (tournamentId == null || tournamentId.trim().isEmpty()) return list;

        String sql = "SELECT m.*, "
                + "t1.raw_name AS t1_name, t1.original_seed AS t1_seed, "
                + "t2.raw_name AS t2_name, t2.original_seed AS t2_seed, "
                + "tw.raw_name AS winner_name "
                + "FROM matches m LEFT JOIN tournament_stages stage_scope ON stage_scope.id = m.stage_id "
                + "LEFT JOIN teams t1 ON m.team1_id = t1.id "
                + "LEFT JOIN teams t2 ON m.team2_id = t2.id "
                + "LEFT JOIN teams tw ON m.winner_id = tw.id "
                + "WHERE m.tournament_id = ? AND (stage_scope.stage_order = ? OR (m.stage_id IS NULL AND ? = 1)) "
                + "ORDER BY m.round_number ASC, m.id ASC";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tournamentId);
            ps.setInt(2, stageOrder); ps.setInt(3, stageOrder);
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

                    m.setTeam1StringId(rs.getString("team1_id"));
                    m.setTeam1Name(rs.getString("t1_name"));
                    int s1Seed = rs.getInt("t1_seed");
                    if (!rs.wasNull()) m.setTeam1Seed(s1Seed);
                    int s1 = rs.getInt("score1");
                    if (!rs.wasNull()) m.setTeam1Score(s1);

                    m.setTeam2StringId(rs.getString("team2_id"));
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
        return getDoubleEliminationData(tournamentId, 1);
    }

    public Map<String, Object> getDoubleEliminationData(String tournamentId, int stageOrder) {
        Map<String, Object> dataMap = new HashMap<>();
        List<Match> matchList = getMatchesByTournamentId(tournamentId, stageOrder);

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
        if(stageOrder>1) {
            try(Connection access=getConnection()){TournamentDAO.assertStage2Ready(access,tournamentId,stageOrder);}
            catch(Exception notReady){return "[]";}
        }
        if (tournamentId == null || tournamentId.trim().isEmpty()) return "[]";

        int cut = new SingleEliminationDAO().getTournamentCutTarget(tournamentId, stageOrder);
        if (cut > 1) {
            int count = new ParticipantDAO().getTeamsByTournamentId(tournamentId).size();
            if (!service.CountAdvanceTeamService.isValidDECutTarget(count, cut)) throw new IllegalStateException("Cấu hình cut DE không hợp lệ.");
            int ub = service.DECutService.calculateUbStoppingRound(count, cut);
            new TournamentDAO().trimCutMatches(tournamentId, ub, service.DECutService.calculateLbStoppingRound(ub));
        }

        // Auto-initialize DE bracket tree in DB if none exists
        ensureBracketInitialized(tournamentId, stageOrder);
        syncByeAdvancementsInDB(tournamentId, stageOrder);

        String sql = "SELECT m.*, "
                + "t1.raw_name AS t1_name, t1.original_seed AS t1_seed, "
                + "t2.raw_name AS t2_name, t2.original_seed AS t2_seed, "
                + "tw.raw_name AS winner_name "
                + "FROM matches m "
                + "LEFT JOIN tournament_stages s ON m.stage_id = s.id AND s.tournament_id = m.tournament_id "
                + "LEFT JOIN teams t1 ON m.team1_id = t1.id "
                + "LEFT JOIN teams t2 ON m.team2_id = t2.id "
                + "LEFT JOIN teams tw ON m.winner_id = tw.id "
                + "WHERE m.tournament_id = ? AND (s.stage_order = ? OR (m.stage_id IS NULL AND ? = 1)) "
                + "ORDER BY m.round_number ASC, LEN(m.id) ASC, m.id ASC";

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
                            .append("\"id\":\"").append(escapeJson(rs.getString("team1_id") != null ? rs.getString("team1_id") : "")).append("\",")
                            .append("\"name\":\"").append(escapeJson(t1Name != null ? t1Name : "")).append("\",")
                            .append("\"seed\":\"").append(escapeJson(t1Seed)).append("\",")
                            .append("\"score\":\"").append(escapeJson(s1)).append("\"")
                            .append("},")
                            .append("\"team2\":{")
                            .append("\"id\":\"").append(escapeJson(rs.getString("team2_id") != null ? rs.getString("team2_id") : "")).append("\",")
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

        List<Team> teams;
        if (stageOrder > 1) {
            teams = new SingleEliminationDAO().getStage2TeamsList(tournamentId);
            if (teams == null || teams.size() < 2) {
                return;
            }
        } else {
            ParticipantDAO pDao = new ParticipantDAO();
            teams = pDao.getTeamsByTournamentId(tournamentId);
            if (teams == null || teams.isEmpty()) return;
        }

        String checkSql = "SELECT COUNT(*), "
                + "SUM(CASE WHEN bracket_type IN ('LOSER_BRACKET', 'LOWER', 'LB') THEN 1 ELSE 0 END) AS lb_count "
                + "FROM matches m "
                + "LEFT JOIN tournament_stages s ON m.stage_id = s.id AND s.tournament_id = m.tournament_id "
                + "WHERE m.tournament_id = ? AND (s.stage_order = ? OR (m.stage_id IS NULL AND ? = 1))";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(checkSql)) {
            ps.setString(1, tournamentId);
            ps.setInt(2, stageOrder);
            ps.setInt(3, stageOrder);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    int totalCount = rs.getInt(1);
                    int lbCount = rs.getInt(2);
                    if (totalCount > 0) return; // Never automatically replace existing stage data.

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
        String prefix = "M_" + tournamentId + (stageOrder > 1 ? "_S" + stageOrder + "_" + java.util.UUID.randomUUID().toString().substring(0,8) : "") + "_";
        Integer configuredSeeds = stageOrder == 1 ? new TournamentDAO().getTopSeedCount(tournamentId) : null;
        List<Team> initialSlots = stageOrder == 1
                ? service.SECutService.drawInitialSlots(teams, configuredSeeds == null ? 0 : configuredSeeds,
                    new TournamentDAO().isClassicDraw(tournamentId)) : null;

        boolean seededGroupField = stageOrder>1 && (n & (n-1))!=0
                && "GROUP_STAGE".equalsIgnoreCase(new TournamentDAO().getStage1Format(tournamentId));
        int[] groupSeeds = seededGroupField ? generateSeedingArray(bracketSize) : null;

        List<DeNode> allMatches = new ArrayList<>();
        int matchSeq = 1;

        // 1. Build Upper Bracket Matches
        Map<Integer, List<DeNode>> upperRoundNodes = new HashMap<>();
        int currUpperMatches = bracketSize / 2;
        for (int r = 1; r <= upperRounds; r++) {
            List<DeNode> roundNodes = new ArrayList<>();
            for (int m = 0; m < currUpperMatches; m++) {
                DeNode node = new DeNode();
                node.id = prefix + "UB_" + matchSeq;
                node.roundNumber = r;
                node.tournamentId = tournamentId;
                node.stageId = stageId;
                node.matchCode = (r == upperRounds) ? "UB Final" : ("UB R" + r + " #" + (m + 1));
                node.bracketType = "WINNER_BRACKET";

                if (r == 1) {
                    Team t1, t2;
                    if (stageOrder > 1) {
                        // Stage 2: sequentially pair from the qualified teams list
                        int idx1 = seededGroupField ? groupSeeds[m * 2] - 1 : m * 2;
                        int idx2 = seededGroupField ? groupSeeds[m * 2 + 1] - 1 : m * 2 + 1;
                        t1 = (idx1 < n) ? teams.get(idx1) : null;
                        t2 = (idx2 < n) ? teams.get(idx2) : null;
                    } else {
                        t1 = initialSlots.get(m * 2);
                        t2 = initialSlots.get(m * 2 + 1);
                    }
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
                node.id = prefix + "LB_" + matchSeq;
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
                            if (prevLb != null && prevLb.isBye && prevLb.winnerId == null && "FINISHED".equals(prevLb.status)) {
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
        gf.id = prefix + "GF_" + matchSeq;
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
            if (totalLowerRounds == 0) {
                ubFinal.get(0).loserNextMatchId = gf.id;
                ubFinal.get(0).loserNextSlot = "SLOT_2";
            }
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

        int cutTarget = new SingleEliminationDAO().getTournamentCutTarget(tournamentId, stageOrder);
        if (cutTarget > 1) {
            if (!service.CountAdvanceTeamService.isValidDECutTarget(n, cutTarget)) throw new IllegalStateException("Cấu hình cut DE không hợp lệ.");
            int ubStop = service.DECutService.calculateUbStoppingRound(n, cutTarget);
            int lbStop = service.DECutService.calculateLbStoppingRound(ubStop);
            allMatches.removeIf(node -> "GRAND_FINAL".equals(node.bracketType)
                || ("WINNER_BRACKET".equals(node.bracketType) && node.roundNumber > ubStop)
                || ("LOSER_BRACKET".equals(node.bracketType) && node.roundNumber > lbStop));
            java.util.Set<String> retained = new java.util.HashSet<>();
            for (DeNode node : allMatches) retained.add(node.id);
            for (DeNode node : allMatches) {
                if (!retained.contains(node.nextMatchId)) { node.nextMatchId = null; node.nextSlot = null; }
                if (!retained.contains(node.loserNextMatchId)) { node.loserNextMatchId = null; node.loserNextSlot = null; }
            }
        }

        // Insert into database in 2 passes to guarantee zero foreign key constraint conflicts
        String insertSql = "INSERT INTO matches (id, tournament_id, stage_id, round_number, match_order, match_code, bracket_type, "
                + "team1_id, team2_id, is_bye, status) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        String updateLinksSql = "UPDATE matches SET next_match_id = ?, next_slot = ?, loser_next_match_id = ?, loser_next_slot = ?, winner_id = ? WHERE id = ?";

        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            TournamentDAO.assertStage2Ready(conn,tournamentId,stageOrder);
            // Concurrent page loads may propose different draws; only the first committed draw wins.
            try (PreparedStatement lock = conn.prepareStatement("SELECT id FROM tournaments WITH (UPDLOCK,HOLDLOCK) WHERE id=?")) {
                lock.setString(1,tournamentId); try(ResultSet rs=lock.executeQuery()){ if(!rs.next()) return; }
            }
            try (PreparedStatement check = conn.prepareStatement("SELECT COUNT(*) FROM matches WHERE tournament_id=? AND (stage_id=? OR (stage_id IS NULL AND ?=1))")) {
                check.setString(1,tournamentId);check.setString(2,stageId);check.setInt(3,stageOrder);
                try(ResultSet rs=check.executeQuery()){rs.next();if(rs.getInt(1)>0)return;}
            }
            
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

            syncStageByes(conn, tournamentId, stageId);
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
        boolean hasScore;
        String winnerId;
        String status;
    }

    private int[] generateSeedingArray(int bracketSize) {
        boolean isPow2 = (bracketSize > 0 && (bracketSize & (bracketSize - 1)) == 0);
        if (!isPow2 && bracketSize % 2 == 0) {
            int[] result = new int[bracketSize];
            for (int i = 0; i < bracketSize / 2; i++) {
                result[i * 2] = i + 1;
                result[i * 2 + 1] = bracketSize - i;
            }
            return result;
        }

        int rounds = (int) (Math.log(bracketSize) / Math.log(2)) - 1;
        List<Integer> pls = new ArrayList<>();
        pls.add(1);
        pls.add(2);

        for (int i = 0; i < rounds; i++) {
            List<Integer> nextPls = new ArrayList<>();
            int sum = (int) Math.pow(2, i + 2) + 1;
            for (int val : pls) {
                nextPls.add(val);
                nextPls.add(sum - val);
            }
            pls = nextPls;
        }

        int[] result = new int[bracketSize];
        for (int i = 0; i < bracketSize / 2; i++) {
            int a = pls.get(i * 2);
            int b = pls.get(i * 2 + 1);
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
        return new TournamentDAO().resetStageMatches(tournamentId, stageOrder);
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
