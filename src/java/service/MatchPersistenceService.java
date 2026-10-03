package service;

import dao.DBContext;
import dao.ParticipantDAO;
import dao.TournamentDAO;
import model.Team;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

// NOTE: DB CHECK constraint on status: ('PENDING', 'READY', 'IN_PROGRESS', 'FINISHED')
// 'SCHEDULED' and 'COMPLETED' are NOT valid DB values.

/**
 * Unified Backend Service for 100% Database-Driven Match Result & Lifecycle Persistence.
 * Supports UPSERT (Insert if missing, Update if existing) to guarantee zero data loss.
 */
public class MatchPersistenceService {

    private static final MatchPersistenceService INSTANCE = new MatchPersistenceService();

    public static MatchPersistenceService getInstance() {
        return INSTANCE;
    }

    private MatchPersistenceService() {}

    /**
     * Update a single match score, determine winner/loser, and advance teams in DB.
     */
    public boolean updateSingleMatch(String tournamentId, String matchId, Integer score1, Integer score2, String winnerTeamId) {
        if (matchId == null || matchId.trim().isEmpty()) return false;
        String mIdClean = matchId.trim();

        // '%[_]' escapes underscore wildcard in SQL Server LIKE
        String selectSql = "SELECT id, tournament_id, next_match_id, next_slot, loser_next_match_id, loser_next_slot, team1_id, team2_id "
                + "FROM matches WHERE (id = ? OR id LIKE '%[_]' + ? OR match_code = ? OR match_code = 'Match #' + ?)"
                + (tournamentId != null ? " AND tournament_id = ?" : "");

        DBContext db = new DBContext();
        try (Connection conn = db.getConnection()) {
            conn.setAutoCommit(false);

            String actualId = null;
            String resolvedTourneyId = tournamentId;
            String nextMatchId = null;
            String nextSlot = null;
            String loserNextMatchId = null;
            String loserNextSlot = null;
            String team1Id = null;
            String team2Id = null;

            try (PreparedStatement psSel = conn.prepareStatement(selectSql)) {
                psSel.setString(1, mIdClean);
                psSel.setString(2, mIdClean);
                psSel.setString(3, mIdClean);
                psSel.setString(4, mIdClean);
                if (tournamentId != null) psSel.setString(5, tournamentId.trim());
                try (ResultSet rs = psSel.executeQuery()) {
                    if (rs.next()) {
                        actualId = rs.getString("id");
                        if (resolvedTourneyId == null) resolvedTourneyId = rs.getString("tournament_id");
                        nextMatchId = rs.getString("next_match_id");
                        nextSlot = rs.getString("next_slot");
                        loserNextMatchId = rs.getString("loser_next_match_id");
                        loserNextSlot = rs.getString("loser_next_slot");
                        team1Id = rs.getString("team1_id");
                        team2Id = rs.getString("team2_id");
                    }
                }
            }

            // DB CHECK: only PENDING/READY/IN_PROGRESS/FINISHED allowed
            String status = (score1 != null && score2 != null) ? "FINISHED" : "PENDING";
            String loserId = null;
            if (winnerTeamId != null) {
                if (winnerTeamId.equals(team1Id)) {
                    loserId = team2Id;
                } else if (winnerTeamId.equals(team2Id)) {
                    loserId = team1Id;
                }
            }

            int rowsUpdated = 0;
            if (actualId != null) {
                String updateSql = "UPDATE matches SET score1 = ?, score2 = ?, winner_id = ?, status = ? WHERE id = ?";
                try (PreparedStatement psUp = conn.prepareStatement(updateSql)) {
                    if (score1 != null) psUp.setInt(1, score1); else psUp.setNull(1, java.sql.Types.INTEGER);
                    if (score2 != null) psUp.setInt(2, score2); else psUp.setNull(2, java.sql.Types.INTEGER);
                    if (winnerTeamId != null) psUp.setString(3, winnerTeamId); else psUp.setNull(3, java.sql.Types.VARCHAR);
                    psUp.setString(4, status);
                    psUp.setString(5, actualId);
                    rowsUpdated = psUp.executeUpdate();
                }
            } else {
                // Match not in DB yet — insert with proper stage_id from tournament_stages
                String stageId = lookupOrCreateStageId(conn, resolvedTourneyId, 1);
                if (stageId != null) {
                    String newId = "M_" + (resolvedTourneyId != null ? resolvedTourneyId : "T") + "_" + mIdClean;
                    String insertSql = "INSERT INTO matches (id, tournament_id, stage_id, round_number, match_code, bracket_type, team1_id, team2_id, score1, score2, winner_id, status) "
                            + "VALUES (?, ?, ?, 1, ?, 'MAIN', ?, ?, ?, ?, ?, ?)";
                    try (PreparedStatement psIns = conn.prepareStatement(insertSql)) {
                        psIns.setString(1, newId);
                        psIns.setString(2, resolvedTourneyId != null ? resolvedTourneyId : "T_DEFAULT");
                        psIns.setString(3, stageId);
                        psIns.setString(4, "Match #" + mIdClean);
                        if (team1Id != null) psIns.setString(5, team1Id); else psIns.setNull(5, java.sql.Types.VARCHAR);
                        if (team2Id != null) psIns.setString(6, team2Id); else psIns.setNull(6, java.sql.Types.VARCHAR);
                        if (score1 != null) psIns.setInt(7, score1); else psIns.setNull(7, java.sql.Types.INTEGER);
                        if (score2 != null) psIns.setInt(8, score2); else psIns.setNull(8, java.sql.Types.INTEGER);
                        if (winnerTeamId != null) psIns.setString(9, winnerTeamId); else psIns.setNull(9, java.sql.Types.VARCHAR);
                        psIns.setString(10, status);
                        rowsUpdated = psIns.executeUpdate();
                    }
                }
            }

            // Advance winner to next match if defined
            if (winnerTeamId != null && nextMatchId != null && !nextMatchId.trim().isEmpty() && resolvedTourneyId != null) {
                String slotCol = "SLOT_2".equalsIgnoreCase(nextSlot) || "2".equals(nextSlot) ? "team2_id" : "team1_id";
                String advSql = "UPDATE matches SET " + slotCol + " = ? WHERE tournament_id = ? AND (id = ? OR id LIKE '%[_]' + ? OR match_code = ? OR match_code = 'Match #' + ?)";
                try (PreparedStatement psAdv = conn.prepareStatement(advSql)) {
                    psAdv.setString(1, winnerTeamId);
                    psAdv.setString(2, resolvedTourneyId);
                    psAdv.setString(3, nextMatchId);
                    psAdv.setString(4, nextMatchId);
                    psAdv.setString(5, nextMatchId);
                    psAdv.setString(6, nextMatchId);
                    psAdv.executeUpdate();
                }
            }

            // Drop loser to lower bracket match if defined (Double Elimination)
            if (loserId != null && loserNextMatchId != null && !loserNextMatchId.trim().isEmpty() && resolvedTourneyId != null) {
                String dropCol = "SLOT_2".equalsIgnoreCase(loserNextSlot) || "2".equals(loserNextSlot) ? "team2_id" : "team1_id";
                String dropSql = "UPDATE matches SET " + dropCol + " = ? WHERE tournament_id = ? AND (id = ? OR id LIKE '%[_]' + ? OR match_code = ? OR match_code = 'Match #' + ?)";
                try (PreparedStatement psDrop = conn.prepareStatement(dropSql)) {
                    psDrop.setString(1, loserId);
                    psDrop.setString(2, resolvedTourneyId);
                    psDrop.setString(3, loserNextMatchId);
                    psDrop.setString(4, loserNextMatchId);
                    psDrop.setString(5, loserNextMatchId);
                    psDrop.setString(6, loserNextMatchId);
                    psDrop.executeUpdate();
                }
            }

            conn.commit();

            if (resolvedTourneyId != null) {
                triggerSeriesRecalculationAsync(resolvedTourneyId);
            }

            return rowsUpdated > 0;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Persist a full list of matches from frontend JSON directly into database using UPSERT.
     */
    public int syncMatchesList(String tournamentId, List<Map<String, Object>> matchesList) {
        if (tournamentId == null || matchesList == null || matchesList.isEmpty()) return 0;

        ParticipantDAO pDao = new ParticipantDAO();
        List<Team> teams = pDao.getTeamsByTournamentId(tournamentId);
        Map<String, String> teamLookup = new HashMap<>();
        if (teams != null) {
            for (Team tm : teams) {
                if (tm.getId() != null) teamLookup.put(tm.getId().toLowerCase().trim(), tm.getId());
                if (tm.getRawName() != null) teamLookup.put(tm.getRawName().toLowerCase().trim(), tm.getId());
                if (tm.getNormalizedName() != null) teamLookup.put(tm.getNormalizedName().toLowerCase().trim(), tm.getId());
            }
        }

        String updateSql = "UPDATE matches SET " +
                "team1_id = COALESCE(?, team1_id), " +
                "team2_id = COALESCE(?, team2_id), " +
                "score1 = ?, score2 = ?, winner_id = ?, status = ? " +
                "WHERE tournament_id = ? AND (id = ? OR match_code = ? OR id LIKE '%[_]' + ?)";

        // Fixed: loser_next_match_id / loser_next_slot (not drop_to_*), stage_id via lookup not hardcoded
        String insertSql = "INSERT INTO matches (id, tournament_id, stage_id, round_number, match_code, bracket_type, team1_id, team2_id, score1, score2, winner_id, next_match_id, next_slot, loser_next_match_id, loser_next_slot, is_bye, status) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        int count = 0;
        DBContext db = new DBContext();
        try (Connection conn = db.getConnection()) {
            conn.setAutoCommit(false);
            // Resolve (or auto-create) real stage_id once for the batch
            String resolvedStageId = lookupOrCreateStageId(conn, tournamentId, 1);
            for (Map<String, Object> m : matchesList) {
                String matchId = getString(m, "id", getString(m, "matchId", null));
                if (matchId == null) continue;

                String t1Name = getString(m, "team1Name", extractTeamField(m, "team1", "name"));
                String t2Name = getString(m, "team2Name", extractTeamField(m, "team2", "name"));

                String t1Id = (t1Name != null) ? teamLookup.get(t1Name.toLowerCase().trim()) : null;
                String t2Id = (t2Name != null) ? teamLookup.get(t2Name.toLowerCase().trim()) : null;

                Integer s1 = getInt(m, "team1Score", extractTeamScore(m, "team1"));
                Integer s2 = getInt(m, "team2Score", extractTeamScore(m, "team2"));

                String winnerSlot = getString(m, "winnerId", null);
                String winnerId = null;
                if ("team1".equalsIgnoreCase(winnerSlot) && t1Id != null) {
                    winnerId = t1Id;
                } else if ("team2".equalsIgnoreCase(winnerSlot) && t2Id != null) {
                    winnerId = t2Id;
                } else if (s1 != null && s2 != null) {
                    if (s1 > s2 && t1Id != null) winnerId = t1Id;
                    else if (s2 > s1 && t2Id != null) winnerId = t2Id;
                }

                // DB CHECK: only PENDING/READY/IN_PROGRESS/FINISHED allowed
                String status = (s1 != null && s2 != null) ? "FINISHED" : "PENDING";

                // Try update first
                int updated = 0;
                try (PreparedStatement psUp = conn.prepareStatement(updateSql)) {
                    if (t1Id != null) psUp.setString(1, t1Id); else psUp.setNull(1, java.sql.Types.VARCHAR);
                    if (t2Id != null) psUp.setString(2, t2Id); else psUp.setNull(2, java.sql.Types.VARCHAR);
                    if (s1 != null) psUp.setInt(3, s1); else psUp.setNull(3, java.sql.Types.INTEGER);
                    if (s2 != null) psUp.setInt(4, s2); else psUp.setNull(4, java.sql.Types.INTEGER);
                    if (winnerId != null) psUp.setString(5, winnerId); else psUp.setNull(5, java.sql.Types.VARCHAR);
                    psUp.setString(6, status);
                    psUp.setString(7, tournamentId);
                    psUp.setString(8, matchId);
                    psUp.setString(9, "Match #" + matchId);
                    psUp.setString(10, matchId);
                    updated = psUp.executeUpdate();
                }

                // If not updated, insert!
                if (updated == 0 && resolvedStageId != null) {
                    int rNum = getInt(m, "roundNumber", 1);
                    String bType = getString(m, "bracketType", "MAIN");
                    // Validate bracket_type CHECK constraint values
                    if (!isValidBracketType(bType)) bType = "MAIN";
                    String nextId = getString(m, "nextMatchId", null);
                    int nextSlot = getInt(m, "nextMatchSlot", 1);
                    String dropId = getString(m, "dropToMatchId", getString(m, "loserNextMatchId", null));
                    int dropSlot = getInt(m, "dropToMatchSlot", 1);
                    boolean isBye = Boolean.parseBoolean(getString(m, "isBye", "false"));

                    String mDbId = "M_" + tournamentId + "_" + matchId;
                    try (PreparedStatement psIns = conn.prepareStatement(insertSql)) {
                        psIns.setString(1, mDbId);
                        psIns.setString(2, tournamentId);
                        psIns.setString(3, resolvedStageId);
                        psIns.setInt(4, rNum);
                        psIns.setString(5, "Match #" + matchId);
                        psIns.setString(6, bType);
                        if (t1Id != null) psIns.setString(7, t1Id); else psIns.setNull(7, java.sql.Types.VARCHAR);
                        if (t2Id != null) psIns.setString(8, t2Id); else psIns.setNull(8, java.sql.Types.VARCHAR);
                        if (s1 != null) psIns.setInt(9, s1); else psIns.setNull(9, java.sql.Types.INTEGER);
                        if (s2 != null) psIns.setInt(10, s2); else psIns.setNull(10, java.sql.Types.INTEGER);
                        if (winnerId != null) psIns.setString(11, winnerId); else psIns.setNull(11, java.sql.Types.VARCHAR);
                        if (nextId != null && !nextId.isEmpty() && !"null".equalsIgnoreCase(nextId) && !"0".equals(nextId)) psIns.setString(12, nextId); else psIns.setNull(12, java.sql.Types.VARCHAR);
                        psIns.setString(13, nextSlot == 2 ? "SLOT_2" : "SLOT_1");
                        if (dropId != null && !dropId.isEmpty() && !"null".equalsIgnoreCase(dropId) && !"0".equals(dropId)) psIns.setString(14, dropId); else psIns.setNull(14, java.sql.Types.VARCHAR);
                        psIns.setString(15, dropSlot == 2 ? "SLOT_2" : "SLOT_1");
                        psIns.setBoolean(16, isBye);
                        psIns.setString(17, status);
                        psIns.executeUpdate();
                    } catch (Exception insertEx) {
                        System.err.println("[MatchPersistenceService] INSERT failed for match " + matchId + ": " + insertEx.getMessage());
                    }
                }
                count++;
            }
            conn.commit();

            triggerSeriesRecalculationAsync(tournamentId);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return count;
    }

    /**
     * Conclude tournament: record champion name & update status to 'COMPLETED' in SQL Server.
     */
    public boolean finishTournament(String tournamentId, String championName) {
        if (tournamentId == null || tournamentId.trim().isEmpty()) return false;
        TournamentDAO dao = new TournamentDAO();
        boolean ok = dao.updateTournamentStatus(tournamentId.trim(), "COMPLETED");
        if (championName != null && !championName.trim().isEmpty()) {
            dao.updateTournamentChampion(tournamentId.trim(), championName.trim());
        }
        triggerSeriesRecalculationAsync(tournamentId);
        return ok;
    }

    private void triggerSeriesRecalculationAsync(String tournamentId) {
        CompletableFuture.runAsync(() -> {
            try {
                TournamentDAO tDao = new TournamentDAO();
                model.Tournament t = tDao.getTournamentById(tournamentId);
                if (t != null && t.getSeriesId() != null && !t.getSeriesId().trim().isEmpty()) {
                    RollingWindowPointService.getInstance().recalculateAndPersistStandings(t.getSeriesId().trim());
                }
            } catch (Exception ignore) {}
        });
    }

    /**
     * Lookup the real stage_id from tournament_stages for (tournamentId, stageOrder).
     * If none exists, auto-creates one to satisfy the FK constraint on matches.stage_id.
     */
    private String lookupOrCreateStageId(Connection conn, String tournamentId, int stageOrder) {
        if (tournamentId == null || tournamentId.trim().isEmpty()) return null;
        try {
            // 1. Try to look up existing stage_id
            String selectSql = "SELECT TOP 1 id FROM tournament_stages WHERE tournament_id = ? AND stage_order = ? ORDER BY created_at ASC";
            try (PreparedStatement ps = conn.prepareStatement(selectSql)) {
                ps.setString(1, tournamentId);
                ps.setInt(2, stageOrder);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getString("id");
                }
            }
            // 2. Auto-create one if missing (lookup fails = stage not created via UI yet)
            String newStageId = "STG_" + tournamentId + "_" + stageOrder;
            String insertSql = "INSERT INTO tournament_stages (id, tournament_id, stage_order, stage_name, format) VALUES (?, ?, ?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                ps.setString(1, newStageId);
                ps.setString(2, tournamentId);
                ps.setInt(3, stageOrder);
                ps.setString(4, "Stage " + stageOrder);
                ps.setString(5, "SINGLE_ELIMINATION");
                ps.executeUpdate();
            }
            return newStageId;
        } catch (Exception e) {
            System.err.println("[MatchPersistenceService] lookupOrCreateStageId error: " + e.getMessage());
            return null;
        }
    }

    private boolean isValidBracketType(String bt) {
        if (bt == null) return false;
        switch (bt.toUpperCase()) {
            case "WINNER_BRACKET": case "LOSER_BRACKET": case "GRAND_FINAL":
            case "GRAND_FINAL_RESET": case "THIRD_PLACE": case "SWISS":
            case "ROUND_ROBIN": case "GROUP_STAGE": case "MAIN":
                return true;
            default: return false;
        }
    }

    private String getString(Map<String, Object> map, String key, String defaultVal) {
        if (map == null || !map.containsKey(key)) return defaultVal;
        Object v = map.get(key);
        return (v != null) ? String.valueOf(v).trim() : defaultVal;
    }

    private Integer getInt(Map<String, Object> map, String key, Integer defaultVal) {
        if (map == null || !map.containsKey(key)) return defaultVal;
        Object v = map.get(key);
        if (v == null) return defaultVal;
        try {
            if (v instanceof Number) return ((Number) v).intValue();
            String s = String.valueOf(v).trim();
            if (s.isEmpty()) return defaultVal;
            return Integer.parseInt(s);
        } catch (Exception e) {
            return defaultVal;
        }
    }

    @SuppressWarnings("unchecked")
    private String extractTeamField(Map<String, Object> match, String teamKey, String field) {
        if (match == null || !match.containsKey(teamKey)) return null;
        Object teamObj = match.get(teamKey);
        if (teamObj instanceof Map) {
            Map<String, Object> tm = (Map<String, Object>) teamObj;
            Object fVal = tm.get(field);
            return (fVal != null) ? String.valueOf(fVal).trim() : null;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private Integer extractTeamScore(Map<String, Object> match, String teamKey) {
        if (match == null || !match.containsKey(teamKey)) return null;
        Object teamObj = match.get(teamKey);
        if (teamObj instanceof Map) {
            Map<String, Object> tm = (Map<String, Object>) teamObj;
            Object sc = tm.get("score");
            if (sc == null) return null;
            try {
                if (sc instanceof Number) return ((Number) sc).intValue();
                String s = String.valueOf(sc).trim();
                return s.isEmpty() ? null : Integer.parseInt(s);
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }
}
