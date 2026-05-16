package com.homework2.support.repository;

import com.homework2.support.domain.Category;
import com.homework2.support.domain.Priority;
import com.homework2.support.domain.TicketStatus;

import java.time.LocalDateTime;

public record TicketSearchCriteria(
        Category category,
        Priority priority,
        TicketStatus status,
        String customerId,
        String customerEmail,
        LocalDateTime createdAtFrom,
        LocalDateTime createdAtTo
) {
}
