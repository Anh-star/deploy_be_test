package com.cmcu.itstudy.repository;

import com.cmcu.itstudy.entity.DocumentAccess;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DocumentAccessRepository extends JpaRepository<DocumentAccess, UUID> {

    boolean existsByUserIdAndDocumentId(UUID userId, UUID documentId);

    List<DocumentAccess> findByDocumentId(UUID documentId);

    @Query("""
        SELECT u.fullName
        FROM DocumentAccess da
        JOIN User u ON da.userId = u.id
        WHERE da.documentId = :documentId
        GROUP BY u.id, u.fullName
        ORDER BY MAX(da.grantedAt) DESC
    """)
    List<String> findDistinctBuyerNamesByDocumentOrderedByRecent(@Param("documentId") UUID documentId);
}
