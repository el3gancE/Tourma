package dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.StringJoiner;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import model.Tournament;

/**
 * Data Access Object for Tournaments table
 */
public class TournamentDAO {
    /** Reset the selected stage and its dependants without touching earlier stages. */
    public boolean resetStageMatches(String tournamentId, int stage) {
        if (tournamentId == null || (stage != 1 && stage != 2)) return false;
        try (Connection conn = new DBContext().getConnection()) {
            conn.setAutoCommit(false);
            lockCutTournament(conn, tournamentId);
            String scope = "tournament_id = ? AND (stage_id IN (SELECT id FROM tournament_stages WHERE tournament_id = ? AND stage_order >= ?) OR (stage_id IS NULL AND ? = 1))";
            try (PreparedStatement ps = conn.prepareStatement("UPDATE matches SET next_match_id = NULL, loser_next_match_id = NULL WHERE " + scope)) {
                bindResetScope(ps, tournamentId, stage); ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM matches WHERE " + scope)) {
                bindResetScope(ps, tournamentId, stage); ps.executeUpdate();
            }
            String sql = stage == 1
                ? "UPDATE tournaments SET status = 'DRAFT', champion_name = NULL, stage1_status = 'PENDING', stage2_teams = NULL WHERE id = ?"
                : "UPDATE tournaments SET status = 'DRAFT', champion_name = NULL WHERE id = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) { ps.setString(1, tournamentId); ps.executeUpdate(); }
            conn.commit();
            clearTournamentCache(tournamentId);
            service.RollingWindowPointService.clearAllCaches();
            return true; // Each format initializes its bracket on the next GET.
        } catch (Exception e) { e.printStackTrace(); return false; }
    }

    private static void bindResetScope(PreparedStatement ps, String id, int stage) throws java.sql.SQLException {
        ps.setString(1, id); ps.setString(2, id); ps.setInt(3, stage); ps.setInt(4, stage);
    }

    /** Trim only unused tail matches, and refuse to discard any recorded result. */
    public void trimCutMatches(String id, int upperStop, int lowerStop) {
        String stageScope = "m.tournament_id = ? AND (s.stage_order = 1 OR m.stage_id IS NULL)";
        String tail = lowerStop < 0 ? "m.round_number > ?"
            : "((m.bracket_type IN ('WINNER_BRACKET', 'UPPER', 'MAIN') AND m.round_number > ?) "
                + "OR (m.bracket_type IN ('LOSER_BRACKET', 'LOWER', 'LB') AND m.round_number > ?) "
                + "OR m.bracket_type IN ('GRAND_FINAL', 'GRAND_FINALS', 'GRAND_FINAL_RESET'))";
        String select = "SELECT m.id, m.score1, m.score2, m.winner_id, m.is_bye FROM matches m WITH (UPDLOCK, HOLDLOCK) "
            + "LEFT JOIN tournament_stages s ON s.id = m.stage_id WHERE " + stageScope + " AND (" + tail + ")";
        try (Connection conn = new DBContext().getConnection()) {
            conn.setAutoCommit(false);
            lockCutTournament(conn, id);
            List<String> remove = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(select)) {
                ps.setString(1, id); ps.setInt(2, upperStop); if (lowerStop >= 0) ps.setInt(3, lowerStop);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        if (rs.getObject(2) != null || rs.getObject(3) != null || (rs.getString(4) != null && !rs.getBoolean(5))) {
                            throw new IllegalStateException("Nhánh cũ đã có kết quả sau vòng dừng. Cần reset rõ ràng trước khi chuyển sang nhánh cut.");
                        }
                        remove.add(rs.getString(1));
                    }
                }
            }
            for (String match : remove) {
                try (PreparedStatement ps = conn.prepareStatement("UPDATE matches SET next_match_id = CASE WHEN next_match_id = ? THEN NULL ELSE next_match_id END, loser_next_match_id = CASE WHEN loser_next_match_id = ? THEN NULL ELSE loser_next_match_id END WHERE tournament_id = ? AND (stage_id IN (SELECT id FROM tournament_stages WHERE tournament_id = ? AND stage_order = 1) OR stage_id IS NULL)")) {
                    ps.setString(1, match); ps.setString(2, match); ps.setString(3, id); ps.setString(4, id); ps.executeUpdate();
                }
            }
            for (String match : remove) {
                try (PreparedStatement ps = conn.prepareStatement("DELETE FROM matches WHERE id = ? AND tournament_id = ?")) {
                    ps.setString(1, match); ps.setString(2, id); ps.executeUpdate();
                }
            }
            conn.commit();
        } catch (Exception e) { throw new IllegalStateException("Không thể chuyển nhánh cũ sang nhánh cut mà vẫn bảo toàn kết quả.", e); }
    }

    private static void lockCutTournament(Connection conn, String id) throws java.sql.SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT id FROM tournaments WITH (UPDLOCK, HOLDLOCK) WHERE id = ?")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) { if (!rs.next()) throw new IllegalStateException("Không tìm thấy giải đấu."); }
        }
    }

    /** Validate winners from DB and persist the draw and stage lock in one transaction. */
    public boolean saveCutStageTeams(String id, String payload, boolean lockStage) {
        try (Connection conn = new DBContext().getConnection()) {
            conn.setAutoCommit(false);
            lockCutTournament(conn, id);
            List<CutTeam> qualified = qualifiedCutTeams(conn, id);
            Map<String, CutTeam> byId = new LinkedHashMap<>();
            for (CutTeam team : qualified) {
                if (byId.put(team.id, team) != null) throw new IllegalStateException("Đội đi tiếp bị trùng.");
            }
            List<CutTeam> ordered = new ArrayList<>();
            Set<String> supplied = new LinkedHashSet<>();
            if (payload == null || !payload.trim().startsWith("[") || !payload.trim().endsWith("]")) return false;
            Matcher matcher = Pattern.compile("\"id\"\\s*:\\s*\"([A-Za-z0-9_-]+)\"").matcher(payload);
            while (matcher.find()) {
                String teamId = matcher.group(1);
                if (!supplied.add(teamId) || !byId.containsKey(teamId)) throw new IllegalArgumentException("Đội đi tiếp không hợp lệ.");
                ordered.add(byId.get(teamId));
            }
            if (!supplied.equals(byId.keySet())) throw new IllegalArgumentException("Danh sách đội không khớp kết quả thi đấu.");
            String canonical = cutTeamsJson(ordered);
            try (PreparedStatement ps = conn.prepareStatement("SELECT stage2_teams FROM tournaments WHERE id = ? AND (stage1_status IN ('LOCKED', 'COMPLETED') OR EXISTS (SELECT 1 FROM matches m JOIN tournament_stages s ON s.id = m.stage_id WHERE m.tournament_id = ? AND s.stage_order > 1))")) {
                ps.setString(1, id); ps.setString(2, id);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next() && !canonical.equals(rs.getString(1))) throw new IllegalStateException("Stage 2 đã có trận. Không thể thay danh sách hoặc bốc thăm lại.");
                }
            }
            try (PreparedStatement ps = conn.prepareStatement("UPDATE tournaments SET stage2_teams = ?" + (lockStage ? ", stage1_status = 'LOCKED'" : "") + " WHERE id = ?")) {
                ps.setString(1, canonical); ps.setString(2, id); ps.executeUpdate();
            }
            conn.commit();
            clearTournamentCache(id);
            return true;
        } catch (Exception e) { e.printStackTrace(); return false; }
    }

    public String getQualifiedCutTeamsJson(String id) {
        try (Connection conn = new DBContext().getConnection()) {
            return cutTeamsJson(qualifiedCutTeams(conn, id));
        } catch (Exception e) { throw new IllegalStateException("Chưa xác định đủ đội đi tiếp từ kết quả đã lưu.", e); }
    }

    private static class CutTeam {
        String id, name;
        int seed;
    }

    private static class CutMatch {
        String bracket, group, team1, team2, winner, status;
        int round;
    }

    private static String cutBracket(String value) {
        String b = value == null ? "" : value.toUpperCase(Locale.ROOT);
        if (b.contains("GRAND") || "GF".equals(b)) return "GF";
        if (b.contains("LOSER") || b.contains("LOWER") || "LB".equals(b)) return "LB";
        return "UB";
    }

    private List<CutTeam> qualifiedCutTeams(Connection conn, String id) throws java.sql.SQLException {
        String format;
        int target;
        try (PreparedStatement ps = conn.prepareStatement("SELECT t.advancing_seats_count, s.format FROM tournaments t JOIN tournament_stages s ON s.tournament_id = t.id AND s.stage_order = 1 WHERE t.id = ? AND t.tournament_type = 'MULTI_STAGE'")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalStateException("Giải không phải multistage.");
                target = rs.getInt(1); format = rs.getString(2).toUpperCase(Locale.ROOT);
            }
        }
        Map<String, CutTeam> teams = new LinkedHashMap<>();
        try (PreparedStatement ps = conn.prepareStatement("SELECT id, raw_name, original_seed FROM teams WITH (HOLDLOCK) WHERE tournament_id = ?")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) { CutTeam team = new CutTeam(); team.id = rs.getString(1); team.name = rs.getString(2); team.seed = rs.getInt(3); teams.put(team.id, team); }
            }
        }
        List<CutMatch> games = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement("SELECT m.* FROM matches m WITH (UPDLOCK, HOLDLOCK) LEFT JOIN tournament_stages s ON s.id = m.stage_id WHERE m.tournament_id = ? AND (s.stage_order = 1 OR m.stage_id IS NULL) ORDER BY m.round_number, m.match_order, m.id")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    CutMatch game = new CutMatch(); game.bracket = cutBracket(rs.getString("bracket_type")); game.round = rs.getInt("round_number");
                    game.group = rs.getString("group_id"); game.team1 = rs.getString("team1_id"); game.team2 = rs.getString("team2_id");
                    game.winner = rs.getString("winner_id"); game.status = rs.getString("status"); games.add(game);
                }
            }
        }
        if ("GSL".equals(format)) {
            Set<String> expectedGroups = new LinkedHashSet<>();
            try (PreparedStatement ps = conn.prepareStatement("SELECT g.id FROM groups g JOIN tournament_stages s ON s.id = g.stage_id WHERE s.tournament_id = ? AND s.stage_order = 1")) {
                ps.setString(1, id);
                try (ResultSet rs = ps.executeQuery()) { while (rs.next()) expectedGroups.add(rs.getString(1)); }
            }
            Set<String> actualGroups = new LinkedHashSet<>();
            for (CutMatch game : games) actualGroups.add(game.group);
            if (expectedGroups.isEmpty() || !actualGroups.equals(expectedGroups)) throw new IllegalStateException("Nhánh GSL chưa đủ các bảng đã cấu hình.");
        }
        Set<String> winners = cutWinners(format, teams.size(), target, games);
        List<CutTeam> qualified = new ArrayList<>();
        for (String winner : winners) {
            if (!teams.containsKey(winner)) throw new IllegalStateException("Đội không thuộc giải đấu.");
            qualified.add(teams.get(winner));
        }
        return qualified;
    }

    private static Set<String> cutWinners(String format, int total, int target, List<CutMatch> games) {
        if (target < 1 || target >= total || games.isEmpty()) throw new IllegalStateException("Cấu hình hoặc nhánh đấu chưa hợp lệ.");
        Set<String> result = new LinkedHashSet<>();
        if ("SINGLE_ELIMINATION".equals(format)) {
            collectCutWinners(games, "UB", service.CountAdvanceTeamService.calculateStoppingRoundSE(total, target), target, result);
        } else if ("DOUBLE_ELIMINATION".equals(format)) {
            if (!service.CountAdvanceTeamService.isValidDECutTarget(total, target)) throw new IllegalStateException("Số đội đi tiếp DE không hợp lệ.");
            int ub = service.DECutService.calculateUbStoppingRound(total, target);
            collectCutWinners(games, "UB", ub, target / 2, result);
            collectCutWinners(games, "LB", service.DECutService.calculateLbStoppingRound(ub), target / 2, result);
        } else if ("GSL".equals(format)) {
            Map<String, List<CutMatch>> groups = new LinkedHashMap<>();
            for (CutMatch game : games) {
                if (game.group == null) throw new IllegalStateException("Trận GSL chưa có bảng.");
                groups.computeIfAbsent(game.group, k -> new ArrayList<>()).add(game);
            }
            if (target % groups.size() != 0) throw new IllegalStateException("Số suất không khớp số bảng.");
            int perGroup = target / groups.size();
            for (List<CutMatch> group : groups.values()) {
                if (perGroup == 1) {
                    String source = group.stream().anyMatch(g -> "GF".equals(g.bracket)) ? "GF" : "UB";
                    collectCutWinners(group, source, lastCutRound(group, source), 1, result);
                } else {
                    if (perGroup % 2 != 0) throw new IllegalStateException("Số suất mỗi bảng không hợp lệ.");
                    collectCutWinners(group, "UB", lastCutRound(group, "UB"), perGroup / 2, result);
                    collectCutWinners(group, "LB", lastCutRound(group, "LB"), perGroup / 2, result);
                }
            }
        } else throw new IllegalArgumentException("Thể thức chưa được hỗ trợ.");
        if (result.size() != target) throw new IllegalStateException("Chưa đủ đội đi tiếp.");
        return result;
    }

    private static int lastCutRound(List<CutMatch> games, String source) {
        return games.stream().filter(g -> source.equals(g.bracket)).mapToInt(g -> g.round).max().orElse(0);
    }

    private static void collectCutWinners(List<CutMatch> games, String source, int round, int expected, Set<String> result) {
        int count = 0;
        for (CutMatch game : games) {
            if (game.round != round || !source.equals(game.bracket)) continue;
            if (game.team1 == null && game.team2 == null && "FINISHED".equalsIgnoreCase(game.status)) continue;
            if (game.winner == null || (!game.winner.equals(game.team1) && !game.winner.equals(game.team2))) throw new IllegalStateException("Trận quyết định chưa hoàn tất.");
            if (!result.add(game.winner)) throw new IllegalStateException("Đội đi tiếp bị trùng.");
            count++;
        }
        if (count != expected) throw new IllegalStateException("Số đội ở vòng dừng chưa đúng cấu hình.");
    }

    private static String cutTeamsJson(List<CutTeam> teams) {
        StringJoiner json = new StringJoiner(",", "[", "]");
        for (CutTeam team : teams) json.add("{\"id\":\"" + cutEscape(team.id) + "\",\"name\":\"" + cutEscape(team.name) + "\",\"seed\":" + team.seed + ",\"originalSeed\":" + team.seed + "}");
        return json.toString();
    }

    private static String cutEscape(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\r", "\\r").replace("\n", "\\n").replace("\t", "\\t");
    }

    public static final String TOURNAMENT_SELECT_QUERY =
        "SELECT t.*, " +
        "(SELECT TOP 1 format FROM tournament_stages WHERE tournament_id = t.id ORDER BY stage_order ASC) AS stage_format, " +
        "(SELECT COUNT(*) FROM teams WHERE tournament_id = t.id) AS team_count, " +
        "(SELECT COUNT(*) FROM matches WHERE tournament_id = t.id AND (is_bye = 0 OR is_bye IS NULL)) AS total_matches, " +
        "(SELECT COUNT(*) FROM matches WHERE tournament_id = t.id AND (is_bye = 0 OR is_bye IS NULL) AND (status IN ('FINISHED', 'COMPLETED', 'DONE') OR winner_id IS NOT NULL OR (score1 IS NOT NULL AND score2 IS NOT NULL))) AS completed_matches, " +
        "(SELECT TOP 1 tm.raw_name FROM matches m " +
        " LEFT JOIN tournament_stages ts ON m.stage_id = ts.id " +
        " JOIN teams tm ON m.winner_id = tm.id " +
        " WHERE m.tournament_id = t.id " +
        "   AND m.winner_id IS NOT NULL " +
        "   AND (m.is_bye = 0 OR m.is_bye IS NULL) " +
        "   AND ( " +
        "       (m.bracket_type IN ('GRAND_FINAL', 'GF_RESET') OR m.match_code LIKE N'%Chung Kết%' OR m.id LIKE '%_GF_%') " +
        "       OR " +
        "       (m.round_number = (SELECT MAX(m2.round_number) FROM matches m2 WHERE m2.tournament_id = t.id AND ISNULL(m2.stage_id, '') = ISNULL(m.stage_id, '') AND (m2.is_bye = 0 OR m2.is_bye IS NULL)) " +
        "        AND (m.match_code NOT LIKE N'%Hạng 3%' AND m.match_code NOT LIKE N'%3rd%' AND m.match_code NOT LIKE N'%Third%' OR m.match_code IS NULL)) " +
        "   ) " +
        "   AND (t.tournament_type <> 'MULTI_STAGE' OR ISNULL(ts.stage_order, 1) = 2 OR m.stage_id LIKE '%S2%' OR m.stage_id LIKE '%STAGE_2%') " +
        " ORDER BY ISNULL(ts.stage_order, 1) DESC, " +
        "   CASE WHEN m.bracket_type = 'GF_RESET' OR m.id LIKE '%GF_RESET%' THEN 100 " +
        "        WHEN m.bracket_type = 'GRAND_FINAL' OR m.match_code LIKE N'%Chung Kết%' OR m.id LIKE '%_GF_%' THEN 90 " +
        "        ELSE 0 END DESC, " +
        "   m.round_number DESC, m.match_order DESC) AS db_champion_name ";

    public Tournament mapTournamentFromResultSet(ResultSet rs) {
        try {
            String rawStatus = null;
            try { rawStatus = rs.getString("status"); } catch (Exception ignore) {}
            int totalMatches = 0;
            int completedMatches = 0;
            try { totalMatches = rs.getInt("total_matches"); } catch (Exception ignore) {}
            try { completedMatches = rs.getInt("completed_matches"); } catch (Exception ignore) {}

            String effectiveStatus = "INCOMING";
            if ("COMPLETED".equalsIgnoreCase(rawStatus) || "FINISHED".equalsIgnoreCase(rawStatus)) {
                effectiveStatus = "COMPLETED";
            } else if ("ONGOING".equalsIgnoreCase(rawStatus) || "IN_PROGRESS".equalsIgnoreCase(rawStatus)) {
                effectiveStatus = "IN_PROGRESS";
            } else if (completedMatches > 0) {
                effectiveStatus = "IN_PROGRESS";
            } else {
                effectiveStatus = "INCOMING";
            }

            Tournament t = new Tournament(
                    rs.getString("id"),
                    rs.getString("series_id"),
                    rs.getString("name"),
                    rs.getString("tournament_type"),
                    rs.getString("series_event_type"),
                    rs.getString("tier_name"),
                    rs.getInt("tournament_index_in_series"),
                    rs.getInt("phase_number"),
                    rs.getInt("max_teams_per_group"),
                    rs.getInt("advancing_seats_count"),
                    rs.getString("linked_qualifier_tournament_id"),
                    effectiveStatus,
                    rs.getTimestamp("created_at"));

            try {
                t.setTeamCount(rs.getInt("team_count"));
            } catch (Exception ignore) {}

            try {
                String fmt = rs.getString("stage_format");
                if (fmt != null && !fmt.trim().isEmpty()) {
                    t.setFormat(fmt.trim());
                }
            } catch (Exception ignore) {}

            try {
                if ("COMPLETED".equals(effectiveStatus)) {
                    String champDirect = null;
                    try { champDirect = rs.getString("champion_name"); } catch (Exception ignore) {}
                    String champSubq = null;
                    try { champSubq = rs.getString("db_champion_name"); } catch (Exception ignore) {}

                    String champ = (champDirect != null && !champDirect.trim().isEmpty()) ? champDirect.trim() : champSubq;
                    if (champ != null && !champ.trim().isEmpty()) {
                        t.setChampionName(champ.trim());
                    }
                } else {
                    t.setChampionName(null);
                }
            } catch (Exception ignore) {}

            try {
                t.setSeriesRewardPoints(rs.getInt("series_reward_points"));
            } catch (Exception ignore) {}
            try {
                t.setSeriesPointsConfig(rs.getString("series_points_config"));
            } catch (Exception ignore) {}
            try {
                t.setGroupAssignments(rs.getString("group_assignments"));
            } catch (Exception ignore) {}
            try {
                t.setStage2Teams(rs.getString("stage2_teams"));
            } catch (Exception ignore) {}
            try {
                t.setMultiStageConfig(rs.getString("multi_stage_config"));
            } catch (Exception ignore) {}
            try {
                t.setStage1Status(rs.getString("stage1_status"));
            } catch (Exception ignore) {}
            try {
                t.setTeamsJson(rs.getString("teams_json"));
            } catch (Exception ignore) {}

            return t;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public List<Tournament> getAllTournaments() {
        List<Tournament> list = new ArrayList<>();
        String sql = TOURNAMENT_SELECT_QUERY + "FROM tournaments t ORDER BY t.created_at DESC";
        DBContext db = new DBContext();

        try (Connection conn = db.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                Tournament t = mapTournamentFromResultSet(rs);
                if (t != null) {
                    list.add(t);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    public Tournament getTournamentById(String id) {
        if (id == null || id.trim().isEmpty()) return null;
        Tournament cached = TOURNAMENT_BY_ID_CACHE.get(id.trim());
        if (cached != null) return cached;

        String sql = TOURNAMENT_SELECT_QUERY + "FROM tournaments t WHERE t.id = ?";
        DBContext db = new DBContext();

        try (Connection conn = db.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, id.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Tournament t = mapTournamentFromResultSet(rs);
                    if (t != null) {
                        TOURNAMENT_BY_ID_CACHE.put(id.trim(), t);
                    }
                    return t;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public List<Tournament> getTournamentsBySeriesId(String seriesId) {
        List<Tournament> list = new ArrayList<>();
        if (seriesId == null || seriesId.trim().isEmpty()) return list;
        String sql = TOURNAMENT_SELECT_QUERY + "FROM tournaments t WHERE t.series_id = ? ORDER BY t.tournament_index_in_series ASC, t.created_at ASC";
        DBContext db = new DBContext();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, seriesId.trim());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Tournament t = mapTournamentFromResultSet(rs);
                    if (t != null) {
                        list.add(t);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    public boolean insertTournament(Tournament t) {
        String sql = "INSERT INTO tournaments (id, series_id, name, tournament_type, series_event_type, " +
                "tier_name, tournament_index_in_series, phase_number, max_teams_per_group, " +
                "advancing_seats_count, linked_qualifier_tournament_id, status) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        DBContext db = new DBContext();

        try (Connection conn = db.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, t.getId());
            ps.setString(2, t.getSeriesId());
            ps.setString(3, t.getName());
            ps.setString(4, t.getTournamentType());
            ps.setString(5, t.getSeriesEventType());
            ps.setString(6, t.getTierName());
            ps.setInt(7, t.getTournamentIndexInSeries());
            ps.setInt(8, t.getPhaseNumber());
            ps.setInt(9, t.getMaxTeamsPerGroup());
            ps.setInt(10, t.getAdvancingSeatsCount());
            ps.setString(11, t.getLinkedQualifierTournamentId());
            ps.setString(12, t.getStatus() != null ? t.getStatus() : "DRAFT");

            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    public boolean saveOrUpdateStageFormat(String tournamentId, String format) {
        if (tournamentId == null || format == null)
            return false;
        String cleanFmt = format.trim().toUpperCase();
        if (!cleanFmt.equals("SINGLE_ELIMINATION") && !cleanFmt.equals("DOUBLE_ELIMINATION") &&
                !cleanFmt.equals("ROUND_ROBIN") && !cleanFmt.equals("SWISS_LITE") && !cleanFmt.equals("GROUP_STAGE") && !cleanFmt.equals("GSL")) {
            cleanFmt = "SINGLE_ELIMINATION";
        }

        String checkSql = "SELECT id FROM tournament_stages WHERE tournament_id = ? AND stage_order = 1";
        String updateSql = "UPDATE tournament_stages SET format = ? WHERE tournament_id = ? AND stage_order = 1";
        String insertSql = "INSERT INTO tournament_stages (id, tournament_id, stage_order, stage_name, format, status) VALUES (?, ?, 1, ?, ?, 'PENDING')";
        DBContext db = new DBContext();

        try (Connection conn = db.getConnection()) {
            boolean exists = false;
            try (PreparedStatement psCheck = conn.prepareStatement(checkSql)) {
                psCheck.setString(1, tournamentId);
                try (ResultSet rs = psCheck.executeQuery()) {
                    if (rs.next())
                        exists = true;
                }
            }

            if (exists) {
                try (PreparedStatement psUpdate = conn.prepareStatement(updateSql)) {
                    psUpdate.setString(1, cleanFmt);
                    psUpdate.setString(2, tournamentId);
                    return psUpdate.executeUpdate() > 0;
                }
            } else {
                try (PreparedStatement psInsert = conn.prepareStatement(insertSql)) {
                    String stageId = "STG_" + java.util.UUID.randomUUID().toString().substring(0, 8);
                    psInsert.setString(1, stageId);
                    psInsert.setString(2, tournamentId);
                    psInsert.setString(3, "Stage 1: Main");
                    psInsert.setString(4, cleanFmt);
                    return psInsert.executeUpdate() > 0;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * Updates the advancingSeatsCount field of a tournament.
     * Used when format configuration is saved to persist cut target to DB.
     */
    public boolean updateAdvancingSeatsCount(String tournamentId, int advancingSeatsCount) {
        if (tournamentId == null || advancingSeatsCount < 1)
            return false;
        String sql = "UPDATE tournaments SET advancing_seats_count = ? WHERE id = ?";
        DBContext db = new DBContext();
        try (Connection conn = db.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, advancingSeatsCount);
            ps.setString(2, tournamentId);
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * Updates tournament_type field (SINGLE_STAGE or MULTI_STAGE).
     */
    public boolean updateTournamentType(String tournamentId, String tournamentType) {
        if (tournamentId == null || tournamentType == null)
            return false;
        if (!tournamentType.equals("SINGLE_STAGE") && !tournamentType.equals("MULTI_STAGE"))
            return false;
        String sql = "UPDATE tournaments SET tournament_type = ? WHERE id = ?";
        DBContext db = new DBContext();
        try (Connection conn = db.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tournamentType);
            ps.setString(2, tournamentId);
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    public boolean deleteTournament(String id) {
        if (id == null || id.trim().isEmpty()) return false;
        String tid = id.trim();

        String sqlUpdateTourneys = "UPDATE tournaments SET linked_qualifier_tournament_id = NULL WHERE linked_qualifier_tournament_id = ?";
        String sqlHistory = "DELETE FROM series_tournament_history WHERE tournament_id = ?";
        String sqlUpdateMatches = "UPDATE matches SET next_match_id = NULL WHERE tournament_id = ?";
        String sqlMatches = "DELETE FROM matches WHERE tournament_id = ?";
        String sqlGroupTeams1 = "DELETE FROM group_teams WHERE group_id IN (SELECT g.id FROM groups g JOIN tournament_stages s ON g.stage_id = s.id WHERE s.tournament_id = ?)";
        String sqlGroupTeams2 = "DELETE FROM group_teams WHERE team_id IN (SELECT id FROM teams WHERE tournament_id = ?)";
        String sqlGroups = "DELETE FROM groups WHERE stage_id IN (SELECT id FROM tournament_stages WHERE tournament_id = ?)";
        String sqlUpdateTeams = "UPDATE teams SET current_stage_id = NULL WHERE tournament_id = ?";
        String sqlTeams = "DELETE FROM teams WHERE tournament_id = ?";
        String sqlStages = "DELETE FROM tournament_stages WHERE tournament_id = ?";
        String sqlTourney = "DELETE FROM tournaments WHERE id = ?";
        DBContext db = new DBContext();

        try (Connection conn = db.getConnection()) {
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement ps = conn.prepareStatement(sqlUpdateTourneys)) {
                    ps.setString(1, tid);
                    ps.executeUpdate();
                } catch (Exception ignore) {}

                try (PreparedStatement ps = conn.prepareStatement(sqlHistory)) {
                    ps.setString(1, tid);
                    ps.executeUpdate();
                } catch (Exception ignore) {}

                try (PreparedStatement ps = conn.prepareStatement(sqlUpdateMatches)) {
                    ps.setString(1, tid);
                    ps.executeUpdate();
                } catch (Exception ignore) {}

                try (PreparedStatement ps = conn.prepareStatement(sqlMatches)) {
                    ps.setString(1, tid);
                    ps.executeUpdate();
                } catch (Exception ignore) {}

                try (PreparedStatement ps = conn.prepareStatement(sqlGroupTeams1)) {
                    ps.setString(1, tid);
                    ps.executeUpdate();
                } catch (Exception ignore) {}

                try (PreparedStatement ps = conn.prepareStatement(sqlGroupTeams2)) {
                    ps.setString(1, tid);
                    ps.executeUpdate();
                } catch (Exception ignore) {}

                try (PreparedStatement ps = conn.prepareStatement(sqlGroups)) {
                    ps.setString(1, tid);
                    ps.executeUpdate();
                } catch (Exception ignore) {}

                try (PreparedStatement ps = conn.prepareStatement(sqlUpdateTeams)) {
                    ps.setString(1, tid);
                    ps.executeUpdate();
                } catch (Exception ignore) {}

                try (PreparedStatement ps = conn.prepareStatement(sqlTeams)) {
                    ps.setString(1, tid);
                    ps.executeUpdate();
                } catch (Exception ignore) {}

                try (PreparedStatement ps = conn.prepareStatement(sqlStages)) {
                    ps.setString(1, tid);
                    ps.executeUpdate();
                } catch (Exception ignore) {}

                int rows = 0;
                try (PreparedStatement ps = conn.prepareStatement(sqlTourney)) {
                    ps.setString(1, tid);
                    rows = ps.executeUpdate();
                }

                conn.commit();
                return rows > 0;
            } catch (Exception ex) {
                conn.rollback();
                throw ex;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    public boolean createSubTournamentInSeries(Tournament t) {
        if (t == null || t.getId() == null || t.getName() == null) return false;
        String sql = "INSERT INTO tournaments (id, series_id, name, tournament_type, tier_name, tournament_index_in_series, phase_number, status) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        DBContext db = new DBContext();
        
        String tier = t.getTierName();
        if (tier != null) {
            tier = tier.replace("Tier ", "").trim();
        }
        if (tier == null || (!tier.equals("S") && !tier.equals("A") && !tier.equals("B") && !tier.equals("C") && !tier.equals("D"))) {
            tier = "S";
        }

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, t.getId());
            ps.setString(2, t.getSeriesId());
            ps.setString(3, t.getName());
            ps.setString(4, t.getTournamentType() != null ? t.getTournamentType() : "SINGLE_STAGE");
            ps.setString(5, tier);
            ps.setInt(6, t.getTournamentIndexInSeries() > 0 ? t.getTournamentIndexInSeries() : 1);
            ps.setInt(7, t.getPhaseNumber() > 0 ? t.getPhaseNumber() : 1);
            ps.setString(8, t.getStatus() != null ? t.getStatus() : "DRAFT");
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public boolean saveTournamentPointsConfig(String tournamentId, int rewardPoints, String pointsConfigJson) {
        if (tournamentId == null || tournamentId.trim().isEmpty()) return false;
        String sql = "UPDATE tournaments SET series_reward_points = ?, series_points_config = ? WHERE id = ?";
        DBContext db = new DBContext();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, rewardPoints);
            ps.setString(2, pointsConfigJson);
            ps.setString(3, tournamentId.trim());
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    public boolean copyPointsConfigFromTournament(String sourceTournamentId, String targetTournamentId) {
        return copyPointsConfigFromTournament(sourceTournamentId, targetTournamentId, true);
    }

    public boolean copyPointsConfigFromTournament(String sourceTournamentId, String targetTournamentId, boolean copyFormatConfig) {
        if (sourceTournamentId == null || targetTournamentId == null) return false;
        Tournament src = getTournamentById(sourceTournamentId.trim());
        if (src == null) return false;
        String sql = "UPDATE tournaments SET series_reward_points = ?, series_points_config = ?, multi_stage_config = COALESCE(?, multi_stage_config) WHERE id = ?";
        DBContext db = new DBContext();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            if (src.getSeriesRewardPoints() != null) {
                ps.setInt(1, src.getSeriesRewardPoints());
            } else {
                ps.setNull(1, java.sql.Types.INTEGER);
            }
            ps.setString(2, src.getSeriesPointsConfig());
            ps.setString(3, copyFormatConfig ? src.getMultiStageConfig() : null);
            ps.setString(4, targetTournamentId.trim());
            ps.executeUpdate();

            // Clone custom placement points if any
            String delSql = "DELETE FROM tournament_placement_points WHERE tournament_id = ?";
            try (PreparedStatement psDel = conn.prepareStatement(delSql)) {
                psDel.setString(1, targetTournamentId.trim());
                psDel.executeUpdate();
            }
            String selSql = "SELECT rank_position, points_awarded, elo_weight FROM tournament_placement_points WHERE tournament_id = ?";
            String insSql = "INSERT INTO tournament_placement_points (id, tournament_id, rank_position, points_awarded, elo_weight) VALUES (?, ?, ?, ?, ?)";
            try (PreparedStatement psSel = conn.prepareStatement(selSql);
                 PreparedStatement psIns = conn.prepareStatement(insSql)) {
                psSel.setString(1, sourceTournamentId.trim());
                try (ResultSet rs = psSel.executeQuery()) {
                    while (rs.next()) {
                        psIns.setString(1, "tpp_" + java.util.UUID.randomUUID().toString().substring(0, 8));
                        psIns.setString(2, targetTournamentId.trim());
                        psIns.setInt(3, rs.getInt("rank_position"));
                        psIns.setInt(4, rs.getInt("points_awarded"));
                        psIns.setDouble(5, rs.getDouble("elo_weight"));
                        psIns.executeUpdate();
                    }
                }
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    public boolean hasStageMatches(String tournamentId, int stageOrder) {
        if (tournamentId == null || tournamentId.trim().isEmpty()) return false;
        String sql = "SELECT COUNT(*) FROM matches m LEFT JOIN tournament_stages s ON s.id = m.stage_id "
            + "WHERE m.tournament_id = ? AND (s.stage_order = ? OR (m.stage_id IS NULL AND ? = 1))";
        try (Connection conn = new DBContext().getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tournamentId); ps.setInt(2, stageOrder); ps.setInt(3, stageOrder);
            try (ResultSet rs = ps.executeQuery()) { return rs.next() && rs.getInt(1) > 0; }
        } catch (Exception e) { throw new IllegalStateException("Cannot check existing matches", e); }
    }

    public boolean updateTournamentFormatAndType(String tournamentId, String format, String tournamentType, String stage1Format, String stage2Format, int advancingSeatsCount) {
        return updateTournamentFormatAndType(tournamentId, format, tournamentType, stage1Format, stage2Format, advancingSeatsCount, null);
    }

    public boolean updateTournamentFormatAndType(String tournamentId, String format, String tournamentType, String stage1Format, String stage2Format, int advancingSeatsCount, String configJson) {
        if (tournamentId == null || tournamentId.trim().isEmpty()) return false;
        if (format == null || format.trim().isEmpty()) format = "SINGLE_ELIMINATION";
        if (tournamentType == null || tournamentType.trim().isEmpty()) tournamentType = "SINGLE_STAGE";

        String sqlTourney = (advancingSeatsCount > 0)
            ? "UPDATE tournaments SET tournament_type = ?, advancing_seats_count = ? WHERE id = ?"
            : "UPDATE tournaments SET tournament_type = ? WHERE id = ?";

        DBContext db = new DBContext();
        try (Connection conn = db.getConnection()) {
            conn.setAutoCommit(false);
            // Changing a cut target after bracket generation would change its shape.
            if (advancingSeatsCount > 0) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT advancing_seats_count FROM tournaments WHERE id = ? AND EXISTS "
                        + "(SELECT 1 FROM matches WHERE tournament_id = ?)")) {
                    ps.setString(1, tournamentId.trim()); ps.setString(2, tournamentId.trim());
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next() && rs.getInt(1) != advancingSeatsCount) { conn.rollback(); return false; }
                    }
                }
            }
            try (PreparedStatement ps = conn.prepareStatement(sqlTourney)) {
                if (advancingSeatsCount > 0) {
                    ps.setString(1, tournamentType.trim().toUpperCase());
                    ps.setInt(2, advancingSeatsCount);
                    ps.setString(3, tournamentId.trim());
                } else {
                    ps.setString(1, tournamentType.trim().toUpperCase());
                    ps.setString(2, tournamentId.trim());
                }
                ps.executeUpdate();
            }

            // Preserve stage IDs: existing matches reference them.
            int expectedStages = "MULTI_STAGE".equalsIgnoreCase(tournamentType) ? 2 : 1;
            String[] formats = expectedStages == 2
                ? new String[] {stage1Format == null ? "GROUP_STAGE" : stage1Format,
                                stage2Format == null ? "SINGLE_ELIMINATION" : stage2Format}
                : new String[] {format};
            for (int order = 1; order <= expectedStages; order++) {
                String desiredFormat = formats[order - 1].trim().toUpperCase();
                String stageId = null;
                String oldFormat = null;
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT id, format FROM tournament_stages WHERE tournament_id = ? AND stage_order = ?")) {
                    ps.setString(1, tournamentId.trim());
                    ps.setInt(2, order);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) { stageId = rs.getString(1); oldFormat = rs.getString(2); }
                    }
                }
                if (stageId != null) {
                    if (!desiredFormat.equalsIgnoreCase(oldFormat)) {
                        try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM matches WHERE stage_id = ?")) {
                            ps.setString(1, stageId);
                            try (ResultSet rs = ps.executeQuery()) {
                                if (rs.next() && rs.getInt(1) > 0) { conn.rollback(); return false; }
                            }
                        }
                    }
                    try (PreparedStatement ps = conn.prepareStatement("UPDATE tournament_stages SET format = ? WHERE id = ?")) {
                        ps.setString(1, desiredFormat); ps.setString(2, stageId); ps.executeUpdate();
                    }
                } else {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "INSERT INTO tournament_stages (id, tournament_id, stage_order, stage_name, format) VALUES (?, ?, ?, ?, ?)")) {
                        ps.setString(1, "STAGE_" + java.util.UUID.randomUUID());
                        ps.setString(2, tournamentId.trim()); ps.setInt(3, order);
                        ps.setString(4, "Stage " + order); ps.setString(5, desiredFormat); ps.executeUpdate();
                    }
                }
            }
            // Never remove a stage which still owns matches or groups.
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT COUNT(*) FROM tournament_stages s WHERE s.tournament_id = ? AND s.stage_order > ? "
                    + "AND (EXISTS (SELECT 1 FROM matches m WHERE m.stage_id = s.id) "
                    + "OR EXISTS (SELECT 1 FROM groups g WHERE g.stage_id = s.id))")) {
                ps.setString(1, tournamentId.trim()); ps.setInt(2, expectedStages);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next() && rs.getInt(1) > 0) { conn.rollback(); return false; }
                }
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM tournament_stages WHERE tournament_id = ? AND stage_order > ?")) {
                ps.setString(1, tournamentId.trim()); ps.setInt(2, expectedStages); ps.executeUpdate();
            }
            if (configJson != null && !configJson.trim().isEmpty()) {
                try (PreparedStatement ps = conn.prepareStatement("UPDATE tournaments SET multi_stage_config = ? WHERE id = ?")) {
                    ps.setString(1, configJson); ps.setString(2, tournamentId.trim()); ps.executeUpdate();
                }
            }
            conn.commit();
            clearTournamentCache(tournamentId);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    public boolean updateTournamentFormatAndType(String tournamentId, String format, String tournamentType, String stage1Format, String stage2Format) {
        return updateTournamentFormatAndType(tournamentId, format, tournamentType, stage1Format, stage2Format, 0);
    }

    public boolean updateTournamentFormatAndType(String tournamentId, String format, String tournamentType) {
        return updateTournamentFormatAndType(tournamentId, format, tournamentType, null, null, 0);
    }

    private static final java.util.Map<String, List<String>> STAGE_FORMATS_CACHE = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Map<String, Map<String, List<String>>> SERIES_STAGE_FORMATS_CACHE = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Map<String, Tournament> TOURNAMENT_BY_ID_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    public static void clearStageFormatsCache() {
        STAGE_FORMATS_CACHE.clear();
        SERIES_STAGE_FORMATS_CACHE.clear();
        TOURNAMENT_BY_ID_CACHE.clear();
    }

    public static void clearTournamentCaches() {
        STAGE_FORMATS_CACHE.clear();
        SERIES_STAGE_FORMATS_CACHE.clear();
        TOURNAMENT_BY_ID_CACHE.clear();
    }

    public static void clearTournamentCache(String tournamentId) {
        if (tournamentId == null) return;
        String tid = tournamentId.trim();
        TOURNAMENT_BY_ID_CACHE.remove(tid);
        STAGE_FORMATS_CACHE.remove(tid);
        for (Map<String, List<String>> map : SERIES_STAGE_FORMATS_CACHE.values()) {
            map.remove(tid);
        }
    }

    public Map<String, List<String>> getStageFormatsBySeriesId(String seriesId) {
        if (seriesId == null || seriesId.trim().isEmpty()) return new HashMap<>();
        String sid = seriesId.trim();
        Map<String, List<String>> cachedSeries = SERIES_STAGE_FORMATS_CACHE.get(sid);
        if (cachedSeries != null) return new HashMap<>(cachedSeries);

        Map<String, List<String>> resultMap = new HashMap<>();

        String sql = "SELECT ts.tournament_id, ts.format " +
                     "FROM tournament_stages ts " +
                     "JOIN tournaments t ON ts.tournament_id = t.id " +
                     "WHERE t.series_id = ? " +
                     "ORDER BY ts.tournament_id, ts.stage_order ASC";
        DBContext db = new DBContext();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, sid);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String tid = rs.getString("tournament_id");
                    String fmt = rs.getString("format");
                    if (tid != null && fmt != null) {
                        resultMap.computeIfAbsent(tid.trim(), k -> new ArrayList<>()).add(fmt.trim().toUpperCase());
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        for (Map.Entry<String, List<String>> entry : resultMap.entrySet()) {
            STAGE_FORMATS_CACHE.put(entry.getKey(), entry.getValue());
        }
        if (!resultMap.isEmpty()) {
            SERIES_STAGE_FORMATS_CACHE.put(sid, resultMap);
        }
        return resultMap;
    }

    public List<String> getStageFormats(String tournamentId) {
        if (tournamentId == null || tournamentId.trim().isEmpty()) return new ArrayList<>();
        String tid = tournamentId.trim();
        List<String> cached = STAGE_FORMATS_CACHE.get(tid);
        if (cached != null) return cached;

        List<String> list = new ArrayList<>();
        String sql = "SELECT format FROM tournament_stages WHERE tournament_id = ? ORDER BY stage_order ASC";
        DBContext db = new DBContext();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tid);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String f = rs.getString("format");
                    if (f != null) list.add(f.trim().toUpperCase());
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        STAGE_FORMATS_CACHE.put(tid, list);
        return list;
    }

    public boolean updateTournamentStage1Status(String tournamentId, String stage1Status) {
        return saveStage1Status(tournamentId, stage1Status);
    }

    public boolean saveGroupAssignments(String tournamentId, String groupAssignmentsJson) {
        if (tournamentId == null || tournamentId.trim().isEmpty()) return false;
        clearTournamentCache(tournamentId);
        String sql = "UPDATE tournaments SET group_assignments = ? WHERE id = ?";
        DBContext db = new DBContext();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, groupAssignmentsJson);
            ps.setString(2, tournamentId.trim());
            boolean ok = ps.executeUpdate() > 0;
            if (ok) {
                clearTournamentCache(tournamentId);
                try {
                    Tournament t = getTournamentById(tournamentId.trim());
                    String fmt = t != null ? t.getTournamentType() : "";
                    if (fmt == null) fmt = "";
                    String s1Format = getStage1Format(tournamentId.trim());
                    if (s1Format != null && !s1Format.isEmpty()) {
                        fmt = s1Format;
                    }
                    if (fmt.toUpperCase().contains("GSL")) {
                        new dao.GSLStageDAO().ensureGSLInitialized(tournamentId.trim(), 1);
                    } else if (fmt.toUpperCase().contains("GROUP")) {
                        new dao.GroupStageDAO().syncGroupsAndGroupTeams(tournamentId.trim(), 1, groupAssignmentsJson);
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
            return ok;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    public String getStage1Format(String tournamentId) {
        if (tournamentId == null || tournamentId.trim().isEmpty()) return "";
        String sql = "SELECT format FROM tournament_stages WHERE tournament_id = ? AND stage_order = 1";
        DBContext db = new DBContext();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tournamentId.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String f = rs.getString("format");
                    return f != null ? f.trim() : "";
                }
            }
        } catch (Exception ignore) {}
        return "";
    }

    public String getSeriesIdByTournamentId(String tournamentId) {
        if (tournamentId == null || tournamentId.trim().isEmpty()) return null;
        String sql = "SELECT series_id FROM tournaments WHERE id = ?";
        DBContext db = new DBContext();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tournamentId.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("series_id");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public String getGroupAssignments(String tournamentId) {
        if (tournamentId == null || tournamentId.trim().isEmpty()) return null;
        String sql = "SELECT group_assignments FROM tournaments WHERE id = ?";
        DBContext db = new DBContext();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tournamentId.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("group_assignments");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public boolean saveStage2Teams(String tournamentId, String stage2TeamsJson) {
        Tournament tournament = getTournamentById(tournamentId);
        if (tournament != null && "MULTI_STAGE".equalsIgnoreCase(tournament.getTournamentType())
                && ("SINGLE_ELIMINATION".equalsIgnoreCase(tournament.getFormat())
                    || "DOUBLE_ELIMINATION".equalsIgnoreCase(tournament.getFormat())
                    || "GSL".equalsIgnoreCase(tournament.getFormat()))) {
            return saveCutStageTeams(tournamentId, stage2TeamsJson, false);
        }
        if (tournamentId == null || tournamentId.trim().isEmpty()) return false;
        clearTournamentCache(tournamentId);
        String sql = "UPDATE tournaments SET stage2_teams = ? WHERE id = ?";
        DBContext db = new DBContext();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, stage2TeamsJson);
            ps.setString(2, tournamentId.trim());
            boolean ok = ps.executeUpdate() > 0;
            if (ok) clearTournamentCache(tournamentId);
            return ok;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    public String getStage2Teams(String tournamentId) {
        if (tournamentId == null || tournamentId.trim().isEmpty()) return null;
        String sql = "SELECT stage2_teams FROM tournaments WHERE id = ?";
        DBContext db = new DBContext();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tournamentId.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("stage2_teams");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public boolean saveMultiStageConfig(String tournamentId, String multiStageConfigJson) {
        if (tournamentId == null || tournamentId.trim().isEmpty()) return false;
        clearTournamentCache(tournamentId);
        String sql = "UPDATE tournaments SET multi_stage_config = ? WHERE id = ?";
        DBContext db = new DBContext();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, multiStageConfigJson);
            ps.setString(2, tournamentId.trim());
            boolean ok = ps.executeUpdate() > 0;
            if (ok) clearTournamentCache(tournamentId);
            return ok;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    public String getMultiStageConfig(String tournamentId) {
        if (tournamentId == null || tournamentId.trim().isEmpty()) return null;
        String sql = "SELECT multi_stage_config FROM tournaments WHERE id = ?";
        DBContext db = new DBContext();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tournamentId.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("multi_stage_config");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public boolean updateTournamentTier(String tournamentId, String tierName) {
        if (tournamentId == null || tournamentId.trim().isEmpty()) return false;
        if (tierName == null || tierName.trim().isEmpty()) return false;
        String cleanTier = tierName.replace("Tier ", "").trim().toUpperCase();
        if (!cleanTier.equals("S") && !cleanTier.equals("A") && !cleanTier.equals("B") && !cleanTier.equals("C") && !cleanTier.equals("D")) {
            cleanTier = "S";
        }
        String sql = "UPDATE tournaments SET tier_name = ? WHERE id = ?";
        DBContext db = new DBContext();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, cleanTier);
            ps.setString(2, tournamentId.trim());
            boolean ok = ps.executeUpdate() > 0;
            TOURNAMENT_BY_ID_CACHE.remove(tournamentId.trim());
            return ok;
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("[TournamentDAO] updateTournamentTier failed for " + tournamentId + ": " + e.getMessage());
        }
        return false;
    }

    public boolean updateTournamentStatus(String tournamentId, String status) {
        if (tournamentId == null || tournamentId.trim().isEmpty()) return false;
        String sql = "UPDATE tournaments SET status = ? WHERE id = ?";
        DBContext db = new DBContext();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setString(2, tournamentId.trim());
            boolean ok = ps.executeUpdate() > 0;
            TOURNAMENT_BY_ID_CACHE.remove(tournamentId.trim());
            return ok;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    public boolean updateTournamentChampion(String tournamentId, String championName) {
        if (tournamentId == null || tournamentId.trim().isEmpty()) return false;
        String sql = "UPDATE tournaments SET champion_name = ? WHERE id = ?";
        DBContext db = new DBContext();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            if (championName != null && !championName.trim().isEmpty()) {
                ps.setString(1, championName.trim());
            } else {
                ps.setNull(1, java.sql.Types.NVARCHAR);
            }
            ps.setString(2, tournamentId.trim());
            ps.executeUpdate();
        } catch (Exception e) {
            // Column may not exist yet — log but don't crash
            System.err.println("[TournamentDAO] updateTournamentChampion failed for " + tournamentId + ": " + e.getMessage());
        }
        TOURNAMENT_BY_ID_CACHE.remove(tournamentId.trim());
        return true;
    }

    /**
     * Persist the full team name list (JSON array) for a tournament.
     * Stored in the teams_json column so rolling standings can read it without localStorage.
     */
    public boolean saveTeamsJson(String tournamentId, String teamsJson) {
        if (tournamentId == null || tournamentId.trim().isEmpty()) return false;
        clearTournamentCache(tournamentId);
        String sql = "UPDATE tournaments SET teams_json = ? WHERE id = ?";
        DBContext db = new DBContext();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, teamsJson);
            ps.setString(2, tournamentId.trim());
            boolean ok = ps.executeUpdate() > 0;
            if (ok) clearTournamentCache(tournamentId);
            return ok;
        } catch (Exception e) {
            System.err.println("[TournamentDAO] saveTeamsJson failed: " + e.getMessage());
        }
        return false;
    }

    public String getTeamsJson(String tournamentId) {
        if (tournamentId == null || tournamentId.trim().isEmpty()) return null;
        String sql = "SELECT teams_json FROM tournaments WHERE id = ?";
        DBContext db = new DBContext();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tournamentId.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("teams_json");
            }
        } catch (Exception e) {
            System.err.println("[TournamentDAO] getTeamsJson failed: " + e.getMessage());
        }
        return null;
    }

    /**
     * Persist Stage 1 lock/completion state to DB (replaces localStorage tourma_stage1_locked_).
     * status: 'PENDING' (in progress), 'LOCKED' (confirmed end, going to stage2), 'COMPLETED' (stage2 done)
     */
    public boolean saveStage1Status(String tournamentId, String status) {
        if (tournamentId == null || tournamentId.trim().isEmpty()) return false;
        // Validate allowed values
        if (!"PENDING".equals(status) && !"LOCKED".equals(status) && !"COMPLETED".equals(status)) return false;
        if (!"PENDING".equals(status)) {
            Tournament tournament = getTournamentById(tournamentId);
            if (tournament != null && "MULTI_STAGE".equalsIgnoreCase(tournament.getTournamentType())
                    && ("SINGLE_ELIMINATION".equalsIgnoreCase(tournament.getFormat())
                        || "DOUBLE_ELIMINATION".equalsIgnoreCase(tournament.getFormat())
                        || "GSL".equalsIgnoreCase(tournament.getFormat()))) {
                return saveCutStageTeams(tournamentId, getStage2Teams(tournamentId), true);
            }
        }
        String sql = "UPDATE tournaments SET stage1_status = ? WHERE id = ?";
        DBContext db = new DBContext();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setString(2, tournamentId.trim());
            boolean ok = ps.executeUpdate() > 0;
            TOURNAMENT_BY_ID_CACHE.remove(tournamentId.trim());
            return ok;
        } catch (Exception e) {
            System.err.println("[TournamentDAO] saveStage1Status failed for " + tournamentId + ": " + e.getMessage());
        }
        return false;
    }

    /**
     * Clones an existing tournament's configuration (format, stages, multi-stage config, group settings, points rule)
     * and creates a brand-new DRAFT tournament in the database.
     * 
     * @param sourceTournamentId ID of the tournament to copy from
     * @param newName Custom name for the cloned tournament (optional)
     * @param copyTeams If true, copies the registered teams from the source tournament with fresh IDs; if false, leaves teams empty
     * @return The newly cloned Tournament object with its new ID, or null on failure
     */
    public Tournament cloneTournament(String sourceTournamentId, String newName, boolean copyTeams) {
        if (sourceTournamentId == null || sourceTournamentId.trim().isEmpty()) {
            return null;
        }
        Tournament src = getTournamentById(sourceTournamentId.trim());
        if (src == null) return null;

        String newId = "t_" + System.currentTimeMillis() + "_" + ((int)(Math.random() * 900) + 100);
        String finalName = (newName != null && !newName.trim().isEmpty()) 
                ? newName.trim() 
                : (src.getName() + " (Bản sao)");

        DBContext db = new DBContext();
        String insertTournamentSql = "INSERT INTO tournaments (" +
                "id, series_id, name, tournament_type, series_event_type, " +
                "tier_name, series_reward_points, tournament_index_in_series, " +
                "phase_number, max_teams_per_group, advancing_seats_count, status, stage1_status, " +
                "series_points_config, group_assignments, multi_stage_config, created_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'DRAFT', 'PENDING', ?, ?, ?, GETDATE())";

        try (Connection conn = db.getConnection()) {
            conn.setAutoCommit(false);

            int nextIndex = src.getTournamentIndexInSeries();
            if (src.getSeriesId() != null && !src.getSeriesId().trim().isEmpty()) {
                nextIndex = getNextTournamentIndexInSeries(conn, src.getSeriesId().trim());
            }

            try (PreparedStatement ps = conn.prepareStatement(insertTournamentSql)) {
                ps.setString(1, newId);
                ps.setString(2, src.getSeriesId());
                ps.setString(3, finalName);
                ps.setString(4, src.getTournamentType() != null ? src.getTournamentType() : "SINGLE_STAGE");
                ps.setString(5, src.getSeriesEventType() != null ? src.getSeriesEventType() : "NONE");
                ps.setString(6, src.getTierName());
                if (src.getSeriesRewardPoints() != null) {
                    ps.setInt(7, src.getSeriesRewardPoints());
                } else {
                    ps.setNull(7, java.sql.Types.INTEGER);
                }
                ps.setInt(8, nextIndex);
                ps.setInt(9, src.getPhaseNumber() > 0 ? src.getPhaseNumber() : 1);
                ps.setInt(10, src.getMaxTeamsPerGroup() > 0 ? src.getMaxTeamsPerGroup() : 4);
                ps.setInt(11, src.getAdvancingSeatsCount() > 0 ? src.getAdvancingSeatsCount() : 16);
                ps.setString(12, src.getSeriesPointsConfig());
                ps.setString(13, src.getGroupAssignments());
                ps.setString(14, src.getMultiStageConfig());

                ps.executeUpdate();
            }

            // Clone tournament_stages
            String selectStagesSql = "SELECT stage_order, stage_name, format, target_wins FROM tournament_stages WHERE tournament_id = ? ORDER BY stage_order ASC";
            String insertStageSql = "INSERT INTO tournament_stages (id, tournament_id, stage_order, stage_name, format, target_wins, status) VALUES (?, ?, ?, ?, ?, ?, 'PENDING')";

            List<Object[]> stages = new ArrayList<>();
            try (PreparedStatement psStages = conn.prepareStatement(selectStagesSql)) {
                psStages.setString(1, sourceTournamentId.trim());
                try (ResultSet rs = psStages.executeQuery()) {
                    while (rs.next()) {
                        stages.add(new Object[]{
                            rs.getInt("stage_order"),
                            rs.getString("stage_name"),
                            rs.getString("format"),
                            rs.getInt("target_wins")
                        });
                    }
                }
            }

            if (stages.isEmpty()) {
                String fmt = src.getFormat() != null ? src.getFormat() : "SINGLE_ELIMINATION";
                try (PreparedStatement psStage = conn.prepareStatement(insertStageSql)) {
                    psStage.setString(1, "stg_" + java.util.UUID.randomUUID().toString().substring(0, 8));
                    psStage.setString(2, newId);
                    psStage.setInt(3, 1);
                    psStage.setString(4, "Stage 1");
                    psStage.setString(5, fmt);
                    psStage.setInt(6, 3);
                    psStage.executeUpdate();
                }
            } else {
                try (PreparedStatement psStage = conn.prepareStatement(insertStageSql)) {
                    for (Object[] st : stages) {
                        psStage.setString(1, "stg_" + java.util.UUID.randomUUID().toString().substring(0, 8));
                        psStage.setString(2, newId);
                        psStage.setInt(3, (Integer) st[0]);
                        psStage.setString(4, (String) st[1]);
                        psStage.setString(5, (String) st[2]);
                        psStage.setInt(6, (Integer) st[3]);
                        psStage.executeUpdate();
                    }
                }
            }

            // Clone custom placement points if any
            String selectPointsSql = "SELECT rank_position, points_awarded, elo_weight FROM tournament_placement_points WHERE tournament_id = ?";
            String insertPointsSql = "INSERT INTO tournament_placement_points (id, tournament_id, rank_position, points_awarded, elo_weight) VALUES (?, ?, ?, ?, ?)";
            try (PreparedStatement psPts = conn.prepareStatement(selectPointsSql)) {
                psPts.setString(1, sourceTournamentId.trim());
                try (ResultSet rs = psPts.executeQuery()) {
                    try (PreparedStatement psIns = conn.prepareStatement(insertPointsSql)) {
                        while (rs.next()) {
                            psIns.setString(1, "tpp_" + java.util.UUID.randomUUID().toString().substring(0, 8));
                            psIns.setString(2, newId);
                            psIns.setInt(3, rs.getInt("rank_position"));
                            psIns.setInt(4, rs.getInt("points_awarded"));
                            psIns.setDouble(5, rs.getDouble("elo_weight"));
                            psIns.executeUpdate();
                        }
                    }
                }
            } catch (Exception ignore) {}

            // Copy teams if requested
            if (copyTeams) {
                String selectTeamsSql = "SELECT raw_name, normalized_name, original_seed FROM teams WHERE tournament_id = ? ORDER BY original_seed ASC";
                String insertTeamSql = "INSERT INTO teams (id, tournament_id, raw_name, normalized_name, original_seed, status) VALUES (?, ?, ?, ?, ?, 'ACTIVE')";
                try (PreparedStatement psTeams = conn.prepareStatement(selectTeamsSql)) {
                    psTeams.setString(1, sourceTournamentId.trim());
                    try (ResultSet rs = psTeams.executeQuery()) {
                        try (PreparedStatement psInsTeam = conn.prepareStatement(insertTeamSql)) {
                            while (rs.next()) {
                                String tId = "TM_" + java.util.UUID.randomUUID().toString().substring(0, 8);
                                psInsTeam.setString(1, tId);
                                psInsTeam.setString(2, newId);
                                psInsTeam.setString(3, rs.getString("raw_name"));
                                psInsTeam.setString(4, rs.getString("normalized_name"));
                                psInsTeam.setInt(5, rs.getInt("original_seed"));
                                psInsTeam.executeUpdate();
                            }
                        }
                    }
                }
            }

            conn.commit();
            TOURNAMENT_BY_ID_CACHE.clear();

            return getTournamentById(newId);
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("[TournamentDAO] cloneTournament failed from " + sourceTournamentId + ": " + e.getMessage());
            return null;
        }
    }

    private int getNextTournamentIndexInSeries(Connection conn, String seriesId) {
        String sql = "SELECT ISNULL(MAX(tournament_index_in_series), 0) + 1 AS next_idx FROM tournaments WHERE series_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, seriesId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("next_idx");
                }
            }
        } catch (Exception ignore) {}
        return 1;
    }
}

