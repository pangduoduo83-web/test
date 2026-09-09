"""Hand-written additive database migrations remain safe for existing installs."""

from __future__ import annotations

import pytest
from sqlalchemy import inspect, text
from sqlalchemy.ext.asyncio import create_async_engine

from app.db.session import _migrate


@pytest.mark.asyncio
async def test_design_constraints_column_is_added_idempotently(tmp_path):
    engine = create_async_engine(f"sqlite+aiosqlite:///{(tmp_path / 'old.db').as_posix()}")
    try:
        async with engine.begin() as connection:
            await connection.execute(text("CREATE TABLE users (id TEXT PRIMARY KEY)"))
            await connection.execute(text("CREATE TABLE projects (id TEXT PRIMARY KEY)"))
            await _migrate(connection)
            await _migrate(connection)

            def columns(sync_connection, table: str):
                return {column["name"]: column for column in inspect(sync_connection).get_columns(table)}

            project_columns = await connection.run_sync(columns, "projects")
            assert "design_constraints" in project_columns
            row = (
                await connection.execute(
                    text("INSERT INTO projects (id) VALUES ('p1') RETURNING design_constraints")
                )
            ).scalar_one()
            assert row == "{}"
    finally:
        await engine.dispose()
