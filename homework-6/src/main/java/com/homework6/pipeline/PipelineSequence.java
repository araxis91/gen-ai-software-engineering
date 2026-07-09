package com.homework6.pipeline;

import com.homework6.pipeline.agent.ComplianceCheckerAgent;
import com.homework6.pipeline.agent.FraudDetectorAgent;
import com.homework6.pipeline.agent.SettlementProcessorAgent;
import com.homework6.pipeline.agent.TransactionValidatorAgent;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The single source of truth for pipeline wiring: an ordered list of agent names.
 * Agents themselves never decide what runs after them (see {@code PipelineAgent}
 * javadoc) — the {@code Integrator} asks this class "what comes after X?" and routes
 * accordingly. Configuring a different order, a subset of stages, or running one stage
 * at a time is entirely a matter of constructing a different sequence.
 */
public record PipelineSequence(List<String> agentNames) {

    /** The pipeline's original fixed order, kept as the default for backward compatibility. */
    public static final List<String> DEFAULT_ORDER = List.of(
            TransactionValidatorAgent.NAME,
            FraudDetectorAgent.NAME,
            ComplianceCheckerAgent.NAME,
            SettlementProcessorAgent.NAME);

    public PipelineSequence {
        agentNames = List.copyOf(agentNames);
        if (agentNames.isEmpty()) {
            throw new IllegalArgumentException("Pipeline sequence must contain at least one agent");
        }
        Set<String> seen = new LinkedHashSet<>();
        for (String name : agentNames) {
            if (!seen.add(name)) {
                throw new IllegalArgumentException("Pipeline sequence contains a duplicate agent: " + name);
            }
        }
    }

    public static PipelineSequence defaultSequence() {
        return new PipelineSequence(DEFAULT_ORDER);
    }

    public static PipelineSequence of(String... agentNames) {
        return new PipelineSequence(List.of(agentNames));
    }

    public String first() {
        return agentNames.get(0);
    }

    /** @return the agent that should run after {@code agentName}, or empty if it's last (or not found). */
    public Optional<String> nextAfter(String agentName) {
        int index = agentNames.indexOf(agentName);
        if (index < 0 || index == agentNames.size() - 1) {
            return Optional.empty();
        }
        return Optional.of(agentNames.get(index + 1));
    }

    public boolean contains(String agentName) {
        return agentNames.contains(agentName);
    }
}
