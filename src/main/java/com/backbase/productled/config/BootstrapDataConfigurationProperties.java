package com.backbase.productled.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for data file location
 */
@Data
@Component
@ConfigurationProperties(prefix = "backbase.stream.bootstrap")
public class BootstrapDataConfigurationProperties {

    private String legalEntityLocation;
    private String productCatalogLocation;

}
