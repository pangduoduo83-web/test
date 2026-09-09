"""FastAPI application entry point.

Run with::

    uvicorn app.main:app --reload --port 8000
"""

from __future__ import annotations

import logging
from contextlib import asynccontextmanager
from pathlib import Path

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import FileResponse
from fastapi.staticfiles import StaticFiles

from app import __version__
from app.agent.factory import AgentRuntime
from app.api import admin, auth, branding, chat, conversations, internal, projects, system, tools
from app.config import get_settings
from app.db import init_db
from app.services import settings_store
from app.services.runs import run_manager

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s: %(message)s")
log = logging.getLogger("ioedu")

FRONTEND_DIST = Path(__file__).resolve().parent.parent.parent / "frontend" / "dist"


async def _bootstrap_llm_from_ioedu(settings) -> None:
    """没有单独配 LLM Key 时,复用教学平台默认站点「AI 设置」里的大模型配置(同一把 Key 不配两遍)。"""
    if settings.llm_api_key or not settings.ioedu_api_url or not settings.ioedu_internal_token:
        return
    url = f"{settings.ioedu_api_url.rstrip('/')}/api/platform/ai-config/{settings.ioedu_default_tenant}"
    import asyncio

    import httpx

    data: dict = {}
    # 主系统可能正在重启(整栈一起 up 时常见),多等几轮再放弃
    for attempt in range(1, 13):
        try:
            async with httpx.AsyncClient(timeout=5.0) as client:
                res = await client.get(url, headers={"Authorization": f"Bearer {settings.ioedu_internal_token}"})
            if res.status_code == 200:
                data = (res.json() or {}).get("data") or {}
                break
            log.warning("IOEDU ai-config returned HTTP %s (attempt %d)", res.status_code, attempt)
        except Exception as exc:  # noqa: BLE001
            log.warning("Could not fetch LLM config from IOEDU (attempt %d): %s", attempt, exc)
        await asyncio.sleep(5)
    if not data.get("apiKey"):
        log.warning("IOEDU default site has no LLM API key configured; KiCad assistant has no model")
        return
    settings.llm_provider = "custom"
    settings.llm_api_key = str(data["apiKey"])
    if data.get("baseUrl"):
        settings.llm_base_url = str(data["baseUrl"]).rstrip("/")
    if data.get("model"):
        settings.llm_model = str(data["model"])
    log.info("LLM config taken from IOEDU: %s @ %s", settings.llm_model, settings.llm_base_url)


@asynccontextmanager
async def lifespan(app: FastAPI):
    settings = get_settings()
    await init_db()
    try:
        overrides = await settings_store.load_overrides(settings.jwt_secret)
        changed = settings_store.apply_overrides(settings, overrides)
        if changed:
            log.info("Applied %d admin setting override(s): %s", len(changed), ", ".join(changed))
    except Exception:
        log.exception("Could not load admin settings from the database; using .env values")
    await _bootstrap_llm_from_ioedu(settings)
    runtime = AgentRuntime(settings)
    app.state.agent_runtime = runtime
    try:
        await runtime.start()
    except Exception:
        log.exception("Agent runtime failed to start; API will report 503 for chat")
    yield
    await run_manager.shutdown()
    await runtime.stop()


def create_app() -> FastAPI:
    settings = get_settings()
    app = FastAPI(title=settings.app_name, version=__version__, lifespan=lifespan)
    app.add_middleware(
        CORSMiddleware,
        allow_origins=settings.cors_origins or ["*"],
        allow_credentials=True,
        allow_methods=["*"],
        allow_headers=["*"],
    )
    for r in (auth.router, projects.router, conversations.router, chat.router, tools.router, system.router, admin.router, branding.router, internal.router):
        app.include_router(r)

    if FRONTEND_DIST.exists():
        app.mount("/assets", StaticFiles(directory=FRONTEND_DIST / "assets"), name="assets")

        @app.get("/{full_path:path}", include_in_schema=False)
        async def spa(full_path: str):  # noqa: ARG001
            index = FRONTEND_DIST / "index.html"
            candidate = FRONTEND_DIST / full_path
            if full_path and candidate.is_file():
                return FileResponse(candidate)
            return FileResponse(index)

    return app


app = create_app()
