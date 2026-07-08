# HOWTORUN

Instructions to install, run, and test each MCP server used in this homework,
with a focus on the custom FastMCP server (Task 4).

## Prerequisites

- Python 3.10+ (tested with 3.13)
- Node.js + `npx` (for the Filesystem MCP server)
- Claude Code CLI

## 1. GitHub MCP

Registered via `claude mcp add` (or the `github` block in your global/user
MCP config) using the official GitHub MCP server. Requires a GitHub token
with repo access. Test with:

```
"List the 5 most recent commits on this repository"
```

## 2. Filesystem MCP

Registered in `.mcp.json`, scoped to this `homework-5/` directory:

```json
"filesystem": {
  "type": "stdio",
  "command": "npx",
  "args": ["-y", "@modelcontextprotocol/server-filesystem", "<path-to-homework-5>"],
  "env": {}
}
```

No install step needed — `npx -y` fetches the package on first run. Test with:

```
"List the files in the current directory using the filesystem MCP"
```

## 3. Jira MCP (Atlassian)

Registered in `.mcp.json`:

```json
"atlassian": {
  "type": "http",
  "url": "https://mcp.atlassian.com/v1/mcp/authv2"
}
```

Authenticate with `/mcp` in Claude Code and complete the OAuth flow in the
browser. Test with:

```
"Show me the last 5 bugs in the KAN project"
```

## 4. Custom MCP server (`custom-mcp-server/`)

### Install dependencies

```bash
cd custom-mcp-server
python3 -m venv .venv
source .venv/bin/activate      # Windows: .venv\Scripts\activate
pip install -r requirements.txt
```

This installs `fastmcp` (declared in `requirements.txt`).

### Run the server standalone (sanity check)

```bash
source .venv/bin/activate
python3 server.py
```

The process starts and waits on stdio for an MCP client — this is expected;
it is not meant to print output on its own. Stop it with `Ctrl+C`. This
confirms the starting script/command works before wiring it into a client.

### Connect via MCP configuration

Add (or verify) this entry in `.mcp.json` at the repo root:

```json
"lorem-ipsum": {
  "type": "stdio",
  "command": "<path-to-homework-5>/custom-mcp-server/.venv/bin/python",
  "args": ["<path-to-homework-5>/custom-mcp-server/server.py"],
  "env": {}
}
```

Use the **venv's** Python interpreter (not the system one) so `fastmcp` is
importable. Restart Claude Code (or run `/mcp`) after editing `.mcp.json` so
it picks up the new server; check `/mcp` shows `lorem-ipsum` as connected.

### Use / test the `read` tool

In Claude Code, once `lorem-ipsum` is connected, ask:

```
"Use the lorem-ipsum MCP read tool to get the first 10 words"
"Use the lorem-ipsum MCP read tool with default word_count"
```

Claude will call the `read` tool (optionally passing `word_count`) and return
exactly that many words from `lorem-ipsum.md`.

You can also read the resource directly (equivalent content, fetched instead
of called):

```
"Read the lorem://ipsum resource"
"Read the lorem://ipsum resource with word_count=50"
```

### Verify without a client (local test)

```bash
source .venv/bin/activate
python3 -c "
import asyncio
from server import mcp

async def main():
    print(await mcp.read_resource('lorem://ipsum?word_count=10'))
    print(await mcp.call_tool('read', {'word_count': 10}))

asyncio.run(main())
"
```

Both calls should print the same first 10 words from `lorem-ipsum.md`.

### Resources vs. Tools (recap)

- **Resource** (`lorem://ipsum{?word_count}`) — a URI Claude *reads*, like a
  file or a read-only API endpoint.
- **Tool** (`read`) — an action Claude *calls*, like running a command or
  performing an operation with arguments.

Both are backed by the same `_read_words()` function in `server.py`, so their
output is identical for the same `word_count`.
