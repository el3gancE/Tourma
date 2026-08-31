package service;

import model.Match;
import model.Team;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * SECutService
 * Handles Cut Stage logic for Single Elimination Stage 1:
 * - Delegates stopping round calculation to CountAdvanceTeamService.
 * - Extracts winning qualified teams at the stopping round.
 * - Randomly shuffles qualified teams for Stage 2 (SE to SE, SE to DE, SE to RR).
 */
public class SECutService {

    /**
     * Delegate calculation of SE stopping round to CountAdvanceTeamService.
     */
    public static int getStoppingRound(int totalTeams, int cutTarget) {
        return CountAdvanceTeamService.calculateStoppingRoundSE(totalTeams, cutTarget);
    }

    /**
     * Check if all matches in the stopping round are completed.
     *
     * @param matches List of matches
     * @param stoppingRound Stopping round number
     * @return true if all matches in stoppingRound are COMPLETED
     */
    public static boolean isStoppingRoundFinished(List<Match> matches, int stoppingRound) {
        if (matches == null || matches.isEmpty()) {
            return false;
        }

        int matchCountInRound = 0;
        int completedCount = 0;

        for (Match m : matches) {
            if (m.getRoundNumber() == stoppingRound) {
                matchCountInRound++;
                if ("COMPLETED".equalsIgnoreCase(m.getStatus()) || "DONE".equalsIgnoreCase(m.getStatus())) {
                    completedCount++;
                }
            }
        }

        return matchCountInRound > 0 && matchCountInRound == completedCount;
    }

    /**
     * Extract qualified teams from Single Elimination matches at the stopping round.
     * Collects all winners from matches played at roundNumber == stoppingRound.
     *
     * @param matches List of all matches in Stage 1 SE
     * @param stoppingRound The round number where Stage 1 stops
     * @return List of Teams that won at the stopping round
     */
    public static List<Team> extractQualifiedTeams(List<Match> matches, int stoppingRound) {
        List<Team> qualifiedTeams = new ArrayList<>();
        if (matches == null || matches.isEmpty()) {
            return qualifiedTeams;
        }

        for (Match m : matches) {
            if (m.getRoundNumber() == stoppingRound && ("COMPLETED".equalsIgnoreCase(m.getStatus()) || "DONE".equalsIgnoreCase(m.getStatus()))) {
                Integer winnerId = m.getWinnerTeamId();
                if (winnerId != null) {
                    Team team = new Team();
                    if (winnerId.equals(m.getTeam1Id())) {
                        team.setId(String.valueOf(m.getTeam1Id()));
                        team.setRawName(m.getTeam1Name());
                        team.setOriginalSeed(m.getTeam1Seed() != null ? m.getTeam1Seed() : 0);
                    } else if (winnerId.equals(m.getTeam2Id())) {
                        team.setId(String.valueOf(m.getTeam2Id()));
                        team.setRawName(m.getTeam2Name());
                        team.setOriginalSeed(m.getTeam2Seed() != null ? m.getTeam2Seed() : 0);
                    }
                    team.setStatus("QUALIFIED");
                    qualifiedTeams.add(team);
                }
            }
        }

        return qualifiedTeams;
    }

    /**
     * Randomly shuffle qualified teams for Stage 2 (100% Random rule for SE Stage 1).
     * Applies to: SE -> SE, SE -> DE, SE -> RR.
     *
     * @param qualifiedTeams List of teams advancing from Stage 1 SE
     * @return Shuffled list of teams ready for Stage 2 seed assignment
     */
    public static List<Team> pairForStage2Random(List<Team> qualifiedTeams) {
        if (qualifiedTeams == null) return new ArrayList<>();
        List<Team> shuffledList = new ArrayList<>(qualifiedTeams);
        Collections.shuffle(shuffledList);
        return shuffledList;
    }
}
