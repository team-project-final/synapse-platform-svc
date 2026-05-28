package com.synapse.platform.global.kafka;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.kafka.topics")
public class KafkaTopicProperties {

    private String dlqSuffix = ".DLT";
    private String userRegistered = "platform.auth.user-registered-v1";

    public String getDlqSuffix() {
        return dlqSuffix;
    }

    public void setDlqSuffix(String dlqSuffix) {
        this.dlqSuffix = dlqSuffix;
    }

    public String getUserRegistered() {
        return userRegistered;
    }

    public void setUserRegistered(String userRegistered) {
        this.userRegistered = userRegistered;
    }
}
