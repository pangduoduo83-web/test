"""End-to-end agent smoke test with the mock model (no API key needed)."""

from __future__ import annotations

import shutil
from pathlib import Path

import pytest

from app.agent.context import AgentContext
from app.agent.factory import AgentRuntime
from app.agent.memory import ensure_user_memory
from app.agent.streaming import stream_agent_events
from app.config import Settings

BACKEND = Path(__file__).resolve().parent.parent


@pytest.fixture
def settings(tmp_path: Path) -> Settings:
    s = Settings(
        llm_provider="mock",
        llm_model="mock-kicad",
        llm_thinking="deep",
        data_dir=tmp_path / "data",
        workspace_root=tmp_path / "workspaces",
        samples_dir=BACKEND / "samples",
        kcaa_mcp_url="",
    )
    s.ensure_dirs()
    return s


@pytest.mark.asyncio
async def test_stream_events(settings: Settings):
    user_id = "u_test"
    ws_dir = settings.workspace_root / user_id / "projects" / "power_module"
    shutil.copytree(settings.samples_dir / "power_module", ws_dir)

    runtime = AgentRuntime(settings)
    await runtime.start()
    try:
        assert runtime.agent is not None
        assert any(t.name == "get_board_info" for t in runtime.tools)
        await ensure_user_memory(runtime.store, user_id)

        ctx = AgentContext(
            user_id=user_id,
            username="tester",
            conversation_id="conv1",
            project_name="power_module",
            project_dir="projects/power_module",
            pcb_path=str(ws_dir / "power_module.kicad_pcb"),
            schematic_path=str(ws_dir / "power_module.kicad_sch"),
            workspace_root=str(settings.workspace_root / user_id),
        )
        events = []
        async for ev in stream_agent_events(
            runtime.agent,
            input_payload={"messages": [{"role": "user", "content": "帮我看看这块板子"}]},
            config=runtime.thread_config("conv1", user_id),
            context=ctx,
        ):
            events.append(ev)
        types = [e["type"] for e in events]
        assert types[0] == "run_start" and types[-1] == "run_end"
        assert "tool_call_start" in types
        results = [e for e in events if e["type"] == "tool_result"]
        assert results, types
        names = {r["name"] for r in results}
        assert {"get_board_info", "list_footprints"} <= names
        board = next(r for r in results if r["name"] == "get_board_info")
        assert board["ok"] and board["data"]["footprint_count"] == 9
        assert any(e["type"] == "text_delta" for e in events)
        reasoning = [e["delta"] for e in events if e["type"] == "reasoning_delta"]
        assert "".join(reasoning), types
        assert not any(e["type"] == "error" for e in events), [e for e in events if e["type"] == "error"]

        state = await runtime.get_state("conv1", user_id)
        msgs = state.values["messages"]
        assert len(msgs) >= 4  # human, ai(tool calls), 2 tool, ai(final)
        thinking_msg = next(m for m in msgs if getattr(m, "additional_kwargs", {}).get("reasoning_content"))
        assert "板级信息" in thinking_msg.additional_kwargs["reasoning_content"]
        from app.agent.streaming import messages_to_history

        history = messages_to_history(msgs)
        assert any(h.get("reasoning") for h in history if h["role"] == "assistant")
    finally:
        await runtime.stop()


@pytest.mark.asyncio
async def test_layout_optimisation_flow(settings: Settings):
    """Mutation path: policy middleware snapshots, moves caps, runs DRC, emits file_changed."""
    from app.kicad import workspace as ws
    from app.kicad.pcb import Board

    user_id = "u_layout"
    ws_dir = settings.workspace_root / user_id / "projects" / "power_module"
    shutil.copytree(settings.samples_dir / "power_module", ws_dir)
    pcb = ws_dir / "power_module.kicad_pcb"

    runtime = AgentRuntime(settings)
    await runtime.start()
    try:
        await ensure_user_memory(runtime.store, user_id)
        ctx = AgentContext(user_id=user_id, conversation_id="conv2", project_name="power_module", pcb_path=str(pcb), workspace_root=str(settings.workspace_root / user_id))
        config = runtime.thread_config("conv2", user_id)
        planning_events = [
            ev
            async for ev in stream_agent_events(
                runtime.agent,
                input_payload={"messages": [{"role": "user", "content": "帮我优化一下电源模块的布局，电容尽量靠近芯片的电源引脚，并检查是否有 DRC 问题。"}]},
                config=config,
                context=ctx,
            )
        ]
        interrupt = next(event for event in planning_events if event["type"] == "interrupt")
        request = interrupt["interrupts"][0]["value"]["action_requests"][0]
        assert request["name"] == "submit_change_plan"
        assert len(request["args"]["actions"]) == 2

        from langgraph.types import Command

        from app.agent.change_plan import validate_change_plan

        # What the API does on approval: the plan the user saw (from the
        # checkpointed interrupt) becomes the run's approved plan.
        approved, error = validate_change_plan(request["args"])
        assert error is None
        ctx.extra["approved_change_plan"] = approved

        execution_events = [
            ev
            async for ev in stream_agent_events(
                runtime.agent,
                input_payload=Command(resume={"decisions": [{"type": "approve"}]}),
                config=config,
                context=ctx,
            )
        ]
        events = planning_events + execution_events
        # Before approval nothing touches the file.
        assert not any(e["type"] == "custom" and e["event"] == "file_changed" for e in planning_events)
        results = {e["name"]: e for e in events if e["type"] == "tool_result"}
        # The approved plan is executed by the server inside submit_change_plan;
        # the model never re-issues the mutations itself.
        assert "set_footprint_position" not in results
        plan = results["submit_change_plan"]
        assert plan["ok"], plan
        report = plan["data"]
        assert report["approved"] is True and report["executed_by"] == "server"
        assert report["executed"] == 2 and report["failed"] == 0 and report["skipped"] == 0
        assert [r["tool"] for r in report["results"]] == ["set_footprint_position"] * 2
        assert all(r["success"] and r["diff"]["changed"] for r in report["results"])
        assert results["run_drc_check"]["ok"]
        drc = results["run_drc_check"]["data"]
        assert drc["engine"] in ("builtin-lite", "kicad-cli")
        if drc["engine"] == "builtin-lite":
            assert drc["passed"] is True
        customs = [e for e in events if e["type"] == "custom"]
        assert any(e["event"] == "snapshot" for e in customs)
        assert any(e["event"] == "file_changed" for e in customs)
        # Live progress for the plan card: running → done per action.
        progress = [e for e in customs if e["event"] == "plan_action"]
        assert [(e["index"], e["status"]) for e in progress] == [(0, "running"), (0, "done"), (1, "running"), (1, "done")]
        assert all(e["plan_call_id"] == "call_mock_change_plan" for e in progress)
        # Cursor-style edit cards: a diff per mutation, keyed by "<plan call>:<index>".
        diffs = {e["tool_call_id"]: e for e in customs if e["event"] == "file_diff"}
        assert set(diffs) == {"call_mock_change_plan:0", "call_mock_change_plan:1"}, list(diffs)
        c3d = diffs["call_mock_change_plan:0"]
        assert c3d["relpath"].endswith("power_module.kicad_pcb")
        # The first save re-formats the whole compact sample file (~500 lines); the
        # diff is computed on canonical forms so only the move (footprint + its
        # pads/texts picking up the new rotation) shows up, and the actions run
        # sequentially so each diff carries exactly its own change.
        for d in diffs.values():
            assert d["normalized"] is True
            assert d["additions"] == d["deletions"] <= 8, (d["tool_call_id"], d["additions"], d["deletions"])
        added_c3 = [line["s"] for h in c3d["hunks"] for line in h["lines"] if line["t"] == "+"]
        assert any("(at 132.3 71.7 270)" in s for s in added_c3), added_c3
        assert not any("(at 130 71.7 270)" in s for s in added_c3), "C4's move leaked into C3's diff"
        starts = {e["id"]: e for e in events if e["type"] == "tool_call_start"}
        assert starts["call_mock_change_plan"]["kind"] == "harness"
        assert starts["call_mock_drc"]["kind"] == "query"
        from app.agent.streaming import messages_to_history

        history = messages_to_history((await runtime.get_state("conv2", user_id)).values["messages"])
        plan_entries = [h for h in history if h["role"] == "tool" and h["name"] == "submit_change_plan"]
        assert plan_entries and '"executed": 2' in plan_entries[-1]["content"]
        # file really changed + snapshot exists on disk
        board = Board.load(pcb)
        c3 = board.get_footprint("C3")
        assert c3 and (c3.x, c3.y, c3.rotation) == (132.3, 71.7, 270.0)
        assert not board.courtyard_overlaps()
        assert ws.list_versions(user_id, str(pcb))
        final = [e for e in events if e["type"] == "message_end"][-1]
        assert "DRC" in final["content"]
    finally:
        await runtime.stop()


@pytest.mark.asyncio
async def test_per_run_thinking_override(settings: Settings):
    """The compiled graph can switch thinking without being rebuilt."""
    user_id = "u_override"
    runtime = AgentRuntime(settings)  # fixture default is deep-thinking mock
    await runtime.start()
    try:
        ctx = AgentContext(
            user_id=user_id,
            conversation_id="conv_off",
            workspace_root=str(settings.workspace_root / user_id),
            extra={"model": "auto", "thinking": "off"},
        )
        events = [
            ev
            async for ev in stream_agent_events(
                runtime.agent,
                input_payload={"messages": [{"role": "user", "content": "你好"}]},
                config=runtime.thread_config("conv_off", user_id),
                context=ctx,
            )
        ]
        assert not any(e["type"] == "reasoning_delta" for e in events)
        assert any(e["type"] == "text_delta" for e in events)
    finally:
        await runtime.stop()


@pytest.mark.asyncio
async def test_path_escape_is_blocked(settings: Settings):
    from app.kicad import workspace as ws

    with pytest.raises(ws.WorkspaceError):
        ws.resolve("u1", "../../etc/passwd")
    with pytest.raises(ws.WorkspaceError):
        ws.resolve("u1", "C:/Windows/system.ini")
