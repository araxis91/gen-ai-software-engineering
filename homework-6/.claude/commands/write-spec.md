---
description: Generate or refresh specification.md for this banking pipeline capstone, following the mandated 5-section template.
---

# /write-spec

You are **Agent 1 (Specification)** for the AI-Powered Multi-Agent Banking Pipeline capstone. Produce (or refresh) `specification.md` at the project root, following the exact template below. Do not start writing pipeline code as part of this command — this command only produces/updates the specification.

## Inputs

- `$ARGUMENTS` — optional free-text description of what's changing or being added (e.g. "add a settlement retry agent", "tighten the fraud threshold to $5,000"). If empty, regenerate the spec for the pipeline as currently described in the existing `specification.md`, `agents.md`, and `sample-transactions.json`.
- Always read, before writing anything:
  1. `TASKS.md` — the assignment brief; re-check which required sections/fields it mandates.
  2. `agents.md` — project conventions (Java 21, `BigDecimal` money, PII masking, file-based IPC, idempotency, coverage gate).
  3. `sample-transactions.json` — the concrete input data; every edge case it contains (invalid currency, negative amount, near-threshold value, cross-border/off-hours timing) must be traceable to a Low-Level Task.
  4. The current `specification.md`, if one exists — treat `$ARGUMENTS` as a diff on top of it, not a from-scratch rewrite, unless the user asks to start over.

## Required template

Write `specification.md` with exactly these five top-level sections, in this order:

```markdown
# [Project/Feature Name] Specification

> Ingest the information from this file, implement the Low-Level Tasks, and generate the code that will satisfy the High and Mid-Level Objectives.

## High-Level Objective
- [One sentence describing what the system/feature does]

## Mid-Level Objectives
- [4-5 concrete, testable requirements — each independently verifiable, e.g. "transactions above $X are flagged with a risk score"]

## Implementation Notes
- [Money: BigDecimal, never double/float; currency always travels with amount]
- [Currency: ISO 4217 validation]
- [Logging: structured, ISO 8601 UTC timestamps, agent name, transaction_id, outcome]
- [PII: account numbers/names masked in logs, never plaintext]
- [Any other constraints relevant to this change: idempotency, atomic file writes, thresholds config]

## Context

### Beginning context
- [Files/state that exist before this change]

### Ending context
- [Files/state that will exist after this change — be specific about file paths]

## Low-Level Tasks
[One entry per agent/component touched by this change, in this exact format:]

### N. [Task name]

Task: [Agent/Component Name]
Prompt: "[Exact prompt you would give Claude Code to implement this]"
File to CREATE: [path]
Function to CREATE: [signature]
Details: [what it checks/transforms/decides, including specific reason codes or edge cases from sample-transactions.json]
```

## Rules while generating

- Every Mid-Level Objective must be testable — phrase it so a unit test could assert it directly (a number, a status, a file location), not a vague quality goal.
- Every Low-Level Task's `Details` must reference a concrete rule from `agents.md` (e.g. threshold source, masking requirement, idempotency check) rather than restating generic best practice.
- Cross-check every edge case present in `sample-transactions.json` (invalid currency `XYZ`, negative amount, sub-threshold and over-threshold amounts, cross-border/off-hours timing) is covered by at least one Low-Level Task's `Details`.
- Do not invent new terminal transaction statuses beyond what's in `agents.md` (`SETTLED`, `REJECTED`, `FLAGGED_FOR_REVIEW`, `COMPLIANCE_HOLD`) without calling it out explicitly as a proposed addition for the user to confirm.
- If `$ARGUMENTS` conflicts with an existing Hard Rule in `agents.md` (e.g. asks to log raw account numbers), flag the conflict to the user instead of silently complying.

## Output

- Write the result to `specification.md` at the project root (overwrite, since this is the canonical spec — not a new dated file).
- After writing, print a short summary: which sections changed and why, and list any edge cases from `sample-transactions.json` that are still not covered by a Low-Level Task.
