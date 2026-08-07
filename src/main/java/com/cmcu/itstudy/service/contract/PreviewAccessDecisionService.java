package com.cmcu.itstudy.service.contract;

import com.cmcu.itstudy.dto.document.PreviewMode;
import com.cmcu.itstudy.entity.Document;
import com.cmcu.itstudy.entity.User;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;
import java.util.UUID;

/**
 * Decides whether a given viewer is allowed to receive the full
 * (non-derived) preview of a document.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Map document state (status, isPaid) and viewer identity into a
 *       {@link PreviewMode} (FULL, LIMITED, LOCKED) or a denial.</li>
 *   <li>Keep moderator / admin checks role-based; never match by email
 *       or other client-controlled fields.</li>
 *   <li>Stay stateless and free of HTTP concerns so the preview service
 *       can reuse it for any caller shape.</li>
 * </ul>
 *
 * <p>This service is intentionally thin: it does not consult the
 * storage layer or load DocumentFile rows. The preview service owns
 * those responsibilities.
 */
public interface PreviewAccessDecisionService {

    /**
     * Outcome of the access decision. {@code mode} is always populated;
     * the boolean fields are convenience flags so the caller can avoid
     * re-computing the same comparisons.
     */
    record Decision(
            PreviewMode mode,
            boolean ownerAccess,
            boolean purchaserAccess,
            boolean moderatorAccess,
            boolean superAdminAccess,
            boolean pendingAllowed) {
    }

    /**
     * Compute the preview access decision for the given viewer.
     *
     * @param document        the document being previewed (must not be null)
     * @param currentUser     the authenticated viewer, or {@code null}
     *                        for an anonymous guest
     * @param authorities     the viewer's granted authorities; may be null
     *                        only when {@code currentUser} is null
     * @param hasPurchaserAccess whether a {@code DocumentAccess} row
     *                        exists for the viewer + document. The
     *                        preview service supplies this; the decision
     *                        service does not touch the repository.
     * @return the decision (never null)
     */
    Decision decide(Document document,
                    User currentUser,
                    Collection<? extends GrantedAuthority> authorities,
                    boolean hasPurchaserAccess);

    /**
     * Convenience overload for anonymous viewers.
     */
    default Decision decide(Document document, boolean hasPurchaserAccess) {
        return decide(document, null, null, hasPurchaserAccess);
    }

    /**
     * Convenience overload used by tests that want to assert against a
     * specific viewer UUID without wiring the full
     * {@link com.cmcu.itstudy.security.UserDetailsImpl}.
     */
    default Decision decideByUuid(Document document,
                                  UUID viewerId,
                                  Collection<? extends GrantedAuthority> authorities,
                                  boolean hasPurchaserAccess) {
        if (viewerId == null) {
            return decide(document, null, authorities, hasPurchaserAccess);
        }
        User viewer = new User();
        viewer.setId(viewerId);
        return decide(document, viewer, authorities, hasPurchaserAccess);
    }
}