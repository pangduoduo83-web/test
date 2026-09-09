"""Tool-result budget: structural shrinking, spill files and pagination."""

from __future__ import annotations

import json
import shutil
from pathlib import Path
from types import SimpleNamespace

import pytest

from app.agent.context import AgentContext
from app.agent.results import SPILL_DIR, TRUNCATION_KEY, shrink_tool_result
from app.agent.tools import native
from app.config import Settings
from app.kicad import workspace as ws

BACKEND = Path(__file__).resolve().parent.parent


def test_small_results_pass_through(tmp_path: Path):
    content, info = shrink_tool_result('{"a": 1}', tool="t", tool_call_id="c1", spill_root=tmp_path, max_chars=100)
    assert content == '{"a": 1}' and info is None


def test_large_list_field_is_cut_and_spilled(tmp_path: Path):
    payload = {
        "success": True,
        "footprints": [{"reference": f"R{i}", "x": i * 1.5, "y": 2.0, "footprint": "Resistor_SMD:R_0402_1005Metric"} for i in range(400)],
        "count": 400,
    }
    raw = json.dumps(payload, ensure_ascii=False)
    content, info = shrink_tool_result(raw, tool="list_footprints", tool_call_id="call_1", spill_root=tmp_path, max_chars=4000)
    assert info is not None and len(content) <= 4000
    data = json.loads(content)
    assert data["success"] is True and data["count"] == 400  # scalar fields survive
    assert 0 < len(data["footprints"]) < 400
    marker = data[TRUNCATION_KEY]
    assert marker["fields"]["footprints"]["total"] == 400
    assert marker["fields"]["footprints"]["shown"] == len(data["footprints"])
    assert marker["full_result"].startswith(f"/{SPILL_DIR}/")
    spilled = tmp_path / marker["full_result"].lstrip("/")
    assert spilled.exists() and json.loads(spilled.read_text(encoding="utf-8"))["count"] == 400


def test_non_json_results_are_hard_cut_with_note(tmp_path: Path):
    raw = "line\n" * 5000
    content, info = shrink_tool_result(raw, tool="read_file", tool_call_id=None, spill_root=tmp_path, max_chars=1000)
    assert info is not None and len(content) <= 1000
    assert "结果已截断" in content and str(len(raw)) in content


def test_list_tools_paginate(tmp_path: Path):
    settings = Settings(data_dir=tmp_path / "data", workspace_root=tmp_path / "ws", samples_dir=BACKEND / "samples")
    settings.ensure_dirs()
    ws.configure(settings.workspace_root)
    proj = settings.workspace_root / "u1" / "power_module"
    shutil.copytree(settings.samples_dir / "power_module", proj)
    runtime = SimpleNamespace(context=AgentContext(user_id="u1", pcb_path=str(proj / "power_module.kicad_pcb"), schematic_path=str(proj / "power_module.kicad_sch")))

    first = json.loads(native.list_footprints.func(runtime=runtime, limit=4, offset=0))
    assert first["count"] == 4 and first["total"] == 9 and first["next_offset"] == 4 and "note" in first
    second = json.loads(native.list_footprints.func(runtime=runtime, limit=4, offset=4))
    third = json.loads(native.list_footprints.func(runtime=runtime, limit=4, offset=8))
    refs = [f["reference"] for page in (first, second, third) for f in page["footprints"]]
    assert len(refs) == 9 and len(set(refs)) == 9 and "next_offset" not in third

    everything = json.loads(native.list_footprints.func(runtime=runtime, limit=None))
    assert everything["count"] == everything["total"] == 9

    nets = json.loads(native.list_nets.func(runtime=runtime, name_contains="gnd"))
    assert nets["total"] >= 1 and all("gnd" in n["name"].lower() for n in nets["nets"])

    symbols = json.loads(native.list_schematic_symbols.func(runtime=runtime, reference_prefix="C", limit=2))
    assert symbols["count"] <= 2 and all(s["reference"].startswith("C") for s in symbols["symbols"])
