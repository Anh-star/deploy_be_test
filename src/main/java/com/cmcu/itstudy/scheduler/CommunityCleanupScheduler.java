package com.cmcu.itstudy.scheduler;

import com.cmcu.itstudy.entity.CommunityPost;
import com.cmcu.itstudy.entity.CommunityPostComment;
import com.cmcu.itstudy.repository.CommunityPostCommentRepository;
import com.cmcu.itstudy.repository.CommunityPostRepository;
import com.cmcu.itstudy.service.contract.CommunityPostService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
public class CommunityCleanupScheduler {

    private final CommunityPostRepository postRepository;
    private final CommunityPostCommentRepository commentRepository;
    private final CommunityPostService postService;

    @Value("${app.community.cleanup-days:30}")
    private int cleanupDays;

    public CommunityCleanupScheduler(
            CommunityPostRepository postRepository,
            CommunityPostCommentRepository commentRepository,
            CommunityPostService postService
    ) {
        this.postRepository = postRepository;
        this.commentRepository = commentRepository;
        this.postService = postService;
    }

    /**
     * Runs every day at midnight (00:00:00) to permanently delete soft-deleted community content.
     */
    @Scheduled(cron = "0 0 0 * * ?")
    @Transactional
    public void executeCleanup() {
        log.info("Starting scheduled community cleanup task. Retention days: {}", cleanupDays);
        LocalDateTime threshold = LocalDateTime.now().minusDays(cleanupDays);

        // 1. Clean up old deleted comments
        try {
            List<CommunityPostComment> oldComments = commentRepository.findByDeletedTrueAndDeletedAtBefore(threshold);
            if (!oldComments.isEmpty()) {
                log.info("Found {} deleted comments older than {} days. Hard deleting...", oldComments.size(), cleanupDays);
                for (CommunityPostComment comment : oldComments) {
                    postService.hardDeleteCommentPhysics(comment.getId());
                }
            }
        } catch (Exception e) {
            log.error("Error during scheduled community comments cleanup", e);
        }

        // 2. Clean up old deleted posts
        try {
            List<CommunityPost> oldPosts = postRepository.findByDeletedTrueAndDeletedAtBefore(threshold);
            if (!oldPosts.isEmpty()) {
                log.info("Found {} deleted posts older than {} days. Hard deleting...", oldPosts.size(), cleanupDays);
                for (CommunityPost post : oldPosts) {
                    postService.hardDeletePostPhysics(post.getId());
                }
            }
        } catch (Exception e) {
            log.error("Error during scheduled community posts cleanup", e);
        }

        log.info("Scheduled community cleanup task completed.");
    }
}
