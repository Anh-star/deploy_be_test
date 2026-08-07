package com.cmcu.itstudy.service.impl;

import com.cmcu.itstudy.dto.document.PreviewMode;
import com.cmcu.itstudy.entity.Document;
import com.cmcu.itstudy.entity.User;
import com.cmcu.itstudy.enums.DocumentStatus;
import com.cmcu.itstudy.service.contract.PreviewAccessDecisionService;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Default implementation of {@link PreviewAccessDecisionService}.
 *
 * <h2>Role policy</h2>
 * <p>The service intentionally does NOT use coarse roles to gate full
 * preview access. Generic roles such as {@code ROLE_ADMIN},
 * {@code ROLE_USER_MODERATOR} and {@code ROLE_CONTENT_MODERATOR} are
 * NOT sufficient on their own to obtain the full paid preview. Only
 * the exact document-approval permission
 * ({@link #PERMISSION_APPROVE_DOCUMENT}) grants the moderator branch,
 * and only the exact super-admin permission ({@link #PERMISSION_SUPER_ADMIN})
 * grants the super-admin branch.</p>
 *
 * <h2>Allowed FULL paths</h2>
 * <ul>
 *   <li><b>Owner</b>: the request's {@code currentUser.id} equals
 *       {@code document.createdBy.id}. Owner FULL is honored on every
 *       status (PENDING / APPROVED / REJECTED) so the contributor can
 *       see their own file.</li>
 *   <li><b>Purchaser</b>: a valid {@code DocumentAccess} row exists for
 *       the viewer (signal is propagated by the caller via
 *       {@code hasPurchaserAccess}). Purchaser FULL is honored on
 *       APPROVED paid documents only; PENDING / REJECTED documents
 *       remain hidden from purchasers.</li>
 *   <li><b>Document approver</b>: viewer holds the exact permission
 *       {@link #PERMISSION_APPROVE_DOCUMENT}. This is the ONLY way the
 *       moderator branch is unlocked — generic moderator role names do
 *       not grant full preview access.</li>
 *   <li><b>Super admin</b>: viewer holds BOTH {@code ROLE_ADMIN} and
 *       {@link #PERMISSION_SUPER_ADMIN}. Without the SUPER_ADMIN
 *       permission the {@code ROLE_ADMIN} carrier is treated as an
 *       ordinary authenticated user.</li>
 * </ul>
 *
 * <h2>USER_MODERATOR hardening</h2>
 * <p>User Moderators ({@link #ROLE_USER_MODERATOR}) are deliberately
 * NOT in the moderator branch. Even when the legacy
 * {@code AdminDocumentController} exposes moderation endpoints to them
 * for non-preview actions, this preview endpoint treats them as an
 * unrelated authenticated user:</p>
 * <ul>
 *   <li>Approved paid PDF → LIMITED</li>
 *   <li>Approved paid non-PDF → LOCKED</li>
 *   <li>Pending / Rejected → denied outright</li>
 * </ul>
 *
 * <h2>Pending / Rejected matrix</h2>
 * <p>PENDING / REJECTED documents return FULL only to the owner, the
 * exact approver permission, or the super-admin combination. Purchaser,
 * user moderator, generic admin, unrelated contributor, authenticated
 * viewer, and guest are all denied.</p>
 */
@Service
public class PreviewAccessDecisionServiceImpl implements PreviewAccessDecisionService {

    /** Coarse admin role carried by every site administrator. NOT a
     * sufficient signal for full preview — must be combined with
     * {@link #PERMISSION_SUPER_ADMIN} or with
     * {@link #PERMISSION_APPROVE_DOCUMENT}. */
    static final String ROLE_ADMIN = "ROLE_ADMIN";

    /** Legacy moderator role label retained for backward compatibility
     * with admin navigation labels. NOT used by this decision service
     * to grant FULL preview. */
    static final String ROLE_CONTENT_MODERATOR = "ROLE_CONTENT_MODERATOR";

    /** User moderator role. NOT used by this decision service to grant
     * FULL preview. User moderators are treated as ordinary
     * authenticated viewers for preview purposes. */
    static final String ROLE_USER_MODERATOR = "ROLE_USER_MODERATOR";

    /** Source-consistent permission name for the document approval
     * authority. A user holding this permission is treated as a content
     * moderator for preview purposes. */
    static final String PERMISSION_SUPER_ADMIN = "SUPER_ADMIN";

    /** Source-consistent permission name for the document approval
     * authority. A user holding this permission is treated as a content
     * moderator for preview purposes. This is the ONLY way the
     * moderator branch is unlocked. */
    static final String PERMISSION_APPROVE_DOCUMENT = "APPROVE_DOCUMENT";

    @Override
    public Decision decide(Document document,
                           User currentUser,
                           Collection<? extends GrantedAuthority> authorities,
                           boolean hasPurchaserAccess) {
        Objects.requireNonNull(document, "document");

        // The moderator branch is permission-only. ROLE_CONTENT_MODERATOR,
        // ROLE_ADMIN, and ROLE_USER_MODERATOR are deliberately ignored.
        boolean moderatorAccess = hasAuthority(authorities, PERMISSION_APPROVE_DOCUMENT);

        // The super-admin branch is the exact (ROLE_ADMIN +
        // SUPER_ADMIN) combination. ROLE_ADMIN alone is not enough.
        boolean superAdminAccess = hasAuthority(authorities, ROLE_ADMIN)
                && hasAuthority(authorities, PERMISSION_SUPER_ADMIN);

        boolean ownerAccess = isOwner(document, currentUser);

        DocumentStatus status = document.getStatus();
        boolean paid = Boolean.TRUE.equals(document.getIsPaid());

        // Pending / Rejected: owner / approver / super-admin only.
        // Purchaser, user moderator, generic admin, unrelated
        // contributor, authenticated viewer, and guest are all denied.
        // A pending document MUST NOT be exposed as a limited preview.
        if (status == DocumentStatus.PENDING || status == DocumentStatus.REJECTED) {
            boolean pendingAllowed = ownerAccess || moderatorAccess || superAdminAccess;
            PreviewMode mode = pendingAllowed ? PreviewMode.FULL : null;
            return new Decision(mode, ownerAccess, false, moderatorAccess,
                    superAdminAccess, pendingAllowed);
        }

        // Approved documents.
        // Purchaser FULL is conditional on the viewer being a real
        // purchaser — hasPurchaserAccess comes from the DocumentAccess
        // service and is never inferred from roles.
        if (ownerAccess || moderatorAccess || superAdminAccess || hasPurchaserAccess) {
            return new Decision(PreviewMode.FULL, ownerAccess, hasPurchaserAccess,
                    moderatorAccess, superAdminAccess, true);
        }

        if (!paid) {
            // Free approved document → any viewer gets the full preview
            // via the existing public pipeline; this decision is
            // primarily informational.
            return new Decision(PreviewMode.FULL, ownerAccess, false,
                    moderatorAccess, superAdminAccess, true);
        }

        // Paid approved document, no privileged access → LIMITED. The
        // preview service still has the final say on whether a
        // derivative can be produced (one-page PDFs and non-PDFs
        // degrade to LOCKED), but the access decision itself never
        // denies a paid approved document for any viewer — guests,
        // user moderators, unrelated contributors, and authenticated
        // unpaid users all see at most a limited / locked page.
        return new Decision(PreviewMode.LIMITED, ownerAccess, false, moderatorAccess,
                superAdminAccess, false);
    }

    private static boolean isOwner(Document document, User currentUser) {
        if (currentUser == null || currentUser.getId() == null) {
            return false;
        }
        User owner = document.getCreatedBy();
        if (owner == null || owner.getId() == null) {
            return false;
        }
        return owner.getId().equals(currentUser.getId());
    }

    private static boolean hasAuthority(Collection<? extends GrantedAuthority> authorities,
                                       String... candidates) {
        if (authorities == null || authorities.isEmpty() || candidates == null) {
            return false;
        }
        Set<String> granted = authorities.stream()
                .map(GrantedAuthority::getAuthority)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        for (String candidate : candidates) {
            if (granted.contains(candidate)) {
                return true;
            }
        }
        return false;
    }

    /** Exposed for tests that want to compute moderator / super-admin
     * flags without constructing a full UserDetails collection. */
    static Set<String> grantedAuthorityNames(Collection<? extends GrantedAuthority> authorities) {
        if (authorities == null) {
            return Collections.emptySet();
        }
        return authorities.stream()
                .map(GrantedAuthority::getAuthority)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }
}