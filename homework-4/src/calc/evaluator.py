"""Expression evaluation for the calc CLI.

``evaluate`` computes simple arithmetic expressions safely by parsing them with
``ast`` and evaluating only numeric literals and arithmetic operators. Any other
input raises ``ValueError`` instead of being executed.
"""

import ast
import operator
from typing import Callable, Dict, Type

_BINOPS: Dict[Type[ast.operator], Callable[[float, float], float]] = {
    ast.Add: operator.add,
    ast.Sub: operator.sub,
    ast.Mult: operator.mul,
    ast.Div: operator.truediv,
    ast.FloorDiv: operator.floordiv,
    ast.Mod: operator.mod,
    ast.Pow: operator.pow,
}

_UNARYOPS: Dict[Type[ast.unaryop], Callable[[float], float]] = {
    ast.UAdd: operator.pos,
    ast.USub: operator.neg,
}


def evaluate(expression: str) -> float:
    """Evaluate an arithmetic ``expression`` such as ``"2 + 3 * 4"`` and return
    the numeric result.

    Only arithmetic over numeric literals is supported; any other input raises
    ``ValueError`` rather than being executed.
    """
    try:
        tree = ast.parse(expression, mode="eval")
    except SyntaxError as exc:
        raise ValueError(f"invalid expression: {expression!r}") from exc
    return _eval_node(tree.body)


def _eval_node(node: ast.AST) -> float:
    if isinstance(node, ast.Constant) and isinstance(node.value, (int, float)):
        return node.value
    if isinstance(node, ast.BinOp) and type(node.op) in _BINOPS:
        return _BINOPS[type(node.op)](_eval_node(node.left), _eval_node(node.right))
    if isinstance(node, ast.UnaryOp) and type(node.op) in _UNARYOPS:
        return _UNARYOPS[type(node.op)](_eval_node(node.operand))
    raise ValueError("unsupported expression element")
