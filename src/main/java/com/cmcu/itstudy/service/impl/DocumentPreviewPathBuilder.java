package com.cmcu.itstudy.service.impl;

import com.cmcu.itstudy.enums.DocumentPreviewArtifactKind;
import com.cmcu.itstudy.handle.InvalidFileNameException;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Deterministic, attempt-owned preview-storage path builder for the
 * Phase&nbsp;O3 worker.
 *
 * <p>Every preview path is derived <strong>only</strong> from
 * server-side stable inputs that uniquely identify a single
 * {@code (artifact, claimedAttempt)} pair:</p>
 * <ul>
 *   <li>{@code documentFileId} (UUID) &mdash; the authoritative source
 *       document id;</li>
 *   <li>{@code artifactId} (UUID) &mdash; the preview artifact id;</li>
 *   <li>{@code artifactKind} &mdash; {@code FULL} or {@code LIMITED};</li>
 *   <li>{@code variantVersion} (int, non-negative) &mdash; the
 *       re-render counter;</li>
 *   <li>{@code claimedAttemptCount} (int, positive) &mdash; the attempt
 *       counter captured by the claim SQL, which is the same value
 *       passed as the {@code claimedAttemptCount} guard for
 *       {@code markReady}/{@code markRetry}/{@code markDead}.</li>
 * </ul>
 *
 * <h2>Path contract</h2>
 * <p>The generated path is:</p>
 * <pre>
 *   previews/{documentFileId}/{kind}/v{variantVersion}/
 *       artifact-{artifactId}-attempt-{claimedAttemptCount}.pdf
 * </pre>
 * <p>Example for a FULL artifact, variant&nbsp;1, claim attempt 2:</p>
 * <pre>
 *   previews/1d5d.../full/v1/artifact-9b21...-attempt-2.pdf
 * </pre>
 *
 * <h2>Why attempt-owned</h2>
 * <ul>
 *   <li>Every retry attempt uploads to a distinct attempt-owned
 *       folder so the orphaned-upload cleanup queue can target the
 *       exact bytes that lost the ownership race &mdash; never a
 *       previous attempt's bytes, never the original DOC/DOCX
 *       bytes.</li>
 *   <li>The same {@code (documentFileId, artifactId, kind, variant,
 *       claimedAttemptCount)} tuple ALWAYS yields the same path; the
 *       path is deterministic and contains no random component.</li>
 *   <li>{@code FULL} and {@code LIMITED} cannot collide because they
 *       live in different folders.</li>
 * </ul>
 *
 * <h2>What the path never contains</h2>
 * <ul>
 *   <li>any user-supplied fragment (filename, email, signed URL, JWT,
 *       Supabase token);</li>
 *   <li>any random UUID or random suffix;</li>
 *   <li>any path traversal fragment ({@code /}, {@code \},
 *       {@code ..}, NUL, {@code .});</li>
 *   <li>the original storage path or bucket name of the source
 *       document.</li>
 * </ul>
 *
 * <p>The deterministic-path contract removes the poisoned-write race
 * the previous random-suffix variant masked: when a retried attempt
 * loses ownership, the worker enqueues cleanup for the exact
 * attempt-owned path it just wrote, and a later successful retry
 * uploads to a distinct attempt-owned folder.</p>
 */
@Service
public class DocumentPreviewPathBuilder {

    /** Top-level prefix for preview artifacts. */
    public static final String PREVIEW_PREFIX = "previews/";

    /**
     * Build the canonical full preview path for an exact claimed
     * attempt.
     *
     * @param documentFileId     the source document file id (non-null)
     * @param artifactId         the preview artifact id (non-null)
     * @param artifactKind       the artifact kind enum (non-null)
     * @param variantVersion     the variant version (non-negative)
     * @param claimedAttemptCount the claim attempt count (strictly
     *                            positive)
     * @return a deterministic object path, never null
     */
    public String buildFullPreviewPath(
            UUID documentFileId,
            UUID artifactId,
            DocumentPreviewArtifactKind artifactKind,
            int variantVersion,
            int claimedAttemptCount) {
        return buildPath(documentFileId, artifactId, artifactKind,
                variantVersion, claimedAttemptCount, "full");
    }

    /**
     * Build the canonical limited preview path for an exact claimed
     * attempt.
     *
     * @param documentFileId     the source document file id (non-null)
     * @param artifactId         the preview artifact id (non-null)
     * @param artifactKind       the artifact kind enum (non-null)
     * @param variantVersion     the variant version (non-negative)
     * @param claimedAttemptCount the claim attempt count (strictly
     *                            positive)
     * @return a deterministic object path, never null
     */
    public String buildLimitedPreviewPath(
            UUID documentFileId,
            UUID artifactId,
            DocumentPreviewArtifactKind artifactKind,
            int variantVersion,
            int claimedAttemptCount) {
        return buildPath(documentFileId, artifactId, artifactKind,
                variantVersion, claimedAttemptCount, "limited");
    }

    private String buildPath(
            UUID documentFileId,
            UUID artifactId,
            DocumentPreviewArtifactKind artifactKind,
            int variantVersion,
            int claimedAttemptCount,
            String canonicalKindFolder) {
        if (documentFileId == null) {
            throw new InvalidFileNameException("documentFileId must not be null");
        }
        if (artifactId == null) {
            throw new InvalidFileNameException("artifactId must not be null");
        }
        if (artifactKind == null) {
            throw new InvalidFileNameException("artifactKind must not be null");
        }
        if (variantVersion < 0) {
            throw new InvalidFileNameException(
                    "variantVersion must be >= 0: " + variantVersion);
        }
        if (claimedAttemptCount < 1) {
            throw new InvalidFileNameException(
                    "claimedAttemptCount must be > 0: " + claimedAttemptCount);
        }
        if (canonicalKindFolder == null || canonicalKindFolder.isBlank()) {
            throw new InvalidFileNameException(
                    "canonicalKindFolder must not be blank");
        }
        // Defence-in-depth: every fragment comes from our own enum /
        // UUID types, but a future caller could pass an arbitrary
        // canonical folder name. Reject anything that smells like a
        // traversal sequence.
        rejectUnsafe(canonicalKindFolder);
        rejectUnsafe(artifactKind.name());

        return PREVIEW_PREFIX
                + documentFileId
                + "/" + canonicalKindFolder
                + "/v" + variantVersion
                + "/artifact-" + artifactId
                + "-attempt-" + claimedAttemptCount
                + ".pdf";
    }

    private static void rejectUnsafe(String s) {
        if (s == null) {
            return;
        }
        if (s.contains("/") || s.contains("\\") || s.contains("..")
                || s.contains("\0") || s.contains(".")) {
            throw new InvalidFileNameException(
                    "Path fragment contains unsafe characters: " + s);
        }
    }
}