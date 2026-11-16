import dao.DBContext;
import dao.TournamentDAO;
import java.sql.*;
import java.util.*;

/** Compare shared tournament summaries with raw match rows, without a SQL GROUP BY. */
public class TournamentSummaryCheck {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Pass Series ID");
        Map<String,int[]> expected = new HashMap<>();
        try (Connection c = new DBContext().getConnection()) {
            String raw = "SELECT m.tournament_id,m.status,m.winner_id,m.score1,m.score2,m.stage_id,s.stage_order "
                    + "FROM matches m JOIN tournaments t ON t.id=m.tournament_id "
                    + "LEFT JOIN tournament_stages s ON s.id=m.stage_id AND s.tournament_id=m.tournament_id "
                    + "WHERE t.series_id=? AND (m.is_bye=0 OR m.is_bye IS NULL) OPTION (LOOP JOIN,MAXDOP 1)";
            try (PreparedStatement s = c.prepareStatement(raw)) {
                s.setString(1,args[0]);
                try (ResultSet r = s.executeQuery()) {
                    while(r.next()) {
                        int[] counts = expected.computeIfAbsent(r.getString(1), k -> new int[3]);
                        counts[0]++;
                        String status = r.getString(2);
                        if ("FINISHED".equalsIgnoreCase(status) || "COMPLETED".equalsIgnoreCase(status) || "DONE".equalsIgnoreCase(status)
                                || r.getString(3)!=null || (r.getObject(4)!=null && r.getObject(5)!=null)) counts[1]++;
                        if (r.getString(6)==null || r.getInt(7)==1) counts[2]++;
                    }
                }
            }
            long start=System.nanoTime();
            int count=0;
            try (PreparedStatement s = c.prepareStatement("SELECT t.id,ms.total_matches,ms.completed_matches,ms.stage1_total_matches "
                    + TournamentDAO.TOURNAMENT_FROM_QUERY + "WHERE t.series_id=? OPTION (LOOP JOIN,MAXDOP 1)")) {
                s.setString(1,args[0]);
                try(ResultSet r=s.executeQuery()) {
                    while(r.next()) {
                        int[] e=expected.getOrDefault(r.getString(1),new int[3]);
                        for(int i=0;i<3;i++)if(r.getInt(i+2)!=e[i])throw new AssertionError("Summary differs: "+r.getString(1));
                        count++;
                    }
                }
            }
            if(count==0)throw new AssertionError("No tournaments checked");
            System.out.printf("PASS: %d tournament summaries match raw results; summary query %.1f ms%n",count,(System.nanoTime()-start)/1e6);
        }
        long start=System.nanoTime();
        var tournaments=new TournamentDAO().getTournamentsBySeriesId(args[0]);
        if(tournaments.isEmpty())throw new AssertionError("DAO returned no tournaments");
        System.out.printf("Full DAO load: %d tournaments / %.1f ms%n",tournaments.size(),(System.nanoTime()-start)/1e6);
    }
}
