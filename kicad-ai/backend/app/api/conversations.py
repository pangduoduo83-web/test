"""Conversation (LangGraph thread) metadata + history."""

from __future__ import annotations

from fastapi import APIRouter, Depends, Request, Response
from langchain_core.messages import AIMessage, HumanMessage, ToolMessage
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.agent.llm import context_window_for
from app.agent.streaming import estimate_context_usage, messages_to_history
from app.auth import get_current_user
from app.config import get_settings
from app.db import Conversation, User, get_session, session_scope
from app.schemas import (
    ConversationCreate,
    ConversationDetail,
    ConversationOut,
    ConversationUpdate,
)
from app.services.conversations import get_owned_conversation, get_owned_project
from app.services.runs import run_manager

router = APIRouter(prefix="/api/conversations", tags=["conversations"])


def _out(c: Conversation) -> ConversationOut:
    return ConversationOut(
        id=c.id,
        title=c.title,
        preview=c.preview,
        project_id=c.project_id,
        message_count=c.message_count,
        tool_call_count=c.tool_call_count,
        input_tokens=c.input_tokens,
        output_tokens=c.output_tokens,
        created_at=c.created_at,
        updated_at=c.updated_at,
        is_running=run_manager.is_running(c.id),
    )


@router.get("", response_model=list[ConversationOut])
async def list_(user: User = Depends(get_current_user), session: AsyncSession = Depends(get_session)):
    result = await session.execute(
        select(Conversation).where(Conversation.owner_id == user.id).order_by(Conversation.updated_at.desc())
    )
    return [_out(c) for c in result.scalars()]


@router.post("", response_model=ConversationOut, status_code=201)
async def create(payload: ConversationCreate, user: User = Depends(get_current_user), session: AsyncSession = Depends(get_session)):
    project = await get_owned_project(session, user, payload.project_id)
    conv = Conversation(owner_id=user.id, title=payload.title or "新对话", project_id=project.id if project else None)
    session.add(conv)
    await session.commit()
    await session.refresh(conv)
    return _out(conv)


@router.get("/{conversation_id}", response_model=ConversationDetail)
async def detail(
    conversation_id: str,
    request: Request,
    user: User = Depends(get_current_user),
    session: AsyncSession = Depends(get_session),
):
    conv = await get_owned_conversation(session, user, conversation_id)
    runtime = request.app.state.agent_runtime
    state = await runtime.get_state(conv.id, user.id)
    messages = list(state.values.get("messages", [])) if state and state.values else []
    todos = list(state.values.get("todos", []) or []) if state and state.values else []
    pending = None
    for task in getattr(state, "tasks", ()) or ():
        for intr in getattr(task, "interrupts", ()) or ():
            pending = {"id": getattr(intr, "id", None), "value": getattr(intr, "value", None)}
    run = run_manager.active(conv.id)
    history = messages
    if run is not None and run.history_len is not None:
        # The in-flight turn is delivered by replaying the run's event stream
        # (GET /api/chat/{id}/events?after=0); return only what preceded it.
        history = messages[: run.history_len]
        pending = None
    return ConversationDetail(
        conversation=_out(conv),
        messages=messages_to_history(history),
        todos=todos,
        pending_interrupt=pending,
        context_usage=estimate_context_usage(messages, context_window_for(get_settings())),
        is_running=run is not None,
        run_id=run.run_id if run is not None else None,
        run_seq=run.seq if run is not None else None,
    )


@router.patch("/{conversation_id}", response_model=ConversationOut)
async def update(
    conversation_id: str,
    payload: ConversationUpdate,
    user: User = Depends(get_current_user),
    session: AsyncSession = Depends(get_session),
):
    conv = await get_owned_conversation(session, user, conversation_id)
    if payload.title is not None:
        conv.title = payload.title.strip()[:128] or conv.title
    if payload.project_id is not None:
        project = await get_owned_project(session, user, payload.project_id or None)
        conv.project_id = project.id if project else None
    await session.commit()
    await session.refresh(conv)
    return _out(conv)


@router.delete("/{conversation_id}", status_code=204)
async def delete(
    conversation_id: str,
    request: Request,
    user: User = Depends(get_current_user),
    session: AsyncSession = Depends(get_session),
):
    conv = await get_owned_conversation(session, user, conversation_id)
    await request.app.state.agent_runtime.delete_thread(conv.id)
    await session.delete(conv)
    await session.commit()
    return Response(status_code=204)


async def refresh_conversation_stats(runtime, conversation_id: str, user_id: str) -> None:
    """Recompute counters from the checkpointed thread after a run."""
    try:
        state = await runtime.get_state(conversation_id, user_id)
    except Exception:  # noqa: BLE001
        return
    messages = list(state.values.get("messages", [])) if state and state.values else []
    humans = [m for m in messages if isinstance(m, HumanMessage)]
    ais = [m for m in messages if isinstance(m, AIMessage)]
    tools = [m for m in messages if isinstance(m, ToolMessage)]
    in_tokens = sum(int((m.usage_metadata or {}).get("input_tokens", 0) or 0) for m in ais if getattr(m, "usage_metadata", None))
    out_tokens = sum(int((m.usage_metadata or {}).get("output_tokens", 0) or 0) for m in ais if getattr(m, "usage_metadata", None))
    last_ai_text = ""
    for m in reversed(ais):
        text = m.content if isinstance(m.content, str) else " ".join(str(b.get("text", "")) if isinstance(b, dict) else str(b) for b in m.content)
        if text.strip():
            last_ai_text = text.strip()
            break
    async with session_scope() as session:
        conv = await session.get(Conversation, conversation_id)
        if conv is None:
            return
        conv.message_count = len(humans) + len([m for m in ais if (m.content if isinstance(m.content, str) else True)])
        conv.tool_call_count = len(tools)
        conv.input_tokens = in_tokens
        conv.output_tokens = out_tokens
        if humans and conv.title in ("新对话", ""):
            first = humans[0].content if isinstance(humans[0].content, str) else str(humans[0].content)
            conv.title = first.strip().replace("\n", " ")[:40] or conv.title
        if last_ai_text:
            conv.preview = last_ai_text.replace("\n", " ")[:120]
