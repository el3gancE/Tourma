IF OBJECT_ID(N'dbo.league_season_withdrawals', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.league_season_withdrawals (
        season_id VARCHAR(50) NOT NULL REFERENCES dbo.seasons(id) ON DELETE CASCADE,
        team_key NVARCHAR(255) COLLATE Latin1_General_100_BIN2 NOT NULL,
        PRIMARY KEY (season_id, team_key)
    );
END;
