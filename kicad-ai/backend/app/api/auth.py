"""Register / login / current user."""

from __future__ import annotations

import random
from datetime import datetime, timezone

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy import func, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.auth import (
    create_access_token,
    get_current_user,
    hash_password,
    is_platform_admin,
    verify_password,
)
from app.auth.deps import get_user_by_username
from app.config import get_settings
from app.db import User, get_session
from app.schemas import LoginRequest, RegisterRequest, TokenResponse, UserOut

router = APIRouter(prefix="/api/auth", tags=["auth"])

AVATAR_COLORS = ["#6366f1", "#8b5cf6", "#ec4899", "#f59e0b", "#10b981", "#0ea5e9", "#ef4444", "#14b8a6"]


def _user_out(user: User) -> UserOut:
    return UserOut(
        id=user.id,
        username=user.username,
        display_name=user.display_name,
        email=user.email,
        role=user.role,
        avatar_color=user.avatar_color,
        created_at=user.created_at,
        tenant=user.tenant or "default",
        platform_admin=is_platform_admin(user),
        sso=bool(user.external_id),
    )


@router.post("/register", response_model=TokenResponse)
async def register(payload: RegisterRequest, session: AsyncSession = Depends(get_session)):
    settings = get_settings()
    if not settings.allow_registration:
        raise HTTPException(status.HTTP_403_FORBIDDEN, "当前未开放注册")
    if await get_user_by_username(session, payload.username):
        raise HTTPException(status.HTTP_409_CONFLICT, "用户名已存在")
    count = (await session.execute(select(func.count()).select_from(User))).scalar_one()
    user = User(
        username=payload.username,
        display_name=payload.display_name or payload.username,
        email=payload.email,
        password_hash=hash_password(payload.password),
        role="admin" if count == 0 else "user",
        avatar_color=random.choice(AVATAR_COLORS),
    )
    user.last_login_at = datetime.now(timezone.utc)
    session.add(user)
    await session.commit()
    await session.refresh(user)
    return TokenResponse(access_token=create_access_token(user.id), user=_user_out(user))


@router.post("/login", response_model=TokenResponse)
async def login(payload: LoginRequest, session: AsyncSession = Depends(get_session)):
    user = await get_user_by_username(session, payload.username)
    if user is None or not verify_password(payload.password, user.password_hash):
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "用户名或密码错误")
    if not user.is_active:
        raise HTTPException(status.HTTP_403_FORBIDDEN, "账号已被禁用")
    user.last_login_at = datetime.now(timezone.utc)
    await session.commit()
    return TokenResponse(access_token=create_access_token(user.id), user=_user_out(user))


@router.get("/me", response_model=UserOut)
async def me(user: User = Depends(get_current_user)):
    return _user_out(user)
