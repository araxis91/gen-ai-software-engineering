# Homework 5 — Configure MCP Servers (GitHub, Filesystem, Jira, Custom)

**Student:** Dmytro Cherneha
**Course:** GenAI & Agentic AI for Software Engineering
**Date:** 2026-07-02

## Overview

This homework connects Claude Code to three external MCP servers and one
custom-built MCP server:

1. **GitHub MCP** — official GitHub MCP server, used to interact with this
   repository (e.g. listing pull requests / commits).
2. **Filesystem MCP** — `@modelcontextprotocol/server-filesystem`, scoped to
   this `homework-5/` directory, used to list/read files.
3. **Jira MCP (Atlassian)** — the hosted Atlassian MCP server
   (`https://mcp.atlassian.com/v1/mcp/authv2`), used to query the `KAN`
   project (site: `sensebank-team-wnghri1e.atlassian.net`).
4. **Custom MCP server** (`custom-mcp-server/`) — a FastMCP server exposing a
   `lorem-ipsum.md` file both as a **resource** and as a **`read` tool**, each
   accepting an optional `word_count` parameter (default `30`).

Most of server configurations are committed in [`.mcp.json`](.mcp.json).
Only GitHub MCP is configured inside general claude code configuration file. This is because github's mcp doesn't support OAuth2, so it required personal token.
Full install/run/connect/test instructions are in [HOWTORUN.md](HOWTORUN.md).

## Resources vs. Tools

- **Resources** are URIs that Claude can _read from_ — passive data sources
  such as files or read-only API endpoints. The custom server exposes
  `lorem://ipsum{?word_count}` as a resource; the client fetches its content
  the same way it would read a file.
- **Tools** are actions Claude can _call_ to perform an operation. The custom
  server exposes a `read` tool that runs the same word-limiting logic as the
  resource but is explicitly invoked by the model as a function call, with
  the `word_count` argument.

## Project structure

```
homework-5/
├── README.md
├── HOWTORUN.md
├── custom-mcp-server/
│   ├── server.py            # FastMCP server: resource + `read` tool
│   ├── lorem-ipsum.md        # source text
│   └── requirements.txt      # includes fastmcp
├── .mcp.json                 # all four MCP servers registered
└── docs/
    └── screenshots/
        ├── github-mcp-result.png
        ├── filesystem-mcp-result.png
        ├── jira-or-notion-mcp-result.png
        └── custom-mcp-read-tool-result.png
```

## Deliverables checklist

- [x] GitHub MCP configured and exercised
- [x] Filesystem MCP configured and exercised
- [x] Jira MCP (Atlassian) configured and exercised (`KAN` project)
- [x] Custom FastMCP server (`server.py`) with `lorem://ipsum` resource and
      `read` tool
- [x] `.mcp.json` registers all four servers
- [x] `custom-mcp-server/requirements.txt` includes `fastmcp`
- [x] Screenshots in `docs/screenshots/`
