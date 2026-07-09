package com.homework6.pipeline;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PipelineSequenceTest {

    @Test
    void defaultSequence_matchesOriginalFixedOrder() {
        PipelineSequence sequence = PipelineSequence.defaultSequence();

        assertEquals(List.of("transaction_validator", "fraud_detector", "compliance_checker", "settlement_processor"),
                sequence.agentNames());
        assertEquals("transaction_validator", sequence.first());
    }

    @Test
    void nextAfter_middleAgent_returnsFollowingAgent() {
        PipelineSequence sequence = PipelineSequence.defaultSequence();
        assertEquals("compliance_checker", sequence.nextAfter("fraud_detector").orElseThrow());
    }

    @Test
    void nextAfter_lastAgent_returnsEmpty() {
        PipelineSequence sequence = PipelineSequence.defaultSequence();
        assertTrue(sequence.nextAfter("settlement_processor").isEmpty());
    }

    @Test
    void nextAfter_unknownAgent_returnsEmpty() {
        PipelineSequence sequence = PipelineSequence.defaultSequence();
        assertTrue(sequence.nextAfter("not_a_real_agent").isEmpty());
    }

    @Test
    void of_buildsCustomOrder() {
        PipelineSequence sequence = PipelineSequence.of("compliance_checker", "fraud_detector");

        assertEquals("compliance_checker", sequence.first());
        assertEquals("fraud_detector", sequence.nextAfter("compliance_checker").orElseThrow());
        assertTrue(sequence.nextAfter("fraud_detector").isEmpty());
    }

    @Test
    void contains_agentInSequence_returnsTrue() {
        assertTrue(PipelineSequence.defaultSequence().contains("fraud_detector"));
    }

    @Test
    void contains_agentNotInSequence_returnsFalse() {
        assertFalse(PipelineSequence.of("transaction_validator").contains("settlement_processor"));
    }

    @Test
    void constructor_emptyList_throws() {
        assertThrows(IllegalArgumentException.class, () -> new PipelineSequence(List.of()));
    }

    @Test
    void constructor_duplicateAgentName_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> PipelineSequence.of("fraud_detector", "compliance_checker", "fraud_detector"));
    }

    @Test
    void constructor_subsetOfStages_isAllowed() {
        PipelineSequence sequence = PipelineSequence.of("transaction_validator", "settlement_processor");
        assertEquals(2, sequence.agentNames().size());
    }
}
