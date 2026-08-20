package com.cmcu.itstudy.service.impl;

import com.cmcu.itstudy.dto.document.DocumentCardResponseDto;
import com.cmcu.itstudy.dto.document.DocumentUploaderDto;
import com.cmcu.itstudy.entity.Document;
import com.cmcu.itstudy.entity.DocumentBookmark;
import com.cmcu.itstudy.entity.DocumentTag;
import com.cmcu.itstudy.entity.Tag;
import com.cmcu.itstudy.entity.User;
import com.cmcu.itstudy.mapper.DocumentMapper;
import com.cmcu.itstudy.repository.DocumentBookmarkRepository;
import com.cmcu.itstudy.repository.DocumentRepository;
import com.cmcu.itstudy.repository.DocumentTagRepository;
import com.cmcu.itstudy.repository.DocumentViewRepository;
import com.cmcu.itstudy.repository.TagRepository;
import com.cmcu.itstudy.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class DocumentCardEnrichmentService {

    private final TagRepository tagRepository;
    private final DocumentTagRepository documentTagRepository;
    private final DocumentBookmarkRepository documentBookmarkRepository;
    private final UserRepository userRepository;
    private final DocumentRepository documentRepository;
    private final DocumentViewRepository documentViewRepository;

    public DocumentCardEnrichmentService(TagRepository tagRepository,
                                         DocumentTagRepository documentTagRepository,
                                         DocumentBookmarkRepository documentBookmarkRepository,
                                         UserRepository userRepository,
                                         DocumentRepository documentRepository,
                                         DocumentViewRepository documentViewRepository) {
        this.tagRepository = tagRepository;
        this.documentTagRepository = documentTagRepository;
        this.documentBookmarkRepository = documentBookmarkRepository;
        this.userRepository = userRepository;
        this.documentRepository = documentRepository;
        this.documentViewRepository = documentViewRepository;
    }

    public List<DocumentCardResponseDto> toEnrichedCardDtos(List<Document> documents, UUID currentUserId) {
        if (documents == null || documents.isEmpty()) {
            return Collections.emptyList();
        }

        Map<UUID, DocumentUploaderDto> uploaders = loadUploaders(documents);
        Map<UUID, List<String>> tagsByDocument = loadTagNames(documents);
        Set<UUID> bookmarkedDocumentIds = loadBookmarkedDocumentIds(documents, currentUserId);

        return documents.stream()
                .map(DocumentMapper::toCardDto)
                .peek(dto -> enrichCardDto(dto, uploaders, tagsByDocument, bookmarkedDocumentIds))
                .collect(Collectors.toList());
    }

    public List<DocumentCardResponseDto> toEnrichedCardDtosWithLastViewedAt(
            List<Document> documents, UUID currentUserId) {
        if (documents == null || documents.isEmpty()) {
            return Collections.emptyList();
        }

        Map<UUID, DocumentUploaderDto> uploaders = loadUploaders(documents);
        Map<UUID, List<String>> tagsByDocument = loadTagNames(documents);
        Set<UUID> bookmarkedDocumentIds = loadBookmarkedDocumentIds(documents, currentUserId);
        Map<UUID, LocalDateTime> lastViewedAtByDocId = loadLastViewedAt(documents, currentUserId);

        return documents.stream()
                .map(DocumentMapper::toCardDto)
                .peek(dto -> enrichCardDtoWithLastViewedAt(dto, uploaders, tagsByDocument,
                        bookmarkedDocumentIds, lastViewedAtByDocId))
                .collect(Collectors.toList());
    }

    private Map<UUID, LocalDateTime> loadLastViewedAt(List<Document> documents, UUID currentUserId) {
        if (currentUserId == null || documents.isEmpty()) {
            return Collections.emptyMap();
        }

        List<UUID> documentIds = documents.stream()
                .map(Document::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        if (documentIds.isEmpty()) {
            return Collections.emptyMap();
        }

        List<Object[]> rows = documentViewRepository.findLastViewedAtByUserAndDocumentIds(currentUserId, documentIds);
        Map<UUID, LocalDateTime> result = new HashMap<>();
        for (Object[] row : rows) {
            if (row == null || row.length < 2 || row[0] == null) {
                continue;
            }
            UUID docId = (UUID) row[0];
            LocalDateTime viewedAt = row[1] instanceof LocalDateTime ? (LocalDateTime) row[1] : null;
            if (viewedAt != null) {
                result.put(docId, viewedAt);
            }
        }
        return result;
    }

    private void enrichCardDtoWithLastViewedAt(DocumentCardResponseDto dto,
                                              Map<UUID, DocumentUploaderDto> uploaders,
                                              Map<UUID, List<String>> tagsByDocument,
                                              Set<UUID> bookmarkedDocumentIds,
                                              Map<UUID, LocalDateTime> lastViewedAtByDocId) {
        enrichCardDto(dto, uploaders, tagsByDocument, bookmarkedDocumentIds);
        if (dto == null || dto.getId() == null) {
            return;
        }
        UUID documentId = UUID.fromString(dto.getId());
        dto.setLastViewedAt(lastViewedAtByDocId.get(documentId));
    }

    private Map<UUID, DocumentUploaderDto> loadUploaders(List<Document> documents) {
        Set<UUID> documentIds = documents.stream()
                .map(Document::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (documentIds.isEmpty()) {
            return Collections.emptyMap();
        }

        List<Object[]> rows = documentRepository.findUploaderByDocumentIds(documentIds);
        Map<UUID, DocumentUploaderDto> result = new HashMap<>();
        Set<UUID> userIds = new HashSet<>();
        Map<UUID, UUID> docToUserMap = new HashMap<>();
        Map<UUID, String> userNames = new HashMap<>();

        for (Object[] row : rows) {
            if (row == null || row.length < 1 || row[0] == null) {
                continue;
            }
            UUID docId = (UUID) row[0];
            UUID userId = row.length > 1 ? (UUID) row[1] : null;
            String fullName = row.length > 2 && row[2] != null ? row[2].toString() : null;
            if (userId == null) {
                continue;
            }
            userIds.add(userId);
            docToUserMap.put(docId, userId);
            userNames.put(userId, fullName);
        }

        // Stats for verified badge: totalDownloads >= 50 || totalViews >= 100
        Map<UUID, Long> userDownloads = new HashMap<>();
        Map<UUID, Long> userViews = new HashMap<>();
        if (!userIds.isEmpty()) {
            List<Object[]> statsRows = documentRepository.findStatsByUserIds(userIds);
            for (Object[] row : statsRows) {
                if (row == null || row.length < 4 || row[0] == null) {
                    continue;
                }
                UUID userId = (UUID) row[0];
                long totalDownloads = row[1] instanceof Number n ? n.longValue() : 0L;
                long totalViews = row[3] instanceof Number n ? n.longValue() : 0L;
                userDownloads.put(userId, totalDownloads);
                userViews.put(userId, totalViews);
            }
        }

        // Compute leaderboard top 10 rankings across 3 boards
        Map<UUID, Integer> bestRankMap = new HashMap<>();
        Map<UUID, String> bestRankCategoryMap = new HashMap<>();
        if (!userIds.isEmpty()) {
            org.springframework.data.domain.Pageable top10 = org.springframework.data.domain.PageRequest.of(0, 10);
            computeRanks(documentRepository.findLeaderboardUsersByViews(top10), "views", userIds, bestRankMap, bestRankCategoryMap);
            computeRanks(documentRepository.findLeaderboardUsersByFreeDownloads(top10), "freeDownloads", userIds, bestRankMap, bestRankCategoryMap);
            computeRanks(documentRepository.findLeaderboardUsersByPaidDownloads(top10), "paidDownloads", userIds, bestRankMap, bestRankCategoryMap);
        }

        for (Map.Entry<UUID, UUID> entry : docToUserMap.entrySet()) {
            UUID docId = entry.getKey();
            UUID userId = entry.getValue();
            String fullName = userNames.get(userId);
            long totalDownloads = userDownloads.getOrDefault(userId, 0L);
            long totalViewsVal = userViews.getOrDefault(userId, 0L);

            result.put(docId, DocumentUploaderDto.builder()
                    .id(userId.toString())
                    .fullName(StringUtils.hasText(fullName) ? fullName : null)
                    .bestRank(bestRankMap.get(userId))
                    .bestRankCategory(bestRankCategoryMap.get(userId))
                    .verified(totalDownloads >= 50 || totalViewsVal >= 100)
                    .build());
        }
        return result;
    }

    /** Helper: scan a leaderboard result set and update bestRank if this board gives a better (lower) rank. */
    private void computeRanks(List<Object[]> leaderboardRows, String category,
                              Set<UUID> relevantUserIds,
                              Map<UUID, Integer> bestRankMap,
                              Map<UUID, String> bestRankCategoryMap) {
        int rank = 0;
        for (Object[] row : leaderboardRows) {
            rank++;
            if (row == null || row[0] == null) continue;
            UUID userId;
            if (row[0] instanceof UUID u) {
                userId = u;
            } else {
                userId = UUID.fromString(row[0].toString());
            }
            if (!relevantUserIds.contains(userId)) continue;
            Integer current = bestRankMap.get(userId);
            if (current == null || rank < current) {
                bestRankMap.put(userId, rank);
                bestRankCategoryMap.put(userId, category);
            }
        }
    }

    private Map<UUID, List<String>> loadTagNames(List<Document> documents) {
        Set<UUID> documentIds = documents.stream()
                .map(Document::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (documentIds.isEmpty()) {
            return Collections.emptyMap();
        }

        List<DocumentTag> documentTags = documentTagRepository.findByDocumentIdIn(documentIds);
        Set<UUID> tagIds = documentTags.stream()
                .map(DocumentTag::getTagId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<UUID, Tag> tagsById = tagRepository.findAllById(tagIds).stream()
                .collect(Collectors.toMap(Tag::getId, Function.identity()));

        Map<UUID, List<String>> result = new HashMap<>();
        for (DocumentTag dt : documentTags) {
            UUID docId = dt.getDocumentId();
            UUID tagId = dt.getTagId();
            Tag tag = tagsById.get(tagId);
            if (docId != null && tag != null) {
                result.computeIfAbsent(docId, k -> new ArrayList<>()).add(tag.getName());
            }
        }
        return result;
    }

    private Set<UUID> loadBookmarkedDocumentIds(List<Document> documents, UUID currentUserId) {
        if (currentUserId == null || documents.isEmpty()) {
            return Collections.emptySet();
        }
        User user = userRepository.findById(currentUserId).orElse(null);
        if (user == null) {
            return Collections.emptySet();
        }
        List<DocumentBookmark> bookmarks = documentBookmarkRepository.findByUserAndActiveTrue(user);
        return bookmarks.stream()
                .map(DocumentBookmark::getDocument)
                .filter(Objects::nonNull)
                .map(Document::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private void enrichCardDto(DocumentCardResponseDto dto,
                               Map<UUID, DocumentUploaderDto> uploaders,
                               Map<UUID, List<String>> tagsByDocument,
                               Set<UUID> bookmarkedDocumentIds) {
        if (dto == null || dto.getId() == null) {
            return;
        }
        UUID documentId = UUID.fromString(dto.getId());
        DocumentUploaderDto uploader = uploaders.get(documentId);
        if (uploader != null) {
            dto.setUploader(uploader);
            dto.setUserId(uploader.getId());
            dto.setAuthorName(uploader.getFullName());
        } else {
            dto.setUploader(null);
            dto.setUserId(null);
            dto.setAuthorName(null);
        }
        dto.setTags(tagsByDocument.getOrDefault(documentId, Collections.emptyList()));
        dto.setIsBookmarked(bookmarkedDocumentIds.contains(documentId));
    }
}
