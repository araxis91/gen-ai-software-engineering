package com.homework6.pipeline.audit;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuditLoggerTest {

    private final AuditLogger auditLogger = new AuditLogger();
    private ListAppender<ILoggingEvent> appender;
    private Logger auditLogbackLogger;

    @BeforeEach
    void attachAppender() {
        auditLogbackLogger = (Logger) LoggerFactory.getLogger("AUDIT");
        appender = new ListAppender<>();
        appender.start();
        auditLogbackLogger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        auditLogbackLogger.detachAppender(appender);
    }

    @Test
    void record_writesOneEntryWithAgentTransactionIdAndOutcome() {
        auditLogger.record("transaction_validator", "TXN001", "VALIDATED");

        assertEquals(1, appender.list.size());
        String message = appender.list.get(0).getFormattedMessage();
        assertTrue(message.contains("agent=transaction_validator"));
        assertTrue(message.contains("transaction_id=TXN001"));
        assertTrue(message.contains("outcome=VALIDATED"));
    }

    @Test
    void recordWithAccounts_masksAccountNumbers_neverLogsRawValues() {
        auditLogger.recordWithAccounts("compliance_checker", "TXN003", "REJECTED:BLOCKED_DESTINATION_ACCOUNT",
                "ACC-1003", "ACC-9999");

        String message = appender.list.get(0).getFormattedMessage();
        assertTrue(message.contains("ACC-****1003"));
        assertTrue(message.contains("ACC-****9999"));
        assertFalse(message.contains("source_account=ACC-1003"));
        assertFalse(message.contains("destination_account=ACC-9999"));
    }
}
