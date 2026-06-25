"""calc — a tiny calculator CLI.

This package is the sample mini-application that the 4-agent bug-fix pipeline
operates on. It intentionally ships with seeded defects so the pipeline has
something concrete to find and fix (see ``context/bugs/001/bug-context.md``):

- two functional bugs in :mod:`calc.operations`
- one security vulnerability in :mod:`calc.evaluator`
"""

__version__ = "0.1.0"
