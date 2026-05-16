package com.homework2.support.api;

import com.homework2.support.api.dto.TicketRequest;
import com.homework2.support.api.dto.TicketResponse;
import com.homework2.support.api.dto.TicketImportSummaryResponse;
import com.homework2.support.api.dto.TicketAutoClassificationResponse;
import com.homework2.support.classification.TicketClassificationService;
import com.homework2.support.domain.Category;
import com.homework2.support.domain.Priority;
import com.homework2.support.domain.TicketStatus;
import com.homework2.support.repository.TicketSearchCriteria;
import com.homework2.support.service.TicketImportService;
import com.homework2.support.service.TicketService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.function.Function;

@RestController
@RequestMapping("/tickets")
public class TicketController {
    private final TicketService ticketService;
    private final TicketImportService ticketImportService;
    private final TicketClassificationService ticketClassificationService;

    public TicketController(
            TicketService ticketService,
            TicketImportService ticketImportService,
            TicketClassificationService ticketClassificationService
    ) {
        this.ticketService = ticketService;
        this.ticketImportService = ticketImportService;
        this.ticketClassificationService = ticketClassificationService;
    }

    @PostMapping
    public ResponseEntity<TicketResponse> createTicket(
            @Valid @RequestBody TicketRequest request,
            @RequestParam(name = "autoClassify", required = false) Boolean autoClassify,
            @RequestParam(name = "auto_classify", required = false) Boolean autoClassifySnakeCase
    ) {
        boolean shouldAutoClassify = Boolean.TRUE.equals(autoClassify) || Boolean.TRUE.equals(autoClassifySnakeCase);
        TicketResponse createdTicket = ticketService.createTicket(request, shouldAutoClassify);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdTicket);
    }
    @PostMapping("/import")
    public TicketImportSummaryResponse importTickets(@RequestParam("file") MultipartFile file) {
        return ticketImportService.importTickets(file);
    }

    @PostMapping("/{id}/auto-classify")
    public TicketAutoClassificationResponse autoClassifyTicket(@PathVariable("id") UUID ticketId) {
        return ticketClassificationService.autoClassifyTicket(ticketId);
    }

    @GetMapping
    public Page<TicketResponse> listTickets(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String customerId,
            @RequestParam(required = false) String customerEmail,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime createdAtFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime createdAtTo,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        TicketSearchCriteria criteria = new TicketSearchCriteria(
                parseOptionalEnum(category, Category::fromValue, "category"),
                parseOptionalEnum(priority, Priority::fromValue, "priority"),
                parseOptionalEnum(status, TicketStatus::fromValue, "status"),
                normalizeOptionalText(customerId),
                normalizeOptionalText(customerEmail),
                createdAtFrom,
                createdAtTo
        );

        return ticketService.findTickets(criteria, pageable);
    }

    @GetMapping("/{id}")
    public TicketResponse getTicket(@PathVariable("id") UUID ticketId) {
        return ticketService.getTicket(ticketId);
    }

    @PutMapping("/{id}")
    public TicketResponse updateTicket(
            @PathVariable("id") UUID ticketId,
            @Valid @RequestBody TicketRequest request
    ) {
        return ticketService.updateTicket(ticketId, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTicket(@PathVariable("id") UUID ticketId) {
        ticketService.deleteTicket(ticketId);
        return ResponseEntity.noContent().build();
    }

    private static String normalizeOptionalText(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static <T> T parseOptionalEnum(String value, Function<String, T> parser, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return parser.apply(value.trim());
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid " + fieldName + ": " + value);
        }
    }
}
