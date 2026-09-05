package service;

import dao.DBContext;
import dao.ParticipantDAO;
import dao.SeriesDAO;
import dao.TournamentDAO;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import model.PartnerParticipant;
import model.Series;
import model.Team;
import model.Tournament;

/**
 * Dedicated Service Class for Rolling Window Point Calculation & Expiry (Khấu Trừ Điểm Trượt)
 * Model: ROLLING_WINDOW
 */
public class RollingWindowPointService {

    private static RollingWindowPointService instance;

    public static synchronized RollingWindowPointService getInstance() {
        if (instance == null) {
            instance = new RollingWindowPointService();
        }
        return instance;
    }

    /**
     * DTO for representing comprehensive standing data with expiry details
     */
    public static class RollingStandingDTO {
        private String partnerParticipantId;
        private String teamName;
        private int rank;
        private int totalActivePoints; // Sum of points in active window W
        private int expiredPoints;     // Sum of points deducted from older tournaments
        private int lastTourneyPoints; // Points earned in the latest sub-tournament
        private int activeTourneysCount; // Count of sub-tournaments played in window W
        private int droppedTourneyPoints; // Points earned in the single tournament that just dropped out of window W
        private int netFluctuation;    // Net change = lastTourneyPoints - droppedTourneyPoints
        private int prevRank;          // Rank in the previous milestone (before latest tournament)
        private int rankChange;        // Rank fluctuation: positive = climbed up, negative = dropped, 0 = same

        public String getPartnerParticipantId() { return partnerParticipantId; }
        public void setPartnerParticipantId(String partnerParticipantId) { this.partnerParticipantId = partnerParticipantId; }

        public String getTeamName() { return teamName; }
        public void setTeamName(String teamName) { this.teamName = teamName; }

        public int getRank() { return rank; }
        public void setRank(int rank) { this.rank = rank; }

        public int getPrevRank() { return prevRank; }
        public void setPrevRank(int prevRank) { this.prevRank = prevRank; }

        public int getRankChange() { return rankChange; }
        public void setRankChange(int rankChange) { this.rankChange = rankChange; }

        public int getTotalActivePoints() { return totalActivePoints; }
        public void setTotalActivePoints(int totalActivePoints) { this.totalActivePoints = totalActivePoints; }

        public int getDroppedTourneyPoints() { return droppedTourneyPoints; }
        public void setDroppedTourneyPoints(int droppedTourneyPoints) { this.droppedTourneyPoints = droppedTourneyPoints; }

        public int getExpiredPoints() { return expiredPoints; }
        public void setExpiredPoints(int expiredPoints) { this.expiredPoints = expiredPoints; }

        public int getLastTourneyPoints() { return lastTourneyPoints; }
        public void setLastTourneyPoints(int lastTourneyPoints) { this.lastTourneyPoints = lastTourneyPoints; }

        public int getActiveTourneysCount() { return activeTourneysCount; }
        public void setActiveTourneysCount(int activeTourneysCount) { this.activeTourneysCount = activeTourneysCount; }

        public int getNetFluctuation() { return netFluctuation; }
        public void setNetFluctuation(int netFluctuation) { this.netFluctuation = netFluctuation; }
    }

    /**
     * Main calculation engine enforcing Rolling Window Expiry Rules:
     * - Only the most recent W sub-tournaments contribute to totalActivePoints.
     * - Points from sub-tournaments older than W are moved to expiredPoints (Deducted).
     * - Non-partner guest teams receive 0 points in Series Standings.
     */
    public List<RollingStandingDTO> calculateSeriesStandingsWithExpiry(String seriesId) {
        List<RollingStandingDTO> resultList = new ArrayList<>();
        if (seriesId == null || seriesId.trim().isEmpty()) return resultList;

        SeriesDAO seriesDAO = new SeriesDAO();
        Series series = seriesDAO.getSeriesById(seriesId.trim());
        if (series == null) return resultList;

        int windowSize = (series.getPhaseSize() > 0) ? series.getPhaseSize() : 3;

        // 1. Fetch official partner participants for this series
        List<PartnerParticipant> partners = seriesDAO.getPartnerParticipantsBySeriesId(seriesId.trim());
        if (partners == null || partners.isEmpty()) return resultList;

        Map<String, RollingStandingDTO> dtoMap = new LinkedHashMap<>();
        Map<String, String> nameToIdMap = new HashMap<>();

        for (PartnerParticipant p : partners) {
            if (p.getName() != null) {
                String key = p.getName().trim().toLowerCase();
                RollingStandingDTO dto = new RollingStandingDTO();
                dto.setPartnerParticipantId(p.getId());
                dto.setTeamName(p.getName().trim());
                dto.setTotalActivePoints(0);
                dto.setExpiredPoints(0);
                dto.setLastTourneyPoints(0);
                dto.setDroppedTourneyPoints(0);
                dto.setActiveTourneysCount(0);
                dto.setNetFluctuation(0);
                dto.setPrevRank(0);
                dto.setRankChange(0);

                dtoMap.put(key, dto);
                nameToIdMap.put(key, p.getId());
            }
        }

        // 2. Fetch all sub-tournaments in Series, ordered chronologically
        List<Tournament> allTourneys = seriesDAO.getTournamentsBySeriesId(seriesId.trim());
        if (allTourneys == null || allTourneys.isEmpty()) {
            return new ArrayList<>(dtoMap.values());
        }

        int totalCount = allTourneys.size();
        int activeStartIndex = Math.max(0, totalCount - windowSize);
        int droppedIndex = totalCount - windowSize - 1;

        ParticipantDAO pDao = new ParticipantDAO();
        Map<String, Map<String, Integer>> placementsCache = new HashMap<>();
        Map<String, List<Team>> teamsCache = new HashMap<>();

        // Optional: Calculate previous milestone rankings if totalCount >= 2
        Map<String, Integer> prevRankMap = new HashMap<>();
        if (totalCount >= 2) {
            int prevTotalCount = totalCount - 1;
            int prevActiveStartIndex = Math.max(0, prevTotalCount - windowSize);
            Map<String, Integer> prevPointsMap = new HashMap<>();
            for (String k : dtoMap.keySet()) {
                prevPointsMap.put(k, 0);
            }

            for (int pIdx = prevActiveStartIndex; pIdx < prevTotalCount; pIdx++) {
                Tournament pt = allTourneys.get(pIdx);
                String pCfgRaw = pt.getSeriesPointsConfig();
                if (pCfgRaw == null || pCfgRaw.trim().isEmpty() || !pCfgRaw.trim().startsWith("{")) {
                    pCfgRaw = "{\"1\":500,\"2\":200,\"3-4\":100,\"5-8\":0}";
                }
                Map<String, Integer> posPtsMap = parsePointsConfigJson(pCfgRaw);
                Map<String, Integer> matchPlacements = placementsCache.computeIfAbsent(pt.getId(), id -> pDao.getTournamentPlacements(id));
                List<Team> tourneyTeams = teamsCache.computeIfAbsent(pt.getId(), id -> pDao.getTeamsByTournamentId(id));
                if (tourneyTeams != null) {
                    for (Team tm : tourneyTeams) {
                        if (tm.getRawName() == null) continue;
                        String pk = tm.getRawName().trim().toLowerCase();
                        if (prevPointsMap.containsKey(pk)) {
                            Integer matchPos = matchPlacements.get(tm.getId());
                            if (matchPos == null) {
                                matchPos = matchPlacements.get(pk);
                            }
                            int pos = (matchPos != null && matchPos > 0) ? matchPos : 0;
                            int pts = (pos > 0) ? resolvePointsForPosition(pos, posPtsMap) : 0;
                            prevPointsMap.put(pk, prevPointsMap.get(pk) + pts);
                        }
                    }
                }
            }

            List<String> sortedPrevKeys = new ArrayList<>(prevPointsMap.keySet());
            sortedPrevKeys.sort((a, b) -> Integer.compare(prevPointsMap.get(b), prevPointsMap.get(a)));
            for (int pr = 0; pr < sortedPrevKeys.size(); pr++) {
                prevRankMap.put(sortedPrevKeys.get(pr), pr + 1);
            }
        }

        // 3. Process each sub-tournament and apply window vs expiry logic
        for (int tIdx = 0; tIdx < totalCount; tIdx++) {
            Tournament t = allTourneys.get(tIdx);
            boolean isActiveWindow = (tIdx >= activeStartIndex);
            boolean isLatestTourney = (tIdx == totalCount - 1);
            boolean isDroppedTourney = (tIdx == droppedIndex);

            // Parse position points map JSON e.g. {"1":500,"2":200,"3-4":100}
            String tCfgRaw = t.getSeriesPointsConfig();
            if (tCfgRaw == null || tCfgRaw.trim().isEmpty() || !tCfgRaw.trim().startsWith("{")) {
                tCfgRaw = "{\"1\":500,\"2\":200,\"3-4\":100,\"5-8\":0}";
            }
            Map<String, Integer> posPtsMap = parsePointsConfigJson(tCfgRaw);

            // Retrieve match-based final placements for this tournament
            Map<String, Integer> matchPlacements = placementsCache.computeIfAbsent(t.getId(), id -> pDao.getTournamentPlacements(id));
            List<Team> tourneyTeams = teamsCache.computeIfAbsent(t.getId(), id -> pDao.getTeamsByTournamentId(id));
            if (tourneyTeams == null || tourneyTeams.isEmpty()) continue;

            for (int idx = 0; idx < tourneyTeams.size(); idx++) {
                Team team = tourneyTeams.get(idx);
                if (team.getRawName() == null) continue;
                String teamKey = team.getRawName().trim().toLowerCase();

                // CRUCIAL RULE: ONLY OFFICIAL PARTNER TEAMS EARN POINTS!
                if (dtoMap.containsKey(teamKey)) {
                    RollingStandingDTO dto = dtoMap.get(teamKey);

                    // Every participated tournament in the active window counts as +1 played tournament!
                    if (isActiveWindow) {
                        dto.setActiveTourneysCount(dto.getActiveTourneysCount() + 1);
                    }

                    Integer matchPos = matchPlacements.get(team.getId());
                    if (matchPos == null && team.getRawName() != null) {
                        matchPos = matchPlacements.get(team.getRawName().trim().toLowerCase());
                    }

                    int pos = (matchPos != null && matchPos > 0) ? matchPos : 0;
                    int pts = (pos > 0) ? resolvePointsForPosition(pos, posPtsMap) : 0;

                    if (isActiveWindow) {
                        dto.setTotalActivePoints(dto.getTotalActivePoints() + pts);
                    } else {
                        // Point Expiry / Khấu trừ điểm do vượt cửa sổ trượt W
                        dto.setExpiredPoints(dto.getExpiredPoints() + pts);
                    }

                    if (isDroppedTourney) {
                        dto.setDroppedTourneyPoints(pts);
                    }

                    if (isLatestTourney) {
                        dto.setLastTourneyPoints(pts);
                    }
                }
            }
        }

        // 4. Calculate Net Fluctuation & Rank
        resultList = new ArrayList<>(dtoMap.values());
        resultList.sort((a, b) -> Integer.compare(b.getTotalActivePoints(), a.getTotalActivePoints()));

        for (int r = 0; r < resultList.size(); r++) {
            RollingStandingDTO dto = resultList.get(r);
            int currentRank = r + 1;
            dto.setRank(currentRank);
            dto.setNetFluctuation(dto.getLastTourneyPoints() - dto.getDroppedTourneyPoints());

            if (totalCount >= 2 && !prevRankMap.isEmpty()) {
                String key = (dto.getTeamName() != null) ? dto.getTeamName().trim().toLowerCase() : "";
                int pRank = prevRankMap.getOrDefault(key, currentRank);
                dto.setPrevRank(pRank);
                dto.setRankChange(pRank - currentRank);
            } else {
                dto.setPrevRank(currentRank);
                dto.setRankChange(0);
            }
        }

        return resultList;
    }

    /**
     * Returns tournament points matrix from DB for all tournaments in series.
     * Index i matches tournament i in chronological order.
     * Each Map has key = lowercase team name, value = points earned in that tournament.
     */
    public List<Map<String, Integer>> getTourneyPointsPerTournament(String seriesId) {
        List<Map<String, Integer>> result = new ArrayList<>();
        if (seriesId == null || seriesId.trim().isEmpty()) return result;

        SeriesDAO seriesDAO = new SeriesDAO();
        List<Tournament> allTourneys = seriesDAO.getTournamentsBySeriesId(seriesId.trim());
        if (allTourneys == null || allTourneys.isEmpty()) return result;

        ParticipantDAO pDao = new ParticipantDAO();
        for (Tournament t : allTourneys) {
            Map<String, Integer> ptsMap = new HashMap<>();
            String tCfgRaw = t.getSeriesPointsConfig();
            if (tCfgRaw == null || tCfgRaw.trim().isEmpty() || !tCfgRaw.trim().startsWith("{")) {
                tCfgRaw = "{\"1\":500,\"2\":200,\"3-4\":100,\"5-8\":0}";
            }
            Map<String, Integer> posPtsMap = parsePointsConfigJson(tCfgRaw);
            Map<String, Integer> matchPlacements = pDao.getTournamentPlacements(t.getId());
            List<Team> tourneyTeams = pDao.getTeamsByTournamentId(t.getId());
            if (tourneyTeams != null) {
                for (Team tm : tourneyTeams) {
                    if (tm.getRawName() == null) continue;
                    String pk = tm.getRawName().trim().toLowerCase();
                    Integer matchPos = matchPlacements.get(tm.getId());
                    if (matchPos == null) {
                        matchPos = matchPlacements.get(pk);
                    }
                    int pos = (matchPos != null && matchPos > 0) ? matchPos : 0;
                    int pts = (pos > 0) ? resolvePointsForPosition(pos, posPtsMap) : 0;
                    ptsMap.put(pk, pts);
                }
            }
            result.add(ptsMap);
        }
        return result;
    }

    /**
     * Returns tournament participation matrix from DB for all tournaments in series.
     */
    public List<Map<String, Boolean>> getTourneyParticipationPerTournament(String seriesId) {
        List<Map<String, Boolean>> result = new ArrayList<>();
        if (seriesId == null || seriesId.trim().isEmpty()) return result;

        SeriesDAO seriesDAO = new SeriesDAO();
        List<Tournament> allTourneys = seriesDAO.getTournamentsBySeriesId(seriesId.trim());
        if (allTourneys == null || allTourneys.isEmpty()) return result;

        ParticipantDAO pDao = new ParticipantDAO();
        for (Tournament t : allTourneys) {
            Map<String, Boolean> partMap = new HashMap<>();
            List<Team> tourneyTeams = pDao.getTeamsByTournamentId(t.getId());
            if (tourneyTeams != null) {
                for (Team tm : tourneyTeams) {
                    if (tm.getRawName() != null) {
                        partMap.put(tm.getRawName().trim().toLowerCase(), true);
                    }
                }
            }
            result.add(partMap);
        }
        return result;
    }

    /**
     * Recalculates and persists the updated standings & expired points into the database
     */
    public boolean recalculateAndPersistStandings(String seriesId) {
        if (seriesId == null || seriesId.trim().isEmpty()) return false;

        List<RollingStandingDTO> standings = calculateSeriesStandingsWithExpiry(seriesId);
        if (standings.isEmpty()) return false;

        DBContext db = new DBContext();
        String updateSql = "UPDATE series_standings SET total_rolling_points = ?, updated_at = CURRENT_TIMESTAMP WHERE series_id = ? AND partner_participant_id = ?";

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(updateSql)) {
            conn.setAutoCommit(false);
            for (RollingStandingDTO dto : standings) {
                ps.setInt(1, dto.getTotalActivePoints());
                ps.setString(2, seriesId.trim());
                ps.setString(3, dto.getPartnerParticipantId());
                ps.addBatch();
            }
            ps.executeBatch();
            conn.commit();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * DTO for representing the team's highest BXH rank and the first tournament milestone where it was achieved
     */
    public static class HighestRankDTO {
        private int highestRank;
        private String tournamentId;
        private String tournamentName;

        public HighestRankDTO(int highestRank, String tournamentId, String tournamentName) {
            this.highestRank = highestRank;
            this.tournamentId = tournamentId;
            this.tournamentName = tournamentName;
        }

        public int getHighestRank() { return highestRank; }
        public String getTournamentId() { return tournamentId; }
        public String getTournamentName() { return tournamentName; }
    }

    /**
     * Calculates the highest BXH rank ever achieved by a team across all tournament milestones in a series.
     */
    public int calculateHighestRankAcrossHistory(String seriesId, String teamName) {
        HighestRankDTO dto = calculateHighestRankWithTourneyAcrossHistory(seriesId, teamName);
        return (dto != null) ? dto.getHighestRank() : 0;
    }

    /**
     * Calculates the highest BXH rank and the FIRST sub-tournament where the team reached that rank.
     */
    public HighestRankDTO calculateHighestRankWithTourneyAcrossHistory(String seriesId, String teamName) {
        if (seriesId == null || seriesId.trim().isEmpty() || teamName == null || teamName.trim().isEmpty()) {
            return new HighestRankDTO(0, "", "");
        }

        SeriesDAO seriesDAO = new SeriesDAO();
        Series series = seriesDAO.getSeriesById(seriesId.trim());
        if (series == null) return new HighestRankDTO(0, "", "");

        int windowSize = (series.getPhaseSize() > 0) ? series.getPhaseSize() : 3;

        List<PartnerParticipant> partners = seriesDAO.getPartnerParticipantsBySeriesId(seriesId.trim());
        if (partners == null || partners.isEmpty()) return new HighestRankDTO(0, "", "");

        String targetKey = teamName.trim().toLowerCase();
        boolean isPartner = false;
        for (PartnerParticipant p : partners) {
            if (p.getName() != null && p.getName().trim().equalsIgnoreCase(teamName.trim())) {
                isPartner = true;
                break;
            }
        }
        if (!isPartner) return new HighestRankDTO(0, "", "");

        List<Tournament> allTourneys = seriesDAO.getTournamentsBySeriesId(seriesId.trim());
        if (allTourneys == null || allTourneys.isEmpty()) {
            return new HighestRankDTO(0, "", "");
        }

        ParticipantDAO pDao = new ParticipantDAO();
        int totalTourneys = allTourneys.size();
        List<Map<String, Integer>> tourneyPointsList = new ArrayList<>();

        for (int tIdx = 0; tIdx < totalTourneys; tIdx++) {
            Tournament t = allTourneys.get(tIdx);
            String tCfgRaw = t.getSeriesPointsConfig();
            if (tCfgRaw == null || tCfgRaw.trim().isEmpty() || !tCfgRaw.trim().startsWith("{")) {
                tCfgRaw = "{\"1\":500,\"2\":200,\"3-4\":100,\"5-8\":0}";
            }
            Map<String, Integer> posPtsMap = parsePointsConfigJson(tCfgRaw);
            Map<String, Integer> matchPlacements = pDao.getTournamentPlacements(t.getId());
            List<Team> tourneyTeams = pDao.getTeamsByTournamentId(t.getId());

            Map<String, Integer> ptsForTourney = new HashMap<>();
            if (tourneyTeams != null) {
                for (Team team : tourneyTeams) {
                    if (team.getRawName() == null) continue;
                    String k = team.getRawName().trim().toLowerCase();
                    Integer matchPos = matchPlacements.get(team.getId());
                    if (matchPos == null) {
                        matchPos = matchPlacements.get(k);
                    }
                    if (matchPos != null && matchPos > 0) {
                        int pts = resolvePointsForPosition(matchPos, posPtsMap);
                        ptsForTourney.put(k, pts);
                    }
                }
            }
            tourneyPointsList.add(ptsForTourney);
        }

        int highestRank = 999;
        String firstTourneyId = "";
        String firstTourneyName = "";

        // Compute standings at each milestone
        for (int step = 0; step < totalTourneys; step++) {
            int activeStart = Math.max(0, step - windowSize + 1);
            List<Map.Entry<String, Integer>> standingsAtStep = new ArrayList<>();

            for (PartnerParticipant p : partners) {
                if (p.getName() == null) continue;
                String pk = p.getName().trim().toLowerCase();
                int totalPts = 0;
                for (int w = activeStart; w <= step; w++) {
                    Integer pts = tourneyPointsList.get(w).get(pk);
                    if (pts != null) {
                        totalPts += pts;
                    }
                }
                standingsAtStep.add(new java.util.AbstractMap.SimpleEntry<>(pk, totalPts));
            }

            standingsAtStep.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));

            for (int r = 0; r < standingsAtStep.size(); r++) {
                Map.Entry<String, Integer> entry = standingsAtStep.get(r);
                if (entry.getKey().equalsIgnoreCase(targetKey)) {
                    int rankAtStep = r + 1;
                    if (entry.getValue() > 0) {
                        if (rankAtStep < highestRank) {
                            highestRank = rankAtStep;
                            firstTourneyId = allTourneys.get(step).getId();
                            firstTourneyName = allTourneys.get(step).getName();
                        }
                    }
                    break;
                }
            }
        }

        if (highestRank == 999) {
            return new HighestRankDTO(0, "", "");
        }
        return new HighestRankDTO(highestRank, firstTourneyId, firstTourneyName);
    }

    // Helper: Parse JSON string into Map
    public Map<String, Integer> parsePointsConfigJson(String jsonStr) {
        Map<String, Integer> map = new HashMap<>();
        if (jsonStr == null || jsonStr.trim().isEmpty()) return map;
        try {
            String clean = jsonStr.trim().replaceAll("[{}\"]", "");
            String[] pairs = clean.split(",");
            for (String pair : pairs) {
                String[] kv = pair.split(":");
                if (kv.length == 2) {
                    map.put(kv[0].trim(), Integer.parseInt(kv[1].trim()));
                }
            }
        } catch (Exception ignore) {}
        return map;
    }

    // Helper: Resolve points awarded for a given position
    public int resolvePointsForPosition(int pos, Map<String, Integer> posPtsMap) {
        if (posPtsMap.containsKey(String.valueOf(pos))) {
            return posPtsMap.get(String.valueOf(pos));
        } else if (pos == 1 && posPtsMap.containsKey("1")) {
            return posPtsMap.get("1");
        } else if (pos == 2 && posPtsMap.containsKey("2")) {
            return posPtsMap.get("2");
        } else if (pos == 3 && posPtsMap.containsKey("3")) {
            return posPtsMap.get("3");
        } else if (pos == 4 && posPtsMap.containsKey("4")) {
            return posPtsMap.get("4");
        } else if (pos >= 3 && pos <= 4 && posPtsMap.containsKey("3-4")) {
            return posPtsMap.get("3-4");
        } else if (pos >= 5 && pos <= 6 && posPtsMap.containsKey("5-6")) {
            return posPtsMap.get("5-6");
        } else if (pos >= 7 && pos <= 8 && posPtsMap.containsKey("7-8")) {
            return posPtsMap.get("7-8");
        } else if (pos >= 5 && pos <= 8 && posPtsMap.containsKey("5-8")) {
            return posPtsMap.get("5-8");
        } else if (pos >= 9 && pos <= 12 && posPtsMap.containsKey("9-12")) {
            return posPtsMap.get("9-12");
        } else if (pos >= 13 && pos <= 16 && posPtsMap.containsKey("13-16")) {
            return posPtsMap.get("13-16");
        } else if (pos >= 9 && pos <= 16 && posPtsMap.containsKey("9-16")) {
            return posPtsMap.get("9-16");
        } else if (pos >= 17 && pos <= 32 && posPtsMap.containsKey("17-32")) {
            return posPtsMap.get("17-32");
        } else if (pos >= 33 && pos <= 64 && posPtsMap.containsKey("33-64")) {
            return posPtsMap.get("33-64");
        } else if (pos >= 65 && pos <= 128 && posPtsMap.containsKey("65-128")) {
            return posPtsMap.get("65-128");
        } else if (pos >= 9 && pos <= 11 && posPtsMap.containsKey("swiss_2-3")) {
            return posPtsMap.get("swiss_2-3");
        } else if (pos >= 12 && pos <= 14 && posPtsMap.containsKey("swiss_1-3")) {
            return posPtsMap.get("swiss_1-3");
        } else if (pos >= 15 && pos <= 16 && posPtsMap.containsKey("swiss_0-3")) {
            return posPtsMap.get("swiss_0-3");
        } else if (posPtsMap.containsKey("stage1_eliminated")) {
            return posPtsMap.get("stage1_eliminated");
        } else if (posPtsMap.containsKey("swiss_2-3") && pos >= 9) {
            return posPtsMap.get("swiss_2-3");
        }
        return 0;
    }
}
