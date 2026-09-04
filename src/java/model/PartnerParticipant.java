package model;

import java.sql.Timestamp;

/**
 * Entity model for partner_participants table
 */
public class PartnerParticipant {
    private String id;
    private String seriesId;
    private String name;
    private String groupName;
    private Timestamp createdAt;

    public PartnerParticipant() {
    }

    public PartnerParticipant(String id, String seriesId, String name, String groupName, Timestamp createdAt) {
        this.id = id;
        this.seriesId = seriesId;
        this.name = name;
        this.groupName = groupName;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getSeriesId() { return seriesId; }
    public void setSeriesId(String seriesId) { this.seriesId = seriesId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getGroupName() { return groupName; }
    public void setGroupName(String groupName) { this.groupName = groupName; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
}
