# AI-Powered Multi-Agent Banking Pipeline Specification

> Ingest the information from this file, implement the Low-Level Tasks, and generate the code that will satisfy the High and Mid-Level Objectives.

## High-Level Objective

- Build a file-based, multi-agent transaction processing pipeline in Java 21 that validates, risk-scores, compliance-checks, and settles banking transactions, writing an auditable, machine-readable outcome for every transaction to `shared/results/`.

## Mid-Level Objectives

1. Every transaction in `sample-transactions.json` flows through the full agent chain — **Transaction Validator → Fraud Detector → Compliance Checker → Settlement Processor** — and a terminal record for each `transaction_id` lands in `shared/results/`.
2. Transactions with `amount` above **$10,000** (in the transaction's own currency) are flagged for fraud review with a numeric risk score (0–100) and a list of the specific risk factors that contributed to the score (high value, unusual timing, cross-border).
3. Transactions that fail validation, compliance, or exceed the fraud risk threshold are written to `shared/results/` with `status: "REJECTED"`, a machine-readable `reason_code`, and a human-readable `reason` — never left in a partial or ambiguous state in `shared/processing/`.
4. Every agent operation (received, validated, scored, checked, settled, rejected) produces a structured JSON audit log entry with an ISO 8601 UTC timestamp, agent name, `transaction_id`, and outcome; account numbers and account holder names are masked wherever they would otherwise appear in logs.
5. The pipeline achieves **≥ 90%** unit test coverage across all agent and utility classes, with tests isolated from the real `shared/` directories (each test uses a temp directory), and a coverage gate blocks `git push` when coverage falls below 80%.

## Implementation Notes

- **Language/runtime**: Java 21, Maven 3.9+. Plain CLI application (no Spring Boot) — the pipeline is a batch/file-watching orchestrator, not a web service, so a DI framework and embedded servlet container are unnecessary overhead.
- **Money**: `BigDecimal` everywhere for amounts. Never `double`/`float`. Rounding mode `RoundingMode.HALF_UP` where rounding is required. Every amount is always paired with its ISO 4217 currency code — never pass a bare numeric amount.
- **Currency validation**: validate currency codes against `java.util.Currency.getInstance(code)`; catch `IllegalArgumentException` and reject unknown codes (e.g. `XYZ` in `TXN006`) with `reason_code: "INVALID_CURRENCY"`.
- **Time**: `java.time.OffsetDateTime` everywhere, always normalized to UTC (`ZoneOffset.UTC`). Never `java.util.Date`.
- **JSON**: Jackson (`jackson-databind` + `jackson-datatype-jsr310`) for all message and result serialization. Register `JavaTimeModule`; write ISO-8601 strings, not epoch millis.
- **File-based IPC**: agents communicate exclusively via JSON files moved through `shared/input/ → shared/processing/ → shared/output/ → shared/results/`. Writes must be atomic (write to a `.tmp` file in the same directory, then `Files.move` with `ATOMIC_MOVE`) so a partially-written file can never be picked up by the next agent.
- **PII handling**: `source_account`, `destination_account`, and any account-holder name are sensitive. Logs must mask them (e.g. `ACC-****1001` — last 4 digits only). The JSON messages passed between agents in `shared/` may carry the full value (agents need it to operate), but nothing sensitive is ever written to the audit *log* in plaintext.
- **Logging**: SLF4J + Logback. Structured log line per agent action: `timestamp`, `agent`, `transaction_id`, `outcome` (and masked account refs where relevant). Use parameterized logging (`log.info("...")`), never string concatenation.
- **Idempotency**: an agent re-processing a message already present in `shared/output/` or `shared/results/` for that `transaction_id` must not produce a duplicate result — check first, act second.
- **Config**: monetary/risk thresholds (fraud threshold `$10,000`, high-risk score cutoff, etc.) live in a single `PipelineConfig` class — no magic numbers scattered across agent classes.
- **Testing**: JUnit 5 + Mockito + JaCoCo. Arrange/Act/Assert, one behavior per `@Test`, tests named `methodName_scenario_expectedOutcome()`. No test may read or write the real top-level `shared/` directory — use `@TempDir`.

## Context

### Beginning context

- `sample-transactions.json` — 8 raw transaction records covering: a normal transfer, a large wire transfer, a value just under the fraud threshold (`$9,999.99`), an off-hours cross-border transfer, a very large wire transfer, an invalid currency code (`XYZ`), a negative-amount refund, and a standard salary-advance transfer.
- `TASKS.md` — the assignment brief for this capstone.
- No source code yet exists; this specification and `agents.md` are the first deliverables (Task 1 / Agent 1).

### Ending context

- Maven project scaffold: `pom.xml`, `src/main/java/com/homework6/pipeline/...`, `src/test/java/com/homework6/pipeline/...`.
- `Integrator.java` — orchestrator: creates `shared/{input,processing,output,results}`, loads `sample-transactions.json` into `shared/input/`, runs agents in order, prints a run summary.
- Agent classes: `TransactionValidatorAgent`, `FraudDetectorAgent`, `ComplianceCheckerAgent`, `SettlementProcessorAgent`, each implementing a common `PipelineAgent` contract.
- Support classes: `FileMessageBus` (read/write/move JSON messages), `AuditLogger`, `MoneyUtil`, `PiiMaskingUtil`, `PipelineConfig`.
- `shared/results/` populated with one terminal JSON record per transaction after a pipeline run, plus a `pipeline-summary.json` report.
- Unit + integration test suite under `src/test/java`, JaCoCo report showing ≥ 90% line coverage.
- `research-notes.md` documenting ≥ 2 context7 queries used while implementing (Task 2/4).
- `mcp.json` (context7 + custom `pipeline-status` server) and `mcp/server.py` exposing `get_transaction_status`, `list_pipeline_results`, and the `pipeline://summary` resource (Task 4).
- `.claude/commands/write-spec.md`, `.claude/commands/run-pipeline.md`, `.claude/commands/validate-transactions.md`, and a coverage-gate hook in `.claude/settings.json` (Task 3).
- `README.md` (with author name), `HOWTORUN.md`, and `docs/screenshots/` (Task 5).

## Low-Level Tasks

### 1. Integrator / Orchestrator

Task: Integrator (Orchestrator)
Prompt: "Create the pipeline orchestrator `Integrator.java` in `com.homework6.pipeline`. On startup it must create `shared/input`, `shared/processing`, `shared/output`, and `shared/results` if they don't exist, load every record from `sample-transactions.json` into a `PipelineMessage` and write one JSON file per transaction into `shared/input/`, then run `TransactionValidatorAgent`, `FraudDetectorAgent`, `ComplianceCheckerAgent`, and `SettlementProcessorAgent` in sequence over the queued messages, moving each message through `shared/processing/` → `shared/output/` as it advances, and finally write a `pipeline-summary.json` to `shared/results/` reporting total/accepted/rejected counts and per-agent timing. Use `FileMessageBus` for all file IO — never read/write `shared/` files directly from `Integrator`."
File to CREATE: `src/main/java/com/homework6/pipeline/Integrator.java`
Function to CREATE: `void run()` and `static void main(String[] args)`
Details: Must be re-runnable without duplicating already-settled transactions (idempotency check against `shared/results/`); must exit with a non-zero status code if any agent throws; must log a summary line per transaction_id with its final status.

### 2. Transaction Validator Agent

Task: Transaction Validator Agent
Prompt: "Create `TransactionValidatorAgent` that reads each `PipelineMessage` from `shared/input/`, checks required fields (`transaction_id`, `timestamp`, `source_account`, `destination_account`, `amount`, `currency`, `transaction_type`) are present and non-blank, checks `amount` parses as a positive `BigDecimal` (reject `TXN007`'s negative refund amount with `reason_code: NEGATIVE_AMOUNT`), and checks `currency` is a valid ISO 4217 code via `java.util.Currency` (reject `TXN006`'s `XYZ` with `reason_code: INVALID_CURRENCY`). On success, write the message to `shared/output/` with `status: VALIDATED` and `target_agent: fraud_detector`; on failure, write directly to `shared/results/` with `status: REJECTED` and the specific `reason_code`."
File to CREATE: `src/main/java/com/homework6/pipeline/agent/TransactionValidatorAgent.java`
Function to CREATE: `PipelineMessage process(PipelineMessage message)`
Details: Validation must be a series of small, independently testable checks (one method per rule) so each rejection reason can be unit tested in isolation; must not mutate the input message, return a new validated/rejected copy.

### 3. Fraud Detector Agent

Task: Fraud Detector Agent
Prompt: "Create `FraudDetectorAgent` that consumes validated messages and computes a risk score 0–100 from three weighted factors: high value (amount > $10,000 in transaction currency), unusual timing (transaction hour, UTC, outside 06:00–22:00), and cross-border (source country vs. destination/metadata country mismatch, using `metadata.country`). Transactions scoring ≥ 70 are written to `shared/results/` with `status: FLAGGED_FOR_REVIEW`, the score, and the contributing factor list; transactions below 70 continue to `shared/output/` with `status: FRAUD_CLEARED` and `target_agent: compliance_checker`."
File to CREATE: `src/main/java/com/homework6/pipeline/agent/FraudDetectorAgent.java`
Function to CREATE: `PipelineMessage process(PipelineMessage message)`
Details: Score weighting and the 70-point flag threshold must be constants in `PipelineConfig`, not inline literals; each of the three factors must be independently unit-testable (e.g. a pure `int scoreHighValue(BigDecimal amount)` method).

### 4. Compliance Checker Agent

Task: Compliance Checker Agent
Prompt: "Create `ComplianceCheckerAgent` that consumes fraud-cleared messages and rejects transactions matching simple sanctions/compliance rules: destination accounts on a static denylist (`ComplianceConfig.BLOCKED_ACCOUNTS`), and wire transfers over $50,000 crossing a border (`transaction_type == wire_transfer` and cross-border) which require manual `status: COMPLIANCE_HOLD` instead of outright rejection. All other messages pass through to `shared/output/` with `status: COMPLIANCE_CLEARED` and `target_agent: settlement_processor`."
File to CREATE: `src/main/java/com/homework6/pipeline/agent/ComplianceCheckerAgent.java`
Function to CREATE: `PipelineMessage process(PipelineMessage message)`
Details: Denylist and the $50,000 hold threshold live in `PipelineConfig`/`ComplianceConfig`; `COMPLIANCE_HOLD` results still land in `shared/results/` (not stuck in `shared/processing/`) since manual review is a valid terminal state for this pipeline.

### 5. Settlement Processor Agent

Task: Settlement Processor Agent
Prompt: "Create `SettlementProcessorAgent` that consumes compliance-cleared messages and produces the final terminal record: `status: SETTLED`, a generated `settlement_id` (UUID), and the settlement timestamp (`OffsetDateTime.now(ZoneOffset.UTC)`). Write the result to `shared/results/` as `{transaction_id}.json`. This agent is the last hop in the chain — it must never write to `shared/output/`."
File to CREATE: `src/main/java/com/homework6/pipeline/agent/SettlementProcessorAgent.java`
Function to CREATE: `PipelineMessage process(PipelineMessage message)`
Details: Must call `AuditEventLogger` (or `AuditLogger`) exactly once per settlement with masked account references; must be idempotent — if `shared/results/{transaction_id}.json` already exists with `status: SETTLED`, skip re-settlement and log a `SKIPPED_DUPLICATE` outcome instead of overwriting.
