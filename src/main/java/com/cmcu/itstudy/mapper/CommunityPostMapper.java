package com.cmcu.itstudy.mapper;

import com.cmcu.itstudy.dto.community.CommunityPostResponseDto;
import com.cmcu.itstudy.dto.community.PollDto;
import com.cmcu.itstudy.dto.community.PollOptionDto;
import com.cmcu.itstudy.dto.community.PostCommentResponseDto;
import com.cmcu.itstudy.entity.CommunityPoll;
import com.cmcu.itstudy.entity.CommunityPollOption;
import com.cmcu.itstudy.entity.CommunityPollVote;
import com.cmcu.itstudy.entity.CommunityPost;
import com.cmcu.itstudy.entity.CommunityPostComment;
import com.cmcu.itstudy.entity.CommunityPostImage;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public final class CommunityPostMapper {

    private CommunityPostMapper() {
    }

    public static CommunityPostResponseDto toPostResponse(
            CommunityPost post,
            List<CommunityPostImage> images,
            Boolean isLiked
    ) {
        return toPostResponse(post, images, isLiked, null, false, null, List.of());
    }

    public static CommunityPostResponseDto toPostResponse(
            CommunityPost post,
            List<CommunityPostImage> images,
            Boolean isLiked,
            String currentUserVote,
            Boolean isSaved,
            CommunityPoll poll,
            List<CommunityPollVote> userPollVotes
    ) {
        return toPostResponse(post, images, isLiked, currentUserVote, isSaved, poll, userPollVotes, false, false, 0L, false);
    }

    public static CommunityPostResponseDto toPostResponse(
            CommunityPost post,
            List<CommunityPostImage> images,
            Boolean isLiked,
            String currentUserVote,
            Boolean isSaved,
            CommunityPoll poll,
            List<CommunityPollVote> userPollVotes,
            Boolean isMuted
    ) {
        return toPostResponse(post, images, isLiked, currentUserVote, isSaved, poll, userPollVotes, false, false, 0L, isMuted, null);
    }

    public static CommunityPostResponseDto toPostResponse(
            CommunityPost post,
            List<CommunityPostImage> images,
            Boolean isLiked,
            String currentUserVote,
            Boolean isSaved,
            CommunityPoll poll,
            List<CommunityPollVote> userPollVotes,
            Boolean isMuted,
            UUID currentUserId
    ) {
        return toPostResponse(post, images, isLiked, currentUserVote, isSaved, poll, userPollVotes, false, false, 0L, isMuted, currentUserId);
    }

    public static CommunityPostResponseDto toPostResponse(
            CommunityPost post,
            List<CommunityPostImage> images,
            Boolean isLiked,
            String currentUserVote,
            Boolean isSaved,
            CommunityPoll poll,
            List<CommunityPollVote> userPollVotes,
            Boolean isReported,
            Long reportCount
    ) {
        return toPostResponse(post, images, isLiked, currentUserVote, isSaved, poll, userPollVotes, isReported, false, reportCount, false);
    }

    public static CommunityPostResponseDto toPostResponse(
            CommunityPost post,
            List<CommunityPostImage> images,
            Boolean isLiked,
            String currentUserVote,
            Boolean isSaved,
            CommunityPoll poll,
            List<CommunityPollVote> userPollVotes,
            Boolean isReported,
            Boolean isReportDismissed,
            Long reportCount,
            Boolean isMuted
    ) {
        return toPostResponse(post, images, isLiked, currentUserVote, isSaved, poll, userPollVotes, isReported, isReportDismissed, reportCount, isMuted, null);
    }

    public static CommunityPostResponseDto toPostResponse(
            CommunityPost post,
            List<CommunityPostImage> images,
            Boolean isLiked,
            String currentUserVote,
            Boolean isSaved,
            CommunityPoll poll,
            List<CommunityPollVote> userPollVotes,
            Boolean isReported,
            Boolean isReportDismissed,
            Long reportCount,
            Boolean isMuted,
            UUID currentUserId
    ) {
        if (post == null) return null;

        String authorName = null;
        String authorAvatar = null;
        String authorId = null;
        try {
            if (post.getAuthor() != null) {
                authorName = post.getAuthor().getFullName();
                authorAvatar = post.getAuthor().getAvatarUrl();
                authorId = uuidToString(post.getAuthor().getId());
            }
        } catch (Exception e) {
            authorName = "Người dùng";
        }

        List<String> imageUrls = images != null
                ? images.stream().map(CommunityPostImage::getImageUrl).collect(Collectors.toList())
                : List.of();

        List<String> fileUrlsList = List.of();
        if (post.getFileUrls() != null && !post.getFileUrls().isBlank()) {
            fileUrlsList = Arrays.stream(post.getFileUrls().split(";;;"))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());
        }

        PollDto pollDto = toPollDto(poll, userPollVotes, currentUserId, Boolean.TRUE.equals(isReported));

        return CommunityPostResponseDto.builder()
                .id(uuidToString(post.getId()))
                .authorId(authorId)
                .authorName(authorName)
                .authorAvatar(authorAvatar)
                .title(post.getTitle())
                .content(post.getContent())
                .tags(post.getTags() != null ? new java.util.ArrayList<>(post.getTags()) : List.of())
                .imageUrls(imageUrls)
                .fileUrls(fileUrlsList)
                .likeCount(post.getLikeCount() != null ? post.getLikeCount() : 0)
                .upvoteCount(post.getUpvoteCount() != null ? post.getUpvoteCount() : 0)
                .downvoteCount(post.getDownvoteCount() != null ? post.getDownvoteCount() : 0)
                .currentUserVote(currentUserVote)
                .commentCount(post.getCommentCount() != null ? post.getCommentCount() : 0)
                .isLiked(isLiked != null ? isLiked : false)
                .isSaved(isSaved != null ? isSaved : false)
                .poll(pollDto)
                .allowComments(post.getAllowComments() != null ? post.getAllowComments() : true)
                .isHidden(post.getHidden() != null ? post.getHidden() : false)
                .isDeleted(post.getDeleted() != null ? post.getDeleted() : false)
                .isPinned(post.getIsPinned() != null ? post.getIsPinned() : false)
                .pinnedAt(post.getPinnedAt())
                .isReported(isReported != null ? isReported : false)
                .isReportDismissed(isReportDismissed != null ? isReportDismissed : false)
                .isMuted(isMuted != null ? isMuted : false)
                .reportCount(reportCount != null ? reportCount : 0L)
                .createdAt(post.getCreatedAt())
                .build();
    }

    public static PollDto toPollDto(CommunityPoll poll, List<CommunityPollVote> userPollVotes) {
        return toPollDto(poll, poll != null ? poll.getOptions() : null, userPollVotes, null, false);
    }

    public static PollDto toPollDto(CommunityPoll poll, List<CommunityPollVote> userPollVotes, UUID currentUserId) {
        return toPollDto(poll, poll != null ? poll.getOptions() : null, userPollVotes, currentUserId, false);
    }

    public static PollDto toPollDto(CommunityPoll poll, List<CommunityPollVote> userPollVotes, UUID currentUserId, boolean bypassHideResults) {
        return toPollDto(poll, poll != null ? poll.getOptions() : null, userPollVotes, currentUserId, bypassHideResults);
    }

    public static PollDto toPollDto(CommunityPoll poll, List<CommunityPollOption> options, List<CommunityPollVote> userPollVotes) {
        return toPollDto(poll, options, userPollVotes, null, false);
    }

    public static PollDto toPollDto(CommunityPoll poll, List<CommunityPollOption> options, List<CommunityPollVote> userPollVotes, UUID currentUserId) {
        return toPollDto(poll, options, userPollVotes, currentUserId, false);
    }

    public static PollDto toPollDto(CommunityPoll poll, List<CommunityPollOption> options, List<CommunityPollVote> userPollVotes, UUID currentUserId, boolean bypassHideResults) {
        if (poll == null) return null;

        Set<UUID> votedOptionIds = (userPollVotes != null && !userPollVotes.isEmpty())
                ? userPollVotes.stream()
                        .filter(v -> v != null && v.getOption() != null && v.getOption().getId() != null)
                        .map(v -> v.getOption().getId())
                        .collect(Collectors.toSet())
                : Set.of();

        boolean hasCurrentUserVoted = !votedOptionIds.isEmpty();
        boolean isPostAuthor = (poll.getPost() != null && poll.getPost().getAuthor() != null && currentUserId != null
                && currentUserId.equals(poll.getPost().getAuthor().getId()));
        boolean hideResults = Boolean.TRUE.equals(poll.getHideResultsBeforeVote()) && !hasCurrentUserVoted && !isPostAuthor && !bypassHideResults;

        List<PollOptionDto> optionDtos = (options != null)
                ? options.stream()
                        .filter(opt -> opt != null)
                        .map(opt -> {
                            UUID createdById = opt.getCreatedBy() != null ? opt.getCreatedBy().getId() : null;
                            boolean isCreator = (createdById != null && currentUserId != null && createdById.equals(currentUserId));
                            boolean canDelete = isCreator || isPostAuthor;
                            return PollOptionDto.builder()
                                    .id(uuidToString(opt.getId()))
                                    .optionText(opt.getOptionText())
                                    .createdById(uuidToString(createdById))
                                    .canDelete(canDelete)
                                    .voteCount(hideResults ? 0 : (opt.getVoteCount() != null ? opt.getVoteCount() : 0))
                                    .isVotedByCurrentUser(opt.getId() != null && votedOptionIds.contains(opt.getId()))
                                    .build();
                        })
                        .collect(Collectors.toList())
                : List.of();

        int totalVotes = hideResults ? 0 : optionDtos.stream().mapToInt(o -> o.getVoteCount() != null ? o.getVoteCount() : 0).sum();

        return PollDto.builder()
                .id(uuidToString(poll.getId()))
                .question(poll.getQuestion())
                .expiresAt(poll.getExpiresAt())
                .allowMultiple(poll.getAllowMultiple())
                .allowAddOptions(poll.getAllowAddOptions())
                .hideResultsBeforeVote(poll.getHideResultsBeforeVote())
                .hideVoters(poll.getHideVoters())
                .hasCurrentUserVoted(hasCurrentUserVoted)
                .totalVotes(totalVotes)
                .options(optionDtos)
                .build();
    }

    public static PostCommentResponseDto toCommentResponse(
            CommunityPostComment comment,
            Integer replyCount,
            Boolean isLiked
    ) {
        return toCommentResponse(comment, replyCount, isLiked, isLiked != null && isLiked ? "UPVOTE" : null);
    }

    public static PostCommentResponseDto toCommentResponse(
            CommunityPostComment comment,
            Integer replyCount,
            Boolean isLiked,
            String userVote
    ) {
        if (comment == null) return null;

        String authorName = comment.getAuthor() != null ? comment.getAuthor().getFullName() : null;
        String authorAvatar = comment.getAuthor() != null ? comment.getAuthor().getAvatarUrl() : null;
        String authorId = comment.getAuthor() != null ? uuidToString(comment.getAuthor().getId()) : null;
        String replyToUserName = comment.getReplyToUser() != null ? comment.getReplyToUser().getFullName() : null;
        String parentCommentId = comment.getParent() != null ? uuidToString(comment.getParent().getId()) : null;

        int upvotes = comment.getUpvoteCount() != null ? comment.getUpvoteCount() : (comment.getLikeCount() != null ? comment.getLikeCount() : 0);
        int downvotes = comment.getDownvoteCount() != null ? comment.getDownvoteCount() : 0;
        int netScore = upvotes - downvotes;

        return PostCommentResponseDto.builder()
                .id(uuidToString(comment.getId()))
                .parentCommentId(parentCommentId)
                .authorId(authorId)
                .authorName(authorName)
                .authorAvatar(authorAvatar)
                .body(comment.getBody())
                .likeCount(netScore)
                .upvoteCount(upvotes)
                .downvoteCount(downvotes)
                .replyCount(replyCount)
                .replyToUserName(replyToUserName)
                .isLiked("UPVOTE".equalsIgnoreCase(userVote))
                .userVote(userVote)
                .createdAt(comment.getCreatedAt())
                .build();
    }

    private static String uuidToString(UUID id) {
        return id != null ? id.toString() : null;
    }
}
