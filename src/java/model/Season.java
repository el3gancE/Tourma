package model;

import java.sql.Timestamp;

public class Season {
    private String id;
    private String seriesId;
    private int seasonNumber;
    private String name;
    private String status; // DRAFT, ACTIVE, COMPLETED
    private boolean hasSpecialCup;
    private String specialCupTournamentId;
    private Timestamp createdAt;
    private Timestamp completedAt;

    public Season() {
    }

    public Season(String id, String seriesId, int seasonNumber, String name, String status,
                  boolean hasSpecialCup, String specialCupTournamentId, Timestamp createdAt, Timestamp completedAt) {
        this.id = id;
        this.seriesId = seriesId;
        this.seasonNumber = seasonNumber;
        this.name = name;
        this.status = status;
        this.hasSpecialCup = hasSpecialCup;
        this.specialCupTournamentId = specialCupTournamentId;
        this.createdAt = createdAt;
        this.completedAt = completedAt;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getSeriesId() { return seriesId; }
    public void setSeriesId(String seriesId) { this.seriesId = seriesId; }

    public int getSeasonNumber() { return seasonNumber; }
    public void setSeasonNumber(int seasonNumber) { this.seasonNumber = seasonNumber; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public boolean isHasSpecialCup() { return hasSpecialCup; }
    public void setHasSpecialCup(boolean hasSpecialCup) { this.hasSpecialCup = hasSpecialCup; }

    public String getSpecialCupTournamentId() { return specialCupTournamentId; }
    public void setSpecialCupTournamentId(String specialCupTournamentId) { this.specialCupTournamentId = specialCupTournamentId; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public Timestamp getCompletedAt() { return completedAt; }
    public void setCompletedAt(Timestamp completedAt) { this.completedAt = completedAt; }
}
