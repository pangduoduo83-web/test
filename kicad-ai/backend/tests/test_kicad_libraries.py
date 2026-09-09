"""KiCad library tables + kcaa search index (what the Docker build runs)."""

from __future__ import annotations

import sys
from pathlib import Path

import pytest

from app.agent.tools.kcaa_direct import kcaa_installed
from app.kicad.libraries import (
    FOOTPRINT_TABLE,
    SYMBOL_TABLE,
    ensure_library_tables,
    library_status,
    resolve_paths,
    sync_index,
)

MINI_SYMBOL_LIB = """(kicad_symbol_lib (version 20241209) (generator "test") (generator_version "9.0")
  (symbol "R" (pin_numbers hide) (pin_names (offset 0)) (exclude_from_sim no) (in_bom yes) (on_board yes)
    (property "Reference" "R" (at 2.032 0 90) (effects (font (size 1.27 1.27))))
    (property "Value" "R" (at 0 0 90) (effects (font (size 1.27 1.27))))
    (property "Footprint" "" (at -1.778 0 90) (effects (font (size 1.27 1.27)) hide))
    (property "Datasheet" "~" (at 0 0 0) (effects (font (size 1.27 1.27)) hide))
    (property "Description" "Resistor" (at 0 0 0) (effects (font (size 1.27 1.27)) hide))
    (property "ki_keywords" "R res resistor" (at 0 0 0) (effects (font (size 1.27 1.27)) hide))
    (symbol "R_0_1" (rectangle (start -1.016 -2.54) (end 1.016 2.54) (stroke (width 0.254) (type default)) (fill (type none))))
    (symbol "R_1_1"
      (pin passive line (at 0 3.81 270) (length 1.27) (name "~" (effects (font (size 1.27 1.27)))) (number "1" (effects (font (size 1.27 1.27)))))
      (pin passive line (at 0 -3.81 90) (length 1.27) (name "~" (effects (font (size 1.27 1.27)))) (number "2" (effects (font (size 1.27 1.27))))))))
"""

MINI_FOOTPRINT = """(footprint "R_0603_1608Metric" (version 20240108) (generator "test") (layer "F.Cu")
  (descr "Resistor SMD 0603 (1608 Metric)")
  (tags "resistor")
  (attr smd)
  (fp_text reference "REF**" (at 0 -1.43) (layer "F.SilkS") (effects (font (size 1 1) (thickness 0.15))))
  (fp_text value "R_0603" (at 0 1.43) (layer "F.Fab") (effects (font (size 1 1) (thickness 0.15))))
  (fp_rect (start -1.48 -0.73) (end 1.48 0.73) (stroke (width 0.05) (type solid)) (fill none) (layer "F.CrtYd"))
  (pad "1" smd roundrect (at -0.825 0) (size 0.8 0.95) (layers "F.Cu" "F.Paste" "F.Mask") (roundrect_rratio 0.25))
  (pad "2" smd roundrect (at 0.825 0) (size 0.8 0.95) (layers "F.Cu" "F.Paste" "F.Mask") (roundrect_rratio 0.25)))
"""


@pytest.fixture
def fake_kicad(tmp_path: Path, monkeypatch: pytest.MonkeyPatch) -> Path:
    """A headless 'KiCad install' (libraries, no GUI, no config) wired into the environment.

    kcaa finds fp-lib-table via the platform config root (APPDATA / XDG_CONFIG_HOME /
    ~/Library), so that root is redirected into tmp as well.
    """
    app = tmp_path / "kicad"
    (app / "symbols").mkdir(parents=True)
    (app / "symbols" / "Device.kicad_sym").write_text(MINI_SYMBOL_LIB, encoding="utf-8")
    pretty = app / "footprints" / "Resistor_SMD.pretty"
    pretty.mkdir(parents=True)
    (pretty / "R_0603_1608Metric.kicad_mod").write_text(MINI_FOOTPRINT, encoding="utf-8")

    cfg_root = tmp_path / "cfg"
    if sys.platform.startswith("win"):
        monkeypatch.setenv("APPDATA", str(cfg_root))
        config_dir = cfg_root / "kicad" / "9.0"
    elif sys.platform == "darwin":
        monkeypatch.setenv("HOME", str(cfg_root))
        config_dir = cfg_root / "Library" / "Preferences" / "kicad" / "9.0"
    else:
        monkeypatch.setenv("XDG_CONFIG_HOME", str(cfg_root))
        config_dir = cfg_root / "kicad" / "9.0"

    monkeypatch.setenv("KICAD_VERSION", "9.0")
    monkeypatch.setenv("KICAD_APP_PATH", str(app))
    monkeypatch.setenv("KICAD_CONFIG_DIR", str(config_dir))
    monkeypatch.setenv("KICAD_SYMBOL_DIR", str(app / "symbols"))
    monkeypatch.setenv("KICAD_FOOTPRINT_DIR", str(app / "footprints"))
    monkeypatch.setenv("KICAD_TEMPLATE_DIR", str(app / "template"))
    for stale in ("KICAD9_SYMBOL_DIR", "KICAD9_FOOTPRINT_DIR"):
        monkeypatch.delenv(stale, raising=False)
    return app


def test_tables_are_generated_once_and_never_overwritten(fake_kicad: Path):
    paths = resolve_paths()
    assert paths.symbol_dir == fake_kicad / "symbols"
    assert paths.major == "9"

    report = ensure_library_tables(paths)
    assert {t["status"] for t in report["tables"].values()} == {"generated"}
    sym = (paths.config_dir / SYMBOL_TABLE).read_text(encoding="utf-8")
    fp = (paths.config_dir / FOOTPRINT_TABLE).read_text(encoding="utf-8")
    assert '(name "Device")' in sym and '${KICAD9_SYMBOL_DIR}/Device.kicad_sym' in sym
    assert '(name "Resistor_SMD")' in fp and '${KICAD9_FOOTPRINT_DIR}/Resistor_SMD.pretty' in fp

    # user edits survive: a second run leaves existing tables alone
    (paths.config_dir / SYMBOL_TABLE).write_text(sym + "; custom\n", encoding="utf-8")
    again = ensure_library_tables(paths)
    assert {t["status"] for t in again["tables"].values()} == {"existing"}
    assert (paths.config_dir / SYMBOL_TABLE).read_text(encoding="utf-8").endswith("; custom\n")
    assert again["tables"][SYMBOL_TABLE]["entries"] == 1


def test_tables_prefer_kicad_templates(fake_kicad: Path):
    template = fake_kicad / "template"
    template.mkdir()
    (template / SYMBOL_TABLE).write_text('(sym_lib_table (version 7)\n  (lib (name "FromTemplate")(type "KiCad")(uri "${KICAD9_SYMBOL_DIR}/Device.kicad_sym")(options "")(descr "")))\n', encoding="utf-8")
    paths = resolve_paths()
    report = ensure_library_tables(paths)
    assert report["tables"][SYMBOL_TABLE]["status"] == "copied_template"
    assert report["tables"][FOOTPRINT_TABLE]["status"] == "generated"  # no fp template shipped → scan
    assert "FromTemplate" in (paths.config_dir / SYMBOL_TABLE).read_text(encoding="utf-8")


def test_nothing_happens_without_kicad(tmp_path: Path, monkeypatch: pytest.MonkeyPatch):
    monkeypatch.setenv("KICAD_APP_PATH", str(tmp_path / "nowhere"))
    monkeypatch.setenv("KICAD_CONFIG_DIR", str(tmp_path / "cfg"))
    for var in ("KICAD_SYMBOL_DIR", "KICAD_FOOTPRINT_DIR", "KICAD_TEMPLATE_DIR"):
        monkeypatch.delenv(var, raising=False)
    report = ensure_library_tables(resolve_paths())
    assert {t["status"] for t in report["tables"].values()} == {"skipped"}
    assert not (tmp_path / "cfg").exists()
    assert library_status(resolve_paths())["ready"] is False


@pytest.mark.skipif(not kcaa_installed(), reason="kcaa not installed")
def test_index_build_makes_symbols_and_footprints_searchable(fake_kicad: Path):
    paths = resolve_paths()
    ensure_library_tables(paths)
    report = sync_index(paths)
    assert report["warnings"] == []
    assert report["symbols"]["total"] == 1 and report["symbols"]["failed"] == 0
    assert report["footprints"]["total"] == 1 and report["footprints"]["failed"] == 0
    assert report["index"]["symbols"]["items"] == 1 and report["index"]["footprints"]["items"] == 1

    from kcaa.utils.config import ServerConfig
    from kcaa.utils.footprint_index_manager import FootprintIndexManager
    from kcaa.utils.symbol_index_manager import SymbolIndexManager
    from kcaa.utils.symbol_index_reader import SymbolIndexReader

    sym_mgr = SymbolIndexManager(SymbolIndexReader(ServerConfig()), db_path=paths.symbol_db)
    try:
        hits = sym_mgr.search_symbols("resistor")
        assert [(s.library_name, s.symbol_name) for s in hits] == [("Device", "R")]
        assert sym_mgr.get_symbol("Device", "R").pin_count == 2
    finally:
        sym_mgr.close()
    fp_mgr = FootprintIndexManager(db_path=paths.footprint_db)
    hits = fp_mgr.search_footprints("0603")
    assert [(f.library_name, f.footprint_name) for f in hits] == [("Resistor_SMD", "R_0603_1608Metric")]

    status = library_status(paths)
    assert status["ready"] is True
    assert status["tables"][SYMBOL_TABLE]["entries"] == 1
    # second sync is incremental: nothing re-parsed
    again = sync_index(paths)
    assert again["symbols"]["skipped"] == 1 and again["footprints"]["skipped"] == 1
