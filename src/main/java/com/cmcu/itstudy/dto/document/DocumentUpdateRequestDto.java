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
     * Integer VND price. Nullable only for free documents; paid documents must provide
     * a value meeting {@link #MIN_PAID_DOCUMENT_PRICE}. The full-replacement update
     * also accepts a positive value: when {@code isPaid=false} the service normalizes
     * the stored price to 0.
     */
    private Long price;

    /**
     * Cross-field invariant (mirrors {@code DocumentCreateRequestDto#isPriceValid}).
     * The update path is a full replacement, so the same rule as create applies.
     */
    @AssertTrue(message = "isPaid/price combination is invalid: free document requires price null or 0; paid document requires price >= 2,000 VND")
    public boolean isPriceValid() {
        if (isPaid == null) {
            return false;
        }
        if (Boolean.FALSE.equals(isPaid)) {
            return price == null || price == 0L;
        }
        return price != null && price >= MIN_PAID_DOCUMENT_PRICE;
    }
}
