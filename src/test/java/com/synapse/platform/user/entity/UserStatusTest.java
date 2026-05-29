package com.synapse.platform.user.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class UserStatusTest {

    @Test
    void converter_shouldStoreLowercaseAndReadEnum() {
        UserStatus.StatusConverter converter = new UserStatus.StatusConverter();

        assertThat(converter.convertToDatabaseColumn(UserStatus.SUSPENDED)).isEqualTo("suspended");
        assertThat(converter.convertToEntityAttribute("deleted")).isEqualTo(UserStatus.DELETED);
    }
}
