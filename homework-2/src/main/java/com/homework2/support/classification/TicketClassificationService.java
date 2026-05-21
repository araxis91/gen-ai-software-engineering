package com.homework2.support.classification;

import com.homework2.support.api.dto.TicketAutoClassificationResponse;
import com.homework2.support.domain.Category;
import com.homework2.support.domain.ClassificationDecisionType;
import com.homework2.support.domain.Priority;
import com.homework2.support.domain.Ticket;
import com.homework2.support.domain.TicketClassificationAudit;
import com.homework2.support.repository.TicketClassificationAuditRepository;
import com.homework2.support.repository.TicketRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class TicketClassificationService {
    private static final Logger logger = LoggerFactory.getLogger(TicketClassificationService.class);

    private static final List<String> URGENT_PRIORITY_KEYWORDS = List.of(
            "can't access", "can’t access", "cannot access", "critical", "production down", "security"
    );
    private static final List<String> HIGH_PRIORITY_KEYWORDS = List.of("important", "blocking", "asap");
    private static final List<String> LOW_PRIORITY_KEYWORDS = List.of("minor", "cosmetic", "suggestion");

    private static final Map<Category, List<String>> CATEGORY_KEYWORDS = Map.of(
            Category.ACCOUNT_ACCESS, List.of("login", "log in", "password", "2fa", "two-factor", "two factor", "can't access", "cannot access", "authentication"),
            Category.TECHNICAL_ISSUE, List.of("error", "errors", "exception", "crash", "crashes", "technical", "timeout", "failed", "not working"),
            Category.BILLING_QUESTION, List.of("billing", "payment", "invoice", "refund", "charged", "charge", "subscription", "receipt"),
            Category.FEATURE_REQUEST, List.of("feature request", "enhancement", "improvement", "suggestion", "would like", "please add"),
            Category.BUG_REPORT, List.of("bug", "defect", "reproduce", "reproduction steps", "steps to reproduce")
    );

    private static final List<Category> CATEGORY_PRIORITY_ORDER = List.of(
            Category.ACCOUNT_ACCESS,
            Category.BUG_REPORT,
            Category.BILLING_QUESTION,
            Category.TECHNICAL_ISSUE,
            Category.FEATURE_REQUEST
    );

    private final TicketRepository ticketRepository;
    private final TicketClassificationAuditRepository ticketClassificationAuditRepository;

    public TicketClassificationService(
            TicketRepository ticketRepository,
            TicketClassificationAuditRepository ticketClassificationAuditRepository
    ) {
        this.ticketRepository = ticketRepository;
        this.ticketClassificationAuditRepository = ticketClassificationAuditRepository;
    }

    @Transactional
    public TicketAutoClassificationResponse autoClassifyTicket(UUID ticketId) {
        Ticket ticket = findTicketOrThrow(ticketId);
        return autoClassifyTicket(ticket);
    }

    @Transactional
    public TicketAutoClassificationResponse autoClassifyTicket(Ticket ticket) {
        Category previousCategory = ticket.getCategory();
        Priority previousPriority = ticket.getPriority();

        ClassificationComputationResult result = classify(ticket.getSubject(), ticket.getDescription());

        ticket.setCategory(result.category());
        ticket.setPriority(result.priority());
        ticket.setClassificationConfidence(result.confidence());
        ticket.setClassificationReasoning(result.reasoning());
        ticket.setClassificationKeywords(serializeKeywords(result.keywordsFound()));
        ticket.setClassificationLastMethod(ClassificationDecisionType.AUTO_CLASSIFICATION);
        ticket.setClassificationLastUpdatedAt(LocalDateTime.now());

        Ticket savedTicket = ticketRepository.save(ticket);
        writeAudit(
                savedTicket.getId(),
                ClassificationDecisionType.AUTO_CLASSIFICATION,
                previousCategory,
                savedTicket.getCategory(),
                previousPriority,
                savedTicket.getPriority(),
                result.confidence(),
                result.reasoning(),
                result.keywordsFound()
        );

        logger.info(
                "Auto-classification decision ticketId={} previousCategory={} newCategory={} previousPriority={} newPriority={} confidence={} keywords={}",
                savedTicket.getId(),
                previousCategory,
                savedTicket.getCategory(),
                previousPriority,
                savedTicket.getPriority(),
                result.confidence(),
                result.keywordsFound()
        );

        return new TicketAutoClassificationResponse(
                savedTicket.getId(),
                savedTicket.getCategory(),
                savedTicket.getPriority(),
                result.confidence(),
                result.reasoning(),
                result.keywordsFound()
        );
    }

    @Transactional
    public void recordManualOverride(Ticket ticket, Category previousCategory, Priority previousPriority) {
        if (previousCategory == ticket.getCategory() && previousPriority == ticket.getPriority()) {
            return;
        }

        String reasoning = "Manual override via PUT /tickets/{id}.";
        ticket.setClassificationConfidence(null);
        ticket.setClassificationReasoning(reasoning);
        ticket.setClassificationKeywords(null);
        ticket.setClassificationLastMethod(ClassificationDecisionType.MANUAL_OVERRIDE);
        ticket.setClassificationLastUpdatedAt(LocalDateTime.now());

        writeAudit(
                ticket.getId(),
                ClassificationDecisionType.MANUAL_OVERRIDE,
                previousCategory,
                ticket.getCategory(),
                previousPriority,
                ticket.getPriority(),
                null,
                reasoning,
                List.of()
        );

        logger.info(
                "Manual classification override ticketId={} previousCategory={} newCategory={} previousPriority={} newPriority={}",
                ticket.getId(),
                previousCategory,
                ticket.getCategory(),
                previousPriority,
                ticket.getPriority()
        );
    }

    private ClassificationComputationResult classify(String subject, String description) {
        String text = normalizeForMatching(subject, description);

        Map<Category, List<String>> categoryMatches = new EnumMap<>(Category.class);
        for (Category category : CATEGORY_PRIORITY_ORDER) {
            List<String> matchedKeywords = findMatchedKeywords(text, CATEGORY_KEYWORDS.getOrDefault(category, List.of()));
            if (!matchedKeywords.isEmpty()) {
                categoryMatches.put(category, matchedKeywords);
            }
        }

        Category category = resolveCategory(categoryMatches);
        List<String> categoryKeywords = categoryMatches.getOrDefault(category, List.of());

        PriorityDecision priorityDecision = resolvePriority(text);
        Set<String> allKeywords = new LinkedHashSet<>();
        allKeywords.addAll(categoryKeywords);
        allKeywords.addAll(priorityDecision.matchedKeywords());

        double confidence = calculateConfidence(category, categoryMatches, categoryKeywords.size(), priorityDecision.matchedKeywords().size(), priorityDecision.priority());
        String reasoning = buildReasoning(category, categoryKeywords, priorityDecision.priority(), priorityDecision.matchedKeywords());

        return new ClassificationComputationResult(
                category,
                priorityDecision.priority(),
                confidence,
                reasoning,
                List.copyOf(allKeywords)
        );
    }

    private Category resolveCategory(Map<Category, List<String>> categoryMatches) {
        return CATEGORY_PRIORITY_ORDER.stream()
                .max(Comparator.comparingInt(category -> categoryMatches.getOrDefault(category, List.of()).size()))
                .filter(category -> !categoryMatches.getOrDefault(category, List.of()).isEmpty())
                .orElse(Category.OTHER);
    }

    private PriorityDecision resolvePriority(String text) {
        List<String> urgentMatches = findMatchedKeywords(text, URGENT_PRIORITY_KEYWORDS);
        if (!urgentMatches.isEmpty()) {
            return new PriorityDecision(Priority.URGENT, urgentMatches);
        }

        List<String> highMatches = findMatchedKeywords(text, HIGH_PRIORITY_KEYWORDS);
        if (!highMatches.isEmpty()) {
            return new PriorityDecision(Priority.HIGH, highMatches);
        }

        List<String> lowMatches = findMatchedKeywords(text, LOW_PRIORITY_KEYWORDS);
        if (!lowMatches.isEmpty()) {
            return new PriorityDecision(Priority.LOW, lowMatches);
        }

        return new PriorityDecision(Priority.MEDIUM, List.of());
    }

    private List<String> findMatchedKeywords(String text, List<String> keywords) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        List<String> matches = new ArrayList<>();
        for (String keyword : keywords) {
            String normalizedKeyword = normalize(keyword);
            if (!normalizedKeyword.isBlank() && text.contains(normalizedKeyword)) {
                matches.add(keyword);
            }
        }
        return matches;
    }

    private double calculateConfidence(
            Category category,
            Map<Category, List<String>> categoryMatches,
            int categorySignalCount,
            int prioritySignalCount,
            Priority priority
    ) {
        if (category == Category.OTHER && prioritySignalCount == 0) {
            return 0.35;
        }

        int secondBestCategorySignalCount = categoryMatches.entrySet().stream()
                .filter(entry -> entry.getKey() != category)
                .mapToInt(entry -> entry.getValue().size())
                .max()
                .orElse(0);

        double confidence = category == Category.OTHER ? 0.45 : 0.55;
        confidence += Math.min(0.24, categorySignalCount * 0.08);
        confidence += Math.min(0.14, prioritySignalCount * 0.07);
        if (categorySignalCount > secondBestCategorySignalCount) {
            confidence += 0.05;
        }
        if (priority == Priority.URGENT) {
            confidence += 0.04;
        }

        return roundToTwoDecimals(Math.min(0.97, confidence));
    }

    private String buildReasoning(
            Category category,
            List<String> categoryKeywords,
            Priority priority,
            List<String> priorityKeywords
    ) {
        String categoryReason = categoryKeywords.isEmpty()
                ? "no strong category-specific keywords were found"
                : "matched category keywords " + categoryKeywords;

        String priorityReason = priorityKeywords.isEmpty()
                ? "no explicit priority keywords were found (defaulted to medium)"
                : "matched priority keywords " + priorityKeywords;

        return "Category '" + category.getApiValue() + "' selected because " + categoryReason
                + "; priority '" + priority.getApiValue() + "' selected because " + priorityReason + ".";
    }

    private Ticket findTicketOrThrow(UUID ticketId) {
        return ticketRepository.findById(ticketId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Ticket not found: " + ticketId));
    }

    private void writeAudit(
            UUID ticketId,
            ClassificationDecisionType decisionType,
            Category previousCategory,
            Category newCategory,
            Priority previousPriority,
            Priority newPriority,
            Double confidence,
            String reasoning,
            List<String> keywords
    ) {
        TicketClassificationAudit audit = new TicketClassificationAudit();
        audit.setTicketId(ticketId);
        audit.setDecisionType(decisionType);
        audit.setPreviousCategory(previousCategory);
        audit.setNewCategory(newCategory);
        audit.setPreviousPriority(previousPriority);
        audit.setNewPriority(newPriority);
        audit.setConfidence(confidence);
        audit.setReasoning(reasoning);
        audit.setKeywords(serializeKeywords(keywords));
        ticketClassificationAuditRepository.save(audit);
    }

    private String normalizeForMatching(String subject, String description) {
        return normalize((subject == null ? "" : subject) + " " + (description == null ? "" : description));
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private String serializeKeywords(List<String> keywords) {
        if (keywords == null || keywords.isEmpty()) {
            return null;
        }
        return String.join(", ", keywords);
    }

    private double roundToTwoDecimals(double value) {
        return Math.round(value * 100.0d) / 100.0d;
    }

    private record PriorityDecision(Priority priority, List<String> matchedKeywords) {
    }

    private record ClassificationComputationResult(
            Category category,
            Priority priority,
            double confidence,
            String reasoning,
            List<String> keywordsFound
    ) {
    }
}
