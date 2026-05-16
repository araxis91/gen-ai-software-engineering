package com.homework2.support.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.homework2.support.TestDataFactory;
import com.homework2.support.domain.ClassificationDecisionType;
import com.homework2.support.domain.Ticket;
import com.homework2.support.repository.TicketClassificationAuditRepository;
import com.homework2.support.repository.TicketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TicketApiTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private TicketRepository ticketRepository;
    @Autowired
    private TicketClassificationAuditRepository ticketClassificationAuditRepository;

    @BeforeEach
    void setUp() {
        ticketClassificationAuditRepository.deleteAll();
        ticketRepository.deleteAll();
    }

    @Test
    void createTicketReturns201AndPayload() throws Exception {
        mockMvc.perform(post("/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(TestDataFactory.validTicketRequestMap())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.customer_id").value("cust-001"))
                .andExpect(jsonPath("$.customer_email").value("customer@example.com"))
                .andExpect(jsonPath("$.status").value("new"))
                .andExpect(jsonPath("$.id").exists());
    }

    @Test
    void getTicketByIdReturns200() throws Exception {
        UUID ticketId = createTicketAndReturnId(TestDataFactory.validTicketRequestMap());

        mockMvc.perform(get("/tickets/{id}", ticketId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ticketId.toString()))
                .andExpect(jsonPath("$.subject").value("Cannot access my account"));
    }

    @Test
    void listTicketsWithFiltersReturnsMatchingItems() throws Exception {
        Map<String, Object> first = TestDataFactory.validTicketRequestMap("Account locked", "Cannot access login due to password issue.");
        Map<String, Object> second = TestDataFactory.validTicketRequestMap("Billing refund question", "I have a billing refund request for invoice #123.");
        second.put("category", "billing_question");
        second.put("priority", "low");

        createTicketAndReturnId(first);
        createTicketAndReturnId(second);

        mockMvc.perform(get("/tickets")
                        .param("category", "billing_question")
                        .param("priority", "low"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].category").value("billing_question"))
                .andExpect(jsonPath("$.content[0].priority").value("low"));
    }

    @Test
    void updateTicketWithResolvedStatusSetsResolvedAt() throws Exception {
        UUID ticketId = createTicketAndReturnId(TestDataFactory.validTicketRequestMap());
        Map<String, Object> updateRequest = TestDataFactory.validTicketRequestMap("Resolved issue", "Issue has now been resolved and verified by support.");
        updateRequest.put("status", "resolved");
        updateRequest.put("category", "technical_issue");

        mockMvc.perform(put("/tickets/{id}", ticketId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("resolved"))
                .andExpect(jsonPath("$.resolved_at").isNotEmpty());
    }

    @Test
    void deleteTicketThenGetReturns404() throws Exception {
        UUID ticketId = createTicketAndReturnId(TestDataFactory.validTicketRequestMap());

        mockMvc.perform(delete("/tickets/{id}", ticketId))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/tickets/{id}", ticketId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Ticket not found: " + ticketId));
    }

    @Test
    void createTicketWithAutoClassifyFlagRunsClassification() throws Exception {
        Map<String, Object> request = TestDataFactory.validTicketRequestMap(
                "Critical bug in production",
                "Production down due to security issue. Steps to reproduce this bug are included."
        );
        request.put("category", "other");
        request.put("priority", "low");

        MvcResult result = mockMvc.perform(post("/tickets")
                        .param("auto_classify", "true")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode response = readBody(result);
        UUID ticketId = UUID.fromString(response.get("id").asText());
        Ticket ticket = ticketRepository.findById(ticketId).orElseThrow();

        assertThat(ticket.getClassificationLastMethod()).isEqualTo(ClassificationDecisionType.AUTO_CLASSIFICATION);
        assertThat(ticket.getClassificationConfidence()).isNotNull();
        assertThat(ticket.getPriority().getApiValue()).isEqualTo("urgent");
    }

    @Test
    void autoClassifyEndpointUpdatesTicketAndReturnsResponse() throws Exception {
        Map<String, Object> request = TestDataFactory.validTicketRequestMap(
                "Invoice refund needed asap",
                "Please help with billing refund and invoice correction asap."
        );
        request.put("category", "other");
        request.put("priority", "low");

        UUID ticketId = createTicketAndReturnId(request);

        mockMvc.perform(post("/tickets/{id}/auto-classify", ticketId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticket_id").value(ticketId.toString()))
                .andExpect(jsonPath("$.category").value("billing_question"))
                .andExpect(jsonPath("$.priority").value("high"))
                .andExpect(jsonPath("$.confidence").isNumber())
                .andExpect(jsonPath("$.keywords_found").isArray());
    }

    @Test
    void importCsvEndpointReturnsSummary() throws Exception {
        String csv = """
                customer_id,customer_email,customer_name,subject,description,category,priority,status,resolved_at,assigned_to,tags,source,browser,device_type
                cust-1,good@example.com,Good User,Login issue,Cannot access account because password reset fails,account_access,high,new,,,login|password,web_form,Chrome,desktop
                cust-2,bad-email,Bad User,Billing issue,Need billing refund now,billing_question,medium,new,,,billing,web_form,Safari,mobile
                """;

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "tickets.csv",
                "text/csv",
                csv.getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart("/tickets/import").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total_records").value(2))
                .andExpect(jsonPath("$.successful").value(1))
                .andExpect(jsonPath("$.failed").value(1))
                .andExpect(jsonPath("$.errors.length()").value(1));
    }

    @Test
    void listTicketsWithInvalidEnumFilterReturns400() throws Exception {
        mockMvc.perform(get("/tickets").param("priority", "invalid-priority"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Invalid priority: invalid-priority"));
    }

    @Test
    void createTicketWithMalformedJsonReturns400StandardContract() throws Exception {
        mockMvc.perform(post("/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customer_id\":\"x\",}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Malformed request body."))
                .andExpect(jsonPath("$.path").value("/tickets"))
                .andExpect(jsonPath("$.details[0].field").value("body"));
    }

    @Test
    void createTicketWithUnsupportedMediaTypeReturns415() throws Exception {
        mockMvc.perform(post("/tickets")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("plain-text-body"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.status").value(415))
                .andExpect(jsonPath("$.error").value("Unsupported Media Type"))
                .andExpect(jsonPath("$.message").value("Unsupported media type."));
    }

    private UUID createTicketAndReturnId(Map<String, Object> request) throws Exception {
        MvcResult result = mockMvc.perform(post("/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(readBody(result).get("id").asText());
    }

    private JsonNode readBody(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
