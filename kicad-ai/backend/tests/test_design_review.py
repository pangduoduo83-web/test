"""Unified ERC, DRC and explainable placement-quality review."""

from __future__ import annotations

import json
import shutil
from pathlib import Path
from types import SimpleNamespace

from app.kicad import cli as kicad_cli
from app.kicad.pcb import Board
from app.kicad.review import run_design_review
from app.kicad.sch import Schematic

SAMPLE = Path(__file__).resolve().parent.parent / "samples" / "power_module"


def _project(tmp_path: Path) -> tuple[Path, Path]:
    directory = tmp_path / "project"
    shutil.copytree(SAMPLE, directory)
    return directory / "power_module.kicad_pcb", directory / "power_module.kicad_sch"


def test_review_combines_drc_erc_and_explainable_score(tmp_path, monkeypatch):
    pcb, schematic = _project(tmp_path)
    monkeypatch.setattr(kicad_cli, "available", lambda: False)
    review = run_design_review(pcb, schematic, {"placement_grid_mm": 0.1})
    assert review["success"] is True
    assert review["drc"]["engine"] == "builtin-lite"
    assert review["erc"]["engine"] == "builtin-lite"
    assert review["placement"]["score"] == sum(review["placement"]["breakdown"].values())
    assert review["grade"] in {"A", "B", "C", "D"}
    assert {step["tool"] for step in review["verification"]} == {
        "run_drc_check",
        "run_erc_check",
        "score_placement",
    }


def test_review_suggests_concrete_approved_actions_for_overlap(tmp_path, monkeypatch):
    pcb, schematic = _project(tmp_path)
    board = Board.load(pcb)
    c4 = board.get_footprint("C4")
    assert c4 is not None
    board.set_footprint_position("C3", c4.x, c4.y, c4.rotation)
    board.save()
    monkeypatch.setattr(kicad_cli, "available", lambda: False)

    review = run_design_review(
        pcb,
        schematic,
        {"placement_grid_mm": 0.1, "copper_edge_clearance_mm": 0.5},
    )
    actions = review["suggested_actions"]
    assert actions
    move = next(action for action in actions if action["tool"] == "set_footprint_position")
    assert {"reference", "x", "y", "rotation"} <= move["args"].keys()
    assert "courtyards_overlap" in move["issue_types"]
    assert review["summary"]["auto_fixable_count"] == len(actions)


def test_review_suggests_board_outline_when_missing(tmp_path, monkeypatch):
    pcb, schematic = _project(tmp_path)
    board = Board.load(pcb)
    board.clear_outline()
    board.save()
    monkeypatch.setattr(kicad_cli, "available", lambda: False)
    review = run_design_review(pcb, schematic)
    outline = next(
        action for action in review["suggested_actions"]
        if action["tool"] == "set_board_outline_rect"
    )
    assert {"x1", "y1", "x2", "y2"} == outline["args"].keys()


def test_erc_lite_finds_missing_footprints():
    result = Schematic.load(SAMPLE / "power_module.kicad_sch").erc_lite()
    assert result["engine"] == "builtin-lite"
    assert "violations" in result and "error_count" in result


def test_kicad_cli_erc_json_is_flattened(tmp_path, monkeypatch):
    _, schematic = _project(tmp_path)

    def fake_run(command, **_kwargs):
        output = Path(command[command.index("--output") + 1])
        output.write_text(
            json.dumps(
                {
                    "kicad_version": "10.0.5",
                    "ignored_checks": [],
                    "sheets": [
                        {
                            "path": "/",
                            "violations": [
                                {
                                    "type": "pin_not_connected",
                                    "severity": "warning",
                                    "description": "Pin not connected",
                                    "items": [],
                                }
                            ],
                        }
                    ],
                }
            ),
            encoding="utf-8",
        )
        return SimpleNamespace(returncode=0, stdout="", stderr="")

    monkeypatch.setattr(kicad_cli, "kicad_cli_path", lambda: "kicad-cli")
    monkeypatch.setattr(kicad_cli, "version", lambda: "10.0.5")
    monkeypatch.setattr(kicad_cli.subprocess, "run", fake_run)
    result = kicad_cli.run_erc(schematic)
    assert result["engine"] == "kicad-cli"
    assert result["warning_count"] == 1
    assert result["violations"][0]["sheet"] == "/"
