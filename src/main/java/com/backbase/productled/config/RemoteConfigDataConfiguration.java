package com.backbase.productled.config;

import com.backbase.productled.model.RemoteConfigUserGroup;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.ResourceUtils;

@Configuration
@Data
@NoArgsConstructor
@Slf4j
public class RemoteConfigDataConfiguration {

    @Bean
    public List<RemoteConfigUserGroup> remoteConfigUsers(ObjectMapper mapper, AdminConfigurationProperties adminConfigurationProperties) {
        try {
            return mapper.readValue(ResourceUtils.getFile(adminConfigurationProperties.getRemoteConfigUsersLocation()), new TypeReference<>() {});
        } catch (IOException e) {
            log.error("Error loading Remote Config users JSON: File not found in classpath");
            return Collections.emptyList();
        }
    }
}
