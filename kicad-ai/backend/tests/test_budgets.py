"""Run ceilings beyond the step limit: wall-clock timeout, token budget, near-duplicate guard."""

from __future__ import annotations

import asyncio
import json
import shutil
from pathlib import Path
from typing import Any

import pytest
from langchain_core.messages import AIMessage, AIMessageChunk, HumanMessage, ToolMessage
from langchain_core.outputs import ChatGeneration, ChatGenerationChunk, ChatResult

from app.agent import factory
from app.agent.budget import RunBudgetMiddleware, turn_token_usage
from app.agent.context import AgentContext
from app.agent.factory import AgentRuntime
from app.agent.llm import MockKiCadChatModel
from app.agent.memory import ensure_user_memory
from app.agent.middleware import NEAR_REPEAT_LIMIT, analyze_repeats
from app.agent.streaming import stream_agent_events
from app.config import Settings

BACKEND = Path(__file__).resolve().parent.parent


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


def _ctx(settings: Settings, user_id: str, conv: str) -> AgentContext:
    ws_dir = settings.workspace_root / user_id / "projects" / "power_module"
    shutil.copytree(settings.samples_dir / "power_module", ws_dir)
    return AgentContext(
        user_id=user_id,
        conversation_id=conv,
        project_name="power_module",
        pcb_path=str(ws_dir / "power_module.kicad_pcb"),
        workspace_root=str(settings.workspace_root / user_id),
    )


def _usage(inp: int, out: int, total: int | None = None) -> dict[str, int]:
    return {"input_tokens": inp, "output_tokens": out, "total_tokens": inp + out if total is None else total}


def test_turn_token_usage_counts_only_current_turn():
    msgs = [
        HumanMessage(content="a"),
        AIMessage(content="x", usage_metadata=_usage(100, 10)),
        HumanMessage(content="b"),
        AIMessage(content="y", usage_metadata=_usage(200, 20)),
        AIMessage(content="z", usage_metadata=_usage(300, 30, total=0)),  # missing total → summed
        AIMessage(content="no usage"),
    ]
    assert turn_token_usage(msgs) == 220 + 330


def test_budget_middleware_is_dynamic_and_disabled_at_zero():
    budget = {"value": 0}
    mw = RunBudgetMiddleware(lambda: budget["value"])
    req = type("R", (), {"state": {"messages": [HumanMessage(content="a"), AIMessage(content="", usage_metadata=_usage(900, 99))]}})()
    assert mw.wrap_model_call(req, lambda r: "ok") == "ok"
    budget["value"] = 500
    with pytest.raises(Exception, match="budget exceeded"):
        mw.wrap_model_call(req, lambda r: "ok")


def test_near_duplicate_calls_are_detected_but_distinct_ones_are_not():
    calls = [
        {"name": "find_free_pcb_area", "args": {"width": 5 + i * 0.01, "height": 3}, "id": f"c{i}", "type": "tool_call"}
        for i in range(NEAR_REPEAT_LIMIT)
    ]
    msgs: list[Any] = [HumanMessage(content="find")]
    for call in calls:
        msgs.append(AIMessage(content="", tool_calls=[call]))
        msgs.append(ToolMessage(content=json.dumps({"candidates": [call["args"]["width"]]}), tool_call_id=call["id"], name=call["name"]))
    probe = {"name": "find_free_pcb_area", "args": {"width": 5.09, "height": 3}, "id": "probe"}
    exact, stalled, near, total = analyze_repeats(msgs, probe)
    assert exact == 0 and stalled is False
    assert near == NEAR_REPEAT_LIMIT and total == NEAR_REPEAT_LIMIT

    far = {"name": "find_free_pcb_area", "args": {"width": 12, "height": 3}, "id": "far"}
    assert analyze_repeats(msgs, far)[2] == 0


class NudgingModel(MockKiCadChatModel):
    """Retries the same query while nudging a number until a tool error arrives."""

    def _next(self, messages: list[Any]) -> AIMessage:
        for m in reversed(messages):
            if isinstance(m, HumanMessage):
                break
            if isinstance(m, ToolMessage) and m.status == "error":
                return AIMessage(content=f"收到拦截，停止。{m.content[:40]}")
        n = sum(1 for m in messages if isinstance(m, AIMessage))
        return AIMessage(content="", tool_calls=[{"name": "find_free_pcb_area", "args": {"width": 5 + n * 0.01, "height": 3}, "id": f"nudge_{n}", "type": "tool_call"}])

    def _generate(self, messages, stop=None, run_manager=None, **kwargs):
        return ChatResult(generations=[ChatGeneration(message=self._next(messages))])

    def _stream(self, messages, stop=None, run_manager=None, **kwargs):
        msg = self._next(messages)
        if msg.tool_calls:
            tc = msg.tool_calls[0]
            yield ChatGenerationChunk(message=AIMessageChunk(content="", tool_call_chunks=[{"name": tc["name"], "args": json.dumps(tc["args"]), "id": tc["id"], "index": 0, "type": "tool_call_chunk"}]))
        else:
            yield ChatGenerationChunk(message=AIMessageChunk(content=msg.content))


@pytest.mark.asyncio
async def test_near_duplicate_loop_is_intercepted(settings: Settings, monkeypatch: pytest.MonkeyPatch):
    monkeypatch.setattr(factory, "build_chat_model", lambda _s: NudgingModel())
    runtime = AgentRuntime(settings)
    await runtime.start()
    try:
        ctx = _ctx(settings, "u_nudge", "c_nudge")
        await ensure_user_memory(runtime.store, ctx.user_id)
        events = [
            ev
            async for ev in stream_agent_events(
                runtime.agent,
                input_payload={"messages": [{"role": "user", "content": "找空位"}]},
                config=runtime.thread_config("c_nudge", ctx.user_id),
                context=ctx,
            )
        ]
        results = [e for e in events if e["type"] == "tool_result"]
        assert results[-1]["ok"] is False and "无效反复" in results[-1]["content"]
        assert sum(1 for r in results if r["ok"]) == NEAR_REPEAT_LIMIT
        assert not any(e["type"] == "error" for e in events)
    finally:
        await runtime.stop()


class BigUsageModel(MockKiCadChatModel):
    """Every reply claims a huge token usage and keeps reading the board."""

    def _next(self, messages: list[Any]) -> AIMessage:
        n = sum(1 for m in messages if isinstance(m, AIMessage))
        msg = AIMessage(content="", tool_calls=[{"name": "get_board_info", "args": {"marker": n}, "id": f"big_{n}", "type": "tool_call"}])
        msg.usage_metadata = {"input_tokens": 40_000, "output_tokens": 100, "total_tokens": 40_100}
        return msg

    def _generate(self, messages, stop=None, run_manager=None, **kwargs):
        return ChatResult(generations=[ChatGeneration(message=self._next(messages))])

    def _stream(self, messages, stop=None, run_manager=None, **kwargs):
        msg = self._next(messages)
        tc = msg.tool_calls[0]
        yield ChatGenerationChunk(message=AIMessageChunk(content="", tool_call_chunks=[{"name": tc["name"], "args": json.dumps(tc["args"]), "id": tc["id"], "index": 0, "type": "tool_call_chunk"}]))
        yield ChatGenerationChunk(message=AIMessageChunk(content="", usage_metadata=msg.usage_metadata))


@pytest.mark.asyncio
async def test_token_budget_stops_run_recoverably(settings: Settings, monkeypatch: pytest.MonkeyPatch):
    settings.agent_run_token_budget = 100_000  # ~2.5 model calls of 40k
    monkeypatch.setattr(factory, "build_chat_model", lambda _s: BigUsageModel())
    runtime = AgentRuntime(settings)
    await runtime.start()
    try:
        ctx = _ctx(settings, "u_budget", "c_budget")
        await ensure_user_memory(runtime.store, ctx.user_id)
        events = [
            ev
            async for ev in stream_agent_events(
                runtime.agent,
                input_payload={"messages": [{"role": "user", "content": "一直读"}]},
                config=runtime.thread_config("c_budget", ctx.user_id),
                context=ctx,
            )
        ]
        errors = [e for e in events if e["type"] == "error"]
        assert len(errors) == 1 and errors[0]["code"] == "token_budget" and errors[0]["recoverable"] is True
        assert errors[0]["used"] > errors[0]["limit"] == 100_000
        assert events[-1]["type"] == "run_end"
        # 3 replies × 40k > 100k → stopped before the 4th model call
        assert sum(1 for e in events if e["type"] == "tool_result") == 3
    finally:
        await runtime.stop()


class SlowModel(MockKiCadChatModel):
    def _next(self, messages: list[Any]) -> AIMessage:
        n = sum(1 for m in messages if isinstance(m, AIMessage))
        return AIMessage(content="", tool_calls=[{"name": "get_board_info", "args": {"marker": n}, "id": f"slow_{n}", "type": "tool_call"}])

    def _generate(self, messages, stop=None, run_manager=None, **kwargs):
        return ChatResult(generations=[ChatGeneration(message=self._next(messages))])

    async def _agenerate(self, messages, stop=None, run_manager=None, **kwargs):
        await asyncio.sleep(0.15)
        return self._generate(messages, stop, run_manager, **kwargs)

    def _stream(self, messages, stop=None, run_manager=None, **kwargs):
        msg = self._next(messages)
        tc = msg.tool_calls[0]
        yield ChatGenerationChunk(message=AIMessageChunk(content="", tool_call_chunks=[{"name": tc["name"], "args": json.dumps(tc["args"]), "id": tc["id"], "index": 0, "type": "tool_call_chunk"}]))

    async def _astream(self, messages, stop=None, run_manager=None, **kwargs):
        await asyncio.sleep(0.15)
        for chunk in self._stream(messages, stop, run_manager, **kwargs):
            yield chunk


@pytest.mark.asyncio
async def test_wall_clock_timeout_is_reported_and_recoverable(settings: Settings, monkeypatch: pytest.MonkeyPatch):
    monkeypatch.setattr(factory, "build_chat_model", lambda _s: SlowModel())
    runtime = AgentRuntime(settings)
    await runtime.start()
    try:
        ctx = _ctx(settings, "u_slow", "c_slow")
        await ensure_user_memory(runtime.store, ctx.user_id)
        events = [
            ev
            async for ev in stream_agent_events(
                runtime.agent,
                input_payload={"messages": [{"role": "user", "content": "慢慢读"}]},
                config=runtime.thread_config("c_slow", ctx.user_id),
                context=ctx,
                timeout_seconds=0.5,
            )
        ]
        errors = [e for e in events if e["type"] == "error"]
        assert len(errors) == 1 and errors[0]["code"] == "run_timeout" and errors[0]["recoverable"] is True
        assert events[-1]["type"] == "run_end"
        # completed rounds are checkpointed and the thread stays usable
        state = await runtime.get_state("c_slow", ctx.user_id)
        assert state.values["messages"]
    finally:
        await runtime.stop()
