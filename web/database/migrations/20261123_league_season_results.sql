IF OBJECT_ID(N'dbo.league_season_results', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.league_season_results (
        season_id VARCHAR(50) NOT NULL,
        team_key NVARCHAR(255) COLLATE Latin1_General_100_BIN2 NOT NULL,
        final_rank INT NOT NULL,
        points INT NOT NULL,
        played INT NOT NULL,
        wins INT NOT NULL,
        draws INT NOT NULL,
        losses INT NOT NULL,
        goals_for INT NOT NULL,
        goals_against INT NOT NULL,
        next_division_level INT NOT NULL,
        PRIMARY KEY (season_id, team_key),
        FOREIGN KEY (season_id, team_key) REFERENCES dbo.league_season_entries(season_id, team_key) ON DELETE CASCADE
    );
END;
