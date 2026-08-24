-- ============================================================================
-- TOURMA - DATABASE QUERY CHEATSHEET & DATA INSPECTION SCRIPT (MS SQL SERVER)
-- Utility script to view, inspect, and query all database tables and statistics
-- ============================================================================

USE tourma_db;
GO

-- ============================================================================
-- 1. OVERVIEW - COUNT RECORDS ACROSS ALL 10 TABLES
-- ============================================================================
SELECT 'series' AS table_name, COUNT(*) AS total_rows FROM series
UNION ALL SELECT 'partner_participants', COUNT(*) FROM partner_participants
UNION ALL SELECT 'tournaments', COUNT(*) FROM tournaments
UNION ALL SELECT 'tournament_stages', COUNT(*) FROM tournament_stages
UNION ALL SELECT 'teams', COUNT(*) FROM teams
UNION ALL SELECT 'groups', COUNT(*) FROM groups
UNION ALL SELECT 'group_teams', COUNT(*) FROM group_teams
UNION ALL SELECT 'matches', COUNT(*) FROM matches
UNION ALL SELECT 'series_standings', COUNT(*) FROM series_standings
UNION ALL SELECT 'series_tournament_history', COUNT(*) FROM series_tournament_history;
GO

-- ============================================================================
-- 2. FULL SELECT QUERIES FOR EACH INDIVIDUAL TABLE
-- ============================================================================

-- 2.1 Bảng Series (Mùa giải / Chuỗi giải)
SELECT * FROM series ORDER BY created_at DESC;

-- 2.2 Bảng Partner Participants (Đội / Thành viên đồng hành)
SELECT * FROM partner_participants ORDER BY created_at DESC;

-- 2.3 Bảng Tournaments (Giải đấu đơn lẻ hoặc thành phần)
SELECT * FROM tournaments ORDER BY created_at DESC;

-- 2.4 Bảng Tournament Stages (Các giai đoạn thi đấu)
SELECT * FROM tournament_stages ORDER BY tournament_id, stage_order ASC;

-- 2.5 Bảng Teams (Các đội tham gia giải & Hạt giống gốc)
SELECT * FROM teams ORDER BY tournament_id, original_seed ASC;

-- 2.6 Bảng Groups (Các bảng đấu Vòng bảng)
SELECT * FROM groups;

-- 2.7 Bảng Group Teams (Thứ hạng & Điểm số trong bảng đấu)
SELECT * FROM group_teams ORDER BY group_id, points DESC, goal_difference DESC;

-- 2.8 Bảng Matches (Cây thi đấu & Kết quả các trận)
SELECT * FROM matches ORDER BY tournament_id, stage_id, round_number, id ASC;

-- 2.9 Bảng Series Standings (Bảng xếp hạng chuỗi trượt & Elo)
SELECT * FROM series_standings ORDER BY series_id, phase_number, rank_overall ASC;

-- 2.10 Bảng Series Tournament History (Lịch sử điểm số từng giải)
SELECT * FROM series_tournament_history ORDER BY series_id, completed_at DESC;
GO

-- ============================================================================
-- 3. ADVANCED JOINED QUERIES (FULL TOURNAMENT & MATCH TREE INSPECTION)
-- ============================================================================

-- 3.1 Xem thông tin chi tiết Giải đấu + Thể thức Stages
SELECT 
    t.id AS tournament_id,
    t.name AS tournament_name,
    t.tournament_type,
    t.status AS tournament_status,
    s.id AS stage_id,
    s.stage_name,
    s.format,
    s.legs_count,
    s.win_points,
    s.draw_points,
    s.loss_points
FROM tournaments t
LEFT JOIN tournament_stages s ON t.id = s.tournament_id
ORDER BY t.created_at DESC, s.stage_order ASC;

-- 3.2 Xem danh sách Đội bóng & Hạt giống gốc của một Giải đấu
SELECT 
    t.name AS tournament_name,
    tm.id AS team_id,
    tm.original_seed AS seed_number,
    tm.raw_name AS team_name,
    tm.status AS team_status
FROM teams tm
JOIN tournaments t ON tm.tournament_id = t.id
ORDER BY t.created_at DESC, tm.original_seed ASC;

-- 3.3 Xem Cây sơ đồ Trận đấu (Match Bracket Tree with Team Names & Scores)
SELECT 
    m.id AS match_id,
    t.name AS tournament_name,
    st.stage_name,
    m.round_number,
    m.match_code,
    m.bracket_type,
    t1.raw_name AS team_1,
    m.score1,
    m.score2,
    t2.raw_name AS team_2,
    w.raw_name AS winner_name,
    m.status AS match_status
FROM matches m
JOIN tournaments t ON m.tournament_id = t.id
JOIN tournament_stages st ON m.stage_id = st.id
LEFT JOIN teams t1 ON m.team1_id = t1.id
LEFT JOIN teams t2 ON m.team2_id = t2.id
LEFT JOIN teams w ON m.winner_id = w.id
ORDER BY m.tournament_id, m.round_number ASC, m.id ASC;

-- ============================================================================
-- 4. UTILITY HELPER - QUICK RESET / CLEANUP FOR TESTING
-- ============================================================================
-- Dọn dẹp dữ liệu để test (Chỉ dùng khi cần reset dữ liệu thử nghiệm)
/*
DELETE FROM matches;
DELETE FROM group_teams;
DELETE FROM groups;
DELETE FROM teams;
DELETE FROM tournament_stages;
DELETE FROM tournaments;
DELETE FROM series_tournament_history;
DELETE FROM series_standings;
DELETE FROM partner_participants;
DELETE FROM series;
*/
