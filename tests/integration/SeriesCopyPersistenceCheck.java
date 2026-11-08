import dao.DBContext;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.time.Duration;
import java.util.*;

/** Local HTTP/DB regression check. Creates and removes only its own empty draft. */
public class SeriesCopyPersistenceCheck {
    static final String BASE = "http://127.0.0.1:8080/Tourma";
    static void require(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    static String snapshot(Connection c, String id) throws Exception {
        try (PreparedStatement p=c.prepareStatement("SELECT tournament_type, tier_name, series_reward_points, series_points_config, multi_stage_config, advancing_seats_count FROM tournaments WHERE id=?")) {
            p.setString(1,id); try(ResultSet r=p.executeQuery()) {
                require(r.next(),"Tournament missing");
                List<String> v=new ArrayList<>(); for(int i=1;i<=6;i++)v.add(r.getString(i));
                return v.toString();
            }
        }
    }
    static List<String> rows(Connection c, String sql, String id) throws Exception {
        List<String> values=new ArrayList<>();
        try(PreparedStatement p=c.prepareStatement(sql)) {p.setString(1,id);try(ResultSet r=p.executeQuery()){while(r.next())values.add(r.getString(1));}}
        return values;
    }
    public static void main(String[] args) throws Exception {
        String target="COPY_CHECK_"+UUID.randomUUID().toString().substring(0,8);
        HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        try(Connection c=new DBContext().getConnection()) {
            String source;
            try(Statement p=c.createStatement();ResultSet r=p.executeQuery("SELECT TOP 1 t.id FROM tournaments t WHERE t.series_id IS NOT NULL ORDER BY t.created_at DESC")) {
                require(r.next(),"No configured Series source available");source=r.getString(1);
            }
            String baseSource=source;
            source="COPY_SOURCE_"+UUID.randomUUID().toString().substring(0,8);
            try {
                try(PreparedStatement p=c.prepareStatement("INSERT INTO tournaments(id,series_id,name,tournament_type,tier_name,tournament_index_in_series,phase_number,status,advancing_seats_count,series_reward_points,series_points_config,multi_stage_config) SELECT ?,series_id,'Temporary copy source','MULTI_STAGE','A',(SELECT ISNULL(MAX(x.tournament_index_in_series),0)+1 FROM tournaments x WHERE x.series_id=t.series_id),phase_number,'DRAFT',4,75,?,? FROM tournaments t WHERE id=?")) {
                    p.setString(1,source);p.setString(2,"{\"1\":100,\"2\":60}");
                    p.setString(3,"{\"tournamentType\":\"MULTI_STAGE\",\"stage1Format\":\"ROUND_ROBIN\",\"stage2Format\":\"SINGLE_ELIMINATION\",\"stage1Config\":{\"advanceCount\":4,\"winPoints\":5,\"drawPoints\":2,\"lossPoints\":0,\"legsCount\":2}}");
                    p.setString(4,baseSource);require(p.executeUpdate()==1,"Cannot create source");
                }
                for(int stage=1;stage<=2;stage++)try(PreparedStatement p=c.prepareStatement("INSERT INTO tournament_stages(id,tournament_id,stage_order,stage_name,format) VALUES(?,?,?,?,?)")) {
                    p.setString(1,source+"_"+stage);p.setString(2,source);p.setInt(3,stage);p.setString(4,"Stage "+stage);p.setString(5,stage==1?"ROUND_ROBIN":"SINGLE_ELIMINATION");p.executeUpdate();
                }
                boolean elo;
                try(ResultSet columns=c.getMetaData().getColumns(c.getCatalog(),null,"tournament_placement_points","elo_weight")){elo=columns.next();}
                try(PreparedStatement p=c.prepareStatement("INSERT INTO tournament_placement_points(id,tournament_id,rank_position,points_awarded"+(elo?",elo_weight":"")+") VALUES(?,?,1,100"+(elo?",1.0":"")+")")) {
                    p.setString(1,source+"_points");p.setString(2,source);p.executeUpdate();
                }
                String sourceBefore=snapshot(c,source);
                try(PreparedStatement p=c.prepareStatement("INSERT INTO tournaments(id,series_id,name,tournament_type,tier_name,tournament_index_in_series,phase_number,status) SELECT ?,series_id,'Temporary copy regression check','SINGLE_STAGE','D',(SELECT ISNULL(MAX(x.tournament_index_in_series),0)+1 FROM tournaments x WHERE x.series_id=t.series_id),phase_number,'DRAFT' FROM tournaments t WHERE id=?")) {
                    p.setString(1,target);p.setString(2,source);require(p.executeUpdate()==1,"Cannot create fixture");
                }
                String body="action=copyConfiguration&tournamentId="+URLEncoder.encode(target,StandardCharsets.UTF_8)+"&sourceTournamentId="+URLEncoder.encode(source,StandardCharsets.UTF_8);
                HttpResponse<String> response=http.send(HttpRequest.newBuilder(URI.create(BASE+"/rolling/tournament-teams")).timeout(Duration.ofSeconds(30)).header("Content-Type","application/x-www-form-urlencoded;charset=UTF-8").POST(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
                require(response.statusCode()==200 && response.body().contains("\"status\":\"success\""),"Copy HTTP failed: "+response.statusCode()+" "+response.body().substring(0,Math.min(300,response.body().length())));
                require(sourceBefore.equals(snapshot(c,target)),"Format, tier or points not persisted exactly");
                String stageSql="SELECT format FROM tournament_stages WHERE tournament_id=? ORDER BY stage_order";
                require(rows(c,stageSql,source).equals(rows(c,stageSql,target)),"Stage formats differ");
                String pointsSql="SELECT CONCAT(rank_position,':',points_awarded) FROM tournament_placement_points WHERE tournament_id=? ORDER BY rank_position";
                require(rows(c,pointsSql,source).equals(rows(c,pointsSql,target)),"Placement points differ");
                require(sourceBefore.equals(snapshot(c,source)),"Source was modified");
                HttpResponse<String> page=http.send(HttpRequest.newBuilder(URI.create(BASE+"/rolling/tournament-teams?id="+target)).timeout(Duration.ofSeconds(45)).GET().build(),HttpResponse.BodyHandlers.ofString());
                require(page.statusCode()==200 && page.body().contains("configureFormatForm") && !page.body().contains("JasperException"),"Reload did not render editor");
                System.out.println("PASS: HTTP copy persists stages, rules, tier, reward/placement points; reload renders; source unchanged");
                if (args.length > 0 && "preview".equals(args[0])) {
                    for(int i=1;i<=8;i++)try(PreparedStatement p=c.prepareStatement("INSERT INTO teams(id,tournament_id,raw_name,normalized_name,original_seed,status) VALUES(?,?,?,?,?,'ACTIVE')")) {
                        p.setString(1,source+"_team_"+i);p.setString(2,source);p.setString(3,"Preview team "+i);p.setString(4,"preview team "+i);p.setInt(5,i);p.executeUpdate();
                    }
                    System.out.println("PREVIEW "+BASE+"/rolling/tournament-teams?id="+target+"&copiedFrom="+source+"&targetTeamCount=8");
                    System.out.println("Press Enter after visual inspection to clean up both fixtures.");
                    System.in.read();
                }
            } finally {
                for(String table:List.of("tournament_placement_points","teams","tournament_stages","tournaments")) {
                    String key=table.equals("tournaments")?"id":"tournament_id";
                    for(String id:List.of(target,source)) try(PreparedStatement p=c.prepareStatement("DELETE FROM "+table+" WHERE "+key+"=?")){p.setString(1,id);p.executeUpdate();}
                }
                System.out.println("Removed temporary copy fixture "+target);
            }
        }
    }
}
