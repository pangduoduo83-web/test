"""Per-user workspaces: safe path resolution, project discovery, file snapshots.

Every user gets ``<workspace_root>/<user_id>/`` and can only touch files inside
it. Snapshots live in ``<project>/.versions/`` so they never collide with
KiCad's own ``*-backups`` folder.
"""

from __future__ import annotations

import json
import re
import shutil
import time
import zipfile
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from app.config import get_settings

PROJECT_EXTS = {".kicad_pro": "project", ".kicad_sch": "schematic", ".kicad_pcb": "pcb"}

# Virtual prefix the agent's filesystem tools historically exposed for the
# shared workspace root (``/data/workspaces/<user_id>/…``). Still accepted so
# older conversations keep working, but only for the caller's own user id.
VIRTUAL_PREFIX = "/data/workspaces/"

_DRIVE_RE = re.compile(r"^[a-zA-Z]:[/\\]")


class WorkspaceError(Exception):
    pass


_configured_root: Path | None = None


def configure(root: Path | str) -> None:
    """Override the workspace root (used by the agent runtime and tests)."""
    global _configured_root
    _configured_root = Path(root)
    _configured_root.mkdir(parents=True, exist_ok=True)


def workspace_root() -> Path:
    return _configured_root or get_settings().workspace_root


def user_root(user_id: str) -> Path:
    root = workspace_root() / user_id
    root.mkdir(parents=True, exist_ok=True)
    return root


def _inside(candidate: Path, root: Path) -> bool:
    try:
        candidate.relative_to(root)
    except ValueError:
        return False
    return True


def resolve(user_id: str, rel_or_abs: str | Path) -> Path:
    """Resolve *rel_or_abs* inside the user's workspace; refuse escapes.

    Accepted shapes, all confined to ``<workspace_root>/<user_id>/``:

    * relative paths (``power_module/x.kicad_pcb``)
    * absolute host paths, as shown in the agent context block
    * virtual paths of the agent filesystem (``/power_module/x.kicad_pcb``)
    * the legacy shared prefix ``/data/workspaces/<user_id>/…`` (own id only)
    """
    root = user_root(user_id).resolve()
    raw = str(rel_or_abs).strip()
    if not raw:
        raise WorkspaceError("路径为空")
    denied = WorkspaceError(f"路径越界：{rel_or_abs} 不在用户工作区内")

    p = Path(raw)
    candidate = (p if p.is_absolute() else root / p).resolve()
    if _inside(candidate, root):
        return candidate
    if _DRIVE_RE.match(raw):
        raise denied  # another drive / another user's folder

    virt = raw.replace("\\", "/")
    if virt.startswith(VIRTUAL_PREFIX) or virt.rstrip("/") == VIRTUAL_PREFIX.rstrip("/"):
        rest = virt[len(VIRTUAL_PREFIX):] if virt.startswith(VIRTUAL_PREFIX) else ""
        uid, _, remainder = rest.partition("/")
        if uid and uid != user_id:
            raise denied
        candidate = (root / remainder).resolve()
    elif virt.startswith("/"):
        candidate = (root / virt.lstrip("/")).resolve()
    if _inside(candidate, root):
        return candidate
    raise denied


def to_virtual(user_id: str, rel_or_abs: str | Path) -> str:
    """Normalise any accepted path shape to a virtual path (``/a/b``) under the user root."""
    resolved = resolve(user_id, rel_or_abs)
    rel = resolved.relative_to(user_root(user_id).resolve()).as_posix()
    return "/" if rel == "." else f"/{rel}"


def relpath(user_id: str, path: Path) -> str:
    return path.resolve().relative_to(user_root(user_id).resolve()).as_posix()


def kind_of(path: Path) -> str:
    return PROJECT_EXTS.get(path.suffix.lower(), "other")


def scan_projects(user_id: str) -> list[dict[str, Any]]:
    """Find KiCad projects (directories containing .kicad_pro/.kicad_pcb/.kicad_sch)."""
    root = user_root(user_id)
    found: dict[Path, dict[str, Any]] = {}
    for p in root.rglob("*"):
        if not p.is_file() or ".versions" in p.parts or p.name.startswith("."):
            continue
        kind = kind_of(p)
        if kind == "other":
            continue
        entry = found.setdefault(
            p.parent, {"dir": relpath(user_id, p.parent), "name": p.parent.name, "pro": None, "sch": None, "pcb": None}
        )
        rel = relpath(user_id, p)
        if kind == "project":
            entry["pro"] = rel
            entry["name"] = p.stem
        elif kind == "schematic" and (entry["sch"] is None or p.stem == entry["name"]):
            entry["sch"] = rel
        elif kind == "pcb" and (entry["pcb"] is None or p.stem == entry["name"]):
            entry["pcb"] = rel
    return sorted(found.values(), key=lambda e: e["name"].lower())


def list_files(user_id: str, rel_dir: str) -> list[dict[str, Any]]:
    d = resolve(user_id, rel_dir)
    out = []
    if not d.exists():
        return out
    for p in sorted(d.rglob("*")):
        if not p.is_file() or ".versions" in p.parts or any(part.endswith("-backups") for part in p.parts):
            continue
        out.append({"path": relpath(user_id, p), "size": p.stat().st_size, "kind": kind_of(p)})
    return out


def import_zip(user_id: str, zip_path: Path, target_name: str) -> Path:
    dest = resolve(user_id, target_name)
    dest.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(zip_path) as zf:
        for member in zf.infolist():
            name = member.filename.replace("\\", "/")
            if name.startswith("/") or ".." in name.split("/"):
                continue
            target = (dest / name).resolve()
            target.relative_to(dest.resolve())
            if member.is_dir():
                target.mkdir(parents=True, exist_ok=True)
            else:
                target.parent.mkdir(parents=True, exist_ok=True)
                with zf.open(member) as src, open(target, "wb") as dst:
                    shutil.copyfileobj(src, dst)
    return dest


def copy_sample(user_id: str, sample_dir: Path, target_name: str | None = None) -> Path:
    dest = resolve(user_id, target_name or sample_dir.name)
    if dest.exists():
        base = dest
        i = 2
        while dest.exists():
            dest = base.with_name(f"{base.name}_{i}")
            i += 1
    shutil.copytree(sample_dir, dest)
    return dest


def create_blank_project(user_id: str, name: str, title: str | None = None) -> Path:
    """Create a conservative KiCad 8+ project that opens cleanly in KiCad 10."""
    clean_name = "".join(c for c in name if c.isalnum() or c in ("-", "_")).strip() or "new_project"
    dest = resolve(user_id, f"projects/{clean_name}")
    if dest.exists():
        base = dest
        i = 2
        while dest.exists():
            clean_name = f"{base.name}_{i}"
            dest = base.with_name(clean_name)
            i += 1
    dest.mkdir(parents=True, exist_ok=True)
    proj_title = title or clean_name

    pro_content = json.dumps(
        {
            "board": {
                "design_settings": {
                    "defaults": {
                        "board_outline_line_width": 0.05,
                        "copper_line_width": 0.2,
                        "silk_line_width": 0.12,
                    },
                    "rules": {
                        "min_clearance": 0.2,
                        "min_copper_edge_clearance": 0.5,
                        "min_hole_clearance": 0.25,
                        "min_track_width": 0.2,
                        "min_via_diameter": 0.6,
                        "min_via_annular_width": 0.1,
                    },
                }
            },
            "meta": {
                "filename": f"{clean_name}.kicad_pro",
                "version": 3,
            },
            "net_settings": {
                "classes": [
                    {
                        "name": "Default",
                        "clearance": 0.2,
                        "track_width": 0.25,
                        "via_diameter": 0.8,
                        "via_drill": 0.4,
                    }
                ]
            },
            "schematic": {
                "legacy_lib_dir": "",
                "legacy_lib_list": [],
            },
            "sheets": [["00000000-0000-0000-0000-000000000000", "Root"]],
        },
        indent=2,
    )

    sch_content = f"""(kicad_sch
\t(version 20231120)
\t(generator "eeschema")
\t(generator_version "8.0")
\t(uuid "00000000-0000-0000-0000-000000000000")
\t(paper "A4")
\t(title_block
\t\t(title "{proj_title}")
\t)
\t(lib_symbols
\t)
\t(sheet_instances
\t\t(path "/"
\t\t\t(page "1")
\t\t)
\t)
)
"""

    pcb_content = """(kicad_pcb
\t(version 20240108)
\t(generator "pcbnew")
\t(generator_version "8.0")
\t(general
\t\t(thickness 1.6)
\t\t(legacy_teardrops no)
\t)
\t(paper "A4")
\t(layers
\t\t(0 "F.Cu" signal)
\t\t(2 "B.Cu" signal)
\t\t(9 "F.Adhes" user "F.Adhesive")
\t\t(11 "B.Adhes" user "B.Adhesive")
\t\t(13 "F.Paste" user)
\t\t(15 "B.Paste" user)
\t\t(5 "F.SilkS" user "F.Silkscreen")
\t\t(7 "B.SilkS" user "B.Silkscreen")
\t\t(1 "F.Mask" user)
\t\t(3 "B.Mask" user)
\t\t(17 "Dwgs.User" user "User.Drawings")
\t\t(19 "Cmts.User" user "User.Comments")
\t\t(21 "Eco1.User" user "User.Eco1")
\t\t(23 "Eco2.User" user "User.Eco2")
\t\t(25 "Edge.Cuts" user)
\t\t(27 "Margin" user)
\t\t(31 "F.CrtYd" user "F.Courtyard")
\t\t(29 "B.CrtYd" user "B.Courtyard")
\t\t(35 "F.Fab" user)
\t\t(33 "B.Fab" user)
\t)
\t(setup
\t\t(pad_to_mask_clearance 0)
\t\t(allow_soldermask_bridges_in_footprints no)
\t\t(pcbplotparams
\t\t\t(layerselection 0x00000000_00000000_55555555_5755f5ff)
\t\t\t(plot_on_all_layers_selection 0x00000000_00000000_00000000_00000000)
\t\t\t(disableapertmacros no)
\t\t\t(usegerberextensions no)
\t\t\t(usegerberattributes yes)
\t\t\t(usegerberadvancedattributes yes)
\t\t\t(creategerberjobfile yes)
\t\t\t(dashed_line_dash_ratio 12.000000)
\t\t\t(dashed_line_gap_ratio 3.000000)
\t\t\t(svgprecision 4)
\t\t\t(plotframeref no)
\t\t\t(mode 1)
\t\t\t(useauxorigin no)
\t\t\t(hpglpennumber 1)
\t\t\t(hpglpenspeed 20)
\t\t)
\t)
)
"""

    (dest / f"{clean_name}.kicad_pro").write_text(pro_content, encoding="utf-8")
    (dest / f"{clean_name}.kicad_sch").write_text(sch_content, encoding="utf-8")
    (dest / f"{clean_name}.kicad_pcb").write_text(pcb_content, encoding="utf-8")
    return dest


# ---------------------------------------------------------------------------
# Snapshots / versions
# ---------------------------------------------------------------------------
def _versions_dir(file_path: Path) -> Path:
    d = file_path.parent / ".versions"
    d.mkdir(exist_ok=True)
    return d


def save_version(user_id: str, file: str, label: str = "") -> dict[str, Any]:
    path = resolve(user_id, file)
    if not path.is_file():
        raise WorkspaceError(f"文件不存在：{file}")
    vdir = _versions_dir(path)
    stamp = time.strftime("%Y%m%d-%H%M%S")
    version_id = f"{stamp}-{int(time.time()*1000) % 1000:03d}"
    target = vdir / f"{path.name}.{version_id}"
    shutil.copy2(path, target)
    meta = {
        "version_id": version_id,
        "file": relpath(user_id, path),
        "created_at": datetime.now(timezone.utc).isoformat(),
        "label": label or "auto snapshot",
        "size": target.stat().st_size,
    }
    (vdir / f"{path.name}.{version_id}.json").write_text(json.dumps(meta, ensure_ascii=False), encoding="utf-8")
    return meta


def version_path(user_id: str, file: str, version_id: str) -> Path:
    """Absolute path of the snapshot file for *version_id* (may not exist)."""
    path = resolve(user_id, file)
    return path.parent / ".versions" / f"{path.name}.{version_id}"


def list_versions(user_id: str, file: str) -> list[dict[str, Any]]:
    path = resolve(user_id, file)
    vdir = path.parent / ".versions"
    if not vdir.exists():
        return []
    out = []
    for meta in sorted(vdir.glob(f"{path.name}.*.json"), reverse=True):
        try:
            out.append(json.loads(meta.read_text(encoding="utf-8")))
        except json.JSONDecodeError:
            continue
    return out


def restore_version(user_id: str, file: str, version_id: str) -> dict[str, Any]:
    path = resolve(user_id, file)
    src = path.parent / ".versions" / f"{path.name}.{version_id}"
    if not src.is_file():
        raise WorkspaceError(f"快照不存在：{version_id}")
    save_version(user_id, file, label=f"before restore {version_id}")
    shutil.copy2(src, path)
    return {"restored": version_id, "file": relpath(user_id, path)}
