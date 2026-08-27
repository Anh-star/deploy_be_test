package com.cmcu.itstudy.repository;

import com.cmcu.itstudy.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    // New method to fetch user with roles
    @Query("SELECT u FROM User u LEFT JOIN FETCH u.userRoles ur LEFT JOIN FETCH ur.role WHERE u.email = :email")
    Optional<User> findByEmailWithRoles(@Param("email") String email);

    @Query("SELECT DISTINCT u FROM User u LEFT JOIN FETCH u.userRoles ur LEFT JOIN FETCH ur.role WHERE u.id = :id")
    Optional<User> findByIdWithRoles(@Param("id") UUID id);

    @Query(
            value = "SELECT DISTINCT u FROM User u LEFT JOIN FETCH u.userRoles ur LEFT JOIN FETCH ur.role WHERE "
                    + "(:search IS NULL OR :search = '' OR LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%')) OR "
                    + "(u.fullName IS NOT NULL AND LOWER(u.fullName) LIKE LOWER(CONCAT('%', :search, '%')))) "
                    + "AND (:status IS NULL OR :status = '' OR "
                    + "  (:status = 'LOCKED' AND UPPER(u.status) IN ('LOCKED', 'DISABLED', 'BANNED')) OR "
                    + "  (:status != 'LOCKED' AND UPPER(u.status) = UPPER(:status))) "
                    + "AND (:startDate IS NULL OR u.createdAt >= :startDate) "
                    + "AND (:endDate IS NULL OR u.createdAt <= :endDate)",
            countQuery = "SELECT COUNT(u) FROM User u WHERE "
                    + "(:search IS NULL OR :search = '' OR LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%')) OR "
                    + "(u.fullName IS NOT NULL AND LOWER(u.fullName) LIKE LOWER(CONCAT('%', :search, '%')))) "
                    + "AND (:status IS NULL OR :status = '' OR "
                    + "  (:status = 'LOCKED' AND UPPER(u.status) IN ('LOCKED', 'DISABLED', 'BANNED')) OR "
                    + "  (:status != 'LOCKED' AND UPPER(u.status) = UPPER(:status))) "
                    + "AND (:startDate IS NULL OR u.createdAt >= :startDate) "
                    + "AND (:endDate IS NULL OR u.createdAt <= :endDate)"
    )
    Page<User> searchForAdmin(
            @Param("search") String search,
            @Param("status") String status,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            Pageable pageable
    );

    default Page<User> searchForAdmin(String search, Pageable pageable) {
        return searchForAdmin(search, null, null, null, pageable);
    }

    boolean existsByEmail(String email);

    long countByStatus(String status);

    @Query("SELECT COUNT(u) FROM User u WHERE UPPER(u.status) IN ('LOCKED', 'DISABLED', 'BANNED')")
    long countLockedUsers();

    @Query("SELECT COUNT(u) FROM User u WHERE UPPER(u.status) = 'ACTIVE'")
    long countActiveUsers();

    @Query("select count(u) from User u where u.createdAt >= :from and u.createdAt < :to")
    long countCreatedBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    List<User> findTop5ByOrderByCreatedAtDesc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.id = :userId")
    Optional<User> findByIdForUpdate(@Param("userId") UUID userId);
}

