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
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import model.PartnerParticipant;
import model.Series;
import model.Team;
import model.Tournament;
import service.RollingWindowPointService;
import service.TeamMatchHistoryService;
import service.TournamentPlacementService;
import service.TournamentPlacementService.PlacementResult;

/**
 * Controller for Sub-Tournament Summary ("Tổng Kết Giải Đấu").
 * Pattern: /rolling/tournament-summary
 */
@WebServlet(name = "RollingTournamentSummaryServlet", urlPatterns = {"/rolling/tournament-summary"})
public class RollingTournamentSummaryServlet extends HttpServlet {

    public static class PathMatchDTO {
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
        private String opponentPartnerId;

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
        public String getOpponentPartnerId() { return opponentPartnerId; }
        public void setOpponentPartnerId(String opponentPartnerId) { this.opponentPartnerId = opponentPartnerId; }
    }

    public static class TeamSummaryDTO {
        private String teamId;
        private String teamName;
        private int seed;
        private String partnerParticipantId;
        private int rank;
        private String rankLabel;
        private String roundGroupKey;
        private String roundGroupTitle;
        private int pointsEarned;
        private int wins;
        private int losses;
        private int draws;
        private int scoreFor;
        private int scoreAgainst;
        private List<PathMatchDTO> matches = new ArrayList<>();

        public String getTeamId() { return teamId; }
        public void setTeamId(String teamId) { this.teamId = teamId; }
        public String getTeamName() { return teamName; }
        public void setTeamName(String teamName) { this.teamName = teamName; }
        public int getSeed() { return seed; }
        public void setSeed(int seed) { this.seed = seed; }
        public String getPartnerParticipantId() { return partnerParticipantId; }
        public void setPartnerParticipantId(String partnerParticipantId) { this.partnerParticipantId = partnerParticipantId; }
        public int getRank() { return rank; }
        public void setRank(int rank) { this.rank = rank; }
        public String getRankLabel() { return rankLabel; }
        public void setRankLabel(String rankLabel) { this.rankLabel = rankLabel; }
        public String getRoundGroupKey() { return roundGroupKey; }
        public void setRoundGroupKey(String roundGroupKey) { this.roundGroupKey = roundGroupKey; }
        public String getRoundGroupTitle() { return roundGroupTitle; }
        public void setRoundGroupTitle(String roundGroupTitle) { this.roundGroupTitle = roundGroupTitle; }
        public int getPointsEarned() { return pointsEarned; }
        public void setPointsEarned(int pointsEarned) { this.pointsEarned = pointsEarned; }
        public int getWins() { return wins; }
        public void setWins(int wins) { this.wins = wins; }
        public int getLosses() { return losses; }
        public void setLosses(int losses) { this.losses = losses; }
        public int getDraws() { return draws; }
        public void setDraws(int draws) { this.draws = draws; }
        public int getScoreFor() { return scoreFor; }
        public void setScoreFor(int scoreFor) { this.scoreFor = scoreFor; }
        public int getScoreAgainst() { return scoreAgainst; }
        public void setScoreAgainst(int scoreAgainst) { this.scoreAgainst = scoreAgainst; }
        public List<PathMatchDTO> getMatches() { return matches; }
        public void setMatches(List<PathMatchDTO> matches) { this.matches = matches; }
    }

    public static class RoundGroupDTO {
        private String groupKey;
        private String groupTitle;
        private int minRank;
        private List<TeamSummaryDTO> teams = new ArrayList<>();

        public RoundGroupDTO(String groupKey, String groupTitle, int minRank) {
            this.groupKey = groupKey;
            this.groupTitle = groupTitle;
            this.minRank = minRank;
        }
        public String getGroupKey() { return groupKey; }
        public String getGroupTitle() { return groupTitle; }
        public int getMinRank() { return minRank; }
        public List<TeamSummaryDTO> getTeams() { return teams; }
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        String tournamentId = request.getParameter("id");
        if (tournamentId == null || tournamentId.trim().isEmpty()) {
            tournamentId = request.getParameter("tournamentId");
        }
        if (tournamentId == null || tournamentId.trim().isEmpty()) {
            response.sendRedirect(request.getContextPath() + "/my-series");
            return;
        }
        tournamentId = tournamentId.trim();

        TournamentDAO tournamentDAO = new TournamentDAO();
        Tournament tournament = tournamentDAO.getTournamentById(tournamentId, false);
        if (tournament == null) {
            response.sendRedirect(request.getContextPath() + "/my-series");
            return;
        }

        String seriesId = tournament.getSeriesId();
        if (seriesId == null || seriesId.trim().isEmpty()) {
            seriesId = request.getParameter("seriesId");
        }
        Series series = null;
        SeriesDAO seriesDAO = new SeriesDAO();
        if (seriesId != null && !seriesId.trim().isEmpty()) {
            series = seriesDAO.getSeriesById(seriesId.trim());
        }

        List<String> stageFormats = tournamentDAO.getStageFormats(tournamentId);
        String s1Format = (stageFormats != null && !stageFormats.isEmpty()) ? stageFormats.get(0)
                : (tournament.getFormat() != null ? tournament.getFormat() : "SINGLE_ELIMINATION");
        String s2Format = (stageFormats != null && stageFormats.size() > 1) ? stageFormats.get(1)
                : (tournament.getStage2Format() != null ? tournament.getStage2Format() : "SINGLE_ELIMINATION");
        boolean isMultiStage = "MULTI_STAGE".equalsIgnoreCase(tournament.getTournamentType());

        int totalMatches = tournament.getTotalMatches();
        int completedMatches = tournament.getCompletedMatches();
        boolean isCompleted = ("COMPLETED".equalsIgnoreCase(tournament.getStatus())
                || "FINISHED".equalsIgnoreCase(tournament.getStatus()))
                && totalMatches > 0 && completedMatches >= totalMatches;

        String activeBracketUrl = buildActiveBracketUrl(request.getContextPath(), tournament, isMultiStage, s1Format, s2Format, seriesId);

        request.setAttribute("setupTournament", tournament);
        if (series != null) request.setAttribute("setupSeries", series);
        request.setAttribute("tournament", tournament);
        request.setAttribute("series", series);
        request.setAttribute("stage1Format", s1Format);
        request.setAttribute("stage2Format", s2Format);
        request.setAttribute("isMultiStage", isMultiStage);
        request.setAttribute("isCompleted", isCompleted);
        request.setAttribute("activeBracketUrl", activeBracketUrl);

        ParticipantDAO participantDAO = new ParticipantDAO();
        List<Team> teams = participantDAO.getTeamsByTournamentId(tournamentId);
        Integer topSeedCount = tournamentDAO.getTopSeedCount(tournamentId);
        request.setAttribute("teamCount", teams != null ? teams.size() : 0);
        request.setAttribute("topSeedCount", topSeedCount);

        if (isCompleted && teams != null && !teams.isEmpty()) {
            Map<String, String> partnerIdByName = new HashMap<>();
            if (seriesId != null && !seriesId.trim().isEmpty()) {
                List<PartnerParticipant> partners = seriesDAO.getPartnerParticipantsBySeriesId(seriesId.trim());
                if (partners != null) {
                    for (PartnerParticipant p : partners) {
                        if (p.getId() == null) continue;
                        if (p.getName() != null) partnerIdByName.put(p.getName().trim().toLowerCase(Locale.ROOT), p.getId());
                    }
                }
            }

            Map<String, PlacementResult> placements = TournamentPlacementService.getInstance().getFreshTournamentResults(tournamentId);
            RollingWindowPointService rwps = RollingWindowPointService.getInstance();

            Map<String, TeamSummaryDTO> dtoByTeamId = new LinkedHashMap<>();
            for (Team tm : teams) {
                TeamSummaryDTO dto = new TeamSummaryDTO();
                dto.setTeamId(tm.getId());
                String displayName = tm.getRawName() != null ? tm.getRawName().trim() : (tm.getName() != null ? tm.getName().trim() : "");
                dto.setTeamName(displayName);
                dto.setSeed(tm.getOriginalSeed());

                String partnerId = tm.getPartnerParticipantId();
                if ((partnerId == null || partnerId.trim().isEmpty()) && !displayName.isEmpty()) {
                    partnerId = partnerIdByName.get(displayName.toLowerCase(Locale.ROOT));
                }
                if ((partnerId == null || partnerId.trim().isEmpty()) && tm.getNormalizedName() != null) {
                    partnerId = partnerIdByName.get(tm.getNormalizedName().trim().toLowerCase(Locale.ROOT));
                }
                dto.setPartnerParticipantId(partnerId);

                PlacementResult pr = placements.get(tm.getId());
                if (pr == null && !displayName.isEmpty()) {
                    pr = placements.get(displayName.toLowerCase(Locale.ROOT));
                }
                int rank = pr != null ? pr.rank : (teams.size() + 1);
                String pointKey = pr != null && pr.pointKey != null ? pr.pointKey : String.valueOf(rank);
                String rawAchievement = pr != null ? pr.getAchievement() : ("Hạng " + rank);

                String groupTitle = resolveRoundTitle(pr, rank, pointKey, rawAchievement, isMultiStage, s1Format, s2Format);
                dto.setRank(rank);
                dto.setRankLabel(resolveRankBadge(rank, pointKey));
                dto.setRoundGroupKey(pointKey + "::" + groupTitle);
                dto.setRoundGroupTitle(groupTitle);

                int pts = (partnerId != null && !partnerId.trim().isEmpty())
                        ? rwps.getTournamentTeamPoints(tournament, tm.getId()) : 0;
                dto.setPointsEarned(pts);
                dtoByTeamId.put(tm.getId(), dto);
            }

            loadMatchesAndBuildPaths(tournamentId, isMultiStage, dtoByTeamId);

            Map<String, RoundGroupDTO> groupsMap = new LinkedHashMap<>();
            List<TeamSummaryDTO> sortedTeams = new ArrayList<>(dtoByTeamId.values());
            sortedTeams.sort(Comparator
                    .comparingInt(TeamSummaryDTO::getRank)
                    .thenComparing(Comparator.comparingInt(TeamSummaryDTO::getWins).reversed())
                    .thenComparing(Comparator.comparingInt((TeamSummaryDTO d) -> d.getScoreFor() - d.getScoreAgainst()).reversed())
                    .thenComparingInt(TeamSummaryDTO::getSeed));

            for (TeamSummaryDTO dto : sortedTeams) {
                RoundGroupDTO grp = groupsMap.computeIfAbsent(
                        dto.getRoundGroupKey(),
                        k -> new RoundGroupDTO(dto.getRoundGroupKey(), dto.getRoundGroupTitle(), dto.getRank()));
                grp.getTeams().add(dto);
            }

            List<RoundGroupDTO> roundGroups = new ArrayList<>(groupsMap.values());
            roundGroups.sort(Comparator.comparingInt(RoundGroupDTO::getMinRank));
            request.setAttribute("roundGroups", roundGroups);
            request.setAttribute("teamSummaries", sortedTeams);
        }

        request.getRequestDispatcher("/common/rolling/rolling-tournament-summary.jsp").forward(request, response);
    }

    private static String resolveRoundTitle(
            PlacementResult pr,
            int rank,
            String pointKey,
            String rawAchievement,
            boolean isMultiStage,
            String s1Format,
            String s2Format) {
        if (pr == null) return "Vòng Phân Hạng";
        if ("stage1_eliminated".equals(pointKey)) {
            String s1Label = "GROUP_STAGE".equalsIgnoreCase(s1Format) ? "Vòng Bảng"
                    : "ROUND_ROBIN".equalsIgnoreCase(s1Format) ? "Vòng Tròn"
                    : "GSL".equalsIgnoreCase(s1Format) ? "Vòng Bảng GSL" : "Vòng 1";
            return isMultiStage ? ("Dừng Bước Ở " + s1Label) : s1Label;
        }
        if (pointKey.startsWith("swiss_")) {
            return (isMultiStage ? "Vòng 1 · " : "") + "Swiss " + pointKey.substring(6);
        }
        if (pointKey.startsWith("s1_")) {
            return "Vòng 1 · " + rawAchievement;
        }
        if (rank == 1) return "Vô Địch";
        if (rank == 2) return "Á Quân";
        String finalFmt = isMultiStage ? s2Format : s1Format;
        if ("ROUND_ROBIN".equalsIgnoreCase(finalFmt)) {
            return "Hạng " + rank;
        }
        if ("3".equals(pointKey) && !"DOUBLE_ELIMINATION".equalsIgnoreCase(finalFmt)) return "Hạng 3";
        if ("4".equals(pointKey) && !"DOUBLE_ELIMINATION".equalsIgnoreCase(finalFmt)) return "Hạng 4";
        return rawAchievement;
    }

    private static String resolveRankBadge(int rank, String pointKey) {
        if (rank == 1) return "1";
        if (rank == 2) return "2";
        if (pointKey != null && pointKey.matches("[0-9]+-[0-9]+")) {
            return pointKey;
        }
        return String.valueOf(rank);
    }

    private static void loadMatchesAndBuildPaths(
            String tournamentId,
            boolean isMultiStage,
            Map<String, TeamSummaryDTO> dtoByTeamId) {
        String sql = "SELECT m.id, m.round_number, COALESCE(m.match_order, 0) AS match_order, "
                + "m.bracket_type, COALESCE(s.stage_order, 1) AS stage_order, s.format AS stage_format, "
                + "m.team1_id, m.team2_id, m.score1, m.score2, m.penalty1, m.penalty2, m.winner_id "
                + "FROM matches m "
                + "LEFT JOIN tournament_stages s ON s.id = m.stage_id AND s.tournament_id = m.tournament_id "
                + "WHERE m.tournament_id = ? AND ISNULL(m.is_bye, 0) = 0 "
                + "AND m.team1_id IS NOT NULL AND m.team2_id IS NOT NULL AND m.team1_id <> m.team2_id "
                + "AND (UPPER(m.status) IN ('FINISHED', 'COMPLETED', 'DONE') OR m.winner_id IS NOT NULL OR (m.score1 IS NOT NULL AND m.score2 IS NOT NULL)) "
                + "ORDER BY COALESCE(s.stage_order, 1) ASC, m.round_number ASC, COALESCE(m.match_order, 0) ASC, m.id ASC";

        class RawMatch {
            int stageOrder, roundNumber, matchOrder, s1, s2, winnerSide;
            Integer pen1, pen2;
            String stageFormat, bracketType, roundKey, t1Id, t2Id;
        }
        List<RawMatch> rawMatches = new ArrayList<>();
        Map<String, Integer> roundSlotCounts = new HashMap<>();

        try (Connection conn = new DBContext().getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tournamentId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String t1Id = rs.getString("team1_id");
                    String t2Id = rs.getString("team2_id");
                    if (!dtoByTeamId.containsKey(t1Id) && !dtoByTeamId.containsKey(t2Id)) continue;

                    RawMatch rm = new RawMatch();
                    rm.stageOrder = rs.getInt("stage_order");
                    rm.roundNumber = rs.getInt("round_number");
                    rm.matchOrder = rs.getInt("match_order");
                    rm.stageFormat = rs.getString("stage_format");
                    rm.bracketType = rs.getString("bracket_type");
                    rm.t1Id = t1Id;
                    rm.t2Id = t2Id;
                    rm.s1 = rs.getInt("score1");
                    rm.s2 = rs.getInt("score2");
                    int p1 = rs.getInt("penalty1");
                    rm.pen1 = rs.wasNull() || p1 < 0 ? null : p1;
                    int p2 = rs.getInt("penalty2");
                    rm.pen2 = rs.wasNull() || p2 < 0 ? null : p2;

                    String wId = rs.getString("winner_id");
                    if (wId != null) {
                        if (wId.equals(t1Id)) rm.winnerSide = 1;
                        else if (wId.equals(t2Id)) rm.winnerSide = 2;
                    } else {
                        if (rm.s1 > rm.s2) rm.winnerSide = 1;
                        else if (rm.s2 > rm.s1) rm.winnerSide = 2;
                        else if (rm.pen1 != null && rm.pen2 != null) {
                            if (rm.pen1 > rm.pen2) rm.winnerSide = 1;
                            else if (rm.pen2 > rm.pen1) rm.winnerSide = 2;
                        }
                    }

                    rm.roundKey = rm.stageOrder + ":" + (rm.bracketType != null ? rm.bracketType : "") + ":" + rm.roundNumber;
                    roundSlotCounts.merge(rm.roundKey, 1, Integer::sum);
                    rawMatches.add(rm);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        for (RawMatch rm : rawMatches) {
            int slots = roundSlotCounts.getOrDefault(rm.roundKey, 0);
            String baseLabel = TeamMatchHistoryService.formatRoundLabel(rm.stageFormat, rm.bracketType, rm.roundNumber, slots);
            String fullRoundLabel = isMultiStage ? ("V" + rm.stageOrder + " · " + baseLabel) : baseLabel;

            TeamSummaryDTO t1 = dtoByTeamId.get(rm.t1Id);
            TeamSummaryDTO t2 = dtoByTeamId.get(rm.t2Id);
            String t1Name = t1 != null ? t1.getTeamName() : rm.t1Id;
            String t2Name = t2 != null ? t2.getTeamName() : rm.t2Id;
            String t1PartnerId = t1 != null ? t1.getPartnerParticipantId() : null;
            String t2PartnerId = t2 != null ? t2.getPartnerParticipantId() : null;

            if (t1 != null) {
                t1.setScoreFor(t1.getScoreFor() + rm.s1);
                t1.setScoreAgainst(t1.getScoreAgainst() + rm.s2);
                String res = rm.winnerSide == 1 ? "W" : (rm.winnerSide == 2 ? "L" : "D");
                if ("W".equals(res)) t1.setWins(t1.getWins() + 1);
                else if ("L".equals(res)) t1.setLosses(t1.getLosses() + 1);
                else t1.setDraws(t1.getDraws() + 1);

                PathMatchDTO pm = new PathMatchDTO();
                pm.setStageOrder(rm.stageOrder);
                pm.setRoundNumber(rm.roundNumber);
                pm.setMatchOrder(rm.matchOrder);
                pm.setRoundLabel(fullRoundLabel);
                pm.setResult(res);
                pm.setTeamScore(rm.s1);
                pm.setOpponentScore(rm.s2);
                pm.setTeamPenalty(rm.pen1);
                pm.setOpponentPenalty(rm.pen2);
                pm.setOpponentName(t2Name);
                pm.setOpponentPartnerId(t2PartnerId);
                t1.getMatches().add(pm);
            }

            if (t2 != null) {
                t2.setScoreFor(t2.getScoreFor() + rm.s2);
                t2.setScoreAgainst(t2.getScoreAgainst() + rm.s1);
                String res = rm.winnerSide == 2 ? "W" : (rm.winnerSide == 1 ? "L" : "D");
                if ("W".equals(res)) t2.setWins(t2.getWins() + 1);
                else if ("L".equals(res)) t2.setLosses(t2.getLosses() + 1);
                else t2.setDraws(t2.getDraws() + 1);

                PathMatchDTO pm = new PathMatchDTO();
                pm.setStageOrder(rm.stageOrder);
                pm.setRoundNumber(rm.roundNumber);
                pm.setMatchOrder(rm.matchOrder);
                pm.setRoundLabel(fullRoundLabel);
                pm.setResult(res);
                pm.setTeamScore(rm.s2);
                pm.setOpponentScore(rm.s1);
                pm.setTeamPenalty(rm.pen2);
                pm.setOpponentPenalty(rm.pen1);
                pm.setOpponentName(t1Name);
                pm.setOpponentPartnerId(t1PartnerId);
                t2.getMatches().add(pm);
            }
        }
    }

    private static String buildActiveBracketUrl(
            String contextPath,
            Tournament tournament,
            boolean isMultiStage,
            String s1Format,
            String s2Format,
            String seriesId) {
        String activeFmt = s1Format;
        String stageParam = "";
        if (isMultiStage) {
            boolean stage1Done = "LOCKED".equalsIgnoreCase(tournament.getStage1Status())
                    || "COMPLETED".equalsIgnoreCase(tournament.getStage1Status());
            activeFmt = stage1Done ? s2Format : s1Format;
            stageParam = "&stage=" + (stage1Done ? "2" : "1");
        }
        String page = "single-elimination.jsp";
        if ("DOUBLE_ELIMINATION".equalsIgnoreCase(activeFmt)) page = "double-elimination.jsp";
        else if ("ROUND_ROBIN".equalsIgnoreCase(activeFmt)) page = "round-robin.jsp";
        else if ("GROUP_STAGE".equalsIgnoreCase(activeFmt)) page = "group-stage.jsp";
        else if ("GSL".equalsIgnoreCase(activeFmt)) page = "gsl.jsp";
        else if (activeFmt != null && activeFmt.toUpperCase(Locale.ROOT).contains("SWISS")) page = "swiss-stage.jsp";

        String sQuery = (seriesId != null && !seriesId.trim().isEmpty()) ? ("&seriesId=" + seriesId.trim()) : "";
        return contextPath + "/common/" + page + "?id=" + tournament.getId()
                + (isMultiStage ? stageParam : ("&format=" + activeFmt))
                + sQuery;
    }
}
