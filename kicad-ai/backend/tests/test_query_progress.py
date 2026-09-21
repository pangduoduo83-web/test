"""Read-heavy planning should converge; edits must allow a fresh verification."""

import json
from types import SimpleNamespace

import pytest
from langchain_core.messages import AIMessage, HumanMessage, SystemMessage, ToolMessage

from app.agent.context import AgentContext
from app.agent.middleware import (
    KiCadPolicyMiddleware,
    QUERY_PROGRESS_MARKER,
    _prepare_model_request,
    _successful_design_queries,
    analyze_repeats,
    detect_repeat,
)
from app.agent.prompts import build_system_prompt


def call(name, index, args=None, payload=None, status="success"):
    tc = {"name": name, "id": str(index), "args": args or {}, "type": "tool_call"}
    return [AIMessage(content="", tool_calls=[tc]),
            ToolMessage(name=name, tool_call_id=str(index), status=status,
                        content=json.dumps(payload if payload is not None else {"success": True}))]


def reads(count=8):
    messages = [HumanMessage(content="给当前原理图添加指示灯")]
    for i in range(count):
        messages += call("search_symbols", i, {"query": f"LED-{i}"})
    return messages


class Request:
    def __init__(self, messages):
        self.messages = messages
        self.tools = []
        self.runtime = SimpleNamespace(context=AgentContext(user_id="u1"))
        self.system_message = SystemMessage(content=build_system_prompt())

    def override(self, **kwargs):
        new = Request(self.messages)
        new.__dict__.update(self.__dict__)
        new.__dict__.update(kwargs)
        return new


def test_varying_queries_get_progress_guidance_without_forcing_edits():
    assert QUERY_PROGRESS_MARKER not in _prepare_model_request(Request(reads(3))).system_message.content
    request = Request(reads())
    out = _prepare_model_request(request)
    assert "已完成 8 次" in out.system_message.content
    assert "submit_change_plan" in out.system_message.content
    assert "只读咨询保持只读" in out.system_message.content
    assert out.messages == request.messages and out.tools == request.tools
    # Re-entering model middleware must not accumulate stale reminders.
    again = _prepare_model_request(out)
    assert again.system_message.content.count(QUERY_PROGRESS_MARKER) == 1


def test_framework_text_blocks_get_context_and_progress_too():
    request = Request(reads())
    request.system_message = SystemMessage(content=[
        {"type": "text", "text": build_system_prompt()},
        {"type": "text", "text": "Extra framework instructions"},
    ], id="system-1")
    out = _prepare_model_request(request).system_message
    assert QUERY_PROGRESS_MARKER in out.text
    assert "Extra framework instructions" in out.text
    assert "# 当前上下文" in out.text
    assert out.id == "system-1"


@pytest.mark.parametrize("payload,status", [
    ({"success": True, "executed": 2}, "success"),
    ({"success": False, "executed": 1, "failed": 1}, "error"),
])
def test_executed_plan_clears_query_phase_even_after_partial_failure(payload, status):
    messages = reads() + call("submit_change_plan", "plan", payload=payload, status=status)
    out = _prepare_model_request(Request(messages))
    assert QUERY_PROGRESS_MARKER not in out.system_message.content
    assert detect_repeat(messages, {"name": "search_symbols", "args": {"query": "LED-0"}, "id": "fresh"}) == (0, False)


@pytest.mark.parametrize("payload", [
    {"success": False, "pending_approval": True},
    {"success": False, "executed": 0},
    {"success": True},
])
def test_unexecuted_plan_does_not_erase_repeated_reads(payload):
    messages = reads() + call("submit_change_plan", "plan", payload=payload)
    assert _successful_design_queries(messages) == 8


def test_required_recheck_runs_after_edit_but_stalled_reads_remain_blocked():
    messages = [HumanMessage(content="移动 C3 后复查")]
    messages += call("run_drc_check", 1) + call("run_drc_check", 2)
    query = {"name": "run_drc_check", "args": {}, "id": "verify"}
    request = SimpleNamespace(tool_call=query, state={"messages": messages})
    policy = KiCadPolicyMiddleware()
    assert policy._loop_guard(request)["loop_guard"]
    messages += call("submit_change_plan", 3, payload={"success": True, "executed": 1})
    assert policy._loop_guard(request) is None
    messages += call("run_drc_check", 4) + call("run_drc_check", 5)
    assert policy._loop_guard(request)["loop_guard"]


@pytest.mark.parametrize("name", ["switch_project", "create_project", "restore_file_version", "move_component"])
def test_successful_state_changes_start_fresh_queries(name):
    assert _successful_design_queries(reads() + call(name, "edit")) == 0
    assert _successful_design_queries(reads() + call(name, "fail", payload={"success": False})) == 8


def test_new_user_turn_and_failed_queries_are_not_counted():
    assert _successful_design_queries(reads() + [HumanMessage(content="现在只解释这个电路")]) == 0
    messages = reads(3) + call("get_symbol_pins", "failed", status="error")
    messages += call("get_symbol_pins", "missing", payload={"success": False})
    assert _successful_design_queries(messages) == 3
    assert _successful_design_queries([]) == 0


def test_tool_call_ids_resolve_results_without_names():
    messages = reads()
    for message in messages:
        if isinstance(message, ToolMessage):
            message.name = None
    assert _successful_design_queries(messages) == 8
    messages += call("submit_change_plan", "plan", payload={"executed": 1})
    messages[-1].name = None
    assert _successful_design_queries(messages) == 0


def test_edit_guard_is_not_reset_by_a_different_edit():
    messages = call("move_component", 1, {"reference": "R1", "x": 1})
    messages += call("move_component", 2, {"reference": "R1", "x": 1})
    messages += call("move_component", 3, {"reference": "R2", "x": 2})
    assert detect_repeat(messages, {"name": "move_component", "args": {"reference": "R1", "x": 1}, "id": "4"}) == (2, True)


def test_query_turn_ceiling_survives_edits():
    messages = reads() + call("submit_change_plan", "plan", payload={"success": True, "executed": 1})
    assert analyze_repeats(messages, {"name": "search_symbols", "args": {"query": "LED-0"}, "id": "next"}) == (0, False, 0, 8)


@pytest.mark.asyncio
async def test_real_agent_delivers_guidance_and_clears_it_after_execution(tmp_path, monkeypatch):
    """Exercise real middleware, tools, approval checkpoint and PCB write offline."""
    import shutil
    from pathlib import Path

    from langgraph.types import Command

    from app.agent.change_plan import validate_change_plan
    from app.agent.factory import AgentRuntime
    from app.agent.llm import MockKiCadChatModel
    from app.agent.streaming import stream_agent_events
    from app.config import Settings

    observed = []

    class QueryHeavyModel(MockKiCadChatModel):
        def _plan(self, messages):
            results = [m for m in messages if isinstance(m, ToolMessage)]
            reads_done = [m for m in results if m.name == "get_footprint"]
            system = "\n".join(m.text for m in messages if isinstance(m, SystemMessage))
            plan_done = any(m.name == "submit_change_plan" for m in results)
            observed.append((len(reads_done), plan_done, QUERY_PROGRESS_MARKER in system))
            if len(reads_done) < 8:
                ref = ["J1", "J2", "U1", "C1", "C2", "C3", "C4", "R1"][len(reads_done)]
                name, args = "get_footprint", {"reference": ref}
            elif not plan_done:
                assert QUERY_PROGRESS_MARKER in system
                name, args = "submit_change_plan", {
                    "title": "移动 C3", "summary": "将 C3 靠近芯片", "actions": [{
                        "tool": "set_footprint_position",
                        "args": {"reference": "C3", "x": 132.3, "y": 71.7, "rotation": 270},
                        "summary": "移动 C3",
                    }],
                }
            elif not any(m.name == "run_drc_check" for m in results):
                assert QUERY_PROGRESS_MARKER not in system
                name, args = "run_drc_check", {}
            else:
                return AIMessage(content="已移动 C3 并完成检查。")
            return AIMessage(content="", tool_calls=[{
                "name": name, "args": args, "id": f"progress-{len(results)}", "type": "tool_call",
            }])

    monkeypatch.setattr("app.agent.factory.build_chat_model", lambda settings: QueryHeavyModel())
    settings = Settings(llm_provider="mock", llm_model="mock-kicad", kcaa_mode="off",
                        enable_subagents=False, data_dir=tmp_path / "data",
                        workspace_root=tmp_path / "workspaces")
    settings.ensure_dirs()
    user_root = settings.workspace_root / "progress-user"
    project = user_root / "projects" / "power_module"
    shutil.copytree(Path(__file__).resolve().parent.parent / "samples" / "power_module", project)
    pcb = project / "power_module.kicad_pcb"
    before = pcb.read_bytes()
    ctx = AgentContext(user_id="progress-user", conversation_id="progress", pcb_path=str(pcb),
                       workspace_root=str(user_root), project_name="power_module")
    runtime = AgentRuntime(settings)
    await runtime.start()
    try:
        config = runtime.thread_config("progress", ctx.user_id)
        events = [e async for e in stream_agent_events(runtime.agent,
            input_payload={"messages": [HumanMessage(content="移动 C3 并检查")]}, config=config, context=ctx)]
        assert not [e for e in events if e["type"] == "error"]
        interrupt = next(e for e in events if e["type"] == "interrupt")
        action = interrupt["interrupts"][0]["value"]["action_requests"][0]
        assert pcb.read_bytes() == before
        approved, error = validate_change_plan(action["args"])
        assert error is None
        ctx.extra["approved_change_plan"] = approved
        events += [e async for e in stream_agent_events(runtime.agent,
            input_payload=Command(resume={"decisions": [{"type": "approve"}]}), config=config, context=ctx)]
        assert not [e for e in events if e["type"] == "error"]
        results = {e["name"]: e for e in events if e["type"] == "tool_result"}
        assert results["submit_change_plan"]["data"]["executed"] == 1
        assert results["run_drc_check"]["ok"]
        assert pcb.read_bytes() != before
        assert (8, False, True) in observed and (8, True, False) in observed
    finally:
        await runtime.stop()
