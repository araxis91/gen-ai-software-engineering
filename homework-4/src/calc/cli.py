"""Command-line interface for the calc sample app.

Usage examples (with ``PYTHONPATH=src``)::

    python -m calc eval "2 + 3 * 4"
    python -m calc percent 25 200
    python -m calc average 2 4 6
"""

import argparse
from typing import List, Optional

from calc.evaluator import evaluate
from calc.operations import average, percentage


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(prog="calc", description="Tiny calculator CLI")
    sub = parser.add_subparsers(dest="command", required=True)

    p_eval = sub.add_parser("eval", help="evaluate an arithmetic expression")
    p_eval.add_argument("expression", help='e.g. "2 + 3 * 4"')

    p_pct = sub.add_parser("percent", help="what percentage part is of whole")
    p_pct.add_argument("part", type=float)
    p_pct.add_argument("whole", type=float)

    p_avg = sub.add_parser("average", help="arithmetic mean of the given numbers")
    p_avg.add_argument("numbers", type=float, nargs="*")

    return parser


def main(argv: Optional[List[str]] = None) -> int:
    args = build_parser().parse_args(argv)

    if args.command == "eval":
        print(evaluate(args.expression))
    elif args.command == "percent":
        print(percentage(args.part, args.whole))
    elif args.command == "average":
        print(average(args.numbers))

    return 0
