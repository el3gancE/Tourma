package service;

import controller.TeamProfileServlet.TourneyPerformanceDTO;
import dao.DBContext;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import model.PartnerParticipant;
import model.Team;

/**
 * Loads grouped match history for a team across all tournaments in a Series.
 */
public class TeamMatchHistoryService {

    public static class MatchRecordDTO {
        private int stt;
        private String tournamentId;
        private String tournamentName;
        private String tierName;
        private int tournamentIndex;
        private String finalStageUrl;
        private int stageOrder;
        private int roundNumber;
        private int matchOrder;
        private String stageFormat;
        private String bracketType;
        private String roundKey;
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

    public static List<MatchRecordDTO> getMatchHistory(
            String seriesId,
            PartnerParticipant partner,
            String teamName,
            List<TourneyPerformanceDTO> performanceList) {
        List<MatchRecordDTO> list = new ArrayList<>();
        if (seriesId == null || seriesId.trim().isEmpty() || performanceList == null || performanceList.isEmpty()) {
            return list;
        }

        Map<String, TourneyPerformanceDTO> perfByTourneyId = new HashMap<>();
        for (TourneyPerformanceDTO p : performanceList) {
            if (p.getTournamentId() != null) {
                perfByTourneyId.put(p.getTournamentId(), p);
            }
        }

        RollingWindowPointService rwps = RollingWindowPointService.getInstance();
        Map<String, TourneyPerformanceDTO> perfByTeamId = new HashMap<>();
        for (TourneyPerformanceDTO p : performanceList) {
            String tId = p.getTournamentId();
            if (tId == null) continue;
            List<Team> teams = rwps.getCachedTeamsByTournamentId(tId);
            Team teamInTourney = null;
            if (teams != null && partner != null && partner.getId() != null) {
                for (Team tm : teams) {
                    if (partner.getId().equalsIgnoreCase(tm.getPartnerParticipantId())) {
                        teamInTourney = tm;
                        break;
                    }
                }
            }
            if (teams != null && teamInTourney == null && teamName != null) {
                for (Team tm : teams) {
                    if (partner != null && tm.getPartnerParticipantId() != null) continue;
                    if ((tm.getRawName() != null && tm.getRawName().trim().equalsIgnoreCase(teamName.trim()))
                            || (tm.getName() != null && tm.getName().trim().equalsIgnoreCase(teamName.trim()))) {
                        teamInTourney = tm;
                        break;
                    }
                }
            }
            if (teamInTourney != null && teamInTourney.getId() != null) {
                perfByTeamId.put(teamInTourney.getId(), p);
            }
        }

        if (perfByTeamId.isEmpty()) {
            return list;
        }

        String sql = "SELECT m.tournament_id, m.round_number, COALESCE(m.match_order, 0) AS match_order, "
                + "m.bracket_type, COALESCE(s.stage_order, 1) AS stage_order, "
                + "s.format AS stage_format, "
                + "m.team1_id, m.team2_id, a.raw_name AS team1_name, b.raw_name AS team2_name, "
                + "m.score1, m.score2, m.penalty1, m.penalty2, m.winner_id "
                + "FROM matches m "
                + "JOIN tournaments t ON t.id = m.tournament_id "
                + "JOIN teams a ON a.id = m.team1_id AND a.tournament_id = m.tournament_id "
                + "JOIN teams b ON b.id = m.team2_id AND b.tournament_id = m.tournament_id "
                + "LEFT JOIN tournament_stages s ON s.id = m.stage_id AND s.tournament_id = m.tournament_id "
                + "WHERE t.series_id = ? AND ISNULL(m.is_bye, 0) = 0 AND m.team1_id <> m.team2_id "
                + "AND UPPER(m.status) IN ('FINISHED', 'COMPLETED', 'DONE') "
                + "AND m.score1 >= 0 AND m.score2 >= 0 OPTION (LOOP JOIN, MAXDOP 1)";

        Map<String, Integer> roundSlotCounts = new HashMap<>();
        DBContext db = new DBContext();
        try (Connection conn = db.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, seriesId.trim());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String tourneyId = rs.getString("tournament_id");
                    if (!perfByTourneyId.containsKey(tourneyId)) continue;

                    int stgOrder = rs.getInt("stage_order");
                    int rNum = rs.getInt("round_number");
                    String bType = rs.getString("bracket_type");
                    String rKey = tourneyId + ":" + stgOrder + ":" + (bType != null ? bType : "") + ":" + rNum;
                    roundSlotCounts.merge(rKey, 1, Integer::sum);

                    String t1Id = rs.getString("team1_id");
                    String t2Id = rs.getString("team2_id");
                    boolean firstIsSelf = perfByTeamId.containsKey(t1Id);
                    boolean secondIsSelf = perfByTeamId.containsKey(t2Id);
                    if (!firstIsSelf && !secondIsSelf) continue;

                    String selfTeamId = firstIsSelf ? t1Id : t2Id;
                    TourneyPerformanceDTO tPerf = perfByTeamId.get(selfTeamId);

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

                    MatchRecordDTO mh = new MatchRecordDTO();
                    mh.setTournamentId(tPerf.getTournamentId());
                    mh.setTournamentName(tPerf.getTournamentName());
                    mh.setTierName(tPerf.getTierName());
                    mh.setTournamentIndex(tPerf.getStt());
                    mh.setFinalStageUrl(tPerf.getFinalStageUrl());
                    mh.setStageOrder(stgOrder);
                    mh.setRoundNumber(rNum);
                    mh.setMatchOrder(rs.getInt("match_order"));
                    mh.stageFormat = rs.getString("stage_format");
                    mh.bracketType = bType;
                    mh.roundKey = rKey;
                    mh.setResult(resCode);
                    mh.setTeamScore(firstIsSelf ? s1 : s2);
                    mh.setOpponentScore(firstIsSelf ? s2 : s1);
                    mh.setTeamPenalty(firstIsSelf ? pen1 : pen2);
                    mh.setOpponentPenalty(firstIsSelf ? pen2 : pen1);
                    mh.setOpponentName(oppName != null ? oppName : "");
                    list.add(mh);
                }
            }

            for (MatchRecordDTO mh : list) {
                int slots = roundSlotCounts.getOrDefault(mh.roundKey, 0);
                mh.setRoundLabel(formatRoundLabel(mh.stageFormat, mh.bracketType, mh.getRoundNumber(), slots));
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

    public static String formatRoundLabel(String format, String bracket, int round, int slots) {
        if ("THIRD_PLACE".equalsIgnoreCase(bracket)) return "THIRD PLACE";
        if (bracket != null && !bracket.isBlank()) {
            String b = bracket.toUpperCase(Locale.ROOT);
            if ("UPPER".equals(b) || "WINNER_BRACKET".equals(b)) return "UB ROUND " + round;
            if ("LOWER".equals(b) || "LOSER_BRACKET".equals(b)) return "LB ROUND " + round;
            if ("GRAND_FINAL".equals(b)) return "GRAND FINAL";
            if ("GRAND_FINAL_RESET".equals(b)) return "GRAND FINAL 2";
        }
        if ((format == null || format.toUpperCase(Locale.ROOT).contains("SINGLE")) && slots > 0) {
            return switch (slots) {
                case 1 -> "FINAL";
                case 2 -> "SEMI-FINAL";
                case 4 -> "QUARTER-FINAL";
                default -> "ROUND OF " + (slots * 2);
            };
        }
        return "ROUND " + round;
    }
}
