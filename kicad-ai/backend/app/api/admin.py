"""Admin console API: overview, users, runtime settings (LLM etc.)."""

from __future__ import annotations

import random
import shutil
from datetime import datetime, timedelta, timezone

from fastapi import APIRouter, Depends, HTTPException, Request, Response, status
from sqlalchemy import func, select
from sqlalchemy.ext.asyncio import AsyncSession

from app import __version__
from app.agent.llm import test_chat_model
from app.agent.models import MODEL_PRESETS, THINKING_MODES, THINKING_STYLES
from app.auth import hash_password, require_admin, require_platform_admin
from app.auth.deps import get_user_by_username
from app.config import get_settings
from app.db import Conversation, Project, User, get_session
from app.kicad import cli as kicad_cli
from app.kicad import workspace as ws
from app.schemas import (
    AdminOverview,
    AdminUserCreate,
    AdminUserOut,
    AdminUserUpdate,
    LlmTestRequest,
    ResetPasswordRequest,
    SettingsUpdate,
)
from app.services import settings_store
from app.services.presence import presence_hub
from app.services.runs import run_manager

router = APIRouter(prefix="/api/admin", tags=["admin"], dependencies=[Depends(require_admin)])

AVATAR_COLORS = ["#6366f1", "#8b5cf6", "#ec4899", "#f59e0b", "#10b981", "#0ea5e9", "#ef4444", "#14b8a6"]


# ---------------------------------------------------------------------------
# helpers
# ---------------------------------------------------------------------------
async def _user_stats(session: AsyncSession) -> dict[str, dict[str, int]]:
    proj = await session.execute(select(Project.owner_id, func.count()).group_by(Project.owner_id))
    conv = await session.execute(
        select(
            Conversation.owner_id,
            func.count(),
            func.coalesce(func.sum(Conversation.message_count), 0),
            func.coalesce(func.sum(Conversation.tool_call_count), 0),
        ).group_by(Conversation.owner_id)
    )
    stats: dict[str, dict[str, int]] = {}
    for owner_id, n in proj:
        stats.setdefault(owner_id, {})["project_count"] = int(n)
    for owner_id, n, msgs, tools in conv:
        s = stats.setdefault(owner_id, {})
        s["conversation_count"] = int(n)
        s["message_count"] = int(msgs or 0)
        s["tool_call_count"] = int(tools or 0)
    return stats


def _tenant_of(admin: User) -> str:
    return admin.tenant or "default"


async def _owned_user(session: AsyncSession, admin: User, user_id: str) -> User:
    """A site admin may only touch users of the same site."""
    user = await session.get(User, user_id)
    if user is None or (user.tenant or "default") != _tenant_of(admin):
        raise HTTPException(status.HTTP_404_NOT_FOUND, "用户不存在")
    return user


def _user_out(u: User, stats: dict[str, int] | None = None) -> AdminUserOut:
    stats = stats or {}
    return AdminUserOut(
        id=u.id,
        username=u.username,
        display_name=u.display_name,
        email=u.email,
        role=u.role,
        avatar_color=u.avatar_color,
        is_active=u.is_active,
        tenant=u.tenant or "default",
        sso=bool(u.external_id),
        created_at=u.created_at,
        last_login_at=u.last_login_at,
        project_count=stats.get("project_count", 0),
        conversation_count=stats.get("conversation_count", 0),
        message_count=stats.get("message_count", 0),
        tool_call_count=stats.get("tool_call_count", 0),
    )


def _dir_size(path) -> int:
    total = 0
    try:
        for p in path.rglob("*"):
            if p.is_file():
                total += p.stat().st_size
    except OSError:
        pass
    return total


# ---------------------------------------------------------------------------
# overview
# ---------------------------------------------------------------------------
@router.get("/overview", response_model=AdminOverview)
async def overview(request: Request, admin: User = Depends(require_admin), session: AsyncSession = Depends(get_session)):
    settings = get_settings()
    runtime = request.app.state.agent_runtime
    now = datetime.now(timezone.utc)
    tenant = _tenant_of(admin)
    in_tenant = User.tenant == tenant
    users_total = (await session.execute(select(func.count()).select_from(User).where(in_tenant))).scalar_one()
    users_active = (await session.execute(select(func.count()).select_from(User).where(in_tenant, User.is_active.is_(True)))).scalar_one()
    users_admins = (await session.execute(select(func.count()).select_from(User).where(in_tenant, User.role == "admin"))).scalar_one()
    users_new_7d = (await session.execute(select(func.count()).select_from(User).where(in_tenant, User.created_at >= now - timedelta(days=7)))).scalar_one()
    logins_24h = (await session.execute(select(func.count()).select_from(User).where(in_tenant, User.last_login_at >= now - timedelta(hours=24)))).scalar_one()
    projects_total = (
        await session.execute(select(func.count()).select_from(Project).join(User, User.id == Project.owner_id).where(in_tenant))
    ).scalar_one()
    conv_row = (
        await session.execute(
            select(
                func.count(),
                func.coalesce(func.sum(Conversation.message_count), 0),
                func.coalesce(func.sum(Conversation.tool_call_count), 0),
                func.coalesce(func.sum(Conversation.input_tokens), 0),
                func.coalesce(func.sum(Conversation.output_tokens), 0),
            )
            .select_from(Conversation)
            .join(User, User.id == Conversation.owner_id)
            .where(in_tenant)
        )
    ).one()
    recent = (await session.execute(select(User).where(in_tenant).order_by(User.created_at.desc()).limit(6))).scalars().all()
    stats = await _user_stats(session)
    tenant_user_ids = (await session.execute(select(User.id).where(in_tenant))).scalars().all()
    workspace_bytes = sum(_dir_size(ws.user_root(uid)) for uid in tenant_user_ids)
    return AdminOverview(
        users_total=users_total,
        users_active=users_active,
        users_admins=users_admins,
        users_new_7d=users_new_7d,
        logins_24h=logins_24h,
        projects_total=projects_total,
        conversations_total=int(conv_row[0]),
        messages_total=int(conv_row[1]),
        tool_calls_total=int(conv_row[2]),
        input_tokens_total=int(conv_row[3]),
        output_tokens_total=int(conv_row[4]),
        online_users=presence_hub.snapshot(tenant)["count"],
        active_runs=run_manager.active_count,
        model=runtime.model_label,
        tool_count=len(runtime.tools),
        mcp_tool_count=runtime.mcp_tool_count,
        mcp_url=settings.kcaa_mcp_url or None,
        kicad_cli=kicad_cli.available(),
        subagents=settings.enable_subagents,
        started_at=runtime.started_at,
        last_reload_at=runtime.last_reload_at,
        last_error=runtime.last_error,
        workspace_bytes=workspace_bytes,
        version=__version__,
        recent_users=[_user_out(u, stats.get(u.id)) for u in recent],
    )


# ---------------------------------------------------------------------------
# users
# ---------------------------------------------------------------------------
@router.get("/users", response_model=list[AdminUserOut])
async def list_users(q: str = "", admin: User = Depends(require_admin), session: AsyncSession = Depends(get_session)):
    stmt = select(User).where(User.tenant == _tenant_of(admin)).order_by(User.created_at.desc())
    if q.strip():
        like = f"%{q.strip()}%"
        stmt = stmt.where((User.username.ilike(like)) | (User.display_name.ilike(like)) | (User.email.ilike(like)))
    users = (await session.execute(stmt)).scalars().all()
    stats = await _user_stats(session)
    return [_user_out(u, stats.get(u.id)) for u in users]


@router.post("/users", response_model=AdminUserOut, status_code=201)
async def create_user(payload: AdminUserCreate, admin: User = Depends(require_admin), session: AsyncSession = Depends(get_session)):
    if await get_user_by_username(session, payload.username):
        raise HTTPException(status.HTTP_409_CONFLICT, "用户名已存在")
    user = User(
        username=payload.username,
        display_name=payload.display_name or payload.username,
        email=payload.email,
        password_hash=hash_password(payload.password),
        role=payload.role,
        avatar_color=random.choice(AVATAR_COLORS),
        tenant=_tenant_of(admin),
    )
    session.add(user)
    await session.commit()
    await session.refresh(user)
    return _user_out(user)


@router.patch("/users/{user_id}", response_model=AdminUserOut)
async def update_user(
    user_id: str,
    payload: AdminUserUpdate,
    admin: User = Depends(require_admin),
    session: AsyncSession = Depends(get_session),
):
    user = await _owned_user(session, admin, user_id)
    if user.external_id and payload.role is not None and payload.role != user.role:
        raise HTTPException(status.HTTP_400_BAD_REQUEST, "该账号由教学平台单点登录同步，角色请在教学平台的用户管理里修改")
    if payload.display_name is not None:
        user.display_name = payload.display_name.strip() or user.display_name
    if payload.email is not None:
        user.email = payload.email.strip() or None
    if payload.role is not None and payload.role != user.role:
        if user.id == admin.id:
            raise HTTPException(status.HTTP_400_BAD_REQUEST, "不能修改自己的角色")
        user.role = payload.role
    if payload.is_active is not None and payload.is_active != user.is_active:
        if user.id == admin.id:
            raise HTTPException(status.HTTP_400_BAD_REQUEST, "不能禁用自己")
        user.is_active = payload.is_active
    await _ensure_admin_remains(session, admin)
    await session.commit()
    await session.refresh(user)
    stats = await _user_stats(session)
    return _user_out(user, stats.get(user.id))


@router.post("/users/{user_id}/reset-password", status_code=204)
async def reset_password(
    user_id: str,
    payload: ResetPasswordRequest,
    admin: User = Depends(require_admin),
    session: AsyncSession = Depends(get_session),
):
    user = await _owned_user(session, admin, user_id)
    if user.external_id:
        raise HTTPException(status.HTTP_400_BAD_REQUEST, "该账号由教学平台单点登录，密码请在教学平台重置")
    user.password_hash = hash_password(payload.password)
    await session.commit()
    return Response(status_code=204)


@router.delete("/users/{user_id}", status_code=204)
async def delete_user(
    user_id: str,
    request: Request,
    delete_files: bool = True,
    admin: User = Depends(require_admin),
    session: AsyncSession = Depends(get_session),
):
    if user_id == admin.id:
        raise HTTPException(status.HTTP_400_BAD_REQUEST, "不能删除自己")
    user = await _owned_user(session, admin, user_id)
    conv_ids = (await session.execute(select(Conversation.id).where(Conversation.owner_id == user_id))).scalars().all()
    await session.delete(user)
    await _ensure_admin_remains(session, admin)
    await session.commit()
    runtime = request.app.state.agent_runtime
    for cid in conv_ids:
        try:
            await runtime.delete_thread(cid)
        except Exception:  # noqa: BLE001
            pass
    if delete_files:
        shutil.rmtree(ws.user_root(user_id), ignore_errors=True)
    return Response(status_code=204)


async def _ensure_admin_remains(session: AsyncSession, admin: User) -> None:
    # SSO 站点的管理员由教学平台同步,随时会再登录进来,不必强留;只有本地账号体系才需要保底
    if admin.external_id:
        return
    await session.flush()
    admins = (
        await session.execute(
            select(func.count()).select_from(User).where(User.tenant == _tenant_of(admin), User.role == "admin", User.is_active.is_(True))
        )
    ).scalar_one()
    if admins == 0:
        raise HTTPException(status.HTTP_400_BAD_REQUEST, "系统至少需要保留一个启用状态的管理员")


# ---------------------------------------------------------------------------
# settings
# ---------------------------------------------------------------------------
@router.get("/settings", dependencies=[Depends(require_platform_admin)])
async def get_runtime_settings(request: Request):
    settings = get_settings()
    runtime = request.app.state.agent_runtime
    return {
        "values": settings_store.public_view(settings),
        "fields": [
            {"key": k, "kind": s.kind, "secret": s.secret, "group": s.group, "reload_agent": s.reload_agent}
            for k, s in settings_store.EDITABLE.items()
        ],
        "presets": MODEL_PRESETS,
        "thinking_modes": list(THINKING_MODES),
        "thinking_styles": list(THINKING_STYLES),
        "runtime": {
            "model": runtime.model_label,
            "tool_count": len(runtime.tools),
            "mcp_tool_count": runtime.mcp_tool_count,
            "last_reload_at": runtime.last_reload_at,
            "last_error": runtime.last_error,
        },
    }


@router.put("/settings")
async def put_runtime_settings(payload: SettingsUpdate, request: Request, admin: User = Depends(require_platform_admin)):
    settings = get_settings()
    values = dict(payload.values)
    # Empty secret means "keep the existing key"
    if "llm_api_key" in values and not values["llm_api_key"]:
        values.pop("llm_api_key")
    unknown = [k for k in values if k not in settings_store.EDITABLE]
    if unknown:
        raise HTTPException(status.HTTP_400_BAD_REQUEST, f"未知设置项：{', '.join(unknown)}")
    try:
        cleaned = await settings_store.save_overrides(values, settings.jwt_secret, admin.username)
    except (TypeError, ValueError) as exc:
        raise HTTPException(status.HTTP_400_BAD_REQUEST, f"设置值格式错误：{exc}") from exc
    changed = settings_store.apply_overrides(settings, cleaned)
    reloaded = False
    error: str | None = None
    if settings_store.needs_agent_reload(changed):
        runtime = request.app.state.agent_runtime
        try:
            await runtime.reload()
            reloaded = True
        except Exception as exc:  # noqa: BLE001
            error = f"{type(exc).__name__}: {exc}"
    return {
        "saved": sorted(cleaned.keys()),
        "changed": changed,
        "agent_reloaded": reloaded,
        "error": error,
        "values": settings_store.public_view(settings),
        "runtime": {
            "model": request.app.state.agent_runtime.model_label,
            "tool_count": len(request.app.state.agent_runtime.tools),
            "mcp_tool_count": request.app.state.agent_runtime.mcp_tool_count,
        },
    }


@router.post("/settings/test-llm", dependencies=[Depends(require_platform_admin)])
async def test_llm(payload: LlmTestRequest):
    settings = get_settings()
    update = {
        "llm_provider": payload.llm_provider,
        "llm_model": payload.llm_model,
        "llm_base_url": payload.llm_base_url or "",
    }
    if payload.llm_api_key:
        update["llm_api_key"] = payload.llm_api_key
    if payload.llm_temperature is not None:
        update["llm_temperature"] = payload.llm_temperature
    if payload.llm_thinking is not None:
        update["llm_thinking"] = payload.llm_thinking
    if payload.llm_thinking_budget is not None:
        update["llm_thinking_budget"] = payload.llm_thinking_budget
    if payload.llm_thinking_style is not None:
        update["llm_thinking_style"] = payload.llm_thinking_style
    candidate = settings.model_copy(update=update)
    return await test_chat_model(candidate)
