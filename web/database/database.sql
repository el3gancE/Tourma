-- ============================================================================
-- TOURMA - COMPLETE DATABASE SCHEMA (MICROSOFT SQL SERVER / T-SQL)
-- Quản lý Giải đấu (Single/Multi-Stage) & Series (Phase-based Rolling / FIFA Elo)
-- ============================================================================

-- Bắt buộc chuyển về database master trước khi Drop & Create database tourma_db
USE master;
GO

IF EXISTS (SELECT * FROM sys.databases WHERE name = 'tourma_db')
BEGIN
    ALTER DATABASE tourma_db SET SINGLE_USER WITH ROLLBACK IMMEDIATE;
    DROP DATABASE tourma_db;
END
GO

CREATE DATABASE tourma_db;
GO

USE tourma_db;
GO

-- 1. BẢNG SERIES (MÙA GIẢI / CHUỖI GIẢI ĐẤU)
CREATE TABLE series (
    id VARCHAR(50) PRIMARY KEY,
    name NVARCHAR(255) NOT NULL,
    ranking_model VARCHAR(50) NOT NULL DEFAULT 'ROLLING_WINDOW' CHECK (ranking_model IN ('ROLLING_WINDOW', 'FIFA_ELO')),
    phase_size INT DEFAULT 10, -- Độ rộng cửa sổ trượt W (Ví dụ: W = 10 giải; 1-10 là Phase 1, 11-20 là Phase 2)
    current_phase INT DEFAULT 1, -- Phase hiện tại của Series
    initial_points INT DEFAULT 0, -- Điểm khởi đầu cho Rolling Window (0 điểm)
    initial_elo FLOAT DEFAULT 1000.0, -- Điểm Elo khởi điểm cho FIFA Elo (1000 điểm)
    status VARCHAR(20) DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'COMPLETED')),
    created_at DATETIME DEFAULT GETDATE()
);

-- 2. BẢNG PARTNER PARTICIPANTS (ĐỐI TÁC / THÀNH VIÊN ĐỒNG HÀNH)
CREATE TABLE partner_participants (
    id VARCHAR(50) PRIMARY KEY,
    series_id VARCHAR(50) NOT NULL,
    name NVARCHAR(255) NOT NULL,
    group_name NVARCHAR(100) DEFAULT 'General', -- Nhóm riêng trong FIFA Elo Series
    created_at DATETIME DEFAULT GETDATE(),
    FOREIGN KEY (series_id) REFERENCES series(id) ON DELETE CASCADE
);

-- 3. BẢNG TOURNAMENTS (GIẢI ĐẤU THÀNH PHẦN HOẶC GIẢI ĐƠN LẺ)
-- Giải Đơn Lẻ (Standalone) có series_id = NULL và series_event_type = 'NONE'
CREATE TABLE tournaments (
    id VARCHAR(50) PRIMARY KEY,
    series_id VARCHAR(50) NULL, -- NULL nếu là Giải Đơn Lẻ (Standalone)
    name NVARCHAR(255) NOT NULL,
    tournament_type VARCHAR(20) NOT NULL DEFAULT 'SINGLE_STAGE' CHECK (tournament_type IN ('SINGLE_STAGE', 'MULTI_STAGE')),
    series_event_type VARCHAR(20) DEFAULT 'NONE' CHECK (series_event_type IN ('QUALIFIER', 'MAIN', 'NONE')), -- QUALIFIER (I=25), MAIN (I=45), NONE
    tier_name VARCHAR(10) NULL CHECK (tier_name IN ('S', 'A', 'B', 'C', 'D')), -- Tier cố định cho Rolling Series: S, A, B, C, D (NULL nếu là Giải đơn lẻ)
    tournament_index_in_series INT DEFAULT 1, -- Thứ tự giải trong Series (e.g. Giải thứ 1, 2, ... 11, 12)
    phase_number INT DEFAULT 1, -- Phase tương ứng (e.g. Giải thứ 12 -> Phase 2)
    max_teams_per_group INT DEFAULT 4, -- Số đội tối đa mỗi bảng
    advancing_seats_count INT DEFAULT 16, -- Tổng số vé đi tiếp sang Stage sau
    linked_qualifier_tournament_id VARCHAR(50) NULL, -- Dùng cho mô hình "Giải trong Giải"
    status VARCHAR(20) DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'ONGOING', 'COMPLETED')),
    created_at DATETIME DEFAULT GETDATE(),
    FOREIGN KEY (series_id) REFERENCES series(id),
    FOREIGN KEY (linked_qualifier_tournament_id) REFERENCES tournaments(id)
);

-- 4. BẢNG TOURNAMENT STAGES (CÁC GIAI ĐOẠN TRONG GIẢI MULTI-STAGE HOẶC SINGLE-STAGE)
CREATE TABLE tournament_stages (
    id VARCHAR(50) PRIMARY KEY,
    tournament_id VARCHAR(50) NOT NULL,
    stage_order INT NOT NULL DEFAULT 1, -- Thứ tự Stage: 1, 2, 3...
    stage_name NVARCHAR(100) NOT NULL, -- e.g. Stage 1: Group Stage, Stage 2: Knockout
    format VARCHAR(30) NOT NULL CHECK (format IN ('SINGLE_ELIMINATION', 'DOUBLE_ELIMINATION', 'ROUND_ROBIN', 'SWISS_LITE', 'GROUP_STAGE')),
    target_wins INT DEFAULT 3, -- Thắng 3 trận là đủ điều kiện đi tiếp (Swiss System)
    elimination_losses INT DEFAULT 3, -- Thua 3 trận là bị loại về nước (Swiss System)
    win_points INT DEFAULT 3, -- Tùy chỉnh điểm cho 1 trận Thắng (Group Stage & Round Robin, e.g. 3 điểm)
    draw_points INT DEFAULT 1, -- Tùy chỉnh điểm cho 1 trận Hòa (Group Stage & Round Robin, e.g. 1 điểm)
    loss_points INT DEFAULT 0, -- Tùy chỉnh điểm cho 1 trận Thua (Group Stage & Round Robin, e.g. 0 điểm)
    legs_count INT DEFAULT 1 CHECK (legs_count BETWEEN 1 AND 10), -- Số lần gặp nhau (Lượt đi / Lượt về, min 1, max 10)
    status VARCHAR(20) DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'ONGOING', 'COMPLETED')),
    created_at DATETIME DEFAULT GETDATE(),
    FOREIGN KEY (tournament_id) REFERENCES tournaments(id) ON DELETE CASCADE
);

-- 5. BẢNG TEAMS (CÁC ĐỘI BÓNG / PARTICIPANTS THAM GIA GIẢI)
CREATE TABLE teams (
    id VARCHAR(50) PRIMARY KEY,
    tournament_id VARCHAR(50) NOT NULL,
    partner_participant_id VARCHAR(50) NULL,
    raw_name NVARCHAR(255) NOT NULL,
    normalized_name NVARCHAR(255) NOT NULL, -- Chuẩn hóa tên đội để gom nhóm tích điểm Series
    original_seed INT NOT NULL, -- Hạt giống gốc đi theo suốt giải từ Stage 1 -> Stage N (BR-02)
    current_stage_id VARCHAR(50) NULL,
    status VARCHAR(20) DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'QUALIFIED', 'ELIMINATED')),
    created_at DATETIME DEFAULT GETDATE(),
    FOREIGN KEY (tournament_id) REFERENCES tournaments(id) ON DELETE CASCADE,
    FOREIGN KEY (partner_participant_id) REFERENCES partner_participants(id),
    FOREIGN KEY (current_stage_id) REFERENCES tournament_stages(id)
);

-- 6. BẢNG GROUPS (CÁC BẢNG ĐẤU TRONG GROUP STAGE)
CREATE TABLE groups (
    id VARCHAR(50) PRIMARY KEY,
    stage_id VARCHAR(50) NOT NULL,
    group_name NVARCHAR(50) NOT NULL, -- e.g. Bảng A, Bảng B...
    qualified_slots_count INT NOT NULL DEFAULT 2, -- Số vé đi tiếp cứng + vé vớt
    FOREIGN KEY (stage_id) REFERENCES tournament_stages(id) ON DELETE CASCADE
);

-- 7. BẢNG GROUP_TEAMS (CHI TIẾT VỊ TRÍ & ĐIỂM SỐ TRONG BẢNG ĐẤU)
CREATE TABLE group_teams (
    id VARCHAR(50) PRIMARY KEY,
    group_id VARCHAR(50) NOT NULL,
    team_id VARCHAR(50) NOT NULL,
    points INT DEFAULT 0,
    wins INT DEFAULT 0,
    draws INT DEFAULT 0,
    losses INT DEFAULT 0,
    goals_scored INT DEFAULT 0,
    goals_conceded INT DEFAULT 0,
    goal_difference INT DEFAULT 0,
    rank_in_group INT DEFAULT 0,
    FOREIGN KEY (group_id) REFERENCES groups(id) ON DELETE CASCADE,
    FOREIGN KEY (team_id) REFERENCES teams(id)
);

-- 8. BẢNG MATCHES (DANH SÁCH TRẬN ĐẤU / MATCH NODES)
CREATE TABLE matches (
    id VARCHAR(50) PRIMARY KEY,
    tournament_id VARCHAR(50) NOT NULL,
    stage_id VARCHAR(50) NOT NULL,
    group_id VARCHAR(50) NULL, -- NULL nếu không phải thi đấu vòng bảng
    round_number INT NOT NULL, -- Vòng 1, Vòng 2...
    match_code NVARCHAR(100) NOT NULL, -- e.g. Match #1, Match #16, NHÓM 0-0 #1
    bracket_type VARCHAR(30) DEFAULT 'MAIN' CHECK (bracket_type IN ('WINNER_BRACKET', 'LOSER_BRACKET', 'GRAND_FINAL', 'SWISS', 'GROUP', 'MAIN')),
    team1_id VARCHAR(50) NULL,
    team2_id VARCHAR(50) NULL,
    score1 INT NULL,
    score2 INT NULL,
    winner_id VARCHAR(50) NULL,
    next_match_id VARCHAR(50) NULL, -- Nút cây dẫn đến trận tiếp theo
    next_slot VARCHAR(10) NULL CHECK (next_slot IN ('SLOT_1', 'SLOT_2')),
    is_bye BIT DEFAULT 0, -- Suất BYE miễn đấu Vòng 1
    status VARCHAR(20) DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'READY', 'IN_PROGRESS', 'FINISHED')),
    created_at DATETIME DEFAULT GETDATE(),
    FOREIGN KEY (tournament_id) REFERENCES tournaments(id) ON DELETE CASCADE,
    FOREIGN KEY (stage_id) REFERENCES tournament_stages(id),
    FOREIGN KEY (group_id) REFERENCES groups(id),
    FOREIGN KEY (team1_id) REFERENCES teams(id),
    FOREIGN KEY (team2_id) REFERENCES teams(id),
    FOREIGN KEY (winner_id) REFERENCES teams(id),
    FOREIGN KEY (next_match_id) REFERENCES matches(id)
);

-- 9. BẢNG SERIES_STANDINGS (BẢNG XẾP HẠNG SERIES TÍCH LŨY CỬA SỔ TRƯỢT & THEO PHASE)
CREATE TABLE series_standings (
    id VARCHAR(50) PRIMARY KEY,
    series_id VARCHAR(50) NOT NULL,
    phase_number INT DEFAULT 1, -- Phase chốt xếp hạng hiện tại (Phase 1, Phase 2...)
    normalized_team_name NVARCHAR(255) NOT NULL,
    partner_participant_id VARCHAR(50) NULL,
    group_name NVARCHAR(100) DEFAULT 'General', -- Dùng cho BXH Riêng từng Nhóm
    total_rolling_points INT DEFAULT 0, -- Điểm Rolling trượt tích lũy trong W giải gần nhất
    current_elo FLOAT DEFAULT 1000.0, -- Điểm Elo FIFA
    rank_overall INT DEFAULT 0, -- Thứ hạng trên BXH Series
    rank_in_group INT DEFAULT 0, -- Thứ hạng trên BXH Riêng từng Nhóm
    updated_at DATETIME DEFAULT GETDATE(),
    FOREIGN KEY (series_id) REFERENCES series(id) ON DELETE CASCADE,
    FOREIGN KEY (partner_participant_id) REFERENCES partner_participants(id)
);

-- 10. BẢNG SERIES_TOURNAMENT_HISTORY (LỊCH SỬ ĐIỂM SỐ VÀ TRỪ ĐIỂM TRƯỢT TỪNG GIẢI)
CREATE TABLE series_tournament_history (
    id VARCHAR(50) PRIMARY KEY,
    series_id VARCHAR(50) NOT NULL,
    tournament_id VARCHAR(50) NOT NULL,
    phase_number INT DEFAULT 1,
    normalized_team_name NVARCHAR(255) NOT NULL,
    tournament_rank INT NOT NULL,
    points_earned INT DEFAULT 0, -- Điểm cộng được từ giải đấu này
    points_deducted INT DEFAULT 0, -- Điểm bị trượt trừ đi của Giải thứ (k - W) khi k > W
    elo_change FLOAT DEFAULT 0.0,
    completed_at DATETIME DEFAULT GETDATE(),
    FOREIGN KEY (series_id) REFERENCES series(id) ON DELETE CASCADE,
    FOREIGN KEY (tournament_id) REFERENCES tournaments(id)
);

-- INDEXES NÂNG CAO TỐI ƯU HIỆU NĂNG QUERY
CREATE INDEX idx_teams_tournament ON teams(tournament_id);
CREATE INDEX idx_teams_normalized ON teams(normalized_name);
CREATE INDEX idx_matches_tournament_stage ON matches(tournament_id, stage_id);
CREATE INDEX idx_matches_status ON matches(status);
CREATE INDEX idx_standings_series_phase ON series_standings(series_id, phase_number);
CREATE INDEX idx_history_series_team ON series_tournament_history(series_id, normalized_team_name);
