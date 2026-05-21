package com.homework2.support.importer;

import com.homework2.support.api.dto.TicketRequest;

public record ImportRecord(
        int recordNumber,
        TicketRequest ticketRequest,
        String parseError
) {
    public static ImportRecord success(int recordNumber, TicketRequest ticketRequest) {
        return new ImportRecord(recordNumber, ticketRequest, null);
    }

    public static ImportRecord failure(int recordNumber, String parseError) {
        return new ImportRecord(recordNumber, null, parseError);
    }

    public boolean hasParseError() {
        return parseError != null && !parseError.isBlank();
    }
}
