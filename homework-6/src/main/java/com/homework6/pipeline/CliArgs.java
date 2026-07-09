package com.homework6.pipeline;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/**
 * Parses {@code Integrator}'s command-line arguments: which transactions file to load,
 * what order to run the pipeline stages in, and whether to run just one stage instead
 * of a full pass. Kept separate from {@code Integrator} so the parsing logic itself is
 * unit-testable without touching the filesystem.
 */
record CliArgs(Path sampleTransactionsFile, PipelineSequence sequence, String singleStage) {

    static final String USAGE = """
            Usage: Integrator [--file=path] [--sequence=agent1,agent2,...] [--stage=agentName]

              --file=path      Transactions JSON file to load (default: sample-transactions.json)
              --sequence=...   Comma-separated agent order (default: transaction_validator,fraud_detector,compliance_checker,settlement_processor)
              --stage=name     Run exactly one named stage on whatever's already queued for it, instead of a full run

            Known agents: transaction_validator, fraud_detector, compliance_checker, settlement_processor
            """;

    private static final String FILE_FLAG = "--file=";
    private static final String SEQUENCE_FLAG = "--sequence=";
    private static final String STAGE_FLAG = "--stage=";

    static CliArgs parse(String[] args) {
        Path file = Path.of("sample-transactions.json");
        PipelineSequence sequence = PipelineSequence.defaultSequence();
        String stage = null;

        for (String arg : args) {
            if (arg.startsWith(FILE_FLAG)) {
                file = Path.of(arg.substring(FILE_FLAG.length()));
            } else if (arg.startsWith(SEQUENCE_FLAG)) {
                sequence = new PipelineSequence(parseSequence(arg.substring(SEQUENCE_FLAG.length())));
            } else if (arg.startsWith(STAGE_FLAG)) {
                stage = arg.substring(STAGE_FLAG.length()).strip();
            } else if (arg.equals("--help") || arg.equals("-h")) {
                throw new HelpRequested();
            } else {
                throw new IllegalArgumentException("Unrecognized argument '" + arg + "'.\n" + USAGE);
            }
        }

        return new CliArgs(file, sequence, stage);
    }

    private static List<String> parseSequence(String csv) {
        return Arrays.stream(csv.split(","))
                .map(String::strip)
                .filter(name -> !name.isEmpty())
                .toList();
    }

    /** Thrown when {@code --help}/{@code -h} is passed, so callers can print usage and exit 0 instead of erroring. */
    static final class HelpRequested extends RuntimeException {
    }
}
