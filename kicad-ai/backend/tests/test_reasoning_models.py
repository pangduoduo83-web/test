"""Reasoning-model request/response compatibility (no network calls)."""

from __future__ import annotations

from langchain_core.messages import AIMessage, AIMessageChunk, HumanMessage

from app.agent.llm import MockKiCadChatModel, ReasoningChatOpenAI, build_chat_model
from app.agent.model_router import ModelRouter
from app.agent.models import (
    BAILIAN_BASE_URL,
    capabilities_for,
    detect_thinking_style,
    echo_policy,
    resolve_thinking,
    routable_presets,
)
from app.config import Settings


def _bailian(**updates) -> Settings:
    values = {
        "llm_provider": "custom",
        "llm_model": "qwen3.8-max",
        "llm_api_key": "test-key",
        "llm_base_url": BAILIAN_BASE_URL,
        "llm_thinking": "deep",
    }
    values.update(updates)
    return Settings(**values)


def test_catalogue_capabilities_and_routable_presets():
    caps = capabilities_for("qwen3.8-max", "custom", BAILIAN_BASE_URL)
    assert caps["thinking"] == "hybrid"
    assert caps["thinking_default"] is True
    assert caps["preserve_thinking"] is True
    assert caps["context_tokens"] == 1_000_000

    model_ids = {p["id"] for p in routable_presets("custom", BAILIAN_BASE_URL, "qwen3.8-max")}
    assert {"qwen3.8-max", "qwen3.8-flash", "qwen3.7-plus", "deepseek-v4-flash", "glm-5.2"} <= model_ids
    assert "gpt-5.6" not in model_ids
    assert detect_thinking_style("custom", BAILIAN_BASE_URL) == "bailian"
    assert detect_thinking_style("custom", "https://api.deepseek.com/v1") == "thinking_type"


def test_thinking_mode_resolution():
    qwen = capabilities_for("qwen3.8-max", "custom", BAILIAN_BASE_URL)
    assert resolve_thinking("off", qwen, "bailian", 4096) == {"enable_thinking": False}
    fast = resolve_thinking("fast", qwen, "bailian", 4096)
    assert fast == {"enable_thinking": True, "reasoning_effort": "low", "preserve_thinking": True}
    deep = resolve_thinking("deep", qwen, "bailian", 4096)
    assert deep == {"enable_thinking": True, "preserve_thinking": True}
    assert echo_policy("bailian", "qwen3.8-max", preserve_thinking=True) == "all"

    kimi = capabilities_for("kimi-k3", "custom", "https://api.moonshot.cn/v1")
    assert kimi["thinking"] == "always"
    assert resolve_thinking("off", kimi, "thinking_type", 4096) == {"reasoning_effort": "low"}


def test_factory_builds_reasoning_aware_variants():
    deep = build_chat_model(_bailian())
    assert isinstance(deep, ReasoningChatOpenAI)
    assert deep.thinking_style == "bailian"
    assert deep.enable_thinking is True
    assert deep.preserve_thinking is True
    assert deep.echo_reasoning == "all"

    fast = build_chat_model(_bailian(llm_thinking="fast"))
    assert isinstance(fast, ReasoningChatOpenAI)
    assert fast.reasoning_effort == "low"

    off = build_chat_model(_bailian(llm_thinking="off"))
    assert isinstance(off, ReasoningChatOpenAI)
    assert off.enable_thinking is False

    assert MockKiCadChatModel.model_validate(build_chat_model(Settings(llm_provider="mock", llm_thinking="deep"))).enable_thinking
    assert not MockKiCadChatModel.model_validate(build_chat_model(Settings(llm_provider="mock", llm_thinking="off"))).enable_thinking


def test_reasoning_request_payload_and_response_conversion():
    model = build_chat_model(_bailian(llm_thinking="fast"))
    assert isinstance(model, ReasoningChatOpenAI)
    messages = [
        HumanMessage(content="first"),
        AIMessage(content="answer", additional_kwargs={"reasoning_content": "完整思考"}),
        HumanMessage(content="second"),
        AIMessage(content="legacy answer"),
        HumanMessage(content="third"),
    ]
    payload = model._get_request_payload(messages)
    assert payload["extra_body"] == {
        "enable_thinking": True,
        "preserve_thinking": True,
    }
    assert payload["reasoning_effort"] == "low"
    assert payload["messages"][1]["reasoning_content"] == "完整思考"
    # ``all`` is required by preserved-thinking providers; old history gets a
    # harmless empty value rather than causing a 400 on strict endpoints.
    assert payload["messages"][3]["reasoning_content"] == ""

    generation = model._convert_chunk_to_generation_chunk(
        {
            "model": "qwen3.8-max",
            "choices": [{"delta": {"role": "assistant", "content": "", "reasoning_content": "分片"}, "finish_reason": None}],
        },
        AIMessageChunk,
        None,
    )
    assert generation is not None
    assert generation.message.additional_kwargs["reasoning_content"] == "分片"

    result = model._create_chat_result(
        {
            "model": "qwen3.8-max",
            "choices": [{"message": {"role": "assistant", "content": "结果", "reasoning_content": "完整"}, "finish_reason": "stop"}],
        }
    )
    assert result.generations[0].message.additional_kwargs["reasoning_content"] == "完整"


def test_model_router_accepts_only_same_endpoint_models():
    settings = _bailian(llm_thinking="off")
    default = build_chat_model(settings)
    router = ModelRouter(settings, default)

    assert router.normalize("qwen3.7-plus", "fast") == ("qwen3.7-plus", "fast")
    assert router.normalize("gpt-5.6", "deep") == ("qwen3.8-max", "deep")
    assert router.resolve("auto", "auto") is default
    variant = router.resolve("qwen3.7-plus", "fast")
    assert isinstance(variant, ReasoningChatOpenAI)
    assert variant.model_name == "qwen3.7-plus"
    assert variant.enable_thinking is True
