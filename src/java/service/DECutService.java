package service;

import model.Match;
import model.Team;
import java.util.*;

/**
 * DECutService
 * Service for Double Elimination Cut Stage:
 * - Upper Bracket Qualifiers (Top K/2 teams)
 * - Lower Bracket Qualifiers (Bottom K/2 teams)
 */
public class DECutService {

    public static class DEQualifier {
        private Team team;
        private String sourceBracket; // "UPPER" or "LOWER"
        private int seed;

        public DEQualifier(Team team, String sourceBracket, int seed) {
            this.team = team;
            this.sourceBracket = sourceBracket;
            this.seed = seed;
        }

        public Team getTeam() { return team; }
        public String getSourceBracket() { return sourceBracket; }
        public int getSeed() { return seed; }
    }

    /**
     * Calculate stopping round for Upper Bracket and Lower Bracket given power of 2 and cutTarget
     */
    public static int calculateUbStoppingRound(int totalTeams, int cutTarget) {
        int pow2 = nextPowerOfTwo(totalTeams);
        int totalUbRounds = (int) (Math.log(pow2) / Math.log(2));
        int ubQualifiers = cutTarget / 2;
        int diff = (int) Math.round(Math.log(ubQualifiers) / Math.log(2));
        return Math.max(1, totalUbRounds - diff);
    }

    public static int calculateLbStoppingRound(int ubStoppingRound) {
        return (ubStoppingRound - 1) * 2;
    }

    /**
     * Check if qualifying matches in both UB and LB are completed.
     */
    public static boolean isCutStageFinished(List<Match> matchesList, int ubStopRound, int lbStopRound) {
        if (matchesList == null || matchesList.isEmpty()) return false;

        for (Match m : matchesList) {
            if (m == null) continue;
            int round = m.getRoundNumber();
            String bracketType = m.getBracketType(); // "UPPER" or "LOWER"

            if ("UPPER".equalsIgnoreCase(bracketType) && round == ubStopRound) {
                if (!"COMPLETED".equalsIgnoreCase(m.getStatus()) && !"done".equalsIgnoreCase(m.getStatus())) {
                    return false;
                }
            } else if ("LOWER".equalsIgnoreCase(bracketType) && round == lbStopRound) {
                if (!"COMPLETED".equalsIgnoreCase(m.getStatus()) && !"done".equalsIgnoreCase(m.getStatus())) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Extract qualified teams from Upper Bracket winners (Top Seeds) and Lower Bracket winners (Bottom Seeds).
     */
    public static List<Team> extractQualifiedTeams(List<Match> matchesList, int ubStopRound, int lbStopRound) {
        List<Team> ubTeams = new ArrayList<>();
        List<Team> lbTeams = new ArrayList<>();

        if (matchesList == null) return new ArrayList<>();

        for (Match m : matchesList) {
            if (m == null) continue;
            int round = m.getRoundNumber();
            String bracketType = m.getBracketType();

            if ("UPPER".equalsIgnoreCase(bracketType) && round == ubStopRound) {
                Team winner = getMatchWinner(m);
                if (winner != null && winner.getName() != null && !"BYE".equalsIgnoreCase(winner.getName())) {
                    ubTeams.add(winner);
                }
            } else if ("LOWER".equalsIgnoreCase(bracketType) && round == lbStopRound) {
                Team winner = getMatchWinner(m);
                if (winner != null && winner.getName() != null && !"BYE".equalsIgnoreCase(winner.getName())) {
                    lbTeams.add(winner);
                }
            }
        }

        // Combine: UB Qualifiers (Seeds 1..K/2), LB Qualifiers (Seeds K/2+1..K)
        List<Team> allQualifiers = new ArrayList<>();
        allQualifiers.addAll(ubTeams);
        allQualifiers.addAll(lbTeams);
        return allQualifiers;
    }

    private static Team getMatchWinner(Match m) {
        if (m == null) return null;
        Integer winnerId = m.getWinnerTeamId();
        
        Team t = new Team();
        if (winnerId != null) {
            if (winnerId.equals(m.getTeam1Id())) {
                t.setId(String.valueOf(m.getTeam1Id()));
                t.setRawName(m.getTeam1Name());
                t.setOriginalSeed(m.getTeam1Seed() != null ? m.getTeam1Seed() : 0);
                t.setStatus("QUALIFIED");
                return t;
            } else if (winnerId.equals(m.getTeam2Id())) {
                t.setId(String.valueOf(m.getTeam2Id()));
                t.setRawName(m.getTeam2Name());
                t.setOriginalSeed(m.getTeam2Seed() != null ? m.getTeam2Seed() : 0);
                t.setStatus("QUALIFIED");
                return t;
            }
        }

        // Fallback to scores
        if (m.getTeam1Score() != null && m.getTeam2Score() != null) {
            if (m.getTeam1Score() > m.getTeam2Score()) {
                t.setId(String.valueOf(m.getTeam1Id()));
                t.setRawName(m.getTeam1Name());
                t.setOriginalSeed(m.getTeam1Seed() != null ? m.getTeam1Seed() : 0);
                t.setStatus("QUALIFIED");
                return t;
            } else if (m.getTeam2Score() > m.getTeam1Score()) {
                t.setId(String.valueOf(m.getTeam2Id()));
                t.setRawName(m.getTeam2Name());
                t.setOriginalSeed(m.getTeam2Seed() != null ? m.getTeam2Seed() : 0);
                t.setStatus("QUALIFIED");
                return t;
            }
        }
        
        if (m.getTeam1Id() != null) {
            t.setId(String.valueOf(m.getTeam1Id()));
            t.setRawName(m.getTeam1Name());
            t.setOriginalSeed(m.getTeam1Seed() != null ? m.getTeam1Seed() : 0);
            t.setStatus("QUALIFIED");
            return t;
        }
        return null;
    }

    private static int nextPowerOfTwo(int n) {
        if (n <= 1) return 2;
        int power = 1;
        while (power < n) power *= 2;
        return power;
    }
}
