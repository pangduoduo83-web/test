"""KiCad system libraries for the upstream ``kcaa`` tools: library tables + search index.

``kcaa`` locates symbol / footprint libraries exactly like KiCad does — through the
``sym-lib-table`` / ``fp-lib-table`` files in KiCad's *configuration* directory
(``~/.config/kicad/<version>`` on Linux) — and serves ``search_symbols``,
``add_symbol_to_schematic``, ``search_footprints`` ... from a SQLite index built
from those tables. The KiCad GUI writes the tables on its first start; on a
headless server nobody does, so this module

* seeds both tables from KiCad's own templates (``<KICAD_APP_PATH>/template``),
  falling back to a scan of ``symbols/*.kicad_sym`` and ``footprints/*.pretty``;
* builds the kcaa index synchronously (run at Docker build time so the image
  ships ready — the container never waits for a first sync);
* reports what is available (``library_status``).

Deliberately standalone (stdlib + optional ``kcaa``, no ``app.*`` imports): the
Dockerfile copies this single file and runs it before the rest of the backend,
so code changes elsewhere do not invalidate the (slow) index layer.

    python -m app.kicad.libraries --ensure-tables --sync --status
"""

from __future__ import annotations

import argparse
import json
import logging
import os
import platform
import re
import shutil
import sqlite3
import sys
import time
from collections.abc import Callable, Mapping
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Any

log = logging.getLogger(__name__)

SYMBOL_TABLE = "sym-lib-table"
FOOTPRINT_TABLE = "fp-lib-table"
DEFAULT_VERSION = "10.0"
_LIB_ENTRY_RE = re.compile(r"\(\s*lib\s")


@dataclass
class KicadPaths:
    """Where KiCad (and therefore kcaa) expects things — mirrors ``kcaa.utils.config``."""

    version: str
    app_path: Path
    config_dir: Path
    symbol_dir: Path
    footprint_dir: Path
    template_dir: Path

    @property
    def major(self) -> str:
        return self.version.split(".")[0]

    @property
    def data_dir(self) -> Path:
        """kcaa's SQLite directory (``ServerConfig.get_kcaa_data_dir``)."""
        return self.config_dir / "kcaa"

    @property
    def symbol_db(self) -> Path:
        return self.data_dir / "kicad_symbols.db"

    @property
    def footprint_db(self) -> Path:
        return self.data_dir / "kicad_footprints.db"


def _expand(value: str) -> Path:
    return Path(os.path.expanduser(os.path.expandvars(value)))


def resolve_paths(env: Mapping[str, str] | None = None, system: str | None = None) -> KicadPaths:
    """Resolve KiCad paths from the environment with the same defaults kcaa uses."""
    env = os.environ if env is None else env
    system = system or platform.system()
    version = env.get("KICAD_VERSION") or DEFAULT_VERSION
    major = version.split(".")[0]

    if env.get("KICAD_APP_PATH"):
        app_path = _expand(env["KICAD_APP_PATH"])
    elif system == "Darwin":
        app_path = Path("/Applications/KiCad/KiCad.app")
    elif system == "Windows":
        app_path = Path(r"C:\Program Files\KiCad")
    else:
        app_path = Path("/usr/share/kicad")

    if env.get("KICAD_CONFIG_DIR"):
        config_dir = _expand(env["KICAD_CONFIG_DIR"])
    elif system == "Darwin":
        config_dir = Path.home() / "Library" / "Preferences" / "kicad" / version
    elif system == "Windows":
        config_dir = Path(env.get("APPDATA") or Path.home()) / "kicad" / version
    else:
        # kcaa's symbol side reads ~/.config regardless of XDG_CONFIG_HOME; follow it.
        config_dir = Path.home() / ".config" / "kicad" / version

    def shared(name: str) -> Path:
        if system == "Darwin":
            return app_path / "Contents" / "SharedSupport" / name
        if system == "Windows":
            return app_path / version / "share" / "kicad" / name
        return app_path / name

    def lib_dir(kind: str, default: Path) -> Path:
        # ${KICAD10_SYMBOL_DIR} in the tables expands to KICAD<major>_SYMBOL_DIR when set,
        # otherwise to kcaa's resolved dir (KICAD_SYMBOL_DIR or the platform default).
        for key in (f"KICAD{major}_{kind}_DIR", f"KICAD_{kind}_DIR"):
            if env.get(key):
                return _expand(env[key])
        return default

    return KicadPaths(
        version=version,
        app_path=app_path,
        config_dir=config_dir,
        symbol_dir=lib_dir("SYMBOL", shared("symbols")),
        footprint_dir=lib_dir("FOOTPRINT", shared("footprints")),
        template_dir=_expand(env["KICAD_TEMPLATE_DIR"]) if env.get("KICAD_TEMPLATE_DIR") else shared("template"),
    )


# ---------------------------------------------------------------------------
# Library tables
# ---------------------------------------------------------------------------
def _quote(value: str) -> str:
    return '"' + value.replace("\\", "\\\\").replace('"', '\\"') + '"'


def _count_entries(table: Path) -> int:
    try:
        return len(_LIB_ENTRY_RE.findall(table.read_text(encoding="utf-8", errors="replace")))
    except OSError:
        return 0


def generate_symbol_table(symbol_dir: Path, major: str) -> tuple[str, int]:
    """Build a ``sym-lib-table`` listing every library found in *symbol_dir*."""
    names: list[str] = []
    if symbol_dir.is_dir():
        for p in sorted(symbol_dir.iterdir(), key=lambda x: x.name.lower()):
            # KiCad <=9: one .kicad_sym per library; KiCad 10 may ship .kicad_symdir directories.
            if (p.is_file() and p.suffix == ".kicad_sym") or (p.is_dir() and p.suffix == ".kicad_symdir"):
                names.append(p.name)
    lines = ["(sym_lib_table", "  (version 7)"]
    for name in names:
        stem = name.rsplit(".", 1)[0]
        lines.append(
            f"  (lib (name {_quote(stem)})(type \"KiCad\")(uri {_quote('${KICAD' + major + '_SYMBOL_DIR}/' + name)})"
            f"(options \"\")(descr \"\"))"
        )
    lines.append(")")
    return "\n".join(lines) + "\n", len(names)


def generate_footprint_table(footprint_dir: Path, major: str) -> tuple[str, int]:
    """Build an ``fp-lib-table`` listing every ``.pretty`` directory in *footprint_dir*."""
    names: list[str] = []
    if footprint_dir.is_dir():
        for p in sorted(footprint_dir.iterdir(), key=lambda x: x.name.lower()):
            if p.is_dir() and p.suffix == ".pretty":
                names.append(p.name)
    lines = ["(fp_lib_table", "  (version 7)"]
    for name in names:
        stem = name[: -len(".pretty")]
        lines.append(
            f"  (lib (name {_quote(stem)})(type \"KiCad\")(uri {_quote('${KICAD' + major + '_FOOTPRINT_DIR}/' + name)})"
            f"(options \"\")(descr \"\"))"
        )
    lines.append(")")
    return "\n".join(lines) + "\n", len(names)


def ensure_library_tables(paths: KicadPaths | None = None, overwrite: bool = False) -> dict[str, Any]:
    """Create ``sym-lib-table`` / ``fp-lib-table`` in KiCad's config dir if they are missing.

    Existing tables are never touched unless *overwrite* is set — users may have
    added their own libraries. Returns a JSON-friendly report per table with
    ``status`` in ``existing | copied_template | generated | skipped``.
    """
    paths = paths or resolve_paths()
    report: dict[str, Any] = {"config_dir": str(paths.config_dir), "tables": {}}
    specs = (
        (SYMBOL_TABLE, paths.symbol_dir, generate_symbol_table),
        (FOOTPRINT_TABLE, paths.footprint_dir, generate_footprint_table),
    )
    for table_name, lib_dir, generate in specs:
        target = paths.config_dir / table_name
        entry: dict[str, Any] = {"path": str(target)}
        if target.exists() and not overwrite:
            entry.update(status="existing", entries=_count_entries(target))
        else:
            template = paths.template_dir / table_name
            if template.is_file():
                paths.config_dir.mkdir(parents=True, exist_ok=True)
                shutil.copyfile(template, target)
                entry.update(status="copied_template", source=str(template), entries=_count_entries(target))
            else:
                text, count = generate(lib_dir, paths.major)
                if count == 0:
                    entry.update(status="skipped", reason=f"no libraries found under {lib_dir}")
                else:
                    paths.config_dir.mkdir(parents=True, exist_ok=True)
                    target.write_text(text, encoding="utf-8")
                    entry.update(status="generated", source=str(lib_dir), entries=count)
        report["tables"][table_name] = entry
        log.info("KiCad %s: %s (%s entries)", table_name, entry["status"], entry.get("entries", 0))
    return report


# ---------------------------------------------------------------------------
# Search index (kcaa SQLite databases)
# ---------------------------------------------------------------------------
def _prepare_kcaa_env(paths: KicadPaths) -> None:
    # kcaa's ServerConfig reads these at import time; they must exist before the import.
    os.environ.setdefault("KICAD_VERSION", paths.version)
    os.environ.setdefault("KICAD_APP_PATH", str(paths.app_path))


def sync_index(
    paths: KicadPaths | None = None,
    force: bool = False,
    progress: Callable[[str, int, int, str], None] | None = None,
) -> dict[str, Any]:
    """Build / refresh kcaa's symbol and footprint indexes from the library tables.

    Blocking (minutes for the full official libraries the first time; incremental
    afterwards). Database paths are passed explicitly so this never writes
    anywhere but ``<config_dir>/kcaa``.
    """
    paths = paths or resolve_paths()
    _prepare_kcaa_env(paths)
    from kcaa.utils.config import ServerConfig
    from kcaa.utils.footprint_index_manager import FootprintIndexManager
    from kcaa.utils.pcb_library_utils import find_fp_lib_tables
    from kcaa.utils.symbol_index_manager import SymbolIndexManager
    from kcaa.utils.symbol_index_reader import SymbolIndexReader

    paths.data_dir.mkdir(parents=True, exist_ok=True)
    report: dict[str, Any] = {"data_dir": str(paths.data_dir), "warnings": []}

    def cb(kind: str):
        if progress is None:
            return None
        return lambda cur, total, name: progress(kind, cur, total, name)

    def summary(stats: Any, total_attr: str) -> dict[str, Any]:
        return {
            "added": stats.added,
            "updated": stats.updated,
            "removed": stats.removed,
            "skipped": stats.skipped,
            "failed": stats.failed,
            "total": getattr(stats, total_attr),
            "elapsed_seconds": round(stats.elapsed_seconds, 1),
        }

    t0 = time.time()
    sym_mgr = SymbolIndexManager(SymbolIndexReader(ServerConfig()), db_path=paths.symbol_db)
    try:
        report["symbols"] = summary(sym_mgr.sync(force=force, progress_callback=cb("symbols")), "total_symbols")
    finally:
        sym_mgr.close()

    fp_table = paths.config_dir / FOOTPRINT_TABLE
    visible = [Path(p).resolve() for p in find_fp_lib_tables()]
    if fp_table.exists() and fp_table.resolve() not in visible:
        report["warnings"].append(
            f"{fp_table} is not where kcaa looks for footprint tables "
            "(~/.config/kicad/<version> or $XDG_CONFIG_HOME); footprint search will be empty."
        )
    fp_mgr = FootprintIndexManager(db_path=paths.footprint_db)
    try:
        report["footprints"] = summary(fp_mgr.sync(force=force, progress_callback=cb("footprints")), "total_footprints")
    finally:
        close = getattr(fp_mgr, "close", None)
        if close:
            close()
    report["index"] = {
        "symbols": _db_counts(paths.symbol_db, "libraries", "symbols"),
        "footprints": _db_counts(paths.footprint_db, "fp_libraries", "footprints"),
    }
    report["elapsed_seconds"] = round(time.time() - t0, 1)
    return report


def _db_counts(db: Path, lib_table: str, item_table: str) -> dict[str, Any]:
    if not db.is_file():
        return {"path": str(db), "present": False}
    try:
        con = sqlite3.connect(f"file:{db.as_posix()}?mode=ro", uri=True)
        try:
            libs = con.execute(f"SELECT COUNT(*) FROM {lib_table}").fetchone()[0]
            items = con.execute(f"SELECT COUNT(*) FROM {item_table}").fetchone()[0]
        finally:
            con.close()
    except sqlite3.Error as exc:
        return {"path": str(db), "present": True, "error": str(exc)}
    return {"path": str(db), "present": True, "libraries": libs, "items": items, "size_mb": round(db.stat().st_size / 1e6, 1)}


def library_status(paths: KicadPaths | None = None) -> dict[str, Any]:
    """Cheap, dependency-free snapshot: tables, library dirs and index databases."""
    paths = paths or resolve_paths()
    sym_table = paths.config_dir / SYMBOL_TABLE
    fp_table = paths.config_dir / FOOTPRINT_TABLE
    symbol_files = len(list(paths.symbol_dir.glob("*.kicad_sym"))) if paths.symbol_dir.is_dir() else 0
    footprint_dirs = len([p for p in paths.footprint_dir.glob("*.pretty") if p.is_dir()]) if paths.footprint_dir.is_dir() else 0
    sym_index = _db_counts(paths.symbol_db, "libraries", "symbols")
    fp_index = _db_counts(paths.footprint_db, "fp_libraries", "footprints")
    return {
        "paths": {k: str(v) for k, v in asdict(paths).items()},
        "tables": {
            SYMBOL_TABLE: {"present": sym_table.is_file(), "entries": _count_entries(sym_table)},
            FOOTPRINT_TABLE: {"present": fp_table.is_file(), "entries": _count_entries(fp_table)},
        },
        "libraries": {"symbol_files": symbol_files, "footprint_dirs": footprint_dirs},
        "index": {"symbols": sym_index, "footprints": fp_index},
        "ready": bool(
            sym_table.is_file() and fp_table.is_file() and sym_index.get("items") and fp_index.get("items")
        ),
    }


# ---------------------------------------------------------------------------
# CLI (used by the Dockerfile and for diagnostics inside the container)
# ---------------------------------------------------------------------------
def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="KiCad library tables + kcaa search index")
    parser.add_argument("--ensure-tables", action="store_true", help="create sym-lib-table / fp-lib-table if missing")
    parser.add_argument("--overwrite", action="store_true", help="with --ensure-tables: replace existing tables")
    parser.add_argument("--sync", action="store_true", help="build / refresh the kcaa symbol + footprint index")
    parser.add_argument("--force", action="store_true", help="with --sync: reparse every library")
    parser.add_argument("--status", action="store_true", help="print tables / index status")
    parser.add_argument("--require-ready", action="store_true", help="exit 1 unless tables and both indexes exist")
    args = parser.parse_args(argv)
    if not (args.ensure_tables or args.sync or args.status or args.require_ready):
        args.status = True

    logging.basicConfig(level=logging.INFO, format="%(levelname)s %(name)s: %(message)s", stream=sys.stderr)
    for noisy in ("kcaa.utils.symbol_index_reader", "kcaa.utils.symbol_index_manager", "kcaa.utils.footprint_index_manager"):
        logging.getLogger(noisy).setLevel(logging.WARNING)

    paths = resolve_paths()
    out: dict[str, Any] = {}
    if args.ensure_tables:
        out["tables"] = ensure_library_tables(paths, overwrite=args.overwrite)
    if args.sync:
        last = {"t": 0.0}

        def progress(kind: str, cur: int, total: int, name: str) -> None:
            now = time.time()
            if now - last["t"] >= 5 or cur + 1 == total:
                last["t"] = now
                print(f"[{kind}] {cur + 1}/{total} {name}", file=sys.stderr, flush=True)

        out["sync"] = sync_index(paths, force=args.force, progress=progress)
    status = library_status(paths)
    if args.status or args.require_ready:
        out["status"] = status
    print(json.dumps(out, ensure_ascii=False, indent=2))
    if args.require_ready and not status["ready"]:
        print("KiCad libraries are not ready (missing tables or empty index)", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
