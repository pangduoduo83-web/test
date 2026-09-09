"""Model catalogue: presets, thinking capabilities and vendor parameter styles.

Kept free of database / framework imports so both the LLM factory and the
admin settings store can use it.

Thinking ("deep reasoning") is controlled differently by every vendor:

* ``bailian``             Alibaba Cloud Model Studio (DashScope) OpenAI-compatible
                          endpoint: ``extra_body.enable_thinking`` /
                          ``thinking_budget`` (+ standard ``reasoning_effort`` on
                          models that support it), reasoning streamed back in
                          ``delta.reasoning_content``.
* ``thinking_type``       DeepSeek / Zhipu GLM / Moonshot official APIs:
                          ``extra_body.thinking = {"type": "enabled"|"disabled"}``.
* ``chat_template_kwargs`` vLLM / SGLang self-hosted Qwen:
                          ``extra_body.chat_template_kwargs.enable_thinking``.
* ``ollama``              Ollama's OpenAI-compatible endpoint: ``extra_body.think``.
* ``none``                Unknown endpoint or native reasoning models (OpenAI /
                          Anthropic): no vendor parameters are injected.
"""

from __future__ import annotations

from typing import Any, TypedDict

BAILIAN_BASE_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1"

THINKING_MODES = ("default", "off", "fast", "deep")
"""``default`` = follow the model/provider default; the rest force a mode."""

THINKING_MODE_LABELS = {"default": "跟随模型默认", "off": "关闭思考", "fast": "快速思考", "deep": "深度思考"}

THINKING_STYLES = ("auto", "bailian", "thinking_type", "chat_template_kwargs", "ollama", "none")
"""Accepted values of ``llm_thinking_style`` (``auto`` = infer from provider / base URL)."""


class ModelPreset(TypedDict, total=False):
    name: str
    provider: str
    model: str
    base_url: str
    # hybrid  – thinking can be switched per request
    # always  – thinking-only model (cannot be disabled)
    # none    – no thinking support
    # native  – provider-native reasoning (OpenAI / Anthropic), not controlled here
    thinking: str
    thinking_default: bool  # what the vendor does when nothing is sent
    efforts: list[str]  # accepted ``reasoning_effort`` values, lowest first (empty = unsupported)
    # Bailian ``preserve_thinking``: feed earlier ``reasoning_content`` back to the model
    # (only a handful of Qwen / Kimi deployments accept the parameter)
    preserve_thinking: bool
    context_tokens: int


# Current model line-up as of 2026-09 (checked against each vendor's model docs).
# Retired ids that must not come back: deepseek-chat / deepseek-reasoner (gone 2026-07-24),
# moonshot-v1-* and kimi-k2.5 (gone 2026-08-31).
MODEL_PRESETS: list[ModelPreset] = [
    {"name": "OpenAI GPT-5.6 (Sol)", "provider": "openai", "model": "gpt-5.6", "base_url": "", "thinking": "native"},
    {"name": "OpenAI GPT-5.6 Terra", "provider": "openai", "model": "gpt-5.6-terra", "base_url": "", "thinking": "native"},
    {"name": "Anthropic Claude Opus 5", "provider": "anthropic", "model": "claude-opus-5", "base_url": "", "thinking": "native"},
    {"name": "Anthropic Claude Sonnet 5", "provider": "anthropic", "model": "claude-sonnet-5", "base_url": "", "thinking": "native"},
    {"name": "DeepSeek V4 Pro", "provider": "custom", "model": "deepseek-v4-pro", "base_url": "https://api.deepseek.com/v1", "thinking": "hybrid", "thinking_default": True, "efforts": ["low", "high", "max"]},
    {"name": "DeepSeek V4 Flash", "provider": "custom", "model": "deepseek-v4-flash", "base_url": "https://api.deepseek.com/v1", "thinking": "hybrid", "thinking_default": True, "efforts": ["low", "high", "max"]},
    # ---- Alibaba Cloud Model Studio (百炼): one API key serves all of these ----
    {"name": "通义千问 Qwen3.8-Max", "provider": "custom", "model": "qwen3.8-max", "base_url": BAILIAN_BASE_URL, "thinking": "hybrid", "thinking_default": True, "efforts": ["low", "medium", "xhigh"], "preserve_thinking": True, "context_tokens": 1_000_000},
    {"name": "通义千问 Qwen3.8-Flash", "provider": "custom", "model": "qwen3.8-flash", "base_url": BAILIAN_BASE_URL, "thinking": "hybrid", "thinking_default": True, "preserve_thinking": True, "context_tokens": 1_000_000},
    {"name": "通义千问 Qwen3.7-Plus", "provider": "custom", "model": "qwen3.7-plus", "base_url": BAILIAN_BASE_URL, "thinking": "hybrid", "thinking_default": True, "preserve_thinking": True, "context_tokens": 1_000_000},
    {"name": "通义千问 Qwen3.7-Flash", "provider": "custom", "model": "qwen3.7-flash", "base_url": BAILIAN_BASE_URL, "thinking": "hybrid", "thinking_default": True, "preserve_thinking": True, "context_tokens": 1_000_000},
    {"name": "通义千问 Qwen3-Max", "provider": "custom", "model": "qwen3-max", "base_url": BAILIAN_BASE_URL, "thinking": "hybrid", "thinking_default": False, "context_tokens": 256_000},
    {"name": "DeepSeek V4 Flash (百炼)", "provider": "custom", "model": "deepseek-v4-flash", "base_url": BAILIAN_BASE_URL, "thinking": "hybrid", "thinking_default": True, "efforts": ["low", "high", "max"], "context_tokens": 1_000_000},
    {"name": "智谱 GLM-5.2 (百炼)", "provider": "custom", "model": "glm-5.2", "base_url": BAILIAN_BASE_URL, "thinking": "hybrid", "thinking_default": True, "efforts": ["low", "high", "max"], "context_tokens": 1_000_000},
    # ---- other vendors ----
    {"name": "Moonshot Kimi K3", "provider": "custom", "model": "kimi-k3", "base_url": "https://api.moonshot.cn/v1", "thinking": "always", "thinking_default": True, "efforts": ["low", "high", "max"], "preserve_thinking": True},
    {"name": "智谱 GLM-5.3", "provider": "custom", "model": "glm-5.3", "base_url": "https://open.bigmodel.cn/api/paas/v4", "thinking": "always", "thinking_default": True, "efforts": ["low", "high", "max"], "preserve_thinking": True},
    {"name": "Ollama (本地)", "provider": "custom", "model": "qwen3.5:27b", "base_url": "http://host.docker.internal:11434/v1", "thinking": "hybrid", "thinking_default": True},
    {"name": "离线演示 (mock)", "provider": "mock", "model": "mock-kicad", "base_url": "", "thinking": "hybrid", "thinking_default": True},
]


def normalize_base_url(url: str | None) -> str:
    return (url or "").strip().rstrip("/").lower()


def normalize_thinking_mode(value: str | None, fallback: str = "default") -> str:
    mode = (value or "").strip().lower()
    return mode if mode in THINKING_MODES else fallback


def detect_thinking_style(provider: str, base_url: str, explicit: str = "auto") -> str:
    """Pick the vendor parameter style for thinking control."""
    explicit = (explicit or "auto").strip().lower()
    if explicit != "auto":
        return explicit
    provider = (provider or "").strip().lower()
    host = normalize_base_url(base_url)
    if provider == "mock":
        return "none"
    if provider in ("dashscope", "qwen", "bailian") or "dashscope" in host or "aliyuncs.com" in host:
        return "bailian"
    if provider in ("deepseek", "moonshot", "zhipu", "glm") or any(h in host for h in ("deepseek.com", "moonshot", "bigmodel.cn", "z.ai")):
        return "thinking_type"
    if provider in ("ollama", "ollama-openai") or "11434" in host or "ollama" in host:
        return "ollama"
    if any(h in host for h in ("localhost", "127.0.0.1", "vllm", "sglang", ":8000")):
        return "chat_template_kwargs"
    return "none"


def uses_reasoning_subclass(provider: str, base_url: str, style: str) -> bool:
    """Whether the OpenAI-compatible endpoint should go through the reasoning-aware subclass."""
    provider = (provider or "").strip().lower()
    if provider in ("openai",) and not normalize_base_url(base_url):
        return False  # official OpenAI: ChatOpenAI handles reasoning natively
    return style != "none" or provider in ("custom", "openai-compatible", "deepseek", "qwen", "dashscope", "moonshot", "ollama-openai")


def echo_policy(style: str, model: str, *, preserve_thinking: bool = False) -> str:
    """Which historical ``reasoning_content`` to send back with each request.

    * DeepSeek (official API, and to be safe the model anywhere) returns HTTP 400 in
      thinking mode unless *every* assistant message of a tool-using conversation
      carries its ``reasoning_content`` → ``all``. GLM / Kimi official APIs want the
      same for their interleaved thinking, and ignore surplus reasoning.
    * Bailian requires all historical reasoning when ``preserve_thinking`` is
      enabled (Qwen3.8 Max/Flash enable it by default) → ``all``.
    * Other Bailian / vLLM / Ollama models keep only the latest turn → ``turn``
      (cheap and enough to keep the chain of thought across tool calls).
    """
    if style == "none":
        return "none"
    model_lower = (model or "").strip().lower()
    if style == "thinking_type" or preserve_thinking or model_lower.startswith(("deepseek", "glm", "kimi")):
        return "all"
    return "turn"


def resolve_thinking(mode: str, caps: dict[str, Any], style: str, budget: int | None) -> dict[str, Any]:
    """Translate a thinking *mode* into request options for one model.

    Returns the ``ReasoningChatOpenAI`` fields to set (``enable_thinking``,
    ``thinking_budget``, ``reasoning_effort``, ``preserve_thinking``); an empty dict
    means "send nothing, keep the vendor default".
    """
    mode = normalize_thinking_mode(mode)
    thinking = caps.get("thinking", "none")
    if mode == "default" or style == "none" or thinking in ("none", "native"):
        return {}
    if mode == "off":
        # Thinking-only models cannot be switched off; use their lowest effort
        # as the closest (and cheapest) equivalent.
        if thinking == "always":
            efforts = list(caps.get("efforts") or [])
            return {"reasoning_effort": efforts[0]} if efforts else {}
        return {"enable_thinking": False}
    out: dict[str, Any] = {}
    if thinking != "always":
        out["enable_thinking"] = True
    efforts = list(caps.get("efforts") or [])
    if mode == "fast":
        if efforts:
            out["reasoning_effort"] = efforts[0]
        elif style == "bailian" and budget:
            out["thinking_budget"] = int(budget)
    if caps.get("preserve_thinking"):
        out["preserve_thinking"] = True
    return out


def _same_endpoint(preset: ModelPreset, provider: str, base_url: str) -> bool:
    provider = (provider or "").strip().lower()
    p_provider = (preset.get("provider") or "").lower()
    if provider == "mock":
        return p_provider == "mock"
    if p_provider != provider:
        return False
    if provider in ("openai", "anthropic") and not normalize_base_url(base_url):
        return not normalize_base_url(preset.get("base_url"))
    return normalize_base_url(preset.get("base_url")) == normalize_base_url(base_url)


def find_preset(model: str, provider: str, base_url: str) -> ModelPreset | None:
    for p in MODEL_PRESETS:
        if p["model"] == model and _same_endpoint(p, provider, base_url):
            return p
    return None


def capabilities_for(model: str, provider: str, base_url: str, style: str | None = None) -> dict[str, Any]:
    """Thinking capability of *model* on the configured endpoint (preset or inferred)."""
    style = style or detect_thinking_style(provider, base_url)
    preset = find_preset(model, provider, base_url)
    if preset is not None:
        thinking = preset.get("thinking", "none")
        if thinking == "hybrid" and style == "none" and provider != "mock":
            thinking = "none"  # we know the model, but not how to talk to this endpoint
        return {
            "thinking": thinking,
            "thinking_default": bool(preset.get("thinking_default", False)),
            "efforts": list(preset.get("efforts", [])),
            "preserve_thinking": bool(preset.get("preserve_thinking", False)),
            "context_tokens": preset.get("context_tokens"),
        }
    inferred = "hybrid" if style != "none" else "none"
    return {"thinking": inferred, "thinking_default": False, "efforts": [], "preserve_thinking": False, "context_tokens": None}


def routable_presets(provider: str, base_url: str, default_model: str) -> list[dict[str, Any]]:
    """Models a user may pick per conversation with the server's credentials.

    Only presets served by the *same* endpoint qualify (one API key), so on
    Bailian this yields the whole Qwen / DeepSeek / GLM line-up while on the
    official OpenAI API it yields the GPT presets. The configured default model
    always comes first.
    """
    out: list[dict[str, Any]] = []
    seen: set[str] = set()

    def add(model: str, name: str, caps: dict[str, Any], is_default: bool) -> None:
        if model in seen:
            return
        seen.add(model)
        out.append({"id": model, "name": name, "provider": provider, "default": is_default, **caps})

    default_preset = find_preset(default_model, provider, base_url)
    add(default_model, default_preset["name"] if default_preset else default_model, capabilities_for(default_model, provider, base_url), True)
    for p in MODEL_PRESETS:
        if _same_endpoint(p, provider, base_url):
            add(p["model"], p["name"], capabilities_for(p["model"], provider, base_url), False)
    return out
