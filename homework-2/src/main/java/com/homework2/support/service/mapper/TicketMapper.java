package com.homework2.support.service.mapper;

import com.homework2.support.api.dto.TicketMetadataRequest;
import com.homework2.support.api.dto.TicketMetadataResponse;
import com.homework2.support.api.dto.TicketRequest;
import com.homework2.support.api.dto.TicketResponse;
import com.homework2.support.domain.Ticket;
import com.homework2.support.domain.TicketMetadata;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public final class TicketMapper {
    private TicketMapper() {
    }

    public static Ticket toEntity(TicketRequest request) {
        Objects.requireNonNull(request, "request must not be null");

        Ticket ticket = new Ticket();
        applyToExisting(ticket, request);
        return ticket;
    }

    public static void applyToExisting(Ticket ticket, TicketRequest request) {
        Objects.requireNonNull(ticket, "ticket must not be null");
        Objects.requireNonNull(request, "request must not be null");

        ticket.setCustomerId(trim(request.customerId()));
        ticket.setCustomerEmail(trim(request.customerEmail()));
        ticket.setCustomerName(trim(request.customerName()));
        ticket.setSubject(trim(request.subject()));
        ticket.setDescription(trim(request.description()));
        ticket.setCategory(request.category());
        ticket.setPriority(request.priority());
        ticket.setStatus(request.status());
        ticket.setResolvedAt(request.resolvedAt());
        ticket.setAssignedTo(nullableTrim(request.assignedTo()));
        ticket.setTags(copyTags(request.tags()));
        ticket.setMetadata(toMetadata(request.metadata()));
    }

    public static TicketResponse toResponse(Ticket ticket) {
        Objects.requireNonNull(ticket, "ticket must not be null");

        return new TicketResponse(
                ticket.getId(),
                ticket.getCustomerId(),
                ticket.getCustomerEmail(),
                ticket.getCustomerName(),
                ticket.getSubject(),
                ticket.getDescription(),
                ticket.getCategory(),
                ticket.getPriority(),
                ticket.getStatus(),
                ticket.getCreatedAt(),
                ticket.getUpdatedAt(),
                ticket.getResolvedAt(),
                ticket.getAssignedTo(),
                copyTags(ticket.getTags()),
                toMetadataResponse(ticket.getMetadata())
        );
    }

    private static TicketMetadata toMetadata(TicketMetadataRequest request) {
        Objects.requireNonNull(request, "metadata request must not be null");
        return new TicketMetadata(
                request.source(),
                trim(request.browser()),
                request.deviceType()
        );
    }

    private static TicketMetadataResponse toMetadataResponse(TicketMetadata metadata) {
        if (metadata == null) {
            return null;
        }
        return new TicketMetadataResponse(
                metadata.getSource(),
                metadata.getBrowser(),
                metadata.getDeviceType()
        );
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }

    private static String nullableTrim(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static List<String> copyTags(List<String> tags) {
        if (tags == null) {
            return new ArrayList<>();
        }
        return tags.stream()
                .map(TicketMapper::trim)
                .filter(tag -> tag != null && !tag.isBlank())
                .collect(Collectors.toCollection(ArrayList::new));
    }
}
