-- ============================================================================
-- MIGRATION: DB-First Persistence (Hướng 1)
-- Thêm 2 cột vào bảng tournaments để lưu champion và danh sách đội trực tiếp vào DB,
-- thay thế việc phụ thuộc vào localStorage (dễ bị mất khi đầy bộ nhớ ~5MB).
-- Chạy script này 1 lần duy nhất trong SQL Server Management Studio.
-- ============================================================================

USE tourma;
GO

-- 1. Thêm cột champion_name (lưu tên vô địch trực tiếp, không cần subquery từ matches)
IF NOT EXISTS (
    SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_NAME = 'tournaments' AND COLUMN_NAME = 'champion_name'
)
BEGIN
    ALTER TABLE tournaments ADD champion_name NVARCHAR(255) NULL;
    PRINT 'Column champion_name added to tournaments.';
END
ELSE
    PRINT 'Column champion_name already exists.';
GO

-- 2. Thêm cột teams_json (lưu JSON array tên đội cho rolling standings đọc lại)
IF NOT EXISTS (
    SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_NAME = 'tournaments' AND COLUMN_NAME = 'teams_json'
)
BEGIN
    ALTER TABLE tournaments ADD teams_json NVARCHAR(MAX) NULL;
    PRINT 'Column teams_json added to tournaments.';
END
ELSE
    PRINT 'Column teams_json already exists.';
GO

-- 3. Backfill champion_name từ matches cho các giải đã có kết quả (giải cũ)
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
WHERE t.champion_name IS NULL;
GO
PRINT 'Backfilled champion_name for existing completed tournaments.';
GO

-- 4. Backfill teams_json từ bảng teams cho các giải đã có đội (giải cũ)
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
PRINT 'Backfilled teams_json for existing tournaments.';
GO

-- Xác nhận kết quả
SELECT
    COUNT(*) AS [Tổng Giải],
    SUM(CASE WHEN champion_name IS NOT NULL THEN 1 ELSE 0 END) AS [Có Champion],
    SUM(CASE WHEN teams_json IS NOT NULL THEN 1 ELSE 0 END) AS [Có Teams JSON]
FROM tournaments;
GO
