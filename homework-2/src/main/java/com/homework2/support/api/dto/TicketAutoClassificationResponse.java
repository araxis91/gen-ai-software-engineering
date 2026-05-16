package com.homework2.support.api.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.homework2.support.domain.Category;
import com.homework2.support.domain.Priority;

import java.util.List;
import java.util.UUID;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record TicketAutoClassificationResponse(
        UUID ticketId,
        Category category,
        Priority priority,
        double confidence,
        String reasoning,
        List<String> keywordsFound
) {
}
