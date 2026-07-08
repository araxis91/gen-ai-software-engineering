"""Custom MCP server built with FastMCP.

Exposes the contents of lorem-ipsum.md in two ways:

- Resource (``lorem://ipsum``): a URI Claude can read directly, like a file.
  Resources are passive data sources -- the client fetches content from them,
  the same way it might read a file or call a read-only API.
- Tool (``read``): an action Claude can call. Tools represent operations the
  model actively invokes (here, "read N words from lorem-ipsum.md") rather
  than content it just fetches.

Both entry points share the same word-limiting logic so their output is
always identical for the same ``word_count``.
"""

from pathlib import Path

from fastmcp import FastMCP

LOREM_IPSUM_PATH = Path(__file__).parent / "lorem-ipsum.md"
DEFAULT_WORD_COUNT = 30

mcp = FastMCP("Lorem Ipsum Server")


def _read_words(word_count: int = DEFAULT_WORD_COUNT) -> str:
    text = LOREM_IPSUM_PATH.read_text(encoding="utf-8")
    words = text.split()
    return " ".join(words[:word_count])


@mcp.resource("lorem://ipsum{?word_count}")
def lorem_ipsum_resource(word_count: int = DEFAULT_WORD_COUNT) -> str:
    """Return the first `word_count` words from lorem-ipsum.md."""
    return _read_words(word_count)


@mcp.tool
def read(word_count: int = DEFAULT_WORD_COUNT) -> str:
    """Read the first `word_count` words from lorem-ipsum.md (default 30)."""
    return _read_words(word_count)


if __name__ == "__main__":
    mcp.run()
