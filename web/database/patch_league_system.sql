-- ============================================================================
-- TOURMA - LEAGUE SYSTEM SCHEMA PATCH SCRIPT (MS SQL SERVER)
-- Chạy script này trên SQL Server nếu đã tạo database tourma_db từ trước
-- ============================================================================

USE tourma_db;
GO

-- 1. CẬP NHẬT BẢNG SERIES
-- Bổ sung kiểu LEAGUE_SYSTEM vào ranking_model
ALTER TABLE series DROP CONSTRAINT IF EXISTS CK_series_ranking_model;
ALTER TABLE series ADD CONSTRAINT CK_series_ranking_model CHECK (ranking_model IN ('ROLLING_WINDOW', 'FIFA_ELO', 'LEAGUE_SYSTEM'));

-- Bổ sung các cột cấu hình League System (Suất thăng / xuống hạng cứng)
IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('series') AND name = 'total_divisions')
    ALTER TABLE series ADD total_divisions INT DEFAULT 2;

IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('series') AND name = 'promotion_slots')
    ALTER TABLE series ADD promotion_slots INT DEFAULT 2;

IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('series') AND name = 'relegation_slots')
    ALTER TABLE series ADD relegation_slots INT DEFAULT 2;
GO

-- 2. CẬP NHẬT BẢNG TOURNAMENTS
-- Bổ sung thông tin Hạng đấu và Mùa giải cho League System
IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('tournaments') AND name = 'division_level')
    ALTER TABLE tournaments ADD division_level INT DEFAULT 1;

IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('tournaments') AND name = 'season_number')
    ALTER TABLE tournaments ADD season_number INT DEFAULT 1;

IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('tournaments') AND name = 'division_name')
    ALTER TABLE tournaments ADD division_name NVARCHAR(100) NULL;
GO

-- 3. CẬP NHẬT BẢNG SERIES_STANDINGS
-- Bổ sung thông tin Hạng đấu, Mùa giải, các chỉ số thi đấu và Trạng thái Thăng/Xuống hạng
IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('series_standings') AND name = 'division_level')
    ALTER TABLE series_standings ADD division_level INT DEFAULT 1;

IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('series_standings') AND name = 'season_number')
    ALTER TABLE series_standings ADD season_number INT DEFAULT 1;

IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('series_standings') AND name = 'matches_played')
    ALTER TABLE series_standings ADD matches_played INT DEFAULT 0;

IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('series_standings') AND name = 'wins')
    ALTER TABLE series_standings ADD wins INT DEFAULT 0;

IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('series_standings') AND name = 'draws')
    ALTER TABLE series_standings ADD draws INT DEFAULT 0;

IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('series_standings') AND name = 'losses')
    ALTER TABLE series_standings ADD losses INT DEFAULT 0;

IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('series_standings') AND name = 'goals_for')
    ALTER TABLE series_standings ADD goals_for INT DEFAULT 0;

IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('series_standings') AND name = 'goals_against')
    ALTER TABLE series_standings ADD goals_against INT DEFAULT 0;

IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('series_standings') AND name = 'goal_diff')
    ALTER TABLE series_standings ADD goal_diff INT DEFAULT 0;

IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('series_standings') AND name = 'points')
    ALTER TABLE series_standings ADD points INT DEFAULT 0;

IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('series_standings') AND name = 'promotion_status')
BEGIN
    ALTER TABLE series_standings ADD promotion_status VARCHAR(30) DEFAULT 'NONE';
    ALTER TABLE series_standings ADD CONSTRAINT CK_standings_promotion_status 
        CHECK (promotion_status IN ('CHAMPION', 'PROMOTED', 'RETAINED', 'RELEGATED', 'NONE'));
END
GO
