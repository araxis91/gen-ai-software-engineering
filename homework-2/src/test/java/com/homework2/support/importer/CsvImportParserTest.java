package com.homework2.support.importer;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CsvImportParserTest {
    private final CsvTicketImportParser parser = new CsvTicketImportParser();

    @Test
    void supportsCsvExtensionAndContentType() {
        assertThat(parser.supports("tickets.csv", null)).isTrue();
        assertThat(parser.supports("tickets.txt", "text/csv")).isTrue();
        assertThat(parser.supports("tickets.txt", "application/json")).isFalse();
    }

    @Test
    void parseValidCsvRecordReturnsSuccess() {
        String csv = """
                customer_id,customer_email,customer_name,subject,description,category,priority,status,resolved_at,assigned_to,tags,source,browser,device_type
                cust-1,good@example.com,Good User,Login issue,Cannot access account because password reset fails,account_access,high,new,,,login|password,web_form,Chrome,desktop
                """;

        List<ImportRecord> records = parser.parse(csv.getBytes(StandardCharsets.UTF_8));

        assertThat(records).hasSize(1);
        ImportRecord record = records.getFirst();
        assertThat(record.hasParseError()).isFalse();
        assertThat(record.ticketRequest()).isNotNull();
        assertThat(record.ticketRequest().customerEmail()).isEqualTo("good@example.com");
    }

    @Test
    void parseInvalidCategoryReturnsFailureRecord() {
        String csv = """
                customer_id,customer_email,customer_name,subject,description,category,priority,status,resolved_at,assigned_to,tags,source,browser,device_type
                cust-1,good@example.com,Good User,Login issue,Cannot access account because password reset fails,invalid_category,high,new,,,login|password,web_form,Chrome,desktop
                """;

        List<ImportRecord> records = parser.parse(csv.getBytes(StandardCharsets.UTF_8));

        assertThat(records).hasSize(1);
        ImportRecord record = records.getFirst();
        assertThat(record.hasParseError()).isTrue();
        assertThat(record.parseError()).contains("Invalid category value");
    }

    @Test
    void parseInvalidDateReturnsFailureRecord() {
        String csv = """
                customer_id,customer_email,customer_name,subject,description,category,priority,status,resolved_at,assigned_to,tags,source,browser,device_type
                cust-1,good@example.com,Good User,Login issue,Cannot access account because password reset fails,account_access,high,resolved,not-a-date,,login|password,web_form,Chrome,desktop
                """;

        List<ImportRecord> records = parser.parse(csv.getBytes(StandardCharsets.UTF_8));

        assertThat(records).hasSize(1);
        ImportRecord record = records.getFirst();
        assertThat(record.hasParseError()).isTrue();
        assertThat(record.parseError()).contains("Invalid resolved_at datetime format");
    }
}
