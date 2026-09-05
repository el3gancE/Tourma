package test;

import dao.SeriesDAO;
import model.Series;
import model.Tournament;
import service.RollingWindowPointService;
import java.util.List;
import java.util.Map;

public class CheckPointsDetail {
    public static void main(String[] args) {
        RollingWindowPointService service = RollingWindowPointService.getInstance();
        SeriesDAO sDao = new SeriesDAO();
        Series s = sDao.getSeriesById("S_0009a91e");
        if (s == null) {
            System.out.println("Series not found");
            return;
        }
        List<Map<String, Integer>> ptsList = service.getTourneyPointsPerTournament(s.getId());
        List<Tournament> tList = sDao.getTournamentsBySeriesId(s.getId());
        for (int i = 0; i < tList.size(); i++) {
            Tournament t = tList.get(i);
            Map<String, Integer> pts = (i < ptsList.size()) ? ptsList.get(i) : null;
            System.out.println("Tourney #" + (i+1) + " [" + t.getName() + "] (ID=" + t.getId() + "): " + (pts != null ? pts.size() : 0) + " teams in pts map");
            if (pts != null) {
                int countWithPoints = 0;
                for (Map.Entry<String, Integer> e : pts.entrySet()) {
                    if (e.getValue() > 0) {
                        countWithPoints++;
                        if (countWithPoints <= 5) {
                            System.out.println("    " + e.getKey() + " -> " + e.getValue() + " pts");
                        }
                    }
                }
                System.out.println("    Total teams with > 0 pts: " + countWithPoints);
            }
        }
        
        List<RollingWindowPointService.RollingStandingDTO> standings = service.calculateSeriesStandingsWithExpiry(s.getId());
        System.out.println("\n--- TOP 10 STANDINGS FROM SERVER ---");
        for (int i = 0; i < Math.min(10, standings.size()); i++) {
            RollingWindowPointService.RollingStandingDTO dto = standings.get(i);
            System.out.println("#" + dto.getRank() + " " + dto.getTeamName() + " => " + dto.getTotalActivePoints() + " pts");
        }
        System.out.println("\n--- BOTTOM 10 STANDINGS FROM SERVER ---");
        for (int i = Math.max(0, standings.size() - 10); i < standings.size(); i++) {
            RollingWindowPointService.RollingStandingDTO dto = standings.get(i);
            System.out.println("#" + dto.getRank() + " " + dto.getTeamName() + " => " + dto.getTotalActivePoints() + " pts");
        }
    }
}
