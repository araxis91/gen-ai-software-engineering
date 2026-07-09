"""Custom MCP server built with FastMCP, exposing the Java banking pipeline's
results (shared/results/*.json, written by com.homework6.pipeline.Integrator)
to Claude Code so a pipeline run can be queried without reading raw JSON files.

- Tool `get_transaction_status`: current status for a single transaction_id.
- Tool `list_pipeline_results`: a summary of every processed transaction.
- Resource (`pipeline://summary`): the latest shared/results/pipeline-summary.json,
  returned as text -- a passive read, the same way a client might read a file.
"""

import json
from pathlib import Path
from typing import Any

from fastmcp import FastMCP

RESULTS_DIR = Path(__file__).resolve().parent.parent / "shared" / "results"
SUMMARY_FILE = RESULTS_DIR / "pipeline-summary.json"

mcp = FastMCP("Banking Pipeline Status Server")


def _load_result(transaction_id: str) -> dict[str, Any] | None:
    path = RESULTS_DIR / f"{transaction_id}.json"
    if not path.exists():
        return None
    return json.loads(path.read_text(encoding="utf-8"))


def _load_all_results() -> list[dict[str, Any]]:
    if not RESULTS_DIR.exists():
        return []
    results = []
    for path in sorted(RESULTS_DIR.glob("*.json")):
        if path.name == "pipeline-summary.json":
            continue
        results.append(json.loads(path.read_text(encoding="utf-8")))
    return results


@mcp.tool
def get_transaction_status(transaction_id: str) -> dict[str, Any]:
    """Return the current pipeline status for one transaction, read from shared/results/.

    If no terminal result exists yet for `transaction_id` (not processed, or
    an invalid id), returns a `found: false` payload instead of raising.
    """
    result = _load_result(transaction_id)
    if result is None:
        return {
            "transaction_id": transaction_id,
            "found": False,
            "message": (
                f"No result found for '{transaction_id}' in shared/results/. "
                "It may not have been processed yet, or the id is invalid."
            ),
        }

    data = result["data"]
    transaction = data["transaction"]
    state = data["state"]
    return {
        "transaction_id": transaction["transaction_id"],
        "found": True,
        "status": state["status"],
        "reason_code": state.get("reason_code"),
        "reason": state.get("reason"),
        "risk_score": state.get("risk_score"),
        "risk_factors": state.get("risk_factors"),
        "settlement_id": state.get("settlement_id"),
        "settled_at": state.get("settled_at"),
    }


@mcp.tool
def list_pipeline_results() -> dict[str, Any]:
    """Return a summary of every transaction currently in shared/results/.

    Includes a total count, a breakdown of counts by status, and one row
    per transaction (id, status, reason_code, risk_score).
    """
    results = _load_all_results()
    counts_by_status: dict[str, int] = {}
    rows = []

    for result in results:
        data = result["data"]
        transaction = data["transaction"]
        state = data["state"]
        status = state["status"]
        counts_by_status[status] = counts_by_status.get(status, 0) + 1
        rows.append({
            "transaction_id": transaction["transaction_id"],
            "status": status,
            "reason_code": state.get("reason_code"),
            "risk_score": state.get("risk_score"),
        })

    return {
        "total": len(results),
        "counts_by_status": counts_by_status,
        "transactions": rows,
    }


@mcp.resource("pipeline://summary")
def pipeline_summary_resource() -> str:
    """Return the latest shared/results/pipeline-summary.json content as text."""
    if not SUMMARY_FILE.exists():
        return (
            "No pipeline run has completed yet -- "
            "shared/results/pipeline-summary.json was not found. "
            "Run the pipeline first (e.g. via /run-pipeline or `java -cp ... Integrator`)."
        )
    return SUMMARY_FILE.read_text(encoding="utf-8")


if __name__ == "__main__":
    mcp.run()
