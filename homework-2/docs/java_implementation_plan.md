# Homework 2 Java Implementation Plan
## Problem statement
Implement the Homework 2 support-ticket system in Java (Spring Boot) so it satisfies all requirements in `TASKS.md:1`, including multi-format imports, auto-classification, >85% test coverage, multi-level documentation, integration/performance testing, and required deliverables.
## Current state
The repository currently contains `TASKS.md`, minimal starter folders (`src`, `demo`), and `docs/screenshots` but no Java implementation yet. The assignment requires REST APIs, importers (CSV/JSON/XML), rule-based classification, robust validation/errors, high test coverage, and documentation artifacts.
## Proposed implementation approach
Use Java 25 + Spring Boot 3 with a layered structure (controller, service, domain, repository, importer, classification, docs/tests assets). Persist tickets with JPA (H2 for local/dev, optional PostgreSQL profile). Use Bean Validation for request validation, a global exception handler for consistent errors, JaCoCo for coverage, and JUnit 5 + Spring test tooling for automated tests.
## Step-by-step plan
Progress legend: ⬜ not completed, ✅ completed
### 1) Initialize project skeleton and dependencies
Status: ✅
1. Create a Spring Boot project with modules/dependencies: `spring-boot-starter-web`, `spring-boot-starter-validation`, `spring-boot-starter-data-jpa`, `spring-boot-starter-test`, `jackson-dataformat-xml`, `commons-csv` (or OpenCSV), `lombok` (optional), and `jacoco-maven-plugin`.
2. Set Java version to 25 and configure build plugin defaults in `pom.xml` (or Gradle equivalent).
3. Create baseline package structure:
   - `ticketing.api`
   - `ticketing.domain`
   - `ticketing.service`
   - `ticketing.importer`
   - `ticketing.classification`
   - `ticketing.config`
   - `ticketing.error`
4. Add `application.yml` profiles (`default`, `test`) and database setup (H2 for development/tests).
### 2) Define domain model and validation rules
Status: ✅
1. Implement `Ticket` entity/model with fields required by `TASKS.md` and enum types:
   - `Category`, `Priority`, `Status`, `Source`, `DeviceType`.
2. Represent metadata as embeddable object (e.g., `TicketMetadata`) or JSON column depending on DB choice.
3. Apply Bean Validation rules:
   - email format for `customerEmail`
   - subject length 1-200
   - description length 10-2000
   - non-null required fields and enums.
4. Add DTOs for API input/output to separate persistence entity from request schema.
5. Add mapper layer (manual mapper or MapStruct) between DTOs and entities.
### 3) Implement persistence and query/filtering foundation
Status: ✅
1. Create `TicketRepository` extending `JpaRepository<Ticket, UUID>`.
2. Add filtering support for category, priority, status, customer id/email, and date ranges using Spring Data Specifications or custom query methods.
3. Add indexes for high-frequency filter fields (`category`, `priority`, `status`, `createdAt`) to support later performance tests.
### 4) Build core ticket CRUD API
Status: ✅
1. Implement endpoints:
   - `POST /tickets`
   - `GET /tickets`
   - `GET /tickets/{id}`
   - `PUT /tickets/{id}`
   - `DELETE /tickets/{id}`.
2. Add pagination + filtering parameters on `GET /tickets`.
3. Ensure status codes and behavior:
   - `201` for create
   - `200` for reads/updates
   - `204` for delete
   - `404` for missing ticket
   - `400` for validation errors.
4. Set `createdAt` and `updatedAt` automatically; set `resolvedAt` only when status transitions to resolved/closed.
### 5) Implement bulk import pipeline (CSV/JSON/XML)
Status: ✅
1. Define a common importer contract (e.g., `TicketImportParser`) with implementations:
   - `CsvTicketImportParser`
   - `JsonTicketImportParser`
   - `XmlTicketImportParser`.
2. Implement format detection from file extension/content type.
3. Parse input into DTOs, validate each record, and collect row-level errors without stopping entire batch.
4. Implement `POST /tickets/import` accepting multipart file upload.
5. Return import summary payload:
   - total records
   - successful
   - failed
   - list of failed records with record index/id + error details.
6. Add graceful malformed-file handling with explicit, user-friendly error messages.
### 6) Implement auto-classification service
Status: ⬜
1. Build rule-based keyword classifier for category and priority according to assignment rules.
2. Implement priority keyword precedence so urgent terms dominate high/medium/low.
3. Return classification result structure:
   - category
   - priority
   - confidence (0-1)
   - reasoning
   - matched keywords.
4. Add endpoint `POST /tickets/{id}/auto-classify` that stores classification output.
5. Support auto-run during ticket creation via optional flag (query param or request field).
6. Store confidence score and classifier reasoning/keywords in ticket metadata or dedicated classification fields.
7. Allow manual override via update endpoint while preserving audit trail.
8. Log every classification decision (ticket id, input snippet/hash, matched rules, output).
### 7) Standardize error handling and API contracts
Status: ⬜
1. Implement global exception handler (`@RestControllerAdvice`) for validation, parse, and not-found errors.
2. Define consistent error JSON shape (`timestamp`, `status`, `error`, `message`, `path`, optional `details`).
3. Map all known failures to correct HTTP codes (`400`, `404`, `415`, `500` as needed).
4. Add request/response examples in test fixtures to lock contract behavior.
### 8) Implement required automated tests (>85% coverage)
Status: ⬜
1. Configure JaCoCo report generation and fail build when coverage <85%.
2. Create test suites corresponding to assignment intent:
   - API endpoint tests (`TicketApiTest`)
   - model/validation tests (`TicketValidationTest`)
   - CSV import tests (`CsvImportTest`)
   - JSON import tests (`JsonImportTest`)
   - XML import tests (`XmlImportTest`)
   - classification tests (`ClassificationServiceTest`)
   - integration workflow tests (`TicketWorkflowIntegrationTest`)
   - performance benchmarks (`TicketPerformanceTest`).
3. Use fixtures for valid/invalid sample files and edge cases.
4. Add negative tests for malformed files, invalid enums, missing required fields, invalid email, and overly long text.
5. Verify error code + message content for all major failure paths.
### 9) Implement integration and performance scenarios
Status: ⬜
1. Add end-to-end test for full lifecycle: create → auto-classify → update → resolve/close → fetch → delete.
2. Add import + auto-classification verification test.
3. Add concurrent test for 20+ simultaneous requests (e.g., `ExecutorService` + REST-assured/TestRestTemplate).
4. Add combined filtering tests for category + priority.
5. Define performance assertions (e.g., response time thresholds) suitable for local CI stability.
### 10) Prepare sample data deliverables
Status: ⬜
1. Generate and commit sample files:
   - `sample_tickets.csv` (50)
   - `sample_tickets.json` (20)
   - `sample_tickets.xml` (30)
   - invalid data files for negative tests.
2. Ensure samples cover all categories/priorities/statuses and metadata source/device variants.
3. Reuse these files in import and integration tests.
### 11) Produce required documentation set
Status: ⬜
1. Create `README.md` for developers with setup, run, tests, structure, and Mermaid architecture diagram.
2. Create `API_REFERENCE.md` with endpoint contracts, schemas, errors, and cURL examples.
3. Create `ARCHITECTURE.md` with component + data flow (Mermaid sequence), design trade-offs, security/performance notes.
4. Create `TESTING_GUIDE.md` with test pyramid Mermaid diagram, run instructions, fixture locations, manual checklist, benchmark summary.
5. Ensure at least 3 Mermaid diagrams total across docs.
6. Record which AI model/tool was used per documentation file to satisfy the “different models for different doc types” requirement.
### 12) Final validation and submission packaging
Status: ⬜
1. Run full test suite and confirm coverage report >85%.
2. Save coverage screenshot to `docs/screenshots/test_coverage.png`.
3. Validate API manually with representative cURL commands.
4. Verify all deliverables exist and naming matches assignment expectations.
5. Prepare final submission checklist:
   - source code
   - coverage report + screenshot
   - sample data files
   - documentation set.
## Risk controls and quality gates
- Add strict DTO validation to avoid bad data entering classification/import flows.
- Keep import parsing isolated per format to simplify debugging and testability.
- Use deterministic rule ordering in classifier to avoid flaky outcomes.
- Enforce coverage threshold in CI/local build to prevent regression under 85%.
- Include contract tests for error payloads to keep API behavior stable.
