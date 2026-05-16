package com.homework2.support.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Embedded;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(
        name = "tickets",
        indexes = {
                @Index(name = "idx_tickets_category", columnList = "category"),
                @Index(name = "idx_tickets_priority", columnList = "priority"),
                @Index(name = "idx_tickets_status", columnList = "status"),
                @Index(name = "idx_tickets_created_at", columnList = "created_at")
        }
)
public class Ticket {
    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @NotBlank
    @Column(name = "customer_id", nullable = false, length = 128)
    private String customerId;

    @NotBlank
    @Email
    @Column(name = "customer_email", nullable = false, length = 320)
    private String customerEmail;

    @NotBlank
    @Column(name = "customer_name", nullable = false, length = 255)
    private String customerName;

    @NotBlank
    @Size(min = 1, max = 200)
    @Column(name = "subject", nullable = false, length = 200)
    private String subject;

    @NotBlank
    @Size(min = 10, max = 2000)
    @Column(name = "description", nullable = false, length = 2000)
    private String description;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 32)
    private Category category;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 16)
    private Priority priority;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private TicketStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "assigned_to", length = 255)
    private String assignedTo;
    @Column(name = "classification_confidence")
    private Double classificationConfidence;

    @Column(name = "classification_reasoning", length = 2000)
    private String classificationReasoning;

    @Column(name = "classification_keywords", length = 2000)
    private String classificationKeywords;

    @Enumerated(EnumType.STRING)
    @Column(name = "classification_last_method", length = 32)
    private ClassificationDecisionType classificationLastMethod;

    @Column(name = "classification_last_updated_at")
    private LocalDateTime classificationLastUpdatedAt;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "ticket_tags", joinColumns = @JoinColumn(name = "ticket_id"))
    @Column(name = "tag", nullable = false, length = 64)
    private List<String> tags = new ArrayList<>();

    @Valid
    @NotNull
    @Embedded
    private TicketMetadata metadata;

    public Ticket() {
    }

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.tags == null) {
            this.tags = new ArrayList<>();
        }
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    public String getCustomerEmail() {
        return customerEmail;
    }

    public void setCustomerEmail(String customerEmail) {
        this.customerEmail = customerEmail;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Category getCategory() {
        return category;
    }

    public void setCategory(Category category) {
        this.category = category;
    }

    public Priority getPriority() {
        return priority;
    }

    public void setPriority(Priority priority) {
        this.priority = priority;
    }

    public TicketStatus getStatus() {
        return status;
    }

    public void setStatus(TicketStatus status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public LocalDateTime getResolvedAt() {
        return resolvedAt;
    }

    public void setResolvedAt(LocalDateTime resolvedAt) {
        this.resolvedAt = resolvedAt;
    }

    public String getAssignedTo() {
        return assignedTo;
    }

    public void setAssignedTo(String assignedTo) {
        this.assignedTo = assignedTo;
    }

    public Double getClassificationConfidence() {
        return classificationConfidence;
    }

    public void setClassificationConfidence(Double classificationConfidence) {
        this.classificationConfidence = classificationConfidence;
    }

    public String getClassificationReasoning() {
        return classificationReasoning;
    }

    public void setClassificationReasoning(String classificationReasoning) {
        this.classificationReasoning = classificationReasoning;
    }

    public String getClassificationKeywords() {
        return classificationKeywords;
    }

    public void setClassificationKeywords(String classificationKeywords) {
        this.classificationKeywords = classificationKeywords;
    }

    public ClassificationDecisionType getClassificationLastMethod() {
        return classificationLastMethod;
    }

    public void setClassificationLastMethod(ClassificationDecisionType classificationLastMethod) {
        this.classificationLastMethod = classificationLastMethod;
    }

    public LocalDateTime getClassificationLastUpdatedAt() {
        return classificationLastUpdatedAt;
    }

    public void setClassificationLastUpdatedAt(LocalDateTime classificationLastUpdatedAt) {
        this.classificationLastUpdatedAt = classificationLastUpdatedAt;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    public TicketMetadata getMetadata() {
        return metadata;
    }

    public void setMetadata(TicketMetadata metadata) {
        this.metadata = metadata;
    }
}
