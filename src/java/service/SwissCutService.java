package service;

import model.Team;
import java.util.*;

/**
 * SwissCutService
 * Service for Swiss Stage Cut Stage & Standings evaluation:
 * - Sorts by Wins (desc) -> Buchholz (desc) -> Sonneborn-Berger -> Point Diff (desc)
 * - Extracts Top K qualified teams
 */
public class SwissCutService {

    public static class SwissTeam implements Comparable<SwissTeam> {
        private String id;
        private String name;
        private int rank;
        private int seed;
        private int wins;
        private int losses;
        private int draws;
        private int buchholzScore;
        private int pointDifference;

        public SwissTeam() {}

        public SwissTeam(String id, String name, int wins, int losses, int buchholzScore, int pointDifference) {
            this.id = id;
            this.name = name;
            this.wins = wins;
            this.losses = losses;
            this.buchholzScore = buchholzScore;
            this.pointDifference = pointDifference;
        }

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public int getRank() { return rank; }
        public void setRank(int rank) { this.rank = rank; }
        public int getSeed() { return seed; }
        public void setSeed(int seed) { this.seed = seed; }
        public int getWins() { return wins; }
        public void setWins(int wins) { this.wins = wins; }
        public int getLosses() { return losses; }
        public void setLosses(int losses) { this.losses = losses; }
        public int getDraws() { return draws; }
        public void setDraws(int draws) { this.draws = draws; }
        public int getBuchholzScore() { return buchholzScore; }
        public void setBuchholzScore(int buchholzScore) { this.buchholzScore = buchholzScore; }
        public int getPointDifference() { return pointDifference; }
        public void setPointDifference(int pointDifference) { this.pointDifference = pointDifference; }

        public Team toTeamModel() {
            Team t = new Team();
            t.setId(this.id);
            t.setRawName(this.name);
            t.setNormalizedName(this.name);
            t.setStatus("QUALIFIED");
            return t;
        }

        @Override
        public int compareTo(SwissTeam other) {
            // Wins (desc)
            if (this.wins != other.wins) {
                return Integer.compare(other.wins, this.wins);
            }
            // Buchholz Score (desc)
            if (this.buchholzScore != other.buchholzScore) {
                return Integer.compare(other.buchholzScore, this.buchholzScore);
            }
            // Point Difference (desc)
            if (this.pointDifference != other.pointDifference) {
                return Integer.compare(other.pointDifference, this.pointDifference);
            }
            return (this.name != null && other.name != null) ? this.name.compareTo(other.name) : 0;
        }
    }

    /**
     * Extract Top K qualified teams from Swiss standings.
     */
    public static List<SwissTeam> extractQualifiedTeams(List<SwissTeam> standings, int cutTarget) {
        List<SwissTeam> qualified = new ArrayList<>();
        if (standings == null || standings.isEmpty() || cutTarget <= 0) {
            return qualified;
        }

        Collections.sort(standings);
        int limit = Math.min(cutTarget, standings.size());
        for (int i = 0; i < limit; i++) {
            SwissTeam st = standings.get(i);
            st.setRank(i + 1);
            qualified.add(st);
        }
        return qualified;
    }

    /**
     * Generate seed order for Stage 2 Knockout.
     */
    public static List<Team> generateStage2BracketSeedOrder(List<SwissTeam> qualifiedTeams) {
        List<Team> ordered = new ArrayList<>();
        if (qualifiedTeams == null || qualifiedTeams.isEmpty()) return ordered;

        for (SwissTeam st : qualifiedTeams) {
            ordered.add(st.toTeamModel());
        }
        return ordered;
    }
}
