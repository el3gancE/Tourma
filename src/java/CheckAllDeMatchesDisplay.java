import dao.DBContext;
import dao.SeriesDAO;
import model.*;
import java.sql.*;
import java.util.*;

public class CheckAllDeMatchesDisplay {
    public static void main(String[] args) {
        try (Connection conn = new DBContext().getConnection()) {
            SeriesDAO seriesDAO = new SeriesDAO();
            List<Series> allSeries = seriesDAO.getAllSeries();
            
            for (Series s : allSeries) {
                System.out.println("==================================================");
                System.out.println("SERIES: " + s.getName() + " (" + s.getId() + ")");
                System.out.println("==================================================");
                
                List<Tournament> tourneys = seriesDAO.getTournamentsBySeriesId(s.getId());
                List<PartnerParticipant> partners = seriesDAO.getPartnerParticipantsBySeriesId(s.getId());
                
                for (Tournament t : tourneys) {
                    System.out.println("\n  TOURNAMENT: " + t.getName() + " (" + t.getId() + ")");
                    
                    // Check tournament stage formats
                    String stgSql = "SELECT stage_order, format FROM tournament_stages WHERE tournament_id = ? ORDER BY stage_order";
                    List<String> formats = new ArrayList<>();
                    try (PreparedStatement psStg = conn.prepareStatement(stgSql)) {
                        psStg.setString(1, t.getId());
                        try (ResultSet rsStg = psStg.executeQuery()) {
                            while (rsStg.next()) {
                                formats.add("Stage " + rsStg.getInt("stage_order") + ": " + rsStg.getString("format"));
                            }
                        }
                    }
                    System.out.println("  Stages: " + String.join(", ", formats));
                    
                    // Count matches per stage and bracket_type
                    String cntSql = "SELECT ISNULL(ts.stage_order, 1) as stg_order, ISNULL(ts.format, 'UNKNOWN') as stg_fmt, m.bracket_type, count(*) as total_matches, " +
                                    "SUM(CASE WHEN m.score1 IS NOT NULL AND m.score2 IS NOT NULL THEN 1 ELSE 0 END) as played_matches " +
                                    "FROM matches m LEFT JOIN tournament_stages ts ON m.stage_id = ts.id " +
                                    "WHERE m.tournament_id = ? " +
                                    "GROUP BY ISNULL(ts.stage_order, 1), ISNULL(ts.format, 'UNKNOWN'), m.bracket_type " +
                                    "ORDER BY stg_order, m.bracket_type";
                    try (PreparedStatement psCnt = conn.prepareStatement(cntSql)) {
                        psCnt.setString(1, t.getId());
                        try (ResultSet rsCnt = psCnt.executeQuery()) {
                            while (rsCnt.next()) {
                                System.out.printf("    Stage %d (%s) | Bracket: %-15s | Total: %d, Played: %d\n",
                                    rsCnt.getInt("stg_order"),
                                    rsCnt.getString("stg_fmt"),
                                    rsCnt.getString("bracket_type"),
                                    rsCnt.getInt("total_matches"),
                                    rsCnt.getInt("played_matches")
                                );
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
