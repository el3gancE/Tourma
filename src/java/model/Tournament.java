package model;

import java.sql.Timestamp;

/**
 * Tournament Entity Model matching database.sql tournaments table
 */
public class Tournament {
    private String id;
    private String seriesId;
    private String name;
    private String tournamentType; // SINGLE_STAGE, MULTI_STAGE
    private String format; // SINGLE_ELIMINATION, DOUBLE_ELIMINATION, ROUND_ROBIN
    private String seriesEventType; // QUALIFIER, MAIN, NONE
    private String tierName; // S, A, B, C, D
    private int tournamentIndexInSeries;
    private int phaseNumber;
    private int maxTeamsPerGroup;
    private int advancingSeatsCount;
    private String linkedQualifierTournamentId;
    private String status; // DRAFT, ONGOING, COMPLETED
    private Timestamp createdAt;
    private String championName;

    public Tournament() {
        this.format = "SINGLE_ELIMINATION";
    }

    public Tournament(String id, String seriesId, String name, String tournamentType, String seriesEventType,
                      String tierName, int tournamentIndexInSeries, int phaseNumber, int maxTeamsPerGroup,
                      int advancingSeatsCount, String linkedQualifierTournamentId, String status, Timestamp createdAt) {
        this.id = id;
        this.seriesId = seriesId;
        this.name = name;
        this.tournamentType = tournamentType;
        this.format = "SINGLE_ELIMINATION";
        this.seriesEventType = seriesEventType;
        this.tierName = tierName;
        this.tournamentIndexInSeries = tournamentIndexInSeries;
        this.phaseNumber = phaseNumber;
        this.maxTeamsPerGroup = maxTeamsPerGroup;
        this.advancingSeatsCount = advancingSeatsCount;
        this.linkedQualifierTournamentId = linkedQualifierTournamentId;
        this.status = status;
        this.createdAt = createdAt;
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getSeriesId() { return seriesId; }
    public void setSeriesId(String seriesId) { this.seriesId = seriesId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getTournamentType() { return tournamentType; }
    public void setTournamentType(String tournamentType) { this.tournamentType = tournamentType; }

    public String getFormat() { return format; }
    public void setFormat(String format) { this.format = format; }

    public String getSeriesEventType() { return seriesEventType; }
    public void setSeriesEventType(String seriesEventType) { this.seriesEventType = seriesEventType; }

    public String getTierName() { return tierName; }
    public void setTierName(String tierName) { this.tierName = tierName; }

    public int getTournamentIndexInSeries() { return tournamentIndexInSeries; }
    public void setTournamentIndexInSeries(int tournamentIndexInSeries) { this.tournamentIndexInSeries = tournamentIndexInSeries; }

    public int getPhaseNumber() { return phaseNumber; }
    public void setPhaseNumber(int phaseNumber) { this.phaseNumber = phaseNumber; }

    public int getMaxTeamsPerGroup() { return maxTeamsPerGroup; }
    public void setMaxTeamsPerGroup(int maxTeamsPerGroup) { this.maxTeamsPerGroup = maxTeamsPerGroup; }

    public int getAdvancingSeatsCount() { return advancingSeatsCount; }
    public void setAdvancingSeatsCount(int advancingSeatsCount) { this.advancingSeatsCount = advancingSeatsCount; }

    public String getLinkedQualifierTournamentId() { return linkedQualifierTournamentId; }
    public void setLinkedQualifierTournamentId(String linkedQualifierTournamentId) { this.linkedQualifierTournamentId = linkedQualifierTournamentId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    private Integer seriesRewardPoints;
    private String seriesPointsConfig;

    public Integer getSeriesRewardPoints() { return seriesRewardPoints; }
    public void setSeriesRewardPoints(Integer seriesRewardPoints) { this.seriesRewardPoints = seriesRewardPoints; }

    public String getSeriesPointsConfig() { return seriesPointsConfig; }
    public void setSeriesPointsConfig(String seriesPointsConfig) { this.seriesPointsConfig = seriesPointsConfig; }

    public String getChampionName() { return championName; }
    public void setChampionName(String championName) { this.championName = championName; }
}
