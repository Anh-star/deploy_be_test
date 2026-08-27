-- ============================================================================
-- SCRIPT SỬA LỖI TÊN USER CÓ DẤU TIẾNG VIỆT THÀNH DẤU CHẤM HỎI (?)
-- Nguyên nhân: Cột full_name và bio trong bảng tbl_users đang để VARCHAR
-- Cách khắc phục: Đổi sang NVARCHAR để hỗ trợ Unicode tiếng Việt đầy đủ.
-- ============================================================================

-- 1. Đổi kiểu dữ liệu cột full_name sang NVARCHAR(255)
IF EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('tbl_users') AND name = 'full_name')
BEGIN
    ALTER TABLE tbl_users ALTER COLUMN full_name NVARCHAR(255) NULL;
    PRINT N'Đã chuyển tbl_users.full_name sang NVARCHAR(255) thành công!';
END

-- 2. Đổi kiểu dữ liệu cột bio sang NVARCHAR(2000)
IF EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('tbl_users') AND name = 'bio')
BEGIN
    ALTER TABLE tbl_users ALTER COLUMN bio NVARCHAR(2000) NULL;
    PRINT N'Đã chuyển tbl_users.bio sang NVARCHAR(2000) thành công!';
END

-- 3. Kiểm tra các bảng liên quan khác (tbl_authors)
IF EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('tbl_authors') AND name = 'name')
BEGIN
    ALTER TABLE tbl_authors ALTER COLUMN name NVARCHAR(255) NOT NULL;
    PRINT N'Đã chuyển tbl_authors.name sang NVARCHAR(255) thành công!';
END

IF EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('tbl_authors') AND name = 'bio')
BEGIN
    ALTER TABLE tbl_authors ALTER COLUMN bio NVARCHAR(MAX) NULL;
    PRINT N'Đã chuyển tbl_authors.bio sang NVARCHAR(MAX) thành công!';
END

-- 4. Kiểm tra lại kiểu dữ liệu của bảng tbl_users
SELECT 
    c.name AS column_name,
    t.name AS data_type,
    c.max_length,
    c.is_nullable
FROM sys.columns c
JOIN sys.types t ON c.user_type_id = t.user_type_id
WHERE c.object_id = OBJECT_ID('tbl_users') AND c.name IN ('full_name', 'bio');
