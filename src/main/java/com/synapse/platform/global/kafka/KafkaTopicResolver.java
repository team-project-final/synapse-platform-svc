package com.synapse.platform.global.kafka;

import org.springframework.stereotype.Component;

@Component("kafkaTopicResolver")
public class KafkaTopicResolver {

    private final String prefix;
    private final String dlqSuffix;
    private final String userRegistered;
    private final String notificationSend;
    private final String noteCreated;
    private final String noteUpdated;
    private final String reviewCompleted;
    private final String badgeEarned;
    private final String levelUp;

    public KafkaTopicResolver(KafkaTopicProperties properties) {
        this.prefix = normalize(properties.getPrefix());
        this.dlqSuffix = normalize(properties.getDlqSuffix());
        this.userRegistered = normalize(properties.getUserRegistered());
        this.notificationSend = normalize(properties.getNotificationSend());
        this.noteCreated = normalize(properties.getNoteCreated());
        this.noteUpdated = normalize(properties.getNoteUpdated());
        this.reviewCompleted = normalize(properties.getReviewCompleted());
        this.badgeEarned = normalize(properties.getBadgeEarned());
        this.levelUp = normalize(properties.getLevelUp());
    }

    public String userRegistered() {
        return prefixed(userRegistered);
    }

    public String notificationSend() {
        return prefixed(notificationSend);
    }

    public String noteCreated() {
        return prefixed(noteCreated);
    }

    public String noteUpdated() {
        return prefixed(noteUpdated);
    }

    public String reviewCompleted() {
        return prefixed(reviewCompleted);
    }

    public String badgeEarned() {
        return prefixed(badgeEarned);
    }

    public String levelUp() {
        return prefixed(levelUp);
    }

    public String dlqTopic(String sourceTopic) {
        return normalize(sourceTopic) + dlqSuffix;
    }

    private String prefixed(String baseTopic) {
        return prefix + baseTopic;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
