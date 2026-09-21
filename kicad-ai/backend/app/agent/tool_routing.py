"""Conservative, local tool routing; no extra model call or shared run state."""

from __future__ import annotations

import json
import re
from typing import Any, Literal

from langchain.tools import tool
from langchain_core.messages import AIMessage, HumanMessage, ToolMessage

from app.agent.context import AgentContext
from app.agent.tools.registry import TOOL_POLICIES


@tool
def expand_design_tools(scope: Literal["schematic", "pcb", "all"]) -> str:
    """当前工具不足时扩展本轮设计工具：schematic 原理图、pcb 电路板、all 全部。

    只加载当前用户原本有权使用的工具，不修改工程、不替代变更审批。
    不需要扩展时直接使用已提供的工具。
    """
    return json.dumps({"success": True, "scope": scope}, ensure_ascii=False)


def _intent(text: str) -> set[str]:
    pcb = bool(re.search(r"pcb|\bdrc\b|封装|焊盘|走线|布线|覆铜|过孔|板框|布局|电路板", text, re.I))
    sch = bool(re.search(r"原理图|schematic|\berc\b|符号|引脚|网表|画.*电路|绘制.*电路", text, re.I))
    if not pcb and re.search(r"添加|加一个|画一个|绘制", text) and re.search(r"LED|LDO|分压|去耦|电源模块|指示灯", text, re.I):
        sch = True
    if re.search(r"\beco\b|同步|整.*工程|全面检查", text, re.I):
        return {"schematic", "pcb"}
    return ({"pcb"} if pcb else set()) | ({"schematic"} if sch else set())


def design_scopes(messages: list[Any], ctx: AgentContext | None) -> set[str]:
    """Unknown requests keep the full catalog; a short continuation inherits intent."""
    start = next((i for i in range(len(messages) - 1, -1, -1)
                  if isinstance(messages[i], HumanMessage)), -1)
    text = messages[start].text if start >= 0 else ""
    scopes = _intent(text)
    continuation = (re.fullmatch(r"\s*(继续|继续执行|接着做|接着画|continue)[。.!！\s]*", text, re.I)
                    or text.startswith("继续执行上面的任务，从刚才中断的地方接着做"))
    if not scopes and continuation:
        for message in reversed(messages[:max(start, 0)]):
            if isinstance(message, HumanMessage):
                scopes = _intent(message.text)
                if scopes:
                    break
    if not scopes and ctx and ctx.selection and re.search(r"选中|这些|这里|所选", text):
        mode = ctx.selection.get("mode")
        scopes = {"pcb"} if mode == "pcb" else {"schematic"} if mode == "sch" else set()
    if not scopes and ctx and bool(ctx.pcb_path) != bool(ctx.schematic_path):
        scopes = {"pcb"} if ctx.pcb_path else {"schematic"}
    if not scopes:
        scopes = {"pcb", "schematic"}

    # Expansion is carried by completed tool results in this user turn. It
    # survives checkpoint/resume but cannot leak into another run or tenant.
    calls: dict[str, dict] = {}
    for message in messages[start + 1:]:
        if isinstance(message, AIMessage):
            calls.update({c["id"]: c for c in message.tool_calls if c.get("id")})
        elif isinstance(message, ToolMessage) and message.status != "error":
            call = calls.get(message.tool_call_id, {})
            if call.get("name") != "expand_design_tools":
                continue
            try:
                result = json.loads(message.text)
            except (ValueError, TypeError):
                continue
            if not isinstance(result, dict) or result.get("success") is not True:
                continue
            scope = result.get("scope")
            if scope != call.get("args", {}).get("scope"):
                continue
            if scope == "all":
                scopes.update({"pcb", "schematic"})
            elif scope in {"pcb", "schematic"}:
                scopes.add(scope)
    return scopes


def select_design_tools(request: Any) -> tuple[Any, str]:
    """Filter only registered design tools; retain framework/unknown tools.

    Never add tools to a request: role and specialist restrictions stay intact.
    Mutation schemas remain visible in the selected domain to construct plans.
    """
    tools = getattr(request, "tools", None) or []
    # Specialist agents without an expansion tool already have scoped tools.
    if not any(getattr(t, "name", None) == "expand_design_tools" for t in tools):
        return request, ""
    context = getattr(getattr(request, "runtime", None), "context", None)
    ctx = context if isinstance(context, AgentContext) else None
    scopes = design_scopes(list(getattr(request, "messages", None) or []), ctx)
    if len(scopes) == 2:
        return request, ""
    scope = next(iter(scopes))

    def keep(t: Any) -> bool:
        name = getattr(t, "name", None)
        policy = TOOL_POLICIES.get(name)
        if policy is None:
            return True
        if policy.category.startswith("pcb_"):
            return scope == "pcb"
        if policy.category.startswith("sch_"):
            return scope == "schematic"
        # Shared libraries, project management, snapshots and full design
        # review stay available, including footprint assignment for schematics.
        if policy.category == "drc":
            if policy.path_arg == "schematic_path":
                return scope == "schematic"
            if policy.path_arg in {"pcb_path", "project_path"}:
                return scope == "pcb"
        return True

    kept = [t for t in tools if keep(t)]
    label = "原理图" if scope == "schematic" else "PCB"
    hint = (f"\n- 当前按{label}任务提供相关工具；若后续需要另一领域的工具，"
            "调用 expand_design_tools 扩展后再读取真实参数定义，不要猜参数或报告功能不存在。")
    return request.override(tools=kept), hint
