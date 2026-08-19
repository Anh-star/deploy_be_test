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

            // 2. Add escalation fields to tbl_community_post_reports if not exists
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
                log.info("Schema migration: tbl_community_post_reports escalation columns verified successfully.");
            } catch (Exception ex) {
                log.warn("Schema migration for tbl_community_post_reports: {}", ex.getMessage());
            }

        } catch (Exception e) {
            log.warn("Schema migration runner error: {}", e.getMessage());
        }
    }
}
