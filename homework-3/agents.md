# Agent Guidelines — Virtual Card Lifecycle Service

## Purpose

This document configures AI coding agents (Claude Code, GitHub Copilot, Cursor) working in this repository. Agents **must** read this file before generating or modifying any code. These rules are non-negotiable in the context of a regulated FinTech service.

---

## Tech Stack Assumptions

- **Language**: Java 21 (LTS). Use records, sealed classes, pattern matching where appropriate.
- **Framework**: Spring Boot 3.3.x, Spring Security 6, Spring Data JPA.
- **Database**: PostgreSQL 15 via JDBC; Flyway for schema migrations.
- **Build**: Maven 3.9+. Do not introduce Gradle unless explicitly asked.
- **Testing**: JUnit 5, Mockito 5, Spring Boot Test, Testcontainers.
- **Serialisation**: Jackson 2.x (Spring default). Use `@JsonProperty` for explicit naming.
- **Money**: `BigDecimal` for calculations; `long` (minor currency units) for storage. Never `float` or `double`.
- **UUIDs**: `java.util.UUID` everywhere for entity IDs. Never auto-increment integers.
- **Time**: `java.time.OffsetDateTime` everywhere. Never `java.util.Date` or `java.sql.Timestamp`.
- **API Documentation**: every Java REST API service must include `springdoc-openapi-starter-webmvc-ui` by default. Swagger UI must be available in `dev` and `staging` profiles and disabled in `prod`. All controllers and DTOs must be annotated with `@Operation`, `@ApiResponse`, `@Parameter`, and `@Schema` as part of the initial implementation, not as an afterthought.

---

## Domain Rules (Banking)

### PAN (Primary Account Number) — CRITICAL
- **NEVER** log a PAN, even at `TRACE` or `DEBUG` level.
- **NEVER** include a raw PAN in any API response body, error message, or exception message.
- **NEVER** pass a plain PAN across service boundaries or store it plain-text anywhere.
- When generating a PAN, encrypt it immediately. The plain-text PAN must exist in memory for the minimum possible duration and must not be assigned to a field that could be accidentally serialised.
- Masking is mandatory for display: `****-****-****-LAST4`. Use `PanMaskingUtil.mask()`.
- Luhn check required on all generated PANs. Use `LuhnValidator.isValid()`.

### CVV
- **NEVER** return CVV in any API response after the issuance moment (and even at issuance, consider whether returning it is in scope).
- Store as BCrypt hash, never plain-text.
- Do not log CVV.

### Money
- All monetary values stored in the database as `BIGINT` in minor currency units (cents for USD).
- Convert to/from `BigDecimal` using `MoneyConverter`. Do not do inline arithmetic with `int`/`long` without going through the converter.
- Currency must always travel with an amount; never pass a bare `long` without the ISO-4217 currency code.
- Rounding: `RoundingMode.HALF_UP` unless explicitly specified otherwise.

### Idempotency
- All state-changing endpoints (`POST`, `PATCH`, `PUT`) must check the `Idempotency-Key` header.
- Never execute the business action before checking idempotency. Check first, act second.
- On replay, return the cached response verbatim. Do not re-validate business rules.

### Card State Machine
```
ACTIVE  --(freeze)-->  FROZEN  --(unfreeze)-->  ACTIVE
ACTIVE  --(cancel)-->  CANCELLED
FROZEN  --(cancel)-->  CANCELLED
```
Any transition not listed above must throw `InvalidStateTransitionException` (HTTP 409). The agent must not add new states or transitions without updating `specification.md`.

---

## Code Style & Conventions

### Naming
- Package root: `com.example.virtualcard`.
- Entity classes: PascalCase, no `Entity` suffix (e.g., `VirtualCard`, not `VirtualCardEntity`).
- DTO classes: suffix with `Request` (inbound) or `Response` (outbound). No `Dto` suffix.
- Service interfaces: not required; use concrete classes unless there is a clear testability reason for an interface.
- Repository methods: follow Spring Data naming conventions. Custom JPQL queries annotated with `@Query`.
- Enum names: ALL_CAPS values (`ACTIVE`, `FROZEN`, `CANCELLED`).

### Error Handling
- Throw domain-specific exceptions (subclasses of `VirtualCardException`) from the service layer.
- Let `GlobalExceptionHandler` handle mapping to HTTP responses.
- Never catch-and-swallow exceptions silently.
- Never log a full exception stacktrace at WARN; use ERROR for unexpected exceptions, INFO for expected business rejections.

### Transactions
- `@Transactional` at the **service layer only**. Never on repositories or controllers.
- Use `@Transactional(readOnly = true)` for query-only methods.
- Audit event recording must occur **within the same transaction** as the state change.

### Validation
- Use Jakarta Bean Validation annotations on DTO fields.
- `@Valid` on controller method parameters.
- Custom business rule validation belongs in the service layer, not in controllers or entities.

### Security
- Extract user identity from `SecurityContextHolder`; never trust a `userId` from the request body or path parameter for ownership decisions.
- Ownership check: always use `VirtualCardRepository.findByIdAndUserId(cardId, userId)` — never load the card first and check user ID in Java.
- Sensitive fields (`cardPanEncrypted`, `cvvHash`) must be annotated `@JsonIgnore` on entity classes to prevent accidental serialisation.

---

## Testing & Verification Expectations

### What to test
- **Unit tests**: every service method; every branch including all edge cases from `specification.md` (EC-1 through EC-15); all utility functions.
- **Integration tests**: every HTTP endpoint; full Flyway migration runs before each test class; Testcontainers PostgreSQL.
- **Security tests**: 401/403 paths for each protected endpoint.
- **PAN scrubbing test**: must pass before any PR is merged.

### How to structure tests
- Arrange / Act / Assert pattern. No comments for obvious setups.
- Each test tests exactly one behaviour. Name tests as `methodName_scenario_expectedOutcome()`.
- Use `@BeforeEach` for common test data setup. Do not share mutable state between tests.
- Mocks: `@ExtendWith(MockitoExtension.class)`. Only mock external dependencies; do not mock the class under test.

### Definition of Done (universal)
Before marking any task complete, verify:
- [ ] All existing tests still pass (`mvn test`).
- [ ] New code has at least one unit test covering the happy path and one covering failure.
- [ ] No PAN-like pattern (13–19 digits) exists in any new log statement.
- [ ] No `TODO`, `FIXME`, or `HACK` left in committed code.
- [ ] Flyway migration scripts are immutable once committed; create a new `Vn__` file for schema changes.

---

## How the Agent Should Treat Edge Cases

1. **Always implement edge case handling proactively.** Do not wait for a test to fail. Every edge case listed in `specification.md` must be handled, even if not explicitly mentioned in the task prompt.

2. **Fail fast and loud at startup.** Missing required environment variables (`CARD_ENCRYPTION_KEY`, `JWT_PUBLIC_KEY`, `DB_URL`) must cause immediate application startup failure with a descriptive error. Never fall back to insecure defaults.

3. **Concurrent modification**: wherever optimistic locking is used (`@Version`), catch `ObjectOptimisticLockingFailureException` and translate to a 409 response with `CONCURRENT_MODIFICATION` error code. Never silently retry.

4. **Empty and null inputs**: never assume a non-null value unless a `@NotNull` constraint is present. Treat empty optional parameters (e.g., no date filter) as "no filter" — return all records.

5. **Pagination**: always enforce a maximum page size of 100. Never return unbounded result sets.

6. **Fraud-ish patterns (EC-15)**: if a request field that is not expected to contain a PAN contains a 13–19 digit numeric string, log the sanitised event at WARN and return 400 with `SUSPICIOUS_INPUT_DETECTED`. Do not echo the suspicious value back in the error response.

7. **Idempotency conflict (EC-6)**: do not expose the cached response body in the conflict error. Return only the error code and message.

---

## Security & Compliance Constraints (Non-Negotiable)

- **Audit trail is sacred**: never delete or update rows in `audit_events`. The service account does not have `DELETE`/`UPDATE` privileges on this table.
- **No hard deletes**: cards are logically cancelled, never deleted. Repositories must not expose `deleteById` for `VirtualCard`.
- **Encryption key rotation**: if `CARD_ENCRYPTION_KEY` changes, existing ciphertexts are not automatically migrated. The agent should not implement re-encryption unless explicitly asked; instead, log a startup warning if a key version mismatch is detected.
- **Logging**: structured JSON in non-dev environments. Every log line must carry `traceId` and `userId` (from MDC). Never log raw HTTP request/response bodies.
- **Dependency updates**: do not upgrade transitive dependencies without verifying the CVE database. If a security vulnerability is found in a dependency, report it in a comment before patching.
- **No `@SuppressWarnings("unchecked")` or `@SuppressWarnings("serial")` without a comment explaining why.**

---

## What the Agent Must Not Do

- Do not add endpoints not described in `specification.md` without updating the spec first.
- Do not enable Swagger UI in the `prod` Spring profile — use `springdoc.swagger-ui.enabled=false` in `application-prod.yml`.
- Do not use `System.out.println` — use `@Slf4j` and the logger.
- Do not hardcode secrets, keys, or passwords in code or test files. Use environment variables or Testcontainers-managed credentials.
- Do not use `Thread.sleep` in production code paths.
- Do not add Lombok unless explicitly approved; write boilerplate by hand or use Java records.
- Do not use `Optional.get()` without checking `isPresent()` — use `orElseThrow()` with a typed exception.
- Do not use `catch (Exception e)` in service layer — catch specific exceptions.
- Do not expose Spring Boot Actuator endpoints other than `/actuator/health` in this specification. 
