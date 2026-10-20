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

    /** Seeded draw shared by SE and DE. Returned entries are physical first-round slots. */
    public static List<Team> drawInitialSlots(List<Team> teams, int topSeedCount) {
        return drawInitialSlots(teams, topSeedCount, java.util.concurrent.ThreadLocalRandom.current());
    }

    public static List<Team> drawInitialSlots(List<Team> teams, int topSeedCount, boolean classic) {
        return drawInitialSlots(teams, topSeedCount, java.util.concurrent.ThreadLocalRandom.current(), classic);
    }

    public static List<Team> drawInitialSlots(List<Team> teams, int topSeedCount, java.util.Random random) {
        return drawInitialSlots(teams, topSeedCount, random, false);
    }

    public static List<Team> drawInitialSlots(List<Team> teams, int topSeedCount, java.util.Random random, boolean classic) {
        if (teams == null || teams.size() < 2) return new ArrayList<>();
        List<Team> ordered = new ArrayList<>(teams);
        ordered.sort(java.util.Comparator.comparingInt((Team t) -> t.getOriginalSeed() > 0 ? t.getOriginalSeed() : Integer.MAX_VALUE)
                .thenComparing(t -> t.getId() == null ? "" : t.getId()));
        int size = 2;
        while (size < ordered.size()) size *= 2;
        int protectedCount = Math.max(0, Math.min(topSeedCount, ordered.size()));
        List<Integer> layout = new ArrayList<>();
        layout.add(1); layout.add(2);
        for (int width = 4; width <= size; width *= 2) {
            List<Integer> next = new ArrayList<>();
            for (int rank : layout) { next.add(rank); next.add(width + 1 - rank); }
            layout = next;
        }
        // Put the better seed first in every opening match, including seed 2 at the start of the lower half.
        int[] positions = new int[size + 1];
        for (int i = 0; i < size; i += 2) {
            positions[Math.min(layout.get(i), layout.get(i + 1))] = i;
            positions[Math.max(layout.get(i), layout.get(i + 1))] = i + 1;
        }
        List<Team> slots = new ArrayList<>(Collections.nCopies(size, (Team) null));
        if (classic) {
            for (int rank = 1; rank <= ordered.size(); rank++) slots.set(positions[rank], ordered.get(rank - 1));
            return slots;
        }
        for (int rank = 1; rank <= Math.min(2, protectedCount); rank++) slots.set(positions[rank], ordered.get(rank - 1));
        // Grand Slam style bands: 3-4, 5-8, 9-12, 13-16, 17-24, 25-32, ...
        for (int first = 3; first <= protectedCount;) {
            int width;
            if (first == 3) width = 2;
            else if (first == 5) width = 4;
            else width = Integer.highestOneBit(first - 1) / 2;
            int last = Math.min(protectedCount, first + width - 1);
            List<Integer> bandPositions = new ArrayList<>();
            for (int rank = first; rank < first + width && rank <= size; rank++) bandPositions.add(positions[rank]);
            Collections.shuffle(bandPositions, random);
            for (int rank = first; rank <= last; rank++) slots.set(bandPositions.get(rank - first), ordered.get(rank - 1));
            first += width;
        }
        List<Team> unseeded = new ArrayList<>(ordered.subList(protectedCount, ordered.size()));
        Collections.shuffle(unseeded, random);
        // Standard occupied positions spread BYEs and avoid empty-vs-empty opening matches.
        int nextTeam = 0;
        for (int rank = 1; rank <= size && nextTeam < unseeded.size(); rank++) {
            if (slots.get(positions[rank]) == null) slots.set(positions[rank], unseeded.get(nextTeam++));
        }
        return slots;
    }


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
                boolean isDone = "COMPLETED".equalsIgnoreCase(m.getStatus())
                        || "DONE".equalsIgnoreCase(m.getStatus())
                        || "FINISHED".equalsIgnoreCase(m.getStatus())
                        || (m.getWinnerTeamId() != null && m.getWinnerTeamId() > 0)
                        || (m.getTeam1Score() != null && m.getTeam2Score() != null && !m.getTeam1Score().equals(m.getTeam2Score()));
                if (isDone) {
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
            if (m.getRoundNumber() == stoppingRound) {
                boolean isDone = "COMPLETED".equalsIgnoreCase(m.getStatus()) || "DONE".equalsIgnoreCase(m.getStatus()) || "FINISHED".equalsIgnoreCase(m.getStatus())
                        || (m.getTeam1Score() != null && m.getTeam2Score() != null) || (m.getWinnerTeamId() != null && m.getWinnerTeamId() > 0);
                if (!isDone) continue;

                Integer winnerId = m.getWinnerTeamId();
                boolean isTeam1Winner = false;
                boolean isTeam2Winner = false;

                if (winnerId != null && (winnerId == 1 || winnerId.equals(m.getTeam1Id()))) {
                    isTeam1Winner = true;
                } else if (winnerId != null && (winnerId == 2 || winnerId.equals(m.getTeam2Id()))) {
                    isTeam2Winner = true;
                } else if (m.getTeam1Score() != null && m.getTeam2Score() != null) {
                    if (m.getTeam1Score() > m.getTeam2Score()) isTeam1Winner = true;
                    else if (m.getTeam2Score() > m.getTeam1Score()) isTeam2Winner = true;
                }

                if (isTeam1Winner && m.getTeam1Name() != null && !m.getTeam1Name().trim().isEmpty() && !"BYE".equalsIgnoreCase(m.getTeam1Name())) {
                    Team team = new Team();
                    String tId = m.getTeam1StringId();
                    if (tId == null || tId.isEmpty()) {
                        tId = m.getTeam1Id() != null ? String.valueOf(m.getTeam1Id()) : ("T_" + m.getTeam1Name());
                    }
                    team.setId(tId);
                    team.setRawName(m.getTeam1Name());
                    team.setOriginalSeed(m.getTeam1Seed() != null ? m.getTeam1Seed() : 0);
                    team.setStatus("QUALIFIED");
                    qualifiedTeams.add(team);
                } else if (isTeam2Winner && m.getTeam2Name() != null && !m.getTeam2Name().trim().isEmpty() && !"BYE".equalsIgnoreCase(m.getTeam2Name())) {
                    Team team = new Team();
                    String tId = m.getTeam2StringId();
                    if (tId == null || tId.isEmpty()) {
                        tId = m.getTeam2Id() != null ? String.valueOf(m.getTeam2Id()) : ("T_" + m.getTeam2Name());
                    }
                    team.setId(tId);
                    team.setRawName(m.getTeam2Name());
                    team.setOriginalSeed(m.getTeam2Seed() != null ? m.getTeam2Seed() : 0);
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
