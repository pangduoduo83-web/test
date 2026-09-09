"""KiCad file/runtime compatibility inspection.

Modern KiCad releases open older projects, but older ``kicad-cli`` versions
cannot open files saved by a newer major release.  Keep this check cheap and
read-only so it can be shown in the project UI before DRC/export fails.
"""

from __future__ import annotations

import re
from pathlib import Path
from typing import Any

_GENERATOR_VERSION_RE = re.compile(r"\(generator_version\s+\"?(\d+(?:\.\d+)?)\"?\)")
_FORMAT_VERSION_RE = re.compile(r"\(version\s+(\d{8})\)")
_SEMVER_RE = re.compile(r"(\d+)(?:\.(\d+))?")


def _major_from_format_date(value: int) -> int | None:
    # Stable format boundaries.  The generator version, when present, wins.
    if value >= 20250000:
        return 10
    if value >= 20241100:
        return 9
    if value >= 20230000:
        return 8
    if value >= 20211100:
        return 7
    return None


def major_version(value: str | None) -> int | None:
    match = _SEMVER_RE.search(value or "")
    return int(match.group(1)) if match else None


def inspect_design_file(path: Path) -> dict[str, Any]:
    """Return version metadata for one KiCad design file."""
    result: dict[str, Any] = {
        "path": path.name,
        "kind": path.suffix.removeprefix(".").replace("kicad_", ""),
        "generator_version": None,
        "format_version": None,
        "major": None,
    }
    if not path.is_file() or path.suffix.lower() not in {".kicad_pcb", ".kicad_sch"}:
        return result
    text = path.read_text(encoding="utf-8", errors="replace")[:128_000]
    generator = _GENERATOR_VERSION_RE.search(text)
    format_match = _FORMAT_VERSION_RE.search(text)
    if generator:
        result["generator_version"] = generator.group(1)
        result["major"] = major_version(generator.group(1))
    if format_match:
        raw = int(format_match.group(1))
        result["format_version"] = raw
        result["major"] = result["major"] or _major_from_format_date(raw)
    return result


def file_compatibility(path: Path, cli_version: str | None) -> dict[str, Any]:
    """Inspect one file and compare it with the installed CLI."""
    item = inspect_design_file(path)
    runtime_major = major_version(cli_version)
    file_major = item.get("major")
    if path.suffix.lower() not in {".kicad_pcb", ".kicad_sch"}:
        item.update(
            status="not_applicable",
            compatible=None,
            message="该文件不包含可识别的 KiCad 设计格式版本。",
        )
    elif runtime_major is None:
        item.update(
            status="cli_unavailable",
            compatible=None,
            message="未安装 kicad-cli；官方 DRC、渲染和导出不可用。",
        )
    elif file_major is None:
        item.update(
            status="unknown",
            compatible=None,
            message=f"无法识别工程格式版本；服务器使用 KiCad {runtime_major}。",
        )
    elif file_major > runtime_major:
        item.update(
            status="incompatible",
            compatible=False,
            message=(
                f"文件由 KiCad {file_major} 保存，但服务器是 KiCad {runtime_major}；"
                "官方 DRC、渲染和导出不可用。"
            ),
        )
    else:
        item.update(
            status="compatible",
            compatible=True,
            message=f"KiCad {file_major} 格式与服务器 KiCad {runtime_major} 兼容。",
        )
    item["runtime_version"] = cli_version
    item["runtime_major"] = runtime_major
    return item


def require_cli_compatible(path: Path, cli_version: str | None) -> None:
    """Raise a clear error before invoking an older CLI on a newer file."""
    item = file_compatibility(path, cli_version)
    if item["compatible"] is False:
        raise RuntimeError(item["message"])


def compatibility_report(files: list[Path], cli_version: str | None) -> dict[str, Any]:
    """Compare project design files with the installed ``kicad-cli`` major."""
    runtime_major = major_version(cli_version)
    inspected = [file_compatibility(path, cli_version) for path in files if path]
    incompatible = [item for item in inspected if item["compatible"] is False]
    project_major = max((int(item["major"]) for item in inspected if item.get("major")), default=None)
    if runtime_major is None:
        status = "cli_unavailable"
        message = "未安装 kicad-cli；仍可使用内置解析与轻量检查，官方 DRC/导出不可用。"
    elif incompatible:
        status = "incompatible"
        message = (
            f"工程由 KiCad {project_major} 保存，但服务器是 KiCad {runtime_major}；"
            "官方 DRC、渲染或导出可能失败。"
        )
    else:
        status = "compatible"
        message = f"工程格式与服务器 KiCad {runtime_major} 兼容。"
    return {
        "status": status,
        "compatible": None if runtime_major is None else not incompatible,
        "project_major": project_major,
        "runtime_version": cli_version,
        "runtime_major": runtime_major,
        "files": inspected,
        "message": message,
    }
