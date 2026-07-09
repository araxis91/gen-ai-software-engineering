package com.homework6.pipeline;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CliArgsTest {

    @Test
    void parse_noArgs_usesDefaults() {
        CliArgs args = CliArgs.parse(new String[0]);

        assertEquals(Path.of("sample-transactions.json"), args.sampleTransactionsFile());
        assertEquals(PipelineSequence.defaultSequence(), args.sequence());
        assertNull(args.singleStage());
    }

    @Test
    void parse_fileFlag_overridesTransactionsFile() {
        CliArgs args = CliArgs.parse(new String[] {"--file=custom.json"});
        assertEquals(Path.of("custom.json"), args.sampleTransactionsFile());
    }

    @Test
    void parse_sequenceFlag_parsesCommaSeparatedOrder() {
        CliArgs args = CliArgs.parse(new String[] {"--sequence=compliance_checker,fraud_detector"});

        assertEquals("compliance_checker", args.sequence().first());
        assertEquals("fraud_detector", args.sequence().nextAfter("compliance_checker").orElseThrow());
    }

    @Test
    void parse_sequenceFlag_stripsWhitespaceAroundNames() {
        CliArgs args = CliArgs.parse(new String[] {"--sequence= fraud_detector , compliance_checker "});
        assertEquals("fraud_detector", args.sequence().first());
    }

    @Test
    void parse_stageFlag_setsSingleStage() {
        CliArgs args = CliArgs.parse(new String[] {"--stage=fraud_detector"});
        assertEquals("fraud_detector", args.singleStage());
    }

    @Test
    void parse_combinedFlags_allApply() {
        CliArgs args = CliArgs.parse(new String[] {
                "--file=custom.json", "--sequence=fraud_detector,compliance_checker", "--stage=fraud_detector"
        });

        assertEquals(Path.of("custom.json"), args.sampleTransactionsFile());
        assertEquals("fraud_detector", args.sequence().first());
        assertEquals("fraud_detector", args.singleStage());
    }

    @Test
    void parse_unrecognizedFlag_throwsWithUsageMessage() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> CliArgs.parse(new String[] {"--bogus=1"}));
        assertTrue(ex.getMessage().contains("Unrecognized argument"));
    }

    @Test
    void parse_helpFlag_throwsHelpRequested() {
        assertThrows(CliArgs.HelpRequested.class, () -> CliArgs.parse(new String[] {"--help"}));
    }

    @Test
    void parse_shortHelpFlag_throwsHelpRequested() {
        assertThrows(CliArgs.HelpRequested.class, () -> CliArgs.parse(new String[] {"-h"}));
    }
}
