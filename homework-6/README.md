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

## Tech stack

| Layer                     | Technology                                                                                                                 |
| ------------------------- | -------------------------------------------------------------------------------------------------------------------------- |
| Pipeline language/runtime | Java 21, Maven 3.9+                                                                                                        |
| JSON messaging            | Jackson (`jackson-databind` + `jackson-datatype-jsr310`)                                                                   |
| Logging                   | SLF4J + Logback (structured, ISO-8601 UTC timestamps)                                                                      |
| Testing                   | JUnit 5, Mockito, JaCoCo (coverage gate + reports)                                                                         |
| Custom MCP server         | Python 3, [FastMCP](https://gofastmcp.com)                                                                                 |
| Library docs during dev   | `context7` MCP server                                                                                                      |
| AI coding agent           | Claude Code — custom skills (`/write-spec`, `/run-pipeline`, `/validate-transactions`) + a `PreToolUse` coverage-gate hook |

## Repository layout

- `specification.md`, `agents.md` — the project spec and AI-agent guidelines (Task 1).
- `src/main/java/com/homework6/pipeline/` — the pipeline itself: `Integrator`, `agent/`, `model/`, `messaging/`, `audit/`, `config/`, `util/`, `cli/` (Task 2).
- `src/test/java/...` — unit tests per class + `IntegratorTest` (full-pipeline integration test) (Task 5).
- `.claude/commands/` — `write-spec.md`, `run-pipeline.md`, `validate-transactions.md` skills; `.claude/settings.json` + `scripts/coverage-gate.sh` — the coverage-gate hook (Task 3).
- `mcp/server.py`, `.mcp.json` — the custom FastMCP server (`get_transaction_status`, `list_pipeline_results`, `pipeline://summary`) plus `context7` (Task 4).
- `research-notes.md` — documented context7 queries used while building the pipeline and MCP server.
- `docs/screenshots/` — screenshots of the spec, pipeline run, tests/coverage, skill/hook in action, and MCP usage.

See `HOWTORUN.md` for step-by-step setup and demo instructions.
