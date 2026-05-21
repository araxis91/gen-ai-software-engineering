package com.homework2.support.repository;

import com.homework2.support.domain.Category;
import com.homework2.support.domain.Priority;
import com.homework2.support.domain.Ticket;
import com.homework2.support.domain.TicketStatus;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Objects;

public final class TicketSpecifications {
    private TicketSpecifications() {
    }

    public static Specification<Ticket> withFilters(TicketSearchCriteria criteria) {
        Objects.requireNonNull(criteria, "criteria must not be null");

        Specification<Ticket> specification = Specification.where(null);

        if (criteria.category() != null) {
            specification = specification.and(hasCategory(criteria.category()));
        }
        if (criteria.priority() != null) {
            specification = specification.and(hasPriority(criteria.priority()));
        }
        if (criteria.status() != null) {
            specification = specification.and(hasStatus(criteria.status()));
        }
        if (hasText(criteria.customerId())) {
            specification = specification.and(hasCustomerId(criteria.customerId()));
        }
        if (hasText(criteria.customerEmail())) {
            specification = specification.and(hasCustomerEmail(criteria.customerEmail()));
        }
        if (criteria.createdAtFrom() != null) {
            specification = specification.and(createdAtFrom(criteria.createdAtFrom()));
        }
        if (criteria.createdAtTo() != null) {
            specification = specification.and(createdAtTo(criteria.createdAtTo()));
        }

        return specification;
    }

    public static Specification<Ticket> withFilters(
            Category category,
            Priority priority,
            TicketStatus status,
            String customerId,
            String customerEmail,
            LocalDateTime createdAtFrom,
            LocalDateTime createdAtTo
    ) {
        return withFilters(new TicketSearchCriteria(
                category,
                priority,
                status,
                customerId,
                customerEmail,
                createdAtFrom,
                createdAtTo
        ));
    }

    public static Specification<Ticket> hasCategory(Category category) {
        return (root, query, builder) -> builder.equal(root.get("category"), category);
    }

    public static Specification<Ticket> hasPriority(Priority priority) {
        return (root, query, builder) -> builder.equal(root.get("priority"), priority);
    }

    public static Specification<Ticket> hasStatus(TicketStatus status) {
        return (root, query, builder) -> builder.equal(root.get("status"), status);
    }

    public static Specification<Ticket> hasCustomerId(String customerId) {
        String normalizedCustomerId = customerId.trim();
        return (root, query, builder) -> builder.equal(root.get("customerId"), normalizedCustomerId);
    }

    public static Specification<Ticket> hasCustomerEmail(String customerEmail) {
        String normalizedEmail = customerEmail.trim().toLowerCase(Locale.ROOT);
        return (root, query, builder) -> builder.equal(builder.lower(root.get("customerEmail")), normalizedEmail);
    }

    public static Specification<Ticket> createdAtFrom(LocalDateTime createdAtFrom) {
        return (root, query, builder) -> builder.greaterThanOrEqualTo(root.get("createdAt"), createdAtFrom);
    }

    public static Specification<Ticket> createdAtTo(LocalDateTime createdAtTo) {
        return (root, query, builder) -> builder.lessThanOrEqualTo(root.get("createdAt"), createdAtTo);
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
