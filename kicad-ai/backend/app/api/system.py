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
    return {
        "app_name": settings.app_name,
        "version": __version__,
        "model": runtime.model_label,
        "provider": settings.llm_provider,
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
        # A model choice must be served by the configured endpoint; all entries
        # therefore work with the single API key configured on this server.
        "model_presets": routable_presets(
            settings.llm_provider, settings.llm_base_url, settings.llm_model
        ),
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
