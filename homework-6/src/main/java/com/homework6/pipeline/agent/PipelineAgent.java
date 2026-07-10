package com.homework6.pipeline.agent;

import com.homework6.pipeline.model.PipelineMessage;

/**
 * Contract every pipeline stage implements. {@code process} is a pure transformation:
 * it must not perform file I/O itself — routing/persistence is the Integrator's job via
 * {@code FileMessageBus}, keeping agents independently unit-testable (agents.md).
 */
public interface PipelineAgent {

    /** Stable identifier used for {@code source_agent}/{@code target_agent} routing fields. */
    String name();

    PipelineMessage process(PipelineMessage message);
}
