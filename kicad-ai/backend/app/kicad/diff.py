"""Compact unified diffs between a file snapshot and its current contents.

Used by the policy middleware to show Cursor-style "edit cards" in the UI:
the snapshot taken before a mutating tool call is diffed against the file the
tool just wrote. Output is a small JSON-friendly structure (hunks of typed
lines with old/new line numbers) capped in size so it can travel in a stream
event and be stored as the ToolMessage artifact.

KiCad files are compared in a canonical form (parse → re-serialise) so that a
tool re-formatting the whole document (one-line vs. pretty-printed
s-expressions, ``12.000000`` vs. ``12``) does not drown the actual change.
"""

from __future__ import annotations

import difflib
import json
from pathlib import Path
from typing import Any

from app.kicad import sexpr

MAX_DIFF_LINES = 400
MAX_FILE_BYTES = 8 * 1024 * 1024
CANON_MAX_BYTES = 4 * 1024 * 1024  # ~200 ms per MB to parse + dump; skip above this
CONTEXT_LINES = 2
SEXPR_SUFFIXES = {".kicad_pcb", ".kicad_sch", ".kicad_mod", ".kicad_sym", ".kicad_wks", ".kicad_dru"}
JSON_SUFFIXES = {".kicad_pro", ".kicad_prl", ".json"}


def _read_text(path: Path) -> str | None:
    try:
        if path.stat().st_size > MAX_FILE_BYTES:
            return None
        return path.read_text(encoding="utf-8", errors="replace")
    except OSError:
        return None


def canonical_lines(path: Path, text: str) -> list[str] | None:
    """Re-serialise *text* in the format our own writers use; None if not applicable."""
    if len(text) > CANON_MAX_BYTES:
        return None
    suffix = path.suffix.lower()
    try:
        if suffix in SEXPR_SUFFIXES:
            return sexpr.dumps(sexpr.loads(text)).splitlines()
        if suffix in JSON_SUFFIXES:
            return json.dumps(json.loads(text), indent=2, ensure_ascii=False).splitlines()
    except (sexpr.ParseError, ValueError, RecursionError):
        return None
    return None


def diff_texts(before: list[str], after: list[str], *, max_lines: int = MAX_DIFF_LINES) -> dict[str, Any]:
    """Return hunks + counts for two line lists. Never raises."""
    additions = deletions = 0
    hunks: list[dict[str, Any]] = []
    emitted = 0
    truncated = False
    current: dict[str, Any] | None = None
    old_no = new_no = 0

    for raw in difflib.unified_diff(before, after, lineterm="", n=CONTEXT_LINES):
        if raw.startswith(("---", "+++")):
            continue
        if raw.startswith("@@"):
            # @@ -old_start,old_len +new_start,new_len @@
            try:
                parts = raw.split()
                old_no = int(parts[1].split(",")[0].lstrip("-"))
                new_no = int(parts[2].split(",")[0].lstrip("+"))
            except (IndexError, ValueError):
                old_no = new_no = 0
            current = {"header": raw, "lines": []}
            hunks.append(current)
            continue
        if current is None:
            continue
        tag = raw[:1]
        text = raw[1:]
        if tag == "+":
            additions += 1
        elif tag == "-":
            deletions += 1
        if emitted >= max_lines:
            truncated = True
            continue
        line: dict[str, Any] = {"t": tag if tag in "+-" else " ", "s": text}
        if tag != "+":
            line["o"] = old_no
            old_no += 1
        if tag != "-":
            line["n"] = new_no
            new_no += 1
        current["lines"].append(line)
        emitted += 1

    hunks = [h for h in hunks if h["lines"]]
    return {
        "additions": additions,
        "deletions": deletions,
        "hunks": hunks,
        "truncated": truncated,
        "changed": additions > 0 or deletions > 0,
    }


def diff_files(before_path: Path, after_path: Path, relpath: str) -> dict[str, Any]:
    """Diff two files on disk; returns a payload with ``relpath`` and counts."""
    before_text = _read_text(before_path)
    after_text = _read_text(after_path)
    size = after_path.stat().st_size if after_path.exists() else 0
    if before_text is None or after_text is None:
        return {"relpath": relpath, "additions": 0, "deletions": 0, "hunks": [], "truncated": True, "changed": True, "normalized": False, "size": size, "note": "文件过大，未生成差异"}

    normalized = False
    before = after = None
    if before_text != after_text:
        cb = canonical_lines(after_path, before_text)  # snapshots are "<file>.<version>": the real file decides the format
        ca = canonical_lines(after_path, after_text) if cb is not None else None
        if cb is not None and ca is not None:
            before, after, normalized = cb, ca, True
    if before is None or after is None:
        before, after = before_text.splitlines(), after_text.splitlines()

    payload = diff_texts(before, after)
    payload.update({"relpath": relpath, "size": size, "normalized": normalized})
    return payload
