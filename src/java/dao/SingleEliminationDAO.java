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
 * High-Performance, Database-Driven DAO for Single Elimination Tournament Format.
 * 100% Database-Driven: initializes bracket trees and reads matches directly from SQL Server.
 */
public class SingleEliminationDAO extends DBContext {

    public List<Match> getMatchesByTournamentId(int tournamentId) {
        return getMatchesByTournamentId(String.valueOf(tournamentId));
    }

    public Map<Integer, List<Match>> getBracketRounds(int tournamentId) {
        return getBracketRounds(String.valueOf(tournamentId));
    }

    public List<Match> getMatchesByTournamentId(String tournamentId) {
        return getMatchesByTournamentId(tournamentId, 1);
    }

    public List<Match> getMatchesByTournamentId(String tournamentId, int stageOrder) {
        List<Match> list = new ArrayList<>();
        if (tournamentId == null || tournamentId.trim().isEmpty()) return list;

        int cutTarget = getTournamentCutTarget(tournamentId, stageOrder);
        int stoppingRound = 0;
        if (cutTarget > 1) {
            ParticipantDAO pDao = new ParticipantDAO();
            List<Team> teams = pDao.getTeamsByTournamentId(tournamentId);
            int n = (teams != null) ? teams.size() : 16;
            if (cutTarget < n) {
                stoppingRound = service.CountAdvanceTeamService.calculateStoppingRoundSE(n, cutTarget);
            }
        }

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
                + "AND (? = 0 OR m.round_number <= ?) "
                + "ORDER BY m.round_number ASC, LEN(m.id) ASC, m.id ASC";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tournamentId);
            ps.setInt(2, stageOrder);
            ps.setInt(3, stageOrder);
            ps.setInt(4, stoppingRound);
            ps.setInt(5, stoppingRound);
            try (ResultSet rs = ps.executeQuery()) {
                int seq = 1;
                while (rs.next()) {
                    Match m = new Match();
                    String rawId = rs.getString("id");
                    int numId = parseNumericId(rawId, seq++);
                    m.setId(numId);
                    m.setRoundNumber(rs.getInt("round_number"));
                    m.setMatchNumber(numId);

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

    public Map<Integer, List<Match>> getBracketRounds(String tournamentId) {
        Map<Integer, List<Match>> roundMap = new HashMap<>();
        List<Match> matchList = getMatchesByTournamentId(tournamentId);
        if (matchList != null) {
            for (Match m : matchList) {
                roundMap.computeIfAbsent(m.getRoundNumber(), k -> new ArrayList<>()).add(m);
            }
        }
        return roundMap;
    }

    public int getTournamentCutTarget(String tournamentId, int stageOrder) {
        if (tournamentId == null || tournamentId.trim().isEmpty() || stageOrder != 1) return 0;
        String sql = "SELECT tournament_type, advancing_seats_count FROM tournaments WHERE id = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tournamentId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String type = rs.getString("tournament_type");
                    int advancing = rs.getInt("advancing_seats_count");
                    if ("MULTI_STAGE".equalsIgnoreCase(type) && advancing > 1) {
                        return advancing;
                    }
                }
            }
        } catch (Exception ignore) {}
        return 0;
    }

    public void cleanupOrphanedCutMatches(String tournamentId, int stageOrder, int stoppingRound) {
        if (stageOrder == 1 && stoppingRound > 0) new TournamentDAO().trimCutMatches(tournamentId, stoppingRound, -1);
    }

    public String getMatchesJsonForFrontend(String tournamentId, int stageOrder) {
        if(stageOrder>1) {
            try(Connection access=getConnection()){TournamentDAO.assertStage2Ready(access,tournamentId,stageOrder);}
            catch(Exception notReady){return "[]";}
        }
        if (tournamentId == null || tournamentId.trim().isEmpty()) return "[]";

        int cutTarget = getTournamentCutTarget(tournamentId, stageOrder);
        int stoppingRound = 0;
        if (cutTarget > 1) {
            ParticipantDAO pDao = new ParticipantDAO();
            List<Team> teams = pDao.getTeamsByTournamentId(tournamentId);
            int n = (teams != null) ? teams.size() : 16;
            if (cutTarget < n) {
                stoppingRound = service.CountAdvanceTeamService.calculateStoppingRoundSE(n, cutTarget);
                cleanupOrphanedCutMatches(tournamentId, stageOrder, stoppingRound);
            }
        }

        // Auto-initialize bracket tree in DB if none exists
        ensureBracketInitialized(tournamentId, stageOrder);

        // Always ensure BYE winners are synchronized to downstream matches in DB
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
                + "AND (? = 0 OR m.round_number <= ?) "
                + "ORDER BY m.round_number ASC, LEN(m.id) ASC, m.id ASC";

        StringBuilder sb = new StringBuilder("[");
        int count = 0;

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tournamentId);
            ps.setInt(2, stageOrder);
            ps.setInt(3, stageOrder);
            ps.setInt(4, stoppingRound);
            ps.setInt(5, stoppingRound);
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

                    if (roundNumber == 1) {
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

                    String status = rs.getString("status");
                    if ("FINISHED".equalsIgnoreCase(status) || "DONE".equalsIgnoreCase(status) || "COMPLETED".equalsIgnoreCase(status) || !winnerSlot.isEmpty() || (!s1.isEmpty() && !s2.isEmpty())) {
                        status = "COMPLETED";
                    } else if (status == null || status.trim().isEmpty()) {
                        status = "SCHEDULED";
                    }

                    String bracketType = rs.getString("bracket_type");
                    sb.append("{")
                            .append("\"matchId\":\"").append(escapeJson(rawId)).append("\",")
                            .append("\"id\":\"").append(escapeJson(rawId)).append("\",")
                            .append("\"rawId\":\"").append(escapeJson(rawId)).append("\",")
                            .append("\"bracketType\":\"").append(escapeJson(bracketType != null ? bracketType : "MAIN")).append("\",")
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

    /**
     * Re-synchronizes any BYE winners to their downstream matches in DB to guarantee integrity.
     */
    public void syncByeAdvancementsInDB(String tournamentId) {
        syncByeAdvancementsInDB(tournamentId, 1);
    }

    public void syncByeAdvancementsInDB(String tournamentId, int stageOrder) {
        if (tournamentId == null || tournamentId.trim().isEmpty()) return;
        String sql = "SELECT m.winner_id, m.next_match_id, m.next_slot "
                   + "FROM matches m LEFT JOIN tournament_stages s ON s.id=m.stage_id AND s.tournament_id=m.tournament_id WHERE m.tournament_id = ? AND (s.stage_order=? OR (m.stage_id IS NULL AND ?=1)) AND m.is_bye = 1 AND m.winner_id IS NOT NULL AND m.next_match_id IS NOT NULL";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tournamentId);
            ps.setInt(2,stageOrder); ps.setInt(3,stageOrder);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String wId = rs.getString("winner_id");
                    String nextId = rs.getString("next_match_id");
                    String nextSlot = rs.getString("next_slot");
                    if (nextId != null && wId != null) {
                        String slotCol = ("SLOT_2".equalsIgnoreCase(nextSlot) || "2".equals(nextSlot)) ? "team2_id" : "team1_id";
                        String otherSlotCol = ("SLOT_2".equalsIgnoreCase(nextSlot) || "2".equals(nextSlot)) ? "team1_id" : "team2_id";
                        String upSql = "UPDATE matches SET " + slotCol + " = ?, status = CASE WHEN (" + otherSlotCol + " IS NOT NULL) THEN 'READY' ELSE status END "
                                     + "WHERE tournament_id = ? AND id = ? AND (stage_id IN (SELECT id FROM tournament_stages WHERE tournament_id=? AND stage_order=?) OR (stage_id IS NULL AND ?=1)) AND score1 IS NULL AND score2 IS NULL AND winner_id IS NULL";
                        try (PreparedStatement psUp = conn.prepareStatement(upSql)) {
                            psUp.setString(1, wId);
                            psUp.setString(2, tournamentId);
                            psUp.setString(3, nextId);
                            psUp.setString(4, tournamentId);
                            psUp.setInt(5, stageOrder); psUp.setInt(6, stageOrder);
                            psUp.executeUpdate();
                        }
                    }
                }
            }
        } catch (Exception ignore) {}
    }

    public List<Team> getStage2TeamsList(String tournamentId) {
        try(Connection conn=getConnection()){TournamentDAO.assertStage2Ready(conn,tournamentId,2);}
        catch(Exception notReady){return new ArrayList<>();}
        String json = new TournamentDAO().getStage2Teams(tournamentId);
        if (json == null || json.trim().isEmpty() || json.trim().equals("[]")) {
            return null;
        }
        List<Team> list = new ArrayList<>();
        try {
            ParticipantDAO pDao = new ParticipantDAO();
            List<Team> dbTeams = pDao.getTeamsByTournamentId(tournamentId);
            Map<String, Team> nameToTeam = new HashMap<>();
            if (dbTeams != null) {
                for (Team t : dbTeams) {
                    if (t.getRawName() != null) nameToTeam.put(t.getRawName().trim().toLowerCase(), t);
                    if (t.getNormalizedName() != null) nameToTeam.put(t.getNormalizedName().trim().toLowerCase(), t);
                }
            }

            java.util.regex.Pattern pObj = java.util.regex.Pattern.compile("\\{([^}]+)\\}");
            java.util.regex.Matcher mObj = pObj.matcher(json);
            int idx = 1;
            while (mObj.find()) {
                String objContent = mObj.group(1);
                String tId = extractJsonField(objContent, "id");
                String tName = extractJsonField(objContent, "name");
                String tSeedStr = extractJsonField(objContent, "originalSeed");
                if (tSeedStr == null || tSeedStr.isEmpty()) tSeedStr = extractJsonField(objContent, "seed");

                if (tName != null && !tName.trim().isEmpty()) {
                    Team team = new Team();
                    Team matched = nameToTeam.get(tName.trim().toLowerCase());
                    team.setId(matched != null ? matched.getId() : (tId != null && !tId.isEmpty() ? tId : "T_" + idx));
                    team.setRawName(matched != null ? matched.getRawName() : tName.trim());
                    team.setNormalizedName(matched != null ? matched.getNormalizedName() : tName.trim());
                    int s = idx;
                    if (tSeedStr != null) {
                        try { s = Integer.parseInt(tSeedStr.trim()); } catch (Exception ignore) {}
                    } else if (matched != null && matched.getOriginalSeed() > 0) {
                        s = matched.getOriginalSeed();
                    }
                    team.setOriginalSeed(s);
                    list.add(team);
                    idx++;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    private static String extractJsonField(String jsonBlock, String key) {
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("\"" + key + "\"\\s*:\\s*\"?([^,\"}]+)\"?");
        java.util.regex.Matcher m = p.matcher(jsonBlock);
        if (m.find()) {
            return m.group(1).trim();
        }
        return null;
    }

    /**
     * Checks if matches exist for this tournament & stage; if not, generates the Single Elimination tree in DB.
     */
    public synchronized void ensureBracketInitialized(String tournamentId, int stageOrder) {
        if (tournamentId == null || tournamentId.trim().isEmpty()) return;

        String checkSql = "SELECT COUNT(*) FROM matches m "
                + "LEFT JOIN tournament_stages s ON m.stage_id = s.id AND s.tournament_id = m.tournament_id "
                + "WHERE m.tournament_id = ? AND (s.stage_order = ? OR (m.stage_id IS NULL AND ? = 1))";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(checkSql)) {
            ps.setString(1, tournamentId);
            ps.setInt(2, stageOrder);
            ps.setInt(3, stageOrder);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next() && rs.getInt(1) > 0) {
                    return; // Already initialized!
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

        // Initialize bracket
        List<Team> teams;
        if (stageOrder > 1) {
            teams = getStage2TeamsList(tournamentId);
            if (teams == null || teams.size() < 2) {
                return; // Do NOT create Stage 2 matches until Stage 1 is finished and stage2_teams saved!
            }
        } else {
            ParticipantDAO pDao = new ParticipantDAO();
            teams = pDao.getTeamsByTournamentId(tournamentId);
            if (teams == null || teams.isEmpty()) return;
        }

        initializeBracketTreeInDB(tournamentId, stageOrder, teams);
    }

    private void initializeBracketTreeInDB(String tournamentId, int stageOrder, List<Team> teams) {
        int n = teams.size();
        if (n < 2) return;

        int cutTarget = getTournamentCutTarget(tournamentId, stageOrder);
        boolean isCutStage = (stageOrder == 1 && cutTarget > 1 && cutTarget < n);

        String stageId = lookupOrCreateStageId(tournamentId, stageOrder);

        Integer configuredSeeds = stageOrder == 1 ? new TournamentDAO().getTopSeedCount(tournamentId) : null;
        List<Team> initialSlots = stageOrder == 1
                ? service.SECutService.drawInitialSlots(teams, configuredSeeds == null ? 0 : configuredSeeds,
                    new TournamentDAO().isClassicDraw(tournamentId)) : null;

        // Build match tree structures
        List<DbMatchNode> allMatches = new ArrayList<>();
        int matchSeq = 1;
        String drawId=java.util.UUID.randomUUID().toString().substring(0,8);

        if (isCutStage) {
            // Cut branches use contiguous sections of the same persisted opening draw.
            List<List<Team>> branches = new ArrayList<>();
            int branchSize = initialSlots.size() / cutTarget;
            if (branchSize < 2 || initialSlots.size() % cutTarget != 0)
                throw new IllegalStateException("Số nhánh cut SE không hợp lệ.");
            for (int i = 0; i < cutTarget; i++)
                branches.add(new ArrayList<>(initialSlots.subList(i * branchSize, (i + 1) * branchSize)));

            // Determine branch max size & power-of-2 depth
            int maxM = 0;
            for (List<Team> b : branches) if (b.size() > maxM) maxM = b.size();

            int pMax = 1;
            while (pMax < maxM) pMax *= 2;
            if (pMax < 2) pMax = 2;
            int totalRounds = (int) (Math.log(pMax) / Math.log(2));


            // Collect nodes by round across all branches so they stay ordered top-to-bottom
            List<List<DbMatchNode>> roundNodes = new ArrayList<>();
            for (int r = 0; r <= totalRounds; r++) roundNodes.add(new ArrayList<>());

            for (int bIdx = 0; bIdx < cutTarget; bIdx++) {
                List<Team> bTeams = branches.get(bIdx);
                int m = bTeams.size();
                int currentRoundMatches = pMax / 2;
                List<DbMatchNode> branchPrevNodes = new ArrayList<>();

                for (int r = 1; r <= totalRounds; r++) {
                    List<DbMatchNode> branchCurNodes = new ArrayList<>();

                    for (int i = 0; i < currentRoundMatches; i++) {
                        DbMatchNode node = new DbMatchNode();
                        node.id = "M_" + tournamentId + "_B" + (bIdx + 1) + "_R" + r + "_" + matchSeq++;
                        node.roundNumber = r;
                        node.tournamentId = tournamentId;
                        node.stageId = stageId;
                        node.bracketType = "MAIN";

                        if (r == 1) {
                            Team t1 = bTeams.get(i * 2);
                            Team t2 = bTeams.get(i * 2 + 1);

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

                        branchCurNodes.add(node);
                        roundNodes.get(r).add(node);
                    }

                    if (!branchPrevNodes.isEmpty()) {
                        for (int i = 0; i < branchPrevNodes.size(); i++) {
                            DbMatchNode prev = branchPrevNodes.get(i);
                            DbMatchNode next = branchCurNodes.get(i / 2);
                            prev.nextMatchId = next.id;
                            prev.nextSlot = (i % 2 == 0) ? "SLOT_1" : "SLOT_2";
                        }
                    }

                    branchPrevNodes = branchCurNodes;
                    currentRoundMatches /= 2;
                }
            }

            // Flatten into allMatches in round-by-round, top-to-bottom order
            for (int r = 1; r <= totalRounds; r++) {
                allMatches.addAll(roundNodes.get(r));
            }
        } else {
            // Standard Championship Bracket (Stage 2 OR Single Stage Full Tournament)
            int bracketSize = 1;
            while (bracketSize < n) bracketSize *= 2;
            int totalRounds = (int) (Math.log(bracketSize) / Math.log(2));

            // Universal Standard Seeding Array for all single-elimination brackets
            int[] seeds = generateSeedingArray(bracketSize);

            // Stage 2 keeps its saved qualification/seed order; stage 1 uses the shared draw slots.
            List<Team> orderedTeams = new ArrayList<>(teams);

            int currentRoundMatches = bracketSize / 2;
            List<DbMatchNode> prevRoundNodes = new ArrayList<>();

            for (int r = 1; r <= totalRounds; r++) {
                List<DbMatchNode> currentRoundNodes = new ArrayList<>();
                for (int m = 0; m < currentRoundMatches; m++) {
                    DbMatchNode node = new DbMatchNode();
                    node.id = "M_" + tournamentId + "_" + (stageOrder > 1 ? "S" + stageOrder + "_" + drawId + "_" : "") + matchSeq++;
                    node.roundNumber = r;
                    node.tournamentId = tournamentId;
                    node.stageId = stageId;
                    node.bracketType = "MAIN";

                    if (r == 1) {
                        int s1 = seeds[m * 2];
                        int s2 = seeds[m * 2 + 1];
                        Team t1 = stageOrder == 1 ? initialSlots.get(m * 2) : (s1 <= n ? orderedTeams.get(s1 - 1) : null);
                        Team t2 = stageOrder == 1 ? initialSlots.get(m * 2 + 1) : (s2 <= n ? orderedTeams.get(s2 - 1) : null);

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

                    currentRoundNodes.add(node);
                    allMatches.add(node);
                }

                if (!prevRoundNodes.isEmpty()) {
                    for (int i = 0; i < prevRoundNodes.size(); i++) {
                        DbMatchNode prevNode = prevRoundNodes.get(i);
                        DbMatchNode nextNode = currentRoundNodes.get(i / 2);
                        prevNode.nextMatchId = nextNode.id;
                        prevNode.nextSlot = (i % 2 == 0) ? "SLOT_1" : "SLOT_2";
                    }
                }

                prevRoundNodes = currentRoundNodes;
                currentRoundMatches /= 2;
            }
        }

        // Contiguous numbering: ONLY playable non-BYE matches get match numbers 1, 2, 3...
        int playableCounter = 1;
        for (DbMatchNode node : allMatches) {
            if (node.isBye) {
                node.matchNumber = null;
                node.matchCode = "BYE";
            } else {
                node.matchNumber = playableCounter++;
                node.matchCode = "Match #" + node.matchNumber;
            }
        }

        // Insert into database in 2 passes to guarantee zero foreign key constraint conflicts
        String insertSql = "INSERT INTO matches (id, tournament_id, stage_id, round_number, match_order, match_code, bracket_type, "
                + "team1_id, team2_id, is_bye, status) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        String updateLinksSql = "UPDATE matches SET next_match_id = ?, next_slot = ?, winner_id = ? WHERE id = ?";

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
                for (DbMatchNode node : allMatches) {
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

            // Pass 2: Update next_match_id links now that all match IDs exist in DB
            try (PreparedStatement psUp = conn.prepareStatement(updateLinksSql)) {
                for (DbMatchNode node : allMatches) {
                    if (node.nextMatchId != null) {
                        psUp.setString(1, node.nextMatchId);
                        psUp.setString(2, node.nextSlot);
                        if (node.winnerId != null) psUp.setString(3, node.winnerId); else psUp.setNull(3, Types.VARCHAR);
                        psUp.setString(4, node.id);
                        psUp.addBatch();
                    }
                }
                psUp.executeBatch();
            }

            // Pass 3: Propagate BYE winners to next round matches
            for (DbMatchNode node : allMatches) {
                if (node.isBye && node.winnerId != null && node.nextMatchId != null) {
                    String slotCol = ("SLOT_2".equalsIgnoreCase(node.nextSlot)) ? "team2_id" : "team1_id";
                    String advSql = "UPDATE matches SET " + slotCol + " = ?, status = CASE WHEN (" + (slotCol.equals("team1_id") ? "team2_id" : "team1_id") + " IS NOT NULL) THEN 'READY' ELSE status END WHERE id = ?";
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

    private static class DbMatchNode {
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
        boolean isBye;
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
            ps.setString(5, "SINGLE_ELIMINATION");
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
