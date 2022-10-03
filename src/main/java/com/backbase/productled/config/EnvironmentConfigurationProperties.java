package com.backbase.productled.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for environment information
 */
@Data
@Component
@ConfigurationProperties(prefix = "environment")
public class EnvironmentConfigurationProperties {

    private String installation;
    private String runtime;

}
