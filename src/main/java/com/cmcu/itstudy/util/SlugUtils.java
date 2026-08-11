package com.cmcu.itstudy.util;

import java.text.Normalizer;
import java.util.Locale;
import java.util.function.Predicate;

public class SlugUtils {

    /** Maximum number of collision iterations before the resolver gives up. */
    private static final int MAX_COLLISION_ATTEMPTS = 1000;

    public static String slugify(String input) {
        if (input == null || input.isBlank()) {
            return "default-slug"; // Provide a default slug or handle as an error
        }
        String s = Normalizer.normalize(input.trim(), Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        s = s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        return s.isEmpty() ? "default-slug" : s; // Ensure slug is not empty
    }

    public static String resolveSlug(String requestedSlug, String name) {
        String raw = requestedSlug != null && !requestedSlug.isBlank() ? requestedSlug.trim() : null;
        return slugify(raw != null ? raw : name);
    }

    /**
     * Generates a globally-unique document slug derived from the supplied
     * title, delegating existence checks to {@code existsBySlug}. Reuses the
     * existing {@link #slugify(String)} pipeline so the slug format is
     * unchanged from before this fix.
     *
     * <p>Algorithm:
     * <ol>
     *   <li>base = slugify(title);</li>
     *   <li>candidate = base — returned verbatim when {@code existsBySlug}
     *       reports it is unused (the common no-collision path);</li>
     *   <li>otherwise candidate = base + "-" + n for n = 2, 3, ... until
     *       an unused slot is found.</li>
     * </ol>
     *
     * <p>The existence predicate MUST consider soft-deleted rows. The
     * {@code tbl_documents.slug} UNIQUE constraint covers deleted rows too,
     * so a soft-deleted entry still occupies its slug and a fresh create
     * with the same title must generate a suffixed slug.
     *
     * <p>The loop is bounded by {@link #MAX_COLLISION_ATTEMPTS}; if every
     * slot in that window is taken the resolver falls back to
     * {@code base + "-" + (MAX_COLLISION_ATTEMPTS + 1)}. In practice this
     * is unreachable — a collision chain of a thousand entries from the
     * same title is not a real-world scenario.
     */
    public static String resolveUniqueSlug(String title, Predicate<String> existsBySlug) {
        if (existsBySlug == null) {
            throw new IllegalArgumentException("existsBySlug must not be null");
        }
        String base = slugify(title);
        if (!existsBySlug.test(base)) {
            return base;
        }
        for (int n = 2; n <= MAX_COLLISION_ATTEMPTS; n++) {
            String candidate = base + "-" + n;
            if (!existsBySlug.test(candidate)) {
                return candidate;
            }
        }
        // Last-resort fallback. The DB UNIQUE constraint remains the final
        // line of defense; this only fires if a thousand sequential suffixes
        // are already taken.
        return base + "-" + (MAX_COLLISION_ATTEMPTS + 1);
    }
}
