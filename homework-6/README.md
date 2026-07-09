# AI-Powered Multi-Agent Banking Pipeline

Created by **Dmytro Cherneha**
**Date:** 2026-07-09

## What this is

This project is a file-based, multi-agent transaction processing pipeline for banking transactions, built in Java 21. A batch of raw transactions (`sample-transactions.json`) is handed to a chain of four cooperating agents — a validator, a fraud detector, a compliance checker, and a settlement processor — that pass JSON messages to one another through `shared/` directories, the way independent services would communicate over a queue. Every transaction ends up with a single, auditable terminal record in `shared/results/`: settled, rejected (with a specific reason code), flagged for fraud review with a risk score, or put on a compliance hold.

The pipeline itself (this repository's four-stage Java system) is the _deliverable_, and it was built using a four-agent AI workflow inside Claude Code: one meta-agent wrote the specification (`specification.md`, via the `/write-spec` skill), one generated the pipeline code (using the `context7` MCP server to look up Jackson/JaCoCo/FastMCP documentation — see `research-notes.md`), one wrote the unit and integration test suite (gated by a coverage hook that blocks `git push` below 80% line coverage), and one produced this documentation.

## Agent responsibilities (pipeline agents)

- **Transaction Validator** (`TransactionValidatorAgent`) — checks required fields are present, the amount is a positive `BigDecimal`, and the currency is a valid ISO 4217 code. Rejects malformed transactions (e.g. negative amounts, unknown currencies) before anything downstream sees them.
- **Fraud Detector** (`FraudDetectorAgent`) — scores every validated transaction 0–100 across three weighted factors (high value, unusual timing, cross-border), flagging anything at or above the threshold for manual fraud review instead of letting it continue.
- **Compliance Checker** (`ComplianceCheckerAgent`) — rejects transactions to denylisted destination accounts outright, and puts large cross-border wire transfers on a manual compliance hold rather than auto-approving them.
- **Settlement Processor** (`SettlementProcessorAgent`) — the terminal stage: generates a settlement id and timestamp for everything that clears the first three stages, and is itself idempotent so a re-run never double-settles a transaction.

Each agent is a pure transform (`TransactionRecord process(TransactionRecord record)`) — none of them know what runs before or after them. Stage order is decided entirely by `PipelineSequence` and enforced by `Integrator`, which is what makes the pipeline configurable (see below).

## Architecture

```
                         sample-transactions.json
                                    │
                                    ▼
                           ┌────────────────┐
                           │   Integrator   │  (orchestrator)
                           └───────┬────────┘
                                   │ writes initial messages
                                   ▼
                          shared/input/*.json
                                   │
                    ┌──────────────┼──────────────────────┐
                    ▼              │                       │
        ┌───────────────────────┐ │                       │
        │ TransactionValidator  │ │  invalid field /       │
        │        Agent          │─┼─ negative amount ────► shared/results/ (REJECTED)
        └───────────┬───────────┘ │  invalid currency      │
                     │ VALIDATED  │                       │
                     ▼            │                       │
        ┌───────────────────────┐ │                       │
        │   FraudDetectorAgent  │─┼─ risk score ≥ 70 ────► shared/results/ (FLAGGED_FOR_REVIEW)
        └───────────┬───────────┘ │                       │
                     │ FRAUD_CLEARED                       │
                     ▼            │                       │
        ┌───────────────────────┐ │  blocked account ────► shared/results/ (REJECTED)
        │ ComplianceCheckerAgent│─┼─ large cross-border ─► shared/results/ (COMPLIANCE_HOLD)
        └───────────┬───────────┘ │  wire transfer          │
                     │ COMPLIANCE_CLEARED                   │
                     ▼            │                       │
        ┌───────────────────────┐ │                       │
        │SettlementProcessorAgent│┴──────────────────────► shared/results/ (SETTLED)
        └───────────────────────┘
                                   │
                                   ▼
                    shared/results/pipeline-summary.json
                    (counts by status + per-transaction outcomes)
```

Agents hand messages to each other exclusively through `shared/input/ → shared/processing/ → shared/output/ → shared/results/`, using atomic file writes so a reader never observes a partially-written message.

## Configuring the pipeline

The diagram above shows the *default* order, but it's just a default — `PipelineSequence` is the single source of truth for what runs after what, and it's fully configurable:

```bash
# default order (unchanged)
java -cp <classpath> com.homework6.pipeline.Integrator

# skip stages entirely — e.g. only validate and settle, no fraud/compliance checks
java -cp <classpath> com.homework6.pipeline.Integrator --sequence=transaction_validator,settlement_processor

# reorder — e.g. run compliance before fraud/validation
java -cp <classpath> com.homework6.pipeline.Integrator --sequence=compliance_checker,fraud_detector,transaction_validator,settlement_processor

# run exactly one stage on whatever's already queued for it, instead of a full pass
java -cp <classpath> com.homework6.pipeline.Integrator --stage=fraud_detector
```

This works because agents never decide what runs next themselves — see `PipelineAgent`'s contract in `agents.md`. Reordering or dropping a stage changes real outcomes (e.g. dropping `compliance_checker` means a denylisted destination account is never caught), which is expected: the configured sequence is a first-class decision, not a workaround.

Programmatically, the same thing looks like:

```java
Integrator integrator = new Integrator(sharedRoot, sampleTransactionsFile,
        PipelineSequence.of("compliance_checker", "settlement_processor"));
integrator.run();

// or drive stages one at a time, in whatever order you choose:
Integrator manual = new Integrator();
manual.seedInput();
manual.runStage("compliance_checker");
manual.runStage("settlement_processor");
```

## REST API Gateway

The file-based pipeline above is also reachable over HTTP (`specification-capstone.md` Task 2) — a Spring Boot service that runs a transaction through the same agents **synchronously** and persists the result to the same `shared/results/` files the CLI and MCP server use. All three entry points (CLI, REST API, MCP server) are interchangeable views onto one shared source of truth.

```bash
mvn spring-boot:run
# or: java -jar target/banking-pipeline-exec.jar   (built via `mvn package`)
```

| Method | Path | Behavior |
|---|---|---|
| `POST` | `/api/v1/transactions` | Submit one transaction; runs it through the pipeline synchronously and returns the terminal result. Submitting the same `transaction_id` again returns the existing result unchanged (idempotent, `200`). |
| `GET` | `/api/v1/transactions/{transactionId}` | The result for one transaction (`404` if not yet processed). |
| `GET` | `/api/v1/transactions` | Every transaction currently in `shared/results/`. |
| `GET` | `/api/v1/pipeline/summary` | The latest `pipeline-summary.json` content (`404` if nothing has been processed yet). |

Swagger UI: `http://localhost:8080/swagger-ui.html`. OpenAPI JSON: `/v3/api-docs`.

Two independent entry points into the same project, unaffected by each other:
- `com.homework6.pipeline.Integrator` — the CLI (`java -cp target/classes:... com.homework6.pipeline.Integrator`), packaged as the plain `target/banking-pipeline.jar`.
- `com.homework6.pipeline.api.PipelineApiApplication` — the REST API, packaged separately as `target/banking-pipeline-exec.jar` (Spring Boot fat jar, `classifier=exec` so it never overwrites the CLI jar).

Both share the same framework-agnostic core — `PipelineExecutor` (advances a transaction through the configured `PipelineSequence`, purely in memory) and `PipelineSummaryWriter` (regenerates `pipeline-summary.json`) have zero Spring dependency, so the CLI never pulls in Spring transitively.

## Tech stack

| Layer                     | Technology                                                                                                                 |
| ------------------------- | -------------------------------------------------------------------------------------------------------------------------- |
| Pipeline language/runtime | Java 21, Maven 3.9+                                                                                                        |
| REST API                  | Spring Boot 3.3.x (`spring-boot-starter-web`/`-validation`), springdoc-openapi (Swagger UI)                                |
| JSON messaging            | Jackson (`jackson-databind` + `jackson-datatype-jsr310`)                                                                   |
| Logging                   | SLF4J + Logback (structured, ISO-8601 UTC timestamps)                                                                      |
| Testing                   | JUnit 5, Mockito, Spring `MockMvc`/`TestRestTemplate`, JaCoCo (coverage gate + reports)                                    |
| Custom MCP server         | Python 3, [FastMCP](https://gofastmcp.com)                                                                                 |
| Library docs during dev   | `context7` MCP server                                                                                                      |
| AI coding agent           | Claude Code — custom skills (`/write-spec`, `/run-pipeline`, `/validate-transactions`) + a `PreToolUse` coverage-gate hook |

## Repository layout

- `specification.md`, `specification-capstone.md`, `agents.md` — the project specs and AI-agent guidelines (Task 1; capstone Tasks 2-3).
- `src/main/java/com/homework6/pipeline/` — the pipeline itself: `Integrator`, `PipelineExecutor`/`PipelineSummaryWriter` (shared, framework-agnostic core), `PipelineSequence` (configurable stage order), `CliArgs`, `agent/`, `model/`, `messaging/`, `audit/`, `config/`, `util/`, `cli/`, `exception/` (Task 2).
- `src/main/java/com/homework6/pipeline/api/` — the REST API Gateway: `PipelineApiApplication`, `TransactionController`, `PipelineExecutionService`, `GlobalExceptionHandler`, `PipelineBeansConfig`, `dto/` (capstone Task 2).
- `src/test/java/...` — unit tests per class + `IntegratorTest`/`PipelineApiIntegrationTest` (full-pipeline integration tests) (Task 5).
- `.claude/commands/` — `write-spec.md`, `run-pipeline.md`, `validate-transactions.md` skills; `.claude/settings.json` + `scripts/coverage-gate.sh` — the coverage-gate hook (Task 3).
- `mcp/server.py`, `.mcp.json` — the custom FastMCP server (`get_transaction_status`, `list_pipeline_results`, `pipeline://summary`) plus `context7` (Task 4).
- `research-notes.md` — documented context7 queries used while building the pipeline and MCP server.
- `docs/screenshots/` — screenshots of the spec, pipeline run, tests/coverage, skill/hook in action, and MCP usage.

See `HOWTORUN.md` for step-by-step setup and demo instructions.
