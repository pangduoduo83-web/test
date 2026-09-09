"""Server-side execution of approved change plans.

The model proposes a batch of design changes with ``submit_change_plan``; the
user approves it once; then *the server* runs every action, in order, with the
same policy as model-issued calls (workspace confinement, design constraints,
snapshot before each mutation, diff + ``file_changed`` events). The model only
receives the results and runs the read-only verification steps.

Executing the approved actions here — instead of asking the model to re-issue
them one by one — removes parameter drift between plan and execution, the
re-approval loops it caused, and a full model round per action.
"""

from __future__ import annotations

import dataclasses
import inspect
import json
import logging
from typing import Any

from langchain.agents.middleware.types import ToolCallRequest
from langchain.tools import ToolRuntime
from langchain_core.messages import ToolMessage
from langchain_core.tools import BaseTool

from app.agent.middleware import (
    KiCadPolicyMiddleware,
    _async_lock,
    _emit,
    _plan_signature,
    apply_call_policy,
)

log = logging.getLogger(__name__)

TOOLS_BY_NAME: dict[str, BaseTool] = {}
ACTION_RESULT_MAX_CHARS = 6_000


def register_tools(tools: list[BaseTool]) -> None:
    TOOLS_BY_NAME.clear()
    TOOLS_BY_NAME.update({t.name: t for t in tools})


def plan_signatures(plan: dict[str, Any]) -> list[str]:
    return [
        _plan_signature(str(action.get("tool") or ""), action.get("args") or {})
        for action in plan.get("actions") or []
        if isinstance(action, dict)
    ]


def plan_matches(approved: Any, submitted: dict[str, Any]) -> bool:
    """The plan the tool received must be the one the user saw and approved."""
    if not isinstance(approved, dict):
        return False
    return plan_signatures(approved) == plan_signatures(submitted) and bool(plan_signatures(submitted))


def _runtime_param(tool: BaseTool) -> str | None:
    fn = getattr(tool, "coroutine", None) or getattr(tool, "func", None)
    if fn is None:
        return None
    try:
        params = inspect.signature(fn).parameters
    except (TypeError, ValueError):
        return None
    for name, param in params.items():
        ann = param.annotation
        origin = getattr(ann, "__origin__", ann)
        if name == "runtime" or origin is ToolRuntime:
            return name
    return None


def _excerpt(message: ToolMessage) -> Any:
    content = message.content if isinstance(message.content, str) else json.dumps(message.content, default=str)
    if len(content) > ACTION_RESULT_MAX_CHARS:
        content = content[:ACTION_RESULT_MAX_CHARS] + f"…[已截断，共 {len(content)} 字符]"
    if content.lstrip().startswith(("{", "[")):
        try:
            return json.loads(content)
        except json.JSONDecodeError:
            pass
    return content


async def _invoke(
    tool: BaseTool,
    name: str,
    args: dict[str, Any],
    sub_id: str,
    runtime: ToolRuntime,
    snapshot_paths: list[str],
    policy: KiCadPolicyMiddleware,
) -> ToolMessage:
    call_args = dict(args)
    param = _runtime_param(tool)
    sub_runtime = dataclasses.replace(runtime, tool_call_id=sub_id)
    if param:
        call_args[param] = sub_runtime
    tool_call = {"name": name, "args": call_args, "id": sub_id, "type": "tool_call"}
    request = ToolCallRequest(tool_call={**tool_call, "args": args}, tool=tool, state=runtime.state, runtime=sub_runtime)

    async def run() -> ToolMessage:
        try:
            result = await tool.ainvoke(tool_call, config=runtime.config)
        except Exception as exc:  # noqa: BLE001 — surfaced in the plan report
            log.warning("plan action %s failed: %s", name, exc)
            return ToolMessage(
                content=json.dumps({"success": False, "error": f"{type(exc).__name__}: {exc}"}, ensure_ascii=False),
                tool_call_id=sub_id,
                name=name,
                status="error",
            )
        if isinstance(result, ToolMessage):
            return result
        return ToolMessage(content=str(result), tool_call_id=sub_id, name=name)

    if not snapshot_paths:
        return await run()
    async with _async_lock(snapshot_paths[0]):
        snaps = policy._before(request, snapshot_paths)
        result = await run()
        if not policy._succeeded(result):
            return result
        files = policy._changed_files(request, snaps)
        diffs = policy._compute_diffs(name, files)
    return policy._after(request, result, files, diffs)


async def execute_plan(plan: dict[str, Any], runtime: ToolRuntime) -> dict[str, Any]:
    """Run every action of an approved plan; stop at the first failure."""
    ctx = runtime.context
    plan_call_id = runtime.tool_call_id or "plan"
    policy = KiCadPolicyMiddleware()
    actions = plan.get("actions") or []
    results: list[dict[str, Any]] = []
    stopped_at: int | None = None

    for index, action in enumerate(actions):
        name = str(action.get("tool") or "")
        args = dict(action.get("args") or {})
        sub_id = f"{plan_call_id}:{index}"
        base = {"index": index, "tool": name, "summary": action.get("summary") or name}
        _emit({"type": "plan_action", "plan_call_id": plan_call_id, "status": "running", **base})

        tool = TOOLS_BY_NAME.get(name)
        if tool is None:
            entry = {**base, "success": False, "error": f"工具 {name} 当前不可用"}
        else:
            args, error, snapshot_paths = apply_call_policy(name, args, ctx)
            if error:
                entry = {**base, "success": False, "error": error.get("error", "策略拒绝执行")}
            else:
                message = await _invoke(tool, name, args, sub_id, runtime, snapshot_paths, policy)
                ok = policy._succeeded(message)
                entry = {**base, "success": ok, "result": _excerpt(message)}
                if not ok:
                    payload = entry["result"]
                    entry["error"] = payload.get("error") if isinstance(payload, dict) else str(payload)[:500]
                artifact = message.artifact if isinstance(message.artifact, dict) else {}
                if artifact.get("file_diff") is not None:
                    entry["diff"] = artifact["file_diff"]
        results.append(entry)
        _emit(
            {
                "type": "plan_action",
                "plan_call_id": plan_call_id,
                "status": "done" if entry["success"] else "error",
                **base,
                "error": entry.get("error"),
                "diff": entry.get("diff"),
            }
        )
        if not entry["success"]:
            stopped_at = index
            break

    executed = sum(1 for r in results if r["success"])
    report: dict[str, Any] = {
        "executed": executed,
        "failed": len(results) - executed,
        "skipped": max(len(actions) - len(results), 0),
        "results": results,
    }
    if stopped_at is not None:
        report["stopped_at"] = stopped_at
        report["note"] = (
            f"第 {stopped_at + 1} 个动作失败，其后的动作未执行。请分析原因；"
            "如需继续，重新提交仅包含剩余动作的计划。"
        )
    else:
        report["note"] = "全部动作已执行。请运行 verification 中的只读检查并向用户汇报结果。"
    return report
