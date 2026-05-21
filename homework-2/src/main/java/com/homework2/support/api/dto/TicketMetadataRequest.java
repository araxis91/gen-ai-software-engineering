package com.homework2.support.api.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.homework2.support.domain.DeviceType;
import com.homework2.support.domain.TicketSource;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record TicketMetadataRequest(
        @NotNull TicketSource source,
        @NotBlank String browser,
        @NotNull DeviceType deviceType
) {
}
