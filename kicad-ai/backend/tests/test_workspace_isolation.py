"""Multi-tenant isolation of the agent's virtual filesystem (ls/read_file/…)."""

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
from app.agent.streaming import stream_agent_events
from app.config import Settings
from app.kicad import workspace as ws

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
    ws.configure(s.workspace_root)
    return s


def test_resolve_accepts_virtual_paths_and_rejects_other_users(settings: Settings):
    (settings.workspace_root / "alice" / "proj").mkdir(parents=True)
    (settings.workspace_root / "alice" / "proj" / "a.kicad_pcb").write_text("(kicad_pcb)")
    (settings.workspace_root / "bob" / "secret").mkdir(parents=True)
    (settings.workspace_root / "bob" / "secret" / "notes.md").write_text("bob only")

    own = (settings.workspace_root / "alice" / "proj" / "a.kicad_pcb").resolve()
    assert ws.resolve("alice", "proj/a.kicad_pcb") == own
    assert ws.resolve("alice", "/proj/a.kicad_pcb") == own  # virtual path from `ls`
    assert ws.resolve("alice", str(own)) == own  # absolute host path from the context block
    assert ws.resolve("alice", "/data/workspaces/alice/proj/a.kicad_pcb") == own  # legacy prefix
    assert ws.to_virtual("alice", str(own)) == "/proj/a.kicad_pcb"
    assert ws.to_virtual("alice", "/data/workspaces/alice") == "/"
    assert ws.to_virtual("alice", "/") == "/"

    bob_file = (settings.workspace_root / "bob" / "secret" / "notes.md").resolve()
    for attempt in (
        str(bob_file),
        "/data/workspaces/bob/secret/notes.md",
        "../bob/secret/notes.md",
        "/../bob/secret/notes.md",
    ):
        with pytest.raises(ws.WorkspaceError):
            ws.resolve("alice", attempt)


class SnoopingModel(MockKiCadChatModel):
    """Tries to list the shared root and read another user's file through the
    generic filesystem tools, then reads its own memory file and reports."""

    other_abs: str = ""

    def _next(self, messages: list[Any]) -> AIMessage:
        done = {m.name for m in messages if isinstance(m, ToolMessage)}
        for m in reversed(messages):
            if isinstance(m, HumanMessage):
                break
        if "ls" not in done:
            return AIMessage(
                content="",
                tool_calls=[
                    {"name": "ls", "args": {"path": "/"}, "id": "snoop_ls", "type": "tool_call"},
                    {"name": "read_file", "args": {"file_path": "/data/workspaces/bob/secret/notes.md"}, "id": "snoop_legacy", "type": "tool_call"},
                    {"name": "read_file", "args": {"file_path": self.other_abs}, "id": "snoop_abs", "type": "tool_call"},
                    {"name": "read_file", "args": {"file_path": "/memories/AGENTS.md"}, "id": "own_memory", "type": "tool_call"},
                    {"name": "read_file", "args": {"file_path": "/proj/a.kicad_pcb"}, "id": "own_file", "type": "tool_call"},
                ],
            )
        return AIMessage(content="done")

    def _generate(self, messages, stop=None, run_manager=None, **kwargs):
        return ChatResult(generations=[ChatGeneration(message=self._next(messages))])

    def _stream(self, messages, stop=None, run_manager=None, **kwargs):
        msg = self._next(messages)
        if msg.tool_calls:
            yield ChatGenerationChunk(
                message=AIMessageChunk(
                    content="",
                    tool_call_chunks=[
                        {"name": tc["name"], "args": json.dumps(tc["args"]), "id": tc["id"], "index": i, "type": "tool_call_chunk"}
                        for i, tc in enumerate(msg.tool_calls)
                    ],
                )
            )
        else:
            yield ChatGenerationChunk(message=AIMessageChunk(content=msg.content))


@pytest.mark.asyncio
async def test_filesystem_tools_are_scoped_to_the_calling_user(settings: Settings, monkeypatch: pytest.MonkeyPatch):
    alice_dir = settings.workspace_root / "alice" / "proj"
    shutil.copytree(settings.samples_dir / "power_module", alice_dir)
    (alice_dir / "a.kicad_pcb").write_text("(kicad_pcb (version 20240108))", encoding="utf-8")
    bob_file = settings.workspace_root / "bob" / "secret" / "notes.md"
    bob_file.parent.mkdir(parents=True)
    bob_file.write_text("bob only", encoding="utf-8")

    monkeypatch.setattr(factory, "build_chat_model", lambda _s: SnoopingModel(other_abs=str(bob_file.resolve())))
    runtime = AgentRuntime(settings)
    await runtime.start()
    try:
        await ensure_user_memory(runtime.store, "alice")
        ctx = AgentContext(user_id="alice", conversation_id="c_iso", workspace_root=str(settings.workspace_root / "alice"))
        events = [
            ev
            async for ev in stream_agent_events(
                runtime.agent,
                input_payload={"messages": [{"role": "user", "content": "看看别人的文件"}]},
                config=runtime.thread_config("c_iso", "alice"),
                context=ctx,
            )
        ]
        results = {e["id"]: e for e in events if e["type"] == "tool_result"}
        assert set(results) >= {"snoop_ls", "snoop_legacy", "snoop_abs", "own_memory", "own_file"}, list(results)

        listing = results["snoop_ls"]["content"]
        assert "proj" in listing and "bob" not in listing and "alice" not in listing, listing

        for blocked in ("snoop_legacy", "snoop_abs"):
            assert "bob only" not in results[blocked]["content"], results[blocked]
            assert "越界" in results[blocked]["content"] or "Error" in results[blocked]["content"], results[blocked]

        assert "用户记忆" in results["own_memory"]["content"]
        assert "kicad_pcb" in results["own_file"]["content"]
        assert not any(e["type"] == "error" for e in events)
    finally:
        await runtime.stop()
