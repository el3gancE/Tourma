package service;

import java.util.Random;

/**
 * RandomService
 * Centralized service for generating realistic match scores, weighted random distributions,
 * and tournament bracket simulations.
 */
public class RandomService {

    private static final Random random = new Random();

    public static class RandomMatchResult {
        private final int team1Score;
        private final int team2Score;
        private final String winnerSlot; // "team1" or "team2"

        public RandomMatchResult(int team1Score, int team2Score, String winnerSlot) {
            this.team1Score = team1Score;
            this.team2Score = team2Score;
            this.winnerSlot = winnerSlot;
        }

        public int getTeam1Score() {
            return team1Score;
        }

        public int getTeam2Score() {
            return team2Score;
        }

        public String getWinnerSlot() {
            return winnerSlot;
        }
    }

    /**
     * Generate weighted match scores:
     * - If targetWinScore is null or <= 0:
     *     75% probability for winning score in range [2, 5]
     *     25% probability for winning score in range [6, 9]
     * - If targetWinScore is specified (> 0), winning score = targetWinScore.
     * - Loser score is strictly random from 0 to (winning score - 1).
     * - 50/50 probability for Team 1 vs Team 2 victory.
     */
    public static RandomMatchResult generateMatchScore(Integer targetWinScore) {
        int finalWinScore;
        if (targetWinScore != null && targetWinScore > 0) {
            finalWinScore = targetWinScore;
        } else {
            double p = random.nextDouble();
            if (p < 0.75) {
                finalWinScore = random.nextInt(4) + 2; // 2, 3, 4, 5
            } else {
                finalWinScore = random.nextInt(4) + 6; // 6, 7, 8, 9
            }
        }

        boolean isT1Winner = random.nextBoolean();
        int loserScore = (finalWinScore > 0) ? random.nextInt(finalWinScore) : 0;

        int s1 = isT1Winner ? finalWinScore : loserScore;
        int s2 = isT1Winner ? loserScore : finalWinScore;
        String winner = isT1Winner ? "team1" : "team2";

        return new RandomMatchResult(s1, s2, winner);
    }
}
