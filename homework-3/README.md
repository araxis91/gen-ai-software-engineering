# Homework 3: Specification-Driven Design

**Student**: Dmytro Cherneha  
**Task**: Design a specification package for a virtual bank card lifecycle management service (Java + Spring Boot).

---

## Summary

This submission contains a complete specification package for a **Virtual Card Lifecycle Management Service** — a regulated REST API that handles virtual payment card issuance, freeze/unfreeze state transitions, spending limit management, and transaction history in a FinTech-grade, auditable environment.

No implementation code is included. The deliverables are:

| File | Purpose |
|------|---------|
| `specification.md` | Layered product spec: objectives → NFRs → implementation notes → context → 18 low-level tasks |
| `agents.md` | AI agent rules: domain constraints, testing expectations, what agents must never do |
| `.claude/CLAUDE.md` | Claude Code editor rules: hard rules, patterns, anti-patterns, file placement |
| `README.md` | This file |

---

## Rationale

### Why this specification structure?

The spec is structured in strict layers (High → Mid → NFR → Implementation → Context → Tasks) so that an AI agent — or a human engineer — can start at any layer and immediately understand what sits above and below. This prevents the common failure mode where a task list exists in isolation from its business motivation.

**Traceability**: every Low-Level Task references which Mid-Level Objective (MO-1 through MO-6) it serves. This means a reviewer can ask "does this task justify its existence?" and trace the answer back to a user-visible outcome.

**18 tasks, not 3**: the task list is deliberately granular. Each task has a single, bounded responsibility and ends with a checkable Definition of Done. This matches how AI agents work best — one focused context, not an open-ended brief.

### Why these performance targets?

The p99 latency targets (100 ms for reads, 400 ms for writes) are chosen based on two constraints:

1. **FinTech UX expectation**: card management operations are user-initiated (not background). Users expect sub-second feedback. Industry benchmarks (Stripe, Marqeta) show sub-200 ms p95 for card state endpoints.
2. **PostgreSQL with HikariCP**: a single-node PostgreSQL with a properly indexed schema and a connection pool of 20 can comfortably serve these targets under moderate load (< 100 RPS). They are labeled "assumed targets" in the spec to signal that load testing is required before committing to an SLA.

Rate limits (60 write / 300 read per minute per user) are conservative for card management, where a human user would rarely issue more than a few requests per minute. They protect against scripted attacks and runaway clients without impacting normal use.

### Why these verification methods?

Verification is specified at three levels:
- **Per-objective**: how to know the feature works end-to-end (integration test, audit count assertion).
- **Per-task**: a Definition of Done checklist an implementer can mechanically verify.
- **Cross-cutting**: the PAN scrubbing test — a log-capture test that asserts no 13–19 digit strings appear in any log line during a full lifecycle flow. This exists because PAN leakage through logging is a common and catastrophic compliance failure in real FinTech systems.

---

## Industry Best Practices Incorporated

### 1. PAN never in logs or responses
**Where**: `specification.md` §Security Policy, §Implementation Notes (Card Number Handling); `agents.md` §Domain Rules (PAN); `.claude/CLAUDE.md` §Hard Rules; Task 5 (PanMaskingUtil), Task 6 (CardEncryptionService), Task 18 (PAN scrubbing test).

Real-world basis: PCI DSS Requirement 3.3 prohibits storage of sensitive authentication data after authorisation. Logging frameworks are a common accidental exposure vector — an explicit scrubbing test is the only reliable control.

### 2. Optimistic locking for concurrent state transitions
**Where**: `specification.md` §EC-5, Task 9 (CardStateService), database schema `version BIGINT NOT NULL DEFAULT 0`.

Real-world basis: card freeze/unfreeze operations must be serialised. Pessimistic locking degrades throughput; optimistic locking with a `@Version` column allows high concurrency and surfaces conflicts as explicit 409 responses rather than silent data corruption.

### 3. Idempotency keys on all write operations
**Where**: `specification.md` §MO-6, §Implementation Notes (Idempotency), Task 12 (IdempotencyService), Task 15 (CardController).

Real-world basis: network retries and client-side double-submits are inevitable. Without idempotency, a retry on card issuance creates duplicate cards. Stripe, Adyen, and every major payment API mandate idempotency keys on write endpoints.

### 4. Append-only audit trail with no service-account DELETE privilege
**Where**: `specification.md` §Security Policy, §MO-5; `agents.md` §Audit Trail; Task 7 (AuditEventService); V4 migration (no FK on audit_events to prevent cascade deletes).

Real-world basis: financial regulators (FCA, OCC) require an immutable audit trail for card operations. The most common audit trail failure is an application that can accidentally delete its own records. Removing `DELETE`/`UPDATE` from the service account's privileges is a defence-in-depth control.

### 5. Money stored as minor currency units (BIGINT cents)
**Where**: `specification.md` §Implementation Notes (Monetary Values); `agents.md` §Monetary Precision; Task 3 (entities), Task 10 (CardLimitService).

Real-world basis: floating-point arithmetic is unreliable for money. `BIGINT` in the database and `BigDecimal` in Java with explicit rounding mode eliminates rounding drift. ISO 4217 minor unit storage is standard practice in payment systems (Stripe, Square, Adyen all use this approach).

### 6. State machine with explicit rejection of invalid transitions
**Where**: `specification.md` §State Machine diagram, §EC-2; `agents.md` §Card State Machine; Task 9 (CardStateService).

Real-world basis: card lifecycle has strict regulatory implications. A cancelled card should never be reactivated without a new issuance flow. Encoding the state machine explicitly (rather than allowing arbitrary status updates) prevents compliance violations caused by bugs or malicious requests.

### 7. Structured JSON logging with MDC traceId
**Where**: `specification.md` §Audit & Logging; Task 18 (RequestLoggingFilter, logback-spring.xml).

Real-world basis: in production FinTech environments, log aggregation (ELK, Splunk, Datadog) requires structured formats. A `traceId` in every log line enables correlation across microservices and is essential for incident response and regulatory audits.

### 8. Testcontainers for integration tests (no H2 in-memory)
**Where**: `agents.md` §Testing & Verification (Do not generate in-memory H2 config); `.claude/CLAUDE.md` §What NOT to Generate; Task 17.

Real-world basis: H2's SQL dialect diverges from PostgreSQL in ways that hide real bugs (different constraint behaviour, different type casting, missing PostgreSQL functions like `gen_random_uuid()`). FinTech systems cannot afford tests that pass on H2 and fail on prod PostgreSQL.

### 9. Fail-fast on missing secrets at startup
**Where**: `specification.md` §EC-9, Task 6 (EncryptionConfig); `agents.md` §Fail fast and loud at startup.

Real-world basis: a service that starts without its encryption key will either silently fail all card operations or, worse, fall back to an insecure default. Failing loudly at startup causes the deployment to be rejected before any traffic is served — the correct behaviour for a security-critical dependency.

### 10. Ownership enforced at query level, not application level
**Where**: `specification.md` §MO-5 security check, §EC-8; `agents.md` §Ownership & Authorization; `.claude/CLAUDE.md` §Hard Rules; Task 4 (`findByIdAndUserId`).

Real-world basis: IDOR (Insecure Direct Object Reference) vulnerabilities are consistently in the OWASP Top 10. Filtering ownership at the SQL level (adding `AND user_id = ?` to the query) is safer than loading the resource and checking in Java — because it eliminates the window where a partially loaded object could be accidentally returned.
