"""Load KiCad tools from the upstream ``kcaa`` MCP server (optional).

Run the upstream server next to this backend, e.g.::

    pip install kcaa
    set MCP_TRANSPORT=streamable-http
    set MCP_PORT=8765
    kcaa

then point ``KCAA_MCP_URL=http://127.0.0.1:8765/mcp`` at it. All 100+ upstream
tools (symbol/footprint library search, hierarchical sheets, routing, zones,
IPC …) become available to the agent in addition to the native tool set.
"""

from __future__ import annotations

import asyncio
import logging

from langchain_core.tools import BaseTool

from app.config import Settings

log = logging.getLogger(__name__)


async def load_mcp_tools(settings: Settings) -> list[BaseTool]:
    if not settings.kcaa_mcp_url:
        return []
    try:
        from langchain_mcp_adapters.client import MultiServerMCPClient
    except ImportError:  # pragma: no cover
        log.warning("langchain-mcp-adapters not installed; skipping MCP tools")
        return []

    transport = settings.kcaa_mcp_transport.replace("-", "_").lower()
    if transport in ("http", "streamable_http", "streamablehttp"):
        conn = {"transport": "streamable_http", "url": settings.kcaa_mcp_url, "timeout": settings.mcp_connect_timeout}
    elif transport == "sse":
        conn = {"transport": "sse", "url": settings.kcaa_mcp_url, "timeout": settings.mcp_connect_timeout}
    else:
        log.warning("Unsupported MCP transport %r", settings.kcaa_mcp_transport)
        return []

    client = MultiServerMCPClient({"kcaa": conn})  # type: ignore[arg-type]
    # In docker-compose the MCP container may come up after the app; retry a few
    # times before giving up so the upstream tools are not silently missing.
    attempts = max(1, settings.mcp_startup_retries)
    for attempt in range(1, attempts + 1):
        try:
            tools = await asyncio.wait_for(client.get_tools(), timeout=settings.mcp_connect_timeout + 5)
        except Exception as exc:  # noqa: BLE001
            if attempt == attempts:
                log.warning("Could not load MCP tools from %s after %d attempts: %s", settings.kcaa_mcp_url, attempts, exc)
                return []
            log.info("MCP server %s not ready (%s); retry %d/%d in %.0fs", settings.kcaa_mcp_url, exc, attempt, attempts, settings.mcp_startup_wait)
            await asyncio.sleep(settings.mcp_startup_wait)
            continue
        log.info("Loaded %d tools from kcaa MCP server", len(tools))
        return tools
    return []
