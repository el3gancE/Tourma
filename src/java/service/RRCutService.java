package service;

import model.Team;
import java.util.*;

/**
 * RRCutService
 * Service for Round Robin Cut Stage transition to Stage 2.
 */
public class RRCutService {

    public static class RRTeam implements Comparable<RRTeam> {
        private String id;
        private String name;
        private int rank;
        private int seed;
        private int points;
        private int matchesPlayed;
        private int won;
        private int drawn;
        private int lost;
        private int goalsFor;
        private int goalsAgainst;
        private int goalDifference;

        public RRTeam() {}

        public RRTeam(String id, String name, int rank, int seed, int points, int gd, int gf) {
            this.id = id;
            this.name = name;
            this.rank = rank;
            this.seed = seed;
            this.points = points;
            this.goalDifference = gd;
            this.goalsFor = gf;
        }

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public int getRank() { return rank; }
        public void setRank(int rank) { this.rank = rank; }
        public int getSeed() { return seed; }
        public void setSeed(int seed) { this.seed = seed; }
        public int getPoints() { return points; }
        public void setPoints(int points) { this.points = points; }
        public int getMatchesPlayed() { return matchesPlayed; }
        public void setMatchesPlayed(int matchesPlayed) { this.matchesPlayed = matchesPlayed; }
        public int getWon() { return won; }
        public void setWon(int won) { this.won = won; }
        public int getDrawn() { return drawn; }
        public void setDrawn(int drawn) { this.drawn = drawn; }
        public int getLost() { return lost; }
        public void setLost(int lost) { this.lost = lost; }
        public int getGoalsFor() { return goalsFor; }
        public void setGoalsFor(int goalsFor) { this.goalsFor = goalsFor; }
        public int getGoalsAgainst() { return goalsAgainst; }
        public void setGoalsAgainst(int goalsAgainst) { this.goalsAgainst = goalsAgainst; }
        public int getGoalDifference() { return goalDifference; }
        public void setGoalDifference(int goalDifference) { this.goalDifference = goalDifference; }

        public Team toTeamModel() {
            Team t = new Team();
            t.setId(this.id);
            t.setRawName(this.name);
            t.setNormalizedName(this.name);
            t.setStatus("QUALIFIED");
            return t;
        }

        @Override
        public int compareTo(RRTeam other) {
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
    }

    /**
     * Extract Top K qualified teams from Round Robin standings.
     */
    public static List<RRTeam> extractQualifiedTeams(List<RRTeam> standings, int cutTarget) {
        List<RRTeam> qualified = new ArrayList<>();
        if (standings == null || standings.isEmpty() || cutTarget <= 0) {
            return qualified;
        }

        Collections.sort(standings);
        int limit = Math.min(cutTarget, standings.size());
        for (int i = 0; i < limit; i++) {
            RRTeam t = standings.get(i);
            t.setRank(i + 1);
            qualified.add(t);
        }
        return qualified;
    }

    /**
     * Generate seed order for Stage 2 Knockout (Single / Double Elimination).
     */
    public static List<Team> generateStage2BracketSeedOrder(List<RRTeam> qualifiedTeams) {
        List<Team> ordered = new ArrayList<>();
        if (qualifiedTeams == null || qualifiedTeams.isEmpty()) return ordered;

        for (RRTeam rt : qualifiedTeams) {
            ordered.add(rt.toTeamModel());
        }
        return ordered;
    }
}
