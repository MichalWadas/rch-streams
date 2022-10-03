package com.backbase.productled;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Initializer class for Bootstrap Spring Boot Application
 */
@SpringBootApplication(scanBasePackages = {"com.backbase.productled", "com.backbase.stream", "com.backbase.mambu"})
public class SetupLegalEntityHierarchyTaskApplication {

    public static void main(String[] args) {
        SpringApplication springApplication = new SpringApplication(SetupLegalEntityHierarchyTaskApplication.class);
        springApplication.setWebApplicationType(WebApplicationType.NONE);
        springApplication.run(args);
    }

}