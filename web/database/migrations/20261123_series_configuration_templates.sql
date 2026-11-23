SET XACT_ABORT ON;
BEGIN TRANSACTION;

IF OBJECT_ID(N'dbo.series_configuration_templates', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.series_configuration_templates (
        id VARCHAR(50) NOT NULL PRIMARY KEY,
        series_id VARCHAR(50) NOT NULL REFERENCES dbo.series(id) ON DELETE CASCADE,
        name NVARCHAR(120) NOT NULL,
        tournament_type VARCHAR(20) NOT NULL,
        stage1_format VARCHAR(30) NOT NULL,
        stage2_format VARCHAR(30) NULL,
        team_count INT NOT NULL CHECK (team_count >= 2),
        advancing_seats INT NOT NULL,
        format_config NVARCHAR(MAX) NOT NULL CHECK (ISJSON(format_config) = 1),
        tier_name VARCHAR(10) NULL,
        top_seed_count INT NULL,
        classic_draw BIT NOT NULL,
        reward_points INT NULL,
        points_config NVARCHAR(MAX) NULL,
        CONSTRAINT UQ_series_configuration_template_name UNIQUE (series_id, name)
    );
    CREATE TABLE dbo.series_configuration_template_points (
        template_id VARCHAR(50) NOT NULL REFERENCES dbo.series_configuration_templates(id) ON DELETE CASCADE,
        rank_position INT NOT NULL,
        rank_label NVARCHAR(100) NULL,
        points_awarded INT NOT NULL,
        PRIMARY KEY (template_id, rank_position)
    );
END;

IF COL_LENGTH('dbo.tournaments', 'setup_template_name') IS NULL
    ALTER TABLE dbo.tournaments ADD setup_template_name NVARCHAR(120) NULL;
IF COL_LENGTH('dbo.tournaments', 'setup_template_team_count') IS NULL
    ALTER TABLE dbo.tournaments ADD setup_template_team_count INT NULL;

COMMIT TRANSACTION;
