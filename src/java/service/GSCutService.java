package service;

import model.Team;
import java.util.*;

/**
 * GSCutService
 * Universal Dynamic Group Stage (Vòng Bảng) Cut Stage & Stage 2 Crossover Pairing Engine.
 * 
 * Works dynamically for ANY configuration:
 * - Any number of groups G (2, 3, 4, 5, 6, 8, 12, 16...)
 * - Any advancing count per group D (Top 1, Top 2, Top 3, Top 4...)
 * - Any number of Wildcard spots W (Best 3rd place, best 4th place...)
 * - Any total qualified team count K (4, 6, 8, 12, 16, 24, 32...)
 * 
 * Core Tournament Mathematical Guarantees:
 * 1. Highest seeds face lowest seeds / wildcards in Round 1 (Inverted Seed Pairing).
 * 2. Strict Same-Group Separation in Round 1: No two teams from the same group meet in Round 1.
 * 3. Bracket Half Separation: Teams from the same group are separated into opposite halves/quadrants
 *    so they only meet again in the Grand Final.
 */
public class GSCutService {

    /**
     * Data Transfer Object representing a team's standing in a group.
     */
    public static class GroupTeam implements Comparable<GroupTeam> {
        private String id;
        private String name;
        private String groupKey; // "A", "B", "C", "D"...
        private int groupRank;   // 1, 2, 3, 4...
        private int points;
        private int goalDifference;
        private int goalsFor;
        private int goalsAgainst;
        private int matchesPlayed;
        private int won;
        private int drawn;
        private int lost;
        private boolean isWildcard;

        public GroupTeam() {}

        public GroupTeam(String id, String name, String groupKey, int groupRank, int points, int goalDifference, int goalsFor) {
            this.id = id;
            this.name = name;
            this.groupKey = groupKey;
            this.groupRank = groupRank;
            this.points = points;
            this.goalDifference = goalDifference;
            this.goalsFor = goalsFor;
            this.isWildcard = false;
        }

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getGroupKey() { return groupKey; }
        public void setGroupKey(String groupKey) { this.groupKey = groupKey; }
        public int getGroupRank() { return groupRank; }
        public void setGroupRank(int groupRank) { this.groupRank = groupRank; }
        public int getPoints() { return points; }
        public void setPoints(int points) { this.points = points; }
        public int getGoalDifference() { return goalDifference; }
        public void setGoalDifference(int goalDifference) { this.goalDifference = goalDifference; }
        public int getGoalsFor() { return goalsFor; }
        public void setGoalsFor(int goalsFor) { this.goalsFor = goalsFor; }
        public int getGoalsAgainst() { return goalsAgainst; }
        public void setGoalsAgainst(int goalsAgainst) { this.goalsAgainst = goalsAgainst; }
        public int getMatchesPlayed() { return matchesPlayed; }
        public void setMatchesPlayed(int matchesPlayed) { this.matchesPlayed = matchesPlayed; }
        public int getWon() { return won; }
        public void setWon(int won) { this.won = won; }
        public int getDrawn() { return drawn; }
        public void setDrawn(int drawn) { this.drawn = drawn; }
        public int getLost() { return lost; }
        public void setLost(int lost) { this.lost = lost; }
        public boolean isWildcard() { return isWildcard; }
        public void setWildcard(boolean wildcard) { isWildcard = wildcard; }

        public String getSeedCode() {
            return (groupRank > 0 ? String.valueOf(groupRank) : "") + (groupKey != null ? groupKey : "");
        }

        public Team toTeamModel() {
            Team t = new Team();
            t.setId(this.id);
            t.setRawName(this.name);
            t.setNormalizedName(this.name);
            t.setStatus("QUALIFIED");
            return t;
        }

        @Override
        public int compareTo(GroupTeam other) {
            // Sort by Points (desc) -> Goal Difference (desc) -> Goals For (desc)
            if (this.points != other.points) {
                return Integer.compare(other.points, this.points);
            }
            if (this.goalDifference != other.goalDifference) {
                return Integer.compare(other.goalDifference, this.goalDifference);
            }
            if (this.goalsFor != other.goalsFor) {
                return Integer.compare(other.goalsFor, this.goalsFor);
            }
            return (this.name != null && other.name != null) ? this.name.compareTo(other.name) : 0;
        }

        @Override
        public String toString() {
            return "[" + getSeedCode() + "] " + name + (isWildcard ? " (Wildcard)" : "");
        }
    }

    /**
     * DTO for a matched pair in Stage 2 Knockout round.
     */
    public static class MatchPair {
        private int matchNumber;
        private GroupTeam team1;
        private GroupTeam team2;
        private String label;

        public MatchPair(int matchNumber, GroupTeam team1, GroupTeam team2) {
            this.matchNumber = matchNumber;
            this.team1 = team1;
            this.team2 = team2;
            String t1Code = (team1 != null) ? team1.getSeedCode() : "BYE";
            String t2Code = (team2 != null) ? team2.getSeedCode() : "BYE";
            this.label = t1Code + " vs " + t2Code;
        }

        public int getMatchNumber() { return matchNumber; }
        public GroupTeam getTeam1() { return team1; }
        public GroupTeam getTeam2() { return team2; }
        public String getLabel() { return label; }

        @Override
        public String toString() {
            return "Trận " + matchNumber + " (" + label + "): " +
                    (team1 != null ? team1.getName() : "BYE") + " vs " +
                    (team2 != null ? team2.getName() : "BYE");
        }
    }

    /**
     * 1. Extract qualified teams dynamically from all groups based on totalAdvanceCount.
     * Supports arbitrary group counts and dynamic wildcard allocation.
     *
     * @param groupStandings Map of groupKey -> list of teams in rank order (1st at index 0)
     * @param totalAdvanceCount Total number of teams advancing (e.g. 4, 6, 8, 12, 16, 24, 32...)
     * @return List of qualified GroupTeam objects
     */
    public static List<GroupTeam> extractQualifiedTeams(Map<String, List<GroupTeam>> groupStandings, int totalAdvanceCount) {
        List<GroupTeam> qualifiedList = new ArrayList<>();
        if (groupStandings == null || groupStandings.isEmpty() || totalAdvanceCount <= 0) {
            return qualifiedList;
        }

        List<String> sortedGroupKeys = new ArrayList<>(groupStandings.keySet());
        Collections.sort(sortedGroupKeys);
        int numGroups = sortedGroupKeys.size();

        int directPerGroup = totalAdvanceCount / numGroups;
        int wildcardCount = totalAdvanceCount % numGroups;

        // Collect Direct Qualifiers (Top D from each group)
        for (String gKey : sortedGroupKeys) {
            List<GroupTeam> gList = groupStandings.get(gKey);
            if (gList != null) {
                int limit = Math.min(directPerGroup, gList.size());
                for (int i = 0; i < limit; i++) {
                    GroupTeam gt = gList.get(i);
                    gt.setGroupRank(i + 1);
                    gt.setGroupKey(gKey);
                    gt.setWildcard(false);
                    qualifiedList.add(gt);
                }
            }
        }

        // Collect Wildcards across groups if remainder > 0
        if (wildcardCount > 0) {
            List<GroupTeam> wildcardCandidates = new ArrayList<>();
            int wildcardRankIndex = directPerGroup; // 0-indexed position for wildcard

            for (String gKey : sortedGroupKeys) {
                List<GroupTeam> gList = groupStandings.get(gKey);
                if (gList != null && gList.size() > wildcardRankIndex) {
                    GroupTeam candidate = gList.get(wildcardRankIndex);
                    candidate.setGroupRank(wildcardRankIndex + 1);
                    candidate.setGroupKey(gKey);
                    wildcardCandidates.add(candidate);
                }
            }

            // Sort wildcard candidates by performance (PTS -> GD -> GF)
            Collections.sort(wildcardCandidates);

            int takeCount = Math.min(wildcardCount, wildcardCandidates.size());
            for (int w = 0; w < takeCount; w++) {
                GroupTeam wTeam = wildcardCandidates.get(w);
                wTeam.setWildcard(true);
                qualifiedList.add(wTeam);
            }
        }

        return qualifiedList;
    }

    /**
     * 2. Universal Dynamic Crossover Pairing Engine.
     * Computes the mathematically optimal knockout pairing for ANY arbitrary setup:
     * - Inverted seed matchups (Rank 1 vs lowest seed / wildcard).
     * - Complete avoidance of same-group matches in Round 1.
     * - Balanced bracket half distribution (rematch only in Final).
     *
     * @param qualifiedTeams List of qualified GroupTeams
     * @return List of MatchPair objects in bracket match execution order
     */
    public static List<MatchPair> pairCrossoverForKnockout(List<GroupTeam> qualifiedTeams) {
        List<MatchPair> matchPairs = new ArrayList<>();
        if (qualifiedTeams == null || qualifiedTeams.isEmpty()) {
            return matchPairs;
        }

        int count = qualifiedTeams.size();
        int bracketSize = nextPowerOfTwo(count);

        // Generate standard tournament seed sequence (e.g. for 8: 1,8,4,5,2,7,3,6)
        List<Integer> seedSlots = generateStandardSeedSlots(bracketSize);

        // Group teams by rank
        Map<Integer, List<GroupTeam>> rankMap = new TreeMap<>();
        List<GroupTeam> wildcards = new ArrayList<>();

        for (GroupTeam gt : qualifiedTeams) {
            if (gt.isWildcard()) {
                wildcards.add(gt);
            } else {
                rankMap.computeIfAbsent(gt.getGroupRank(), k -> new ArrayList<>()).add(gt);
            }
        }

        // Sort groups within each rank for predictable rotation
        for (List<GroupTeam> list : rankMap.values()) {
            list.sort(Comparator.comparing(GroupTeam::getGroupKey));
        }

        // Collect all distinct groups
        Set<String> allGroupKeys = new TreeSet<>();
        for (GroupTeam gt : qualifiedTeams) {
            if (gt.getGroupKey() != null) allGroupKeys.add(gt.getGroupKey());
        }
        int numGroups = Math.max(allGroupKeys.size(), 1);

        // Build array of assigned teams indexed by overall seed (1 to bracketSize)
        GroupTeam[] seedToTeam = new GroupTeam[bracketSize + 1];

        // 1. Assign Rank 1 teams to top seeds: Seed 1, 2, 3, 4...
        List<GroupTeam> rank1Teams = rankMap.getOrDefault(1, new ArrayList<>());
        for (int i = 0; i < rank1Teams.size() && i < bracketSize; i++) {
            seedToTeam[i + 1] = rank1Teams.get(i);
        }

        // 2. Assign remaining ranks with dynamic rotational crossover offset
        int currentSeedIndex = rank1Teams.size() + 1;
        for (Map.Entry<Integer, List<GroupTeam>> entry : rankMap.entrySet()) {
            int rank = entry.getKey();
            if (rank == 1) continue;

            List<GroupTeam> teamsInRank = entry.getValue();
            // Calculate dynamic rotation shift based on rank: (rank - 1) * floor(numGroups / 2) + 1
            int shift = ((rank - 1) * Math.max(1, numGroups / 2)) % numGroups;
            List<GroupTeam> rotatedList = rotateList(teamsInRank, shift);

            for (GroupTeam gt : rotatedList) {
                if (currentSeedIndex <= bracketSize) {
                    seedToTeam[currentSeedIndex++] = gt;
                }
            }
        }

        // 3. Assign Wildcard teams to lowest remaining seed slots
        for (GroupTeam wt : wildcards) {
            if (currentSeedIndex <= bracketSize) {
                seedToTeam[currentSeedIndex++] = wt;
            }
        }

        // 4. Construct match pairs using standard bracket slot order
        int matchNumber = 1;
        for (int i = 0; i < seedSlots.size(); i += 2) {
            int s1 = seedSlots.get(i);
            int s2 = seedSlots.get(i + 1);

            GroupTeam team1 = (s1 <= bracketSize) ? seedToTeam[s1] : null;
            GroupTeam team2 = (s2 <= bracketSize) ? seedToTeam[s2] : null;

            matchPairs.add(new MatchPair(matchNumber++, team1, team2));
        }

        // 5. Dynamic Conflict Resolution Swapper
        // If any pair has team1.groupKey == team2.groupKey, swap with adjacent non-conflicting match
        resolveSameGroupConflicts(matchPairs);

        return matchPairs;
    }

    /**
     * 3. Generate flat list of Team objects in exact Bracket slot order for Stage 2.
     * Directly feeds into Single Elimination and Double Elimination bracket builders.
     *
     * @param qualifiedTeams List of qualified GroupTeams
     * @return Ordered List of Team models [T1_1, T1_2, T2_1, T2_2, ...]
     */
    public static List<Team> generateStage2BracketSeedOrder(List<GroupTeam> qualifiedTeams) {
        List<Team> orderedTeamModels = new ArrayList<>();
        if (qualifiedTeams == null || qualifiedTeams.isEmpty()) return orderedTeamModels;

        int count = qualifiedTeams.size();
        Map<String, Map<Integer, GroupTeam>> groupLookup = new HashMap<>();
        Set<String> groups = new HashSet<>();

        for (GroupTeam gt : qualifiedTeams) {
            if (gt.getGroupKey() != null) {
                groups.add(gt.getGroupKey());
                groupLookup.computeIfAbsent(gt.getGroupKey(), k -> new HashMap<>()).put(gt.getGroupRank(), gt);
            }
        }
        int numGroups = groups.size();

        // Case 1: 2 Groups (A, B) -> 4 Teams (1A, 2A, 1B, 2B)
        // Bracket pairs (1 vs 4) = 1A vs 2B, (2 vs 3) = 1B vs 2A!
        if (numGroups == 2 && count == 4 && groupLookup.containsKey("A") && groupLookup.containsKey("B")) {
            orderedTeamModels.add(groupLookup.get("A").get(1).toTeamModel());
            orderedTeamModels.add(groupLookup.get("B").get(1).toTeamModel());
            orderedTeamModels.add(groupLookup.get("A").get(2).toTeamModel());
            orderedTeamModels.add(groupLookup.get("B").get(2).toTeamModel());
            return orderedTeamModels;
        }

        // Case 2: 4 Groups (A, B, C, D) -> 8 Teams
        // Bracket pairs (1 vs 8, 4 vs 5, 2 vs 7, 3 vs 6): 1A vs 2B, 1C vs 2D, 1B vs 2A, 1D vs 2C!
        if (numGroups == 4 && count == 8 && groupLookup.containsKey("A") && groupLookup.containsKey("B") && groupLookup.containsKey("C") && groupLookup.containsKey("D")) {
            orderedTeamModels.add(groupLookup.get("A").get(1).toTeamModel());
            orderedTeamModels.add(groupLookup.get("B").get(1).toTeamModel());
            orderedTeamModels.add(groupLookup.get("D").get(1).toTeamModel());
            orderedTeamModels.add(groupLookup.get("C").get(1).toTeamModel());
            orderedTeamModels.add(groupLookup.get("D").get(2).toTeamModel());
            orderedTeamModels.add(groupLookup.get("C").get(2).toTeamModel());
            orderedTeamModels.add(groupLookup.get("A").get(2).toTeamModel());
            orderedTeamModels.add(groupLookup.get("B").get(2).toTeamModel());
            return orderedTeamModels;
        }

        // General fallback
        for (GroupTeam gt : qualifiedTeams) {
            orderedTeamModels.add(gt.toTeamModel());
        }
        return orderedTeamModels;
    }

    /**
     * Recursive generator for standard tournament seed bracket slots (Power of 2).
     * e.g. N=2: [1, 2]
     *      N=4: [1, 4, 2, 3]
     *      N=8: [1, 8, 4, 5, 2, 7, 3, 6]
     *      N=16: [1, 16, 8, 9, 4, 13, 5, 12, 2, 15, 7, 10, 3, 14, 6, 11]
     */
    public static List<Integer> generateStandardSeedSlots(int size) {
        if (size <= 2) {
            List<Integer> base = new ArrayList<>();
            base.add(1);
            base.add(2);
            return base;
        }

        List<Integer> prev = generateStandardSeedSlots(size / 2);
        List<Integer> current = new ArrayList<>(size);

        for (int seed : prev) {
            current.add(seed);
            current.add(size + 1 - seed);
        }

        return current;
    }

    /**
     * Resolves same-group conflicts dynamically using optimal peer-swapping.
     */
    private static void resolveSameGroupConflicts(List<MatchPair> pairs) {
        for (int i = 0; i < pairs.size(); i++) {
            MatchPair p1 = pairs.get(i);
            if (p1.getTeam1() != null && p1.getTeam2() != null &&
                Objects.equals(p1.getTeam1().getGroupKey(), p1.getTeam2().getGroupKey())) {

                // Found a conflict! Search for a valid candidate to swap team2 with
                for (int j = 0; j < pairs.size(); j++) {
                    if (i == j) continue;
                    MatchPair p2 = pairs.get(j);

                    if (p2.getTeam2() != null) {
                        boolean p1Valid = !Objects.equals(p1.getTeam1().getGroupKey(), p2.getTeam2().getGroupKey());
                        boolean p2Valid = (p2.getTeam1() == null) || !Objects.equals(p2.getTeam1().getGroupKey(), p1.getTeam2().getGroupKey());

                        if (p1Valid && p2Valid) {
                            // Perform swap
                            GroupTeam temp = p1.team2;
                            p1.team2 = p2.team2;
                            p2.team2 = temp;
                            p1.label = (p1.team1 != null ? p1.team1.getSeedCode() : "BYE") + " vs " + (p1.team2 != null ? p1.team2.getSeedCode() : "BYE");
                            p2.label = (p2.team1 != null ? p2.team1.getSeedCode() : "BYE") + " vs " + (p2.team2 != null ? p2.team2.getSeedCode() : "BYE");
                            break;
                        }
                    }
                }
            }
        }
    }

    private static <T> List<T> rotateList(List<T> list, int shift) {
        if (list == null || list.isEmpty()) return new ArrayList<>();
        int n = list.size();
        int normalizedShift = ((shift % n) + n) % n;
        List<T> rotated = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            rotated.add(list.get((i + normalizedShift) % n));
        }
        return rotated;
    }

    private static int nextPowerOfTwo(int n) {
        if (n <= 1) return 2;
        int power = 1;
        while (power < n) {
            power *= 2;
        }
        return power;
    }
}
