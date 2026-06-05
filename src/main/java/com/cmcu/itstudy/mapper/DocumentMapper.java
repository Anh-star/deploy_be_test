package com.cmcu.itstudy.mapper;

import com.cmcu.itstudy.dto.document.DocumentCardResponseDto;
import com.cmcu.itstudy.dto.document.DocumentDetailCommentItemDto;
import com.cmcu.itstudy.dto.document.DocumentDetailCommentsDto;
import com.cmcu.itstudy.dto.document.DocumentDetailInfoDto;
import com.cmcu.itstudy.dto.document.DocumentDetailQuizDto;
import com.cmcu.itstudy.dto.document.DocumentDetailResponseDto;
import com.cmcu.itstudy.dto.document.DocumentDetailStatsDto;
import com.cmcu.itstudy.dto.document.DocumentFileUrlResponseDto;
import com.cmcu.itstudy.dto.document.DocumentUploaderDto;
import com.cmcu.itstudy.dto.document.DocumentPrimaryFileDto;
import com.cmcu.itstudy.dto.document.DocumentRelatedDocumentDto;
import com.cmcu.itstudy.dto.document.FileTypeDto;
import com.cmcu.itstudy.dto.document.QuizListItemDto;
import com.cmcu.itstudy.dto.document.QuizListPageResponseDto;
import com.cmcu.itstudy.entity.Document;
import com.cmcu.itstudy.entity.DocumentComment;
import com.cmcu.itstudy.entity.DocumentFile;
import com.cmcu.itstudy.entity.DocumentQuiz;
import com.cmcu.itstudy.entity.Quiz;
import com.cmcu.itstudy.enums.FileType;
import org.springframework.data.domain.Page;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class DocumentMapper {

    private DocumentMapper() {
    }

    public static DocumentCardResponseDto toCardDto(Document document) {
        if (document == null) {
            return null;
        }

        return DocumentCardResponseDto.builder()
                .id(uuidToString(document.getId()))
                .title(document.getTitle())
                .thumbnail(document.getThumbnailUrl())
                .categoryName(document.getCategory() != null ? document.getCategory().getName() : null)
                .authorName(null)
                .createdAt(document.getCreatedAt())
                .viewCount(document.getViewCount() != null ? document.getViewCount().intValue() : null)
                .downloadCount(document.getDownloadCount() != null ? document.getDownloadCount().intValue() : null)
                .isBookmarked(null)
                .fileType(map(document.getFileType()))
                .tags(null)
                .build();
    }

    public static DocumentDetailResponseDto toDetailResponseDto(Document document,
                                                                  String authorName,
                                                                  String uploaderUserId,
                                                                  DocumentUploaderDto uploader,
                                                                  List<String> tags,
                                                                  long totalViews,
                                                                  long totalDownloads,
                                                                  long reportCount,
                                                                  DocumentPrimaryFileDto primaryFile,
                                                                  DocumentDetailCommentsDto comments,
                                                                  List<DocumentDetailQuizDto> quizzes,
                                                                  List<DocumentRelatedDocumentDto> relatedDocuments) {
        if (document == null) {
            return null;
        }

        DocumentDetailInfoDto documentInfo = toDocumentDetailInfoDto(document, authorName, uploaderUserId, uploader, tags);
        DocumentDetailStatsDto stats = DocumentDetailStatsDto.builder()
                .totalViews(totalViews)
                .totalDownloads(totalDownloads)
                .reportCount(reportCount)
                .build();

        return DocumentDetailResponseDto.builder()
                .documentInfo(documentInfo)
                .stats(stats)
                .file(primaryFile)
                .comments(comments)
                .quizzes(quizzes != null ? quizzes : Collections.emptyList())
                .relatedDocuments(relatedDocuments != null ? relatedDocuments : Collections.emptyList())
                .build();
    }

    public static DocumentDetailInfoDto toDocumentDetailInfoDto(Document document,
                                                                 String authorName,
                                                                 String uploaderUserId,
                                                                 DocumentUploaderDto uploader,
                                                                 List<String> tags) {
        if (document == null) {
            return null;
        }
        return DocumentDetailInfoDto.builder()
                .id(uuidToString(document.getId()))
                .title(document.getTitle())
                .description(document.getDescription())
                .documentType(map(document.getFileType()))
                .createdAt(document.getCreatedAt())
                .authorName(authorName)
                .userId(uploaderUserId)
                .uploader(uploader)
                .categoryName(document.getCategory() != null ? document.getCategory().getName() : null)
                .tags(tags != null ? tags : Collections.emptyList())
                .build();
    }

    /**
     * Primary file for detail/preview: prefer {@link DocumentFile#getFileUrl()}, then {@link Document#getFileUrl()},
     * then storage path (legacy rows).
     */
    public static DocumentPrimaryFileDto toPrimaryFileDto(DocumentFile file, Document document) {
        if (file == null) {
            return legacyPrimaryFromDocument(document);
        }
        String fileUrl = firstNonBlank(
                file.getFileUrl(),
                document != null ? document.getFileUrl() : null,
                file.getStoragePath()
        );
        Long size = file.getSizeBytes() != null ? file.getSizeBytes() : (document != null ? document.getFileSize() : null);
        String ft = StringUtils.hasText(file.getFileExtension())
                ? file.getFileExtension()
                : (document != null && document.getFileType() != null ? document.getFileType().name() : null);
        return DocumentPrimaryFileDto.builder()
                .fileUrl(fileUrl)
                .fileType(ft)
                .fileSize(size)
                .build();
    }

    public static DocumentPrimaryFileDto legacyPrimaryFromDocument(Document document) {
        if (document == null || !StringUtils.hasText(document.getFileUrl())) {
            return null;
        }
        return DocumentPrimaryFileDto.builder()
                .fileUrl(document.getFileUrl())
                .fileType(document.getFileType() != null ? document.getFileType().name() : null)
                .fileSize(document.getFileSize())
                .build();
    }

    public static DocumentFileUrlResponseDto toFileUrlResponseDto(DocumentFile file, Document document) {
        if (file == null) {
            if (document == null || !StringUtils.hasText(document.getFileUrl())) {
                return null;
            }
            return DocumentFileUrlResponseDto.builder().fileUrl(document.getFileUrl()).build();
        }
        String url = firstNonBlank(file.getFileUrl(), document.getFileUrl(), file.getStoragePath());
        return DocumentFileUrlResponseDto.builder().fileUrl(url).build();
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String v : values) {
            if (StringUtils.hasText(v)) {
                return v;
            }
        }
        return null;
    }

    public static DocumentDetailCommentItemDto toCommentItemDto(DocumentComment comment) {
        if (comment == null) {
            return null;
        }
        String authorName = comment.getAuthor() != null ? comment.getAuthor().getFullName() : null;
        return DocumentDetailCommentItemDto.builder()
                .id(uuidToString(comment.getId()))
                .content(comment.getBody())
                .authorName(authorName)
                .createdAt(comment.getCreatedAt())
                .totalLikes(comment.getLikeCount())
                .isPinned(Boolean.TRUE.equals(comment.getPinned()))
                .build();
    }

    public static DocumentDetailQuizDto toQuizDto(Quiz quiz, long totalQuestions) {
        if (quiz == null) {
            return null;
        }
        return DocumentDetailQuizDto.builder()
                .id(uuidToString(quiz.getId()))
                .title(quiz.getTitle())
                .totalQuestions(totalQuestions)
                .durationMinutes(quiz.getDurationMinutes())
                .passScorePercent(quiz.getPassScorePercent())
                .build();
    }

    public static QuizListItemDto toQuizListItemDto(Quiz quiz, long totalQuestions) {
        if (quiz == null) {
            return null;
        }
        return QuizListItemDto.builder()
                .quizId(uuidToString(quiz.getId()))
                .title(quiz.getTitle())
                .description(quiz.getDescription())
                .totalQuestions(totalQuestions)
                .durationMinutes(quiz.getDurationMinutes())
                .passScorePercent(quiz.getPassScorePercent())
                .build();
    }

    public static QuizListPageResponseDto toQuizListPageResponseDto(Page<DocumentQuiz> linkPage, Map<UUID, Long> counts) {
        if (linkPage == null) {
            return null;
        }
        List<QuizListItemDto> items = linkPage.getContent().stream()
                .map(DocumentQuiz::getQuiz)
                .filter(q -> q != null && q.getId() != null)
                .map(q -> toQuizListItemDto(q, counts.getOrDefault(q.getId(), 0L)))
                .toList();
        return QuizListPageResponseDto.builder()
                .items(items)
                .page(linkPage.getNumber())
                .totalPages(linkPage.getTotalPages())
                .totalItems(linkPage.getTotalElements())
                .build();
    }

    public static DocumentRelatedDocumentDto toRelatedDocumentDto(Document document) {
        if (document == null) {
            return null;
        }
        return DocumentRelatedDocumentDto.builder()
                .id(uuidToString(document.getId()))
                .title(document.getTitle())
                .thumbnail(document.getThumbnailUrl())
                .totalViews(document.getViewCount())
                .build();
    }

    public static Map<UUID, Long> toQuizQuestionCountMap(List<Object[]> rows) {
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<UUID, Long> map = new HashMap<>();
        for (Object[] row : rows) {
            if (row == null || row.length < 2 || row[0] == null) {
                continue;
            }
            UUID quizId = (UUID) row[0];
            long cnt = row[1] instanceof Number n ? n.longValue() : 0L;
            map.put(quizId, cnt);
        }
        return map;
    }

    private static String uuidToString(UUID id) {
        return id != null ? id.toString() : null;
    }

    private static FileTypeDto map(FileType fileType) {
        if (fileType == null) {
            return null;
        }
        return switch (fileType) {
            case PPT -> FileTypeDto.PPTX;
            default -> FileTypeDto.valueOf(fileType.name());
        };
    }
}
