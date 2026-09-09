"""Health, system info and presence WebSocket."""

from __future__ import annotations

import asyncio
import json

from fastapi import APIRouter, Depends, Request, WebSocket, WebSocketDisconnect

from app import __version__
from app.agent.models import THINKING_MODES, routable_presets
from app.api.branding import public_branding
from app.auth import get_current_user, get_user_from_token
from app.config import get_settings
from app.db import User, get_session_factory
from app.kicad import cli as kicad_cli
from app.services.presence import presence_hub
from app.services.runs import run_manager
from app.services.tenant_llm import tenant_llm

router = APIRouter(tags=["system"])


@router.get("/api/health")
async def health(request: Request):
    runtime = request.app.state.agent_runtime
    return {"status": "ok", "version": __version__, "agent_ready": runtime.agent is not None}


@router.get("/api/system/public")
async def public_info(request: Request):
    """Unauthenticated info for the landing / login pages."""
    settings = get_settings()
    runtime = request.app.state.agent_runtime
    return {
        **public_branding(settings),
        "version": __version__,
        "allow_registration": settings.allow_registration,
        "agent_ready": runtime.agent is not None,
        "tool_count": len(runtime.tools),
        "upstream_tool_count": runtime.mcp_tool_count,
        "upstream_source": runtime.upstream_source,
        "online": presence_hub.snapshot()["count"],
        "demo_mode": settings.llm_provider == "mock",
    }


@router.get("/api/system/info")
async def system_info(request: Request, user: User = Depends(get_current_user)):
    settings = get_settings()
    runtime = request.app.state.agent_runtime
    # 多商户:展示该用户所属站点自己配置的模型
    site_llm = await tenant_llm.get(user.tenant or "default")
    model_label = f"custom:{site_llm['model']}" if site_llm else runtime.model_label
    provider = "custom" if site_llm else settings.llm_provider
    presets = routable_presets("custom", site_llm["base_url"], site_llm["model"]) if site_llm else routable_presets(
        settings.llm_provider, settings.llm_base_url, settings.llm_model)
    return {
        "app_name": settings.app_name,
        "version": __version__,
        "model": model_label,
        "provider": provider,
        "site_model_configured": site_llm is not None,
        "site_model_required": tenant_llm.enforced(),
        "context_tokens": settings.llm_context_tokens,
        "thinking": settings.llm_thinking,
        "thinking_modes": list(THINKING_MODES),
        "tool_count": len(runtime.tools),
        "mcp_tool_count": runtime.mcp_tool_count,
        "upstream_source": runtime.upstream_source,
        "kcaa_mode": settings.kcaa_mode,
        "mcp_url": settings.kcaa_mcp_url or None,
        "kicad_cli": kicad_cli.available(),
        "kicad_cli_version": kicad_cli.version(),
        "subagents": settings.enable_subagents,
        "active_runs": run_manager.active_count,
        "online": presence_hub.snapshot(user.tenant or "default")["count"],
        # 可选模型必须由同一端点提供(同一把 Key);多商户时按站点自己的端点给
        "model_presets": presets,
    }


@router.get("/api/presence")
async def presence(user: User = Depends(get_current_user)):
    return presence_hub.snapshot(user.tenant or "default")


@router.websocket("/ws/presence")
async def presence_ws(websocket: WebSocket, token: str = ""):
    async with get_session_factory()() as session:
        user = await get_user_from_token(token, session) if token else None
    if user is None:
        await websocket.close(code=4401)
        return
    await websocket.accept()
    await presence_hub.connect(websocket, user)
    try:
        while True:
            try:
                raw = await asyncio.wait_for(websocket.receive_text(), timeout=30)
            except asyncio.TimeoutError:
                await websocket.send_text(json.dumps({"type": "ping"}))
                continue
            try:
                msg = json.loads(raw)
            except json.JSONDecodeError:
                continue
            if msg.get("type") == "status" and msg.get("status") in ("idle", "busy"):
                await presence_hub.set_status(user.id, msg["status"])
    except WebSocketDisconnect:
        pass
    except Exception:  # noqa: BLE001
        pass
    finally:
        await presence_hub.disconnect(websocket, user.id)
