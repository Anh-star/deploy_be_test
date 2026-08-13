package com.cmcu.itstudy.config;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the typed configuration class for the Phase&nbsp;2D
 * Auto Quiz backend → n8n dispatcher.
 *
 * <p>This dedicated configuration class keeps the
 * {@code @EnableConfigurationProperties} list in
 * {@code ItstudyApplication.java} untouched.</p>
 *
 * <p>{@link AutoQuizDispatchProperties#validate()} runs at bean
 * creation time so an invalid configuration fails the application
 * startup BEFORE any dispatcher cycle is ever scheduled.</p>
 */
@Configuration
@EnableConfigurationProperties(AutoQuizDispatchProperties.class)
public class AutoQuizDispatchConfiguration {

    private final AutoQuizDispatchProperties properties;

    public AutoQuizDispatchConfiguration(AutoQuizDispatchProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    void validateProperties() {
        properties.validate();
    }
}