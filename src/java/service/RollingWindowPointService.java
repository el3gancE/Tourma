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

        public String getPartnerParticipantId() { return partnerParticipantId; }
        public void setPartnerParticipantId(String partnerParticipantId) { this.partnerParticipantId = partnerParticipantId; }

        public String getTeamName() { return teamName; }
        public void setTeamName(String teamName) { this.teamName = teamName; }

        public int getRank() { return rank; }
        public void setRank(int rank) { this.rank = rank; }

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

                dtoMap.put(key, dto);
                nameToIdMap.put(key, p.getId());
            }
        }

        // 2. Fetch all sub-tournaments in Series, ordered chronologically
        TournamentDAO tDao = new TournamentDAO();
        List<Tournament> allTourneys = tDao.getTournamentsBySeriesId(seriesId.trim());
        if (allTourneys == null || allTourneys.isEmpty()) {
            return new ArrayList<>(dtoMap.values());
        }

        // Filter tournaments that have points configuration
        List<Tournament> configuredTourneys = new ArrayList<>();
        for (Tournament t : allTourneys) {
            if (t.getSeriesPointsConfig() != null && !t.getSeriesPointsConfig().trim().isEmpty()) {
                configuredTourneys.add(t);
            }
        }

        int totalCount = configuredTourneys.size();
        int activeStartIndex = Math.max(0, totalCount - windowSize);
        int droppedIndex = totalCount - windowSize - 1;

        ParticipantDAO pDao = new ParticipantDAO();

        // 3. Process each sub-tournament and apply window vs expiry logic
        for (int tIdx = 0; tIdx < totalCount; tIdx++) {
            Tournament t = configuredTourneys.get(tIdx);
            boolean isActiveWindow = (tIdx >= activeStartIndex);
            boolean isLatestTourney = (tIdx == totalCount - 1);
            boolean isDroppedTourney = (tIdx == droppedIndex);

            // Parse position points map JSON e.g. {"1":500,"2":200,"3-4":100}
            Map<String, Integer> posPtsMap = parsePointsConfigJson(t.getSeriesPointsConfig());

            // Retrieve match-based final placements for this tournament
            Map<String, Integer> matchPlacements = pDao.getTournamentPlacements(t.getId());

            List<Team> tourneyTeams = pDao.getTeamsByTournamentId(t.getId());
            if (tourneyTeams == null || tourneyTeams.isEmpty()) continue;

            for (int idx = 0; idx < tourneyTeams.size(); idx++) {
                Team team = tourneyTeams.get(idx);
                if (team.getRawName() == null) continue;
                String teamKey = team.getRawName().trim().toLowerCase();

                // CRUCIAL RULE: ONLY OFFICIAL PARTNER TEAMS EARN POINTS!
                if (dtoMap.containsKey(teamKey)) {
                    RollingStandingDTO dto = dtoMap.get(teamKey);

                    Integer matchPos = matchPlacements.get(team.getId());
                    if (matchPos == null && team.getRawName() != null) {
                        matchPos = matchPlacements.get(team.getRawName().trim().toLowerCase());
                    }

                    if (matchPos == null || matchPos <= 0) {
                        continue;
                    }
                    int pos = matchPos;

                    int pts = resolvePointsForPosition(pos, posPtsMap);

                    if (isActiveWindow) {
                        dto.setTotalActivePoints(dto.getTotalActivePoints() + pts);
                        if (pts > 0) {
                            dto.setActiveTourneysCount(dto.getActiveTourneysCount() + 1);
                        }
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
            dto.setRank(r + 1);
            dto.setNetFluctuation(dto.getLastTourneyPoints() - dto.getDroppedTourneyPoints());
        }

        return resultList;
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

    // Helper: Parse JSON string into Map
    private Map<String, Integer> parsePointsConfigJson(String jsonStr) {
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
    private int resolvePointsForPosition(int pos, Map<String, Integer> posPtsMap) {
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
        } else if (posPtsMap.containsKey("stage1_eliminated")) {
            return posPtsMap.get("stage1_eliminated");
        } else if (posPtsMap.containsKey("swiss_2-3") && pos >= 9) {
            return posPtsMap.get("swiss_2-3");
        }
        return 0;
    }
}
