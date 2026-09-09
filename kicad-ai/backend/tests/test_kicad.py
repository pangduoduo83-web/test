from __future__ import annotations

import shutil
from pathlib import Path

from app.kicad import sexpr
from app.kicad.pcb import Board
from app.kicad.sch import Schematic

SAMPLE = Path(__file__).resolve().parent.parent / "samples" / "power_module"


def test_sexpr_roundtrip():
    text = '(kicad_pcb (version 20241229) (generator "pcbnew") (net 0 "") (net 1 "GND"))'
    tree = sexpr.loads(text)
    assert tree[0] == "kicad_pcb"
    assert sexpr.value(tree, "version") == 20241229
    out = sexpr.dumps(tree)
    assert sexpr.loads(out) == tree


def test_board_info_and_move(tmp_path: Path):
    pcb = tmp_path / "b.kicad_pcb"
    shutil.copy(SAMPLE / "power_module.kicad_pcb", pcb)
    board = Board.load(pcb)
    info = board.info()
    assert info["footprint_count"] == 9
    assert info["board_size_mm"] == [60.0, 40.0]
    c3 = board.get_footprint("C3")
    assert c3 and c3.x == 118.0
    board.set_footprint_position("C3", 133.5, 69.2, 90)
    board.save()
    again = Board.load(pcb)
    moved = again.get_footprint("C3")
    assert moved and (moved.x, moved.y, moved.rotation) == (133.5, 69.2, 90.0)
    # pads rotate with the footprint
    assert abs(moved.pads[0].x - moved.pads[1].x) < 1e-6
    drc = again.drc_lite()
    assert drc["engine"] == "builtin-lite"


def test_schematic_netlist():
    sch = Schematic.load(SAMPLE / "power_module.kicad_sch")
    nets = {n["name"]: n["pins"] for n in sch.netlist()["nets"]}
    assert nets["GND"] == ["C1.2", "C2.2", "C3.2", "C4.2", "D1.1", "J1.2", "J2.2", "U1.1"]
    assert nets["VIN"] == ["C1.1", "C3.1", "J1.1", "U1.3"]
    assert sch.reference_conflicts() == []


def test_render_svg():
    board = Board.load(SAMPLE / "power_module.kicad_pcb")
    board_svg = board.render_svg()
    assert "<svg" in board_svg and "</svg>" in board_svg
    sch = Schematic.load(SAMPLE / "power_module.kicad_sch")
    sch_svg = sch.render_svg()
    assert "<svg" in sch_svg and "</svg>" in sch_svg

