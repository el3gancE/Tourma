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

    private String stage1Status; // PENDING, LOCKED, COMPLETED
    private String groupAssignments;
    private String stage2Teams;
    private String multiStageConfig;
    private String teamsJson;

    public String getStage1Status() { return stage1Status; }
    public void setStage1Status(String stage1Status) { this.stage1Status = stage1Status; }
    /** Returns true if stage 1 is locked (cannot edit scores) */
    public boolean isStage1Locked() { return "LOCKED".equals(stage1Status) || "COMPLETED".equals(stage1Status); }

    public String getGroupAssignments() { return groupAssignments; }
    public void setGroupAssignments(String groupAssignments) { this.groupAssignments = groupAssignments; }

    public String getStage2Teams() { return stage2Teams; }
    public void setStage2Teams(String stage2Teams) { this.stage2Teams = stage2Teams; }

    public String getMultiStageConfig() { return multiStageConfig; }
    public void setMultiStageConfig(String multiStageConfig) { this.multiStageConfig = multiStageConfig; }

    public String getTeamsJson() { return teamsJson; }
    public void setTeamsJson(String teamsJson) { this.teamsJson = teamsJson; }

    private int teamCount;

    public int getTeamCount() { return teamCount; }
    public void setTeamCount(int teamCount) { this.teamCount = teamCount; }

    private int totalMatches;
    private int completedMatches;
    private int stage1TotalMatches;
    private String stage2Format;

    public int getTotalMatches() { return totalMatches; }
    public void setTotalMatches(int value) { totalMatches = Math.max(0, value); }
    public int getCompletedMatches() { return completedMatches; }
    public void setCompletedMatches(int value) { completedMatches = Math.max(0, value); }
    public void setStage1TotalMatches(int value) { stage1TotalMatches = Math.max(0, value); }
    public void setStage2Format(String value) { stage2Format = value; }
    public String getStage2Format() { return stage2Format; }

    public String getFormatSummary() {
        String first = formatName(format);
        return "MULTI_STAGE".equalsIgnoreCase(tournamentType)
                ? first + " ➔ " + formatName(stage2Format) : first;
    }

    private static String formatName(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(java.util.Locale.ROOT);
        switch (normalized) {
            case "DOUBLE_ELIMINATION": return "Double Elimination";
            case "ROUND_ROBIN": return "Round Robin";
            case "GROUP_STAGE": return "Group Stage";
            case "GSL": return "GSL Format";
            case "SWISS":
            case "SWISS_LITE": return "Swiss System";
            default: return "Single Elimination";
        }
    }

    /** Reserve matches for an uncreated final stage rather than assigning equal stage weights. */
    public int getProgressTotalMatches() {
        int planned = totalMatches;
        if (("SWISS".equalsIgnoreCase(format) || "SWISS_LITE".equalsIgnoreCase(format)) && teamCount == 16) {
            // Swiss inserts subsequent rounds on demand; the complete 16-team schedule has 33 games.
            int swissMatches = "MULTI_STAGE".equalsIgnoreCase(tournamentType) ? stage1TotalMatches : totalMatches;
            planned += Math.max(0, 33 - swissMatches);
        }
        if ("MULTI_STAGE".equalsIgnoreCase(tournamentType) && totalMatches == stage1TotalMatches) {
            int n = advancingSeatsCount;
            if (n >= 2 && stage2Format != null) {
                if ("SINGLE_ELIMINATION".equalsIgnoreCase(stage2Format)) planned += n - 1;
                else if ("DOUBLE_ELIMINATION".equalsIgnoreCase(stage2Format)) planned += 2 * n - 2;
                else if ("ROUND_ROBIN".equalsIgnoreCase(stage2Format)) planned += n * (n - 1) / 2;
            }
        }
        return planned;
    }

    /** All played matches contribute equally, regardless of their stage. */
    public int getProgressPercent() {
        if ("COMPLETED".equalsIgnoreCase(status) || "FINISHED".equalsIgnoreCase(status)) return 100;
        double progress = 100 * matchProgress(completedMatches, getProgressTotalMatches());
        // A finished schedule still needs the organizer's final confirmation.
        return Math.min(99, Math.max(0, (int) Math.floor(progress)));
    }

    private static double matchProgress(int completed, int total) {
        return total <= 0 ? 0 : Math.max(0, Math.min(1, (double) completed / total));
    }
}





