"""Translate LangGraph v2 stream chunks into flat UI events.

Event types emitted (all JSON objects with a ``type`` key):

``run_start`` · ``message_start`` · ``reasoning_delta`` · ``text_delta`` · ``message_end`` ·
``tool_call_start`` · ``tool_call_delta`` · ``tool_call_args`` · ``tool_result`` ·
``todos`` · ``custom`` (``file_changed`` / ``file_diff`` / ``snapshot`` / ``loop_guard``) ·
``interrupt`` · ``usage`` · ``error`` · ``run_end``
"""

from __future__ import annotations

import asyncio
import json
import logging
import uuid
from collections.abc import AsyncIterator
from datetime import datetime, timezone
from typing import Any

from langchain_core.messages import (
    AIMessage,
    AIMessageChunk,
    BaseMessage,
    HumanMessage,
    ToolMessage,
)
from langgraph.errors import GraphRecursionError
from langgraph.types import Command

from app.agent.budget import RunBudgetExceeded
from app.agent.context import AgentContext
from app.agent.tools.registry import get_policy

log = logging.getLogger(__name__)

# Steps LangGraph spends per model→tools round in the deep agent graph
# (model node + after_model hooks + tools node); used only for user-facing hints.
STEPS_PER_ROUND = 4


def _source(ns: tuple[str, ...] | list[str]) -> str:
    for seg in ns:
        if str(seg).startswith("tools:"):
            return f"subagent:{str(seg).split(':', 1)[1][:8]}"
    return "main"


def _text_of(message: BaseMessage) -> str:
    content = message.content
    if isinstance(content, str):
        return content
    if isinstance(content, list):
        parts = []
        for block in content:
            if isinstance(block, str):
                parts.append(block)
            elif isinstance(block, dict) and block.get("type") in ("text", None) and "text" in block:
                parts.append(str(block["text"]))
        return "".join(parts)
    return str(content or "")


def _reasoning_of(message: BaseMessage) -> str:
    """Model reasoning: ``reasoning_content`` (OpenAI-compatible vendors) or
    ``thinking`` / ``reasoning`` content blocks (Anthropic, LangChain v1 blocks)."""
    kwargs = getattr(message, "additional_kwargs", None) or {}
    value = kwargs.get("reasoning_content")
    if isinstance(value, str) and value:
        return value
    content = message.content
    if isinstance(content, list):
        parts = []
        for block in content:
            if isinstance(block, dict) and block.get("type") in ("thinking", "reasoning"):
                text = block.get("thinking") or block.get("reasoning") or ""
                if isinstance(text, str):
                    parts.append(text)
        return "".join(parts)
    return ""


def _json_safe(value: Any) -> Any:
    try:
        json.dumps(value)
        return value
    except (TypeError, ValueError):
        return json.loads(json.dumps(value, default=str))


def _tool_meta(name: str) -> dict[str, str]:
    policy = get_policy(name)
    return {"kind": policy.kind, "category": policy.category}


def _tool_call_view(tc: dict[str, Any]) -> dict[str, Any]:
    return {"id": tc.get("id"), "name": tc["name"], "args": _json_safe(tc.get("args") or {}), **_tool_meta(tc["name"])}


def _artifact_diff(msg: ToolMessage) -> Any:
    artifact = getattr(msg, "artifact", None)
    if isinstance(artifact, dict):
        return _json_safe(artifact.get("file_diff"))
    return None


def _tool_result_payload(msg: ToolMessage) -> dict[str, Any]:
    content = _text_of(msg)
    parsed: Any = None
    if content.lstrip().startswith(("{", "[")):
        try:
            parsed = json.loads(content)
        except json.JSONDecodeError:
            parsed = None
    ok = msg.status != "error"
    if isinstance(parsed, dict) and parsed.get("success") is False:
        ok = False
    payload: dict[str, Any] = {
        "type": "tool_result",
        "id": msg.tool_call_id,
        "name": msg.name,
        "ok": ok,
        "content": content[:20000],
        "data": parsed if isinstance(parsed, (dict, list)) else None,
    }
    diff = _artifact_diff(msg)
    if diff is not None:
        payload["diff"] = diff
    return payload


def _interrupt_payload(interrupts: Any) -> dict[str, Any]:
    items = []
    for intr in interrupts or []:
        value = getattr(intr, "value", intr)
        iid = getattr(intr, "id", None) or getattr(intr, "interrupt_id", None)
        items.append({"id": iid, "value": _json_safe(value)})
    return {"type": "interrupt", "interrupts": items}


def recursion_limit_message(limit: int) -> str:
    rounds = max(1, limit // STEPS_PER_ROUND)
    return (
        f"本轮执行已达到步数上限（{limit} 步，约 {rounds} 轮工具调用）。"
        "已完成的修改和进度都已保存；点击「继续执行」可让助手从中断处接着做，"
        "也可以缩小任务范围后重新描述。管理员可在后台「系统设置」中调高上限。"
    )


def run_timeout_message(seconds: int) -> str:
    minutes = max(1, round(seconds / 60))
    return (
        f"本轮执行已超过时长上限（约 {minutes} 分钟）。已完成的修改和进度都已保存；"
        "点击「继续执行」可让助手接着做，也可以缩小任务范围后重新描述。"
        "管理员可在后台「系统设置」中调整上限。"
    )


def token_budget_message(used: int, budget: int) -> str:
    return (
        f"本轮执行已用掉约 {used:,} token，超过单次运行预算（{budget:,}）。"
        "已完成的修改和进度都已保存；请检查助手是否在无效循环，确认后点击「继续执行」"
        "可开始新一轮预算。管理员可在后台「系统设置」中调整预算。"
    )


class _Deadline:
    """``asyncio.timeout`` that is a no-op when *seconds* is falsy."""

    def __init__(self, seconds: float | None):
        self._cm = asyncio.timeout(seconds) if seconds and seconds > 0 else None

    async def __aenter__(self):
        if self._cm is not None:
            await self._cm.__aenter__()
        return self

    async def __aexit__(self, exc_type, exc, tb):
        if self._cm is not None:
            return await self._cm.__aexit__(exc_type, exc, tb)
        return False


async def stream_agent_events(
    agent,
    *,
    input_payload: dict[str, Any] | Command,
    config: dict[str, Any],
    context: AgentContext,
    timeout_seconds: float | None = None,
) -> AsyncIterator[dict[str, Any]]:
    """Run the agent and yield UI events."""
    run_id = uuid.uuid4().hex
    yield {"type": "run_start", "run_id": run_id, "at": datetime.now(timezone.utc).isoformat()}
    # Echo the user's message so a client that (re)attaches to the run can show
    # the turn it belongs to; the sender's own tab already has it and de-dupes.
    if isinstance(input_payload, dict):
        for raw in reversed(list(input_payload.get("messages") or [])):
            role = raw.get("role") if isinstance(raw, dict) else getattr(raw, "type", None)
            if role in ("user", "human"):
                content = raw.get("content") if isinstance(raw, dict) else getattr(raw, "content", "")
                yield {"type": "user_message", "content": content if isinstance(content, str) else str(content)}
                break

    current_key: str | None = None
    current_msg_id: str | None = None
    announced_tool_calls: set[str] = set()
    index_to_call: dict[int, str] = {}  # streaming tool_call_chunks: index → tool call id
    index_to_name: dict[int, str] = {}
    usage_totals = {"input_tokens": 0, "output_tokens": 0}
    saw_interrupt = False

    async def chunks() -> AsyncIterator[dict[str, Any]]:
        # The deadline lives here, around the graph awaits, so an expiry surfaces
        # as TimeoutError to the loop below rather than as a bare cancellation.
        async with _Deadline(timeout_seconds):
            async for chunk in agent.astream(
                input_payload,
                config=config,
                context=context,
                stream_mode=["updates", "messages", "custom"],
                subgraphs=True,
                version="v2",
            ):
                yield chunk

    try:
        async for chunk in chunks():
            ctype = chunk.get("type")
            ns = tuple(chunk.get("ns") or ())
            data = chunk.get("data")
            source = _source(ns)

            # ---------------------------------------------------- token stream
            if ctype == "messages":
                token, metadata = data
                metadata = metadata or {}
                if isinstance(token, AIMessageChunk):
                    key = f"{'/'.join(ns)}|{metadata.get('langgraph_step')}|{metadata.get('langgraph_node')}"
                    if key != current_key:
                        current_key = key
                        current_msg_id = token.id or f"msg_{uuid.uuid4().hex[:12]}"
                        index_to_call = {}
                        index_to_name = {}
                        yield {
                            "type": "message_start",
                            "id": current_msg_id,
                            "source": source,
                            "agent": metadata.get("lc_agent_name") or metadata.get("name"),
                        }
                    reasoning = _reasoning_of(token)
                    if reasoning:
                        yield {"type": "reasoning_delta", "id": current_msg_id, "source": source, "delta": reasoning}
                    text = _text_of(token)
                    if text:
                        yield {"type": "text_delta", "id": current_msg_id, "source": source, "delta": text}
                    for tc in token.tool_call_chunks or []:
                        name = tc.get("name")
                        tcid = tc.get("id")
                        index = tc.get("index")
                        idx = int(index) if index is not None else 0
                        if tcid:
                            index_to_call[idx] = tcid
                        if name:
                            index_to_name[idx] = index_to_name.get(idx, "") + name
                        call_id = tcid or index_to_call.get(idx)
                        call_name = index_to_name.get(idx)
                        if call_name and call_id and call_id not in announced_tool_calls:
                            announced_tool_calls.add(call_id)
                            yield {"type": "tool_call_start", "id": call_id, "name": call_name, "source": source, "message_id": current_msg_id, **_tool_meta(call_name)}
                        partial = tc.get("args")
                        if call_id and partial:
                            yield {"type": "tool_call_delta", "id": call_id, "source": source, "delta": partial}
                    usage = getattr(token, "usage_metadata", None)
                    if usage:
                        usage_totals["input_tokens"] += int(usage.get("input_tokens", 0) or 0)
                        usage_totals["output_tokens"] += int(usage.get("output_tokens", 0) or 0)
                        yield {"type": "usage", "source": source, **usage}
                elif isinstance(token, ToolMessage):
                    yield {**_tool_result_payload(token), "source": source}

            # ---------------------------------------------------- node updates
            elif ctype == "updates" and isinstance(data, dict):
                for node, update in data.items():
                    if node == "__interrupt__":
                        saw_interrupt = True
                        yield _interrupt_payload(update)
                        continue
                    if not isinstance(update, dict):
                        continue
                    if "todos" in update and update["todos"] is not None:
                        yield {"type": "todos", "source": source, "todos": _json_safe(update["todos"])}
                    for msg in update.get("messages") or []:
                        if isinstance(msg, AIMessage) and not isinstance(msg, AIMessageChunk):
                            for tc in msg.tool_calls or []:
                                tcid = tc.get("id") or ""
                                if tcid not in announced_tool_calls:
                                    announced_tool_calls.add(tcid)
                                    yield {"type": "tool_call_start", "id": tcid, "name": tc["name"], "source": source, "message_id": msg.id, **_tool_meta(tc["name"])}
                                yield {"type": "tool_call_args", "source": source, **_tool_call_view(tc)}
                            if node == "model" or node.endswith("model"):
                                yield {
                                    "type": "message_end",
                                    "id": msg.id or current_msg_id,
                                    "stream_id": current_msg_id,
                                    "source": source,
                                    "content": _text_of(msg),
                                    "reasoning": _reasoning_of(msg),
                                    "tool_calls": [_tool_call_view(tc) for tc in msg.tool_calls or []],
                                }
                                current_key = None
                        elif isinstance(msg, ToolMessage) and not ns:
                            # main-agent tool results are also delivered via the
                            # messages stream; emit here only for subagent `task`
                            # summaries that may not have streamed.
                            if msg.name == "task":
                                yield {**_tool_result_payload(msg), "source": source}

            # ---------------------------------------------------- custom events
            elif ctype == "custom":
                payload = dict(data) if isinstance(data, dict) else {"value": _json_safe(data)}
                event_name = str(payload.pop("type", "custom"))
                yield {"type": "custom", "event": event_name, "source": source, **_json_safe(payload)}

    except GraphRecursionError:
        limit = int(config.get("recursion_limit") or 0)
        log.warning("agent run hit recursion limit (%s) for thread %s", limit, config.get("configurable", {}).get("thread_id"))
        yield {
            "type": "error",
            "code": "recursion_limit",
            "recoverable": True,
            "limit": limit,
            "message": recursion_limit_message(limit),
        }
    except TimeoutError:
        seconds = int(timeout_seconds or 0)
        log.warning("agent run timed out after %ss for thread %s", seconds, config.get("configurable", {}).get("thread_id"))
        yield {
            "type": "error",
            "code": "run_timeout",
            "recoverable": True,
            "limit": seconds,
            "message": run_timeout_message(seconds),
        }
    except RunBudgetExceeded as exc:
        log.warning("agent run exceeded token budget (%s > %s) for thread %s", exc.used, exc.budget, config.get("configurable", {}).get("thread_id"))
        yield {
            "type": "error",
            "code": "token_budget",
            "recoverable": True,
            "limit": exc.budget,
            "used": exc.used,
            "message": token_budget_message(exc.used, exc.budget),
        }
    except Exception as exc:
        log.exception("agent run failed")
        yield {"type": "error", "code": "run_failed", "recoverable": False, "message": f"{type(exc).__name__}: {exc}"}
    finally:
        yield {
            "type": "run_end",
            "run_id": run_id,
            "interrupted": saw_interrupt,
            "usage": usage_totals,
            "at": datetime.now(timezone.utc).isoformat(),
        }


# ---------------------------------------------------------------------------
# History conversion
# ---------------------------------------------------------------------------
def messages_to_history(messages: list[BaseMessage]) -> list[dict[str, Any]]:
    out: list[dict[str, Any]] = []
    for m in messages:
        if isinstance(m, HumanMessage):
            out.append({"id": m.id or uuid.uuid4().hex, "role": "user", "content": _text_of(m)})
        elif isinstance(m, AIMessage):
            ai_entry: dict[str, Any] = {
                "id": m.id or uuid.uuid4().hex,
                "role": "assistant",
                "content": _text_of(m),
                "tool_calls": [_tool_call_view(tc) for tc in m.tool_calls or []],
            }
            reasoning = _reasoning_of(m)
            if reasoning:
                ai_entry["reasoning"] = reasoning
            out.append(ai_entry)
        elif isinstance(m, ToolMessage):
            payload = _tool_result_payload(m)
            entry: dict[str, Any] = {
                "id": m.id or uuid.uuid4().hex,
                "role": "tool",
                "content": payload["content"],
                "tool_call_id": m.tool_call_id,
                "name": m.name,
                "status": "ok" if payload["ok"] else "error",
            }
            if "diff" in payload:
                entry["diff"] = payload["diff"]
            out.append(entry)
    return out


def estimate_context_usage(messages: list[BaseMessage], context_tokens: int) -> float:
    safe_limit = max(16_000, context_tokens)
    last_usage = 0
    for m in reversed(messages):
        usage = getattr(m, "usage_metadata", None)
        if isinstance(m, AIMessage) and usage:
            last_usage = int(usage.get("total_tokens") or usage.get("input_tokens") or 0)
            break
    if not last_usage:
        chars = 0
        for m in messages:
            text = _text_of(m)
            # ToolMessage payloads (PCB dumps, netlists, file trees) can be hundreds
            # of KB; cap their contribution so token estimation stays realistic.
            if isinstance(m, ToolMessage):
                chars += min(len(text), 2000)
            else:
                chars += len(text)
        last_usage = chars // 3
    return min(1.0, last_usage / safe_limit)
