package com.cmcu.itstudy.config;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the typed configuration classes introduced in Phase O1.
 *
 * <p>This dedicated configuration class keeps the
 * {@code @EnableConfigurationProperties} list in
 * {@code ItstudyApplication.java} untouched.</p>
 *
 * <p>{@link OfficePreviewProperties#validate()} runs at bean creation
 * time so an invalid configuration fails the application startup
 * BEFORE any document is ever converted. This guarantees that
 * {@code OfficeConversionConfigurationException} is never surfaced as
 * a per-document terminal failure.</p>
 */
@Configuration
@EnableConfigurationProperties(OfficePreviewProperties.class)
public class OfficePreviewConfiguration {

    private final OfficePreviewProperties properties;

    public OfficePreviewConfiguration(OfficePreviewProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    void validateProperties() {
        properties.validate();
    }
}
