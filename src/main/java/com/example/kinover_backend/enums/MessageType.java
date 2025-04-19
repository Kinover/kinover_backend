package com.example.kinover_backend.enums;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class MessageTypeConverter implements AttributeConverter<MessageType, String> {

    @Override
    public String convertToDatabaseColumn(MessageType attribute) {
        return attribute != null ? attribute.getValue() : null;
    }

    @Override
    public MessageType convertToEntityAttribute(String dbData) {
        if (dbData == null) return null;

        for (MessageType type : MessageType.values()) {
            if (type.getValue().equalsIgnoreCase(dbData.trim())) {
                return type;
            }
        }

        throw new IllegalArgumentException("Unknown value: " + dbData);
    }
}
