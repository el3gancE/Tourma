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
import java.util.UUID;
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

    // Global in-memory caches to make standings and profile navigation instantaneous (<50ms)
    private static final java.util.Map<String, java.util.Map<String, Integer>> GLOBAL_PLACEMENTS_CACHE = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Map<String, List<Team>> GLOBAL_TEAMS_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    private static class CacheEntry<T> {
        final T data;
        final long timestamp;
        CacheEntry(T data) {
            this.data = data;
            this.timestamp = System.currentTimeMillis();
        }
        boolean isExpired(long ttlMs) {
            return (System.currentTimeMillis() - timestamp) > ttlMs;
        }
    }

    private static final java.util.Map<String, CacheEntry<List<RollingStandingDTO>>> STANDINGS_CACHE = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Map<String, CacheEntry<List<Map<String, Integer>>>> TOURNEY_POINTS_CACHE = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Map<String, CacheEntry<List<Map<String, Boolean>>>> TOURNEY_PART_CACHE = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Map<String, CacheEntry<HighestRankDTO>> HIGHEST_RANK_CACHE = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Set<String> PRELOADED_SERIES = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public static void clearAllCaches() {
        GLOBAL_PLACEMENTS_CACHE.clear();
        GLOBAL_TEAMS_CACHE.clear();
        STANDINGS_CACHE.clear();
        TOURNEY_POINTS_CACHE.clear();
        TOURNEY_PART_CACHE.clear();
        HIGHEST_RANK_CACHE.clear();
        PRELOADED_SERIES.clear();
        TournamentPlacementService.clearCaches();
        dao.TournamentDAO.clearTournamentCaches();
        dao.SeriesDAO.clearSeriesCaches();
    }

    public void putCachedTournamentPlacements(String tourneyId, Map<String, Integer> placements) {
        if (tourneyId != null && placements != null) {
            GLOBAL_PLACEMENTS_CACHE.put(tourneyId.trim(), placements);
        }
    }

    public void putCachedTeams(String tourneyId, List<Team> teams) {
        if (tourneyId != null && teams != null) {
            GLOBAL_TEAMS_CACHE.put(tourneyId.trim(), teams);
        }
    }

    public void preloadSeriesData(String seriesId) {
        if (seriesId == null || seriesId.trim().isEmpty()) return;
        String sid = seriesId.trim();
        if (PRELOADED_SERIES.contains(sid)) return;

        // 1. Bulk prefetch all stage formats in 1 query
        new dao.TournamentDAO().getStageFormatsBySeriesId(sid);

        // 2. Bulk prefetch all teams in 1 query
        Map<String, List<Team>> teamsByTourney = new dao.ParticipantDAO().getTeamsBySeriesId(sid);
        if (teamsByTourney != null) {
            for (Map.Entry<String, List<Team>> entry : teamsByTourney.entrySet()) {
                GLOBAL_TEAMS_CACHE.put(entry.getKey(), entry.getValue());
            }
        }

        // 3. Resolve and cache each tournament's stage-specific result categories
        TournamentPlacementService.getInstance().preloadSeriesPlacements(sid);
        PRELOADED_SERIES.add(sid);
    }

    public Map<String, Integer> getCachedTournamentPlacements(String tourneyId) {
        if (tourneyId == null || tourneyId.trim().isEmpty()) return new HashMap<>();
        return GLOBAL_PLACEMENTS_CACHE.computeIfAbsent(tourneyId.trim(), id -> TournamentPlacementService.getInstance().getTournamentPlacements(id));
    }

    public List<Team> getCachedTeamsByTournamentId(String tourneyId) {
        if (tourneyId == null || tourneyId.trim().isEmpty()) return new ArrayList<>();
        return GLOBAL_TEAMS_CACHE.computeIfAbsent(tourneyId.trim(), id -> new ParticipantDAO().getTeamsByTournamentId(id));
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
        return calculateSeriesStandingsWithExpiry(seriesId, null);
    }

    /** A historical milestone is read-only and must not replace the latest caches. */
    public List<RollingStandingDTO> calculateSeriesStandingsWithExpiry(String seriesId, Integer milestoneIndex) {
        List<RollingStandingDTO> resultList = new ArrayList<>();
        if (seriesId == null || seriesId.trim().isEmpty()) return resultList;

        String sKey = seriesId.trim();
        CacheEntry<List<RollingStandingDTO>> cached = STANDINGS_CACHE.get(sKey);
        if (milestoneIndex == null && cached != null && !cached.isExpired(60000L)) {
            return cached.data;
        }

        // 0. Preload stage formats, teams and result categories if the series cache is cold
        preloadSeriesData(sKey);

        SeriesDAO seriesDAO = new SeriesDAO();
        Series series = seriesDAO.getSeriesById(sKey);
        if (series == null) return resultList;

        int windowSize = (series.getPhaseSize() > 0) ? series.getPhaseSize() : 3;

        // 1. Fetch official partner participants for this series
        List<PartnerParticipant> partners = seriesDAO.getPartnerParticipantsBySeriesId(sKey);
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
        List<Tournament> allTourneys = seriesDAO.getTournamentsBySeriesId(sKey);
        if (allTourneys == null || allTourneys.isEmpty()) {
            return new ArrayList<>(dtoMap.values());
        }

        if (milestoneIndex != null) {
            if (milestoneIndex < 0 || milestoneIndex >= allTourneys.size()) {
                throw new IllegalArgumentException("Invalid standings milestone");
            }
            allTourneys = new ArrayList<>(allTourneys.subList(0, milestoneIndex + 1));
        }

        int totalCount = allTourneys.size();
        int activeStartIndex = Math.max(0, totalCount - windowSize);
        int droppedIndex = totalCount - windowSize - 1;

        List<Map<String, Integer>> allTourneyPointsList = new ArrayList<>();
        List<Map<String, Boolean>> allTourneyPartList = new ArrayList<>();

        // 3. Process each sub-tournament and apply window vs expiry logic
        for (int tIdx = 0; tIdx < totalCount; tIdx++) {
            Tournament t = allTourneys.get(tIdx);
            boolean isActiveWindow = (tIdx >= activeStartIndex);
            boolean isLatestTourney = (tIdx == totalCount - 1);
            boolean isDroppedTourney = (tIdx == droppedIndex);

            String tCfgRaw = t.getSeriesPointsConfig();
            if (tCfgRaw == null || tCfgRaw.trim().isEmpty() || !tCfgRaw.trim().startsWith("{")) {
                tCfgRaw = buildFallbackConfig(t);
            }
            Map<String, Integer> posPtsMap = parsePointsConfigJson(tCfgRaw);

            List<Team> tourneyTeams = getCachedTeamsByTournamentId(t.getId());

            Map<String, Integer> tourneyPtsMap = new HashMap<>();
            Map<String, Boolean> tourneyPartMap = new HashMap<>();

            if (tourneyTeams != null) {
                for (int idx = 0; idx < tourneyTeams.size(); idx++) {
                    Team team = tourneyTeams.get(idx);
                    if (team.getRawName() == null) continue;
                    String teamKey = team.getRawName().trim().toLowerCase();

                    tourneyPartMap.put(teamKey, true);
                    if (team.getNormalizedName() != null) {
                        tourneyPartMap.put(team.getNormalizedName().trim().toLowerCase(), true);
                    }

                    TournamentPlacementService.PlacementResult result = TournamentPlacementService.getInstance()
                            .getTournamentResults(t.getId()).get(team.getId());
                    int pts = resolvePointsForResult(result, posPtsMap);

                    tourneyPtsMap.put(teamKey, pts);

                    // CRUCIAL RULE: ONLY OFFICIAL PARTNER TEAMS EARN POINTS!
                    if (dtoMap.containsKey(teamKey)) {
                        RollingStandingDTO dto = dtoMap.get(teamKey);

                        if (isActiveWindow) {
                            dto.setActiveTourneysCount(dto.getActiveTourneysCount() + 1);
                            dto.setTotalActivePoints(dto.getTotalActivePoints() + pts);
                        } else {
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

            allTourneyPointsList.add(tourneyPtsMap);
            allTourneyPartList.add(tourneyPartMap);
        }

        if (milestoneIndex == null) {
            TOURNEY_POINTS_CACHE.put(sKey, new CacheEntry<>(allTourneyPointsList));
            TOURNEY_PART_CACHE.put(sKey, new CacheEntry<>(allTourneyPartList));
        }

        // 4. Calculate previous milestone rankings if totalCount >= 2 using already computed points matrix
        Map<String, Integer> prevRankMap = new HashMap<>();
        if (totalCount >= 2) {
            int prevTotalCount = totalCount - 1;
            int prevActiveStartIndex = Math.max(0, prevTotalCount - windowSize);
            Map<String, Integer> prevPointsMap = new HashMap<>();
            for (String k : dtoMap.keySet()) {
                int pts = 0;
                for (int pIdx = prevActiveStartIndex; pIdx < prevTotalCount; pIdx++) {
                    Integer p = allTourneyPointsList.get(pIdx).get(k);
                    if (p != null) pts += p;
                }
                prevPointsMap.put(k, pts);
            }

            List<String> sortedPrevKeys = new ArrayList<>(prevPointsMap.keySet());
            sortedPrevKeys.sort((a, b) -> Integer.compare(prevPointsMap.get(b), prevPointsMap.get(a)));
            for (int pr = 0; pr < sortedPrevKeys.size(); pr++) {
                prevRankMap.put(sortedPrevKeys.get(pr), pr + 1);
            }
        }

        // 5. Calculate Net Fluctuation & Rank
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

        if (milestoneIndex == null) STANDINGS_CACHE.put(sKey, new CacheEntry<>(resultList));
        return resultList;
    }

    /**
     * Returns tournament points matrix from DB for all tournaments in series.
     * Index i matches tournament i in chronological order.
     * Each Map has key = lowercase team name, value = points earned in that tournament.
     */
    public List<Map<String, Integer>> getTourneyPointsPerTournament(String seriesId) {
        if (seriesId == null || seriesId.trim().isEmpty()) return new ArrayList<>();
        String sKey = seriesId.trim();
        CacheEntry<List<Map<String, Integer>>> cached = TOURNEY_POINTS_CACHE.get(sKey);
        if (cached != null && !cached.isExpired(60000L)) {
            return cached.data;
        }
        calculateSeriesStandingsWithExpiry(seriesId);
        cached = TOURNEY_POINTS_CACHE.get(sKey);
        return (cached != null) ? cached.data : new ArrayList<>();
    }

    /**
     * Returns tournament participation matrix from DB for all tournaments in series.
     */
    public List<Map<String, Boolean>> getTourneyParticipationPerTournament(String seriesId) {
        if (seriesId == null || seriesId.trim().isEmpty()) return new ArrayList<>();
        String sKey = seriesId.trim();
        CacheEntry<List<Map<String, Boolean>>> cached = TOURNEY_PART_CACHE.get(sKey);
        if (cached != null && !cached.isExpired(60000L)) {
            return cached.data;
        }
        calculateSeriesStandingsWithExpiry(seriesId);
        cached = TOURNEY_PART_CACHE.get(sKey);
        return (cached != null) ? cached.data : new ArrayList<>();
    }

    /**
     * Recalculates and persists standings for a series into `series_standings` and `series_tournament_history`.
     */
    public synchronized boolean recalculateAndPersistStandings(String seriesId) {
        if (seriesId == null || seriesId.trim().isEmpty()) return false;

        // Rebuild from persisted teams, matches and point configuration.
        clearAllCaches();

        SeriesDAO seriesDAO = new SeriesDAO();
        Series series = seriesDAO.getSeriesById(seriesId.trim());
        if (series == null || !"ROLLING_WINDOW".equalsIgnoreCase(series.getRankingModel())) return false;

        List<RollingStandingDTO> standings = calculateSeriesStandingsWithExpiry(seriesId.trim());
        List<Tournament> allTourneys = seriesDAO.getTournamentsBySeriesId(seriesId.trim());

        DBContext db = new DBContext();
        try (Connection conn = db.getConnection()) {
            conn.setAutoCommit(false);

            try {
                // Serialize replacement of this series' standings and history in SQL Server.
                try (PreparedStatement ps = conn.prepareStatement("SELECT id FROM series WITH (UPDLOCK, HOLDLOCK) WHERE id = ?")) {
                    ps.setString(1, seriesId.trim());
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) { conn.rollback(); return false; }
                    }
                }

                // 1. Delete old standings & history for this series
                try (PreparedStatement psDelS = conn.prepareStatement("DELETE FROM series_standings WHERE series_id = ?");
                     PreparedStatement psDelH = conn.prepareStatement("DELETE FROM series_tournament_history WHERE series_id = ?")) {
                    psDelS.setString(1, seriesId.trim());
                    psDelS.executeUpdate();
                    psDelH.setString(1, seriesId.trim());
                    psDelH.executeUpdate();
                }

                // 2. Insert fresh calculated series_standings
                String sqlInsertStanding = "INSERT INTO series_standings (id, series_id, phase_number, normalized_team_name, " +
                        "partner_participant_id, group_name, total_rolling_points, current_elo, matches_played, rank_overall, updated_at) " +
                        "VALUES (?, ?, 1, ?, ?, 'General', ?, 1000.0, ?, ?, CURRENT_TIMESTAMP)";

                try (PreparedStatement psS = conn.prepareStatement(sqlInsertStanding)) {
                    int r = 1;
                    for (RollingStandingDTO dto : standings) {
                        String standingId = "ST_" + seriesId.trim() + "_" + (dto.getPartnerParticipantId() != null ? dto.getPartnerParticipantId() : ("T_" + r));
                        psS.setString(1, standingId);
                        psS.setString(2, seriesId.trim());
                        psS.setString(3, dto.getTeamName());
                        psS.setString(4, dto.getPartnerParticipantId());
                        psS.setInt(5, dto.getTotalActivePoints());
                        psS.setInt(6, dto.getActiveTourneysCount());
                        psS.setInt(7, dto.getRank() > 0 ? dto.getRank() : r);
                        psS.addBatch();
                        r++;
                    }
                    psS.executeBatch();
                }

                // 3. Insert fresh series_tournament_history for each tournament in the series
                if (allTourneys != null && !allTourneys.isEmpty()) {
                    String sqlInsertHistory = "INSERT INTO series_tournament_history (id, series_id, tournament_id, phase_number, " +
                            "normalized_team_name, tournament_rank, points_earned, points_deducted, elo_change, completed_at) " +
                            "VALUES (?, ?, ?, 1, ?, ?, ?, 0, 0.0, ?)";

                    try (PreparedStatement psH = conn.prepareStatement(sqlInsertHistory)) {
                        for (Tournament t : allTourneys) {
                            String tCfgRaw = t.getSeriesPointsConfig();
                            if (tCfgRaw == null || tCfgRaw.trim().isEmpty() || !tCfgRaw.trim().startsWith("{")) {
                                tCfgRaw = buildFallbackConfig(t);
                            }
                            Map<String, Integer> posPtsMap = parsePointsConfigJson(tCfgRaw);
                            List<Team> tourneyTeams = getCachedTeamsByTournamentId(t.getId());

                            if (tourneyTeams != null) {
                                for (Team tm : tourneyTeams) {
                                    if (tm.getRawName() == null) continue;
                                    TournamentPlacementService.PlacementResult result = TournamentPlacementService.getInstance()
                                            .getTournamentResults(t.getId()).get(tm.getId());
                                    int pos = result == null ? 0 : result.rank;
                                    int pts = resolvePointsForResult(result, posPtsMap);

                                    String histId = "H_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
                                    psH.setString(1, histId);
                                    psH.setString(2, seriesId.trim());
                                    psH.setString(3, t.getId());
                                    psH.setString(4, tm.getNormalizedName() != null ? tm.getNormalizedName() : tm.getRawName());
                                    psH.setInt(5, pos);
                                    psH.setInt(6, pts);
                                    psH.setTimestamp(7, t.getCreatedAt() != null ? t.getCreatedAt() : new java.sql.Timestamp(System.currentTimeMillis()));
                                    psH.addBatch();
                                }
                            }
                        }
                        psH.executeBatch();
                    }
                }

                conn.commit();
            } catch (Exception failure) {
                conn.rollback();
                throw failure;
            }
            clearAllCaches();
            return true;
        } catch (Exception e) {
            clearAllCaches();
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

        String cacheKey = seriesId.trim() + "___" + teamName.trim().toLowerCase();
        CacheEntry<HighestRankDTO> cached = HIGHEST_RANK_CACHE.get(cacheKey);
        if (cached != null && !cached.isExpired(60000L)) {
            return cached.data;
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

        List<Map<String, Integer>> tourneyPointsList = getTourneyPointsPerTournament(seriesId.trim());

        int totalTourneys = allTourneys.size();
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

    // Helper: Build a fallback points config from a tournament's seriesRewardPoints
    // Used when seriesPointsConfig is null or invalid, to stay consistent with TeamProfileServlet.calculatePointsForTournament
    private String buildFallbackConfig(Tournament t) {
        int champPts = (t != null && t.getSeriesRewardPoints() != null && t.getSeriesRewardPoints() > 0)
            ? t.getSeriesRewardPoints() : 100;
        int runnerUpPts  = (int) Math.round(champPts * 0.70);
        int semiPts      = (int) Math.round(champPts * 0.40);
        int quarterPts   = (int) Math.round(champPts * 0.20);
        int r16Pts       = (int) Math.round(champPts * 0.10);
        return "{\"1\":" + champPts + ",\"2\":" + runnerUpPts + ",\"3-4\":" + semiPts + ",\"5-8\":" + quarterPts + ",\"9-16\":" + r16Pts + "}";
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

    public int resolvePointsForResult(TournamentPlacementService.PlacementResult result, Map<String, Integer> config) {
        if (result == null || config == null) return 0;
        if (config.containsKey(result.pointKey)) return config.get(result.pointKey);
        // Elimination categories must never be inferred from an arbitrary numeric rank.
        if (result.pointKey.startsWith("s1_") || result.pointKey.startsWith("swiss_")
                || "stage1_eliminated".equals(result.pointKey)) return 0;
        return resolvePointsForPosition(result.rank, config);
    }

    public int getTournamentTeamPoints(Tournament tournament, String teamId) {
        if (tournament == null || teamId == null) return 0;
        String raw = tournament.getSeriesPointsConfig();
        if (raw == null || raw.trim().isEmpty() || !raw.trim().startsWith("{")) raw = buildFallbackConfig(tournament);
        return resolvePointsForResult(TournamentPlacementService.getInstance().getTournamentResults(tournament.getId()).get(teamId),
                parsePointsConfigJson(raw));
    }

    public int resolvePointsForPosition(int pos, Map<String, Integer> config) {
        if (pos <= 0 || config == null || config.isEmpty()) return 0;
        if (config.containsKey(String.valueOf(pos))) return config.get(String.valueOf(pos));
        int points = 0, bestWidth = Integer.MAX_VALUE;
        for (Map.Entry<String, Integer> entry : config.entrySet()) {
            if (!entry.getKey().matches("[0-9]+-[0-9]+")) continue;
            String[] bounds = entry.getKey().split("-");
            int first = Integer.parseInt(bounds[0]), last = Integer.parseInt(bounds[1]);
            if (pos >= first && pos <= last && last-first < bestWidth) {
                points=entry.getValue(); bestWidth=last-first;
            }
        }
        if (bestWidth != Integer.MAX_VALUE) return points;
        String alias = pos==1 ? "champPoints" : pos==2 ? "runnerUpPoints" : pos<=4 ? "semiPoints"
                : pos<=8 ? "quarterPoints" : pos<=16 ? "r16Points" : "";
        return config.getOrDefault(alias, 0);
    }
}
