package com.homework2.support.importer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TicketImportUtilitiesTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void parseTagsHandlesDifferentSeparatorsAndBlankValues() {
        List<String> tags = TicketImportParsingUtils.parseTags("alpha| beta, gamma ; ;");

        assertThat(tags).containsExactly("alpha", "beta", "gamma");
    }

    @Test
    void parseOptionalDateTimeParsesValidInput() {
        LocalDateTime value = TicketImportParsingUtils.parseOptionalDateTime("2026-05-16T10:00:00", "resolved_at");

        assertThat(value).isEqualTo(LocalDateTime.of(2026, 5, 16, 10, 0, 0));
    }

    @Test
    void parseOptionalDateTimeRejectsInvalidInput() {
        assertThatThrownBy(() -> TicketImportParsingUtils.parseOptionalDateTime("not-a-date", "resolved_at"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid resolved_at datetime format");
    }

    @Test
    void rootCauseMessageReturnsDeepestCauseMessage() {
        Exception nested = new RuntimeException("top", new IllegalStateException("deep-cause"));

        String message = TicketImportParsingUtils.rootCauseMessage(nested);

        assertThat(message).isEqualTo("deep-cause");
    }

    @Test
    void extractTicketNodesFailsWhenNoRecordsFound() throws Exception {
        JsonNode root = objectMapper.readTree("{\"foo\":\"bar\"}");

        assertThatThrownBy(() -> TicketImportNodeSupport.extractTicketNodes(root))
                .isInstanceOf(MalformedImportFileException.class)
                .hasMessageContaining("Could not find ticket records");
    }

    @Test
    void toImportRecordReturnsFailureForInvalidEnumValues() throws Exception {
        JsonNode node = objectMapper.readTree("""
                {
                  "customer_id": "cust-1",
                  "customer_email": "good@example.com",
                  "customer_name": "Good User",
                  "subject": "Login issue",
                  "description": "Cannot access account because password reset fails",
                  "category": "invalid-category",
                  "priority": "high",
                  "status": "new",
                  "metadata": {
                    "source": "web_form",
                    "browser": "Chrome",
                    "device_type": "desktop"
                  }
                }
                """);

        ImportRecord record = TicketImportNodeSupport.toImportRecord(node, 1);

        assertThat(record.hasParseError()).isTrue();
        assertThat(record.parseError()).contains("Invalid category value");
    }
}
