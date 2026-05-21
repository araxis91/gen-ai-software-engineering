package com.homework2.support.api.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.homework2.support.domain.Category;
import com.homework2.support.domain.Priority;
import com.homework2.support.domain.TicketStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record TicketRequest(
        @NotBlank String customerId,
        @NotBlank @Email String customerEmail,
        @NotBlank String customerName,
        @NotBlank @Size(min = 1, max = 200) String subject,
        @NotBlank @Size(min = 10, max = 2000) String description,
        @NotNull Category category,
        @NotNull Priority priority,
        @NotNull TicketStatus status,
        LocalDateTime resolvedAt,
        String assignedTo,
        List<@NotBlank String> tags,
        @NotNull @Valid TicketMetadataRequest metadata
) {
}
