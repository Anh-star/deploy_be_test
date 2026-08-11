package com.cmcu.itstudy.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.cmcu.itstudy.enums.DocumentStatus;
import com.cmcu.itstudy.enums.FileType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString
@Entity
@Table(name = "tbl_documents")
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", columnDefinition = "uniqueidentifier")
    @EqualsAndHashCode.Include
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    @ToString.Exclude
    @JsonIgnore
    private Category category;

    @Column(name = "title", nullable = false, length = 255, columnDefinition = "nvarchar(255)")
    private String title;

    @Column(name = "slug", nullable = false, length = 300, unique = true)
    private String slug;

    @Column(name = "description", columnDefinition = "nvarchar(max)")
    private String description;

    @Column(name = "content", columnDefinition = "nvarchar(max)")
    private String content;

    @Column(name = "thumbnail_url", length = 500)
    private String thumbnailUrl;

    /**
     * Free documents store the public Supabase URL here. Paid documents
     * leave this column {@code NULL} — paid files are served exclusively
     * through {@link DocumentFile#getStorageBucket()} + {@link DocumentFile#getStoragePath()}
     * via the access-controlled paid download endpoint. The legacy column
     * is retained for backward compatibility with existing free rows and
     * the owner-detail preview; it is allowed to be null because paid
     * documents MUST NOT persist a public, signed or placeholder URL.
     */
    @Column(name = "file_url", nullable = true, length = 500)
    private String fileUrl;

    @Column(name = "file_name", length = 255, columnDefinition = "nvarchar(255)")
    private String fileName;

    @Enumerated(EnumType.STRING)
    @Column(name = "file_type", nullable = false, length = 20)
    private FileType fileType;

    @Column(name = "file_size")
    private Long fileSize;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private DocumentStatus status;

    @Column(name = "reject_reason", columnDefinition = "nvarchar(max)")
    private String rejectReason;

    @Column(name = "is_deleted", nullable = false)
    @Builder.Default
    private Boolean deleted = Boolean.FALSE;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "deleted_by")
    @ToString.Exclude
    @JsonIgnore
    private User deletedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    @ToString.Exclude
    @JsonIgnore
    private User createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by")
    @ToString.Exclude
    @JsonIgnore
    private User updatedBy;

    @Column(name = "view_count", nullable = false)
    @Builder.Default
    private Long viewCount = 0L;

    @Column(name = "download_count", nullable = false)
    @Builder.Default
    private Long downloadCount = 0L;

    @Column(name = "bookmark_count", nullable = false)
    @Builder.Default
    private Long bookmarkCount = 0L;

    @Column(name = "is_paid", nullable = false)
    @Builder.Default
    private Boolean isPaid = Boolean.FALSE;

    @Column(name = "price")
    @Builder.Default
    private Long price = 0L;

    @Column(name = "last_viewed_at")
    private LocalDateTime lastViewedAt;

    @Column(name = "last_downloaded_at")
    private LocalDateTime lastDownloadedAt;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "document", fetch = FetchType.LAZY)
    @ToString.Exclude
    @JsonIgnore
    @Builder.Default
    private Set<DocumentTag> documentTags = new HashSet<>();

    @OneToMany(mappedBy = "document", fetch = FetchType.LAZY)
    @ToString.Exclude
    @JsonIgnore
    @Builder.Default
    private Set<DocumentAuthor> documentAuthors = new HashSet<>();

    @OneToMany(mappedBy = "document", fetch = FetchType.LAZY)
    @ToString.Exclude
    @JsonIgnore
    @Builder.Default
    private Set<DocumentBookmark> bookmarks = new HashSet<>();

    @OneToMany(mappedBy = "document", fetch = FetchType.LAZY)
    @ToString.Exclude
    @JsonIgnore
    @Builder.Default
    private List<DocumentFile> files = new ArrayList<>();

    @OneToMany(mappedBy = "document", fetch = FetchType.LAZY)
    @ToString.Exclude
    @JsonIgnore
    @Builder.Default
    private List<DocumentView> views = new ArrayList<>();

    @OneToMany(mappedBy = "document", fetch = FetchType.LAZY)
    @ToString.Exclude
    @JsonIgnore
    @Builder.Default
    private List<DocumentDownload> downloads = new ArrayList<>();

    @OneToMany(mappedBy = "document", fetch = FetchType.LAZY)
    @ToString.Exclude
    @JsonIgnore
    @Builder.Default
    private List<DocumentComment> comments = new ArrayList<>();

    @OneToMany(mappedBy = "document", fetch = FetchType.LAZY)
    @ToString.Exclude
    @JsonIgnore
    @Builder.Default
    private List<DocumentQuiz> documentQuizzes = new ArrayList<>();

    @OneToMany(mappedBy = "sourceDocument", fetch = FetchType.LAZY)
    @ToString.Exclude
    @JsonIgnore
    @Builder.Default
    private List<DocumentPreference> preferences = new ArrayList<>();

    @OneToMany(mappedBy = "document", fetch = FetchType.LAZY)
    @ToString.Exclude
    @JsonIgnore
    @Builder.Default
    private List<DocumentReport> reports = new ArrayList<>();

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.deleted == null) this.deleted = Boolean.FALSE;
        if (this.viewCount == null) this.viewCount = 0L;
        if (this.downloadCount == null) this.downloadCount = 0L;
        if (this.bookmarkCount == null) this.bookmarkCount = 0L;
        if (this.status == null) this.status = DocumentStatus.PENDING;
        if (this.isPaid == null) this.isPaid = Boolean.FALSE;
        if (this.price == null) this.price = 0L;
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
