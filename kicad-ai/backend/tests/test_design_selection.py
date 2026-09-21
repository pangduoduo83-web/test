"""Server-owned PCB / schematic selection metadata and validation."""

from __future__ import annotations

import shutil
from pathlib import Path

import pytest
from fastapi import HTTPException

from app.agent.context import AgentContext
from app.db import Project, User
from app.kicad import sexpr, workspace as ws
from app.kicad.pcb import Board
from app.kicad.sch import Schematic
from app.schemas import DesignSelection
from app.services.conversations import validate_design_selection

SAMPLE = Path(__file__).resolve().parent.parent / "samples" / "power_module"


@pytest.fixture
def owned_project(tmp_path: Path, monkeypatch: pytest.MonkeyPatch) -> tuple[User, Project, Path]:
    root = tmp_path / "workspaces"
    monkeypatch.setattr(ws, "_configured_root", root)
    project_dir = root / "user-1" / "projects" / "power_module"
    shutil.copytree(SAMPLE, project_dir)
    user = User(id="user-1", username="tester", display_name="Tester")
    project = Project(
        id="project-1",
        owner_id=user.id,
        name="power_module",
        rel_dir="projects/power_module",
        pcb_file="projects/power_module/power_module.kicad_pcb",
        schematic_file="projects/power_module/power_module.kicad_sch",
        pro_file="projects/power_module/power_module.kicad_pro",
    )
    return user, project, project_dir


def test_selection_maps_match_render_canvases(owned_project):
    _, _, directory = owned_project
    board = Board.load(directory / "power_module.kicad_pcb")
    pcb_map = board.selection_map()
    assert pcb_map["mode"] == "pcb"
    assert pcb_map["canvas"]["width"] == 640
    assert {element["reference"] for element in pcb_map["elements"]} >= {"U1", "C3", "C4"}
    assert all(element["bbox"][0] < element["bbox"][2] for element in pcb_map["elements"])

    schematic = Schematic.load(directory / "power_module.kicad_sch")
    sch_map = schematic.selection_map()
    assert sch_map["mode"] == "sch"
    assert sch_map["canvas"]["width"] == 1200
    assert {element["reference"] for element in sch_map["elements"]} >= {"U1", "C3", "C4"}


def test_selection_is_resolved_from_file_and_client_bounds_are_ignored(owned_project):
    user, project, _ = owned_project
    selection = DesignSelection(
        project_id=project.id,
        mode="pcb",
        references=["c3", "U1", "C3"],
        bounds=(9999, 9999, 10000, 10000),
    )
    validated = validate_design_selection(user, project, selection)
    assert validated is not None
    assert validated["references"] == ["C3", "U1"]
    assert validated["bounds"][2] < 1000
    assert {item["reference"] for item in validated["objects"]} == {"C3", "U1"}

    context = AgentContext(user_id=user.id, project_id=project.id, selection=validated)
    block = context.context_block()
    assert "current_design_selection" in block
    assert '"C3"' in block and '"U1"' in block


@pytest.mark.parametrize("net_reference", ['"+3V3"', '3', '"3"'])
def test_selection_preview_accepts_track_net_names_and_codes(owned_project, net_reference):
    import xml.etree.ElementTree as ET

    _, _, directory = owned_project
    board = Board.load(directory / "power_module.kicad_pcb")
    assert board.nets[3] == "+3V3"
    existing_segments = next(net["track_segments"] for net in board.list_nets() if net["code"] == 3)
    board.tree.append(sexpr.loads(
        '(segment (start 131 69) (end 139 77) (width 0.25) '
        f'(layer "F.Cu") (net {net_reference}))'
    ))
    before = sexpr.dumps(board.tree)

    svg = ET.fromstring(board.render_svg())
    selection = board.selection_map()
    assert svg.attrib["viewBox"] == f'0 0 {selection["canvas"]["width"]} {selection["canvas"]["height"]}'
    assert len(selection["elements"]) == 9
    nets = {net["name"]: net for net in board.list_nets()}
    assert nets["+3V3"]["track_segments"] == existing_segments + 1
    assert "+3V3" not in {net["net"] for net in board.ratsnest()}
    assert "+3V3" not in board.info()["unrouted_nets"]
    assert sexpr.dumps(board.tree) == before


def test_unknown_reference_is_rejected(owned_project):
    user, project, _ = owned_project
    with pytest.raises(HTTPException, match="不属于当前活动工程"):
        validate_design_selection(
            user,
            project,
            DesignSelection(project_id="another-project", mode="pcb", references=["C3"]),
        )
    with pytest.raises(HTTPException, match="选区包含不存在的对象"):
        validate_design_selection(
            user,
            project,
            DesignSelection(project_id=project.id, mode="sch", references=["NOT_A_SYMBOL"]),
        )
