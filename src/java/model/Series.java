package model;

import java.sql.Timestamp;

/**
 * Series Entity Model matching database.sql series table
 */
public class Series {
    private String id;
    private String name;
    private String rankingModel; // ROLLING_WINDOW, FIFA_ELO
    private int phaseSize;
    private int currentPhase;
    private int initialPoints;
    private double initialElo;
    private String status; // ACTIVE, COMPLETED
    private Timestamp createdAt;

    public Series() {
    }

    public Series(String id, String name, String rankingModel, int phaseSize, int currentPhase,
                  int initialPoints, double initialElo, String status, Timestamp createdAt) {
        this.id = id;
        this.name = name;
        this.rankingModel = rankingModel;
        this.phaseSize = phaseSize;
        this.currentPhase = currentPhase;
        this.initialPoints = initialPoints;
        this.initialElo = initialElo;
        this.status = status;
        this.createdAt = createdAt;
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getRankingModel() { return rankingModel; }
    public void setRankingModel(String rankingModel) { this.rankingModel = rankingModel; }

    public int getPhaseSize() { return phaseSize; }
    public void setPhaseSize(int phaseSize) { this.phaseSize = phaseSize; }

    public int getCurrentPhase() { return currentPhase; }
    public void setCurrentPhase(int currentPhase) { this.currentPhase = currentPhase; }

    public int getInitialPoints() { return initialPoints; }
    public void setInitialPoints(int initialPoints) { this.initialPoints = initialPoints; }

    public double getInitialElo() { return initialElo; }
    public void setInitialElo(double initialElo) { this.initialElo = initialElo; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
}
