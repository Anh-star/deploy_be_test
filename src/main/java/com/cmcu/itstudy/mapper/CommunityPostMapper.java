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
        if (post == null) return null;

        String authorName = post.getAuthor() != null ? post.getAuthor().getFullName() : null;
        String authorAvatar = post.getAuthor() != null ? post.getAuthor().getAvatarUrl() : null;
        String authorId = post.getAuthor() != null ? uuidToString(post.getAuthor().getId()) : null;

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

        PollDto pollDto = toPollDto(poll, userPollVotes);

        return CommunityPostResponseDto.builder()
                .id(uuidToString(post.getId()))
                .authorId(authorId)
                .authorName(authorName)
                .authorAvatar(authorAvatar)
                .content(post.getContent())
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
                .createdAt(post.getCreatedAt())
                .build();
    }

    public static PollDto toPollDto(CommunityPoll poll, List<CommunityPollVote> userPollVotes) {
        return toPollDto(poll, poll != null ? poll.getOptions() : null, userPollVotes);
    }

    public static PollDto toPollDto(CommunityPoll poll, List<CommunityPollOption> options, List<CommunityPollVote> userPollVotes) {
        if (poll == null) return null;

        Set<UUID> votedOptionIds = (userPollVotes != null && !userPollVotes.isEmpty())
                ? userPollVotes.stream()
                        .filter(v -> v != null && v.getOption() != null && v.getOption().getId() != null)
                        .map(v -> v.getOption().getId())
                        .collect(Collectors.toSet())
                : Set.of();

        boolean hasCurrentUserVoted = !votedOptionIds.isEmpty();
        boolean hideResults = Boolean.TRUE.equals(poll.getHideResultsBeforeVote()) && !hasCurrentUserVoted;

        List<PollOptionDto> optionDtos = (options != null)
                ? options.stream()
                        .filter(opt -> opt != null)
                        .map(opt -> PollOptionDto.builder()
                                .id(uuidToString(opt.getId()))
                                .optionText(opt.getOptionText())
                                .voteCount(hideResults ? 0 : (opt.getVoteCount() != null ? opt.getVoteCount() : 0))
                                .isVotedByCurrentUser(opt.getId() != null && votedOptionIds.contains(opt.getId()))
                                .build())
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
        if (comment == null) return null;

        String authorName = comment.getAuthor() != null ? comment.getAuthor().getFullName() : null;
        String authorAvatar = comment.getAuthor() != null ? comment.getAuthor().getAvatarUrl() : null;
        String authorId = comment.getAuthor() != null ? uuidToString(comment.getAuthor().getId()) : null;
        String replyToUserName = comment.getReplyToUser() != null ? comment.getReplyToUser().getFullName() : null;

        return PostCommentResponseDto.builder()
                .id(uuidToString(comment.getId()))
                .authorId(authorId)
                .authorName(authorName)
                .authorAvatar(authorAvatar)
                .body(comment.getBody())
                .likeCount(comment.getLikeCount())
                .replyCount(replyCount)
                .replyToUserName(replyToUserName)
                .isLiked(isLiked)
                .createdAt(comment.getCreatedAt())
                .build();
    }

    private static String uuidToString(UUID id) {
        return id != null ? id.toString() : null;
    }
}
