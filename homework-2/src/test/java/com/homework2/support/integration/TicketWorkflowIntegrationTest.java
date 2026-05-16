package com.homework2.support.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.homework2.support.TestDataFactory;
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
import java.util.LinkedHashMap;
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
class TicketWorkflowIntegrationTest {
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
    void fullTicketLifecycleWorkflow() throws Exception {
        Map<String, Object> createRequest = TestDataFactory.validTicketRequestMap(
                "Cannot access account",
                "Cannot access account in production due to security incident and password failure."
        );
        createRequest.put("customer_id", "lifecycle-001");
        createRequest.put("customer_email", "lifecycle-001@example.com");
        createRequest.put("category", "other");
        createRequest.put("priority", "low");

        UUID ticketId = createTicketAndReturnId(createRequest);

        mockMvc.perform(post("/tickets/{id}/auto-classify", ticketId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticket_id").value(ticketId.toString()))
                .andExpect(jsonPath("$.category").value("account_access"))
                .andExpect(jsonPath("$.priority").value("urgent"));

        Map<String, Object> updateRequest = TestDataFactory.validTicketRequestMap(
                "Account issue resolved",
                "The account issue was resolved and customer confirmed successful login."
        );
        updateRequest.put("customer_id", "lifecycle-001");
        updateRequest.put("customer_email", "lifecycle-001@example.com");
        updateRequest.put("category", "account_access");
        updateRequest.put("priority", "urgent");
        updateRequest.put("status", "resolved");

        mockMvc.perform(put("/tickets/{id}", ticketId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("resolved"))
                .andExpect(jsonPath("$.resolved_at").isNotEmpty());

        mockMvc.perform(get("/tickets/{id}", ticketId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ticketId.toString()))
                .andExpect(jsonPath("$.status").value("resolved"));

        mockMvc.perform(delete("/tickets/{id}", ticketId))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/tickets/{id}", ticketId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void importThenAutoClassifyWorkflow() throws Exception {
        String csv = """
                customer_id,customer_email,customer_name,subject,description,category,priority,status,resolved_at,assigned_to,tags,source,browser,device_type
                import-001,import-001@example.com,Import User,Cannot access account,Cannot access account in production down security incident and password flow is broken,other,low,new,,,login|password,web_form,Chrome,desktop
                """;

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "workflow.csv",
                "text/csv",
                csv.getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart("/tickets/import").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total_records").value(1))
                .andExpect(jsonPath("$.successful").value(1))
                .andExpect(jsonPath("$.failed").value(0));

        MvcResult listResult = mockMvc.perform(get("/tickets")
                        .param("customerId", "import-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].category").value("other"))
                .andReturn();

        JsonNode listBody = objectMapper.readTree(listResult.getResponse().getContentAsString());
        UUID importedTicketId = UUID.fromString(listBody.get("content").get(0).get("id").asText());

        mockMvc.perform(post("/tickets/{id}/auto-classify", importedTicketId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticket_id").value(importedTicketId.toString()))
                .andExpect(jsonPath("$.category").value("account_access"))
                .andExpect(jsonPath("$.priority").value("urgent"));
    }

    @Test
    void combinedCategoryAndPriorityFilteringWorkflow() throws Exception {
        Map<String, Object> match = withCategoryPriority("bug_report", "high", "filter-match-001");
        Map<String, Object> otherPriority = withCategoryPriority("bug_report", "low", "filter-low-001");
        Map<String, Object> otherCategory = withCategoryPriority("billing_question", "high", "filter-billing-001");

        createTicketAndReturnId(match);
        createTicketAndReturnId(otherPriority);
        createTicketAndReturnId(otherCategory);

        MvcResult result = mockMvc.perform(get("/tickets")
                        .param("category", "bug_report")
                        .param("priority", "high")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].category").value("bug_report"))
                .andExpect(jsonPath("$.content[0].priority").value("high"))
                .andReturn();

        JsonNode content = objectMapper.readTree(result.getResponse().getContentAsString()).get("content");
        assertThat(content.get(0).get("customer_id").asText()).isEqualTo("filter-match-001");
    }

    private Map<String, Object> withCategoryPriority(String category, String priority, String customerId) {
        Map<String, Object> request = new LinkedHashMap<>(TestDataFactory.validTicketRequestMap(
                "Workflow ticket for " + customerId,
                "Detailed workflow description for " + customerId + " with enough valid text length."
        ));
        request.put("customer_id", customerId);
        request.put("customer_email", customerId + "@example.com");
        request.put("category", category);
        request.put("priority", priority);
        return request;
    }

    private UUID createTicketAndReturnId(Map<String, Object> request) throws Exception {
        MvcResult result = mockMvc.perform(post("/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
    }
}
