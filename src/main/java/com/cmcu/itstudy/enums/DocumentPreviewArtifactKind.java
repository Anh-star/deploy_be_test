package com.cmcu.itstudy.enums;

/**
 * Kind of Office preview artifact persisted on
 * {@code dbo.tbl_document_preview_artifacts}.
 *
 * <p>Phase&nbsp;O2 introduces this enum; the value {@code FULL} covers the
 * full document preview rendered by LibreOffice and the value
 * {@code LIMITED} covers the blurred derivative rendered for
 * non-purchasers. The enum is intentionally narrow; Phase&nbsp;O3 will
 * not add additional kinds without a database migration.</p>
 */
public enum DocumentPreviewArtifactKind {
    FULL,
    LIMITED
}
