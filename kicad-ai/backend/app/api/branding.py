"""Branding: public logo endpoint + admin upload/reset."""

from __future__ import annotations

import shutil
from pathlib import Path

from fastapi import (
    APIRouter,
    Depends,
    File,
    HTTPException,
    Response,
    UploadFile,
    status,
)
from fastapi.responses import FileResponse

from app.auth import require_platform_admin
from app.config import Settings, get_settings
from app.db import User
from app.services import settings_store

router = APIRouter(tags=["branding"])

ALLOWED = {".png": "image/png", ".jpg": "image/jpeg", ".jpeg": "image/jpeg", ".svg": "image/svg+xml", ".webp": "image/webp", ".ico": "image/x-icon"}
MAX_BYTES = 2 * 1024 * 1024


def branding_dir(settings: Settings) -> Path:
    d = settings.data_dir / "branding"
    d.mkdir(parents=True, exist_ok=True)
    return d


def logo_path(settings: Settings) -> Path | None:
    if not settings.app_logo:
        return None
    p = branding_dir(settings) / Path(settings.app_logo).name
    return p if p.is_file() else None


def logo_url(settings: Settings) -> str | None:
    p = logo_path(settings)
    if p is None:
        return None
    return f"/api/branding/logo?v={int(p.stat().st_mtime)}"


def public_branding(settings: Settings) -> dict:
    return {
        "app_name": settings.app_name,
        "app_tagline": settings.app_tagline,
        "logo_url": logo_url(settings),
        "app_footer": settings.app_footer,
        "app_copyright": settings.app_copyright,
    }


@router.get("/api/branding/logo")
async def get_logo():
    settings = get_settings()
    p = logo_path(settings)
    if p is None:
        raise HTTPException(status.HTTP_404_NOT_FOUND, "未设置 Logo")
    return FileResponse(p, media_type=ALLOWED.get(p.suffix.lower(), "application/octet-stream"), headers={"Cache-Control": "public, max-age=86400"})


@router.post("/api/admin/branding/logo")
async def upload_logo(file: UploadFile = File(...), admin: User = Depends(require_platform_admin)):
    settings = get_settings()
    suffix = Path(file.filename or "").suffix.lower()
    if suffix not in ALLOWED:
        raise HTTPException(status.HTTP_400_BAD_REQUEST, "仅支持 PNG / JPG / SVG / WebP / ICO")
    data = await file.read()
    if len(data) > MAX_BYTES:
        raise HTTPException(status.HTTP_413_REQUEST_ENTITY_TOO_LARGE, "Logo 文件不能超过 2 MB")
    d = branding_dir(settings)
    for old in d.glob("logo.*"):
        old.unlink(missing_ok=True)
    target = d / f"logo{suffix}"
    target.write_bytes(data)
    cleaned = await settings_store.save_overrides({"app_logo": target.name}, settings.jwt_secret, admin.username)
    settings_store.apply_overrides(settings, cleaned)
    return public_branding(settings)


@router.delete("/api/admin/branding/logo")
async def delete_logo(admin: User = Depends(require_platform_admin)):
    settings = get_settings()
    d = branding_dir(settings)
    for old in d.glob("logo.*"):
        old.unlink(missing_ok=True)
    cleaned = await settings_store.save_overrides({"app_logo": ""}, settings.jwt_secret, admin.username)
    settings_store.apply_overrides(settings, cleaned)
    shutil.rmtree(d, ignore_errors=True)
    return Response(status_code=204)
