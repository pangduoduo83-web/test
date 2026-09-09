"""Validation shared by the plan tool and resume authorization."""

from __future__ import annotations

from typing import Any

from app.agent.tools.registry import get_policy


def validate_change_plan(value: Any) -> tuple[dict[str, Any] | None, str | None]:
    if not isinstance(value, dict):
        return None, "修改计划格式无效"
    actions = value.get("actions")
    if not isinstance(actions, list) or not actions:
        return None, "修改计划至少需要一个动作"
    if len(actions) > 50:
        return None, "单批修改最多 50 个动作，请拆分计划"

    cleaned_actions: list[dict[str, Any]] = []
    for index, action in enumerate(actions):
        if not isinstance(action, dict):
            return None, f"第 {index + 1} 个动作格式无效"
        name = str(action.get("tool") or "").strip()
        args = action.get("args")
        if not name or not isinstance(args, dict):
            return None, f"第 {index + 1} 个动作缺少有效的 tool / args"
        if get_policy(name).kind != "file_mutation":
            return None, f"第 {index + 1} 个动作 {name} 不是设计文件修改工具"
        if name == "pcb_route_pad_to_pad" and args.get("width") is None:
            return None, f"第 {index + 1} 个布线动作必须在计划中明确 width"
        if name == "pcb_add_vias":
            vias = args.get("vias")
            if not isinstance(vias, list) or any(
                not isinstance(via, dict)
                or via.get("diameter") is None
                or via.get("drill") is None
                for via in vias
            ):
                return None, f"第 {index + 1} 个过孔动作必须为每个过孔明确 diameter 和 drill"
        if name == "add_zone" and args.get("clearance") is None:
            return None, f"第 {index + 1} 个覆铜动作必须在计划中明确 clearance"
        cleaned_actions.append(
            {
                "tool": name,
                "args": args,
                "summary": str(action.get("summary") or name)[:300],
                "reason": str(action.get("reason") or "")[:500],
            }
        )

    checks: list[dict[str, Any]] = []
    verification = value.get("verification") or []
    if not isinstance(verification, list):
        return None, "verification 必须是数组"
    for item in verification:
        if not isinstance(item, dict):
            return None, "验证步骤格式无效"
        name = str(item.get("tool") or "").strip()
        args = item.get("args")
        if not name or not isinstance(args, dict) or get_policy(name).kind == "file_mutation":
            return None, f"验证步骤 {name or '(未命名)'} 必须是只读工具"
        checks.append(
            {
                "tool": name,
                "args": args,
                "summary": str(item.get("summary") or name)[:300],
            }
        )

    scope = value.get("scope") or []
    if not isinstance(scope, list):
        return None, "scope 必须是数组"
    return {
        "title": str(value.get("title") or "设计修改计划").strip()[:200],
        "summary": str(value.get("summary") or "").strip()[:1000],
        "scope": [str(item)[:64] for item in scope[:200]],
        "actions": cleaned_actions,
        "verification": checks,
    }, None
