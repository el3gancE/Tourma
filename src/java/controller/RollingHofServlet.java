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

    public static class MatrixTourneyColDTO {
        private String id;
        private String name;
        private String tier;
        private String summaryUrl;

        public MatrixTourneyColDTO(String id, String name, String tier, String summaryUrl) {
            this.id = id;
            this.name = name;
            this.tier = tier;
            this.summaryUrl = summaryUrl;
        }
        public String getId() { return id; }
        public String getName() { return name; }
        public String getTier() { return tier; }
        public String getSummaryUrl() { return summaryUrl; }
    }

    public static class MatrixCellDTO {
        private String code;
        private boolean bold;
        private boolean winner;
        private boolean finalist;
        private boolean participated;

        public MatrixCellDTO(String code, boolean bold, boolean winner, boolean finalist, boolean participated) {
            this.code = code;
            this.bold = bold;
            this.winner = winner;
            this.finalist = finalist;
            this.participated = participated;
        }
        public String getCode() { return code; }
        public boolean isBold() { return bold; }
        public boolean isWinner() { return winner; }
        public boolean isFinalist() { return finalist; }
        public boolean isParticipated() { return participated; }
    }

    public static class MatrixRowDTO {
        private int bxhRank;
        private String partnerId;
        private String teamName;
        private List<MatrixCellDTO> cells = new ArrayList<>();

        public MatrixRowDTO(int bxhRank, String partnerId, String teamName) {
            this.bxhRank = bxhRank;
            this.partnerId = partnerId;
            this.teamName = teamName;
        }
        public int getBxhRank() { return bxhRank; }
        public String getPartnerId() { return partnerId; }
        public String getTeamName() { return teamName; }
        public List<MatrixCellDTO> getCells() { return cells; }
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
        List<MatrixTourneyColDTO> performanceMatrixCols = new ArrayList<>();
        List<MatrixRowDTO> performanceMatrixRows = new ArrayList<>();

        if (series != null) {
            seriesId = series.getId();
            tournamentsList = seriesDAO.getTournamentsBySeriesId(seriesId);
            partnerList = seriesDAO.getPartnerParticipantsBySeriesId(seriesId);

            service.RollingWindowPointService rwps = service.RollingWindowPointService.getInstance();
            rwps.preloadSeriesData(seriesId);
            if ("ROLLING_WINDOW".equalsIgnoreCase(series.getRankingModel())) {
                List<service.NumberOneHistory.Leader> history = service.NumberOneHistory.build(
                        tournamentsList, partnerList, rwps.getTourneyPointsPerTournament(seriesId),
                        series.getPhaseSize()>0?series.getPhaseSize():3);
                request.setAttribute("numberOneHistory", history);
                List<service.NumberOneHistory.Reign> reigns = new ArrayList<>();
                for (service.NumberOneHistory.Leader leader : history) reigns.addAll(leader.getReigns());
                reigns.sort(java.util.Comparator.comparingInt(service.NumberOneHistory.Reign::getOrdinal).reversed());
                request.setAttribute("numberOneReigns", reigns);
            }
            Map<String, TeamChampionStatsDTO> statsMap = new HashMap<>();
            Map<String, Integer> teamCumulativeChamps = new HashMap<>();
            Map<String, List<String>> stageFormatsMap = new TournamentDAO().getStageFormatsBySeriesId(seriesId);

            if (tournamentsList != null) {
                for (Tournament t : tournamentsList) {
                    Map<String, Integer> placements = rwps.getCachedTournamentPlacements(t.getId());
                    List<Team> teams = rwps.getCachedTeamsByTournamentId(t.getId());

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

                    String finalStageUrl = buildLatestActiveStageUrl(request.getContextPath(), t, stageFormatsMap.get(t.getId()), seriesId, champName != null);
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

            if (tournamentsList != null) {
                List<Map<String, Team>> tourneyTeamLookupList = new ArrayList<>();
                List<Map<String, service.TournamentPlacementService.PlacementResult>> tourneyResultsList = new ArrayList<>();

                for (Tournament t : tournamentsList) {
                    String tier = (t.getTierName() != null && !t.getTierName().trim().isEmpty())
                            ? t.getTierName().trim().toUpperCase(java.util.Locale.ROOT) : "A";
                    Map<String, Integer> tPlacements = rwps.getCachedTournamentPlacements(t.getId());
                    boolean hasChamp = tPlacements != null && tPlacements.containsValue(1);
                    String latestStageUrl = buildLatestActiveStageUrl(request.getContextPath(), t, stageFormatsMap.get(t.getId()), seriesId, hasChamp);
                    performanceMatrixCols.add(new MatrixTourneyColDTO(t.getId(), t.getName(), tier, latestStageUrl));

                    List<Team> tTeams = rwps.getCachedTeamsByTournamentId(t.getId());
                    Map<String, Team> lookup = new HashMap<>();
                    if (tTeams != null) {
                        for (Team tm : tTeams) {
                            if (tm.getPartnerParticipantId() != null && !tm.getPartnerParticipantId().trim().isEmpty()) {
                                lookup.put("P:" + tm.getPartnerParticipantId().trim().toLowerCase(java.util.Locale.ROOT), tm);
                            }
                            if (tm.getRawName() != null && !tm.getRawName().trim().isEmpty()) {
                                lookup.put("N:" + tm.getRawName().trim().toLowerCase(java.util.Locale.ROOT), tm);
                            }
                            if (tm.getNormalizedName() != null && !tm.getNormalizedName().trim().isEmpty()) {
                                lookup.put("N:" + tm.getNormalizedName().trim().toLowerCase(java.util.Locale.ROOT), tm);
                            }
                        }
                    }
                    tourneyTeamLookupList.add(lookup);
                    tourneyResultsList.add(service.TournamentPlacementService.getInstance().getTournamentResults(t.getId()));
                }

                List<service.RollingWindowPointService.RollingStandingDTO> currentStandings = rwps.calculateSeriesStandingsWithExpiry(seriesId);
                if (currentStandings != null) {
                    for (int rIdx = 0; rIdx < currentStandings.size(); rIdx++) {
                        service.RollingWindowPointService.RollingStandingDTO st = currentStandings.get(rIdx);
                        int rank = st.getRank() > 0 ? st.getRank() : (rIdx + 1);
                        String pId = st.getPartnerParticipantId();
                        String tName = st.getTeamName() != null ? st.getTeamName().trim() : "";
                        MatrixRowDTO row = new MatrixRowDTO(rank, pId, tName);

                        String pKey = (pId != null && !pId.trim().isEmpty()) ? ("P:" + pId.trim().toLowerCase(java.util.Locale.ROOT)) : null;
                        String nKey = !tName.isEmpty() ? ("N:" + tName.toLowerCase(java.util.Locale.ROOT)) : null;

                        for (int cIdx = 0; cIdx < tournamentsList.size(); cIdx++) {
                            Tournament t = tournamentsList.get(cIdx);
                            String tier = performanceMatrixCols.get(cIdx).getTier();
                            Map<String, Team> lookup = tourneyTeamLookupList.get(cIdx);
                            Team matchedTeam = null;
                            if (pKey != null) matchedTeam = lookup.get(pKey);
                            if (matchedTeam == null && nKey != null) matchedTeam = lookup.get(nKey);

                            if (matchedTeam == null) {
                                row.getCells().add(new MatrixCellDTO("-", false, false, false, false));
                            } else {
                                Map<String, service.TournamentPlacementService.PlacementResult> resMap = tourneyResultsList.get(cIdx);
                                service.TournamentPlacementService.PlacementResult pr = resMap != null ? resMap.get(matchedTeam.getId()) : null;
                                if (pr == null && resMap != null && !tName.isEmpty()) {
                                    pr = resMap.get(tName.toLowerCase(java.util.Locale.ROOT));
                                }
                                if (pr == null) {
                                    row.getCells().add(new MatrixCellDTO("-", false, false, false, true));
                                } else {
                                    String code = formatShortPlacementCode(pr);
                                    boolean bold = isBoldPlacement(pr, tier);
                                    boolean isWin = "W".equals(code);
                                    boolean isFin = "F".equals(code);
                                    row.getCells().add(new MatrixCellDTO(code, bold, isWin, isFin, true));
                                }
                            }
                        }
                        performanceMatrixRows.add(row);
                    }
                }
            }
        }

        request.setAttribute("series", series);
        request.setAttribute("tournamentsList", tournamentsList);
        request.setAttribute("partnerList", partnerList);
        request.setAttribute("tourneyChampList", tourneyChampList);
        request.setAttribute("topChampionsList", topChampionsList);
        request.setAttribute("performanceMatrixCols", performanceMatrixCols);
        request.setAttribute("performanceMatrixRows", performanceMatrixRows);

        request.getRequestDispatcher("/common/rolling/rolling-hof.jsp").forward(request, response);
    }

    private static String buildLatestActiveStageUrl(
            String contextPath,
            Tournament t,
            List<String> stageFormats,
            String seriesId,
            boolean hasChampion) {
        boolean multi = "MULTI_STAGE".equalsIgnoreCase(t.getTournamentType());
        String s1 = (stageFormats != null && !stageFormats.isEmpty() && stageFormats.get(0) != null)
                ? stageFormats.get(0).toUpperCase(java.util.Locale.ROOT)
                : (t.getFormat() != null ? t.getFormat().toUpperCase(java.util.Locale.ROOT) : "SINGLE_ELIMINATION");
        String s2 = (stageFormats != null && stageFormats.size() > 1 && stageFormats.get(1) != null)
                ? stageFormats.get(1).toUpperCase(java.util.Locale.ROOT)
                : (t.getStage2Format() != null ? t.getStage2Format().toUpperCase(java.util.Locale.ROOT) : "SINGLE_ELIMINATION");

        boolean stage2Active = multi && (hasChampion
                || "COMPLETED".equalsIgnoreCase(t.getStatus())
                || "FINISHED".equalsIgnoreCase(t.getStatus())
                || "LOCKED".equalsIgnoreCase(t.getStage1Status())
                || "COMPLETED".equalsIgnoreCase(t.getStage1Status())
                || (t.getStage2Teams() != null && t.getStage2Teams().trim().length() > 2));
        String activeFmt = multi ? (stage2Active ? s2 : s1) : s1;
        String targetPage = "single-elimination.jsp";
        if (activeFmt.contains("DOUBLE") || "DE".equals(activeFmt)) targetPage = "double-elimination.jsp";
        else if (activeFmt.contains("ROUND") || "RR".equals(activeFmt)) targetPage = "round-robin.jsp";
        else if (activeFmt.contains("GROUP") || "GS".equals(activeFmt)) targetPage = "group-stage.jsp";
        else if ("GSL".equals(activeFmt)) targetPage = "gsl.jsp";
        else if (activeFmt.contains("SWISS") || "SW".equals(activeFmt)) targetPage = "swiss-stage.jsp";

        String stageQuery = multi ? ("&stage=" + (stage2Active ? "2" : "1")) : ("&format=" + activeFmt);
        return contextPath + "/common/" + targetPage + "?id=" + t.getId() + stageQuery + "&seriesId=" + seriesId;
    }

    private static String formatShortPlacementCode(service.TournamentPlacementService.PlacementResult pr) {
        if (pr == null) return "-";
        String key = pr.pointKey != null ? pr.pointKey : "";
        String ach = pr.getAchievement() != null ? pr.getAchievement() : "";
        if (key.startsWith("swiss_")) return key.substring(6);
        if ("stage1_eliminated".equals(key)) return "GS";
        if ("Loser's Qualification".equalsIgnoreCase(ach) || "Group Qualification".equalsIgnoreCase(ach)) return "LQ";
        if (ach.startsWith("Loser's Round ")) {
            return "LR" + ach.substring("Loser's Round ".length()).trim();
        }
        if (pr.rank == 1) return "W";
        if (pr.rank == 2) return "F";
        if (pr.rank <= 4) return "SF";
        if (pr.rank <= 8) return "QF";
        int size = 16;
        while (size < pr.rank) size *= 2;
        return "RO" + size;
    }

    private static boolean isBoldPlacement(service.TournamentPlacementService.PlacementResult pr, String tier) {
        if (pr == null || pr.rank <= 0) return false;
        String key = pr.pointKey != null ? pr.pointKey : "";
        if (key.startsWith("s1_") || key.startsWith("swiss_") || "stage1_eliminated".equals(key)) {
            return false;
        }
        String t = tier != null ? tier.trim().toUpperCase(java.util.Locale.ROOT) : "A";
        if ("S".equals(t) || "A".equals(t)) return pr.rank <= 16;
        if ("B".equals(t)) return pr.rank <= 8;
        return pr.rank <= 4;
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        doGet(request, response);
    }
}
