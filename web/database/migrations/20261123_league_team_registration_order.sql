IF COL_LENGTH('dbo.league_series_teams', 'registration_order') IS NULL
    ALTER TABLE dbo.league_series_teams ADD registration_order BIGINT IDENTITY(1,1) NOT NULL;
