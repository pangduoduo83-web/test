"""Per-request prompt trimming and the memory-file size cap."""

from __future__ import annotations

import json
from types import SimpleNamespace

import pytest
from langchain_core.messages import SystemMessage

from app.agent.context import AgentContext
from app.agent.memory import MEMORY_MAX_CHARS, MemoryTooLarge, check_memory_size
from app.agent.middleware import KiCadPolicyMiddleware, _prepare_model_request
from app.agent.prompts import build_system_prompt, trim_system_prompt


class _Request:
    def __init__(self, content: str, ctx: AgentContext | None):
        self.system_message = SystemMessage(content=content)
        self.runtime = SimpleNamespace(context=ctx)
        self.tools = []

    def override(self, **kw):
        new = _Request(self.system_message.content, self.runtime.context)
        new.__dict__.update(kw)
        return new


def test_sections_are_dropped_for_single_domain_projects():
    prompt = build_system_prompt("2026-01-01 00:00:00 (星期四)")
    assert "<!-- section:pcb -->" in prompt and "<!-- section:schematic -->" in prompt

    both = trim_system_prompt(prompt, has_pcb=True, has_schematic=True)
    assert "PCB 坐标系" in both and "原理图坐标系" in both and "<!--" not in both

    pcb_only = trim_system_prompt(prompt, has_pcb=True, has_schematic=False)
    assert "PCB 坐标系" in pcb_only and "原理图坐标系" not in pcb_only and "<!--" not in pcb_only

    sch_only = trim_system_prompt(prompt, has_pcb=False, has_schematic=True)
    assert "原理图坐标系" in sch_only and "PCB 坐标系" not in sch_only

    none = trim_system_prompt(prompt, has_pcb=False, has_schematic=False)  # no project: keep both
    assert "PCB 坐标系" in none and "原理图坐标系" in none and "<!--" not in none


def test_model_request_is_trimmed_and_gets_context_block():
    prompt = build_system_prompt()
    ctx = AgentContext(user_id="u1", project_name="board-only", pcb_path="C:/ws/u1/b/b.kicad_pcb")
    out = _prepare_model_request(_Request(prompt, ctx))
    text = out.system_message.content
    assert "原理图坐标系" not in text and "PCB 坐标系" in text
    assert "<!--" not in text and "active_pcb: C:/ws/u1/b/b.kicad_pcb" in text
    assert "当前系统日期与时间：" in text

    # Outside a run (no context) markers are still stripped and both sections kept.
    text = _prepare_model_request(_Request(prompt, None)).system_message.content
    assert "<!--" not in text and "原理图坐标系" in text and "PCB 坐标系" in text


def test_memory_size_cap():
    check_memory_size("x" * MEMORY_MAX_CHARS)
    with pytest.raises(MemoryTooLarge):
        check_memory_size("x" * (MEMORY_MAX_CHARS + 1))


class _Store:
    def __init__(self, content: str):
        self.content = content

    async def aget(self, ns, key):
        return SimpleNamespace(value={"content": self.content})


def _tool_request(name: str, args: dict, store, ctx=None):
    return SimpleNamespace(
        tool_call={"id": "t1", "name": name, "args": args},
        state={"messages": []},
        runtime=SimpleNamespace(context=ctx or AgentContext(user_id="u1"), store=store),
    )


@pytest.mark.asyncio
async def test_agent_memory_edits_are_capped():
    mw = KiCadPolicyMiddleware()
    store = _Store("# 用户记忆\n" + "- 偏好\n" * 100)

    ok = await mw._memory_limit_error(_tool_request("edit_file", {"file_path": "/memories/AGENTS.md", "old_string": "（暂无）", "new_string": "- 栅格 0.1 mm"}, store))
    assert ok is None

    huge = await mw._memory_limit_error(_tool_request("write_file", {"file_path": "/memories/AGENTS.md", "content": "y" * (MEMORY_MAX_CHARS + 10)}, store))
    assert huge and huge["memory_limit"] is True and "超过上限" in huge["error"]

    growth = await mw._memory_limit_error(
        _tool_request("edit_file", {"file_path": "/memories/AGENTS.md", "old_string": "- 偏好\n", "new_string": "- 偏好" + "详情" * 40 + "\n", "replace_all": True}, store)
    )
    assert growth and growth["memory_limit"] is True

    # Workspace files are not subject to the memory cap.
    assert await mw._memory_limit_error(_tool_request("write_file", {"file_path": "/notes.md", "content": "z" * 50_000}, store)) is None

    # The error payload is what the model sees.
    assert json.loads(mw._error_message(_tool_request("write_file", {}, store), huge).content)["memory_limit"] is True
