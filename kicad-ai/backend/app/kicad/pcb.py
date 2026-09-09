"""Read / edit / analyse ``.kicad_pcb`` files without KiCad installed.

Coordinates follow KiCad's board convention: millimetres, +X right, +Y down.
"""

from __future__ import annotations

import math
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any

from . import sexpr
from .sexpr import Str, Sym, child, children, value

COPPER_LAYERS = {"F.Cu", "B.Cu"}


@dataclass
class Pad:
    number: str
    x: float  # absolute board coords
    y: float
    size_w: float
    size_h: float
    net_code: int
    net_name: str
    layers: list[str]
    pad_type: str
    shape: str


@dataclass
class Footprint:
    node: list
    ref: str
    val: str
    lib_id: str
    layer: str
    x: float
    y: float
    rotation: float
    uuid: str
    pads: list[Pad] = field(default_factory=list)
    bbox: tuple[float, float, float, float] = (0, 0, 0, 0)  # x1,y1,x2,y2 absolute
    attrs: list[str] = field(default_factory=list)

    @property
    def side(self) -> str:
        return "bottom" if self.layer.startswith("B.") else "top"

    @property
    def width(self) -> float:
        return self.bbox[2] - self.bbox[0]

    @property
    def height(self) -> float:
        return self.bbox[3] - self.bbox[1]

    def to_dict(self, include_pads: bool = False) -> dict[str, Any]:
        d = {
            "reference": self.ref,
            "value": self.val,
            "footprint": self.lib_id,
            "layer": self.layer,
            "side": self.side,
            "x": round(self.x, 4),
            "y": round(self.y, 4),
            "rotation": self.rotation,
            "bbox": [round(v, 4) for v in self.bbox],
            "size": [round(self.width, 3), round(self.height, 3)],
            "pad_count": len(self.pads),
            "attrs": self.attrs,
            "uuid": self.uuid,
        }
        if include_pads:
            d["pads"] = [
                {
                    "number": p.number,
                    "x": round(p.x, 4),
                    "y": round(p.y, 4),
                    "size": [p.size_w, p.size_h],
                    "net": p.net_name,
                    "net_code": p.net_code,
                    "type": p.pad_type,
                    "shape": p.shape,
                    "layers": p.layers,
                }
                for p in self.pads
            ]
        return d


def _rot(px: float, py: float, deg: float) -> tuple[float, float]:
    a = math.radians(deg)
    c, s = math.cos(a), math.sin(a)
    # KiCad rotation is CCW on a Y-down canvas → apply as clockwise in math coords
    return px * c + py * s, -px * s + py * c


def _at(node: list) -> tuple[float, float, float]:
    at = child(node, "at")
    if not at:
        return 0.0, 0.0, 0.0
    x = float(at[1]) if len(at) > 1 else 0.0
    y = float(at[2]) if len(at) > 2 else 0.0
    r = float(at[3]) if len(at) > 3 else 0.0
    return x, y, r


def _property(fp: list, name: str) -> str:
    for p in children(fp, "property"):
        if len(p) > 2 and p[1] == name:
            return str(p[2])
    # KiCad 6/7 legacy: (fp_text reference "C1" ...)
    for t in children(fp, "fp_text"):
        if len(t) > 2 and t[1] == name.lower():
            return str(t[2])
    return ""


class Board:
    def __init__(self, tree: list, path: Path | None = None):
        if not tree or tree[0] != "kicad_pcb":
            raise ValueError("not a kicad_pcb document")
        self.tree = tree
        self.path = path
        self.footprints: list[Footprint] = []
        self.nets: dict[int, str] = {}
        self._parse()

    # ------------------------------------------------------------------ parse
    @classmethod
    def load(cls, path: str | Path) -> Board:
        p = Path(path)
        return cls(sexpr.load(p), p)

    def _parse(self) -> None:
        self.nets = {}
        for n in children(self.tree, "net"):
            if len(n) >= 3:
                self.nets[int(n[1])] = str(n[2])
        self.footprints = [self._parse_footprint(fp) for fp in children(self.tree, "footprint")]

    def _parse_footprint(self, fp: list) -> Footprint:
        x, y, rot = _at(fp)
        layer = str(value(fp, "layer", "F.Cu"))
        attrs = [str(a) for a in (child(fp, "attr") or [])[1:]]
        f = Footprint(
            node=fp,
            ref=_property(fp, "Reference"),
            val=_property(fp, "Value"),
            lib_id=str(fp[1]) if len(fp) > 1 and not isinstance(fp[1], list) else "",
            layer=layer,
            x=x,
            y=y,
            rotation=rot,
            uuid=str(value(fp, "uuid", value(fp, "tstamp", ""))),
            attrs=attrs,
        )
        xs: list[float] = []
        ys: list[float] = []
        for pad in children(fp, "pad"):
            px, py, pad_rot = _at(pad)
            size = child(pad, "size")
            sw = float(size[1]) if size and len(size) > 1 else 0.0
            sh = float(size[2]) if size and len(size) > 2 else sw
            # KiCad stores the pad angle as absolute (footprint + local); when it
            # is missing the pad is aligned with the footprint.
            at_node = child(pad, "at")
            effective = pad_rot if at_node is not None and len(at_node) > 3 else rot
            if round(effective) % 180 == 90:
                sw, sh = sh, sw
            ax, ay = _rot(px, py, rot)
            net = child(pad, "net")
            net_code = int(net[1]) if net and len(net) > 1 else 0
            net_name = str(net[2]) if net and len(net) > 2 else self.nets.get(net_code, "")
            layers = [str(l) for l in (child(pad, "layers") or [])[1:]]
            f.pads.append(
                Pad(
                    number=str(pad[1]) if len(pad) > 1 else "",
                    x=x + ax,
                    y=y + ay,
                    size_w=sw,
                    size_h=sh,
                    net_code=net_code,
                    net_name=net_name,
                    layers=layers,
                    pad_type=str(pad[2]) if len(pad) > 2 else "",
                    shape=str(pad[3]) if len(pad) > 3 else "",
                )
            )
            half = max(sw, sh) / 2
            xs += [x + ax - half, x + ax + half]
            ys += [y + ay - half, y + ay + half]
        # graphic outline items (courtyard / silkscreen / fab) enlarge the bbox
        for g in fp:
            if not isinstance(g, list) or not g:
                continue
            head = g[0]
            if head in ("fp_line", "fp_rect"):
                for key in ("start", "end"):
                    pt = child(g, key)
                    if pt and len(pt) > 2:
                        ax, ay = _rot(float(pt[1]), float(pt[2]), rot)
                        xs.append(x + ax)
                        ys.append(y + ay)
            elif head == "fp_circle":
                c = child(g, "center")
                e = child(g, "end")
                if c and e:
                    r = math.dist((float(c[1]), float(c[2])), (float(e[1]), float(e[2])))
                    ax, ay = _rot(float(c[1]), float(c[2]), rot)
                    xs += [x + ax - r, x + ax + r]
                    ys += [y + ay - r, y + ay + r]
            elif head == "fp_poly":
                pts = child(g, "pts")
                for xy in children(pts or [], "xy"):
                    ax, ay = _rot(float(xy[1]), float(xy[2]), rot)
                    xs.append(x + ax)
                    ys.append(y + ay)
        if xs and ys:
            f.bbox = (min(xs), min(ys), max(xs), max(ys))
        else:
            f.bbox = (x - 1, y - 1, x + 1, y + 1)
        return f

    # ------------------------------------------------------------------ query
    def get_footprint(self, ref: str) -> Footprint | None:
        ref_l = ref.strip().lower()
        for f in self.footprints:
            if f.ref.lower() == ref_l:
                return f
        return None

    def outline_bbox(self) -> tuple[float, float, float, float] | None:
        xs: list[float] = []
        ys: list[float] = []
        for g in self.tree:
            if not isinstance(g, list) or not g:
                continue
            if str(value(g, "layer", "")) != "Edge.Cuts":
                continue
            head = g[0]
            if head in ("gr_line", "gr_rect", "gr_arc"):
                for key in ("start", "end", "mid"):
                    pt = child(g, key)
                    if pt and len(pt) > 2:
                        xs.append(float(pt[1]))
                        ys.append(float(pt[2]))
            elif head == "gr_circle":
                c = child(g, "center")
                e = child(g, "end")
                if c and e:
                    r = math.dist((float(c[1]), float(c[2])), (float(e[1]), float(e[2])))
                    xs += [float(c[1]) - r, float(c[1]) + r]
                    ys += [float(c[2]) - r, float(c[2]) + r]
            elif head == "gr_poly":
                for xy in children(child(g, "pts") or [], "xy"):
                    xs.append(float(xy[1]))
                    ys.append(float(xy[2]))
        if not xs:
            return None
        return (min(xs), min(ys), max(xs), max(ys))

    def outline_items(self) -> list[dict[str, Any]]:
        items = []
        for g in self.tree:
            if not isinstance(g, list) or not g or str(value(g, "layer", "")) != "Edge.Cuts":
                continue
            d: dict[str, Any] = {"type": str(g[0]).replace("gr_", "")}
            for key in ("start", "end", "mid", "center"):
                pt = child(g, key)
                if pt and len(pt) > 2:
                    d[key] = [float(pt[1]), float(pt[2])]
            items.append(d)
        return items

    def segments(self) -> list[list]:
        return children(self.tree, "segment")

    def vias(self) -> list[list]:
        return children(self.tree, "via")

    def zones(self) -> list[list]:
        return children(self.tree, "zone")

    def layers(self) -> list[dict[str, Any]]:
        out = []
        for l in (child(self.tree, "layers") or [])[1:]:
            if isinstance(l, list) and len(l) >= 3:
                out.append({"ordinal": l[0], "name": str(l[1]), "type": str(l[2])})
        return out

    def design_rules(self) -> dict[str, Any]:
        setup = child(self.tree, "setup") or []
        rules: dict[str, Any] = {}
        for key in ("pad_to_mask_clearance", "solder_mask_min_width", "pad_to_paste_clearance"):
            v = value(setup, key)
            if v is not None:
                rules[key] = v
        # Board-level defaults are in the .kicad_pro; expose sane fallbacks here.
        rules.setdefault("min_clearance_mm", 0.2)
        rules.setdefault("min_track_width_mm", 0.2)
        rules.setdefault("min_via_diameter_mm", 0.6)
        rules.setdefault("copper_edge_clearance_mm", 0.5)
        return rules

    def info(self) -> dict[str, Any]:
        outline = self.outline_bbox()
        general = child(self.tree, "general")
        net_pads: dict[str, int] = {}
        for f in self.footprints:
            for p in f.pads:
                if p.net_name:
                    net_pads[p.net_name] = net_pads.get(p.net_name, 0) + 1
        routed_nets = {
            self.nets.get(int(value(s, "net", 0)), "") for s in self.segments()
        }
        unrouted = [n for n, c in net_pads.items() if c > 1 and n not in routed_nets]
        return {
            "file": str(self.path) if self.path else None,
            "version": value(self.tree, "version"),
            "generator": str(value(self.tree, "generator", "")),
            "generator_version": str(value(self.tree, "generator_version", "")),
            "thickness_mm": value(general, "thickness", 1.6),
            "paper": str(value(self.tree, "paper", "")),
            "copper_layers": len([l for l in self.layers() if l["type"] == "signal" or l["name"].endswith(".Cu")]),
            "layer_count": len(self.layers()),
            "footprint_count": len(self.footprints),
            "net_count": len([n for n in self.nets if n != 0]),
            "track_count": len(self.segments()),
            "via_count": len(self.vias()),
            "zone_count": len(self.zones()),
            "outline_bbox": [round(v, 3) for v in outline] if outline else None,
            "board_size_mm": [round(outline[2] - outline[0], 3), round(outline[3] - outline[1], 3)]
            if outline
            else None,
            "unrouted_nets": unrouted,
            "design_rules": self.design_rules(),
        }

    def list_nets(self) -> list[dict[str, Any]]:
        pads_by_net: dict[int, list[str]] = {}
        for f in self.footprints:
            for p in f.pads:
                pads_by_net.setdefault(p.net_code, []).append(f"{f.ref}.{p.number}")
        segs_by_net: dict[int, int] = {}
        for s in self.segments():
            code = int(value(s, "net", 0))
            segs_by_net[code] = segs_by_net.get(code, 0) + 1
        out = []
        for code, name in sorted(self.nets.items()):
            if code == 0:
                continue
            out.append(
                {
                    "code": code,
                    "name": name,
                    "pads": pads_by_net.get(code, []),
                    "pad_count": len(pads_by_net.get(code, [])),
                    "track_segments": segs_by_net.get(code, 0),
                }
            )
        return out

    def ratsnest(self) -> list[dict[str, Any]]:
        """Approximate unrouted connections: nets with >1 pad and no tracks."""
        routed = {int(value(s, "net", 0)) for s in self.segments()}
        out = []
        for net in self.list_nets():
            if net["pad_count"] > 1 and net["code"] not in routed:
                pads = net["pads"]
                out.append({"net": net["name"], "from": pads[0], "to": pads[1:]})
        return out

    def board_bbox(self) -> tuple[float, float, float, float] | None:
        if not self.footprints:
            return None
        xs1 = [f.bbox[0] for f in self.footprints]
        ys1 = [f.bbox[1] for f in self.footprints]
        xs2 = [f.bbox[2] for f in self.footprints]
        ys2 = [f.bbox[3] for f in self.footprints]
        return (min(xs1), min(ys1), max(xs2), max(ys2))

    # ------------------------------------------------------------------- edit
    def set_footprint_position(
        self, ref: str, x: float | None = None, y: float | None = None, rotation: float | None = None
    ) -> Footprint:
        f = self.get_footprint(ref)
        if f is None:
            raise KeyError(f"footprint '{ref}' not found")
        nx = f.x if x is None else float(x)
        ny = f.y if y is None else float(y)
        nr = f.rotation if rotation is None else float(rotation) % 360
        payload: list[Any] = [nx, ny]
        if nr:
            payload.append(nr)
        sexpr.set_child(f.node, "at", payload)
        # KiCad stores pad/text rotation as absolute (footprint + local). Keep
        # pads consistent when the footprint rotation changes.
        delta = nr - f.rotation
        if delta:
            for sub in f.node:
                if isinstance(sub, list) and sub and sub[0] in ("pad", "property", "fp_text"):
                    at = child(sub, "at")
                    if at and len(at) > 3:
                        at[3] = (float(at[3]) + delta) % 360
                    elif at and len(at) == 3 and delta:
                        at.append(delta % 360)
        self._parse()
        return self.get_footprint(ref)  # type: ignore[return-value]

    def flip_footprint(self, ref: str) -> Footprint:
        f = self.get_footprint(ref)
        if f is None:
            raise KeyError(f"footprint '{ref}' not found")
        new_layer = "B.Cu" if f.layer == "F.Cu" else "F.Cu"
        sexpr.set_child(f.node, "layer", [Str(new_layer)])

        def swap(name: str) -> str:
            if name.startswith("F."):
                return "B." + name[2:]
            if name.startswith("B."):
                return "F." + name[2:]
            return name

        for sub in sexpr.walk(f.node):
            if sub is f.node:
                continue
            lay = child(sub, "layer")
            if lay and len(lay) > 1:
                lay[1] = Str(swap(str(lay[1])))
            lays = child(sub, "layers")
            if lays:
                for i in range(1, len(lays)):
                    lays[i] = Str(swap(str(lays[i])))
        self._parse()
        return self.get_footprint(ref)  # type: ignore[return-value]

    def set_footprint_property(self, ref: str, name: str, val: str) -> Footprint:
        f = self.get_footprint(ref)
        if f is None:
            raise KeyError(f"footprint '{ref}' not found")
        for p in children(f.node, "property"):
            if len(p) > 2 and p[1] == name:
                p[2] = Str(val)
                break
        else:
            f.node.append([Sym("property"), Str(name), Str(val)])
        self._parse()
        return self.get_footprint(ref)  # type: ignore[return-value]

    def set_outline_rect(self, x1: float, y1: float, x2: float, y2: float) -> None:
        self.clear_outline()
        self.tree.append(
            [
                Sym("gr_rect"),
                [Sym("start"), float(x1), float(y1)],
                [Sym("end"), float(x2), float(y2)],
                [Sym("stroke"), [Sym("width"), 0.05], [Sym("type"), Sym("default")]],
                [Sym("fill"), Sym("no")],
                [Sym("layer"), Str("Edge.Cuts")],
            ]
        )

    def clear_outline(self) -> int:
        before = len(self.tree)
        self.tree[:] = [
            g
            for g in self.tree
            if not (isinstance(g, list) and g and str(value(g, "layer", "")) == "Edge.Cuts")
        ]
        return before - len(self.tree)

    def save(self, path: str | Path | None = None) -> Path:
        target = Path(path) if path else self.path
        if target is None:
            raise ValueError("no path to save to")
        sexpr.dump(self.tree, target)
        self.path = target
        return target

    # --------------------------------------------------------------- analysis
    def placement_score(self, grid_mm: float = 0.1) -> dict[str, Any]:
        """Heuristic placement quality with an explainable 0-100 breakdown."""
        overlaps = self.courtyard_overlaps()
        total_len = 0.0
        pads_by_net: dict[int, list[Pad]] = {}
        for f in self.footprints:
            for p in f.pads:
                if p.net_code:
                    pads_by_net.setdefault(p.net_code, []).append(p)
        for pads in pads_by_net.values():
            if len(pads) < 2:
                continue
            # star length from first pad — cheap proxy for MST
            for p in pads[1:]:
                total_len += math.dist((pads[0].x, pads[0].y), (p.x, p.y))
        outline = self.outline_bbox()
        outside = []
        if outline:
            for f in self.footprints:
                if f.bbox[0] < outline[0] or f.bbox[1] < outline[1] or f.bbox[2] > outline[2] or f.bbox[3] > outline[3]:
                    outside.append(f.ref)
        grid = max(0.001, float(grid_mm or 0.1))
        misaligned = [
            f.ref
            for f in self.footprints
            if abs(f.x / grid - round(f.x / grid)) > 1e-4
            or abs(f.y / grid - round(f.y / grid)) > 1e-4
        ]
        average_span = total_len / max(1, len(self.footprints))
        breakdown = {
            "no_overlap": round(max(0.0, 35.0 - 15.0 * len(overlaps)), 1),
            "inside_outline": round(max(0.0, 20.0 - 10.0 * len(outside)), 1) if outline else 0.0,
            "connection_compactness": round(max(0.0, 30.0 - min(30.0, average_span * 0.5)), 1),
            "grid_alignment": round(max(0.0, 15.0 - 2.0 * len(misaligned)), 1),
        }
        score = sum(breakdown.values())
        return {
            "score": round(max(0.0, score), 1),
            "grade": "A" if score >= 90 else "B" if score >= 75 else "C" if score >= 60 else "D",
            "breakdown": breakdown,
            "overlapping_pairs": overlaps,
            "outside_outline": outside,
            "outline_present": outline is not None,
            "estimated_ratsnest_length_mm": round(total_len, 2),
            "average_connection_span_mm": round(average_span, 2),
            "grid_mm": grid,
            "misaligned_to_grid": misaligned,
        }

    def courtyard_overlaps(self, margin: float = 0.0) -> list[list[str]]:
        out = []
        fps = self.footprints
        for i in range(len(fps)):
            a = fps[i]
            for j in range(i + 1, len(fps)):
                b = fps[j]
                if a.side != b.side:
                    continue
                if (
                    a.bbox[0] < b.bbox[2] + margin
                    and a.bbox[2] > b.bbox[0] - margin
                    and a.bbox[1] < b.bbox[3] + margin
                    and a.bbox[3] > b.bbox[1] - margin
                ):
                    out.append([a.ref, b.ref])
        return out

    def find_free_area(
        self, width: float, height: float, step: float = 1.0, margin: float = 0.5
    ) -> list[dict[str, float]]:
        outline = self.outline_bbox()
        if not outline:
            return []
        x1, y1, x2, y2 = outline
        candidates = []
        x = x1 + margin
        while x + width <= x2 - margin and len(candidates) < 5:
            y = y1 + margin
            while y + height <= y2 - margin and len(candidates) < 5:
                rect = (x, y, x + width, y + height)
                blocked = False
                for f in self.footprints:
                    if (
                        rect[0] < f.bbox[2] + margin
                        and rect[2] > f.bbox[0] - margin
                        and rect[1] < f.bbox[3] + margin
                        and rect[3] > f.bbox[1] - margin
                    ):
                        blocked = True
                        break
                if not blocked:
                    candidates.append(
                        {"x": round(x + width / 2, 3), "y": round(y + height / 2, 3), "w": width, "h": height}
                    )
                y += step
            x += step
        return candidates

    def drc_lite(self) -> dict[str, Any]:
        """Built-in DRC subset: overlaps, outline, pad clearance, unconnected."""
        rules = self.design_rules()
        clearance = float(rules.get("min_clearance_mm", 0.2))
        violations: list[dict[str, Any]] = []
        for a, b in self.courtyard_overlaps():
            fa, fb = self.get_footprint(a), self.get_footprint(b)
            violations.append(
                {
                    "type": "courtyards_overlap",
                    "severity": "error",
                    "description": f"Courtyards overlap: {a} and {b}",
                    "items": [
                        {"description": f"Footprint {a}", "pos": {"x": fa.x, "y": fa.y}},
                        {"description": f"Footprint {b}", "pos": {"x": fb.x, "y": fb.y}},
                    ],
                }
            )
        outline = self.outline_bbox()
        if outline:
            edge = float(rules.get("copper_edge_clearance_mm", 0.5))
            for f in self.footprints:
                if (
                    f.bbox[0] < outline[0] + edge
                    or f.bbox[1] < outline[1] + edge
                    or f.bbox[2] > outline[2] - edge
                    or f.bbox[3] > outline[3] - edge
                ):
                    violations.append(
                        {
                            "type": "copper_edge_clearance",
                            "severity": "error",
                            "description": f"Footprint {f.ref} too close to (or outside) board edge",
                            "items": [{"description": f"Footprint {f.ref}", "pos": {"x": f.x, "y": f.y}}],
                        }
                    )
        else:
            violations.append(
                {
                    "type": "missing_outline",
                    "severity": "warning",
                    "description": "Board has no Edge.Cuts outline",
                    "items": [],
                }
            )
        pads = [(f, p) for f in self.footprints for p in f.pads]
        for i in range(len(pads)):
            fa, pa = pads[i]
            for j in range(i + 1, len(pads)):
                fb, pb = pads[j]
                if fa is fb or pa.net_code == pb.net_code:
                    continue
                if not (set(pa.layers) & set(pb.layers) & COPPER_LAYERS) and pa.pad_type != "thru_hole" and pb.pad_type != "thru_hole":
                    continue
                dx = max(0.0, abs(pa.x - pb.x) - (pa.size_w + pb.size_w) / 2)
                dy = max(0.0, abs(pa.y - pb.y) - (pa.size_h + pb.size_h) / 2)
                gap = math.hypot(dx, dy)
                if gap < clearance:
                    violations.append(
                        {
                            "type": "clearance",
                            "severity": "error",
                            "description": f"Clearance violation ({gap:.3f} mm < {clearance} mm) between {fa.ref}.{pa.number} [{pa.net_name}] and {fb.ref}.{pb.number} [{pb.net_name}]",
                            "items": [
                                {"description": f"Pad {fa.ref}.{pa.number}", "pos": {"x": pa.x, "y": pa.y}},
                                {"description": f"Pad {fb.ref}.{pb.number}", "pos": {"x": pb.x, "y": pb.y}},
                            ],
                        }
                    )
        unconnected = [
            {"net": r["net"], "from": r["from"], "to": r["to"]} for r in self.ratsnest()
        ]
        errors = sum(1 for v in violations if v["severity"] == "error")
        warnings = sum(1 for v in violations if v["severity"] == "warning")
        return {
            "engine": "builtin-lite",
            "passed": errors == 0,
            "error_count": errors,
            "warning_count": warnings,
            "unconnected_count": len(unconnected),
            "violations": violations,
            "unconnected_items": unconnected,
        }

    # ------------------------------------------------------------------ render
    def selection_map(self, width_px: int = 640) -> dict[str, Any]:
        """Objects and pixel bounds matching :meth:`render_svg` exactly."""
        outline = self.outline_bbox() or self.board_bbox() or (0, 0, 100, 80)
        pad_mm = 3.0
        x1 = outline[0] - pad_mm
        y1 = outline[1] - pad_mm
        x2 = outline[2] + pad_mm
        y2 = outline[3] + pad_mm
        w_mm, h_mm = max(1e-3, x2 - x1), max(1e-3, y2 - y1)
        scale = width_px / w_mm
        height_px = int(h_mm * scale)

        def bbox_px(bbox: tuple[float, float, float, float]) -> list[float]:
            return [
                round((bbox[0] - x1) * scale, 2),
                round((bbox[1] - y1) * scale, 2),
                round((bbox[2] - x1) * scale, 2),
                round((bbox[3] - y1) * scale, 2),
            ]

        return {
            "mode": "pcb",
            "canvas": {"width": width_px, "height": height_px},
            "world_bounds": [round(v, 4) for v in (x1, y1, x2, y2)],
            "elements": [
                {
                    "id": f.ref,
                    "reference": f.ref,
                    "label": f"{f.ref} · {f.val}" if f.val else f.ref,
                    "value": f.val,
                    "kind": "footprint",
                    "position": [round(f.x, 4), round(f.y, 4)],
                    "bbox": bbox_px(f.bbox),
                    "world_bbox": [round(v, 4) for v in f.bbox],
                }
                for f in self.footprints
                if f.ref
            ],
        }

    def render_svg(self, width_px: int = 640) -> str:
        outline = self.outline_bbox() or self.board_bbox() or (0, 0, 100, 80)
        pad_mm = 3.0
        x1, y1, x2, y2 = outline[0] - pad_mm, outline[1] - pad_mm, outline[2] + pad_mm, outline[3] + pad_mm
        w_mm, h_mm = max(1e-3, x2 - x1), max(1e-3, y2 - y1)
        scale = width_px / w_mm
        height_px = int(h_mm * scale)

        def sx(v: float) -> float:
            return (v - x1) * scale

        def sy(v: float) -> float:
            return (v - y1) * scale

        parts = [
            f'<svg xmlns="http://www.w3.org/2000/svg" width="{width_px}" height="{height_px}" viewBox="0 0 {width_px} {height_px}">',
            f'<rect width="{width_px}" height="{height_px}" fill="#001d17"/>',
        ]
        ob = self.outline_bbox()
        if ob:
            parts.append(
                f'<rect x="{sx(ob[0]):.1f}" y="{sy(ob[1]):.1f}" width="{(ob[2]-ob[0])*scale:.1f}" height="{(ob[3]-ob[1])*scale:.1f}" fill="#0b3d2e" stroke="#e8c547" stroke-width="1.5"/>'
            )
        elif not self.footprints and not self.segments() and not self.zones():
            margin = 30
            parts.append(
                f'<rect x="{margin}" y="{margin}" width="{width_px - margin*2}" height="{height_px - margin*2}" rx="8" fill="#05251c" stroke="#10b981" stroke-width="1.5" stroke-dasharray="6,4" stroke-opacity="0.4"/>'
                f'<text x="{width_px/2:.1f}" y="{height_px/2 - 12:.1f}" fill="#10b981" font-family="sans-serif" font-size="15" font-weight="600" text-anchor="middle">PCB 尚未放置元器件</text>'
                f'<text x="{width_px/2:.1f}" y="{height_px/2 + 14:.1f}" fill="#94a3b8" font-family="sans-serif" font-size="12" text-anchor="middle">可点击上方「原理图」标签查看已生成的电路图</text>'
            )
        # zones (filled polygons, translucent)
        for z in self.zones():
            layer = str(value(z, "layer", "F.Cu"))
            color = "#b3261e" if layer == "F.Cu" else "#2f6fd6"
            for poly in children(z, "polygon"):
                pts = [
                    f"{sx(float(xy[1])):.1f},{sy(float(xy[2])):.1f}"
                    for xy in children(child(poly, "pts") or [], "xy")
                ]
                if pts:
                    parts.append(f'<polygon points="{" ".join(pts)}" fill="{color}" fill-opacity="0.25"/>')
        # tracks
        for s in self.segments():
            st, en = child(s, "start"), child(s, "end")
            if not st or not en:
                continue
            layer = str(value(s, "layer", "F.Cu"))
            color = "#d0453a" if layer == "F.Cu" else "#3f7ee8"
            wdt = float(value(s, "width", 0.25)) * scale
            parts.append(
                f'<line x1="{sx(float(st[1])):.1f}" y1="{sy(float(st[2])):.1f}" x2="{sx(float(en[1])):.1f}" y2="{sy(float(en[2])):.1f}" stroke="{color}" stroke-width="{max(1.0, wdt):.1f}" stroke-linecap="round"/>'
            )
        for v in self.vias():
            vx, vy, _ = _at(v)
            r = float(value(v, "size", 0.8)) * scale / 2
            parts.append(f'<circle cx="{sx(vx):.1f}" cy="{sy(vy):.1f}" r="{r:.1f}" fill="#9aa0a6" stroke="#333" stroke-width="0.5"/>')
        # footprints
        for f in self.footprints:
            stroke = "#f5c542" if f.side == "top" else "#7fb3ff"
            parts.append(
                f'<rect x="{sx(f.bbox[0]):.1f}" y="{sy(f.bbox[1]):.1f}" width="{max(2.0, f.width*scale):.1f}" height="{max(2.0, f.height*scale):.1f}" fill="none" stroke="{stroke}" stroke-width="1" stroke-dasharray="3 2"/>'
            )
            for p in f.pads:
                pc = "#c8a54a" if "F.Cu" in p.layers else "#5b8def"
                if p.pad_type == "thru_hole":
                    pc = "#c9c9c9"
                parts.append(
                    f'<rect x="{sx(p.x - p.size_w/2):.1f}" y="{sy(p.y - p.size_h/2):.1f}" width="{max(1.5, p.size_w*scale):.1f}" height="{max(1.5, p.size_h*scale):.1f}" fill="{pc}" rx="1"/>'
                )
            font = max(7, min(12, int(f.width * scale * 0.35)))
            parts.append(
                f'<text x="{sx(f.x):.1f}" y="{sy(f.bbox[1]) - 2:.1f}" font-size="{font}" fill="#e6f1ff" text-anchor="middle" font-family="ui-monospace, monospace">{_esc(f.ref)}</text>'
            )
        # ratsnest
        for r in self.ratsnest():
            pads = {f"{f.ref}.{p.number}": p for f in self.footprints for p in f.pads}
            a = pads.get(r["from"])
            for t in r["to"]:
                b = pads.get(t)
                if a and b:
                    parts.append(
                        f'<line x1="{sx(a.x):.1f}" y1="{sy(a.y):.1f}" x2="{sx(b.x):.1f}" y2="{sy(b.y):.1f}" stroke="#ffffff" stroke-opacity="0.35" stroke-width="0.7" stroke-dasharray="2 2"/>'
                    )
        parts.append("</svg>")
        return "\n".join(parts)


def _esc(s: str) -> str:
    return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
