"""Call the upstream KiCad-AI-Assistant (``kcaa``) tools **in-process**, no MCP.

The upstream project registers its tools on a FastMCP server, but every tool is
just a Python coroutine/function (``FunctionTool.fn``). We register them on a
throw-away FastMCP instance purely to collect the functions and their JSON
schemas, then wrap each one as a LangChain ``StructuredTool``:

* the ``ctx: Context`` parameter that FastMCP would inject is replaced by
  :class:`DirectContext` (logging + progress events + lifespan cache);
* results are serialised to JSON like our native tools;
* exceptions become ``{"success": false, "error": ...}`` so the model can react.

No MCP transport, no second process, no network hop.
"""

from __future__ import annotations

import asyncio
import inspect
import json
import logging
import os
from pathlib import Path
from types import SimpleNamespace
from typing import Any

from langchain_core.tools import BaseTool, StructuredTool

from app.config import Settings

log = logging.getLogger(__name__)

_CTX_TYPE_NAMES = ("Context",)

# name → (fn, ctx_param, is_async, lifespan_context); filled by load_kcaa_tools so
# native tools (e.g. the circuit wizard) can drive upstream tools in-process.
_RAW_TOOLS: dict[str, tuple[Any, str | None, bool, Any]] = {}


def kcaa_tool_available(name: str) -> bool:
    return name in _RAW_TOOLS


async def call_kcaa(name: str, **kwargs: Any) -> dict[str, Any]:
    """Invoke an upstream kcaa tool function directly and return its dict result."""
    entry = _RAW_TOOLS.get(name)
    if entry is None:
        raise RuntimeError(f"上游 kcaa 工具 {name} 不可用（未安装 kcaa 或 KCAA_MODE=off）")
    fn, ctx_param, is_async, lifespan_context = entry
    if ctx_param:
        kwargs[ctx_param] = DirectContext(lifespan_context, name)
    result = await fn(**kwargs) if is_async else await asyncio.to_thread(fn, **kwargs)
    if isinstance(result, str):
        try:
            result = json.loads(result)
        except json.JSONDecodeError:
            result = {"success": False, "error": result}
    if not isinstance(result, dict):
        return {"success": True, "result": result}
    if "error" in result and result.get("success") is not True:
        result.setdefault("success", False)
    return result


def kcaa_installed() -> bool:
    try:
        import importlib.util

        return importlib.util.find_spec("kcaa") is not None and importlib.util.find_spec("fastmcp") is not None
    except Exception:  # noqa: BLE001
        return False


class DirectContext:
    """Minimal stand-in for ``fastmcp.Context`` (only what kcaa tools use)."""

    def __init__(self, lifespan_context: Any, tool_name: str):
        self.request_context = SimpleNamespace(lifespan_context=lifespan_context)
        self.tool_name = tool_name

    async def debug(self, message: str, *args: Any, **kwargs: Any) -> None:
        log.debug("kcaa[%s]: %s", self.tool_name, message)

    async def info(self, message: str, *args: Any, **kwargs: Any) -> None:
        log.info("kcaa[%s]: %s", self.tool_name, message)

    async def warning(self, message: str, *args: Any, **kwargs: Any) -> None:
        log.warning("kcaa[%s]: %s", self.tool_name, message)

    async def error(self, message: str, *args: Any, **kwargs: Any) -> None:
        log.error("kcaa[%s]: %s", self.tool_name, message)

    async def report_progress(self, progress: float, total: float | None = None, message: str | None = None) -> None:
        try:
            from langgraph.config import get_stream_writer

            get_stream_writer()({"type": "progress", "tool": self.tool_name, "progress": progress, "total": total, "message": message})
        except Exception:  # noqa: BLE001 — outside a streaming run
            pass


def _has_ctx_param(fn) -> str | None:
    for name, param in inspect.signature(fn).parameters.items():
        ann = param.annotation
        ann_name = getattr(ann, "__name__", str(ann))
        if name == "ctx" or any(t in ann_name for t in _CTX_TYPE_NAMES):
            return name
    return None


def _dumps(value: Any) -> str:
    if isinstance(value, str):
        return value
    try:
        return json.dumps(value, ensure_ascii=False, default=str)
    except (TypeError, ValueError):
        return str(value)


def _wrap(tool, lifespan_context: Any) -> BaseTool:
    fn = tool.fn
    ctx_param = _has_ctx_param(fn)
    is_async = inspect.iscoroutinefunction(fn)
    name = tool.name
    schema = dict(tool.parameters or {"type": "object", "properties": {}})
    schema.setdefault("type", "object")
    schema.setdefault("properties", {})
    schema.pop("additionalProperties", None)
    if ctx_param and ctx_param in schema["properties"]:
        schema["properties"].pop(ctx_param, None)
        schema["required"] = [r for r in schema.get("required", []) if r != ctx_param]

    async def call(**kwargs: Any) -> str:
        if ctx_param:
            kwargs[ctx_param] = DirectContext(lifespan_context, name)
        try:
            result = await fn(**kwargs) if is_async else await asyncio.to_thread(fn, **kwargs)
        except Exception as exc:  # noqa: BLE001 — surfaced to the model
            log.warning("kcaa tool %s failed: %s", name, exc)
            return _dumps({"success": False, "error": f"{type(exc).__name__}: {exc}", "tool": name})
        return _dumps(result)

    description = (tool.description or fn.__doc__ or name).strip()
    return StructuredTool(
        name=name,
        description=description,
        args_schema=schema,
        coroutine=call,
        metadata={"source": "kcaa"},
    )


def _ensure_kicad_libraries() -> None:
    """Seed sym-lib-table / fp-lib-table when KiCad is installed but its GUI never ran (headless server)."""
    try:
        from app.kicad.libraries import ensure_library_tables, library_status

        ensure_library_tables()
        status = library_status()
    except Exception as exc:  # noqa: BLE001 — libraries are optional
        log.warning("KiCad library setup skipped: %s", exc)
        return
    tables = status["tables"]
    index = status["index"]
    log.info(
        "KiCad libraries: sym-lib-table=%s fp-lib-table=%s index symbols=%s footprints=%s ready=%s",
        tables["sym-lib-table"]["entries"] if tables["sym-lib-table"]["present"] else "missing",
        tables["fp-lib-table"]["entries"] if tables["fp-lib-table"]["present"] else "missing",
        index["symbols"].get("items", 0),
        index["footprints"].get("items", 0),
        status["ready"],
    )


async def load_kcaa_tools(settings: Settings) -> list[BaseTool]:
    """Import the upstream package and return its tools as LangChain tools."""
    if not kcaa_installed():
        log.info("kcaa not installed; upstream tools unavailable (pip install kcaa)")
        return []
    # kcaa reads its configuration from the environment at import time.
    os.environ.setdefault("KICAD_VERSION", settings.kicad_version)
    os.environ.setdefault("KICAD_SEARCH_PATHS", str(settings.workspace_root))
    if settings.kicad_app_path:
        os.environ.setdefault("KICAD_APP_PATH", settings.kicad_app_path)
    skills_dir = Path(__file__).resolve().parent.parent / "skills"
    os.environ.setdefault("KCAA_SKILLS_DIR", str(skills_dir))
    _ensure_kicad_libraries()
    try:
        import kcaa.server as kcaa_server
        from fastmcp import FastMCP
        from kcaa.context import KiCadAppContext
    except Exception as exc:  # noqa: BLE001
        log.warning("Could not import kcaa: %s", exc)
        return []

    registry = FastMCP("kcaa-direct")
    try:
        if settings.kcaa_profile == "plugin":
            kcaa_server._register_plugin_profile(registry)
        else:
            kcaa_server._register_full_profile(registry)
        raw_tools = await registry.list_tools()
    except Exception as exc:  # noqa: BLE001
        log.warning("Registering kcaa tools failed: %s", exc)
        return []

    lifespan_context = KiCadAppContext(kicad_modules_available=False, cache={})
    tools: list[BaseTool] = []
    _RAW_TOOLS.clear()
    for t in raw_tools:
        if getattr(t, "fn", None) is None:
            continue
        try:
            tools.append(_wrap(t, lifespan_context))
            _RAW_TOOLS[t.name] = (t.fn, _has_ctx_param(t.fn), inspect.iscoroutinefunction(t.fn), lifespan_context)
        except Exception as exc:  # noqa: BLE001
            log.warning("Skipping kcaa tool %s: %s", getattr(t, "name", "?"), exc)
    log.info("Loaded %d upstream kcaa tools in-process (profile=%s)", len(tools), settings.kcaa_profile)
    return tools
