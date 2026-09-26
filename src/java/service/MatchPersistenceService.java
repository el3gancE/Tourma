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

/**
 * Unified Backend Service for 100% Database-Driven Match Result & Lifecycle Persistence.
 * Replaces all brittle regexes and localStorage fallback mechanisms.
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
        if (tournamentId == null || matchId == null) return false;

        String selectSql = "SELECT id, next_match_id, next_slot, drop_to_match_id, drop_to_slot, team1_id, team2_id FROM matches WHERE tournament_id = ? AND (id = ? OR match_code = ?)";
        String updateSql = "UPDATE matches SET score1 = ?, score2 = ?, winner_id = ?, status = ? WHERE tournament_id = ? AND (id = ? OR match_code = ?)";

        DBContext db = new DBContext();
        try (Connection conn = db.getConnection()) {
            conn.setAutoCommit(false);

            String actualId = matchId;
            String nextMatchId = null;
            String nextSlot = null;
            String dropToMatchId = null;
            String dropToSlot = null;
            String team1Id = null;
            String team2Id = null;

            try (PreparedStatement psSel = conn.prepareStatement(selectSql)) {
                psSel.setString(1, tournamentId);
                psSel.setString(2, matchId);
                psSel.setString(3, matchId);
                try (ResultSet rs = psSel.executeQuery()) {
                    if (rs.next()) {
                        actualId = rs.getString("id");
                        nextMatchId = rs.getString("next_match_id");
                        nextSlot = rs.getString("next_slot");
                        dropToMatchId = rs.getString("drop_to_match_id");
                        dropToSlot = rs.getString("drop_to_slot");
                        team1Id = rs.getString("team1_id");
                        team2Id = rs.getString("team2_id");
                    }
                }
            }

            String status = (score1 != null && score2 != null) ? "FINISHED" : "SCHEDULED";
            String loserId = null;
            if (winnerTeamId != null) {
                if (winnerTeamId.equals(team1Id)) {
                    loserId = team2Id;
                } else if (winnerTeamId.equals(team2Id)) {
                    loserId = team1Id;
                }
            }

            try (PreparedStatement psUp = conn.prepareStatement(updateSql)) {
                if (score1 != null) psUp.setInt(1, score1); else psUp.setNull(1, java.sql.Types.INTEGER);
                if (score2 != null) psUp.setInt(2, score2); else psUp.setNull(2, java.sql.Types.INTEGER);
                if (winnerTeamId != null) psUp.setString(3, winnerTeamId); else psUp.setNull(3, java.sql.Types.VARCHAR);
                psUp.setString(4, status);
                psUp.setString(5, tournamentId);
                psUp.setString(6, matchId);
                psUp.setString(7, matchId);
                psUp.executeUpdate();
            }

            // Advance winner to next match if defined
            if (winnerTeamId != null && nextMatchId != null && !nextMatchId.trim().isEmpty()) {
                String slotCol = "SLOT_2".equalsIgnoreCase(nextSlot) || "2".equals(nextSlot) ? "team2_id" : "team1_id";
                String advSql = "UPDATE matches SET " + slotCol + " = ? WHERE tournament_id = ? AND (id = ? OR match_code = ?)";
                try (PreparedStatement psAdv = conn.prepareStatement(advSql)) {
                    psAdv.setString(1, winnerTeamId);
                    psAdv.setString(2, tournamentId);
                    psAdv.setString(3, nextMatchId);
                    psAdv.setString(4, nextMatchId);
                    psAdv.executeUpdate();
                }
            }

            // Drop loser to lower bracket match if defined (Double Elimination)
            if (loserId != null && dropToMatchId != null && !dropToMatchId.trim().isEmpty()) {
                String dropCol = "SLOT_2".equalsIgnoreCase(dropToSlot) || "2".equals(dropToSlot) ? "team2_id" : "team1_id";
                String dropSql = "UPDATE matches SET " + dropCol + " = ? WHERE tournament_id = ? AND (id = ? OR match_code = ?)";
                try (PreparedStatement psDrop = conn.prepareStatement(dropSql)) {
                    psDrop.setString(1, loserId);
                    psDrop.setString(2, tournamentId);
                    psDrop.setString(3, dropToMatchId);
                    psDrop.setString(4, dropToMatchId);
                    psDrop.executeUpdate();
                }
            }

            conn.commit();

            // Trigger series points update asynchronously
            triggerSeriesRecalculationAsync(tournamentId);

            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Persist a full list of matches from frontend JSON directly into database.
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

        String sql = "UPDATE matches SET " +
                "team1_id = COALESCE(?, team1_id), " +
                "team2_id = COALESCE(?, team2_id), " +
                "score1 = ?, score2 = ?, winner_id = ?, status = ? " +
                "WHERE tournament_id = ? AND (id = ? OR match_code = ?)";

        int count = 0;
        DBContext db = new DBContext();
        try (Connection conn = db.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
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

                    String status = (s1 != null && s2 != null) ? "FINISHED" : "SCHEDULED";

                    if (t1Id != null) ps.setString(1, t1Id); else ps.setNull(1, java.sql.Types.VARCHAR);
                    if (t2Id != null) ps.setString(2, t2Id); else ps.setNull(2, java.sql.Types.VARCHAR);
                    if (s1 != null) ps.setInt(3, s1); else ps.setNull(3, java.sql.Types.INTEGER);
                    if (s2 != null) ps.setInt(4, s2); else ps.setNull(4, java.sql.Types.INTEGER);
                    if (winnerId != null) ps.setString(5, winnerId); else ps.setNull(5, java.sql.Types.VARCHAR);
                    ps.setString(6, status);
                    ps.setString(7, tournamentId);
                    ps.setString(8, matchId);
                    ps.setString(9, matchId);

                    ps.addBatch();
                    count++;
                }
                ps.executeBatch();
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
