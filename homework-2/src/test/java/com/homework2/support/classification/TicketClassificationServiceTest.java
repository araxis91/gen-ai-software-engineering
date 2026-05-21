package com.homework2.support.classification;

import com.homework2.support.api.dto.TicketAutoClassificationResponse;
import com.homework2.support.domain.Category;
import com.homework2.support.domain.ClassificationDecisionType;
import com.homework2.support.domain.DeviceType;
import com.homework2.support.domain.Priority;
import com.homework2.support.domain.Ticket;
import com.homework2.support.domain.TicketClassificationAudit;
import com.homework2.support.domain.TicketMetadata;
import com.homework2.support.domain.TicketSource;
import com.homework2.support.domain.TicketStatus;
import com.homework2.support.repository.TicketClassificationAuditRepository;
import com.homework2.support.repository.TicketRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TicketClassificationServiceTest {
    @Mock
    private TicketRepository ticketRepository;
    @Mock
    private TicketClassificationAuditRepository ticketClassificationAuditRepository;

    @InjectMocks
    private TicketClassificationService classificationService;

    @Test
    void autoClassifyTicketByIdThrowsNotFoundWhenTicketMissing() {
        UUID ticketId = UUID.randomUUID();
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> classificationService.autoClassifyTicket(ticketId))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode")
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void autoClassifyTicketByIdDelegatesAndReturnsClassification() {
        Ticket ticket = buildTicket(
                UUID.randomUUID(),
                "Cannot access account",
                "Cannot access account and this is critical for production users."
        );
        ticket.setCategory(Category.OTHER);
        ticket.setPriority(Priority.LOW);
        when(ticketRepository.findById(ticket.getId())).thenReturn(Optional.of(ticket));
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TicketAutoClassificationResponse response = classificationService.autoClassifyTicket(ticket.getId());

        assertThat(response.ticketId()).isEqualTo(ticket.getId());
        assertThat(response.category()).isEqualTo(Category.ACCOUNT_ACCESS);
        assertThat(response.priority()).isEqualTo(Priority.URGENT);
    }

    @Test
    void autoClassifyTicketUpdatesFieldsAndWritesAudit() {
        Ticket ticket = buildTicket(
                UUID.randomUUID(),
                "Password login issue",
                "Cannot access account due to authentication failure and security concern."
        );
        ticket.setCategory(Category.OTHER);
        ticket.setPriority(Priority.LOW);
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TicketAutoClassificationResponse response = classificationService.autoClassifyTicket(ticket);

        assertThat(response.category()).isEqualTo(Category.ACCOUNT_ACCESS);
        assertThat(response.priority()).isEqualTo(Priority.URGENT);
        assertThat(response.confidence()).isGreaterThan(0.6d);
        assertThat(response.keywordsFound()).isNotEmpty();
        assertThat(ticket.getClassificationLastMethod()).isEqualTo(ClassificationDecisionType.AUTO_CLASSIFICATION);
        assertThat(ticket.getClassificationConfidence()).isEqualTo(response.confidence());
        assertThat(ticket.getClassificationReasoning()).isNotBlank();
        assertThat(ticket.getClassificationLastUpdatedAt()).isNotNull();

        ArgumentCaptor<TicketClassificationAudit> auditCaptor = ArgumentCaptor.forClass(TicketClassificationAudit.class);
        verify(ticketClassificationAuditRepository).save(auditCaptor.capture());
        TicketClassificationAudit audit = auditCaptor.getValue();
        assertThat(audit.getDecisionType()).isEqualTo(ClassificationDecisionType.AUTO_CLASSIFICATION);
        assertThat(audit.getPreviousCategory()).isEqualTo(Category.OTHER);
        assertThat(audit.getNewCategory()).isEqualTo(Category.ACCOUNT_ACCESS);
        assertThat(audit.getPreviousPriority()).isEqualTo(Priority.LOW);
        assertThat(audit.getNewPriority()).isEqualTo(Priority.URGENT);
        assertThat(audit.getConfidence()).isEqualTo(response.confidence());
    }

    @Test
    void autoClassifyTicketDefaultsToOtherAndMediumWhenNoSignalsFound() {
        Ticket ticket = buildTicket(
                UUID.randomUUID(),
                "Hello support team",
                "I would appreciate some help with a general question."
        );
        ticket.setCategory(Category.ACCOUNT_ACCESS);
        ticket.setPriority(Priority.HIGH);
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TicketAutoClassificationResponse response = classificationService.autoClassifyTicket(ticket);

        assertThat(response.category()).isEqualTo(Category.OTHER);
        assertThat(response.priority()).isEqualTo(Priority.MEDIUM);
        assertThat(response.confidence()).isEqualTo(0.35d);
        assertThat(response.reasoning()).contains("no strong category-specific keywords were found");
        assertThat(response.keywordsFound()).isEmpty();
        assertThat(ticket.getClassificationKeywords()).isNull();
    }

    @Test
    void recordManualOverrideSkipsWhenNoChanges() {
        Ticket ticket = buildTicket(
                UUID.randomUUID(),
                "Subject",
                "Description with enough text for ticket validation."
        );
        ticket.setCategory(Category.ACCOUNT_ACCESS);
        ticket.setPriority(Priority.HIGH);

        classificationService.recordManualOverride(ticket, Category.ACCOUNT_ACCESS, Priority.HIGH);

        verify(ticketClassificationAuditRepository, never()).save(any(TicketClassificationAudit.class));
        assertThat(ticket.getClassificationLastMethod()).isNull();
    }

    @Test
    void recordManualOverrideWritesAuditAndSetsManualMetadata() {
        Ticket ticket = buildTicket(
                UUID.randomUUID(),
                "Subject",
                "Description with enough text for ticket validation."
        );
        ticket.setCategory(Category.BILLING_QUESTION);
        ticket.setPriority(Priority.LOW);

        classificationService.recordManualOverride(ticket, Category.ACCOUNT_ACCESS, Priority.HIGH);

        assertThat(ticket.getClassificationLastMethod()).isEqualTo(ClassificationDecisionType.MANUAL_OVERRIDE);
        assertThat(ticket.getClassificationConfidence()).isNull();
        assertThat(ticket.getClassificationReasoning()).isEqualTo("Manual override via PUT /tickets/{id}.");
        assertThat(ticket.getClassificationKeywords()).isNull();
        assertThat(ticket.getClassificationLastUpdatedAt()).isNotNull();

        ArgumentCaptor<TicketClassificationAudit> auditCaptor = ArgumentCaptor.forClass(TicketClassificationAudit.class);
        verify(ticketClassificationAuditRepository).save(auditCaptor.capture());
        TicketClassificationAudit audit = auditCaptor.getValue();
        assertThat(audit.getDecisionType()).isEqualTo(ClassificationDecisionType.MANUAL_OVERRIDE);
        assertThat(audit.getPreviousCategory()).isEqualTo(Category.ACCOUNT_ACCESS);
        assertThat(audit.getNewCategory()).isEqualTo(Category.BILLING_QUESTION);
        assertThat(audit.getPreviousPriority()).isEqualTo(Priority.HIGH);
        assertThat(audit.getNewPriority()).isEqualTo(Priority.LOW);
        assertThat(audit.getConfidence()).isNull();
    }

    private static Ticket buildTicket(UUID id, String subject, String description) {
        Ticket ticket = new Ticket();
        ticket.setId(id);
        ticket.setCustomerId("cust-001");
        ticket.setCustomerEmail("customer@example.com");
        ticket.setCustomerName("Jane Customer");
        ticket.setSubject(subject);
        ticket.setDescription(description);
        ticket.setCategory(Category.OTHER);
        ticket.setPriority(Priority.MEDIUM);
        ticket.setStatus(TicketStatus.NEW);
        ticket.setMetadata(new TicketMetadata(TicketSource.WEB_FORM, "Chrome", DeviceType.DESKTOP));
        return ticket;
    }
}
