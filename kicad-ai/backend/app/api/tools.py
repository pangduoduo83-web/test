"""Tool catalog, skills and user memory endpoints."""

from __future__ import annotations

from fastapi import APIRouter, Depends, HTTPException, Request, status
from pydantic import BaseModel

from app.agent.memory import MEMORY_MAX_CHARS, MemoryTooLarge, ensure_user_memory, read_user_memory, write_user_memory
from app.agent.tools.registry import TOOL_CATEGORIES
from app.auth import get_current_user
from app.db import User
from app.schemas import SkillOut, ToolCategoryOut, ToolOut

router = APIRouter(prefix="/api", tags=["tools"])


@router.get("/tools", response_model=list[ToolCategoryOut])
async def tools(request: Request, user: User = Depends(get_current_user)):
    runtime = request.app.state.agent_runtime
    grouped: dict[str, list[ToolOut]] = {k: [] for k in TOOL_CATEGORIES}
    for t in runtime.tool_catalog():
        cat = t["category"] if t["category"] in grouped else "pcb_query"
        grouped[cat].append(
            ToolOut(
                name=t["name"],
                description=t["description"],
                category=cat,
                category_label=TOOL_CATEGORIES[cat]["label"],
                kind=t["kind"],
                source=t["source"],
            )
        )
    out = []
    for key, meta in TOOL_CATEGORIES.items():
        items = grouped[key]
        if not items and key != "harness":
            continue
        out.append(ToolCategoryOut(key=key, label=meta["label"], description=meta["description"], icon=meta["icon"], count=len(items), tools=items))
    return out


@router.get("/skills", response_model=list[SkillOut])
async def skills(request: Request, user: User = Depends(get_current_user)):
    return [SkillOut(**s) for s in request.app.state.agent_runtime.skills_catalog()]


class MemoryPayload(BaseModel):
    content: str


@router.get("/memory")
async def get_memory(request: Request, user: User = Depends(get_current_user)):
    runtime = request.app.state.agent_runtime
    await ensure_user_memory(runtime.store, user.id)
    return {
        "path": "/memories/AGENTS.md",
        "content": await read_user_memory(runtime.store, user.id),
        "max_chars": MEMORY_MAX_CHARS,
    }


@router.put("/memory")
async def put_memory(payload: MemoryPayload, request: Request, user: User = Depends(get_current_user)):
    runtime = request.app.state.agent_runtime
    try:
        await write_user_memory(runtime.store, user.id, payload.content)
    except MemoryTooLarge as exc:
        raise HTTPException(status.HTTP_413_REQUEST_ENTITY_TOO_LARGE, str(exc)) from None
    return {"ok": True, "max_chars": MEMORY_MAX_CHARS}
