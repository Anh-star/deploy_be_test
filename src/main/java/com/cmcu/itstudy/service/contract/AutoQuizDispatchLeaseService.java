package com.cmcu.itstudy.service.contract;

import java.time.LocalDateTime;

/**
 * Thin lease-recovery contract for the Auto Quiz dispatch handshake.
 *
 * <p>Exists as a separate Spring bean so that
 * {@link #releaseStaleLeases(LocalDateTime)} always executes inside a
 * real Spring transactional proxy — avoiding the same-bean
 * self-invocation trap where {@code @Transactional} is bypassed.</p>
 *
 * <p>Invoked at the start of every dispatcher cycle, BEFORE the
 * candidate lookup, so a cleared lease becomes visible to the
 * candidate query in the same cycle.</p>
 *
 * @see com.cmcu.itstudy.service.impl.AutoQuizDispatchLeaseServiceImpl
 */
public interface AutoQuizDispatchLeaseService {

    /**
     * Release every {@code QUEUED} dispatch lease that is older than
     * {@code now - leaseTimeout} (or whose {@code dispatchTokenIssuedAt}
     * is {@code null}).
     *
     * <p>Only {@code QUEUED} rows are eligible. The {@code SET} clause
     * touches only {@code dispatchToken}, {@code dispatchTokenIssuedAt},
     * and {@code updatedAt} — no other column is modified.</p>
     *
     * <p>The call runs in its own {@code REQUIRES_NEW} transaction
     * regardless of any outer transactional context.</p>
     *
     * @param now caller-supplied cycle timestamp; used to compute
     *           {@code staleBefore = now - leaseTimeout} and to stamp
     *           {@code updatedAt}
     * @return number of stale leases released (0 to N)
     */
    int releaseStaleLeases(LocalDateTime now);
}
