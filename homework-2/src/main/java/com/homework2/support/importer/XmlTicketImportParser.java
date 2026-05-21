package com.homework2.support.importer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class XmlTicketImportParser implements TicketImportParser {
    private final XmlMapper xmlMapper = new XmlMapper();

    @Override
    public boolean supports(String filename, String contentType) {
        return TicketImportParsingUtils.hasExtension(filename, ".xml")
                || TicketImportParsingUtils.contentTypeContains(contentType, "xml");
    }

    @Override
    public List<ImportRecord> parse(byte[] fileBytes) {
        try {
            JsonNode rootNode = xmlMapper.readTree(fileBytes);
            List<JsonNode> ticketNodes = TicketImportNodeSupport.extractTicketNodes(rootNode);

            List<ImportRecord> records = new ArrayList<>();
            for (int i = 0; i < ticketNodes.size(); i++) {
                records.add(TicketImportNodeSupport.toImportRecord(ticketNodes.get(i), i + 1));
            }
            return records;
        } catch (JsonProcessingException exception) {
            throw new MalformedImportFileException("Malformed XML file: " + exception.getOriginalMessage(), exception);
        } catch (Exception exception) {
            if (exception instanceof MalformedImportFileException malformedImportFileException) {
                throw malformedImportFileException;
            }
            throw new MalformedImportFileException("Malformed XML file: " + TicketImportParsingUtils.rootCauseMessage(exception), exception);
        }
    }
}
