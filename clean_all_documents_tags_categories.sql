-- ============================================================================
-- SCRIPT: XÓA SẠCH TẤT CẢ TÀI LIỆU, THẺ (TAGS) VÀ DANH MỤC (CATEGORIES)
-- Target Database: SQL Server (svthayvu2026 / itstudy_db)
-- Mục đích: Làm sạch 100% dữ liệu tài liệu, thẻ rác, danh mục rác và tái tạo
--          bộ 8 Danh mục CNTT & 40 Thẻ công nghệ tiếng Việt chuẩn (Unicode NVARCHAR).
-- ============================================================================

USE [svthayvu2026];
GO

SET NOCOUNT ON;
BEGIN TRANSACTION;

BEGIN TRY
    PRINT '>>> BẮT ĐẦU QUÁ TRÌNH DỌN DẸP DỮ LIỆU TÀI LIỆU, THẺ & DANH MỤC...';

    -- 1. Xóa các bảng liên quan đến Quiz & Bài kiểm tra của tài liệu
    IF OBJECT_ID('dbo.tbl_quiz_attempt_answers', 'U') IS NOT NULL
    BEGIN
        DELETE FROM dbo.tbl_quiz_attempt_answers;
        PRINT ' - Đã xóa dữ liệu tbl_quiz_attempt_answers';
    END

    IF OBJECT_ID('dbo.tbl_quiz_attempts', 'U') IS NOT NULL
    BEGIN
        DELETE FROM dbo.tbl_quiz_attempts;
        PRINT ' - Đã xóa dữ liệu tbl_quiz_attempts';
    END

    IF OBJECT_ID('dbo.tbl_quiz_question_options', 'U') IS NOT NULL
    BEGIN
        DELETE FROM dbo.tbl_quiz_question_options;
        PRINT ' - Đã xóa dữ liệu tbl_quiz_question_options';
    END

    IF OBJECT_ID('dbo.tbl_quiz_questions', 'U') IS NOT NULL
    BEGIN
        DELETE FROM dbo.tbl_quiz_questions;
        PRINT ' - Đã xóa dữ liệu tbl_quiz_questions';
    END

    IF OBJECT_ID('dbo.tbl_quiz_generations', 'U') IS NOT NULL
    BEGIN
        DELETE FROM dbo.tbl_quiz_generations;
        PRINT ' - Đã xóa dữ liệu tbl_quiz_generations';
    END

    IF OBJECT_ID('dbo.tbl_document_quizzes', 'U') IS NOT NULL
    BEGIN
        DELETE FROM dbo.tbl_document_quizzes;
        PRINT ' - Đã xóa dữ liệu tbl_document_quizzes';
    END

    IF OBJECT_ID('dbo.tbl_quizzes', 'U') IS NOT NULL
    BEGIN
        DELETE FROM dbo.tbl_quizzes;
        PRINT ' - Đã xóa dữ liệu tbl_quizzes';
    END

    -- 2. Xóa các tương tác của tài liệu (Bình luận, Bookmark, View, Download, Báo cáo, Đánh giá)
    IF OBJECT_ID('dbo.tbl_document_comment_likes', 'U') IS NOT NULL
    BEGIN
        DELETE FROM dbo.tbl_document_comment_likes;
        PRINT ' - Đã xóa dữ liệu tbl_document_comment_likes';
    END

    IF OBJECT_ID('dbo.tbl_document_comments', 'U') IS NOT NULL
    BEGIN
        DELETE FROM dbo.tbl_document_comments;
        PRINT ' - Đã xóa dữ liệu tbl_document_comments';
    END

    IF OBJECT_ID('dbo.tbl_document_reports', 'U') IS NOT NULL
    BEGIN
        DELETE FROM dbo.tbl_document_reports;
        PRINT ' - Đã xóa dữ liệu tbl_document_reports';
    END

    IF OBJECT_ID('dbo.tbl_document_bookmarks', 'U') IS NOT NULL
    BEGIN
        DELETE FROM dbo.tbl_document_bookmarks;
        PRINT ' - Đã xóa dữ liệu tbl_document_bookmarks';
    END

    IF OBJECT_ID('dbo.tbl_document_views', 'U') IS NOT NULL
    BEGIN
        DELETE FROM dbo.tbl_document_views;
        PRINT ' - Đã xóa dữ liệu tbl_document_views';
    END

    IF OBJECT_ID('dbo.tbl_document_downloads', 'U') IS NOT NULL
    BEGIN
        DELETE FROM dbo.tbl_document_downloads;
        PRINT ' - Đã xóa dữ liệu tbl_document_downloads';
    END

    IF OBJECT_ID('dbo.tbl_document_authors', 'U') IS NOT NULL
    BEGIN
        DELETE FROM dbo.tbl_document_authors;
        PRINT ' - Đã xóa dữ liệu tbl_document_authors';
    END

    IF OBJECT_ID('dbo.tbl_document_preferences', 'U') IS NOT NULL
    BEGIN
        DELETE FROM dbo.tbl_document_preferences;
        PRINT ' - Đã xóa dữ liệu tbl_document_preferences';
    END

    IF OBJECT_ID('dbo.tbl_document_preview_artifacts', 'U') IS NOT NULL
    BEGIN
        DELETE FROM dbo.tbl_document_preview_artifacts;
        PRINT ' - Đã xóa dữ liệu tbl_document_preview_artifacts';
    END

    IF OBJECT_ID('dbo.tbl_document_files', 'U') IS NOT NULL
    BEGIN
        DELETE FROM dbo.tbl_document_files;
        PRINT ' - Đã xóa dữ liệu tbl_document_files';
    END

    -- 3. Xóa các bảng liên quan đến giao dịch / thu nhập của tài liệu
    IF OBJECT_ID('dbo.tbl_seller_earnings', 'U') IS NOT NULL
    BEGIN
        DELETE FROM dbo.tbl_seller_earnings;
        PRINT ' - Đã xóa dữ liệu tbl_seller_earnings';
    END

    IF OBJECT_ID('dbo.tbl_pending_storage_uploads', 'U') IS NOT NULL
    BEGIN
        DELETE FROM dbo.tbl_pending_storage_uploads;
        PRINT ' - Đã xóa dữ liệu tbl_pending_storage_uploads';
    END

    IF OBJECT_ID('dbo.tbl_storage_cleanup_tasks', 'U') IS NOT NULL
    BEGIN
        DELETE FROM dbo.tbl_storage_cleanup_tasks;
        PRINT ' - Đã xóa dữ liệu tbl_storage_cleanup_tasks';
    END

    -- 4. Xóa bảng liên kết Thẻ & Tài liệu
    IF OBJECT_ID('dbo.tbl_document_tags', 'U') IS NOT NULL
    BEGIN
        DELETE FROM dbo.tbl_document_tags;
        PRINT ' - Đã xóa dữ liệu tbl_document_tags';
    END

    -- 5. Xóa toàn bộ Bảng Tài liệu (tbl_documents)
    IF OBJECT_ID('dbo.tbl_documents', 'U') IS NOT NULL
    BEGIN
        DELETE FROM dbo.tbl_documents;
        PRINT ' - Đã xóa toàn bộ dữ liệu tbl_documents';
    END

    -- 6. Xóa toàn bộ Bảng Thẻ (tbl_tags)
    IF OBJECT_ID('dbo.tbl_tags', 'U') IS NOT NULL
    BEGIN
        DELETE FROM dbo.tbl_tags;
        PRINT ' - Đã xóa toàn bộ dữ liệu tbl_tags';
    END

    -- 7. Xóa toàn bộ Bảng Danh mục (tbl_categories)
    IF OBJECT_ID('dbo.tbl_categories', 'U') IS NOT NULL
    BEGIN
        -- Gỡ bỏ self-reference parent_id trước khi xóa
        UPDATE dbo.tbl_categories SET parent_id = NULL;
        DELETE FROM dbo.tbl_categories;
        PRINT ' - Đã xóa toàn bộ dữ liệu tbl_categories';
    END

    -- 8. Tự động gỡ các Index và Unique Constraint cản trở việc đổi cột sang NVARCHAR
    PRINT '>>> GỠ BỎ CÁC INDEX/CONSTRAINT TẠM THỜI ĐỂ ĐỔI SANG NVARCHAR...';
    DECLARE @DropSql NVARCHAR(MAX) = N'';

    -- Xóa các Indexes không phải Primary Key trên các cột name, description, title, etc.
    SELECT @DropSql += N'DROP INDEX ' + QUOTENAME(i.name) + N' ON ' + QUOTENAME(SCHEMA_NAME(t.schema_id)) + N'.' + QUOTENAME(t.name) + N'; '
    FROM sys.indexes i
    JOIN sys.index_columns ic ON i.object_id = ic.object_id AND i.index_id = ic.index_id
    JOIN sys.columns c ON ic.object_id = c.object_id AND ic.column_id = c.column_id
    JOIN sys.tables t ON t.object_id = i.object_id
    WHERE t.name IN ('tbl_categories', 'tbl_tags', 'tbl_documents', 'tbl_post_tags')
      AND c.name IN ('name', 'description', 'title', 'file_name', 'tag_name', 'reject_reason')
      AND i.is_primary_key = 0;

    -- Xóa Unique Constraints trên các cột này nếu có
    SELECT @DropSql += N'ALTER TABLE ' + QUOTENAME(SCHEMA_NAME(t.schema_id)) + N'.' + QUOTENAME(t.name) + N' DROP CONSTRAINT ' + QUOTENAME(con.name) + N'; '
    FROM sys.key_constraints con
    JOIN sys.index_columns ic ON con.parent_object_id = ic.object_id AND con.unique_index_id = ic.index_id
    JOIN sys.columns c ON ic.object_id = c.object_id AND ic.column_id = c.column_id
    JOIN sys.tables t ON t.object_id = con.parent_object_id
    WHERE t.name IN ('tbl_categories', 'tbl_tags', 'tbl_documents', 'tbl_post_tags')
      AND c.name IN ('name', 'description', 'title', 'file_name', 'tag_name', 'reject_reason')
      AND con.type = 'UQ';

    IF LEN(@DropSql) > 0
    BEGIN
        EXEC sp_executesql @DropSql;
        PRINT ' - Đã gỡ bỏ các index/constraint phụ thuộc';
    END

    -- 9. Chuyển đổi các cột text sang NVARCHAR (hỗ trợ Tiếng Việt có dấu 100%)
    PRINT '>>> ĐỒNG BỘ CẤU TRÚC NVARCHAR CHO TIẾNG VIỆT...';
    BEGIN TRY
        IF EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID(N'dbo.tbl_categories') AND name = N'name')
            ALTER TABLE dbo.tbl_categories ALTER COLUMN name NVARCHAR(150) NOT NULL;
    END TRY BEGIN CATCH PRINT ' - Bỏ qua alter tbl_categories.name: ' + ERROR_MESSAGE(); END CATCH;

    BEGIN TRY
        IF EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID(N'dbo.tbl_categories') AND name = N'description')
            ALTER TABLE dbo.tbl_categories ALTER COLUMN description NVARCHAR(500) NULL;
    END TRY BEGIN CATCH PRINT ' - Bỏ qua alter tbl_categories.description: ' + ERROR_MESSAGE(); END CATCH;

    BEGIN TRY
        IF EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID(N'dbo.tbl_tags') AND name = N'name')
            ALTER TABLE dbo.tbl_tags ALTER COLUMN name NVARCHAR(100) NOT NULL;
    END TRY BEGIN CATCH PRINT ' - Bỏ qua alter tbl_tags.name: ' + ERROR_MESSAGE(); END CATCH;

    BEGIN TRY
        IF EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID(N'dbo.tbl_documents') AND name = N'title')
            ALTER TABLE dbo.tbl_documents ALTER COLUMN title NVARCHAR(255) NOT NULL;
    END TRY BEGIN CATCH PRINT ' - Bỏ qua alter tbl_documents.title: ' + ERROR_MESSAGE(); END CATCH;

    BEGIN TRY
        IF EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID(N'dbo.tbl_documents') AND name = N'file_name')
            ALTER TABLE dbo.tbl_documents ALTER COLUMN file_name NVARCHAR(255) NULL;
    END TRY BEGIN CATCH PRINT ' - Bỏ qua alter tbl_documents.file_name: ' + ERROR_MESSAGE(); END CATCH;

    BEGIN TRY
        IF EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID(N'dbo.tbl_documents') AND name = N'description')
            ALTER TABLE dbo.tbl_documents ALTER COLUMN description NVARCHAR(MAX) NULL;
    END TRY BEGIN CATCH PRINT ' - Bỏ qua alter tbl_documents.description: ' + ERROR_MESSAGE(); END CATCH;

    BEGIN TRY
        IF EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID(N'dbo.tbl_documents') AND name = N'reject_reason')
            ALTER TABLE dbo.tbl_documents ALTER COLUMN reject_reason NVARCHAR(MAX) NULL;
    END TRY BEGIN CATCH PRINT ' - Bỏ qua alter tbl_documents.reject_reason: ' + ERROR_MESSAGE(); END CATCH;

    -- 10. Nạp lại 8 Danh mục CNTT Tiếng Việt chuẩn
    PRINT '>>> NẠP MỚI 8 DANH MỤC CNTT CHUẨN...';
    DECLARE @Now DATETIME2 = SYSDATETIME();

    INSERT INTO dbo.tbl_categories (id, name, slug, description, display_order, is_active, created_at, updated_at) VALUES
    (NEWID(), N'Lập trình Web', N'lap-trinh-web', N'Tài liệu phát triển Web: Frontend, Backend, Fullstack (HTML, CSS, React, Vue, Angular, Node.js, Spring Boot, ASP.NET...)', 1, 1, @Now, @Now),
    (NEWID(), N'Lập trình Di động', N'lap-trinh-di-dong', N'Tài liệu phát triển ứng dụng di động: Android, iOS, Flutter, React Native, Kotlin, Swift...', 2, 1, @Now, @Now),
    (NEWID(), N'Cơ sở dữ liệu', N'co-so-du-lieu', N'Hệ quản trị CSDL quan hệ & NoSQL: SQL Server, MySQL, PostgreSQL, Oracle, MongoDB, Redis...', 3, 1, @Now, @Now),
    (NEWID(), N'Trí tuệ nhân tạo & Khoa học dữ liệu', N'tri-tue-nhan-tao-khoa-hoc-du-lieu', N'Tài liệu AI, Machine Learning, Deep Learning, Phân tích dữ liệu, Python, TensorFlow, PyTorch...', 4, 1, @Now, @Now),
    (NEWID(), N'Mạng máy tính & An toàn thông tin', N'mang-may-tinh-an-toan-thong-tin', N'Quản trị mạng, CCNA, An ninh mạng, Bảo mật hệ thống, Hacking đạo đức, SOC, Mật mã học...', 5, 1, @Now, @Now),
    (NEWID(), N'Kiến trúc phần mềm & DevOps', N'kien-truc-phan-mem-devops', N'Docker, Kubernetes, CI/CD, Microservices, Điện toán đám mây (AWS, Azure, GCP), System Design...', 6, 1, @Now, @Now),
    (NEWID(), N'Thuật toán & Cấu trúc dữ liệu', N'thuat-toan-cau-truc-du-lieu', N'Giáo trình CTDL & GT, Giải thuật nâng cao, Luyện thi thuật toán, Lập trình thi đấu ACM/ICPC...', 7, 1, @Now, @Now),
    (NEWID(), N'Công nghệ phần mềm & Đồ án', N'cong-nghe-phan-mem-do-an', N'Phân tích thiết kế hệ thống (UML), Quản lý dự án Agile/Scrum, Hướng dẫn làm Khóa luận & Đồ án tốt nghiệp...', 8, 1, @Now, @Now);

    -- 11. Nạp lại 40 Thẻ công nghệ Tiếng Việt / Chuẩn quốc tế
    PRINT '>>> NẠP MỚI 40 THẺ CÔNG NGHỆ CHUẨN...';
    INSERT INTO dbo.tbl_tags (id, name, slug, usage_count, is_active, created_at, updated_at) VALUES
    -- Ngôn ngữ lập trình
    (NEWID(), N'Java', N'java', 0, 1, @Now, @Now),
    (NEWID(), N'Python', N'python', 0, 1, @Now, @Now),
    (NEWID(), N'C / C++', N'c-cpp', 0, 1, @Now, @Now),
    (NEWID(), N'C# (.NET)', N'c-sharp', 0, 1, @Now, @Now),
    (NEWID(), N'JavaScript', N'javascript', 0, 1, @Now, @Now),
    (NEWID(), N'TypeScript', N'typescript', 0, 1, @Now, @Now),
    (NEWID(), N'PHP', N'php', 0, 1, @Now, @Now),
    (NEWID(), N'Golang', N'golang', 0, 1, @Now, @Now),
    (NEWID(), N'Kotlin', N'kotlin', 0, 1, @Now, @Now),
    (NEWID(), N'Swift', N'swift', 0, 1, @Now, @Now),
    (NEWID(), N'Rust', N'rust', 0, 1, @Now, @Now),
    (NEWID(), N'Dart', N'dart', 0, 1, @Now, @Now),
    -- Frontend & Mobile
    (NEWID(), N'ReactJS', N'reactjs', 0, 1, @Now, @Now),
    (NEWID(), N'Vue.js', N'vuejs', 0, 1, @Now, @Now),
    (NEWID(), N'Angular', N'angular', 0, 1, @Now, @Now),
    (NEWID(), N'Next.js', N'nextjs', 0, 1, @Now, @Now),
    (NEWID(), N'HTML5 / CSS3', N'html5-css3', 0, 1, @Now, @Now),
    (NEWID(), N'Tailwind CSS', N'tailwind-css', 0, 1, @Now, @Now),
    (NEWID(), N'Flutter', N'flutter', 0, 1, @Now, @Now),
    (NEWID(), N'React Native', N'react-native', 0, 1, @Now, @Now),
    -- Backend & Frameworks
    (NEWID(), N'Spring Boot', N'spring-boot', 0, 1, @Now, @Now),
    (NEWID(), N'Node.js', N'nodejs', 0, 1, @Now, @Now),
    (NEWID(), N'Express.js', N'expressjs', 0, 1, @Now, @Now),
    (NEWID(), N'ASP.NET Core', N'aspnet-core', 0, 1, @Now, @Now),
    (NEWID(), N'Laravel', N'laravel', 0, 1, @Now, @Now),
    (NEWID(), N'NestJS', N'nestjs', 0, 1, @Now, @Now),
    (NEWID(), N'Django', N'django', 0, 1, @Now, @Now),
    (NEWID(), N'FastAPI', N'fastapi', 0, 1, @Now, @Now),
    -- CSDL & DevOps
    (NEWID(), N'SQL Server', N'sql-server', 0, 1, @Now, @Now),
    (NEWID(), N'MySQL', N'mysql', 0, 1, @Now, @Now),
    (NEWID(), N'PostgreSQL', N'postgresql', 0, 1, @Now, @Now),
    (NEWID(), N'MongoDB', N'mongodb', 0, 1, @Now, @Now),
    (NEWID(), N'Redis', N'redis', 0, 1, @Now, @Now),
    (NEWID(), N'Docker', N'docker', 0, 1, @Now, @Now),
    (NEWID(), N'Kubernetes', N'kubernetes', 0, 1, @Now, @Now),
    (NEWID(), N'Git & GitHub', N'git-github', 0, 1, @Now, @Now),
    (NEWID(), N'AWS', N'aws', 0, 1, @Now, @Now),
    (NEWID(), N'Linux / Ubuntu', N'linux-ubuntu', 0, 1, @Now, @Now),
    -- Học thuật, AI & Đồ án
    (NEWID(), N'Machine Learning', N'machine-learning', 0, 1, @Now, @Now),
    (NEWID(), N'Deep Learning', N'deep-learning', 0, 1, @Now, @Now),
    (NEWID(), N'An toàn thông tin', N'an-toan-thong-tin', 0, 1, @Now, @Now),
    (NEWID(), N'Kiến trúc Microservices', N'microservices', 0, 1, @Now, @Now),
    (NEWID(), N'Cấu trúc dữ liệu & Giải thuật', N'ctdl-gt', 0, 1, @Now, @Now),
    (NEWID(), N'Thiết kế hệ thống (System Design)', N'system-design', 0, 1, @Now, @Now),
    (NEWID(), N'Đề thi & Lời giải', N'de-thi-loi-giai', 0, 1, @Now, @Now),
    (NEWID(), N'Đồ án tốt nghiệp', N'do-an-tot-nghiep', 0, 1, @Now, @Now);

    COMMIT TRANSACTION;
    PRINT '========================================================================';
    PRINT '>>> HOÀN TẤT THÀNH CÔNG! ĐÃ XÓA SẠCH VÀ TÁI TẠO 8 DANH MỤC & 40 THẺ MỚI.';
    PRINT '========================================================================';
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0
        ROLLBACK TRANSACTION;

    PRINT '!!! LỖI TRONG QUÁ TRÌNH THỰC THI:';
    PRINT ERROR_MESSAGE();
END CATCH;
GO
