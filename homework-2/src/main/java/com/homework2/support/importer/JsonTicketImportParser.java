package com.homework2.support.importer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class JsonTicketImportParser implements TicketImportParser {
    private final ObjectMapper objectMapper;

    public JsonTicketImportParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(String filename, String contentType) {
        return TicketImportParsingUtils.hasExtension(filename, ".json")
                || TicketImportParsingUtils.contentTypeContains(contentType, "json");
    }

    @Override
    public List<ImportRecord> parse(byte[] fileBytes) {
        try {
            JsonNode rootNode = objectMapper.readTree(fileBytes);
            List<JsonNode> ticketNodes = TicketImportNodeSupport.extractTicketNodes(rootNode);

            List<ImportRecord> records = new ArrayList<>();
            for (int i = 0; i < ticketNodes.size(); i++) {
                records.add(TicketImportNodeSupport.toImportRecord(ticketNodes.get(i), i + 1));
            }
            return records;
        } catch (JsonProcessingException exception) {
            throw new MalformedImportFileException("Malformed JSON file: " + exception.getOriginalMessage(), exception);
        } catch (Exception exception) {
            if (exception instanceof MalformedImportFileException malformedImportFileException) {
                throw malformedImportFileException;
            }
            throw new MalformedImportFileException("Malformed JSON file: " + TicketImportParsingUtils.rootCauseMessage(exception), exception);
        }
    }
}
