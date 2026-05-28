package com.synapse.platform.user.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class UserStatusDomainTest {

    @Test
    void suspendAndActivate_shouldChangeStatusAndSuspendedAt() {
        User user = User.ofEmailPassword("user@example.com", "user", "$2a$hash", UUID.randomUUID());

        user.suspend();

        assertThat(user.getStatus()).isEqualTo(UserStatus.SUSPENDED);
        assertThat(user.getSuspendedAt()).isNotNull();

        user.activate();

        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.getSuspendedAt()).isNull();
    }

    @Test
    void softDelete_shouldMaskPersonalFields() {
        UUID userId = UUID.randomUUID();
        User user = User.ofEmailPassword("user@example.com", "user", "$2a$hash", UUID.randomUUID());
        ReflectionTestUtils.setField(user, "id", userId);

        user.softDelete();

        assertThat(user.getStatus()).isEqualTo(UserStatus.DELETED);
        assertThat(user.getEmail()).isEqualTo("deleted_" + userId + "@deleted.invalid");
        assertThat(user.getUsername()).isEqualTo("deleted_" + userId);
        assertThat(user.getDisplayName()).isEqualTo("Deleted User");
        assertThat(user.getDeletedAt()).isNotNull();
    }
}
