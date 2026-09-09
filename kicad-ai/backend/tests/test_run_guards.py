"""Run-level safety nets: loop guard, recursion-limit recovery, diff payloads."""

from __future__ import annotations

import json
import shutil
from pathlib import Path
from typing import Any

import pytest
from langchain_core.messages import AIMessage, AIMessageChunk, HumanMessage, ToolMessage
from langchain_core.outputs import ChatGeneration, ChatGenerationChunk, ChatResult

from app.agent import factory
from app.agent.context import AgentContext
from app.agent.factory import AgentRuntime
from app.agent.llm import MockKiCadChatModel
from app.agent.memory import ensure_user_memory
from app.agent.middleware import _apply_design_constraints, detect_repeat
from app.agent.streaming import stream_agent_events
from app.config import Settings
from app.kicad.diff import diff_texts

BACKEND = Path(__file__).resolve().parent.parent


def test_project_constraints_fill_defaults_and_reject_undersized_geometry():
    ctx = AgentContext(
        user_id="u1",
        design_constraints={
            "min_clearance_mm": 0.25,
            "min_track_width_mm": 0.2,
            "signal_track_width_mm": 0.3,
            "power_track_width_mm": 0.8,
            "via_diameter_mm": 0.7,
            "via_drill_mm": 0.35,
        },
    )
    routed, error = _apply_design_constraints(
        "pcb_route_pad_to_pad", {"net": "GND", "width": None}, ctx
    )
    assert error is None and routed["width"] == 0.8
    _, error = _apply_design_constraints(
        "pcb_route_pad_to_pad", {"net": "SIG", "width": 0.15}, ctx
    )
    assert error and "低于项目约束" in error

    vias, error = _apply_design_constraints(
        "pcb_add_vias", {"vias": [{"x": 10, "y": 10, "net": "GND"}]}, ctx
    )
    assert error is None
    assert vias["vias"][0]["diameter"] == 0.7 and vias["vias"][0]["drill"] == 0.35

    _, error = _apply_design_constraints(
        "set_net_class_rules", {"updates": {"clearance": 0.1}}, ctx
    )
    assert error and "网络间距" in error


class RepeatingModel(MockKiCadChatModel):
    """Keeps calling ``get_board_info`` with identical args until a tool error
    arrives, then answers in plain text (what a well-behaved model does after
    the loop guard fires)."""

    def _next(self, messages: list[Any]) -> AIMessage:
        for m in reversed(messages):
            if isinstance(m, HumanMessage):
                break
            if isinstance(m, ToolMessage) and m.status == "error":
                return AIMessage(content=f"收到拦截提示，停止重复调用。{m.content[:60]}")
        n = sum(1 for m in messages if isinstance(m, AIMessage))
        return AIMessage(content="", tool_calls=[{"name": "get_board_info", "args": {}, "id": f"rep_{n}", "type": "tool_call"}])

    def _generate(self, messages, stop=None, run_manager=None, **kwargs):
        return ChatResult(generations=[ChatGeneration(message=self._next(messages))])

    def _stream(self, messages, stop=None, run_manager=None, **kwargs):
        msg = self._next(messages)
        if msg.tool_calls:
            tc = msg.tool_calls[0]
            yield ChatGenerationChunk(
                message=AIMessageChunk(content="", tool_call_chunks=[{"name": tc["name"], "args": json.dumps(tc["args"]), "id": tc["id"], "index": 0, "type": "tool_call_chunk"}])
            )
        else:
            yield ChatGenerationChunk(message=AIMessageChunk(content=msg.content))


class EndlessModel(MockKiCadChatModel):
    """Alternates between two different reads forever (never trips the loop
    guard) — the only thing that stops it is the recursion limit. After a
    ``继续`` message it answers plainly."""

    def _next(self, messages: list[Any]) -> AIMessage:
        last_human = next((m for m in reversed(messages) if isinstance(m, HumanMessage)), None)
        if last_human is not None and "继续" in str(last_human.content):
            return AIMessage(content="好的，继续完成剩余工作。")
        n = sum(1 for m in messages if isinstance(m, AIMessage))
        name = "get_board_info" if n % 2 == 0 else "list_footprints"
        return AIMessage(content="", tool_calls=[{"name": name, "args": {"marker": n}, "id": f"end_{n}", "type": "tool_call"}])

    def _generate(self, messages, stop=None, run_manager=None, **kwargs):
        return ChatResult(generations=[ChatGeneration(message=self._next(messages))])

    def _stream(self, messages, stop=None, run_manager=None, **kwargs):
        msg = self._next(messages)
        if msg.tool_calls:
            tc = msg.tool_calls[0]
            yield ChatGenerationChunk(
                message=AIMessageChunk(content="", tool_call_chunks=[{"name": tc["name"], "args": json.dumps(tc["args"]), "id": tc["id"], "index": 0, "type": "tool_call_chunk"}])
            )
        else:
            yield ChatGenerationChunk(message=AIMessageChunk(content=msg.content))


@pytest.fixture
def settings(tmp_path: Path) -> Settings:
    s = Settings(
        llm_provider="mock",
        llm_model="mock-kicad",
        data_dir=tmp_path / "data",
        workspace_root=tmp_path / "workspaces",
        samples_dir=BACKEND / "samples",
        kcaa_mode="off",
        enable_subagents=False,
    )
    s.ensure_dirs()
    return s


def _ctx(settings: Settings, user_id: str, conv: str) -> tuple[AgentContext, Path]:
    ws_dir = settings.workspace_root / user_id / "projects" / "power_module"
    shutil.copytree(settings.samples_dir / "power_module", ws_dir)
    ctx = AgentContext(
        user_id=user_id,
        conversation_id=conv,
        project_name="power_module",
        pcb_path=str(ws_dir / "power_module.kicad_pcb"),
        workspace_root=str(settings.workspace_root / user_id),
    )
    return ctx, ws_dir


async def _collect(runtime: AgentRuntime, ctx: AgentContext, conv: str, text: str) -> list[dict[str, Any]]:
    return [
        ev
        async for ev in stream_agent_events(
            runtime.agent,
            input_payload={"messages": [{"role": "user", "content": text}]},
            config=runtime.thread_config(conv, ctx.user_id),
            context=ctx,
        )
    ]


def test_detect_repeat_counts_identical_calls_in_turn():
    msgs = [
        HumanMessage(content="a"),
        AIMessage(content="", tool_calls=[{"name": "t", "args": {"x": 1}, "id": "1", "type": "tool_call"}]),
        ToolMessage(content="r", tool_call_id="1", name="t"),
        AIMessage(content="", tool_calls=[{"name": "t", "args": {"x": 1}, "id": "2", "type": "tool_call"}]),
        ToolMessage(content="r", tool_call_id="2", name="t"),
        AIMessage(content="", tool_calls=[{"name": "t", "args": {"x": 1}, "id": "3", "type": "tool_call"}]),
    ]
    assert detect_repeat(msgs, msgs[-1].tool_calls[0]) == (2, True)
    # different args → not a repeat; earlier human turn is ignored
    assert detect_repeat(msgs, {"name": "t", "args": {"x": 2}, "id": "4"}) == (0, False)
    later = [*msgs, ToolMessage(content="r", tool_call_id="3", name="t"), HumanMessage(content="b"), AIMessage(content="", tool_calls=[{"name": "t", "args": {"x": 1}, "id": "5", "type": "tool_call"}])]
    assert detect_repeat(later, later[-1].tool_calls[0]) == (0, False)


@pytest.mark.asyncio
async def test_loop_guard_blocks_stalled_repeats(settings: Settings, monkeypatch: pytest.MonkeyPatch):
    monkeypatch.setattr(factory, "build_chat_model", lambda _s: RepeatingModel())
    runtime = AgentRuntime(settings)
    await runtime.start()
    try:
        ctx, _ = _ctx(settings, "u_loop", "c_loop")
        await ensure_user_memory(runtime.store, ctx.user_id)
        events = await _collect(runtime, ctx, "c_loop", "读板子")
        results = [e for e in events if e["type"] == "tool_result"]
        # two identical successful reads, the third identical call is intercepted
        assert [r["ok"] for r in results] == [True, True, False]
        assert "检测到循环" in results[-1]["content"]
        assert any(e["type"] == "custom" and e["event"] == "loop_guard" for e in events)
        assert not any(e["type"] == "error" for e in events)
        final = [e for e in events if e["type"] == "message_end"][-1]
        assert "停止重复调用" in final["content"]
    finally:
        await runtime.stop()


@pytest.mark.asyncio
async def test_recursion_limit_is_reported_and_recoverable(settings: Settings, monkeypatch: pytest.MonkeyPatch):
    settings.agent_recursion_limit = 12  # 3 model→tools rounds
    monkeypatch.setattr(factory, "build_chat_model", lambda _s: EndlessModel())
    runtime = AgentRuntime(settings)
    await runtime.start()
    try:
        ctx, _ = _ctx(settings, "u_rec", "c_rec")
        await ensure_user_memory(runtime.store, ctx.user_id)
        events = await _collect(runtime, ctx, "c_rec", "一直读")
        errors = [e for e in events if e["type"] == "error"]
        assert len(errors) == 1
        err = errors[0]
        assert err["code"] == "recursion_limit" and err["recoverable"] is True and err["limit"] == 12
        assert "继续执行" in err["message"]
        assert events[-1]["type"] == "run_end"

        # progress survived: the completed tool rounds are checkpointed
        state = await runtime.get_state("c_rec", ctx.user_id)
        assert sum(1 for m in state.values["messages"] if isinstance(m, ToolMessage)) >= 2

        # "继续" on the same thread works: dangling tool call gets patched, model answers
        events2 = await _collect(runtime, ctx, "c_rec", "继续")
        assert not any(e["type"] == "error" for e in events2)
        final = [e for e in events2 if e["type"] == "message_end"][-1]
        assert "继续完成" in final["content"]
    finally:
        await runtime.stop()


def test_diff_texts_hunks_and_counts():
    before = ["(kicad_pcb", "  (footprint C3", "    (at 150 90)", "  )", ")"]
    after = ["(kicad_pcb", "  (footprint C3", "    (at 132.3 71.7 270)", "  )", ")"]
    d = diff_texts(before, after)
    assert (d["additions"], d["deletions"], d["changed"], d["truncated"]) == (1, 1, True, False)
    lines = d["hunks"][0]["lines"]
    removed = next(line for line in lines if line["t"] == "-")
    added = next(line for line in lines if line["t"] == "+")
    assert removed["o"] == 3 and "150 90" in removed["s"]
    assert added["n"] == 3 and "132.3 71.7" in added["s"]
    assert diff_texts(before, before) == {"additions": 0, "deletions": 0, "hunks": [], "truncated": False, "changed": False}
    big = diff_texts([], [f"line {i}" for i in range(1000)], max_lines=50)
    assert big["additions"] == 1000 and big["truncated"] is True and len(big["hunks"][0]["lines"]) == 50
