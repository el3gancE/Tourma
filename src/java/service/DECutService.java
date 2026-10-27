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

        int upperCount = 0, lowerCount = 0;
        for (Match m : matchesList) {
            if (m == null) continue;
            int round = m.getRoundNumber();
            String bracketType = m.getBracketType(); // "UPPER" or "LOWER"

            if (isUpper(bracketType) && round == ubStopRound) {
                upperCount++;
                if (!"COMPLETED".equalsIgnoreCase(m.getStatus()) && !"done".equalsIgnoreCase(m.getStatus()) && !"FINISHED".equalsIgnoreCase(m.getStatus())) {
                    return false;
                }
            } else if (isLower(bracketType) && round == lbStopRound) {
                lowerCount++;
                if (!"COMPLETED".equalsIgnoreCase(m.getStatus()) && !"done".equalsIgnoreCase(m.getStatus()) && !"FINISHED".equalsIgnoreCase(m.getStatus())) {
                    return false;
                }
            }
        }
        return upperCount > 0 && lowerCount > 0;
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

            if (isUpper(bracketType) && round == ubStopRound) {
                Team winner = getMatchWinner(m);
                if (winner != null && winner.getName() != null && !"BYE".equalsIgnoreCase(winner.getName())) {
                    ubTeams.add(winner);
                }
            } else if (isLower(bracketType) && round == lbStopRound) {
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

    private static boolean isUpper(String bracket) {
        return "UPPER".equalsIgnoreCase(bracket) || "WINNER_BRACKET".equalsIgnoreCase(bracket) || "MAIN".equalsIgnoreCase(bracket);
    }

    private static boolean isLower(String bracket) {
        return "LOWER".equalsIgnoreCase(bracket) || "LOSER_BRACKET".equalsIgnoreCase(bracket) || "LB".equalsIgnoreCase(bracket);
    }

    private static Team getMatchWinner(Match match) {
        Integer winner = match.getWinnerTeamId();
        if (winner == null || (winner != 1 && winner != 2)) return null;
        boolean first = winner == 1;
        String id = first ? match.getTeam1StringId() : match.getTeam2StringId();
        String name = first ? match.getTeam1Name() : match.getTeam2Name();
        Integer seed = first ? match.getTeam1Seed() : match.getTeam2Seed();
        if (id == null || name == null || "BYE".equalsIgnoreCase(name)) return null;
        Team team = new Team(); team.setId(id); team.setRawName(name);
        team.setOriginalSeed(seed == null ? 0 : seed); team.setStatus("QUALIFIED");
        return team;
    }

    private static int nextPowerOfTwo(int n) {
        if (n <= 1) return 2;
        int power = 1;
        while (power < n) power *= 2;
        return power;
    }
}
