package com.homework2.support.api.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.homework2.support.domain.Category;
import com.homework2.support.domain.Priority;
import com.homework2.support.domain.TicketStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record TicketResponse(
        UUID id,
        String customerId,
        String customerEmail,
        String customerName,
        String subject,
        String description,
        Category category,
        Priority priority,
        TicketStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime resolvedAt,
        String assignedTo,
        List<String> tags,
        TicketMetadataResponse metadata
) {
}
