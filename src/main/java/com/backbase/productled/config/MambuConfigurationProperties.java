package com.backbase.productled.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for integrating with Mambu
 */
@Component
@ConfigurationProperties(prefix = "mambu")
@Data
public class MambuConfigurationProperties {

    private boolean bootstrapResourcesEnabled = false;
    private String basePath;
    private String username;
    private String password;
    private String currentAccountProductKey;
    private String savingsAccountProductKey;
    private String creditCardAccountProductKey;
    private String branchKey;
    // This account will be used as beneficiary for the random transactions
    private String transactionsBeneficiaryAccountKey;
    private boolean ingestPlacesData;
    private String placesDataFile;

}
