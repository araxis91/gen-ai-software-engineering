# Capstone Challenge Specification — Task 2 (REST API Gateway) & Task 3 (Demo Script)

> Ingest the information from this file, implement the Low-Level Tasks, and generate the code that will satisfy the High and Mid-Level Objectives.
>
> Scope note: this document covers only Task 2 and Task 3 of the capstone challenge ("A REST API gateway" and "A demo script"). Task 1 ("A new agent + configurable rule engine") is out of scope here and is assumed to already exist as a fifth `PipelineAgent` participating in `PipelineSequence` by the time Task 2/3 are implemented — nothing below should require changing `PipelineAgent`'s contract again.
>
> Decisions locked in for this spec (confirmed with the student): the REST API uses **Spring Boot**, and transaction submission is **synchronous** (the HTTP response contains the terminal result, no polling).

---

## Task 2: REST API Gateway

### High-Level Objective

- Wrap the existing file-based banking pipeline behind a synchronous Spring Boot REST API, so transactions can be submitted and results retrieved over HTTP — while `shared/results/` remains the single source of truth shared with the existing CLI (`Integrator`) and the MCP server (`mcp/server.py`).

### Mid-Level Objectives

1. `POST /api/v1/transactions` accepts one transaction as a JSON body, synchronously runs it through the currently configured `PipelineSequence`, persists the terminal result to `shared/results/{transaction_id}.json`, and returns that result in the response body — no polling required.
2. `GET /api/v1/transactions/{transactionId}` returns the terminal result for a previously processed transaction (404 with a machine-readable error body if none exists yet) — same semantics as the MCP server's `get_transaction_status` tool.
3. `GET /api/v1/transactions` returns a summary of every processed transaction (status counts + one row per transaction) — same semantics as `list_pipeline_results`.
4. `GET /api/v1/pipeline/summary` returns the latest `shared/results/pipeline-summary.json` content as JSON.
5. Submitting a `transaction_id` that has already reached a terminal state returns the existing result unchanged (`200`, not re-run) — the API must not violate the idempotency rule already enforced by `Integrator.seedInput()` for the CLI path.
6. Structurally invalid request bodies (missing required fields, unparseable JSON) return `400` with a machine-readable error body — never a raw `500` stack trace.
7. Swagger UI (`springdoc-openapi`) is available in the default/dev run for manual exploration and testing of the API.

### Implementation Notes

- **New dependencies** (added to `homework-6/pom.xml`): `spring-boot-starter-web`, `spring-boot-starter-validation`, `springdoc-openapi-starter-webmvc-ui`, `spring-boot-maven-plugin`. Pin versions via a `spring-boot-dependencies` BOM import (`<dependencyManagement>`) rather than hand-picking each transitive version.
- **This supersedes one line in `agents.md`**: the flat "Do not add a REST API, web server, or Spring dependency" rule under "What the Agent Must Not Do" must be updated once this spec exists — `agents.md`'s own Tech Stack Assumptions section already carves out the exception ("...unless the spec is updated to require an API surface"), so this document is that update. Do not treat the old blanket rule as still binding once this file is present.
- **New package**: `com.homework6.pipeline.api` — `PipelineApiApplication` (Spring Boot `@SpringBootApplication` entry point; a _second_, independent `main`, alongside `Integrator`'s existing CLI entry point — both remain valid ways to run the project), `TransactionController`, `dto/` (request/response records), `GlobalExceptionHandler`.
- **`PipelineExecutor`** (new class, framework-agnostic, lives in the root `com.homework6.pipeline` package — not under `api/` — so it has zero Spring dependency and stays independently unit-testable): extracts the "advance a single `TransactionRecord` through a `PipelineSequence`" loop out of `Integrator`, operating **purely in memory** (no `shared/processing`/`shared/output` file shuffling — that file choreography exists for the batch/file-queue CLI mode; a synchronous HTTP request processes one transaction start-to-finish within one request-response cycle). It must reuse the _same_ agent instances / `PipelineSequence` construction logic `Integrator` already has — do not duplicate the agent registry or duplicate `PipelineSequence` validation in two places. Refactor `Integrator` to delegate its own per-stage advancement to `PipelineExecutor` internally if that removes duplication; do not just copy-paste the loop.
- **Idempotency**: `TransactionController` (or `PipelineExecutor`) must check `shared/results/{transaction_id}.json` via `FileMessageBus` before calling `PipelineExecutor` — if a terminal result already exists, return it as-is (`200`) without re-invoking any agent. This mirrors `Integrator.seedInput()`'s existing skip-and-log-`SKIPPED_DUPLICATE` behavior; reuse `AuditLogger` for the same log line, don't invent a second idempotency mechanism.
- **DTOs**: request/response records under `com.homework6.pipeline.api.dto`, separate from the internal `Transaction`/`TransactionRecord`/`ProcessingState` model — never return a JPA-style internal object directly from a controller. Static factory methods (`TransactionResultResponse.from(TransactionRecord)`) rather than exposing setters.
- **Validation**: Jakarta Bean Validation (`@NotBlank`, `@NotNull`) on `SubmitTransactionRequest` covers only _structural_ completeness (a field is present). Business rules (amount must be positive, currency must be valid ISO 4217) stay inside `TransactionValidatorAgent` — do not duplicate them as bean-validation annotations, or the two can drift out of sync.
- **Error handling**: a single `@RestControllerAdvice` `GlobalExceptionHandler` — bean-validation failures and malformed JSON → `400`; unknown `transactionId` on the `GET` endpoint → `404`; anything else unexpected → `500` with a generic body (never leak a stack trace or internal class name to the client). Every error response uses one consistent `ErrorResponse` shape (`code`, `message`).
- **PII / logging / audit / money rules** from `agents.md` are unchanged and fully apply to this new entry point — the API is a new way _into_ the same agents, not a bypass of any hard rule (masked account numbers in logs, `BigDecimal` for amounts, structured audit log per agent action, etc.).
- **Swagger UI**: enabled by default for local/dev use (no `prod` profile exists yet in this project — if one is introduced later, disable Swagger there, per the project's existing convention for Java REST services).
- **Port/config**: default Spring Boot port `8080`, overridable via `server.port` in `application.yml` or `SERVER_PORT` env var — `demo.sh` (Task 3) must not hardcode a port it can't override.

### Context

#### Beginning context

- The completed homework-6 pipeline as of the "configurable pipeline" work: `Integrator`, `PipelineSequence`, `CliArgs`, 4 `PipelineAgent` implementations (plus the Task 1 fifth agent, assumed present), `FileMessageBus`, `AuditLogger`, and `shared/{input,processing,output,results}`.
- `agents.md`'s current blanket "no REST API" rule, which this spec formally supersedes.
- No `com.homework6.pipeline.api` package yet; `pom.xml` has no web/Spring dependencies yet.

#### Ending context

- A runnable Spring Boot application (`mvn spring-boot:run`, or `java -jar target/banking-pipeline.jar` if packaged with the Spring Boot plugin) exposing the four endpoints above.
- `shared/results/` remains the single shared source of truth read/written by all three entry points into this project: the CLI (`Integrator`), the REST API, and the MCP server (`mcp/server.py`) — a transaction submitted via `curl` and one submitted via `Integrator.run()` are indistinguishable once terminal.
- Swagger UI reachable at `/swagger-ui.html` (or springdoc's default path) for manual testing.
- `agents.md` updated: the stale "Do not add a REST API..." bullet removed or rewritten to reference this spec.

### Low-Level Tasks

#### 1. PipelineExecutor (core, framework-agnostic)

Task: PipelineExecutor
Prompt: "Create `PipelineExecutor` in `com.homework6.pipeline`, extracted from `Integrator`'s existing per-stage advancement loop. It takes a `TransactionRecord` and a `PipelineSequence` (plus the existing agent-name→`PipelineAgent` registry) and advances the record through agents in memory — calling `agent.process(record)` for the sequence's first agent, then `sequence.nextAfter(name)` repeatedly, stopping when the result is terminal (`TransactionStatus.isTerminal()`) or there is no next agent — returning the final `TransactionRecord`. It must not perform any file I/O itself. Refactor `Integrator.runStage` to delegate to this class for the 'advance one record' step, removing the duplicated logic rather than leaving two implementations of the same loop."
File to CREATE: `src/main/java/com/homework6/pipeline/PipelineExecutor.java`
Function to CREATE: `TransactionRecord advance(TransactionRecord record, PipelineSequence sequence, Map<String, PipelineAgent> agentsByName)`
Details: Must be independently unit-testable with a `@TempDir`-free test (no file system involved at all, since this class touches no files); must produce identical results to the existing file-based `Integrator.run()` path for the same input and default sequence (add a test asserting this equivalence for at least one transaction of each terminal status: SETTLED, REJECTED, FLAGGED_FOR_REVIEW, COMPLIANCE_HOLD).

#### 2. Spring Boot bootstrap

Task: Spring Boot Application Bootstrap
Prompt: "Add Spring Boot web/validation/springdoc dependencies to `pom.xml` (via a `spring-boot-dependencies` BOM import for version alignment) and the `spring-boot-maven-plugin` for building an executable jar. Create `PipelineApiApplication` with a standard `@SpringBootApplication` `main` method, separate from (and not replacing) `Integrator.main`. Add `src/main/resources/application.yml` configuring `server.port` (default 8080, overridable) and the springdoc UI path."
File to CREATE: `src/main/java/com/homework6/pipeline/api/PipelineApiApplication.java`, `src/main/resources/application.yml`
Function to CREATE: `static void main(String[] args)`
Details: `mvn spring-boot:run` must start the server without requiring `Integrator`'s CLI path to run first; both `Integrator.main` and `PipelineApiApplication.main` must remain independently runnable from the same jar/classpath after this change.

#### 3. TransactionController + DTOs

Task: Transaction Controller
Prompt: "Create `TransactionController` exposing `POST /api/v1/transactions`, `GET /api/v1/transactions/{transactionId}`, `GET /api/v1/transactions`, and `GET /api/v1/pipeline/summary`, per the Mid-Level Objectives above. The controller must check `shared/results/` for an existing terminal result before invoking `PipelineExecutor` (idempotency), use `FileMessageBus`/`AuditLogger` exactly the way `Integrator` already does, and never expose the internal `Transaction`/`TransactionRecord` model directly — map to/from `dto` records. Annotate with `@Operation`/`@ApiResponse`/`@Schema` (springdoc) for a useful Swagger UI."
File to CREATE: `src/main/java/com/homework6/pipeline/api/TransactionController.java`, `src/main/java/com/homework6/pipeline/api/dto/SubmitTransactionRequest.java`, `src/main/java/com/homework6/pipeline/api/dto/TransactionResultResponse.java`, `src/main/java/com/homework6/pipeline/api/dto/PipelineSummaryResponse.java`
Function to CREATE: `TransactionResultResponse submit(SubmitTransactionRequest request)`, `TransactionResultResponse getById(String transactionId)`, `List<TransactionResultResponse> listAll()`, `PipelineSummaryResponse getSummary()`
Details: `POST` must return `200` with the terminal result body (not `201`, since the "resource" — the result — may already have existed, per the idempotency rule); `GET .../{transactionId}` on an unknown id must produce a `404` handled by `GlobalExceptionHandler`, not an unhandled exception.

#### 4. GlobalExceptionHandler

Task: Global Exception Handler
Prompt: "Create `GlobalExceptionHandler` (`@RestControllerAdvice`) mapping bean-validation failures (`MethodArgumentNotValidException`) and malformed JSON (`HttpMessageNotReadableException`) to `400`, a new `TransactionNotFoundException` to `404`, and any other unhandled exception to a generic `500` — every case returning the same `ErrorResponse` record shape (`code`, `message`). Never let a raw stack trace or internal class name reach the response body."
File to CREATE: `src/main/java/com/homework6/pipeline/api/GlobalExceptionHandler.java`, `src/main/java/com/homework6/pipeline/api/dto/ErrorResponse.java`, `src/main/java/com/homework6/pipeline/exception/TransactionNotFoundException.java`
Function to CREATE: `ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex)`, `ResponseEntity<ErrorResponse> handleNotFound(TransactionNotFoundException ex)`, `ResponseEntity<ErrorResponse> handleUnexpected(Exception ex)`
Details: Log unexpected (`500`) exceptions at `ERROR` with the `transaction_id` if available (via MDC or a request attribute) before returning the generic body; do not log `400`/`404` cases as errors (they're ordinary client input problems, not pipeline failures — `INFO`/`WARN` at most).

#### 5. Swagger / OpenAPI wiring

Task: Swagger UI
Prompt: "Wire `springdoc-openapi-starter-webmvc-ui` so Swagger UI is reachable at the default path with a title/description identifying this as the Banking Pipeline API, and confirm all four endpoints render with example request/response bodies from the `@Schema`/`@ApiResponse` annotations added in Task 3 above."
File to UPDATE: `src/main/resources/application.yml`, `src/main/java/com/homework6/pipeline/api/PipelineApiApplication.java`
Function to CREATE: none (configuration + annotations only)
Details: No endpoints beyond the four specified should appear in the OpenAPI spec — do not let springdoc auto-expose actuator or other internal endpoints without an explicit decision to do so.

---

## Task 3: Demo Script

### High-Level Objective

- A single `demo.sh` that builds the project, starts the REST API from Task 2, submits every transaction from `sample-transactions.json` over HTTP, and displays the results — with zero manual steps and a guaranteed clean shutdown.

### Mid-Level Objectives

1. Running `./demo.sh` from a clean checkout (no pre-built jar, no server already running) ends with every transaction's terminal status printed to the terminal — no prompts, no manual intervention required at any point.
2. The script waits for the API to be genuinely ready (polling a health endpoint) before submitting any transaction, rather than a fixed `sleep N` guess.
3. The script fails loudly and exits non-zero if the build fails, the server doesn't become ready within a timeout, or any transaction submission returns an unexpected HTTP status — it must never silently continue past a failure.
4. Whatever server process the script starts is always terminated on exit — success, failure, or Ctrl-C — leaving no orphaned `java` process behind.
5. Output is a readable, formatted summary (transaction id → status → reason), not a raw JSON dump, so the "zero manual steps" demo is actually presentable to someone watching.

### Implementation Notes

- **Location**: `homework-6/demo.sh`, executable (`chmod +x demo.sh`), `#!/usr/bin/env bash`, `set -euo pipefail` at the top.
- **Dependency check first**: verify `mvn`, `curl`, and `jq` are on `PATH` before doing anything else; if any is missing, print a clear one-line message naming the missing tool and exit non-zero — not a cryptic `curl: command not found` mid-script.
- **Build**: `mvn -q package -DskipTests` (tests already ran in CI/via `mvn verify` separately — the demo shouldn't re-run the full suite and coverage gate every time it's invoked, just build a runnable artifact). Fail loudly (`set -e` already covers this) with the Maven output visible if the build fails.
- **Server lifecycle**: start the API in the background (`java -jar target/banking-pipeline.jar &` after packaging, or `mvn -q spring-boot:run &` if not pre-packaged — pick one and be consistent), capture `$!` as the PID, and register a `trap 'kill "$SERVER_PID" 2>/dev/null || true' EXIT INT TERM` immediately after starting it — before any command that could fail — so the server is always cleaned up regardless of how the script exits.
- **Readiness polling**: loop on `curl -sf http://localhost:${PORT:-8080}/api/v1/pipeline/summary` (or a dedicated health endpoint if Task 2 adds one) every ~1s up to a hard timeout (e.g. 60s total); on timeout, print the last captured server output/log tail and exit non-zero — don't hang forever.
- **Submission**: read `sample-transactions.json` with `jq -c '.[]'` and `POST` each record to `/api/v1/transactions`; check each response's HTTP status (`curl -w '%{http_code}'` or `-o`/`-w` pattern) and abort with a clear message identifying which transaction failed if any POST returns something other than `200`.
- **Summary display**: after all submissions, `curl` `GET /api/v1/pipeline/summary` and pretty-print with `jq` into a simple aligned table (transaction id, status, reason code) plus the overall status counts — avoid dumping the raw response as-is.
- **Idempotent re-run**: since both the API (Task 2, Mid-Level Objective #5) and the CLI already skip already-terminal transactions, re-running `demo.sh` against a `shared/results/` that already has prior results must not error. Decide explicitly (and document in a comment at the top of the script) whether `demo.sh` clears `shared/` first for a guaranteed-fresh demo, or relies on idempotent skip-and-report — either is acceptable, but the behavior must be intentional and documented, not accidental.

### Context

#### Beginning context

- The REST API from Task 2, buildable via `mvn package` and runnable via the packaged jar or `spring-boot:run`, listening on a configurable port (default 8080).
- `sample-transactions.json` at the project root, unchanged.

#### Ending context

- `homework-6/demo.sh`, executable, with no other manual setup required beyond having `mvn`/`curl`/`jq`/a JDK installed.
- Running `cd homework-6 && ./demo.sh` end-to-end builds, starts, exercises, summarizes, and tears down the API in one command.

### Low-Level Tasks

#### 1. demo.sh — build, start, wait, submit, summarize, clean up

Task: Demo Script
Prompt: "Create `demo.sh` at the homework-6 project root implementing, in order: (1) a dependency check for `mvn`/`curl`/`jq`; (2) `mvn -q package -DskipTests` to build a runnable jar; (3) start the packaged jar in the background, capture its PID, and immediately register a `trap` to kill it on `EXIT`/`INT`/`TERM`; (4) poll a health/summary endpoint until it responds successfully or a 60-second timeout is hit, failing loudly on timeout; (5) load `sample-transactions.json` and `POST` each record to `/api/v1/transactions`, aborting with a clear message if any submission returns a non-200 status; (6) `GET /api/v1/pipeline/summary` and print a formatted table of transaction id, status, and reason code, plus overall status counts. The whole script must run with zero prompts and must never leave the server process running after it exits, in any exit path."
File to CREATE: `homework-6/demo.sh`
Function to CREATE: n/a (shell script — structure as named functions: `check_dependencies`, `build`, `start_server`, `wait_for_ready`, `submit_transactions`, `print_summary`, plus a top-level sequence calling them in order)
Details: Must use `set -euo pipefail`; must not hardcode the server port in more than one place (a single `PORT` variable near the top, referenced everywhere else); exit code must be `0` only if the build succeeded, the server became ready, and every transaction submission returned `200` — any other outcome is a non-zero exit, even if the script "finished" (e.g. printed a summary after a partial failure).
