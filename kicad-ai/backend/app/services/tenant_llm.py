"""Per-site (tenant) LLM configuration, pulled from the IOEDU teaching platform.

Every IOEDU site configures its own model endpoint and API key in its admin
console ("AI 设置").  The hardware assistant reuses exactly that configuration
for the site's users, so each customer pays for its own model usage and the
platform key is never shared across sites.  Configs are cached for a few
minutes; the model router looks them up synchronously per model call.
"""

from __future__ import annotations

import logging
import time
from typing import Any

from app.config import get_settings

log = logging.getLogger(__name__)
_TTL = 300.0


class TenantLlmRegistry:
    def __init__(self) -> None:
        self._cache: dict[str, tuple[float, dict[str, Any] | None]] = {}

    def enforced(self) -> bool:
        """True when running inside the IOEDU platform: sites must bring their own model config."""
        s = get_settings()
        return bool(s.ioedu_api_url and s.ioedu_internal_token)

    def cached(self, tenant: str | None) -> dict[str, Any] | None:
        """Synchronous lookup for the model router (only what ``get`` already fetched)."""
        if not tenant:
            return None
        entry = self._cache.get(tenant)
        return entry[1] if entry else None

    def invalidate(self, tenant: str | None = None) -> None:
        if tenant is None:
            self._cache.clear()
        else:
            self._cache.pop(tenant, None)

    async def get(self, tenant: str | None) -> dict[str, Any] | None:
        tenant = (tenant or "default").lower()
        entry = self._cache.get(tenant)
        if entry and time.time() - entry[0] < _TTL:
            return entry[1]
        cfg = await self._fetch(tenant)
        self._cache[tenant] = (time.time(), cfg)
        return cfg

    async def _fetch(self, tenant: str) -> dict[str, Any] | None:
        s = get_settings()
        if not self.enforced():
            return None
        url = f"{s.ioedu_api_url.rstrip('/')}/api/platform/ai-config/{tenant}"
        try:
            import httpx

            async with httpx.AsyncClient(timeout=5.0) as client:
                res = await client.get(url, headers={"Authorization": f"Bearer {s.ioedu_internal_token}"})
            if res.status_code != 200:
                log.warning("ai-config for site %s returned HTTP %s", tenant, res.status_code)
                return None
            data = (res.json() or {}).get("data") or {}
        except Exception as exc:  # noqa: BLE001
            log.warning("ai-config fetch failed for site %s: %s", tenant, exc)
            # keep a stale value if we have one
            stale = self._cache.get(tenant)
            return stale[1] if stale else None
        if not data.get("apiKey") or data.get("enabled") is False:
            return None
        return {
            "provider": "custom",
            "base_url": str(data.get("baseUrl") or "").rstrip("/"),
            "api_key": str(data["apiKey"]),
            "model": str(data.get("model") or "deepseek-chat"),
            "temperature": float(data.get("temperature") or 0.2),
        }


tenant_llm = TenantLlmRegistry()

NOT_CONFIGURED_MESSAGE = "本站还没有配置大模型:请站点管理员在教学平台后台「AI 设置」里填写模型接口地址与 API Key,保存后即可使用。"
