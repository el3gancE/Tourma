-- ============================================================================
-- TOURMA - COMPLETE DATABASE QUERIES & CHEATSHEET (MICROSOFT SQL SERVER)
-- File: queries.sql
-- Bộ truy vấn toàn diện cho cơ sở dữ liệu tourma (13 bảng chuẩn hóa)
-- Hỗ trợ tất cả các thể thức: Single/Double Elimination, Round Robin, Swiss,
-- Group Stage, Multi-Stage, Series Rolling Window, FIFA Elo & League System.
-- ============================================================================

USE tourma;
GO

-- ============================================================================
-- PHẦN 1: TỔNG QUAN HỆ THỐNG & ĐẾM BẢN GHI (OVERVIEW & ROW COUNT)
-- ============================================================================

-- 1.1 Tổng số bản ghi trên toàn bộ 13 bảng trong hệ thống
SELECT '1. series' AS [Bảng], COUNT(*) AS [Tổng Số Dòng] FROM series
UNION ALL SELECT '2. partner_participants', COUNT(*) FROM partner_participants
UNION ALL SELECT '3. series_point_rules', COUNT(*) FROM series_point_rules
UNION ALL SELECT '4. tournaments', COUNT(*) FROM tournaments
UNION ALL SELECT '5. tournament_placement_points', COUNT(*) FROM tournament_placement_points
UNION ALL SELECT '6. tournament_stages', COUNT(*) FROM tournament_stages
UNION ALL SELECT '7. teams', COUNT(*) FROM teams
UNION ALL SELECT '8. stage_participants', COUNT(*) FROM stage_participants
UNION ALL SELECT '9. groups', COUNT(*) FROM groups
UNION ALL SELECT '10. group_teams', COUNT(*) FROM group_teams
UNION ALL SELECT '11. matches', COUNT(*) FROM matches
UNION ALL SELECT '12. series_standings', COUNT(*) FROM series_standings
UNION ALL SELECT '13. series_tournament_history', COUNT(*) FROM series_tournament_history;
GO

-- 1.2 Thống kê trạng thái giải đấu & trận đấu
SELECT 
    (SELECT COUNT(*) FROM series WHERE status = 'ACTIVE') AS [Series Đang Chạy],
    (SELECT COUNT(*) FROM tournaments) AS [Tổng Số Giải Đấu],
    (SELECT COUNT(*) FROM tournaments WHERE series_id IS NOT NULL) AS [Giải Thuộc Series],
    (SELECT COUNT(*) FROM tournaments WHERE series_id IS NULL) AS [Giải Độc Lập],
    (SELECT COUNT(*) FROM teams) AS [Tổng Số Đội Đăng Ký],
    (SELECT COUNT(*) FROM matches) AS [Tổng Số Trận Đã Tạo],
    (SELECT COUNT(*) FROM matches WHERE status = 'FINISHED') AS [Trận Đã Kết Thúc],
    (SELECT COUNT(*) FROM matches WHERE status IN ('PENDING', 'READY', 'IN_PROGRESS')) AS [Trận Chưa Xong];
GO


-- ============================================================================
-- PHẦN 2: TRUY VẤN XEM NHANH DỮ LIỆU TỪNG BẢNG (SELECT * CHEATSHEET)
-- ============================================================================

-- 2.1 Bảng series: Chuỗi giải đấu / Mùa giải
SELECT * FROM series ORDER BY created_at DESC;
GO

-- 2.2 Bảng partner_participants: Thành viên / Đội cố định trong Series
SELECT * FROM partner_participants ORDER BY series_id, name ASC;
GO

-- 2.3 Bảng series_point_rules: Cấu hình điểm thưởng theo Tier & Vị trí
SELECT * FROM series_point_rules ORDER BY series_id, tier_name, rank_position ASC;
GO

-- 2.4 Bảng tournaments: Tất cả các giải đấu
SELECT * FROM tournaments ORDER BY created_at DESC;
GO

-- 2.5 Bảng tournament_placement_points: Điểm thưởng tùy biến của từng giải
SELECT * FROM tournament_placement_points ORDER BY tournament_id, rank_position ASC;
GO

-- 2.6 Bảng tournament_stages: Các giai đoạn thi đấu trong giải
SELECT * FROM tournament_stages ORDER BY tournament_id, stage_order ASC;
GO

-- 2.7 Bảng teams: Danh sách Đội tham gia từng giải
SELECT * FROM teams ORDER BY tournament_id, original_seed ASC;
GO

-- 2.8 Bảng stage_participants: Danh sách đội tham gia / vượt qua từng Stage
SELECT * FROM stage_participants ORDER BY tournament_id, stage_id, seed_in_stage ASC;
GO

-- 2.9 Bảng groups: Các bảng đấu (Bảng A, B, C...)
SELECT * FROM groups ORDER BY stage_id, group_name ASC;
GO

-- 2.10 Bảng group_teams: Chi tiết BXH từng bảng đấu
SELECT * FROM group_teams ORDER BY group_id, rank_in_group ASC, points DESC;
GO

-- 2.11 Bảng matches: Toàn bộ danh sách trận đấu, tỉ số, luân lưu và nhánh
SELECT * FROM matches ORDER BY tournament_id, stage_id, round_number ASC, match_order ASC;
GO

-- 2.12 Bảng series_standings: Bảng xếp hạng tổng hợp Series (Rolling, Elo, League)
SELECT * FROM series_standings ORDER BY series_id, phase_number, rank_overall ASC;
GO

-- 2.13 Bảng series_tournament_history: Lịch sử điểm cộng/trừ từng giải trong Series
SELECT * FROM series_tournament_history ORDER BY series_id, completed_at DESC, tournament_rank ASC;
GO


-- ============================================================================
-- PHẦN 3: TRUY VẤN CHI TIẾT THEO TỪNG THỂ THỨC THI ĐẤU
-- ============================================================================

-- 3.1 THỂ THỨC SINGLE ELIMINATION: Xem cây nhánh đấu, tỉ số và đội thắng
SELECT 
    t.name AS [Giải Đấu],
    ts.stage_name AS [Giai Đoạn],
    m.round_number AS [Vòng],
    m.match_code AS [Mã Trận],
    ISNULL(t1.raw_name, N'Chờ xác định') AS [Đội 1],
    m.score1 AS [Điểm 1],
    m.penalty1 AS [Pen 1],
    m.score2 AS [Điểm 2],
    m.penalty2 AS [Pen 2],
    ISNULL(t2.raw_name, N'Chờ xác định') AS [Đội 2],
    ISNULL(tw.raw_name, N'---') AS [Đội Thắng],
    m.status AS [Trạng Thái],
    m.next_match_id AS [Trận Kế Tiếp]
FROM matches m
JOIN tournaments t ON m.tournament_id = t.id
JOIN tournament_stages ts ON m.stage_id = ts.id
LEFT JOIN teams t1 ON m.team1_id = t1.id
LEFT JOIN teams t2 ON m.team2_id = t2.id
LEFT JOIN teams tw ON m.winner_id = tw.id
WHERE ts.format = 'SINGLE_ELIMINATION'
ORDER BY t.id, m.round_number ASC, m.match_order ASC;
GO

-- 3.2 THỂ THỨC DOUBLE ELIMINATION: Xem Nhánh Thắng (Upper), Nhánh Thua (Lower) & Chung kết
SELECT 
    t.name AS [Giải Đấu],
    m.bracket_type AS [Nhánh Đấu],
    m.round_number AS [Vòng],
    m.match_code AS [Mã Trận],
    ISNULL(t1.raw_name, N'Chờ xác định') AS [Đội 1],
    m.score1 AS [Tỉ Số 1],
    m.score2 AS [Tỉ Số 2],
    ISNULL(t2.raw_name, N'Chờ xác định') AS [Đội 2],
    ISNULL(tw.raw_name, N'---') AS [Thắng (Vào Next)],
    m.next_match_id AS [Trận Thắng Kế],
    ISNULL(tl.raw_name, N'---') AS [Thua (Rơi Nhánh)],
    m.loser_next_match_id AS [Trận Thua Rơi Xuống],
    m.status AS [Trạng Thái]
FROM matches m
JOIN tournaments t ON m.tournament_id = t.id
JOIN tournament_stages ts ON m.stage_id = ts.id
LEFT JOIN teams t1 ON m.team1_id = t1.id
LEFT JOIN teams t2 ON m.team2_id = t2.id
LEFT JOIN teams tw ON m.winner_id = tw.id
LEFT JOIN teams tl ON m.loser_id = tl.id
WHERE ts.format = 'DOUBLE_ELIMINATION'
ORDER BY t.id, 
         CASE m.bracket_type 
             WHEN 'WINNER_BRACKET' THEN 1 
             WHEN 'LOSER_BRACKET' THEN 2 
             WHEN 'GRAND_FINAL' THEN 3 
             ELSE 4 
         END, 
         m.round_number ASC, m.match_order ASC;
GO

-- 3.3 THỂ THỨC ROUND ROBIN: Bảng xếp hạng Vòng Tròn đầy đủ tiêu chí
SELECT 
    t.name AS [Giải Đấu],
    ts.stage_name AS [Giai Đoạn],
    tm.raw_name AS [Đội Bóng],
    tm.original_seed AS [Hạt Giống Gốc],
    COUNT(CASE WHEN m.status = 'FINISHED' AND (m.team1_id = tm.id OR m.team2_id = tm.id) THEN 1 END) AS [Trận Đã Đấu],
    SUM(CASE WHEN m.winner_id = tm.id THEN 1 ELSE 0 END) AS [Thắng],
    SUM(CASE WHEN m.status = 'FINISHED' AND m.score1 = m.score2 AND (m.team1_id = tm.id OR m.team2_id = tm.id) THEN 1 ELSE 0 END) AS [Hòa],
    SUM(CASE WHEN m.status = 'FINISHED' AND m.winner_id IS NOT NULL AND m.winner_id != tm.id AND (m.team1_id = tm.id OR m.team2_id = tm.id) THEN 1 ELSE 0 END) AS [Thua],
    SUM(CASE WHEN m.team1_id = tm.id THEN ISNULL(m.score1, 0) WHEN m.team2_id = tm.id THEN ISNULL(m.score2, 0) ELSE 0 END) AS [Bàn Thắng],
    SUM(CASE WHEN m.team1_id = tm.id THEN ISNULL(m.score2, 0) WHEN m.team2_id = tm.id THEN ISNULL(m.score1, 0) ELSE 0 END) AS [Bàn Thua],
    (SUM(CASE WHEN m.team1_id = tm.id THEN ISNULL(m.score1, 0) WHEN m.team2_id = tm.id THEN ISNULL(m.score2, 0) ELSE 0 END) -
     SUM(CASE WHEN m.team1_id = tm.id THEN ISNULL(m.score2, 0) WHEN m.team2_id = tm.id THEN ISNULL(m.score1, 0) ELSE 0 END)) AS [Hiệu Số],
    (SUM(CASE WHEN m.winner_id = tm.id THEN ts.win_points ELSE 0 END) +
     SUM(CASE WHEN m.status = 'FINISHED' AND m.score1 = m.score2 AND (m.team1_id = tm.id OR m.team2_id = tm.id) THEN ts.draw_points ELSE 0 END)) AS [Điểm Số]
FROM teams tm
JOIN tournaments t ON tm.tournament_id = t.id
JOIN tournament_stages ts ON ts.tournament_id = t.id
LEFT JOIN matches m ON m.stage_id = ts.id AND (m.team1_id = tm.id OR m.team2_id = tm.id)
WHERE ts.format = 'ROUND_ROBIN'
GROUP BY t.id, t.name, ts.stage_name, ts.win_points, ts.draw_points, tm.id, tm.raw_name, tm.original_seed
ORDER BY [Điểm Số] DESC, [Hiệu Số] DESC, [Bàn Thắng] DESC;
GO

-- 3.4 THỂ THỨC GROUP STAGE: Bảng xếp hạng chi tiết từng bảng đấu (Bảng A, B, C, D...)
SELECT 
    t.name AS [Giải Đấu],
    g.group_name AS [Bảng Đấu],
    gt.rank_in_group AS [Hạng],
    tm.raw_name AS [Tên Đội],
    gt.matches_played AS [Số Trận],
    gt.wins AS [T],
    gt.draws AS [H],
    gt.losses AS [B],
    gt.goals_scored AS [BT],
    gt.goals_conceded AS [BB],
    gt.goal_difference AS [HS],
    gt.points AS [Điểm],
    CASE WHEN gt.rank_in_group <= g.qualified_slots_count THEN N'Vé Trực Tiếp' ELSE N'Xét Vé Vớt / Loại' END AS [Tình Trạng Đi Tiếp]
FROM group_teams gt
JOIN groups g ON gt.group_id = g.id
JOIN tournament_stages ts ON g.stage_id = ts.id
JOIN tournaments t ON ts.tournament_id = t.id
JOIN teams tm ON gt.team_id = tm.id
ORDER BY t.id, g.group_name ASC, gt.rank_in_group ASC, gt.points DESC;
GO

-- 3.5 THỂ THỨC SWISS SYSTEM: Thống kê hiệu số Thắng - Thua từng đội qua 5 vòng
SELECT 
    t.name AS [Giải Đấu],
    tm.raw_name AS [Tên Đội],
    tm.original_seed AS [Hạt Giống],
    SUM(CASE WHEN m.winner_id = tm.id THEN 1 ELSE 0 END) AS [Số Trận Thắng],
    SUM(CASE WHEN m.status = 'FINISHED' AND m.winner_id != tm.id AND (m.team1_id = tm.id OR m.team2_id = tm.id) THEN 1 ELSE 0 END) AS [Số Trận Thua],
    CASE 
        WHEN SUM(CASE WHEN m.winner_id = tm.id THEN 1 ELSE 0 END) >= 3 THEN N'ĐI TIẾP (3 Thắng)'
        WHEN SUM(CASE WHEN m.status = 'FINISHED' AND m.winner_id != tm.id AND (m.team1_id = tm.id OR m.team2_id = tm.id) THEN 1 ELSE 0 END) >= 3 THEN N'BỊ LOẠI (3 Thua)'
        ELSE N'Đang thi đấu'
    END AS [Trạng Thái Swiss]
FROM teams tm
JOIN tournaments t ON tm.tournament_id = t.id
JOIN tournament_stages ts ON ts.tournament_id = t.id
LEFT JOIN matches m ON m.stage_id = ts.id AND (m.team1_id = tm.id OR m.team2_id = tm.id)
WHERE ts.format = 'SWISS_LITE'
GROUP BY t.id, t.name, tm.id, tm.raw_name, tm.original_seed
ORDER BY [Số Trận Thắng] DESC, [Số Trận Thua] ASC, tm.original_seed ASC;
GO

-- 3.6 TIẾN TRÌNH MULTI-STAGE: Danh sách đội hạt giống tiến vào Stage 2
SELECT 
    t.name AS [Giải Đấu],
    ts.stage_name AS [Giai Đoạn Đích (Stage 2)],
    sp.seed_in_stage AS [Hạt Giống Stage 2],
    tm.raw_name AS [Tên Đội],
    sp.qualification_source AS [Nguồn Suất Vé],
    sp.status AS [Trạng Thái]
FROM stage_participants sp
JOIN tournament_stages ts ON sp.stage_id = ts.id
JOIN tournaments t ON sp.tournament_id = t.id
JOIN teams tm ON sp.team_id = tm.id
ORDER BY t.name, sp.seed_in_stage ASC;
GO


-- ============================================================================
-- PHẦN 4: TRUY VẤN SERIES (ROLLING WINDOW, FIFA ELO & LEAGUE SYSTEM)
-- ============================================================================

-- 4.1 Bảng xếp hạng Series Rolling Window (Cửa sổ trượt W=10)
SELECT 
    s.name AS [Tên Series],
    ss.phase_number AS [Phase],
    ss.rank_overall AS [Hạng],
    ss.normalized_team_name AS [Đội / VĐV],
    ss.total_rolling_points AS [Điểm Rolling (10 Giải Gần Nhất)],
    ss.current_elo AS [Điểm Elo],
    ss.updated_at AS [Cập Nhật Lúc]
FROM series_standings ss
JOIN series s ON ss.series_id = s.id
WHERE s.ranking_model = 'ROLLING_WINDOW'
ORDER BY s.id, ss.phase_number DESC, ss.rank_overall ASC;
GO

-- 4.2 Lịch sử Điểm cộng & Điểm trừ trượt từng giải (Rolling Window History)
SELECT 
    s.name AS [Series],
    t.tournament_index_in_series AS [Giải Thứ #],
    t.name AS [Tên Giải Đấu],
    t.tier_name AS [Tier],
    sth.normalized_team_name AS [Tên Đội],
    sth.tournament_rank AS [Thứ Hạng Đạt Được],
    sth.points_earned AS [Điểm Cộng (+) Giải Này],
    sth.points_deducted AS [Điểm Trừ (-) Cửa Sổ Trượt],
    sth.elo_change AS [Biến Động Elo],
    sth.completed_at AS [Ngày Hoàn Thành]
FROM series_tournament_history sth
JOIN series s ON sth.series_id = s.id
JOIN tournaments t ON sth.tournament_id = t.id
ORDER BY s.id, t.tournament_index_in_series DESC, sth.tournament_rank ASC;
GO

-- 4.3 Bảng xếp hạng FIFA Elo Ranking (Phân nhóm Partner & Trọng số)
SELECT 
    s.name AS [Series Elo],
    p.group_name AS [Nhóm Partner],
    ss.rank_in_group AS [Hạng Nhóm],
    ss.rank_overall AS [Hạng Tổng],
    ss.normalized_team_name AS [Tên VĐV / Đội],
    ss.current_elo AS [Elo Hiện Tại],
    ss.matches_played AS [Số Trận],
    ss.wins AS [Thắng],
    ss.losses AS [Thua]
FROM series_standings ss
JOIN series s ON ss.series_id = s.id
LEFT JOIN partner_participants p ON ss.partner_participant_id = p.id
WHERE s.ranking_model = 'FIFA_ELO'
ORDER BY s.id, p.group_name ASC, ss.current_elo DESC;
GO

-- 4.4 Bảng Vàng Thành Tích (Hall of Fame) - Thống kê Cúp Vô Địch & Danh hiệu
SELECT 
    sth.normalized_team_name AS [Tên Đội / VĐV],
    COUNT(DISTINCT sth.series_id) AS [Số Series Tham Gia],
    COUNT(DISTINCT sth.tournament_id) AS [Tổng Số Giải Đấu],
    SUM(CASE WHEN sth.tournament_rank = 1 THEN 1 ELSE 0 END) AS [Số Cúp Vô Địch],
    SUM(CASE WHEN sth.tournament_rank = 2 THEN 1 ELSE 0 END) AS [Số Lần Á Quân],
    SUM(CASE WHEN sth.tournament_rank IN (3, 4) THEN 1 ELSE 0 END) AS [Số Lần Top 4],
    SUM(ISNULL(sth.points_earned, 0)) AS [Tổng Điểm Thưởng Tích Lũy]
FROM series_tournament_history sth
GROUP BY sth.normalized_team_name
ORDER BY [Số Cúp Vô Địch] DESC, [Số Lần Á Quân] DESC, [Tổng Điểm Thưởng Tích Lũy] DESC;
GO

-- 4.5 Hồ sơ Đội bóng (Team Profile) - Tra cứu toàn bộ lịch sử thi đấu của 1 đội
-- Thay thế 'ha noi fc' bằng tên đội cần tra cứu
DECLARE @SearchTeam NVARCHAR(255) = N'ha noi fc';

SELECT 
    t.name AS [Giải Đấu],
    ISNULL(s.name, N'Giải Đơn') AS [Series],
    ts.stage_name AS [Giai Đoạn],
    m.match_code AS [Trận Đấu],
    CASE WHEN m.team1_id = tm.id THEN t2.raw_name ELSE t1.raw_name END AS [Đối Thủ],
    CASE WHEN m.team1_id = tm.id THEN m.score1 ELSE m.score2 END AS [Bàn Thắng Của Đội],
    CASE WHEN m.team1_id = tm.id THEN m.score2 ELSE m.score1 END AS [Bàn Thua],
    CASE 
        WHEN m.winner_id = tm.id THEN N'THẮNG'
        WHEN m.status = 'FINISHED' AND m.score1 = m.score2 THEN N'HÒA'
        WHEN m.status = 'FINISHED' THEN N'THUA'
        ELSE N'CHƯA ĐẤU'
    END AS [Kết Quả]
FROM matches m
JOIN teams tm ON (m.team1_id = tm.id OR m.team2_id = tm.id)
JOIN tournaments t ON m.tournament_id = t.id
JOIN tournament_stages ts ON m.stage_id = ts.id
LEFT JOIN series s ON t.series_id = s.id
LEFT JOIN teams t1 ON m.team1_id = t1.id
LEFT JOIN teams t2 ON m.team2_id = t2.id
WHERE tm.normalized_name = @SearchTeam OR tm.raw_name = @SearchTeam
ORDER BY m.created_at DESC;
GO


-- ============================================================================
-- PHẦN 5: KIỂM TRA TÍNH TOÀN VẸN & DỌN DẸP DỮ LIỆU (INTEGRITY & CLEANUP)
-- ============================================================================

-- 5.1 Kiểm tra các trận đấu thiếu liên kết đội hoặc lỗi logic
SELECT 
    m.id AS [Match_ID],
    t.name AS [Giải Đấu],
    m.match_code AS [Mã Trận],
    m.round_number AS [Vòng],
    m.status AS [Trạng Thái],
    CASE 
        WHEN m.status = 'FINISHED' AND m.score1 IS NULL THEN N'Lỗi: Finished nhưng thiếu tỉ số 1'
        WHEN m.status = 'FINISHED' AND m.score2 IS NULL THEN N'Lỗi: Finished nhưng thiếu tỉ số 2'
        WHEN m.status = 'FINISHED' AND m.score1 != m.score2 AND m.winner_id IS NULL THEN N'Lỗi: Có tỉ số nhưng thiếu winner_id'
        ELSE N'Hợp Lệ'
    END AS [Kiểm Tra Logic]
FROM matches m
JOIN tournaments t ON m.tournament_id = t.id
WHERE m.status = 'FINISHED' AND (m.score1 IS NULL OR m.score2 IS NULL OR (m.score1 != m.score2 AND m.winner_id IS NULL));
GO

-- 5.2 Lệnh dọn dẹp sạch toàn bộ dữ liệu kiểm thử (Theo đúng thứ tự khóa ngoại)
/*
DELETE FROM matches;
DELETE FROM group_teams;
DELETE FROM groups;
DELETE FROM stage_participants;
DELETE FROM tournament_placement_points;
DELETE FROM teams;
DELETE FROM tournament_stages;
DELETE FROM series_tournament_history;
DELETE FROM series_standings;
DELETE FROM tournaments;
DELETE FROM series_point_rules;
DELETE FROM partner_participants;
DELETE FROM series;
*/
GO
