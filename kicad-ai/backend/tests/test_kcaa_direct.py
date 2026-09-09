"""Upstream KiCad-AI-Assistant tools called in-process (no MCP)."""

from __future__ import annotations

import json
import shutil
from pathlib import Path

import pytest

from app.agent.factory import AgentRuntime
from app.agent.tools.kcaa_direct import kcaa_installed, load_kcaa_tools
from app.config import Settings

BACKEND = Path(__file__).resolve().parent.parent
pytestmark = pytest.mark.skipif(not kcaa_installed(), reason="kcaa not installed")


@pytest.mark.asyncio
async def test_direct_tools_run_on_workspace_files(tmp_path: Path):
    settings = Settings(llm_provider="mock", data_dir=tmp_path / "data", workspace_root=tmp_path / "ws", kcaa_mode="direct")
    settings.ensure_dirs()
    ws_dir = settings.workspace_root / "u1" / "projects" / "power_module"
    shutil.copytree(BACKEND / "samples" / "power_module", ws_dir)

    tools = {t.name: t for t in await load_kcaa_tools(settings)}
    assert len(tools) >= 100
    zones = json.loads(await tools["list_zones"].ainvoke({"pcb_path": str(ws_dir / "power_module.kicad_pcb")}))
    assert zones["zones"][0]["net_name"] == "GND"
    props = json.loads(await tools["list_symbol_properties"].ainvoke({"schematic_path": str(ws_dir / "power_module.kicad_sch"), "reference": "U1"}))
    assert props["success"] and any(p["value"] == "AMS1117-3.3" for p in props["properties"])
    # failures are reported, not raised
    bad = json.loads(await tools["list_zones"].ainvoke({"pcb_path": str(ws_dir / "missing.kicad_pcb")}))
    assert bad.get("success") is False or "error" in json.dumps(bad).lower()


@pytest.mark.asyncio
async def test_runtime_merges_upstream_tools(tmp_path: Path):
    settings = Settings(llm_provider="mock", data_dir=tmp_path / "data", workspace_root=tmp_path / "ws", kcaa_mode="direct")
    settings.ensure_dirs()
    runtime = AgentRuntime(settings)
    await runtime.start()
    try:
        assert runtime.upstream_source == "kcaa"
        assert runtime.mcp_tool_count >= 100
        names = {t.name for t in runtime.tools}
        assert {"get_board_info", "pcb_route_pad_to_pad", "search_symbols", "add_sheet_symbol"} <= names
        # native implementation wins on collisions
        assert runtime.tool_sources["get_board_info"] == "native"
        assert runtime.tool_sources["pcb_route_pad_to_pad"] == "kcaa"
    finally:
        await runtime.stop()
