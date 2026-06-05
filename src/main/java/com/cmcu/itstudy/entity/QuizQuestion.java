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
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import jakarta.persistence.CascadeType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString
@Entity
@Table(name = "tbl_quiz_questions")
public class QuizQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", columnDefinition = "uniqueidentifier")
    @EqualsAndHashCode.Include
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "quiz_id", nullable = false)
    @ToString.Exclude
    @JsonIgnore
    private Quiz quiz;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @Column(name = "question_text", nullable = false, columnDefinition = "nvarchar(max)")
    private String questionText;

    @Column(name = "explanation", columnDefinition = "NVARCHAR(MAX)")
    private String explanation;

    @Column(name = "points", nullable = false)
    @Builder.Default
    private Integer points = 1;


    @ToString.Exclude
    @JsonIgnore
    @OneToMany(
        mappedBy = "question",
        fetch = FetchType.LAZY,
        cascade = CascadeType.ALL,
        orphanRemoval = true
    )
    @Builder.Default
    private List<QuizQuestionOption> options = new ArrayList<>();

    @OneToMany(mappedBy = "question", fetch = FetchType.LAZY)
    @ToString.Exclude
    @JsonIgnore
    @Builder.Default
    private List<QuizAttemptAnswer> attemptAnswers = new ArrayList<>();
}
