"""FastAPI dependencies for authentication.

Two kinds of bearer tokens are accepted:

* tokens issued by this service (``/api/auth/login``);
* tokens issued by the IOEDU teaching platform (single sign-on).  They carry a
  ``tid`` claim (site code) and ``role``; the first request auto-provisions a
  local user scoped to that site, so every IOEDU site gets its own isolated
  user set, workspaces and conversations without any extra step.
"""

from __future__ import annotations

import base64
import logging
import random
import secrets
from datetime import datetime, timezone

import jwt
from fastapi import Depends, HTTPException, status
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.config import get_settings
from app.db import User, get_session

from .security import decode_access_token, hash_password

log = logging.getLogger(__name__)
_bearer = HTTPBearer(auto_error=False)
_AVATAR_COLORS = ["#6366f1", "#8b5cf6", "#ec4899", "#f59e0b", "#10b981", "#0ea5e9", "#ef4444", "#14b8a6"]


def _ioedu_key_candidates(secret: str) -> list[bytes | str]:
    """IOEDU 后端用 jjwt 0.9 签名,它把密钥字符串先按 Base64 解码再做 HMAC;PyJWT 用原始字节。两种都试。"""
    keys: list[bytes | str] = []
    try:
        padded = secret + "=" * (-len(secret) % 4)
        decoded = base64.b64decode(padded, validate=False)
        if decoded:
            keys.append(decoded)
    except Exception:  # noqa: BLE001
        pass
    keys.append(secret)
    return keys


def _decode_ioedu(token: str) -> dict | None:
    settings = get_settings()
    if not settings.ioedu_jwt_secret:
        return None
    for key in _ioedu_key_candidates(settings.ioedu_jwt_secret):
        try:
            payload = jwt.decode(token, key, algorithms=["HS256"])
        except jwt.PyJWTError:
            continue
        return payload if payload.get("sub") and payload.get("tid") else None
    return None


async def _fetch_ioedu_profile(tenant: str, user_id: str) -> dict:
    """Best effort: ask the IOEDU backend for name / email so the account looks right on first login."""
    settings = get_settings()
    if not settings.ioedu_api_url or not settings.ioedu_internal_token:
        return {}
    url = f"{settings.ioedu_api_url.rstrip('/')}/api/platform/users/{tenant}/{user_id}"
    try:
        import httpx  # 运行镜像里随 requirements 安装;放在函数内避免本地缺包时整个模块导入失败

        async with httpx.AsyncClient(timeout=4.0) as client:
            res = await client.get(url, headers={"Authorization": f"Bearer {settings.ioedu_internal_token}"})
        if res.status_code == 200:
            body = res.json()
            return body.get("data") or {}
    except Exception as exc:  # noqa: BLE001
        log.warning("IOEDU profile lookup failed for %s/%s: %s", tenant, user_id, exc)
    return {}


async def _sso_user(payload: dict, session: AsyncSession) -> User | None:
    tenant = str(payload["tid"]).lower()
    ioedu_id = str(payload["sub"])
    external_id = f"{tenant}:{ioedu_id}"
    role = "admin" if payload.get("role") == "ADMIN" else "user"
    user = (await session.execute(select(User).where(User.external_id == external_id))).scalar_one_or_none()
    now = datetime.now(timezone.utc)
    if user is None:
        profile = await _fetch_ioedu_profile(tenant, ioedu_id)
        if profile.get("enabled") is False:
            return None
        user = User(
            username=f"ioedu_{tenant}_{ioedu_id}",
            display_name=str(profile.get("name") or f"用户{ioedu_id}"),
            email=profile.get("email") or None,
            # SSO accounts never log in with a password; store an unguessable hash anyway
            password_hash=hash_password(secrets.token_urlsafe(24)),
            role=role,
            avatar_color=random.choice(_AVATAR_COLORS),
            tenant=tenant,
            external_id=external_id,
        )
        user.last_login_at = now
        session.add(user)
        await session.commit()
        await session.refresh(user)
        log.info("SSO provisioned user %s (tenant %s, role %s)", user.username, tenant, role)
        return user
    changed = False
    if user.role != role:
        user.role = role
        changed = True
    # SQLite 读回的是无时区的时间,统一按 UTC 处理再比较
    last = user.last_login_at
    if last is not None and last.tzinfo is None:
        last = last.replace(tzinfo=timezone.utc)
    if last is None or (now - last).total_seconds() > 3600:
        user.last_login_at = now
        changed = True
    if changed:
        await session.commit()
    if not user.is_active:
        return None
    return user


async def get_user_from_token(token: str, session: AsyncSession) -> User | None:
    payload: dict | None = None
    try:
        payload = decode_access_token(token)
    except jwt.PyJWTError:
        payload = None
    # IOEDU tokens carry a site code; when both services share one secret they decode here too
    if payload is not None and payload.get("tid"):
        return await _sso_user(payload, session)
    if payload is None:
        ioedu_payload = _decode_ioedu(token)
        if ioedu_payload is not None:
            return await _sso_user(ioedu_payload, session)
        return None
    user_id = payload.get("sub")
    if not user_id:
        return None
    user = await session.get(User, user_id)
    if user is None or not user.is_active:
        return None
    return user


async def get_current_user(
    credentials: HTTPAuthorizationCredentials | None = Depends(_bearer),
    session: AsyncSession = Depends(get_session),
) -> User:
    if credentials is None:
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "未登录")
    user = await get_user_from_token(credentials.credentials, session)
    if user is None:
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "登录已过期，请重新登录")
    return user


async def require_admin(user: User = Depends(get_current_user)) -> User:
    if user.role != "admin":
        raise HTTPException(status.HTTP_403_FORBIDDEN, "需要管理员权限")
    return user


def is_platform_admin(user: User) -> bool:
    """Only admins of the IOEDU default site may change global settings (model, skills, system)."""
    return user.role == "admin" and (user.tenant or "default") == get_settings().ioedu_default_tenant


async def require_platform_admin(user: User = Depends(require_admin)) -> User:
    if not is_platform_admin(user):
        raise HTTPException(status.HTTP_403_FORBIDDEN, "模型与系统设置由平台统一管理，站点管理员只能管理本站用户")
    return user


async def get_user_by_username(session: AsyncSession, username: str) -> User | None:
    result = await session.execute(select(User).where(User.username == username))
    return result.scalar_one_or_none()
