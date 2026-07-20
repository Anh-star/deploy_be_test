package com.cmcu.itstudy.service.impl;

import com.cmcu.itstudy.dto.community.CommunityPostResponseDto;
import com.cmcu.itstudy.dto.community.PostCommentResponseDto;
import com.cmcu.itstudy.entity.CommunityPost;
import com.cmcu.itstudy.entity.CommunityPostComment;
import com.cmcu.itstudy.entity.CommunityPostCommentLike;
import com.cmcu.itstudy.entity.CommunityPostImage;
import com.cmcu.itstudy.entity.CommunityPostLike;
import com.cmcu.itstudy.entity.User;
import com.cmcu.itstudy.mapper.CommunityPostMapper;
import com.cmcu.itstudy.repository.CommunityPostCommentLikeRepository;
import com.cmcu.itstudy.repository.CommunityPostCommentRepository;
import com.cmcu.itstudy.repository.CommunityPostImageRepository;
import com.cmcu.itstudy.repository.CommunityPostLikeRepository;
import com.cmcu.itstudy.repository.CommunityPostRepository;
import com.cmcu.itstudy.repository.UserRepository;
import com.cmcu.itstudy.service.contract.CommunityPostService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class CommunityPostServiceImpl implements CommunityPostService {

    private final CommunityPostRepository postRepository;
    private final CommunityPostImageRepository imageRepository;
    private final CommunityPostLikeRepository likeRepository;
    private final CommunityPostCommentRepository commentRepository;
    private final CommunityPostCommentLikeRepository commentLikeRepository;
    private final UserRepository userRepository;

    public CommunityPostServiceImpl(
            CommunityPostRepository postRepository,
            CommunityPostImageRepository imageRepository,
            CommunityPostLikeRepository likeRepository,
            CommunityPostCommentRepository commentRepository,
            CommunityPostCommentLikeRepository commentLikeRepository,
            UserRepository userRepository
    ) {
        this.postRepository = postRepository;
        this.imageRepository = imageRepository;
        this.likeRepository = likeRepository;
        this.commentRepository = commentRepository;
        this.commentLikeRepository = commentLikeRepository;
        this.userRepository = userRepository;
    }

    @Override
    @Transactional
    public CommunityPostResponseDto createPost(UUID userId, String content, List<String> imageUrls) {
        User author = userRepository.getReferenceById(userId);

        CommunityPost post = postRepository.save(CommunityPost.builder()
                .author(author)
                .content(content)
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

        // Re-fetch to get author details
        CommunityPost saved = postRepository.findByIdWithAuthor(post.getId()).orElse(post);
        return CommunityPostMapper.toPostResponse(saved, images, false);
    }

    @Override
    @Transactional(readOnly = true)
    public CommunityPostResponseDto getPostById(UUID postId, UUID currentUserId) {
        CommunityPost post = postRepository.findByIdWithAuthor(postId)
                .orElseThrow(() -> new NoSuchElementException("Post not found"));

        List<CommunityPostImage> images = imageRepository.findByPostIdOrderByDisplayOrderAsc(postId);
        boolean isLiked = false;
        if (currentUserId != null) {
            isLiked = likeRepository.findByPost_IdAndUser_Id(postId, currentUserId).isPresent();
        }

        return CommunityPostMapper.toPostResponse(post, images, isLiked);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CommunityPostResponseDto> getFeed(int page, int size, UUID currentUserId) {
        Page<CommunityPost> postPage = postRepository.findByDeletedFalseOrderByCreatedAtDesc(
                PageRequest.of(page, size)
        );

        List<CommunityPost> posts = postPage.getContent();
        if (posts.isEmpty()) return List.of();

        List<UUID> postIds = posts.stream().map(CommunityPost::getId).toList();

        // Batch fetch liked post ids for current user
        Set<UUID> likedPostIds = new HashSet<>();
        if (currentUserId != null) {
            likedPostIds.addAll(postRepository.findLikedPostIds(postIds, currentUserId));
        }

        return posts.stream().map(post -> {
            List<CommunityPostImage> images = imageRepository.findByPostIdOrderByDisplayOrderAsc(post.getId());
            boolean isLiked = likedPostIds.contains(post.getId());
            return CommunityPostMapper.toPostResponse(post, images, isLiked);
        }).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public long getFeedTotalCount() {
        return postRepository.count();
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
        post.setDeletedAt(java.time.LocalDateTime.now());
        postRepository.save(post);
    }

    @Override
    @Transactional
    public void hardDeletePostPhysics(UUID postId) {
        CommunityPost post = postRepository.findById(postId).orElse(null);
        if (post == null) return;

        // 1. Delete all comment likes associated with comments on this post
        List<CommunityPostComment> comments = commentRepository.findByPost_Id(postId);
        if (!comments.isEmpty()) {
            for (CommunityPostComment c : comments) {
                commentLikeRepository.deleteByCommentId(c.getId());
            }
            // 2. Delete all comments on this post
            commentRepository.deleteAll(comments);
        }

        // 3. Delete all post likes
        likeRepository.deleteByPostId(postId);

        // 4. Delete all post images
        imageRepository.deleteByPostId(postId);

        // 5. Delete the post itself
        postRepository.delete(post);
    }

    @Override
    @Transactional
    public CommunityPostResponseDto toggleLikePost(UUID postId, UUID userId) {
        CommunityPost post = postRepository.findByIdWithAuthor(postId)
                .orElseThrow(() -> new NoSuchElementException("Post not found"));

        User userRef = userRepository.getReferenceById(userId);
        var existing = likeRepository.findByPost_IdAndUser_Id(postId, userId);

        boolean isLiked;
        if (existing.isPresent()) {
            likeRepository.delete(existing.get());
            likeRepository.flush();
            post.setLikeCount(Math.max(0, post.getLikeCount() - 1));
            isLiked = false;
        } else {
            likeRepository.save(CommunityPostLike.builder()
                    .post(post)
                    .user(userRef)
                    .build());
            post.setLikeCount(post.getLikeCount() + 1);
            isLiked = true;
        }

        postRepository.save(post);
        List<CommunityPostImage> images = imageRepository.findByPostIdOrderByDisplayOrderAsc(postId);
        return CommunityPostMapper.toPostResponse(post, images, isLiked);
    }

    @Override
    @Transactional
    public PostCommentResponseDto addComment(UUID postId, UUID userId, String body, UUID parentCommentId) {
        CommunityPost post = postRepository.findById(postId)
                .orElseThrow(() -> new NoSuchElementException("Post not found"));
        if (Boolean.TRUE.equals(post.getDeleted())) {
            throw new NoSuchElementException("Post not found");
        }

        User author = userRepository.getReferenceById(userId);

        CommunityPostComment.CommunityPostCommentBuilder builder = CommunityPostComment.builder()
                .post(post)
                .author(author)
                .body(body);

        if (parentCommentId != null) {
            CommunityPostComment parent = commentRepository.findById(parentCommentId)
                    .orElseThrow(() -> new NoSuchElementException("Parent comment not found"));
            builder.parent(parent);
            builder.replyToUser(parent.getAuthor());
        }

        CommunityPostComment saved = commentRepository.save(builder.build());

        // Update denormalized comment count
        post.setCommentCount(post.getCommentCount() + 1);
        postRepository.save(post);

        int replyCount = 0;
        return CommunityPostMapper.toCommentResponse(saved, replyCount, false);
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
        comment.setDeletedAt(java.time.LocalDateTime.now());
        commentRepository.save(comment);

        // 1. Soft-delete replies as well
        List<CommunityPostComment> replies = commentRepository.findByParent_Id(commentId);
        if (!replies.isEmpty()) {
            for (CommunityPostComment r : replies) {
                r.setDeleted(true);
                r.setDeletedAt(java.time.LocalDateTime.now());
            }
            commentRepository.saveAll(replies);
        }

        // 2. Update denormalized comment count of the post
        CommunityPost post = comment.getPost();
        int commentsRemoved = 1 + replies.size();
        post.setCommentCount(Math.max(0, post.getCommentCount() - commentsRemoved));
        postRepository.save(post);
    }

    @Override
    @Transactional
    public void hardDeleteCommentPhysics(UUID commentId) {
        CommunityPostComment comment = commentRepository.findById(commentId).orElse(null);
        if (comment == null) return;

        // 1. Find all replies to this comment
        List<CommunityPostComment> replies = commentRepository.findByParent_Id(commentId);
        if (!replies.isEmpty()) {
            for (CommunityPostComment r : replies) {
                commentLikeRepository.deleteByCommentId(r.getId());
            }
            commentRepository.deleteAll(replies);
        }

        // 2. Delete likes of the comment itself
        commentLikeRepository.deleteByCommentId(commentId);

        // 3. Delete the comment itself
        commentRepository.delete(comment);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PostCommentResponseDto> getComments(UUID postId, int page, int size, UUID currentUserId) {
        Page<CommunityPostComment> commentPage = commentRepository
                .findByPost_IdAndParentIsNullAndDeletedFalseOrderByCreatedAtDesc(postId, PageRequest.of(page, size));

        List<CommunityPostComment> content = commentPage.getContent();
        if (content.isEmpty()) return List.of();

        List<UUID> commentIds = content.stream().map(CommunityPostComment::getId).toList();
        Set<UUID> likedCommentIds = new HashSet<>();
        if (currentUserId != null) {
            likedCommentIds.addAll(commentLikeRepository.findLikedCommentIds(commentIds, currentUserId));
        }

        return content.stream().map(c -> {
            int replyCount = commentRepository.findByParent_IdAndDeletedFalseOrderByCreatedAtAsc(c.getId()).size();
            boolean isLiked = likedCommentIds.contains(c.getId());
            return CommunityPostMapper.toCommentResponse(c, replyCount, isLiked);
        }).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<PostCommentResponseDto> getReplies(UUID commentId, UUID currentUserId) {
        List<CommunityPostComment> replies = commentRepository
                .findByParent_IdAndDeletedFalseOrderByCreatedAtAsc(commentId);

        if (replies.isEmpty()) return List.of();

        List<UUID> replyIds = replies.stream().map(CommunityPostComment::getId).toList();
        Set<UUID> likedReplyIds = new HashSet<>();
        if (currentUserId != null) {
            likedReplyIds.addAll(commentLikeRepository.findLikedCommentIds(replyIds, currentUserId));
        }

        return replies.stream()
                .map(c -> {
                    boolean isLiked = likedReplyIds.contains(c.getId());
                    return CommunityPostMapper.toCommentResponse(c, 0, isLiked);
                })
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public PostCommentResponseDto toggleLikeComment(UUID commentId, UUID userId) {
        CommunityPostComment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new NoSuchElementException("Comment not found"));
        if (Boolean.TRUE.equals(comment.getDeleted())) {
            throw new NoSuchElementException("Comment not found");
        }

        User userRef = userRepository.getReferenceById(userId);
        var existing = commentLikeRepository.findByComment_IdAndUser_Id(commentId, userId);

        boolean isLiked;
        if (existing.isPresent()) {
            commentLikeRepository.delete(existing.get());
            commentLikeRepository.flush();
            comment.setLikeCount(Math.max(0, comment.getLikeCount() - 1));
            isLiked = false;
        } else {
            commentLikeRepository.save(CommunityPostCommentLike.builder()
                    .comment(comment)
                    .user(userRef)
                    .build());
            comment.setLikeCount(comment.getLikeCount() + 1);
            isLiked = true;
        }

        commentRepository.save(comment);
        int replyCount = commentRepository.findByParent_IdAndDeletedFalseOrderByCreatedAtAsc(commentId).size();
        return CommunityPostMapper.toCommentResponse(comment, replyCount, isLiked);
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

        CommunityPost saved = postRepository.findByIdWithAuthor(postId).orElse(post);
        List<CommunityPostImage> currentImages = imageRepository.findByPostIdOrderByDisplayOrderAsc(postId);
        boolean isLiked = likeRepository.findByPost_IdAndUser_Id(postId, userId).isPresent();

        return CommunityPostMapper.toPostResponse(saved, currentImages, isLiked);
    }
}
