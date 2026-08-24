package com.cmcu.itstudy.repository;

import com.cmcu.itstudy.entity.ContributorRequest;
import com.cmcu.itstudy.entity.User;
import com.cmcu.itstudy.enums.ContributorRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ContributorRequestRepository extends JpaRepository<ContributorRequest, UUID> {
    boolean existsByUserAndStatus(User user, ContributorRequestStatus status);

    long countByStatus(ContributorRequestStatus status);

    Optional<ContributorRequest> findFirstByUserOrderByCreatedAtDesc(User user);

    Optional<ContributorRequest> findFirstByUserAndStatusOrderByUpdatedAtDesc(User user, ContributorRequestStatus status);

    // Custom query to fetch all ContributorRequests with their associated User and Certificates eagerly
    @Query("SELECT DISTINCT cr FROM ContributorRequest cr LEFT JOIN FETCH cr.user LEFT JOIN FETCH cr.certificates")
    List<ContributorRequest> findAllWithUserAndCertificates();
}
