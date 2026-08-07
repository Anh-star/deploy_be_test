package com.cmcu.itstudy.enums;

public enum StorageCleanupReason {
    BIND_FAIL_NEW,
    REPLACE_FAIL_NEW,
    DELETE_OLD_AFTER_REPLACE,
    DELETE_OLD_AFTER_FREE_TO_PAID,
    DELETE_OLD_AFTER_PAID_TO_FREE,
    EXPIRED_PENDING_UPLOAD,
    /**
     * Worker uploaded a preview PDF and lost the ownership race on
     * the guarded markReady UPDATE. The cleanup queue will delete the
     * orphaned object asynchronously.
     */
    WORKER_PARTIAL_FAILURE
}