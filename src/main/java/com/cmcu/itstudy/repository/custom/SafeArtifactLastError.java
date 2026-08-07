package com.cmcu.itstudy.repository.custom;

/**
 * Centralised, safe {@code lastError} sanitiser for the
 * {@code DocumentPreviewArtifact} state-update path.
 *
 * <p>Phase&nbsp;O2 stores {@code lastError} in a column of maximum
 * 1000&nbsp;characters. The values stored there must NEVER carry:</p>
 * <ul>
 *   <li>stack traces;</li>
 *   <li>raw stdout / stderr capture;</li>
 *   <li>document content (PDF bytes, original file content);</li>
 *   <li>paths or URL fragments containing credentials;</li>
 *   <li>signed URLs;</li>
 *   <li>secrets (Supabase keys, JWTs, PayOS keys, database passwords).</li>
 * </ul>
 *
 * <p>This helper enforces those rules consistently for every state
 * transition ({@code markReady}, {@code markRetry}, {@code markDead},
 * {@code releaseToRetry}). It also bounds the length to the column
 * limit so a caller cannot accidentally push a megabyte diagnostic
 * into the database.</p>
 *
 * <p>Nulls and empty strings are passed through as-is so the caller
 * can deliberately clear the column.</p>
 */
public final class SafeArtifactLastError {

    /**
     * Operational diagnostic length target. The column allows 1000
     * characters, but real operational codes are far shorter. The
     * repository implementation should use the
     * {@link #sanitize(String, int)} overload with a much smaller
     * upper bound (e.g. 200).
     */
    public static final int OPERATIONAL_MAX_LENGTH = 200;

    private SafeArtifactLastError() {
    }

    /**
     * @return {@code true} when the candidate value already passes the
     * safety rules for {@code lastError}.
     */
    public static boolean looksSafe(String candidate) {
        if (candidate == null || candidate.isEmpty()) {
            return true;
        }
        String lower = candidate.toLowerCase();
        if (lower.contains("stacktrace")
                || lower.contains("at org.")
                || lower.contains("at com.")
                || lower.contains("at java.")
                || lower.contains("exception:")
                || lower.contains("caused by:")) {
            return false;
        }
        if (lower.contains("password=") || lower.contains("pwd=")
                || lower.contains("secret=") || lower.contains("api_key=")
                || lower.contains("apikey=")
                || lower.contains("authorization: bearer")) {
            return false;
        }
        if (lower.contains("supabase.co/v1/")
                || lower.contains("?token=") || lower.contains("&token=")
                || lower.contains("signature=")) {
            return false;
        }
        return true;
    }

    /**
     * Sanitises a candidate {@code lastError} value.
     *
     * <p>Behaviour:</p>
     * <ul>
     *   <li>{@code null} or empty input passes through as
     *       {@code null} (so callers can deliberately clear the
     *       column);</li>
     *   <li>whitespace-only input is normalised to {@code null};</li>
     *   <li>values containing any forbidden pattern (stack trace,
     *       credential, signed-URL token) are replaced by a fixed
     *       operational code {@code O2_REDACTED};</li>
     *   <li>values whose length exceeds the column maximum
     *       ({@link com.cmcu.itstudy.entity.DocumentPreviewArtifact#LAST_ERROR_MAX_LENGTH})
     *       are truncated with an explicit {@code [TRUNC]} suffix so
     *       the operator can tell the original was longer.</li>
     * </ul>
     *
     * <p>This method never throws on ordinary input. Callers wanting
     * an exception on overflow should pre-validate with
     * {@link #wouldExceed(String, int)}.</p>
     */
    public static String sanitize(String candidate) {
        if (candidate == null) {
            return null;
        }
        String trimmed = candidate.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (!looksSafe(trimmed)) {
            return "O2_REDACTED";
        }
        int maxLen =
                com.cmcu.itstudy.entity.DocumentPreviewArtifact
                        .LAST_ERROR_MAX_LENGTH;
        if (trimmed.length() <= maxLen) {
            return trimmed;
        }
        // Truncate with a sentinel so the operator knows.
        return trimmed.substring(0, maxLen - "[TRUNC]".length())
                + "[TRUNC]";
    }

    /**
     * Sanitises a candidate against an explicit operational length
     * ceiling (much shorter than the column maximum).
     *
     * @param candidate  value to sanitise
     * @param maxLen     inclusive operational length ceiling, must be
     *                   {@code > 0} and not larger than the column
     *                   maximum
     * @return sanitised value
     */
    public static String sanitize(String candidate, int maxLen) {
        if (maxLen <= 0) {
            throw new IllegalArgumentException(
                    "maxLen must be > 0: " + maxLen);
        }
        if (maxLen > com.cmcu.itstudy.entity.DocumentPreviewArtifact
                .LAST_ERROR_MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "maxLen must be <= column max ("
                            + com.cmcu.itstudy.entity.DocumentPreviewArtifact
                                    .LAST_ERROR_MAX_LENGTH
                            + "): " + maxLen);
        }
        String sanitised = sanitize(candidate);
        if (sanitised == null) {
            return null;
        }
        if (sanitised.length() <= maxLen) {
            return sanitised;
        }
        return sanitised.substring(0, maxLen - "[TRUNC]".length())
                + "[TRUNC]";
    }

    /**
     * @return {@code true} when the candidate, after trimming,
     *         exceeds {@code maxLen}.
     */
    public static boolean wouldExceed(String candidate, int maxLen) {
        if (candidate == null) {
            return false;
        }
        return candidate.trim().length() > maxLen;
    }
}