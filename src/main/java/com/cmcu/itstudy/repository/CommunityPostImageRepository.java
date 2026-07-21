package com.cmcu.itstudy.repository;

import com.cmcu.itstudy.entity.CommunityPostImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CommunityPostImageRepository extends JpaRepository<CommunityPostImage, UUID> {

    List<CommunityPostImage> findByPostIdOrderByDisplayOrderAsc(UUID postId);

    void deleteByPostId(UUID postId);
}
