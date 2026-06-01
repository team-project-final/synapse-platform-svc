package com.synapse.platform.user.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Locale;

public enum UserStatus {
    ACTIVE,
    SUSPENDED,
    DELETED;

    public String toDbValue() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static UserStatus fromDbValue(String value) {
        return valueOf(value.toUpperCase(Locale.ROOT));
    }

    @Converter(autoApply = true)
    public static class StatusConverter implements AttributeConverter<UserStatus, String> {

        @Override
        public String convertToDatabaseColumn(UserStatus status) {
            return status == null ? null : status.toDbValue();
        }

        @Override
        public UserStatus convertToEntityAttribute(String dbValue) {
            return dbValue == null ? null : UserStatus.fromDbValue(dbValue);
        }
    }
}
