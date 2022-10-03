package com.backbase.productled.config;

import com.backbase.productled.service.DataIngestionValidatorService;
import com.backbase.productled.service.EnvironmentDetailsEnricher;
import com.backbase.productled.service.AdminService;
import com.backbase.productled.service.MambuService;
import com.backbase.productled.service.MarqetaService;
import com.backbase.productled.service.PayverisMockService;
import com.backbase.stream.LegalEntitySaga;
import com.backbase.stream.LegalEntityTask;
import com.backbase.stream.legalentity.model.LegalEntity;
import com.backbase.stream.productcatalog.ProductCatalogService;
import com.backbase.stream.productcatalog.model.ProductCatalog;
import com.backbase.stream.worker.exception.StreamTaskException;
import com.backbase.stream.worker.model.StreamTask;
import java.util.Collections;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.cloud.task.configuration.EnableTask;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Flux;

/**
 * Create task from legalEntity and call BB stream
 */
@EnableTask
@Configuration
@AllArgsConstructor
@Slf4j
public class SetupLegalEntityHierarchyConfiguration {

    private final LegalEntitySaga legalEntitySaga;
    private final MambuService mambuService;
    private final MarqetaService marqetaService;
    private final AdminService adminService;
    private final PayverisMockService payverisMockService;
    private final DataIngestionValidatorService validatorService;
    private final ProductCatalogService productCatalogService;
    private final LegalEntity legalEntityHierarchy;
    private final ProductCatalog productCatalog;
    private final EnvironmentDetailsEnricher envDetailEnricher;

    @Bean
    public CommandLineRunner commandLineRunner() {
        return this::run;
    }

    private void run(String... args) throws InterruptedException {

        if (productCatalog == null) {
            log.info("Product Catalog not found in bootstrap config. Skipping creation");
        } else {
            bootstrapProductCatalog(productCatalog);
        }

        if (legalEntityHierarchy == null) {
            log.error("Failed to load Legal Entity Structure");
            System.exit(1);
        } else {
            envDetailEnricher.enrich(legalEntityHierarchy);
            bootstrapLegalEntities(legalEntityHierarchy);
            validatorService.validateIngestedData(legalEntityHierarchy, productCatalog);
            System.exit(0);
        }
    }

    private void bootstrapLegalEntities(LegalEntity legalEntity) {
        log.info("Bootstrapping Root Legal Entity Structure: {}", legalEntity.getName());
        List<LegalEntity> aggregates = Collections.singletonList(legalEntity);

        Flux.fromIterable(aggregates)
            .map(LegalEntityTask::new)
            .flatMap(mambuService::executeTask)
            .flatMap(marqetaService::executeTask)
            .flatMap(legalEntitySaga::executeTask)
            .flatMap(adminService::executeTask)
            .flatMap(payverisMockService::executeTask)
            .doOnNext(StreamTask::logSummary)
            .doOnError(StreamTaskException.class, throwable -> {
                log.error("Failed to bootstrap legal entities: ", throwable);
                throwable.getTask().logSummary();
            })
            .collectList()
            .block();
        log.info("Finished bootstrapping Legal Entity Structure");
    }

    private void bootstrapProductCatalog(ProductCatalog productCatalog) {
        log.info("Bootstrapping Product Catalog");
        productCatalogService.setupProductCatalog(productCatalog);
        log.info("Successfully Bootstrapped Product Catalog");
    }

}
