-- ============================================================================
-- TOURMA - COMPLETE DATABASE QUERIES & CHEATSHEET (MICROSOFT SQL SERVER / T-SQL)
-- File: queries.sql
-- Mô tả: Bộ truy vấn toàn diện cho hệ thống quản trị giải đấu Tourma
-- Hỗ trợ: Single/Multi-Stage, Single/Double Elimination, Round Robin, Swiss,
--         Group Stage, Series Rolling Window, FIFA Elo Ranking & League System.
-- ============================================================================

USE tourma; -- Đổi thành 'USE tourma_db;' nếu database của bạn đặt tên là tourma_db
GO

-- ============================================================================
-- PHẦN 1: TỔNG QUAN HỆ THỐNG & DASHBOARD THỐNG KÊ (OVERVIEW & HEALTH CHECK)
-- ============================================================================

-- 1.1 Tổng số bản ghi trên toàn bộ 13 bảng trong hệ thống
SELECT '1. series' AS [Tên Bảng], COUNT(*) AS [Tổng Số Dòng] FROM series
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

-- 1.2 Thống kê tổng hợp số lượng giải đấu, đội và trạng thái trận đấu
SELECT 
    (SELECT COUNT(*) FROM series WHERE status = 'ACTIVE') AS [Series Đang Hoạt Động],
    (SELECT COUNT(*) FROM tournaments) AS [Tổng Số Giải Đấu],
    (SELECT COUNT(*) FROM tournaments WHERE status = 'COMPLETED') AS [Giải Đã Hoàn Thành],
    (SELECT COUNT(*) FROM tournaments WHERE status = 'ONGOING') AS [Giải Đang Diễn Ra],
    (SELECT COUNT(*) FROM tournaments WHERE status = 'DRAFT') AS [Giải Nháp/Chưa Bắt Đầu],
    (SELECT COUNT(*) FROM teams) AS [Tổng Đội Đăng Ký],
    (SELECT COUNT(*) FROM matches) AS [Tổng Số Trận Đã Tạo],
    (SELECT COUNT(*) FROM matches WHERE status = 'FINISHED') AS [Trận Đã Kết Thúc],
    (SELECT COUNT(*) FROM matches WHERE status IN ('PENDING', 'READY', 'IN_PROGRESS')) AS [Trận Chưa Xong];
GO

-- 1.3 Dashboard danh sách các giải đấu: Thể thức, Số đội, Tiến độ trận đấu & Nhà vô địch
SELECT 
    t.id AS [Mã Giải],
    t.name AS [Tên Giải Đấu],
    ISNULL(s.name, N'Giải Độc Lập') AS [Thuộc Series],
    t.tournament_type AS [Loại Giải],
    ts.format AS [Thể Thức Thi Đấu],
    t.status AS [Trạng Thái Giải],
    ISNULL(t.champion_name, N'Chưa xác định') AS [Nhà Vô Địch],
    COUNT(DISTINCT tm.id) AS [Số Đội],
    COUNT(DISTINCT m.id) AS [Tổng Số Trận],
    SUM(CASE WHEN m.status = 'FINISHED' THEN 1 ELSE 0 END) AS [Trận Đã Đấu],
    CAST(ROUND(CASE WHEN COUNT(m.id) > 0 
         THEN (CAST(SUM(CASE WHEN m.status = 'FINISHED' THEN 1 ELSE 0 END) AS FLOAT) / COUNT(m.id)) * 100 
         ELSE 0 END, 1) AS NVARCHAR(10)) + '%' AS [Tiến Độ Hoàn Thành],
    t.created_at AS [Ngày Tạo]
FROM tournaments t
LEFT JOIN series s ON t.series_id = s.id
LEFT JOIN tournament_stages ts ON ts.tournament_id = t.id AND ts.stage_order = 1
LEFT JOIN teams tm ON tm.tournament_id = t.id
LEFT JOIN matches m ON m.tournament_id = t.id
GROUP BY t.id, t.name, s.name, t.tournament_type, ts.format, t.status, t.champion_name, t.created_at
ORDER BY t.created_at DESC;
GO


-- ============================================================================
-- PHẦN 2: TRUY VẤN XEM NHANH TỪNG BẢNG (SELECT * CHEATSHEET)
-- ============================================================================

-- 2.1 Bảng series: Chuỗi giải đấu / Mùa giải tích lũy điểm
SELECT * FROM series ORDER BY created_at DESC;
GO

-- 2.2 Bảng partner_participants: Thành viên cố định trong Series
SELECT * FROM partner_participants ORDER BY series_id, name ASC;
GO

-- 2.3 Bảng series_point_rules: Quy tắc điểm thưởng theo Tier (S, A, B, C, D) & Thứ hạng
SELECT * FROM series_point_rules ORDER BY series_id, tier_name, rank_position ASC;
GO

-- 2.4 Bảng tournaments: Danh sách tất cả các giải đấu
SELECT * FROM tournaments ORDER BY created_at DESC;
GO

-- 2.5 Bảng tournament_placement_points: Điểm thưởng tùy biến của từng giải
SELECT * FROM tournament_placement_points ORDER BY tournament_id, rank_position ASC;
GO

-- 2.6 Bảng tournament_stages: Các giai đoạn thi đấu (Stage 1, Stage 2...)
SELECT * FROM tournament_stages ORDER BY tournament_id, stage_order ASC;
GO

-- 2.7 Bảng teams: Danh sách Đội tham gia từng giải
SELECT * FROM teams ORDER BY tournament_id, original_seed ASC;
GO

-- 2.8 Bảng stage_participants: Danh sách đội tham gia / vượt qua từng Stage
SELECT * FROM stage_participants ORDER BY tournament_id, stage_id, seed_in_stage ASC;
GO

-- 2.9 Bảng groups: Các bảng đấu (Bảng A, B, C, D...)
SELECT * FROM groups ORDER BY stage_id, group_name ASC;
GO

-- 2.10 Bảng group_teams: Chi tiết BXH và hiệu số từng bảng đấu
SELECT * FROM group_teams ORDER BY group_id, rank_in_group ASC, points DESC;
GO

-- 2.11 Bảng matches: Toàn bộ danh sách trận đấu, tỉ số, penalty và cây nhánh đấu
SELECT * FROM matches ORDER BY tournament_id, stage_id, round_number ASC, match_order ASC;
GO

-- 2.12 Bảng series_standings: Bảng xếp hạng Series (Rolling Window, Elo, League)
SELECT * FROM series_standings ORDER BY series_id, phase_number DESC, rank_overall ASC;
GO

-- 2.13 Bảng series_tournament_history: Lịch sử cộng/trừ điểm trượt từng giải
SELECT * FROM series_tournament_history ORDER BY series_id, completed_at DESC, tournament_rank ASC;
GO


-- ============================================================================
-- PHẦN 3: TRUY VẤN XEM MATCHES & KẾT QUẢ THEO TỪNG THỂ THỨC THI ĐẤU
-- ============================================================================

-- 3.1 THỂ THỨC SINGLE ELIMINATION (LOẠI TRỰC TIẾP): Cây thi đấu, tỉ số & nhánh tiếp theo
SELECT 
    t.name AS [Giải Đấu],
    ts.stage_name AS [Giai Đoạn],
    m.round_number AS [Vòng Đấu],
    m.match_code AS [Mã Trận],
    ISNULL(t1.raw_name, N'Chờ xác định') AS [Đội 1],
    m.score1 AS [Điểm 1],
    m.penalty1 AS [Pen 1],
    m.score2 AS [Điểm 2],
    m.penalty2 AS [Pen 2],
    ISNULL(t2.raw_name, N'Chờ xác định') AS [Đội 2],
    ISNULL(tw.raw_name, N'---') AS [Đội Thắng],
    m.status AS [Trạng Thái],
    m.next_match_id AS [Mã Trận Kế Tiếp]
FROM matches m
JOIN tournaments t ON m.tournament_id = t.id
JOIN tournament_stages ts ON m.stage_id = ts.id
LEFT JOIN teams t1 ON m.team1_id = t1.id
LEFT JOIN teams t2 ON m.team2_id = t2.id
LEFT JOIN teams tw ON m.winner_id = tw.id
WHERE ts.format = 'SINGLE_ELIMINATION'
ORDER BY t.id, m.round_number ASC, m.match_order ASC;
GO

-- 3.2 THỂ THỨC DOUBLE ELIMINATION (NHÁNH THẮNG - NHÁNH THUA): Xem nhánh và đường rơi
SELECT 
    t.name AS [Giải Đấu],
    m.bracket_type AS [Nhánh Đấu],
    m.round_number AS [Vòng],
    m.match_code AS [Mã Trận],
    ISNULL(t1.raw_name, N'Chờ xác định') AS [Đội 1],
    m.score1 AS [Tỉ Số 1],
    m.score2 AS [Tỉ Số 2],
    ISNULL(t2.raw_name, N'Chờ xác định') AS [Đội 2],
    ISNULL(tw.raw_name, N'---') AS [Đội Thắng (Next)],
    m.next_match_id AS [Trận Thắng Kế Tiếp],
    ISNULL(tl.raw_name, N'---') AS [Đội Thua (Rơi Nhánh)],
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
             WHEN 'GRAND_FINAL_RESET' THEN 4
             ELSE 5 
         END, 
         m.round_number ASC, m.match_order ASC;
GO

-- 3.3 THỂ THỨC ROUND ROBIN (VÒNG TRÒN TÍNH ĐIỂM)
-- 3.3.1 Danh sách chi tiết các cặp đấu Vòng Tròn
SELECT 
    t.name AS [Giải Đấu],
    m.round_number AS [Lượt/Vòng],
    m.match_code AS [Mã Trận],
    ISNULL(t1.raw_name, N'Đội 1') AS [Đội Nhà],
    m.score1 AS [Điểm 1],
    m.score2 AS [Điểm 2],
    ISNULL(t2.raw_name, N'Đội 2') AS [Đội Khách],
    ISNULL(tw.raw_name, CASE WHEN m.status = 'FINISHED' AND m.score1 = m.score2 THEN N'HÒA' ELSE N'---' END) AS [Kết Quả],
    m.status AS [Trạng Thái]
FROM matches m
JOIN tournaments t ON m.tournament_id = t.id
JOIN tournament_stages ts ON m.stage_id = ts.id
LEFT JOIN teams t1 ON m.team1_id = t1.id
LEFT JOIN teams t2 ON m.team2_id = t2.id
LEFT JOIN teams tw ON m.winner_id = tw.id
WHERE ts.format = 'ROUND_ROBIN'
ORDER BY t.id, m.round_number ASC, m.match_order ASC;
GO

-- 3.3.2 Bảng Xếp Hạng Vòng Tròn Tính Điểm Động (Realtime Tính Từ Matches)
SELECT 
    t.name AS [Giải Đấu],
    tm.raw_name AS [Tên Đội],
    tm.original_seed AS [Hạt Giống Gốc],
    COUNT(CASE WHEN m.status = 'FINISHED' AND (m.team1_id = tm.id OR m.team2_id = tm.id) THEN 1 END) AS [Số Trận Đã Đấu],
    SUM(CASE WHEN m.winner_id = tm.id THEN 1 ELSE 0 END) AS [Thắng],
    SUM(CASE WHEN m.status = 'FINISHED' AND m.score1 = m.score2 AND (m.team1_id = tm.id OR m.team2_id = tm.id) THEN 1 ELSE 0 END) AS [Hòa],
    SUM(CASE WHEN m.status = 'FINISHED' AND m.winner_id IS NOT NULL AND m.winner_id != tm.id AND (m.team1_id = tm.id OR m.team2_id = tm.id) THEN 1 ELSE 0 END) AS [Thua],
    SUM(CASE WHEN m.team1_id = tm.id THEN ISNULL(m.score1, 0) WHEN m.team2_id = tm.id THEN ISNULL(m.score2, 0) ELSE 0 END) AS [Bàn Thắng],
    SUM(CASE WHEN m.team1_id = tm.id THEN ISNULL(m.score2, 0) WHEN m.team2_id = tm.id THEN ISNULL(m.score1, 0) ELSE 0 END) AS [Bàn Thua],
    (SUM(CASE WHEN m.team1_id = tm.id THEN ISNULL(m.score1, 0) WHEN m.team2_id = tm.id THEN ISNULL(m.score2, 0) ELSE 0 END) -
     SUM(CASE WHEN m.team1_id = tm.id THEN ISNULL(m.score2, 0) WHEN m.team2_id = tm.id THEN ISNULL(m.score1, 0) ELSE 0 END)) AS [Hiệu Số],
    (SUM(CASE WHEN m.winner_id = tm.id THEN ISNULL(ts.win_points, 3) ELSE 0 END) +
     SUM(CASE WHEN m.status = 'FINISHED' AND m.score1 = m.score2 AND (m.team1_id = tm.id OR m.team2_id = tm.id) THEN ISNULL(ts.draw_points, 1) ELSE 0 END)) AS [Điểm Số]
FROM teams tm
JOIN tournaments t ON tm.tournament_id = t.id
JOIN tournament_stages ts ON ts.tournament_id = t.id AND ts.format = 'ROUND_ROBIN'
LEFT JOIN matches m ON m.stage_id = ts.id AND (m.team1_id = tm.id OR m.team2_id = tm.id)
GROUP BY t.id, t.name, ts.win_points, ts.draw_points, tm.id, tm.raw_name, tm.original_seed
ORDER BY t.id, [Điểm Số] DESC, [Hiệu Số] DESC, [Bàn Thắng] DESC;
GO

-- 3.4 THỂ THỨC GROUP STAGE (CHIA BẢNG ĐẤU: BẢNG A, B, C, D...)
-- 3.4.1 Bảng xếp hạng chi tiết từng bảng đấu
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
    CASE WHEN gt.rank_in_group <= g.qualified_slots_count THEN N'Vé Trực Tiếp Vào Vòng Trong' 
         ELSE N'Xét Vé Vớt / Bị Loại' END AS [Tình Trạng Suất Vé]
FROM group_teams gt
JOIN groups g ON gt.group_id = g.id
JOIN tournament_stages ts ON g.stage_id = ts.id
JOIN tournaments t ON ts.tournament_id = t.id
JOIN teams tm ON gt.team_id = tm.id
ORDER BY t.id, g.group_name ASC, gt.rank_in_group ASC, gt.points DESC;
GO

-- 3.4.2 Danh sách các trận đấu vòng bảng
SELECT 
    t.name AS [Giải Đấu],
    g.group_name AS [Bảng Đấu],
    m.round_number AS [Lượt Trận],
    m.match_code AS [Mã Trận],
    ISNULL(t1.raw_name, N'Đội 1') AS [Đội 1],
    m.score1 AS [Tỉ Số 1],
    m.score2 AS [Tỉ Số 2],
    ISNULL(t2.raw_name, N'Đội 2') AS [Đội 2],
    ISNULL(tw.raw_name, CASE WHEN m.status = 'FINISHED' AND m.score1 = m.score2 THEN N'HÒA' ELSE N'---' END) AS [Kết Quả],
    m.status AS [Trạng Thái]
FROM matches m
JOIN tournaments t ON m.tournament_id = t.id
JOIN tournament_stages ts ON m.stage_id = ts.id
JOIN groups g ON m.group_id = g.id
LEFT JOIN teams t1 ON m.team1_id = t1.id
LEFT JOIN teams t2 ON m.team2_id = t2.id
LEFT JOIN teams tw ON m.winner_id = tw.id
WHERE ts.format = 'GROUP_STAGE'
ORDER BY t.id, g.group_name ASC, m.round_number ASC, m.match_order ASC;
GO

-- 3.5 THỂ THỨC SWISS SYSTEM (HỆ THỤY SĨ: 3-WINS ĐI TIẾP, 3-LOSSES BỊ LOẠI)
-- 3.5.1 Thống kê tiến độ Thắng/Thua và trạng thái đi tiếp của từng đội
SELECT 
    t.name AS [Giải Đấu],
    tm.raw_name AS [Tên Đội],
    tm.original_seed AS [Hạt Giống],
    SUM(CASE WHEN m.winner_id = tm.id THEN 1 ELSE 0 END) AS [Số Trận Thắng (W)],
    SUM(CASE WHEN m.status = 'FINISHED' AND m.winner_id != tm.id AND (m.team1_id = tm.id OR m.team2_id = tm.id) THEN 1 ELSE 0 END) AS [Số Trận Thua (L)],
    CASE 
        WHEN SUM(CASE WHEN m.winner_id = tm.id THEN 1 ELSE 0 END) >= ISNULL(ts.target_wins, 3) 
            THEN N' ĐI TIẾP (' + CAST(ISNULL(ts.target_wins, 3) AS NVARCHAR) + N' Thắng)'
        WHEN SUM(CASE WHEN m.status = 'FINISHED' AND m.winner_id != tm.id AND (m.team1_id = tm.id OR m.team2_id = tm.id) THEN 1 ELSE 0 END) >= ISNULL(ts.elimination_losses, 3) 
            THEN N'❌ BỊ LOẠI (' + CAST(ISNULL(ts.elimination_losses, 3) AS NVARCHAR) + N' Thua)'
        ELSE N'⏳ Đang thi đấu'
    END AS [Trạng Thái Swiss]
FROM teams tm
JOIN tournaments t ON tm.tournament_id = t.id
JOIN tournament_stages ts ON ts.tournament_id = t.id AND ts.format = 'SWISS_LITE'
LEFT JOIN matches m ON m.stage_id = ts.id AND (m.team1_id = tm.id OR m.team2_id = tm.id)
GROUP BY t.id, t.name, ts.target_wins, ts.elimination_losses, tm.id, tm.raw_name, tm.original_seed
ORDER BY t.id, [Số Trận Thắng (W)] DESC, [Số Trận Thua (L)] ASC, tm.original_seed ASC;
GO

-- 3.5.2 Danh sách tất cả các trận đấu Swiss theo nhóm điểm (Pool 0-0, 1-0, 0-1...)
SELECT 
    t.name AS [Giải Đấu],
    m.round_number AS [Vòng Swiss],
    m.match_code AS [Mã Trận / Nhóm Điểm],
    ISNULL(t1.raw_name, N'Đội 1') AS [Đội 1],
    m.score1 AS [Điểm 1],
    m.score2 AS [Điểm 2],
    ISNULL(t2.raw_name, N'Đội 2') AS [Đội 2],
    ISNULL(tw.raw_name, N'---') AS [Đội Thắng],
    m.status AS [Trạng Thái]
FROM matches m
JOIN tournaments t ON m.tournament_id = t.id
JOIN tournament_stages ts ON m.stage_id = ts.id
LEFT JOIN teams t1 ON m.team1_id = t1.id
LEFT JOIN teams t2 ON m.team2_id = t2.id
LEFT JOIN teams tw ON m.winner_id = tw.id
WHERE ts.format = 'SWISS_LITE'
ORDER BY t.id, m.round_number ASC, m.match_order ASC;
GO

-- 3.6 TIẾN TRÌNH MULTI-STAGE: Danh sách đội hạt giống tiến vào Stage 2
SELECT 
    t.name AS [Giải Đấu],
    ts.stage_name AS [Giai Đoạn Đích (Stage 2)],
    sp.seed_in_stage AS [Hạt Giống Stage 2],
    tm.raw_name AS [Tên Đội],
    sp.qualification_source AS [Nguồn Suất Vé (Vòng Bảng / Swiss)],
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

-- 4.1 Bảng xếp hạng Series Rolling Window (Cửa sổ trượt W=10 giải gần nhất)
SELECT 
    s.name AS [Tên Series],
    ss.phase_number AS [Phase],
    ss.rank_overall AS [Hạng Toàn Cục],
    ss.normalized_team_name AS [Đội / VĐV],
    ss.total_rolling_points AS [Điểm Rolling (10 Giải Gần Nhất)],
    ss.current_elo AS [Điểm Elo],
    ss.updated_at AS [Cập Nhật Lúc]
FROM series_standings ss
JOIN series s ON ss.series_id = s.id
WHERE s.ranking_model = 'ROLLING_WINDOW'
ORDER BY s.id, ss.phase_number DESC, ss.rank_overall ASC;
GO

-- 4.2 Lịch sử Điểm cộng (+) và Điểm trừ trượt (-) từng giải trong Rolling Series
SELECT 
    s.name AS [Series],
    t.tournament_index_in_series AS [Giải Thứ #],
    t.name AS [Tên Giải Đấu],
    t.tier_name AS [Tier],
    sth.normalized_team_name AS [Tên Đội],
    sth.tournament_rank AS [Thứ Hạng Đạt Được],
    sth.points_earned AS [Điểm Cộng (+) Giải Này],
    sth.points_deducted AS [Điểm Trừ (-) Cửa Sổ Trượt],
    sth.elo_change AS [Biến Động Elo (+/-)],
    sth.completed_at AS [Ngày Hoàn Thành]
FROM series_tournament_history sth
JOIN series s ON sth.series_id = s.id
JOIN tournaments t ON sth.tournament_id = t.id
ORDER BY s.id, t.tournament_index_in_series DESC, sth.tournament_rank ASC;
GO

-- 4.3 Bảng xếp hạng FIFA Elo Ranking (Phân nhóm Partner & Điểm Elo)
SELECT 
    s.name AS [Series Elo],
    ISNULL(p.group_name, N'Chung') AS [Nhóm Partner],
    ss.rank_in_group AS [Hạng Nhóm],
    ss.rank_overall AS [Hạng Tổng],
    ss.normalized_team_name AS [Tên VĐV / Đội],
    ss.current_elo AS [Elo Hiện Tại],
    ss.matches_played AS [Số Trận Đã Đấu],
    ss.wins AS [Thắng],
    ss.losses AS [Thua]
FROM series_standings ss
JOIN series s ON ss.series_id = s.id
LEFT JOIN partner_participants p ON ss.partner_participant_id = p.id
WHERE s.ranking_model = 'FIFA_ELO'
ORDER BY s.id, p.group_name ASC, ss.current_elo DESC;
GO

-- 4.4 Bảng xếp hạng League System (Hạng 1, Hạng 2, Thăng / Xuống Hạng)
SELECT 
    s.name AS [Tên Series],
    t.division_name AS [Hạng Đấu],
    ss.rank_overall AS [Vị Trí],
    ss.normalized_team_name AS [Tên Đội],
    ss.matches_played AS [Trận],
    ss.wins AS [T],
    ss.draws AS [H],
    ss.losses AS [B],
    ss.goals_for AS [BT],
    ss.goals_against AS [BB],
    ss.goal_diff AS [HS],
    ss.points AS [Điểm],
    ss.promotion_status AS [Trạng Thái Mùa Sau]
FROM series_standings ss
JOIN series s ON ss.series_id = s.id
LEFT JOIN tournaments t ON t.series_id = s.id AND t.division_level = ss.division_level
WHERE s.ranking_model = 'LEAGUE_SYSTEM'
ORDER BY ss.division_level ASC, ss.rank_overall ASC;
GO

-- 4.5 Bảng Vàng Thành Tích (Hall of Fame) - Thống kê Cúp Vô Địch & Danh hiệu toàn thời gian
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

-- 4.6 Hồ sơ Đội bóng (Team Profile / Match History) - Tra cứu toàn bộ lịch sử thi đấu của 1 đội
-- Gõ tên đội cần tìm vào biến @SearchTeam bên dưới:
DECLARE @SearchTeam NVARCHAR(255) = N'Team 1'; -- Thay tên đội bạn muốn tìm ở đây

SELECT 
    t.name AS [Giải Đấu],
    ISNULL(s.name, N'Giải Độc Lập') AS [Series],
    ts.stage_name AS [Giai Đoạn],
    m.match_code AS [Trận Đấu],
    CASE WHEN m.team1_id = tm.id THEN ISNULL(t2.raw_name, N'Chờ xác định') 
         ELSE ISNULL(t1.raw_name, N'Chờ xác định') END AS [Đối Thủ],
    CASE WHEN m.team1_id = tm.id THEN m.score1 ELSE m.score2 END AS [Bàn Thắng Của Đội],
    CASE WHEN m.team1_id = tm.id THEN m.score2 ELSE m.score1 END AS [Bàn Thua],
    CASE 
        WHEN m.winner_id = tm.id THEN N' THẮNG'
        WHEN m.status = 'FINISHED' AND m.score1 = m.score2 THEN N'⚖️ HÒA'
        WHEN m.status = 'FINISHED' THEN N'❌ THUA'
        ELSE N'⏳ CHƯA ĐẤU'
    END AS [Kết Quả Trận]
FROM matches m
JOIN teams tm ON (m.team1_id = tm.id OR m.team2_id = tm.id)
JOIN tournaments t ON m.tournament_id = t.id
JOIN tournament_stages ts ON m.stage_id = ts.id
LEFT JOIN series s ON t.series_id = s.id
LEFT JOIN teams t1 ON m.team1_id = t1.id
LEFT JOIN teams t2 ON m.team2_id = t2.id
WHERE tm.normalized_name = LOWER(LTRIM(RTRIM(@SearchTeam))) 
   OR tm.raw_name = @SearchTeam
ORDER BY m.created_at DESC;
GO


-- ============================================================================
-- PHẦN 5: BỘ TRUY VẤN KIỂM TRA ĐỒNG BỘ DỮ LIỆU & DEBUGGING (HEALTH & INTEGRITY)
-- ============================================================================

-- 5.1 KIỂM TRA CÁC GIẢI ĐÃ TẠO NHƯNG CHƯA CÓ ROWS TRONG BẢNG MATCHES (Fix bug matches rỗng)
SELECT 
    t.id AS [Mã Giải],
    t.name AS [Tên Giải Đấu],
    ts.format AS [Thể Thức],
    t.status AS [Trạng Thái Giải],
    COUNT(DISTINCT tm.id) AS [Số Đội Đăng Ký],
    COUNT(DISTINCT m.id) AS [Số Trận Trong DB],
    CASE 
        WHEN COUNT(DISTINCT m.id) = 0 THEN N'⚠️ CHƯA LƯU MATCHES VÀO DB (Cần vào trang giải để sync)'
        ELSE N'✅ Đã lưu đầy đủ'
    END AS [Tình Trạng Lưu Matches]
FROM tournaments t
LEFT JOIN tournament_stages ts ON ts.tournament_id = t.id AND ts.stage_order = 1
LEFT JOIN teams tm ON tm.tournament_id = t.id
LEFT JOIN matches m ON m.tournament_id = t.id
GROUP BY t.id, t.name, ts.format, t.status
ORDER BY [Số Trận Trong DB] ASC, t.created_at DESC;
GO

-- 5.2 KIỂM TRA LOGIC TỈ SỐ & NGƯỜI CHIẾN THẮNG TRONG BẢNG MATCHES
SELECT 
    m.id AS [Match_ID],
    t.name AS [Giải Đấu],
    m.match_code AS [Mã Trận],
    m.round_number AS [Vòng],
    ISNULL(t1.raw_name, 'NULL') AS [Đội 1],
    m.score1 AS [Điểm 1],
    m.score2 AS [Điểm 2],
    ISNULL(t2.raw_name, 'NULL') AS [Đội 2],
    ISNULL(tw.raw_name, 'NULL') AS [Đội Thắng],
    m.status AS [Trạng Thái],
    CASE 
        WHEN m.status = 'FINISHED' AND (m.score1 IS NULL OR m.score2 IS NULL) 
            THEN N'❌ Lỗi: FINISHED nhưng thiếu điểm'
        WHEN m.status = 'FINISHED' AND m.score1 != m.score2 AND m.winner_id IS NULL 
            THEN N'❌ Lỗi: Có tỉ số nhưng thiếu winner_id'
        WHEN m.status = 'FINISHED' AND m.score1 = m.score2 AND m.winner_id IS NOT NULL AND ts.format = 'ROUND_ROBIN'
            THEN N'⚠️ Lưu ý: Hòa nhưng lại gán winner_id trong Round Robin'
        ELSE N'✅ Hợp lệ'
    END AS [Kiểm Tra Logic]
FROM matches m
JOIN tournaments t ON m.tournament_id = t.id
JOIN tournament_stages ts ON m.stage_id = ts.id
LEFT JOIN teams t1 ON m.team1_id = t1.id
LEFT JOIN teams t2 ON m.team2_id = t2.id
LEFT JOIN teams tw ON m.winner_id = tw.id
WHERE m.status = 'FINISHED' 
  AND (m.score1 IS NULL OR m.score2 IS NULL OR (m.score1 != m.score2 AND m.winner_id IS NULL));
GO

-- 5.3 KIỂM TRA ĐỒNG BỘ TRẠNG THÁI HOÀN THÀNH & TÊN NHÀ VÔ ĐỊCH (DB vs UI)
SELECT 
    t.id AS [Mã Giải],
    t.name AS [Tên Giải Đấu],
    t.status AS [Trạng Thái DB],
    ISNULL(t.champion_name, N'Chưa có') AS [Champion Cột DB],
    ISNULL(sub.final_winner, N'Chưa xác định') AS [Winner Tính Từ Trận Cuối],
    CASE 
        WHEN t.status = 'COMPLETED' AND t.champion_name IS NULL 
            THEN N'⚠️ Giải COMPLETED nhưng chưa có champion_name'
        WHEN t.status = 'COMPLETED' AND t.champion_name IS NOT NULL 
            THEN N'✅ Hoàn tất đồng bộ'
        ELSE N'⏳ Giải chưa kết thúc'
    END AS [Tình Trạng Popup & DB]
FROM tournaments t
LEFT JOIN (
    SELECT m.tournament_id,
           tm.raw_name AS final_winner,
           ROW_NUMBER() OVER (PARTITION BY m.tournament_id ORDER BY m.round_number DESC, m.match_order DESC) AS rn
    FROM matches m
    JOIN teams tm ON m.winner_id = tm.id
    WHERE m.status = 'FINISHED'
) sub ON t.id = sub.tournament_id AND sub.rn = 1
ORDER BY t.created_at DESC;
GO


-- ============================================================================
-- PHẦN 6: CÁC TIỆN ÍCH QUẢN TRỊ & DỌN DẸP DỮ LIỆU (ADMIN UTILITIES)
-- ============================================================================

-- 6.1 Backfill champion_name & teams_json cho tất cả các giải cũ (nếu chưa có)
UPDATE t
SET t.champion_name = sub.raw_name
FROM tournaments t
JOIN (
    SELECT m.tournament_id,
           tm.raw_name,
           ROW_NUMBER() OVER (
               PARTITION BY m.tournament_id
               ORDER BY ISNULL(ts.stage_order, 1) DESC, m.round_number DESC
           ) AS rn
    FROM matches m
    JOIN teams tm ON m.winner_id = tm.id
    LEFT JOIN tournament_stages ts ON m.stage_id = ts.id
    WHERE m.winner_id IS NOT NULL
) sub ON t.id = sub.tournament_id AND sub.rn = 1
WHERE t.champion_name IS NULL AND t.status = 'COMPLETED';

UPDATE t
SET t.teams_json = sub.json_val
FROM tournaments t
JOIN (
    SELECT tournament_id,
           '[' + STRING_AGG('"' + REPLACE(raw_name, '"', '\"') + '"', ',') WITHIN GROUP (ORDER BY original_seed ASC) + ']' AS json_val
    FROM teams
    GROUP BY tournament_id
) sub ON t.id = sub.tournament_id
WHERE t.teams_json IS NULL;
GO

-- 6.2 Lệnh Reset kết quả trận đấu của 1 giải (Xóa điểm, đưa về PENDING)
-- Điền mã giải cần Reset vào biến @ResetTourneyId:
/*
DECLARE @ResetTourneyId VARCHAR(50) = 'YOUR_TOURNAMENT_ID';

UPDATE matches 
SET score1 = NULL, score2 = NULL, penalty1 = NULL, penalty2 = NULL, winner_id = NULL, loser_id = NULL, status = 'PENDING'
WHERE tournament_id = @ResetTourneyId;

UPDATE tournaments 
SET status = 'ONGOING', champion_name = NULL 
WHERE id = @ResetTourneyId;

UPDATE tournament_stages
SET status = 'ONGOING'
WHERE tournament_id = @ResetTourneyId;

PRINT N'Đã reset trạng thái giải đấu và điểm trận đấu thành công!';
*/
GO

-- 6.3 Lệnh Xóa sạch 1 giải đấu cụ thể theo đúng thứ tự ràng buộc khóa ngoại (FK)
-- Điền mã giải cần Xóa vào biến @DeleteTourneyId:
/*
DECLARE @DeleteTourneyId VARCHAR(50) = 'YOUR_TOURNAMENT_ID';

DELETE FROM series_tournament_history WHERE tournament_id = @DeleteTourneyId;
DELETE FROM matches WHERE tournament_id = @DeleteTourneyId;
DELETE FROM group_teams WHERE group_id IN (SELECT g.id FROM groups g JOIN tournament_stages ts ON g.stage_id = ts.id WHERE ts.tournament_id = @DeleteTourneyId);
DELETE FROM groups WHERE stage_id IN (SELECT id FROM tournament_stages WHERE tournament_id = @DeleteTourneyId);
DELETE FROM stage_participants WHERE tournament_id = @DeleteTourneyId;
DELETE FROM tournament_placement_points WHERE tournament_id = @DeleteTourneyId;
DELETE FROM teams WHERE tournament_id = @DeleteTourneyId;
DELETE FROM tournament_stages WHERE tournament_id = @DeleteTourneyId;
DELETE FROM tournaments WHERE id = @DeleteTourneyId;

PRINT N'Đã xóa hoàn toàn giải đấu ' + @DeleteTourneyId;
*/
GO

-- 6.4 Lệnh Xóa toàn bộ dữ liệu mẫu / kiểm thử của hệ thống (CHỈ DÙNG KHI CẦN RESET TOÀN BỘ)
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

PRINT N'Đã dọn dẹp toàn bộ dữ liệu kiểm thử!';
*/
GO

-- ============================================================================
-- 12. MIGRATION: THÊM CỘT stage1_status VÀO BẢNG tournaments
-- Giúp lưu trạng thái khoá/kết thúc vòng 1 vào CSDL (chống mất dữ liệu khi localStorage bị tràn)
-- ============================================================================
IF NOT EXISTS (
    SELECT 1 FROM sys.columns 
    WHERE object_id = OBJECT_ID('tournaments') AND name = 'stage1_status'
)
BEGIN
    ALTER TABLE tournaments ADD stage1_status VARCHAR(20) DEFAULT 'PENDING';
    PRINT N'Đã thêm cột stage1_status vào bảng tournaments!';
END
ELSE
BEGIN
    PRINT N'Cột stage1_status đã tồn tại trong bảng tournaments.';
END
GO

