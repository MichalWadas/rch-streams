package com.backbase.productled.config;

import com.backbase.stream.legalentity.model.LegalEntity;
import com.backbase.stream.productcatalog.model.ProductCatalog;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.ResourceUtils;

/**
 * Load LegalEntity from config file
 */
@Configuration
@Data
@NoArgsConstructor
@Slf4j
public class BootstrapDataConfiguration {

    @Bean
    public LegalEntity legalEntity(ObjectMapper mapper, BootstrapDataConfigurationProperties configurationProperties) {
        try {
            return mapper.readValue(ResourceUtils.getFile(configurationProperties.getLegalEntityLocation()), LegalEntity.class);
        } catch (IOException e) {
            log.error("Error loading Legal Entity hierarchy JSON: File not found in classpath");
            return null;
        }
    }

    @Bean
    public ProductCatalog productCatalog(ObjectMapper mapper, BootstrapDataConfigurationProperties configurationProperties) {
        try {
            return mapper.readValue(ResourceUtils.getFile(configurationProperties.getProductCatalogLocation()), ProductCatalog.class);
        } catch (IOException e) {
            log.warn("Error loading Product Catalog JSON: File not found in classpath");
            return null;
        }
    }

}
