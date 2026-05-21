package com.homework2.support.importer;

import java.util.List;

public interface TicketImportParser {
    boolean supports(String filename, String contentType);

    List<ImportRecord> parse(byte[] fileBytes);
}
