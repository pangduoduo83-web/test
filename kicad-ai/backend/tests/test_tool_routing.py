import json
from types import SimpleNamespace

import pytest
from langchain_core.messages import AIMessage, HumanMessage, SystemMessage, ToolMessage

from app.agent.context import AgentContext
from app.agent.middleware import KiCadPolicyMiddleware, _prepare_model_request
from app.agent.tool_routing import design_scopes, expand_design_tools, select_design_tools
from app.agent.tools.registry import TOOL_POLICIES


class Request:
    def __init__(self, text, ctx=None, names=None):
        self.messages = [HumanMessage(content=text)]
        self.runtime = SimpleNamespace(context=ctx or AgentContext(user_id="u1"))
        self.tools = [SimpleNamespace(name=n) for n in (names or [*TOOL_POLICIES, "read_file", "future_tool"])]
        self.system_message = SystemMessage(content="Rules")

    def override(self, **updates):
        new = object.__new__(Request)
        new.__dict__.update(self.__dict__)
        new.__dict__.update(updates)
        return new


def names(request):
    return {t.name for t in request.tools}


@pytest.mark.parametrize("text,expected", [
    ("在原理图加一个 LED", {"schematic"}),
    ("添加一个 LED 指示灯", {"schematic"}),
    ("优化 PCB 布局并做 DRC", {"pcb"}),
    ("检查 schematic 的 ERC", {"schematic"}),
    ("原理图同步到 PCB", {"pcb", "schematic"}),
    ("全面检查这个工程", {"pcb", "schematic"}),
    ("帮我看一下", {"pcb", "schematic"}),
])
def test_intents(text, expected):
    assert design_scopes([HumanMessage(content=text)], None) == expected


def test_domain_schemas_keep_plans_and_shared_capabilities():
    request = Request("画原理图")
    out, hint = select_design_tools(request)
    available = names(out)
    assert {"submit_change_plan", "expand_design_tools", "connect_pins_with_wire",
            "apply_circuit_template", "run_erc_check", "read_file", "search_footprints",
            "restore_file_version", "future_tool"} <= available
    assert not {"pcb_add_vias", "list_tracks", "set_footprint_position", "run_drc_check"} & available
    assert len(out.tools) < len(request.tools) and hint
    assert out.messages is request.messages
    assert "run_drc_check" in names(request)  # no mutation of shared catalog
    pcb, _ = select_design_tools(Request("移动 PCB 元件"))
    assert "set_footprint_position" in names(pcb)
    assert "move_component" not in names(pcb)


def expansion(scope="pcb", success=True):
    return [
        AIMessage(content="", tool_calls=[{"name": "expand_design_tools", "args": {"scope": scope}, "id": "e"}]),
        ToolMessage(content=json.dumps({"success": success, "scope": scope}), tool_call_id="e"),
    ]


def test_expansion_survives_calls_but_not_new_user_turn_or_other_conversation():
    request = Request("检查原理图")
    request.messages += expansion()
    assert "run_drc_check" in names(select_design_tools(request)[0])
    request.messages += [HumanMessage(content="继续检查原理图")]
    assert "run_drc_check" not in names(select_design_tools(request)[0])
    assert "run_drc_check" not in names(select_design_tools(Request("画原理图"))[0])
    failed = Request("检查原理图")
    failed.messages += expansion(success=False)
    assert "run_drc_check" not in names(select_design_tools(failed)[0])


def test_context_followup_and_permissions():
    ctx = AgentContext(user_id="u1", pcb_path="board.kicad_pcb", schematic_path="board.kicad_sch",
                       selection={"mode": "sch"})
    assert design_scopes([HumanMessage(content="移动这些元件")], ctx) == {"schematic"}
    assert design_scopes([HumanMessage(content="画原理图"), HumanMessage(content="继续")], ctx) == {"schematic"}
    assert design_scopes([HumanMessage(content="画原理图"), HumanMessage(content="继续执行上面的任务，从刚才中断的地方接着做；先用一句话说明接下来要做什么。")], ctx) == {"schematic"}
    request = Request("画原理图", ctx)
    request.messages += expansion("all")
    prepared = _prepare_model_request(request)
    assert not {"add_skill", "append_to_skill", "delete_skill"} & names(prepared)
    specialist = Request("原理图", names=["get_board_info", "run_drc_check"])
    assert select_design_tools(specialist)[0] is specialist


@pytest.mark.asyncio
async def test_framework_receives_small_catalog_and_expands_next_call():
    from langchain.agents import create_agent
    from langchain.tools import tool
    from app.agent.llm import MockKiCadChatModel

    @tool
    def list_schematic_symbols() -> str:
        """Read schematic symbols."""
        return "{}"

    @tool
    def run_drc_check() -> str:
        """Check PCB rules."""
        return "{}"

    observed = []

    class RoutedModel(MockKiCadChatModel):
        def _plan(self, messages):
            observed.append(set(self.tool_names))
            if not any(isinstance(m, ToolMessage) for m in messages):
                assert "run_drc_check" not in self.tool_names
                assert "list_schematic_symbols" in self.tool_names
                return AIMessage(content="", tool_calls=[{
                    "name": "expand_design_tools", "args": {"scope": "pcb"}, "id": "expand",
                }])
            assert "run_drc_check" in self.tool_names
            return AIMessage(content="工具已补齐")

    agent = create_agent(model=RoutedModel(), tools=[expand_design_tools, list_schematic_symbols, run_drc_check],
                         system_prompt="检查工程", middleware=[KiCadPolicyMiddleware()], context_schema=AgentContext)
    result = await agent.ainvoke({"messages": [HumanMessage(content="检查原理图")]}, context=AgentContext(user_id="u1"))
    assert result["messages"][-1].content == "工具已补齐"
    assert len(observed) == 2 and len(observed[0]) < len(observed[1])
