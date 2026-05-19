package com.synapse.platform.billing.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "stripe")
public record StripeProperties(Webhook webhook, Plans plans) {
    public record Webhook(String secret) {
    }

    public record Plans(Plan pro, Plan team, Plan enterprise) {
    }

    public record Plan(String priceId) {
    }
}
