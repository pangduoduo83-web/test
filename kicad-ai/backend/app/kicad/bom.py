"""Bill of materials + design-for-manufacturing (DFM) checks.

Everything is derived from the design files (and, when available, the KiCad
footprint library index). No pricing or stock data is invented.
"""

from __future__ import annotations

import csv
import io
import re
from pathlib import Path
from typing import Any

from app.kicad.pcb import Board
from app.kicad.sch import Schematic

PLACEHOLDER_VALUES = {"", "~", "?", "value", "val", "tbd", "todo", "dnp"}
# Imperial size code embedded in KiCad footprint names, e.g. R_0402_1005Metric.
_PACKAGE_RE = re.compile(r"_(01005|0201|0402|0603|0805|1206|1210|2010|2512)_")
FINE_PITCH_PACKAGES = {"01005", "0201"}
_PITCH_RE = re.compile(r"_P(\d+\.\d+)mm")
FINE_PITCH_MM = 0.5


def _footprint_index():
    """kcaa footprint index (None when KiCad libraries are not installed)."""
    try:
        from app.kicad.libraries import resolve_paths
        from kcaa.utils.footprint_index_manager import FootprintIndexManager
    except Exception:  # noqa: BLE001 — optional dependency
        return None
    paths = resolve_paths()
    if not paths.footprint_db.is_file():
        return None
    try:
        return FootprintIndexManager(db_path=paths.footprint_db)
    except Exception:  # noqa: BLE001
        return None


def _footprint_exists(index, lib_id: str) -> bool | None:
    if index is None or ":" not in lib_id:
        return None
    library, name = lib_id.split(":", 1)
    try:
        return index.get_footprint(library, name) is not None
    except Exception:  # noqa: BLE001
        return None


def _prefix(reference: str) -> str:
    return reference.rstrip("0123456789?")


def build_bom_report(
    pcb_path: Path | None,
    schematic_path: Path | None,
    constraints: dict[str, Any] | None = None,
) -> dict[str, Any]:
    constraints = constraints or {}
    if not (pcb_path and pcb_path.is_file()) and not (schematic_path and schematic_path.is_file()):
        return {"success": False, "error": "工程中没有可分析的 PCB 或原理图文件"}

    board = Board.load(pcb_path) if pcb_path and pcb_path.is_file() else None
    schematic = Schematic.load(schematic_path) if schematic_path and schematic_path.is_file() else None
    index = _footprint_index()

    # ---- component inventory (PCB is authoritative for assembly; fall back to schematic)
    components: list[dict[str, Any]] = []
    if board is not None:
        for footprint in board.footprints:
            if not footprint.ref:
                continue
            components.append(
                {
                    "reference": footprint.ref,
                    "value": footprint.val,
                    "footprint": footprint.lib_id,
                    "side": footprint.side,
                    "mount": "tht" if any(p.pad_type == "thru_hole" for p in footprint.pads) else "smd",
                    "exclude_from_bom": "exclude_from_bom" in footprint.attrs,
                    "dnp": "dnp" in footprint.attrs,
                    "source": "pcb",
                }
            )
    elif schematic is not None:
        for symbol in schematic.symbols:
            if not symbol.ref or symbol.ref.startswith("#"):
                continue
            components.append(
                {
                    "reference": symbol.ref,
                    "value": symbol.val,
                    "footprint": symbol.footprint,
                    "side": None,
                    "mount": None,
                    "exclude_from_bom": False,
                    "dnp": False,
                    "source": "schematic",
                }
            )

    schematic_values: dict[str, Any] = {}
    if schematic is not None:
        schematic_values = {s.ref: s for s in schematic.symbols if s.ref and not s.ref.startswith("#")}

    # ---- grouped BOM lines
    groups: dict[tuple[str, str], list[str]] = {}
    for item in components:
        if item["exclude_from_bom"] or item["dnp"]:
            continue
        groups.setdefault((item["value"], item["footprint"]), []).append(item["reference"])
    lines = [
        {
            "value": value,
            "footprint": footprint,
            "quantity": len(refs),
            "references": sorted(refs, key=lambda r: (_prefix(r), int(re.sub(r"\D", "", r) or 0))),
            "prefix": _prefix(refs[0]),
        }
        for (value, footprint), refs in sorted(groups.items(), key=lambda kv: (_prefix(kv[1][0]), kv[0]))
    ]

    # ---- DFM issues
    issues: list[dict[str, Any]] = []
    seen_refs: dict[str, int] = {}
    for item in components:
        ref = item["reference"]
        seen_refs[ref] = seen_refs.get(ref, 0) + 1
        if ref.endswith("?"):
            issues.append({"type": "unannotated", "severity": "error", "reference": ref, "description": f"{ref} 尚未分配最终位号"})
        if str(item["value"]).strip().lower() in PLACEHOLDER_VALUES:
            issues.append({"type": "missing_value", "severity": "error", "reference": ref, "description": f"{ref} 缺少有效的元件值"})
        fp = item["footprint"] or ""
        if not fp:
            issues.append({"type": "missing_footprint", "severity": "error", "reference": ref, "description": f"{ref} 未分配封装"})
        else:
            exists = _footprint_exists(index, fp)
            if exists is False:
                issues.append({"type": "footprint_not_in_library", "severity": "warning", "reference": ref, "description": f"{ref} 的封装 {fp} 不在已安装的 KiCad 库中，生产文件可能缺少 3D/焊盘定义核对"})
            match = _PACKAGE_RE.search(fp)
            if match and match.group(1) in FINE_PITCH_PACKAGES:
                issues.append({"type": "fine_pitch_package", "severity": "warning", "reference": ref, "description": f"{ref} 使用 {match.group(1)} 超小封装，普通贴片工艺良率低"})
            pitch = _PITCH_RE.search(fp)
            if pitch and float(pitch.group(1)) < FINE_PITCH_MM:
                issues.append({"type": "fine_pitch_ic", "severity": "info", "reference": ref, "description": f"{ref} 引脚间距 {pitch.group(1)} mm，需要确认板厂支持"})
        if schematic_values and ref in schematic_values and item["source"] == "pcb":
            sym = schematic_values[ref]
            if sym.val != item["value"]:
                issues.append({"type": "value_mismatch", "severity": "warning", "reference": ref, "description": f"{ref} 值不一致：原理图 {sym.val} / PCB {item['value']}"})
    for ref, count in seen_refs.items():
        if count > 1:
            issues.append({"type": "duplicate_reference", "severity": "error", "reference": ref, "description": f"位号 {ref} 重复 {count} 次"})

    # ---- assembly profile
    assembly: dict[str, Any] = {}
    if board is not None:
        sides = {item["side"] for item in components if item["mount"] == "smd"}
        assembly = {
            "smd_count": sum(1 for item in components if item["mount"] == "smd"),
            "tht_count": sum(1 for item in components if item["mount"] == "tht"),
            "top_side_count": sum(1 for item in components if item["side"] == "top"),
            "bottom_side_count": sum(1 for item in components if item["side"] == "bottom"),
            "double_sided_smd": len(sides) > 1,
            "dnp_count": sum(1 for item in components if item["dnp"]),
            "excluded_count": sum(1 for item in components if item["exclude_from_bom"]),
        }
        if assembly["double_sided_smd"]:
            issues.append({"type": "double_sided_assembly", "severity": "info", "reference": None, "description": "顶层和底层都有贴片元件，需要两次贴装/回流，成本更高"})
        if assembly["tht_count"] and assembly["smd_count"]:
            issues.append({"type": "mixed_technology", "severity": "info", "reference": None, "description": "同时包含贴片与插件元件，需要额外波峰焊或手工焊接工序"})
        max_height = constraints.get("max_component_height_mm")
        if max_height:
            assembly["max_component_height_mm"] = max_height
            assembly["height_check"] = "封装库不含高度信息，请人工核对高于限制的器件"

    errors = sum(1 for i in issues if i["severity"] == "error")
    warnings = sum(1 for i in issues if i["severity"] == "warning")
    return {
        "success": True,
        "status": "errors" if errors else "warnings" if warnings else "ready",
        "library_index_available": index is not None,
        "summary": {
            "line_count": len(lines),
            "component_count": sum(line["quantity"] for line in lines),
            "error_count": errors,
            "warning_count": warnings,
            "issue_count": len(issues),
        },
        "lines": lines,
        "issues": issues,
        "assembly": assembly,
        "by_prefix": _count_by_prefix(components),
    }


def _count_by_prefix(components: list[dict[str, Any]]) -> dict[str, int]:
    out: dict[str, int] = {}
    for item in components:
        key = _prefix(item["reference"]) or "?"
        out[key] = out.get(key, 0) + 1
    return dict(sorted(out.items()))


def bom_csv(report: dict[str, Any]) -> str:
    buffer = io.StringIO()
    writer = csv.writer(buffer)
    writer.writerow(["Item", "Quantity", "References", "Value", "Footprint"])
    for index, line in enumerate(report.get("lines", []), start=1):
        writer.writerow([index, line["quantity"], " ".join(line["references"]), line["value"], line["footprint"]])
    return buffer.getvalue()
