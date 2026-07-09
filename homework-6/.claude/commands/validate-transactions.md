---
description: Validate all transactions in sample-transactions.json without running the full pipeline (dry run).
---

# /validate-transactions

Validate all transactions in `sample-transactions.json` without processing them through the rest of the pipeline.

## Steps

1. **Run the validator in dry-run mode.** This uses only `TransactionValidatorAgent` in memory — it never touches `shared/input`, `shared/processing`, `shared/output`, or `shared/results`:
   ```bash
   mvn -q compile
   mvn -q dependency:build-classpath -Dmdep.outputFile=/tmp/homework6-cp.txt
   java -cp "target/classes:$(cat /tmp/homework6-cp.txt)" com.homework6.pipeline.cli.ValidateTransactionsCli
   ```
   Pass a different file path as the first argument to validate a different transaction file, e.g. `... ValidateTransactionsCli path/to/other-transactions.json`.
2. **Report**: total count, valid count, invalid count, and the specific reason for each rejection (the CLI already prints `reason_code` + `reason` per transaction — surface these, don't re-derive them).
3. **Show a table of results** with columns `transaction_id`, `result` (VALID/INVALID), `reason_code`, `reason` — the CLI's own output is already in this shape, so relay it directly rather than reformatting from scratch.

## Notes

- This is strictly a validation dry run — do not run `Integrator`, and do not report on fraud/compliance/settlement outcomes here (that's `/run-pipeline`'s job). A transaction that passes here can still be rejected, flagged, or held later in the real pipeline.
- Do not print raw `source_account`/`destination_account` values beyond what the CLI table already shows (it never includes account numbers — only `transaction_id`).
