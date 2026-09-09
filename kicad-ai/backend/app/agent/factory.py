"""Build and own the process-wide deep agent.

One compiled graph serves every user; per-user isolation comes from

* ``thread_id``  → one LangGraph thread per conversation (checkpointer)
* ``AgentContext`` → user id + active project paths (runtime context)
* store namespaces → ``("memories", user_id)`` for long-term memory
"""

from __future__ import annotations

import asyncio
import logging
from contextlib import AsyncExitStack
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from deepagents import FilesystemPermission, create_deep_agent
from deepagents.backends import (
    CompositeBackend,
    FilesystemBackend,
    StoreBackend,
)
from langchain.agents.middleware import TodoListMiddleware
from langchain_core.tools import BaseTool
from langgraph.checkpoint.base import BaseCheckpointSaver
from langgraph.store.base import BaseStore

from app.agent.backends import UserWorkspaceBackend
from app.agent.budget import RunBudgetMiddleware
from app.agent.context import AgentContext
from app.agent.executor import register_tools
from app.agent.llm import build_chat_model
from app.agent.memory import MEMORY_FILE, MEMORY_ROUTE, memory_namespace
from app.agent.middleware import KiCadPolicyMiddleware
from app.agent.model_router import ModelRouter, ModelRouterMiddleware
from app.agent.prompts import build_system_prompt
from app.agent.subagents import build_subagents
from app.agent.tools.kcaa_direct import load_kcaa_tools
from app.agent.tools.mcp import load_mcp_tools
from app.agent.tools.native import NATIVE_TOOLS
from app.agent.tools.registry import TOOL_POLICIES, get_policy
from app.config import Settings
from app.kicad import workspace as ws

log = logging.getLogger(__name__)

SKILLS_DIR = Path(__file__).resolve().parent / "skills"
SKILLS_ROUTE = "/skills/"

HEADLESS_EXCLUDED_TOOLS = {
    "open_project",
    "check_kicad_ipc_connection",
    "save_document",
    "reload_kicad",
}


class AgentRuntime:
    """Lifecycle owner for checkpointer, store, tools and the compiled agent."""

    def __init__(self, settings: Settings):
        self.settings = settings
        self._stack = AsyncExitStack()
        self._reload_lock = asyncio.Lock()
        self.checkpointer: BaseCheckpointSaver | None = None
        self.store: BaseStore | None = None
        self.agent = None
        self.router: ModelRouter | None = None
        self.tools: list[BaseTool] = []
        self.tool_sources: dict[str, str] = {}
        self.mcp_tool_count = 0  # number of upstream (kcaa) tools, direct or MCP
        self.upstream_source: str | None = None
        self.model_label = f"{settings.llm_provider}:{settings.llm_model}"
        self.started_at = datetime.now(timezone.utc)
        self.last_reload_at: datetime | None = None
        self.last_error: str | None = None

    # ------------------------------------------------------------------ setup
    async def start(self) -> None:
        ws.configure(self.settings.workspace_root)
        await self._open_persistence()
        await self._rebuild()

    async def reload(self) -> None:
        """Rebuild model, tools and the compiled agent after settings changed.

        Runs already in flight keep their reference to the previous graph; the
        checkpointer/store are shared so conversations continue seamlessly.
        """
        async with self._reload_lock:
            await self._rebuild()
            self.last_reload_at = datetime.now(timezone.utc)

    async def _rebuild(self) -> None:
        self.model_label = f"{self.settings.llm_provider}:{self.settings.llm_model}"
        self.tool_sources = {}
        try:
            self.tools = await self._collect_tools()
            register_tools(self.tools)  # approved plans are executed server-side by name
            self.agent = self._build_agent()
            self.last_error = None
        except Exception as exc:  # noqa: BLE001 — keep serving with the previous agent
            self.last_error = f"{type(exc).__name__}: {exc}"
            log.exception("Agent rebuild failed")
            raise
        log.info(
            "Agent ready: model=%s tools=%d (native=%d, mcp=%d)",
            self.model_label,
            len(self.tools),
            sum(1 for s in self.tool_sources.values() if s == "native"),
            self.mcp_tool_count,
        )

    async def stop(self) -> None:
        await self._stack.aclose()

    async def _open_persistence(self) -> None:
        s = self.settings
        pg_url = s.database_url if s.database_url.startswith("postgres") else ""
        if pg_url:
            try:
                from langgraph.checkpoint.postgres.aio import AsyncPostgresSaver
                from langgraph.store.postgres.aio import AsyncPostgresStore

                url = pg_url.replace("+asyncpg", "").replace("+psycopg", "")
                self.checkpointer = await self._stack.enter_async_context(AsyncPostgresSaver.from_conn_string(url))
                await self.checkpointer.setup()  # type: ignore[union-attr]
                self.store = await self._stack.enter_async_context(AsyncPostgresStore.from_conn_string(url))
                await self.store.setup()  # type: ignore[union-attr]
                log.info("LangGraph persistence: PostgreSQL")
                return
            except ImportError:
                log.warning("PostgreSQL requested but langgraph-checkpoint-postgres missing; falling back to SQLite")

        from langgraph.checkpoint.sqlite.aio import AsyncSqliteSaver
        from langgraph.store.sqlite import AsyncSqliteStore

        self.checkpointer = await self._stack.enter_async_context(AsyncSqliteSaver.from_conn_string(s.resolved_checkpoint_db))
        await self.checkpointer.setup()  # type: ignore[union-attr]
        self.store = await self._stack.enter_async_context(AsyncSqliteStore.from_conn_string(s.resolved_store_db))
        await self.store.setup()  # type: ignore[union-attr]
        log.info("LangGraph persistence: SQLite (%s)", s.data_dir)

    async def _collect_tools(self) -> list[BaseTool]:
        tools: dict[str, BaseTool] = {}
        for t in NATIVE_TOOLS:
            if t.name in HEADLESS_EXCLUDED_TOOLS:
                continue
            tools[t.name] = t
            self.tool_sources[t.name] = "native"
        mode = (self.settings.kcaa_mode or "off").lower()
        upstream: list[BaseTool] = []
        source = "kcaa"
        if mode == "direct":
            upstream = await load_kcaa_tools(self.settings)
        elif mode == "mcp":
            upstream = await load_mcp_tools(self.settings)
            source = "mcp"
        self.mcp_tool_count = len(upstream)
        self.upstream_source = source if upstream else None
        for t in upstream:
            if t.name in tools:
                continue  # native implementation is workspace-aware; keep it
            if t.name in HEADLESS_EXCLUDED_TOOLS:
                log.info("Excluding desktop GUI tool from web agent: %s", t.name)
                continue
            tools[t.name] = t
            self.tool_sources[t.name] = source
        return list(tools.values())

    def _interrupt_map(self) -> dict[str, bool]:
        names = {t.name for t in self.tools}
        # File mutations are approved once through submit_change_plan and then
        # enforced by KiCadPolicyMiddleware. Keep direct confirmation only for
        # non-plan operations such as restoring a historical snapshot.
        confirm = {
            n for n, p in TOOL_POLICIES.items() if p.confirm and p.kind != "file_mutation"
        }
        confirm |= {
            n for n in self.settings.hitl_tools if get_policy(n).kind != "file_mutation"
        }
        return {n: True for n in confirm if n in names}

    def _build_agent(self):
        s = self.settings
        model = build_chat_model(s)
        self.router = ModelRouter(s, default=model)
        # The default backend resolves the *calling* user's workspace on every
        # call, so `ls /` shows that user's projects and no path shape can reach
        # another user's directory (see app.agent.backends).
        backend = CompositeBackend(
            default=UserWorkspaceBackend(),
            routes={
                MEMORY_ROUTE: StoreBackend(namespace=lambda rt: memory_namespace(rt.context.user_id)),
                SKILLS_ROUTE: FilesystemBackend(root_dir=str(SKILLS_DIR), virtual_mode=True),
            },
            # Offloaded conversation history etc. lands in a hidden per-user folder.
            artifacts_root="/.agent/",
        )
        interrupt_on = self._interrupt_map()
        policy = KiCadPolicyMiddleware(result_max_chars=s.tool_result_max_chars)
        # Read at call time so admins can change the budget without a rebuild.
        budget = RunBudgetMiddleware(lambda: self.settings.agent_run_token_budget)
        subagents = (
            build_subagents(
                self.tools,
                interrupt_on,
                middleware=[ModelRouterMiddleware(self.router), budget],
                policy=policy,
            )
            if s.enable_subagents
            else []
        )
        return create_deep_agent(
            model=model,
            tools=self.tools,
            system_prompt=build_system_prompt(),
            middleware=[ModelRouterMiddleware(self.router), budget, policy, TodoListMiddleware()],
            subagents=subagents,
            skills=[SKILLS_ROUTE],
            memory=[MEMORY_FILE],
            permissions=[FilesystemPermission(operations=["write"], paths=[f"{SKILLS_ROUTE}**"], mode="deny")],
            backend=backend,
            interrupt_on=interrupt_on or None,
            context_schema=AgentContext,
            checkpointer=self.checkpointer,
            store=self.store,
            name="kicad-ai-assistant",
        )

    # ------------------------------------------------------------------ query
    def tool_catalog(self) -> list[dict[str, Any]]:
        out = []
        for t in self.tools:
            policy = get_policy(t.name)
            out.append(
                {
                    "name": t.name,
                    "description": (t.description or "").strip().split("\n")[0][:200],
                    "category": policy.category,
                    "kind": policy.kind,
                    "source": self.tool_sources.get(t.name, "native"),
                    "confirm": policy.confirm,
                }
            )
        return out

    def skills_catalog(self) -> list[dict[str, Any]]:
        skills = []
        for skill_file in sorted(SKILLS_DIR.glob("*/SKILL.md")):
            text = skill_file.read_text(encoding="utf-8")
            meta = _front_matter(text)
            skills.append(
                {
                    "name": meta.get("name", skill_file.parent.name),
                    "description": meta.get("description", ""),
                    "path": f"{SKILLS_ROUTE}{skill_file.parent.name}/SKILL.md",
                    "content": text,
                }
            )
        return skills

    def thread_config(self, conversation_id: str, user_id: str) -> dict[str, Any]:
        return {
            "configurable": {"thread_id": conversation_id, "user_id": user_id},
            "recursion_limit": self.settings.agent_recursion_limit,
            "metadata": {"user_id": user_id, "conversation_id": conversation_id},
        }

    async def get_state(self, conversation_id: str, user_id: str):
        assert self.agent is not None
        return await self.agent.aget_state(self.thread_config(conversation_id, user_id))

    async def delete_thread(self, conversation_id: str) -> None:
        if self.checkpointer is not None and hasattr(self.checkpointer, "adelete_thread"):
            await self.checkpointer.adelete_thread(conversation_id)


def _front_matter(text: str) -> dict[str, str]:
    meta: dict[str, str] = {}
    if not text.startswith("---"):
        return meta
    end = text.find("\n---", 3)
    if end == -1:
        return meta
    for line in text[3:end].splitlines():
        if ":" in line:
            k, _, v = line.partition(":")
            meta[k.strip()] = v.strip().strip('"').strip("'")
    return meta
