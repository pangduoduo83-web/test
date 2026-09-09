"""Per-user scoping for the deepagents virtual filesystem.

The compiled agent is a process-wide singleton, and deepagents 0.7 removed
backend factories, so the filesystem backend handed to ``create_deep_agent``
is shared by every user. :class:`UserWorkspaceBackend` therefore resolves the
*current* user's workspace on every call (from ``get_runtime().context``, the
same mechanism ``StoreBackend`` uses for its namespace) and delegates to a
``FilesystemBackend`` rooted there. ``ls /`` lists the caller's projects, and
no path shape — virtual, legacy ``/data/workspaces/<id>/`` or absolute host
path — can reach another user's directory.
"""

from __future__ import annotations

import threading
from collections.abc import Callable
from typing import Any

from deepagents.backends import FilesystemBackend
from deepagents.backends.protocol import (
    DeleteResult,
    EditResult,
    FileDownloadResponse,
    FileUploadResponse,
    GlobResult,
    GrepResult,
    LsResult,
    ReadResult,
    WriteResult,
)
from langgraph.runtime import get_runtime

from app.agent.context import AgentContext
from app.kicad import workspace as ws

NO_USER_ERROR = "Error: no active user context; the agent filesystem is only available inside a chat run."


def _current_user_id() -> str | None:
    try:
        runtime = get_runtime()
    except RuntimeError:
        return None
    ctx = getattr(runtime, "context", None)
    if isinstance(ctx, AgentContext):
        return ctx.user_id
    user_id = getattr(ctx, "user_id", None)
    return str(user_id) if user_id else None


class UserWorkspaceBackend(FilesystemBackend):
    """``FilesystemBackend`` whose root is the calling user's workspace."""

    def __init__(self, max_file_size_mb: int = 10) -> None:
        super().__init__(root_dir=str(ws.workspace_root()), virtual_mode=True, max_file_size_mb=max_file_size_mb)
        self._max_file_size_mb = max_file_size_mb
        self._delegates: dict[str, FilesystemBackend] = {}
        self._guard = threading.Lock()

    # ------------------------------------------------------------------ scoping
    def _delegate(self, user_id: str) -> FilesystemBackend:
        root = ws.user_root(user_id).resolve()
        key = str(root)
        with self._guard:
            backend = self._delegates.get(key)
            if backend is None:
                backend = self._delegates[key] = FilesystemBackend(
                    root_dir=str(root), virtual_mode=True, max_file_size_mb=self._max_file_size_mb
                )
        return backend

    def _scope(self, path: str | None) -> tuple[FilesystemBackend | None, str, str | None]:
        """Return ``(delegate, normalised_path, error)`` for *path*."""
        user_id = _current_user_id()
        if not user_id:
            return None, path or "/", NO_USER_ERROR
        try:
            virtual = ws.to_virtual(user_id, path or "/")
        except ws.WorkspaceError as exc:
            return None, path or "/", f"Error: {exc}"
        return self._delegate(user_id), virtual, None

    def _scope_pattern(self, user_id: str, pattern: str) -> str:
        """Best-effort: strip an own-user legacy prefix / host root from glob patterns."""
        raw = pattern.replace("\\", "/")
        own_prefix = f"{ws.VIRTUAL_PREFIX}{user_id}/"
        if raw.startswith(own_prefix):
            return "/" + raw[len(own_prefix):]
        host_root = ws.user_root(user_id).resolve().as_posix().rstrip("/") + "/"
        if raw.lower().startswith(host_root.lower()):
            return "/" + raw[len(host_root):]
        return pattern

    def _run(self, path: str | None, fn: Callable[[FilesystemBackend, str], Any], error_cls: type) -> Any:
        delegate, virtual, error = self._scope(path)
        if error:
            return error_cls(error=error)
        return fn(delegate, virtual)

    # ------------------------------------------------------------------ sync API
    def ls(self, path: str) -> LsResult:
        return self._run(path, lambda b, p: b.ls(p), LsResult)

    def read(self, file_path: str, offset: int = 0, limit: int = 2000) -> ReadResult:
        return self._run(file_path, lambda b, p: b.read(p, offset=offset, limit=limit), ReadResult)

    def write(self, file_path: str, content: str) -> WriteResult:
        return self._run(file_path, lambda b, p: b.write(p, content), WriteResult)

    def edit(self, file_path: str, old_string: str, new_string: str, replace_all: bool = False) -> EditResult:
        return self._run(file_path, lambda b, p: b.edit(p, old_string, new_string, replace_all=replace_all), EditResult)

    def glob(self, pattern: str, path: str | None = None) -> GlobResult:
        user_id = _current_user_id()
        if not user_id:
            return GlobResult(error=NO_USER_ERROR)
        return self._run(path, lambda b, p: b.glob(self._scope_pattern(user_id, pattern), p), GlobResult)

    def grep(
        self,
        pattern: str,
        path: str | None = None,
        glob: str | None = None,
        *,
        max_count: int | None = None,
        context_lines: int = 0,
    ) -> GrepResult:
        return self._run(
            path,
            lambda b, p: b.grep(pattern, p, glob, max_count=max_count, context_lines=context_lines),
            GrepResult,
        )

    def delete(self, file_path: str) -> DeleteResult:
        return self._run(file_path, lambda b, p: b.delete(p), DeleteResult)

    def upload_files(self, files: list[tuple[str, bytes]]) -> list[FileUploadResponse]:
        out: list[FileUploadResponse] = []
        for path, content in files:
            delegate, virtual, error = self._scope(path)
            if error:
                out.append(FileUploadResponse(path=path, error="permission_denied"))
                continue
            out.extend(delegate.upload_files([(virtual, content)]))
        return out

    def download_files(self, paths: list[str]) -> list[FileDownloadResponse]:
        out: list[FileDownloadResponse] = []
        for path in paths:
            delegate, virtual, error = self._scope(path)
            if error:
                out.append(FileDownloadResponse(path=path, error="permission_denied"))
                continue
            out.extend(delegate.download_files([virtual]))
        return out

    # The inherited async methods run the sync ones through ``asyncio.to_thread``,
    # which copies the current contextvars, so ``get_runtime()`` still resolves
    # the calling user inside the worker thread.
