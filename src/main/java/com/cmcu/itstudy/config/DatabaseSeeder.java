package com.cmcu.itstudy.config;

import com.cmcu.itstudy.entity.Category;
import com.cmcu.itstudy.entity.Role;
import com.cmcu.itstudy.entity.Tag;
import com.cmcu.itstudy.entity.User;
import com.cmcu.itstudy.entity.UserRole;
import com.cmcu.itstudy.entity.Menu;
import com.cmcu.itstudy.entity.Permission;
import com.cmcu.itstudy.entity.MenuPermission;
import com.cmcu.itstudy.entity.RolePermission;
import com.cmcu.itstudy.repository.CategoryRepository;
import com.cmcu.itstudy.repository.RoleRepository;
import com.cmcu.itstudy.repository.TagRepository;
import com.cmcu.itstudy.repository.UserRepository;
import com.cmcu.itstudy.repository.UserRoleRepository;
import com.cmcu.itstudy.repository.MenuRepository;
import com.cmcu.itstudy.repository.PermissionRepository;
import com.cmcu.itstudy.repository.MenuPermissionRepository;
import com.cmcu.itstudy.repository.RolePermissionRepository;
import com.cmcu.itstudy.entity.Document;
import com.cmcu.itstudy.entity.DocumentTag;
import com.cmcu.itstudy.enums.DocumentStatus;
import com.cmcu.itstudy.enums.FileType;
import com.cmcu.itstudy.repository.DocumentRepository;
import com.cmcu.itstudy.repository.DocumentTagRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.List;

@Component
@ConditionalOnProperty(
        prefix = "app.database.seeder",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class DatabaseSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final PasswordEncoder passwordEncoder;
    private final CategoryRepository categoryRepository;
    private final TagRepository tagRepository;
    private final MenuRepository menuRepository;
    private final PermissionRepository permissionRepository;
    private final MenuPermissionRepository menuPermissionRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final DocumentRepository documentRepository;
    private final DocumentTagRepository documentTagRepository;
    private final JdbcTemplate jdbcTemplate;

    public DatabaseSeeder(
            UserRepository userRepository,
            RoleRepository roleRepository,
            UserRoleRepository userRoleRepository,
            PasswordEncoder passwordEncoder,
            CategoryRepository categoryRepository,
            TagRepository tagRepository,
            MenuRepository menuRepository,
            PermissionRepository permissionRepository,
            MenuPermissionRepository menuPermissionRepository,
            RolePermissionRepository rolePermissionRepository,
            DocumentRepository documentRepository,
            DocumentTagRepository documentTagRepository,
            JdbcTemplate jdbcTemplate
    ) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.userRoleRepository = userRoleRepository;
        this.passwordEncoder = passwordEncoder;
        this.categoryRepository = categoryRepository;
        this.tagRepository = tagRepository;
        this.menuRepository = menuRepository;
        this.permissionRepository = permissionRepository;
        this.menuPermissionRepository = menuPermissionRepository;
        this.rolePermissionRepository = rolePermissionRepository;
        this.documentRepository = documentRepository;
        this.documentTagRepository = documentTagRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        System.out.println("[DatabaseSeeder] Starting database initialization/seeding...");

        // 0. Auto-migrate missing columns for community posts & polls
        try {
            jdbcTemplate.execute("IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID(N'tbl_community_posts') AND name = N'allow_comments') " +
                    "ALTER TABLE tbl_community_posts ADD allow_comments BIT NOT NULL DEFAULT 1;");
            jdbcTemplate.execute("IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID(N'tbl_community_polls') AND name = N'allow_add_options') " +
                    "ALTER TABLE tbl_community_polls ADD allow_add_options BIT NOT NULL DEFAULT 0;");
            jdbcTemplate.execute("IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID(N'tbl_community_polls') AND name = N'hide_results_before_vote') " +
                    "ALTER TABLE tbl_community_polls ADD hide_results_before_vote BIT NOT NULL DEFAULT 0;");
            jdbcTemplate.execute("IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID(N'tbl_community_polls') AND name = N'hide_voters') " +
                    "ALTER TABLE tbl_community_polls ADD hide_voters BIT NOT NULL DEFAULT 0;");
            jdbcTemplate.execute("IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID(N'tbl_community_posts') AND name = N'is_hidden') " +
                    "ALTER TABLE tbl_community_posts ADD is_hidden BIT NOT NULL DEFAULT 0;");

            jdbcTemplate.execute("IF OBJECT_ID(N'tbl_community_post_reports', N'U') IS NULL " +
                    "CREATE TABLE tbl_community_post_reports (" +
                    "id UNIQUEIDENTIFIER NOT NULL DEFAULT NEWID() PRIMARY KEY, " +
                    "post_id UNIQUEIDENTIFIER NOT NULL, " +
                    "reporter_user_id UNIQUEIDENTIFIER NOT NULL, " +
                    "reason_code NVARCHAR(64) NOT NULL, " +
                    "detail NVARCHAR(MAX) NULL, " +
                    "status NVARCHAR(32) NOT NULL DEFAULT 'PENDING', " +
                    "created_at DATETIME2 NOT NULL DEFAULT GETDATE(), " +
                    "resolved_at DATETIME2 NULL, " +
                    "resolved_by_user_id UNIQUEIDENTIFIER NULL, " +
                    "CONSTRAINT fk_report_post FOREIGN KEY (post_id) REFERENCES tbl_community_posts(id), " +
                    "CONSTRAINT fk_report_reporter FOREIGN KEY (reporter_user_id) REFERENCES tbl_users(id), " +
                    "CONSTRAINT fk_report_resolver FOREIGN KEY (resolved_by_user_id) REFERENCES tbl_users(id), " +
                    "CONSTRAINT uq_one_report_per_user_post UNIQUE (post_id, reporter_user_id));");

            jdbcTemplate.execute("IF OBJECT_ID(N'tbl_notifications', N'U') IS NULL " +
                    "CREATE TABLE tbl_notifications (" +
                    "id UNIQUEIDENTIFIER NOT NULL DEFAULT NEWID() PRIMARY KEY, " +
                    "recipient_user_id UNIQUEIDENTIFIER NOT NULL, " +
                    "actor_user_id UNIQUEIDENTIFIER NULL, " +
                    "type NVARCHAR(64) NOT NULL, " +
                    "reference_id NVARCHAR(255) NULL, " +
                    "reference_type NVARCHAR(64) NULL, " +
                    "message NVARCHAR(500) NOT NULL, " +
                    "is_read BIT NOT NULL DEFAULT 0, " +
                    "created_at DATETIME2 NOT NULL DEFAULT GETDATE(), " +
                    "CONSTRAINT fk_notif_recipient FOREIGN KEY (recipient_user_id) REFERENCES tbl_users(id), " +
                    "CONSTRAINT fk_notif_actor FOREIGN KEY (actor_user_id) REFERENCES tbl_users(id));");

            // Auto-recreate notification mutes table if user_id type is not uniqueidentifier (e.g. varbinary)
            jdbcTemplate.execute("IF OBJECT_ID(N'tbl_community_post_notification_mutes', N'U') IS NOT NULL " +
                    "AND EXISTS (SELECT 1 FROM sys.columns c JOIN sys.types t ON c.user_type_id = t.user_type_id " +
                    "WHERE c.object_id = OBJECT_ID(N'tbl_community_post_notification_mutes') AND c.name = N'user_id' AND t.name <> N'uniqueidentifier') " +
                    "DROP TABLE tbl_community_post_notification_mutes;");

            jdbcTemplate.execute("IF OBJECT_ID(N'tbl_community_post_notification_mutes', N'U') IS NULL " +
                    "CREATE TABLE tbl_community_post_notification_mutes (" +
                    "id UNIQUEIDENTIFIER NOT NULL DEFAULT NEWID() PRIMARY KEY, " +
                    "post_id UNIQUEIDENTIFIER NOT NULL, " +
                    "user_id UNIQUEIDENTIFIER NOT NULL, " +
                    "created_at DATETIME2 NOT NULL DEFAULT GETDATE(), " +
                    "CONSTRAINT fk_mute_post FOREIGN KEY (post_id) REFERENCES tbl_community_posts(id), " +
                    "CONSTRAINT fk_mute_user FOREIGN KEY (user_id) REFERENCES tbl_users(id), " +
                    "CONSTRAINT uq_mute_user_post UNIQUE (post_id, user_id));");

            // Drop any old CHECK constraints generated by Hibernate on tbl_notifications.type column
            jdbcTemplate.execute(
                "DECLARE @chkName NVARCHAR(256); " +
                "SELECT TOP 1 @chkName = cc.name " +
                "FROM sys.check_constraints cc " +
                "JOIN sys.columns c ON cc.parent_object_id = c.object_id AND cc.parent_column_id = c.column_id " +
                "WHERE cc.parent_object_id = OBJECT_ID(N'tbl_notifications') AND c.name = N'type'; " +
                "WHILE @chkName IS NOT NULL " +
                "BEGIN " +
                "    EXEC(N'ALTER TABLE tbl_notifications DROP CONSTRAINT [' + @chkName + N'];'); " +
                "    SET @chkName = NULL; " +
                "    SELECT TOP 1 @chkName = cc.name " +
                "    FROM sys.check_constraints cc " +
                "    JOIN sys.columns c ON cc.parent_object_id = c.object_id AND cc.parent_column_id = c.column_id " +
                "    WHERE cc.parent_object_id = OBJECT_ID(N'tbl_notifications') AND c.name = N'type'; " +
                "END"
            );

            // Ensure Unicode (NVARCHAR) types for Vietnamese text columns
            jdbcTemplate.execute("ALTER TABLE tbl_community_posts ALTER COLUMN content NVARCHAR(MAX) NOT NULL;");
            jdbcTemplate.execute("ALTER TABLE tbl_community_polls ALTER COLUMN question NVARCHAR(500) NOT NULL;");
            jdbcTemplate.execute("ALTER TABLE tbl_community_poll_options ALTER COLUMN option_text NVARCHAR(255) NOT NULL;");
            jdbcTemplate.execute("UPDATE tbl_community_posts SET allow_comments = 1 WHERE allow_comments IS NULL OR allow_comments = 0;");
            jdbcTemplate.execute("UPDATE tbl_community_posts SET is_hidden = 0 WHERE is_hidden IS NULL;");
            jdbcTemplate.execute("UPDATE tbl_community_post_comments SET is_deleted = 0 WHERE is_deleted IS NULL;");
            jdbcTemplate.execute("UPDATE tbl_community_post_comments SET like_count = 0 WHERE like_count IS NULL;");

            // Category & Tag NVARCHAR column migration & cleanup
            jdbcTemplate.execute("IF EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID(N'tbl_categories') AND name = N'name') " +
                    "ALTER TABLE tbl_categories ALTER COLUMN name NVARCHAR(150) NOT NULL;");
            jdbcTemplate.execute("IF EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID(N'tbl_categories') AND name = N'description') " +
                    "ALTER TABLE tbl_categories ALTER COLUMN description NVARCHAR(500) NULL;");
            jdbcTemplate.execute("IF EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID(N'tbl_tags') AND name = N'name') " +
                    "ALTER TABLE tbl_tags ALTER COLUMN name NVARCHAR(100) NOT NULL;");
            jdbcTemplate.execute("IF EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID(N'tbl_post_tags') AND name = N'tag_name') " +
                    "ALTER TABLE tbl_post_tags ALTER COLUMN tag_name NVARCHAR(100) NOT NULL;");
            jdbcTemplate.execute("IF EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID(N'tbl_documents') AND name = N'title') " +
                    "ALTER TABLE tbl_documents ALTER COLUMN title NVARCHAR(255) NOT NULL;");
            jdbcTemplate.execute("IF EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID(N'tbl_documents') AND name = N'file_name') " +
                    "ALTER TABLE tbl_documents ALTER COLUMN file_name NVARCHAR(255) NULL;");
            jdbcTemplate.execute("IF EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID(N'tbl_documents') AND name = N'description') " +
                    "ALTER TABLE tbl_documents ALTER COLUMN description NVARCHAR(MAX) NULL;");
            jdbcTemplate.execute("IF EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID(N'tbl_documents') AND name = N'reject_reason') " +
                    "ALTER TABLE tbl_documents ALTER COLUMN reject_reason NVARCHAR(MAX) NULL;");

            // Make tbl_documents.category_id nullable if needed so foreign keys can be safely cleared
            jdbcTemplate.execute("IF EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID(N'tbl_documents') AND name = N'category_id' AND is_nullable = 0) " +
                    "ALTER TABLE tbl_documents ALTER COLUMN category_id UNIQUEIDENTIFIER NULL;");

            // Complete wipe of all old/garbage tags and document tag mappings
            jdbcTemplate.execute("DELETE FROM tbl_document_tags;");
            jdbcTemplate.execute("DELETE FROM tbl_tags;");

            // Complete wipe of all old/garbage/corrupted categories
            jdbcTemplate.execute("UPDATE tbl_documents SET category_id = NULL;");
            jdbcTemplate.execute("UPDATE tbl_categories SET parent_id = NULL;");
            jdbcTemplate.execute("DELETE FROM tbl_categories;");
        } catch (Exception e) {
            System.err.println("[DatabaseSeeder] Column migration & cleanup: " + e.getMessage());
        }

        // 1. Seed Roles
        Role userRole = seedRole("USER", "Normal User");
        Role adminRole = seedRole("ADMIN", "Administrator");
        Role contributorRole = seedRole("CONTRIBUTOR", "Contributor User");
        Role moderatorRole = seedRole("USER_MODERATOR", "Moderator User");
        Role contentModeratorRole = seedRole("CONTENT_MODERATOR", "Content Moderator User");
        Role communityModeratorRole = seedRole("COMMUNITY_MODERATOR", "Community Moderator User");

        // 2. Seed Users
        seedUser("user@example.com", "Normal User", userRole);
        seedUser("admin@example.com", "Administrator", adminRole);
        seedUser("contributor@example.com", "Contributor User", contributorRole);
        seedUser("moderator@example.com", "Moderator User", moderatorRole);
        seedUser("content_moderator@example.com", "Content Moderator User", contentModeratorRole);
        seedUser("community_moderator@example.com", "Community Moderator User", communityModeratorRole);

        // 3. Seed Standard IT Categories (Tiếng Việt chuẩn)
        Category catWeb = seedCategory("Lập trình Web", "lap-trinh-web", "Tài liệu phát triển Web: Frontend, Backend, Fullstack (HTML, CSS, React, Vue, Angular, Node.js, Spring Boot, ASP.NET...)", 1);
        Category catMobile = seedCategory("Lập trình Di động", "lap-trinh-di-dong", "Tài liệu phát triển ứng dụng di động: Android, iOS, Flutter, React Native, Kotlin, Swift...", 2);
        Category catDb = seedCategory("Cơ sở dữ liệu", "co-so-du-lieu", "Hệ quản trị CSDL quan hệ & NoSQL: SQL Server, MySQL, PostgreSQL, Oracle, MongoDB, Redis...", 3);
        Category catAi = seedCategory("Trí tuệ nhân tạo & Khoa học dữ liệu", "tri-tue-nhan-tao-khoa-hoc-du-lieu", "Tài liệu AI, Machine Learning, Deep Learning, Phân tích dữ liệu, Python, TensorFlow, PyTorch...", 4);
        Category catNet = seedCategory("Mạng máy tính & An toàn thông tin", "mang-may-tinh-an-toan-thong-tin", "Quản trị mạng, CCNA, An ninh mạng, Bảo mật hệ thống, Hacking đạo đức, SOC, Mật mã học...", 5);
        Category catDevOps = seedCategory("Kiến trúc phần mềm & DevOps", "kien-truc-phan-mem-devops", "Docker, Kubernetes, CI/CD, Microservices, Điện toán đám mây (AWS, Azure, GCP), System Design...", 6);
        Category catAlgo = seedCategory("Thuật toán & Cấu trúc dữ liệu", "thuat-toan-cau-truc-du-lieu", "Giáo trình CTDL & GT, Giải thuật nâng cao, Luyện thi thuật toán, Lập trình thi đấu ACM/ICPC...", 7);
        Category catSe = seedCategory("Công nghệ phần mềm & Đồ án", "cong-nghe-phan-mem-do-an", "Phân tích thiết kế hệ thống (UML), Quản lý dự án Agile/Scrum, Hướng dẫn làm Khóa luận & Đồ án tốt nghiệp...", 8);

        // Re-link any existing documents in DB to default web category if null
        try {
            jdbcTemplate.execute("UPDATE tbl_documents SET category_id = '" + catWeb.getId() + "' WHERE category_id IS NULL;");
        } catch (Exception e) {
            System.err.println("[DatabaseSeeder] Re-linking existing documents to default category: " + e.getMessage());
        }

        // 4. Seed Standard IT Tags (Phổ biến theo nhóm)
        // Ngôn ngữ lập trình
        seedTag("Java", "java");
        seedTag("Python", "python");
        seedTag("C / C++", "c-cpp");
        seedTag("C# (.NET)", "c-sharp");
        seedTag("JavaScript", "javascript");
        seedTag("TypeScript", "typescript");
        seedTag("PHP", "php");
        seedTag("Golang", "golang");
        seedTag("Kotlin", "kotlin");
        seedTag("Swift", "swift");
        seedTag("Rust", "rust");
        seedTag("Dart", "dart");

        // Frontend / Mobile
        seedTag("ReactJS", "reactjs");
        seedTag("Vue.js", "vuejs");
        seedTag("Angular", "angular");
        seedTag("Next.js", "nextjs");
        seedTag("HTML5 / CSS3", "html5-css3");
        seedTag("Tailwind CSS", "tailwind-css");
        seedTag("Flutter", "flutter");
        seedTag("React Native", "react-native");

        // Backend & Frameworks
        seedTag("Spring Boot", "spring-boot");
        seedTag("Node.js", "nodejs");
        seedTag("Express.js", "expressjs");
        seedTag("ASP.NET Core", "aspnet-core");
        seedTag("Laravel", "laravel");
        seedTag("NestJS", "nestjs");
        seedTag("Django", "django");
        seedTag("FastAPI", "fastapi");

        // CSDL & DevOps
        seedTag("SQL Server", "sql-server");
        seedTag("MySQL", "mysql");
        seedTag("PostgreSQL", "postgresql");
        seedTag("MongoDB", "mongodb");
        seedTag("Redis", "redis");
        seedTag("Docker", "docker");
        seedTag("Kubernetes", "kubernetes");
        seedTag("Git & GitHub", "git-github");
        seedTag("AWS", "aws");
        seedTag("Linux / Ubuntu", "linux-ubuntu");

        // Học thuật, AI & Chuyên đề
        seedTag("Machine Learning", "machine-learning");
        seedTag("Deep Learning", "deep-learning");
        seedTag("Data Science", "data-science");
        seedTag("Cấu trúc dữ liệu & Giải thuật", "cau-truc-du-lieu-giai-thuat");
        seedTag("An toàn thông tin", "an-toan-thong-tin");
        seedTag("Mạng máy tính", "mang-may-tinh");
        seedTag("Thiết kế hệ thống", "thiet-ke-he-thong");
        seedTag("Đồ án tốt nghiệp", "do-an-tot-nghiep");
        seedTag("Đề thi & Lời giải", "de-thi-va-loi-giai");
        seedTag("Trắc nghiệm IT", "trac-nghiem-it");

        // 5. Seed Permissions
        Permission pUserRead = seedPermission("USER_READ", "Read user list and details");
        Permission pUserWrite = seedPermission("USER_WRITE", "Create, edit, delete users");
        
        Permission pRoleRead = seedPermission("ROLE_READ", "Read role list and details");
        Permission pRoleWrite = seedPermission("ROLE_WRITE", "Create, edit, delete roles");
        
        Permission pPermRead = seedPermission("PERMISSION_READ", "Read permission list and details");
        Permission pPermWrite = seedPermission("PERMISSION_WRITE", "Create, edit, delete permissions");
        
        Permission pCatRead = seedPermission("CATEGORY_READ", "Read category list and details");
        Permission pCatWrite = seedPermission("CATEGORY_WRITE", "Create, edit, delete categories");
        
        Permission pTagRead = seedPermission("TAG_READ", "Read tag list and details");
        Permission pTagWrite = seedPermission("TAG_WRITE", "Create, edit, delete tags");
        
        Permission pContribRead = seedPermission("CONTRIBUTOR_REQUEST_READ", "Read contributor requests");
        Permission pContribWrite = seedPermission("CONTRIBUTOR_REQUEST_WRITE", "Approve/Reject contributor requests");
        
        Permission pDocRead = seedPermission("DOCUMENT_READ", "Read document list and details");
        Permission pDocWrite = seedPermission("DOCUMENT_WRITE", "Upload, edit, delete documents");
        
        Permission pReportRead = seedPermission("USER_REPORT_READ", "Read user reports");
        Permission pReportWrite = seedPermission("USER_REPORT_WRITE", "Handle user reports");
        
        Permission pConfigRead = seedPermission("SYSTEM_CONFIG_READ", "Read system settings");
        Permission pConfigWrite = seedPermission("SYSTEM_CONFIG_WRITE", "Edit system settings");

        Permission pCommunityMod = seedPermission("COMMUNITY_MODERATION", "Moderate community posts and comments");
        Permission pMenuCommunityMod = seedPermission("MENU_COMMUNITY_MODERATION", "Access Community Moderation menu");

        // Frontend UI Permissions
        Permission pProfileView = seedPermission("profile:view", "View personal profile");
        Permission pUserStatsView = seedPermission("user:statistics:view", "View user statistics");
        Permission pContribProfileView = seedPermission("contributor:profile:view", "View contributor profile");
        Permission pDocManage = seedPermission("document:manage", "Manage documents");
        Permission pQuizManage = seedPermission("quiz:manage", "Manage quizzes");
        Permission pBookmarkView = seedPermission("bookmark:view", "View favorite documents");
        Permission pHistQuizView = seedPermission("history:quiz:view", "View quiz history");
        Permission pHistDocView = seedPermission("history:document:view", "View document history");

        // Menu Permissions
        Permission pMenuDashboard = seedPermission("MENU_DASHBOARD", "Access Dashboard menu");
        Permission pMenuAccess = seedPermission("MENU_ACCESS_CONTROL", "Access Access Control parent menu");
        Permission pMenuUsers = seedPermission("MENU_USERS", "Access Users menu");
        Permission pMenuRoles = seedPermission("MENU_ROLES", "Access Roles menu");
        Permission pMenuPerms = seedPermission("MENU_PERMISSIONS", "Access Permissions menu");
        Permission pMenuCats = seedPermission("MENU_CATEGORIES", "Access Categories menu");
        Permission pMenuTags = seedPermission("MENU_TAGS", "Access Tags menu");
        Permission pMenuContribs = seedPermission("MENU_CONTRIBUTOR_REQUESTS", "Access Contributor Requests menu");
        Permission pMenuDocs = seedPermission("MENU_PENDING_DOCUMENTS", "Access Pending Documents menu");
        Permission pMenuReports = seedPermission("MENU_USER_REPORTS", "Access User Reports menu");
        Permission pMenuSettings = seedPermission("MENU_SETTINGS", "Access Settings menu");
        Permission pMenuUserAccount = seedPermission("MENU_USER_ACCOUNT", "Access User Account menu group");
        Permission pMenuContributorGroup = seedPermission("MENU_CONTRIBUTOR_GROUP", "Access Contributor Management menu group");

        // 6. Assign Permissions to Roles
        // ADMIN gets all permissions
        List<Permission> allPermissions = List.of(
            pUserRead, pUserWrite, pRoleRead, pRoleWrite, pPermRead, pPermWrite,
            pCatRead, pCatWrite, pTagRead, pTagWrite, pContribRead, pContribWrite,
            pDocRead, pDocWrite, pReportRead, pReportWrite, pConfigRead, pConfigWrite,
            pCommunityMod, pMenuCommunityMod,
            pMenuDashboard, pMenuAccess, pMenuUsers, pMenuRoles, pMenuPerms,
            pMenuCats, pMenuTags, pMenuContribs, pMenuDocs, pMenuReports, pMenuSettings,
            pProfileView, pUserStatsView, pContribProfileView, pDocManage, pQuizManage,
            pBookmarkView, pHistQuizView, pHistDocView
        );
        for (Permission perm : allPermissions) {
            seedRolePermission(adminRole, perm);
        }

        // CONTENT_MODERATOR gets category, tag, document, and report permissions + respective menus + user permissions
        List<Permission> contentModPermissions = List.of(
            pCatRead, pCatWrite, pTagRead, pTagWrite,
            pDocRead, pDocWrite, pReportRead, pReportWrite,
            pMenuDashboard, pMenuCats, pMenuTags, pMenuDocs, pMenuReports,
            pProfileView, pUserStatsView, pBookmarkView, pHistQuizView, pHistDocView
        );
        for (Permission perm : contentModPermissions) {
            seedRolePermission(contentModeratorRole, perm);
        }

        // USER_MODERATOR gets user and contributor request permissions + respective menus + user permissions
        List<Permission> userModPermissions = List.of(
            pUserRead, pUserWrite, pContribRead, pContribWrite,
            pMenuDashboard, pMenuUsers, pMenuContribs,
            pProfileView, pUserStatsView, pBookmarkView, pHistQuizView, pHistDocView
        );
        for (Permission perm : userModPermissions) {
            seedRolePermission(moderatorRole, perm);
        }

        // COMMUNITY_MODERATOR gets community moderation permissions + menu + user permissions
        List<Permission> communityModPermissions = List.of(
            pCommunityMod, pMenuCommunityMod, pReportRead, pReportWrite,
            pProfileView, pUserStatsView, pBookmarkView, pHistQuizView, pHistDocView
        );
        for (Permission perm : communityModPermissions) {
            seedRolePermission(communityModeratorRole, perm);
        }

        // USER gets user-level permissions
        List<Permission> userPermissions = List.of(
            pProfileView, pUserStatsView, pBookmarkView, pHistQuizView, pHistDocView, pMenuUserAccount
        );
        for (Permission perm : userPermissions) {
            seedRolePermission(userRole, perm);
        }

        // CONTRIBUTOR gets contributor-specific + user-level permissions
        List<Permission> contributorPermissions = List.of(
            pProfileView, pUserStatsView, pBookmarkView, pHistQuizView, pHistDocView,
            pContribProfileView, pDocManage, pQuizManage, pMenuUserAccount, pMenuContributorGroup
        );
        for (Permission perm : contributorPermissions) {
            seedRolePermission(contributorRole, perm);
        }

        // 7. Seed Menus
        // Ensure tbl_menus.name column is NVARCHAR(150) and cleanup any old corrupted/duplicate menu records before seeding
        try {
            jdbcTemplate.execute("ALTER TABLE tbl_menus ALTER COLUMN name NVARCHAR(150) NOT NULL;");
            jdbcTemplate.execute("DELETE FROM tbl_menu_permissions WHERE menu_id IN (" +
                    "SELECT id FROM tbl_menus WHERE CHARINDEX('?', name) > 0 " +
                    "OR route IN ('/profile', '/purchase-history', '/quiz-history', '/favorite-documents', '/view-history', '/contributor-profile', '/manage-documents', '/manage-quizzes', '/contributor/withdrawals', '/community/saved') " +
                    "OR name IN ('Saved Posts', 'Bài viết đã lưu', N'Tài khoản', N'Quản lý') " +
                    "OR (parent_id IS NOT NULL AND parent_id IN (SELECT id FROM tbl_menus WHERE name IN (N'Tài khoản', N'Quản lý'))));");
            
            jdbcTemplate.execute("DELETE FROM tbl_menus WHERE parent_id IN (SELECT id FROM tbl_menus WHERE name IN (N'Tài khoản', N'Quản lý') OR CHARINDEX('?', name) > 0);");
            jdbcTemplate.execute("DELETE FROM tbl_menus WHERE CHARINDEX('?', name) > 0 " +
                    "OR route IN ('/profile', '/purchase-history', '/quiz-history', '/favorite-documents', '/view-history', '/contributor-profile', '/manage-documents', '/manage-quizzes', '/contributor/withdrawals', '/community/saved') " +
                    "OR name IN ('Saved Posts', 'Bài viết đã lưu', N'Tài khoản', N'Quản lý');");
        } catch (Exception e) {
            System.err.println("[DatabaseSeeder] Menu table migration & cleanup error: " + e.getMessage());
        }

        Menu mDashboard = seedMenu("Dashboard", "/admin/dashboard", null, 1);
        seedMenuPermission(mDashboard, pMenuDashboard);

        Menu mAccessControl = seedMenu("Access Control", null, null, 2);
        seedMenuPermission(mAccessControl, pMenuAccess);

        Menu mUsers = seedMenu("Users", "/admin/users", mAccessControl, 1);
        seedMenuPermission(mUsers, pMenuUsers);

        Menu mRoles = seedMenu("Roles", "/admin/roles", mAccessControl, 2);
        seedMenuPermission(mRoles, pMenuRoles);

        Menu mPermissions = seedMenu("Permissions", "/admin/permissions", mAccessControl, 3);
        seedMenuPermission(mPermissions, pMenuPerms);

        Menu mCategories = seedMenu("Categories", "/admin/categories", null, 3);
        seedMenuPermission(mCategories, pMenuCats);

        Menu mTags = seedMenu("Tags", "/admin/tags", null, 4);
        seedMenuPermission(mTags, pMenuTags);

        Menu mContributorRequests = seedMenu("Contributor Requests", "/admin/contributor-requests", null, 5);
        seedMenuPermission(mContributorRequests, pMenuContribs);

        Menu mPendingDocs = seedMenu("Pending Documents", "/admin/documents/pending", null, 6);
        seedMenuPermission(mPendingDocs, pMenuDocs);

        Menu mUserReports = seedMenu("User Reports", "/admin/reports", null, 7);
        seedMenuPermission(mUserReports, pMenuReports);

        Menu mCommunityMod = seedMenu("Quản lý cộng đồng", "/admin/community-moderation", null, 8);
        seedMenuPermission(mCommunityMod, pMenuCommunityMod);

        Menu mSettings = seedMenu("Settings", "/admin/config", null, 9);
        seedMenuPermission(mSettings, pMenuSettings);

        // 7.1 Seed User Account & Contributor Menus for Avatar UserPopup
        Menu mUserAccount = seedMenu("Tài khoản", null, null, 10);
        seedMenuPermission(mUserAccount, pMenuUserAccount);

        Menu mProfile = seedMenu("Thông tin cá nhân", "/profile", mUserAccount, 1);
        seedMenuPermission(mProfile, pProfileView);

        Menu mPurchaseHistory = seedMenu("Lịch sử mua tài liệu", "/purchase-history", mUserAccount, 2);
        seedMenuPermission(mPurchaseHistory, pHistDocView);

        Menu mQuizHistory = seedMenu("Lịch sử làm bài", "/quiz-history", mUserAccount, 3);
        seedMenuPermission(mQuizHistory, pHistQuizView);

        Menu mFavoriteDocs = seedMenu("Tài liệu yêu thích", "/favorite-documents", mUserAccount, 4);
        seedMenuPermission(mFavoriteDocs, pBookmarkView);

        Menu mViewHistory = seedMenu("Lịch sử đã xem", "/view-history", mUserAccount, 5);
        seedMenuPermission(mViewHistory, pHistDocView);

        // Contributor Management Group
        Menu mContributorGroup = seedMenu("Quản lý", null, null, 11);
        seedMenuPermission(mContributorGroup, pMenuContributorGroup);

        Menu mContribProfile = seedMenu("Hồ sơ Người đóng góp", "/contributor-profile", mContributorGroup, 1);
        seedMenuPermission(mContribProfile, pContribProfileView);

        Menu mManageDocs = seedMenu("Quản lý tài liệu", "/manage-documents", mContributorGroup, 2);
        seedMenuPermission(mManageDocs, pDocManage);

        Menu mManageQuizzes = seedMenu("Quản lý đề thi", "/manage-quizzes", mContributorGroup, 3);
        seedMenuPermission(mManageQuizzes, pQuizManage);

        Menu mWithdrawalHub = seedMenu("Trung tâm rút tiền", "/contributor/withdrawals", mContributorGroup, 4);
        seedMenuPermission(mWithdrawalHub, pDocManage);

        // 6. Seed Sample Free Documents
        seedSampleFreeDocuments();

        System.out.println("[DatabaseSeeder] Database seeding completed successfully.");
    }

    private Permission seedPermission(String name, String description) {
        Optional<Permission> existing = permissionRepository.findByName(name);
        if (existing.isPresent()) {
            return existing.get();
        }
        LocalDateTime now = LocalDateTime.now();
        Permission permission = Permission.builder()
                .name(name)
                .description(description)
                .createdAt(now)
                .updatedAt(now)
                .build();
        Permission saved = permissionRepository.save(permission);
        System.out.println("[DatabaseSeeder] Created permission: " + name);
        return saved;
    }

    private void seedRolePermission(Role role, Permission permission) {
        RolePermission.RolePermissionId id = new RolePermission.RolePermissionId(role.getId(), permission.getId());
        if (!rolePermissionRepository.existsById(id)) {
            RolePermission rp = RolePermission.builder()
                    .roleId(role.getId())
                    .permissionId(permission.getId())
                    .createdAt(LocalDateTime.now())
                    .build();
            rolePermissionRepository.save(rp);
            System.out.println("[DatabaseSeeder] Associated role " + role.getName() + " with permission " + permission.getName());
        }
    }

    private Menu seedMenu(String name, String route, Menu parent, Integer displayOrder) {
        Optional<Menu> existingOpt = (parent == null)
                ? menuRepository.findByNameAndParentIsNull(name)
                : menuRepository.findByNameAndParent(name, parent);

        if (existingOpt.isPresent()) {
            Menu existing = existingOpt.get();
            if (!name.equals(existing.getName()) || (route != null && !route.equals(existing.getRoute()))) {
                existing.setName(name);
                if (route != null) existing.setRoute(route);
                existing.setUpdatedAt(LocalDateTime.now());
                return menuRepository.save(existing);
            }
            return existing;
        }

        LocalDateTime now = LocalDateTime.now();
        Menu menu = Menu.builder()
                .name(name)
                .route(route)
                .parent(parent)
                .displayOrder(displayOrder)
                .createdAt(now)
                .updatedAt(now)
                .build();
        Menu saved = menuRepository.save(menu);
        System.out.println("[DatabaseSeeder] Created menu: " + name);
        return saved;
    }

    private void seedMenuPermission(Menu menu, Permission permission) {
        if (!menuPermissionRepository.existsByMenuIdAndPermissionId(menu.getId(), permission.getId())) {
            LocalDateTime now = LocalDateTime.now();
            MenuPermission mp = MenuPermission.builder()
                    .menu(menu)
                    .permission(permission)
                    .createdAt(now)
                    .updatedAt(now)
                    .build();
            menuPermissionRepository.save(mp);
            System.out.println("[DatabaseSeeder] Associated menu " + menu.getName() + " with permission " + permission.getName());
        }
    }

    private Role seedRole(String name, String description) {
        Optional<Role> existingRole = roleRepository.findByName(name);
        if (existingRole.isPresent()) {
            return existingRole.get();
        }

        LocalDateTime now = LocalDateTime.now();
        Role role = Role.builder()
                .name(name)
                .description(description)
                .active(Boolean.TRUE)
                .createdAt(now)
                .updatedAt(now)
                .build();

        Role saved = roleRepository.save(role);
        System.out.println("[DatabaseSeeder] Created role: " + name);
        return saved;
    }

    private User seedUser(String email, String fullName, Role role) {
        return seedUser(email, fullName, null, role);
    }

    private User seedUser(String email, String fullName, String avatarUrl, Role role) {
        Optional<User> existingUserOpt = userRepository.findByEmail(email);
        LocalDateTime now = LocalDateTime.now();
        User user;

        if (existingUserOpt.isPresent()) {
            user = existingUserOpt.get();
            if (avatarUrl != null && (user.getAvatarUrl() == null || user.getAvatarUrl().isBlank())) {
                user.setAvatarUrl(avatarUrl);
                user = userRepository.save(user);
            }
        } else {
            user = User.builder()
                    .email(email)
                    .password(passwordEncoder.encode("password123"))
                    .fullName(fullName)
                    .avatarUrl(avatarUrl)
                    .status("ACTIVE")
                    .emailVerified(true)
                    .createdAt(now)
                    .updatedAt(now)
                    .build();
            user = userRepository.save(user);
            System.out.println("[DatabaseSeeder] Created user: " + email);
        }

        // Ensure user is associated with the role
        UserRole.UserRoleId userRoleId = new UserRole.UserRoleId(user.getId(), role.getId());
        if (!userRoleRepository.existsById(userRoleId)) {
            UserRole userRole = UserRole.builder()
                    .userId(user.getId())
                    .roleId(role.getId())
                    .createdAt(now)
                    .build();
            userRoleRepository.save(userRole);
            System.out.println("[DatabaseSeeder] Associated user " + email + " with role " + role.getName());
        }
        return user;
    }

    private Category seedCategory(String name, String slug, String description, int displayOrder) {
        Optional<Category> existing = categoryRepository.findBySlug(slug);
        if (existing.isPresent()) {
            Category c = existing.get();
            c.setName(name);
            c.setDescription(description);
            c.setDisplayOrder(displayOrder);
            c.setActive(Boolean.TRUE);
            c.setUpdatedAt(LocalDateTime.now());
            return categoryRepository.save(c);
        }
        Optional<Category> byName = categoryRepository.findByName(name);
        if (byName.isPresent()) {
            Category c = byName.get();
            c.setSlug(slug);
            c.setDescription(description);
            c.setDisplayOrder(displayOrder);
            c.setActive(Boolean.TRUE);
            c.setUpdatedAt(LocalDateTime.now());
            return categoryRepository.save(c);
        }
        Category category = Category.builder()
                .name(name)
                .slug(slug)
                .description(description)
                .active(Boolean.TRUE)
                .displayOrder(displayOrder)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        Category saved = categoryRepository.save(category);
        System.out.println("[DatabaseSeeder] Created category: " + name);
        return saved;
    }

    private Tag seedTag(String name, String slug) {
        Optional<Tag> existing = tagRepository.findBySlug(slug);
        if (existing.isPresent()) {
            Tag t = existing.get();
            t.setName(name);
            t.setActive(Boolean.TRUE);
            t.setUpdatedAt(LocalDateTime.now());
            return tagRepository.save(t);
        }
        Optional<Tag> byName = tagRepository.findByName(name);
        if (byName.isPresent()) {
            Tag t = byName.get();
            t.setSlug(slug);
            t.setActive(Boolean.TRUE);
            t.setUpdatedAt(LocalDateTime.now());
            return tagRepository.save(t);
        }
        Tag tag = Tag.builder()
                .name(name)
                .slug(slug)
                .usageCount(0L)
                .active(Boolean.TRUE)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        Tag saved = tagRepository.save(tag);
        System.out.println("[DatabaseSeeder] Created tag: " + name);
        return saved;
    }

    private void seedDocumentTag(Document document, Tag tag) {
        if (document == null || tag == null) return;
        DocumentTag.DocumentTagId id = new DocumentTag.DocumentTagId(document.getId(), tag.getId());
        if (!documentTagRepository.existsById(id)) {
            DocumentTag dt = DocumentTag.builder()
                    .documentId(document.getId())
                    .tagId(tag.getId())
                    .document(document)
                    .tag(tag)
                    .createdAt(LocalDateTime.now())
                    .build();
            documentTagRepository.save(dt);
            tag.setUsageCount((tag.getUsageCount() != null ? tag.getUsageCount() : 0L) + 1L);
            tagRepository.save(tag);
        }
    }

    private void seedSampleFreeDocuments() {
        Role contributorRole = roleRepository.findByName("CONTRIBUTOR")
                .orElseGet(() -> roleRepository.findAll().stream().findFirst().orElse(null));
        if (contributorRole == null) return;

        Category webCat = categoryRepository.findByName("Lập trình Web")
                .orElseGet(() -> categoryRepository.findAll().stream().findFirst().orElse(null));
        Category dbCat = categoryRepository.findByName("Cơ sở dữ liệu")
                .orElse(webCat);
        Category aiCat = categoryRepository.findByName("Trí tuệ nhân tạo & Khoa học dữ liệu")
                .orElse(webCat);
        Category netCat = categoryRepository.findByName("Mạng máy tính & An toàn thông tin")
                .orElse(webCat);
        Category algoCat = categoryRepository.findByName("Thuật toán & Cấu trúc dữ liệu")
                .orElse(webCat);

        if (webCat == null) return;

        // Tags for sample documents
        Tag tagCtdl = tagRepository.findBySlug("cau-truc-du-lieu-giai-thuat").orElse(null);
        Tag tagJava = tagRepository.findBySlug("java").orElse(null);
        Tag tagCpp = tagRepository.findBySlug("c-cpp").orElse(null);
        Tag tagSpringBoot = tagRepository.findBySlug("spring-boot").orElse(null);
        Tag tagReact = tagRepository.findBySlug("reactjs").orElse(null);
        Tag tagSqlServer = tagRepository.findBySlug("sql-server").orElse(null);
        Tag tagNode = tagRepository.findBySlug("nodejs").orElse(null);
        Tag tagAi = tagRepository.findBySlug("machine-learning").orElse(null);
        Tag tagPython = tagRepository.findBySlug("python").orElse(null);
        Tag tagNetwork = tagRepository.findBySlug("mang-may-tinh").orElse(null);
        Tag tagDocker = tagRepository.findBySlug("docker").orElse(null);
        Tag tagQuiz = tagRepository.findBySlug("trac-nghiem-it").orElse(null);
        Tag tagExam = tagRepository.findBySlug("de-thi-va-loi-giai").orElse(null);

        // Seed 8 distinct users with avatars
        User u1 = seedUser("esther.howard@example.com", "Esther Howard", "https://images.unsplash.com/photo-1494790108377-be9c29b29330?w=200&auto=format&fit=crop&q=80", contributorRole);
        User u2 = seedUser("cody.fisher@example.com", "Cody Fisher", "https://images.unsplash.com/photo-1539571696357-5a69c17a67c6?w=200&auto=format&fit=crop&q=80", contributorRole);
        User u3 = seedUser("jane.cooper@example.com", "Jane Cooper", "https://images.unsplash.com/photo-1517841905240-472988babdf9?w=200&auto=format&fit=crop&q=80", contributorRole);
        User u4 = seedUser("cameron.w@example.com", "Cameron Williamson", "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=200&auto=format&fit=crop&q=80", contributorRole);
        User u5 = seedUser("marvin.m@example.com", "Marvin McKinney", "https://images.unsplash.com/photo-1500648767791-00dcc994a43e?w=200&auto=format&fit=crop&q=80", contributorRole);
        User u6 = seedUser("darrell.s@example.com", "Darrell Steward", "https://images.unsplash.com/photo-1522075469751-3a6694fb2f61?w=200&auto=format&fit=crop&q=80", contributorRole);
        User u7 = seedUser("kristin.w@example.com", "Kristin Watson", "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=200&auto=format&fit=crop&q=80", contributorRole);
        User u8 = seedUser("devon.l@example.com", "Devon Lane", "https://images.unsplash.com/photo-1506794778202-cad84cf45f1d?w=200&auto=format&fit=crop&q=80", contributorRole);

        // User 1 - Esther Howard
        Document d1 = seedOneFreeDoc(
                "Giáo trình Cấu trúc Dữ liệu và Giải thuật (CTDL & GT)",
                "giao-trinh-cau-truc-du-lieu-va-giai-thuat",
                "Tổng hợp toàn bộ kiến thức về cấu trúc dữ liệu mảng, danh sách liên kết, ngăn xếp (Stack), hàng đợi (Queue), cây nhị phân, đồ thị và các thuật toán sắp xếp, tìm kiếm kinh điển.",
                algoCat,
                u1,
                3200L,
                950L,
                85L
        );
        seedDocumentTag(d1, tagCtdl);
        seedDocumentTag(d1, tagCpp);

        Document d2 = seedOneFreeDoc(
                "Tuyển tập 100 Bài toán Lập trình thi đấu ACM/ICPC chọn lọc",
                "tuyen-tap-100-bai-toan-lap-trinh-thi-dau-acm-icpc",
                "Phân tích độ phức tạp thuật toán và lời giải chi tiết bằng C++ và Java cho các bài toán quy hoạch động, đồ thị và hình học tính toán.",
                algoCat,
                u1,
                1800L,
                550L,
                70L
        );
        seedDocumentTag(d2, tagCtdl);
        seedDocumentTag(d2, tagCpp);
        seedDocumentTag(d2, tagJava);

        // User 2 - Cody Fisher
        Document d3 = seedOneFreeDoc(
                "Bộ câu hỏi trắc nghiệm & Bài tập Lập trình Java từ Cơ bản đến Nâng cao",
                "bo-cau-hoi-trac-nghiem-va-bai-tap-lap-trinh-java",
                "Tài liệu ôn tập toàn diện Java Core, Lập trình hướng đối tượng (OOP), Java Collection Framework, Đa luồng (Multi-threading) và Java 8 Stream API kèm đáp án chi tiết.",
                webCat,
                u2,
                2670L,
                700L,
                140L
        );
        seedDocumentTag(d3, tagJava);
        seedDocumentTag(d3, tagQuiz);

        Document d4 = seedOneFreeDoc(
                "Lập trình Web chuyên nghiệp với Spring Boot 3 và Spring Security 6",
                "lap-trinh-web-chuyen-nghiep-voi-spring-boot-3",
                "Hướng dẫn xây dựng hệ thống xác thực JWT, OAuth2 Google, quản lý quyền hạn phân tầng RBAC và tối ưu kết nối Hibernate MSSQL.",
                webCat,
                u2,
                2000L,
                500L,
                110L
        );
        seedDocumentTag(d4, tagJava);
        seedDocumentTag(d4, tagSpringBoot);

        // User 3 - Jane Cooper
        Document d5 = seedOneFreeDoc(
                "Tài liệu Thiết kế & Tối ưu Cơ sở dữ liệu quan hệ SQL Server",
                "tai-lieu-thiet-ke-va-toi-uu-co-so-du-lieu-sql-server",
                "Hướng dẫn chuẩn hóa dữ liệu 1NF đến 3NF, kỹ thuật tối ưu câu truy vấn nâng cao với Index, Transaction, Stored Procedure và Trigger trong SQL Server.",
                dbCat,
                u3,
                2400L,
                600L,
                95L
        );
        seedDocumentTag(d5, tagSqlServer);

        Document d6 = seedOneFreeDoc(
                "Giáo trình Phân tích và Thiết kế Hệ thống Thông tin chuẩn UML",
                "giao-trinh-phan-tich-va-thiet-ke-he-thong-thong-tin",
                "Phương pháp lập sơ đồ Use Case, Class Diagram, Sequence Diagram và thiết kế cơ sở dữ liệu quan hệ cho các dự án phần mềm doanh nghiệp.",
                dbCat,
                u3,
                1500L,
                350L,
                65L
        );
        seedDocumentTag(d6, tagSqlServer);

        // User 4 - Cameron Williamson
        Document d7 = seedOneFreeDoc(
                "Đề thi và Lời giải mẫu Lập trình Web Fullstack (Spring Boot & ReactJS)",
                "de-thi-va-loi-giai-mau-lap-trinh-web-fullstack",
                "Tuyển tập đề thi thực hành xây dựng hệ thống REST API chuẩn MVC với Spring Boot kết hợp giao diện Single Page Application hiện đại với ReactJS.",
                webCat,
                u4,
                2930L,
                780L,
                210L
        );
        seedDocumentTag(d7, tagSpringBoot);
        seedDocumentTag(d7, tagReact);
        seedDocumentTag(d7, tagExam);

        // User 5 - Marvin McKinney
        Document d8 = seedOneFreeDoc(
                "Hướng dẫn xây dựng RESTful API chuẩn quốc tế với Node.js & Express",
                "huong-dan-xay-dung-restful-api-voi-nodejs",
                "Thiết kế kiến trúc tầng Clean Architecture, xác thực JWT, kết nối MongoDB Mongoose và viết tài liệu Swagger OpenAPI tự động.",
                webCat,
                u5,
                2560L,
                650L,
                125L
        );
        seedDocumentTag(d8, tagNode);

        // User 6 - Darrell Steward
        Document d9 = seedOneFreeDoc(
                "Tổng quan Trí tuệ Nhân tạo (AI) và Học máy cơ bản cho người mới bắt đầu",
                "tong-quan-tri-tue-nhan-tao-va-hoc-may-co-ban",
                "Giới thiệu các mô hình Machine Learning cơ bản: Hồi quy tuyến tính (Linear Regression), Cây quyết định (Decision Tree), Random Forest và Mạng nơ-ron nhân tạo (ANN).",
                aiCat,
                u6,
                1890L,
                490L,
                60L
        );
        seedDocumentTag(d9, tagAi);
        seedDocumentTag(d9, tagPython);

        // User 7 - Kristin Watson
        Document d10 = seedOneFreeDoc(
                "Giáo trình Mạng máy tính và An toàn thông tin căn bản",
                "giao-trinh-mang-may-tinh-va-an-toan-thong-tin",
                "Tìm hiểu mô hình OSI 7 tầng, giao thức TCP/IP, định tuyến Router, bảo mật tường lửa Firewall và mã hóa dữ liệu RSA/AES.",
                netCat,
                u7,
                1040L,
                280L,
                45L
        );
        seedDocumentTag(d10, tagNetwork);

        // User 8 - Devon Lane
        Document d11 = seedOneFreeDoc(
                "Tài liệu Tự học Docker & Kubernetes từ Con số 0",
                "tai-lieu-tu-hoc-docker-va-kubernetes",
                "Đóng gói ứng dụng Container, viết Dockerfile, Docker Compose và quản lý cụm dịch vụ tự động mở rộng trên Kubernetes.",
                netCat,
                u8,
                1025L,
                260L,
                55L
        );
        seedDocumentTag(d11, tagDocker);

        System.out.println("[DatabaseSeeder] Seeded sample free documents with clean Vietnamese categories & tags successfully.");
    }

    private Document seedOneFreeDoc(
            String title,
            String slug,
            String description,
            Category category,
            User author,
            Long viewCount,
            Long downloadCount,
            Long bookmarkCount
    ) {
        Optional<Document> existingOpt = documentRepository.findBySlug(slug);
        if (existingOpt.isPresent()) {
            Document doc = existingOpt.get();
            if (category != null) {
                doc.setCategory(category);
                doc = documentRepository.save(doc);
            }
            return doc;
        }

        LocalDateTime now = LocalDateTime.now();
        Document doc = Document.builder()
                .title(title)
                .slug(slug)
                .description(description)
                .content(description)
                .category(category)
                .createdBy(author)
                .status(DocumentStatus.APPROVED)
                .fileType(FileType.PDF)
                .fileUrl("https://pdfobject.com/pdf/sample.pdf")
                .thumbnailUrl("https://images.unsplash.com/photo-1517694712202-14dd9538aa97?w=600&auto=format&fit=crop&q=80")
                .fileName(slug + ".pdf")
                .fileSize(1024L * 1024L * 2) // 2MB
                .isPaid(Boolean.FALSE)
                .price(0L)
                .viewCount(viewCount)
                .downloadCount(downloadCount)
                .bookmarkCount(bookmarkCount)
                .publishedAt(now.minusDays(5))
                .createdAt(now.minusDays(10))
                .updatedAt(now)
                .deleted(Boolean.FALSE)
                .hidden(Boolean.FALSE)
                .build();

        Document saved = documentRepository.save(doc);
        System.out.println("[DatabaseSeeder] Created free document: " + title);
        return saved;
    }
}
