package com.homework2.support.performance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.homework2.support.TestDataFactory;
import com.homework2.support.domain.Category;
import com.homework2.support.domain.DeviceType;
import com.homework2.support.domain.Priority;
import com.homework2.support.domain.Ticket;
import com.homework2.support.domain.TicketMetadata;
import com.homework2.support.domain.TicketSource;
import com.homework2.support.domain.TicketStatus;
import com.homework2.support.repository.TicketClassificationAuditRepository;
import com.homework2.support.repository.TicketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TicketPerformanceTest {
    private static final int CONCURRENT_REQUESTS = 25;
    private static final Duration CONCURRENT_CREATE_THRESHOLD = Duration.ofSeconds(12);
    private static final Duration LIST_THRESHOLD = Duration.ofSeconds(3);

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
    void handlesTwentyPlusConcurrentCreateRequestsWithinThreshold() throws Exception {
        ExecutorService executorService = Executors.newFixedThreadPool(CONCURRENT_REQUESTS);
        CountDownLatch readyLatch = new CountDownLatch(CONCURRENT_REQUESTS);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(CONCURRENT_REQUESTS);
        AtomicInteger successCount = new AtomicInteger();
        ConcurrentLinkedQueue<String> errors = new ConcurrentLinkedQueue<>();

        for (int index = 0; index < CONCURRENT_REQUESTS; index++) {
            final int requestIndex = index;
            executorService.submit(() -> {
                readyLatch.countDown();
                try {
                    if (!startLatch.await(5, TimeUnit.SECONDS)) {
                        errors.add("Request " + requestIndex + " timed out waiting to start.");
                        return;
                    }

                    Map<String, Object> request = buildConcurrentRequest(requestIndex);
                    int responseStatus = mockMvc.perform(post("/tickets")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(request)))
                            .andReturn()
                            .getResponse()
                            .getStatus();

                    if (responseStatus == 201) {
                        successCount.incrementAndGet();
                    } else {
                        errors.add("Request " + requestIndex + " returned status " + responseStatus);
                    }
                } catch (Exception exception) {
                    errors.add("Request " + requestIndex + " failed: " + exception.getMessage());
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        assertThat(readyLatch.await(10, TimeUnit.SECONDS)).isTrue();

        Instant start = Instant.now();
        startLatch.countDown();
        assertThat(doneLatch.await(20, TimeUnit.SECONDS)).isTrue();
        Duration duration = Duration.between(start, Instant.now());

        executorService.shutdown();
        assertThat(executorService.awaitTermination(5, TimeUnit.SECONDS)).isTrue();

        assertThat(errors).isEmpty();
        assertThat(successCount.get()).isEqualTo(CONCURRENT_REQUESTS);
        assertThat(ticketRepository.count()).isEqualTo(CONCURRENT_REQUESTS);
        assertThat(duration).isLessThan(CONCURRENT_CREATE_THRESHOLD);
    }

    @Test
    void listEndpointRespondsWithinStableThreshold() throws Exception {
        ticketRepository.saveAll(buildSeedTickets(160));

        Instant start = Instant.now();
        mockMvc.perform(get("/tickets")
                        .param("size", "50")
                        .param("page", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(50));
        Duration duration = Duration.between(start, Instant.now());

        assertThat(duration).isLessThan(LIST_THRESHOLD);
    }

    private Map<String, Object> buildConcurrentRequest(int index) {
        Map<String, Object> request = TestDataFactory.validTicketRequestMap(
                "Concurrent request " + index,
                "Concurrent load description " + index + " includes enough text for valid ticket creation."
        );
        request.put("customer_id", "concurrent-" + index);
        request.put("customer_email", "concurrent-" + index + "@example.com");
        return request;
    }

    private List<Ticket> buildSeedTickets(int count) {
        List<Ticket> tickets = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            Ticket ticket = new Ticket();
            ticket.setCustomerId("seed-" + index);
            ticket.setCustomerEmail("seed-" + index + "@example.com");
            ticket.setCustomerName("Seed User " + index);
            ticket.setSubject("Seed subject " + index);
            ticket.setDescription("Seed description " + index + " includes enough characters for validation.");
            ticket.setCategory(index % 2 == 0 ? Category.BUG_REPORT : Category.BILLING_QUESTION);
            ticket.setPriority(index % 3 == 0 ? Priority.HIGH : Priority.MEDIUM);
            ticket.setStatus(TicketStatus.NEW);
            ticket.setTags(List.of("seed", "perf"));
            ticket.setMetadata(new TicketMetadata(TicketSource.WEB_FORM, "Chrome", DeviceType.DESKTOP));
            tickets.add(ticket);
        }
        return tickets;
    }
}
