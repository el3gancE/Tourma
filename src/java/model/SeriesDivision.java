package model;

import java.sql.Timestamp;

public class SeriesDivision {
    private String id;
    private String seriesId;
    private int divisionLevel;
    private String name;
    private int teamQuota;
    private int promotionSlots;
    private int relegationSlots;
    private Timestamp createdAt;

    public SeriesDivision() {
    }

    public SeriesDivision(String id, String seriesId, int divisionLevel, String name,
                          int teamQuota, int promotionSlots, int relegationSlots, Timestamp createdAt) {
        this.id = id;
        this.seriesId = seriesId;
        this.divisionLevel = divisionLevel;
        this.name = name;
        this.teamQuota = teamQuota;
        this.promotionSlots = promotionSlots;
        this.relegationSlots = relegationSlots;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getSeriesId() { return seriesId; }
    public void setSeriesId(String seriesId) { this.seriesId = seriesId; }

    public int getDivisionLevel() { return divisionLevel; }
    public void setDivisionLevel(int divisionLevel) { this.divisionLevel = divisionLevel; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public int getTeamQuota() { return teamQuota; }
    public void setTeamQuota(int teamQuota) { this.teamQuota = teamQuota; }

    public int getPromotionSlots() { return promotionSlots; }
    public void setPromotionSlots(int promotionSlots) { this.promotionSlots = promotionSlots; }

    public int getRelegationSlots() { return relegationSlots; }
    public void setRelegationSlots(int relegationSlots) { this.relegationSlots = relegationSlots; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
}
