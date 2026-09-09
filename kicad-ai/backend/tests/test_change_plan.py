"""Batch change-plan approval: one confirmation, server-side execution."""

from __future__ import annotations

import json
from types import SimpleNamespace

import pytest

from app.agent.context import AgentContext
from app.agent.executor import plan_matches
from app.agent.middleware import KiCadPolicyMiddleware, _mutation_plan_error, _plan_signature
from app.agent.tools.native import submit_change_plan
from app.agent.tools.registry import get_policy
from app.api.chat import _approved_change_plan


def _request(context: AgentContext, messages=None, tool_call_id="current-call"):
    return SimpleNamespace(
        runtime=SimpleNamespace(context=context),
        state={"messages": messages or []},
        tool_call={"id": tool_call_id},
    )


PLAN_ACTION = {
    "tool": "set_footprint_position",
    "args": {"reference": "C3", "x": 132.0, "y": 71.7},
    "summary": "移动 C3",
}


@pytest.mark.asyncio
async def test_submit_change_plan_refuses_to_run_without_approval():
    assert submit_change_plan.name == "submit_change_plan"
    policy = get_policy("submit_change_plan")
    assert policy.confirm is True and policy.kind == "harness"

    result = await submit_change_plan.coroutine(
        title="移动去耦电容",
        summary="把 C3 移到 U1 附近并复查 DRC",
        actions=[PLAN_ACTION],
        scope=["C3", "U1"],
        verification=[{"tool": "run_drc_check", "args": {}, "summary": "运行 DRC"}],
        runtime=SimpleNamespace(context=AgentContext(user_id="u1"), tool_call_id="plan-1"),
    )
    payload = json.loads(result)
    assert payload["success"] is False and payload["pending_approval"] is True
    assert payload["actions"][0]["tool"] == "set_footprint_position"

    # A different approved plan (the user saw something else) must not execute either.
    other = AgentContext(
        user_id="u1",
        extra={"approved_change_plan": {"actions": [{**PLAN_ACTION, "args": {"reference": "C3", "x": 140.0, "y": 71.7}}]}},
    )
    result = await submit_change_plan.coroutine(
        title="移动去耦电容",
        summary="",
        actions=[PLAN_ACTION],
        runtime=SimpleNamespace(context=other, tool_call_id="plan-2"),
    )
    assert json.loads(result)["pending_approval"] is True


def test_plan_matching_ignores_paths_and_float_formatting():
    approved = {"actions": [{"tool": "set_footprint_position", "args": {"reference": "C3", "x": 132, "y": 71.7, "pcb_path": "server.kicad_pcb"}}]}
    assert plan_matches(approved, {"actions": [PLAN_ACTION]})
    assert not plan_matches(approved, {"actions": [PLAN_ACTION, PLAN_ACTION]})
    assert not plan_matches(None, {"actions": [PLAN_ACTION]})
    assert not plan_matches(approved, {"actions": []})


def test_model_issued_mutations_are_always_routed_through_plans():
    context = AgentContext(user_id="u1", extra={"require_change_plan": True})
    request = _request(context)
    error = _mutation_plan_error(request, "set_footprint_position", {"reference": "C3", "x": 132, "y": 71.7})
    assert error and "submit_change_plan" in error

    # Even with an approved plan in flight the model must not re-issue the actions:
    # the server already executed them inside submit_change_plan.
    context.extra["approved_change_plan"] = {"actions": [PLAN_ACTION]}
    error = _mutation_plan_error(request, "set_footprint_position", PLAN_ACTION["args"])
    assert error and "自动执行" in error

    # Without the per-run flag (e.g. unit invocations) nothing is enforced.
    assert _mutation_plan_error(_request(AgentContext(user_id="u1")), "set_footprint_position", {}) is None


def test_plan_signature_normalizes_numbers_and_ignores_server_paths():
    left = _plan_signature("move_component", {"reference": "R1", "x": 10, "schematic_path": "a"})
    right = _plan_signature("move_component", {"x": 10.0, "reference": "R1", "schematic_path": "b"})
    assert left == right


def test_generic_file_tools_cannot_bypass_design_plan():
    context = AgentContext(user_id="u1")
    request = SimpleNamespace(
        runtime=SimpleNamespace(context=context),
        state={"messages": []},
        tool_call={
            "id": "generic-edit",
            "name": "edit_file",
            "args": {"file_path": "/workspace/board.kicad_pcb", "old_string": "a", "new_string": "b"},
        },
    )
    _, error, snapshots = KiCadPolicyMiddleware()._prepare(request)
    assert error and "禁止使用通用 edit_file" in error["error"]
    assert snapshots == []


def test_resume_uses_only_server_checkpoint_plan():
    server_plan = {
        "title": "server-owned",
        "actions": [{"tool": "set_footprint_position", "args": {"reference": "C3", "x": 132}}],
    }
    state = SimpleNamespace(
        tasks=[
            SimpleNamespace(
                interrupts=[
                    SimpleNamespace(
                        value={
                            "action_requests": [
                                {"name": "submit_change_plan", "args": server_plan}
                            ]
                        }
                    )
                ]
            )
        ]
    )
    approved = _approved_change_plan(state, [{"type": "approve"}])
    assert approved is not None
    assert approved["title"] == "server-owned"
    assert approved["actions"][0]["args"] == server_plan["actions"][0]["args"]
    assert _approved_change_plan(state, [{"type": "reject"}]) is None

    state.tasks[0].interrupts[0].value["action_requests"][0]["args"] = {
        "title": "invalid",
        "actions": [{"tool": "run_drc_check", "args": {}}],
    }
    assert _approved_change_plan(state, [{"type": "approve"}]) is None
