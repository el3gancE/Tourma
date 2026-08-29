package model;

import java.sql.Timestamp;

/**
 * Team / Participant Entity Model matching database.sql teams table
 */
public class Team {
    private String id;
    private String tournamentId;
    private String partnerParticipantId;
    private String rawName;
    private String normalizedName;
    private int originalSeed;
    private String currentStageId;
    private String status; // ACTIVE, QUALIFIED, ELIMINATED
    private Timestamp createdAt;

    public Team() {
    }

    public Team(String id, String tournamentId, String partnerParticipantId, String rawName,
                String normalizedName, int originalSeed, String currentStageId, String status, Timestamp createdAt) {
        this.id = id;
        this.tournamentId = tournamentId;
        this.partnerParticipantId = partnerParticipantId;
        this.rawName = rawName;
        this.normalizedName = normalizedName;
        this.originalSeed = originalSeed;
        this.currentStageId = currentStageId;
        this.status = status;
        this.createdAt = createdAt;
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTournamentId() { return tournamentId; }
    public void setTournamentId(String tournamentId) { this.tournamentId = tournamentId; }

    public String getPartnerParticipantId() { return partnerParticipantId; }
    public void setPartnerParticipantId(String partnerParticipantId) { this.partnerParticipantId = partnerParticipantId; }

    public String getRawName() { return rawName; }
    public void setRawName(String rawName) { this.rawName = rawName; }

    public String getName() { return (rawName != null && !rawName.trim().isEmpty()) ? rawName : normalizedName; }

    public String getNormalizedName() { return normalizedName; }
    public void setNormalizedName(String normalizedName) { this.normalizedName = normalizedName; }

    public int getOriginalSeed() { return originalSeed; }
    public void setOriginalSeed(int originalSeed) { this.originalSeed = originalSeed; }

    public String getCurrentStageId() { return currentStageId; }
    public void setCurrentStageId(String currentStageId) { this.currentStageId = currentStageId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
}
