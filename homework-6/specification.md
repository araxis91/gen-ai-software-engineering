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
6. The pipeline's stage order is configurable without modifying any agent class: a `PipelineSequence` (an ordered list of agent names) governs routing, agents themselves never decide what runs next, and individual stages can be run standalone (`Integrator.runStage(name)`), as a custom-ordered subset, or reversed relative to the default order — with each agent still correctly enforcing its own rule regardless of position.

## Implementation Notes

- **Language/runtime**: Java 21, Maven 3.9+. Plain CLI application (no Spring Boot) — the pipeline is a batch/file-watching orchestrator, not a web service, so a DI framework and embedded servlet container are unnecessary overhead.
- **Money**: `BigDecimal` everywhere for amounts. Never `double`/`float`. Rounding mode `RoundingMode.HALF_UP` where rounding is required. Every amount is always paired with its ISO 4217 currency code — never pass a bare numeric amount.
- **Currency validation**: validate currency codes against `java.util.Currency.getInstance(code)`; catch `IllegalArgumentException` and reject unknown codes (e.g. `XYZ` in `TXN006`) with `reason_code: "INVALID_CURRENCY"`.
- **Time**: `java.time.OffsetDateTime` everywhere, always normalized to UTC (`ZoneOffset.UTC`). Never `java.util.Date`.
- **JSON**: Jackson (`jackson-databind` + `jackson-datatype-jsr310`) for all message and result serialization. Register `JavaTimeModule`; write ISO-8601 strings, not epoch millis.
- **File-based IPC**: agents communicate exclusively via JSON files moved through `shared/input/ → shared/processing/ → shared/output/ → shared/results/`. Writes must be atomic (write to a `.tmp` file in the same directory, then `Files.move` with `ATOMIC_MOVE`) so a partially-written file can never be picked up by the next agent.
- **PII handling**: `source_account`, `destination_account`, and any account-holder name are sensitive. Logs must mask them (e.g. `ACC-****1001` — last 4 digits only). The JSON messages passed between agents in `shared/` may carry the full value (agents need it to operate), but nothing sensitive is ever written to the audit *log* in plaintext.
- **Logging**: SLF4J + Logback. Structured log line per agent action: `timestamp`, `agent`, `transaction_id`, `outcome` (and masked account refs where relevant). Use parameterized logging (`log.info("...")`), never string concatenation.
- **Idempotency**: enforced once, centrally, by `Integrator` — it checks `shared/results/` before seeding a transaction into `shared/input/` and skips (logging `SKIPPED_DUPLICATE`) if a terminal result already exists. Agents themselves have no file-system access and cannot duplicate this check.
- **Pipeline sequencing**: `PipelineAgent.process(TransactionRecord)` takes and returns a `TransactionRecord` only — no message envelope, no routing decision. `PipelineSequence` (an ordered `List<String>` of agent names, with a `nextAfter(name)` lookup) is the only place that decides what runs after what; `Integrator` reads it to route non-terminal output and to seed `shared/input/` targeting the sequence's first stage.
- **Config**: monetary/risk thresholds (fraud threshold `$10,000`, high-risk score cutoff, etc.) live in a single `PipelineConfig` class — no magic numbers scattered across agent classes.
- **Testing**: JUnit 5 + Mockito + JaCoCo. Arrange/Act/Assert, one behavior per `@Test`, tests named `methodName_scenario_expectedOutcome()`. No test may read or write the real top-level `shared/` directory — use `@TempDir`.

## Context

### Beginning context

- `sample-transactions.json` — 8 raw transaction records covering: a normal transfer, a large wire transfer, a value just under the fraud threshold (`$9,999.99`), an off-hours cross-border transfer, a very large wire transfer, an invalid currency code (`XYZ`), a negative-amount refund, and a standard salary-advance transfer.
- `TASKS.md` — the assignment brief for this capstone.
- No source code yet exists; this specification and `agents.md` are the first deliverables (Task 1 / Agent 1).

### Ending context

- Maven project scaffold: `pom.xml`, `src/main/java/com/homework6/pipeline/...`, `src/test/java/com/homework6/pipeline/...`.
- `Integrator.java` — orchestrator: creates `shared/{input,processing,output,results}`, loads `sample-transactions.json` into `shared/input/`, runs the configured `PipelineSequence` (default or overridden via CLI/constructor), prints a run summary.
- `PipelineSequence.java` — configurable, validated agent order + `nextAfter(name)` routing lookup. `CliArgs.java` — parses `--sequence`/`--stage`/`--file` flags.
- Agent classes: `TransactionValidatorAgent`, `FraudDetectorAgent`, `ComplianceCheckerAgent`, `SettlementProcessorAgent`, each implementing the `PipelineAgent` contract (`TransactionRecord process(TransactionRecord record)`) — pure transforms with no routing knowledge.
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
Prompt: "Create the pipeline orchestrator `Integrator.java` in `com.homework6.pipeline`. On startup it must create `shared/input`, `shared/processing`, `shared/output`, and `shared/results` if they don't exist, load every record from `sample-transactions.json` into a `TransactionRecord` and write one `PipelineMessage` per transaction into `shared/input/` targeted at `PipelineSequence.first()`, then run each agent named in the configured `PipelineSequence`, in order, over the queued messages, moving each message through `shared/processing/` → `shared/output/` as it advances (routing decided by `PipelineSequence.nextAfter(name)`, never by the agent), and finally write a `pipeline-summary.json` to `shared/results/` reporting total/accepted/rejected counts and the agent sequence used. Use `FileMessageBus` for all file IO — never read/write `shared/` files directly from `Integrator`."
File to CREATE: `src/main/java/com/homework6/pipeline/Integrator.java`
Function to CREATE: `void run()`, `void runStage(String agentName)`, `int seedInput()`, and `static void main(String[] args)`
Details: Must be re-runnable without duplicating already-settled transactions (idempotency check against `shared/results/`, performed once during `seedInput()`); `runStage(String)` must be callable standalone, any number of times, in any order, processing only whatever's currently queued for that named agent; must exit with a non-zero status code if any agent throws; must log a summary line per transaction_id with its final status.

### 2. Transaction Validator Agent

Task: Transaction Validator Agent
Prompt: "Create `TransactionValidatorAgent` that reads each `PipelineMessage` from `shared/input/`, checks required fields (`transaction_id`, `timestamp`, `source_account`, `destination_account`, `amount`, `currency`, `transaction_type`) are present and non-blank, checks `amount` parses as a positive `BigDecimal` (reject `TXN007`'s negative refund amount with `reason_code: NEGATIVE_AMOUNT`), and checks `currency` is a valid ISO 4217 code via `java.util.Currency` (reject `TXN006`'s `XYZ` with `reason_code: INVALID_CURRENCY`). On success, write the message to `shared/output/` with `status: VALIDATED` and `target_agent: fraud_detector`; on failure, write directly to `shared/results/` with `status: REJECTED` and the specific `reason_code`."
File to CREATE: `src/main/java/com/homework6/pipeline/agent/TransactionValidatorAgent.java`
Function to CREATE: `TransactionRecord process(TransactionRecord record)`
Details: Validation must be a series of small, independently testable checks (one method per rule) so each rejection reason can be unit tested in isolation; must not mutate the input message, return a new validated/rejected copy.

### 3. Fraud Detector Agent

Task: Fraud Detector Agent
Prompt: "Create `FraudDetectorAgent` that consumes validated messages and computes a risk score 0–100 from three weighted factors: high value (amount > $10,000 in transaction currency), unusual timing (transaction hour, UTC, outside 06:00–22:00), and cross-border (source country vs. destination/metadata country mismatch, using `metadata.country`). Transactions scoring ≥ 70 are written to `shared/results/` with `status: FLAGGED_FOR_REVIEW`, the score, and the contributing factor list; transactions below 70 continue to `shared/output/` with `status: FRAUD_CLEARED` and `target_agent: compliance_checker`."
File to CREATE: `src/main/java/com/homework6/pipeline/agent/FraudDetectorAgent.java`
Function to CREATE: `TransactionRecord process(TransactionRecord record)`
Details: Score weighting and the 70-point flag threshold must be constants in `PipelineConfig`, not inline literals; each of the three factors must be independently unit-testable (e.g. a pure `int scoreHighValue(BigDecimal amount)` method).

### 4. Compliance Checker Agent

Task: Compliance Checker Agent
Prompt: "Create `ComplianceCheckerAgent` that consumes fraud-cleared messages and rejects transactions matching simple sanctions/compliance rules: destination accounts on a static denylist (`ComplianceConfig.BLOCKED_ACCOUNTS`), and wire transfers over $50,000 crossing a border (`transaction_type == wire_transfer` and cross-border) which require manual `status: COMPLIANCE_HOLD` instead of outright rejection. All other messages pass through to `shared/output/` with `status: COMPLIANCE_CLEARED` and `target_agent: settlement_processor`."
File to CREATE: `src/main/java/com/homework6/pipeline/agent/ComplianceCheckerAgent.java`
Function to CREATE: `TransactionRecord process(TransactionRecord record)`
Details: Denylist and the $50,000 hold threshold live in `PipelineConfig`/`ComplianceConfig`; `COMPLIANCE_HOLD` results still land in `shared/results/` (not stuck in `shared/processing/`) since manual review is a valid terminal state for this pipeline.

### 5. Settlement Processor Agent

Task: Settlement Processor Agent
Prompt: "Create `SettlementProcessorAgent` that consumes compliance-cleared messages and produces the final terminal record: `status: SETTLED`, a generated `settlement_id` (UUID), and the settlement timestamp (`OffsetDateTime.now(ZoneOffset.UTC)`). Write the result to `shared/results/` as `{transaction_id}.json`. This agent is the last hop in the chain — it must never write to `shared/output/`."
File to CREATE: `src/main/java/com/homework6/pipeline/agent/SettlementProcessorAgent.java`
Function to CREATE: `TransactionRecord process(TransactionRecord record)`
Details: Must call `AuditLogger` exactly once per settlement with masked account references. Has no file-system access (see Implementation Notes: Idempotency) — duplicate-settlement protection is `Integrator`'s job, not this agent's.

### 6. Pipeline Sequence Configuration

Task: PipelineSequence + CLI configuration
Prompt: "Create `PipelineSequence.java`, an ordered, validated list of agent names with a `nextAfter(name)` lookup and a `DEFAULT_ORDER` matching the original fixed chain. Update `Integrator` to build an agent-name → `PipelineAgent` registry, accept a `PipelineSequence` in its constructor, and use it (not any hardcoded agent field) to decide routing after each stage. Add `CliArgs.java` to parse `--sequence=name1,name2,...`, `--stage=name`, and `--file=path` command-line flags so `Integrator.main` can run a custom order, a subset of stages, or exactly one stage without recompiling."
File to CREATE: `src/main/java/com/homework6/pipeline/PipelineSequence.java`, `src/main/java/com/homework6/pipeline/CliArgs.java`
Function to CREATE: `Optional<String> nextAfter(String agentName)`, `static PipelineSequence defaultSequence()`, `static CliArgs parse(String[] args)`
Details: Constructor must reject an empty or duplicate-containing sequence; `Integrator`'s constructor must reject a sequence referencing an unknown agent name with a clear error listing valid names; reordering/dropping stages must change real transaction outcomes correctly (e.g. dropping `compliance_checker` from the sequence means a denylisted destination account is never caught) rather than silently no-op.
