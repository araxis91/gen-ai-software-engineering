package com.homework6.pipeline.agent;

import com.homework6.pipeline.model.TransactionRecord;

/**
 * Contract every pipeline stage implements. {@code process} is a pure transformation
 * from one {@link TransactionRecord} state to the next: it must not perform file I/O,
 * and it must not know what agent (if any) runs next. Routing between stages — which
 * agent runs after which — is decided entirely by the orchestrator (see
 * {@code com.homework6.pipeline.PipelineSequence}), not by the agent itself. This is
 * what makes it possible to call agents individually, in any configured order.
 */
public interface PipelineAgent {

    /** Stable identifier used for {@code source_agent}/{@code target_agent} routing fields. */
    String name();

    TransactionRecord process(TransactionRecord record);
}
