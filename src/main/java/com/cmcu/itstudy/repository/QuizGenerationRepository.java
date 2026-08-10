package com.cmcu.itstudy.repository;

import com.cmcu.itstudy.entity.QuizGeneration;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Phase 2B persistence for {@link QuizGeneration}.
 *
 * <p>Only JPQL-derived methods are exposed — no native SQL.
 */
public interface QuizGenerationRepository
        extends JpaRepository<QuizGeneration, UUID> {

    /**
     * Returns the (at-most-one) generation row attached to the supplied
     * document id. The {@code uq_quiz_generation_document} unique
     * constraint guarantees this returns at most one row.
     */
    Optional<QuizGeneration> findByDocument_Id(UUID documentId);
}