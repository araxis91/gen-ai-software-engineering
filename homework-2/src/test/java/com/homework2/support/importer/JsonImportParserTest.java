package com.homework2.support.importer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JsonImportParserTest {
    private final JsonTicketImportParser parser = new JsonTicketImportParser(new ObjectMapper());

    @Test
    void supportsJsonExtensionAndContentType() {
        assertThat(parser.supports("tickets.json", null)).isTrue();
        assertThat(parser.supports("tickets.txt", "application/json")).isTrue();
        assertThat(parser.supports("tickets.txt", "application/xml")).isFalse();
    }

    @Test
    void parseJsonArrayReturnsRecords() {
        String json = """
                [
                  {
                    "customer_id": "cust-1",
                    "customer_email": "good@example.com",
                    "customer_name": "Good User",
                    "subject": "Login issue",
                    "description": "Cannot access account because password reset fails",
                    "category": "account_access",
                    "priority": "high",
                    "status": "new",
                    "tags": ["login", "password"],
                    "metadata": {
                      "source": "web_form",
                      "browser": "Chrome",
                      "device_type": "desktop"
                    }
                  }
                ]
                """;

        List<ImportRecord> records = parser.parse(json.getBytes(StandardCharsets.UTF_8));

        assertThat(records).hasSize(1);
        assertThat(records.getFirst().hasParseError()).isFalse();
        assertThat(records.getFirst().ticketRequest().customerId()).isEqualTo("cust-1");
    }

    @Test
    void parseJsonObjectWithTicketsArrayReturnsRecords() {
        String json = """
                {
                  "tickets": [
                    {
                      "customer_id": "cust-2",
                      "customer_email": "good2@example.com",
                      "customer_name": "Good User 2",
                      "subject": "Billing issue",
                      "description": "Need refund for duplicate charge on invoice 55",
                      "category": "billing_question",
                      "priority": "medium",
                      "status": "new",
                      "metadata": {
                        "source": "api",
                        "browser": "Safari",
                        "device_type": "mobile"
                      }
                    }
                  ]
                }
                """;

        List<ImportRecord> records = parser.parse(json.getBytes(StandardCharsets.UTF_8));

        assertThat(records).hasSize(1);
        assertThat(records.getFirst().hasParseError()).isFalse();
        assertThat(records.getFirst().ticketRequest().category().getApiValue()).isEqualTo("billing_question");
    }

    @Test
    void malformedJsonThrowsMalformedImportFileException() {
        String malformedJson = "{\"tickets\": [}";

        assertThatThrownBy(() -> parser.parse(malformedJson.getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(MalformedImportFileException.class)
                .hasMessageContaining("Malformed JSON file");
    }
}
