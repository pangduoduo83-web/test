"""Schematic ↔ PCB consistency report and approved sync."""

from __future__ import annotations

import json
import shutil
from pathlib import Path
from types import SimpleNamespace

from app.agent.context import AgentContext
from app.agent.tools.native import update_pcb_from_schematic
from app.kicad import workspace as ws
from app.kicad.eco import build_eco_report
from app.kicad.pcb import Board
from app.kicad.sch import Schematic

SAMPLE = Path(__file__).resolve().parent.parent / "samples" / "power_module"


def _project(tmp_path: Path) -> tuple[Path, Path]:
    directory = tmp_path / "power_module"
    shutil.copytree(SAMPLE, directory)
    return directory / "power_module.kicad_sch", directory / "power_module.kicad_pcb"


def test_sample_project_reports_no_component_drift(tmp_path: Path):
    schematic, pcb = _project(tmp_path)
    report = build_eco_report(schematic, pcb)
    assert report["success"] is True
    assert report["missing_on_pcb"] == []
    assert report["extra_on_pcb"] == []
    assert report["summary"]["schematic_components"] == report["summary"]["pcb_footprints"]


def test_value_drift_is_detected_and_only_safe_sync_is_suggested(tmp_path: Path):
    schematic, pcb = _project(tmp_path)
    board = Board.load(pcb)
    board.set_footprint_property("C3", "Value", "1uF")
    board.save()

    report = build_eco_report(schematic, pcb)
    assert report["status"] == "drift"
    assert report["value_mismatches"] == [{"reference": "C3", "schematic": "100nF", "pcb": "1uF"}]
    assert [action["tool"] for action in report["suggested_actions"]] == ["update_pcb_from_schematic"]
    assert report["suggested_actions"][0]["args"] == {"sync_values": True, "sync_nets": True}


def test_extra_pcb_footprint_is_reported_but_never_auto_deleted(tmp_path: Path):
    schematic, pcb = _project(tmp_path)
    sch = Schematic.load(schematic)
    # Drop C4 from the schematic so the PCB has an extra footprint.
    sch.tree[:] = [
        node
        for node in sch.tree
        if not (isinstance(node, list) and node and node[0] == "symbol" and any(
            isinstance(prop, list) and len(prop) > 2 and prop[1] == "Reference" and prop[2] == "C4"
            for prop in node
        ))
    ]
    sch.save()

    report = build_eco_report(schematic, pcb)
    assert [item["reference"] for item in report["extra_on_pcb"]] == ["C4"]
    assert all(action["tool"] != "delete_footprint" for action in report["suggested_actions"])
    assert report["summary"]["manual_count"] >= 1


def test_update_pcb_syncs_values_and_pad_nets(tmp_path: Path, monkeypatch):
    schematic, pcb = _project(tmp_path)
    monkeypatch.setattr(ws, "_configured_root", tmp_path)
    board = Board.load(pcb)
    board.set_footprint_property("C3", "Value", "1uF")
    board.save()

    ctx = AgentContext(
        user_id=".",
        schematic_path=str(schematic),
        pcb_path=str(pcb),
        workspace_root=str(tmp_path),
    )
    result = json.loads(
        update_pcb_from_schematic.func(runtime=SimpleNamespace(context=ctx), sync_values=True, sync_nets=True)
    )
    assert result["success"] is True, result
    updated = {item["reference"]: item for item in result["updated"]}
    assert updated["C3"]["value"] == {"before": "1uF", "after": "100nF"}
    assert Board.load(pcb).get_footprint("C3").val == "100nF"
    assert build_eco_report(schematic, pcb)["value_mismatches"] == []
