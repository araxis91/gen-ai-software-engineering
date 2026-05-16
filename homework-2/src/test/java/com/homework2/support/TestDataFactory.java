package com.homework2.support;

import com.homework2.support.api.dto.TicketMetadataRequest;
import com.homework2.support.api.dto.TicketRequest;
import com.homework2.support.domain.Category;
import com.homework2.support.domain.DeviceType;
import com.homework2.support.domain.Priority;
import com.homework2.support.domain.TicketSource;
import com.homework2.support.domain.TicketStatus;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class TestDataFactory {
    private TestDataFactory() {
    }

    public static TicketRequest validTicketRequest() {
        return validTicketRequest("Cannot access my account", "I cannot access my account after enabling 2FA and need urgent help.");
    }

    public static TicketRequest validTicketRequest(String subject, String description) {
        return new TicketRequest(
                "cust-001",
                "customer@example.com",
                "Jane Customer",
                subject,
                description,
                Category.ACCOUNT_ACCESS,
                Priority.HIGH,
                TicketStatus.NEW,
                null,
                null,
                List.of("login", "2fa"),
                new TicketMetadataRequest(TicketSource.WEB_FORM, "Chrome", DeviceType.DESKTOP)
        );
    }

    public static Map<String, Object> validTicketRequestMap() {
        return validTicketRequestMap("Cannot access my account", "I cannot access my account after enabling 2FA and need urgent help.");
    }

    public static Map<String, Object> validTicketRequestMap(String subject, String description) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("source", "web_form");
        metadata.put("browser", "Chrome 125");
        metadata.put("device_type", "desktop");

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("customer_id", "cust-001");
        request.put("customer_email", "customer@example.com");
        request.put("customer_name", "Jane Customer");
        request.put("subject", subject);
        request.put("description", description);
        request.put("category", "account_access");
        request.put("priority", "high");
        request.put("status", "new");
        request.put("resolved_at", null);
        request.put("assigned_to", null);
        request.put("tags", List.of("login", "2fa"));
        request.put("metadata", metadata);
        return request;
    }
}
