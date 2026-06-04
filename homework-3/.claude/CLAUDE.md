# Claude Code Rules — Virtual Card Lifecycle Service

## Project Identity

This is a **regulated FinTech service** handling virtual payment card lifecycle in Java 21 + Spring Boot 3.3.x. Every decision must be weighed against security, compliance, and auditability before convenience or brevity.

---

## Mandatory First Steps

Before writing any code:
1. Read `specification.md` — it is the authoritative source of truth for all requirements, edge cases, and acceptance criteria.
2. Read `agents.md` — it defines all domain rules, coding conventions, and what the agent must not do.
3. Check which Low-Level Task you are implementing and identify which Mid-Level Objectives it serves.

---

## Hard Rules (Never Violate)

### PAN Safety
- `NEVER` write a log statement that could contain a PAN or card number.
- `NEVER` return a raw PAN in any JSON response.
- `NEVER` store PAN in plain text. Encrypt before any persistence operation.
- When you see a field named `pan`, `cardNumber`, `primaryAccountNumber`, or similar — treat it as PAN-sensitive and apply encryption + masking automatically.
- Regex to detect accidental PAN leakage in log strings: `\b\d{13,19}\b` — ensure none of your generated log messages match this.

### Monetary Precision
- `NEVER` use `double` or `float` for amounts. `BigDecimal` in Java, `BIGINT` in SQL.
- `NEVER` pass a bare numeric amount without an accompanying currency code.

### Ownership & Authorization
- `NEVER` load a resource then check ownership in Java. Always filter at the database query level (e.g., `findByIdAndUserId`).
- `NEVER` trust user-supplied IDs for authorization. Always derive the acting user from the JWT via `SecurityContextHolder`.

### Audit Trail
- `NEVER` skip calling `AuditEventService.record(...)` after a state-changing operation.
- `NEVER` write to `audit_events` table from anywhere other than `AuditEventService`.
- `NEVER` add `DELETE` or `UPDATE` operations on `audit_events`.

---

## Patterns to Follow

### Service Layer Pattern
```java
@Service
@RequiredArgsConstructor  // or manual constructor injection
public class CardStateService {

    private final VirtualCardRepository cardRepository;
    private final AuditEventService auditEventService;

    @Transactional
    public CardResponse transition(UUID cardId, UUID userId,
                                   CardStatus targetStatus, String ipAddress) {
        VirtualCard card = cardRepository.findByIdAndUserId(cardId, userId)
            .orElseThrow(() -> new CardNotFoundException(cardId));

        CardStatus previousStatus = card.getStatus();
        validateTransition(previousStatus, targetStatus);  // throws on invalid

        card.setStatus(targetStatus);
        cardRepository.save(card);

        auditEventService.record(
            mapToEventType(targetStatus), cardId, userId,
            previousStatus.name(), targetStatus.name(), ipAddress, "USER"
        );

        return CardResponse.from(card);
    }
}
```

### Error Response Pattern
```java
// In GlobalExceptionHandler:
@ExceptionHandler(CardNotFoundException.class)
public ResponseEntity<ErrorResponse> handleCardNotFound(CardNotFoundException ex) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(ErrorResponse.of("CARD_NOT_FOUND", ex.getMessage()));
}
```

### DTO Immutability Pattern
- Request DTOs: use Java records or final fields with `@JsonCreator`.
- Response DTOs: use Java records or static factory `from(Entity entity)` method.
- Never expose JPA entities directly in controller responses.

---

## File Placement

| Type | Package |
|------|---------|
| JPA Entities | `com.example.virtualcard.domain` |
| Enums | `com.example.virtualcard.domain.enums` |
| Repositories | `com.example.virtualcard.repository` |
| Services | `com.example.virtualcard.service` |
| Audit | `com.example.virtualcard.audit` |
| Controllers | `com.example.virtualcard.web` |
| DTOs | `com.example.virtualcard.web.dto` |
| Security | `com.example.virtualcard.security` |
| Config | `com.example.virtualcard.config` |
| Exceptions | `com.example.virtualcard.exception` |
| Utilities | `com.example.virtualcard.util` |

---

## What NOT to Generate

- Swagger/OpenAPI is part of the spec (Task 19). Follow the annotations described there; do not add extra undocumented endpoints to the OpenAPI spec.
- Do not generate `@Data` (Lombok) on entities — use explicit getters/setters or records.
- Do not generate `hashCode`/`equals` on JPA entities based on mutable fields — use `id` only.
- Do not generate Spring Data `deleteById` for `VirtualCard` (no hard deletes).
- Do not generate plain-text PAN in any test fixture — use a pre-encrypted value or call the encryption service in `@BeforeEach`.
- Do not generate in-memory H2 database configuration — use Testcontainers PostgreSQL in all tests.

---

## Common Mistakes to Avoid in This Domain

1. Using `@Transactional` on a `private` method — Spring AOP proxies won't intercept it.
2. Calling `save()` inside a loop for batch inserts — use `saveAll()`.
3. Lazy loading outside a transaction — ensure `@Transactional` scope covers any lazy association access.
4. Returning `Optional<T>` from controller methods — always resolve to value or throw.
5. Using string concatenation in log messages — use parameterised logging (`log.info("Card {} frozen", cardId)`).
6. Catching `RuntimeException` and not re-throwing — this silently breaks the transaction.
7. Using `new Date()` or `LocalDateTime.now()` — always use `OffsetDateTime.now(ZoneOffset.UTC)`.

---

## Test Writing Rules

- Test class name mirrors the class under test: `CardIssuanceService` → `CardIssuanceServiceTest`.
- Integration tests end with `IntegrationTest`: `CardControllerIntegrationTest`.
- One `@Test` per behaviour. Do not put multiple assertions for different scenarios in a single test.
- Use `assertThatThrownBy(() -> service.method(...)).isInstanceOf(CardNotFoundException.class)` (AssertJ style).
- Seed data in `@BeforeEach`, clean up via `@Transactional` on the test class (Testcontainers rolls back automatically with `@Transactional`).

---

## Performance Defaults

When generating repository queries:
- Add `Pageable` parameter to any method that could return more than one result.
- Default sort: `createdAt DESC` for time-series data.
- For counts used in validation (e.g., max cards), use `count` queries not `findAll`.
- Annotate read-only service methods with `@Transactional(readOnly = true)`.

---

## Dependency Injection Style

- Use **constructor injection** always. No `@Autowired` on fields.
- If `@RequiredArgsConstructor` (Lombok) is available, use it. Otherwise, write the constructor explicitly.
- No circular dependencies — if you detect a circular dependency, restructure rather than using `@Lazy`.
