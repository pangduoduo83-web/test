"""Streaming chat endpoints (Server-Sent Events over POST)."""

from __future__ import annotations

import asyncio
import json
import logging
from collections.abc import AsyncIterator
from typing import Any

from fastapi import APIRouter, Depends, HTTPException, Request, Response, status
from fastapi.responses import StreamingResponse
from langgraph.types import Command
from sqlalchemy.ext.asyncio import AsyncSession

from app.agent.change_plan import validate_change_plan
from app.agent.memory import ensure_user_memory
from app.agent.streaming import stream_agent_events
from app.api.conversations import refresh_conversation_stats
from app.auth import get_current_user
from app.db import User, get_session
from app.schemas import ChatRequest, DesignSelection, ResumeRequest
from app.services.conversations import (
    build_context,
    get_owned_conversation,
    get_owned_project,
    validate_design_selection,
)
from app.services.presence import presence_hub
from app.services.tenant_llm import NOT_CONFIGURED_MESSAGE, tenant_llm
from app.services.runs import RunSession, run_manager

log = logging.getLogger(__name__)
router = APIRouter(prefix="/api/chat", tags=["chat"])

SSE_HEADERS = {
    "Cache-Control": "no-cache, no-transform",
    "Connection": "keep-alive",
    "X-Accel-Buffering": "no",
}


def _approved_change_plan(state: Any, decisions: list[dict[str, Any]]) -> dict[str, Any] | None:
    """Extract an approved plan from the server-side pending interrupt.

    The client sends only approve/reject decisions. Action names and arguments
    always come from the checkpoint, so a forged resume request cannot expand
    what the user was shown.
    """
    for task in getattr(state, "tasks", ()) or ():
        for interrupt in getattr(task, "interrupts", ()) or ():
            value = getattr(interrupt, "value", interrupt)
            if not isinstance(value, dict):
                continue
            requests = value.get("action_requests")
            if not isinstance(requests, list):
                continue
            for index, action_request in enumerate(requests):
                decision = decisions[index] if index < len(decisions) else {}
                if (
                    isinstance(action_request, dict)
                    and action_request.get("name") == "submit_change_plan"
                    and decision.get("type") == "approve"
                    and isinstance(action_request.get("args"), dict)
                ):
                    plan, error = validate_change_plan(action_request["args"])
                    if error is None:
                        return plan
    return None


def _sse(event: dict[str, Any]) -> str:
    seq = event.get("seq")
    id_line = f"id: {seq}\n" if seq is not None else ""
    return f"{id_line}event: {event.get('type', 'message')}\ndata: {json.dumps(event, ensure_ascii=False, default=str)}\n\n"


async def _with_keepalive(source: AsyncIterator[dict[str, Any]], interval: float = 15.0) -> AsyncIterator[str]:
    """Interleave SSE comments so proxies do not drop idle connections."""
    it = source.__aiter__()
    pending: asyncio.Task | None = None
    try:
        while True:
            if pending is None:
                pending = asyncio.ensure_future(it.__anext__())
            done, _ = await asyncio.wait({pending}, timeout=interval)
            if not done:
                yield ": ping\n\n"
                continue
            try:
                event = pending.result()
            except StopAsyncIteration:
                return
            pending = None
            yield _sse(event)
    finally:
        # Only the subscription is torn down here; the agent run itself is a
        # background task owned by RunManager and keeps going.
        if pending is not None and not pending.done():
            pending.cancel()


def _attach(run: RunSession, after_seq: int = 0) -> StreamingResponse:
    return StreamingResponse(_with_keepalive(run.subscribe(after_seq)), media_type="text/event-stream", headers=SSE_HEADERS)


async def _start_run(
    request: Request,
    user: User,
    conversation_id: str,
    input_payload: dict[str, Any] | Command,
    project_id: str | None,
    session: AsyncSession,
    *,
    model: str | None = None,
    thinking: str | None = None,
    selection: DesignSelection | None = None,
    approved_plan: dict[str, Any] | None = None,
) -> StreamingResponse:
    conv = await get_owned_conversation(session, user, conversation_id)
    project = await get_owned_project(session, user, project_id or conv.project_id)
    if project and conv.project_id != project.id:
        conv.project_id = project.id
        await session.commit()
    runtime = request.app.state.agent_runtime
    if runtime.agent is None:
        raise HTTPException(status.HTTP_503_SERVICE_UNAVAILABLE, "Agent 尚未就绪")
    if run_manager.is_running(conv.id):
        raise HTTPException(status.HTTP_409_CONFLICT, "该对话正在处理上一条消息，请稍候")

    # 多商户:用该用户所属站点自己配置的大模型;站点没配就明确提示,不用平台的 Key
    site_llm = await tenant_llm.get(getattr(user, "tenant", None) or "default")
    if site_llm is None and tenant_llm.enforced():
        raise HTTPException(status.HTTP_503_SERVICE_UNAVAILABLE, NOT_CONFIGURED_MESSAGE)

    context = build_context(user, conv, project)
    context.selection = validate_design_selection(user, project, selection)
    context.extra["require_change_plan"] = True
    if approved_plan:
        context.extra["approved_change_plan"] = approved_plan
    if model:
        context.extra["model"] = model
    if thinking:
        context.extra["thinking"] = thinking
    config = runtime.thread_config(conv.id, user.id)
    if site_llm is not None:
        context.extra["llm_tenant"] = (user.tenant or "default").lower()
        config["configurable"]["llm_tenant"] = (user.tenant or "default").lower()
    if model:
        config["configurable"]["model"] = model
    if thinking:
        config["configurable"]["thinking"] = thinking
    if context.selection:
        config["configurable"]["design_selection"] = context.selection
    config["configurable"]["require_change_plan"] = True
    if approved_plan:
        config["configurable"]["approved_change_plan"] = approved_plan
    config["configurable"]["agent_context_block"] = context.context_block()
    await ensure_user_memory(runtime.store, user.id)
    await presence_hub.set_status(user.id, "busy")

    agent = runtime.agent
    conv_id, user_id = conv.id, user.id
    timeout_seconds = runtime.settings.agent_run_timeout_seconds

    def source() -> AsyncIterator[dict[str, Any]]:
        return stream_agent_events(
            agent,
            input_payload=input_payload,
            config=config,
            context=context,
            timeout_seconds=timeout_seconds,
        )

    async def finalize(_run: RunSession) -> None:
        await presence_hub.set_status(user_id, "idle")
        try:
            await refresh_conversation_stats(runtime, conv_id, user_id)
        except Exception:  # noqa: BLE001
            log.exception("failed to refresh conversation stats")

    history_len: int | None = None
    try:
        state = await runtime.get_state(conv_id, user_id)
        history_len = len(state.values.get("messages", []) or []) if state and state.values else 0
    except Exception:  # noqa: BLE001 — replay cut-off is best effort
        log.exception("could not read thread state before run")

    try:
        run = run_manager.start(conv_id, user_id, source, on_finish=finalize, history_len=history_len)
    except RuntimeError:
        await presence_hub.set_status(user_id, "idle")
        raise HTTPException(status.HTTP_409_CONFLICT, "该对话正在处理上一条消息，请稍候") from None
    return _attach(run)


@router.post("/{conversation_id}/stream")
async def stream(
    conversation_id: str,
    payload: ChatRequest,
    request: Request,
    user: User = Depends(get_current_user),
    session: AsyncSession = Depends(get_session),
):
    return await _start_run(
        request,
        user,
        conversation_id,
        {"messages": [{"role": "user", "content": payload.content}]},
        payload.project_id,
        session,
        model=payload.model,
        thinking=payload.thinking,
        selection=payload.selection,
    )


@router.post("/{conversation_id}/resume")
async def resume(
    conversation_id: str,
    payload: ResumeRequest,
    request: Request,
    user: User = Depends(get_current_user),
    session: AsyncSession = Depends(get_session),
):
    decisions = [d.model_dump(exclude_none=True) for d in payload.decisions]
    await get_owned_conversation(session, user, conversation_id)
    runtime = request.app.state.agent_runtime
    state = await runtime.get_state(conversation_id, user.id)
    approved_plan = _approved_change_plan(state, decisions)
    return await _start_run(
        request,
        user,
        conversation_id,
        Command(resume={"decisions": decisions}),
        None,
        session,
        model=payload.model,
        thinking=payload.thinking,
        selection=payload.selection,
        approved_plan=approved_plan,
    )


@router.get("/{conversation_id}/events")
async def events(
    conversation_id: str,
    after: int = 0,
    user: User = Depends(get_current_user),
    session: AsyncSession = Depends(get_session),
):
    """Re-attach to the conversation's run (active, or finished within the last
    two minutes) and replay every event with ``seq > after``."""
    await get_owned_conversation(session, user, conversation_id)
    run = run_manager.get(conversation_id)
    if run is None or run.user_id != user.id:
        return Response(status_code=status.HTTP_204_NO_CONTENT)
    return _attach(run, after_seq=max(int(after or 0), 0))


@router.get("/{conversation_id}/run")
async def run_status(
    conversation_id: str,
    user: User = Depends(get_current_user),
    session: AsyncSession = Depends(get_session),
):
    await get_owned_conversation(session, user, conversation_id)
    run = run_manager.get(conversation_id)
    if run is None or run.user_id != user.id:
        return {"active": False, "run": None}
    return {"active": run.is_active, "run": run.snapshot()}


@router.post("/{conversation_id}/cancel")
async def cancel(conversation_id: str, user: User = Depends(get_current_user), session: AsyncSession = Depends(get_session)):
    await get_owned_conversation(session, user, conversation_id)
    return {"cancelled": run_manager.cancel(conversation_id)}
