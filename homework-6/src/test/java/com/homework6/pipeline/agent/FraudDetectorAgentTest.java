package com.homework6.pipeline.agent;

import com.homework6.pipeline.audit.AuditLogger;
import com.homework6.pipeline.model.Transaction;
import com.homework6.pipeline.model.TransactionRecord;
import com.homework6.pipeline.model.TransactionStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FraudDetectorAgentTest {

    private final FraudDetectorAgent agent = new FraudDetectorAgent(new AuditLogger());

    private Transaction transaction(String amount, String currency, String timestamp, String country) {
        return new Transaction("TXN-TEST", OffsetDateTime.parse(timestamp), "ACC-1001", "ACC-2001",
                new BigDecimal(amount), currency, "transfer", "test",
                Map.of("channel", "online", "country", country));
    }

    private TransactionRecord process(Transaction transaction) {
        TransactionRecord validated = TransactionRecord.received(transaction)
                .withState(TransactionRecord.received(transaction).state().validated());
        return agent.process(validated);
    }

    @Test
    void process_highValueAloneMeetsFlagThreshold_flagsForReview() {
        Transaction tx = transaction("25000.00", "USD", "2026-03-16T09:15:00Z", "US");

        TransactionRecord result = process(tx);

        assertEquals(TransactionStatus.FLAGGED_FOR_REVIEW, result.state().status());
        assertEquals(70, result.state().riskScore());
        assertTrue(result.state().riskFactors().contains("high_value"));
        assertEquals("FRAUD_RISK_THRESHOLD_EXCEEDED", result.state().reasonCode());
    }

    @Test
    void process_lowValueDuringBusinessHoursDomestic_clearsWithZeroScore() {
        Transaction tx = transaction("1500.00", "USD", "2026-03-16T09:00:00Z", "US");

        TransactionRecord result = process(tx);

        assertEquals(TransactionStatus.FRAUD_CLEARED, result.state().status());
        assertEquals(0, result.state().riskScore());
    }

    @Test
    void process_unusualTimingAndCrossBorderCombinedBelowThreshold_clearsWithBothFactorsRecorded() {
        Transaction tx = transaction("500.00", "EUR", "2026-03-16T02:47:00Z", "DE");

        TransactionRecord result = process(tx);

        assertEquals(TransactionStatus.FRAUD_CLEARED, result.state().status());
        assertEquals(40, result.state().riskScore());
        assertTrue(result.state().riskFactors().contains("unusual_timing"));
        assertTrue(result.state().riskFactors().contains("cross_border"));
    }

    @Test
    void isHighValue_amountAboveThreshold_returnsTrue() {
        assertTrue(agent.isHighValue(transaction("10000.01", "USD", "2026-03-16T09:00:00Z", "US")));
    }

    @Test
    void isHighValue_amountAtThreshold_returnsFalse() {
        assertFalse(agent.isHighValue(transaction("10000.00", "USD", "2026-03-16T09:00:00Z", "US")));
    }

    @Test
    void isUnusualTiming_beforeBusinessHours_returnsTrue() {
        assertTrue(agent.isUnusualTiming(transaction("100.00", "USD", "2026-03-16T02:47:00Z", "US")));
    }

    @Test
    void isUnusualTiming_afterBusinessHours_returnsTrue() {
        assertTrue(agent.isUnusualTiming(transaction("100.00", "USD", "2026-03-16T23:00:00Z", "US")));
    }

    @Test
    void isUnusualTiming_duringBusinessHours_returnsFalse() {
        assertFalse(agent.isUnusualTiming(transaction("100.00", "USD", "2026-03-16T09:00:00Z", "US")));
    }

    @Test
    void isCrossBorder_foreignCountry_returnsTrue() {
        assertTrue(agent.isCrossBorder(transaction("100.00", "EUR", "2026-03-16T09:00:00Z", "DE")));
    }

    @Test
    void isCrossBorder_homeCountry_returnsFalse() {
        assertFalse(agent.isCrossBorder(transaction("100.00", "USD", "2026-03-16T09:00:00Z", "US")));
    }
}
