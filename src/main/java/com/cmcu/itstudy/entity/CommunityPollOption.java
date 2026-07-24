package com.cmcu.itstudy.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString
@Entity
@Table(name = "tbl_community_poll_options")
public class CommunityPollOption {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", columnDefinition = "uniqueidentifier")
    @EqualsAndHashCode.Include
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "poll_id", nullable = false)
    @ToString.Exclude
    @JsonIgnore
    private CommunityPoll poll;

    @Column(name = "option_text", nullable = false, columnDefinition = "nvarchar(255)")
    private String optionText;

    @Column(name = "vote_count", nullable = false)
    private Integer voteCount;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    @PrePersist
    void prePersist() {
        if (this.voteCount == null) this.voteCount = 0;
        if (this.displayOrder == null) this.displayOrder = 0;
    }
}
