IF COL_LENGTH('dbo.league_season_divisions', 'tournament_id') IS NULL
    ALTER TABLE dbo.league_season_divisions ADD tournament_id VARCHAR(50) NULL;
