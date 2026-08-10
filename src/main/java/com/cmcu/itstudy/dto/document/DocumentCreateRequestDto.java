package com.cmcu.itstudy.dto.document;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentCreateRequestDto {

    /**
     * Minimum price for a paid document, in VND. Integer Long; no decimals.
     * Used as a cross-field invariant by the create DTO and as the service-side
     * floor when the owner updates a paid document with a new price.
     *
     * <p>Floor is derived from the contributor-net requirement: after a 10%
     * platform fee the contributor must net at least 2,700 VND. With
     * {@code platformFee = Math.floor(price * 10 / 100)} and
     * {@code sellerNet = price - platformFee}, the smallest integer
     * {@code price} giving {@code sellerNet >= 2700} is 3,000
     * (fee = 300, net = 2,700).
     *
     * <p>Note: the update DTO does NOT enforce this floor at the DTO level.
     * Legacy documents created under the previous 2,222 VND minimum must still
     * round-trip their existing price through a metadata-only PUT, so
     * {@code DocumentUpdateRequestDto#isPriceValid} only checks structure. The
     * minimum-vs-pricing-changed rule lives in
     * {@code DocumentServiceImpl#updateDocument}.
     */
    public static final long MIN_PAID_DOCUMENT_PRICE = 3000L;

    @NotBlank(message = "Title cannot be empty")
    @Size(min = 15, max = 255, message = "Title must be between 15 and 255 characters")
    private String title;

    @NotBlank(message = "Description cannot be empty")
    @Size(min = 80, max = 1000, message = "Description must be between 80 and 1000 characters")
    private String description;

    @NotBlank(message = "Category cannot be empty")
    private String category;

    @NotEmpty(message = "Tags cannot be empty")
    private List<String> tags;

    /**
     * Storage path of the uploaded file in the public bucket.
     *
     * <p>For a FREE document ({@code isPaid == false}) this MUST be a
     * non-blank string. The unconditional Bean Validation has been
     * removed (Phase C1) so a PAID create can send {@code null} / blank
     * and supply an {@code uploadId} instead. The cross-field invariant
     * is enforced by {@link #isPaidUploadShapeValid()}.
     */
    private String storagePath;

    /**
     * Public URL for the uploaded file (source of truth for free docs).
     * Same conditional validation rules as {@link #storagePath}.
     */
    private String documentUrl;

    @NotBlank(message = "Thumbnail URL cannot be empty")
    private String thumbnailUrl;

    @NotBlank(message = "File name cannot be empty")
    private String fileName;

    @NotNull(message = "File size cannot be empty")
    private Long fileSizeBytes;

    @NotNull(message = "isPaid flag cannot be null")
    private Boolean isPaid;

    /**
     * Optional {@code uploadId} that ties this create to a previously
     * issued Supabase signed-upload-target for a paid document.
     *
     * <p>For a PAID document ({@code isPaid == true}) this MUST be
     * non-null. For a FREE document this MUST be {@code null}. The
     * cross-field invariant is enforced by {@link #isPaidUploadShapeValid()}.
     *
     * <p>The {@code uploadId} is never echoed back to the client in the
     * response and is never logged.
     */
    private UUID uploadId;

    /**
     * Price in integer VND. Nullable for free documents. Rejected when negative
     * via the cross-field validator {@link #isPriceValid()}; the hard floor for
     * paid documents is enforced by the same predicate, not by a field-level
     * {@code @Min}, so free documents can leave this blank or send 0.
     */
    private Long price;

    /**
     * Cross-field invariant enforced at the create DTO:
     * <ul>
     *   <li>Free document ({@code isPaid == false}): {@code price} must be null or 0.</li>
     *   <li>Paid document ({@code isPaid == true}): {@code price} must be present
     *       and {@code >= MIN_PAID_DOCUMENT_PRICE} (3,000 VND).</li>
     * </ul>
     * Negative prices are rejected here too.
     */
    @AssertTrue(message = "Giá bán tài liệu có phí phải từ 3.000 VND trở lên.")
    public boolean isPriceValid() {
        if (isPaid == null) {
            return false;
        }
        if (Boolean.FALSE.equals(isPaid)) {
            // Free document: price must be null or zero (no negative, no stray amount).
            return price == null || price == 0L;
        }
        // Paid document: price must be present, non-negative, and meet minimum.
        return price != null && price >= MIN_PAID_DOCUMENT_PRICE;
    }

    // ----- Phase QUIZ-AI-2A: AI quiz auto-generation preferences -----
    // These two fields ONLY describe the user's intent at upload time.
    // No quiz is generated yet, no n8n webhook is fired, and no Quiz
    // row is created. Downstream phases will read these via a dedicated
    // event listener or scheduled job (not in scope here).

    /**
     * Whether the uploader wants the system to auto-generate an AI
     * quiz from the document's content after upload.
     *
     * <p>Defaults to {@code false} when omitted. Phase 2B persists the
     * intent as a {@code tbl_quiz_generations} row inside the same
     * transaction that creates the document.
     */
    @Builder.Default
    private Boolean generateQuiz = Boolean.FALSE;

    /**
     * Number of AI-generated quiz questions the uploader wants.
     *
     * <p>Required to be a non-null integer in {@code [10, 50]} when
     * {@link #generateQuiz} is {@code true}. For {@code generateQuiz ==
     * false} this MUST be {@code null} — enforced by
     * {@link #isQuizOptionShapeValid()}.
     *
     * <p>Final business rule (Phase QUIZ-AI-2D): the valid range is
     * {@code [10, 50]} inclusive. The lower bound 10 keeps generated
     * quizzes at meaningful coverage; the upper bound 50 caps the AI
     * generation cost per document. The same range is enforced
     * <em>again</em> inside {@code QuizGenerationServiceImpl} so the
     * service layer cannot be bypassed by a controller that skips
     * bean-validation.
     */
    @Min(value = 10, message = "Số câu hỏi phải từ 10 đến 50.")
    @Max(value = 50, message = "Số câu hỏi phải từ 10 đến 50.")
    private Integer quizQuestionCount;

    /**
     * Cross-field invariant for the quiz auto-generation preferences:
     * <ul>
     *   <li>{@code generateQuiz == false} ⇒ {@code quizQuestionCount}
     *       MUST be {@code null}.</li>
     *   <li>{@code generateQuiz == true} ⇒ {@code quizQuestionCount}
     *       MUST be present.</li>
     * </ul>
     * {@code null} {@code generateQuiz} is treated as {@code false}.
     */
    @AssertTrue(message = "Vui lòng chọn số câu hỏi khi bật tự động tạo bài Quiz.")
    public boolean isQuizOptionShapeValid() {
        if (generateQuiz == null || Boolean.FALSE.equals(generateQuiz)) {
            return quizQuestionCount == null;
        }
        return quizQuestionCount != null;
    }

    /**
     * Cross-field invariant (Phase C1):
     * <ul>
     *   <li>FREE ({@code isPaid == false}): {@code uploadId} MUST be null,
     *       {@code documentUrl} MUST be non-blank, {@code storagePath}
     *       MUST be non-blank.</li>
     *   <li>PAID ({@code isPaid == true}): {@code uploadId} MUST be
     *       non-null; {@code documentUrl} and {@code storagePath} MUST
     *       be null / blank because the authoritative paths are stored
     *       on {@link com.cmcu.itstudy.entity.PendingStorageUpload}
     *       and copied to the {@link com.cmcu.itstudy.entity.DocumentFile}
     *       inside the binder transaction.</li>
     * </ul>
     * Clients that try to mix shapes (e.g. paid request with URL/path)
     * are rejected with HTTP 400 here before any remote call.
     */
    @AssertTrue(message = "Dữ liệu hình thức và định danh upload chưa hợp lệ.")
    public boolean isPaidUploadShapeValid() {
        if (isPaid == null) {
            return false;
        }
        if (Boolean.TRUE.equals(isPaid)) {
            if (uploadId == null) {
                return false;
            }
            return isNullOrBlank(documentUrl) && isNullOrBlank(storagePath);
        }
        // Free path: no uploadId, authoritative URL/path required.
        return uploadId == null
                && !isNullOrBlank(documentUrl)
                && !isNullOrBlank(storagePath);
    }

    private static boolean isNullOrBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
