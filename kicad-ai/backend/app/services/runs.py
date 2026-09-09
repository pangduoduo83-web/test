"""Background agent runs, decoupled from the HTTP connection.

A chat turn used to execute *inside* the SSE response generator, so a page
refresh, a network hiccup or simply opening another conversation cancelled
the agent mid-flight. Here every run is an ``asyncio.Task`` owned by the
process; its events are appended to a sequence-numbered buffer and any number
of SSE subscribers can attach — or re-attach after a disconnect — replaying
what they missed. Only an explicit cancel stops the agent.
"""

from __future__ import annotations

import asyncio
import logging
import time
import uuid
from collections.abc import AsyncIterator, Awaitable, Callable
from dataclasses import dataclass, field
from typing import Any

log = logging.getLogger(__name__)

# Finished runs stay attachable for this long so a client that reconnects just
# after completion still receives the tail of the stream.
FINISHED_RETENTION_SECONDS = 120.0
# Hard cap on buffered events per run (token deltas dominate); older events are
# folded away once exceeded because late subscribers reload history anyway.
MAX_BUFFERED_EVENTS = 20_000


@dataclass
class RunSession:
    run_id: str
    conversation_id: str
    user_id: str
    # Number of checkpointed messages when the run started. History requests
    # during the run are cut here so a client can replay the run's events on
    # top without duplicating the in-flight turn.
    history_len: int | None = None
    started_at: float = field(default_factory=time.time)
    finished_at: float | None = None
    events: list[dict[str, Any]] = field(default_factory=list)
    first_seq: int = 1  # seq of events[0] (buffer may be trimmed from the front)
    seq: int = 0
    done: bool = False
    cancelled: bool = False
    task: asyncio.Task | None = None
    _cond: asyncio.Condition = field(default_factory=asyncio.Condition, repr=False)

    # ------------------------------------------------------------------ producer
    async def publish(self, event: dict[str, Any]) -> None:
        async with self._cond:
            self.seq += 1
            event = {**event, "seq": self.seq, "run_id": self.run_id}
            self.events.append(event)
            if len(self.events) > MAX_BUFFERED_EVENTS:
                drop = len(self.events) - MAX_BUFFERED_EVENTS
                del self.events[:drop]
                self.first_seq += drop
            self._cond.notify_all()

    async def finish(self) -> None:
        async with self._cond:
            self.done = True
            self.finished_at = time.time()
            self._cond.notify_all()

    # ------------------------------------------------------------------ consumer
    async def subscribe(self, after_seq: int = 0) -> AsyncIterator[dict[str, Any]]:
        """Yield events with ``seq > after_seq``, then live events until the run ends."""
        cursor = after_seq
        while True:
            async with self._cond:
                if cursor < self.first_seq - 1:
                    # The client is further behind than our buffer; tell it to reload.
                    cursor = self.first_seq - 1
                    yield {"type": "run_resync", "run_id": self.run_id, "seq": cursor, "conversation_id": self.conversation_id}
                pending = [e for e in self.events if e["seq"] > cursor]
                if not pending:
                    if self.done:
                        return
                    await self._cond.wait()
                    continue
            for event in pending:
                cursor = event["seq"]
                yield event

    @property
    def is_active(self) -> bool:
        return not self.done

    def snapshot(self) -> dict[str, Any]:
        return {
            "run_id": self.run_id,
            "conversation_id": self.conversation_id,
            "started_at": self.started_at,
            "finished_at": self.finished_at,
            "done": self.done,
            "cancelled": self.cancelled,
            "seq": self.seq,
            "history_len": self.history_len,
        }


EventSource = Callable[[], AsyncIterator[dict[str, Any]]]
Finalizer = Callable[[RunSession], Awaitable[None]]


class RunManager:
    """At most one active run per conversation; finished runs linger briefly."""

    def __init__(self) -> None:
        self._runs: dict[str, RunSession] = {}

    # ------------------------------------------------------------------ queries
    def get(self, conversation_id: str) -> RunSession | None:
        self._expire()
        return self._runs.get(conversation_id)

    def active(self, conversation_id: str) -> RunSession | None:
        run = self._runs.get(conversation_id)
        return run if run is not None and run.is_active else None

    def is_running(self, conversation_id: str) -> bool:
        return self.active(conversation_id) is not None

    @property
    def active_count(self) -> int:
        return sum(1 for r in self._runs.values() if r.is_active)

    def _expire(self) -> None:
        now = time.time()
        stale = [
            cid
            for cid, run in self._runs.items()
            if run.done and run.finished_at is not None and now - run.finished_at > FINISHED_RETENTION_SECONDS
        ]
        for cid in stale:
            self._runs.pop(cid, None)

    # ------------------------------------------------------------------ lifecycle
    def start(
        self,
        conversation_id: str,
        user_id: str,
        source: EventSource,
        *,
        on_finish: Finalizer | None = None,
        history_len: int | None = None,
    ) -> RunSession:
        """Start consuming *source* in the background. Raises if a run is active."""
        self._expire()
        if self.is_running(conversation_id):
            raise RuntimeError("conversation already has an active run")
        run = RunSession(
            run_id=uuid.uuid4().hex,
            conversation_id=conversation_id,
            user_id=user_id,
            history_len=history_len,
        )
        self._runs[conversation_id] = run
        run.task = asyncio.create_task(self._drive(run, source, on_finish), name=f"agent-run:{conversation_id[:8]}")
        # A task cancelled before its first step never enters ``_drive``; make
        # sure the session is still sealed so the conversation is not stuck busy.
        run.task.add_done_callback(lambda _t: asyncio.ensure_future(self._seal(run)))
        return run

    @staticmethod
    async def _seal(run: RunSession) -> None:
        if run.done:
            return
        run.cancelled = True
        await run.publish({"type": "run_end", "interrupted": False, "cancelled": True, "usage": {}})
        await run.finish()

    async def _drive(self, run: RunSession, source: EventSource, on_finish: Finalizer | None) -> None:
        # ``run_end`` is held back and published last so an error raised while
        # the source winds down (e.g. cancellation) still precedes it.
        end_event: dict[str, Any] | None = None
        stream = source()
        try:
            async for event in stream:
                if event.get("type") == "run_end":
                    end_event = event
                    continue
                await run.publish(event)
        except asyncio.CancelledError:
            # The canceller only wants the agent stopped; the bookkeeping below
            # must still happen, so the cancellation is absorbed here.
            run.cancelled = True
            current = asyncio.current_task()
            if current is not None and hasattr(current, "uncancel"):
                current.uncancel()
            await run.publish(
                {
                    "type": "error",
                    "code": "cancelled",
                    "recoverable": True,
                    "message": "本次运行已被取消。已完成的修改和进度都已保存；可以继续对话或点击「继续执行」。",
                }
            )
            end_event = {**(end_event or {"interrupted": False, "usage": {}}), "type": "run_end", "cancelled": True}
        except Exception as exc:  # noqa: BLE001 — the source normally reports its own errors
            log.exception("agent run %s crashed", run.run_id)
            await run.publish({"type": "error", "code": "run_failed", "recoverable": False, "message": f"{type(exc).__name__}: {exc}"})
        finally:
            aclose = getattr(stream, "aclose", None)
            if aclose is not None:
                try:
                    await aclose()
                except Exception:  # noqa: BLE001, S110
                    pass
            await run.publish(end_event or {"type": "run_end", "interrupted": False, "usage": {}})
            if on_finish is not None:
                try:
                    await on_finish(run)
                except Exception:  # noqa: BLE001
                    log.exception("run finalizer failed")
            await run.finish()

    def cancel(self, conversation_id: str) -> bool:
        run = self.active(conversation_id)
        if run is None or run.task is None or run.task.done():
            return False
        run.task.cancel()
        return True

    async def shutdown(self) -> None:
        tasks = [r.task for r in self._runs.values() if r.task is not None and not r.task.done()]
        for task in tasks:
            task.cancel()
        if tasks:
            await asyncio.gather(*tasks, return_exceptions=True)
        for run in list(self._runs.values()):
            await self._seal(run)


run_manager = RunManager()
