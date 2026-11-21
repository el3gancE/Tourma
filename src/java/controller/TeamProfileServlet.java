package controller;

import dao.DBContext;
import dao.ParticipantDAO;
import dao.SeriesDAO;
import dao.TournamentDAO;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import model.PartnerParticipant;
import model.Series;
import model.Team;
import model.Tournament;
import service.RollingWindowPointService;
import service.RollingWindowPointService.RollingStandingDTO;

/**
 * Controller Servlet for Unified Team Profile Screen
 * Route: /team-profile
 */
@WebServlet(name = "TeamProfileServlet", urlPatterns = {"/team-profile"})
public class TeamProfileServlet extends HttpServlet {

    public static class TourneyPerformanceDTO {
        private String tournamentId;
        private String tournamentName;
        private String tierName;
        private String formatLabel;
        private String achievement;
        private int rank;
        private int pointsEarned;
        private int stt;
        private String finalStageUrl;

        public String getTournamentId() { return tournamentId; }
        public void setTournamentId(String tournamentId) { this.tournamentId = tournamentId; }

        public String getTournamentName() { return tournamentName; }
        public void setTournamentName(String tournamentName) { this.tournamentName = tournamentName; }

        public String getTierName() { return tierName; }
        public void setTierName(String tierName) { this.tierName = tierName; }

        public String getFormatLabel() { return formatLabel; }
        public void setFormatLabel(String formatLabel) { this.formatLabel = formatLabel; }

        public String getAchievement() { return achievement; }
        public void setAchievement(String achievement) { this.achievement = achievement; }

        public int getRank() { return rank; }
        public void setRank(int rank) { this.rank = rank; }

        public int getPointsEarned() { return pointsEarned; }
        public void setPointsEarned(int pointsEarned) { this.pointsEarned = pointsEarned; }

        public int getStt() { return stt; }
        public void setStt(int stt) { this.stt = stt; }

        public String getFinalStageUrl() { return finalStageUrl; }
        public void setFinalStageUrl(String finalStageUrl) { this.finalStageUrl = finalStageUrl; }
    }

    public static class ChampionTournamentDTO {
        private String tournamentId;
        private String tournamentName;
        private String tierName;
        private String finalStageUrl;

        public ChampionTournamentDTO(String tournamentId, String tournamentName, String tierName, String finalStageUrl) {
            this.tournamentId = tournamentId;
            this.tournamentName = tournamentName;
            this.tierName = tierName;
            this.finalStageUrl = finalStageUrl;
        }

        public String getTournamentId() { return tournamentId; }
        public String getTournamentName() { return tournamentName; }
        public String getTierName() { return tierName; }
        public String getFinalStageUrl() { return finalStageUrl; }
        public void setFinalStageUrl(String finalStageUrl) { this.finalStageUrl = finalStageUrl; }
    }

    public static class RankProgressionDTO {
        private String tournamentId;
        private String tournamentName;
        private int tournamentIndex;
        private int rank;
        private int totalActivePoints;
        private int pointsEarned;
        private String achievement;
        private boolean participated;

        public String getTournamentId() { return tournamentId; }
        public void setTournamentId(String tournamentId) { this.tournamentId = tournamentId; }

        public String getTournamentName() { return tournamentName; }
        public void setTournamentName(String tournamentName) { this.tournamentName = tournamentName; }

        public int getTournamentIndex() { return tournamentIndex; }
        public void setTournamentIndex(int tournamentIndex) { this.tournamentIndex = tournamentIndex; }

        public int getRank() { return rank; }
        public void setRank(int rank) { this.rank = rank; }

        public int getTotalActivePoints() { return totalActivePoints; }
        public void setTotalActivePoints(int totalActivePoints) { this.totalActivePoints = totalActivePoints; }

        public int getPointsEarned() { return pointsEarned; }
        public void setPointsEarned(int pointsEarned) { this.pointsEarned = pointsEarned; }

        public String getAchievement() { return achievement; }
        public void setAchievement(String achievement) { this.achievement = achievement; }

        public boolean isParticipated() { return participated; }
        public void setParticipated(boolean participated) { this.participated = participated; }
    }

    public static class MatchHistoryDTO {
        private int stt;
        private String tournamentId;
        private String tournamentName;
        private String tierName;
        private int tournamentIndex;
        private String finalStageUrl;
        private int stageOrder;
        private int roundNumber;
        private int matchOrder;
        private String roundLabel;
        private String result;
        private int teamScore;
        private int opponentScore;
        private Integer teamPenalty;
        private Integer opponentPenalty;
        private String opponentName;

        public int getStt() { return stt; }
        public void setStt(int stt) { this.stt = stt; }
        public String getTournamentId() { return tournamentId; }
        public void setTournamentId(String tournamentId) { this.tournamentId = tournamentId; }
        public String getTournamentName() { return tournamentName; }
        public void setTournamentName(String tournamentName) { this.tournamentName = tournamentName; }
        public String getTierName() { return tierName; }
        public void setTierName(String tierName) { this.tierName = tierName; }
        public int getTournamentIndex() { return tournamentIndex; }
        public void setTournamentIndex(int tournamentIndex) { this.tournamentIndex = tournamentIndex; }
        public String getFinalStageUrl() { return finalStageUrl; }
        public void setFinalStageUrl(String finalStageUrl) { this.finalStageUrl = finalStageUrl; }
        public int getStageOrder() { return stageOrder; }
        public void setStageOrder(int stageOrder) { this.stageOrder = stageOrder; }
        public int getRoundNumber() { return roundNumber; }
        public void setRoundNumber(int roundNumber) { this.roundNumber = roundNumber; }
        public int getMatchOrder() { return matchOrder; }
        public void setMatchOrder(int matchOrder) { this.matchOrder = matchOrder; }
        public String getRoundLabel() { return roundLabel; }
        public void setRoundLabel(String roundLabel) { this.roundLabel = roundLabel; }
        public String getResult() { return result; }
        public void setResult(String result) { this.result = result; }
        public int getTeamScore() { return teamScore; }
        public void setTeamScore(int teamScore) { this.teamScore = teamScore; }
        public int getOpponentScore() { return opponentScore; }
        public void setOpponentScore(int opponentScore) { this.opponentScore = opponentScore; }
        public Integer getTeamPenalty() { return teamPenalty; }
        public void setTeamPenalty(Integer teamPenalty) { this.teamPenalty = teamPenalty; }
        public Integer getOpponentPenalty() { return opponentPenalty; }
        public void setOpponentPenalty(Integer opponentPenalty) { this.opponentPenalty = opponentPenalty; }
        public String getOpponentName() { return opponentName; }
        public void setOpponentName(String opponentName) { this.opponentName = opponentName; }
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        String seriesId = request.getParameter("seriesId");
        String idParam = request.getParameter("id");
        String teamNameParam = request.getParameter("teamName");
        String partnerIdParam = request.getParameter("partnerId");
        if (seriesId == null || seriesId.trim().isEmpty()) {
            seriesId = idParam;
        } else if ((partnerIdParam == null || partnerIdParam.trim().isEmpty())
                && idParam != null && !idParam.trim().isEmpty()
                && !idParam.trim().equalsIgnoreCase(seriesId.trim())) {
            partnerIdParam = idParam.trim();
        }

        SeriesDAO seriesDAO = new SeriesDAO();
        TournamentDAO tournamentDAO = new TournamentDAO();
        ParticipantDAO participantDAO = new ParticipantDAO();

        Series series = null;
        if (seriesId != null && !seriesId.trim().isEmpty()) {
            series = seriesDAO.getSeriesById(seriesId.trim());
        }

        if (series == null) {
            List<Series> allSeries = seriesDAO.getAllSeries();
            if (allSeries != null) {
                for (Series s : allSeries) {
                    if ("ROLLING_WINDOW".equalsIgnoreCase(s.getRankingModel())) {
                        series = s;
                        seriesId = s.getId();
                        break;
                    }
                }
                if (series == null && !allSeries.isEmpty()) {
                    series = allSeries.get(0);
                    seriesId = series.getId();
                }
            }
        }

        PartnerParticipant partner = null;
        String teamName = (teamNameParam != null) ? teamNameParam.trim() : "";
        List<PartnerParticipant> partners = null;
        List<Tournament> tourneys = null;
        Map<String, List<String>> stageFormatsMap = new HashMap<>();
        long profStart = System.currentTimeMillis();
        long tPreloadEnd = 0, tPerfEnd = 0, tProgEnd = 0;

        if (series != null) {
            RollingWindowPointService.getInstance().preloadSeriesData(series.getId());
            partners = seriesDAO.getPartnerParticipantsBySeriesId(series.getId());
            tourneys = seriesDAO.getTournamentsBySeriesId(series.getId());
            stageFormatsMap = tournamentDAO.getStageFormatsBySeriesId(series.getId());
            tPreloadEnd = System.currentTimeMillis();

            if (partners != null) {
                for (PartnerParticipant p : partners) {
                    if (partnerIdParam != null && partnerIdParam.trim().equalsIgnoreCase(p.getId())) {
                        partner = p;
                        teamName = p.getName();
                        break;
                    } else if (!teamName.isEmpty() && p.getName() != null && p.getName().trim().equalsIgnoreCase(teamName)) {
                        partner = p;
                        break;
                    }
                }
                if (partner == null && !partners.isEmpty() && teamName.isEmpty()) {
                    partner = partners.get(0);
                    teamName = partner.getName();
                }
            }
        }

        // Key stats calculation
        int currentRank = 0;
        int highestRank = 0;
        String highestRankTourneyName = "";
        String highestRankTourneyTier = "";
        String highestRankTourneyUrl = "";
        int currentPoints = 0;
        int totalAccumulatedPoints = 0;
        int totalWins = 0;
        int totalLosses = 0;
        int totalTourneysPlayed = 0;

        int champCount = 0;
        int runnerUpCount = 0;
        int semiCount = 0;
        int quarterCount = 0;

        List<ChampionTournamentDTO> championTourneys = new ArrayList<>();
        List<TourneyPerformanceDTO> performanceList = new ArrayList<>();
        List<MatchHistoryDTO> matchHistoryList = new ArrayList<>();
        Map<String, TourneyPerformanceDTO> perfByTeamId = new HashMap<>();
        Map<String, Integer> tourneyOrderByTeamId = new HashMap<>();

        if (series != null && !teamName.isEmpty()) {
            // Standings for current rank & active points
            List<RollingStandingDTO> standings = RollingWindowPointService.getInstance().calculateSeriesStandingsWithExpiry(series.getId());
            if (standings != null) {
                for (RollingStandingDTO dto : standings) {
                    if (dto.getTeamName() != null && dto.getTeamName().trim().equalsIgnoreCase(teamName)) {
                        currentRank = dto.getRank();
                        currentPoints = dto.getTotalActivePoints();
                        break;
                    }
                }
            }

            if (tourneys != null) {
                DBContext db = new DBContext();
                try (Connection conn = db.getConnection()) {
                    Map<String, int[]> matchStats = dao.MatchStatisticsDAO.get(conn, series.getId());

                    RollingWindowPointService rwps = RollingWindowPointService.getInstance();
                    for (int tIdx = 0; tIdx < tourneys.size(); tIdx++) {
                        Tournament t = tourneys.get(tIdx);
                        List<String> stgFormats = stageFormatsMap.get(t.getId());

                        List<Team> teams = rwps.getCachedTeamsByTournamentId(t.getId());
                        Team teamInTourney = null;
                        // Resolve stable Partner identity before falling back to an unlinked name.
                        if (teams != null && partner != null && partner.getId() != null) {
                            for (Team tm : teams) {
                                if (partner.getId().equalsIgnoreCase(tm.getPartnerParticipantId())) {
                                    teamInTourney = tm;
                                    break;
                                }
                            }
                        }
                        if (teams != null && teamInTourney == null) {
                            for (Team tm : teams) {
                                if (partner != null && tm.getPartnerParticipantId() != null) continue;
                                if ((tm.getRawName() != null && tm.getRawName().trim().equalsIgnoreCase(teamName))
                                        || (tm.getName() != null && tm.getName().trim().equalsIgnoreCase(teamName))) {
                                    teamInTourney = tm;
                                    break;
                                }
                            }
                        }

                        boolean playedInTourney = teamInTourney != null;
                        int[] stats = playedInTourney ? matchStats.get(teamInTourney.getId()) : null;
                        int tWins = stats == null ? 0 : stats[0];
                        int tLosses = stats == null ? 0 : stats[1];

                        if (playedInTourney) {
                            totalTourneysPlayed++;

                            // Determine achievement & rank using cached bracket placement
                            Map<String, Integer> matchPlacements = rwps.getCachedTournamentPlacements(t.getId());
                            Integer mPos = (teamInTourney != null) ? matchPlacements.get(teamInTourney.getId()) : null;
                            if (mPos == null && teamInTourney != null && teamInTourney.getRawName() != null) {
                                mPos = matchPlacements.get(teamInTourney.getRawName().trim().toLowerCase());
                            }
                            if (mPos == null) {
                                mPos = matchPlacements.get(teamName.trim().toLowerCase());
                            }
                            if (mPos == null && t.getChampionName() != null) {
                                String cName = t.getChampionName().trim().toLowerCase();
                                if (teamName.trim().toLowerCase().equals(cName)
                                        || (teamInTourney != null && teamInTourney.getRawName() != null && teamInTourney.getRawName().trim().toLowerCase().equals(cName))) {
                                    mPos = 1;
                                }
                            }

                            int tourneyRank = (mPos != null && mPos > 0) ? mPos : 16;

                            // Format display name (abbreviated: SE, DE, SW, RR, GS, or S1 ➔ S2)
                            boolean isMulti = "MULTI_STAGE".equalsIgnoreCase(t.getTournamentType());
                            String s1Fmt = (stgFormats != null && !stgFormats.isEmpty()) ? stgFormats.get(0) : (t.getFormat() != null ? t.getFormat() : "SINGLE_ELIMINATION");
                            String s2Fmt = (stgFormats != null && stgFormats.size() > 1) ? stgFormats.get(1) : "SINGLE_ELIMINATION";

                            boolean isDe = (s1Fmt != null && s1Fmt.toUpperCase().contains("DOUBLE")) || (t.getFormat() != null && t.getFormat().toUpperCase().contains("DOUBLE"));
                            boolean isSwiss = (s1Fmt != null && (s1Fmt.toUpperCase().contains("SWISS") || s1Fmt.toUpperCase().contains("SW"))) || (t.getFormat() != null && (t.getFormat().toUpperCase().contains("SWISS") || t.getFormat().toUpperCase().contains("SW")));
                            String achievement = "Round of 16";
                            boolean isChamp = false;

                            if (isSwiss) {
                                if (isMulti && tourneyRank <= 8) {
                                    String champName = t.getChampionName();
                                    if (champName != null && champName.trim().equalsIgnoreCase(teamName)) {
                                        achievement = "Vô Địch";
                                        tourneyRank = 1;
                                        isChamp = true;
                                    } else if (tourneyRank == 1) {
                                        achievement = "Vô Địch";
                                        isChamp = true;
                                    } else if (tourneyRank == 2) {
                                        achievement = "Á Quân";
                                    } else if (tourneyRank <= 4) {
                                        achievement = "Bán Kết";
                                    } else if (tourneyRank <= 8) {
                                        achievement = "Tứ Kết";
                                    }
                                } else if (tWins + tLosses > 0) {
                                    if (tWins >= 3) {
                                        if (isMulti) {
                                            achievement = "Tứ Kết";
                                        } else {
                                            if (tLosses == 0) {
                                                achievement = "3-0";
                                                if (tourneyRank == 1) isChamp = true;
                                            } else if (tLosses == 1) {
                                                achievement = "3-1";
                                            } else {
                                                achievement = "3-2";
                                            }
                                        }
                                    } else if (tLosses >= 3) {
                                        if (tWins == 2) {
                                            achievement = "2-3";
                                        } else if (tWins == 1) {
                                            achievement = "1-3";
                                        } else {
                                            achievement = "0-3";
                                        }
                                    } else {
                                        achievement = tWins + "-" + tLosses;
                                    }
                                } else {
                                    if (tourneyRank <= 2) {
                                        achievement = isMulti ? "Tứ Kết" : "3-0";
                                        if (tourneyRank == 1 && !isMulti) isChamp = true;
                                    } else if (tourneyRank <= 5) {
                                        achievement = isMulti ? "Tứ Kết" : "3-1";
                                    } else if (tourneyRank <= 8) {
                                        achievement = isMulti ? "Tứ Kết" : "3-2";
                                    } else if (tourneyRank <= 11) {
                                        achievement = "2-3";
                                    } else if (tourneyRank <= 14) {
                                        achievement = "1-3";
                                    } else {
                                        achievement = "0-3";
                                    }
                                }
                            } else {
                                String champName = t.getChampionName();
                                if (champName != null && champName.trim().equalsIgnoreCase(teamName)) {
                                    achievement = "Vô Địch";
                                    tourneyRank = 1;
                                    isChamp = true;
                                } else if (tourneyRank == 1) {
                                    achievement = "Vô Địch";
                                    tourneyRank = 1;
                                    isChamp = true;
                                } else if (tourneyRank == 2) {
                                    achievement = "Á Quân";
                                } else if (tourneyRank <= 4) {
                                    achievement = "Bán Kết";
                                } else if (tourneyRank <= 8) {
                                    achievement = "Tứ Kết";
                                } else if (tourneyRank <= 16) {
                                    achievement = "Round of 16";
                                } else if (tourneyRank <= 32) {
                                    achievement = "Round of 32";
                                } else if (tourneyRank <= 64) {
                                    achievement = (isDe && !isMulti) ? "Loser's Qualification" : "Round of 64";
                                } else if (tourneyRank <= 96) {
                                    achievement = (isDe && !isMulti) ? "Loser's Qualification" : "Round of 128";
                                } else if (tourneyRank <= 128) {
                                    achievement = (isDe && !isMulti) ? "Loser's Round 1" : "Round of 128";
                                } else {
                                    achievement = (isDe && !isMulti) ? "Loser's Round 1" : "Vòng Bảng";
                                }
                            }

                            service.TournamentPlacementService.PlacementResult actualResult = service.TournamentPlacementService.getInstance()
                                    .getTournamentResults(t.getId()).get(teamInTourney == null ? null : teamInTourney.getId());
                            if (actualResult != null) {
                                achievement = actualResult.getAchievement();
                                tourneyRank = actualResult.rank;
                                isChamp = actualResult.rank == 1;
                            }

                            String fmtLabel = getFormatShortCode(s1Fmt);
                            if (isMulti) {
                                fmtLabel = getFormatShortCode(s1Fmt) + " ➔ " + getFormatShortCode(s2Fmt);
                            }

                            totalWins += tWins;
                            totalLosses += tLosses;

                            String finalUrl = getTournamentFinalStageUrl(request.getContextPath(), t.getId(), series.getId(), isMulti, s1Fmt, s2Fmt);

                            // Points calculation for this tournament
                            int ptsEarned = 0;
                            try {
                                ptsEarned = calculatePointsForTournament(t, teamInTourney == null ? null : teamInTourney.getId());
                            } catch (Exception ignore) {}
                            totalAccumulatedPoints += ptsEarned;

                            if (isChamp) {
                                champCount++;
                                semiCount++;
                                quarterCount++;
                                String tier = (t.getTierName() != null && !t.getTierName().isEmpty()) ? t.getTierName().toUpperCase() : "A";
                                championTourneys.add(new ChampionTournamentDTO(t.getId(), t.getName(), tier, finalUrl));
                            } else if ("Á Quân".equals(achievement)) {
                                runnerUpCount++;
                                semiCount++;
                                quarterCount++;
                            } else if ("Bán Kết".equals(achievement)) {
                                semiCount++;
                                quarterCount++;
                            } else if ("Tứ Kết".equals(achievement)) {
                                quarterCount++;
                            }

                            TourneyPerformanceDTO perf = new TourneyPerformanceDTO();
                            perf.setTournamentId(t.getId());
                            perf.setTournamentName(t.getName());
                            perf.setTierName((t.getTierName() != null) ? t.getTierName().toUpperCase() : "A");
                            perf.setFormatLabel(fmtLabel);
                            perf.setAchievement(achievement);
                            perf.setRank(tourneyRank);
                            perf.setPointsEarned(ptsEarned);
                            perf.setStt(totalTourneysPlayed);
                            perf.setFinalStageUrl(finalUrl);

                            performanceList.add(perf);
                            if (teamInTourney != null && teamInTourney.getId() != null) {
                                perfByTeamId.put(teamInTourney.getId(), perf);
                                int tOrder = t.getTournamentIndexInSeries() > 0 ? t.getTournamentIndexInSeries() : (tIdx + 1);
                                tourneyOrderByTeamId.put(teamInTourney.getId(), tOrder);
                            }
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            if (!perfByTeamId.isEmpty()) {
                matchHistoryList = loadTeamMatchHistory(series.getId(), partner, teamName, perfByTeamId, tourneyOrderByTeamId);
            }
            tPerfEnd = System.currentTimeMillis();
            // Calculate Rank Progression and track highest rank in a single fast pass
            List<RankProgressionDTO> rankProgressionList = new ArrayList<>();
            List<PartnerParticipant> allPartners = (partners != null) ? partners : new ArrayList<>();
            List<Map<String, Integer>> tourneyPointsList = RollingWindowPointService.getInstance().getTourneyPointsPerTournament(series.getId());
            List<Map<String, Boolean>> tourneyPartList = RollingWindowPointService.getInstance().getTourneyParticipationPerTournament(series.getId());
            int phaseSize = (series.getPhaseSize() > 0) ? series.getPhaseSize() : 3;
            String targetKey = teamName.trim().toLowerCase();

            Map<String, TourneyPerformanceDTO> perfMap = new HashMap<>();
            for (TourneyPerformanceDTO p : performanceList) {
                if (p.getTournamentId() != null) {
                    perfMap.put(p.getTournamentId(), p);
                }
            }

            int targetIdx = -1;
            if (tourneys != null) {
                for (int ti = tourneys.size() - 1; ti >= 0; ti--) {
                    if (ti < tourneyPointsList.size()) {
                        Map<String, Integer> pMap = tourneyPointsList.get(ti);
                        if (pMap != null && !pMap.isEmpty()) {
                            for (int v : pMap.values()) {
                                if (v > 0) { targetIdx = ti; break; }
                            }
                        }
                    }
                    if (targetIdx != -1) break;
                }
                if (targetIdx == -1) targetIdx = tourneys.size() - 1;
            }

            class MilestoneTeamScore {
                String key;
                String name;
                int totalPts;
                int lastPts;
                MilestoneTeamScore(String key, String name, int totalPts, int lastPts) {
                    this.key = key; this.name = name; this.totalPts = totalPts; this.lastPts = lastPts;
                }
            }

            int bestRank = 999;
            Tournament bestTourney = null;

            if (tourneys != null) {
                for (int step = 0; step <= targetIdx && step < tourneys.size(); step++) {
                    Tournament t = tourneys.get(step);
                    int activeStart = Math.max(0, step - phaseSize + 1);

                    List<MilestoneTeamScore> scoresAtStep = new ArrayList<>();
                    if (allPartners != null) {
                        for (PartnerParticipant p : allPartners) {
                            if (p.getName() == null) continue;
                            String pk = p.getName().trim().toLowerCase();
                            int pts = 0;
                            for (int w = activeStart; w <= step; w++) {
                                if (w < tourneyPointsList.size()) {
                                    Integer pVal = tourneyPointsList.get(w).get(pk);
                                    if (pVal != null) pts += pVal;
                                }
                            }
                            int lastP = 0;
                            if (step < tourneyPointsList.size()) {
                                Integer lp = tourneyPointsList.get(step).get(pk);
                                if (lp != null) lastP = lp;
                            }
                            scoresAtStep.add(new MilestoneTeamScore(pk, p.getName().trim(), pts, lastP));
                        }
                    }

                    scoresAtStep.sort((a, b) -> {
                        if (b.totalPts != a.totalPts) return Integer.compare(b.totalPts, a.totalPts);
                        if (b.lastPts != a.lastPts) return Integer.compare(b.lastPts, a.lastPts);
                        return a.name.compareToIgnoreCase(b.name);
                    });

                    int rankAtStep = 0;
                    int pointsAtStep = 0;
                    for (int r = 0; r < scoresAtStep.size(); r++) {
                        MilestoneTeamScore ms = scoresAtStep.get(r);
                        if (ms.key.equalsIgnoreCase(targetKey)) {
                            rankAtStep = r + 1;
                            pointsAtStep = ms.totalPts;
                            break;
                        }
                    }

                    if (rankAtStep > 0 && pointsAtStep > 0 && rankAtStep < bestRank) {
                        bestRank = rankAtStep;
                        bestTourney = t;
                    }

                    RankProgressionDTO rp = new RankProgressionDTO();
                    rp.setTournamentId(t.getId());
                    rp.setTournamentName(t.getName());
                    rp.setTournamentIndex(t.getTournamentIndexInSeries() > 0 ? t.getTournamentIndexInSeries() : (step + 1));
                    rp.setRank(rankAtStep);
                    rp.setTotalActivePoints(pointsAtStep);

                    int ptsEarned = 0;
                    if (step < tourneyPointsList.size()) {
                        Integer pe = tourneyPointsList.get(step).get(targetKey);
                        if (pe != null) ptsEarned = pe;
                    }
                    rp.setPointsEarned(ptsEarned);

                    boolean participated = false;
                    if (step < tourneyPartList.size()) {
                        Boolean part = tourneyPartList.get(step).get(targetKey);
                        participated = (part != null && part);
                    }
                    rp.setParticipated(participated);

                    TourneyPerformanceDTO perf = perfMap.get(t.getId());
                    if (perf != null && perf.getAchievement() != null && !perf.getAchievement().isEmpty()) {
                        rp.setAchievement(perf.getAchievement());
                    } else if (participated) {
                        rp.setAchievement("+" + ptsEarned + " pts");
                    } else {
                        rp.setAchievement("Không tham gia");
                    }

                    rankProgressionList.add(rp);
                }
            }

            highestRank = (bestRank < 999) ? bestRank : 0;
            if (bestTourney != null) {
                highestRankTourneyName = bestTourney.getName();
                highestRankTourneyTier = (bestTourney.getTierName() != null) ? bestTourney.getTierName().toUpperCase() : "A";
                boolean isMultiH = "MULTI_STAGE".equalsIgnoreCase(bestTourney.getTournamentType());
                List<String> stgFormatsH = stageFormatsMap.get(bestTourney.getId());
                String s1FmtH = (stgFormatsH != null && !stgFormatsH.isEmpty()) ? stgFormatsH.get(0) : bestTourney.getFormat();
                String s2FmtH = (stgFormatsH != null && stgFormatsH.size() > 1) ? stgFormatsH.get(1) : "SINGLE_ELIMINATION";
                highestRankTourneyUrl = getTournamentFinalStageUrl(request.getContextPath(), bestTourney.getId(), series.getId(), isMultiH, s1FmtH, s2FmtH);
            }

            request.setAttribute("rankProgressionList", rankProgressionList);

            java.util.Collections.reverse(performanceList);
            java.util.Collections.reverse(championTourneys);
        }

        int sumTablePoints = 0;
        for (TourneyPerformanceDTO perf : performanceList) {
            sumTablePoints += perf.getPointsEarned();
        }
        if (!performanceList.isEmpty()) {
            totalAccumulatedPoints = sumTablePoints;
            if (currentPoints == 0 && currentRank == 0) {
                currentPoints = sumTablePoints;
            }
        }

        if (highestRank == 0 && currentRank > 0 && currentPoints > 0) {
            highestRank = currentRank;
        }

        request.setAttribute("series", series);
        request.setAttribute("partner", partner);
        request.setAttribute("teamName", teamName);

        request.setAttribute("currentRank", currentRank);
        request.setAttribute("highestRank", highestRank);
        request.setAttribute("highestRankTourneyName", highestRankTourneyName);
        request.setAttribute("highestRankTourneyTier", highestRankTourneyTier);
        request.setAttribute("highestRankTourneyUrl", highestRankTourneyUrl);
        request.setAttribute("currentPoints", currentPoints);
        request.setAttribute("totalAccumulatedPoints", totalAccumulatedPoints);
        request.setAttribute("totalWins", totalWins);
        request.setAttribute("totalLosses", totalLosses);
        request.setAttribute("totalTourneysPlayed", totalTourneysPlayed);

        request.setAttribute("champCount", champCount);
        request.setAttribute("runnerUpCount", runnerUpCount);
        request.setAttribute("semiCount", semiCount);
        request.setAttribute("quarterCount", quarterCount);

        request.setAttribute("championTourneys", championTourneys);
        request.setAttribute("performanceList", performanceList);
        request.setAttribute("matchHistoryList", matchHistoryList);

        List<Tournament> tournamentsList = (tourneys != null) ? tourneys : ((series != null) ? seriesDAO.getTournamentsBySeriesId(series.getId()) : new ArrayList<>());
        List<PartnerParticipant> partnersList = (partners != null) ? partners : ((series != null) ? seriesDAO.getPartnerParticipantsBySeriesId(series.getId()) : new ArrayList<>());
        
        request.setAttribute("tournamentsList", tournamentsList);
        request.setAttribute("partnersList", partnersList);
        request.setAttribute("stageFormatsMap", (series != null && !teamName.isEmpty()) ? stageFormatsMap : new HashMap<>());

        List<Map<String, Integer>> serverTourneyPoints = (series != null) ? RollingWindowPointService.getInstance().getTourneyPointsPerTournament(series.getId()) : new ArrayList<>();
        List<Map<String, Boolean>> serverTourneyParticipation = (series != null) ? RollingWindowPointService.getInstance().getTourneyParticipationPerTournament(series.getId()) : new ArrayList<>();
        request.setAttribute("serverTourneyPoints", serverTourneyPoints);
        request.setAttribute("serverTourneyParticipation", serverTourneyParticipation);

        long tBeforeFwd = System.currentTimeMillis();
        request.getRequestDispatcher("/common/team-profile.jsp").forward(request, response);
        long tAfterFwd = System.currentTimeMillis();
        java.util.logging.Logger.getLogger(TeamProfileServlet.class.getName()).warning(
            "[TIMING TeamProfile: " + teamName + "] preload=" + (tPreloadEnd - profStart)
            + "ms, performances=" + (tPerfEnd - tPreloadEnd)
            + "ms, prog=" + (tBeforeFwd - tPerfEnd)
            + "ms, forwardJsp=" + (tAfterFwd - tBeforeFwd)
            + "ms, total=" + (tAfterFwd - profStart) + "ms");
    }

    public static String getTournamentFinalStageUrl(String contextPath, String tournamentId, String seriesId, boolean isMultiStage, String s1Format, String s2Format) {
        if (tournamentId == null || tournamentId.trim().isEmpty()) return "#";
        String finalFormat = isMultiStage ? s2Format : s1Format;
        if (finalFormat == null || finalFormat.trim().isEmpty()) finalFormat = "SINGLE_ELIMINATION";
        finalFormat = finalFormat.toUpperCase().trim();

        String page = "single-elimination.jsp";
        if (finalFormat.contains("DOUBLE") || "DE".equals(finalFormat)) {
            page = "double-elimination.jsp";
        } else if (finalFormat.contains("ROUND") || "RR".equals(finalFormat)) {
            page = "round-robin.jsp";
        } else if (finalFormat.contains("GROUP") || "GS".equals(finalFormat)) {
            page = "group-stage.jsp";
        } else if (finalFormat.contains("SWISS") || "SW".equals(finalFormat)) {
            page = "swiss-stage.jsp";
        }

        StringBuilder url = new StringBuilder();
        if (contextPath != null) {
            url.append(contextPath);
        }
        url.append("/common/").append(page).append("?id=").append(tournamentId);
        if (isMultiStage) {
            url.append("&stage=2");
        }
        if (seriesId != null && !seriesId.trim().isEmpty()) {
            url.append("&seriesId=").append(seriesId.trim());
        }
        return url.toString();
    }

    private int calculatePointsForTournament(Tournament t, String teamId) {
        return RollingWindowPointService.getInstance().getTournamentTeamPoints(t, teamId);
    }

    private String getFormatShortCode(String fmt) {
        if (fmt == null || fmt.trim().isEmpty()) return "SE";
        String f = fmt.toUpperCase().trim();
        if ("GSL".equals(f) || "GF".equals(f)) return "GF";
        if (f.contains("DOUBLE") || "DE".equals(f)) return "DE";
        if (f.contains("ROUND") || "RR".equals(f)) return "RR";
        if (f.contains("GROUP") || "GS".equals(f)) return "GS";
        if (f.contains("SWISS") || "SW".equals(f)) return "SW";
        return "SE";
    }

    /** Aggregate persisted results across all stages; draws count as neither wins nor losses. */
    private static List<MatchHistoryDTO> loadTeamMatchHistory(
            String seriesId,
            PartnerParticipant partner,
            String teamName,
            Map<String, TourneyPerformanceDTO> perfByTeamId,
            Map<String, Integer> tourneyOrderByTeamId) {
        List<MatchHistoryDTO> list = new ArrayList<>();
        if (seriesId == null || seriesId.trim().isEmpty() || perfByTeamId == null || perfByTeamId.isEmpty()) {
            return list;
        }
        String pIdVal = (partner != null && partner.getId() != null) ? partner.getId().trim() : "";
        String sql = "SELECT m.tournament_id, m.round_number, COALESCE(m.match_order, 0) AS match_order, "
                + "m.bracket_type, COALESCE(s.stage_order, 1) AS stage_order, s.format AS stage_format, "
                + "CASE WHEN s.format = 'SINGLE_ELIMINATION' OR s.format IS NULL THEN ("
                + "  SELECT COUNT(*) FROM matches slot "
                + "  WHERE slot.tournament_id = m.tournament_id "
                + "    AND ISNULL(slot.stage_id, '') = ISNULL(m.stage_id, '') "
                + "    AND slot.round_number = m.round_number"
                + ") ELSE 0 END AS round_slots, "
                + "m.team1_id, m.team2_id, a.raw_name AS team1_name, b.raw_name AS team2_name, "
                + "m.score1, m.score2, m.penalty1, m.penalty2, m.winner_id "
                + "FROM tournaments t "
                + "JOIN matches m ON m.tournament_id = t.id "
                + "JOIN teams a ON a.id = m.team1_id AND a.tournament_id = t.id "
                + "JOIN teams b ON b.id = m.team2_id AND b.tournament_id = t.id "
                + "LEFT JOIN tournament_stages s ON s.id = m.stage_id AND s.tournament_id = t.id "
                + "WHERE t.series_id = ? AND ISNULL(m.is_bye, 0) = 0 AND m.team1_id <> m.team2_id "
                + "AND UPPER(m.status) IN ('FINISHED', 'COMPLETED', 'DONE') "
                + "AND m.score1 >= 0 AND m.score2 >= 0 "
                + "AND ((? <> '' AND (a.partner_participant_id = ? OR b.partner_participant_id = ?)) "
                + "  OR LTRIM(RTRIM(a.raw_name)) COLLATE Latin1_General_100_CI_AS = ? "
                + "  OR LTRIM(RTRIM(b.raw_name)) COLLATE Latin1_General_100_CI_AS = ?) "
                + "OPTION (LOOP JOIN, MAXDOP 1)";
        DBContext db = new DBContext();
        try (Connection conn = db.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, seriesId.trim());
            ps.setString(2, pIdVal);
            ps.setString(3, pIdVal);
            ps.setString(4, pIdVal);
            ps.setNString(5, teamName != null ? teamName.trim() : "");
            ps.setNString(6, teamName != null ? teamName.trim() : "");
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String t1Id = rs.getString("team1_id");
                    String t2Id = rs.getString("team2_id");
                    boolean firstIsSelf = perfByTeamId.containsKey(t1Id);
                    boolean secondIsSelf = perfByTeamId.containsKey(t2Id);
                    if (!firstIsSelf && !secondIsSelf) continue;
                    String selfTeamId = firstIsSelf ? t1Id : t2Id;
                    TourneyPerformanceDTO tPerf = perfByTeamId.get(selfTeamId);
                    int tOrder = tourneyOrderByTeamId.getOrDefault(selfTeamId, 0);

                    int s1 = rs.getInt("score1");
                    int s2 = rs.getInt("score2");
                    int pen1Raw = rs.getInt("penalty1");
                    Integer pen1 = rs.wasNull() || pen1Raw < 0 ? null : pen1Raw;
                    int pen2Raw = rs.getInt("penalty2");
                    Integer pen2 = rs.wasNull() || pen2Raw < 0 ? null : pen2Raw;
                    String wId = rs.getString("winner_id");

                    int winnerSide = 0;
                    if (wId != null) {
                        if (wId.equals(t1Id)) winnerSide = 1;
                        else if (wId.equals(t2Id)) winnerSide = 2;
                    } else {
                        if (s1 > s2) winnerSide = 1;
                        else if (s2 > s1) winnerSide = 2;
                        else if (pen1 != null && pen2 != null) {
                            if (pen1 > pen2) winnerSide = 1;
                            else if (pen2 > pen1) winnerSide = 2;
                        }
                    }

                    String resCode = (winnerSide == 0) ? "D" : ((firstIsSelf ? winnerSide == 1 : winnerSide == 2) ? "W" : "L");
                    String oppName = firstIsSelf ? rs.getString("team2_name") : rs.getString("team1_name");
                    if (oppName != null) oppName = oppName.trim();

                    MatchHistoryDTO mh = new MatchHistoryDTO();
                    mh.setTournamentId(tPerf.getTournamentId());
                    mh.setTournamentName(tPerf.getTournamentName());
                    mh.setTierName(tPerf.getTierName());
                    mh.setTournamentIndex(tOrder);
                    mh.setFinalStageUrl(tPerf.getFinalStageUrl());
                    mh.setStageOrder(rs.getInt("stage_order"));
                    mh.setRoundNumber(rs.getInt("round_number"));
                    mh.setMatchOrder(rs.getInt("match_order"));
                    mh.setRoundLabel(formatProfileRoundLabel(
                            rs.getString("stage_format"),
                            rs.getString("bracket_type"),
                            mh.getRoundNumber(),
                            rs.getInt("round_slots")));
                    mh.setResult(resCode);
                    mh.setTeamScore(firstIsSelf ? s1 : s2);
                    mh.setOpponentScore(firstIsSelf ? s2 : s1);
                    mh.setTeamPenalty(firstIsSelf ? pen1 : pen2);
                    mh.setOpponentPenalty(firstIsSelf ? pen2 : pen1);
                    mh.setOpponentName(oppName != null ? oppName : "");
                    list.add(mh);
                }
            }
            list.sort((a, b) -> {
                if (b.getTournamentIndex() != a.getTournamentIndex()) return Integer.compare(b.getTournamentIndex(), a.getTournamentIndex());
                if (b.getStageOrder() != a.getStageOrder()) return Integer.compare(b.getStageOrder(), a.getStageOrder());
                if (b.getRoundNumber() != a.getRoundNumber()) return Integer.compare(b.getRoundNumber(), a.getRoundNumber());
                return Integer.compare(b.getMatchOrder(), a.getMatchOrder());
            });
            for (int i = 0; i < list.size(); i++) {
                list.get(i).setStt(list.size() - i);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    static String formatProfileRoundLabel(String format, String bracket, int round, int slots) {
        if ("THIRD_PLACE".equalsIgnoreCase(bracket)) return "THIRD PLACE";
        if ("SINGLE_ELIMINATION".equalsIgnoreCase(format) && slots > 0) {
            return switch (slots) {
                case 1 -> "FINAL";
                case 2 -> "SEMI-FINAL";
                case 4 -> "QUARTER-FINAL";
                default -> "ROUND OF " + (slots * 2);
            };
        }
        if (bracket != null && !bracket.isBlank()) {
            return switch (bracket.toUpperCase(java.util.Locale.ROOT)) {
                case "UPPER", "WINNER_BRACKET" -> "UB ROUND " + round;
                case "LOWER", "LOSER_BRACKET" -> "LB ROUND " + round;
                case "GRAND_FINAL" -> "GRAND FINAL";
                case "GRAND_FINAL_RESET" -> "GRAND FINAL 2";
                default -> "ROUND " + round;
            };
        }
        return "ROUND " + round;
    }

    static Map<String, int[]> loadMatchStats(Connection conn, String seriesId) throws java.sql.SQLException {
        return dao.MatchStatisticsDAO.load(conn, seriesId);
    }

}
