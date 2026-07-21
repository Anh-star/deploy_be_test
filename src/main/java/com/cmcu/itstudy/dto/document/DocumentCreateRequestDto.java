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

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentCreateRequestDto {

    /**
     * Minimum price for a paid document, in VND. Integer Long; no decimals.
     * Used as a cross-field invariant and by the service for normalization.
     */
    public static final long MIN_PAID_DOCUMENT_PRICE = 2000L;

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

    @NotBlank(message = "Storage path cannot be empty")
    private String storagePath;

    @NotBlank(message = "Thumbnail URL cannot be empty")
    private String thumbnailUrl;

    @NotBlank(message = "File name cannot be empty")
    private String fileName;

    @NotNull(message = "File size cannot be empty")
    private Long fileSizeBytes;

    @NotNull(message = "isPaid flag cannot be null")
    private Boolean isPaid;

    /**
     * Price in integer VND. Nullable for free documents. Rejected when negative
     * via the cross-field validator {@link #isPriceValid()}; the hard floor for
     * paid documents is enforced by the same predicate, not by a field-level
     * {@code @Min}, so free documents can leave this blank or send 0.
     */
    private Long price;

    /**
     * Cross-field invariant:
     * <ul>
     *   <li>Free document ({@code isPaid == false}): {@code price} must be null or 0.</li>
     *   <li>Paid document ({@code isPaid == true}): {@code price} must be present and {@code >= 2,000} VND.</li>
     * </ul>
     * Negative prices are rejected here too.
     */
    @AssertTrue(message = "isPaid/price combination is invalid: free document requires price null or 0; paid document requires price >= 2,000 VND")
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
}
