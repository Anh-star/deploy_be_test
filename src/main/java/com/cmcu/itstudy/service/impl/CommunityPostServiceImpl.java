package com.cmcu.itstudy.service.impl;

import com.cmcu.itstudy.dto.community.CommunityModerationStatsDto;
import com.cmcu.itstudy.dto.community.CommunityPostResponseDto;
import com.cmcu.itstudy.dto.community.CreatePollRequestDto;
import com.cmcu.itstudy.dto.community.PollDto;
import com.cmcu.itstudy.dto.community.PostCommentResponseDto;
import com.cmcu.itstudy.dto.community.PostReportResponseDto;
import com.cmcu.itstudy.dto.community.VoterDto;
import com.cmcu.itstudy.entity.CommunityPoll;
import com.cmcu.itstudy.entity.CommunityPollOption;
import com.cmcu.itstudy.entity.CommunityPollVote;
import com.cmcu.itstudy.entity.CommunityPost;
import com.cmcu.itstudy.entity.CommunityPostComment;
import com.cmcu.itstudy.entity.CommunityPostCommentLike;
import com.cmcu.itstudy.entity.CommunityPostImage;
import com.cmcu.itstudy.entity.CommunityPostLike;
import com.cmcu.itstudy.entity.CommunityPostNotificationMute;
import com.cmcu.itstudy.entity.CommunityPostReport;
import com.cmcu.itstudy.entity.CommunityPostSave;
import com.cmcu.itstudy.entity.User;
import com.cmcu.itstudy.enums.NotificationType;
import com.cmcu.itstudy.mapper.CommunityPostMapper;
import com.cmcu.itstudy.repository.CommunityPollOptionRepository;
import com.cmcu.itstudy.repository.CommunityPollRepository;
import com.cmcu.itstudy.repository.CommunityPollVoteRepository;
import com.cmcu.itstudy.repository.CommunityPostCommentLikeRepository;
import com.cmcu.itstudy.repository.CommunityPostCommentRepository;
import com.cmcu.itstudy.repository.CommunityPostImageRepository;
import com.cmcu.itstudy.repository.CommunityPostLikeRepository;
import com.cmcu.itstudy.repository.CommunityPostNotificationMuteRepository;
import com.cmcu.itstudy.repository.CommunityPostReportRepository;
import com.cmcu.itstudy.repository.CommunityPostRepository;
import com.cmcu.itstudy.repository.CommunityPostSaveRepository;
import com.cmcu.itstudy.repository.UserRepository;
import com.cmcu.itstudy.service.contract.CommunityPostService;
import com.cmcu.itstudy.service.contract.NotificationService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
public class CommunityPostServiceImpl implements CommunityPostService {

    private final CommunityPostRepository postRepository;
    private final CommunityPostImageRepository imageRepository;
    private final CommunityPostLikeRepository likeRepository;
    private final CommunityPostSaveRepository saveRepository;
    private final CommunityPollRepository pollRepository;
    private final CommunityPollOptionRepository pollOptionRepository;
    private final CommunityPollVoteRepository pollVoteRepository;
    private final CommunityPostCommentRepository commentRepository;
    private final CommunityPostCommentLikeRepository commentLikeRepository;
    private final UserRepository userRepository;
    private final CommunityPostNotificationMuteRepository notificationMuteRepository;
    private final NotificationService notificationService;
    private final CommunityPostReportRepository reportRepository;
    private final SseService sseService;
    private final com.cmcu.itstudy.repository.DocumentRepository documentRepository;
    private final com.cmcu.itstudy.repository.RefreshTokenRepository refreshTokenRepository;
    private final com.cmcu.itstudy.repository.NotificationRepository notificationRepository;

    public CommunityPostServiceImpl(
            CommunityPostRepository postRepository,
            CommunityPostImageRepository imageRepository,
            CommunityPostLikeRepository likeRepository,
            CommunityPostSaveRepository saveRepository,
            CommunityPollRepository pollRepository,
            CommunityPollOptionRepository pollOptionRepository,
            CommunityPollVoteRepository pollVoteRepository,
            CommunityPostCommentRepository commentRepository,
            CommunityPostCommentLikeRepository commentLikeRepository,
            UserRepository userRepository,
            CommunityPostNotificationMuteRepository notificationMuteRepository,
            NotificationService notificationService,
            CommunityPostReportRepository reportRepository,
            SseService sseService,
            com.cmcu.itstudy.repository.DocumentRepository documentRepository,
            com.cmcu.itstudy.repository.RefreshTokenRepository refreshTokenRepository,
            com.cmcu.itstudy.repository.NotificationRepository notificationRepository
    ) {
        this.postRepository = postRepository;
        this.imageRepository = imageRepository;
        this.likeRepository = likeRepository;
        this.saveRepository = saveRepository;
        this.pollRepository = pollRepository;
        this.pollOptionRepository = pollOptionRepository;
        this.pollVoteRepository = pollVoteRepository;
        this.commentRepository = commentRepository;
        this.commentLikeRepository = commentLikeRepository;
        this.userRepository = userRepository;
        this.notificationMuteRepository = notificationMuteRepository;
        this.notificationService = notificationService;
        this.reportRepository = reportRepository;
        this.sseService = sseService;
        this.documentRepository = documentRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.notificationRepository = notificationRepository;
    }

    @Override
    @Transactional
    public CommunityPostResponseDto createPost(UUID userId, String content, List<String> imageUrls) {
        return createPost(userId, null, content, null, imageUrls, null, null, true);
    }

    @Override
    @Transactional
    public CommunityPostResponseDto createPost(
            UUID userId,
            String title,
            String content,
            List<String> tags,
            List<String> imageUrls,
            List<String> fileUrls,
            CreatePollRequestDto pollRequest,
            Boolean allowComments
    ) {
        User author = userRepository.getReferenceById(userId);

        String joinedFileUrls = (fileUrls != null && !fileUrls.isEmpty())
                ? String.join(";;;", fileUrls)
                : null;

        CommunityPost post = postRepository.save(CommunityPost.builder()
                .author(author)
                .title(title != null && !title.isBlank() ? title.trim() : null)
                .content(content != null ? content.trim() : "")
                .tags(tags != null ? new ArrayList<>(tags) : new ArrayList<>())
                .fileUrls(joinedFileUrls)
                .allowComments(allowComments != null ? allowComments : true)
                .build());

        List<CommunityPostImage> images = new ArrayList<>();
        if (imageUrls != null && !imageUrls.isEmpty()) {
            for (int i = 0; i < Math.min(imageUrls.size(), 4); i++) {
                images.add(imageRepository.save(CommunityPostImage.builder()
                        .post(post)
                        .imageUrl(imageUrls.get(i))
                        .displayOrder(i)
                        .build()));
            }
        }

        // Create Poll if pollRequest exists
        CommunityPoll poll = null;
        if (pollRequest != null && pollRequest.getQuestion() != null && !pollRequest.getQuestion().isBlank()
                && pollRequest.getOptions() != null && pollRequest.getOptions().size() >= 2) {
            
            LocalDateTime expiresAt = null;
            if (pollRequest.getDurationDays() != null && pollRequest.getDurationDays() > 0) {
                expiresAt = LocalDateTime.now().plusDays(pollRequest.getDurationDays());
            }

            poll = CommunityPoll.builder()
                    .post(post)
                    .question(pollRequest.getQuestion().trim())
                    .expiresAt(expiresAt)
                    .allowMultiple(Boolean.TRUE.equals(pollRequest.getAllowMultiple()))
                    .allowAddOptions(Boolean.TRUE.equals(pollRequest.getAllowAddOptions()))
                    .hideResultsBeforeVote(Boolean.TRUE.equals(pollRequest.getHideResultsBeforeVote()))
                    .hideVoters(Boolean.TRUE.equals(pollRequest.getHideVoters()))
                    .build();

            poll = pollRepository.save(poll);

            List<CommunityPollOption> options = new ArrayList<>();
            for (int i = 0; i < pollRequest.getOptions().size(); i++) {
                String optText = pollRequest.getOptions().get(i);
                if (optText != null && !optText.isBlank()) {
                    options.add(CommunityPollOption.builder()
                            .poll(poll)
                            .optionText(optText.trim())
                            .displayOrder(i)
                            .build());
                }
            }
            pollOptionRepository.saveAll(options);
            poll.setOptions(options);
        }

        CommunityPost savedPost = postRepository.findByIdWithAuthor(post.getId()).orElse(post);
        return CommunityPostMapper.toPostResponse(savedPost, images, false, null, false, poll, List.of());
    }

    @Override
    @Transactional(readOnly = true)
    public CommunityPostResponseDto getPostById(UUID postId, UUID currentUserId) {
        CommunityPost post = postRepository.findByIdWithAuthor(postId)
                .orElseThrow(() -> new NoSuchElementException("Post not found"));

        List<CommunityPostImage> images = imageRepository.findByPostIdOrderByDisplayOrderAsc(postId);
        
        String currentUserVote = null;
        boolean isLiked = false;
        boolean isSaved = false;

        boolean isMuted = false;
        if (currentUserId != null) {
            try {
                Optional<CommunityPostLike> likeOpt = likeRepository.findByPost_IdAndUser_Id(postId, currentUserId);
                if (likeOpt.isPresent()) {
                    isLiked = true;
                    currentUserVote = likeOpt.get().getVoteType();
                }
                isSaved = saveRepository.findByPost_IdAndUser_Id(postId, currentUserId).isPresent();
                isMuted = notificationMuteRepository.existsByPost_IdAndUser_Id(postId, currentUserId);
            } catch (Exception ignored) {
            }
        }

        CommunityPoll poll = pollRepository.findByPost_Id(postId).orElse(null);
        List<CommunityPollVote> userPollVotes = (poll != null && currentUserId != null)
                ? pollVoteRepository.findAllByPoll_IdAndUser_Id(poll.getId(), currentUserId)
                : List.of();

        boolean isReported = reportRepository.existsByPostIdAndStatus(postId, "PENDING");
        boolean isReportDismissed = !isReported && reportRepository.existsByPostIdAndStatus(postId, "DISMISSED");
        long reportCount = reportRepository.countByPostId(postId);

        return CommunityPostMapper.toPostResponse(post, images, isLiked, currentUserVote, isSaved, poll, userPollVotes, isReported, isReportDismissed, reportCount, isMuted);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CommunityPostResponseDto> getFeed(int page, int size, UUID currentUserId) {
        Page<CommunityPost> postPage = postRepository.findByDeletedFalseOrderByCreatedAtDesc(
                PageRequest.of(page, size)
        );

        List<CommunityPost> posts = postPage.getContent();
        if (posts.isEmpty()) return List.of();

        return posts.stream().map(post -> getPostResponseForUser(post, currentUserId)).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<CommunityPostResponseDto> getUserPosts(UUID authorId, int page, int size, UUID currentUserId) {
        Page<CommunityPost> postPage = postRepository.findByAuthorIdUserPosts(
                authorId, PageRequest.of(page, size)
        );
        List<CommunityPost> posts = postPage.getContent();
        if (posts.isEmpty()) return List.of();

        return posts.stream().map(post -> getPostResponseForUser(post, currentUserId)).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public CommunityPostResponseDto togglePinPost(UUID postId, UUID userId) {
        CommunityPost post = postRepository.findByIdWithAuthor(postId)
                .orElseThrow(() -> new NoSuchElementException("Post not found"));

        if (!post.getAuthor().getId().equals(userId)) {
            throw new IllegalArgumentException("Chỉ có thể ghim bài viết của chính bạn");
        }

        boolean willPin = !Boolean.TRUE.equals(post.getIsPinned());
        post.setIsPinned(willPin);
        post.setPinnedAt(willPin ? LocalDateTime.now() : null);

        postRepository.save(post);
        return getPostResponseForUser(post, userId);
    }

    private CommunityPostResponseDto getPostResponseForUser(CommunityPost post, UUID currentUserId) {
        List<CommunityPostImage> images = imageRepository.findByPostIdOrderByDisplayOrderAsc(post.getId());
        String currentUserVote = null;
        boolean isLiked = false;
        boolean isSaved = false;

        boolean isMuted = false;
        if (currentUserId != null) {
            try {
                Optional<CommunityPostLike> likeOpt = likeRepository.findByPost_IdAndUser_Id(post.getId(), currentUserId);
                if (likeOpt.isPresent()) {
                    isLiked = true;
                    currentUserVote = likeOpt.get().getVoteType();
                }
                isSaved = saveRepository.findByPost_IdAndUser_Id(post.getId(), currentUserId).isPresent();
                isMuted = notificationMuteRepository.existsByPost_IdAndUser_Id(post.getId(), currentUserId);
            } catch (Exception ignored) {
            }
        }

        CommunityPoll poll = pollRepository.findByPost_Id(post.getId()).orElse(null);
        List<CommunityPollVote> userPollVotes = (poll != null && currentUserId != null)
                ? pollVoteRepository.findAllByPoll_IdAndUser_Id(poll.getId(), currentUserId)
                : List.of();

        return CommunityPostMapper.toPostResponse(post, images, isLiked, currentUserVote, isSaved, poll, userPollVotes, isMuted);
    }

    @Override
    @Transactional(readOnly = true)
    public long getFeedTotalCount() {
        return postRepository.countByDeletedFalse();
    }

    @Override
    @Transactional
    public void deletePost(UUID postId, UUID userId) {
        CommunityPost post = postRepository.findById(postId)
                .orElseThrow(() -> new NoSuchElementException("Post not found"));

        if (!post.getAuthor().getId().equals(userId)) {
            throw new IllegalArgumentException("You can only delete your own post");
        }

        post.setDeleted(true);
        post.setDeletedAt(LocalDateTime.now());
        postRepository.save(post);

        List<CommunityPostReport> reports = reportRepository.findByPostId(postId);
        if (reports != null && !reports.isEmpty()) {
            for (CommunityPostReport r : reports) {
                r.setStatus("RESOLVED");
            }
            reportRepository.saveAll(reports);
        }
    }

    @Override
    @Transactional
    public void hardDeletePostPhysics(UUID postId) {
        CommunityPost post = postRepository.findById(postId).orElse(null);
        if (post == null) return;

        // Delete comments & comment likes
        List<CommunityPostComment> comments = commentRepository.findByPost_Id(postId);
        if (!comments.isEmpty()) {
            for (CommunityPostComment c : comments) {
                commentLikeRepository.deleteByCommentId(c.getId());
            }
            commentRepository.deleteAll(comments);
        }

        // Delete likes & saves
        likeRepository.deleteByPostId(postId);
        saveRepository.deleteByPostId(postId);

        // Delete poll
        pollRepository.deleteByPostId(postId);

        // Delete images
        imageRepository.deleteByPostId(postId);

        // Delete post
        postRepository.delete(post);
    }

    @Override
    @Transactional
    public CommunityPostResponseDto toggleLikePost(UUID postId, UUID userId) {
        return votePost(postId, userId, "UPVOTE");
    }

    @Override
    @Transactional
    public synchronized CommunityPostResponseDto votePost(UUID postId, UUID userId, String voteType) {
        String targetVote = ("DOWNVOTE".equalsIgnoreCase(voteType)) ? "DOWNVOTE" : "UPVOTE";

        CommunityPost post = postRepository.findByIdWithAuthor(postId)
                .orElseThrow(() -> new NoSuchElementException("Post not found"));

        User userRef = userRepository.getReferenceById(userId);
        Optional<CommunityPostLike> existing = likeRepository.findByPost_IdAndUser_Id(postId, userId);

        String resultVote = null;

        if (existing.isPresent()) {
            CommunityPostLike currentLike = existing.get();
            String currentVoteType = currentLike.getVoteType() != null ? currentLike.getVoteType() : "UPVOTE";

            if (currentVoteType.equalsIgnoreCase(targetVote)) {
                // Toggle off (remove vote)
                likeRepository.delete(currentLike);
                resultVote = null;
            } else {
                // Switch vote type
                currentLike.setVoteType(targetVote);
                currentLike.setLikedAt(LocalDateTime.now());
                likeRepository.save(currentLike);
                resultVote = targetVote;
            }
        } else {
            // New vote
            likeRepository.save(CommunityPostLike.builder()
                    .post(post)
                    .user(userRef)
                    .voteType(targetVote)
                    .likedAt(LocalDateTime.now())
                    .build());
            resultVote = targetVote;
        }

        likeRepository.flush();

        // Exact count from database - immune to drifting counters during spam
        int upvotes = (int) likeRepository.countByPost_IdAndVoteType(postId, "UPVOTE");
        int downvotes = (int) likeRepository.countByPost_IdAndVoteType(postId, "DOWNVOTE");

        post.setUpvoteCount(upvotes);
        post.setDownvoteCount(downvotes);
        post.setLikeCount(upvotes);
        postRepository.saveAndFlush(post);

        // Real-time SSE broadcast of post vote count
        Map<String, Object> voteEventData = new HashMap<>();
        voteEventData.put("postId", postId.toString());
        voteEventData.put("upvoteCount", upvotes);
        voteEventData.put("downvoteCount", downvotes);
        sseService.broadcast("post-voted", voteEventData);

        // Notification logic for upvote / cancel upvote
        if ("UPVOTE".equals(resultVote)) {
            sendAggregatedUpvoteNotificationIfUnmuted(post.getAuthor().getId(), userRef, post);
        } else {
            handleCancelUpvoteNotification(post.getAuthor().getId(), post);
        }

        return getPostResponseForUser(post, userId);
    }

    @Override
    @Transactional
    public boolean toggleSavePost(UUID postId, UUID userId) {
        CommunityPost post = postRepository.findById(postId)
                .orElseThrow(() -> new NoSuchElementException("Post not found"));

        Optional<CommunityPostSave> existing = saveRepository.findByPost_IdAndUser_Id(postId, userId);
        if (existing.isPresent()) {
            saveRepository.delete(existing.get());
            saveRepository.flush();
            return false; // Now unsaved
        } else {
            User userRef = userRepository.getReferenceById(userId);
            saveRepository.save(CommunityPostSave.builder()
                    .post(post)
                    .user(userRef)
                    .build());
            return true; // Now saved
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<CommunityPostResponseDto> getSavedPosts(int page, int size, UUID userId) {
        Page<CommunityPostSave> savePage = saveRepository.findByUser_IdOrderBySavedAtDesc(
                userId, PageRequest.of(page, size)
        );

        List<CommunityPostSave> saves = savePage.getContent();
        if (saves.isEmpty()) return List.of();

        return saves.stream()
                .map(save -> getPostResponseForUser(save.getPost(), userId))
                .filter(dto -> dto != null)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public PollDto votePollOption(UUID pollId, UUID optionId, UUID userId) {
        CommunityPoll poll = pollRepository.findById(pollId)
                .orElseThrow(() -> new NoSuchElementException("Poll not found"));

        if (poll.getExpiresAt() != null && LocalDateTime.now().isAfter(poll.getExpiresAt())) {
            throw new IllegalArgumentException("Khảo sát này đã kết thúc.");
        }

        CommunityPollOption option = pollOptionRepository.findById(optionId)
                .orElseThrow(() -> new NoSuchElementException("Option not found"));

        User userRef = userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("User not found"));

        // Eagerly fetch existing votes with their options via JOIN FETCH
        List<CommunityPollVote> existingVotes = pollVoteRepository.findAllByPoll_IdAndUser_Id(pollId, userId);
        boolean isAllowMultiple = Boolean.TRUE.equals(poll.getAllowMultiple());

        // Check if user already voted for this specific option
        boolean alreadyVotedThisOption = existingVotes.stream()
                .anyMatch(v -> optionId.equals(v.getOption().getId()));

        if (alreadyVotedThisOption) {
            // Toggle off: user clicked the same option they already voted for
            // Use bulk JPQL delete to avoid entity state issues
            pollVoteRepository.deleteAllByPollIdAndUserId(pollId, userId);
            pollVoteRepository.flush();

            if (!isAllowMultiple) {
                // Single-choice: just removed the vote, done
            } else {
                // Multi-choice: re-add all votes EXCEPT for this option
                for (CommunityPollVote v : existingVotes) {
                    if (!optionId.equals(v.getOption().getId())) {
                        pollVoteRepository.save(CommunityPollVote.builder()
                                .poll(poll)
                                .option(v.getOption())
                                .user(userRef)
                                .build());
                    }
                }
                pollVoteRepository.flush();
            }
        } else {
            // User is voting for a new option
            if (!isAllowMultiple) {
                // Single-choice: clear all existing votes first
                if (!existingVotes.isEmpty()) {
                    pollVoteRepository.deleteAllByPollIdAndUserId(pollId, userId);
                    pollVoteRepository.flush();
                }
            }
            // Add the new vote
            pollVoteRepository.save(CommunityPollVote.builder()
                    .poll(poll)
                    .option(option)
                    .user(userRef)
                    .build());
            pollVoteRepository.flush();
        }

        // Recalculate exact vote counts for all options from DB
        List<CommunityPollOption> freshOptions = pollOptionRepository.findByPoll_IdOrderByDisplayOrderAsc(pollId);
        for (CommunityPollOption opt : freshOptions) {
            long count = pollVoteRepository.countByOption_Id(opt.getId());
            opt.setVoteCount((int) count);
        }
        pollOptionRepository.saveAll(freshOptions);
        // DO NOT call poll.setOptions(...) — it triggers Hibernate orphan removal error

        List<CommunityPollVote> updatedUserVotes = pollVoteRepository.findAllByPoll_IdAndUser_Id(pollId, userId);
        return CommunityPostMapper.toPollDto(poll, freshOptions, updatedUserVotes);
    }

    @Override
    @Transactional(readOnly = true)
    public List<VoterDto> getPollVoters(UUID optionId, UUID currentUserId) {
        CommunityPollOption option = pollOptionRepository.findById(optionId)
                .orElseThrow(() -> new NoSuchElementException("Option not found"));

        if (Boolean.TRUE.equals(option.getPoll().getHideVoters())) {
            throw new IllegalArgumentException("Khảo sát này đã ẩn người bình chọn.");
        }

        List<CommunityPollVote> votes = pollVoteRepository.findByOptionIdWithUser(optionId);
        return votes.stream()
                .filter(v -> v.getUser() != null)
                .map(v -> VoterDto.builder()
                        .userId(v.getUser().getId() != null ? v.getUser().getId().toString() : null)
                        .fullName(v.getUser().getFullName())
                        .avatarUrl(v.getUser().getAvatarUrl())
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public PollDto addPollOption(UUID pollId, String optionText, UUID userId) {
        CommunityPoll poll = pollRepository.findById(pollId)
                .orElseThrow(() -> new NoSuchElementException("Poll not found"));

        if (!Boolean.TRUE.equals(poll.getAllowAddOptions())) {
            throw new IllegalArgumentException("Khảo sát này không cho phép người khác thêm phương án.");
        }

        if (poll.getExpiresAt() != null && LocalDateTime.now().isAfter(poll.getExpiresAt())) {
            throw new IllegalArgumentException("Khảo sát này đã kết thúc.");
        }

        if (optionText == null || optionText.isBlank()) {
            throw new IllegalArgumentException("Nội dung phương án không được để trống.");
        }

        List<CommunityPollOption> currentOptions = pollOptionRepository.findByPoll_IdOrderByDisplayOrderAsc(pollId);

        pollOptionRepository.save(CommunityPollOption.builder()
                .poll(poll)
                .optionText(optionText.trim())
                .voteCount(0)
                .displayOrder(currentOptions.size())
                .build());

        List<CommunityPollOption> updatedOptions = pollOptionRepository.findByPoll_IdOrderByDisplayOrderAsc(pollId);
        List<CommunityPollVote> userVotes = pollVoteRepository.findAllByPoll_IdAndUser_Id(pollId, userId);
        return CommunityPostMapper.toPollDto(poll, updatedOptions, userVotes);
    }

    private void sendNotificationIfUnmuted(UUID recipientId, UUID senderId, UUID postId, String referenceId, NotificationType type, String message) {
        if (recipientId == null || recipientId.equals(senderId)) return;
        try {
            if (!notificationMuteRepository.existsByPost_IdAndUser_Id(postId, recipientId)) {
                notificationService.createAndPush(recipientId, senderId, type, referenceId != null ? referenceId : postId.toString(), "COMMUNITY_POST", message);
            }
        } catch (Exception ignored) {}
    }

    private void sendAggregatedCommentNotificationIfUnmuted(
            UUID recipientId,
            User actor,
            CommunityPost post,
            String targetRefId,
            NotificationType type,
            String singleMessage,
            boolean isParentAuthor
    ) {
        if (recipientId == null || actor == null || recipientId.equals(actor.getId())) return;
        UUID postId = post.getId();
        try {
            if (notificationMuteRepository.existsByPost_IdAndUser_Id(postId, recipientId)) {
                return;
            }

            // Check if recipient has an existing unread comment notification on this post
            List<com.cmcu.itstudy.entity.Notification> unreadList =
                    notificationRepository.findUnreadCommunityPostNotifications(recipientId, postId.toString() + "%");

            if (unreadList.isEmpty()) {
                // No unread notification: create new single notification
                notificationService.createAndPush(
                        recipientId,
                        actor.getId(),
                        type,
                        targetRefId != null ? targetRefId : postId.toString(),
                        "COMMUNITY_POST",
                        singleMessage
                );
                return;
            }

            // Aggregate with existing unread notification
            com.cmcu.itstudy.entity.Notification existing = unreadList.get(0);

            // Fetch distinct recent commenters for this post (excluding the recipient)
            List<String> commenterNames = commentRepository.findDistinctCommenterNamesByPostOrderedByRecent(postId, recipientId);
            String actorName = (actor.getFullName() != null && !actor.getFullName().isBlank()) ? actor.getFullName() : "Ai đó";

            String aggregatedMessage;
            if (commenterNames == null || commenterNames.size() <= 1) {
                aggregatedMessage = singleMessage;
            } else if (commenterNames.size() == 2) {
                String first = commenterNames.get(0);
                String second = commenterNames.get(1);
                if (isParentAuthor) {
                    aggregatedMessage = first + " và " + second + " đã trả lời bình luận của bạn";
                } else {
                    aggregatedMessage = first + " và " + second + " đã bình luận về bài viết của bạn";
                }
            } else {
                String first = commenterNames.get(0);
                int othersCount = commenterNames.size() - 1;
                if (isParentAuthor) {
                    aggregatedMessage = first + " và " + othersCount + " người khác đã trả lời bình luận của bạn";
                } else {
                    aggregatedMessage = first + " và " + othersCount + " người khác đã bình luận về bài viết của bạn";
                }
            }

            existing.setMessage(aggregatedMessage);
            existing.setActor(actor);
            existing.setType(type);
            existing.setReferenceId(targetRefId != null ? targetRefId : postId.toString());
            existing.setCreatedAt(LocalDateTime.now());
            existing.setRead(false);
            com.cmcu.itstudy.entity.Notification saved = notificationRepository.save(existing);

            // Push updated notification via SSE to recipient
            try {
                com.cmcu.itstudy.dto.notification.NotificationResponseDto dto = com.cmcu.itstudy.dto.notification.NotificationResponseDto.builder()
                        .id(saved.getId().toString())
                        .actorId(actor.getId().toString())
                        .actorName(actorName)
                        .actorAvatar(actor.getAvatarUrl())
                        .type(saved.getType())
                        .referenceId(saved.getReferenceId())
                        .referenceType(saved.getReferenceType())
                        .message(saved.getMessage())
                        .isRead(false)
                        .createdAt(saved.getCreatedAt())
                        .build();
                sseService.pushEvent(recipientId, "notification", dto);
            } catch (Exception sseEx) {
                log.warn("Failed to push aggregated SSE notification to user {}: {}", recipientId, sseEx.getMessage());
            }

        } catch (Exception ex) {
            log.warn("Failed to send aggregated comment notification: {}", ex.getMessage());
        }
    }

    private void sendAggregatedUpvoteNotificationIfUnmuted(
            UUID recipientId,
            User actor,
            CommunityPost post
    ) {
        if (recipientId == null || actor == null || recipientId.equals(actor.getId())) return;
        UUID postId = post.getId();
        try {
            if (notificationMuteRepository.existsByPost_IdAndUser_Id(postId, recipientId)) {
                return;
            }

            // Fetch distinct recent upvoters for this post (excluding the recipient)
            List<String> upvoterNames = likeRepository.findUpvoterNamesByPostOrderedByRecent(postId, recipientId);
            if (upvoterNames == null || upvoterNames.isEmpty()) {
                return;
            }

            String actorName = (actor.getFullName() != null && !actor.getFullName().isBlank()) ? actor.getFullName() : "Ai đó";
            String aggregatedMessage;
            if (upvoterNames.size() == 1) {
                aggregatedMessage = upvoterNames.get(0) + " đã thích bài viết của bạn.";
            } else if (upvoterNames.size() == 2) {
                aggregatedMessage = upvoterNames.get(0) + " và " + upvoterNames.get(1) + " đã thích bài viết của bạn.";
            } else {
                int othersCount = upvoterNames.size() - 1;
                aggregatedMessage = upvoterNames.get(0) + " và " + othersCount + " người khác đã thích bài viết của bạn.";
            }

            // Check if recipient has an existing unread upvote notification on this post
            List<com.cmcu.itstudy.entity.Notification> unreadList =
                    notificationRepository.findUnreadPostUpvoteNotifications(recipientId, postId.toString());

            if (unreadList.isEmpty()) {
                // No unread notification: create new single notification
                notificationService.createAndPush(
                        recipientId,
                        actor.getId(),
                        NotificationType.POST_UPVOTED,
                        postId.toString(),
                        "COMMUNITY_POST",
                        aggregatedMessage
                );
                return;
            }

            // Aggregate with existing unread notification
            com.cmcu.itstudy.entity.Notification existing = unreadList.get(0);
            existing.setMessage(aggregatedMessage);
            existing.setActor(actor);
            existing.setType(NotificationType.POST_UPVOTED);
            existing.setReferenceId(postId.toString());
            existing.setCreatedAt(LocalDateTime.now());
            existing.setRead(false);
            com.cmcu.itstudy.entity.Notification saved = notificationRepository.save(existing);

            // Push updated notification via SSE to recipient
            try {
                com.cmcu.itstudy.dto.notification.NotificationResponseDto dto = com.cmcu.itstudy.dto.notification.NotificationResponseDto.builder()
                        .id(saved.getId().toString())
                        .actorId(actor.getId().toString())
                        .actorName(actorName)
                        .actorAvatar(actor.getAvatarUrl())
                        .type(saved.getType())
                        .referenceId(saved.getReferenceId())
                        .referenceType(saved.getReferenceType())
                        .message(saved.getMessage())
                        .isRead(false)
                        .createdAt(saved.getCreatedAt())
                        .build();
                sseService.pushEvent(recipientId, "notification", dto);
            } catch (Exception sseEx) {
                log.warn("Failed to push aggregated upvote SSE notification to user {}: {}", recipientId, sseEx.getMessage());
            }

        } catch (Exception ex) {
            log.warn("Failed to send aggregated upvote notification: {}", ex.getMessage());
        }
    }

    private void handleCancelUpvoteNotification(UUID recipientId, CommunityPost post) {
        if (recipientId == null || post == null) return;
        UUID postId = post.getId();
        try {
            List<com.cmcu.itstudy.entity.Notification> unreadList =
                    notificationRepository.findUnreadPostUpvoteNotifications(recipientId, postId.toString());
            if (unreadList.isEmpty()) return;

            com.cmcu.itstudy.entity.Notification existing = unreadList.get(0);
            List<String> remainingUpvoters = likeRepository.findUpvoterNamesByPostOrderedByRecent(postId, recipientId);

            if (remainingUpvoters == null || remainingUpvoters.isEmpty()) {
                // No upvoters left: delete notification from DB
                notificationRepository.delete(existing);

                // Push removal event via SSE
                try {
                    Map<String, Object> removeData = new HashMap<>();
                    removeData.put("id", existing.getId().toString());
                    removeData.put("action", "DELETE");
                    sseService.pushEvent(recipientId, "notification-removed", removeData);
                    sseService.pushEvent(recipientId, "notification", removeData);
                } catch (Exception sseEx) {
                    log.warn("Failed to push remove upvote notification event to user {}: {}", recipientId, sseEx.getMessage());
                }
            } else {
                // Some upvoters still remain: recalculate message
                String updatedMessage;
                if (remainingUpvoters.size() == 1) {
                    updatedMessage = remainingUpvoters.get(0) + " đã thích bài viết của bạn.";
                } else if (remainingUpvoters.size() == 2) {
                    updatedMessage = remainingUpvoters.get(0) + " và " + remainingUpvoters.get(1) + " đã thích bài viết của bạn.";
                } else {
                    int othersCount = remainingUpvoters.size() - 1;
                    updatedMessage = remainingUpvoters.get(0) + " và " + othersCount + " người khác đã thích bài viết của bạn.";
                }

                existing.setMessage(updatedMessage);
                com.cmcu.itstudy.entity.Notification saved = notificationRepository.save(existing);

                try {
                    User actor = saved.getActor();
                    String actorName = (actor != null && actor.getFullName() != null) ? actor.getFullName() : "Ai đó";
                    String actorAvatar = actor != null ? actor.getAvatarUrl() : null;
                    com.cmcu.itstudy.dto.notification.NotificationResponseDto dto = com.cmcu.itstudy.dto.notification.NotificationResponseDto.builder()
                            .id(saved.getId().toString())
                            .actorId(actor != null ? actor.getId().toString() : null)
                            .actorName(actorName)
                            .actorAvatar(actorAvatar)
                            .type(saved.getType())
                            .referenceId(saved.getReferenceId())
                            .referenceType(saved.getReferenceType())
                            .message(saved.getMessage())
                            .isRead(false)
                            .createdAt(saved.getCreatedAt())
                            .build();
                    sseService.pushEvent(recipientId, "notification", dto);
                } catch (Exception sseEx) {
                    log.warn("Failed to push updated upvote SSE notification to user {}: {}", recipientId, sseEx.getMessage());
                }
            }
        } catch (Exception ex) {
            log.warn("Failed to handle cancel upvote notification: {}", ex.getMessage());
        }
    }

    @Override
    @Transactional
    public PostCommentResponseDto addComment(UUID postId, UUID userId, String body, UUID parentCommentId) {
        CommunityPost post = postRepository.findByIdWithAuthor(postId)
                .orElseThrow(() -> new NoSuchElementException("Post not found"));
        if (Boolean.TRUE.equals(post.getDeleted())) {
            throw new NoSuchElementException("Post not found");
        }
        if (Boolean.TRUE.equals(post.getAllowComments() == null ? Boolean.FALSE : !post.getAllowComments())) {
            throw new IllegalArgumentException("Bài viết này đã tắt tính năng bình luận.");
        }

        User author = userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("User not found"));

        CommunityPostComment.CommunityPostCommentBuilder builder = CommunityPostComment.builder()
                .post(post)
                .author(author)
                .body(body != null ? body.trim() : "")
                .likeCount(0)
                .upvoteCount(0)
                .downvoteCount(0)
                .deleted(false);

        CommunityPostComment parent = null;
        if (parentCommentId != null) {
            parent = commentRepository.findById(parentCommentId)
                    .orElseThrow(() -> new NoSuchElementException("Parent comment not found"));
            builder.parent(parent);
            builder.replyToUser(parent.getAuthor());
        }

        CommunityPostComment saved = commentRepository.save(builder.build());
        saved.setAuthor(author);
        if (parent != null) {
            saved.setParent(parent);
            saved.setReplyToUser(parent.getAuthor());
        }

        // Update denormalized comment count directly from database
        long totalComments = commentRepository.countByPost_IdAndDeletedFalse(postId);
        post.setCommentCount((int) totalComments);
        postRepository.saveAndFlush(post);

        int replyCount = 0;
        PostCommentResponseDto commentDto = CommunityPostMapper.toCommentResponse(saved, replyCount, false, null);
        commentDto.setPostCommentCount((int) totalComments);

        // 1. Real-time SSE broadcast of the new comment with exact commentCount
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("postId", postId.toString());
        eventData.put("comment", commentDto);
        eventData.put("commentCount", (int) totalComments);
        sseService.broadcast("new-comment", eventData);

        // 2. Create and push notification with Facebook-style text & aggregation
        String commenterName = (author.getFullName() != null && !author.getFullName().isBlank()) ? author.getFullName() : "Ai đó";
        String snippet = body != null && body.length() > 50 ? body.substring(0, 50) + "..." : (body != null ? body : "");
        String snippetSuffix = (snippet != null && !snippet.isBlank()) ? ": \"" + snippet + "\"" : "";
        String targetRefId = post.getId() + "?commentId=" + saved.getId();

        UUID postAuthorId = post.getAuthor() != null ? post.getAuthor().getId() : null;
        if (parent != null && parent.getAuthor() != null) {
            UUID parentAuthorId = parent.getAuthor().getId();
            String parentAuthorName = (parent.getAuthor().getFullName() != null && !parent.getAuthor().getFullName().isBlank())
                    ? parent.getAuthor().getFullName()
                    : "người dùng";

            // 2a. Gửi cho tác giả của bình luận trước (nếu không phải chính người phản hồi)
            if (!parentAuthorId.equals(userId)) {
                String parentMsg = commenterName + " đã trả lời bình luận của bạn" + snippetSuffix;
                sendAggregatedCommentNotificationIfUnmuted(parentAuthorId, author, post, targetRefId, NotificationType.COMMENT_REPLIED, parentMsg, true);
            }

            // 2b. Gửi cho tác giả bài viết (nếu khác tác giả bình luận trước và khác người phản hồi)
            if (postAuthorId != null && !postAuthorId.equals(parentAuthorId) && !postAuthorId.equals(userId)) {
                String postAuthorMsg = commenterName + " đã trả lời bình luận của " + parentAuthorName + " về bài viết của bạn" + snippetSuffix;
                sendAggregatedCommentNotificationIfUnmuted(postAuthorId, author, post, targetRefId, NotificationType.POST_COMMENTED, postAuthorMsg, false);
            }
        } else if (postAuthorId != null && !postAuthorId.equals(userId)) {
            // Bình luận trực tiếp vào bài viết
            String postMsg = commenterName + " đã bình luận về bài viết của bạn" + snippetSuffix;
            sendAggregatedCommentNotificationIfUnmuted(postAuthorId, author, post, targetRefId, NotificationType.POST_COMMENTED, postMsg, false);
        }

        return commentDto;
    }

    @Override
    @Transactional
    public void deleteComment(UUID commentId, UUID userId) {
        CommunityPostComment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new NoSuchElementException("Comment not found"));

        if (!comment.getAuthor().getId().equals(userId)) {
            throw new IllegalArgumentException("You can only delete your own comment");
        }

        comment.setDeleted(true);
        comment.setDeletedAt(LocalDateTime.now());
        commentRepository.save(comment);

        // Soft-delete replies
        List<CommunityPostComment> replies = commentRepository.findByParent_Id(commentId);
        if (!replies.isEmpty()) {
            for (CommunityPostComment r : replies) {
                r.setDeleted(true);
                r.setDeletedAt(LocalDateTime.now());
            }
            commentRepository.saveAll(replies);
        }

        // Update comment count directly from DB
        CommunityPost post = comment.getPost();
        long totalComments = commentRepository.countByPost_IdAndDeletedFalse(post.getId());
        post.setCommentCount((int) totalComments);
        postRepository.saveAndFlush(post);

        // Delete related notifications so when recipient refreshes page, notification is gone (like Facebook)
        try {
            notificationRepository.deleteByReferenceIdContaining("commentId=" + commentId);
            for (CommunityPostComment r : replies) {
                notificationRepository.deleteByReferenceIdContaining("commentId=" + r.getId());
            }
        } catch (Exception ex) {
            log.warn("Failed to delete notifications for comment {}: {}", commentId, ex.getMessage());
        }
    }

    @Override
    @Transactional
    public void hardDeleteCommentPhysics(UUID commentId) {
        CommunityPostComment comment = commentRepository.findById(commentId).orElse(null);
        if (comment == null) return;

        List<CommunityPostComment> replies = commentRepository.findByParent_Id(commentId);
        if (!replies.isEmpty()) {
            for (CommunityPostComment r : replies) {
                commentLikeRepository.deleteByCommentId(r.getId());
            }
            commentRepository.deleteAll(replies);
        }

        commentLikeRepository.deleteByCommentId(commentId);
        commentRepository.delete(comment);

        // Delete notifications when comment is hard-deleted
        try {
            notificationRepository.deleteByReferenceIdContaining("commentId=" + commentId);
            for (CommunityPostComment r : replies) {
                notificationRepository.deleteByReferenceIdContaining("commentId=" + r.getId());
            }
        } catch (Exception ex) {
            log.warn("Failed to delete notifications for hard-deleted comment {}: {}", commentId, ex.getMessage());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<PostCommentResponseDto> getComments(UUID postId, int page, int size, UUID currentUserId) {
        Page<CommunityPostComment> commentPage = commentRepository
                .findRootCommentsByPostId(postId, PageRequest.of(page, size));

        List<CommunityPostComment> content = commentPage.getContent();
        if (content.isEmpty()) return List.of();

        List<UUID> commentIds = content.stream().map(CommunityPostComment::getId).toList();
        Map<UUID, String> userVoteMap = new HashMap<>();
        if (currentUserId != null) {
            List<CommunityPostCommentLike> likes = commentLikeRepository.findAllByCommentIdInAndUserId(commentIds, currentUserId);
            for (CommunityPostCommentLike l : likes) {
                userVoteMap.put(l.getComment().getId(), l.getVoteType() != null ? l.getVoteType() : "UPVOTE");
            }
        }

        return content.stream().map(c -> {
            int replyCount = (int) commentRepository.countRepliesByParentId(c.getId());
            String userVote = userVoteMap.get(c.getId());
            boolean isLiked = "UPVOTE".equalsIgnoreCase(userVote);
            return CommunityPostMapper.toCommentResponse(c, replyCount, isLiked, userVote);
        }).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<PostCommentResponseDto> getReplies(UUID commentId, UUID currentUserId) {
        List<CommunityPostComment> replies = commentRepository
                .findRepliesByParentId(commentId);

        if (replies.isEmpty()) return List.of();

        List<UUID> replyIds = replies.stream().map(CommunityPostComment::getId).toList();
        Map<UUID, String> userVoteMap = new HashMap<>();
        if (currentUserId != null) {
            List<CommunityPostCommentLike> likes = commentLikeRepository.findAllByCommentIdInAndUserId(replyIds, currentUserId);
            for (CommunityPostCommentLike l : likes) {
                userVoteMap.put(l.getComment().getId(), l.getVoteType() != null ? l.getVoteType() : "UPVOTE");
            }
        }

        return replies.stream()
                .map(c -> {
                    String userVote = userVoteMap.get(c.getId());
                    boolean isLiked = "UPVOTE".equalsIgnoreCase(userVote);
                    return CommunityPostMapper.toCommentResponse(c, 0, isLiked, userVote);
                })
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public PostCommentResponseDto toggleLikeComment(UUID commentId, UUID userId) {
        return voteComment(commentId, userId, "UPVOTE");
    }

    @Override
    @Transactional
    public PostCommentResponseDto voteComment(UUID commentId, UUID userId, String voteType) {
        String targetVote = ("DOWNVOTE".equalsIgnoreCase(voteType)) ? "DOWNVOTE" : "UPVOTE";

        CommunityPostComment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new NoSuchElementException("Comment not found"));
        if (Boolean.TRUE.equals(comment.getDeleted())) {
            throw new NoSuchElementException("Comment not found");
        }

        User userRef = userRepository.getReferenceById(userId);
        Optional<CommunityPostCommentLike> existing = commentLikeRepository.findByComment_IdAndUser_Id(commentId, userId);

        String resultVote = null;

        if (existing.isPresent()) {
            CommunityPostCommentLike currentLike = existing.get();
            String currentVoteType = currentLike.getVoteType() != null ? currentLike.getVoteType() : "UPVOTE";

            if (currentVoteType.equalsIgnoreCase(targetVote)) {
                // Toggle off
                commentLikeRepository.delete(currentLike);
                resultVote = null;
            } else {
                // Switch vote type
                currentLike.setVoteType(targetVote);
                commentLikeRepository.save(currentLike);
                resultVote = targetVote;
            }
        } else {
            // New vote
            commentLikeRepository.save(CommunityPostCommentLike.builder()
                    .comment(comment)
                    .user(userRef)
                    .voteType(targetVote)
                    .build());
            resultVote = targetVote;
        }

        commentLikeRepository.flush();

        // Exact count from database
        int upvotes = (int) commentLikeRepository.countByComment_IdAndVoteType(commentId, "UPVOTE");
        int downvotes = (int) commentLikeRepository.countByComment_IdAndVoteType(commentId, "DOWNVOTE");

        comment.setUpvoteCount(upvotes);
        comment.setDownvoteCount(downvotes);
        comment.setLikeCount(upvotes - downvotes);
        commentRepository.saveAndFlush(comment);

        // Push notification when comment is upvoted / cancelled
        if ("UPVOTE".equals(targetVote) && "UPVOTE".equals(resultVote)) {
            if (!comment.getAuthor().getId().equals(userId)) {
                try {
                    User liker = userRepository.findById(userId).orElse(null);
                    String likerName = (liker != null && liker.getFullName() != null) ? liker.getFullName() : "Ai đó";
                    notificationService.createAndPush(
                            comment.getAuthor().getId(),
                            userId,
                            NotificationType.COMMENT_LIKED,
                            comment.getPost().getId().toString() + "?commentId=" + comment.getId(),
                            "COMMUNITY_POST",
                            likerName + " đã thích bình luận của bạn."
                    );
                } catch (Exception ignored) {}
            }
        } else {
            try {
                notificationRepository.deleteByReferenceIdContaining("commentId=" + commentId);
            } catch (Exception ignored) {}
        }

        // Real-time SSE broadcast of comment vote count
        Map<String, Object> commentLikeData = new HashMap<>();
        commentLikeData.put("commentId", commentId.toString());
        commentLikeData.put("postId", comment.getPost().getId().toString());
        commentLikeData.put("upvoteCount", upvotes);
        commentLikeData.put("downvoteCount", downvotes);
        commentLikeData.put("likeCount", upvotes - downvotes);
        commentLikeData.put("score", upvotes - downvotes);
        sseService.broadcast("comment-voted", commentLikeData);
        sseService.broadcast("comment-liked", commentLikeData);

        int replyCount = (int) commentRepository.countRepliesByParentId(commentId);
        boolean isLiked = "UPVOTE".equals(resultVote);
        return CommunityPostMapper.toCommentResponse(comment, replyCount, isLiked, resultVote);
    }

    @Override
    @Transactional
    public CommunityPostResponseDto updatePost(UUID postId, UUID userId, String content, List<String> imageUrls) {
        CommunityPost post = postRepository.findById(postId)
                .orElseThrow(() -> new NoSuchElementException("Post not found"));
        if (Boolean.TRUE.equals(post.getDeleted())) {
            throw new NoSuchElementException("Post not found");
        }

        if (!post.getAuthor().getId().equals(userId)) {
            throw new IllegalArgumentException("You can only edit your own post");
        }

        post.setContent(content);
        postRepository.save(post);

        if (imageUrls != null) {
            imageRepository.deleteByPostId(postId);
            imageRepository.flush();
            for (int i = 0; i < Math.min(imageUrls.size(), 4); i++) {
                imageRepository.save(CommunityPostImage.builder()
                        .post(post)
                        .imageUrl(imageUrls.get(i))
                        .displayOrder(i)
                        .build());
            }
        }

        return getPostResponseForUser(post, userId);
    }

    // ================== Report & Moderation ==================

    @Override
    @Transactional
    public void reportPost(UUID postId, UUID reporterId, String reasonCode, String detail) {
        CommunityPost post = postRepository.findById(postId)
                .orElseThrow(() -> new NoSuchElementException("Bài viết không tồn tại"));

        Optional<CommunityPostReport> existingOpt = reportRepository.findByPostIdAndReporterId(postId, reporterId);
        if (existingOpt.isPresent()) {
            CommunityPostReport existingReport = existingOpt.get();
            if ("PENDING".equalsIgnoreCase(existingReport.getStatus())) {
                throw new IllegalArgumentException("Bạn đã báo cáo bài viết này rồi và đang chờ xử lý.");
            }

            existingReport.setReasonCode(reasonCode);
            existingReport.setDetail(detail);
            existingReport.setStatus("PENDING");
            existingReport.setCreatedAt(LocalDateTime.now());
            existingReport.setResolvedAt(null);
            existingReport.setResolvedBy(null);

            reportRepository.save(existingReport);
            return;
        }

        User reporter = userRepository.findById(reporterId)
                .orElseThrow(() -> new NoSuchElementException("User không tồn tại"));

        CommunityPostReport report = CommunityPostReport.builder()
                .post(post)
                .reporter(reporter)
                .reasonCode(reasonCode)
                .detail(detail)
                .status("PENDING")
                .build();

        reportRepository.save(report);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PostReportResponseDto> getReportedPosts(String status, String keyword, java.time.LocalDateTime startDate, java.time.LocalDateTime endDate, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size);
        String st = (status != null && !status.isBlank()) ? status.toUpperCase() : null;
        String kw = (keyword != null && !keyword.isBlank()) ? keyword.trim() : null;
        Page<CommunityPostReport> reports = reportRepository.searchReports(st, kw, startDate, endDate, pageRequest);

        return reports.map(r -> {
            CommunityPost p = r.getPost();
            if (p == null) return null;

            User reporter = r.getReporter();
            User author = p.getAuthor();
            long count = reportRepository.countByPostId(p.getId());

            User escalatedBy = r.getEscalatedBy();

            return PostReportResponseDto.builder()
                    .id(r.getId().toString())
                    .postId(p.getId().toString())
                    .postTitle(p.getTitle())
                    .postContent(p.getContent())
                    .postAuthorId(author != null ? author.getId().toString() : null)
                    .postAuthorName(author != null ? author.getFullName() : "Không xác định")
                    .postAuthorAvatar(author != null ? author.getAvatarUrl() : null)
                    .reporterId(reporter != null ? reporter.getId().toString() : null)
                    .reporterName(reporter != null ? reporter.getFullName() : "Không xác định")
                    .reporterAvatar(reporter != null ? reporter.getAvatarUrl() : null)
                    .reasonCode(r.getReasonCode())
                    .detail(r.getDetail())
                    .status(r.getStatus())
                    .reportCount(count)
                    .isPostHidden(Boolean.TRUE.equals(p.getHidden()))
                    .isPostDeleted(Boolean.TRUE.equals(p.getDeleted()))
                    .escalationReason(r.getEscalationReason())
                    .escalatedByName(escalatedBy != null ? escalatedBy.getFullName() : null)
                    .escalatedAt(r.getEscalatedAt())
                    .createdAt(r.getCreatedAt())
                    .build();
        });
    }

    @Override
    @Transactional
    public void resolveReport(UUID reportId, UUID resolverId) {
        CommunityPostReport report = reportRepository.findById(reportId)
                .orElseThrow(() -> new NoSuchElementException("Báo cáo không tồn tại"));
        report.setStatus("RESOLVED");
        reportRepository.save(report);
    }

    @Override
    @Transactional
    public void dismissReport(UUID reportId, UUID resolverId, String reason) {
        CommunityPostReport report = reportRepository.findById(reportId)
                .orElseThrow(() -> new NoSuchElementException("Báo cáo không tồn tại"));

        reportRepository.updateStatusByReportId(reportId, "DISMISSED");

        try {
            User reporter = report.getReporter();
            UUID reporterId = (reporter != null) ? reporter.getId() : null;
            if (reporterId != null) {
                String msg = "Báo cáo bài viết của bạn đã bị từ chối/bỏ qua bởi quản trị viên cộng đồng.";
                if (StringUtils.hasText(reason)) {
                    msg += " Lý do: " + reason.trim();
                }
                notificationService.createAndPush(
                        reporterId,
                        resolverId,
                        NotificationType.REPORT_DISMISSED,
                        report.getPost() != null ? report.getPost().getId().toString() : reportId.toString(),
                        "COMMUNITY_POST_REPORT",
                        msg
                );
            }
        } catch (Exception e) {
            log.warn("Failed to push dismiss notification for report {}: {}", reportId, e.getMessage());
        }
    }

    @Override
    @Transactional
    public void dismissReportByPostId(UUID postId, UUID resolverId, String reason) {
        List<CommunityPostReport> reports = reportRepository.findByPostId(postId);
        log.info("[DISMISS_REPORT] dismissReportByPostId called for postId: {}, found {} reports", postId, (reports != null ? reports.size() : 0));

        // Direct SQL update status to DISMISSED
        int updatedCount = reportRepository.updateStatusByPostId(postId, "DISMISSED");
        log.info("[DISMISS_REPORT] Updated {} reports status to DISMISSED for postId: {}", updatedCount, postId);

        if (reports != null && !reports.isEmpty()) {
            for (CommunityPostReport report : reports) {
                try {
                    User reporter = report.getReporter();
                    UUID reporterId = (reporter != null) ? reporter.getId() : null;
                    log.info("[DISMISS_REPORT] Processing report ID: {}, reporterId: {}", report.getId(), reporterId);
                    if (reporterId != null) {
                        String msg = "Báo cáo bài viết của bạn đã bị từ chối/bỏ qua bởi quản trị viên cộng đồng.";
                        if (StringUtils.hasText(reason)) {
                            msg += " Lý do: " + reason.trim();
                        }
                        notificationService.createAndPush(
                                reporterId,
                                resolverId,
                                NotificationType.REPORT_DISMISSED,
                                postId.toString(),
                                "COMMUNITY_POST_REPORT",
                                msg
                        );
                        log.info("[DISMISS_REPORT] Pushed REPORT_DISMISSED notification to reporterId: {}", reporterId);
                    }
                } catch (Exception e) {
                    log.warn("[DISMISS_REPORT] Failed to push dismiss notification for post {}: {}", postId, e.getMessage());
                }
            }
        }
    }

    @Override
    @Transactional
    public void hidePost(UUID postId, UUID moderatorId, String reason) {
        CommunityPost post = postRepository.findById(postId)
                .orElseThrow(() -> new NoSuchElementException("Bài viết không tồn tại"));
        post.setHidden(true);
        postRepository.save(post);

        reportRepository.updateStatusByPostId(postId, "RESOLVED");

        String adminEmail = userRepository.findById(moderatorId)
                .map(User::getEmail)
                .filter(StringUtils::hasText)
                .orElse("admin@example.com");

        String msg = "Bài viết của bạn đã bị ẩn bởi quản trị viên cộng đồng.";
        if (StringUtils.hasText(reason)) {
            msg += " Lý do: " + reason.trim();
        }
        msg += ". Vui lòng liên hệ tới email quản trị viên (" + adminEmail + ") nếu bạn có thắc mắc hoặc khiếu nại.";

        notificationService.createAndPush(
                post.getAuthor().getId(),
                moderatorId,
                NotificationType.POST_HIDDEN,
                post.getId().toString(),
                "COMMUNITY_POST",
                msg
        );
    }

    @Override
    @Transactional
    public void unhidePost(UUID postId, UUID moderatorId, String reason) {
        CommunityPost post = postRepository.findById(postId)
                .orElseThrow(() -> new NoSuchElementException("Bài viết không tồn tại"));

        List<CommunityPostReport> postReports = reportRepository.findByPostId(postId);
        if (postReports != null && postReports.stream().anyMatch(r -> "ESCALATED".equalsIgnoreCase(r.getStatus()))) {
            throw new IllegalArgumentException("Bài viết này đã được chuyển lên Admin xử lý. Quản lý cộng đồng không thể tự mở ẩn.");
        }

        post.setHidden(false);
        postRepository.save(post);

        // 1. Send notification to Post Author
        try {
            if (post.getAuthor() != null) {
                String msg = "Bài viết của bạn đã được hiển thị lại trên cộng đồng.";
                if (StringUtils.hasText(reason)) {
                    msg += " Lý do: " + reason.trim();
                }
                notificationService.createAndPush(
                        post.getAuthor().getId(),
                        moderatorId,
                        NotificationType.POST_HIDDEN,
                        post.getId().toString(),
                        "COMMUNITY_POST",
                        msg
                );
            }
        } catch (Exception e) {
            log.warn("Failed to push unhide notification to post author: {}", e.getMessage());
        }

        // 2. Send notification to Reporters
        try {
            List<CommunityPostReport> reports = reportRepository.findByPostId(postId);
            if (reports != null && !reports.isEmpty()) {
                for (CommunityPostReport report : reports) {
                    User reporter = report.getReporter();
                    UUID reporterId = (reporter != null) ? reporter.getId() : null;
                    if (reporterId != null) {
                        String msg = "Bài viết bạn từng báo cáo đã được ban quản trị xét duyệt và hiển thị lại.";
                        if (StringUtils.hasText(reason)) {
                            msg += " Lý do: " + reason.trim();
                        }
                        notificationService.createAndPush(
                                reporterId,
                                moderatorId,
                                NotificationType.REPORT_DISMISSED,
                                postId.toString(),
                                "COMMUNITY_POST_REPORT",
                                msg
                        );
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to push unhide notification to reporters: {}", e.getMessage());
        }
    }

    @Override
    @Transactional
    public boolean toggleMutePostNotifications(UUID postId, UUID userId) {
        CommunityPost post = postRepository.findById(postId)
                .orElseThrow(() -> new NoSuchElementException("Bài viết không tồn tại"));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("Người dùng không tồn tại"));

        Optional<CommunityPostNotificationMute> existing = notificationMuteRepository.findByPost_IdAndUser_Id(postId, userId);
        if (existing.isPresent()) {
            notificationMuteRepository.delete(existing.get());
            notificationMuteRepository.flush();
            return false;
        } else {
            notificationMuteRepository.save(CommunityPostNotificationMute.builder()
                    .post(post)
                    .user(user)
                    .build());
            notificationMuteRepository.flush();
            return true;
        }
    }

    @Override
    @Transactional
    public void moderatorDeletePost(UUID postId, UUID moderatorId, String reason) {
        CommunityPost post = postRepository.findById(postId)
                .orElseThrow(() -> new NoSuchElementException("Bài viết không tồn tại"));
        post.setDeleted(true);
        post.setDeletedAt(LocalDateTime.now());
        postRepository.save(post);

        List<CommunityPostReport> reports = reportRepository.findByPostId(postId);
        if (reports != null && !reports.isEmpty()) {
            for (CommunityPostReport r : reports) {
                r.setStatus("RESOLVED");
            }
            reportRepository.saveAll(reports);
        }

        String msg = "Bài viết của bạn đã bị xóa bởi quản trị viên cộng đồng.";
        if (StringUtils.hasText(reason)) {
            msg += " Lý do: " + reason.trim();
        }

        notificationService.createAndPush(
                post.getAuthor().getId(),
                moderatorId,
                NotificationType.POST_DELETED,
                post.getId().toString(),
                "COMMUNITY_POST",
                msg
        );
    }

    @Override
    @Transactional(readOnly = true)
    public CommunityModerationStatsDto getModerationStats() {
        long pendingPosts = reportRepository.countDistinctPostByStatus("PENDING");
        long pendingReports = reportRepository.countByStatusAndPostDeletedFalse("PENDING");

        long resolvedPosts = reportRepository.countDistinctPostByStatus("RESOLVED");
        long resolvedReports = reportRepository.countByStatusAndPostDeletedFalse("RESOLVED");

        long dismissedPosts = reportRepository.countDistinctPostByStatus("DISMISSED");
        long dismissedReports = reportRepository.countByStatusAndPostDeletedFalse("DISMISSED");

        long hiddenPosts = reportRepository.countDistinctHiddenReportedPosts();

        return CommunityModerationStatsDto.builder()
                .pendingPostsCount(pendingPosts)
                .pendingReportsCount(pendingReports)
                .resolvedPostsCount(resolvedPosts)
                .resolvedReportsCount(resolvedReports)
                .dismissedPostsCount(dismissedPosts)
                .dismissedReportsCount(dismissedReports)
                .hiddenPostsCount(hiddenPosts)
                .build();
    }

    @Override
    @Transactional
    public void escalateReport(UUID reportId, UUID moderatorId, String reason) {
        CommunityPostReport report = reportRepository.findById(reportId)
                .orElseThrow(() -> new NoSuchElementException("Báo cáo không tồn tại"));

        User moderator = userRepository.findById(moderatorId)
                .orElseThrow(() -> new NoSuchElementException("Người dùng không tồn tại"));

        CommunityPost post = report.getPost();
        if (post == null) {
            throw new NoSuchElementException("Bài viết không tồn tại");
        }

        // 1. Hide post immediately
        post.setHidden(true);
        postRepository.save(post);

        // 2. Mark all reports of this post as ESCALATED with escalation details
        List<CommunityPostReport> postReports = reportRepository.findByPostId(post.getId());
        LocalDateTime now = LocalDateTime.now();
        if (postReports != null && !postReports.isEmpty()) {
            for (CommunityPostReport r : postReports) {
                r.setStatus("ESCALATED");
                r.setEscalationReason(reason);
                r.setEscalatedBy(moderator);
                r.setEscalatedAt(now);
            }
            reportRepository.saveAll(postReports);
        } else {
            report.setStatus("ESCALATED");
            report.setEscalationReason(reason);
            report.setEscalatedBy(moderator);
            report.setEscalatedAt(now);
            reportRepository.save(report);
        }

        // 3. Send notification to author that post is under review by admin
        try {
            String msg = "Bài viết của bạn đang được chuyển lên Ban Quản Trị cấp cao (Admin) để xem xét giải quyết.";
            if (StringUtils.hasText(reason)) {
                msg += " Lý do chuyển tiếp: " + reason.trim();
            }
            notificationService.createAndPush(
                    post.getAuthor().getId(),
                    moderatorId,
                    NotificationType.POST_HIDDEN,
                    post.getId().toString(),
                    "COMMUNITY_POST",
                    msg
            );
        } catch (Exception e) {
            log.warn("Failed to push escalate notification to post author: {}", e.getMessage());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PostReportResponseDto> getEscalatedReports(String status, String keyword, java.time.LocalDateTime startDate, java.time.LocalDateTime endDate, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size);
        String targetStatus = (status != null && !status.isBlank()) ? status.trim().toUpperCase() : "ESCALATED";
        String kw = (keyword != null && !keyword.isBlank()) ? keyword.trim() : null;
        Page<CommunityPostReport> reports = reportRepository.searchReports(targetStatus, kw, startDate, endDate, pageRequest);

        return reports.map(r -> {
            CommunityPost p = r.getPost();
            if (p == null) return null;

            User reporter = r.getReporter();
            User author = p.getAuthor();
            long count = reportRepository.countByPostId(p.getId());
            User escalatedBy = r.getEscalatedBy();
            User resolvedBy = r.getResolvedBy();

            return PostReportResponseDto.builder()
                    .id(r.getId().toString())
                    .postId(p.getId().toString())
                    .postTitle(p.getTitle())
                    .postContent(p.getContent())
                    .postAuthorId(author != null ? author.getId().toString() : null)
                    .postAuthorName(author != null ? author.getFullName() : "Không xác định")
                    .postAuthorAvatar(author != null ? author.getAvatarUrl() : null)
                    .authorStatus(author != null ? author.getStatus() : null)
                    .reporterId(reporter != null ? reporter.getId().toString() : null)
                    .reporterName(reporter != null ? reporter.getFullName() : "Không xác định")
                    .reporterAvatar(reporter != null ? reporter.getAvatarUrl() : null)
                    .reasonCode(r.getReasonCode())
                    .detail(r.getDetail())
                    .status(r.getStatus())
                    .reportCount(count)
                    .isPostHidden(Boolean.TRUE.equals(p.getHidden()))
                    .isPostDeleted(Boolean.TRUE.equals(p.getDeleted()))
                    .escalationReason(r.getEscalationReason())
                    .escalatedByName(escalatedBy != null ? escalatedBy.getFullName() : null)
                    .escalatedAt(r.getEscalatedAt())
                    .resolutionNotes(r.getResolutionNotes())
                    .resolvedByName(resolvedBy != null ? resolvedBy.getFullName() : null)
                    .createdAt(r.getCreatedAt())
                    .resolvedAt(r.getResolvedAt())
                    .build();
        });
    }

    @Override
    @Transactional
    public void adminBanUserFromReport(UUID reportId, UUID adminId, String reason) {
        CommunityPostReport report = reportRepository.findById(reportId)
                .orElseThrow(() -> new NoSuchElementException("Báo cáo không tồn tại"));

        CommunityPost post = report.getPost();
        if (post == null) {
            throw new NoSuchElementException("Bài viết không tồn tại");
        }

        User author = post.getAuthor();
        if (author == null) {
            throw new NoSuchElementException("Tác giả bài viết không tồn tại");
        }

        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new NoSuchElementException("Admin không tồn tại"));

        // 1. Lock user account
        author.setStatus("LOCKED");
        author.setUpdatedAt(LocalDateTime.now());
        userRepository.save(author);

        // 2. Revoke all tokens
        refreshTokenRepository.revokeAllByUserId(author.getId());

        // 3. Hide ALL community posts by this author
        postRepository.hideAllByAuthorId(author.getId());

        // 4. Hide ALL documents by this author
        documentRepository.hideAllByCreatedById(author.getId());

        // 5. Mark all reports of this post as RESOLVED_BAN and record reason
        List<CommunityPostReport> postReports = reportRepository.findByPostId(post.getId());
        LocalDateTime now = LocalDateTime.now();
        String banReason = (reason != null && !reason.isBlank()) ? reason.trim() : "Vi phạm quy chuẩn cộng đồng";

        if (postReports != null && !postReports.isEmpty()) {
            for (CommunityPostReport r : postReports) {
                r.setStatus("RESOLVED_BAN");
                r.setResolvedAt(now);
                r.setResolvedBy(admin);
                r.setResolutionNotes(banReason);
            }
            reportRepository.saveAll(postReports);
        } else {
            report.setStatus("RESOLVED_BAN");
            report.setResolvedAt(now);
            report.setResolvedBy(admin);
            report.setResolutionNotes(banReason);
            reportRepository.save(report);
        }

        // 6. Send notification to author WITHOUT reason (generic notice)
        try {
            String msg = "Tài khoản của bạn đã bị khóa bởi Ban Quản Trị do vi phạm quy chuẩn cộng đồng. Vui lòng liên hệ support@itstudy.edu.vn nếu bạn có khiếu nại.";
            notificationService.createAndPush(
                    author.getId(),
                    adminId,
                    NotificationType.POST_DELETED,
                    post.getId().toString(),
                    "USER_ACCOUNT",
                    msg
            );
        } catch (Exception e) {
            log.warn("Failed to push ban notification to author: {}", e.getMessage());
        }
    }

    @Override
    @Transactional
    public void adminUnbanUserFromReport(UUID reportId, UUID adminId, String reason) {
        CommunityPostReport report = reportRepository.findById(reportId)
                .orElseThrow(() -> new NoSuchElementException("Báo cáo không tồn tại"));

        CommunityPost post = report.getPost();
        if (post == null) {
            throw new NoSuchElementException("Bài viết không tồn tại");
        }

        User author = post.getAuthor();
        if (author == null) {
            throw new NoSuchElementException("Tác giả bài viết không tồn tại");
        }

        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new NoSuchElementException("Admin không tồn tại"));

        // 1. Unlock user account
        author.setStatus("ACTIVE");
        author.setUpdatedAt(LocalDateTime.now());
        userRepository.save(author);

        // 2. Unhide ALL community posts by this author
        postRepository.unhideAllByAuthorId(author.getId());

        // 3. Unhide ALL documents by this author
        documentRepository.unhideAllByCreatedById(author.getId());

        // 4. Update reports status to RESOLVED_UNBAN
        List<CommunityPostReport> postReports = reportRepository.findByPostId(post.getId());
        LocalDateTime now = LocalDateTime.now();
        String unbanNote = (reason != null && !reason.isBlank()) ? reason.trim() : null;

        if (postReports != null && !postReports.isEmpty()) {
            for (CommunityPostReport r : postReports) {
                r.setStatus("RESOLVED_UNBAN");
                r.setResolvedAt(now);
                r.setResolvedBy(admin);
                if (unbanNote != null) {
                    r.setResolutionNotes((r.getResolutionNotes() != null ? r.getResolutionNotes() + " | Mở khóa: " : "Mở khóa: ") + unbanNote);
                }
            }
            reportRepository.saveAll(postReports);
        } else {
            report.setStatus("RESOLVED_UNBAN");
            report.setResolvedAt(now);
            report.setResolvedBy(admin);
            if (unbanNote != null) {
                report.setResolutionNotes((report.getResolutionNotes() != null ? report.getResolutionNotes() + " | Mở khóa: " : "Mở khóa: ") + unbanNote);
            }
            reportRepository.save(report);
        }

        // 5. Send unban notification to author (with note if provided)
        try {
            String msg = "Tài khoản của bạn đã được mở khóa bởi Ban Quản Trị. Các bài viết và tài liệu của bạn đã được khôi phục hiển thị.";
            if (StringUtils.hasText(unbanNote)) {
                msg += " Ghi chú: " + unbanNote;
            }
            notificationService.createAndPush(
                    author.getId(),
                    adminId,
                    NotificationType.REPORT_DISMISSED,
                    post.getId().toString(),
                    "USER_ACCOUNT",
                    msg
            );
        } catch (Exception e) {
            log.warn("Failed to push unban notification to author: {}", e.getMessage());
        }
    }

    @Override
    @Transactional
    public void adminAcquitReport(UUID reportId, UUID adminId, String reason) {
        CommunityPostReport report = reportRepository.findById(reportId)
                .orElseThrow(() -> new NoSuchElementException("Báo cáo không tồn tại"));

        CommunityPost post = report.getPost();
        if (post == null) {
            throw new NoSuchElementException("Bài viết không tồn tại");
        }

        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new NoSuchElementException("Admin không tồn tại"));

        // 1. Soft-delete post (User account remains ACTIVE)
        LocalDateTime now = LocalDateTime.now();
        post.setDeleted(true);
        post.setDeletedAt(now);
        post.setHidden(true);
        postRepository.save(post);

        // 2. Mark all reports of this post as DISMISSED
        List<CommunityPostReport> postReports = reportRepository.findByPostId(post.getId());
        String dismissReason = (reason != null && !reason.isBlank()) ? reason.trim() : null;

        if (postReports != null && !postReports.isEmpty()) {
            for (CommunityPostReport r : postReports) {
                r.setStatus("DISMISSED");
                r.setResolvedAt(now);
                r.setResolvedBy(admin);
                r.setResolutionNotes(dismissReason);
            }
            reportRepository.saveAll(postReports);
        } else {
            report.setStatus("DISMISSED");
            report.setResolvedAt(now);
            report.setResolvedBy(admin);
            report.setResolutionNotes(dismissReason);
            reportRepository.save(report);
        }

        // 3. Send notification to author that post has been deleted
        try {
            String msg = "Bài viết của bạn đã bị xóa bởi Ban Quản Trị sau khi xem xét báo cáo vi phạm (Tài khoản của bạn vẫn hoạt động bình thường).";
            if (StringUtils.hasText(dismissReason)) {
                msg += " Ghi chú: " + dismissReason;
            }
            notificationService.createAndPush(
                    post.getAuthor().getId(),
                    adminId,
                    NotificationType.POST_DELETED,
                    post.getId().toString(),
                    "COMMUNITY_POST",
                    msg
            );
        } catch (Exception e) {
            log.warn("Failed to push acquit/delete notification to author: {}", e.getMessage());
        }
    }
}
