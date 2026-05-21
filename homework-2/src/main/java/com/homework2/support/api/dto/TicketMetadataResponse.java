package com.homework2.support.api.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.homework2.support.domain.DeviceType;
import com.homework2.support.domain.TicketSource;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record TicketMetadataResponse(
        TicketSource source,
        String browser,
        DeviceType deviceType
) {
}
