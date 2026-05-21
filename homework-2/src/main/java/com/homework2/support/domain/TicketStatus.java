package com.homework2.support.domain;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum TicketStatus {
    NEW("new"),
    IN_PROGRESS("in_progress"),
    WAITING_CUSTOMER("waiting_customer"),
    RESOLVED("resolved"),
    CLOSED("closed");

    private final String apiValue;

    TicketStatus(String apiValue) {
        this.apiValue = apiValue;
    }

    @JsonValue
    public String getApiValue() {
        return apiValue;
    }

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static TicketStatus fromValue(String value) {
        for (TicketStatus status : values()) {
            if (status.apiValue.equalsIgnoreCase(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown status: " + value);
    }
}
