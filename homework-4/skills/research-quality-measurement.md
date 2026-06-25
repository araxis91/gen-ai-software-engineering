---
name: research-quality-measurement
description: >-
  Rubric and procedure for measuring the quality of bug-research output. Use this
  skill when verifying a `codebase-research.md` file and writing a
  `verified-research.md` result file. Defines the quality levels, scoring
  dimensions, per-claim status labels, and the PASS/FAIL mapping that the Bug
  Research Verifier must apply.
version: 1.0.0
applies_to:
  - agents/research-verifier.agent.md
outputs:
  - research/verified-research.md
---

# Research Quality Measurement

A deterministic rubric for grading how trustworthy a piece of bug research is.
Its purpose is to let a downstream **Bug Planner** know, at a glance, whether the
research can be turned into an implementation plan as-is, used with caution, or
must be redone.

The verifier **must** apply this skill literally: compute the two rates below,
assign each claim a status label, map the result to a quality level, and report
the level (with its numeric score) in `verified-research.md`.

## When to use

- Whenever a `codebase-research.md` file is being fact-checked.
- When producing the `Research Quality` value in the **Verification Summary** of
  `verified-research.md`.
- When filling the **Research Quality Assessment** section (level + reasoning).

## Core definitions

A **claim** is any verifiable statement in the research, primarily:

- A **reference** — a `path/to/file:line` (or line range) pointer.
- A **snippet** — a quoted block of source code or text attributed to a location.
- An **assertion** — a factual statement about behaviour, control flow, or root
  cause that can be checked against the source.

Each claim is assigned exactly one **status label**:

- `VERIFIED` — the reference resolves and the snippet/assertion matches the
  source exactly (allowing only whitespace-insensitive differences).
- `PARTIAL` — substantively correct but imprecise (e.g. line number off by a few,
  snippet lightly paraphrased) in a way that does not change meaning.
- `DISCREPANCY` — resolves to a real location but the snippet/assertion does not
  match (wrong code, wrong behaviour, misattributed).
- `UNVERIFIABLE` — the reference does not resolve (missing file, out-of-range
  line) or the claim cannot be checked against any source.

## Scoring dimensions

Compute two rates over all claims. `PARTIAL` counts as a half-credit.

1. **Reference Accuracy Rate (RAR)** — over all references:
   `RAR = (VERIFIED + 0.5 × PARTIAL) / total_references`
2. **Snippet Fidelity Rate (SFR)** — over all snippets/assertions:
   `SFR = (VERIFIED + 0.5 × PARTIAL) / total_snippets`

The **Overall Score** is the lower (more conservative) of the two, expressed as a
percentage:

`Overall Score = min(RAR, SFR) × 100`

Using the minimum prevents a flood of correct references from masking a few
dangerous snippet mismatches.

## Quality levels

Assign the level whose score band the Overall Score falls into. Any
**fabricated reference** (a confident pointer to a file or symbol that does not
exist) caps the level at **L1 — UNRELIABLE** regardless of score.

- **L4 — VERIFIED (Excellent)** — Score 100%.
  Every reference resolves and every snippet matches. Zero discrepancies.
  Bug Planner may proceed with no caveats.
- **L3 — RELIABLE (Good)** — Score 90–99%.
  Only minor `PARTIAL` issues (e.g. small line drift); no meaning-changing
  discrepancies. Bug Planner may proceed; note the minor items.
- **L2 — PARTIAL (Fair)** — Score 70–89%.
  Core findings are usable but contain material discrepancies or gaps. Bug
  Planner must re-check the flagged claims before planning.
- **L1 — UNRELIABLE (Poor)** — Score below 70%, OR any fabricated reference.
  Research cannot be trusted. It must be redone before any planning.

## PASS / FAIL mapping

Report a single verdict in the Verification Summary using this mapping:

- **PASS** — level is **L4** or **L3**.
- **CONDITIONAL PASS** — level is **L2** (usable only after the listed re-checks).
- **FAIL** — level is **L1**.

## How to report in `verified-research.md`

- **Verification Summary** must state the verdict (`PASS` / `CONDITIONAL PASS` /
  `FAIL`) and the `Research Quality` as `Lx — LABEL (Score%)`,
  e.g. `L3 — RELIABLE (94%)`.
- **Research Quality Assessment** must restate the level, show the RAR, SFR, and
  Overall Score (with counts: total claims, VERIFIED / PARTIAL / DISCREPANCY /
  UNVERIFIABLE), and give the reasoning that justifies the level.
- Each entry under **Verified Claims** and **Discrepancies Found** must carry one
  of the status labels above.

## Worked example

15 references, 10 snippets.
References: 14 `VERIFIED`, 1 `PARTIAL` → `RAR = (14 + 0.5)/15 = 0.967`.
Snippets: 9 `VERIFIED`, 1 `DISCREPANCY` → `SFR = 9/10 = 0.900`.
`Overall Score = min(0.967, 0.900) × 100 = 90%` → **L3 — RELIABLE (90%)** →
verdict **PASS**, with the single discrepancy listed for the Bug Planner to note.
