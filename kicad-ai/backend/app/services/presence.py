"""Online-user presence (WebSocket fan-out).

Multi-tenant: every online user carries the site code of its account, and the
presence snapshot sent to a socket only lists users of the same site.
"""

from __future__ import annotations

import asyncio
import json
from dataclasses import dataclass, field
from datetime import datetime, timezone
from typing import Any

from fastapi import WebSocket


@dataclass
class PresenceUser:
    id: str
    display_name: str
    username: str
    avatar_color: str
    tenant: str = "default"
    sockets: set[WebSocket] = field(default_factory=set)
    since: datetime = field(default_factory=lambda: datetime.now(timezone.utc))
    status: str = "idle"  # idle | busy


class PresenceHub:
    def __init__(self) -> None:
        self._users: dict[str, PresenceUser] = {}
        self._lock = asyncio.Lock()

    async def connect(self, ws: WebSocket, user: Any) -> None:
        async with self._lock:
            entry = self._users.get(user.id)
            if entry is None:
                entry = PresenceUser(user.id, user.display_name, user.username, user.avatar_color,
                                     getattr(user, "tenant", None) or "default")
                self._users[user.id] = entry
            entry.sockets.add(ws)
        await self.broadcast(tenant=entry.tenant)

    async def disconnect(self, ws: WebSocket, user_id: str) -> None:
        tenant = "default"
        async with self._lock:
            entry = self._users.get(user_id)
            if entry:
                tenant = entry.tenant
                entry.sockets.discard(ws)
                if not entry.sockets:
                    self._users.pop(user_id, None)
        await self.broadcast(tenant=tenant)

    async def set_status(self, user_id: str, status: str) -> None:
        entry = self._users.get(user_id)
        if entry and entry.status != status:
            entry.status = status
            await self.broadcast(tenant=entry.tenant)

    def snapshot(self, tenant: str | None = None) -> dict[str, Any]:
        """Online users; ``tenant=None`` returns everyone (internal statistics only)."""
        users = [
            {
                "id": u.id,
                "display_name": u.display_name,
                "username": u.username,
                "avatar_color": u.avatar_color,
                "status": u.status,
                "since": u.since.isoformat(),
                "tenant": u.tenant,
            }
            for u in self._users.values()
            if tenant is None or u.tenant == tenant
        ]
        return {"type": "presence", "count": len(users), "users": users}

    async def broadcast(self, message: dict[str, Any] | None = None, tenant: str | None = None) -> None:
        """Push presence to sockets. Without an explicit message each site receives only its own list."""
        dead: list[tuple[str, WebSocket]] = []
        payload_cache: dict[str, str] = {}
        for uid, entry in list(self._users.items()):
            if tenant is not None and entry.tenant != tenant:
                continue
            if message is not None:
                payload = json.dumps(message, ensure_ascii=False)
            else:
                payload = payload_cache.get(entry.tenant)
                if payload is None:
                    payload = json.dumps(self.snapshot(entry.tenant), ensure_ascii=False)
                    payload_cache[entry.tenant] = payload
            for ws in list(entry.sockets):
                try:
                    await ws.send_text(payload)
                except Exception:  # noqa: BLE001
                    dead.append((uid, ws))
        for uid, ws in dead:
            entry = self._users.get(uid)
            if entry:
                entry.sockets.discard(ws)
                if not entry.sockets:
                    self._users.pop(uid, None)

    async def notify_user(self, user_id: str, message: dict[str, Any]) -> None:
        entry = self._users.get(user_id)
        if not entry:
            return
        payload = json.dumps(message, ensure_ascii=False)
        for ws in list(entry.sockets):
            try:
                await ws.send_text(payload)
            except Exception:  # noqa: BLE001
                entry.sockets.discard(ws)


presence_hub = PresenceHub()
