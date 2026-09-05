package test;

import dao.DBContext;
import dao.SeriesDAO;
import dao.TournamentDAO;
import dao.ParticipantDAO;
import service.RollingWindowPointService;
import service.RollingWindowPointService.RollingStandingDTO;
import model.Series;
import model.Tournament;
import model.Team;
import model.PartnerParticipant;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.Map;

public class CheckDB {
    public static void main(String[] args) {
        try {
            SeriesDAO seriesDAO = new SeriesDAO();
            List<Series> allSeries = seriesDAO.getAllSeries();
            System.out.println("=== ALL SERIES (" + allSeries.size() + ") ===");
            for (Series s : allSeries) {
                System.out.println("Series: ID=" + s.getId() + ", Name=" + s.getName() + ", Model=" + s.getRankingModel() + ", PhaseSize=" + s.getPhaseSize());
                
                List<Tournament> tourneys = seriesDAO.getTournamentsBySeriesId(s.getId());
                System.out.println("  Tournaments (" + (tourneys != null ? tourneys.size() : 0) + "):");
                if (tourneys != null) {
                    for (int i = 0; i < tourneys.size(); i++) {
                        Tournament t = tourneys.get(i);
                        System.out.println("    #" + (i+1) + " ID=" + t.getId() + ", Name=" + t.getName() + ", Index=" + t.getTournamentIndexInSeries() + ", Tier=" + t.getTierName() + ", Fmt=" + t.getFormat() + ", Type=" + t.getTournamentType() + ", PtsCfg=" + t.getSeriesPointsConfig());
                    }
                }

                List<PartnerParticipant> partners = seriesDAO.getPartnerParticipantsBySeriesId(s.getId());
                System.out.println("  Partner Participants (" + (partners != null ? partners.size() : 0) + ")");

                RollingWindowPointService service = RollingWindowPointService.getInstance();
                List<RollingStandingDTO> standings = service.calculateSeriesStandingsWithExpiry(s.getId());
                System.out.println("  Standings (" + standings.size() + "):");
                for (int i = 0; i < Math.min(15, standings.size()); i++) {
                    RollingStandingDTO dto = standings.get(i);
                    System.out.println("    Rank #" + dto.getRank() + ": " + dto.getTeamName() + " => TotalActive=" + dto.getTotalActivePoints() + " pts, Last=" + dto.getLastTourneyPoints() + " pts, ActiveCount=" + dto.getActiveTourneysCount() + ", PrevRank=" + dto.getPrevRank() + ", RankChange=" + dto.getRankChange());
                }

                // Check Mieszko Fortunski specifically
                for (RollingStandingDTO dto : standings) {
                    if (dto.getTeamName() != null && dto.getTeamName().toLowerCase().contains("mieszko")) {
                        System.out.println("  >>> FOUND TARGET: Rank #" + dto.getRank() + ": " + dto.getTeamName() + " => TotalActive=" + dto.getTotalActivePoints() + " pts, Last=" + dto.getLastTourneyPoints() + " pts, ActiveCount=" + dto.getActiveTourneysCount() + ", Expired=" + dto.getExpiredPoints());
                    }
                }

                List<Map<String, Integer>> tourneyPtsList = service.getTourneyPointsPerTournament(s.getId());
                System.out.println("  Tourney Points Matrix (" + tourneyPtsList.size() + " tourneys):");
                for (int tIdx = 0; tIdx < tourneyPtsList.size(); tIdx++) {
                    Map<String, Integer> map = tourneyPtsList.get(tIdx);
                    System.out.println("    Tourney " + (tIdx + 1) + " has points for " + map.size() + " teams.");
                    for (String k : map.keySet()) {
                        if (k.contains("mieszko")) {
                            System.out.println("      Mieszko in Tourney " + (tIdx + 1) + " earned " + map.get(k) + " pts");
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
