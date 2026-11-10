package controller;
import java.sql.*;
import java.util.*;
/** Compare streaming profile stats with the original SQL aggregate; read-only. */
public class ProfileMatchStatsCheck {
 public static void main(String[] args) throws Exception {
  if(args.length==0) { checkAggregation(); return; }
  if(args.length!=1)throw new IllegalArgumentException("Pass a Series ID");
  try(Connection c=new dao.DBContext().getConnection()) {
   Map<String,int[]> expected=baseline(c,args[0]);
   Map<String,int[]> actual=TeamProfileServlet.loadMatchStats(c,args[0]);
   if(!expected.keySet().equals(actual.keySet()))throw new AssertionError("Team IDs differ");
   for(String key:expected.keySet())if(!Arrays.equals(expected.get(key),actual.get(key)))throw new AssertionError("Stats differ for "+key);
   System.out.println("PASS: profile win/loss totals match the original aggregate for "+actual.size()+" teams");
  }
 }

 static void checkAggregation() throws Exception {
  String[][] rows={{"a","b","a"},{"a","b","b"},{"a","c",null},{"c","b","c"},{"d","e",null}};
  int[] cursor={-1};
  ResultSet rs=(ResultSet)java.lang.reflect.Proxy.newProxyInstance(ProfileMatchStatsCheck.class.getClassLoader(),new Class[]{ResultSet.class},(p,m,a)-> {
   if(m.getName().equals("next")) return ++cursor[0]<rows.length;
   if(m.getName().equals("getString")) return rows[cursor[0]][a[0].equals("team1_id")?0:a[0].equals("team2_id")?1:2];
   return null;
  });
  PreparedStatement ps=(PreparedStatement)java.lang.reflect.Proxy.newProxyInstance(ProfileMatchStatsCheck.class.getClassLoader(),new Class[]{PreparedStatement.class},(p,m,a)->m.getName().equals("executeQuery")?rs:null);
  Connection conn=(Connection)java.lang.reflect.Proxy.newProxyInstance(ProfileMatchStatsCheck.class.getClassLoader(),new Class[]{Connection.class},(p,m,a)->m.getName().equals("prepareStatement")?ps:null);
  Map<String,int[]> result=TeamProfileServlet.loadMatchStats(conn,"series-test");
  Map<String,int[]> expected=new HashMap<>();
  expected.put("a",new int[]{1,1});expected.put("b",new int[]{1,2});expected.put("c",new int[]{1,0});expected.put("d",new int[]{0,0});expected.put("e",new int[]{0,0});
  if(!result.keySet().equals(expected.keySet()))throw new AssertionError("Missing draw-only participants");
  for(String key:expected.keySet())if(!Arrays.equals(result.get(key),expected.get(key)))throw new AssertionError("Incorrect wins/losses: "+key);
  System.out.println("PASS: repeated opponents, either winner, draws and draw-only participants");
 }

    static Map<String, int[]> baseline(Connection conn, String seriesId) throws java.sql.SQLException {
        String sql = "WITH results AS ("
                + "SELECT m.team1_id, m.team2_id, CASE "
                + "WHEN m.winner_id IN (m.team1_id, m.team2_id) THEN m.winner_id "
                + "WHEN m.winner_id IS NULL THEN CASE "
                + "WHEN m.score1 > m.score2 THEN m.team1_id WHEN m.score2 > m.score1 THEN m.team2_id "
                + "WHEN m.penalty1 >= 0 AND m.penalty2 >= 0 AND m.penalty1 > m.penalty2 THEN m.team1_id "
                + "WHEN m.penalty1 >= 0 AND m.penalty2 >= 0 AND m.penalty2 > m.penalty1 THEN m.team2_id END "
                + "END AS winner_id FROM matches m JOIN tournaments t ON t.id = m.tournament_id "
                + "JOIN teams a ON a.id = m.team1_id AND a.tournament_id = m.tournament_id "
                + "JOIN teams b ON b.id = m.team2_id AND b.tournament_id = m.tournament_id "
                + "WHERE t.series_id = ? AND ISNULL(m.is_bye, 0) = 0 AND m.team1_id <> m.team2_id "
                + "AND UPPER(m.status) IN ('FINISHED', 'COMPLETED', 'DONE') "
                + "AND m.score1 >= 0 AND m.score2 >= 0), appearances AS ("
                + "SELECT team1_id AS team_id, winner_id FROM results UNION ALL "
                + "SELECT team2_id, winner_id FROM results) "
                + "SELECT team_id, SUM(CASE WHEN winner_id = team_id THEN 1 ELSE 0 END) AS wins, "
                + "SUM(CASE WHEN winner_id IS NOT NULL AND winner_id <> team_id THEN 1 ELSE 0 END) AS losses "
                + "FROM appearances GROUP BY team_id OPTION (LOOP JOIN, MAXDOP 1, MAX_GRANT_PERCENT = 0)";
        Map<String, int[]> stats = new HashMap<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setQueryTimeout(15);
            ps.setString(1, seriesId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) stats.put(rs.getString("team_id"), new int[]{rs.getInt("wins"), rs.getInt("losses")});
            }
        }
        return stats;
    }
}
