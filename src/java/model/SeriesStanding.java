package model;

import java.sql.Timestamp;

/**
 * Entity model for series_standings table
 */
public class SeriesStanding {
    private String id;
    private String seriesId;
    private int phaseNumber;
    private int divisionLevel;
    private int seasonNumber;
    private String normalizedTeamName;
    private String partnerParticipantId;
    private String groupName;
    private int totalRollingPoints;
    private double currentElo;
    private int matchesPlayed;
    private int wins;
    private int draws;
    private int losses;
    private int goalsFor;
    private int goalsAgainst;
    private int goalDiff;
    private int points;
    private String promotionStatus;
    private int rankOverall;
    private int rankInGroup;
    private Timestamp updatedAt;

    public SeriesStanding() {
    }

    public SeriesStanding(String id, String seriesId, int phaseNumber, String normalizedTeamName,
                          int totalRollingPoints, int rankOverall, Timestamp updatedAt) {
        this.id = id;
        this.seriesId = seriesId;
        this.phaseNumber = phaseNumber;
        this.normalizedTeamName = normalizedTeamName;
        this.totalRollingPoints = totalRollingPoints;
        this.rankOverall = rankOverall;
        this.updatedAt = updatedAt;
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getSeriesId() { return seriesId; }
    public void setSeriesId(String seriesId) { this.seriesId = seriesId; }

    public int getPhaseNumber() { return phaseNumber; }
    public void setPhaseNumber(int phaseNumber) { this.phaseNumber = phaseNumber; }

    public int getDivisionLevel() { return divisionLevel; }
    public void setDivisionLevel(int divisionLevel) { this.divisionLevel = divisionLevel; }

    public int getSeasonNumber() { return seasonNumber; }
    public void setSeasonNumber(int seasonNumber) { this.seasonNumber = seasonNumber; }

    public String getNormalizedTeamName() { return normalizedTeamName; }
    public void setNormalizedTeamName(String normalizedTeamName) { this.normalizedTeamName = normalizedTeamName; }

    public String getPartnerParticipantId() { return partnerParticipantId; }
    public void setPartnerParticipantId(String partnerParticipantId) { this.partnerParticipantId = partnerParticipantId; }

    public String getGroupName() { return groupName; }
    public void setGroupName(String groupName) { this.groupName = groupName; }

    public int getTotalRollingPoints() { return totalRollingPoints; }
    public void setTotalRollingPoints(int totalRollingPoints) { this.totalRollingPoints = totalRollingPoints; }

    public double getCurrentElo() { return currentElo; }
    public void setCurrentElo(double currentElo) { this.currentElo = currentElo; }

    public int getMatchesPlayed() { return matchesPlayed; }
    public void setMatchesPlayed(int matchesPlayed) { this.matchesPlayed = matchesPlayed; }

    public int getWins() { return wins; }
    public void setWins(int wins) { this.wins = wins; }

    public int getDraws() { return draws; }
    public void setDraws(int draws) { this.draws = draws; }

    public int getLosses() { return losses; }
    public void setLosses(int losses) { this.losses = losses; }

    public int getGoalsFor() { return goalsFor; }
    public void setGoalsFor(int goalsFor) { this.goalsFor = goalsFor; }

    public int getGoalsAgainst() { return goalsAgainst; }
    public void setGoalsAgainst(int goalsAgainst) { this.goalsAgainst = goalsAgainst; }

    public int getGoalDiff() { return goalDiff; }
    public void setGoalDiff(int goalDiff) { this.goalDiff = goalDiff; }

    public int getPoints() { return points; }
    public void setPoints(int points) { this.points = points; }

    public String getPromotionStatus() { return promotionStatus; }
    public void setPromotionStatus(String promotionStatus) { this.promotionStatus = promotionStatus; }

    public int getRankOverall() { return rankOverall; }
    public void setRankOverall(int rankOverall) { this.rankOverall = rankOverall; }

    public int getRankInGroup() { return rankInGroup; }
    public void setRankInGroup(int rankInGroup) { this.rankInGroup = rankInGroup; }

    private int tournamentsPlayedInWindow;
    private int latestTourneyPoints;
    private int netChange;
    private int rankChange;

    public int getTournamentsPlayedInWindow() { return tournamentsPlayedInWindow; }
    public void setTournamentsPlayedInWindow(int tournamentsPlayedInWindow) { this.tournamentsPlayedInWindow = tournamentsPlayedInWindow; }

    public int getLatestTourneyPoints() { return latestTourneyPoints; }
    public void setLatestTourneyPoints(int latestTourneyPoints) { this.latestTourneyPoints = latestTourneyPoints; }

    public int getNetChange() { return netChange; }
    public void setNetChange(int netChange) { this.netChange = netChange; }

    public int getRankChange() { return rankChange; }
    public void setRankChange(int rankChange) { this.rankChange = rankChange; }

    public Timestamp getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }
}
