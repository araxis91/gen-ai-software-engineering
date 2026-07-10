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

## 2. Run the full pipeline

```bash
mvn -q dependency:build-classpath -Dmdep.outputFile=/tmp/homework6-cp.txt
java -cp "target/classes:$(cat /tmp/homework6-cp.txt)" com.homework6.pipeline.Integrator
```

This loads `sample-transactions.json`, runs every transaction through the four-stage pipeline, and writes one terminal result per transaction to `shared/results/`, plus `shared/results/pipeline-summary.json`. Re-running is safe — already-settled/rejected/flagged/held transactions are skipped, not reprocessed.

Inside Claude Code (with `homework-6/` as the project root), the same thing is available as `/run-pipeline`, which also clears `shared/` first and reports rejected/flagged/held transactions.

## 3. Validate transactions without running the full pipeline (dry run)

```bash
java -cp "target/classes:$(cat /tmp/homework6-cp.txt)" com.homework6.pipeline.cli.ValidateTransactionsCli
```

Runs only the `TransactionValidatorAgent`, entirely in memory — no `shared/` files are touched. Prints a table of valid/invalid transactions with reason codes. Available in Claude Code as `/validate-transactions`.

## 4. Run the test suite and check coverage

```bash
mvn verify
```

`mvn verify` runs all unit + integration tests, generates a JaCoCo report, and **fails the build if line coverage drops below 80%** (currently ~91%). Open the HTML report:

```bash
open target/site/jacoco/index.html
```

## 5. Coverage-gate hook (blocks `git push` below 80% coverage)

The hook lives at `scripts/coverage-gate.sh`, wired into `.claude/settings.json` as a `PreToolUse` hook on the `Bash` tool. It only activates on `git push` commands (everything else passes through instantly) and runs `mvn verify` before letting the push through.

To test the script directly, without needing a `git push`:

```bash
echo '{"tool_input":{"command":"git push origin homework-6-submission"}}' | bash scripts/coverage-gate.sh; echo "exit=$?"
```

To see it fire as a real Claude Code hook, launch Claude Code with `homework-6/` as the project root (`cd homework-6 && claude`) and ask it to run `git push` — it only intercepts pushes made through Claude's own Bash tool calls, not ones you type directly in a plain terminal.

## 6. Custom MCP server + context7

```bash
cd mcp
python3 -m venv .venv
.venv/bin/pip install -r requirements.txt
cd ..
```

`.mcp.json` at the project root configures both `context7` and the custom `pipeline-status` server (`mcp/server.py`). Open a Claude Code session rooted at `homework-6/`, approve both servers when prompted, and confirm they're connected with `/mcp`. Then, with `shared/results/` populated (step 2), you can ask Claude things like:

- "What's the status of TXN001?" → calls the `get_transaction_status` tool
- "Summarize the pipeline run" → calls `list_pipeline_results` or reads the `pipeline://summary` resource
- "Use context7 to look up FastMCP resources" → a real context7 documentation lookup

## 7. Regenerate the specification (optional)

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
