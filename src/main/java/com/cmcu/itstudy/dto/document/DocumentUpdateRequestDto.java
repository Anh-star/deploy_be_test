package com.cmcu.itstudy.dto.document;

import jakarta.validation.constraints.AssertTrue;
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
public class DocumentUpdateRequestDto {

    /**
     * Minimum price for a paid document, in VND. Integer Long; no decimals.
     *
     * <p>Used by the service-side update guard. This DTO does NOT enforce the
     * floor at the validator level (see {@link #isPriceValid()}) because legacy
     * documents priced below 3,000 VND under the previous 2,222 VND minimum
     * must still round-trip their existing price through a metadata-only PUT.
     * The pricing-changed-vs-minimum rule lives in
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

    @NotBlank(message = "Document URL cannot be empty")
    private String documentUrl;

    /** Supabase object path; optional when only metadata changes (keep existing primary file path). */
    private String storagePath;

    @NotBlank(message = "Thumbnail URL cannot be empty")
    private String thumbnailUrl;

    @NotBlank(message = "File name cannot be empty")
    private String fileName;

    @NotNull(message = "File size cannot be empty")
    private Long fileSizeBytes;

    /**
     * Full replacement: must be present. Update is not a partial patch.
     */
    @NotNull(message = "isPaid flag cannot be null")
    private Boolean isPaid;

    /**
     * Integer VND price. Nullable only for free documents; paid documents must
     * provide a strictly positive value. The structural validator
     * {@link #isPriceValid()} does NOT enforce the create-side
     * {@link #MIN_PAID_DOCUMENT_PRICE} floor — legacy prices below 3,000 VND
     * must keep round-tripping on a metadata-only PUT, and the service-side
     * guard in {@code DocumentServiceImpl#updateDocument} decides whether a
     * request actually changes pricing and therefore must meet the minimum.
     */
    private Long price;

    /**
     * Cross-field structural invariant only:
     * <ul>
     *   <li>Free document ({@code isPaid == false}): {@code price} must be null or 0.</li>
     *   <li>Paid document ({@code isPaid == true}): {@code price} must be a positive Long.</li>
     * </ul>
     * The 3,000 VND floor is checked by the service layer once the request is
     * compared against the existing document's pricing.
     */
    @AssertTrue(message = "Dữ liệu hình thức và giá bán tài liệu không hợp lệ.")
    public boolean isPriceValid() {
        if (isPaid == null) {
            return false;
        }
        if (Boolean.FALSE.equals(isPaid)) {
            // Free document: price must be null or zero (no negative, no stray amount).
            return price == null || price == 0L;
        }
        // Paid document: price must be a strictly positive Long. The minimum is
        // checked in the service after we know whether the request changes pricing.
        return price != null && price > 0L;
    }
}
