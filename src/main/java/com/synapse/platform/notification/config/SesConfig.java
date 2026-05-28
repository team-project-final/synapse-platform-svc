package com.synapse.platform.notification.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sesv2.SesV2Client;

@Configuration
@ConditionalOnProperty(name = "app.ses.enabled", havingValue = "true")
public class SesConfig {

    @Value("${app.ses.region:ap-northeast-2}")
    private String region;

    @Bean
    public SesV2Client sesV2Client() {
        return SesV2Client.builder()
                .region(Region.of(region))
                .build();
    }
}
