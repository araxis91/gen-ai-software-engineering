package com.homework2.support.api.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record TicketImportSummaryResponse(
        int totalRecords,
        int successful,
        int failed,
        List<TicketImportErrorResponse> errors
) {
}
