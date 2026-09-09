"""Server-to-server endpoints for the IOEDU teaching platform (big screen / admin dashboards).

Protected by the shared ``IOEDU_INTERNAL_TOKEN`` (same value as IOEDU's PLATFORM_TOKEN);
never exposed through the public nginx location, but the token check makes it safe anyway.
"""

from __future__ import annotations

from datetime import datetime, timedelta, timezone

from fastapi import APIRouter, Depends, Header, HTTPException, Request, status
from sqlalchemy import func, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.config import get_settings
from app.db import Conversation, Project, User, get_session
from app.services.presence import presence_hub
from app.services.runs import run_manager

router = APIRouter(prefix="/api/internal", tags=["internal"])


def _check_token(x_internal_token: str = Header(default="")) -> None:
    expected = get_settings().ioedu_internal_token
    if not expected or x_internal_token != expected:
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "invalid internal token")


@router.get("/stats", dependencies=[Depends(_check_token)])
async def tenant_stats(request: Request, tenant: str = "default", session: AsyncSession = Depends(get_session)):
    """Usage snapshot for one IOEDU site: users, conversations, tool calls, tokens, online now."""
    now = datetime.now(timezone.utc)
    tenant = tenant.lower()
    in_tenant = User.tenant == tenant
    users_total = (await session.execute(select(func.count()).select_from(User).where(in_tenant))).scalar_one()
    active_24h = (
        await session.execute(select(func.count()).select_from(User).where(in_tenant, User.last_login_at >= now - timedelta(hours=24)))
    ).scalar_one()
    projects_total = (
        await session.execute(select(func.count()).select_from(Project).join(User, User.id == Project.owner_id).where(in_tenant))
    ).scalar_one()
    conv = (
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
    conv_today = (
        await session.execute(
            select(func.count())
            .select_from(Conversation)
            .join(User, User.id == Conversation.owner_id)
            .where(in_tenant, Conversation.updated_at >= now - timedelta(hours=24))
        )
    ).scalar_one()
    online = presence_hub.snapshot(tenant)["count"]
    runtime = request.app.state.agent_runtime
    return {
        "tenant": tenant,
        "users": int(users_total),
        "activeUsers24h": int(active_24h),
        "projects": int(projects_total),
        "conversations": int(conv[0]),
        "conversationsActive24h": int(conv_today),
        "messages": int(conv[1]),
        "toolCalls": int(conv[2]),
        "inputTokens": int(conv[3]),
        "outputTokens": int(conv[4]),
        "online": online,
        "activeRuns": run_manager.active_count,
        "agentReady": runtime.agent is not None,
        "toolCount": len(runtime.tools),
        "model": runtime.model_label,
    }
