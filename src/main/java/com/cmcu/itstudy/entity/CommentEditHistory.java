package com.cmcu.itstudy.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString
@Entity
@Table(
        name = "tbl_comment_edit_history",
        indexes = {
                @Index(name = "idx_comment_edit_history_comment", columnList = "comment_id, comment_type"),
                @Index(name = "idx_comment_edit_history_edited_at", columnList = "edited_at")
        }
)
public class CommentEditHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", columnDefinition = "uniqueidentifier")
    @EqualsAndHashCode.Include
    private UUID id;

    @Column(name = "comment_type", nullable = false, length = 32)
    private String commentType; // "DOCUMENT" or "COMMUNITY"

    @Column(name = "comment_id", nullable = false, columnDefinition = "uniqueidentifier")
    private UUID commentId;

    @Column(name = "previous_body", nullable = false, columnDefinition = "nvarchar(max)")
    private String previousBody;

    @Column(name = "previous_image_urls", columnDefinition = "nvarchar(max)")
    private String previousImageUrls;

    @Column(name = "edited_at", nullable = false)
    private LocalDateTime editedAt;
}
