"""Keep tool results within a context budget.

Large query results (footprint dumps, DRC reports, netlists, file trees) are
the fastest way to exhaust a model's context window. Results above the budget
are shrunk *structurally* — the largest list field is cut and annotated with
``shown``/``total`` — and the complete payload is spilled to a hidden folder in
the user's workspace so the model can page through it with
``read_file(offset, limit)`` or ``grep`` when it really needs the rest.
"""

from __future__ import annotations

import json
import logging
import re
import time
import uuid
from pathlib import Path
from typing import Any

log = logging.getLogger(__name__)

DEFAULT_MAX_CHARS = 24_000
SPILL_DIR = ".agent/tool_results"
SPILL_KEEP = 60  # newest spilled results kept per user
TRUNCATION_KEY = "_truncated"
HINT = "结果过大已截断。请用过滤/分页参数（如 layer、reference_prefix、limit、offset）缩小范围；确实需要全部数据时，可用 read_file(offset, limit) 或 grep 读取 full_result 指向的文件。"


def _dumps(value: Any) -> str:
    return json.dumps(value, ensure_ascii=False, default=str)


def _safe_id(value: str | None) -> str:
    cleaned = re.sub(r"[^A-Za-z0-9_-]+", "_", value or "")[:60]
    return cleaned or uuid.uuid4().hex[:12]


def spill_result(root: Path | None, tool: str, tool_call_id: str | None, content: str) -> str | None:
    """Write the full result under ``<root>/.agent/tool_results``; return its virtual path."""
    if root is None:
        return None
    try:
        folder = root / SPILL_DIR
        folder.mkdir(parents=True, exist_ok=True)
        name = f"{int(time.time())}-{_safe_id(tool)}-{_safe_id(tool_call_id)}.json"
        (folder / name).write_text(content, encoding="utf-8")
        _prune(folder)
        return f"/{SPILL_DIR}/{name}"
    except OSError as exc:
        log.info("could not spill tool result: %s", exc)
        return None


def _prune(folder: Path) -> None:
    files = sorted(folder.glob("*.json"), key=lambda p: p.stat().st_mtime, reverse=True)
    for stale in files[SPILL_KEEP:]:
        try:
            stale.unlink()
        except OSError:
            pass


def _largest_list_key(data: dict[str, Any]) -> str | None:
    best: tuple[int, str] | None = None
    for key, value in data.items():
        if key == TRUNCATION_KEY or not isinstance(value, list) or len(value) < 2:
            continue
        size = len(_dumps(value))
        if best is None or size > best[0]:
            best = (size, key)
    return best[1] if best else None


def _shrink_list(items: list[Any], budget: int) -> list[Any]:
    """Keep the longest prefix of *items* whose serialised size fits *budget*."""
    kept: list[Any] = []
    used = 2  # brackets
    for item in items:
        size = len(_dumps(item)) + 1
        if used + size > budget:
            break
        kept.append(item)
        used += size
    return kept


def _shrink_dict(data: dict[str, Any], max_chars: int, note: dict[str, Any]) -> str | None:
    serialized = _dumps(data)
    cuts: dict[str, dict[str, int]] = {}
    # Reserve room for the marker up front so one pass normally suffices.
    marker_size = len(_dumps({TRUNCATION_KEY: {**note, "fields": {"placeholder": {"shown": 0, "total": 0}}}})) + 32
    for _ in range(3):
        if len(serialized) <= max_chars:
            break
        key = _largest_list_key(data)
        if key is None:
            return None
        items = data[key]
        overhead = len(serialized) - len(_dumps(items))
        if TRUNCATION_KEY not in data:
            overhead += marker_size
        kept = _shrink_list(items, max(max_chars - overhead, 0))
        if len(kept) == len(items):
            return None
        total = cuts.get(key, {}).get("total", len(items))
        cuts[key] = {"shown": len(kept), "total": total}
        data[key] = kept
        data[TRUNCATION_KEY] = {**note, "fields": cuts}
        serialized = _dumps(data)
    if len(serialized) > max_chars:
        return None
    return serialized


def shrink_tool_result(
    content: str,
    *,
    tool: str,
    tool_call_id: str | None,
    spill_root: Path | None,
    max_chars: int = DEFAULT_MAX_CHARS,
) -> tuple[str, dict[str, Any] | None]:
    """Return ``(content_for_model, truncation_info | None)``."""
    if max_chars <= 0 or len(content) <= max_chars:
        return content, None
    full_path = spill_result(spill_root, tool, tool_call_id, content)
    note: dict[str, Any] = {"total_chars": len(content), "hint": HINT}
    if full_path:
        note["full_result"] = full_path

    parsed: Any = None
    stripped = content.lstrip()
    if stripped.startswith(("{", "[")):
        try:
            parsed = json.loads(content)
        except json.JSONDecodeError:
            parsed = None

    if isinstance(parsed, dict):
        shrunk = _shrink_dict(parsed, max_chars, note)
        if shrunk is not None:
            return shrunk, {**note, "fields": parsed[TRUNCATION_KEY]["fields"]}
    elif isinstance(parsed, list):
        kept = _shrink_list(parsed, max_chars - len(_dumps(note)) - 64)
        if kept and len(kept) < len(parsed):
            wrapped = {"items": kept, TRUNCATION_KEY: {**note, "fields": {"items": {"shown": len(kept), "total": len(parsed)}}}}
            return _dumps(wrapped), wrapped[TRUNCATION_KEY]

    tail = "\n…[结果已截断：共 {total} 字符{where}。{hint}]".format(
        total=len(content),
        where=f"，完整结果见 {full_path}" if full_path else "",
        hint=HINT,
    )
    head = content[: max(max_chars - len(tail), 0)]
    return head + tail, note
