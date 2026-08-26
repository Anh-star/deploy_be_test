-- ============================================================================
-- SCRIPT CẬP NHẬT 6 DANH MỤC CÔNG NGHỆ NỔI BẬT LÊN ĐẦU TRANG CHỦ
-- Hỗ trợ: Microsoft SQL Server (ITSTUDY)
-- ============================================================================

-- 1. Đẩy thứ tự hiển thị (display_order) của các danh mục hiện tại lùi về sau (bắt đầu từ 7)
UPDATE tbl_categories 
SET display_order = display_order + 6 
WHERE slug NOT IN ('docker', 'java', 'unity', 'mysql', 'sql-server', 'firebase');

-- 2. Thêm hoặc cập nhật 6 danh mục công nghệ nổi bật (display_order từ 1 đến 6)

-- 1. Docker
IF EXISTS (SELECT 1 FROM tbl_categories WHERE slug = 'docker')
    UPDATE tbl_categories SET name = N'Docker', display_order = 1, is_active = 1, updated_at = GETDATE() WHERE slug = 'docker';
ELSE IF EXISTS (SELECT 1 FROM tbl_categories WHERE name = N'Docker')
    UPDATE tbl_categories SET slug = 'docker', display_order = 1, is_active = 1, updated_at = GETDATE() WHERE name = N'Docker';
ELSE
    INSERT INTO tbl_categories (id, name, slug, description, is_active, display_order, created_at, updated_at)
    VALUES (NEWID(), N'Docker', 'docker', N'Nền tảng container hóa ứng dụng: Dockerfile, Docker Compose, Images, Containers...', 1, 1, GETDATE(), GETDATE());

-- 2. Java
IF EXISTS (SELECT 1 FROM tbl_categories WHERE slug = 'java')
    UPDATE tbl_categories SET name = N'Java', display_order = 2, is_active = 1, updated_at = GETDATE() WHERE slug = 'java';
ELSE IF EXISTS (SELECT 1 FROM tbl_categories WHERE name = N'Java')
    UPDATE tbl_categories SET slug = 'java', display_order = 2, is_active = 1, updated_at = GETDATE() WHERE name = N'Java';
ELSE
    INSERT INTO tbl_categories (id, name, slug, description, is_active, display_order, created_at, updated_at)
    VALUES (NEWID(), N'Java', 'java', N'Lập trình Java Core, OOP, Đa luồng, Collection, JVM, Spring Framework, Spring Boot...', 1, 2, GETDATE(), GETDATE());

-- 3. Unity
IF EXISTS (SELECT 1 FROM tbl_categories WHERE slug = 'unity')
    UPDATE tbl_categories SET name = N'Unity', display_order = 3, is_active = 1, updated_at = GETDATE() WHERE slug = 'unity';
ELSE IF EXISTS (SELECT 1 FROM tbl_categories WHERE name = N'Unity')
    UPDATE tbl_categories SET slug = 'unity', display_order = 3, is_active = 1, updated_at = GETDATE() WHERE name = N'Unity';
ELSE
    INSERT INTO tbl_categories (id, name, slug, description, is_active, display_order, created_at, updated_at)
    VALUES (NEWID(), N'Unity', 'unity', N'Phát triển Game 2D/3D, C# Scripting, Vật lý, Hoạt ảnh và tối ưu hóa Game trên Unity Engine...', 1, 3, GETDATE(), GETDATE());

-- 4. MySQL
IF EXISTS (SELECT 1 FROM tbl_categories WHERE slug = 'mysql')
    UPDATE tbl_categories SET name = N'MySQL', display_order = 4, is_active = 1, updated_at = GETDATE() WHERE slug = 'mysql';
ELSE IF EXISTS (SELECT 1 FROM tbl_categories WHERE name = N'MySQL')
    UPDATE tbl_categories SET slug = 'mysql', display_order = 4, is_active = 1, updated_at = GETDATE() WHERE name = N'MySQL';
ELSE
    INSERT INTO tbl_categories (id, name, slug, description, is_active, display_order, created_at, updated_at)
    VALUES (NEWID(), N'MySQL', 'mysql', N'Hệ quản trị cơ sở dữ liệu quan hệ mã nguồn mở: SQL, Indexing, Khóa chính/ngoại, Tối ưu hóa truy vấn...', 1, 4, GETDATE(), GETDATE());

-- 5. SQL Server
IF EXISTS (SELECT 1 FROM tbl_categories WHERE slug = 'sql-server')
    UPDATE tbl_categories SET name = N'SQL Server', display_order = 5, is_active = 1, updated_at = GETDATE() WHERE slug = 'sql-server';
ELSE IF EXISTS (SELECT 1 FROM tbl_categories WHERE name = N'SQL Server')
    UPDATE tbl_categories SET slug = 'sql-server', display_order = 5, is_active = 1, updated_at = GETDATE() WHERE name = N'SQL Server';
ELSE
    INSERT INTO tbl_categories (id, name, slug, description, is_active, display_order, created_at, updated_at)
    VALUES (NEWID(), N'SQL Server', 'sql-server', N'Hệ quản trị CSDL của Microsoft: T-SQL, Stored Procedures, Functions, Triggers, Transactions...', 1, 5, GETDATE(), GETDATE());

-- 6. Firebase
IF EXISTS (SELECT 1 FROM tbl_categories WHERE slug = 'firebase')
    UPDATE tbl_categories SET name = N'Firebase', display_order = 6, is_active = 1, updated_at = GETDATE() WHERE slug = 'firebase';
ELSE IF EXISTS (SELECT 1 FROM tbl_categories WHERE name = N'Firebase')
    UPDATE tbl_categories SET slug = 'firebase', display_order = 6, is_active = 1, updated_at = GETDATE() WHERE name = N'Firebase';
ELSE
    INSERT INTO tbl_categories (id, name, slug, description, is_active, display_order, created_at, updated_at)
    VALUES (NEWID(), N'Firebase', 'firebase', N'Nền tảng đám mây của Google: Authentication, Cloud Firestore, Realtime Database, Cloud Functions...', 1, 6, GETDATE(), GETDATE());

-- 3. Kiểm tra kết quả
SELECT id, name, slug, display_order, is_active 
FROM tbl_categories 
ORDER BY display_order ASC, name ASC;
