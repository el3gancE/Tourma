package service;

/**
 * CountAdvanceTeamService
 * Universal service for counting advancing teams, calculating stopping rounds,
 * and validating cut targets across ALL 5 tournament formats:
 * 1. Single Elimination (SE)
 * 2. Double Elimination (DE)
 * 3. Swiss System (SW)
 * 4. Round Robin (RR)
 * 5. Group Stage (GS)
 */
public class CountAdvanceTeamService {

    /**
     * 1. SINGLE ELIMINATION (SE)
     * Calculate stopping round R_stop where Stage 1 stops when cutTarget teams remain.
     * Formula: R_stop = totalRounds - log2(cutTarget)
     */
    public static int calculateStoppingRoundSE(int totalTeams, int cutTarget) {
        if (totalTeams <= 0 || cutTarget <= 0 || cutTarget >= totalTeams) {
            return 1;
        }

        int totalRounds = (int) Math.ceil(Math.log(totalTeams) / Math.log(2));
        // Use ceil so stopping round leaves remaining teams >= cutTarget
        // e.g. 32 teams, cutTarget=9: ceil(log2(9))=4, stopping=5-4=1 (16 remain >= 9) ✓
        // e.g. 32 teams, cutTarget=8: ceil(log2(8))=3, stopping=5-3=2 (8 remain = 8) ✓
        int remainingRoundsForCut = (int) Math.ceil(Math.log(cutTarget) / Math.log(2));
        int stoppingRound = totalRounds - remainingRoundsForCut;

        return Math.max(1, stoppingRound);
    }

    /**
     * 2. DOUBLE ELIMINATION (DE)
     * Calculate upper and lower bracket stopping round thresholds for DE cutTarget.
     */
    public static int calculateStoppingRoundDE(int totalTeams, int cutTarget) {
        if (totalTeams <= 0 || cutTarget <= 0 || cutTarget >= totalTeams) {
            return 1;
        }
        int winnerBracketCut = cutTarget / 2;
        int totalWinnerRounds = (int) Math.ceil(Math.log(totalTeams) / Math.log(2));
        int remainingRounds = (int) (Math.log(winnerBracketCut > 0 ? winnerBracketCut : 1) / Math.log(2));

        return Math.max(1, totalWinnerRounds - remainingRounds);
    }

    /**
     * 3. SWISS SYSTEM (SW)
     * Calculate advancing team count for Swiss stage based on target wins (e.g. 3 wins).
     */
    public static int countAdvancingSwiss(int targetWins, int totalTeams) {
        if (totalTeams == 16 && targetWins == 3) {
            return 8; // Standard 16-team Swiss Top 8 qualified
        }
        return totalTeams / 2;
    }

    /**
     * 4. ROUND ROBIN (RR)
     * Count advancing teams from Round Robin standings (Top K teams).
     */
    public static int countAdvancingRoundRobin(int totalTeams, int cutTarget) {
        if (cutTarget <= 0 || cutTarget >= totalTeams) {
            return Math.max(1, totalTeams / 2);
        }
        return cutTarget;
    }

    /**
     * 5. GROUP STAGE (GS)
     * Calculate total advancing teams from Group Stage: (numGroups * advancePerGroup) + wildcardCount.
     */
    public static int countAdvancingGroupStage(int numGroups, int advancePerGroup, int wildcardCount) {
        int baseAdvancing = Math.max(1, numGroups) * Math.max(1, advancePerGroup);
        return baseAdvancing + Math.max(0, wildcardCount);
    }

    /**
     * Universal Power of 2 Check for Cut Target (must be 2, 4, 8, 16...)
     */
    public static boolean isPowerOfTwo(int n) {
        return n > 1 && (n & (n - 1)) == 0;
    }

    /**
     * Universal Validation of cutTarget for Knockout Formats (SE / DE)
     */
    public static boolean isValidKnockoutCutTarget(int totalTeams, int cutTarget) {
        return cutTarget > 1 && cutTarget < totalTeams && isPowerOfTwo(cutTarget);
    }
}
