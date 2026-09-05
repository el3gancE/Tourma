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

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        String seriesId = request.getParameter("seriesId");
        if (seriesId == null || seriesId.trim().isEmpty()) {
            seriesId = request.getParameter("id");
        }
        String teamNameParam = request.getParameter("teamName");
        String partnerIdParam = request.getParameter("partnerId");

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

        if (series != null) {
            List<PartnerParticipant> partners = seriesDAO.getPartnerParticipantsBySeriesId(series.getId());
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

            // Fetch all tournaments in series
            List<Tournament> tourneys = seriesDAO.getTournamentsBySeriesId(series.getId());
            if (tourneys != null) {
                for (int tIdx = 0; tIdx < tourneys.size(); tIdx++) {
                    Tournament t = tourneys.get(tIdx);
                    List<Team> teams = participantDAO.getTeamsByTournamentId(t.getId());
                    Team teamInTourney = null;
                    if (teams != null) {
                        for (Team tm : teams) {
                            if (partner != null && partner.getId() != null && partner.getId().equalsIgnoreCase(tm.getPartnerParticipantId())) {
                                teamInTourney = tm;
                                break;
                            }
                            if (tm.getName() != null && tm.getName().trim().equalsIgnoreCase(teamName)) {
                                teamInTourney = tm;
                                break;
                            }
                            if (tm.getRawName() != null && tm.getRawName().trim().equalsIgnoreCase(teamName)) {
                                teamInTourney = tm;
                                break;
                            }
                        }
                    }

                    // Query matches in this tournament
                    int tWins = 0;
                    int tLosses = 0;
                    boolean playedInTourney = false;

                    String matchSql = "SELECT * FROM matches WHERE tournament_id = ?";
                    DBContext db = new DBContext();
                    try (Connection conn = db.getConnection();
                         PreparedStatement ps = conn.prepareStatement(matchSql)) {
                        ps.setString(1, t.getId());
                        try (ResultSet rs = ps.executeQuery()) {
                            while (rs.next()) {
                                String t1 = rs.getString("team1_id");
                                String t2 = rs.getString("team2_id");
                                String t1Name = null, t2Name = null;
                                try { t1Name = rs.getString("team1_name"); } catch (Exception ignore) {}
                                try { t2Name = rs.getString("team2_name"); } catch (Exception ignore) {}

                                boolean isT1 = false;
                                boolean isT2 = false;

                                if (teamInTourney != null) {
                                    if (teamInTourney.getId() != null) {
                                        if (teamInTourney.getId().equalsIgnoreCase(t1)) isT1 = true;
                                        if (teamInTourney.getId().equalsIgnoreCase(t2)) isT2 = true;
                                    }
                                    if (teamInTourney.getRawName() != null) {
                                        if (teamInTourney.getRawName().equalsIgnoreCase(t1Name) || teamInTourney.getRawName().equalsIgnoreCase(t1)) isT1 = true;
                                        if (teamInTourney.getRawName().equalsIgnoreCase(t2Name) || teamInTourney.getRawName().equalsIgnoreCase(t2)) isT2 = true;
                                    }
                                }
                                if (!isT1 && !isT2 && !teamName.isEmpty()) {
                                    if (teamName.equalsIgnoreCase(t1Name) || teamName.equalsIgnoreCase(t1)) isT1 = true;
                                    if (teamName.equalsIgnoreCase(t2Name) || teamName.equalsIgnoreCase(t2)) isT2 = true;
                                }

                                if (!isT1 && !isT2) continue;
                                playedInTourney = true;

                                String wId = null;
                                try { wId = rs.getString("winner_id"); } catch (Exception ignore) {}
                                if (wId == null) {
                                    try { wId = rs.getString("winner_team_id"); } catch (Exception ignore) {}
                                }

                                int s1 = -1, s2 = -1;
                                try { s1 = rs.getInt("score1"); if (rs.wasNull()) s1 = -1; } catch (Exception ignore) {}
                                if (s1 == -1) {
                                    try { s1 = rs.getInt("team1_score"); if (rs.wasNull()) s1 = -1; } catch (Exception ignore) {}
                                }
                                try { s2 = rs.getInt("score2"); if (rs.wasNull()) s2 = -1; } catch (Exception ignore) {}
                                if (s2 == -1) {
                                    try { s2 = rs.getInt("team2_score"); if (rs.wasNull()) s2 = -1; } catch (Exception ignore) {}
                                }

                                if (wId == null && s1 >= 0 && s2 >= 0 && s1 != s2) {
                                    wId = (s1 > s2) ? t1 : t2;
                                    if (wId == null && t1Name != null && t2Name != null) {
                                        wId = (s1 > s2) ? t1Name : t2Name;
                                    }
                                }

                                if (wId != null) {
                                    boolean won = false;
                                    if (isT1 && (wId.equalsIgnoreCase(t1) || (t1Name != null && wId.equalsIgnoreCase(t1Name)) || (teamInTourney != null && wId.equalsIgnoreCase(teamInTourney.getId())) || wId.equalsIgnoreCase(teamName))) {
                                        won = true;
                                    } else if (isT2 && (wId.equalsIgnoreCase(t2) || (t2Name != null && wId.equalsIgnoreCase(t2Name)) || (teamInTourney != null && wId.equalsIgnoreCase(teamInTourney.getId())) || wId.equalsIgnoreCase(teamName))) {
                                        won = true;
                                    }
                                    if (won) {
                                        tWins++;
                                    } else {
                                        tLosses++;
                                    }
                                }
                            }
                        }
                    } catch (Exception ignore) {}

                    if (playedInTourney) {
                        totalTourneysPlayed++;
                        totalWins += tWins;
                        totalLosses += tLosses;

                        // Determine achievement & rank using exact bracket placement
                        Map<String, Integer> matchPlacements = participantDAO.getTournamentPlacements(t.getId());
                        Integer mPos = (teamInTourney != null) ? matchPlacements.get(teamInTourney.getId()) : null;
                        if (mPos == null && teamInTourney != null && teamInTourney.getRawName() != null) {
                            mPos = matchPlacements.get(teamInTourney.getRawName().trim().toLowerCase());
                        }
                        if (mPos == null) {
                            mPos = matchPlacements.get(teamName.trim().toLowerCase());
                        }

                        String achievement = "Vòng Bảng";
                        int tourneyRank = (mPos != null && mPos > 0) ? mPos : 16;
                        boolean isChamp = false;

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
                        } else {
                            if (tLosses > 0 && tWins >= 3) {
                                achievement = "Á Quân";
                                tourneyRank = 2;
                            } else if (tWins >= 2) {
                                achievement = "Bán Kết";
                                tourneyRank = 4;
                            } else if (tWins >= 1) {
                                achievement = "Tứ Kết";
                                tourneyRank = 8;
                            } else {
                                achievement = "Vòng Bảng";
                            }
                        }

                        // Format display name (abbreviated: SE, DE, SW, RR, GS, or S1 ➔ S2)
                        boolean isMulti = "MULTI_STAGE".equalsIgnoreCase(t.getTournamentType());
                        List<String> stgFormats = tournamentDAO.getStageFormats(t.getId());
                        String s1Fmt = (stgFormats != null && !stgFormats.isEmpty()) ? stgFormats.get(0) : t.getFormat();
                        String s2Fmt = (stgFormats != null && stgFormats.size() > 1) ? stgFormats.get(1) : "SINGLE_ELIMINATION";

                        String fmtLabel = getFormatShortCode(s1Fmt);
                        if (isMulti) {
                            fmtLabel = getFormatShortCode(s1Fmt) + " ➔ " + getFormatShortCode(s2Fmt);
                        }

                        String finalUrl = getTournamentFinalStageUrl(request.getContextPath(), t.getId(), series.getId(), isMulti, s1Fmt, s2Fmt);

                        // Points calculation for this tournament
                        int ptsEarned = 0;
                        try {
                            ptsEarned = calculatePointsForTournament(t, tourneyRank);
                        } catch (Exception ignore) {}
                        totalAccumulatedPoints += ptsEarned;

                        if (isChamp) {
                            champCount++;
                            String tier = (t.getTierName() != null && !t.getTierName().isEmpty()) ? t.getTierName().toUpperCase() : "A";
                            championTourneys.add(new ChampionTournamentDTO(t.getId(), t.getName(), tier, finalUrl));
                        } else if ("Á Quân".equals(achievement)) {
                            runnerUpCount++;
                        } else if ("Bán Kết".equals(achievement)) {
                            semiCount++;
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
                    }
                }
            }

            // Calculate exact highest rank and the first milestone tournament where it was reached
            RollingWindowPointService.HighestRankDTO hDto = RollingWindowPointService.getInstance().calculateHighestRankWithTourneyAcrossHistory(series.getId(), teamName);
            highestRank = (hDto != null) ? hDto.getHighestRank() : 0;
            if (hDto != null && hDto.getTournamentId() != null && !hDto.getTournamentId().isEmpty()) {
                highestRankTourneyName = hDto.getTournamentName();
                Tournament hTourney = tournamentDAO.getTournamentById(hDto.getTournamentId());
                if (hTourney != null) {
                    highestRankTourneyTier = (hTourney.getTierName() != null) ? hTourney.getTierName().toUpperCase() : "A";
                    boolean isMultiH = "MULTI_STAGE".equalsIgnoreCase(hTourney.getTournamentType());
                    List<String> stgFormatsH = tournamentDAO.getStageFormats(hTourney.getId());
                    String s1FmtH = (stgFormatsH != null && !stgFormatsH.isEmpty()) ? stgFormatsH.get(0) : hTourney.getFormat();
                    String s2FmtH = (stgFormatsH != null && stgFormatsH.size() > 1) ? stgFormatsH.get(1) : "SINGLE_ELIMINATION";
                    highestRankTourneyUrl = getTournamentFinalStageUrl(request.getContextPath(), hTourney.getId(), series.getId(), isMultiH, s1FmtH, s2FmtH);
                }
            }

            java.util.Collections.reverse(performanceList);
            java.util.Collections.reverse(championTourneys);
        }

        if (highestRank == 0 && currentRank > 0 && currentPoints > 0) {
            highestRank = currentRank;
        }
        if (totalAccumulatedPoints < currentPoints) {
            totalAccumulatedPoints = currentPoints;
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

        List<Tournament> tournamentsList = (series != null) ? seriesDAO.getTournamentsBySeriesId(series.getId()) : new ArrayList<>();
        List<PartnerParticipant> partnersList = (series != null) ? seriesDAO.getPartnerParticipantsBySeriesId(series.getId()) : new ArrayList<>();
        Map<String, List<String>> stageFormatsMap = new HashMap<>();
        if (tournamentsList != null) {
            for (Tournament t : tournamentsList) {
                stageFormatsMap.put(t.getId(), tournamentDAO.getStageFormats(t.getId()));
            }
        }
        request.setAttribute("tournamentsList", tournamentsList);
        request.setAttribute("partnersList", partnersList);
        request.setAttribute("stageFormatsMap", stageFormatsMap);

        request.getRequestDispatcher("/common/team-profile.jsp").forward(request, response);
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

    private int calculatePointsForTournament(Tournament t, int rank) {
        if (t == null) return 0;
        RollingWindowPointService svc = RollingWindowPointService.getInstance();
        Map<String, Integer> config = svc.parsePointsConfigJson(t.getSeriesPointsConfig());
        if (!config.isEmpty()) {
            return svc.resolvePointsForPosition(rank, config);
        }
        int champPts = (t.getSeriesRewardPoints() > 0) ? t.getSeriesRewardPoints() : 100;
        if (rank == 1) return champPts;
        if (rank == 2) return (int) Math.round(champPts * 0.70);
        if (rank <= 4) return (int) Math.round(champPts * 0.40);
        if (rank <= 8) return (int) Math.round(champPts * 0.20);
        return (int) Math.round(champPts * 0.10);
    }

    private String getFormatShortCode(String fmt) {
        if (fmt == null || fmt.trim().isEmpty()) return "SE";
        String f = fmt.toUpperCase().trim();
        if (f.contains("DOUBLE") || "DE".equals(f)) return "DE";
        if (f.contains("ROUND") || "RR".equals(f)) return "RR";
        if (f.contains("GROUP") || "GS".equals(f)) return "GS";
        if (f.contains("SWISS") || "SW".equals(f)) return "SW";
        return "SE";
    }
}
