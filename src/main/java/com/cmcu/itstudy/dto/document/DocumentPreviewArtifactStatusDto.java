package com.cmcu.itstudy.dto.document;

/**
 * DTO-level copy of the worker status enum.
 * Exists so the status value can travel over the public API without
 * coupling the frontend to internal package-private entities.
 */
public enum DocumentPreviewArtifactStatusDto {
    PENDING,
    PROCESSING,
    READY,
    RETRY,
    DEAD
}
