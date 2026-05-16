package com.homework2.support.importer;

import com.homework2.support.api.dto.TicketMetadataRequest;
import com.homework2.support.api.dto.TicketRequest;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Component
public class CsvTicketImportParser implements TicketImportParser {
    @Override
    public boolean supports(String filename, String contentType) {
        return TicketImportParsingUtils.hasExtension(filename, ".csv")
                || TicketImportParsingUtils.contentTypeContains(contentType, "csv");
    }

    @Override
    public List<ImportRecord> parse(byte[] fileBytes) {
        CSVFormat csvFormat = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreSurroundingSpaces(true)
                .setTrim(true)
                .build();

        try (Reader reader = new InputStreamReader(new ByteArrayInputStream(fileBytes), StandardCharsets.UTF_8);
             CSVParser parser = csvFormat.parse(reader)) {
            List<ImportRecord> records = new ArrayList<>();

            for (CSVRecord csvRecord : parser) {
                int recordNumber = Math.toIntExact(csvRecord.getRecordNumber());
                try {
                    records.add(ImportRecord.success(recordNumber, toTicketRequest(csvRecord)));
                } catch (IllegalArgumentException exception) {
                    records.add(ImportRecord.failure(recordNumber, exception.getMessage()));
                }
            }

            return records;
        } catch (IOException exception) {
            throw new MalformedImportFileException("Malformed CSV file: " + TicketImportParsingUtils.rootCauseMessage(exception), exception);
        }
    }

    private TicketRequest toTicketRequest(CSVRecord record) {
        TicketMetadataRequest metadataRequest = new TicketMetadataRequest(
                TicketImportParsingUtils.parseSource(value(record, "source", "metadata_source")),
                value(record, "browser", "metadata_browser"),
                TicketImportParsingUtils.parseDeviceType(value(record, "device_type", "metadata_device_type", "deviceType"))
        );

        return new TicketRequest(
                value(record, "customer_id", "customerId"),
                value(record, "customer_email", "customerEmail"),
                value(record, "customer_name", "customerName"),
                value(record, "subject"),
                value(record, "description"),
                TicketImportParsingUtils.parseCategory(value(record, "category")),
                TicketImportParsingUtils.parsePriority(value(record, "priority")),
                TicketImportParsingUtils.parseStatus(value(record, "status")),
                TicketImportParsingUtils.parseOptionalDateTime(value(record, "resolved_at", "resolvedAt"), "resolved_at"),
                value(record, "assigned_to", "assignedTo"),
                TicketImportParsingUtils.parseTags(value(record, "tags")),
                metadataRequest
        );
    }

    private String value(CSVRecord record, String... headers) {
        for (String header : headers) {
            if (record.isMapped(header) && record.isSet(header)) {
                return TicketImportParsingUtils.normalizeNullable(record.get(header));
            }
        }
        return null;
    }
}
