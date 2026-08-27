-- Script tạo bảng lưu trạng thái tắt thông báo bài viết nếu chưa có trên SQL Server
IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'tbl_community_post_notification_mutes')
BEGIN
    CREATE TABLE tbl_community_post_notification_mutes (
        id UNIQUEIDENTIFIER NOT NULL PRIMARY KEY DEFAULT NEWID(),
        post_id UNIQUEIDENTIFIER NOT NULL,
        user_id UNIQUEIDENTIFIER NOT NULL,
        created_at DATETIME2 NOT NULL DEFAULT GETDATE(),
        CONSTRAINT fk_post_mute_post FOREIGN KEY (post_id) REFERENCES tbl_community_posts(id) ON DELETE CASCADE,
        CONSTRAINT fk_post_mute_user FOREIGN KEY (user_id) REFERENCES tbl_users(id) ON DELETE CASCADE,
        CONSTRAINT uk_community_post_notification_mute_post_user UNIQUE (post_id, user_id)
    );
    PRINT 'Đã tạo bảng tbl_community_post_notification_mutes thành công.';
END
ELSE
BEGIN
    PRINT 'Bảng tbl_community_post_notification_mutes đã tồn tại.';
END
GO
