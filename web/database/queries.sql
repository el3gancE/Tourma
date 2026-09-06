-- ============================================================================
-- TOURMA - COMPLETE DATABASE QUERIES & CHEATSHEET (MS SQL SERVER)
-- Bộ truy vấn toàn diện cho cơ sở dữ liệu tourma_db
-- Tất cả các bảng và câu lệnh đã được chuẩn hóa 100% theo đúng Schema hiện tại.
-- ============================================================================

USE tourma_db;
GO

-- ============================================================================
-- PHẦN 1: ĐẾM SỐ LƯỢNG BẢN GHI TẤT CẢ 10 BẢNG (OVERVIEW & ROW COUNT)
-- ============================================================================

-- 1.1 Tổng số bản ghi trên toàn bộ 10 bảng trong hệ thống
SELECT '1. series' AS [Bảng], COUNT(*) AS [Tổng Số Dòng] FROM series
UNION ALL SELECT '2. partner_participants', COUNT(*) FROM partner_participants
UNION ALL SELECT '3. tournaments', COUNT(*) FROM tournaments
UNION ALL SELECT '4. tournament_stages', COUNT(*) FROM tournament_stages
UNION ALL SELECT '5. teams', COUNT(*) FROM teams
UNION ALL SELECT '6. groups', COUNT(*) FROM groups
UNION ALL SELECT '7. group_teams', COUNT(*) FROM group_teams
UNION ALL SELECT '8. matches', COUNT(*) FROM matches
UNION ALL SELECT '9. series_standings', COUNT(*) FROM series_standings
UNION ALL SELECT '10. series_tournament_history', COUNT(*) FROM series_tournament_history;
GO

-- 1.2 Thống kê tổng quan trạng thái hệ thống
SELECT 
    (SELECT COUNT(*) FROM series WHERE status = 'ACTIVE') AS [Series Đang Hoạt Động],
    (SELECT COUNT(*) FROM tournaments) AS [Tổng Số Giải Đấu],
    (SELECT COUNT(*) FROM tournaments WHERE series_id IS NOT NULL) AS [Giải Thuộc Series],
    (SELECT COUNT(*) FROM tournaments WHERE series_id IS NULL) AS [Giải Độc Lập],
    (SELECT COUNT(*) FROM teams) AS [Tổng Số Đội/VĐV Đăng Ký],
    (SELECT COUNT(*) FROM matches) AS [Tổng Số Trận Đấu Đã Tạo],
    (SELECT COUNT(*) FROM matches WHERE status IN ('FINISHED', 'COMPLETED')) AS [Trận Đã Kết Thúc],
    (SELECT COUNT(*) FROM matches WHERE status IN ('PENDING', 'READY', 'SCHEDULED')) AS [Trận Chưa Xong];
GO


-- ============================================================================
-- PHẦN 2: XEM NHANH TOÀN BỘ DỮ LIỆU (SELECT * FROM MỖI BẢNG)
-- Mỗi câu lệnh có phân tách riêng biệt, có thể bôi đen chạy từng câu hoặc chạy F5 toàn bộ
-- ============================================================================

-- 2.1 Bảng series: Chuỗi giải đấu / Mùa giải
SELECT * FROM series ORDER BY created_at DESC;
GO

-- 2.2 Bảng partner_participants: Danh sách VĐV / Đội Partner trong Series
SELECT * FROM partner_participants ORDER BY series_id, created_at ASC;
GO

-- 2.3 Bảng tournaments: Tất cả các giải đấu (độc lập & trong series)
SELECT * FROM tournaments ORDER BY created_at DESC;
GO

-- 2.4 Bảng tournament_stages: Các giai đoạn thi đấu (Vòng bảng, Single/Double Elim, Swiss...)
SELECT * FROM tournament_stages ORDER BY tournament_id, stage_order ASC;
GO

-- 2.5 Bảng teams: Danh sách Đội / VĐV tham gia từng giải đấu
SELECT * FROM teams ORDER BY tournament_id, original_seed ASC;
GO

-- 2.6 Bảng groups: Các bảng đấu (Bảng A, B, C, D...) trong Vòng bảng
SELECT * FROM groups ORDER BY stage_id, group_name ASC;
GO

-- 2.7 Bảng group_teams: Chi tiết BXH từng bảng đấu (Thắng, Hòa, Thua, Điểm, Hiệu số)
SELECT * FROM group_teams ORDER BY group_id, rank_in_group ASC, points DESC;
GO

-- 2.8 Bảng matches: Toàn bộ danh sách trận đấu và tỉ số
SELECT * FROM matches ORDER BY tournament_id, stage_id, round_number ASC, id ASC;
GO

-- 2.9 Bảng series_standings: Bảng xếp hạng tổng Series (Rolling Points & Elo)
SELECT * FROM series_standings ORDER BY series_id, rank_overall ASC;
GO

-- 2.10 Bảng series_tournament_history: Lịch sử thành tích từng giải con trong Series
SELECT * FROM series_tournament_history ORDER BY series_id, completed_at DESC, tournament_rank ASC;
GO


-- ============================================================================
-- PHẦN 3: XEM CHI TIẾT TỪNG BẢNG VỚI TÊN ĐẦY ĐỦ (JOIN HUMAN-READABLE)
-- ============================================================================

-- 3.1 Chi tiết Partner Participants kèm Tên Series
SELECT 
    p.id AS [Participant_ID],
    s.name AS [Series_Name],
    p.name AS [Tên VĐV / Đội],
    p.group_name AS [Nhóm / Hạng],
    p.created_at AS [Ngày Thêm]
FROM partner_participants p
LEFT JOIN series s ON p.series_id = s.id
ORDER BY s.name, p.name ASC;
GO

-- 3.2 Chi tiết Giải Đấu kèm Tên Series và Cấu Hình Điểm
SELECT 
    t.id AS [Tournament_ID],
    ISNULL(s.name, N'(Giải Độc Lập)') AS [Series_Name],
    t.name AS [Tên Giải],
    t.tournament_type AS [Thể Thức],
    t.tier_name AS [Tier],
    t.series_reward_points AS [Điểm P1 Vô Địch],
    t.advancing_seats_count AS [Số Vé Vào Stage 2],
    t.status AS [Trạng Thái],
    t.created_at AS [Ngày Tạo]
FROM tournaments t
LEFT JOIN series s ON t.series_id = s.id
ORDER BY t.created_at DESC;
GO

-- 3.3 Chi tiết Stages kèm Tên Giải Đấu
SELECT 
    ts.id AS [Stage_ID],
    t.name AS [Tên Giải],
    ts.stage_order AS [Thứ Tự Stage],
    ts.stage_name AS [Tên Giai Đoạn],
    ts.format AS [Định Dạng],
    ts.win_points AS [Điểm Thắng],
    ts.draw_points AS [Điểm Hòa],
    ts.loss_points AS [Điểm Thua],
    ts.status AS [Trạng Thái]
FROM tournament_stages ts
JOIN tournaments t ON ts.tournament_id = t.id
ORDER BY t.id, ts.stage_order ASC;
GO

-- 3.4 Chi tiết Danh Sách Đội Kèm Hạt Giống & Giải Đấu
SELECT 
    tm.id AS [Team_ID],
    t.name AS [Tên Giải],
    tm.raw_name AS [Tên Đội / VĐV],
    tm.original_seed AS [Hạt Giống (Seed)],
    tm.status AS [Trạng Thái],
    tm.created_at AS [Thời Gian Đăng Ký]
FROM teams tm
JOIN tournaments t ON tm.tournament_id = t.id
ORDER BY t.name, tm.original_seed ASC;
GO

-- 3.5 Chi tiết Bảng Xếp Hạng Vòng Bảng (Group Standings với Tên Đội Rõ Ràng)
SELECT 
    t.name AS [Giải Đấu],
    ts.stage_name AS [Giai Đoạn],
    g.group_name AS [Bảng],
    gt.rank_in_group AS [Hạng Trong Bảng],
    tm.raw_name AS [Tên Đội],
    gt.wins AS [Thắng],
    gt.draws AS [Hòa],
    gt.losses AS [Thua],
    gt.goals_scored AS [Bàn Thắng / Ván Thắng],
    gt.goals_conceded AS [Bàn Thua / Ván Thua],
    gt.goal_difference AS [Hiệu Số],
    gt.points AS [Điểm Số]
FROM group_teams gt
JOIN groups g ON gt.group_id = g.id
JOIN tournament_stages ts ON g.stage_id = ts.id
JOIN tournaments t ON ts.tournament_id = t.id
JOIN teams tm ON gt.team_id = tm.id
ORDER BY t.id, g.group_name ASC, gt.rank_in_group ASC, gt.points DESC;
GO

-- 3.6 Chi tiết Cây Thi Đấu (Matches Bracket với Tên Đội & Kết Quả)
SELECT 
    t.name AS [Giải Đấu],
    ts.stage_name AS [Giai Đoạn],
    m.bracket_type AS [Nhánh Đấu],
    m.round_number AS [Vòng],
    m.match_code AS [Mã Trận],
    ISNULL(t1.raw_name, N'Chờ xác định') AS [Đội 1],
    m.score1 AS [Tỉ Số 1],
    m.score2 AS [Tỉ Số 2],
    ISNULL(t2.raw_name, N'Chờ xác định') AS [Đội 2],
    ISNULL(w.raw_name, N'---') AS [Đội Thắng],
    m.status AS [Trạng Thái],
    m.next_match_id AS [Trận Kế Tiếp]
FROM matches m
JOIN tournaments t ON m.tournament_id = t.id
JOIN tournament_stages ts ON m.stage_id = ts.id
LEFT JOIN teams t1 ON m.team1_id = t1.id
LEFT JOIN teams t2 ON m.team2_id = t2.id
LEFT JOIN teams w ON m.winner_id = w.id
ORDER BY t.id, ts.stage_order ASC, m.round_number ASC, m.id ASC;
GO

-- 3.7 Bảng Xếp Hạng Series Chi Tiết (Series Standings)
SELECT 
    s.name AS [Series],
    ss.rank_overall AS [Hạng Tổng],
    ss.normalized_team_name AS [Tên Đội / VĐV],
    ss.total_rolling_points AS [Điểm Tích Lũy (Rolling)],
    ss.current_elo AS [Điểm Elo],
    ss.matches_played AS [Số Trận Đã Đấu],
    ss.wins AS [Thắng],
    ss.losses AS [Thua],
    ss.updated_at AS [Cập Nhật Lúc]
FROM series_standings ss
JOIN series s ON ss.series_id = s.id
ORDER BY s.id, ss.rank_overall ASC;
GO

-- 3.8 Lịch Sử Nhận Điểm Từng Giải Trong Series (Series Tournament History)
SELECT 
    s.name AS [Series],
    t.name AS [Tên Giải Đấu],
    sth.normalized_team_name AS [Tên Đội / VĐV],
    sth.tournament_rank AS [Thứ Hạng Đạt Được],
    sth.points_earned AS [Điểm Thưởng Nhận Được],
    sth.points_deducted AS [Điểm Bị Khấu Trừ],
    sth.elo_change AS [Thay Đổi Elo],
    sth.completed_at AS [Thời Gian Kết Thúc]
FROM series_tournament_history sth
JOIN series s ON sth.series_id = s.id
JOIN tournaments t ON sth.tournament_id = t.id
ORDER BY s.id, sth.completed_at DESC, sth.tournament_rank ASC;
GO


-- ============================================================================
-- PHẦN 4: TRUY VẤN NGHIỆP VỤ & PHÂN TÍCH (BUSINESS & STATS QUERIES)
-- ============================================================================

-- 4.1 Bảng Vàng Thành Tích (Hall of Fame) - Thống kê số lần Vô địch & Tổng điểm
SELECT 
    sth.normalized_team_name AS [Tên Đội / VĐV],
    s.name AS [Series],
    SUM(CASE WHEN sth.tournament_rank = 1 THEN 1 ELSE 0 END) AS [Số Lần Vô Địch],
    SUM(CASE WHEN sth.tournament_rank = 2 THEN 1 ELSE 0 END) AS [Số Lần Á Quân],
    SUM(CASE WHEN sth.tournament_rank IN (3, 4) THEN 1 ELSE 0 END) AS [Số Lần Top 4],
    SUM(ISNULL(sth.points_earned, 0)) AS [Tổng Điểm Nhận Được],
    COUNT(*) AS [Tổng Số Giải Tham Gia]
FROM series_tournament_history sth
JOIN series s ON sth.series_id = s.id
GROUP BY sth.normalized_team_name, s.name
ORDER BY [Số Lần Vô Địch] DESC, [Tổng Điểm Nhận Được] DESC;
GO

-- 4.2 Cấu Hình Điểm Thưởng (Points Config JSON) của các giải con trong Series
SELECT 
    s.name AS [Series],
    t.id AS [Tournament_ID],
    t.name AS [Tên Giải],
    t.tier_name AS [Tier],
    t.series_reward_points AS [Điểm P1],
    t.series_points_config AS [Cấu Hình Điểm JSON],
    t.status AS [Trạng Thái]
FROM tournaments t
JOIN series s ON t.series_id = s.id
ORDER BY s.id, t.tournament_index_in_series ASC;
GO

-- 4.3 Danh sách các đội đi tiếp vào Vòng 2 (Multi-Stage Stage 2 Teams)
SELECT 
    t.id AS [Tournament_ID],
    t.name AS [Tên Giải],
    t.tournament_type AS [Thể Thức],
    t.advancing_seats_count AS [Số Vé Vào Stage 2],
    t.multi_stage_config AS [Cấu Hình Multi-Stage],
    t.stage2_teams AS [Danh Sách Đội Stage 2 (JSON)]
FROM tournaments t
WHERE t.tournament_type = 'MULTI_STAGE' OR t.stage2_teams IS NOT NULL;
GO

-- 4.4 Tra cứu lịch sử đối đầu (Head-to-Head) giữa 2 đội bất kỳ (Ví dụ: 'Jayson Shaw' vs 'Fedor Gorst')
SELECT 
    t.name AS [Giải Đấu],
    ts.stage_name AS [Giai Đoạn],
    t1.raw_name AS [Đội 1],
    m.score1 AS [Tỉ Số 1],
    m.score2 AS [Tỉ Số 2],
    t2.raw_name AS [Đội 2],
    w.raw_name AS [Đội Thắng],
    m.created_at AS [Thời Gian]
FROM matches m
JOIN tournaments t ON m.tournament_id = t.id
JOIN tournament_stages ts ON m.stage_id = ts.id
LEFT JOIN teams t1 ON m.team1_id = t1.id
LEFT JOIN teams t2 ON m.team2_id = t2.id
LEFT JOIN teams w ON m.winner_id = w.id
WHERE (t1.raw_name LIKE N'%Shaw%' AND t2.raw_name LIKE N'%Gorst%')
   OR (t1.raw_name LIKE N'%Gorst%' AND t2.raw_name LIKE N'%Shaw%')
ORDER BY m.created_at DESC;
GO


-- ============================================================================
-- PHẦN 5: LỆNH DỌN DẸP & RESET DỮ LIỆU THỬ NGHIỆM (CLEANUP SCRIPTS)
-- Lưu ý: Mở comment /* ... */ khi muốn thực hiện xóa
-- ============================================================================

-- 5.1 Chỉ xóa kết quả trận đấu và bảng xếp hạng (Giữ nguyên cấu hình giải đấu & danh sách VĐV)
/*
DELETE FROM matches;
DELETE FROM group_teams;
DELETE FROM series_tournament_history;
DELETE FROM series_standings;
UPDATE tournaments SET status = 'DRAFT';
UPDATE tournament_stages SET status = 'PENDING';
*/
GO

-- 5.2 Xóa sạch 100% cơ sở dữ liệu để kiểm thử tạo mới toàn bộ (Theo đúng thứ tự Foreign Key)
/*
DELETE FROM matches;
DELETE FROM group_teams;
DELETE FROM groups;
DELETE FROM teams;
DELETE FROM tournament_stages;
DELETE FROM series_tournament_history;
DELETE FROM series_standings;
DELETE FROM tournaments;
DELETE FROM partner_participants;
DELETE FROM series;
*/
GO
