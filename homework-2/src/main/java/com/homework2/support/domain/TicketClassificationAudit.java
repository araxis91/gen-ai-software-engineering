package com.homework2.support.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "ticket_classification_audit",
        indexes = {
                @Index(name = "idx_ticket_classification_audit_ticket_id", columnList = "ticket_id"),
                @Index(name = "idx_ticket_classification_audit_created_at", columnList = "created_at")
        }
)
public class TicketClassificationAudit {
    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "ticket_id", nullable = false)
    private UUID ticketId;

    @Enumerated(EnumType.STRING)
    @Column(name = "decision_type", nullable = false, length = 32)
    private ClassificationDecisionType decisionType;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_category", length = 32)
    private Category previousCategory;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_category", length = 32)
    private Category newCategory;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_priority", length = 16)
    private Priority previousPriority;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_priority", length = 16)
    private Priority newPriority;

    @Column(name = "confidence")
    private Double confidence;

    @Column(name = "reasoning", length = 2000)
    private String reasoning;

    @Column(name = "keywords", length = 2000)
    private String keywords;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public TicketClassificationAudit() {
    }

    @PrePersist
    void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getTicketId() {
        return ticketId;
    }

    public void setTicketId(UUID ticketId) {
        this.ticketId = ticketId;
    }

    public ClassificationDecisionType getDecisionType() {
        return decisionType;
    }

    public void setDecisionType(ClassificationDecisionType decisionType) {
        this.decisionType = decisionType;
    }

    public Category getPreviousCategory() {
        return previousCategory;
    }

    public void setPreviousCategory(Category previousCategory) {
        this.previousCategory = previousCategory;
    }

    public Category getNewCategory() {
        return newCategory;
    }

    public void setNewCategory(Category newCategory) {
        this.newCategory = newCategory;
    }

    public Priority getPreviousPriority() {
        return previousPriority;
    }

    public void setPreviousPriority(Priority previousPriority) {
        this.previousPriority = previousPriority;
    }

    public Priority getNewPriority() {
        return newPriority;
    }

    public void setNewPriority(Priority newPriority) {
        this.newPriority = newPriority;
    }

    public Double getConfidence() {
        return confidence;
    }

    public void setConfidence(Double confidence) {
        this.confidence = confidence;
    }

    public String getReasoning() {
        return reasoning;
    }

    public void setReasoning(String reasoning) {
        this.reasoning = reasoning;
    }

    public String getKeywords() {
        return keywords;
    }

    public void setKeywords(String keywords) {
        this.keywords = keywords;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
