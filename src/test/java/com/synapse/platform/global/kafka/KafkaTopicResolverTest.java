package com.synapse.platform.global.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class KafkaTopicResolverTest {

    @Test
    void shouldReturnBaseTopicsWhenPrefixIsEmpty() {
        KafkaTopicResolver resolver = new KafkaTopicResolver(new KafkaTopicProperties());

        assertThat(resolver.userRegistered()).isEqualTo("platform.auth.user-registered-v1");
        assertThat(resolver.notificationSend()).isEqualTo("platform.notification.notification-send-v1");
        assertThat(resolver.noteCreated()).isEqualTo("knowledge.note.note-created-v1");
        assertThat(resolver.noteUpdated()).isEqualTo("knowledge.note.note-updated-v1");
        assertThat(resolver.reviewCompleted()).isEqualTo("learning.card.review-completed-v1");
        assertThat(resolver.badgeEarned()).isEqualTo("engagement.gamification.badge-earned-v1");
        assertThat(resolver.levelUp()).isEqualTo("engagement.gamification.level-up-v1");
    }

    @Test
    void shouldPrefixAllTopicsWhenPrefixIsConfigured() {
        KafkaTopicProperties properties = new KafkaTopicProperties();
        properties.setPrefix("dev.");
        KafkaTopicResolver resolver = new KafkaTopicResolver(properties);

        assertThat(resolver.userRegistered()).isEqualTo("dev.platform.auth.user-registered-v1");
        assertThat(resolver.notificationSend()).isEqualTo("dev.platform.notification.notification-send-v1");
        assertThat(resolver.noteCreated()).isEqualTo("dev.knowledge.note.note-created-v1");
        assertThat(resolver.noteUpdated()).isEqualTo("dev.knowledge.note.note-updated-v1");
        assertThat(resolver.reviewCompleted()).isEqualTo("dev.learning.card.review-completed-v1");
        assertThat(resolver.badgeEarned()).isEqualTo("dev.engagement.gamification.badge-earned-v1");
        assertThat(resolver.levelUp()).isEqualTo("dev.engagement.gamification.level-up-v1");
    }

    @Test
    void shouldAppendDlqSuffixToPrefixedSourceTopic() {
        KafkaTopicProperties properties = new KafkaTopicProperties();
        properties.setPrefix("dev.");
        KafkaTopicResolver resolver = new KafkaTopicResolver(properties);

        assertThat(resolver.dlqTopic(resolver.notificationSend()))
                .isEqualTo("dev.platform.notification.notification-send-v1.dlq");
    }
}
