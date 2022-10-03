package com.backbase.productled.service;

import static java.util.Objects.requireNonNull;

import com.backbase.productled.config.EnvironmentConfigurationProperties;
import com.backbase.stream.legalentity.model.LegalEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.util.Optional;
import java.util.Properties;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySource;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.core.io.ClassPathResource;

@RunWith(MockitoJUnitRunner.class)
public class EnvironmentDetailsEnricherTest {

    private LegalEntity legalEntity;

    private EnvironmentConfigurationProperties configurationProperties;

    private EnvironmentDetailsEnricher environmentDetailsEnricher;

    @Before
    public void setUp() throws IOException {
        YamlPropertiesFactoryBean factoryBean = new YamlPropertiesFactoryBean();
        factoryBean.setResources(new ClassPathResource("application.yml"));

        Properties properties = factoryBean.getObject();

        ConfigurationPropertySource propertySource = new MapConfigurationPropertySource(properties);
        Binder binder = new Binder(propertySource);

        configurationProperties =
            binder.bind("environment", EnvironmentConfigurationProperties.class).get();
        environmentDetailsEnricher = new EnvironmentDetailsEnricher(configurationProperties);

        legalEntity = new ObjectMapper().readValue(new File("src/main/resources/legal-entity-hierarchy.json"),
                LegalEntity.class);
    }

    @Test
    public void testExecutionTask() {

        environmentDetailsEnricher.enrich(legalEntity);

        String envPrefix = String.join("-",configurationProperties.getInstallation(), configurationProperties.getRuntime());
        requireNonNull(legalEntity)
            .getSubsidiaries()
            .forEach(legalEntity -> {
                Optional.ofNullable(legalEntity.getUsers()).ifPresent(jobProfileUsers -> jobProfileUsers.forEach(
                    jobProfileUser -> Assert.assertTrue(jobProfileUser.getUser().getExternalId().contains(envPrefix))));
                Optional.ofNullable(legalEntity.getUsers()).ifPresent(jobProfileUsers -> jobProfileUsers.forEach(
                    jobProfileUser -> Assert.assertTrue(jobProfileUser.getUser().getExternalId().contains(envPrefix))));
                Optional.ofNullable(legalEntity.getAdministrators()).ifPresent(
                    users -> users.forEach(user -> Assert.assertTrue(user.getExternalId().contains(envPrefix))));
                Optional.ofNullable(legalEntity.getProductGroups()).ifPresent(productGroups -> {
                    productGroups
                        .forEach(productGroup -> Assert.assertTrue(productGroup.getName().contains(envPrefix)));
                    productGroups.forEach(productGroup -> productGroup.getSavingAccounts().forEach(
                        savingsAccount -> Assert.assertTrue(savingsAccount.getExternalId().contains(envPrefix))));
                    productGroups.forEach(productGroup -> productGroup.getCurrentAccounts().forEach(
                        currentAccount -> Assert.assertTrue(currentAccount.getExternalId().contains(envPrefix))));
                });
            });
    }

}