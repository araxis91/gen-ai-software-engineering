package com.homework2.support.domain;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum DeviceType {
    DESKTOP("desktop"),
    MOBILE("mobile"),
    TABLET("tablet");

    private final String apiValue;

    DeviceType(String apiValue) {
        this.apiValue = apiValue;
    }

    @JsonValue
    public String getApiValue() {
        return apiValue;
    }

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static DeviceType fromValue(String value) {
        for (DeviceType deviceType : values()) {
            if (deviceType.apiValue.equalsIgnoreCase(value)) {
                return deviceType;
            }
        }
        throw new IllegalArgumentException("Unknown deviceType: " + value);
    }
}
