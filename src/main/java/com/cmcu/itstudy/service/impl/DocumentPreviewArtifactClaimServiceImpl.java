package com.cmcu.itstudy.service.impl;

import com.cmcu.itstudy.repository.DocumentPreviewArtifactRepository;
import com.cmcu.itstudy.repository.custom.DocumentPreviewArtifactClaim;
import com.cmcu.itstudy.service.contract.DocumentPreviewArtifactClaimService;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Short-transaction wrapper around
 * {@link com.cmcu.itstudy.repository.custom.DocumentPreviewArtifactClaimRepository}.
 *
 * <p>This service performs ONE thing: it converts
 * {@code (batchSize, staleBefore)} into a single atomic claim SQL
 * call and returns the immutable snapshots. It MUST NOT do any of the
 * following:</p>
 * <ul>
 *   <li>open or hold long-lived transactions;</li>
 *   <li>call Supabase or any remote storage service;</li>
 *   <li>invoke {@code LibreOfficeDocumentConverter};</li>
 *   <li>sleep or poll inside the database transaction.</li>
 * </ul>
 *
 * <p>{@code now} is acquired once from the application {@link Clock}
 * and passed through to the repository so the SQL parameter and the
 * service-level validation agree on the same instant.</p>
 *
 * <p>Per project convention this service injects the Spring Data
 * repository ({@link DocumentPreviewArtifactRepository}) rather than
 * the fragment interface directly. The fragment methods are inherited
 * via the {@code extends} clause on the repository, so the call still
 * resolves to the same {@code
 * DocumentPreviewArtifactClaimRepositoryImpl} bean.</p>
 */
@Service
public class DocumentPreviewArtifactClaimServiceImpl
        implements DocumentPreviewArtifactClaimService {

    private final DocumentPreviewArtifactRepository artifactRepository;
    private final Clock clock;

    public DocumentPreviewArtifactClaimServiceImpl(
            DocumentPreviewArtifactRepository artifactRepository,
            Clock applicationClock) {
        if (artifactRepository == null) {
            throw new IllegalArgumentException(
                    "artifactRepository must not be null");
        }
        if (applicationClock == null) {
            throw new IllegalArgumentException(
                    "applicationClock must not be null");
        }
        this.artifactRepository = artifactRepository;
        this.clock = applicationClock;
    }

    @Override
    public List<DocumentPreviewArtifactClaim> claimBatch(
            int batchSize,
            LocalDateTime staleBefore) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException(
                    "batchSize must be > 0: " + batchSize);
        }
        if (batchSize
                > com.cmcu.itstudy.repository.custom
                        .DocumentPreviewArtifactClaimRepository
                        .MAX_BATCH_SIZE) {
            throw new IllegalArgumentException(
                    "batchSize must be <= "
                            + com.cmcu.itstudy.repository.custom
                                    .DocumentPreviewArtifactClaimRepository
                                    .MAX_BATCH_SIZE
                            + ": " + batchSize);
        }
        LocalDateTime now = LocalDateTime.now(clock);
        if (staleBefore == null) {
            throw new IllegalArgumentException(
                    "staleBefore must not be null");
        }
        if (staleBefore.isAfter(now)) {
            throw new IllegalArgumentException(
                    "staleBefore must be <= now; got staleBefore="
                            + staleBefore + ", now=" + now);
        }
        return artifactRepository.claim(batchSize, now, staleBefore);
    }

    @Override
    public Clock clock() {
        return clock;
    }
}
