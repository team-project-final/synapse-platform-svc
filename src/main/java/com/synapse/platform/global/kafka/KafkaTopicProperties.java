package com.synapse.platform.global.kafka;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.kafka.topics")
public class KafkaTopicProperties {

    private String prefix = "";
    private String dlqSuffix = ".dlq";
    private String userRegistered = "platform.auth.user-registered-v1";
    private String notificationSend = "platform.notification.notification-send-v1";
    private String noteCreated = "knowledge.note.note-created-v1";
    private String noteUpdated = "knowledge.note.note-updated-v1";
    private String reviewCompleted = "learning.card.review-completed-v1";
    private String badgeEarned = "engagement.gamification.badge-earned-v1";
    private String levelUp = "engagement.gamification.level-up-v1";

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

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

    public String getNotificationSend() {
        return notificationSend;
    }

    public void setNotificationSend(String notificationSend) {
        this.notificationSend = notificationSend;
    }

    public String getNoteCreated() {
        return noteCreated;
    }

    public void setNoteCreated(String noteCreated) {
        this.noteCreated = noteCreated;
    }

    public String getNoteUpdated() {
        return noteUpdated;
    }

    public void setNoteUpdated(String noteUpdated) {
        this.noteUpdated = noteUpdated;
    }

    public String getReviewCompleted() {
        return reviewCompleted;
    }

    public void setReviewCompleted(String reviewCompleted) {
        this.reviewCompleted = reviewCompleted;
    }

    public String getBadgeEarned() {
        return badgeEarned;
    }

    public void setBadgeEarned(String badgeEarned) {
        this.badgeEarned = badgeEarned;
    }

    public String getLevelUp() {
        return levelUp;
    }

    public void setLevelUp(String levelUp) {
        this.levelUp = levelUp;
    }
}
