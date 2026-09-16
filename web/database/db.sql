-- ============================================================================
-- TOURMA - COMPLETE DATABASE SCHEMA (MICROSOFT SQL SERVER / T-SQL)
-- File: db.sql
-- Mô tả: Cơ sở dữ liệu chuẩn hóa cho hệ thống quản lý giải đấu Tourma
-- Hỗ trợ: Single/Multi-Stage, Single/Double Elimination, Round Robin, Swiss,
--         Group Stage, Series Rolling Window, FIFA Elo Ranking & League System.
-- ============================================================================

USE master;
GO

IF EXISTS (SELECT * FROM sys.databases WHERE name = 'tourma')
BEGIN
    ALTER DATABASE tourma SET SINGLE_USER WITH ROLLBACK IMMEDIATE;
    DROP DATABASE tourma;
END
GO

CREATE DATABASE tourma;
GO

USE tourma;
GO

-- ============================================================================
-- 1. BẢNG SERIES (MÙA GIẢI / CHUỖI GIẢI ĐẤU)
-- ============================================================================
CREATE TABLE series (
    id VARCHAR(50) PRIMARY KEY,
    name NVARCHAR(255) NOT NULL,
    ranking_model VARCHAR(50) NOT NULL DEFAULT 'ROLLING_WINDOW' 
        CHECK (ranking_model IN ('ROLLING_WINDOW', 'FIFA_ELO', 'LEAGUE_SYSTEM')),
    phase_size INT DEFAULT 10,              -- Độ rộng cửa sổ trượt W (Ví dụ: W = 10 giải; 1-10 là Phase 1, 11-20 là Phase 2)
    current_phase INT DEFAULT 1,            -- Phase hiện tại của Series
    initial_points INT DEFAULT 0,           -- Điểm khởi đầu cho Rolling Window (0 điểm)
    initial_elo FLOAT DEFAULT 1000.0,       -- Điểm Elo khởi điểm cho FIFA Elo (1000 điểm)
    total_divisions INT DEFAULT 2,          -- Số Hạng đấu cho League System (Hạng 1, Hạng 2...)
    promotion_slots INT DEFAULT 2,          -- Số suất Thăng hạng cứng cho League System
    relegation_slots INT DEFAULT 2,         -- Số suất Xuống hạng cứng cho League System
    status VARCHAR(20) DEFAULT 'ACTIVE' 
        CHECK (status IN ('ACTIVE', 'COMPLETED')),
    created_at DATETIME DEFAULT GETDATE()
);
GO

-- ============================================================================
-- 2. BẢNG PARTNER PARTICIPANTS (ĐỐI TÁC / THÀNH VIÊN ĐỒNG HÀNH)
-- ============================================================================
CREATE TABLE partner_participants (
    id VARCHAR(50) PRIMARY KEY,
    series_id VARCHAR(50) NOT NULL,
    name NVARCHAR(255) NOT NULL,
    group_name NVARCHAR(100) DEFAULT 'General', -- Phân nhóm riêng trong Series (ví dụ: Nhóm A, Nhóm B, Pro, Amateur)
    created_at DATETIME DEFAULT GETDATE(),
    FOREIGN KEY (series_id) REFERENCES series(id) ON DELETE CASCADE
);
GO

-- ============================================================================
-- 3. BẢNG SERIES_POINT_RULES (QUY TẮC ĐIỂM THƯỞNG CHUẨN THEO TIER & THỨ HẠNG)
-- Chuẩn hóa từ cấu hình JSON, định nghĩa điểm theo Tier (S, A, B, C, D) và thứ hạng
-- ============================================================================
CREATE TABLE series_point_rules (
    id VARCHAR(50) PRIMARY KEY,
    series_id VARCHAR(50) NULL,             -- NULL nếu là rule chuẩn mặc định toàn hệ thống
    tier_name VARCHAR(10) NOT NULL 
        CHECK (tier_name IN ('S', 'A', 'B', 'C', 'D', 'QUALIFIER', 'MAIN')),
    rank_position INT NOT NULL,             -- 1: Vô địch, 2: Á quân, 3: Hạng 3, 4: Top 4, 8: Top 8...
    rank_label NVARCHAR(100) NULL,          -- Nhãn hiển thị ('Vô địch', 'Á quân', 'Bán kết', 'Tứ kết'...)
    points_awarded INT NOT NULL DEFAULT 0,  -- Điểm thưởng rolling window
    elo_weight FLOAT DEFAULT 1.0,           -- Trọng số nhân hệ số Elo (nếu dùng)
    created_at DATETIME DEFAULT GETDATE(),
    FOREIGN KEY (series_id) REFERENCES series(id) ON DELETE CASCADE,
    CONSTRAINT uq_series_tier_rank UNIQUE (series_id, tier_name, rank_position)
);
GO

-- ============================================================================
-- 4. BẢNG TOURNAMENTS (GIẢI ĐẤU THÀNH PHẦN HOẶC GIẢI ĐƠN LẺ ĐỘC LẬP)
-- ============================================================================
CREATE TABLE tournaments (
    id VARCHAR(50) PRIMARY KEY,
    series_id VARCHAR(50) NULL,             -- NULL nếu là Giải Đơn Lẻ (Standalone)
    name NVARCHAR(255) NOT NULL,
    tournament_type VARCHAR(20) NOT NULL DEFAULT 'SINGLE_STAGE' 
        CHECK (tournament_type IN ('SINGLE_STAGE', 'MULTI_STAGE')),
    series_event_type VARCHAR(20) DEFAULT 'NONE' 
        CHECK (series_event_type IN ('QUALIFIER', 'MAIN', 'NONE')), -- QUALIFIER (I=25), MAIN (I=45), NONE
    tier_name VARCHAR(10) NULL 
        CHECK (tier_name IN ('S', 'A', 'B', 'C', 'D')),             -- Tier cố định cho Rolling Series (S, A, B, C, D)
    series_reward_points INT NULL,          -- Điểm thưởng Vô địch cơ sở
    tournament_index_in_series INT DEFAULT 1, -- Thứ tự giải trong Series (Giải 1, 2, ... 11, 12)
    phase_number INT DEFAULT 1,             -- Phase tương ứng (VD: Giải 12 -> Phase 2)
    division_level INT DEFAULT 1,           -- Cấp Hạng đấu trong League System (1 = Hạng 1, 2 = Hạng 2...)
    season_number INT DEFAULT 1,            -- Mùa giải trong League System (Mùa 1, Mùa 2...)
    division_name NVARCHAR(100) NULL,       -- Tên Hạng đấu hiển thị (VD: "V-League 1", "Hạng Nhất")
    max_teams_per_group INT DEFAULT 4,      -- Số đội tối đa mỗi bảng đấu (cho thể thức Group Stage)
    advancing_seats_count INT DEFAULT 16,   -- Tổng số vé đi tiếp sang Stage sau (cho Multi-Stage)
    linked_qualifier_tournament_id VARCHAR(50) NULL, -- Khóa ngoại liên kết giải vòng loại ("Giải trong Giải")
    status VARCHAR(20) DEFAULT 'DRAFT' 
        CHECK (status IN ('DRAFT', 'ONGOING', 'COMPLETED')),
    -- Cột tương thích ngược hỗ trợ dữ liệu legacy:
    series_points_config NVARCHAR(MAX) NULL, -- JSON cấu hình điểm thưởng tùy biến
    group_assignments NVARCHAR(MAX) NULL,    -- JSON cấu hình phân bảng (legacy backup)
    stage2_teams NVARCHAR(MAX) NULL,         -- JSON danh sách đội hạt giống Stage 2 (legacy backup)
    multi_stage_config NVARCHAR(MAX) NULL,   -- JSON cấu hình đa giai đoạn (legacy backup)
    created_at DATETIME DEFAULT GETDATE(),
    FOREIGN KEY (series_id) REFERENCES series(id),
    FOREIGN KEY (linked_qualifier_tournament_id) REFERENCES tournaments(id)
);
GO

-- ============================================================================
-- 5. BẢNG TOURNAMENT_PLACEMENT_POINTS (CẤU HÌNH ĐIỂM THƯỞNG RIÊNG CHO GIẢI CỤ THỂ)
-- Chuẩn hóa bảng điểm tùy biến của từng giải đấu (thay thế JSON series_points_config)
-- ============================================================================
CREATE TABLE tournament_placement_points (
    id VARCHAR(50) PRIMARY KEY,
    tournament_id VARCHAR(50) NOT NULL,
    rank_position INT NOT NULL,             -- Thứ hạng (1, 2, 3, 4, 8, 16...)
    rank_label NVARCHAR(100) NULL,          -- 'Vô địch', 'Á quân', 'Hạng 3', 'Top 4', 'Top 8'...
    points_awarded INT NOT NULL DEFAULT 0,  -- Điểm thưởng cộng cho thứ hạng này
    created_at DATETIME DEFAULT GETDATE(),
    FOREIGN KEY (tournament_id) REFERENCES tournaments(id) ON DELETE CASCADE,
    CONSTRAINT uq_tourney_rank UNIQUE (tournament_id, rank_position)
);
GO

-- ============================================================================
-- 6. BẢNG TOURNAMENT_STAGES (CÁC GIAI ĐOẠN TRONG GIẢI MULTI-STAGE HOẶC SINGLE-STAGE)
-- ============================================================================
CREATE TABLE tournament_stages (
    id VARCHAR(50) PRIMARY KEY,
    tournament_id VARCHAR(50) NOT NULL,
    stage_order INT NOT NULL DEFAULT 1,     -- Thứ tự Giai đoạn: 1 (Stage 1), 2 (Stage 2), 3 (Stage 3)...
    stage_name NVARCHAR(100) NOT NULL,      -- VD: "Stage 1: Vòng Bảng", "Stage 2: Knockout Trực Tiếp"
    format VARCHAR(30) NOT NULL 
        CHECK (format IN ('SINGLE_ELIMINATION', 'DOUBLE_ELIMINATION', 'ROUND_ROBIN', 'SWISS_LITE', 'GROUP_STAGE')),
    advancing_teams_count INT DEFAULT 0,    -- Số đội giành quyền tiến vào Stage kế tiếp
    target_wins INT DEFAULT 3,              -- Swiss: Số trận thắng đạt chuẩn đi tiếp
    elimination_losses INT DEFAULT 3,       -- Swiss: Số trận thua bị loại
    win_points INT DEFAULT 3,               -- Group / Round Robin: Điểm cho 1 trận Thắng
    draw_points INT DEFAULT 1,              -- Group / Round Robin: Điểm cho 1 trận Hòa
    loss_points INT DEFAULT 0,              -- Group / Round Robin: Điểm cho 1 trận Thua
    legs_count INT DEFAULT 1 
        CHECK (legs_count BETWEEN 1 AND 10), -- Số lượt thi đấu (1: Lượt đi đơn, 2: Lượt đi - Lượt về)
    status VARCHAR(20) DEFAULT 'PENDING' 
        CHECK (status IN ('PENDING', 'ONGOING', 'COMPLETED')),
    created_at DATETIME DEFAULT GETDATE(),
    FOREIGN KEY (tournament_id) REFERENCES tournaments(id) ON DELETE CASCADE,
    CONSTRAINT uq_tourney_stage_order UNIQUE (tournament_id, stage_order)
);
GO

-- ============================================================================
-- 7. BẢNG TEAMS (CÁC ĐỘI BÓNG / THÀNH VIÊN ĐĂNG KÝ THAM GIA GIẢI ĐẤU)
-- ============================================================================
CREATE TABLE teams (
    id VARCHAR(50) PRIMARY KEY,
    tournament_id VARCHAR(50) NOT NULL,
    partner_participant_id VARCHAR(50) NULL, -- Khóa ngoại liên kết hồ sơ Partner trong Series (nếu có)
    raw_name NVARCHAR(255) NOT NULL,         -- Tên hiển thị gốc nhập từ giao diện
    normalized_name NVARCHAR(255) NOT NULL,  -- Chuẩn hóa tên (bỏ khoảng trắng, chữ hoa/thường) để gom nhóm điểm Series
    original_seed INT NOT NULL,              -- Hạt giống gốc cố định từ đầu giải (BR-02)
    current_stage_id VARCHAR(50) NULL,       -- Giai đoạn hiện tại đội đang thi đấu
    final_rank INT NULL,                     -- Thứ hạng chung cuộc khi giải kết thúc (1: Vô địch, 2: Á quân...)
    status VARCHAR(20) DEFAULT 'ACTIVE' 
        CHECK (status IN ('ACTIVE', 'QUALIFIED', 'ELIMINATED')),
    created_at DATETIME DEFAULT GETDATE(),
    FOREIGN KEY (tournament_id) REFERENCES tournaments(id) ON DELETE CASCADE,
    FOREIGN KEY (partner_participant_id) REFERENCES partner_participants(id),
    FOREIGN KEY (current_stage_id) REFERENCES tournament_stages(id)
);
GO

-- ============================================================================
-- 8. BẢNG STAGE_PARTICIPANTS (DANH SÁCH ĐỘI VÀ HẠT GIỐNG TỪNG GIAI ĐOẠN)
-- Chuẩn hóa danh sách đội tham dự và đi tiếp giữa các Stage (thay thế JSON stage2_teams)
-- ============================================================================
CREATE TABLE stage_participants (
    id VARCHAR(50) PRIMARY KEY,
    tournament_id VARCHAR(50) NOT NULL,
    stage_id VARCHAR(50) NOT NULL,
    team_id VARCHAR(50) NOT NULL,
    seed_in_stage INT NOT NULL,              -- Hạt giống phân bổ riêng trong Stage này
    qualification_source VARCHAR(50) DEFAULT 'AUTO_SEED'
        CHECK (qualification_source IN ('DIRECT_ENTRY', 'AUTO_SEED', 'GROUP_WINNER', 'GROUP_RUNNER_UP', 'SWISS_ADVANCE', 'WILDCARD')),
    status VARCHAR(20) DEFAULT 'ACTIVE' 
        CHECK (status IN ('ACTIVE', 'QUALIFIED', 'ELIMINATED')),
    created_at DATETIME DEFAULT GETDATE(),
    FOREIGN KEY (tournament_id) REFERENCES tournaments(id),
    FOREIGN KEY (stage_id) REFERENCES tournament_stages(id) ON DELETE CASCADE,
    FOREIGN KEY (team_id) REFERENCES teams(id),
    CONSTRAINT uq_stage_team UNIQUE (stage_id, team_id)
);
GO

-- ============================================================================
-- 9. BẢNG GROUPS (CÁC BẢNG ĐẤU TRONG GIAI ĐOẠN GROUP STAGE)
-- Chuẩn hóa danh sách các bảng đấu (Bảng A, B, C, D...)
-- ============================================================================
CREATE TABLE groups (
    id VARCHAR(50) PRIMARY KEY,
    stage_id VARCHAR(50) NOT NULL,
    group_name NVARCHAR(50) NOT NULL,        -- VD: "Bảng A", "Bảng B", "Bảng C"...
    qualified_slots_count INT NOT NULL DEFAULT 2, -- Số suất chính thức đi tiếp vào vòng sau
    created_at DATETIME DEFAULT GETDATE(),
    FOREIGN KEY (stage_id) REFERENCES tournament_stages(id) ON DELETE CASCADE,
    CONSTRAINT uq_stage_group_name UNIQUE (stage_id, group_name)
);
GO

-- ============================================================================
-- 10. BẢNG GROUP_TEAMS (CHI TIẾT VỊ TRÍ, THÀNH TÍCH & BXH TRONG BẢNG ĐẤU)
-- Chuẩn hóa phân bổ đội vào từng bảng đấu (thay thế JSON group_assignments)
-- ============================================================================
CREATE TABLE group_teams (
    id VARCHAR(50) PRIMARY KEY,
    group_id VARCHAR(50) NOT NULL,
    team_id VARCHAR(50) NOT NULL,
    seed_in_group INT DEFAULT 1,             -- Hạt giống trong bảng (Hạt giống 1, 2, 3, 4)
    matches_played INT DEFAULT 0,            -- Số trận đã đấu
    points INT DEFAULT 0,                    -- Điểm số tích lũy
    wins INT DEFAULT 0,                      -- Số trận thắng
    draws INT DEFAULT 0,                     -- Số trận hòa
    losses INT DEFAULT 0,                    -- Số trận thua
    goals_scored INT DEFAULT 0,              -- Bàn thắng ghi được
    goals_conceded INT DEFAULT 0,            -- Bàn thua nhận vào
    goal_difference INT DEFAULT 0,           -- Hiệu số bàn thắng thua (+/-)
    rank_in_group INT DEFAULT 0,             -- Thứ hạng hiện tại trong bảng (1, 2, 3...)
    FOREIGN KEY (group_id) REFERENCES groups(id) ON DELETE CASCADE,
    FOREIGN KEY (team_id) REFERENCES teams(id),
    CONSTRAINT uq_group_team UNIQUE (group_id, team_id)
);
GO

-- ============================================================================
-- 11. BẢNG MATCHES (DANH SÁCH TRẬN ĐẤU & CÂY NHÁNH ĐẤU BRACKET TREE)
-- Tối ưu toàn diện cho Single/Double Elimination, Swiss, Round Robin, Group Stage:
-- Hỗ trợ next_match_id (nhánh thắng), loser_next_match_id (nhánh thua), penalty, leg
-- ============================================================================
CREATE TABLE matches (
    id VARCHAR(50) PRIMARY KEY,
    tournament_id VARCHAR(50) NOT NULL,
    stage_id VARCHAR(50) NOT NULL,
    group_id VARCHAR(50) NULL,               -- NULL nếu là Knockout/Swiss; Có giá trị nếu là trận Vòng Bảng
    round_number INT NOT NULL,               -- Vòng 1, Vòng 2, Vòng Bán Kết, Chung Kết...
    match_order INT DEFAULT 1,               -- Thứ tự trận đấu trong vòng
    match_code NVARCHAR(100) NOT NULL,       -- VD: "Trận #1", "Nhóm 0-0 #1", "Bảng A - Vòng 1"
    bracket_type VARCHAR(30) DEFAULT 'MAIN' 
        CHECK (bracket_type IN ('WINNER_BRACKET', 'LOSER_BRACKET', 'GRAND_FINAL', 'GRAND_FINAL_RESET', 'THIRD_PLACE', 'SWISS', 'ROUND_ROBIN', 'GROUP_STAGE', 'MAIN')),
    team1_id VARCHAR(50) NULL,               -- Đội 1
    team2_id VARCHAR(50) NULL,               -- Đội 2
    score1 INT NULL,                         -- Điểm / Bàn thắng Đội 1
    score2 INT NULL,                         -- Điểm / Bàn thắng Đội 2
    penalty1 INT NULL,                       -- Điểm sút luân lưu Đội 1 (nếu có)
    penalty2 INT NULL,                       -- Điểm sút luân lưu Đội 2 (nếu có)
    winner_id VARCHAR(50) NULL,              -- Đội chiến thắng
    loser_id VARCHAR(50) NULL,               -- Đội thua cuộc
    
    -- Cây điều hướng người THẮNG (Winner Advancement):
    next_match_id VARCHAR(50) NULL,          -- Trận kế tiếp mà đội Thắng sẽ tiến vào
    next_slot VARCHAR(10) NULL 
        CHECK (next_slot IN ('SLOT_1', 'SLOT_2')), -- Vị trí Slot 1 hay Slot 2 ở trận tiếp theo
    
    -- Cây điều hướng người THUA (Loser Advancement - Dành cho Double Elimination):
    loser_next_match_id VARCHAR(50) NULL,    -- Trận kế tiếp ở Nhánh Thua (Loser Bracket) mà đội Thua sẽ rơi xuống
    loser_next_slot VARCHAR(10) NULL 
        CHECK (loser_next_slot IN ('SLOT_1', 'SLOT_2')), -- Vị trí Slot 1 hay Slot 2 ở nhánh thua
    
    leg_number INT DEFAULT 1,                -- Lượt đấu (Lượt 1 / Lượt 2 trong thể thức 2 lượt đi-về)
    is_bye BIT DEFAULT 0,                    -- Suất BYE miễn đấu Vòng 1
    status VARCHAR(20) DEFAULT 'PENDING' 
        CHECK (status IN ('PENDING', 'READY', 'IN_PROGRESS', 'FINISHED')),
    created_at DATETIME DEFAULT GETDATE(),
    FOREIGN KEY (tournament_id) REFERENCES tournaments(id) ON DELETE CASCADE,
    FOREIGN KEY (stage_id) REFERENCES tournament_stages(id),
    FOREIGN KEY (group_id) REFERENCES groups(id),
    FOREIGN KEY (team1_id) REFERENCES teams(id),
    FOREIGN KEY (team2_id) REFERENCES teams(id),
    FOREIGN KEY (winner_id) REFERENCES teams(id),
    FOREIGN KEY (loser_id) REFERENCES teams(id),
    FOREIGN KEY (next_match_id) REFERENCES matches(id),
    FOREIGN KEY (loser_next_match_id) REFERENCES matches(id)
);
GO

-- ============================================================================
-- 12. BẢNG SERIES_STANDINGS (BẢNG XẾP HẠNG TỔNG HỢP SERIES & PHASE)
-- ============================================================================
CREATE TABLE series_standings (
    id VARCHAR(50) PRIMARY KEY,
    series_id VARCHAR(50) NOT NULL,
    phase_number INT DEFAULT 1,              -- Phase chốt xếp hạng (Phase 1, Phase 2...)
    division_level INT DEFAULT 1,            -- Hạng đấu trong League System (1 = Hạng 1, 2 = Hạng 2...)
    season_number INT DEFAULT 1,             -- Mùa giải trong League System (Mùa 1, Mùa 2...)
    normalized_team_name NVARCHAR(255) NOT NULL,
    partner_participant_id VARCHAR(50) NULL, -- Khóa ngoại liên kết hồ sơ Partner
    group_name NVARCHAR(100) DEFAULT 'General', -- Nhóm xếp hạng riêng biệt (nếu có)
    total_rolling_points INT DEFAULT 0,      -- Điểm Rolling tích lũy trong W giải gần nhất
    current_elo FLOAT DEFAULT 1000.0,        -- Điểm Elo FIFA hiện tại
    matches_played INT DEFAULT 0,            -- Tổng trận đã đấu (League System)
    wins INT DEFAULT 0,                      -- Tổng trận thắng (League System)
    draws INT DEFAULT 0,                     -- Tổng trận hòa (League System)
    losses INT DEFAULT 0,                    -- Tổng trận thua (League System)
    goals_for INT DEFAULT 0,                 -- Tổng bàn thắng ghi được (League System)
    goals_against INT DEFAULT 0,             -- Tổng bàn thua phải nhận (League System)
    goal_diff INT DEFAULT 0,                 -- Hiệu số bàn thắng thua (+/-)
    points INT DEFAULT 0,                    -- Điểm số mùa giải (League System)
    promotion_status VARCHAR(30) DEFAULT 'NONE' 
        CHECK (promotion_status IN ('CHAMPION', 'PROMOTED', 'RETAINED', 'RELEGATED', 'NONE')), -- Trạng thái Thăng/Xuống hạng
    rank_overall INT DEFAULT 0,              -- Thứ hạng trên BXH Tổng thể Series
    rank_in_group INT DEFAULT 0,             -- Thứ hạng trên BXH Nhóm riêng
    updated_at DATETIME DEFAULT GETDATE(),
    FOREIGN KEY (series_id) REFERENCES series(id) ON DELETE CASCADE,
    FOREIGN KEY (partner_participant_id) REFERENCES partner_participants(id),
    CONSTRAINT uq_series_team_phase UNIQUE (series_id, phase_number, normalized_team_name)
);
GO

-- ============================================================================
-- 13. BẢNG SERIES_TOURNAMENT_HISTORY (LỊCH SỬ THÀNH TÍCH & TRỪ ĐIỂM CỬA SỔ TRƯỢT)
-- ============================================================================
CREATE TABLE series_tournament_history (
    id VARCHAR(50) PRIMARY KEY,
    series_id VARCHAR(50) NOT NULL,
    tournament_id VARCHAR(50) NOT NULL,
    phase_number INT DEFAULT 1,
    normalized_team_name NVARCHAR(255) NOT NULL,
    tournament_rank INT NOT NULL,            -- Thứ hạng đạt được trong giải này (1: Vô địch, 2: Á quân...)
    points_earned INT DEFAULT 0,             -- Điểm cộng được từ giải đấu này
    points_deducted INT DEFAULT 0,           -- Điểm bị trượt trừ đi của Giải thứ (k - W) khi k > W
    elo_change FLOAT DEFAULT 0.0,            -- Biến động điểm Elo (+/-) sau giải đấu
    completed_at DATETIME DEFAULT GETDATE(),
    FOREIGN KEY (series_id) REFERENCES series(id) ON DELETE CASCADE,
    FOREIGN KEY (tournament_id) REFERENCES tournaments(id),
    CONSTRAINT uq_series_tourney_team UNIQUE (series_id, tournament_id, normalized_team_name)
);
GO

-- ============================================================================
-- INDEXES TỐI ƯU HIỆU NĂNG QUERY (INDEXING STRATEGY)
-- ============================================================================
CREATE INDEX idx_tournaments_series ON tournaments(series_id);
CREATE INDEX idx_tournaments_status ON tournaments(status);
CREATE INDEX idx_stages_tournament ON tournament_stages(tournament_id, stage_order);
CREATE INDEX idx_teams_tournament ON teams(tournament_id);
CREATE INDEX idx_teams_normalized ON teams(normalized_name);
CREATE INDEX idx_stage_participants_lookup ON stage_participants(stage_id, team_id);
CREATE INDEX idx_groups_stage ON groups(stage_id);
CREATE INDEX idx_group_teams_group ON group_teams(group_id);
CREATE INDEX idx_matches_tournament_stage ON matches(tournament_id, stage_id);
CREATE INDEX idx_matches_bracket_round ON matches(tournament_id, bracket_type, round_number);
CREATE INDEX idx_matches_teams ON matches(team1_id, team2_id);
CREATE INDEX idx_matches_status ON matches(status);
CREATE INDEX idx_standings_series_phase ON series_standings(series_id, phase_number);
CREATE INDEX idx_history_series_team ON series_tournament_history(series_id, normalized_team_name);
GO

-- ============================================================================
-- DỮ LIỆU MẪU KHỞI TẠO (SAMPLE SEED DATA)
-- ============================================================================

-- 1. Thêm Series mẫu (Rolling Window & FIFA Elo)
INSERT INTO series (id, name, ranking_model, phase_size, current_phase, initial_points, initial_elo, status)
VALUES 
('SERIES_ROLLING_2026', N'Vietnam Pro Tour 2026 (Rolling Window W=10)', 'ROLLING_WINDOW', 10, 1, 0, 1000.0, 'ACTIVE'),
('SERIES_ELO_2026', N'National Elo Circuit 2026 (FIFA Elo)', 'FIFA_ELO', 10, 1, 0, 1200.0, 'ACTIVE');

-- 2. Thêm Thành viên / Đội Partner mẫu
INSERT INTO partner_participants (id, series_id, name, group_name)
VALUES 
('P01', 'SERIES_ROLLING_2026', N'Hà Nội FC', 'General'),
('P02', 'SERIES_ROLLING_2026', N'Hoàng Anh Gia Lai', 'General'),
('P03', 'SERIES_ROLLING_2026', N'Sông Lam Nghệ An', 'General'),
('P04', 'SERIES_ROLLING_2026', N'Bình Định FC', 'General'),
('P05', 'SERIES_ROLLING_2026', N'Thép Xanh Nam Định', 'General'),
('P06', 'SERIES_ROLLING_2026', N'Công An Hà Nội', 'General'),
('P07', 'SERIES_ROLLING_2026', N'Hải Phòng FC', 'General'),
('P08', 'SERIES_ROLLING_2026', N'Becamex Bình Dương', 'General');

-- 3. Thêm Quy tắc điểm chuẩn theo Tier cho Series (S, A, B, C, D)
INSERT INTO series_point_rules (id, series_id, tier_name, rank_position, rank_label, points_awarded, elo_weight)
VALUES 
('R_S_1', 'SERIES_ROLLING_2026', 'S', 1, N'Vô địch', 100, 1.5),
('R_S_2', 'SERIES_ROLLING_2026', 'S', 2, N'Á quân', 70, 1.2),
('R_S_3', 'SERIES_ROLLING_2026', 'S', 3, N'Hạng 3', 50, 1.0),
('R_S_4', 'SERIES_ROLLING_2026', 'S', 4, N'Top 4', 40, 0.9),
('R_S_8', 'SERIES_ROLLING_2026', 'S', 8, N'Top 8', 20, 0.7),
('R_A_1', 'SERIES_ROLLING_2026', 'A', 1, N'Vô địch', 60, 1.2),
('R_A_2', 'SERIES_ROLLING_2026', 'A', 2, N'Á quân', 40, 1.0),
('R_A_4', 'SERIES_ROLLING_2026', 'A', 4, N'Top 4', 25, 0.8),
('R_A_8', 'SERIES_ROLLING_2026', 'A', 8, N'Top 8', 10, 0.5);

-- 4. Thêm Giải đấu mẫu Độc lập (Multi-Stage: Vòng Bảng -> Knockout)
INSERT INTO tournaments (id, series_id, name, tournament_type, series_event_type, tier_name, series_reward_points, max_teams_per_group, advancing_seats_count, status)
VALUES 
('TOUR_DEMO_01', 'SERIES_ROLLING_2026', N'Cúp Mùa Xuân 2026 - Tier S', 'MULTI_STAGE', 'MAIN', 'S', 100, 4, 4, 'ONGOING');

-- 5. Thêm Các Giai đoạn (Stages) cho Giải TOUR_DEMO_01
INSERT INTO tournament_stages (id, tournament_id, stage_order, stage_name, format, advancing_teams_count, win_points, draw_points, loss_points, status)
VALUES 
('STAGE_DEMO_01_S1', 'TOUR_DEMO_01', 1, N'Stage 1: Vòng Bảng (Group Stage)', 'GROUP_STAGE', 4, 3, 1, 0, 'COMPLETED'),
('STAGE_DEMO_01_S2', 'TOUR_DEMO_01', 2, N'Stage 2: Loại Trực Tiếp (Single Elimination)', 'SINGLE_ELIMINATION', 1, 3, 1, 0, 'ONGOING');

-- 6. Thêm Đội tham gia Giải TOUR_DEMO_01
INSERT INTO teams (id, tournament_id, partner_participant_id, raw_name, normalized_name, original_seed, current_stage_id, status)
VALUES 
('TM_01', 'TOUR_DEMO_01', 'P01', N'Hà Nội FC', 'ha noi fc', 1, 'STAGE_DEMO_01_S2', 'ACTIVE'),
('TM_02', 'TOUR_DEMO_01', 'P02', N'Hoàng Anh Gia Lai', 'hoang anh gia lai', 2, 'STAGE_DEMO_01_S2', 'ACTIVE'),
('TM_03', 'TOUR_DEMO_01', 'P03', N'Sông Lam Nghệ An', 'song lam nghe an', 3, 'STAGE_DEMO_01_S2', 'ACTIVE'),
('TM_04', 'TOUR_DEMO_01', 'P04', N'Bình Định FC', 'binh dinh fc', 4, 'STAGE_DEMO_01_S2', 'ACTIVE'),
('TM_05', 'TOUR_DEMO_01', 'P05', N'Thép Xanh Nam Định', 'thep xanh nam dinh', 5, 'STAGE_DEMO_01_S1', 'ELIMINATED'),
('TM_06', 'TOUR_DEMO_01', 'P06', N'Công An Hà Nội', 'cong an ha noi', 6, 'STAGE_DEMO_01_S1', 'ELIMINATED'),
('TM_07', 'TOUR_DEMO_01', 'P07', N'Hải Phòng FC', 'hai phong fc', 7, 'STAGE_DEMO_01_S1', 'ELIMINATED'),
('TM_08', 'TOUR_DEMO_01', 'P08', N'Becamex Bình Dương', 'becamex binh duong', 8, 'STAGE_DEMO_01_S1', 'ELIMINATED');

-- 7. Thêm Bảng đấu (Groups) cho Stage 1
INSERT INTO groups (id, stage_id, group_name, qualified_slots_count)
VALUES 
('GRP_A', 'STAGE_DEMO_01_S1', N'Bảng A', 2),
('GRP_B', 'STAGE_DEMO_01_S1', N'Bảng B', 2);

-- 8. Thêm Đội vào từng Bảng đấu (Group Teams)
INSERT INTO group_teams (id, group_id, team_id, seed_in_group, matches_played, points, wins, draws, losses, goals_scored, goals_conceded, goal_difference, rank_in_group)
VALUES 
('GT_A1', 'GRP_A', 'TM_01', 1, 3, 9, 3, 0, 0, 7, 1, 6, 1),
('GT_A2', 'GRP_A', 'TM_03', 2, 3, 6, 2, 0, 1, 5, 3, 2, 2),
('GT_A3', 'GRP_A', 'TM_05', 3, 3, 3, 1, 0, 2, 3, 5, -2, 3),
('GT_A4', 'GRP_A', 'TM_07', 4, 3, 0, 0, 0, 3, 1, 7, -6, 4),
('GT_B1', 'GRP_B', 'TM_02', 1, 3, 7, 2, 1, 0, 6, 2, 4, 1),
('GT_B2', 'GRP_B', 'TM_04', 2, 3, 5, 1, 2, 0, 4, 3, 1, 2),
('GT_B3', 'GRP_B', 'TM_06', 3, 3, 4, 1, 1, 1, 3, 4, -1, 3),
('GT_B4', 'GRP_B', 'TM_08', 4, 3, 0, 0, 0, 3, 2, 6, -4, 4);

-- 9. Thêm Danh sách 4 đội đi tiếp vào Stage 2 (Stage Participants)
INSERT INTO stage_participants (id, tournament_id, stage_id, team_id, seed_in_stage, qualification_source, status)
VALUES 
('SP_01', 'TOUR_DEMO_01', 'STAGE_DEMO_01_S2', 'TM_01', 1, 'GROUP_WINNER', 'ACTIVE'),
('SP_02', 'TOUR_DEMO_01', 'STAGE_DEMO_01_S2', 'TM_04', 4, 'GROUP_RUNNER_UP', 'ACTIVE'),
('SP_03', 'TOUR_DEMO_01', 'STAGE_DEMO_01_S2', 'TM_02', 2, 'GROUP_WINNER', 'ACTIVE'),
('SP_04', 'TOUR_DEMO_01', 'STAGE_DEMO_01_S2', 'TM_03', 3, 'GROUP_RUNNER_UP', 'ACTIVE');

-- 10. Thêm Các trận đấu Stage 2 Knockout (Bán kết & Chung kết)
INSERT INTO matches (id, tournament_id, stage_id, round_number, match_order, match_code, bracket_type, team1_id, team2_id, score1, score2, winner_id, loser_id, next_match_id, next_slot, status)
VALUES 
-- Chung kết (Round 2)
('M_FINAL', 'TOUR_DEMO_01', 'STAGE_DEMO_01_S2', 2, 1, N'Trận Chung Kết', 'MAIN', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'PENDING');

-- Bán kết 1 & 2 (Round 1) - Dẫn đến Chung Kết
INSERT INTO matches (id, tournament_id, stage_id, round_number, match_order, match_code, bracket_type, team1_id, team2_id, score1, score2, winner_id, loser_id, next_match_id, next_slot, status)
VALUES 
('M_SEMI_1', 'TOUR_DEMO_01', 'STAGE_DEMO_01_S2', 1, 1, N'Bán Kết 1 (Nhất A vs Nhì B)', 'MAIN', 'TM_01', 'TM_04', 3, 1, 'TM_01', 'TM_04', 'M_FINAL', 'SLOT_1', 'FINISHED'),
('M_SEMI_2', 'TOUR_DEMO_01', 'STAGE_DEMO_01_S2', 1, 2, N'Bán Kết 2 (Nhất B vs Nhì A)', 'MAIN', 'TM_02', 'TM_03', 2, 0, 'TM_02', 'TM_03', 'M_FINAL', 'SLOT_2', 'FINISHED');

-- Cập nhật 2 đội vào Chung kết sau khi Bán kết xong
UPDATE matches 
SET team1_id = 'TM_01', team2_id = 'TM_02', status = 'READY'
WHERE id = 'M_FINAL';

-- 11. Cập nhật Bảng xếp hạng Series mẫu (Series Standings)
INSERT INTO series_standings (id, series_id, phase_number, normalized_team_name, partner_participant_id, total_rolling_points, current_elo, rank_overall)
VALUES 
('STD_01', 'SERIES_ROLLING_2026', 1, 'ha noi fc', 'P01', 100, 1050.0, 1),
('STD_02', 'SERIES_ROLLING_2026', 1, 'hoang anh gia lai', 'P02', 70, 1035.0, 2),
('STD_03', 'SERIES_ROLLING_2026', 1, 'song lam nghe an', 'P03', 40, 990.0, 3),
('STD_04', 'SERIES_ROLLING_2026', 1, 'binh dinh fc', 'P04', 40, 985.0, 4),
('STD_05', 'SERIES_ROLLING_2026', 1, 'thep xanh nam dinh', 'P05', 10, 970.0, 5),
('STD_06', 'SERIES_ROLLING_2026', 1, 'cong an ha noi', 'P06', 10, 970.0, 6),
('STD_07', 'SERIES_ROLLING_2026', 1, 'hai phong fc', 'P07', 0, 950.0, 7),
('STD_08', 'SERIES_ROLLING_2026', 1, 'becamex binh duong', 'P08', 0, 950.0, 8);
GO

PRINT N'===================================================================';
PRINT N'ĐÃ KHỞI TẠO THÀNH CÔNG DATABASE tourma_db VỚI 13 BẢNG CHUẨN HÓA!';
PRINT N'===================================================================';
