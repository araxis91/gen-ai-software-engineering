package com.homework2.support.repository;

import com.homework2.support.domain.TicketClassificationAudit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TicketClassificationAuditRepository extends JpaRepository<TicketClassificationAudit, UUID> {
}
