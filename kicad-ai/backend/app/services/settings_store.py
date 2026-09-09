"""Admin-editable runtime settings persisted in the database.

Values stored here override the ``.env`` defaults at startup and whenever an
admin saves the form. Secrets (API keys) are encrypted at rest with a key
derived from ``JWT_SECRET``.
"""

from __future__ import annotations

import base64
import hashlib
import json
import logging
from dataclasses import dataclass
from typing import Any

from sqlalchemy import select

from app.agent.models import THINKING_MODES, THINKING_STYLES
from app.config import Settings
from app.db import AppSetting, session_scope

log = logging.getLogger(__name__)


@dataclass(frozen=True)
class FieldSpec:
    key: str
    kind: str  # str | int | float | bool
    secret: bool = False
    group: str = "llm"
    reload_agent: bool = True  # changing it requires rebuilding the agent


EDITABLE: dict[str, FieldSpec] = {
    "llm_provider": FieldSpec("llm_provider", "str"),
    "llm_model": FieldSpec("llm_model", "str"),
    "llm_api_key": FieldSpec("llm_api_key", "str", secret=True),
    "llm_base_url": FieldSpec("llm_base_url", "str"),
    "llm_temperature": FieldSpec("llm_temperature", "float"),
    "llm_context_tokens": FieldSpec("llm_context_tokens", "int", reload_agent=False),
    "llm_max_tokens": FieldSpec("llm_max_tokens", "int"),
    "llm_thinking": FieldSpec("llm_thinking", "str"),
    "llm_thinking_budget": FieldSpec("llm_thinking_budget", "int"),
    "llm_thinking_style": FieldSpec("llm_thinking_style", "str"),
    "enable_subagents": FieldSpec("enable_subagents", "bool", group="agent"),
    "agent_recursion_limit": FieldSpec("agent_recursion_limit", "int", group="agent", reload_agent=False),
    "agent_run_timeout_seconds": FieldSpec("agent_run_timeout_seconds", "int", group="agent", reload_agent=False),
    "agent_run_token_budget": FieldSpec("agent_run_token_budget", "int", group="agent", reload_agent=False),
    "tool_result_max_chars": FieldSpec("tool_result_max_chars", "int", group="agent"),
    "kcaa_mode": FieldSpec("kcaa_mode", "str", group="agent"),
    "kcaa_profile": FieldSpec("kcaa_profile", "str", group="agent"),
    "kcaa_mcp_url": FieldSpec("kcaa_mcp_url", "str", group="agent"),
    "allow_registration": FieldSpec("allow_registration", "bool", group="system", reload_agent=False),
    "app_name": FieldSpec("app_name", "str", group="branding", reload_agent=False),
    "app_tagline": FieldSpec("app_tagline", "str", group="branding", reload_agent=False),
    "app_logo": FieldSpec("app_logo", "str", group="branding", reload_agent=False),
    "app_footer": FieldSpec("app_footer", "str", group="branding", reload_agent=False),
    "app_copyright": FieldSpec("app_copyright", "str", group="branding", reload_agent=False),
}

# Allowed values for enumerated string settings (anything else is rejected on save).
CHOICES: dict[str, tuple[str, ...]] = {
    "llm_thinking": THINKING_MODES,
    "llm_thinking_style": THINKING_STYLES,
}


# ---------------------------------------------------------------------------
# encryption
# ---------------------------------------------------------------------------
def _fernet(secret: str):
    try:
        from cryptography.fernet import Fernet
    except ImportError:  # pragma: no cover
        return None
    key = base64.urlsafe_b64encode(hashlib.sha256(secret.encode("utf-8")).digest())
    return Fernet(key)


def encrypt(value: str, secret: str) -> str:
    f = _fernet(secret)
    if f is None or not value:
        return value
    return "enc:" + f.encrypt(value.encode("utf-8")).decode("ascii")


def decrypt(value: str, secret: str) -> str:
    if not value.startswith("enc:"):
        return value
    f = _fernet(secret)
    if f is None:
        return ""
    try:
        return f.decrypt(value[4:].encode("ascii")).decode("utf-8")
    except Exception:  # noqa: BLE001 — secret rotated; treat as unset
        log.warning("Could not decrypt stored secret (JWT_SECRET changed?); ignoring it")
        return ""


def mask(value: str) -> str:
    if not value:
        return ""
    if len(value) <= 8:
        return "*" * len(value)
    return f"{value[:3]}{'*' * 8}{value[-4:]}"


# ---------------------------------------------------------------------------
# persistence
# ---------------------------------------------------------------------------
def _coerce(spec: FieldSpec, raw: Any) -> Any:
    if raw is None or raw == "":
        if spec.key in CHOICES:
            return CHOICES[spec.key][0]  # empty enum → its default ("default" / "auto")
        return None if spec.kind in ("int", "float") and spec.key == "llm_max_tokens" else ("" if spec.kind == "str" else raw)
    if spec.kind == "bool":
        return raw if isinstance(raw, bool) else str(raw).lower() in ("1", "true", "yes", "on")
    if spec.kind == "int":
        val = int(raw)
        if spec.key == "llm_context_tokens" and val < 16_000:
            val = 128_000
        if spec.key == "llm_thinking_budget" and val < 0:
            raise ValueError("llm_thinking_budget 不能为负数")
        if spec.key == "tool_result_max_chars" and val != 0 and val < 4_000:
            raise ValueError("tool_result_max_chars 至少为 4000（0 表示不限制）")
        if spec.key in ("agent_run_timeout_seconds", "agent_run_token_budget") and val < 0:
            raise ValueError(f"{spec.key} 不能为负数（0 表示不限制）")
        return val
    if spec.kind == "float":
        return float(raw)
    choices = CHOICES.get(spec.key)
    if choices is None:
        return str(raw)
    val = str(raw).strip().lower()
    if val not in choices:
        raise ValueError(f"{spec.key} 只能是 {', '.join(choices)} 之一")
    return val


async def load_overrides(secret: str) -> dict[str, Any]:
    async with session_scope() as session:
        rows = (await session.execute(select(AppSetting))).scalars().all()
    out: dict[str, Any] = {}
    for row in rows:
        spec = EDITABLE.get(row.key)
        if spec is None:
            continue
        try:
            value = json.loads(row.value)
        except json.JSONDecodeError:
            value = row.value
        if spec.secret and isinstance(value, str):
            value = decrypt(value, secret)
        out[row.key] = value
    return out


async def save_overrides(values: dict[str, Any], secret: str, updated_by: str | None) -> dict[str, Any]:
    cleaned: dict[str, Any] = {}
    for key, raw in values.items():
        spec = EDITABLE.get(key)
        if spec is None:
            continue
        cleaned[key] = _coerce(spec, raw)
    async with session_scope() as session:
        for key, value in cleaned.items():
            spec = EDITABLE[key]
            stored = encrypt(value, secret) if spec.secret and isinstance(value, str) else value
            row = await session.get(AppSetting, key)
            if row is None:
                row = AppSetting(key=key, value=json.dumps(stored), is_secret=spec.secret, updated_by=updated_by)
                session.add(row)
            else:
                row.value = json.dumps(stored)
                row.is_secret = spec.secret
                row.updated_by = updated_by
    return cleaned


def apply_overrides(settings: Settings, overrides: dict[str, Any]) -> list[str]:
    """Mutate the cached Settings in place; return the keys that changed."""
    changed: list[str] = []
    for key, value in overrides.items():
        if key not in EDITABLE:
            continue
        if getattr(settings, key, None) != value:
            setattr(settings, key, value)
            changed.append(key)
    return changed


def needs_agent_reload(changed_keys: list[str]) -> bool:
    return any(EDITABLE[k].reload_agent for k in changed_keys if k in EDITABLE)


def public_view(settings: Settings) -> dict[str, Any]:
    """Settings as shown in the admin form (secrets masked)."""
    view: dict[str, Any] = {}
    for key, spec in EDITABLE.items():
        value = getattr(settings, key, None)
        if spec.secret:
            view[key] = mask(value or "")
            view[f"{key}_set"] = bool(value)
        else:
            view[key] = value
    return view
