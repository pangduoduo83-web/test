"""Unified ERC + DRC + placement-quality review."""

from __future__ import annotations

from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from app.kicad import cli as kicad_cli
from app.kicad.pcb import Board
from app.kicad.sch import Schematic


def _run_drc(board: Board, path: Path) -> dict[str, Any]:
    if kicad_cli.available():
        try:
            return kicad_cli.run_drc(path)
        except Exception as exc:  # noqa: BLE001 — report fallback to caller
            result = board.drc_lite()
            result["note"] = f"kicad-cli DRC 失败 ({exc})，已回退到内置检查。"
            return result
    result = board.drc_lite()
    result["note"] = "未检测到 kicad-cli，使用内置轻量 DRC。"
    return result


def _run_erc(schematic: Schematic, path: Path) -> dict[str, Any]:
    if kicad_cli.available():
        try:
            return kicad_cli.run_erc(path)
        except Exception as exc:  # noqa: BLE001 — report fallback to caller
            result = schematic.erc_lite()
            result["note"] = f"kicad-cli ERC 失败 ({exc})，已回退到内置检查。"
            return result
    return schematic.erc_lite()


def _issue(source: str, index: int, raw: dict[str, Any]) -> dict[str, Any]:
    kind = str(raw.get("type") or raw.get("key") or "unknown")
    return {
        "id": f"{source}-{index}",
        "source": source,
        "type": kind,
        "severity": str(raw.get("severity") or "warning"),
        "description": str(raw.get("description") or kind),
        "items": raw.get("items") if isinstance(raw.get("items"), list) else [],
        "sheet": raw.get("sheet"),
        "auto_fixable": False,
    }


def _placement_actions(
    board: Board, constraints: dict[str, Any]
) -> tuple[list[dict[str, Any]], float]:
    """Simulate conservative placement fixes and return concrete tool actions."""
    actions: list[dict[str, Any]] = []
    planned: set[str] = set()
    edge = float(constraints.get("copper_edge_clearance_mm", 0.5) or 0.5)
    grid = max(0.01, float(constraints.get("placement_grid_mm", 0.1) or 0.1))
    outline = board.outline_bbox()

    if outline is None:
        bbox = board.board_bbox()
        if bbox:
            margin = max(5.0, edge)
            actions.append(
                {
                    "tool": "set_board_outline_rect",
                    "args": {
                        "x1": round(bbox[0] - margin, 3),
                        "y1": round(bbox[1] - margin, 3),
                        "x2": round(bbox[2] + margin, 3),
                        "y2": round(bbox[3] + margin, 3),
                    },
                    "summary": "围绕现有封装创建矩形板框",
                    "reason": "当前 PCB 缺少 Edge.Cuts 板框",
                    "issue_types": ["missing_outline"],
                }
            )
    else:
        ox1, oy1, ox2, oy2 = outline
        for reference in board.placement_score(grid)["outside_outline"]:
            footprint = board.get_footprint(reference)
            if footprint is None:
                continue
            left = footprint.x - footprint.bbox[0]
            right = footprint.bbox[2] - footprint.x
            top = footprint.y - footprint.bbox[1]
            bottom = footprint.bbox[3] - footprint.y
            min_x, max_x = ox1 + edge + left, ox2 - edge - right
            min_y, max_y = oy1 + edge + top, oy2 - edge - bottom
            if min_x > max_x or min_y > max_y:
                continue
            x = round(round(min(max(footprint.x, min_x), max_x) / grid) * grid, 4)
            y = round(round(min(max(footprint.y, min_y), max_y) / grid) * grid, 4)
            if (x, y) == (footprint.x, footprint.y):
                continue
            actions.append(
                {
                    "tool": "set_footprint_position",
                    "args": {
                        "reference": footprint.ref,
                        "x": x,
                        "y": y,
                        "rotation": footprint.rotation,
                    },
                    "summary": f"将 {footprint.ref} 移回板框安全区",
                    "reason": f"保持至少 {edge:g} mm 的铜到板边间距",
                    "issue_types": ["copper_edge_clearance"],
                }
            )
            planned.add(footprint.ref)
            board.set_footprint_position(footprint.ref, x, y, footprint.rotation)

    for first, second in list(board.courtyard_overlaps()):
        movable = second if second not in planned else first
        if movable in planned:
            continue
        footprint = board.get_footprint(movable)
        if footprint is None:
            continue
        candidates = board.find_free_area(
            max(footprint.width, grid),
            max(footprint.height, grid),
            step=max(grid, 0.5),
            margin=max(edge, 0.25),
        )
        if not candidates:
            continue
        target = candidates[0]
        x = round(round(float(target["x"]) / grid) * grid, 4)
        y = round(round(float(target["y"]) / grid) * grid, 4)
        actions.append(
            {
                "tool": "set_footprint_position",
                "args": {
                    "reference": footprint.ref,
                    "x": x,
                    "y": y,
                    "rotation": footprint.rotation,
                },
                "summary": f"移动 {footprint.ref} 解除与 {first if movable == second else second} 的占位重叠",
                "reason": "保持封装 Courtyard 互不重叠",
                "issue_types": ["courtyards_overlap"],
            }
        )
        planned.add(footprint.ref)
        board.set_footprint_position(footprint.ref, x, y, footprint.rotation)

    return actions, float(board.placement_score(grid)["score"])


def run_design_review(
    pcb_path: Path | None,
    schematic_path: Path | None,
    constraints: dict[str, Any] | None = None,
) -> dict[str, Any]:
    constraints = constraints or {}
    if not (pcb_path and pcb_path.is_file()) and not (schematic_path and schematic_path.is_file()):
        return {
            "success": False,
            "status": "no_design_files",
            "error": "工程中没有可审查的 PCB 或原理图文件",
        }
    placement: dict[str, Any] | None = None
    drc: dict[str, Any] | None = None
    erc: dict[str, Any] | None = None
    actions: list[dict[str, Any]] = []
    predicted_score: float | None = None

    if pcb_path and pcb_path.is_file():
        board = Board.load(pcb_path)
        placement = board.placement_score(float(constraints.get("placement_grid_mm", 0.1) or 0.1))
        drc = _run_drc(board, pcb_path)
        # Use a fresh in-memory board because suggestions simulate moves.
        actions, predicted_score = _placement_actions(Board.load(pcb_path), constraints)
    if schematic_path and schematic_path.is_file():
        schematic = Schematic.load(schematic_path)
        erc = _run_erc(schematic, schematic_path)

    issues: list[dict[str, Any]] = []
    for source, report in (("drc", drc), ("erc", erc)):
        if not report:
            continue
        for index, raw in enumerate(report.get("violations", [])):
            if isinstance(raw, dict):
                issues.append(_issue(source, index, raw))
    action_types = {
        issue_type
        for action in actions
        for issue_type in action.get("issue_types", [])
    }
    for issue in issues:
        issue["auto_fixable"] = issue["type"] in action_types

    if placement:
        for reference in placement.get("misaligned_to_grid", []):
            issues.append(
                {
                    "id": f"placement-grid-{reference}",
                    "source": "placement",
                    "type": "grid_alignment",
                    "severity": "info",
                    "description": f"{reference} 未对齐到 {placement['grid_mm']:g} mm 布局栅格",
                    "items": [{"description": f"Footprint {reference}"}],
                    "auto_fixable": False,
                }
            )

    errors = sum(1 for issue in issues if issue["severity"] == "error")
    warnings = sum(1 for issue in issues if issue["severity"] == "warning")
    placement_score = float(placement["score"]) if placement else 100.0
    quality_score = max(0.0, placement_score - min(50.0, errors * 8.0 + warnings * 1.5))
    status = "errors" if errors else "warnings" if warnings else "passed"
    return {
        "success": True,
        "status": status,
        "quality_score": round(quality_score, 1),
        "grade": "A" if quality_score >= 90 else "B" if quality_score >= 75 else "C" if quality_score >= 60 else "D",
        "generated_at": datetime.now(timezone.utc).isoformat(),
        "summary": {
            "error_count": errors,
            "warning_count": warnings,
            "issue_count": len(issues),
            "auto_fixable_count": len(actions),
        },
        "placement": placement,
        "predicted_placement_score": round(predicted_score, 1) if predicted_score is not None else None,
        "drc": drc,
        "erc": erc,
        "issues": issues,
        "suggested_actions": actions,
        "verification": [
            *([{"tool": "run_drc_check", "args": {}, "summary": "重新运行 DRC"}] if drc else []),
            *([{"tool": "run_erc_check", "args": {}, "summary": "重新运行 ERC"}] if erc else []),
            *([{"tool": "score_placement", "args": {}, "summary": "重新计算布局评分"}] if placement else []),
        ],
    }
