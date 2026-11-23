SET XACT_ABORT ON;
BEGIN TRANSACTION;

IF OBJECT_ID(N'dbo.seasons', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.seasons (
        id VARCHAR(50) PRIMARY KEY,
        series_id VARCHAR(50) NOT NULL REFERENCES dbo.series(id) ON DELETE CASCADE,
        season_number INT NOT NULL,
        name NVARCHAR(255) NOT NULL,
        status VARCHAR(20) CHECK (status IN ('DRAFT', 'ACTIVE', 'COMPLETED')),
        has_special_cup BIT NOT NULL DEFAULT 0,
        special_cup_tournament_id VARCHAR(50) NULL,
        created_at DATETIME DEFAULT GETDATE(),
        completed_at DATETIME NULL
    );
END;

IF OBJECT_ID(N'dbo.league_season_divisions', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.league_season_divisions (
        season_id VARCHAR(50) NOT NULL REFERENCES dbo.seasons(id) ON DELETE CASCADE,
        division_level INT NOT NULL CHECK (division_level > 0),
        round_robin_legs INT NOT NULL CHECK (round_robin_legs > 0),
        exchange_slots_below INT NOT NULL CHECK (exchange_slots_below >= 0),
        PRIMARY KEY (season_id, division_level)
    );
END;

IF OBJECT_ID(N'dbo.league_season_entries', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.league_season_entries (
        season_id VARCHAR(50) NOT NULL,
        team_key NVARCHAR(255) COLLATE Latin1_General_100_BIN2 NOT NULL,
        team_name NVARCHAR(255) NOT NULL,
        division_level INT NOT NULL,
        PRIMARY KEY (season_id, team_key),
        FOREIGN KEY (season_id, division_level)
            REFERENCES dbo.league_season_divisions(season_id, division_level) ON DELETE CASCADE
    );
END;

COMMIT TRANSACTION;
