package com.homework6.pipeline;

import com.homework6.pipeline.agent.PipelineAgent;
import com.homework6.pipeline.model.TransactionRecord;

import java.util.Map;

/**
 * Framework-agnostic core that advances a {@link TransactionRecord} through pipeline
 * agents, entirely in memory (no file I/O, no Spring dependency). Shared by both entry
 * points into this project:
 *
 * <ul>
 *   <li>{@link Integrator} (the file-based CLI), which persists intermediate state to
 *       {@code shared/} between each single-stage call via {@link #runStage}; and
 *   <li>the synchronous REST API ({@code com.homework6.pipeline.api}), which runs a
 *       transaction to completion in one call via {@link #runToCompletion}, persisting
 *       only the final result.
 * </ul>
 *
 * Must stay free of any Spring import so the CLI never pulls in a Spring dependency
 * transitively (see agents.md: "What the Agent Must Not Do").
 */
public final class PipelineExecutor {

    /** Runs exactly one named agent over {@code record}, validating the name against {@code agentsByName}. */
    public TransactionRecord runStage(TransactionRecord record, String agentName, Map<String, PipelineAgent> agentsByName) {
        PipelineAgent agent = agentsByName.get(agentName);
        if (agent == null) {
            throw new IllegalArgumentException("Unknown pipeline agent '" + agentName
                    + "'. Known agents: " + agentsByName.keySet());
        }
        return agent.process(record);
    }

    /**
     * Runs {@code record} through every stage of {@code sequence}, starting from its
     * first agent, stopping as soon as the result is terminal or there is no next agent
     * configured. Performs no file I/O -- the caller decides what (if anything) to persist.
     */
    public TransactionRecord runToCompletion(TransactionRecord record, PipelineSequence sequence,
                                              Map<String, PipelineAgent> agentsByName) {
        TransactionRecord current = record;
        String agentName = sequence.first();

        while (agentName != null) {
            current = runStage(current, agentName, agentsByName);
            if (current.state().status().isTerminal()) {
                break;
            }
            agentName = sequence.nextAfter(agentName).orElse(null);
        }
        return current;
    }
}
