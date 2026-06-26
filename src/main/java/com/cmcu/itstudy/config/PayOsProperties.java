package com.cmcu.itstudy.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@ConfigurationProperties(prefix = "payos")
public class PayOsProperties {

    private String clientId;

    private String apiKey;

    private String checksumKey;

    private String apiBaseUrl;

    private String returnUrl;

    private String cancelUrl;
}