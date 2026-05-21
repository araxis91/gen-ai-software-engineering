package com.homework2.support.domain;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum Category {
    ACCOUNT_ACCESS("account_access"),
    TECHNICAL_ISSUE("technical_issue"),
    BILLING_QUESTION("billing_question"),
    FEATURE_REQUEST("feature_request"),
    BUG_REPORT("bug_report"),
    OTHER("other");

    private final String apiValue;

    Category(String apiValue) {
        this.apiValue = apiValue;
    }

    @JsonValue
    public String getApiValue() {
        return apiValue;
    }

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static Category fromValue(String value) {
        for (Category category : values()) {
            if (category.apiValue.equalsIgnoreCase(value)) {
                return category;
            }
        }
        throw new IllegalArgumentException("Unknown category: " + value);
    }
}
