package com.homework2.support.importer;

import com.fasterxml.jackson.databind.JsonNode;
import com.homework2.support.api.dto.TicketMetadataRequest;
import com.homework2.support.api.dto.TicketRequest;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

final class TicketImportNodeSupport {
    private TicketImportNodeSupport() {
    }

    static List<JsonNode> extractTicketNodes(JsonNode rootNode) {
        if (rootNode == null || rootNode.isNull() || rootNode.isMissingNode()) {
            throw new MalformedImportFileException("Import file is empty or malformed.");
        }

        if (rootNode.isArray()) {
            return toNodeList(rootNode);
        }

        if (!rootNode.isObject()) {
            throw new MalformedImportFileException("Import file must contain an object or array of tickets.");
        }

        JsonNode directTicketsNode = firstNode(rootNode, "tickets", "ticket");
        if (directTicketsNode != null) {
            if (directTicketsNode.isArray()) {
                return toNodeList(directTicketsNode);
            }
            JsonNode nestedTicketNode = firstNode(directTicketsNode, "ticket");
            if (nestedTicketNode != null) {
                if (nestedTicketNode.isArray()) {
                    return toNodeList(nestedTicketNode);
                }
                if (nestedTicketNode.isObject()) {
                    return List.of(nestedTicketNode);
                }
            }
            if (looksLikeTicketNode(directTicketsNode)) {
                return List.of(directTicketsNode);
            }
        }

        if (looksLikeTicketNode(rootNode)) {
            return List.of(rootNode);
        }

        throw new MalformedImportFileException("Could not find ticket records in import file.");
    }

    static ImportRecord toImportRecord(JsonNode ticketNode, int recordNumber) {
        try {
            return ImportRecord.success(recordNumber, toTicketRequest(ticketNode));
        } catch (IllegalArgumentException exception) {
            return ImportRecord.failure(recordNumber, exception.getMessage());
        }
    }

    private static TicketRequest toTicketRequest(JsonNode ticketNode) {
        JsonNode metadataNode = firstNode(ticketNode, "metadata");
        JsonNode metadataDataSource = metadataNode != null ? metadataNode : ticketNode;

        TicketMetadataRequest metadataRequest = new TicketMetadataRequest(
                TicketImportParsingUtils.parseSource(text(metadataDataSource, "source")),
                text(metadataDataSource, "browser"),
                TicketImportParsingUtils.parseDeviceType(text(metadataDataSource, "device_type", "deviceType"))
        );

        return new TicketRequest(
                text(ticketNode, "customer_id", "customerId"),
                text(ticketNode, "customer_email", "customerEmail"),
                text(ticketNode, "customer_name", "customerName"),
                text(ticketNode, "subject"),
                text(ticketNode, "description"),
                TicketImportParsingUtils.parseCategory(text(ticketNode, "category")),
                TicketImportParsingUtils.parsePriority(text(ticketNode, "priority")),
                TicketImportParsingUtils.parseStatus(text(ticketNode, "status")),
                TicketImportParsingUtils.parseOptionalDateTime(text(ticketNode, "resolved_at", "resolvedAt"), "resolved_at"),
                text(ticketNode, "assigned_to", "assignedTo"),
                extractTags(ticketNode.get("tags")),
                metadataRequest
        );
    }

    private static List<String> extractTags(JsonNode tagsNode) {
        if (tagsNode == null || tagsNode.isNull() || tagsNode.isMissingNode()) {
            return List.of();
        }

        if (tagsNode.isTextual()) {
            return TicketImportParsingUtils.parseTags(tagsNode.asText());
        }

        if (tagsNode.isArray()) {
            List<String> tags = new ArrayList<>();
            for (JsonNode tagNode : tagsNode) {
                if (tagNode.isTextual()) {
                    String tag = TicketImportParsingUtils.normalizeNullable(tagNode.asText());
                    if (tag != null) {
                        tags.add(tag);
                    }
                } else if (tagNode.isObject() || tagNode.isArray()) {
                    tags.addAll(extractTags(tagNode));
                }
            }
            return tags;
        }

        if (tagsNode.isObject()) {
            JsonNode tagNode = firstNode(tagsNode, "tag", "tags");
            if (tagNode != null) {
                return extractTags(tagNode);
            }
            return List.of();
        }

        return List.of();
    }

    private static String text(JsonNode node, String... fieldNames) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }

        for (String fieldName : fieldNames) {
            JsonNode valueNode = node.get(fieldName);
            if (valueNode == null || valueNode.isNull() || valueNode.isMissingNode()) {
                continue;
            }
            String rawValue = valueNode.isValueNode() ? valueNode.asText() : valueNode.toString();
            return TicketImportParsingUtils.normalizeNullable(rawValue);
        }

        return null;
    }

    private static JsonNode firstNode(JsonNode node, String... fieldNames) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        for (String fieldName : fieldNames) {
            JsonNode value = node.get(fieldName);
            if (value != null && !value.isNull() && !value.isMissingNode()) {
                return value;
            }
        }
        return null;
    }

    private static boolean looksLikeTicketNode(JsonNode node) {
        if (node == null || !node.isObject()) {
            return false;
        }
        return node.has("customer_id")
                || node.has("customerId")
                || node.has("subject")
                || node.has("description")
                || node.has("metadata");
    }

    private static List<JsonNode> toNodeList(JsonNode arrayNode) {
        List<JsonNode> nodes = new ArrayList<>();
        Iterator<JsonNode> iterator = arrayNode.elements();
        while (iterator.hasNext()) {
            nodes.add(iterator.next());
        }
        return nodes;
    }
}
