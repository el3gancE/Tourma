package service;

import dao.DBContext;
import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Resolves actual elimination rounds and final standings from persisted matches. */
public class TournamentPlacementService {
    private static final TournamentPlacementService INSTANCE = new TournamentPlacementService();
    private static final Map<String, Map<String, PlacementResult>> RESULTS = new ConcurrentHashMap<>();
    public static TournamentPlacementService getInstance() { return INSTANCE; }
    public static void clearCaches() { RESULTS.clear(); }

    public static class PlacementResult {
        public final int rank;
        public final String pointKey;
        private final String achievement;
        public PlacementResult(int rank, String pointKey) { this(rank, pointKey, null); }
        public PlacementResult(int rank, String pointKey, String achievement) {
            this.rank = rank; this.pointKey = pointKey; this.achievement = achievement;
        }
        public String getAchievement() {
            if (achievement != null) return achievement;
            if (pointKey.startsWith("swiss_")) return pointKey.substring(6);
            if ("stage1_eliminated".equals(pointKey)) return "Vòng Bảng";
            if (rank == 1) return "Vô Địch";
            if (rank == 2) return "Á Quân";
            if (rank <= 4) return "Bán Kết";
            if (rank <= 8) return "Tứ Kết";
            int size=16; while(size<rank) size*=2;
            return "Round of " + size;
        }
    }
    private static class TeamInfo {
        String id, name, norm;
        int seed;
    }
    private static class MatchInfo {
        String id, groupId, bracketType, team1Id, team2Id, winnerId, loserId, status;
        int stageOrder, roundNumber;
        Integer score1, score2;
        boolean bye;
    }
    private static class StageInfo {
        String format;
        int winPoints = 3, drawPoints = 1, lossPoints = 0;
    }
    private static class Context {
        boolean multi;
        int cut;
        Map<String, TeamInfo> teams = new LinkedHashMap<>();
        Map<Integer, StageInfo> stages = new TreeMap<>();
        List<MatchInfo> matches = new ArrayList<>();
        Set<String> qualified = new HashSet<>();
        Map<String, PlacementResult> results = new HashMap<>();
    }

    public Map<String, Map<String, Integer>> preloadSeriesPlacements(String seriesId) {
        Map<String, Map<String, Integer>> result = new HashMap<>();
        if (seriesId == null || seriesId.trim().isEmpty()) return result;
        List<String> ids = new ArrayList<>();
        try (Connection conn = new DBContext().getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT id FROM tournaments WHERE series_id = ?")) {
            ps.setString(1, seriesId.trim());
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) ids.add(rs.getString(1)); }
        } catch (Exception e) { throw new IllegalStateException("Cannot load series results", e); }
        for (String id : ids) {
            Map<String, Integer> placements = getTournamentPlacements(id);
            result.put(id, placements);
            RollingWindowPointService.getInstance().putCachedTournamentPlacements(id, placements);
        }
        return result;
    }

    public Map<String, Integer> getTournamentPlacements(String tournamentId) {
        Map<String, Integer> ranks = new HashMap<>();
        getTournamentResults(tournamentId).forEach((key, result) -> ranks.put(key, result.rank));
        return ranks;
    }

    public Map<String, PlacementResult> getTournamentResults(String tournamentId) {
        if (tournamentId == null || tournamentId.trim().isEmpty()) return Collections.emptyMap();
        return RESULTS.computeIfAbsent(tournamentId.trim(), this::loadResults);
    }

    public Map<String, PlacementResult> getFreshTournamentResults(String tournamentId) {
        Map<String, PlacementResult> results = loadResults(tournamentId);
        RESULTS.put(tournamentId, results);
        return results;
    }

    private Map<String, PlacementResult> loadResults(String id) {
        try (Connection conn = new DBContext().getConnection()) {
            Context ctx = new Context();
            String config = "CASE WHEN ISJSON(t.multi_stage_config)=1 THEN t.multi_stage_config ELSE '{}' END";
            String sql = "SELECT t.tournament_type, t.advancing_seats_count, s.stage_order, s.format, "
                + "JSON_VALUE(" + config + ", '$.stage1Config.winPoints') AS w1, "
                + "JSON_VALUE(" + config + ", '$.stage1Config.drawPoints') AS d1, "
                + "JSON_VALUE(" + config + ", '$.stage1Config.lossPoints') AS l1, "
                + "JSON_VALUE(" + config + ", '$.stage2Config.winPoints') AS w2, "
                + "JSON_VALUE(" + config + ", '$.stage2Config.drawPoints') AS d2, "
                + "JSON_VALUE(" + config + ", '$.stage2Config.lossPoints') AS l2 "
                + "FROM tournaments t LEFT JOIN tournament_stages s ON s.tournament_id=t.id WHERE t.id=?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        ctx.multi = "MULTI_STAGE".equalsIgnoreCase(rs.getString("tournament_type"));
                        ctx.cut = rs.getInt("advancing_seats_count");
                        int order = rs.getInt("stage_order");
                        if (order < 1) continue;
                        StageInfo stage = new StageInfo();
                        stage.format = rs.getString("format");
                        String suffix = order == 1 ? "1" : "2";
                        stage.winPoints = intOr(rs.getString("w" + suffix), 3);
                        stage.drawPoints = intOr(rs.getString("d" + suffix), 1);
                        stage.lossPoints = intOr(rs.getString("l" + suffix), 0);
                        ctx.stages.put(order, stage);
                    }
                }
            }
            try (PreparedStatement ps = conn.prepareStatement("SELECT id, raw_name, normalized_name, original_seed FROM teams WHERE tournament_id=? ORDER BY original_seed,id")) {
                ps.setString(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        TeamInfo team = new TeamInfo();
                        team.id = rs.getString("id"); team.name = rs.getString("raw_name");
                        team.norm = rs.getString("normalized_name"); team.seed = rs.getInt("original_seed");
                        ctx.teams.put(team.id, team);
                    }
                }
            }
            try (PreparedStatement ps = conn.prepareStatement("SELECT m.*, ISNULL(s.stage_order,1) AS stage_order FROM matches m LEFT JOIN tournament_stages s ON s.id=m.stage_id WHERE m.tournament_id=? ORDER BY s.stage_order,m.round_number,m.match_order,m.id")) {
                ps.setString(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        MatchInfo m = new MatchInfo();
                        m.id=rs.getString("id"); m.groupId=rs.getString("group_id");
                        m.stageOrder=rs.getInt("stage_order"); m.roundNumber=rs.getInt("round_number");
                        m.bracketType=rs.getString("bracket_type"); m.status=rs.getString("status");
                        m.team1Id=rs.getString("team1_id"); m.team2Id=rs.getString("team2_id");
                        m.winnerId=rs.getString("winner_id"); m.loserId=rs.getString("loser_id");
                        m.score1=(Integer)rs.getObject("score1"); m.score2=(Integer)rs.getObject("score2");
                        m.bye=rs.getBoolean("is_bye");
                        if (m.winnerId == null && finished(m) && m.score1 != null && m.score2 != null && !m.score1.equals(m.score2)) {
                            m.winnerId=m.score1 > m.score2 ? m.team1Id : m.team2Id;
                        }
                        if (m.winnerId != null && m.loserId == null) {
                            m.loserId=m.winnerId.equals(m.team1Id) ? m.team2Id : m.team1Id;
                        }
                        ctx.matches.add(m);
                        if (m.stageOrder == 2) {
                            if (ctx.teams.containsKey(m.team1Id)) ctx.qualified.add(m.team1Id);
                            if (ctx.teams.containsKey(m.team2Id)) ctx.qualified.add(m.team2Id);
                        }
                    }
                }
            }
            if (ctx.multi) {
                try (PreparedStatement ps = conn.prepareStatement("SELECT j.team_id FROM tournaments t CROSS APPLY OPENJSON(CASE WHEN ISJSON(t.stage2_teams)=1 THEN t.stage2_teams ELSE '[]' END) WITH (team_id nvarchar(100) '$.id') j WHERE t.id=?")) {
                    ps.setString(1, id);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) if (ctx.teams.containsKey(rs.getString(1))) ctx.qualified.add(rs.getString(1));
                    }
                }
            }
            for (Map.Entry<Integer, StageInfo> entry : ctx.stages.entrySet()) {
                int order=entry.getKey(); StageInfo stage=entry.getValue();
                List<MatchInfo> games=new ArrayList<>();
                for (MatchInfo m:ctx.matches) if(m.stageOrder==order) games.add(m);
                boolean cut=ctx.multi && order==1;
                String format=stage.format==null ? "" : stage.format.toUpperCase(Locale.ROOT);
                if ("SINGLE_ELIMINATION".equals(format)) knockout(ctx,games,cut,false,false,order);
                else if ("DOUBLE_ELIMINATION".equals(format)) knockout(ctx,games,cut,true,false,order);
                else if ("GSL".equals(format) && cut) knockout(ctx,games,true,true,true,order);
                else if (format.contains("SWISS") && cut) swiss(ctx,games);
                else if ("ROUND_ROBIN".equals(format)) roundRobin(ctx,games,stage,cut,order);
                else if ("GROUP_STAGE".equals(format) && cut) groups(ctx,games,conn,id);
            }
            return Collections.unmodifiableMap(ctx.results);
        } catch (Exception e) { throw new IllegalStateException("Cannot calculate tournament results: " + id, e); }
    }

    private static void knockout(Context ctx,List<MatchInfo> games,boolean cut,boolean de,boolean gsl,int order) {
        Map<String,List<MatchInfo>> brackets=new LinkedHashMap<>();
        for(MatchInfo m:games) brackets.computeIfAbsent(gsl ? String.valueOf(m.groupId) : "main", k->new ArrayList<>()).add(m);
        int groupCount=Math.max(1,brackets.size());
        for(List<MatchInfo> bracket:brackets.values()) {
            int lastLowerRound=0;
            boolean groupFinal=false;
            for (MatchInfo m : bracket) if (lower(m)) lastLowerRound=Math.max(lastLowerRound,m.roundNumber);
            for (MatchInfo m : bracket) if (grandFinal(m)) groupFinal=true;
            Set<String> participants=participants(bracket);
            int count=gsl ? participants.size() : order==1 ? ctx.teams.size() : Math.max(ctx.cut,participants.size());
            int size=gsl ? Math.max(4,powerOfTwo(count)) : powerOfTwo(count);
            int ubRounds=log2(size), lbRounds=Math.max(0,2*(ubRounds-1));
            for(MatchInfo m:bracket) {
                if(m.bye || m.winnerId==null || !ctx.teams.containsKey(m.winnerId)) continue;
                String type=type(m);
                if("THIRD_PLACE".equals(type)) {
                    if(!cut) {put(ctx,m.winnerId,3,"3");put(ctx,m.loserId,4,"4");}
                    continue;
                }
                if(grandFinal(m)) {
                    if(cut && gsl) put(ctx,m.loserId,groupCount+1,"s1_gsl_final","Group Qualification");
                    else if(!cut) {put(ctx,m.winnerId,1,"1");put(ctx,m.loserId,2,"2");}
                    continue;
                }
                if(de) {
                    if(!lower(m)) continue; // An upper bracket loss is not elimination.
                    int distance=lbRounds-m.roundNumber;
                    if(distance<0) throw new IllegalStateException("Invalid lower bracket round: "+m.id);
                    int rank=deRank(distance);
                    String key=cut ? (gsl ? "s1_gsl_lb_r" : "s1_lb_r")+m.roundNumber : rangeKey(rank,deWidth(distance));
                    String achievement=cut && !groupFinal && m.roundNumber==lastLowerRound ? "Loser's Qualification"
                        : (!cut || groupFinal) && distance==0 ? "Losers Final"
                        : (!cut || groupFinal) && distance==1 ? "Losers Semi-final" : "Loser's Round " + m.roundNumber;
                    put(ctx,m.loserId,gsl ? groupCount*(rank-1)+1 : rank,key,achievement);
                } else {
                    int remaining=ubRounds-m.roundNumber;
                    if(remaining<0) throw new IllegalStateException("Invalid elimination round: "+m.id);
                    int rank=(1<<remaining)+1;
                    put(ctx,m.loserId,rank,rangeKey(rank,Math.max(1,1<<remaining)));
                    if(!cut && m.roundNumber==ubRounds) put(ctx,m.winnerId,1,"1");
                }
            }
        }
    }

    private static int deRank(int distance) {
        return (distance%2==0 ? 1<<(distance/2+1) : 3*(1<<((distance-1)/2)))+1;
    }
    private static int deWidth(int distance) {return 1<<(distance/2);}
    private static String rangeKey(int rank,int width) {return width<=1 ? String.valueOf(rank) : rank+"-"+(rank+width-1);}

    private static void swiss(Context ctx,List<MatchInfo> games) {
        Map<String,int[]> records=new HashMap<>();
        for(MatchInfo m:games) {
            if(m.winnerId==null) continue;
            records.computeIfAbsent(m.winnerId,k->new int[2])[0]++;
            if(!m.bye && m.loserId!=null) records.computeIfAbsent(m.loserId,k->new int[2])[1]++;
        }
        for(Map.Entry<String,int[]> entry:records.entrySet()) {
            int wins=entry.getValue()[0],losses=entry.getValue()[1];
            if(losses!=3 || wins>2) continue;
            int rank=ctx.cut+1;
            for(int[] other:records.values()) if(other[1]==3 && other[0]>wins) rank++;
            put(ctx,entry.getKey(),rank,"swiss_"+wins+"-3");
        }
    }

    private static class RRRow {
        String id; int points,scored,conceded,seed;
    }
    private static void roundRobin(Context ctx,List<MatchInfo> games,StageInfo stage,boolean cut,int order) {
        if(games.isEmpty() || !complete(games,true)) return;
        Map<String,RRRow> rows=new LinkedHashMap<>();
        Set<String> ids=order==1 ? ctx.teams.keySet() : participants(games);
        for(String id:ids) {
            TeamInfo team=ctx.teams.get(id); if(team==null) continue;
            RRRow row=new RRRow();row.id=id;row.seed=team.seed;rows.put(id,row);
        }
        for(MatchInfo m:games) {
            if(m.bye) continue;
            RRRow a=rows.get(m.team1Id),b=rows.get(m.team2Id);
            if(a==null||b==null) continue;
            a.scored+=m.score1;a.conceded+=m.score2;b.scored+=m.score2;b.conceded+=m.score1;
            a.points+=m.score1>m.score2?stage.winPoints:m.score1.equals(m.score2)?stage.drawPoints:stage.lossPoints;
            b.points+=m.score2>m.score1?stage.winPoints:m.score1.equals(m.score2)?stage.drawPoints:stage.lossPoints;
        }
        List<RRRow> sorted=new ArrayList<>(rows.values());
        sorted.sort(Comparator.comparingInt((RRRow r)->r.points).reversed()
            .thenComparing(Comparator.comparingInt((RRRow r)->r.scored-r.conceded).reversed())
            .thenComparing(Comparator.comparingInt((RRRow r)->r.scored).reversed())
            .thenComparingInt(r->r.seed).thenComparing(r->r.id));
        for(int i=0;i<sorted.size();i++) {
            if(cut) {
                boolean eliminated=ctx.qualified.size()==ctx.cut ? !ctx.qualified.contains(sorted.get(i).id) : i>=ctx.cut;
                if(eliminated) put(ctx,sorted.get(i).id,ctx.cut+1,"stage1_eliminated");
            }
            else put(ctx,sorted.get(i).id,i+1,String.valueOf(i+1));
        }
    }

    private static void groups(Context ctx,List<MatchInfo> games,Connection conn,String id)throws SQLException {
        if(games.isEmpty() || !complete(games,true)) return;
        Map<String,Map<String,Integer>> groups=new LinkedHashMap<>();
        try(PreparedStatement ps=conn.prepareStatement("SELECT g.id, gt.team_id, gt.rank_in_group FROM groups g JOIN tournament_stages s ON s.id=g.stage_id JOIN group_teams gt ON gt.group_id=g.id WHERE s.tournament_id=? AND s.stage_order=1")) {
            ps.setString(1,id);
            try(ResultSet rs=ps.executeQuery()) {while(rs.next()) groups.computeIfAbsent(rs.getString(1),k->new HashMap<>()).put(rs.getString(2),rs.getInt(3));}
        }
        if (ctx.cut > 0 && ctx.qualified.size() == ctx.cut) {
            for (Map<String,Integer> group : groups.values()) for (String teamId : group.keySet()) {
                if (!ctx.qualified.contains(teamId)) put(ctx, teamId, ctx.cut+1, "stage1_eliminated");
            }
            return;
        }
        if(groups.isEmpty()||ctx.cut<=0||ctx.cut%groups.size()!=0) return;
        int quota=ctx.cut/groups.size();
        for(Map<String,Integer> group:groups.values()) for(Map.Entry<String,Integer> row:group.entrySet()) {
            if(row.getValue()>quota) put(ctx,row.getKey(),ctx.cut+1,"stage1_eliminated");
        }
    }

    private static void put(Context ctx,String id,int rank,String key) {
        put(ctx,id,rank,key,null);
    }
    private static void put(Context ctx,String id,int rank,String key,String achievement) {
        TeamInfo team=ctx.teams.get(id);if(team==null) return;
        PlacementResult result=new PlacementResult(rank,key,achievement);
        ctx.results.put(id,result);
        if(team.name!=null) ctx.results.put(team.name.trim().toLowerCase(Locale.ROOT),result);
        if(team.norm!=null) ctx.results.put(team.norm.trim().toLowerCase(Locale.ROOT),result);
    }
    private static Set<String> participants(List<MatchInfo> games) {
        Set<String> ids=new HashSet<>();
        for(MatchInfo m:games) {if(m.team1Id!=null)ids.add(m.team1Id);if(m.team2Id!=null)ids.add(m.team2Id);}
        return ids;
    }
    private static boolean complete(List<MatchInfo> games,boolean scoresRequired) {
        for(MatchInfo m:games) {
            if(m.bye) continue;
            if(m.team1Id==null||m.team2Id==null||!finished(m)) return false;
            if(scoresRequired&&(m.score1==null||m.score2==null)) return false;
        }
        return true;
    }
    private static boolean finished(MatchInfo m) {return "FINISHED".equalsIgnoreCase(m.status)||"COMPLETED".equalsIgnoreCase(m.status)||"DONE".equalsIgnoreCase(m.status);}
    private static String type(MatchInfo m) {return m.bracketType==null?"":m.bracketType.toUpperCase(Locale.ROOT);}
    private static boolean lower(MatchInfo m) {return Arrays.asList("LOSER_BRACKET","LOWER","LB").contains(type(m));}
    private static boolean grandFinal(MatchInfo m) {return type(m).contains("GRAND_FINAL")||"GF".equals(type(m));}
    private static int powerOfTwo(int n) {int size=2;while(size<n)size*=2;return size;}
    private static int log2(int n) {return Integer.numberOfTrailingZeros(n);}
    private static int intOr(String value,int fallback) {try{return Integer.parseInt(value);}catch(Exception ignored){return fallback;}}
}
