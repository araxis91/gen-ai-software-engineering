package com.homework2.support.repository;

import com.homework2.support.domain.Category;
import com.homework2.support.domain.DeviceType;
import com.homework2.support.domain.Priority;
import com.homework2.support.domain.Ticket;
import com.homework2.support.domain.TicketMetadata;
import com.homework2.support.domain.TicketSource;
import com.homework2.support.domain.TicketStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ActiveProfiles("test")
class TicketSpecificationsIntegrationTest {
    @Autowired
    private TicketRepository ticketRepository;

    @BeforeEach
    void setUp() {
        ticketRepository.deleteAll();
    }

    @Test
    void withFiltersAppliesAllSupportedCriteria() {
        Ticket target = saveTicket(
                "cust-target",
                "target@example.com",
                Category.BILLING_QUESTION,
                Priority.HIGH,
                TicketStatus.NEW,
                "Billing issue",
                "Need billing refund due to duplicate charge on invoice."
        );
        saveTicket(
                "cust-other",
                "other@example.com",
                Category.ACCOUNT_ACCESS,
                Priority.LOW,
                TicketStatus.IN_PROGRESS,
                "Login issue",
                "Cannot access account after password reset attempt."
        );

        TicketSearchCriteria criteria = new TicketSearchCriteria(
                Category.BILLING_QUESTION,
                Priority.HIGH,
                TicketStatus.NEW,
                "  cust-target  ",
                "  TARGET@example.com  ",
                LocalDateTime.now().minusDays(1),
                LocalDateTime.now().plusDays(1)
        );

        List<Ticket> results = ticketRepository.findAll(TicketSpecifications.withFilters(criteria));

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().getId()).isEqualTo(target.getId());
    }

    @Test
    void withFiltersIgnoresBlankTextFilters() {
        saveTicket(
                "cust-one",
                "one@example.com",
                Category.BUG_REPORT,
                Priority.MEDIUM,
                TicketStatus.NEW,
                "Bug report",
                "A reproducible defect appears in the latest release."
        );
        saveTicket(
                "cust-two",
                "two@example.com",
                Category.FEATURE_REQUEST,
                Priority.LOW,
                TicketStatus.WAITING_CUSTOMER,
                "Feature request",
                "Would like export options for dashboard analytics."
        );

        TicketSearchCriteria criteria = new TicketSearchCriteria(
                null,
                null,
                null,
                "   ",
                "   ",
                null,
                null
        );

        List<Ticket> results = ticketRepository.findAll(TicketSpecifications.withFilters(criteria));

        assertThat(results).hasSize(2);
    }

    @Test
    void overloadedWithFiltersMethodBuildsEquivalentSpecification() {
        Ticket target = saveTicket(
                "cust-overload",
                "overload@example.com",
                Category.ACCOUNT_ACCESS,
                Priority.URGENT,
                TicketStatus.CLOSED,
                "Account locked",
                "Cannot access account and this is critical for support operations."
        );
        saveTicket(
                "cust-overload-2",
                "overload2@example.com",
                Category.OTHER,
                Priority.MEDIUM,
                TicketStatus.NEW,
                "General question",
                "Need help understanding available settings in the profile page."
        );

        List<Ticket> results = ticketRepository.findAll(TicketSpecifications.withFilters(
                Category.ACCOUNT_ACCESS,
                Priority.URGENT,
                TicketStatus.CLOSED,
                "cust-overload",
                "OVERLOAD@example.com",
                LocalDateTime.now().minusDays(1),
                LocalDateTime.now().plusDays(1)
        ));

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().getId()).isEqualTo(target.getId());
    }

    @Test
    void withFiltersRejectsNullCriteria() {
        assertThatThrownBy(() -> TicketSpecifications.withFilters((TicketSearchCriteria) null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("criteria must not be null");
    }

    private Ticket saveTicket(
            String customerId,
            String customerEmail,
            Category category,
            Priority priority,
            TicketStatus status,
            String subject,
            String description
    ) {
        Ticket ticket = new Ticket();
        ticket.setCustomerId(customerId);
        ticket.setCustomerEmail(customerEmail);
        ticket.setCustomerName("Customer " + customerId);
        ticket.setSubject(subject);
        ticket.setDescription(description);
        ticket.setCategory(category);
        ticket.setPriority(priority);
        ticket.setStatus(status);
        ticket.setTags(List.of("tag1", "tag2"));
        ticket.setMetadata(new TicketMetadata(TicketSource.WEB_FORM, "Chrome", DeviceType.DESKTOP));
        return ticketRepository.save(ticket);
    }
}
