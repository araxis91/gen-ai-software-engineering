package com.homework6.pipeline.api;

import com.homework6.pipeline.PipelineSummaryWriter;
import com.homework6.pipeline.api.dto.PipelineSummaryResponse;
import com.homework6.pipeline.api.dto.SubmitTransactionRequest;
import com.homework6.pipeline.api.dto.TransactionResultResponse;
import com.homework6.pipeline.exception.TransactionNotFoundException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Wraps the file-based banking pipeline behind synchronous HTTP endpoints. Every
 * endpoint reads/writes the same {@code shared/results/} files the CLI and MCP server
 * already use -- this is a new entry point into the same agents, not a bypass of any
 * hard rule in agents.md (money/PII/audit/idempotency rules all still apply).
 */
@RestController
@RequestMapping("/api/v1")
public class TransactionController {

    private final PipelineExecutionService service;

    public TransactionController(PipelineExecutionService service) {
        this.service = service;
    }

    @Operation(summary = "Submit a transaction and run it through the pipeline synchronously")
    @ApiResponse(responseCode = "200", description = "Terminal (or already-existing) result for this transaction")
    @PostMapping("/transactions")
    public ResponseEntity<TransactionResultResponse> submit(@Valid @RequestBody SubmitTransactionRequest request) {
        var record = service.submit(request.toTransaction());
        return ResponseEntity.ok(TransactionResultResponse.from(record));
    }

    @Operation(summary = "Get the result for a previously processed transaction")
    @ApiResponse(responseCode = "200", description = "Terminal result found")
    @ApiResponse(responseCode = "404", description = "No result exists yet for this transaction id")
    @GetMapping("/transactions/{transactionId}")
    public ResponseEntity<TransactionResultResponse> getById(@PathVariable("transactionId") String transactionId) {
        var record = service.findById(transactionId)
                .orElseThrow(() -> TransactionNotFoundException.forTransactionId(transactionId));
        return ResponseEntity.ok(TransactionResultResponse.from(record));
    }

    @Operation(summary = "List every transaction currently in shared/results/")
    @ApiResponse(responseCode = "200", description = "Possibly empty list")
    @GetMapping("/transactions")
    public ResponseEntity<List<TransactionResultResponse>> listAll() {
        return ResponseEntity.ok(service.listAll().stream().map(TransactionResultResponse::from).toList());
    }

    @Operation(summary = "Get the latest pipeline-summary.json content")
    @ApiResponse(responseCode = "200", description = "Summary found")
    @ApiResponse(responseCode = "404", description = "No pipeline run has produced a summary yet")
    @GetMapping("/pipeline/summary")
    public ResponseEntity<PipelineSummaryResponse> getSummary() {
        PipelineSummaryWriter.PipelineSummary summary = service.latestSummary()
                .orElseThrow(TransactionNotFoundException::noSummaryAvailableYet);
        return ResponseEntity.ok(toResponse(summary));
    }

    private PipelineSummaryResponse toResponse(PipelineSummaryWriter.PipelineSummary summary) {
        return new PipelineSummaryResponse(
                summary.generatedAt(),
                summary.totalTransactions(),
                summary.resultsWritten(),
                summary.agentSequence(),
                summary.countsByStatus(),
                summary.outcomes().stream()
                        .map(o -> new PipelineSummaryResponse.TransactionOutcomeSummary(
                                o.transactionId(), o.status(), o.reasonCode(), o.riskScore()))
                        .toList());
    }
}
