"""Chat-model factory.

Supports OpenAI, Anthropic, any OpenAI-compatible endpoint (DeepSeek, Qwen,
Moonshot, Ollama, vLLM …) and a deterministic ``mock`` provider used for
offline demos and tests.

OpenAI-compatible *reasoning* endpoints (Alibaba Bailian / DashScope, DeepSeek,
GLM, Moonshot, vLLM, Ollama) go through :class:`ReasoningChatOpenAI`, because
``langchain_openai.ChatOpenAI`` deliberately drops every non-OpenAI response
field — including the ``reasoning_content`` these vendors stream — and offers
no way to send their thinking switches.
"""

from __future__ import annotations

import json
from collections.abc import Iterator, Sequence
from typing import Any

from langchain_core.callbacks import CallbackManagerForLLMRun
from langchain_core.language_models import BaseChatModel, LanguageModelInput
from langchain_core.messages import (
    AIMessage,
    AIMessageChunk,
    BaseMessage,
    HumanMessage,
    ToolMessage,
)
from langchain_core.outputs import ChatGeneration, ChatGenerationChunk, ChatResult
from langchain_openai import ChatOpenAI

from app.agent.models import (
    capabilities_for,
    detect_thinking_style,
    echo_policy,
    normalize_thinking_mode,
    resolve_thinking,
    uses_reasoning_subclass,
)
from app.config import Settings

MOCK_REASONING = (
    "用户想了解当前工程的状态。我需要先读取板级信息和封装列表，"
    "再根据结果决定是否需要进一步的布局或 DRC 操作。"
)


# ---------------------------------------------------------------------------
# Reasoning-aware OpenAI-compatible chat model
# ---------------------------------------------------------------------------
class ReasoningChatOpenAI(ChatOpenAI):
    """``ChatOpenAI`` + vendor thinking switches + ``reasoning_content`` support.

    * Thinking is enabled/disabled/budgeted through ``extra_body`` in the vendor
      dialect selected by ``thinking_style`` (see ``app.agent.models``).
    * Streamed ``delta.reasoning_content`` (or ``delta.reasoning``) is captured in
      ``AIMessageChunk.additional_kwargs["reasoning_content"]``; LangChain
      concatenates it while merging chunks, so the final ``AIMessage`` carries the
      full reasoning text for the UI and for persistence.
    * Stored reasoning is echoed back in the request per ``echo_reasoning``
      (see :func:`app.agent.models.echo_policy`): ``turn`` sends the current
      agent turn only, which keeps the chain of thought across tool calls without
      paying for the whole conversation's reasoning; ``all`` sends every
      assistant message's reasoning (empty string when unknown), which DeepSeek
      requires in thinking mode or it rejects the request with HTTP 400.
    """

    thinking_style: str = "bailian"
    enable_thinking: bool | None = None
    thinking_budget: int | None = None
    preserve_thinking: bool | None = None
    echo_reasoning: str = "turn"  # turn | all | none

    @property
    def _llm_type(self) -> str:
        return "openai-chat-reasoning"

    # ---- request ------------------------------------------------------------
    def _thinking_body(self) -> dict[str, Any]:
        style = self.thinking_style
        body: dict[str, Any] = {}
        if self.enable_thinking is None and self.thinking_budget is None and not self.preserve_thinking:
            return body
        if style == "bailian":
            if self.enable_thinking is not None:
                body["enable_thinking"] = self.enable_thinking
            if self.thinking_budget is not None and self.enable_thinking is not False:
                body["thinking_budget"] = int(self.thinking_budget)
            if self.preserve_thinking is not None:
                body["preserve_thinking"] = self.preserve_thinking
        elif style == "thinking_type":
            thinking: dict[str, Any] = {}
            if self.enable_thinking is not None:
                thinking["type"] = "enabled" if self.enable_thinking else "disabled"
            if self.preserve_thinking and (self.model_name or "").lower().startswith("glm"):
                thinking["clear_thinking"] = False
            if thinking:
                body["thinking"] = thinking
        elif style == "chat_template_kwargs":
            if self.enable_thinking is not None:
                body["chat_template_kwargs"] = {"enable_thinking": self.enable_thinking}
        elif style == "ollama":
            if self.enable_thinking is not None:
                body["think"] = self.enable_thinking
        return body

    def _get_request_payload(self, input_: LanguageModelInput, *, stop: list[str] | None = None, **kwargs: Any) -> dict:
        payload = super()._get_request_payload(input_, stop=stop, **kwargs)
        thinking = self._thinking_body()
        if thinking:
            payload["extra_body"] = {**(payload.get("extra_body") or {}), **thinking}
        wire = payload.get("messages")
        if not wire or self.echo_reasoning == "none" or self.enable_thinking is False:
            return payload
        messages = self._convert_input(input_).to_messages()
        if len(messages) != len(wire):
            return payload
        start = 0
        if self.echo_reasoning == "turn":
            for i in range(len(messages) - 1, -1, -1):
                if isinstance(messages[i], HumanMessage):
                    start = i
                    break
        for i in range(start, len(messages)):
            m = messages[i]
            if isinstance(m, AIMessage) and wire[i].get("role") == "assistant":
                reasoning = m.additional_kwargs.get("reasoning_content")
                if isinstance(reasoning, str) and reasoning:
                    wire[i]["reasoning_content"] = reasoning
                elif self.echo_reasoning == "all":
                    # history produced without thinking: DeepSeek still insists on the field
                    wire[i]["reasoning_content"] = ""
        return payload

    # ---- response -----------------------------------------------------------
    @staticmethod
    def _reasoning_from(obj: Any) -> str | None:
        if not isinstance(obj, dict):
            return None
        for key in ("reasoning_content", "reasoning"):
            val = obj.get(key)
            if isinstance(val, str) and val:
                return val
        return None

    def _convert_chunk_to_generation_chunk(self, chunk: dict, default_chunk_class: type, base_generation_info: dict | None) -> ChatGenerationChunk | None:
        generation_chunk = super()._convert_chunk_to_generation_chunk(chunk, default_chunk_class, base_generation_info)
        if generation_chunk is None:
            return None
        choices = chunk.get("choices") or chunk.get("chunk", {}).get("choices") or []
        if choices and isinstance(generation_chunk.message, AIMessageChunk):
            reasoning = self._reasoning_from(choices[0].get("delta") or {})
            if reasoning:
                generation_chunk.message.additional_kwargs["reasoning_content"] = reasoning
        return generation_chunk

    def _create_chat_result(self, response: Any, generation_info: dict | None = None) -> ChatResult:
        result = super()._create_chat_result(response, generation_info)
        response_dict = response if isinstance(response, dict) else getattr(response, "model_dump", lambda **_: {})(warnings=False)
        choices = (response_dict or {}).get("choices") or []
        for gen, choice in zip(result.generations, choices, strict=False):
            reasoning = self._reasoning_from((choice or {}).get("message") or {})
            if reasoning and isinstance(gen.message, AIMessage):
                gen.message.additional_kwargs["reasoning_content"] = reasoning
        return result


class MockKiCadChatModel(BaseChatModel):
    """Scripted model: calls a couple of read-only KiCad tools, then summarises.

    Useful to exercise the full streaming / tool-call / persistence pipeline
    without an API key. Never use in production.

    Behaves like a hybrid-thinking model: with ``enable_thinking`` it streams a
    short ``reasoning_content`` before the first tool round, so the reasoning
    UI / persistence path can be exercised offline too.
    """

    tool_names: list[str] = []
    enable_thinking: bool = True
    # Artificial latency per model call (demo realism; lets reconnect flows be exercised by hand).
    delay_ms: int = 0

    @property
    def _llm_type(self) -> str:
        return "mock-kicad"

    async def _agenerate(self, messages, stop=None, run_manager=None, **kwargs):
        if self.delay_ms > 0:
            import asyncio

            await asyncio.sleep(self.delay_ms / 1000)
        return self._generate(messages, stop, run_manager, **kwargs)

    async def _astream(self, messages, stop=None, run_manager=None, **kwargs):
        if self.delay_ms > 0:
            import asyncio

            await asyncio.sleep(self.delay_ms / 1000)
        for chunk in self._stream(messages, stop, run_manager, **kwargs):
            yield chunk

    def bind_tools(self, tools: Sequence[Any], **kwargs: Any):  # type: ignore[override]
        names: list[str] = []
        for t in tools:
            name = getattr(t, "name", None) or (t.get("name") if isinstance(t, dict) else None)
            if name is None and isinstance(t, dict) and "function" in t:
                name = t["function"].get("name")
            if name:
                names.append(name)
        return self.model_copy(update={"tool_names": names})

    def _reasoning_for(self, messages: list[BaseMessage]) -> str:
        """Reasoning only on the first model call of a turn (like real hybrid models)."""
        if not self.enable_thinking:
            return ""
        idx = max((i for i, m in enumerate(messages) if isinstance(m, HumanMessage)), default=-1)
        return "" if any(isinstance(m, ToolMessage) for m in messages[idx + 1 :]) else MOCK_REASONING

    @staticmethod
    def _parse(m: ToolMessage) -> Any:
        body = m.content if isinstance(m.content, str) else json.dumps(m.content, ensure_ascii=False)
        try:
            return json.loads(body)
        except (TypeError, json.JSONDecodeError):
            return body

    def _plan(self, messages: list[BaseMessage]) -> AIMessage:
        idx = max((i for i, m in enumerate(messages) if isinstance(m, HumanMessage)), default=-1)
        request = messages[idx].content if idx >= 0 else ""
        request = request if isinstance(request, str) else str(request)
        turn = messages[idx + 1 :]
        results = {m.name: self._parse(m) for m in turn if isinstance(m, ToolMessage)}
        has = lambda *names: all(n in self.tool_names for n in names)  # noqa: E731
        wants_layout = any(k in request for k in ("优化", "电容", "移动", "布局", "DRC", "drc"))

        # Phase 1 — read the board state
        if not results and has("get_board_info", "list_footprints"):
            calls = [
                {"name": "get_board_info", "args": {}, "id": "call_mock_read_0", "type": "tool_call"},
                {"name": "list_footprints", "args": {}, "id": "call_mock_read_1", "type": "tool_call"},
            ]
            plan = "好的，我会帮您优化电源模块布局并检查 DRC。\n\n我将按以下步骤进行：\n1. 分析当前电源模块的元器件分布\n2. 识别芯片电源引脚位置\n3. 移动电容靠近电源引脚\n4. 检查 DRC 规则\n5. 提供优化建议" if wants_layout else "好的，我先读取当前 PCB 的板级信息和封装列表，再给出建议。"
            return AIMessage(content=plan, tool_calls=calls)

        # Phase 2 — plan the cap moves; after approval the server executes them,
        # so the only follow-up is the read-only verification (DRC).
        board = results.get("get_board_info")
        plan_result = results.get("submit_change_plan")
        plan_executed = isinstance(plan_result, dict) and plan_result.get("approved") is True
        if (
            wants_layout
            and isinstance(board, dict)
            and board.get("success")
            and "run_drc_check" not in results
            and has("set_footprint_position", "run_drc_check")
        ):
            fps = {f["reference"] for f in (results.get("list_footprints") or {}).get("footprints", [])}
            planned_actions = []
            if {"U1", "C3"} <= fps:
                planned_actions.append(
                    {
                        "tool": "set_footprint_position",
                        "args": {"reference": "C3", "x": 132.3, "y": 71.7, "rotation": 270},
                        "summary": "把 C3 移到 U1 电源引脚附近",
                        "reason": "缩短去耦回路",
                    }
                )
            if {"U1", "C4"} <= fps:
                planned_actions.append(
                    {
                        "tool": "set_footprint_position",
                        "args": {"reference": "C4", "x": 130.0, "y": 71.7, "rotation": 270},
                        "summary": "把 C4 移到 U1 电源引脚附近",
                        "reason": "缩短去耦回路",
                    }
                )
            if has("submit_change_plan") and not plan_executed:
                submitted = any(
                    isinstance(message, ToolMessage) and message.name == "submit_change_plan"
                    for message in turn
                )
                if submitted:
                    return AIMessage(content="已取消本批布局修改，工程文件保持不变。")
                return AIMessage(
                    content="分析完成。我先提交整批修改计划供您确认；批准前不会改动工程文件。",
                    tool_calls=[
                        {
                            "name": "submit_change_plan",
                            "args": {
                                "title": "优化 U1 去耦电容布局",
                                "summary": "移动 C3、C4 靠近 U1，然后运行 DRC 检查。",
                                "scope": ["U1", "C3", "C4"],
                                "actions": planned_actions,
                                "verification": [
                                    {
                                        "tool": "run_drc_check",
                                        "args": {},
                                        "summary": "运行 DRC 检查",
                                    }
                                ],
                            },
                            "id": "call_mock_change_plan",
                            "type": "tool_call",
                        }
                    ],
                )
            return AIMessage(
                content="计划已由系统执行：C3、C4 已移到 U1 对应电源引脚 2 mm 以内。现在运行 DRC 复查。",
                tool_calls=[{"name": "run_drc_check", "args": {}, "id": "call_mock_drc", "type": "tool_call"}],
            )

        # Phase 3 — summarise
        if not results:
            return AIMessage(
                content="我是 KiCad AI 助手（当前为离线演示模型）。请配置 LLM_API_KEY 以启用真实模型；"
                "你可以先在右侧选择或导入工程，然后让我读取板级信息或优化布局。"
            )
        lines: list[str] = []
        drc = results.get("run_drc_check")
        moved = [
            entry.get("result")
            for entry in (plan_result.get("results") or [] if plan_executed else [])
            if isinstance(entry, dict) and entry.get("tool") == "set_footprint_position" and entry.get("success")
        ]
        if isinstance(drc, dict):
            lines.append("布局优化已完成，DRC 检查结果如下：" if moved else "DRC 检查结果如下：")
            lines.append("")
            lines.append("**优化摘要**：" if moved else "**摘要**：")
            for d in moved:
                if isinstance(d, dict) and d.get("success"):
                    b, a = d["before"], d["after"]
                    lines.append(f"- {d['reference']}：({b['x']}, {b['y']}) → ({a['x']}, {a['y']})，旋转 {a['rotation']}°（距电源引脚 < 2 mm）")
            lines.append(f"- DRC：{'通过' if drc.get('passed') else '发现 ' + str(drc.get('error_count')) + ' 个错误'}，"
                         f"警告 {drc.get('warning_count', 0)}，未连接 {drc.get('unconnected_count', 0)}（引擎 {drc.get('engine')}）")
            if drc.get("unconnected_count"):
                lines.append("- 未连接项属于尚未布线的飞线，建议在布线阶段处理")
            lines.append("")
            lines.append("请查看右侧的布局预览，您可以随时告诉我需要进一步调整的地方。")
            return AIMessage(content="\n".join(lines))

        lines.append("已完成读取，下面是摘要：")
        lines.append("")
        if isinstance(board, dict) and board.get("success"):
            lines.append(f"- 板子尺寸 {board.get('board_size_mm')} mm，{board.get('footprint_count')} 个封装，{board.get('net_count')} 个网络，{board.get('track_count')} 段走线。")
            if board.get("unrouted_nets"):
                lines.append(f"- 未布线网络：{', '.join(board['unrouted_nets'])}")
        fl = results.get("list_footprints")
        if isinstance(fl, dict) and fl.get("footprints"):
            lines.append("- 封装：" + ", ".join(f["reference"] for f in fl["footprints"][:12]))
        for name, data in results.items():
            if isinstance(data, dict) and data.get("success") is False:
                lines.append(f"- {name} 失败：{data.get('error')}")
        lines += ["", "**建议**：将去耦电容移至芯片电源引脚 2 mm 内，然后运行 DRC 检查。"]
        return AIMessage(content="\n".join(lines))

    def _generate(
        self,
        messages: list[BaseMessage],
        stop: list[str] | None = None,
        run_manager: CallbackManagerForLLMRun | None = None,
        **kwargs: Any,
    ) -> ChatResult:
        msg = self._plan(messages)
        msg.usage_metadata = {"input_tokens": 600, "output_tokens": 120, "total_tokens": 720}
        reasoning = self._reasoning_for(messages)
        if reasoning:
            msg.additional_kwargs["reasoning_content"] = reasoning
        return ChatResult(generations=[ChatGeneration(message=msg)])

    def _stream(
        self,
        messages: list[BaseMessage],
        stop: list[str] | None = None,
        run_manager: CallbackManagerForLLMRun | None = None,
        **kwargs: Any,
    ) -> Iterator[ChatGenerationChunk]:
        msg = self._plan(messages)
        reasoning = self._reasoning_for(messages)
        for i in range(0, len(reasoning), 8):
            yield ChatGenerationChunk(message=AIMessageChunk(content="", additional_kwargs={"reasoning_content": reasoning[i : i + 8]}))
        text = msg.content if isinstance(msg.content, str) else ""
        for i in range(0, len(text), 6):
            piece = text[i : i + 6]
            yield ChatGenerationChunk(message=AIMessageChunk(content=piece))
        if msg.tool_calls:
            chunk = AIMessageChunk(
                content="",
                tool_call_chunks=[
                    {"name": tc["name"], "args": json.dumps(tc["args"]), "id": tc["id"], "index": i, "type": "tool_call_chunk"}
                    for i, tc in enumerate(msg.tool_calls)
                ],
            )
            yield ChatGenerationChunk(message=chunk)
        yield ChatGenerationChunk(
            message=AIMessageChunk(
                content="", usage_metadata={"input_tokens": 600, "output_tokens": 120, "total_tokens": 720}
            )
        )


async def test_chat_model(settings: Settings, timeout: float = 40.0) -> dict[str, Any]:
    """Build a model from *settings* and send a one-token probe (admin console)."""
    import asyncio
    import time

    from langchain_core.messages import HumanMessage

    started = time.perf_counter()
    thinking = normalize_thinking_mode(settings.llm_thinking)
    label = f"{settings.llm_provider}:{settings.llm_model}"
    try:
        model = build_chat_model(settings)
        reply = await asyncio.wait_for(
            model.ainvoke([HumanMessage(content="Reply with the single word: pong")]), timeout=timeout
        )
        text = reply.content if isinstance(reply.content, str) else str(reply.content)
        reasoning = reply.additional_kwargs.get("reasoning_content") if isinstance(reply, AIMessage) else None
        return {
            "ok": True,
            "latency_ms": int((time.perf_counter() - started) * 1000),
            "reply": text[:200],
            "model": label,
            "thinking": thinking,
            "reasoning_chars": len(reasoning) if isinstance(reasoning, str) else 0,
        }
    except Exception as exc:  # noqa: BLE001 — surfaced to the admin UI
        return {
            "ok": False,
            "latency_ms": int((time.perf_counter() - started) * 1000),
            "error": f"{type(exc).__name__}: {str(exc)[:400]}",
            "model": label,
            "thinking": thinking,
        }


OPENAI_COMPATIBLE_PROVIDERS = ("openai", "custom", "openai-compatible", "deepseek", "qwen", "dashscope", "moonshot", "ollama-openai")

MIN_CONTEXT_TOKENS = 16_000


def context_window_for(settings: Settings) -> int:
    """Input-token window of the configured model.

    A preset's ``context_tokens`` wins over the generic ``LLM_CONTEXT_TOKENS``
    setting, which is the operator's answer for models we do not know.
    """
    provider = (settings.llm_provider or "openai").strip().lower()
    caps = capabilities_for(settings.llm_model, provider, settings.llm_base_url)
    preset_window = caps.get("context_tokens")
    window = int(preset_window) if isinstance(preset_window, int) and preset_window > 0 else int(settings.llm_context_tokens or 0)
    return max(MIN_CONTEXT_TOKENS, window)


def ensure_model_profile(model: BaseChatModel, settings: Settings) -> BaseChatModel:
    """Make sure the model advertises ``max_input_tokens``.

    deepagents sizes its summarization middleware from ``model.profile``; for
    models LangChain does not know (every OpenAI-compatible vendor model) the
    profile is ``None`` and the fallback trigger is a fixed 170k tokens — above
    the window of most 128k models, so the history would overflow before it is
    ever summarized. With the profile set, summarization triggers at 85 % of
    the real window and keeps the last 10 %.
    """
    profile = getattr(model, "profile", None)
    if isinstance(profile, dict) and isinstance(profile.get("max_input_tokens"), int):
        return model
    window = context_window_for(settings)
    merged = {**(profile or {}), "max_input_tokens": window}
    try:
        model.profile = merged  # type: ignore[assignment]
    except (AttributeError, TypeError, ValueError):
        return model
    return model


def build_chat_model(settings: Settings) -> BaseChatModel:
    """Build the chat model described by *settings* (``llm_*`` fields).

    Per-conversation overrides are expressed by passing a ``settings.model_copy``
    with a different ``llm_model`` / ``llm_thinking`` (see ``ModelRouter``).
    """
    return ensure_model_profile(_build_chat_model(settings), settings)


def _build_chat_model(settings: Settings) -> BaseChatModel:
    provider = (settings.llm_provider or "openai").strip().lower()
    thinking = normalize_thinking_mode(settings.llm_thinking)
    if provider == "mock":
        return MockKiCadChatModel(enable_thinking=thinking != "off", delay_ms=max(0, int(settings.llm_mock_delay_ms or 0)))

    common: dict[str, Any] = {"temperature": settings.llm_temperature}
    if settings.llm_max_tokens:
        common["max_tokens"] = settings.llm_max_tokens

    if provider in OPENAI_COMPATIBLE_PROVIDERS:
        kwargs: dict[str, Any] = {"model": settings.llm_model, "streaming": True, **common}
        if settings.llm_api_key:
            kwargs["api_key"] = settings.llm_api_key
        if settings.llm_base_url:
            kwargs["base_url"] = settings.llm_base_url
        style = detect_thinking_style(provider, settings.llm_base_url, settings.llm_thinking_style)
        if uses_reasoning_subclass(provider, settings.llm_base_url, style):
            caps = capabilities_for(settings.llm_model, provider, settings.llm_base_url, style)
            return ReasoningChatOpenAI(
                **kwargs,
                # Chat Completions only: these vendors have no Responses API and the
                # reasoning fields are Chat-Completions shaped.
                use_responses_api=False,
                thinking_style=style,
                echo_reasoning=echo_policy(
                    style,
                    settings.llm_model,
                    preserve_thinking=bool(caps.get("preserve_thinking")),
                ),
                **resolve_thinking(thinking, caps, style, settings.llm_thinking_budget),
            )
        # Official OpenAI: ChatOpenAI maps this to the provider-native parameter.
        if thinking != "default":
            kwargs["reasoning_effort"] = {"off": "none", "fast": "low", "deep": "high"}[thinking]
        return ChatOpenAI(**kwargs)

    if provider == "anthropic":
        from langchain_anthropic import ChatAnthropic

        kwargs = {"model": settings.llm_model, "max_tokens": settings.llm_max_tokens or 8192, "temperature": settings.llm_temperature}
        if thinking == "off":
            kwargs["thinking"] = {"type": "disabled"}
        elif thinking in ("fast", "deep"):
            # Claude Opus 5 / Sonnet 5 use adaptive thinking; summarized display
            # is required if we want a reasoning block that can be streamed to UI.
            kwargs["thinking"] = {"type": "adaptive", "display": "summarized"}
            kwargs["reasoning_effort"] = "low" if thinking == "fast" else "high"
        if settings.llm_api_key:
            kwargs["api_key"] = settings.llm_api_key
        if settings.llm_base_url:
            kwargs["base_url"] = settings.llm_base_url
        return ChatAnthropic(**kwargs)

    # Anything else goes through LangChain's provider registry
    from langchain.chat_models import init_chat_model

    kwargs = dict(common)
    if settings.llm_api_key:
        kwargs["api_key"] = settings.llm_api_key
    if settings.llm_base_url:
        kwargs["base_url"] = settings.llm_base_url
    return init_chat_model(f"{provider}:{settings.llm_model}", **kwargs)
