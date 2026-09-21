"""Prevent nil schematic roots from producing blank native previews and false ERC passes."""
from pathlib import Path
import json
import shutil
import xml.etree.ElementTree as ET
from uuid import UUID

import pytest

from app.kicad import cli, sexpr, workspace as ws
from app.kicad.sch import Schematic


SAMPLE = Path(__file__).resolve().parent.parent / "samples/power_module/power_module.kicad_sch"
NIL = "00000000-0000-0000-0000-000000000000"


def test_new_projects_have_unique_non_nil_roots_matching_project_metadata(tmp_path, monkeypatch):
    monkeypatch.setattr(ws, "_configured_root", tmp_path)
    roots = []
    for _ in range(2):
        folder = ws.create_blank_project("owner", "circuit")
        schematic = next(folder.glob("*.kicad_sch"))
        root = str(sexpr.value(sexpr.load(schematic), "uuid"))
        assert UUID(root).int != 0
        project = json.loads(next(folder.glob("*.kicad_pro")).read_text())
        assert project["sheets"] == [[root, "Root"]]
        roots.append(root)
    assert roots[0] != roots[1]


def test_sample_root_and_all_symbol_instance_paths_agree():
    tree = sexpr.load(SAMPLE)
    root = str(sexpr.value(tree, "uuid"))
    assert UUID(root).int != 0
    schematic = Schematic(tree)
    assert len(schematic.symbols) == 9
    for symbol in schematic.symbols:
        instances = sexpr.child(symbol.node, "instances")
        paths = [str(node[1]) for node in sexpr.walk(instances) if node[0] == "path"]
        assert paths == ["/" + root]
    project = json.loads(SAMPLE.with_suffix(".kicad_pro").read_text())
    assert project["sheets"] == [[root, "Root"]]


@pytest.mark.parametrize("operation", [cli.export_sch_svg, cli.run_erc])
def test_native_commands_reject_nil_root_before_reporting_success(tmp_path, monkeypatch, operation):
    tree = sexpr.load(SAMPLE)
    sexpr.child(tree, "uuid")[1] = sexpr.Str(NIL)
    path = tmp_path / "legacy.kicad_sch"
    sexpr.dump(tree, path)
    before = path.read_bytes()
    monkeypatch.setattr(cli, "kicad_cli_path", lambda: "kicad-cli")
    monkeypatch.setattr(cli, "version", lambda: "10.0.5")
    monkeypatch.setattr(cli.subprocess, "run", lambda *a, **k: pytest.fail("Must reject nil root before invoking KiCad"))
    with pytest.raises(RuntimeError, match="UUID"):
        operation(path)
    assert path.read_bytes() == before


@pytest.mark.skipif(not cli.available(), reason="Native SVG regression requires KiCad CLI")
@pytest.mark.parametrize("project_kind", ["sample", "new"])
def test_native_preview_draws_components_and_every_wire(tmp_path, monkeypatch, project_kind):
    if project_kind == "new":
        monkeypatch.setattr(ws, "_configured_root", tmp_path)
        folder = ws.create_blank_project("owner", "power_module")
        path = folder / SAMPLE.name
        root = str(sexpr.value(sexpr.load(path), "uuid"))
        sample_root = str(sexpr.value(sexpr.load(SAMPLE), "uuid"))
        path.write_text(SAMPLE.read_text().replace(sample_root, root))
    else:
        path = tmp_path / SAMPLE.name
        shutil.copy2(SAMPLE, path)
        shutil.copy2(SAMPLE.with_suffix(".kicad_pro"), path.with_suffix(".kicad_pro"))
    before = path.read_bytes()
    svg = cli.export_sch_svg(path)
    assert all(">" + ref + "<" in svg for ref in ("U1", "J1", "J2", "C1", "C2", "C3", "C4", "R1", "D1"))
    tree = ET.fromstring(svg)
    visible_paths = set()
    for group in tree.iter("{http://www.w3.org/2000/svg}g"):
        style = dict(part.strip().split(":", 1) for part in group.attrib.get("style", "").split(";") if ":" in part)
        if style.get("stroke") in (None, "none") or float(style.get("stroke-width", "0")) <= 0:
            continue
        visible_paths.update(" ".join(element.attrib.get("d", "").split())
                             for element in group.findall("{http://www.w3.org/2000/svg}path"))
    for wire in Schematic.load(path).wires():
        start, end = wire["start"], wire["end"]
        forward = f"M{start[0]:.4f} {start[1]:.4f} L{end[0]:.4f} {end[1]:.4f}"
        reverse = f"M{end[0]:.4f} {end[1]:.4f} L{start[0]:.4f} {start[1]:.4f}"
        assert forward in visible_paths or reverse in visible_paths, "Native SVG has an invisible wire"
    assert path.read_bytes() == before
