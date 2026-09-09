"""Background runs survive subscriber disconnects and support re-attachment."""

from __future__ import annotations

import asyncio
from typing import Any

import pytest

from app.services.runs import RunManager


async def _collect(run, after: int = 0, limit: int | None = None) -> list[dict[str, Any]]:
    out: list[dict[str, Any]] = []
    async for ev in run.subscribe(after):
        out.append(ev)
        if limit is not None and len(out) >= limit:
            break
    return out


def _slow_source(n: int, delay: float):
    async def source():
        yield {"type": "run_start"}
        for i in range(n):
            await asyncio.sleep(delay)
            yield {"type": "text_delta", "delta": f"t{i}"}
        yield {"type": "run_end", "interrupted": False}

    return source


@pytest.mark.asyncio
async def test_run_outlives_subscriber_and_replays_on_reattach():
    manager = RunManager()
    finished: list[str] = []

    async def on_finish(run):
        finished.append(run.run_id)

    run = manager.start("conv", "u1", _slow_source(6, 0.02), on_finish=on_finish)
    assert manager.is_running("conv")

    # First subscriber reads two events and "disconnects".
    head = await _collect(run, limit=2)
    assert [e["type"] for e in head] == ["run_start", "text_delta"]
    assert head[-1]["seq"] == 2
    assert manager.is_running("conv"), "dropping a subscriber must not cancel the run"

    # Re-attach from where we left off: no duplicates, no gaps.
    tail = await _collect(run, after=head[-1]["seq"])
    seqs = [e["seq"] for e in head + tail]
    assert seqs == list(range(1, len(seqs) + 1))
    assert tail[-1]["type"] == "run_end"
    assert not manager.is_running("conv")
    assert finished == [run.run_id]

    # A late subscriber (within the retention window) still gets the whole stream.
    replay = await _collect(manager.get("conv"))
    assert [e["seq"] for e in replay] == seqs
    assert manager.active_count == 0


@pytest.mark.asyncio
async def test_cancel_emits_error_then_run_end_and_frees_the_conversation():
    manager = RunManager()
    run = manager.start("conv", "u1", _slow_source(100, 0.05))
    await asyncio.sleep(0.12)
    assert manager.cancel("conv") is True
    events = await _collect(run)
    types = [e["type"] for e in events]
    assert types[0] == "run_start"
    assert types[-2:] == ["error", "run_end"]
    assert events[-2]["code"] == "cancelled" and events[-2]["recoverable"] is True
    assert events[-1].get("cancelled") is True
    assert run.cancelled and not manager.is_running("conv")
    assert manager.cancel("conv") is False

    # The conversation can start a new run right away.
    again = manager.start("conv", "u1", _slow_source(1, 0))
    await _collect(again)
    assert not manager.is_running("conv")


@pytest.mark.asyncio
async def test_second_run_is_rejected_while_active():
    manager = RunManager()
    manager.start("conv", "u1", _slow_source(3, 0.05))
    with pytest.raises(RuntimeError):
        manager.start("conv", "u1", _slow_source(1, 0))
    await manager.shutdown()
    assert not manager.is_running("conv")
