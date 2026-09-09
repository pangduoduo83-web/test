"""Async SQLAlchemy engine / session helpers."""

from __future__ import annotations

from collections.abc import AsyncIterator
from contextlib import asynccontextmanager

from sqlalchemy.ext.asyncio import AsyncSession, async_sessionmaker, create_async_engine

from app.config import get_settings

from .models import Base

_engine = None
_session_factory: async_sessionmaker[AsyncSession] | None = None


def get_engine():
    global _engine, _session_factory
    if _engine is None:
        settings = get_settings()
        _engine = create_async_engine(settings.resolved_database_url, echo=settings.debug)
        _session_factory = async_sessionmaker(_engine, expire_on_commit=False)
    return _engine


def get_session_factory() -> async_sessionmaker[AsyncSession]:
    get_engine()
    assert _session_factory is not None
    return _session_factory


async def init_db() -> None:
    engine = get_engine()
    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.create_all)
        await _migrate(conn)


# Columns added after the first release. create_all() never alters existing
# tables, so add them explicitly (idempotent, no Alembic needed for this size).
_ADDED_COLUMNS = [
    ("users", "last_login_at", "TIMESTAMP"),
    ("projects", "design_constraints", "TEXT NOT NULL DEFAULT '{}'"),
    ("users", "tenant", "VARCHAR(32) NOT NULL DEFAULT 'default'"),
    ("users", "external_id", "VARCHAR(64)"),
]


async def _migrate(conn) -> None:
    from sqlalchemy import inspect, text

    def existing_columns(sync_conn, table: str) -> set[str]:
        return {c["name"] for c in inspect(sync_conn).get_columns(table)}

    for table, column, ddl_type in _ADDED_COLUMNS:
        cols = await conn.run_sync(existing_columns, table)
        if column not in cols:
            await conn.execute(text(f"ALTER TABLE {table} ADD COLUMN {column} {ddl_type}"))


async def get_session() -> AsyncIterator[AsyncSession]:
    """FastAPI dependency."""
    async with get_session_factory()() as session:
        yield session


@asynccontextmanager
async def session_scope() -> AsyncIterator[AsyncSession]:
    async with get_session_factory()() as session:
        try:
            yield session
            await session.commit()
        except Exception:
            await session.rollback()
            raise
