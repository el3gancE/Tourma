package controller;

import dao.ParticipantDAO;
import dao.SeriesDAO;
import dao.TournamentDAO;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import model.PartnerParticipant;
import model.Series;
import model.Team;
import model.Tournament;

/**
 * Controller Servlet for Rolling Series Hall of Fame (Bảng Vàng) Screen
 * Pattern: /rolling-hof, /rolling/hof
 */
@WebServlet(name = "RollingHofServlet", urlPatterns = {"/rolling-hof", "/rolling/hof"})
public class RollingHofServlet extends HttpServlet {

    public static class TournamentChampionDTO {
        private Tournament tournament;
        private String championTeamName;
        private String runnerUpTeamName;
        private String finalStageUrl;
        private String status; // COMPLETED, ONGOING, UPCOMING
        private int championshipOrdinal; // 1, 2, 3... (Lần thứ N)

        public TournamentChampionDTO(Tournament tournament, String championTeamName, String runnerUpTeamName, String finalStageUrl, String status, int championshipOrdinal) {
            this.tournament = tournament;
            this.championTeamName = championTeamName;
            this.runnerUpTeamName = runnerUpTeamName;
            this.finalStageUrl = finalStageUrl;
            this.status = status;
            this.championshipOrdinal = championshipOrdinal;
        }

        public Tournament getTournament() { return tournament; }
        public String getChampionTeamName() { return championTeamName; }
        public String getRunnerUpTeamName() { return runnerUpTeamName; }
        public String getFinalStageUrl() { return finalStageUrl; }
        public String getStatus() { return status; }
        public int getChampionshipOrdinal() { return championshipOrdinal; }
    }

    public static class TeamChampionStatsDTO {
        private String teamName;
        private int totalChampionships;
        private int tierSCount;
        private int tierACount;
        private int tierBCount;
        private int tierCCount;
        private int tierDCount;
        private List<String> tournamentNames = new ArrayList<>();

        public TeamChampionStatsDTO(String teamName) {
            this.teamName = teamName;
        }

        public String getTeamName() { return teamName; }
        public int getTotalChampionships() { return totalChampionships; }
        public void setTotalChampionships(int totalChampionships) { this.totalChampionships = totalChampionships; }
        public int getTierSCount() { return tierSCount; }
        public void setTierSCount(int tierSCount) { this.tierSCount = tierSCount; }
        public int getTierACount() { return tierACount; }
        public void setTierACount(int tierACount) { this.tierACount = tierACount; }
        public int getTierBCount() { return tierBCount; }
        public void setTierBCount(int tierBCount) { this.tierBCount = tierBCount; }
        public int getTierCCount() { return tierCCount; }
        public void setTierCCount(int tierCCount) { this.tierCCount = tierCCount; }
        public int getTierDCount() { return tierDCount; }
        public void setTierDCount(int tierDCount) { this.tierDCount = tierDCount; }
        public List<String> getTournamentNames() { return tournamentNames; }
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        String seriesId = request.getParameter("id");
        if (seriesId == null || seriesId.trim().isEmpty()) {
            seriesId = request.getParameter("seriesId");
        }

        SeriesDAO seriesDAO = new SeriesDAO();
        Series series = null;
        if (seriesId != null && !seriesId.trim().isEmpty()) {
            series = seriesDAO.getSeriesById(seriesId.trim());
        }

        if (series == null) {
            List<Series> allSeries = seriesDAO.getAllSeries();
            for (Series s : allSeries) {
                if ("ROLLING_WINDOW".equalsIgnoreCase(s.getRankingModel())) {
                    series = s;
                    break;
                }
            }
            if (series == null && !allSeries.isEmpty()) {
                series = allSeries.get(0);
            }
        }

        List<Tournament> tournamentsList = null;
        List<PartnerParticipant> partnerList = null;
        List<TournamentChampionDTO> tourneyChampList = new ArrayList<>();
        List<TeamChampionStatsDTO> topChampionsList = new ArrayList<>();

        if (series != null) {
            seriesId = series.getId();
            tournamentsList = seriesDAO.getTournamentsBySeriesId(seriesId);
            partnerList = seriesDAO.getPartnerParticipantsBySeriesId(seriesId);

            ParticipantDAO pDao = new ParticipantDAO();
            Map<String, TeamChampionStatsDTO> statsMap = new HashMap<>();
            Map<String, Integer> teamCumulativeChamps = new HashMap<>();

            if (tournamentsList != null) {
                for (Tournament t : tournamentsList) {
                    Map<String, Integer> placements = pDao.getTournamentPlacements(t.getId());
                    List<Team> teams = pDao.getTeamsByTournamentId(t.getId());

                    String champName = null;
                    String runnerUpName = null;

                    if (teams != null && placements != null && !placements.isEmpty()) {
                        for (Team tm : teams) {
                            if (tm.getRawName() == null) continue;
                            Integer pos = placements.get(tm.getId());
                            if (pos == null) {
                                pos = placements.get(tm.getRawName().trim().toLowerCase());
                            }
                            if (pos != null) {
                                if (pos == 1) {
                                    champName = tm.getRawName().trim();
                                } else if (pos == 2) {
                                    runnerUpName = tm.getRawName().trim();
                                }
                            }
                        }
                    }

                    // Build Final Stage Url
                    String isMulti = t.getTournamentType();
                    boolean multi = "MULTI_STAGE".equalsIgnoreCase(isMulti);
                    String s1 = (t.getFormat() != null) ? t.getFormat().toUpperCase() : "SINGLE_ELIMINATION";
                    String s2 = "SINGLE_ELIMINATION";
                    String targetPage = "single-elimination.jsp";

                    String finalFmt = multi ? s2 : s1;
                    if (finalFmt.contains("DOUBLE") || "DE".equals(finalFmt)) targetPage = "double-elimination.jsp";
                    else if (finalFmt.contains("ROUND") || "RR".equals(finalFmt)) targetPage = "round-robin.jsp";
                    else if (finalFmt.contains("GROUP") || "GS".equals(finalFmt)) targetPage = "group-stage.jsp";
                    else if (finalFmt.contains("SWISS") || "SW".equals(finalFmt)) targetPage = "swiss-stage.jsp";

                    String finalStageUrl = request.getContextPath() + "/common/" + targetPage + "?id=" + t.getId() + (multi ? "&stage=2" : "") + "&seriesId=" + seriesId;
                    String status = (champName != null) ? "COMPLETED" : "ONGOING";

                    int champOrdinal = 0;
                    if (champName != null) {
                        String key = champName.toLowerCase().trim();
                        int prevCount = teamCumulativeChamps.getOrDefault(key, 0);
                        champOrdinal = prevCount + 1;
                        teamCumulativeChamps.put(key, champOrdinal);
                    }

                    tourneyChampList.add(new TournamentChampionDTO(t, champName, runnerUpName, finalStageUrl, status, champOrdinal));

                    // Aggregate Champions Leaderboard
                    if (champName != null) {
                        String key = champName.toLowerCase().trim();
                        TeamChampionStatsDTO stats = statsMap.get(key);
                        if (stats == null) {
                            stats = new TeamChampionStatsDTO(champName);
                            statsMap.put(key, stats);
                        }
                        stats.setTotalChampionships(stats.getTotalChampionships() + 1);
                        stats.getTournamentNames().add(t.getName());

                        String tier = (t.getTierName() != null) ? t.getTierName().toUpperCase().trim() : "A";
                        if ("S".equals(tier)) stats.setTierSCount(stats.getTierSCount() + 1);
                        else if ("A".equals(tier)) stats.setTierACount(stats.getTierACount() + 1);
                        else if ("B".equals(tier)) stats.setTierBCount(stats.getTierBCount() + 1);
                        else if ("C".equals(tier)) stats.setTierCCount(stats.getTierCCount() + 1);
                        else if ("D".equals(tier)) stats.setTierDCount(stats.getTierDCount() + 1);
                    }
                }
                // Reverse tourneyChampList so most recent tournament appears at top
                java.util.Collections.reverse(tourneyChampList);
            }

            topChampionsList = new ArrayList<>(statsMap.values());
            topChampionsList.sort((a, b) -> {
                if (b.getTotalChampionships() != a.getTotalChampionships()) {
                    return Integer.compare(b.getTotalChampionships(), a.getTotalChampionships());
                }
                if (b.getTierSCount() != a.getTierSCount()) {
                    return Integer.compare(b.getTierSCount(), a.getTierSCount());
                }
                if (b.getTierACount() != a.getTierACount()) {
                    return Integer.compare(b.getTierACount(), a.getTierACount());
                }
                if (b.getTierBCount() != a.getTierBCount()) {
                    return Integer.compare(b.getTierBCount(), a.getTierBCount());
                }
                return Integer.compare(b.getTierCCount(), a.getTierCCount());
            });
        }

        request.setAttribute("series", series);
        request.setAttribute("tournamentsList", tournamentsList);
        request.setAttribute("partnerList", partnerList);
        request.setAttribute("tourneyChampList", tourneyChampList);
        request.setAttribute("topChampionsList", topChampionsList);

        request.getRequestDispatcher("/common/rolling/rolling-hof.jsp").forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        doGet(request, response);
    }
}
