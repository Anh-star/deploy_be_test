package com.cmcu.itstudy.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Application-wide {@link Clock} configuration.
 *
 * <p>The whole preview pipeline (artifact factory, claim service,
 * state service, scheduler, processor) and the supporting
 * orchestrators depend on this single bean so that the timestamps
 * produced by {@code LocalDateTime.now(clock)} agree on the same
 * instant.</p>
 *
 * <h2>Why {@link Clock#systemUTC()}?</h2>
 *
 * <p>The preview pipeline's business timestamps
 * ({@code createdAt}, {@code updatedAt}, {@code nextAttemptAt},
 * {@code claimedAt}) are stored in the SQL Server {@code datetime2}
 * columns. The two operationally observed failure modes with a
 * {@link Clock#systemDefaultZone()} bean were:</p>
 *
 * <ol>
 *   <li>The MySQL and SQL Server DSNs kept their own JDBC time-zone
 *       interpretations; on hosts in {@code Asia/Ho_Chi_Minh}
 *       ({@code UTC+07:00}) a {@code next_attempt_at} stamped by the
 *       JVM clock and a {@code created_at} defaulted on the column by
 *       {@code SYSUTCDATETIME()} differed by exactly seven hours, so
 *       operators saw "scheduled hours later" even though both
 *       timestamps were technically valid.</li>
 *   <li>The entity-level {@code @PrePersist} fallback used the
 *       JVM-default-zone clock as well, so the apparent time basis
 *       shifted when the JVM was moved between timezones.</li>
 * </ol>
 *
 * <p>Choosing {@link Clock#systemUTC()} removes the JVM-host-zone
 * coupling entirely. {@code LocalDateTime.now(clock)} now resolves to
 * the UTC wall clock on every host regardless of the operating
 * system's regional settings, and the SQL Server {@code datetime2}
 * columns receive a stable, region-independent value.</p>
 *
 * <p>The fallback in {@link
 * com.cmcu.itstudy.entity.DocumentPreviewArtifact#prePersist()} also
 * uses {@code LocalDateTime.now(Clock.systemUTC())} so a caller that
 * bypasses the factory still produces a row whose timestamps agree
 * with the rest of the preview pipeline.</p>
 *
 * <p>For unit tests the bean can be replaced with {@code Clock.fixed(
 * instant, ZoneOffset.UTC)} to obtain deterministic timestamps
 * without depending on the host timezone.</p>
 */
@Configuration
public class ApplicationClockConfig {

    /**
     * Single application-wide {@link Clock}. The whole preview
     * pipeline depends on this bean; tests may override it with a
     * fixed UTC clock to obtain deterministic timestamps.
     *
     * @return a non-null {@link Clock#systemUTC()} instance
     */
    @Bean
    public Clock applicationClock() {
        return Clock.systemUTC();
    }
}
