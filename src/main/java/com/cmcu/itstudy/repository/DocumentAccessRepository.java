package com.cmcu.itstudy.repository;

import com.cmcu.itstudy.entity.DocumentAccess;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface DocumentAccessRepository extends JpaRepository<DocumentAccess, UUID> {

    boolean existsByUserIdAndDocumentId(UUID userId, UUID documentId);
}
