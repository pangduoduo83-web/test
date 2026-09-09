"""Per-run cost ceiling.

The recursion limit caps *steps*, not spend: a hundred rounds over a growing
context can burn millions of tokens. ``RunBudgetMiddleware`` adds up the
``usage_metadata`` of every model reply in the current turn before each new
model call and stops the run once the configured budget is exceeded. The stop
is reported like the recursion limit — recoverable, progress checkpointed — so
the user can consciously continue instead of paying for a runaway loop.
"""

from __future__ import annotations

from collections.abc import Callable
from typing import Any

from langchain.agents.middleware import AgentMiddleware
from langchain_core.messages import AIMessage, HumanMessage


class RunBudgetExceeded(RuntimeError):
    def __init__(self, used: int, budget: int) -> None:
        super().__init__(f"run token budget exceeded: {used} > {budget}")
        self.used = used
        self.budget = budget


def turn_token_usage(messages: list[Any]) -> int:
    """Total tokens reported by model replies since the last human message."""
    start = 0
    for index in range(len(messages) - 1, -1, -1):
        if isinstance(messages[index], HumanMessage):
            start = index
            break
    total = 0
    for message in messages[start:]:
        usage = getattr(message, "usage_metadata", None) if isinstance(message, AIMessage) else None
        if not usage:
            continue
        reported = usage.get("total_tokens")
        if not reported:
            reported = int(usage.get("input_tokens", 0) or 0) + int(usage.get("output_tokens", 0) or 0)
        total += int(reported or 0)
    return total


class RunBudgetMiddleware(AgentMiddleware):
    """Refuse further model calls once the turn's token usage exceeds the budget."""

    name = "RunBudgetMiddleware"

    def __init__(self, budget: int | Callable[[], int]) -> None:
        super().__init__()
        self._budget = budget

    @property
    def budget(self) -> int:
        value = self._budget() if callable(self._budget) else self._budget
        return int(value or 0)

    def _check(self, request: Any) -> None:
        budget = self.budget
        if budget <= 0:
            return
        state = getattr(request, "state", None)
        messages = state.get("messages") if isinstance(state, dict) else None
        if not messages:
            messages = getattr(request, "messages", None) or []
        used = turn_token_usage(list(messages))
        if used > budget:
            raise RunBudgetExceeded(used, budget)

    def wrap_model_call(self, request: Any, handler: Any) -> Any:
        self._check(request)
        return handler(request)

    async def awrap_model_call(self, request: Any, handler: Any) -> Any:
        self._check(request)
        return await handler(request)
