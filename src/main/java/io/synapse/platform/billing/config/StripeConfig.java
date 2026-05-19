package io.synapse.platform.billing.config;

import com.stripe.StripeClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(StripeProperties.class)
public class StripeConfig {

    @Bean
    public StripeClient stripeClient(@Value("${stripe.api.key}") String apiKey) {
        return StripeClient.builder()
                .setApiKey(apiKey)
                .build();
    }
}
