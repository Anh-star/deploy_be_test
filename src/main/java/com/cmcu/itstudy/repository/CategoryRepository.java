package com.cmcu.itstudy.repository;

import com.cmcu.itstudy.entity.Category;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface CategoryRepository extends JpaRepository<Category, UUID> {

    boolean existsBySlug(String slug);

    boolean existsByName(String name);

    boolean existsBySlugAndIdNot(String slug, UUID id);

    boolean existsByNameAndIdNot(String name, UUID id);

    @EntityGraph(attributePaths = {"parent"})
    @Query("SELECT c FROM Category c")
    Page<Category> findAllPaged(Pageable pageable);

    @Query("SELECT c FROM Category c LEFT JOIN FETCH c.parent WHERE c.id = :id")
    Optional<Category> findByIdWithParent(@Param("id") UUID id);

    Optional<Category> findByName(String name);

    Optional<Category> findBySlug(String slug);
}
