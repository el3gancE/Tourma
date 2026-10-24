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
    public boolean isClassicDraw(String tournamentId) {
        try (Connection conn=new DBContext().getConnection();
             PreparedStatement ps=conn.prepareStatement("SELECT classic_draw FROM tournaments WHERE id=?")) {
            ps.setString(1,tournamentId);
            try(ResultSet rs=ps.executeQuery()){ return rs.next() && rs.getBoolean(1); }
        } catch(Exception e){ throw new IllegalStateException("Cannot load draw mode",e); }
    }

    public boolean saveClassicDraw(String tournamentId, String raw) {
        if(raw==null) return true; // Older or unrelated forms preserve the saved mode.
        if(!"true".equals(raw) && !"false".equals(raw)) return false;
        try(Connection conn=new DBContext().getConnection();
            PreparedStatement ps=conn.prepareStatement("UPDATE tournaments SET classic_draw=? WHERE id=?")) {
            ps.setBoolean(1,Boolean.parseBoolean(raw));ps.setString(2,tournamentId);return ps.executeUpdate()==1;
        } catch(Exception e){e.printStackTrace();return false;}
    }

    public Integer getTopSeedCount(String tournamentId) {
        try (Connection conn = new DBContext().getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT top_seed_count FROM tournaments WHERE id=?")) {
            ps.setString(1, tournamentId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                int count=rs.getInt(1);
                return rs.wasNull() ? null : count;
            }
        } catch (Exception e) { throw new IllegalStateException("Cannot load Top Seed count", e); }
    }

    public boolean saveTopSeedCount(String tournamentId, String rawCount) {
        return saveTopSeedCount(tournamentId, rawCount, false);
    }

    public boolean saveTopSeedCount(String tournamentId, String rawCount, boolean onlyIfUnset) {
        if (rawCount == null) return true; // Older forms do not submit this setting.
        int count;
        try { count=rawCount.trim().isEmpty() ? 0 : Integer.parseInt(rawCount.trim()); }
        catch (NumberFormatException e) { return false; }
        if (count<0 || count>999) return false;
        try (Connection conn = new DBContext().getConnection();
             PreparedStatement ps = conn.prepareStatement("UPDATE tournaments SET top_seed_count=? WHERE id=?"
                     + (onlyIfUnset ? " AND top_seed_count IS NULL" : ""))) {
            ps.setInt(1,count); ps.setString(2,tournamentId);
            int updated=ps.executeUpdate();
            return updated==1 || (onlyIfUnset && getTopSeedCount(tournamentId)!=null);
        } catch (Exception e) { e.printStackTrace(); return false; }
    }

    /** Branch membership is replayed from persisted results of the displayed DE stage. */
    public String getDEBranchLiveStatusJson(String tournamentId, int stage) {
        if (stage < 1 || stage > 2) throw new IllegalArgumentException("Invalid stage");
        Map<String,Integer> categories = new LinkedHashMap<>();
        Set<String> dropped = new LinkedHashSet<>(), eliminated = new LinkedHashSet<>();
        int[] upperTotal=new int[3], upperAlive=new int[3], lowerTotal=new int[3], lowerAlive=new int[3];
        String rosterSql="SELECT tm.id,tm.original_seed,ISNULL(t.top_seed_count,0), "
            + "CASE WHEN t.series_id IS NULL OR EXISTS (SELECT 1 FROM partner_participants p WHERE p.series_id=t.series_id "
            + "AND (p.id=tm.partner_participant_id OR LOWER(LTRIM(RTRIM(p.name)))=LOWER(LTRIM(RTRIM(tm.raw_name))))) THEN 1 ELSE 0 END "
            + "FROM teams tm JOIN tournaments t ON t.id=tm.tournament_id WHERE t.id=?";
        String matchSql="SELECT m.team1_id,m.team2_id,m.winner_id,m.bracket_type,m.status,m.is_bye "
            + "FROM matches m LEFT JOIN tournament_stages s ON s.id=m.stage_id AND s.tournament_id=m.tournament_id "
            + "WHERE m.tournament_id=? AND (s.stage_order=? OR (m.stage_id IS NULL AND ?=1))";
        try(Connection conn=new DBContext().getConnection()) {
            Set<String> stageTeams=new LinkedHashSet<>();
            try(PreparedStatement ps=conn.prepareStatement(matchSql)) {
                ps.setString(1,tournamentId);ps.setInt(2,stage);ps.setInt(3,stage);
                try(ResultSet rs=ps.executeQuery()) { while(rs.next()) {
                    String a=rs.getString(1),b=rs.getString(2),winner=rs.getString(3);
                    if(a!=null)stageTeams.add(a);if(b!=null)stageTeams.add(b);
                    if(rs.getBoolean(6)||a==null||b==null||!finishedCutMatch(rs.getString(5))
                            ||winner==null||!(winner.equals(a)||winner.equals(b)))continue;
                    String loser=winner.equals(a)?b:a, bracket=String.valueOf(rs.getString(4)).toUpperCase(Locale.ROOT);
                    if(java.util.Arrays.asList("WINNER_BRACKET","UPPER","UB").contains(bracket))dropped.add(loser);
                    else if(java.util.Arrays.asList("LOSER_BRACKET","LOWER","LB","GRAND_FINAL","GRAND_FINALS","GRAND_FINAL_RESET","GF").contains(bracket))eliminated.add(loser);
                }}
            }
            try(PreparedStatement ps=conn.prepareStatement(rosterSql)) {
                ps.setString(1,tournamentId);
                try(ResultSet rs=ps.executeQuery()) { while(rs.next()) {
                    if(stage>1&&!stageTeams.contains(rs.getString(1)))continue;
                    int seed=rs.getInt(2),category=!rs.getBoolean(4)?2:seed>0&&seed<=rs.getInt(3)?0:1;
                    categories.put(rs.getString(1),category);
                }}
            }
            for(Map.Entry<String,Integer> entry:categories.entrySet()) {
                String id=entry.getKey();int category=entry.getValue();upperTotal[category]++;
                if(dropped.contains(id)) {lowerTotal[category]++;if(!eliminated.contains(id))lowerAlive[category]++;}
                else if(!eliminated.contains(id))upperAlive[category]++;
            }
        } catch(Exception e) {throw new IllegalStateException("Cannot load DE branch Live Status",e);}
        return "{\"upper\":"+liveCountsJson(upperAlive,upperTotal)+",\"lower\":"+liveCountsJson(lowerAlive,lowerTotal)+"}";
    }

    private static String liveCountsJson(int[] alive,int[] total) {
        String[] keys={"seed","unseed","guest"};StringJoiner json=new StringJoiner(",","{","}");
        for(int i=0;i<keys.length;i++)json.add("\""+keys[i]+"\":{\"alive\":"+alive[i]+",\"total\":"+total[i]+"}");
        return json.toString();
    }

    /** Original roster totals are stable across stages; only actual eliminations reduce alive counts. */
    public String getLiveStatusJson(String tournamentId) {
        Map<String,service.TournamentPlacementService.PlacementResult> results =
                service.TournamentPlacementService.getInstance().getFreshTournamentResults(tournamentId);
        int[] totals=new int[3], alive=new int[3];
        Set<String> eliminated=new LinkedHashSet<>();
        String sql="SELECT tm.id, tm.original_seed, ISNULL(t.top_seed_count,0) AS top_seed_count, "
            + "CASE WHEN t.series_id IS NULL OR EXISTS (SELECT 1 FROM partner_participants p WHERE p.series_id=t.series_id "
            + "AND (p.id=tm.partner_participant_id OR LOWER(LTRIM(RTRIM(p.name)))=LOWER(LTRIM(RTRIM(tm.raw_name))))) THEN 1 ELSE 0 END AS official "
            + "FROM teams tm JOIN tournaments t ON t.id=tm.tournament_id WHERE t.id=?";
        try (Connection conn=new DBContext().getConnection(); PreparedStatement ps=conn.prepareStatement(sql)) {
            // Match elimination also covers standalone GSL, which has no series placement rule.
            String lossSql="SELECT COALESCE(m.loser_id,CASE WHEN m.winner_id=m.team1_id THEN m.team2_id ELSE m.team1_id END) "
                + "FROM matches m LEFT JOIN tournament_stages s ON s.tournament_id=m.tournament_id "
                + "AND (s.id=m.stage_id OR (m.stage_id IS NULL AND s.stage_order=1)) "
                + "WHERE m.tournament_id=? AND ISNULL(m.is_bye,0)=0 AND m.team1_id IS NOT NULL AND m.team2_id IS NOT NULL "
                + "AND m.winner_id IN (m.team1_id,m.team2_id) AND (s.format='SINGLE_ELIMINATION' "
                + "OR (s.format IN ('DOUBLE_ELIMINATION','GSL') AND m.bracket_type IN "
                + "('LOSER_BRACKET','LOWER','LB','GRAND_FINAL','GRAND_FINALS','GRAND_FINAL_RESET','GF')))";
            try (PreparedStatement losses=conn.prepareStatement(lossSql)) {
                losses.setString(1,tournamentId);
                try (ResultSet rs=losses.executeQuery()) { while(rs.next()) eliminated.add(rs.getString(1)); }
            }
            ps.setString(1,tournamentId);
            try(ResultSet rs=ps.executeQuery()) {
                while(rs.next()) {
                    int seed=rs.getInt("original_seed");
                    int category=!rs.getBoolean("official") ? 2 : seed>0 && seed<=rs.getInt("top_seed_count") ? 0 : 1;
                    totals[category]++;
                    service.TournamentPlacementService.PlacementResult result=results.get(rs.getString("id"));
                    if(!eliminated.contains(rs.getString("id")) && (result==null || result.rank==1)) alive[category]++;
                }
            }
        } catch(Exception e) { throw new IllegalStateException("Cannot load Live Status",e); }
        return "{\"seed\":{\"alive\":"+alive[0]+",\"total\":"+totals[0]+"},"
            + "\"unseed\":{\"alive\":"+alive[1]+",\"total\":"+totals[1]+"},"
            + "\"guest\":{\"alive\":"+alive[2]+",\"total\":"+totals[2]+"}}";
    }

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
            // GS knockout draws are owned by the server; payload order cannot bypass group protection.
            String groupDestination = groupKnockoutDestination(conn, id);
            if (groupDestination != null) {
                ordered = "DOUBLE_ELIMINATION".equals(groupDestination) ? physicalSeedSlots(qualified) : qualified;
            }
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
        } catch (IllegalStateException e) { throw e; }
        catch (Exception e) { throw new IllegalStateException("Chưa xác định đủ đội đi tiếp từ kết quả đã lưu.", e); }
    }

    private static class CutTeam {
        String id, name, group;
        int seed, groupRank;
    }

    private static class CutMatch {
        String bracket, group, team1, team2, winner, status;
        int round;
        Integer score1, score2;
        boolean bye;
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
        try (PreparedStatement ps = conn.prepareStatement("SELECT m.* FROM matches m WITH (UPDLOCK, HOLDLOCK) LEFT JOIN tournament_stages s ON s.id = m.stage_id WHERE m.tournament_id = ? AND ((s.stage_order = 1 AND s.tournament_id=m.tournament_id) OR m.stage_id IS NULL) ORDER BY m.round_number, m.match_order, m.id")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    CutMatch game = new CutMatch(); game.bracket = cutBracket(rs.getString("bracket_type")); game.round = rs.getInt("round_number");
                    game.group = rs.getString("group_id"); game.team1 = rs.getString("team1_id"); game.team2 = rs.getString("team2_id");
                    game.winner = rs.getString("winner_id"); game.status = rs.getString("status");
                    game.score1 = (Integer) rs.getObject("score1"); game.score2 = (Integer) rs.getObject("score2");
                    game.bye = rs.getBoolean("is_bye"); games.add(game);
                }
            }
        }
        if ("ROUND_ROBIN".equals(format) || "GROUP_STAGE".equals(format)) {
            return qualifiedLeagueTeams(conn, id, format, teams, target, games);
        }
        if ("SWISS".equals(format) || "SWISS_LITE".equals(format)) {
            return qualifiedSwissTeams(teams, target, games);
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

    /** Persisted league scoring rules, shared with fixture generation and group standings. */
    public static int[] getLeagueRules(Connection conn, String id, int stage) throws java.sql.SQLException {
        int[] rules = {3, 1, 0, 1};
        String config = "CASE WHEN ISJSON(multi_stage_config) = 1 THEN multi_stage_config ELSE '{}' END";
        String prefix = "$.stage" + stage + "Config.";
        String[] keys = {"winPoints", "drawPoints", "lossPoints", "legsCount"};
        StringJoiner fields = new StringJoiner(",");
        for (String key : keys) fields.add("COALESCE(JSON_VALUE(" + config + ", '" + prefix + key + "'),"
                + (stage == 1 ? "JSON_VALUE(" + config + ", '$." + key + "')" : "NULL") + ")");
        try (PreparedStatement ps = conn.prepareStatement("SELECT " + fields + " FROM tournaments WHERE id = ?")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalStateException("Không tìm thấy giải đấu.");
                for (int i = 0; i < rules.length; i++) {
                    String value = rs.getString(i + 1);
                    if (value != null) {
                        try { rules[i] = Integer.parseInt(value); }
                        catch (NumberFormatException e) { throw new IllegalStateException("Cấu hình điểm hoặc số lượt chưa hợp lệ."); }
                    }
                }
            }
        }
        if (rules[3] < 1 || rules[3] > 10) throw new IllegalStateException("Số lượt vòng tròn phải từ 1 đến 10.");
        return rules;
    }

    private List<CutTeam> qualifiedLeagueTeams(Connection conn, String id, String format,
            Map<String, CutTeam> teams, int target, List<CutMatch> games) throws java.sql.SQLException {
        if (target < 1 || target >= teams.size()) throw new IllegalStateException("Số đội đi tiếp chưa hợp lệ.");
        int[] rules = getLeagueRules(conn, id, 1);
        Map<String, List<CutTeam>> groups = new LinkedHashMap<>();
        Map<String, String> membership = new HashMap<>();
        if ("GROUP_STAGE".equals(format)) {
            String sql = "SELECT g.id, gt.team_id FROM groups g WITH (HOLDLOCK) "
                    + "JOIN tournament_stages s ON s.id = g.stage_id AND s.tournament_id = ? AND s.stage_order = 1 "
                    + "LEFT JOIN group_teams gt WITH (HOLDLOCK) ON gt.group_id = g.id ORDER BY LEN(g.group_name), g.group_name, g.id, gt.seed_in_group, gt.team_id";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String group = rs.getString(1), team = rs.getString(2);
                        groups.computeIfAbsent(group, key -> new ArrayList<>());
                        if (!teams.containsKey(team) || membership.put(team, group) != null)
                            throw new IllegalStateException("Bảng chưa đủ đội hoặc có đội bị trùng.");
                        groups.get(group).add(teams.get(team));
                    }
                }
            }
            if (!membership.keySet().equals(teams.keySet())) throw new IllegalStateException("Danh sách chia bảng chưa đủ đội.");
        } else {
            groups.put("RR", new ArrayList<>(teams.values()));
            for (String team : teams.keySet()) membership.put(team, "RR");
        }
        if (groups.isEmpty()) throw new IllegalStateException("Chưa có bảng đấu.");
        int slots = target / groups.size();
        int remaining = target % groups.size();
        Map<String, int[]> stats = new HashMap<>(); // MP, W, D, L, GF, GA, PTS
        Map<String, Integer> pairs = new HashMap<>();
        for (String team : teams.keySet()) stats.put(team, new int[7]);
        for (CutMatch game : games) {
            if (game.bye) {
                String survivor = game.team1 == null ? game.team2 : game.team1;
                if ((game.team1 != null && game.team2 != null) || !teams.containsKey(survivor)
                        || !survivor.equals(game.winner) || !finishedCutMatch(game.status))
                    throw new IllegalStateException("Trận BYE chưa hợp lệ.");
                continue;
            }
            String group = membership.get(game.team1);
            if (group == null || !group.equals(membership.get(game.team2)) || game.team1.equals(game.team2)
                    || ("GROUP_STAGE".equals(format) && !group.equals(game.group))
                    || !finishedCutMatch(game.status) || game.score1 == null || game.score2 == null
                    || game.score1 < 0 || game.score2 < 0)
                throw new IllegalStateException("Vòng tròn còn trận chưa xong hoặc có cặp đấu không hợp lệ.");
            String winner = game.score1 > game.score2 ? game.team1 : game.score1 < game.score2 ? game.team2 : null;
            if (!java.util.Objects.equals(winner, game.winner)) throw new IllegalStateException("Kết quả không khớp tỷ số.");
            String pair = game.team1.compareTo(game.team2) < 0 ? game.team1 + "|" + game.team2 : game.team2 + "|" + game.team1;
            if (pairs.merge(pair, 1, Integer::sum) > rules[3]) throw new IllegalStateException("Cặp đấu bị lặp quá số lượt cấu hình.");
            int[] a = stats.get(game.team1), b = stats.get(game.team2);
            a[0]++; b[0]++; a[4] += game.score1; a[5] += game.score2; b[4] += game.score2; b[5] += game.score1;
            if (winner == null) { a[2]++; b[2]++; a[6] += rules[1]; b[6] += rules[1]; }
            else if (winner.equals(game.team1)) { a[1]++; b[3]++; a[6] += rules[0]; b[6] += rules[2]; }
            else { b[1]++; a[3]++; b[6] += rules[0]; a[6] += rules[2]; }
        }
        java.util.Comparator<CutTeam> ranking = java.util.Comparator.<CutTeam>comparingInt(t -> stats.get(t.id)[6]).reversed()
                .thenComparing((a, b) -> Integer.compare(stats.get(b.id)[4] - stats.get(b.id)[5], stats.get(a.id)[4] - stats.get(a.id)[5]))
                .thenComparing((a, b) -> Integer.compare(stats.get(b.id)[4], stats.get(a.id)[4]))
                .thenComparingInt(t -> t.seed > 0 ? t.seed : Integer.MAX_VALUE).thenComparing(t -> t.id);
        List<CutTeam> qualified = new ArrayList<>(), wildcardCandidates = new ArrayList<>();
        for (Map.Entry<String, List<CutTeam>> group : groups.entrySet()) {
            List<CutTeam> members = group.getValue();
            if (members.size() < slots) throw new IllegalStateException("Bảng không đủ đội cho số suất cứng.");
            for (int i = 0; i < members.size(); i++) for (int j = i + 1; j < members.size(); j++) {
                String a = members.get(i).id, b = members.get(j).id;
                String key = a.compareTo(b) < 0 ? a + "|" + b : b + "|" + a;
                if (pairs.getOrDefault(key, 0) != rules[3]) throw new IllegalStateException("Chưa hoàn thành đủ lịch vòng tròn.");
            }
            members.sort(ranking);
            for(int rank=0;rank<members.size();rank++){members.get(rank).group=group.getKey();members.get(rank).groupRank=rank+1;}
            qualified.addAll(members.subList(0, slots));
            if (remaining > 0 && members.size() > slots) wildcardCandidates.add(members.get(slots));
            if ("GROUP_STAGE".equals(format) && !conn.getAutoCommit()) {
                try (PreparedStatement ps = conn.prepareStatement("UPDATE group_teams SET matches_played=?,wins=?,draws=?,losses=?,goals_scored=?,goals_conceded=?,goal_difference=?,points=?,rank_in_group=? WHERE group_id=? AND team_id=?")) {
                    for (int i = 0; i < members.size(); i++) {
                        CutTeam team = members.get(i); int[] st = stats.get(team.id);
                        for (int k = 0; k < 6; k++) ps.setInt(k + 1, st[k]);
                        ps.setInt(7, st[4] - st[5]); ps.setInt(8, st[6]); ps.setInt(9, i + 1);
                        ps.setString(10, group.getKey()); ps.setString(11, team.id); ps.addBatch();
                    }
                    ps.executeBatch();
                }
            }
        }
        if (remaining > 0) {
            if (wildcardCandidates.size() < remaining) throw new IllegalStateException("Không đủ đội xếp hạng tiếp theo để phân suất bổ sung.");
            wildcardCandidates.sort(ranking);
            // Rank-major order gives direct qualifiers seed priority over every wildcard.
            qualified.clear();
            for (int rank=0;rank<slots;rank++) for(List<CutTeam> members:groups.values()) qualified.add(members.get(rank));
            qualified.addAll(wildcardCandidates.subList(0, remaining));
        }
        if (qualified.size()!=target) throw new IllegalStateException("Số đội đi tiếp chưa khớp cấu hình.");
        if ("GROUP_STAGE".equals(format) && !conn.getAutoCommit()) {
            Map<String,Integer> counts=new HashMap<>();
            for(CutTeam team:qualified)counts.merge(membership.get(team.id),1,Integer::sum);
            try(PreparedStatement ps=conn.prepareStatement("UPDATE groups SET qualified_slots_count=? WHERE id=?")){
                for(String group:groups.keySet()){ps.setInt(1,counts.getOrDefault(group,0));ps.setString(2,group);ps.addBatch();}
                ps.executeBatch();
            }
        }
        if ("GROUP_STAGE".equals(format) && groupKnockoutDestination(conn,id)!=null) {
            // Keep the agreed A-B/C-D draw whenever its equal-field prerequisites hold.
            if(remaining==0 && groups.size()>1 && (groups.size() & (groups.size()-1))==0 && (slots & (slots-1))==0)
                qualified=groupCrossoverSeedOrder(new ArrayList<>(groups.values()),slots);
            else {
                qualified.sort(java.util.Comparator.comparingInt((CutTeam team)->team.groupRank).thenComparing(ranking));
                qualified=constrainedGroupSeedOrder(qualified);
            }
        }
        // Seeds describe draw order; the popup applies the Stage 2 bracket layout afterwards.
        for (int i = 0; i < qualified.size(); i++) qualified.get(i).seed = i + 1;
        return qualified;
    }

    /** Group-aware knockout draws apply only to GS -> SE/DE; RR destinations retain league order. */
    private static String groupKnockoutDestination(Connection conn, String id) throws java.sql.SQLException {
        String sql="SELECT b.format FROM tournament_stages a JOIN tournament_stages b ON b.tournament_id=a.tournament_id "
                + "AND b.stage_order=2 WHERE a.tournament_id=? AND a.stage_order=1 AND a.format='GROUP_STAGE' "
                + "AND b.format IN ('SINGLE_ELIMINATION','DOUBLE_ELIMINATION')";
        try(PreparedStatement ps=conn.prepareStatement(sql)) {
            ps.setString(1,id);try(ResultSet rs=ps.executeQuery()){return rs.next()?rs.getString(1):null;}
        }
    }

    /** The index at each physical slot in the same standard layout consumed by SE and the DE popup. */
    private static List<Integer> leagueSeedLayout(int size) {
        if(size<2 || (size & (size-1))!=0) throw new IllegalStateException("Số đội nhánh đấu phải là lũy thừa của 2.");
        List<Integer> layout=new ArrayList<>();layout.add(1);layout.add(2);
        for(int width=4;width<=size;width*=2){
            List<Integer> next=new ArrayList<>();
            for(int rank:layout){next.add(rank);next.add(width+1-rank);}
            layout=next;
        }
        for(int i=0;i<size;i+=2){
            int a=layout.get(i),b=layout.get(i+1);layout.set(i,Math.min(a,b));layout.set(i+1,Math.max(a,b));
        }
        return layout;
    }

    private static List<CutTeam> physicalSeedSlots(List<CutTeam> seedOrder) {
        // Non-power-of-two GS fields retain seed order; DE places them in a padded standard bracket.
        if((seedOrder.size() & (seedOrder.size()-1))!=0)return new ArrayList<>(seedOrder);
        List<CutTeam> slots=new ArrayList<>();
        for(int rank:leagueSeedLayout(seedOrder.size()))slots.add(seedOrder.get(rank-1));
        return slots;
    }

    /** A-B, C-D, ...; reverse ranks within each pair and put each group's ranks 1 and 2 in opposite halves. */
    private static List<CutTeam> groupCrossoverSeedOrder(List<List<CutTeam>> groups, int perGroup) {
        int size=groups.size()*perGroup;
        List<Integer> layout=leagueSeedLayout(size);
        if(groups.size()%2!=0 || (perGroup>1 && perGroup%2!=0))
            throw new IllegalStateException("Ghép chéo cần số bảng chẵn và số suất mỗi bảng phù hợp nhánh đấu.");
        List<CutTeam> physical=new ArrayList<>();
        for(int half=0;half<2;half++) {
            for(int group=0;group<groups.size();group+=2) {
                for(int rank=half;rank<perGroup;rank+=2) {
                    physical.add(groups.get(group).get(rank));
                    physical.add(groups.get(group+1).get(perGroup-1-rank));
                }
            }
        }
        List<CutTeam> seedOrder=new ArrayList<>(java.util.Collections.nCopies(size,(CutTeam)null));
        for(int slot=0;slot<size;slot++)seedOrder.set(layout.get(slot)-1,physical.get(slot));
        return seedOrder;
    }

    /** Exact search on occupied standard seed slots; BYEs stay spread across the padded bracket. */
    private static List<CutTeam> constrainedGroupSeedOrder(List<CutTeam> teams) {
        return new GroupDrawSearch(teams).draw();
    }

    private static final class GroupDrawSearch {
        final List<CutTeam> teams;
        final int count, size;
        final int[] seedSlots, occupants, placements, mates, groupIndex, remaining;
        final boolean[] used;
        final int[] freeHalf=new int[2];
        int states;
        final long deadline=System.nanoTime()+3_000_000_000L;

        GroupDrawSearch(List<CutTeam> teams) {
            this.teams=teams;count=teams.size();int padded=2;while(padded<count)padded*=2;size=padded;
            if(count<2)throw new IllegalStateException("Stage 2 cần ít nhất hai đội.");
            seedSlots=new int[count];occupants=new int[size];placements=new int[count];mates=new int[count];groupIndex=new int[count];used=new boolean[count];
            java.util.Arrays.fill(occupants,-2);java.util.Arrays.fill(placements,-1);java.util.Arrays.fill(mates,-1);
            List<Integer> layout=leagueSeedLayout(size);
            for(int slot=0;slot<size;slot++)if(layout.get(slot)<=count){seedSlots[layout.get(slot)-1]=slot;occupants[slot]=-1;freeHalf[slot/(size/2)]++;}
            Map<String,Integer> indices=new LinkedHashMap<>();Map<String,int[]> protectedTeams=new HashMap<>();
            for(int team=0;team<count;team++) {
                CutTeam entry=teams.get(team);
                if(entry.group==null || entry.groupRank<1)throw new IllegalStateException("Thiếu thông tin bảng hoặc thứ hạng của đội đi tiếp.");
                groupIndex[team]=indices.computeIfAbsent(entry.group,key->indices.size());
                if(entry.groupRank<=2){int[] pair=protectedTeams.computeIfAbsent(entry.group,key->new int[]{-1,-1});pair[entry.groupRank-1]=team;}
            }
            remaining=new int[indices.size()];for(int group:groupIndex)remaining[group]++;
            for(int n:remaining)if(n>size/2)throw new IllegalStateException("Không thể tránh tái đấu vòng đầu: một bảng có quá nhiều đội đi tiếp.");
            for(int[] pair:protectedTeams.values())if(pair[0]>=0&&pair[1]>=0){mates[pair[0]]=pair[1];mates[pair[1]]=pair[0];}
        }

        List<CutTeam> draw() {
            if(!search(0))throw new IllegalStateException("Không có cách xếp nhánh thỏa điều kiện khác bảng vòng đầu và tách hai đội đầu bảng.");
            List<CutTeam> result=new ArrayList<>();
            for(int slot:seedSlots)result.add(teams.get(occupants[slot]));
            return result;
        }

        boolean allowed(int team,int slot) {
            int opponent=occupants[slot^1];
            if(opponent>=0&&groupIndex[opponent]==groupIndex[team])return false;
            int mate=mates[team];
            return mate<0||placements[mate]<0||placements[mate]/(size/2)!=slot/(size/2);
        }

        boolean feasible(int left) {
            int[] required=new int[2];
            for(int team=0;team<count;team++)if(mates[team]>team){
                int a=placements[team],b=placements[mates[team]];
                if(a<0&&b<0){required[0]++;required[1]++;}
                else if(a<0)required[1-b/(size/2)]++;
                else if(b<0)required[1-a/(size/2)]++;
            }
            if(required[0]>freeHalf[0]||required[1]>freeHalf[1])return false;
            // Each group can occupy at most one slot per remaining opening match.
            if(left<=64)for(int group=0;group<remaining.length;group++)if(remaining[group]>0){
                int available=0;
                for(int slot=0;slot<size;slot+=2){
                    int a=occupants[slot],b=occupants[slot+1];
                    if((a==-1||b==-1)&&!(a>=0&&groupIndex[a]==group)&&!(b>=0&&groupIndex[b]==group))available++;
                }
                if(available<remaining[group])return false;
            }
            return true;
        }

        boolean search(int seed) {
            if(seed==count)return true;
            if(++states>250_000 || ((states&255)==0 && System.nanoTime()>deadline))
                throw new IllegalStateException("Chưa tìm được nhánh hợp lệ trong giới hạn xử lý. Hãy kiểm tra số bảng và số suất đi tiếp.");
            int slot=seedSlots[seed],half=slot/(size/2);
            // The input order favors higher group ranks and stronger standings for early seeds/BYEs.
            for(int team=0;team<count;team++)if(!used[team]&&allowed(team,slot)){
                used[team]=true;placements[team]=slot;occupants[slot]=team;remaining[groupIndex[team]]--;freeHalf[half]--;
                if(feasible(count-seed-1)&&search(seed+1))return true;
                freeHalf[half]++;remaining[groupIndex[team]]++;occupants[slot]=-1;placements[team]=-1;used[team]=false;
            }
            return false;
        }
    }

    private static boolean finishedCutMatch(String status) {
        return "FINISHED".equalsIgnoreCase(status) || "COMPLETED".equalsIgnoreCase(status) || "DONE".equalsIgnoreCase(status);
    }

    /** Replay each Swiss round from persisted matches before accepting a stage transition. */
    private static List<CutTeam> qualifiedSwissTeams(Map<String, CutTeam> teams, int target, List<CutMatch> games) {
        if (teams.size() != 16 || target != 8 || games.size() != 33)
            throw new IllegalStateException("Swiss cần đủ 16 đội, 33 trận và 8 suất đi tiếp.");
        Map<String, int[]> stats = new LinkedHashMap<>(); // wins, losses, diff, scores for
        Map<String, List<String>> opponents = new LinkedHashMap<>();
        for (String team : teams.keySet()) { stats.put(team, new int[4]); opponents.put(team, new ArrayList<>()); }
        int[] expected = {0, 8, 8, 8, 6, 3};
        for (int round = 1; round <= 5; round++) {
            Set<String> active = new LinkedHashSet<>(), seen = new LinkedHashSet<>();
            for (Map.Entry<String, int[]> entry : stats.entrySet())
                if (entry.getValue()[0] < 3 && entry.getValue()[1] < 3) active.add(entry.getKey());
            int count = 0;
            for (CutMatch game : games) {
                if (game.round != round) continue;
                int[] a = stats.get(game.team1), b = stats.get(game.team2);
                if (game.bye || a == null || b == null || game.team1.equals(game.team2)
                        || !active.contains(game.team1) || !active.contains(game.team2)
                        || !seen.add(game.team1) || !seen.add(game.team2)
                        || a[0] != b[0] || a[1] != b[1]
                        || game.score1 == null || game.score2 == null || game.score1 < 0 || game.score2 < 0
                        || game.score1.equals(game.score2)
                        || !("FINISHED".equalsIgnoreCase(game.status) || "COMPLETED".equalsIgnoreCase(game.status)
                            || "DONE".equalsIgnoreCase(game.status)))
                    throw new IllegalStateException("Kết quả hoặc cặp đấu Swiss chưa hợp lệ.");
                boolean firstWon = game.score1 > game.score2;
                if (!(firstWon ? game.team1 : game.team2).equals(game.winner))
                    throw new IllegalStateException("Đội thắng Swiss không khớp tỷ số.");
                if (firstWon) { a[0]++; b[1]++; } else { b[0]++; a[1]++; }
                a[2] += game.score1 - game.score2; b[2] += game.score2 - game.score1;
                a[3] += game.score1; b[3] += game.score2;
                opponents.get(game.team1).add(game.team2); opponents.get(game.team2).add(game.team1); count++;
            }
            if (count != expected[round] || !seen.equals(active))
                throw new IllegalStateException("Swiss chưa hoàn thành đủ các vòng.");
        }
        Map<String, Integer> buchholz = new HashMap<>();
        List<CutTeam> qualified = new ArrayList<>();
        for (String id : teams.keySet()) {
            int[] record = stats.get(id);
            if (record[0] != 3 && record[1] != 3) throw new IllegalStateException("Swiss còn đội chưa kết thúc.");
            buchholz.put(id, opponents.get(id).stream().mapToInt(o -> stats.get(o)[0] * 3).sum());
            if (record[0] == 3) qualified.add(teams.get(id));
        }
        if (qualified.size() != target) throw new IllegalStateException("Swiss chưa đủ đội đi tiếp.");
        qualified.sort(java.util.Comparator.<CutTeam>comparingInt(t -> stats.get(t.id)[1])
            .thenComparing((a, b) -> Integer.compare(buchholz.get(b.id), buchholz.get(a.id)))
            .thenComparing((a, b) -> Integer.compare(stats.get(b.id)[2], stats.get(a.id)[2]))
            .thenComparing((a, b) -> Integer.compare(stats.get(b.id)[3], stats.get(a.id)[3]))
            .thenComparingInt(t -> t.seed).thenComparing(t -> t.id));
        for (int i = 0; i < qualified.size(); i++) qualified.get(i).seed = i + 1;
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
        "(SELECT TOP 1 format FROM tournament_stages WHERE tournament_id = t.id AND stage_order = 2 ORDER BY id) AS stage2_format, " +
        "(SELECT COUNT(*) FROM teams WHERE tournament_id = t.id) AS team_count, " +
        "ISNULL(ms.total_matches,0) AS total_matches, ISNULL(ms.completed_matches,0) AS completed_matches, " +
        "ISNULL(ms.stage1_total_matches,0) AS stage1_total_matches, " +
        "(SELECT TOP 1 tm.raw_name FROM matches m " +
        " LEFT JOIN tournament_stages ts ON m.stage_id = ts.id " +
        " JOIN teams tm ON m.winner_id = tm.id " +
        " WHERE m.tournament_id = t.id AND t.status IN ('COMPLETED','FINISHED') AND NULLIF(LTRIM(RTRIM(t.champion_name)), N'') IS NULL " +
        "   AND m.winner_id IS NOT NULL " +
        "   AND (m.is_bye = 0 OR m.is_bye IS NULL) " +
        "   AND ( " +
        "       (m.bracket_type IN ('GRAND_FINAL', 'GF_RESET') OR m.match_code LIKE N'%Chung Kết%' OR m.id LIKE '%_GF_%') " +
        "       OR " +
        "       (m.round_number = (SELECT MAX(m2.round_number) FROM matches m2 WHERE m2.tournament_id = t.id AND ISNULL(m2.stage_id, '') = ISNULL(m.stage_id, '') AND (m2.is_bye = 0 OR m2.is_bye IS NULL)) " +
        "        AND (m.match_code NOT LIKE N'%Hạng 3%' AND m.match_code NOT LIKE N'%3rd%' AND m.match_code NOT LIKE N'%Third%' OR m.match_code IS NULL)) " +
        "   ) " +
        "   AND (t.tournament_type <> 'MULTI_STAGE' OR ts.stage_order = 2) " +
        " ORDER BY ISNULL(ts.stage_order, 1) DESC, " +
        "   CASE WHEN m.bracket_type = 'GF_RESET' OR m.id LIKE '%GF_RESET%' THEN 100 " +
        "        WHEN m.bracket_type = 'GRAND_FINAL' OR m.match_code LIKE N'%Chung Kết%' OR m.id LIKE '%_GF_%' THEN 90 " +
        "        ELSE 0 END DESC, " +
        "   m.round_number DESC, m.match_order DESC) AS db_champion_name ";

    // Aggregate the match table once instead of repeating four counts for every tournament.
    public static final String TOURNAMENT_FROM_QUERY =
        "FROM tournaments t LEFT JOIN (" +
        "SELECT m.tournament_id, COUNT(*) AS total_matches, " +
        "SUM(CASE WHEN m.status IN ('FINISHED','COMPLETED','DONE') OR m.winner_id IS NOT NULL OR (m.score1 IS NOT NULL AND m.score2 IS NOT NULL) THEN 1 ELSE 0 END) AS completed_matches, " +
        "SUM(CASE WHEN m.stage_id IS NULL OR ts.stage_order=1 THEN 1 ELSE 0 END) AS stage1_total_matches " +
        "FROM matches m LEFT JOIN tournament_stages ts ON ts.id=m.stage_id AND ts.tournament_id=m.tournament_id " +
        "WHERE m.is_bye=0 OR m.is_bye IS NULL GROUP BY m.tournament_id) ms ON ms.tournament_id=t.id ";

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

            t.setTotalMatches(totalMatches);
            t.setCompletedMatches(completedMatches);
            try {
                t.setStage1TotalMatches(rs.getInt("stage1_total_matches"));
            } catch (Exception ignore) {}

            try {
                String fmt = rs.getString("stage_format");
                if (fmt != null && !fmt.trim().isEmpty()) {
                    t.setFormat(fmt.trim());
                }
            } catch (Exception ignore) {}
            try { t.setStage2Format(rs.getString("stage2_format")); } catch (Exception ignore) {}

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

    public static final class TournamentPage {
        private final List<Tournament> tournaments;
        private final int totalCount;
        private final int page;
        private final String filter;
        private final String query;

        private TournamentPage(List<Tournament> tournaments, int totalCount, int page, String filter, String query) {
            this.tournaments = tournaments; this.totalCount = totalCount; this.page = page;
            this.filter = filter; this.query = query;
        }
        public List<Tournament> getTournaments() { return tournaments; }
        public int getTotalCount() { return totalCount; }
        public int getPage() { return page; }
        public int getTotalPages() { return Math.max(1, (totalCount + 24) / 25); }
        public String getFilter() { return filter; }
        public String getQuery() { return query; }
    }

    /** Filter in SQL before fetching a maximum of 25 cards; no browser-side paging of all rows. */
    public TournamentPage getTournamentPage(String seriesId, String rawQuery, String rawFilter, String rawPage) {
        String query = rawQuery == null ? "" : rawQuery.trim();
        String filter = rawFilter == null ? "ALL" : rawFilter.toUpperCase(Locale.ROOT);
        if (!Set.of("ALL", "STANDALONE", "SERIES", "INCOMING", "IN_PROGRESS", "COMPLETED").contains(filter)) filter = "ALL";
        int requestedPage = 1;
        try { requestedPage = Math.max(1, Integer.parseInt(rawPage)); } catch (Exception ignored) {}
        List<String> parameters = new ArrayList<>();
        StringBuilder where = new StringBuilder("WHERE 1=1 ");
        if (seriesId != null && !seriesId.trim().isEmpty()) {
            where.append("AND t.series_id=? "); parameters.add(seriesId.trim());
        }
        if (!query.isEmpty()) {
            where.append("AND CHARINDEX(LOWER(?),LOWER(t.name))>0 "); parameters.add(query);
        }
        if ("STANDALONE".equals(filter)) where.append("AND NULLIF(LTRIM(RTRIM(t.series_id)),'') IS NULL ");
        else if ("SERIES".equals(filter)) where.append("AND NULLIF(LTRIM(RTRIM(t.series_id)),'') IS NOT NULL ");
        else if (!"ALL".equals(filter)) {
            where.append("AND (CASE WHEN t.status IN ('COMPLETED','FINISHED') THEN 'COMPLETED' ")
                 .append("WHEN t.status IN ('ONGOING','IN_PROGRESS') OR ISNULL(ms.completed_matches,0)>0 THEN 'IN_PROGRESS' ELSE 'INCOMING' END)=? ");
            parameters.add(filter);
        }
        String order = seriesId == null || seriesId.trim().isEmpty()
                ? "t.created_at DESC,t.id DESC" : "t.tournament_index_in_series DESC,t.created_at DESC,t.id DESC";
        try (Connection conn = new DBContext().getConnection()) {
            int count;
            boolean statusFilter = Set.of("INCOMING", "IN_PROGRESS", "COMPLETED").contains(filter);
            String countFrom = statusFilter ? TOURNAMENT_FROM_QUERY : "FROM tournaments t ";
            try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) " + countFrom + where + "OPTION (MAXDOP 1)")) {
                for (int i=0; i<parameters.size(); i++) ps.setString(i+1, parameters.get(i));
                try (ResultSet rs=ps.executeQuery()) { rs.next(); count=rs.getInt(1); }
            }
            int page = Math.min(requestedPage, Math.max(1, (count + 24) / 25));
            List<Tournament> rows = new ArrayList<>();
            if (count > 0) {
                String sql = TOURNAMENT_SELECT_QUERY + TOURNAMENT_FROM_QUERY + where + "ORDER BY " + order
                        + " OFFSET ? ROWS FETCH NEXT 25 ROWS ONLY OPTION (MAXDOP 1)";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    for (int i=0; i<parameters.size(); i++) ps.setString(i+1, parameters.get(i));
                    ps.setInt(parameters.size()+1, (page-1)*25);
                    try (ResultSet rs=ps.executeQuery()) {
                        while (rs.next()) { Tournament tournament=mapTournamentFromResultSet(rs); if (tournament!=null) rows.add(tournament); }
                    }
                }
            }
            return new TournamentPage(rows,count,page,filter,query);
        } catch (Exception e) { throw new IllegalStateException("Cannot load tournament page",e); }
    }

    public List<Tournament> getAllTournaments() {
        List<Tournament> list = new ArrayList<>();
        // These small dashboard result sets do not need parallel plans or their larger memory grants.
        String sql = TOURNAMENT_SELECT_QUERY + TOURNAMENT_FROM_QUERY + "ORDER BY t.created_at DESC OPTION (MAXDOP 1)";
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
        return getTournamentById(id, true);
    }

    public Tournament getTournamentById(String id, boolean useCache) {
        if (id == null || id.trim().isEmpty()) return null;
        Tournament cached = useCache ? TOURNAMENT_BY_ID_CACHE.get(id.trim()) : null;
        if (cached != null) return cached;

        String sql = TOURNAMENT_SELECT_QUERY + TOURNAMENT_FROM_QUERY + "WHERE t.id = ? OPTION (MAXDOP 1)";
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
        String sql = TOURNAMENT_SELECT_QUERY + TOURNAMENT_FROM_QUERY + "WHERE t.series_id = ? ORDER BY t.tournament_index_in_series ASC, t.created_at ASC OPTION (MAXDOP 1)";
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

    public String getStageFormat(String tournamentId, int stageOrder) {
        List<String> formats = getStageFormats(tournamentId);
        if (stageOrder > 0 && stageOrder <= formats.size()) return formats.get(stageOrder - 1);
        Tournament tournament = getTournamentById(tournamentId, false);
        return tournament == null ? null : tournament.getFormat();
    }

    public boolean updateTournamentStage1Status(String tournamentId, String stage1Status) {
        return saveStage1Status(tournamentId, stage1Status);
    }

    public boolean saveGroupAssignments(String tournamentId, String groupAssignmentsJson) {
        if (tournamentId == null || tournamentId.trim().isEmpty()) return false;
        clearTournamentCache(tournamentId);
        String sql = "UPDATE tournaments SET group_assignments = ? WHERE id = ? AND ISNULL(stage1_status,'PENDING') NOT IN ('LOCKED','COMPLETED') AND ISNULL(status,'DRAFT') NOT IN ('COMPLETED','FINISHED')";
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
        Tournament tournament = getTournamentById(tournamentId, false);
        if (tournament != null && "MULTI_STAGE".equalsIgnoreCase(tournament.getTournamentType())
                && ("SINGLE_ELIMINATION".equalsIgnoreCase(tournament.getFormat())
                    || "DOUBLE_ELIMINATION".equalsIgnoreCase(tournament.getFormat())
                    || "GSL".equalsIgnoreCase(tournament.getFormat())
                    || "SWISS".equalsIgnoreCase(tournament.getFormat())
                    || "SWISS_LITE".equalsIgnoreCase(tournament.getFormat())
                    || "ROUND_ROBIN".equalsIgnoreCase(tournament.getFormat())
                    || "GROUP_STAGE".equalsIgnoreCase(tournament.getFormat()))) {
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
    /** Stage 2 availability and draw identity, always read fresh for open browser tabs. */
    public String getStageAccessStateJson(String id) {
        String sql="SELECT t.tournament_type,t.stage1_status,t.stage2_teams,"
            + "(SELECT TOP 1 format FROM tournament_stages WHERE tournament_id=t.id AND stage_order=1),"
            + "(SELECT MIN(m.id) FROM matches m JOIN tournament_stages s ON s.id=m.stage_id AND s.tournament_id=m.tournament_id WHERE m.tournament_id=t.id AND s.stage_order=2) "
            + "FROM tournaments t WHERE t.id=?";
        try(Connection conn=new DBContext().getConnection();PreparedStatement ps=conn.prepareStatement(sql)) {
            ps.setString(1,id);try(ResultSet rs=ps.executeQuery()) {
                if(!rs.next()) throw new IllegalArgumentException("Không tìm thấy giải.");
                boolean ready="MULTI_STAGE".equalsIgnoreCase(rs.getString(1))
                    && ("LOCKED".equalsIgnoreCase(rs.getString(2)) || "COMPLETED".equalsIgnoreCase(rs.getString(2)))
                    && rs.getString(3)!=null && !rs.getString(3).trim().equals("[]");
                return "{\"ready\":"+ready+",\"stage1Status\":\""+cutEscape(rs.getString(2))+"\",\"stage1Format\":\""+cutEscape(rs.getString(4))+"\",\"generation\":\""+cutEscape(rs.getString(5))+"\"}";
            }
        } catch(Exception e){throw new IllegalStateException("Cannot load stage state",e);}
    }

    public static void assertStage2Ready(Connection conn, String id, int stage) throws java.sql.SQLException {
        if(stage<2) return;
        try(PreparedStatement ps=conn.prepareStatement("SELECT tournament_type,stage1_status,stage2_teams FROM tournaments WITH (UPDLOCK,HOLDLOCK) WHERE id=?")) {
            ps.setString(1,id);try(ResultSet rs=ps.executeQuery()) {
                if(!rs.next() || !"MULTI_STAGE".equalsIgnoreCase(rs.getString(1))
                    || !("LOCKED".equalsIgnoreCase(rs.getString(2)) || "COMPLETED".equalsIgnoreCase(rs.getString(2)))
                    || rs.getString(3)==null || rs.getString(3).trim().equals("[]"))
                    throw new IllegalStateException("Stage 1 chưa được xác nhận. Stage 2 hiện không khả dụng.");
            }
        }
    }

    private boolean unlockStage1AndInvalidateStage2(String id) {
        try(Connection conn=new DBContext().getConnection()) {
            conn.setAutoCommit(false);lockCutTournament(conn,id);
            if (!service.RollingWindowPointService.getInstance().refreshForTournamentTransition(conn, id, "DRAFT")) return false;
            String stages="SELECT id FROM tournament_stages WHERE tournament_id=? AND stage_order>1";
            for(String sql:new String[]{
                "UPDATE matches SET next_match_id=NULL,loser_next_match_id=NULL WHERE tournament_id=? AND stage_id IN ("+stages+")",
                "DELETE FROM matches WHERE tournament_id=? AND stage_id IN ("+stages+")",
                "DELETE FROM group_teams WHERE group_id IN (SELECT id FROM groups WHERE stage_id IN ("+stages+"))",
                "DELETE FROM groups WHERE stage_id IN ("+stages+")"}) {
                try(PreparedStatement ps=conn.prepareStatement(sql)) {
                    ps.setString(1,id);if(sql.contains("tournament_id=? AND stage_id"))ps.setString(2,id);ps.executeUpdate();
                }
            }
            try(PreparedStatement ps=conn.prepareStatement("UPDATE tournament_stages SET status='PENDING' WHERE tournament_id=? AND stage_order>1")){ps.setString(1,id);ps.executeUpdate();}
            try(PreparedStatement ps=conn.prepareStatement("UPDATE tournaments SET stage1_status='PENDING',stage2_teams=NULL,status='DRAFT',champion_name=NULL WHERE id=?")){ps.setString(1,id);ps.executeUpdate();}
            conn.commit();clearTournamentCache(id);service.RollingWindowPointService.clearAllCaches();return true;
        }catch(Exception e){service.RollingWindowPointService.clearAllCaches();e.printStackTrace();return false;}
    }

    public boolean saveStage1Status(String tournamentId, String status) {
        if (tournamentId == null || tournamentId.trim().isEmpty()) return false;
        // Validate allowed values
        if (!"PENDING".equals(status) && !"LOCKED".equals(status) && !"COMPLETED".equals(status)) return false;
        if ("PENDING".equals(status)) {
            Tournament current=getTournamentById(tournamentId, false);
            if(current!=null && "MULTI_STAGE".equalsIgnoreCase(current.getTournamentType()))
                return unlockStage1AndInvalidateStage2(tournamentId);
        }
        if (!"PENDING".equals(status)) {
            Tournament tournament = getTournamentById(tournamentId, false);
            if (tournament != null && "MULTI_STAGE".equalsIgnoreCase(tournament.getTournamentType())
                    && ("SINGLE_ELIMINATION".equalsIgnoreCase(tournament.getFormat())
                        || "DOUBLE_ELIMINATION".equalsIgnoreCase(tournament.getFormat())
                        || "GSL".equalsIgnoreCase(tournament.getFormat())
                        || "SWISS".equalsIgnoreCase(tournament.getFormat())
                        || "SWISS_LITE".equalsIgnoreCase(tournament.getFormat())
                    || "ROUND_ROBIN".equalsIgnoreCase(tournament.getFormat())
                    || "GROUP_STAGE".equalsIgnoreCase(tournament.getFormat()))) {
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

            try (PreparedStatement ps = conn.prepareStatement("UPDATE dst SET top_seed_count=src.top_seed_count, classic_draw=src.classic_draw FROM tournaments dst CROSS JOIN tournaments src WHERE src.id=? AND dst.id=?")) {
                ps.setString(1, sourceTournamentId.trim()); ps.setString(2, newId); ps.executeUpdate();
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

