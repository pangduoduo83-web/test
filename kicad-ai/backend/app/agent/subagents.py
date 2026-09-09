"""Specialised subagents (deepagents ``SubAgent`` specs).

Each subagent gets an isolated context window and only the tools of its
domain, so long multi-step layout/DRC work does not bloat the main thread.
"""

from __future__ import annotations

from langchain_core.tools import BaseTool

from app.agent.middleware import KiCadPolicyMiddleware
from app.agent.prompts import (
    SUBAGENT_DRC_PROMPT,
    SUBAGENT_PCB_LAYOUT_PROMPT,
    SUBAGENT_SCHEMATIC_PROMPT,
)
from app.agent.tools.registry import get_policy


def _by_category(tools: list[BaseTool], categories: set[str]) -> list[BaseTool]:
    # Subagents are read-only specialists: design changes are proposed back to
    # the main agent, which submits the single approved plan the server executes.
    return [
        t
        for t in tools
        if get_policy(t.name).category in categories and get_policy(t.name).kind != "file_mutation"
    ]


def build_subagents(
    tools: list[BaseTool],
    interrupt_on: dict | None = None,
    middleware: list | None = None,
    policy: KiCadPolicyMiddleware | None = None,
) -> list[dict]:
    """*middleware* runs before the policy middleware (e.g. the per-run model router)."""
    common = {"middleware": [*(middleware or []), policy or KiCadPolicyMiddleware()]}
    if interrupt_on:
        common["interrupt_on"] = interrupt_on
    return [
        {
            "name": "pcb-layout-agent",
            "description": (
                "PCB 布局分析专家（只读）：读取板情与焊盘坐标、校验占位与板框、"
                "评估布局评分，产出可直接提交计划的移动/对齐/布线动作列表。"
            ),
            "system_prompt": SUBAGENT_PCB_LAYOUT_PROMPT,
            "tools": _by_category(tools, {"pcb_query", "pcb_place", "pcb_edit"}),
            **common,
        },
        {
            "name": "drc-agent",
            "description": (
                "DRC / 设计规则专家（只读）：运行 DRC、解读违规、按类型归类，"
                "并给出可自动处理项目的具体修复动作。"
            ),
            "system_prompt": SUBAGENT_DRC_PROMPT,
            "tools": _by_category(tools, {"drc", "pcb_query", "pcb_place"}),
            **common,
        },
        {
            "name": "schematic-agent",
            "description": (
                "原理图专家（只读）：检索符号库与引脚、读取网表与标签、检查参考号冲突，"
                "产出放置/连线/标号的具体动作列表。"
            ),
            "system_prompt": SUBAGENT_SCHEMATIC_PROMPT,
            "tools": _by_category(tools, {"sch_query", "sch_edit", "library"}),
            **common,
        },
    ]
