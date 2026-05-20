package com.synapse.platform.notification.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class PlatformConverter implements AttributeConverter<Platform, String> {

    @Override
    public String convertToDatabaseColumn(Platform attribute) {
        return attribute == null ? null : attribute.getValue();
    }

    @Override
    public Platform convertToEntityAttribute(String dbData) {
        return dbData == null ? null : Platform.from(dbData);
    }
}
