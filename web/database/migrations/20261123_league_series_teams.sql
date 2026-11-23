SET XACT_ABORT ON;
BEGIN TRANSACTION;

IF OBJECT_ID(N'dbo.league_series_teams', N'U') IS NOT NULL
    AND OBJECT_ID(N'dbo.league_draft_teams', N'U') IS NOT NULL
    THROW 50000, 'Both League roster tables exist. Resolve the duplicate tables before migrating.', 1;

IF OBJECT_ID(N'dbo.league_series_teams', N'U') IS NULL
    AND OBJECT_ID(N'dbo.league_draft_teams', N'U') IS NOT NULL
BEGIN
    EXEC sys.sp_rename N'dbo.league_draft_teams', N'league_series_teams';
    EXEC sys.sp_rename N'dbo.PK_league_draft_teams', N'PK_league_series_teams', N'OBJECT';
    EXEC sys.sp_rename N'dbo.FK_league_draft_teams_series', N'FK_league_series_teams_series', N'OBJECT';
END;

IF OBJECT_ID(N'dbo.league_series_teams', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.league_series_teams (
        series_id VARCHAR(50) NOT NULL,
        team_key NVARCHAR(255) COLLATE Latin1_General_100_BIN2 NOT NULL,
        team_name NVARCHAR(255) NOT NULL,
        registered_at DATETIME2(3) NULL,
        CONSTRAINT PK_league_series_teams PRIMARY KEY (series_id, team_key),
        CONSTRAINT FK_league_series_teams_series FOREIGN KEY (series_id)
            REFERENCES dbo.series(id) ON DELETE CASCADE
    );
END;

COMMIT TRANSACTION;

