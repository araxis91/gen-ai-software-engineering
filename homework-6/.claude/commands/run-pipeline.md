---
description: Run the multi-agent banking pipeline end-to-end and summarize the results.
---

# /run-pipeline

Run the multi-agent banking pipeline end-to-end.

## Steps

1. **Check that `sample-transactions.json` exists** at the project root. If it's missing, stop and tell the user — there's nothing to process.
2. **Clear `shared/` directories.** Remove the contents of `shared/input/`, `shared/processing/`, `shared/output/`, and `shared/results/` (keep the directories themselves / `.gitkeep` files) so this is a clean run, not an idempotent skip of a prior run's results. Use:
   ```bash
   find shared/input shared/processing shared/output shared/results -type f ! -name '.gitkeep' -delete
   ```
3. **Run the pipeline**:
   ```bash
   mvn -q compile
   mvn -q dependency:build-classpath -Dmdep.outputFile=/tmp/homework6-cp.txt
   java -cp "target/classes:$(cat /tmp/homework6-cp.txt)" com.homework6.pipeline.Integrator
   ```
   (If a `banking-pipeline.jar` has already been built via `mvn package`, `java -jar target/banking-pipeline.jar` also works.)
4. **Show a summary of results from `shared/results/`.** Read `shared/results/pipeline-summary.json` and present the `counts_by_status` breakdown as a short table (status → count), plus the total transaction count.
5. **Report any transactions that were rejected and why.** For every result file in `shared/results/` (excluding `pipeline-summary.json`) whose `data.state.status` is `REJECTED`, `FLAGGED_FOR_REVIEW`, or `COMPLIANCE_HOLD`, list its `transaction_id`, `status`, and `reason_code`/`reason` in a short table. Do not print masked account numbers in plaintext beyond what's already in the result JSON.

## Notes

- This command is meant for a full, clean demo run — it deliberately wipes `shared/` first. If the user wants to test idempotency/re-run behavior instead, skip step 2 and just re-run step 3, then point out which transactions were skipped as `SKIPPED_DUPLICATE` in the console log.
- If `mvn compile` fails, stop and show the compiler error — do not attempt to run a stale `Integrator` build.
