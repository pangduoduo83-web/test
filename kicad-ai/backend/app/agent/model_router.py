"""Per-conversation model / thinking-mode selection.

The compiled agent is a process-wide singleton built around the server's
default model. A user may still pick, for a conversation, another model served
by the *same* endpoint (so the server's API key works) and a thinking mode.
``ModelRouter`` builds and caches those variants; ``ModelRouterMiddleware``
swaps them into every model call — main agent and subagents alike — based on
the ``model`` / ``thinking`` entries of the run's ``AgentContext.extra``.
"""

from __future__ import annotations

import logging
from typing import Any

from langchain.agents.middleware import AgentMiddleware
from langchain_core.language_models import BaseChatModel
from langgraph.config import get_config

from app.agent.context import AgentContext
from app.agent.llm import build_chat_model
from app.agent.models import normalize_thinking_mode, routable_presets
from app.config import Settings

log = logging.getLogger(__name__)

AUTO_MODEL = "auto"
"""Client value for "use the server default model"."""


class ModelRouter:
    def __init__(self, settings: Settings, default: BaseChatModel):
        self.settings = settings
        self.default = default
        self._variants: dict[tuple[str, str], BaseChatModel] = {}

    @property
    def default_model(self) -> str:
        return self.settings.llm_model

    @property
    def default_thinking(self) -> str:
        return normalize_thinking_mode(self.settings.llm_thinking)

    def presets(self) -> list[dict[str, Any]]:
        """Models the client may choose from (default first)."""
        return routable_presets(self.settings.llm_provider, self.settings.llm_base_url, self.default_model)

    def normalize(self, model: str | None, thinking: str | None) -> tuple[str, str]:
        """Validate a client's wish → the ``(model id, thinking mode)`` actually used.

        Unknown / non-routable models fall back to the default (a stale choice in
        the browser after the admin switched endpoints must not break chat).
        """
        wanted = (model or "").strip()
        model_id = self.default_model
        if wanted and wanted != AUTO_MODEL and wanted != self.default_model:
            if any(p["id"] == wanted for p in self.presets()):
                model_id = wanted
            else:
                log.info("model %r is not routable on this endpoint; using default %s", wanted, self.default_model)
        return model_id, normalize_thinking_mode(thinking, fallback=self.default_thinking)

    def resolve(self, model: str | None, thinking: str | None) -> BaseChatModel:
        key = self.normalize(model, thinking)
        if key == (self.default_model, self.default_thinking):
            return self.default
        variant = self._variants.get(key)
        if variant is None:
            settings = self.settings.model_copy(update={"llm_model": key[0], "llm_thinking": key[1]})
            variant = self._variants[key] = build_chat_model(settings)
            log.info("built model variant %s (thinking=%s)", *key)
        return variant


def _route(router: ModelRouter, request: Any) -> Any:
    ctx = getattr(getattr(request, "runtime", None), "context", None)
    extra = ctx.extra if isinstance(ctx, AgentContext) else {}
    # Raw subagents are nested graph invocations. Their RunnableConfig inherits
    # the parent's ``configurable`` values even on framework versions where the
    # typed Runtime context itself is not forwarded.
    try:
        config = get_config()
    except RuntimeError:  # direct unit invocation, outside a LangGraph run
        config = {}
    configurable = config.get("configurable") if isinstance(config, dict) else {}
    configurable = configurable if isinstance(configurable, dict) else {}
    model = extra.get("model") or configurable.get("model")
    thinking = extra.get("thinking") or configurable.get("thinking")
    if not model and not thinking:
        return request
    resolved = router.resolve(model, thinking)
    if resolved is router.default:
        return request
    return request.override(model=resolved)


class ModelRouterMiddleware(AgentMiddleware):
    """Swap the model of each call for the one the conversation asked for."""

    def __init__(self, router: ModelRouter):
        self.router = router

    def wrap_model_call(self, request: Any, handler: Any) -> Any:
        return handler(_route(self.router, request))

    async def awrap_model_call(self, request: Any, handler: Any) -> Any:
        return await handler(_route(self.router, request))
