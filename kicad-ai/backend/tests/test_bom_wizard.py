"""BOM/DFM report and circuit template wizard."""

from __future__ import annotations

import asyncio
import json
import shutil
from pathlib import Path
from types import SimpleNamespace

import pytest

from app.agent.context import AgentContext
from app.agent.tools import native
from app.agent.tools.registry import get_policy
from app.kicad import workspace as ws
from app.kicad.bom import bom_csv, build_bom_report
from app.kicad.pcb import Board
from app.kicad.wizard import (
    TEMPLATES,
    expand_template,
    format_ohms,
    list_templates,
    nearest_e24,
)

SAMPLE = Path(__file__).resolve().parent.parent / "samples" / "power_module"


def _project(tmp_path: Path) -> tuple[Path, Path]:
    directory = tmp_path / "power_module"
    shutil.copytree(SAMPLE, directory)
    return directory / "power_module.kicad_pcb", directory / "power_module.kicad_sch"


# ---------------------------------------------------------------- BOM / DFM
def test_bom_groups_by_value_and_footprint(tmp_path: Path):
    pcb, sch = _project(tmp_path)
    report = build_bom_report(pcb, sch, {})
    assert report["success"] is True
    assert report["summary"]["component_count"] == 9
    line = next(l for l in report["lines"] if l["value"] == "100nF")
    assert set(line["references"]) >= {"C3", "C4"} and line["quantity"] == len(line["references"])
    assert report["assembly"]["smd_count"] + report["assembly"]["tht_count"] == 9
    csv_text = bom_csv(report)
    assert csv_text.splitlines()[0] == "Item,Quantity,References,Value,Footprint"
    assert len(csv_text.splitlines()) == 1 + report["summary"]["line_count"]


def test_dfm_flags_missing_footprint_and_placeholder_value(tmp_path: Path):
    pcb, sch = _project(tmp_path)
    board = Board.load(pcb)
    board.set_footprint_property("C3", "Value", "~")
    board.save()
    report = build_bom_report(pcb, sch, {})
    types = {(i["type"], i["reference"]) for i in report["issues"]}
    assert ("missing_value", "C3") in types
    assert ("value_mismatch", "C3") in types  # schematic still says 100nF
    assert report["status"] == "errors"


# ---------------------------------------------------------------- wizard
def test_e24_selection_and_led_resistor_math():
    assert nearest_e24(660) == 680
    assert nearest_e24(9800) == 10_000
    assert nearest_e24(650, prefer="up") == 680  # tie → round up for LED safety
    assert nearest_e24(4700, prefer="up") == 4700
    assert format_ohms(4700) == "4.7k" and format_ohms(1_000_000) == "1M"
    expanded = expand_template("led_indicator", {"supply_voltage": 3.3, "led_forward_voltage": 2.0, "led_current_ma": 2.0}, 50.8, 50.8)
    resistor = next(p for p in expanded["parts"] if p["id"] == "R")
    assert resistor["value"] == "680"  # (3.3-2.0)/2mA = 650Ω → E24 680Ω
    assert all(abs(p["x"] / 1.27 - round(p["x"] / 1.27)) < 1e-6 for p in expanded["parts"])
    assert {w["from"] for w in expanded["wires"]} == {"VCC.1", "R.2", "D.1"}


def test_all_templates_expand_with_defaults_and_reference_valid_pins():
    for template_id, template in TEMPLATES.items():
        expanded = expand_template(template_id, {}, 25.4, 25.4)
        ids = {p["id"] for p in expanded["parts"]}
        for wire in expanded["wires"]:
            assert wire["from"].split(".")[0] in ids and wire["to"].split(".")[0] in ids, (template_id, wire)
        for label in expanded["labels"]:
            assert label["pin"].split(".")[0] in ids
        assert expanded["symbols"] and template["name"]
    assert {t["id"] for t in list_templates()} == set(TEMPLATES)


def test_template_param_validation():
    with pytest.raises(ValueError, match="高于 LED"):
        expand_template("led_indicator", {"supply_voltage": 1.8, "led_forward_voltage": 2.0}, 0, 0)
    with pytest.raises(ValueError, match="只能是"):
        expand_template("ldo_3v3", {"input_net": "VIN"}, 0, 0)
    with pytest.raises(ValueError, match="未知电路模板"):
        expand_template("nope", {}, 0, 0)


def test_apply_template_is_a_gated_mutation_and_drives_kcaa_tools(tmp_path: Path, monkeypatch):
    assert get_policy("apply_circuit_template").kind == "file_mutation"
    assert get_policy("apply_circuit_template").auto_snapshot is True
    pcb, sch = _project(tmp_path)
    monkeypatch.setattr(ws, "_configured_root", tmp_path)
    monkeypatch.setattr(native, "kcaa_tool_available", lambda name: True)
    monkeypatch.setattr(native, "check_library_availability", lambda expanded: {"checked": True, "missing_symbols": [], "missing_footprints": []})

    calls: list[tuple[str, dict]] = []
    counter = {"n": 0}

    async def fake_call(name, **kwargs):
        calls.append((name, kwargs))
        if name == "add_symbol_to_schematic":
            counter["n"] += 1
            return {"success": True, "reference_assigned": f"X{counter['n']}"}
        return {"success": True}

    monkeypatch.setattr(native, "call_kcaa", fake_call)
    ctx = AgentContext(user_id=".", schematic_path=str(sch), pcb_path=str(pcb), workspace_root=str(tmp_path))
    result = json.loads(
        asyncio.run(
            native.apply_circuit_template.coroutine(
                template="led_indicator",
                params={"led_current_ma": 2.0},
                runtime=SimpleNamespace(context=ctx),
                anchor_x=50.8,
                anchor_y=50.8,
            )
        )
    )
    assert result["success"] is True, result
    names = [c[0] for c in calls]
    assert names.count("add_symbol_to_schematic") == 4  # VCC, R, D, GND
    assert names.count("set_symbol_property") == 2  # footprints for R and D only
    assert names.count("connect_pins_with_wire") == 3
    footprint_calls = [c[1] for c in calls if c[0] == "set_symbol_property"]
    assert {c["property_value"] for c in footprint_calls} == {"Resistor_SMD:R_0603_1608Metric", "LED_SMD:LED_0603_1608Metric"}
    assert [p["reference"] for p in result["placed"]] == ["X1", "X2", "X3", "X4"]
