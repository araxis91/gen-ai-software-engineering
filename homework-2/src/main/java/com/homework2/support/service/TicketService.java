package com.homework2.support.service;

import com.homework2.support.api.dto.TicketRequest;
import com.homework2.support.api.dto.TicketResponse;
import com.homework2.support.classification.TicketClassificationService;
import com.homework2.support.domain.Category;
import com.homework2.support.domain.Priority;
import com.homework2.support.domain.Ticket;
import com.homework2.support.domain.TicketStatus;
import com.homework2.support.repository.TicketRepository;
import com.homework2.support.repository.TicketSearchCriteria;
import com.homework2.support.repository.TicketSpecifications;
import com.homework2.support.service.mapper.TicketMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class TicketService {
    private final TicketRepository ticketRepository;
    private final TicketClassificationService ticketClassificationService;

    public TicketService(
            TicketRepository ticketRepository,
            TicketClassificationService ticketClassificationService
    ) {
        this.ticketRepository = ticketRepository;
        this.ticketClassificationService = ticketClassificationService;
    }

    @Transactional
    public TicketResponse createTicket(TicketRequest request) {
        return createTicket(request, false);
    }

    @Transactional
    public TicketResponse createTicket(TicketRequest request, boolean autoClassify) {
        Ticket ticket = TicketMapper.toEntity(request);
        applyResolvedAtOnCreate(ticket);

        Ticket savedTicket = ticketRepository.save(ticket);
        if (autoClassify) {
            ticketClassificationService.autoClassifyTicket(savedTicket);
            savedTicket = ticketRepository.findById(savedTicket.getId()).orElse(savedTicket);
        }
        return TicketMapper.toResponse(savedTicket);
    }

    @Transactional(readOnly = true)
    public Page<TicketResponse> findTickets(TicketSearchCriteria criteria, Pageable pageable) {
        return ticketRepository.findAll(TicketSpecifications.withFilters(criteria), pageable)
                .map(TicketMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public TicketResponse getTicket(UUID ticketId) {
        return TicketMapper.toResponse(getTicketEntityOrThrow(ticketId));
    }

    @Transactional
    public TicketResponse updateTicket(UUID ticketId, TicketRequest request) {
        Ticket existingTicket = getTicketEntityOrThrow(ticketId);
        TicketStatus previousStatus = existingTicket.getStatus();
        Category previousCategory = existingTicket.getCategory();
        Priority previousPriority = existingTicket.getPriority();

        TicketMapper.applyToExisting(existingTicket, request);
        applyResolvedAtOnUpdate(existingTicket, previousStatus);
        ticketClassificationService.recordManualOverride(existingTicket, previousCategory, previousPriority);

        Ticket updatedTicket = ticketRepository.save(existingTicket);
        return TicketMapper.toResponse(updatedTicket);
    }

    @Transactional
    public void deleteTicket(UUID ticketId) {
        Ticket existingTicket = getTicketEntityOrThrow(ticketId);
        ticketRepository.delete(existingTicket);
    }

    private Ticket getTicketEntityOrThrow(UUID ticketId) {
        return ticketRepository.findById(ticketId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Ticket not found: " + ticketId));
    }

    private void applyResolvedAtOnCreate(Ticket ticket) {
        if (isResolvedState(ticket.getStatus()) && ticket.getResolvedAt() == null) {
            ticket.setResolvedAt(LocalDateTime.now());
        }
        if (!isResolvedState(ticket.getStatus())) {
            ticket.setResolvedAt(null);
        }
    }

    private void applyResolvedAtOnUpdate(Ticket ticket, TicketStatus previousStatus) {
        boolean wasResolved = isResolvedState(previousStatus);
        boolean isResolved = isResolvedState(ticket.getStatus());

        if (!wasResolved && isResolved) {
            ticket.setResolvedAt(LocalDateTime.now());
            return;
        }

        if (!isResolved) {
            ticket.setResolvedAt(null);
            return;
        }

        if (ticket.getResolvedAt() == null) {
            ticket.setResolvedAt(LocalDateTime.now());
        }
    }

    private boolean isResolvedState(TicketStatus status) {
        return status == TicketStatus.RESOLVED || status == TicketStatus.CLOSED;
    }
}
