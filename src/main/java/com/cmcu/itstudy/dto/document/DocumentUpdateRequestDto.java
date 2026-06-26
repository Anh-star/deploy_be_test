package com.cmcu.itstudy.dto.document;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
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

    @NotNull(message = "isPaid flag cannot be null")
    private Boolean isPaid;

    @Min(value = 0, message = "Price cannot be negative")
    private Long price;

    @AssertTrue(message = "Price must be greater than 0 when isPaid is true")
    private boolean isPriceValid() {
        return !Boolean.TRUE.equals(isPaid) || (price != null && price > 0);
    }
}
