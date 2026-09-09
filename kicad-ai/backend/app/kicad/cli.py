"""Optional integration with ``kicad-cli`` (real DRC / SVG export when present)."""

from __future__ import annotations

import json
import shutil
import subprocess
import tempfile
from functools import lru_cache
from pathlib import Path
from typing import Any

from app.config import get_settings
from app.kicad.compat import require_cli_compatible


@lru_cache
def kicad_cli_path() -> str | None:
    configured = get_settings().kicad_cli
    if configured and Path(configured).is_file():
        return configured
    found = shutil.which(configured or "kicad-cli")
    if found:
        return found
    for candidate in (
        r"C:\Program Files\KiCad\10.0\bin\kicad-cli.exe",
        r"C:\Program Files\KiCad\9.0\bin\kicad-cli.exe",
        r"C:\Program Files\KiCad\8.0\bin\kicad-cli.exe",
        "/usr/bin/kicad-cli",
        "/Applications/KiCad/KiCad.app/Contents/MacOS/kicad-cli",
    ):
        if Path(candidate).is_file():
            return candidate
    return None


def available() -> bool:
    return kicad_cli_path() is not None


@lru_cache
def version() -> str | None:
    cli = kicad_cli_path()
    if not cli:
        return None
    try:
        return subprocess.run([cli, "version"], capture_output=True, text=True, timeout=15).stdout.strip()
    except (OSError, subprocess.SubprocessError):
        return None


def run_drc(pcb_path: Path, timeout: float = 120) -> dict[str, Any]:
    cli = kicad_cli_path()
    if not cli:
        raise FileNotFoundError("kicad-cli not available")
    require_cli_compatible(pcb_path, version())
    with tempfile.TemporaryDirectory() as tmp:
        report = Path(tmp) / "drc.json"
        cmd = [
            cli,
            "pcb",
            "drc",
            "--format",
            "json",
            "--severity-all",
            "--units",
            "mm",
            "--output",
            str(report),
            str(pcb_path),
        ]
        proc = subprocess.run(cmd, capture_output=True, text=True, timeout=timeout)
        if not report.exists():
            raise RuntimeError(f"kicad-cli drc failed: {proc.stderr.strip() or proc.stdout.strip()}")
        data = json.loads(report.read_text(encoding="utf-8"))
    violations = data.get("violations", [])
    unconnected = data.get("unconnected_items", [])
    parity = data.get("schematic_parity", [])
    errors = sum(1 for v in violations if v.get("severity") == "error")
    warnings = sum(1 for v in violations if v.get("severity") == "warning")
    return {
        "engine": "kicad-cli",
        "passed": errors == 0 and not unconnected,
        "error_count": errors,
        "warning_count": warnings,
        "unconnected_count": len(unconnected),
        "violations": violations,
        "unconnected_items": unconnected,
        "schematic_parity": parity,
    }


def run_erc(sch_path: Path, timeout: float = 120) -> dict[str, Any]:
    cli = kicad_cli_path()
    if not cli:
        raise FileNotFoundError("kicad-cli not available")
    require_cli_compatible(sch_path, version())
    with tempfile.TemporaryDirectory() as tmp:
        report = Path(tmp) / "erc.json"
        cmd = [
            cli,
            "sch",
            "erc",
            "--format",
            "json",
            "--severity-all",
            "--units",
            "mm",
            "--output",
            str(report),
            str(sch_path),
        ]
        proc = subprocess.run(cmd, capture_output=True, text=True, timeout=timeout)
        if not report.exists():
            raise RuntimeError(f"kicad-cli erc failed: {proc.stderr.strip() or proc.stdout.strip()}")
        data = json.loads(report.read_text(encoding="utf-8"))
    violations = []
    for sheet in data.get("sheets", []):
        if not isinstance(sheet, dict):
            continue
        for raw in sheet.get("violations", []):
            if isinstance(raw, dict):
                violations.append({**raw, "sheet": sheet.get("path", "/")})
    errors = sum(1 for item in violations if item.get("severity") == "error")
    warnings = sum(1 for item in violations if item.get("severity") == "warning")
    return {
        "engine": "kicad-cli",
        "passed": errors == 0,
        "error_count": errors,
        "warning_count": warnings,
        "violations": violations,
        "ignored_checks": data.get("ignored_checks", []),
        "sheet_count": len(data.get("sheets", [])),
        "kicad_version": data.get("kicad_version"),
    }


def export_svg(pcb_path: Path, layers: str = "F.Cu,B.Cu,Edge.Cuts,F.SilkS", timeout: float = 120) -> str:
    cli = kicad_cli_path()
    if not cli:
        raise FileNotFoundError("kicad-cli not available")
    require_cli_compatible(pcb_path, version())
    with tempfile.TemporaryDirectory() as tmp:
        out = Path(tmp) / "board.svg"
        cmd = [
            cli,
            "pcb",
            "export",
            "svg",
            "--layers",
            layers,
            "--page-size-mode",
            "2",
            "--exclude-drawing-sheet",
            "--output",
            str(out),
            str(pcb_path),
        ]
        proc = subprocess.run(cmd, capture_output=True, text=True, timeout=timeout)
        if not out.exists():
            raise RuntimeError(f"kicad-cli svg export failed: {proc.stderr.strip()}")
        return out.read_text(encoding="utf-8")


def export_sch_svg(sch_path: Path, timeout: float = 120) -> str:
    cli = kicad_cli_path()
    if not cli:
        raise FileNotFoundError("kicad-cli not available")
    require_cli_compatible(sch_path, version())
    with tempfile.TemporaryDirectory() as tmp:
        cmd = [
            cli,
            "sch",
            "export",
            "svg",
            "--output",
            tmp,
            str(sch_path),
        ]
        proc = subprocess.run(cmd, capture_output=True, text=True, timeout=timeout)
        svgs = sorted(Path(tmp).glob("*.svg"))
        if not svgs:
            raise RuntimeError(f"kicad-cli sch svg export failed: {proc.stderr.strip() or proc.stdout.strip()}")
        exact = Path(tmp) / f"{sch_path.stem}.svg"
        if exact.is_file():
            return exact.read_text(encoding="utf-8")
        return svgs[0].read_text(encoding="utf-8")
