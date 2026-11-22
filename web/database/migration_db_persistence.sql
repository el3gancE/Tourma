-- ============================================================================
-- TOURMA - IN-PLACE DATABASE MIGRATION SCRIPT (SAFE & NON-DESTRUCTIVE)
-- File: migration_db_persistence.sql
-- Mục đích: Nâng cấp trực tiếp database hiện tại 'tourma' lên cấu trúc mới nhất
--           mà KHÔNG làm mất dữ liệu (NO DROP TABLE, NO DROP DATABASE).
-- Áp dụng cho: Cơ sở dữ liệu 'tourma' đang chứa dữ liệu thực tế.
-- ============================================================================

USE tourma;
GO

SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
GO

PRINT N'>>> BẮT ĐẦU MIGRATION CHO DATABASE [tourma]...';
GO

-- ----------------------------------------------------------------------------
-- 1. BẢNG QUẢN LÝ PHIÊN BẢN MIGRATION (schema_migrations)
-- ----------------------------------------------------------------------------
IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'schema_migrations')
BEGIN
    CREATE TABLE schema_migrations (
        version VARCHAR(50) PRIMARY KEY,
        description NVARCHAR(255) NOT NULL,
        applied_at DATETIME DEFAULT GETDATE()
    );
    PRINT N'  + Đã tạo bảng tracking [schema_migrations].';
END
GO

IF NOT EXISTS (SELECT 1 FROM schema_migrations WHERE version = '001')
BEGIN
    INSERT INTO schema_migrations (version, description)
    VALUES ('001', N'Khởi tạo 13 bảng cốt lõi và tracking schema_migrations');
    PRINT N'  + Ghi nhận migration 001.';
END
GO

-- ----------------------------------------------------------------------------
-- 2. BỔ SUNG CÁC CỘT MỚI VÀO BẢNG TOURNAMENTS (NẾU CHƯA CÓ)
-- ----------------------------------------------------------------------------
IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID(N'tournaments') AND name = 'champion_name')
BEGIN
    ALTER TABLE tournaments ADD champion_name NVARCHAR(255) NULL;
    PRINT N'  + Đã thêm cột [champion_name] vào bảng tournaments.';
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID(N'tournaments') AND name = 'teams_json')
BEGIN
    ALTER TABLE tournaments ADD teams_json NVARCHAR(MAX) NULL;
    PRINT N'  + Đã thêm cột [teams_json] vào bảng tournaments.';
END
GO

IF COL_LENGTH('tournaments', 'top_seed_count') IS NULL
BEGIN
    ALTER TABLE tournaments ADD top_seed_count INT NULL;
    PRINT N'  + Đã thêm cột [top_seed_count] vào bảng tournaments.';
END
GO

IF COL_LENGTH('tournaments', 'classic_draw') IS NULL
BEGIN
    ALTER TABLE tournaments ADD classic_draw BIT NOT NULL DEFAULT 0;
    PRINT N'  + Đã thêm cột [classic_draw] vào bảng tournaments.';
END
GO

IF NOT EXISTS (SELECT 1 FROM schema_migrations WHERE version = '002')
BEGIN
    INSERT INTO schema_migrations (version, description)
    VALUES ('002', N'Bổ sung các cột champion_name, teams_json, top_seed_count, classic_draw vào tournaments');
    PRINT N'  + Ghi nhận migration 002.';
END
GO

-- ----------------------------------------------------------------------------
-- 3. CÁC INDEX HIỆU NĂNG BAO PHỦ (COVERING INDEXES) CHO CÁC TRUY VẤN NẶNG
-- ----------------------------------------------------------------------------
-- 3.1. Covering index cho matches: Đếm số trận, hoàn thành, trạng thái, kết quả
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'idx_matches_tourney_perf' AND object_id = OBJECT_ID('matches'))
BEGIN
    CREATE NONCLUSTERED INDEX idx_matches_tourney_perf 
    ON matches(tournament_id, is_bye) 
    INCLUDE (status, winner_id, loser_id, score1, score2, penalty1, penalty2, team1_id, team2_id, stage_id)
    WITH (MAXDOP = 1);
    PRINT N'  + Đã tạo index [idx_matches_tourney_perf] trên matches.';
END
GO

-- 3.2. Index cho teams: Phục vụ sắp xếp seed và nạp nhanh danh sách đội theo giải
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'idx_teams_tourney_seed' AND object_id = OBJECT_ID('teams'))
BEGIN
    CREATE NONCLUSTERED INDEX idx_teams_tourney_seed 
    ON teams(tournament_id, original_seed)
    WITH (MAXDOP = 1);
    PRINT N'  + Đã tạo index [idx_teams_tourney_seed] trên teams.';
END
GO

-- 3.3. Index cho group_teams: Tối ưu tính toán thứ hạng vòng bảng
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'idx_group_teams_perf' AND object_id = OBJECT_ID('group_teams'))
BEGIN
    CREATE NONCLUSTERED INDEX idx_group_teams_perf 
    ON group_teams(group_id) 
    INCLUDE (team_id, rank_in_group)
    WITH (MAXDOP = 1);
    PRINT N'  + Đã tạo index [idx_group_teams_perf] trên group_teams.';
END
GO

IF NOT EXISTS (SELECT 1 FROM schema_migrations WHERE version = '003')
BEGIN
    INSERT INTO schema_migrations (version, description)
    VALUES ('003', N'Tạo các chỉ mục bao phủ (Covering Indexes) cho matches, teams, group_teams, tournaments');
    PRINT N'  + Ghi nhận migration 003.';
END
GO

-- ----------------------------------------------------------------------------
-- 4. CÁC INDEX TRÊN TOÀN BỘ KHÓA NGOẠI (FOREIGN KEYS) - CHỐNG TIMEOUT KHI XÓA
-- ----------------------------------------------------------------------------
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'idx_series_history_tourney' AND object_id = OBJECT_ID('series_tournament_history'))
    CREATE NONCLUSTERED INDEX idx_series_history_tourney ON series_tournament_history(tournament_id) WITH (MAXDOP = 1);
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'idx_tournaments_linked' AND object_id = OBJECT_ID('tournaments'))
    CREATE NONCLUSTERED INDEX idx_tournaments_linked ON tournaments(linked_qualifier_tournament_id) WHERE linked_qualifier_tournament_id IS NOT NULL WITH (MAXDOP = 1);
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'idx_stage_participants_tourney' AND object_id = OBJECT_ID('stage_participants'))
    CREATE NONCLUSTERED INDEX idx_stage_participants_tourney ON stage_participants(tournament_id) WITH (MAXDOP = 1);
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'idx_matches_team2' AND object_id = OBJECT_ID('matches'))
    CREATE NONCLUSTERED INDEX idx_matches_team2 ON matches(team2_id) WHERE team2_id IS NOT NULL WITH (MAXDOP = 1);
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'idx_matches_winner' AND object_id = OBJECT_ID('matches'))
    CREATE NONCLUSTERED INDEX idx_matches_winner ON matches(winner_id) WHERE winner_id IS NOT NULL WITH (MAXDOP = 1);
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'idx_matches_loser' AND object_id = OBJECT_ID('matches'))
    CREATE NONCLUSTERED INDEX idx_matches_loser ON matches(loser_id) WHERE loser_id IS NOT NULL WITH (MAXDOP = 1);
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'idx_matches_group' AND object_id = OBJECT_ID('matches'))
    CREATE NONCLUSTERED INDEX idx_matches_group ON matches(group_id) WHERE group_id IS NOT NULL WITH (MAXDOP = 1);
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'idx_matches_stage' AND object_id = OBJECT_ID('matches'))
    CREATE NONCLUSTERED INDEX idx_matches_stage ON matches(stage_id) WHERE stage_id IS NOT NULL WITH (MAXDOP = 1);
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'idx_teams_stage' AND object_id = OBJECT_ID('teams'))
    CREATE NONCLUSTERED INDEX idx_teams_stage ON teams(current_stage_id) WHERE current_stage_id IS NOT NULL WITH (MAXDOP = 1);
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'idx_matches_next_match' AND object_id = OBJECT_ID('matches'))
    CREATE NONCLUSTERED INDEX idx_matches_next_match ON matches(next_match_id) WHERE next_match_id IS NOT NULL WITH (MAXDOP = 1);
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'idx_matches_loser_next_match' AND object_id = OBJECT_ID('matches'))
    CREATE NONCLUSTERED INDEX idx_matches_loser_next_match ON matches(loser_next_match_id) WHERE loser_next_match_id IS NOT NULL WITH (MAXDOP = 1);
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'idx_group_teams_team' AND object_id = OBJECT_ID('group_teams'))
    CREATE NONCLUSTERED INDEX idx_group_teams_team ON group_teams(team_id) WITH (MAXDOP = 1);
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'idx_partner_participants_series' AND object_id = OBJECT_ID('partner_participants'))
    CREATE NONCLUSTERED INDEX idx_partner_participants_series ON partner_participants(series_id, created_at) WITH (MAXDOP = 1);
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'idx_teams_partner_participant' AND object_id = OBJECT_ID('teams'))
    CREATE NONCLUSTERED INDEX idx_teams_partner_participant ON teams(partner_participant_id) WHERE partner_participant_id IS NOT NULL WITH (MAXDOP = 1);
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'idx_series_standings_partner' AND object_id = OBJECT_ID('series_standings'))
    CREATE NONCLUSTERED INDEX idx_series_standings_partner ON series_standings(partner_participant_id) WITH (MAXDOP = 1);
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'idx_stage_participants_team' AND object_id = OBJECT_ID('stage_participants'))
    CREATE NONCLUSTERED INDEX idx_stage_participants_team ON stage_participants(team_id) WITH (MAXDOP = 1);
GO

IF NOT EXISTS (SELECT 1 FROM schema_migrations WHERE version = '004')
BEGIN
    INSERT INTO schema_migrations (version, description)
    VALUES ('004', N'Tạo chỉ mục trên toàn bộ các cột khóa ngoại (Foreign Keys) để tối ưu Cascade Delete');
    PRINT N'  + Ghi nhận migration 004.';
END
GO

-- ----------------------------------------------------------------------------
-- 5. LEAGUE SYSTEM: BẢNG SEASONS, SERIES_DIVISIONS VÀ CỘT MỚI BẢNG TOURNAMENTS
-- ----------------------------------------------------------------------------
IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'seasons')
BEGIN
    CREATE TABLE seasons (
        id VARCHAR(50) PRIMARY KEY,
        series_id VARCHAR(50) NOT NULL,
        season_number INT NOT NULL DEFAULT 1,
        name NVARCHAR(255) NOT NULL,
        status VARCHAR(20) DEFAULT 'ACTIVE' CHECK (status IN ('DRAFT', 'ACTIVE', 'COMPLETED')),
        has_special_cup BIT NOT NULL DEFAULT 0,
        special_cup_tournament_id VARCHAR(50) NULL,
        created_at DATETIME DEFAULT GETDATE(),
        completed_at DATETIME NULL,
        FOREIGN KEY (series_id) REFERENCES series(id) ON DELETE CASCADE
    );
    PRINT N'  + Đã tạo bảng [seasons].';
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'series_divisions')
BEGIN
    CREATE TABLE series_divisions (
        id VARCHAR(50) PRIMARY KEY,
        series_id VARCHAR(50) NOT NULL,
        division_level INT NOT NULL,
        name NVARCHAR(100) NOT NULL,
        team_quota INT NOT NULL DEFAULT 10,
        promotion_slots INT NOT NULL DEFAULT 0,
        relegation_slots INT NOT NULL DEFAULT 0,
        created_at DATETIME DEFAULT GETDATE(),
        FOREIGN KEY (series_id) REFERENCES series(id) ON DELETE CASCADE
    );
    PRINT N'  + Đã tạo bảng [series_divisions].';
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID(N'tournaments') AND name = 'season_id')
BEGIN
    ALTER TABLE tournaments ADD season_id VARCHAR(50) NULL;
    PRINT N'  + Đã thêm cột [season_id] vào tournaments.';
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID(N'tournaments') AND name = 'is_special_cup')
BEGIN
    ALTER TABLE tournaments ADD is_special_cup BIT NOT NULL DEFAULT 0;
    PRINT N'  + Đã thêm cột [is_special_cup] vào tournaments.';
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID(N'tournaments') AND name = 'division_id')
BEGIN
    ALTER TABLE tournaments ADD division_id VARCHAR(50) NULL;
    PRINT N'  + Đã thêm cột [division_id] vào tournaments.';
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = 'FK_tournaments_seasons')
BEGIN
    ALTER TABLE tournaments ADD CONSTRAINT FK_tournaments_seasons FOREIGN KEY (season_id) REFERENCES seasons(id);
    PRINT N'  + Đã thêm foreign key [FK_tournaments_seasons].';
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = 'FK_tournaments_divisions')
BEGIN
    ALTER TABLE tournaments ADD CONSTRAINT FK_tournaments_divisions FOREIGN KEY (division_id) REFERENCES series_divisions(id);
    PRINT N'  + Đã thêm foreign key [FK_tournaments_divisions].';
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'idx_seasons_series' AND object_id = OBJECT_ID('seasons'))
BEGIN
    CREATE NONCLUSTERED INDEX idx_seasons_series ON seasons(series_id, season_number);
    PRINT N'  + Đã tạo index [idx_seasons_series].';
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'idx_divisions_series' AND object_id = OBJECT_ID('series_divisions'))
BEGIN
    CREATE NONCLUSTERED INDEX idx_divisions_series ON series_divisions(series_id, division_level);
    PRINT N'  + Đã tạo index [idx_divisions_series].';
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'idx_tournaments_season' AND object_id = OBJECT_ID('tournaments'))
BEGIN
    CREATE NONCLUSTERED INDEX idx_tournaments_season ON tournaments(season_id) WHERE season_id IS NOT NULL;
    PRINT N'  + Đã tạo index [idx_tournaments_season].';
END
GO

IF NOT EXISTS (SELECT 1 FROM schema_migrations WHERE version = '005')
BEGIN
    INSERT INTO schema_migrations (version, description)
    VALUES ('005', N'Thêm bảng seasons, series_divisions và các cột season_id, is_special_cup, division_id cho League System');
    PRINT N'  + Ghi nhận migration 005.';
END
GO

PRINT N'===================================================================';
PRINT N'>>> MIGRATION HOÀN TẤT! DATABASE [tourma] ĐÃ ĐỒNG BỘ TOÀN DIỆN, BẢO TOÀN DỮ LIỆU CŨ.';
PRINT N'===================================================================';
GO

