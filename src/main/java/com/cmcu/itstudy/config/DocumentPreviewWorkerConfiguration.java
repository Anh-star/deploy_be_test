package com.cmcu.itstudy.config;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the typed configuration class for the Phase&nbsp;O3
 * document preview worker.
 *
 * <p>This dedicated configuration class keeps the
 * {@code @EnableConfigurationProperties} list in
 * {@code ItstudyApplication.java} untouched.
 *
 * <p>{@link DocumentPreviewWorkerProperties#validate()} runs at bean
 * creation time so an invalid configuration fails the application
 * startup BEFORE any worker cycle is ever scheduled. The worker
 * defaults to {@code enabled=false} so the absence of the
 * {@code app.document-preview.worker.enabled} key never accidentally
 * triggers a real storage cycle.
 */
@Configuration
@EnableConfigurationProperties(DocumentPreviewWorkerProperties.class)
public class DocumentPreviewWorkerConfiguration {

    private final DocumentPreviewWorkerProperties properties;

    public DocumentPreviewWorkerConfiguration(
            DocumentPreviewWorkerProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    void validateProperties() {
        properties.validate();
    }
}
