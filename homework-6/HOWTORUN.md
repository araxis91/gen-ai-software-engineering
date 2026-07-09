# How to Run — AI-Powered Multi-Agent Banking Pipeline

## Prerequisites

- Java 21+ (JDK)
- Maven 3.9+
- Python 3.10+ (for the custom MCP server)
- Node.js + `npx` (for the `context7` MCP server)
- Claude Code, launched with `homework-6/` as the project root, if you want to use the `/write-spec`, `/run-pipeline`, `/validate-transactions` skills or the coverage-gate hook

## 1. Build the pipeline

```bash
cd homework-6
mvn -q compile
```

## 2. Run the full pipeline (CLI)

```bash
mvn -q dependency:build-classpath -Dmdep.outputFile=/tmp/homework6-cp.txt
java -cp "target/classes:$(cat /tmp/homework6-cp.txt)" com.homework6.pipeline.Integrator
```

This loads `sample-transactions.json`, runs every transaction through the four-stage pipeline, and writes one terminal result per transaction to `shared/results/`, plus `shared/results/pipeline-summary.json`. Re-running is safe — already-settled/rejected/flagged/held transactions are skipped, not reprocessed.

Inside Claude Code (with `homework-6/` as the project root), the same thing is available as `/run-pipeline`, which also clears `shared/` first and reports rejected/flagged/held transactions.

### Configuring which agents run, and in what order

`Integrator` supports three flags for this — pass `--help` to see them:

```bash
java -cp "target/classes:$(cat /tmp/homework6-cp.txt)" com.homework6.pipeline.Integrator --help
```

- `--sequence=agent1,agent2,...` — run a custom order, or a subset of stages. Example: `--sequence=transaction_validator,settlement_processor` skips fraud detection and compliance entirely.
- `--stage=agentName` — run exactly one named stage on whatever's currently queued for it, instead of a full pass. Useful for driving the pipeline one step at a time and inspecting `shared/output/` in between.
- `--file=path` — load a different transactions file.

Reordering changes real outcomes — e.g. running `compliance_checker` before `fraud_detector`/`transaction_validator` still catches denylisted accounts, but skipping a stage entirely means its rule is never enforced. This is by design: `PipelineAgent` implementations never decide what runs next (see `agents.md`), so the sequence is the only thing controlling pipeline wiring.

## 3. Run the REST API Gateway (specification-capstone.md Task 2)

```bash
mvn -q spring-boot:run
```

Or build and run the standalone executable jar (separate from the CLI's plain jar):

```bash
mvn -q package -DskipTests
java -jar target/banking-pipeline-exec.jar
```

Both start a Spring Boot server on `http://localhost:8080` (override with `SERVER_PORT`). Try it:

```bash
# submit a transaction -- runs synchronously, returns the terminal result
curl -s -X POST http://localhost:8080/api/v1/transactions -H "Content-Type: application/json" -d '{
  "transaction_id": "APITX001",
  "timestamp": "2026-03-16T09:00:00Z",
  "source_account": "ACC-1001",
  "destination_account": "ACC-2001",
  "amount": "1500.00",
  "currency": "USD",
  "transaction_type": "transfer",
  "metadata": {"channel": "online", "country": "US"}
}' | python3 -m json.tool

# look it up again
curl -s http://localhost:8080/api/v1/transactions/APITX001 | python3 -m json.tool

# list everything processed so far
curl -s http://localhost:8080/api/v1/transactions | python3 -m json.tool

# the latest pipeline-summary.json, over HTTP
curl -s http://localhost:8080/api/v1/pipeline/summary | python3 -m json.tool
```

Open `http://localhost:8080/swagger-ui.html` for interactive API docs.

The API writes to the same `shared/results/` files the CLI (`Integrator`) and the MCP server (`mcp/server.py`) read/write — submit a transaction via `curl`, then ask the MCP server for its status, or run `/run-pipeline` afterward and see it skipped as already-settled. All three are the same source of truth.

To point the API at a different `shared/` root (e.g. for a scratch demo), set `PIPELINE_SHARED_DIR`:

```bash
PIPELINE_SHARED_DIR=/tmp/demo-shared mvn -q spring-boot:run
```

## 4. Validate transactions without running the full pipeline (dry run)

```bash
java -cp "target/classes:$(cat /tmp/homework6-cp.txt)" com.homework6.pipeline.cli.ValidateTransactionsCli
```

Runs only the `TransactionValidatorAgent`, entirely in memory — no `shared/` files are touched. Prints a table of valid/invalid transactions with reason codes. Available in Claude Code as `/validate-transactions`.

## 5. Run the test suite and check coverage

```bash
mvn verify
```

`mvn verify` runs all unit + integration tests (including `@WebMvcTest`/`@SpringBootTest` tests for the REST API), generates a JaCoCo report, and **fails the build if line coverage drops below 80%** (currently ~92%). Open the HTML report:

```bash
open target/site/jacoco/index.html
```

## 6. Coverage-gate hook (blocks `git push` below 80% coverage)

The hook lives at `scripts/coverage-gate.sh`, wired into `.claude/settings.json` as a `PreToolUse` hook on the `Bash` tool. It only activates on `git push` commands (everything else passes through instantly) and runs `mvn verify` before letting the push through.

To test the script directly, without needing a `git push`:

```bash
echo '{"tool_input":{"command":"git push origin homework-6-submission"}}' | bash scripts/coverage-gate.sh; echo "exit=$?"
```

To see it fire as a real Claude Code hook, launch Claude Code with `homework-6/` as the project root (`cd homework-6 && claude`) and ask it to run `git push` — it only intercepts pushes made through Claude's own Bash tool calls, not ones you type directly in a plain terminal.

## 7. Custom MCP server + context7

```bash
cd mcp
python3 -m venv .venv
.venv/bin/pip install -r requirements.txt
cd ..
```

`.mcp.json` at the project root configures both `context7` and the custom `pipeline-status` server (`mcp/server.py`). Open a Claude Code session rooted at `homework-6/`, approve both servers when prompted, and confirm they're connected with `/mcp`. Then, with `shared/results/` populated (step 2 or 3), you can ask Claude things like:

- "What's the status of TXN001?" → calls the `get_transaction_status` tool
- "Summarize the pipeline run" → calls `list_pipeline_results` or reads the `pipeline://summary` resource
- "Use context7 to look up FastMCP resources" → a real context7 documentation lookup

## 8. Regenerate the specification (optional)

Inside Claude Code, `/write-spec` regenerates `specification.md` from the mandated template, cross-checking that every edge case in `sample-transactions.json` is covered by a Low-Level Task.

## Quick demo script (all of the above, in order)

```bash
cd homework-6
mvn -q compile
mvn -q dependency:build-classpath -Dmdep.outputFile=/tmp/homework6-cp.txt
java -cp "target/classes:$(cat /tmp/homework6-cp.txt)" com.homework6.pipeline.Integrator
cat shared/results/pipeline-summary.json
mvn verify
open target/site/jacoco/index.html
```
