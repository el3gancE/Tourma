package model;

import java.io.Serializable;

/**
 * Entity Model representing a match in Tourma system.
 */
public class Match implements Serializable {
    private int id;
    private int tournamentId;
    private int roundNumber;
    private int matchNumber;
    
    private Integer team1Id;
    private String team1Name;
    private Integer team1Seed;
    private Integer team1Score;
    
    private Integer team2Id;
    private String team2Name;
    private Integer team2Seed;
    private Integer team2Score;
    
    private Integer winnerTeamId;
    private Integer nextMatchId;
    private int nextMatchSlot; // 1 = Team 1, 2 = Team 2 in next match
    
    private Integer dropToMatchId; // For Double Elimination (where loser goes)
    private int dropToMatchSlot;
    
    private String status; // "SCHEDULED" (PENDING) or "COMPLETED" (DONE)
    private String bracketType; // "UPPER", "LOWER", "GRAND_FINAL"
    private boolean isLosersBracket;

    public Match() {
        this.status = "SCHEDULED";
        this.nextMatchSlot = 1;
        this.bracketType = "UPPER";
        this.isLosersBracket = false;
    }

    public Match(int id, int tournamentId, int roundNumber, int matchNumber, 
                 Integer team1Id, String team1Name, Integer team1Seed, Integer team1Score, 
                 Integer team2Id, String team2Name, Integer team2Seed, Integer team2Score, 
                 Integer winnerTeamId, Integer nextMatchId, int nextMatchSlot, String status) {
        this.id = id;
        this.tournamentId = tournamentId;
        this.roundNumber = roundNumber;
        this.matchNumber = matchNumber;
        this.team1Id = team1Id;
        this.team1Name = team1Name;
        this.team1Seed = team1Seed;
        this.team1Score = team1Score;
        this.team2Id = team2Id;
        this.team2Name = team2Name;
        this.team2Seed = team2Seed;
        this.team2Score = team2Score;
        this.winnerTeamId = winnerTeamId;
        this.nextMatchId = nextMatchId;
        this.nextMatchSlot = nextMatchSlot;
        this.status = status;
        this.bracketType = "UPPER";
        this.isLosersBracket = false;
    }

    // Getters and Setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getTournamentId() { return tournamentId; }
    public void setTournamentId(int tournamentId) { this.tournamentId = tournamentId; }

    public int getRoundNumber() { return roundNumber; }
    public void setRoundNumber(int roundNumber) { this.roundNumber = roundNumber; }

    public int getMatchNumber() { return matchNumber; }
    public void setMatchNumber(int matchNumber) { this.matchNumber = matchNumber; }

    public Integer getTeam1Id() { return team1Id; }
    public void setTeam1Id(Integer team1Id) { this.team1Id = team1Id; }

    public String getTeam1Name() { return team1Name; }
    public void setTeam1Name(String team1Name) { this.team1Name = team1Name; }

    public Integer getTeam1Seed() { return team1Seed; }
    public void setTeam1Seed(Integer team1Seed) { this.team1Seed = team1Seed; }

    public Integer getTeam1Score() { return team1Score; }
    public void setTeam1Score(Integer team1Score) { this.team1Score = team1Score; }

    public Integer getTeam2Id() { return team2Id; }
    public void setTeam2Id(Integer team2Id) { this.team2Id = team2Id; }

    public String getTeam2Name() { return team2Name; }
    public void setTeam2Name(String team2Name) { this.team2Name = team2Name; }

    public Integer getTeam2Seed() { return team2Seed; }
    public void setTeam2Seed(Integer team2Seed) { this.team2Seed = team2Seed; }

    public Integer getTeam2Score() { return team2Score; }
    public void setTeam2Score(Integer team2Score) { this.team2Score = team2Score; }

    public Integer getWinnerTeamId() { return winnerTeamId; }
    public void setWinnerTeamId(Integer winnerTeamId) { this.winnerTeamId = winnerTeamId; }

    public Integer getNextMatchId() { return nextMatchId; }
    public void setNextMatchId(Integer nextMatchId) { this.nextMatchId = nextMatchId; }

    public int getNextMatchSlot() { return nextMatchSlot; }
    public void setNextMatchSlot(int nextMatchSlot) { this.nextMatchSlot = nextMatchSlot; }

    public Integer getDropToMatchId() { return dropToMatchId; }
    public void setDropToMatchId(Integer dropToMatchId) { this.dropToMatchId = dropToMatchId; }

    public int getDropToMatchSlot() { return dropToMatchSlot; }
    public void setDropToMatchSlot(int dropToMatchSlot) { this.dropToMatchSlot = dropToMatchSlot; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getBracketType() { return bracketType != null ? bracketType : (isLosersBracket ? "LOWER" : "UPPER"); }
    public void setBracketType(String bracketType) { 
        this.bracketType = bracketType; 
        this.isLosersBracket = "LOWER".equalsIgnoreCase(bracketType);
    }

    public boolean isLosersBracket() { return isLosersBracket || "LOWER".equalsIgnoreCase(bracketType); }
    public void setLosersBracket(boolean losersBracket) { 
        this.isLosersBracket = losersBracket; 
        if (losersBracket) this.bracketType = "LOWER";
    }
}
