"""Schematic ↔ PCB consistency (Engineering Change Order) report."""

from __future__ import annotations

from pathlib import Path
from typing import Any

from app.kicad.pcb import Board
from app.kicad.sch import Schematic


def _real_symbols(schematic: Schematic) -> dict[str, Any]:
    return {
        symbol.ref: symbol
        for symbol in schematic.symbols
        if symbol.ref and not symbol.ref.startswith("#") and not symbol.ref.endswith("?")
    }


def _named_schematic_nets(schematic: Schematic) -> dict[str, set[str]]:
    return {
        net["name"]: set(net["pins"])
        for net in schematic.netlist()["nets"]
        if net["name"] and not str(net["name"]).startswith("Net-")
    }


def _named_board_nets(board: Board) -> dict[str, set[str]]:
    return {
        net["name"]: set(net["pads"])
        for net in board.list_nets()
        if net["name"]
    }


def build_eco_report(schematic_path: Path, pcb_path: Path) -> dict[str, Any]:
    schematic = Schematic.load(schematic_path)
    board = Board.load(pcb_path)
    symbols = _real_symbols(schematic)
    footprints = {footprint.ref: footprint for footprint in board.footprints if footprint.ref}

    missing_on_pcb = [
        {
            "reference": reference,
            "value": symbol.val,
            "footprint": symbol.footprint,
            "syncable": bool(symbol.footprint),
        }
        for reference, symbol in sorted(symbols.items())
        if reference not in footprints
    ]
    extra_on_pcb = [
        {
            "reference": reference,
            "value": footprint.val,
            "footprint": footprint.lib_id,
        }
        for reference, footprint in sorted(footprints.items())
        if reference not in symbols
    ]
    value_mismatches = []
    footprint_mismatches = []
    for reference in sorted(symbols.keys() & footprints.keys()):
        symbol, footprint = symbols[reference], footprints[reference]
        if symbol.val != footprint.val:
            value_mismatches.append(
                {
                    "reference": reference,
                    "schematic": symbol.val,
                    "pcb": footprint.val,
                }
            )
        if symbol.footprint and symbol.footprint != footprint.lib_id:
            footprint_mismatches.append(
                {
                    "reference": reference,
                    "schematic": symbol.footprint,
                    "pcb": footprint.lib_id,
                }
            )

    schematic_nets = _named_schematic_nets(schematic)
    board_nets = _named_board_nets(board)
    net_mismatches = []
    for name in sorted(schematic_nets.keys() | board_nets.keys()):
        expected = schematic_nets.get(name, set())
        actual = board_nets.get(name, set())
        missing_pads = sorted(expected - actual)
        extra_pads = sorted(actual - expected)
        if missing_pads or extra_pads:
            net_mismatches.append(
                {
                    "net": name,
                    "missing_on_pcb": missing_pads,
                    "extra_on_pcb": extra_pads,
                }
            )

    syncable_missing = [item for item in missing_on_pcb if item["syncable"]]
    can_sync = bool(syncable_missing or value_mismatches or net_mismatches)
    suggested_actions = (
        [
            {
                "tool": "update_pcb_from_schematic",
                "args": {"sync_values": True, "sync_nets": True},
                "summary": "从原理图同步缺失封装、元件值和焊盘网络到 PCB",
                "reason": (
                    f"缺失封装 {len(syncable_missing)} 个、值差异 {len(value_mismatches)} 个、"
                    f"网络差异 {len(net_mismatches)} 个"
                ),
            }
        ]
        if can_sync
        else []
    )
    drift_count = (
        len(missing_on_pcb)
        + len(extra_on_pcb)
        + len(value_mismatches)
        + len(footprint_mismatches)
        + len(net_mismatches)
    )
    manual_count = (
        len(extra_on_pcb)
        + len(footprint_mismatches)
        + len([item for item in missing_on_pcb if not item["syncable"]])
    )
    return {
        "success": True,
        "status": "synchronized" if drift_count == 0 else "drift",
        "summary": {
            "schematic_components": len(symbols),
            "pcb_footprints": len(footprints),
            "drift_count": drift_count,
            "auto_sync_count": len(suggested_actions),
            "manual_count": manual_count,
        },
        "missing_on_pcb": missing_on_pcb,
        "extra_on_pcb": extra_on_pcb,
        "value_mismatches": value_mismatches,
        "footprint_mismatches": footprint_mismatches,
        "net_mismatches": net_mismatches,
        "suggested_actions": suggested_actions,
        "verification": [
            {"tool": "run_eco_check", "args": {}, "summary": "重新检查原理图与 PCB 一致性"},
            {"tool": "run_drc_check", "args": {}, "summary": "同步后运行 DRC"},
        ],
        "notes": [
            "PCB 中多余封装不会自动删除，避免误删机械件或仅 PCB 器件。",
            "封装型号不一致不会自动替换，替换可能改变焊盘编号与机械尺寸。",
            "原理图未分配 Footprint 的器件无法自动加入 PCB。",
        ],
    }
