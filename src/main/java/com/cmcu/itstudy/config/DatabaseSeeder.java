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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.List;

@Component
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

            // Ensure Unicode (NVARCHAR) types for Vietnamese text columns
            jdbcTemplate.execute("ALTER TABLE tbl_community_posts ALTER COLUMN content NVARCHAR(MAX) NOT NULL;");
            jdbcTemplate.execute("ALTER TABLE tbl_community_polls ALTER COLUMN question NVARCHAR(500) NOT NULL;");
            jdbcTemplate.execute("ALTER TABLE tbl_community_poll_options ALTER COLUMN option_text NVARCHAR(255) NOT NULL;");
        } catch (Exception e) {
            System.err.println("[DatabaseSeeder] Column migration: " + e.getMessage());
        }

        // 1. Seed Roles
        Role userRole = seedRole("USER", "Normal User");
        Role adminRole = seedRole("ADMIN", "Administrator");
        Role contributorRole = seedRole("CONTRIBUTOR", "Contributor User");
        Role moderatorRole = seedRole("USER_MODERATOR", "Moderator User");
        Role contentModeratorRole = seedRole("CONTENT_MODERATOR", "Content Moderator User");

        // 2. Seed Users
        seedUser("user@example.com", "Normal User", userRole);
        seedUser("admin@example.com", "Administrator", adminRole);
        seedUser("contributor@example.com", "Contributor User", contributorRole);
        seedUser("moderator@example.com", "Moderator User", moderatorRole);
        seedUser("content_moderator@example.com", "Content Moderator User", contentModeratorRole);

        // 3. Seed Categories
        seedCategory("Lập trình Web", "lap-trinh-web", "Tài liệu về phát triển web: HTML, CSS, React, Spring Boot...");
        seedCategory("Lập trình Di động", "lap-trinh-di-dong", "Tài liệu phát triển ứng dụng di động: Flutter, React Native, Android...");
        seedCategory("Cơ sở dữ liệu", "co-so-du-lieu", "Tài liệu về SQL Server, PostgreSQL, MySQL, MongoDB...");
        seedCategory("Trí tuệ nhân tạo", "tri-tue-nhan-tao", "Tài liệu AI, Machine Learning, Deep Learning...");
        seedCategory("Mạng & Bảo mật", "mang-va-bao-mat", "Tài liệu quản trị mạng, CCNA, an toàn thông tin...");

        // 4. Seed Tags
        seedTag("Java", "java");
        seedTag("Spring Boot", "spring-boot");
        seedTag("React", "react");
        seedTag("React Native", "react-native");
        seedTag("SQL Server", "sql-server");
        seedTag("Python", "python");
        seedTag("Docker", "docker");
        seedTag("Git", "git");

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

        // 6. Assign Permissions to Roles
        // ADMIN gets all permissions
        List<Permission> allPermissions = List.of(
            pUserRead, pUserWrite, pRoleRead, pRoleWrite, pPermRead, pPermWrite,
            pCatRead, pCatWrite, pTagRead, pTagWrite, pContribRead, pContribWrite,
            pDocRead, pDocWrite, pReportRead, pReportWrite, pConfigRead, pConfigWrite,
            pMenuDashboard, pMenuAccess, pMenuUsers, pMenuRoles, pMenuPerms,
            pMenuCats, pMenuTags, pMenuContribs, pMenuDocs, pMenuReports, pMenuSettings,
            pProfileView, pUserStatsView, pContribProfileView, pDocManage, pQuizManage,
            pBookmarkView, pHistQuizView, pHistDocView
        );
        for (Permission perm : allPermissions) {
            seedRolePermission(adminRole, perm);
        }

        // CONTENT_MODERATOR gets document and contributor request permissions + respective menus + user permissions
        List<Permission> contentModPermissions = List.of(
            pContribRead, pContribWrite, pDocRead, pDocWrite,
            pMenuDashboard, pMenuContribs, pMenuDocs,
            pProfileView, pUserStatsView, pBookmarkView, pHistQuizView, pHistDocView
        );
        for (Permission perm : contentModPermissions) {
            seedRolePermission(contentModeratorRole, perm);
        }

        // USER_MODERATOR gets user read and report permissions + respective menus + user permissions
        List<Permission> userModPermissions = List.of(
            pUserRead, pReportRead, pReportWrite,
            pMenuReports,
            pProfileView, pUserStatsView, pBookmarkView, pHistQuizView, pHistDocView
        );
        for (Permission perm : userModPermissions) {
            seedRolePermission(moderatorRole, perm);
        }

        // USER gets user-level permissions
        List<Permission> userPermissions = List.of(
            pProfileView, pUserStatsView, pBookmarkView, pHistQuizView, pHistDocView
        );
        for (Permission perm : userPermissions) {
            seedRolePermission(userRole, perm);
        }

        // CONTRIBUTOR gets contributor-specific + user-level permissions
        List<Permission> contributorPermissions = List.of(
            pProfileView, pUserStatsView, pBookmarkView, pHistQuizView, pHistDocView,
            pContribProfileView, pDocManage, pQuizManage
        );
        for (Permission perm : contributorPermissions) {
            seedRolePermission(contributorRole, perm);
        }

        // 7. Seed Menus
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

        Menu mSettings = seedMenu("Settings", "/admin/config", null, 8);
        seedMenuPermission(mSettings, pMenuSettings);

        // User / Community Saved Posts Menu
        Menu mSavedPosts = seedMenu("Bài viết đã lưu", "/community/saved", null, 9);
        seedMenuPermission(mSavedPosts, pBookmarkView);

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
            return existingOpt.get();
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

    private void seedUser(String email, String fullName, Role role) {
        Optional<User> existingUserOpt = userRepository.findByEmail(email);
        LocalDateTime now = LocalDateTime.now();
        User user;

        if (existingUserOpt.isPresent()) {
            user = existingUserOpt.get();
            System.out.println("[DatabaseSeeder] User already exists: " + email + ". Ensuring role association...");
        } else {
            user = User.builder()
                    .email(email)
                    .password(passwordEncoder.encode("password123"))
                    .fullName(fullName)
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
    }

    private void seedCategory(String name, String slug, String description) {
        if (categoryRepository.existsBySlug(slug)) {
            return;
        }
        Category category = Category.builder()
                .name(name)
                .slug(slug)
                .description(description)
                .active(Boolean.TRUE)
                .displayOrder(0)
                .build();
        categoryRepository.save(category);
        System.out.println("[DatabaseSeeder] Created category: " + name);
    }

    private void seedTag(String name, String slug) {
        if (tagRepository.existsBySlug(slug)) {
            return;
        }
        Tag tag = Tag.builder()
                .name(name)
                .slug(slug)
                .usageCount(0L)
                .active(Boolean.TRUE)
                .build();
        tagRepository.save(tag);
        System.out.println("[DatabaseSeeder] Created tag: " + name);
    }
}
