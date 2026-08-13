package com.cmcu.itstudy.dto.autoquiz;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.UUID;

/**
 * Stable JSON payload posted by the backend Auto Quiz dispatcher
 * to the configured n8n webhook.
 *
 * <p>The contract is the authoritative handshake between this
 * service and the n8n workflow. Phase&nbsp;2D ships ONLY this
 * shape. Future phases (Gemini, PDF extraction, callback) will add
 * fields; existing fields MUST remain stable so n8n workflows do
 * not silently break.</p>
 *
 * <h2>Fields</h2>
 * <ul>
 *   <li>{@code generationId} — stable idempotency key for the
 *       backend-side generation. n8n MUST use this as the
 *       deduplication key on its side.</li>
 *   <li>{@code documentId} — the {@link com.cmcu.itstudy.entity.Document}
 *       the quiz is generated for.</li>
 *   <li>{@code documentFileId} — the primary {@link com.cmcu.itstudy.entity.DocumentFile}
 *       of the document; the AI-readable source is resolved from
 *       this on the n8n side, NOT from this payload.</li>
 *   <li>{@code requestedQuestionCount} — number of questions the
 *       uploader wants. Comes from the database (the
 *       {@code QuizGeneration} row), NEVER hard-coded.</li>
 *   <li>{@code dispatchToken} — the lease the dispatcher holds.
 *       n8n MUST echo this token back on the future secure-source
 *       callback so the backend can correlate the response with
 *       the right {@code QuizGeneration} row.</li>
 * </ul>
 *
 * <h2>What this payload does NOT contain</h2>
 * <ul>
 *   <li>Supabase service-role key</li>
 *   <li>Database credentials</li>
 *   <li>JWT / session tokens</li>
 *   <li>Storage credentials</li>
 *   <li>Signed URLs</li>
 * </ul>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "generationId",
        "documentId",
        "documentFileId",
        "requestedQuestionCount",
        "dispatchToken"
})
public final class AutoQuizDispatchPayloadDto {

    @JsonProperty("generationId")
    private UUID generationId;

    @JsonProperty("documentId")
    private UUID documentId;

    @JsonProperty("documentFileId")
    private UUID documentFileId;

    @JsonProperty("requestedQuestionCount")
    private Integer requestedQuestionCount;

    @JsonProperty("dispatchToken")
    private UUID dispatchToken;

    public AutoQuizDispatchPayloadDto() {
    }

    public AutoQuizDispatchPayloadDto(UUID generationId,
                                       UUID documentId,
                                       UUID documentFileId,
                                       Integer requestedQuestionCount,
                                       UUID dispatchToken) {
        this.generationId = generationId;
        this.documentId = documentId;
        this.documentFileId = documentFileId;
        this.requestedQuestionCount = requestedQuestionCount;
        this.dispatchToken = dispatchToken;
    }

    public UUID getGenerationId() {
        return generationId;
    }

    public UUID getDocumentId() {
        return documentId;
    }

    public UUID getDocumentFileId() {
        return documentFileId;
    }

    public Integer getRequestedQuestionCount() {
        return requestedQuestionCount;
    }

    public UUID getDispatchToken() {
        return dispatchToken;
    }

    public void setGenerationId(UUID generationId) {
        this.generationId = generationId;
    }

    public void setDocumentId(UUID documentId) {
        this.documentId = documentId;
    }

    public void setDocumentFileId(UUID documentFileId) {
        this.documentFileId = documentFileId;
    }

    public void setRequestedQuestionCount(Integer requestedQuestionCount) {
        this.requestedQuestionCount = requestedQuestionCount;
    }

    public void setDispatchToken(UUID dispatchToken) {
        this.dispatchToken = dispatchToken;
    }
}