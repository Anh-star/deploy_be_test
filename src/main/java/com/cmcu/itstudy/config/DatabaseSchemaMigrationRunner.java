package com.cmcu.itstudy.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

@Slf4j
@Component
public class DatabaseSchemaMigrationRunner implements ApplicationRunner {

    private final DataSource dataSource;

    public DatabaseSchemaMigrationRunner(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            // 1. Add is_hidden to tbl_documents if not exists
            try {
                stmt.execute("IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('tbl_documents') AND name = 'is_hidden') " +
                             "BEGIN " +
                             "    ALTER TABLE tbl_documents ADD is_hidden BIT NULL DEFAULT 0; " +
                             "END");
                stmt.execute("UPDATE tbl_documents SET is_hidden = 0 WHERE is_hidden IS NULL;");
                log.info("Schema migration: tbl_documents.is_hidden verified successfully.");
            } catch (Exception ex) {
                log.warn("Schema migration for tbl_documents.is_hidden: {}", ex.getMessage());
            }

            // 2. Add escalation & resolution fields to tbl_community_post_reports if not exists
            try {
                stmt.execute("IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('tbl_community_post_reports') AND name = 'escalation_reason') " +
                             "BEGIN " +
                             "    ALTER TABLE tbl_community_post_reports ADD escalation_reason NVARCHAR(MAX) NULL; " +
                             "END");
                stmt.execute("IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('tbl_community_post_reports') AND name = 'escalated_at') " +
                             "BEGIN " +
                             "    ALTER TABLE tbl_community_post_reports ADD escalated_at DATETIME2 NULL; " +
                             "END");
                stmt.execute("IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('tbl_community_post_reports') AND name = 'escalated_by_user_id') " +
                             "BEGIN " +
                             "    ALTER TABLE tbl_community_post_reports ADD escalated_by_user_id UNIQUEIDENTIFIER NULL; " +
                             "END");
                stmt.execute("IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('tbl_community_post_reports') AND name = 'resolution_notes') " +
                             "BEGIN " +
                             "    ALTER TABLE tbl_community_post_reports ADD resolution_notes NVARCHAR(MAX) NULL; " +
                             "END");
                log.info("Schema migration: tbl_community_post_reports escalation & resolution columns verified successfully.");
            } catch (Exception ex) {
                log.warn("Schema migration for tbl_community_post_reports: {}", ex.getMessage());
            }

            // 3. Add Indexes to prevent table locks during bulk operations
            try {
                stmt.execute("IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'idx_documents_created_by' AND object_id = OBJECT_ID('tbl_documents')) " +
                             "    CREATE INDEX idx_documents_created_by ON tbl_documents(created_by);");
                stmt.execute("IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'idx_posts_author_id' AND object_id = OBJECT_ID('tbl_community_posts')) " +
                             "    CREATE INDEX idx_posts_author_id ON tbl_community_posts(author_id);");
                stmt.execute("IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'idx_refresh_tokens_user_id' AND object_id = OBJECT_ID('tbl_refresh_tokens')) " +
                             "    CREATE INDEX idx_refresh_tokens_user_id ON tbl_refresh_tokens(user_id);");
                log.info("Schema migration: Performance indexes verified successfully.");
            } catch (Exception ex) {
                log.warn("Schema migration for performance indexes: {}", ex.getMessage());
            }

            // 4. Ensure tbl_users.full_name and bio are NVARCHAR to properly support Vietnamese diacritics
            try {
                stmt.execute("IF EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('tbl_users') AND name = 'full_name') " +
                             "    ALTER TABLE tbl_users ALTER COLUMN full_name NVARCHAR(255) NULL;");
                stmt.execute("IF EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('tbl_users') AND name = 'bio') " +
                             "    ALTER TABLE tbl_users ALTER COLUMN bio NVARCHAR(2000) NULL;");
                stmt.execute("IF EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('tbl_authors') AND name = 'name') " +
                             "    ALTER TABLE tbl_authors ALTER COLUMN name NVARCHAR(255) NOT NULL;");
                stmt.execute("IF EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('tbl_authors') AND name = 'bio') " +
                             "    ALTER TABLE tbl_authors ALTER COLUMN bio NVARCHAR(MAX) NULL;");
                log.info("Schema migration: tbl_users full_name & bio NVARCHAR verified successfully.");
            } catch (Exception ex) {
                log.warn("Schema migration for Unicode columns: {}", ex.getMessage());
            }

            // 5. One-time self-healing sync: recalibrate any drifted post & comment vote counts to match exact likes table
            try {
                stmt.execute("UPDATE p SET " +
                             "    upvote_count = ISNULL((SELECT COUNT(1) FROM tbl_community_post_likes l WHERE l.post_id = p.id AND l.vote_type = 'UPVOTE'), 0), " +
                             "    downvote_count = ISNULL((SELECT COUNT(1) FROM tbl_community_post_likes l WHERE l.post_id = p.id AND l.vote_type = 'DOWNVOTE'), 0) " +
                             "FROM tbl_community_posts p;");
                stmt.execute("UPDATE c SET " +
                             "    upvote_count = ISNULL((SELECT COUNT(1) FROM tbl_community_post_comment_likes l WHERE l.comment_id = c.id AND l.vote_type = 'UPVOTE'), 0), " +
                             "    downvote_count = ISNULL((SELECT COUNT(1) FROM tbl_community_post_comment_likes l WHERE l.comment_id = c.id AND l.vote_type = 'DOWNVOTE'), 0) " +
                             "FROM tbl_community_post_comments c;");
                log.info("Schema migration: Recalibrated post and comment vote counts to exact database state.");
            } catch (Exception ex) {
                log.warn("Schema migration for recalibrating vote counts: {}", ex.getMessage());
            }

            // 6. Ensure tbl_community_post_notification_mutes table exists
            try {
                stmt.execute("IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'tbl_community_post_notification_mutes') " +
                             "BEGIN " +
                             "    CREATE TABLE tbl_community_post_notification_mutes ( " +
                             "        id UNIQUEIDENTIFIER NOT NULL PRIMARY KEY DEFAULT NEWID(), " +
                             "        post_id UNIQUEIDENTIFIER NOT NULL, " +
                             "        user_id UNIQUEIDENTIFIER NOT NULL, " +
                             "        created_at DATETIME2 NOT NULL DEFAULT GETDATE(), " +
                             "        CONSTRAINT fk_post_mute_post FOREIGN KEY (post_id) REFERENCES tbl_community_posts(id) ON DELETE CASCADE, " +
                             "        CONSTRAINT fk_post_mute_user FOREIGN KEY (user_id) REFERENCES tbl_users(id) ON DELETE CASCADE, " +
                             "        CONSTRAINT uk_community_post_notification_mute_post_user UNIQUE (post_id, user_id) " +
                             "    ); " +
                             "END");
                log.info("Schema migration: tbl_community_post_notification_mutes table verified successfully.");
            } catch (Exception ex) {
                log.warn("Schema migration for tbl_community_post_notification_mutes: {}", ex.getMessage());
            }

        } catch (Exception e) {
            log.warn("Schema migration runner error: {}", e.getMessage());
        }
    }
}
