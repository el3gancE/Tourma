package test;

import dao.SeriesDAO;
import dao.TournamentDAO;
import model.Series;
import model.Tournament;
import java.util.List;

public class CheckTourney1Champ {
    public static void main(String[] args) {
        SeriesDAO sDao = new SeriesDAO();
        Series s = sDao.getSeriesById("S_0009a91e");
        if (s != null) {
            List<Tournament> tList = sDao.getTournamentsBySeriesId(s.getId());
            if (!tList.isEmpty()) {
                Tournament t1 = tList.get(0);
                System.out.println("First Tourney in Series: ID=" + t1.getId() + ", Name=" + t1.getName());
            }
        }
    }
}
