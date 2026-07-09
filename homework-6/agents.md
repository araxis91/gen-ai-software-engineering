# Agent Guidelines — AI-Powered Multi-Agent Banking Pipeline

## Purpose

This document configures AI coding agents (Claude Code, GitHub Copilot, Cursor) working in this repository. Agents **must** read this file, and `specification.md`, before generating or modifying any code. This project is a capstone banking-domain exercise: a file-based multi-agent transaction processing pipeline. Rules below extend the starter template with project-specific context.

---

## Tech Stack Assumptions

- **Language**: Java 21 (LTS). Use records for immutable DTOs/messages, sealed interfaces for status/outcome hierarchies where it clarifies intent.
- **Build**: Maven 3.9+. Do not introduce Gradle.
- **Framework**: none (plain Java CLI). This is a batch/file-orchestrated pipeline, not a web service — do not add Spring Boot, servlet containers, or REST controllers unless the spec is updated to require an API surface.
- **JSON**: Jackson (`jackson-databind`, `jackson-datatype-jsr310`). Always register `JavaTimeModule`.
- **Testing**: JUnit 5, Mockito 5, JaCoCo for coverage.
- **Money**: `BigDecimal` for all amounts. Never `float`/`double`. Always carry the ISO 4217 currency code alongside an amount.
- **Time**: `java.time.OffsetDateTime`, always normalized to `ZoneOffset.UTC`. Never `java.util.Date`.
- **Logging**: SLF4J + Logback, structured output. Never `System.out.println` in `src/main`.
- **IDs**: `java.util.UUID` for generated identifiers (e.g. `settlement_id`). Transaction IDs come from the input data (`TXN001`, etc.) and are treated as opaque strings — never regenerated.

---

## Domain Rules (Banking Pipeline)

### Money
- All amounts are `BigDecimal`, parsed from the JSON string representation (e.g. `"1500.00"`) — never parse through `double`.
- Currency must always travel with an amount. A method that accepts an amount without its currency code is a bug.
- Validate currency codes via `java.util.Currency.getInstance(code)`. Unknown/invalid codes (e.g. `XYZ`) are rejected at the validator stage, not silently passed through.
- Rounding: `RoundingMode.HALF_UP` wherever rounding is required (e.g. displaying a risk score-adjusted amount).

### PII / Sensitive Data
- `source_account`, `destination_account`, and any account-holder name are sensitive fields.
- **Never** log these fields in plaintext. Mask via `PiiMaskingUtil.mask(accountRef)` → `ACC-****1001` (last 4 characters visible only).
- The full value *may* appear inside the JSON message files under `shared/` (agents need the real value to route/settle), but it must never appear in a log line, exception message, or the `pipeline-summary.json` report beyond the masked form.
- Regex to self-check for accidental leakage in a log string before treating a change as done: any raw `ACC-\d{4,}` pattern in a log statement is a bug.

### Fraud & Compliance Thresholds
- Fraud review threshold: `amount > $10,000` (transaction currency) is one of three scored factors — see `specification.md` for the full weighting.
- All thresholds (fraud score cutoff, compliance hold amount, denylist) live in `PipelineConfig` / `ComplianceConfig`. Never hardcode a threshold literal inside an agent class.
- A transaction's fate is always one of: `SETTLED`, `REJECTED`, `FLAGGED_FOR_REVIEW`, `COMPLIANCE_HOLD`. Do not introduce a new terminal status without updating `specification.md` first.

### Audit Trail
- Every agent action (validated, rejected, scored, held, settled) must produce one structured audit log entry: `timestamp` (ISO 8601 UTC), `agent`, `transaction_id`, `outcome`.
- Audit logging happens through a single `AuditLogger` component — agents must not write ad-hoc log lines for state transitions that bypass it.
- `shared/results/` records are themselves part of the audit trail — never delete or mutate a file already written there; if an agent must correct a prior result, it does so by explicit reprocessing logic, not by silent overwrite (see idempotency rule below).

### Idempotency
- Agents have no file-system access (see Pipeline Sequencing below) and therefore cannot check `shared/results/` themselves. Idempotency is the `Integrator`'s responsibility: it checks whether a terminal result already exists for a `transaction_id` before seeding it into `shared/input/`, and skips + logs `SKIPPED_DUPLICATE` if so. Never re-add a duplicate idempotency check inside an agent — it belongs in exactly one place.
- File writes to any `shared/` subdirectory must be atomic: write to a `.tmp` file in the same directory, then move it into place. A reader must never observe a partially-written JSON file.

### Pipeline Sequencing (Configurability)
- Agents **must never** decide, hardcode, or hint at what agent runs next. `PipelineAgent.process(TransactionRecord)` takes and returns a `TransactionRecord` only — no message envelope, no routing fields, no knowledge of neighboring stages.
- The single source of truth for stage order is `PipelineSequence` (an ordered list of agent names), consumed by `Integrator`. Changing the pipeline's order, running a subset of stages, or running one stage at a time (`Integrator.runStage(String)`) must never require touching an agent class — only `PipelineSequence` construction changes.
- A transaction's fate can differ depending on which stages are configured to run (e.g. skipping `compliance_checker` means a blocked destination account is never caught). That's expected, not a bug — the configured sequence is a first-class decision, and each agent enforces its own rule correctly regardless of position.

---

## Code Style & Conventions

### Naming
- Package root: `com.homework6.pipeline`.
- Agent classes: suffix `Agent` (e.g. `TransactionValidatorAgent`), one class per pipeline stage, each implementing the shared `PipelineAgent` interface (`TransactionRecord process(TransactionRecord record)`). Agents are pure transforms of transaction state — no file I/O, no routing decisions (see Pipeline Sequencing above).
- Message/DTO classes: Java records where possible (e.g. `PipelineMessage`, `Transaction`).
- Enum values: ALL_CAPS (`VALIDATED`, `REJECTED`, `FLAGGED_FOR_REVIEW`, `COMPLIANCE_HOLD`, `SETTLED`).

### Error Handling
- Agents throw domain-specific exceptions (e.g. `InvalidTransactionException`) only for conditions that should halt the pipeline run (I/O failure, corrupt message). Ordinary business rejections (bad currency, fraud flag) are **not** exceptions — they are a normal `TransactionRecord` with `status: REJECTED`/`FLAGGED_FOR_REVIEW` and a `reason_code`.
- Never catch `Exception` broadly in an agent's `process` method — catch the specific checked exceptions IO/Jackson can throw.
- Never swallow an exception silently; if it isn't rethrown, it must be logged at `ERROR` with the `transaction_id`.

### Testing
- Test class name mirrors the class under test: `FraudDetectorAgent` → `FraudDetectorAgentTest`.
- One `@Test` per behavior; name as `methodName_scenario_expectedOutcome()`.
- Use `@TempDir` for any test that exercises `FileMessageBus` — **never** point a test at the real `shared/` directory.
- Every rejection/flag/hold reason introduced in an agent must have at least one dedicated test asserting the exact `reason_code`.
- Coverage gate (enforced by a pre-push hook, see `.claude/settings.json`): push is blocked below 80% line coverage; target ≥ 90%.

---

## What the Agent Must Not Do

- Do not add a REST API, web server, or Spring dependency **unless implementing `specification-capstone.md` Task 2** — that spec explicitly supersedes this rule and requires Spring Boot. Outside of that scope, this pipeline stays file/CLI-orchestrated per `specification.md`. When Task 2 is implemented, `PipelineExecutor` must stay framework-agnostic (no Spring imports) so the CLI (`Integrator`) and the REST API (`com.homework6.pipeline.api`) share the same core logic without the CLI depending on Spring.
- Do not use `double`/`float` for any monetary value.
- Do not log a raw `source_account`/`destination_account` — always route through `PiiMaskingUtil`.
- Do not hardcode fraud/compliance thresholds inline — use `PipelineConfig`/`ComplianceConfig`.
- Do not overwrite an existing file in `shared/results/` — check for an existing terminal result first (idempotency).
- Do not introduce a new transaction status without updating `specification.md`.
- Do not commit secrets, API keys, or MCP server credentials to the repo — use environment variables.
- Do not skip the coverage gate hook (`--no-verify`) to force a push below 80% coverage.
