# Virtual Bank Card Lifecycle Service — Specification

> Ingest the information from this file, implement the Low-Level Tasks, and generate the code that will satisfy the High and Mid-Level Objectives.

---

## High-Level Objective

Build a **Virtual Card Lifecycle Management Service** — a regulated, auditable REST API that enables end-users to issue virtual payment cards, manage their state (freeze/unfreeze), enforce spending limits, and review transaction history; and enables internal ops/compliance to monitor card activity and enforce policy — **without ever exposing raw PANs in logs, responses, or non-authorised contexts**.

Scope boundary: this service owns card issuance, state transitions, limit enforcement, and transaction recording. It does **not** own payment processing, KYC/onboarding, or account funding.

---

## Mid-Level Objectives

### MO-1: Card Issuance
- A user can request a new virtual card; the service creates a unique card with a generated PAN, CVV, and expiry, stores them encrypted at rest, and returns a masked representation.
- Observable success: a new `VirtualCard` row exists in the database with status `ACTIVE`, the PAN is stored AES-256 encrypted, and the response contains only a masked PAN (e.g., `****-****-****-4321`).

### MO-2: Card State Management
- A user (or ops) can freeze an `ACTIVE` card and unfreeze a `FROZEN` card; invalid transitions (e.g., unfreeze an already `ACTIVE` card, freeze a `CANCELLED` card) are rejected.
- Observable success: status field updates atomically; every transition is written to the `audit_events` table with actor, timestamp, IP, and before/after state.

### MO-3: Spending Limit Enforcement
- A user can set per-transaction and periodic (daily / monthly) spending limits on a card; the service validates new limits against business rules and persists them.
- Observable success: a `CardLimit` row exists with the configured values; any simulated authorization that would exceed the limit returns HTTP 422 with `LIMIT_EXCEEDED` error code.

### MO-4: Transaction History
- A user can retrieve paginated, filterable transaction records for their card (by date range, status, amount range).
- Observable success: `GET /cards/{cardId}/transactions` returns a page of `TransactionDTO` objects, newest first, with cursor-based pagination; total count is accurate; no raw PAN appears in any transaction record.

### MO-5: Compliance, Audit & Security
- All state-changing operations produce an immutable audit record; no PAN, CVV, or full card number appears in any log line, API response, or audit record; every endpoint requires a valid JWT and enforces ownership checks.
- Observable success: audit table has a row for every POST/PATCH call; structured logs contain no card number patterns (verified by log scrubbing test); attempting to access another user's card returns HTTP 403.

### MO-6: Resilience & Idempotency
- All write endpoints accept an `Idempotency-Key` header; replaying the same key returns the same response without creating duplicate resources.
- Observable success: sending the same `POST /cards` request twice with the same idempotency key creates exactly one card row and returns HTTP 200 on the second call.

---

## Non-Functional & Policy Requirements

### Performance (assumed targets — reasonable for FinTech UX)
| Operation | p50 | p95 | p99 | Rationale |
|-----------|-----|-----|-----|-----------|
| `GET /cards/{id}` | 20 ms | 50 ms | 100 ms | Read from DB with cache |
| `GET /cards/{id}/transactions` | 30 ms | 80 ms | 150 ms | Paginated index scan |
| `POST /cards` (issuance) | 80 ms | 200 ms | 400 ms | Crypto + DB write |
| `PATCH /cards/{id}/status` | 40 ms | 100 ms | 200 ms | Single row update + audit write |
| `PUT /cards/{id}/limits` | 40 ms | 100 ms | 200 ms | Upsert + validation |

These are **assumed targets** — realistic for a single-region deployment with connection pooling (HikariCP) and an indexed PostgreSQL schema.

### Rate Limiting
- 60 write requests/min per authenticated user (card issuance, state changes, limit updates).
- 300 read requests/min per authenticated user.
- Ops/compliance role: 600 read requests/min, 120 write requests/min.
- Violations return HTTP 429 with `Retry-After` header.

### Availability & Reliability
- Target: **99.9% monthly uptime** (≈ 43 min downtime/month).
- Database: PostgreSQL with connection pool size 10–20; retry on transient failures with exponential backoff (3 attempts, 100 ms / 200 ms / 400 ms).
- No in-memory state; service is stateless and horizontally scalable.

### Security Policy
- Authentication: JWT Bearer tokens (RS256, 15-min expiry, refresh token pattern out of scope).
- Authorisation: resource-level ownership check — a user may only access their own cards; ops role may read all cards.
- PAN, CVV: encrypted with AES-256-GCM at rest using a key from environment variable / secrets manager. Never stored plain-text.
- PAN display: always masked (`****-****-****-LAST4`) except in a dedicated, separately-authorised `GET /cards/{id}/pan` endpoint (out of scope for this spec).
- CVV: **never returned** in any API response after issuance.
- Audit trail: append-only `audit_events` table; service account must not have `DELETE` or `UPDATE` on this table.
- TLS: required on all channels; HTTP requests redirected to HTTPS.

### Data & Privacy
- Card records must not be hard-deleted; use logical `CANCELLED` status.
- PII (user ID linkage) treated as sensitive; not logged at DEBUG level.
- Retention policy: transaction records retained for **7 years** (regulatory minimum); application enforces no automatic purge.
- GDPR consideration: user ID is a foreign key only; PII resolution is delegated to the user-profile service.

### Audit & Logging
- Structured JSON logs (Logback + logstash-logback-encoder).
- Every inbound request logged at INFO: method, path, response status, duration; no body logged.
- Every state-change event published to `AuditEventService` synchronously before the HTTP response is returned.
- Log pattern must be verified to contain no 13–19 digit numeric sequences (PAN check).

---

## Implementation Notes

### Technology Stack
- **Language**: Java 21 (LTS)
- **Framework**: Spring Boot 3.3.x
- **Persistence**: Spring Data JPA + Hibernate 6, PostgreSQL 15
- **Security**: Spring Security 6 (JWT via `spring-security-oauth2-resource-server`)
- **Validation**: Jakarta Bean Validation (Hibernate Validator)
- **Build**: Maven 3.9+
- **Testing**: JUnit 5, Mockito, Spring Boot Test, Testcontainers (PostgreSQL)
- **Migration**: Flyway
- **HTTP Client** (for downstream mocks in tests): MockMvc

### Monetary Values
- All monetary amounts stored as `BIGINT` (minor currency units, e.g., cents) in the database.
- All monetary amounts in the API exchanged as `String` in ISO format (e.g., `"1500"` = $15.00 USD) to avoid float serialization issues.
- In Java, represented as `BigDecimal`; never use `double` or `float` for money.
- Currency stored as ISO-4217 `VARCHAR(3)` (e.g., `"USD"`).

### Card Number Handling
- PAN generated via a cryptographically secure random number generator (`SecureRandom`), conforming to Luhn algorithm.
- PAN stored as AES-256-GCM ciphertext in `card_pan_encrypted` column; encryption key loaded from `CARD_ENCRYPTION_KEY` env variable (32 bytes, base64-encoded).
- Masking utility: `PanMaskingUtil.mask(String pan)` — always returns `"****-****-****-" + pan.substring(pan.length()-4)`.
- CVV stored as BCrypt hash; never returned in any response.

### Idempotency
- Header: `Idempotency-Key: <uuid>` (required on `POST /cards`, `PATCH /cards/{id}/status`).
- Implementation: store `(idempotency_key, user_id, response_body, http_status)` in `idempotency_cache` table with a TTL of 24 hours.
- On replay: return cached response with HTTP status verbatim; do not re-execute business logic.
- Conflict: if the same idempotency key is used with a different request payload, return HTTP 422 `IDEMPOTENCY_CONFLICT`.

### Error Semantics
- All errors return `application/json` with shape:
  ```json
  {
    "errorCode": "CARD_NOT_FOUND",
    "message": "Card with id 'abc' not found",
    "timestamp": "2026-06-03T10:00:00Z",
    "traceId": "abc123"
  }
  ```
- HTTP status mapping:
  - `400` — validation failure, malformed request
  - `401` — missing/invalid JWT
  - `403` — authenticated but not authorised (wrong owner / insufficient role)
  - `404` — resource not found
  - `409` — state transition conflict (e.g., freeze already-frozen card)
  - `422` — business rule violation (limit exceeded, invalid limit value)
  - `429` — rate limit exceeded
  - `500` — unexpected server error (no sensitive detail in body)

### Naming & Code Conventions
- Package root: `com.example.virtualcard`
- Sub-packages: `domain`, `repository`, `service`, `web`, `security`, `audit`, `config`, `exception`
- Entity IDs: `UUID` type, generated by the database (`gen_random_uuid()`).
- All timestamps: `OffsetDateTime` stored as `TIMESTAMPTZ` in PostgreSQL; serialised as ISO-8601 in API.
- REST resource naming: plural nouns (`/cards`, `/transactions`), no verbs in path.
- Status values: use `enum` with `@Enumerated(EnumType.STRING)`.

### State Machine
```
Card states:   ACTIVE ──freeze──> FROZEN ──unfreeze──> ACTIVE
                                   │
                      cancel ──────┘──> CANCELLED  (terminal)
ACTIVE ──cancel──────────────────────> CANCELLED  (terminal)
```
Any transition not shown above must be rejected with HTTP 409 and error code `INVALID_STATE_TRANSITION`.

---

## Context

### Beginning Context
- Empty Maven project directory with Java 21 and Spring Boot 3.3.x parent POM.
- PostgreSQL 15 instance accessible at `localhost:5432`, database `virtual_cards_db` (or via Testcontainers in tests).
- Environment variables available: `CARD_ENCRYPTION_KEY`, `JWT_PUBLIC_KEY`, `DB_URL`, `DB_USER`, `DB_PASSWORD`.
- No existing entities, services, or migrations.

### Ending Context
After all low-level tasks are complete, the repository will contain:

```
src/
  main/
    java/com/example/virtualcard/
      VirtualCardApplication.java
      domain/
        VirtualCard.java          (JPA entity)
        CardLimit.java            (JPA entity)
        Transaction.java          (JPA entity)
        AuditEvent.java           (JPA entity)
        IdempotencyCache.java     (JPA entity)
        enums/
          CardStatus.java
          TransactionStatus.java
          LimitPeriod.java
      repository/
        VirtualCardRepository.java
        CardLimitRepository.java
        TransactionRepository.java
        AuditEventRepository.java
        IdempotencyCacheRepository.java
      service/
        CardIssuanceService.java
        CardStateService.java
        CardLimitService.java
        TransactionService.java
        IdempotencyService.java
      audit/
        AuditEventService.java
      security/
        JwtAuthenticationFilter.java
        SecurityConfig.java
        OwnershipValidator.java
      web/
        CardController.java
        TransactionController.java
        dto/
          CreateCardRequest.java
          CardResponse.java
          UpdateCardStatusRequest.java
          SetLimitsRequest.java
          CardLimitResponse.java
          TransactionResponse.java
          PageResponse.java
          ErrorResponse.java
      config/
        EncryptionConfig.java
        RateLimiterConfig.java
        HikariConfig.java
      exception/
        GlobalExceptionHandler.java
        VirtualCardException.java
        (subclasses per error code)
      util/
        PanMaskingUtil.java
        PanGenerator.java
        LuhnValidator.java
        MoneyConverter.java
  resources/
    application.yml
    application-test.yml
    db/migration/
      V1__create_virtual_cards.sql
      V2__create_card_limits.sql
      V3__create_transactions.sql
      V4__create_audit_events.sql
      V5__create_idempotency_cache.sql
      V6__indexes.sql
  test/
    java/com/example/virtualcard/
      service/
        CardIssuanceServiceTest.java
        CardStateServiceTest.java
        CardLimitServiceTest.java
        TransactionServiceTest.java
        IdempotencyServiceTest.java
      web/
        CardControllerIntegrationTest.java
        TransactionControllerIntegrationTest.java
      security/
        OwnershipValidatorTest.java
      util/
        PanMaskingUtilTest.java
        LuhnValidatorTest.java
        MoneyConverterTest.java
      audit/
        AuditEventServiceTest.java
```

---

## Edge Cases & Failure Modes

| # | Scenario | Expected Behaviour | Compliance/Audit Implication |
|---|----------|--------------------|------------------------------|
| EC-1 | User requests card issuance but already has 10 active cards | HTTP 422 `MAX_CARDS_EXCEEDED`; no card created | Audit event written: `CARD_ISSUANCE_REJECTED` |
| EC-2 | Freeze a card that is already `FROZEN` | HTTP 409 `INVALID_STATE_TRANSITION`; no DB change | No audit event (no state change occurred) |
| EC-3 | Set a per-transaction limit lower than the daily limit | HTTP 422 `INVALID_LIMIT_CONFIGURATION` with message explaining the constraint | Audit event written: `LIMIT_UPDATE_REJECTED` |
| EC-4 | Set a limit of zero | HTTP 422 `INVALID_LIMIT_VALUE`; zero disables the card effectively — must be explicit cancellation instead | — |
| EC-5 | Concurrent freeze + unfreeze requests for the same card | Database-level optimistic lock (`@Version`); one request succeeds, the other gets HTTP 409 `CONCURRENT_MODIFICATION` | Audit event for the successful transition only |
| EC-6 | Idempotency key re-used with different payload | HTTP 422 `IDEMPOTENCY_CONFLICT`; cached response NOT returned | Audit event: `IDEMPOTENCY_CONFLICT` with key hash (not the full key) |
| EC-7 | JWT token expired mid-request | HTTP 401 `TOKEN_EXPIRED` | No audit event; request rejected at filter level |
| EC-8 | User accesses card belonging to another user | HTTP 403 `ACCESS_DENIED`; no card data in response | Audit event: `UNAUTHORISED_ACCESS_ATTEMPT` with user ID and target card ID |
| EC-9 | Encryption key missing from environment | Application fails to start with clear startup error; no partial state | Operational concern; startup probe will fail |
| EC-10 | Database unavailable during card issuance | HTTP 503 `SERVICE_UNAVAILABLE` after 3 retries; no partial card record | Alarm triggered; no audit event written (DB unavailable) |
| EC-11 | Transaction list query with future `from` date > `to` date | HTTP 400 `INVALID_DATE_RANGE` | — |
| EC-12 | Pagination request with `page=0, size=0` or `size > 200` | HTTP 400 `INVALID_PAGINATION`; enforce max page size of 100 | — |
| EC-13 | Cancel a card with pending transactions (status `PENDING`) | HTTP 422 `CARD_HAS_PENDING_TRANSACTIONS`; require resolution first | Audit event: `CARD_CANCELLATION_REJECTED` |
| EC-14 | PAN generation produces a duplicate (collision) | Retry up to 3 times; if all collide, return HTTP 500 and alert ops | Audit event: `PAN_COLLISION_RETRY` |
| EC-15 | Request body contains a raw PAN-like number (fraud probing) | Log the sanitised event, reject with HTTP 400 if it matches a PAN pattern in a non-PAN field | Audit event: `SUSPICIOUS_INPUT_DETECTED` |

---

## Verification

### How Each Mid-Level Objective Is Verified

| Objective | Verification Method |
|-----------|---------------------|
| MO-1 Card Issuance | Integration test: `POST /cards` → assert 201, response contains masked PAN only, DB row has non-null encrypted PAN, CVV column is a bcrypt hash. |
| MO-2 State Management | Unit tests for state machine transitions (all valid + all invalid paths). Integration test: freeze → check audit row exists. Concurrent test: two threads freeze the same card simultaneously; assert exactly one succeeds. |
| MO-3 Limit Enforcement | Unit tests for limit validation logic. Integration test: set limit, then POST a simulated transaction exceeding it → assert 422. |
| MO-4 Transaction History | Integration test: seed 25 transactions, `GET /cards/{id}/transactions?size=10` → assert 10 items, correct cursor. Filter test: date range returns only matching rows. |
| MO-5 Compliance & Audit | Log scrubbing test: assert no 13–19 digit runs in any log output during a full create-freeze-unfreeze flow. Audit table count assertion after each state-changing operation. Ownership test: JWT for user B cannot access user A's card. |
| MO-6 Idempotency | Integration test: same `POST /cards` with same `Idempotency-Key` twice → assert second response is HTTP 200, DB count is 1. |

### Test Categories
- **Unit tests** (`*Test.java`): service layer with mocked repositories; cover all branches including edge cases EC-1 through EC-15.
- **Integration tests** (`*IntegrationTest.java`): Spring Boot Test with Testcontainers PostgreSQL; test full HTTP request/response cycle including Flyway migrations.
- **Security tests**: part of integration tests — verify 401/403 responses; confirm no PAN in response/log.
- **Acceptance criteria per task**: each low-level task below includes a "Definition of Done" checklist.

---

## Low-Level Tasks

---

### Task 1: Project Bootstrap & Maven POM

**Prompt:**
Create a Spring Boot 3.3.x Maven project for the Virtual Card Lifecycle Service with all required dependencies: Spring Web, Spring Data JPA, Spring Security OAuth2 Resource Server, Flyway, PostgreSQL driver, Bean Validation, Testcontainers (test scope), Logback with logstash-logback-encoder, and Bucket4j for rate limiting.

**Files to CREATE:**
- `pom.xml`
- `src/main/resources/application.yml`
- `src/main/resources/application-test.yml`
- `src/main/java/com/example/virtualcard/VirtualCardApplication.java`

**Details:**
- Java 21, Spring Boot 3.3.x parent.
- HikariCP pool: `minimum-idle=5`, `maximum-pool-size=20`, `connection-timeout=30000`.
- Flyway: baseline-on-migrate=true, locations=classpath:db/migration.
- `application-test.yml`: use Testcontainers JDBC URL pattern (`jdbc:tc:postgresql:15:///virtual_cards_test`), disable HikariCP minimum-idle in test.
- Structured logging: include `logstash-logback-encoder` dependency; configure `logback-spring.xml` for JSON output in non-test profiles.

**Definition of Done:**
- [ ] `mvn clean compile` succeeds with no warnings.
- [ ] `VirtualCardApplication` starts and exits cleanly (with placeholder `DataSource` config).
- [ ] `mvn test` runs (zero tests pass, zero tests fail — no errors).

---

### Task 2: Database Migrations (Flyway)

**Prompt:**
Create Flyway SQL migration scripts to set up all tables for the Virtual Card Lifecycle Service. Follow the schema design with proper data types, constraints, and foreign keys for PostgreSQL 15.

**Files to CREATE:**
- `src/main/resources/db/migration/V1__create_virtual_cards.sql`
- `src/main/resources/db/migration/V2__create_card_limits.sql`
- `src/main/resources/db/migration/V3__create_transactions.sql`
- `src/main/resources/db/migration/V4__create_audit_events.sql`
- `src/main/resources/db/migration/V5__create_idempotency_cache.sql`
- `src/main/resources/db/migration/V6__indexes.sql`

**Details:**
- `virtual_cards`: `id UUID DEFAULT gen_random_uuid() PK`, `user_id UUID NOT NULL`, `card_pan_encrypted TEXT NOT NULL`, `masked_pan VARCHAR(19) NOT NULL`, `cvv_hash VARCHAR(255) NOT NULL`, `expiry_month SMALLINT NOT NULL`, `expiry_year SMALLINT NOT NULL`, `status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'`, `currency VARCHAR(3) NOT NULL DEFAULT 'USD'`, `created_at TIMESTAMPTZ NOT NULL DEFAULT now()`, `updated_at TIMESTAMPTZ NOT NULL DEFAULT now()`, `version BIGINT NOT NULL DEFAULT 0`.
- `card_limits`: `id UUID PK`, `card_id UUID NOT NULL REFERENCES virtual_cards(id)`, `per_transaction_limit BIGINT`, `daily_limit BIGINT`, `monthly_limit BIGINT`, `currency VARCHAR(3) NOT NULL`, `updated_at TIMESTAMPTZ NOT NULL DEFAULT now()`, `version BIGINT NOT NULL DEFAULT 0`. One row per card (UNIQUE on `card_id`).
- `transactions`: `id UUID PK`, `card_id UUID NOT NULL REFERENCES virtual_cards(id)`, `amount BIGINT NOT NULL`, `currency VARCHAR(3) NOT NULL`, `merchant_name VARCHAR(255)`, `status VARCHAR(20) NOT NULL`, `created_at TIMESTAMPTZ NOT NULL DEFAULT now()`.
- `audit_events`: `id UUID PK`, `card_id UUID`, `user_id UUID NOT NULL`, `event_type VARCHAR(100) NOT NULL`, `before_state VARCHAR(50)`, `after_state VARCHAR(50)`, `ip_address VARCHAR(45)`, `actor_role VARCHAR(50)`, `created_at TIMESTAMPTZ NOT NULL DEFAULT now()`. **No FK to virtual_cards** (audit must survive card deletion/anonymisation).
- `idempotency_cache`: `id UUID PK`, `idempotency_key VARCHAR(255) NOT NULL UNIQUE`, `user_id UUID NOT NULL`, `response_body TEXT NOT NULL`, `http_status INT NOT NULL`, `request_hash VARCHAR(64) NOT NULL`, `created_at TIMESTAMPTZ NOT NULL DEFAULT now()`, `expires_at TIMESTAMPTZ NOT NULL`.
- V6: Indexes on `virtual_cards(user_id)`, `transactions(card_id, created_at DESC)`, `audit_events(card_id)`, `idempotency_cache(idempotency_key)`, `idempotency_cache(expires_at)`.

**Definition of Done:**
- [ ] `mvn flyway:migrate` against a local PostgreSQL creates all 5 tables + indexes with no errors.
- [ ] `\d virtual_cards` in psql shows all expected columns with correct types.
- [ ] Re-running migration is idempotent (Flyway checksum passes).

---

### Task 3: Domain Entities & Enums

**Prompt:**
Create JPA entity classes and enums for the Virtual Card Lifecycle domain model. Use Hibernate 6 conventions and Jakarta Persistence annotations.

**Files to CREATE:**
- `src/main/java/com/example/virtualcard/domain/enums/CardStatus.java`
- `src/main/java/com/example/virtualcard/domain/enums/TransactionStatus.java`
- `src/main/java/com/example/virtualcard/domain/enums/LimitPeriod.java`
- `src/main/java/com/example/virtualcard/domain/VirtualCard.java`
- `src/main/java/com/example/virtualcard/domain/CardLimit.java`
- `src/main/java/com/example/virtualcard/domain/Transaction.java`
- `src/main/java/com/example/virtualcard/domain/AuditEvent.java`
- `src/main/java/com/example/virtualcard/domain/IdempotencyCache.java`

**Details:**
- `CardStatus`: `ACTIVE`, `FROZEN`, `CANCELLED`.
- `TransactionStatus`: `PENDING`, `COMPLETED`, `DECLINED`, `REVERSED`.
- `LimitPeriod`: `DAILY`, `MONTHLY`.
- `VirtualCard`: annotated with `@Entity`, `@Table(name="virtual_cards")`. Fields: `id` (`@GeneratedValue(strategy=GenerationType.UUID)`), `userId`, `cardPanEncrypted`, `maskedPan`, `cvvHash`, `expiryMonth`, `expiryYear`, `status` (`@Enumerated(STRING)`), `currency`, `createdAt`, `updatedAt`, `version` (`@Version`).
- `@PreUpdate` sets `updatedAt = OffsetDateTime.now()`.
- `CardLimit`: one-to-one with `VirtualCard` (`@OneToOne(fetch=LAZY)`). Nullable limit fields (null = no limit set).
- `Transaction`: `@ManyToOne(fetch=LAZY)` to `VirtualCard`. Immutable after creation (no setters for `amount`, `currency`, `cardId`).
- `AuditEvent`: no JPA relationship to `VirtualCard` (standalone). All fields immutable (`@Column(updatable=false)`).
- `IdempotencyCache`: standard entity; include `expiresAt` field.

**Definition of Done:**
- [ ] `mvn compile` with no Hibernate mapping warnings.
- [ ] A simple integration test with `@DataJpaTest` can persist and retrieve a `VirtualCard`.

---

### Task 4: Repository Interfaces

**Prompt:**
Create Spring Data JPA repository interfaces for all domain entities, adding custom queries needed for the service layer.

**Files to CREATE:**
- `src/main/java/com/example/virtualcard/repository/VirtualCardRepository.java`
- `src/main/java/com/example/virtualcard/repository/CardLimitRepository.java`
- `src/main/java/com/example/virtualcard/repository/TransactionRepository.java`
- `src/main/java/com/example/virtualcard/repository/AuditEventRepository.java`
- `src/main/java/com/example/virtualcard/repository/IdempotencyCacheRepository.java`

**Details:**
- `VirtualCardRepository extends JpaRepository<VirtualCard, UUID>`:
  - `findByIdAndUserId(UUID id, UUID userId): Optional<VirtualCard>` — used in ownership check.
  - `countByUserIdAndStatusNot(UUID userId, CardStatus status): long` — for max-card validation.
  - `findAllByUserId(UUID userId, Pageable pageable): Page<VirtualCard>`.
- `TransactionRepository extends JpaRepository<Transaction, UUID>`:
  - `findAllByCardId(UUID cardId, Pageable pageable): Page<Transaction>`.
  - `findAllByCardIdAndCreatedAtBetween(UUID cardId, OffsetDateTime from, OffsetDateTime to, Pageable pageable): Page<Transaction>`.
  - `countByCardIdAndStatus(UUID cardId, TransactionStatus status): long` — used in EC-13.
- `IdempotencyCacheRepository`:
  - `findByIdempotencyKeyAndUserId(String key, UUID userId): Optional<IdempotencyCache>`.
  - `deleteByExpiresAtBefore(OffsetDateTime now): int` — scheduled cleanup.
- All repositories: no `@Transactional` at repository level (let service layer control transactions).

**Definition of Done:**
- [ ] All repositories load in a `@DataJpaTest` context without errors.
- [ ] `VirtualCardRepository.findByIdAndUserId` returns empty for mismatched userId.

---

### Task 5: Utility Classes (PAN, Money, Luhn)

**Prompt:**
Create utility classes for PAN generation, masking, Luhn validation, and monetary value conversion. These must be pure functions with no Spring dependencies.

**Files to CREATE:**
- `src/main/java/com/example/virtualcard/util/PanGenerator.java`
- `src/main/java/com/example/virtualcard/util/PanMaskingUtil.java`
- `src/main/java/com/example/virtualcard/util/LuhnValidator.java`
- `src/main/java/com/example/virtualcard/util/MoneyConverter.java`
- `src/test/java/com/example/virtualcard/util/PanMaskingUtilTest.java`
- `src/test/java/com/example/virtualcard/util/LuhnValidatorTest.java`
- `src/test/java/com/example/virtualcard/util/MoneyConverterTest.java`

**Details:**
- `PanGenerator.generate()`: uses `SecureRandom`; generates a 16-digit number starting with `4` (Visa-like), passes Luhn check; returns the plain PAN string (only ever used internally before encryption).
- `LuhnValidator.isValid(String pan)`: standard Luhn algorithm; returns boolean.
- `PanMaskingUtil.mask(String pan)`: returns `"****-****-****-" + last4`. Throws `IllegalArgumentException` if pan is null or length < 4.
- `MoneyConverter`: `toMinorUnits(BigDecimal amount, int scale): long` and `fromMinorUnits(long amount, int scale): BigDecimal`. Scale is currency-specific (USD = 2, JPY = 0).
- `PanMaskingUtilTest`: test with 16-digit PAN, 13-digit PAN, null input (expect exception), 4-digit PAN (edge: all masked portion is empty — returns `"****-****-****-1234"`).
- `LuhnValidatorTest`: known valid PAN, known invalid PAN, empty string, null.
- `MoneyConverterTest`: round-trip test for `1500` cents ↔ `15.00` USD.

**Definition of Done:**
- [ ] All utility unit tests pass.
- [ ] `PanGenerator.generate()` produces a PAN that passes `LuhnValidator.isValid()` for 100 iterations.
- [ ] `PanMaskingUtil.mask()` never returns a string that matches the regex `\d{4}[\- ]\d{4}` in the masked prefix.

---

### Task 6: Encryption Configuration & Service

**Prompt:**
Create the AES-256-GCM encryption configuration and a `CardEncryptionService` that encrypts and decrypts card PANs. The encryption key is loaded from an environment variable.

**Files to CREATE:**
- `src/main/java/com/example/virtualcard/config/EncryptionConfig.java`
- `src/main/java/com/example/virtualcard/service/CardEncryptionService.java`

**Details:**
- `EncryptionConfig`: reads `CARD_ENCRYPTION_KEY` (base64-encoded 32-byte AES key) from environment; fails fast at startup with a descriptive error if the variable is missing or the key is not 32 bytes after decoding; exposes a `SecretKey` Spring bean.
- `CardEncryptionService`: `String encrypt(String plaintext)` and `String decrypt(String ciphertext)`.
  - Uses AES/GCM/NoPadding, 96-bit random IV per encryption, authentication tag 128 bits.
  - Output format: `Base64(IV) + ":" + Base64(ciphertext+tag)`.
  - Each call to `encrypt` uses a fresh IV from `SecureRandom`.
  - `decrypt` validates format before attempting decryption; throws `EncryptionException` on failure.
- No logging of plaintext PAN or key material at any log level.

**Definition of Done:**
- [ ] Round-trip test: `decrypt(encrypt(pan)) == pan` for a sample PAN.
- [ ] Two calls to `encrypt(pan)` produce different ciphertext (IV randomness).
- [ ] Application fails to start (throws `BeanCreationException`) if `CARD_ENCRYPTION_KEY` is not set.

---

### Task 7: Audit Event Service

**Prompt:**
Create the `AuditEventService` that persists immutable audit records for every state-changing operation. This service must be called synchronously before the HTTP response is returned.

**Files to CREATE:**
- `src/main/java/com/example/virtualcard/audit/AuditEventService.java`
- `src/test/java/com/example/virtualcard/audit/AuditEventServiceTest.java`

**Details:**
- `AuditEventService.record(AuditEventType eventType, UUID cardId, UUID userId, String beforeState, String afterState, String ipAddress, String actorRole)`: creates and saves an `AuditEvent`.
- `AuditEventType`: enum with values covering all state-changing operations: `CARD_ISSUED`, `CARD_FROZEN`, `CARD_UNFROZEN`, `CARD_CANCELLED`, `LIMIT_SET`, `CARD_ISSUANCE_REJECTED`, `LIMIT_UPDATE_REJECTED`, `CARD_CANCELLATION_REJECTED`, `UNAUTHORISED_ACCESS_ATTEMPT`, `IDEMPOTENCY_CONFLICT`, `SUSPICIOUS_INPUT_DETECTED`, `PAN_COLLISION_RETRY`.
- IP address extracted from `HttpServletRequest` (passed in from controller layer via `RequestContextHolder` or parameter injection).
- No sensitive data (PAN, CVV) passed to this service.
- Test: mock `AuditEventRepository`; verify that `record()` constructs the entity correctly and calls `save()`.

**Definition of Done:**
- [ ] Unit test for `AuditEventService.record()` passes.
- [ ] Integration test: after `POST /cards`, verify `audit_events` table has exactly one row with `event_type = 'CARD_ISSUED'`.

---

### Task 8: Card Issuance Service

**Prompt:**
Implement `CardIssuanceService` that creates a new virtual card for an authenticated user, enforcing the max-cards rule, encrypting the PAN, hashing the CVV, and recording an audit event.

**Files to CREATE:**
- `src/main/java/com/example/virtualcard/service/CardIssuanceService.java`
- `src/main/java/com/example/virtualcard/web/dto/CreateCardRequest.java`
- `src/main/java/com/example/virtualcard/web/dto/CardResponse.java`
- `src/test/java/com/example/virtualcard/service/CardIssuanceServiceTest.java`

**Details:**
- `CreateCardRequest`: `currency` (ISO-4217, 3 chars, `@NotNull @Size(min=3,max=3)`).
- `CardResponse`: `id`, `maskedPan`, `status`, `currency`, `expiryMonth`, `expiryYear`, `createdAt`. **No PAN, no CVV**.
- `CardIssuanceService.issue(UUID userId, CreateCardRequest request, String ipAddress)`:
  1. Check `countByUserIdAndStatusNot(userId, CANCELLED) >= 10` → throw `MaxCardsExceededException` (EC-1).
  2. Generate PAN via `PanGenerator.generate()` with up to 3 collision retries (EC-14).
  3. Encrypt PAN via `CardEncryptionService.encrypt(pan)`.
  4. Hash CVV (4-digit random) with BCrypt.
  5. Set expiry to `(currentYear + 3, currentMonth)`.
  6. Save `VirtualCard`.
  7. Call `AuditEventService.record(CARD_ISSUED, ...)`.
  8. Return `CardResponse` with masked PAN.
- Wrap steps 2–7 in a single `@Transactional` method.
- Tests: mock all dependencies; test happy path, max-cards rejection, PAN collision retry up to limit, collision retry exhaustion → `CardIssuanceException`.

**Definition of Done:**
- [ ] All unit tests pass.
- [ ] `CardResponse` contains no PAN-like numeric sequence (assertion in test).
- [ ] Integration test: `POST /cards` creates exactly one DB row; response `maskedPan` matches `****-****-****-XXXX` pattern.

---

### Task 9: Card State Management Service

**Prompt:**
Implement `CardStateService` for freeze, unfreeze, and cancel operations, enforcing the state machine, using optimistic locking, and recording audit events.

**Files to CREATE:**
- `src/main/java/com/example/virtualcard/service/CardStateService.java`
- `src/main/java/com/example/virtualcard/web/dto/UpdateCardStatusRequest.java`
- `src/test/java/com/example/virtualcard/service/CardStateServiceTest.java`

**Details:**
- `UpdateCardStatusRequest`: `targetStatus` (`@NotNull`, one of `FROZEN`, `ACTIVE`, `CANCELLED`).
- `CardStateService.transition(UUID cardId, UUID userId, CardStatus targetStatus, String ipAddress)`:
  1. Load card via `findByIdAndUserId` → 404 if not found (ownership enforced at query level).
  2. Validate transition using the state machine (see Implementation Notes). Invalid → `InvalidStateTransitionException` (409).
  3. For `CANCELLED`: check `countByCardIdAndStatus(cardId, PENDING) > 0` → `CardHasPendingTransactionsException` (422, EC-13).
  4. Update `card.setStatus(targetStatus)`.
  5. Save (optimistic lock via `@Version`; catch `ObjectOptimisticLockingFailureException` → `ConcurrentModificationException` (409, EC-5)).
  6. Call `AuditEventService.record(...)` with before/after state.
  7. Return updated `CardResponse`.
- Test: all valid transitions, all invalid transitions, concurrent modification scenario (simulate stale version).

**Definition of Done:**
- [ ] All unit tests pass including all state machine paths.
- [ ] Integration test: freeze a card, then attempt to freeze again → 409. Check audit table has 1 row, not 2.
- [ ] Concurrent test: two threads update same card simultaneously; assert exactly one succeeds and one gets 409.

---

### Task 10: Card Limit Service

**Prompt:**
Implement `CardLimitService` for setting and retrieving spending limits on a virtual card, with validation rules preventing contradictory configurations.

**Files to CREATE:**
- `src/main/java/com/example/virtualcard/service/CardLimitService.java`
- `src/main/java/com/example/virtualcard/web/dto/SetLimitsRequest.java`
- `src/main/java/com/example/virtualcard/web/dto/CardLimitResponse.java`
- `src/test/java/com/example/virtualcard/service/CardLimitServiceTest.java`

**Details:**
- `SetLimitsRequest`: `perTransactionLimit` (optional, `BigDecimal`, min 0.01), `dailyLimit` (optional), `monthlyLimit` (optional). All amounts in major currency units (converted to minor units on receipt).
- Validation rules (throw `InvalidLimitConfigurationException` / 422):
  - `perTransactionLimit` must be ≤ `dailyLimit` if both are set.
  - `dailyLimit` must be ≤ `monthlyLimit` if both are set.
  - Any limit must be > 0 (EC-4).
- `CardLimitService.setLimits(UUID cardId, UUID userId, SetLimitsRequest req, String ipAddress)`:
  1. Verify card ownership (load via `findByIdAndUserId`).
  2. Verify card is not `CANCELLED`.
  3. Validate limit relationships.
  4. Upsert `CardLimit` (create if absent, update if present).
  5. Audit `LIMIT_SET` event.
  6. Return `CardLimitResponse`.
- `CardLimitResponse`: all limits in major currency units (converted back from minor units), `currency`, `updatedAt`.
- Test: happy path upsert, contradiction rejection, cancelled card rejection, partial limit update (only one field set).

**Definition of Done:**
- [ ] All unit tests pass.
- [ ] Integration test: set limits, GET card limits, assert values round-trip correctly (no floating-point drift).
- [ ] Attempting to set `perTransactionLimit > dailyLimit` returns 422 with `INVALID_LIMIT_CONFIGURATION`.

---

### Task 11: Transaction Service

**Prompt:**
Implement `TransactionService` for recording new transactions (for testing/simulation) and querying paginated transaction history with filters.

**Files to CREATE:**
- `src/main/java/com/example/virtualcard/service/TransactionService.java`
- `src/main/java/com/example/virtualcard/web/dto/TransactionResponse.java`
- `src/main/java/com/example/virtualcard/web/dto/PageResponse.java`
- `src/test/java/com/example/virtualcard/service/TransactionServiceTest.java`

**Details:**
- `TransactionResponse`: `id`, `amount` (major units as String), `currency`, `merchantName`, `status`, `createdAt`. No card number.
- `PageResponse<T>`: `content: List<T>`, `page`, `size`, `totalElements`, `totalPages`, `hasNext`.
- `TransactionService.getTransactions(UUID cardId, UUID userId, OffsetDateTime from, OffsetDateTime to, int page, int size)`:
  1. Enforce `size` ≤ 100 (EC-12). Throw `InvalidPaginationException` (400) if violated.
  2. Validate `from` ≤ `to` if both provided (EC-11).
  3. Verify ownership.
  4. Query `TransactionRepository` with date filters if provided, else all transactions.
  5. Return `PageResponse<TransactionResponse>`.
- Transactions sorted by `createdAt DESC` always.
- Test: pagination boundaries, date filter (from after to), size > 100 rejection, empty result set (card exists but no transactions).

**Definition of Done:**
- [ ] All unit tests pass.
- [ ] Integration test: seed 15 transactions, page size 10 → page 0 has 10, page 1 has 5.
- [ ] `TransactionResponse` contains no card number in any field.

---

### Task 12: Idempotency Service

**Prompt:**
Implement `IdempotencyService` that intercepts duplicate requests using the `Idempotency-Key` header and returns cached responses for replays.

**Files to CREATE:**
- `src/main/java/com/example/virtualcard/service/IdempotencyService.java`
- `src/test/java/com/example/virtualcard/service/IdempotencyServiceTest.java`

**Details:**
- `IdempotencyService.checkAndStore(String idempotencyKey, UUID userId, String requestHash, Supplier<ResponseEntity<?>> action): ResponseEntity<?>`:
  1. Look up `(idempotencyKey, userId)` in `idempotency_cache`.
  2. If found:
     a. If `requestHash` matches stored hash → return cached response deserialized from `responseBody` with stored `httpStatus`.
     b. If hash differs → throw `IdempotencyConflictException` (422, EC-6).
  3. If not found: execute `action.get()`, store result in cache with `expiresAt = now + 24h`, return result.
- `requestHash`: SHA-256 of serialised request body (hex string, 64 chars).
- Scheduled cleanup: `@Scheduled(cron="0 0 * * * *")` in a `IdempotencyCacheCleanupJob` calls `deleteByExpiresAtBefore(now)`.
- Test: replay same key → same response, replay with different body → 422, first call executes supplier exactly once.

**Definition of Done:**
- [ ] All unit tests pass.
- [ ] Integration test: `POST /cards` with same idempotency key twice → `SELECT COUNT(*) FROM virtual_cards WHERE user_id = ?` = 1.

---

### Task 13: Spring Security Configuration

**Prompt:**
Configure Spring Security for the Virtual Card Service: JWT resource server (RS256), stateless session, per-endpoint authorization rules, and ownership validation.

**Files to CREATE:**
- `src/main/java/com/example/virtualcard/security/SecurityConfig.java`
- `src/main/java/com/example/virtualcard/security/OwnershipValidator.java`
- `src/main/java/com/example/virtualcard/security/JwtUserDetails.java`
- `src/test/java/com/example/virtualcard/security/OwnershipValidatorTest.java`

**Details:**
- `SecurityConfig extends WebSecurityConfigurerAdapter` (Spring Security 6): configure `HttpSecurity` with `.oauth2ResourceServer().jwt()`, `SessionCreationPolicy.STATELESS`, CSRF disabled (stateless API).
- Endpoint rules:
  - `POST /cards` → `hasAnyRole('USER', 'OPS')`
  - `GET /cards/**` → `hasAnyRole('USER', 'OPS')`
  - `PATCH /cards/**` → `hasAnyRole('USER', 'OPS')`
  - `PUT /cards/*/limits` → `hasAnyRole('USER', 'OPS')`
  - `GET /actuator/health` → `permitAll()`
  - All others → `denyAll()`
- `OwnershipValidator.validateOwner(UUID cardId, UUID jwtUserId)`: loads card minimal projection, throws `AccessDeniedException` (403) if `card.userId != jwtUserId` and caller is not `OPS` role.
- `JwtUserDetails`: wraps JWT claims, extracts `sub` as `userId` (UUID), extracts `roles` claim as list.
- JWT public key loaded from `JWT_PUBLIC_KEY` env variable (PEM-encoded RSA public key).
- Test `OwnershipValidatorTest`: own card → no exception; other user's card → `AccessDeniedException`; OPS role → no exception regardless of ownership.

**Definition of Done:**
- [ ] `GET /cards/any-id` without JWT returns 401.
- [ ] Accessing another user's card returns 403 and writes `UNAUTHORISED_ACCESS_ATTEMPT` audit event.
- [ ] Integration test with a test JWT for user A cannot retrieve user B's card.

---

### Task 14: Rate Limiter Configuration

**Prompt:**
Add rate limiting using Bucket4j for the Virtual Card Service. Rate limits are enforced per authenticated user ID, backed by an in-memory store (suitable for single-node; note the limitation).

**Files to CREATE:**
- `src/main/java/com/example/virtualcard/config/RateLimiterConfig.java`
- `src/main/java/com/example/virtualcard/web/RateLimitingFilter.java`

**Details:**
- Write: 60 requests/min per user; Read: 300 requests/min per user; OPS role: 600 read / 120 write per minute.
- Use `Bucket4j` with `Caffeine` cache (local, in-process). Add a comment noting this does not work across multiple instances — a Redis-backed `ProxyManager` would be required for horizontal scaling.
- `RateLimitingFilter extends OncePerRequestFilter`: extract user ID from `SecurityContextHolder`, determine bucket by `(userId, requestType)`, try to consume 1 token; if not available, return 429 with `Retry-After` header (seconds until bucket refills).
- `Retry-After` value: `bucket.getAvailableTokens() == 0 ? refillDurationSeconds : 0`.

**Definition of Done:**
- [ ] Unit test: a user exceeding 60 write requests/min receives HTTP 429 on the 61st request.
- [ ] `Retry-After` header present in 429 response.

---

### Task 15: REST Controllers

**Prompt:**
Create `CardController` and `TransactionController` RESTful controllers, wiring together idempotency, ownership validation, service calls, and audit context (IP address extraction).

**Files to CREATE:**
- `src/main/java/com/example/virtualcard/web/CardController.java`
- `src/main/java/com/example/virtualcard/web/TransactionController.java`

**Details:**

**`CardController`** (`@RestController @RequestMapping("/cards")`):
- `POST /cards` — `@PostMapping`: extract `Idempotency-Key` header (required, UUID format), compute request hash, delegate to `IdempotencyService.checkAndStore(...)` wrapping `CardIssuanceService.issue(...)`. Returns 201 on new card, 200 on replay.
- `GET /cards/{cardId}` — `@GetMapping("/{cardId}")`: validate ownership, return `CardResponse`.
- `PATCH /cards/{cardId}/status` — `@PatchMapping("/{cardId}/status")`: validate ownership, delegate to `CardStateService.transition(...)`. Returns 200.
- `GET /cards/{cardId}/limits` — `@GetMapping("/{cardId}/limits")`: validate ownership, return `CardLimitResponse`.
- `PUT /cards/{cardId}/limits` — `@PutMapping("/{cardId}/limits")`: validate ownership, delegate to `CardLimitService.setLimits(...)`. Returns 200.

**`TransactionController`** (`@RestController @RequestMapping("/cards/{cardId}/transactions")`):
- `GET /cards/{cardId}/transactions` — `@GetMapping`: query params `from`, `to` (ISO-8601), `page` (default 0), `size` (default 20). Validate ownership, delegate to `TransactionService.getTransactions(...)`. Returns `PageResponse<TransactionResponse>`.

- IP address: extract from `HttpServletRequest.getRemoteAddr()` (or `X-Forwarded-For` header if present).
- User ID: extract from `SecurityContextHolder.getContext().getAuthentication()` as `JwtUserDetails.getUserId()`.
- `@Valid` on all request body parameters.

**Definition of Done:**
- [ ] All endpoints return correct HTTP status codes for happy path.
- [ ] `POST /cards` without `Idempotency-Key` header returns 400.
- [ ] Integration tests for all 6 endpoints covering happy path and one error case each.

---

### Task 16: Global Exception Handler

**Prompt:**
Create a `@RestControllerAdvice` global exception handler that maps all domain exceptions to the standard error response format and correct HTTP status codes.

**Files to CREATE:**
- `src/main/java/com/example/virtualcard/exception/GlobalExceptionHandler.java`
- `src/main/java/com/example/virtualcard/exception/VirtualCardException.java`
- (subclasses): `CardNotFoundException`, `MaxCardsExceededException`, `InvalidStateTransitionException`, `CardHasPendingTransactionsException`, `InvalidLimitConfigurationException`, `IdempotencyConflictException`, `InvalidPaginationException`, `InvalidDateRangeException`, `ConcurrentModificationException`, `EncryptionException`

**Details:**
- `VirtualCardException` is the abstract base; takes `errorCode` (String) and message.
- `GlobalExceptionHandler`:
  - `VirtualCardException` subtypes → mapped to their corresponding HTTP status (see error semantics table in Implementation Notes).
  - `MethodArgumentNotValidException` → 400 with validation field errors in `message`.
  - `AccessDeniedException` → 403 `ACCESS_DENIED`.
  - `JwtException` → 401 `TOKEN_INVALID`.
  - Generic `Exception` → 500 `INTERNAL_ERROR`; log full stacktrace at ERROR level; return no sensitive detail in body.
- All responses include `traceId` from `MDC.get("traceId")` (MDC populated by a request filter).
- `timestamp` field in ISO-8601.

**Definition of Done:**
- [ ] Unit test: each exception subclass maps to correct HTTP status and error code.
- [ ] Integration test: invalid JWT → 401; missing `Idempotency-Key` → 400; non-existent card → 404.

---

### Task 17: Comprehensive Integration Tests

**Prompt:**
Create full integration tests for the Virtual Card Lifecycle Service using Spring Boot Test with Testcontainers PostgreSQL, covering the primary user flows and key edge cases.

**Files to CREATE:**
- `src/test/java/com/example/virtualcard/web/CardControllerIntegrationTest.java`
- `src/test/java/com/example/virtualcard/web/TransactionControllerIntegrationTest.java`

**Details:**
- Use `@SpringBootTest(webEnvironment=RANDOM_PORT)` with `@Testcontainers` and a `@Container` PostgreSQL 15 image.
- `CardControllerIntegrationTest` test cases:
  1. Full happy path: create card → get card → freeze → unfreeze → set limits → cancel.
  2. EC-1: Create 10 cards, attempt 11th → 422.
  3. EC-2: Freeze already-frozen card → 409.
  4. EC-5: Concurrent freeze (CompletableFuture) → one 200, one 409.
  5. EC-6: Replay with different body → 422.
  6. MO-6: Idempotent replay → one DB row.
  7. MO-5 security: User B's JWT cannot GET user A's card → 403.
  8. EC-13: Cancel card with pending transactions → 422.
  9. EC-3: Set per-transaction limit > daily limit → 422.
  10. Verify audit_events table row counts after each state change.
- `TransactionControllerIntegrationTest`:
  1. Paginated retrieval: seed 25 transactions, verify pages.
  2. Date filter: transactions outside range excluded.
  3. EC-11: from > to → 400.
  4. EC-12: size=200 → 400.
- Generate test JWTs using `nimbus-jose-jwt` in test scope with a test RSA key pair.

**Definition of Done:**
- [ ] All integration tests pass with Testcontainers.
- [ ] No test touches another test's data (each test uses isolated card/user IDs).
- [ ] Test suite completes in under 3 minutes.
- [ ] Zero log lines containing 13–19 digit numeric runs during test execution (validated by a custom log appender in test configuration).

---

### Task 18: Logging Configuration & PAN Scrubbing Test

**Prompt:**
Configure structured JSON logging (Logback + logstash-logback-encoder) and add a test that verifies no PAN-like data appears in log output during a full card lifecycle flow.

**Files to CREATE:**
- `src/main/resources/logback-spring.xml`
- `src/main/java/com/example/virtualcard/web/RequestLoggingFilter.java`
- `src/test/java/com/example/virtualcard/PanLogScrubbingTest.java`

**Details:**
- `logback-spring.xml`: JSON output in `prod` and `staging` profiles; plain text in `dev` and `test`. Include `traceId`, `userId`, `method`, `path`, `status`, `durationMs` as MDC fields in every log line.
- `RequestLoggingFilter`: populates MDC with `traceId` (UUID), `userId` (from JWT if present), logs `method + path + status + duration` at INFO on response commit. Clears MDC on completion.
- `PanLogScrubbingTest`: captures log output using a custom `ListAppender<ILoggingEvent>`; runs a `POST /cards` → freeze → unfreeze → GET transactions flow; asserts that no captured log message body matches the regex `\b\d{13,19}\b`.

**Definition of Done:**
- [ ] `PanLogScrubbingTest` passes — zero log lines match the PAN regex.
- [ ] Each log line in integration tests contains a `traceId` field.
- [ ] `RequestLoggingFilter` does not log request/response bodies.

---

### Task 19: OpenAPI / Swagger UI Documentation

**Prompt:**
Add `springdoc-openapi` to the project so developers can explore and manually test all Virtual Card Service endpoints via the Swagger UI at `/swagger-ui.html`. Annotate all controllers and DTOs with OpenAPI metadata.

**Files to CREATE or UPDATE:**
- `pom.xml` — add `springdoc-openapi-starter-webmvc-ui` dependency
- `src/main/resources/application.yml` — configure springdoc properties
- `src/main/java/com/example/virtualcard/config/OpenApiConfig.java`
- `src/main/java/com/example/virtualcard/web/CardController.java` — add OpenAPI annotations
- `src/main/java/com/example/virtualcard/web/TransactionController.java` — add OpenAPI annotations
- `src/main/java/com/example/virtualcard/web/dto/*.java` — add `@Schema` annotations on all DTOs

**Details:**
- Dependency: `org.springdoc:springdoc-openapi-starter-webmvc-ui:2.5.0` (compatible with Spring Boot 3.3.x).
- `application.yml` additions:
  ```yaml
  springdoc:
    api-docs:
      path: /api-docs
    swagger-ui:
      path: /swagger-ui.html
      operationsSorter: method
    default-produces-media-type: application/json
  ```
- `OpenApiConfig`: defines an `OpenAPI` bean with:
  - `Info`: title `"Virtual Card Lifecycle API"`, version `"1.0.0"`, description including a security notice ("PAN is never returned in plain text").
  - `SecurityScheme`: named `"bearerAuth"`, type `HTTP`, scheme `bearer`, bearerFormat `JWT`.
  - `SecurityRequirement`: apply `"bearerAuth"` globally to all endpoints.
- `CardController` annotations per endpoint:
  - `@Operation(summary = "...", description = "...")` — concise one-liner summary + description noting idempotency key requirement where applicable.
  - `@ApiResponse(responseCode = "201", description = "Card created")`, plus error codes (400, 401, 403, 409, 422, 429) with `@ApiResponse` for each.
  - `@Parameter(description = "Idempotency-Key header (UUID)", required = true)` on the `Idempotency-Key` header parameter of `POST /cards`.
- `TransactionController` annotations: `@Operation`, `@ApiResponse` for 200/400/401/403; `@Parameter` for `from`, `to`, `page`, `size` query params including valid ranges.
- DTO `@Schema` annotations:
  - `CreateCardRequest.currency`: `@Schema(example = "USD", description = "ISO-4217 currency code")`.
  - `CardResponse.maskedPan`: `@Schema(example = "****-****-****-4321", description = "Masked card number — last 4 digits only")`.
  - `SetLimitsRequest`: each limit field annotated with `@Schema(example = "100.00", description = "...")`.
  - `ErrorResponse`: all fields annotated with examples matching the error format in Implementation Notes.
- Security: expose `/swagger-ui.html` and `/api-docs/**` as `permitAll()` in `SecurityConfig` (update Task 13's security rules). Swagger UI itself does not bypass JWT — it sends the Bearer token entered by the user in the "Authorize" dialog.
- Do **not** expose Swagger UI in the `prod` Spring profile. Use `@ConditionalOnExpression` or `springdoc.swagger-ui.enabled=false` in `application-prod.yml`.

**Definition of Done:**
- [ ] `GET /api-docs` returns valid OpenAPI 3.0 JSON when the service is running.
- [ ] Swagger UI loads at `http://localhost:8080/swagger-ui.html` in `dev` profile.
- [ ] Every endpoint appears in the UI with correct HTTP method, path, parameters, and documented response codes.
- [ ] Clicking "Authorize" and entering a valid Bearer JWT allows executing requests directly from the UI.
- [ ] Swagger UI is **not** accessible when the `prod` profile is active (returns 404 or 403).
- [ ] No raw PAN example appears in any `@Schema(example = ...)` annotation.
