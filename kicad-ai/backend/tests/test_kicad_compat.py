"""KiCad design-file/runtime compatibility checks."""

from __future__ import annotations

from pathlib import Path

import pytest

from app.kicad.compat import (
    compatibility_report,
    file_compatibility,
    inspect_design_file,
    require_cli_compatible,
)


def _design(tmp_path: Path, name: str, generator: str, format_version: int) -> Path:
    path = tmp_path / name
    root = "kicad_pcb" if path.suffix == ".kicad_pcb" else "kicad_sch"
    path.write_text(
        f'({root}\n  (version {format_version})\n'
        f'  (generator "test")\n  (generator_version "{generator}")\n)\n',
        encoding="utf-8",
    )
    return path


def test_inspect_prefers_generator_version(tmp_path: Path):
    pcb = _design(tmp_path, "v10.kicad_pcb", "10.0", 20260206)
    info = inspect_design_file(pcb)
    assert info["generator_version"] == "10.0"
    assert info["format_version"] == 20260206
    assert info["major"] == 10


def test_new_cli_opens_old_files_but_old_cli_rejects_new_files(tmp_path: Path):
    old = _design(tmp_path, "v8.kicad_sch", "8.0", 20231120)
    new = _design(tmp_path, "v10.kicad_pcb", "10.0", 20260206)

    assert file_compatibility(old, "10.0.5")["compatible"] is True
    incompatible = file_compatibility(new, "9.0.8")
    assert incompatible["compatible"] is False
    assert incompatible["status"] == "incompatible"
    assert "KiCad 10" in incompatible["message"]
    with pytest.raises(RuntimeError, match="服务器是 KiCad 9"):
        require_cli_compatible(new, "9.0.8")


def test_cli_unavailable_is_unknown_not_compatible(tmp_path: Path):
    pcb = _design(tmp_path, "v10.kicad_pcb", "10.0", 20260206)
    item = file_compatibility(pcb, None)
    assert item["compatible"] is None
    assert item["status"] == "cli_unavailable"

    report = compatibility_report([pcb], None)
    assert report["compatible"] is None
    assert report["status"] == "cli_unavailable"


def test_format_date_fallback_without_generator(tmp_path: Path):
    pcb = tmp_path / "date-only.kicad_pcb"
    pcb.write_text("(kicad_pcb (version 20260206) (generator pcbnew))", encoding="utf-8")
    assert inspect_design_file(pcb)["major"] == 10
