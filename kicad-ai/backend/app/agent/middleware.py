"""Framework policy middleware (the deepagents equivalent of upstream's
``LLMClient._execute_tool_with_policy``).

For every tool call it:

1. Fills in the active project's file path when the model omitted it.
2. Validates that every path argument stays inside the caller's workspace
   (defence in depth for MCP tools, which otherwise see the whole host FS).
3. Blocks runaway loops: the same tool re-issued with identical arguments
   while its results stop changing gets an error instead of executing.
4. Serialises mutations of the same file and creates a version snapshot
   before each one.
5. Emits ``file_changed`` + ``file_diff`` custom stream events after successful
   mutations (the UI refreshes the preview and renders an edit card); the
   diff is also attached as the ToolMessage artifact so history reloads can
   show it again without polluting the model's context.
"""

from __future__ import annotations

import asyncio
import json
import logging
import re
import threading
from collections.abc import Awaitable, Callable
from pathlib import Path
from typing import Any

from langchain.agents.middleware import AgentMiddleware
from langchain.agents.middleware.types import ToolCallRequest
from langchain_core.messages import AIMessage, HumanMessage, ToolMessage
from langgraph.config import get_config, get_stream_writer
from langgraph.types import Command

from app.agent.context import AgentContext
from app.agent.results import DEFAULT_MAX_CHARS, shrink_tool_result
from app.agent.tools.registry import ADMIN_ONLY_TOOLS, PATH_ARG_NAMES, get_policy
from app.kicad import workspace as ws
from app.kicad.diff import diff_files

log = logging.getLogger(__name__)

# A tool call repeated this many times with identical args is blocked outright.
HARD_REPEAT_LIMIT = 5
# ...or earlier, once the last two identical calls also produced identical results.
SOFT_REPEAT_LIMIT = 2
# Near-duplicates (same tool, numbers nudged by tiny amounts) are the way a
# model sidesteps the exact-args guard; block after this many in one turn.
NEAR_REPEAT_LIMIT = 8
NEAR_REPEAT_GRID = 0.5  # numeric args are bucketed to this step for the coarse signature
# Absolute ceiling for calls of one tool within a single human turn.
TOOL_CALLS_PER_TURN_LIMIT = 60

Snapshot = tuple[str, Path | None]  # (resolved file path, snapshot file or None)
ChangedFile = tuple[str, str, Path | None]  # (absolute path, workspace-relative path, snapshot)
GENERIC_MUTATION_TOOLS = {"write_file", "edit_file", "delete", "execute"}
KICAD_DESIGN_SUFFIXES = (
    ".kicad_pcb",
    ".kicad_sch",
    ".kicad_pro",
    ".kicad_dru",
    ".kicad_mod",
    ".kicad_sym",
)

# Mutating tools do load → edit → save on the whole file; the tools node runs
# parallel tool calls concurrently, so two edits of the same file must be
# serialised or one of them is lost (and their diffs would blend together).
_async_locks: dict[str, asyncio.Lock] = {}
_sync_locks: dict[str, threading.Lock] = {}
_locks_guard = threading.Lock()


def _async_lock(path: str) -> asyncio.Lock:
    key = path.lower()
    with _locks_guard:
        lock = _async_locks.get(key)
        if lock is None:
            lock = _async_locks[key] = asyncio.Lock()
        return lock


def _sync_lock(path: str) -> threading.Lock:
    key = path.lower()
    with _locks_guard:
        lock = _sync_locks.get(key)
        if lock is None:
            lock = _sync_locks[key] = threading.Lock()
        return lock


def _ctx_from_request(request: ToolCallRequest) -> AgentContext | None:
    runtime = getattr(request, "runtime", None)
    ctx = getattr(runtime, "context", None)
    return ctx if isinstance(ctx, AgentContext) else None


def _restrict_tools(request: Any) -> Any:
    """Hide admin-only tools from non-admin runs so the model never proposes them."""
    ctx = getattr(getattr(request, "runtime", None), "context", None)
    tools = getattr(request, "tools", None)
    if not isinstance(ctx, AgentContext) or ctx.is_admin or not tools:
        return request
    kept = [t for t in tools if getattr(t, "name", None) not in ADMIN_ONLY_TOOLS]
    if len(kept) == len(tools):
        return request
    return request.override(tools=kept)


def _prepare_model_request(request: Any) -> Any:
    """Refresh time and append the server-owned per-run project context."""
    request = _restrict_tools(request)
    system_message = getattr(request, "system_message", None)
    content = getattr(system_message, "content", None)
    if not system_message or not isinstance(content, str):
        return request
    updated = content
    if "当前系统日期与时间：" in updated:
        from app.agent.prompts import current_time_str

        updated = re.sub(
            r"当前系统日期与时间：[^\n]+",
            f"当前系统日期与时间：{current_time_str()}",
            updated,
        )
    marker = "\n\n# 当前上下文 (由系统注入，每次请求可能不同)"
    if marker in updated:
        updated = updated.split(marker, 1)[0]
    runtime = getattr(request, "runtime", None)
    ctx = getattr(runtime, "context", None)
    if isinstance(ctx, AgentContext):
        if "<!-- section:" in updated:
            from app.agent.prompts import trim_system_prompt

            updated = trim_system_prompt(updated, has_pcb=bool(ctx.pcb_path), has_schematic=bool(ctx.schematic_path))
        updated = f"{updated.rstrip()}\n\n{ctx.context_block()}"
    else:
        if "<!-- section:" in updated:
            from app.agent.prompts import trim_system_prompt

            updated = trim_system_prompt(updated, has_pcb=False, has_schematic=False)  # keep both, drop markers
        try:
            configurable = get_config().get("configurable", {})
        except RuntimeError:
            configurable = {}
        block = configurable.get("agent_context_block") if isinstance(configurable, dict) else None
        if isinstance(block, str) and block:
            updated = f"{updated.rstrip()}\n\n{block}"
    if updated == content:
        return request
    return request.override(system_message=system_message.__class__(content=updated))


def _default_for(policy_path_arg: str | None, ctx: AgentContext) -> str | None:
    if policy_path_arg == "pcb_path":
        return ctx.pcb_path
    if policy_path_arg == "schematic_path":
        return ctx.schematic_path
    if policy_path_arg == "project_path":
        return ctx.pro_path or ctx.pcb_path
    return None


def _apply_design_constraints(
    tool: str, args: dict[str, Any], ctx: AgentContext
) -> tuple[dict[str, Any], str | None]:
    """Fill safe defaults and reject parameters below project hard limits."""
    constraints = ctx.design_constraints or {}
    if not constraints:
        return args, None
    adjusted = dict(args)

    def minimum(value: Any, key: str, label: str) -> str | None:
        if value is None:
            return None
        try:
            actual = float(value)
            required = float(constraints[key])
        except (KeyError, TypeError, ValueError):
            return None
        if actual + 1e-9 < required:
            return f"{label} {actual:g} mm 低于项目约束 {required:g} mm"
        return None

    if tool == "pcb_route_pad_to_pad":
        net = str(adjusted.get("net") or "").upper()
        power_net = any(token in net for token in ("GND", "VCC", "VDD", "VIN", "VOUT", "+3V", "+5V", "+12V"))
        preferred_key = "power_track_width_mm" if power_net else "signal_track_width_mm"
        preferred = max(
            float(constraints.get(preferred_key, 0) or 0),
            float(constraints.get("min_track_width_mm", 0) or 0),
        )
        if adjusted.get("width") is None and preferred:
            adjusted["width"] = preferred
        error = minimum(adjusted.get("width"), "min_track_width_mm", "走线宽度")
        if error:
            return adjusted, error

    elif tool == "pcb_add_vias" and isinstance(adjusted.get("vias"), list):
        vias = []
        for index, raw in enumerate(adjusted["vias"]):
            if not isinstance(raw, dict):
                vias.append(raw)
                continue
            via = dict(raw)
            via.setdefault("diameter", constraints.get("via_diameter_mm"))
            via.setdefault("drill", constraints.get("via_drill_mm"))
            error = minimum(via.get("diameter"), "via_diameter_mm", f"第 {index + 1} 个过孔外径")
            error = error or minimum(via.get("drill"), "via_drill_mm", f"第 {index + 1} 个过孔钻孔")
            if error:
                return adjusted, error
            vias.append(via)
        adjusted["vias"] = vias

    elif tool == "add_zone":
        adjusted.setdefault("clearance", constraints.get("min_clearance_mm"))
        error = minimum(adjusted.get("clearance"), "min_clearance_mm", "覆铜间距")
        if error:
            return adjusted, error

    elif tool == "set_design_rules" and isinstance(adjusted.get("rules"), dict):
        checks = {
            "min_clearance": ("min_clearance_mm", "最小间距"),
            "min_track_width": ("min_track_width_mm", "最小线宽"),
            "min_via_diameter": ("via_diameter_mm", "最小过孔外径"),
            "min_via_drill": ("via_drill_mm", "最小过孔钻孔"),
            "min_copper_edge_clearance": ("copper_edge_clearance_mm", "铜到板边间距"),
        }
        for field, (constraint, label) in checks.items():
            error = minimum(adjusted["rules"].get(field), constraint, label)
            if error:
                return adjusted, error

    elif tool == "set_net_class_rules" and isinstance(adjusted.get("updates"), dict):
        checks = {
            "clearance": ("min_clearance_mm", "网络间距"),
            "track_width": ("min_track_width_mm", "网络线宽"),
            "via_diameter": ("via_diameter_mm", "网络过孔外径"),
            "via_drill": ("via_drill_mm", "网络过孔钻孔"),
        }
        for field, (constraint, label) in checks.items():
            error = minimum(adjusted["updates"].get(field), constraint, label)
            if error:
                return adjusted, error

    return adjusted, None


HARNESS_PATH_ARGS = ("path", "file_path", "paths")
# Virtual routes that are not part of the user's workspace (memory store, read-only skills).
HARNESS_ROUTE_PREFIXES = ("/memories/", "/skills/")


def _harness_path_error(args: dict[str, Any], ctx: AgentContext) -> str | None:
    """Reject filesystem-tool paths that point outside the caller's workspace."""
    for key in HARNESS_PATH_ARGS:
        value = args.get(key)
        if not value:
            continue
        for raw in value if isinstance(value, list) else [value]:
            if not isinstance(raw, str) or not raw.strip():
                continue
            candidate = raw.strip().replace("\\", "/")
            if candidate.startswith(HARNESS_ROUTE_PREFIXES) or candidate.rstrip("/") in {p.rstrip("/") for p in HARNESS_ROUTE_PREFIXES}:
                continue
            try:
                ws.to_virtual(ctx.user_id, raw)
            except ws.WorkspaceError as exc:
                return str(exc)
    return None


def apply_call_policy(
    name: str, args: dict[str, Any], ctx: AgentContext
) -> tuple[dict[str, Any], dict[str, Any] | None, list[str]]:
    """Workspace + constraint policy for one EDA tool call.

    Returns ``(args, error_payload, snapshot_paths)``: the active project's file
    is filled in when omitted, every path-like argument is confined to the
    caller's workspace, project design constraints are applied, and the file
    to snapshot before a mutation is identified. Shared by the middleware (for
    model-issued calls) and the plan executor (for approved plans).
    """
    policy = get_policy(name)
    args = dict(args or {})

    # 1. default path from the active project
    if policy.path_arg and policy.path_arg in PATH_ARG_NAMES and not args.get(policy.path_arg):
        default = _default_for(policy.path_arg, ctx)
        if default:
            args[policy.path_arg] = default

    # 2. validate every path-like argument (known names + anything that looks like one)
    resolved: dict[str, str] = {}
    path_keys = [k for k in args if k in PATH_ARG_NAMES or any(h in k.lower() for h in ("path", "_file", "_dir"))]
    for key in path_keys:
        val = args.get(key)
        if not val or not isinstance(val, (str, list)):
            continue
        candidates = val if isinstance(val, list) else [val]
        try:
            fixed = [str(ws.resolve(ctx.user_id, str(c))) for c in candidates]
        except ws.WorkspaceError as exc:
            return args, {"success": False, "error": str(exc), "tool": name}, []
        args[key] = fixed if isinstance(val, list) else fixed[0]
        resolved[key] = fixed[0]

    args, constraint_error = _apply_design_constraints(name, args, ctx)
    if constraint_error:
        return args, {
            "success": False,
            "error": f"项目设计约束拒绝执行：{constraint_error}",
            "tool": name,
            "design_constraint": True,
        }, []

    snapshot_paths: list[str] = []
    if policy.auto_snapshot and policy.path_arg and policy.path_arg in resolved:
        snapshot_paths.append(resolved[policy.path_arg])
    return args, None, snapshot_paths


def _emit(event: dict[str, Any]) -> None:
    try:
        writer = get_stream_writer()
        writer(event)
    except Exception:  # noqa: BLE001, S110 — not inside a streaming run
        pass


def _canonical(args: Any) -> str:
    try:
        return json.dumps(args or {}, sort_keys=True, ensure_ascii=False, default=str)
    except (TypeError, ValueError):
        return str(args)


def _runtime_option(request: Any, key: str) -> Any:
    ctx = getattr(getattr(request, "runtime", None), "context", None)
    if isinstance(ctx, AgentContext) and key in ctx.extra:
        return ctx.extra[key]
    try:
        configurable = get_config().get("configurable", {})
    except RuntimeError:
        configurable = {}
    return configurable.get(key) if isinstance(configurable, dict) else None


def _plan_value(value: Any) -> Any:
    if isinstance(value, dict):
        return {
            key: _plan_value(item)
            for key, item in sorted(value.items())
            if key not in PATH_ARG_NAMES
            and not any(hint in key.lower() for hint in ("path", "_file", "_dir"))
        }
    if isinstance(value, list | tuple):
        return [_plan_value(item) for item in value]
    if isinstance(value, int | float) and not isinstance(value, bool):
        return round(float(value), 6)
    return value


def _plan_signature(name: str, args: Any) -> str:
    return f"{name}:{_canonical(_plan_value(args if isinstance(args, dict) else {}))}"


def _mentions_kicad_design(value: Any) -> bool:
    if isinstance(value, str):
        lowered = value.lower()
        return any(suffix in lowered for suffix in KICAD_DESIGN_SUFFIXES)
    if isinstance(value, dict):
        return any(_mentions_kicad_design(item) for item in value.values())
    if isinstance(value, list | tuple):
        return any(_mentions_kicad_design(item) for item in value)
    return False


def _mutation_plan_error(request: ToolCallRequest, name: str, args: dict[str, Any]) -> str | None:  # noqa: ARG001
    """Model-issued design mutations are never executed directly.

    Approved plans are executed server-side by ``submit_change_plan`` itself
    (see ``app.agent.executor``), so a direct call is either the model trying to
    skip approval or re-doing work that already happened.
    """
    if not _runtime_option(request, "require_change_plan"):
        return None
    if isinstance(_runtime_option(request, "approved_change_plan"), dict):
        return (
            f"{name} 不能直接调用：已批准的计划由系统自动执行完毕（结果见 submit_change_plan 的返回）。"
            "请不要重复修改；如需进一步改动，请重新提交一份新的修改计划。"
        )
    return (
        f"{name} 是设计文件修改工具，不能直接调用。请先完成只读分析，再用 "
        "submit_change_plan 一次列出全部具体修改（真实工具名 + 完整参数）；"
        "用户批准后系统会自动按顺序执行，并把每个动作的结果返回给你。"
    )


def _text(msg: ToolMessage | None) -> str | None:
    if msg is None:
        return None
    return msg.content if isinstance(msg.content, str) else json.dumps(msg.content, default=str)


def _coarse_value(value: Any) -> Any:
    if isinstance(value, dict):
        return {
            key: _coarse_value(item)
            for key, item in sorted(value.items())
            if key not in PATH_ARG_NAMES and not any(hint in key.lower() for hint in ("path", "_file", "_dir"))
        }
    if isinstance(value, list | tuple):
        return [_coarse_value(item) for item in value]
    if isinstance(value, int | float) and not isinstance(value, bool):
        return round(float(value) / NEAR_REPEAT_GRID) * NEAR_REPEAT_GRID
    return value


def _coarse_signature(args: Any) -> str:
    return _canonical(_coarse_value(args if isinstance(args, dict) else {}))


def detect_repeat(messages: list[Any], tool_call: dict[str, Any]) -> tuple[int, bool]:
    """Count earlier identical calls in the current human turn.

    Returns ``(repeats, stalled)`` where *stalled* means the last two identical
    calls returned exactly the same result (the agent is not making progress).
    """
    repeats, stalled, _near, _total = analyze_repeats(messages, tool_call)
    return repeats, stalled


def analyze_repeats(messages: list[Any], tool_call: dict[str, Any]) -> tuple[int, bool, int, int]:
    """``(exact_repeats, stalled, near_repeats, calls_of_tool)`` within the current turn.

    *near_repeats* counts earlier calls of the same tool whose numeric arguments
    fall into the same ``NEAR_REPEAT_GRID`` buckets (paths ignored) — the
    "nudge a coordinate by 0.01 and retry" pattern.
    """
    name = tool_call.get("name")
    tcid = tool_call.get("id")
    sig = _canonical(tool_call.get("args"))
    coarse = _coarse_signature(tool_call.get("args"))
    start = 0
    for i in range(len(messages) - 1, -1, -1):
        if isinstance(messages[i], HumanMessage):
            start = i
            break
    turn = messages[start:]
    results = {m.tool_call_id: m for m in turn if isinstance(m, ToolMessage)}
    prior: list[str | None] = []
    near = 0
    total = 0
    for m in turn:
        if not isinstance(m, AIMessage):
            continue
        for tc in m.tool_calls or []:
            if tc.get("id") == tcid or tc.get("name") != name:
                continue
            total += 1
            if _canonical(tc.get("args")) == sig:
                prior.append(_text(results.get(tc.get("id"))))
            elif _coarse_signature(tc.get("args")) == coarse:
                near += 1
    stalled = len(prior) >= 2 and prior[-1] is not None and prior[-1] == prior[-2]
    return len(prior), stalled, near, total


class KiCadPolicyMiddleware(AgentMiddleware):
    """Path scoping + loop guard + auto snapshot + UI change notifications."""

    name = "KiCadPolicyMiddleware"

    def __init__(self, result_max_chars: int = DEFAULT_MAX_CHARS) -> None:
        super().__init__()
        self.result_max_chars = result_max_chars

    # ---- result budget ------------------------------------------------------
    def _shrink(self, request: ToolCallRequest, result: ToolMessage | Command) -> ToolMessage | Command:
        """Cut oversized results so one query cannot flood the context window."""
        if not isinstance(result, ToolMessage) or not isinstance(result.content, str):
            return result
        if len(result.content) <= self.result_max_chars:
            return result
        ctx = _ctx_from_request(request)
        root = ws.user_root(ctx.user_id).resolve() if ctx else None
        tool = request.tool_call["name"]
        tcid = request.tool_call.get("id")
        content, info = shrink_tool_result(
            result.content,
            tool=tool,
            tool_call_id=tcid,
            spill_root=root,
            max_chars=self.result_max_chars,
        )
        if info is None:
            return result
        _emit({"type": "result_truncated", "tool": tool, "tool_call_id": tcid, **info})
        artifact = dict(result.artifact) if isinstance(result.artifact, dict) else {}
        artifact["truncated"] = info
        return result.model_copy(update={"content": content, "artifact": artifact})

    # ---- live time injection ------------------------------------------------
    def wrap_model_call(self, request: Any, handler: Any) -> Any:
        return handler(_prepare_model_request(request))

    async def awrap_model_call(self, request: Any, handler: Any) -> Any:
        return await handler(_prepare_model_request(request))

    # ---- loop guard ---------------------------------------------------------
    def _loop_guard(self, request: ToolCallRequest) -> dict[str, Any] | None:
        tool_call = request.tool_call
        name = tool_call["name"]
        if get_policy(name).kind == "harness":
            return None
        state = request.state if isinstance(request.state, dict) else {}
        repeats, stalled, near, total = analyze_repeats(list(state.get("messages") or []), tool_call)
        reason: str | None = None
        if repeats >= HARD_REPEAT_LIMIT or (repeats >= SOFT_REPEAT_LIMIT and stalled):
            reason = (
                f"检测到循环：这是你在本轮中第 {repeats + 1} 次用完全相同的参数调用 {name}，"
                "而之前的调用结果已经没有变化。"
            )
        elif near + repeats >= NEAR_REPEAT_LIMIT:
            reason = (
                f"检测到无效反复：本轮已用几乎相同的参数（仅数值微调）调用 {name} {near + repeats + 1} 次。"
            )
        elif total >= TOOL_CALLS_PER_TURN_LIMIT:
            reason = f"本轮对 {name} 的调用已达 {total} 次，超过单轮上限（{TOOL_CALLS_PER_TURN_LIMIT}）。"
        if reason is None:
            return None
        _emit({"type": "loop_guard", "tool": name, "repeats": repeats + 1, "near": near, "total": total + 1, "tool_call_id": tool_call.get("id")})
        return {
            "success": False,
            "error": (
                reason + "本次调用已被拦截。请不要再重试同样的操作——直接利用已有结果继续，"
                "换一种方法，或者停下来向用户说明现状并请求指示。"
            ),
            "tool": name,
            "loop_guard": True,
        }

    # ---- path policy --------------------------------------------------------
    def _prepare(self, request: ToolCallRequest) -> tuple[ToolCallRequest, dict[str, Any] | None, list[str]]:
        """Return (request, error_payload, snapshot_paths)."""
        tool_call = request.tool_call
        name = tool_call["name"]
        args = dict(tool_call.get("args") or {})
        policy = get_policy(name)
        ctx = _ctx_from_request(request)
        if name in GENERIC_MUTATION_TOOLS and _mentions_kicad_design(args):
            return request, {
                "success": False,
                "error": (
                    f"禁止使用通用 {name} 直接改写 KiCad 设计文件；"
                    "请改用已注册的 EDA 修改工具并通过 submit_change_plan 审批。"
                ),
                "tool": name,
                "change_plan_required": True,
            }, []
        if policy.kind == "file_mutation":
            plan_error = _mutation_plan_error(request, name, args)
            if plan_error:
                return request, {
                    "success": False,
                    "error": plan_error,
                    "tool": name,
                    "change_plan_required": True,
                }, []
        if ctx is None:
            return request, None, []
        if policy.kind == "harness":
            # The virtual filesystem is already user-scoped (UserWorkspaceBackend);
            # this is defence in depth so a cross-user path never reaches a backend.
            harness_error = _harness_path_error(args, ctx)
            if harness_error:
                return request, {"success": False, "error": harness_error, "tool": name}, []
            return request, None, []

        args, error, snapshot_paths = apply_call_policy(name, args, ctx)
        if error:
            return request, error, []
        return request.override(tool_call={**tool_call, "args": args}), None, snapshot_paths

    def _before(self, request: ToolCallRequest, snapshot_paths: list[str]) -> list[Snapshot]:
        ctx = _ctx_from_request(request)
        if ctx is None:
            return []
        snaps: list[Snapshot] = []
        for path in snapshot_paths:
            try:
                meta = ws.save_version(ctx.user_id, path, label=f"before {request.tool_call['name']}")
                _emit({"type": "snapshot", "tool": request.tool_call["name"], **meta})
                snaps.append((path, ws.version_path(ctx.user_id, path, meta["version_id"])))
            except ws.WorkspaceError as exc:
                log.info("snapshot skipped: %s", exc)
                snaps.append((path, None))
        return snaps

    # ---- post-processing ----------------------------------------------------
    @staticmethod
    def _succeeded(result: ToolMessage | Command) -> bool:
        if not isinstance(result, ToolMessage) or result.status == "error":
            return False
        content = result.content if isinstance(result.content, str) else ""
        if content.lstrip().startswith("{"):
            try:
                return json.loads(content).get("success", True) is not False
            except json.JSONDecodeError:
                pass
        return True

    @staticmethod
    def _changed_files(request: ToolCallRequest, snaps: list[Snapshot]) -> list[ChangedFile]:
        ctx = _ctx_from_request(request)
        out: list[ChangedFile] = []
        for path, snap in snaps:
            rel = Path(path).name
            if ctx:
                try:
                    rel = ws.relpath(ctx.user_id, ws.resolve(ctx.user_id, path))
                except (ws.WorkspaceError, ValueError):
                    pass
            out.append((path, rel, snap))
        return out

    @staticmethod
    def _compute_diffs(tool: str, files: list[ChangedFile]) -> list[dict[str, Any]]:
        """CPU-bound part (parsing + difflib); safe to run in a worker thread."""
        diffs: list[dict[str, Any]] = []
        for path, rel, snap in files:
            if snap is None or not snap.exists():
                continue
            try:
                diff = diff_files(snap, Path(path), rel)
            except Exception:  # diffs are best effort
                log.exception("diff failed for %s", path)
                continue
            diff["tool"] = tool
            diffs.append(diff)
        return diffs

    def _after(self, request: ToolCallRequest, result: ToolMessage | Command, files: list[ChangedFile], diffs: list[dict[str, Any]]) -> ToolMessage | Command:
        """Emit UI events and attach diffs to the ToolMessage (as artifact, not content)."""
        tool = request.tool_call["name"]
        tcid = request.tool_call.get("id")
        for path, rel, _snap in files:
            _emit({"type": "file_changed", "tool": tool, "path": path, "relpath": rel, "tool_call_id": tcid})
        for diff in diffs:
            _emit({"type": "file_diff", "tool_call_id": tcid, **diff})
        if not diffs or not isinstance(result, ToolMessage):
            return result
        artifact = dict(result.artifact) if isinstance(result.artifact, dict) else {}
        artifact["file_diff"] = diffs[0] if len(diffs) == 1 else diffs
        return result.model_copy(update={"artifact": artifact})

    @staticmethod
    def _error_message(request: ToolCallRequest, payload: dict[str, Any]) -> ToolMessage:
        return ToolMessage(
            content=json.dumps(payload, ensure_ascii=False),
            tool_call_id=request.tool_call["id"],
            name=request.tool_call["name"],
            status="error",
        )

    # ---- sync ---------------------------------------------------------------
    def wrap_tool_call(
        self,
        request: ToolCallRequest,
        handler: Callable[[ToolCallRequest], ToolMessage | Command[Any]],
    ) -> ToolMessage | Command[Any]:
        guard = self._loop_guard(request)
        if guard:
            return self._error_message(request, guard)
        request, error, snapshot_paths = self._prepare(request)
        if error:
            return self._error_message(request, error)
        if not snapshot_paths:
            return self._shrink(request, handler(request))
        with _sync_lock(snapshot_paths[0]):
            snaps = self._before(request, snapshot_paths)
            result = handler(request)
            if not self._succeeded(result):
                return self._shrink(request, result)
            files = self._changed_files(request, snaps)
            diffs = self._compute_diffs(request.tool_call["name"], files)
        return self._shrink(request, self._after(request, result, files, diffs))

    # ---- memory size ----------------------------------------------------------
    @staticmethod
    async def _memory_limit_error(request: ToolCallRequest) -> dict[str, Any] | None:
        """Refuse memory edits that would push /memories/AGENTS.md past its cap."""
        from app.agent.memory import MEMORY_MAX_CHARS, MEMORY_ROUTE, read_user_memory

        name = request.tool_call["name"]
        if name not in ("write_file", "edit_file"):
            return None
        args = request.tool_call.get("args") or {}
        path = str(args.get("file_path") or "").replace("\\", "/")
        if not path.startswith(MEMORY_ROUTE):
            return None
        ctx = _ctx_from_request(request)
        store = getattr(request.runtime, "store", None)
        if ctx is None or store is None:
            return None
        try:
            current = await read_user_memory(store, ctx.user_id)
        except Exception:  # noqa: BLE001 — never block on a bookkeeping failure
            return None
        if name == "write_file":
            projected = len(str(args.get("content") or ""))
        else:
            old, new = str(args.get("old_string") or ""), str(args.get("new_string") or "")
            occurrences = current.count(old) if args.get("replace_all") else (1 if old in current else 0)
            projected = len(current) + occurrences * (len(new) - len(old))
        if projected <= MEMORY_MAX_CHARS:
            return None
        return {
            "success": False,
            "error": (
                f"记忆文件将达到 {projected} 字符，超过上限 {MEMORY_MAX_CHARS}。记忆会注入每次请求的系统提示，"
                "请先精简：合并重复条目、删除过时或一次性的信息，只保留长期偏好与项目约定。"
            ),
            "tool": name,
            "memory_limit": True,
        }

    # ---- async --------------------------------------------------------------
    async def awrap_tool_call(
        self,
        request: ToolCallRequest,
        handler: Callable[[ToolCallRequest], Awaitable[ToolMessage | Command[Any]]],
    ) -> ToolMessage | Command[Any]:
        guard = self._loop_guard(request)
        if guard:
            return self._error_message(request, guard)
        request, error, snapshot_paths = self._prepare(request)
        if error:
            return self._error_message(request, error)
        memory_error = await self._memory_limit_error(request)
        if memory_error:
            return self._error_message(request, memory_error)
        if not snapshot_paths:
            return self._shrink(request, await handler(request))
        async with _async_lock(snapshot_paths[0]):
            snaps = self._before(request, snapshot_paths)
            result = await handler(request)
            if not self._succeeded(result):
                return self._shrink(request, result)
            files = self._changed_files(request, snaps)
            diffs = await asyncio.to_thread(self._compute_diffs, request.tool_call["name"], files)
        return self._shrink(request, self._after(request, result, files, diffs))
