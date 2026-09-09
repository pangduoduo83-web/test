"""Read / analyse ``.kicad_sch`` files (KiCad 6+ S-expression schematics).

Coordinates: millimetres, +X right, +Y down (screen convention). Symbol library
pin coordinates inside ``lib_symbols`` are Y-up and get converted here.
"""

from __future__ import annotations

import math
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any

from . import sexpr
from .sexpr import Str, Sym, child, children, value

GRID = 1.27


@dataclass
class Pin:
    number: str
    name: str
    x: float
    y: float
    electrical: str


@dataclass
class Symbol:
    node: list
    lib_id: str
    ref: str
    val: str
    footprint: str
    x: float
    y: float
    rotation: float
    mirror: str | None
    unit: int
    uuid: str
    pins: list[Pin] = field(default_factory=list)

    def to_dict(self, include_pins: bool = False) -> dict[str, Any]:
        d = {
            "reference": self.ref,
            "value": self.val,
            "lib_id": self.lib_id,
            "footprint": self.footprint,
            "x": round(self.x, 3),
            "y": round(self.y, 3),
            "rotation": self.rotation,
            "mirror": self.mirror,
            "unit": self.unit,
            "uuid": self.uuid,
            "pin_count": len(self.pins),
        }
        if include_pins:
            d["pins"] = [
                {"number": p.number, "name": p.name, "x": round(p.x, 3), "y": round(p.y, 3), "type": p.electrical}
                for p in self.pins
            ]
        return d


def _at(node: list) -> tuple[float, float, float]:
    at = child(node, "at")
    if not at:
        return 0.0, 0.0, 0.0
    return (
        float(at[1]) if len(at) > 1 else 0.0,
        float(at[2]) if len(at) > 2 else 0.0,
        float(at[3]) if len(at) > 3 else 0.0,
    )


def _property(node: list, name: str) -> str:
    for p in children(node, "property"):
        if len(p) > 2 and p[1] == name:
            return str(p[2])
    return ""


def _snap(v: float) -> float:
    return round(round(v / GRID) * GRID, 4)


def _on_segment(px: float, py: float, x1: float, y1: float, x2: float, y2: float, eps: float = 0.02) -> bool:
    if px < min(x1, x2) - eps or px > max(x1, x2) + eps or py < min(y1, y2) - eps or py > max(y1, y2) + eps:
        return False
    cross = (x2 - x1) * (py - y1) - (y2 - y1) * (px - x1)
    length = math.hypot(x2 - x1, y2 - y1) or 1.0
    return abs(cross) / length <= eps


class Schematic:
    def __init__(self, tree: list, path: Path | None = None):
        if not tree or tree[0] != "kicad_sch":
            raise ValueError("not a kicad_sch document")
        self.tree = tree
        self.path = path
        self.lib_pins: dict[str, list[tuple[str, str, float, float, str]]] = {}
        self.symbols: list[Symbol] = []
        self._parse()

    @classmethod
    def load(cls, path: str | Path) -> Schematic:
        p = Path(path)
        return cls(sexpr.load(p), p)

    # ------------------------------------------------------------------ parse
    def _parse(self) -> None:
        self.lib_pins = {}
        libs = child(self.tree, "lib_symbols")
        if libs:
            for lib in children(libs, "symbol"):
                name = str(lib[1])
                pins: list[tuple[str, str, float, float, str]] = []
                for sub in sexpr.walk(lib):
                    if sub is lib or not sub or sub[0] != "pin":
                        continue
                    px, py, prot = _at(sub)
                    num_node = child(sub, "number")
                    name_node = child(sub, "name")
                    num = str(num_node[1]) if num_node and len(num_node) > 1 else ""
                    nm = str(name_node[1]) if name_node and len(name_node) > 1 else ""
                    pins.append((num, nm, px, py, str(sub[1]) if len(sub) > 1 else ""))
                self.lib_pins[name] = pins
        self.symbols = [self._parse_symbol(s) for s in children(self.tree, "symbol")]

    def _parse_symbol(self, node: list) -> Symbol:
        x, y, rot = _at(node)
        mirror_node = child(node, "mirror")
        mirror = str(mirror_node[1]) if mirror_node and len(mirror_node) > 1 else None
        lib_id = str(value(node, "lib_id", ""))
        sym = Symbol(
            node=node,
            lib_id=lib_id,
            ref=_property(node, "Reference"),
            val=_property(node, "Value"),
            footprint=_property(node, "Footprint"),
            x=x,
            y=y,
            rotation=rot,
            mirror=mirror,
            unit=int(value(node, "unit", 1)),
            uuid=str(value(node, "uuid", "")),
        )
        for num, nm, px, py, etype in self.lib_pins.get(lib_id, []):
            ax, ay = self._transform_pin(px, py, x, y, rot, mirror)
            sym.pins.append(Pin(num, nm, ax, ay, etype))
        return sym

    @staticmethod
    def _transform_pin(px: float, py: float, x: float, y: float, rot: float, mirror: str | None):
        a = math.radians(rot)
        rx = px * math.cos(a) - py * math.sin(a)
        ry = px * math.sin(a) + py * math.cos(a)
        if mirror == "x":
            ry = -ry
        elif mirror == "y":
            rx = -rx
        return round(x + rx, 4), round(y - ry, 4)

    # ------------------------------------------------------------------ query
    def get_symbol(self, ref: str) -> Symbol | None:
        r = ref.strip().lower()
        for s in self.symbols:
            if s.ref.lower() == r:
                return s
        return None

    def sheet_info(self) -> dict[str, Any]:
        paper = str(value(self.tree, "paper", "A4"))
        sizes = {"A4": (297.0, 210.0), "A3": (420.0, 297.0), "A2": (594.0, 420.0), "A": (279.4, 215.9), "B": (431.8, 279.4)}
        w, h = sizes.get(paper, (297.0, 210.0))
        return {
            "file": str(self.path) if self.path else None,
            "version": value(self.tree, "version"),
            "generator": str(value(self.tree, "generator", "")),
            "paper": paper,
            "size_mm": [w, h],
            "grid_mm": GRID,
            "recommended_drawing_area": {"x1": 12.7, "y1": 12.7, "x2": round(w - 12.7, 2), "y2": round(h - 40.0, 2)},
            "symbol_count": len(self.symbols),
            "wire_count": len(self.wires()),
            "label_count": len(self.labels()),
            "sheet_count": len(children(self.tree, "sheet")),
        }

    def wires(self) -> list[dict[str, Any]]:
        out = []
        for w in children(self.tree, "wire"):
            pts = [(float(xy[1]), float(xy[2])) for xy in children(child(w, "pts") or [], "xy")]
            if len(pts) >= 2:
                out.append({"start": pts[0], "end": pts[-1], "uuid": str(value(w, "uuid", ""))})
        return out

    def labels(self) -> list[dict[str, Any]]:
        out = []
        for kind in ("label", "global_label", "hierarchical_label", "power"):
            for l in children(self.tree, kind):
                x, y, rot = _at(l)
                out.append({"type": kind, "text": str(l[1]) if len(l) > 1 else "", "x": x, "y": y, "rotation": rot})
        return out

    def netlist(self) -> dict[str, Any]:
        """Approximate connectivity by unioning wire endpoints, pins and labels."""
        parent: dict[tuple[float, float], tuple[float, float]] = {}

        def key(x: float, y: float) -> tuple[float, float]:
            return (round(x, 2), round(y, 2))

        def find(k):
            parent.setdefault(k, k)
            while parent[k] != k:
                parent[k] = parent[parent[k]]
                k = parent[k]
            return k

        def union(a, b):
            ra, rb = find(a), find(b)
            if ra != rb:
                parent[rb] = ra

        wires = self.wires()
        for w in wires:
            union(key(*w["start"]), key(*w["end"]))

        def attach(k: tuple[float, float]) -> None:
            """Join point *k* to every wire segment it touches (endpoint or mid-span)."""
            find(k)
            for w in wires:
                (x1, y1), (x2, y2) = w["start"], w["end"]
                if _on_segment(k[0], k[1], x1, y1, x2, y2):
                    union(k, key(x1, y1))

        # T-junctions: a wire ending on another wire's span is connected (KiCad
        # auto-places a junction there), as are explicit junction dots.
        for w in wires:
            attach(key(*w["start"]))
            attach(key(*w["end"]))
        for j in children(self.tree, "junction"):
            jx, jy, _ = _at(j)
            attach(key(jx, jy))

        pin_points: list[tuple[tuple[float, float], str]] = []
        for s in self.symbols:
            for p in s.pins:
                k = key(p.x, p.y)
                attach(k)
                pin_points.append((k, f"{s.ref}.{p.number}"))
        label_names: dict[tuple[float, float], str] = {}
        for l in self.labels():
            k = key(l["x"], l["y"])
            attach(k)
            label_names[k] = l["text"]
        # Labels with the same text join nets
        by_text: dict[str, tuple[float, float]] = {}
        for k, text in label_names.items():
            if text in by_text:
                union(by_text[text], k)
            else:
                by_text[text] = k
        nets: dict[tuple[float, float], dict[str, Any]] = {}
        for k, pin in pin_points:
            root = find(k)
            nets.setdefault(root, {"name": None, "pins": []})["pins"].append(pin)
        for k, text in label_names.items():
            root = find(k)
            nets.setdefault(root, {"name": None, "pins": []})
            nets[root]["name"] = nets[root]["name"] or text
        result = []
        auto = 1
        for net in nets.values():
            if len(net["pins"]) < 1:
                continue
            name = net["name"] or f"Net-{auto}"
            if not net["name"]:
                auto += 1
            result.append({"name": name, "pins": sorted(net["pins"])})
        result.sort(key=lambda n: n["name"])
        unconnected = [n["pins"][0] for n in result if len(n["pins"]) == 1 and not n["name"].startswith(("GND", "VCC", "+", "-"))]
        return {"nets": result, "net_count": len(result), "unconnected_pins": unconnected}

    def reference_conflicts(self) -> list[str]:
        seen: dict[str, int] = {}
        for s in self.symbols:
            if s.ref and not s.ref.endswith("?"):
                seen[s.ref] = seen.get(s.ref, 0) + 1
        return [r for r, c in seen.items() if c > 1]

    def erc_lite(self) -> dict[str, Any]:
        """Built-in ERC subset used when ``kicad-cli`` is unavailable."""
        violations: list[dict[str, Any]] = []
        for reference in self.reference_conflicts():
            symbols = [symbol for symbol in self.symbols if symbol.ref == reference]
            violations.append(
                {
                    "type": "duplicate_reference",
                    "severity": "error",
                    "description": f"Duplicate schematic reference: {reference}",
                    "items": [
                        {
                            "description": f"Symbol {reference}",
                            "pos": {"x": symbol.x, "y": symbol.y},
                        }
                        for symbol in symbols
                    ],
                }
            )
        for symbol in self.symbols:
            if symbol.ref.endswith("?"):
                violations.append(
                    {
                        "type": "unannotated_symbol",
                        "severity": "error",
                        "description": f"Symbol {symbol.val or symbol.lib_id} has no final reference",
                        "items": [
                            {
                                "description": symbol.ref or symbol.val or symbol.lib_id,
                                "pos": {"x": symbol.x, "y": symbol.y},
                            }
                        ],
                    }
                )
            if (
                symbol.ref
                and not symbol.ref.startswith("#")
                and not symbol.footprint
                and not symbol.lib_id.startswith("power:")
            ):
                violations.append(
                    {
                        "type": "missing_footprint",
                        "severity": "warning",
                        "description": f"{symbol.ref} has no footprint assigned",
                        "items": [
                            {
                                "description": f"Symbol {symbol.ref}",
                                "pos": {"x": symbol.x, "y": symbol.y},
                            }
                        ],
                    }
                )
        for pin in self.netlist()["unconnected_pins"]:
            reference = pin.split(".", 1)[0]
            symbol = self.get_symbol(reference)
            violations.append(
                {
                    "type": "unconnected_pin",
                    "severity": "warning",
                    "description": f"Pin {pin} has no connection",
                    "items": [
                        {
                            "description": f"Pin {pin}",
                            "pos": {"x": symbol.x, "y": symbol.y} if symbol else None,
                        }
                    ],
                }
            )
        errors = sum(1 for item in violations if item["severity"] == "error")
        warnings = sum(1 for item in violations if item["severity"] == "warning")
        return {
            "engine": "builtin-lite",
            "passed": errors == 0,
            "error_count": errors,
            "warning_count": warnings,
            "violations": violations,
            "note": "内置轻量 ERC 仅覆盖重复/未注释位号、未分配封装和未连接引脚。",
        }

    # ------------------------------------------------------------------- edit
    def move_symbol(self, ref: str, x: float | None, y: float | None, rotation: float | None) -> Symbol:
        s = self.get_symbol(ref)
        if s is None:
            raise KeyError(f"symbol '{ref}' not found")
        nx = _snap(s.x if x is None else float(x))
        ny = _snap(s.y if y is None else float(y))
        nr = s.rotation if rotation is None else float(rotation)
        if nr % 90:
            raise ValueError("schematic rotation must be a multiple of 90")
        dx, dy = nx - s.x, ny - s.y
        sexpr.set_child(s.node, "at", [nx, ny, nr % 360])
        for p in children(s.node, "property"):
            at = child(p, "at")
            if at and len(at) > 2:
                at[1] = float(at[1]) + dx
                at[2] = float(at[2]) + dy
        self._parse()
        return self.get_symbol(ref)  # type: ignore[return-value]

    def set_property(self, ref: str, name: str, val: str) -> Symbol:
        s = self.get_symbol(ref)
        if s is None:
            raise KeyError(f"symbol '{ref}' not found")
        for p in children(s.node, "property"):
            if len(p) > 2 and p[1] == name:
                p[2] = Str(val)
                break
        else:
            s.node.append(
                [Sym("property"), Str(name), Str(val), [Sym("at"), s.x, s.y + 2.54 * (len(children(s.node, "property")) + 1), 0],
                 [Sym("effects"), [Sym("font"), [Sym("size"), 1.27, 1.27]]]]
            )
        self._parse()
        return self.get_symbol(ref)  # type: ignore[return-value]

    def add_wire(self, x1: float, y1: float, x2: float, y2: float) -> dict[str, Any]:
        import uuid as _uuid

        node = [
            Sym("wire"),
            [Sym("pts"), [Sym("xy"), _snap(x1), _snap(y1)], [Sym("xy"), _snap(x2), _snap(y2)]],
            [Sym("stroke"), [Sym("width"), 0], [Sym("type"), Sym("default")]],
            [Sym("uuid"), Str(str(_uuid.uuid4()))],
        ]
        self.tree.append(node)
        return {"start": [_snap(x1), _snap(y1)], "end": [_snap(x2), _snap(y2)]}

    def add_label(self, text: str, x: float, y: float, rotation: float = 0) -> dict[str, Any]:
        import uuid as _uuid

        node = [
            Sym("label"),
            Str(text),
            [Sym("at"), _snap(x), _snap(y), rotation],
            [Sym("effects"), [Sym("font"), [Sym("size"), 1.27, 1.27]], [Sym("justify"), Sym("left"), Sym("bottom")]],
            [Sym("uuid"), Str(str(_uuid.uuid4()))],
        ]
        self.tree.append(node)
        return {"text": text, "x": _snap(x), "y": _snap(y)}

    def save(self, path: str | Path | None = None) -> Path:
        target = Path(path) if path else self.path
        if target is None:
            raise ValueError("no path to save to")
        sexpr.dump(self.tree, target)
        self.path = target
        return target

    # ------------------------------------------------------------------ render
    def selection_map(self, width_px: int = 1200) -> dict[str, Any]:
        """Symbols and pixel bounds matching :meth:`render_svg` exactly."""
        info = self.sheet_info()
        w_mm, h_mm = info["size_mm"]
        scale = width_px / w_mm
        height_px = int(h_mm * scale)
        elements: list[dict[str, Any]] = []
        for symbol in self.symbols:
            if not symbol.ref:
                continue
            if symbol.pins:
                min_x = min(p.x for p in symbol.pins)
                max_x = max(p.x for p in symbol.pins)
                min_y = min(p.y for p in symbol.pins)
                max_y = max(p.y for p in symbol.pins)
                width = max(10.0, (max_x - min_x) + 6.0)
                height = max(10.0, (max_y - min_y) + 6.0)
                x1 = (min_x + max_x) / 2.0 - width / 2.0
                y1 = (min_y + max_y) / 2.0 - height / 2.0
            else:
                width = height = 10.0
                x1, y1 = symbol.x - 5.0, symbol.y - 5.0
            x2, y2 = x1 + width, y1 + height
            elements.append(
                {
                    "id": symbol.ref,
                    "reference": symbol.ref,
                    "label": f"{symbol.ref} · {symbol.val}" if symbol.val else symbol.ref,
                    "value": symbol.val,
                    "kind": "symbol",
                    "position": [round(symbol.x, 4), round(symbol.y, 4)],
                    "bbox": [
                        round(x1 * scale, 2),
                        round(y1 * scale, 2),
                        round(x2 * scale, 2),
                        round(y2 * scale, 2),
                    ],
                    "world_bbox": [round(v, 4) for v in (x1, y1, x2, y2)],
                }
            )
        return {
            "mode": "sch",
            "canvas": {"width": width_px, "height": height_px},
            "world_bounds": [0.0, 0.0, float(w_mm), float(h_mm)],
            "elements": elements,
        }

    def render_svg(self, width_px: int = 1200) -> str:
        info = self.sheet_info()
        w_mm, h_mm = info["size_mm"]
        scale = width_px / w_mm
        height_px = int(h_mm * scale)

        def sx(v: float) -> float:
            return v * scale

        def sy(v: float) -> float:
            return v * scale

        parts = [
            f'<svg xmlns="http://www.w3.org/2000/svg" width="{width_px}" height="{height_px}" viewBox="0 0 {width_px} {height_px}">',
            f'<rect width="{width_px}" height="{height_px}" fill="#ffffff"/>',
            # Outer sheet border
            f'<rect x="{sx(10):.1f}" y="{sy(10):.1f}" width="{sx(w_mm - 20):.1f}" height="{sy(h_mm - 20):.1f}" fill="none" stroke="#840000" stroke-width="1.5"/>',
            f'<rect x="{sx(12):.1f}" y="{sy(12):.1f}" width="{sx(w_mm - 24):.1f}" height="{sy(h_mm - 24):.1f}" fill="none" stroke="#840000" stroke-width="0.8"/>',
        ]

        # Title block
        tb_w, tb_h = 75.0, 22.0
        tb_x, tb_y = w_mm - 12.0 - tb_w, h_mm - 12.0 - tb_h
        parts.extend([
            f'<rect x="{sx(tb_x):.1f}" y="{sy(tb_y):.1f}" width="{sx(tb_w):.1f}" height="{sy(tb_h):.1f}" fill="#fffdfa" stroke="#840000" stroke-width="1"/>',
            f'<line x1="{sx(tb_x):.1f}" y1="{sy(tb_y + 11):.1f}" x2="{sx(tb_x + tb_w):.1f}" y2="{sy(tb_y + 11):.1f}" stroke="#840000" stroke-width="0.8"/>',
            f'<text x="{sx(tb_x + 3):.1f}" y="{sy(tb_y + 8):.1f}" font-size="{max(8, int(scale * 3.0))}" font-weight="bold" fill="#000000" font-family="sans-serif">KiCad Schematic</text>',
            f'<text x="{sx(tb_x + 3):.1f}" y="{sy(tb_y + 18):.1f}" font-size="{max(7, int(scale * 2.2))}" fill="#555555" font-family="sans-serif">Paper: {info["paper"]} | Symbols: {len(self.symbols)}</text>',
        ])

        # Junction dots calculation
        pt_counts: dict[tuple[float, float], int] = {}
        for w in self.wires():
            p1 = (round(w["start"][0], 2), round(w["start"][1], 2))
            p2 = (round(w["end"][0], 2), round(w["end"][1], 2))
            pt_counts[p1] = pt_counts.get(p1, 0) + 1
            pt_counts[p2] = pt_counts.get(p2, 0) + 1

        # Wires
        for w in self.wires():
            x1, y1 = w["start"]
            x2, y2 = w["end"]
            parts.append(
                f'<line x1="{sx(x1):.1f}" y1="{sy(y1):.1f}" x2="{sx(x2):.1f}" y2="{sy(y2):.1f}" stroke="#008400" stroke-width="1.8" stroke-linecap="round"/>'
            )

        # Junctions
        for pt, cnt in pt_counts.items():
            if cnt >= 3:
                parts.append(f'<circle cx="{sx(pt[0]):.1f}" cy="{sy(pt[1]):.1f}" r="3" fill="#008400"/>')

        # Labels
        for l in self.labels():
            lx, ly = l["x"], l["y"]
            txt = _esc(l["text"])
            rot = l["rotation"]
            parts.append(
                f'<g transform="translate({sx(lx):.1f}, {sy(ly):.1f}) rotate({rot})">'
                f'<circle cx="0" cy="0" r="2" fill="#004b99"/>'
                f'<text x="4" y="3" font-size="{max(8, int(scale * 2.0))}" fill="#004b99" font-family="sans-serif">{txt}</text>'
                f'</g>'
            )

        # Symbols
        for s in self.symbols:
            sx_pos, sy_pos = s.x, s.y
            if s.pins:
                min_px = min(p.x for p in s.pins)
                max_px = max(p.x for p in s.pins)
                min_py = min(p.y for p in s.pins)
                max_py = max(p.y for p in s.pins)
                bw = max(10.0, (max_px - min_px) + 6.0)
                bh = max(10.0, (max_py - min_py) + 6.0)
                bx = (min_px + max_px) / 2.0 - bw / 2.0
                by = (min_py + max_py) / 2.0 - bh / 2.0
                parts.append(
                    f'<rect x="{sx(bx):.1f}" y="{sy(by):.1f}" width="{sx(bw):.1f}" height="{sy(bh):.1f}" fill="#ffffea" stroke="#840000" stroke-width="1.4" rx="2"/>'
                )
                for p in s.pins:
                    parts.append(
                        f'<circle cx="{sx(p.x):.1f}" cy="{sy(p.y):.1f}" r="2" fill="#840000"/>'
                    )
                    if p.name and p.name != "~":
                        parts.append(
                            f'<text x="{sx(p.x):.1f}" y="{sy(p.y) - 2:.1f}" font-size="{max(6, int(scale * 1.5))}" fill="#555555" font-family="monospace">{_esc(p.name)}</text>'
                        )
            else:
                bw, bh = 10.0, 10.0
                bx, by = sx_pos - 5.0, sy_pos - 5.0
                parts.append(
                    f'<rect x="{sx(bx):.1f}" y="{sy(by):.1f}" width="{sx(bw):.1f}" height="{sy(bh):.1f}" fill="#ffffea" stroke="#840000" stroke-width="1.4" rx="2"/>'
                )

            # Ref & Val text
            ref_txt = _esc(s.ref)
            val_txt = _esc(s.val)
            parts.append(
                f'<text x="{sx(sx_pos):.1f}" y="{sy(sy_pos) - 8:.1f}" font-size="{max(8, int(scale * 2.4))}" font-weight="bold" fill="#840000" text-anchor="middle" font-family="sans-serif">{ref_txt}</text>'
            )
            if val_txt:
                parts.append(
                    f'<text x="{sx(sx_pos):.1f}" y="{sy(sy_pos) + 12:.1f}" font-size="{max(7, int(scale * 2.0))}" fill="#000084" text-anchor="middle" font-family="sans-serif">{val_txt}</text>'
                )

        parts.append("</svg>")
        return "\n".join(parts)


def _esc(s: str) -> str:
    return (s or "").replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace('"', "&quot;")
