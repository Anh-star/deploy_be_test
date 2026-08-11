package com.cmcu.itstudy.dto.storage;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Request body for the paid-upload-target endpoint.
 *
 * <p>Only upload-target metadata (filename, MIME type, size) is accepted.
 * The bucket, object path, and userId are NEVER accepted from the frontend.
 *
 * <p>Business validation (extension whitelist, MIME-extension matching,
 * max size cap) lives in the service layer.
 *
 * <h2>JSON deserialization</h2>
 * <p>This class declares a public no-arg constructor (Lombok
 * {@code @NoArgsConstructor}) and setters (Lombok {@code @Setter}) so
 * Spring / Jackson can deserialize it from a JSON request body without
 * any {@code @JsonCreator} annotation. {@code @AllArgsConstructor} is
 * kept so {@code @Builder} (also kept for tests and internal callers)
 * continues to compile.
 *
 * <h2>JSON field contract</h2>
 * <ul>
 *   <li>{@code fileName} — must be non-blank.</li>
 *   <li>{@code mimeType} — must be non-blank.</li>
 *   <li>{@code sizeBytes} — must be non-null and positive.</li>
 * </ul>
 * Unknown JSON properties ({@code bucket}, {@code path}, {@code userId},
 * ...) are ignored by Jackson by default; this class does NOT define
 * any authoritative field for them. Server-side code never reads them.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaidUploadTargetRequestDto {

    @NotBlank(message = "fileName must not be blank")
    private String fileName;

    @NotBlank(message = "mimeType must not be blank")
    private String mimeType;

    @NotNull(message = "sizeBytes must not be null")
    @Positive(message = "sizeBytes must be greater than 0")
    private Long sizeBytes;
}
