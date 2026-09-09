"""Admin console API: users, runtime settings, hot reload (mock model)."""

from __future__ import annotations

import os
import json
import uuid
from pathlib import Path

import pytest
import pytest_asyncio
from httpx import ASGITransport, AsyncClient


@pytest_asyncio.fixture
async def client(tmp_path: Path, monkeypatch):
    monkeypatch.setenv("LLM_PROVIDER", "mock")
    monkeypatch.setenv("LLM_MODEL", "mock-kicad")
    monkeypatch.setenv("DATA_DIR", str(tmp_path / "data"))
    monkeypatch.setenv("WORKSPACE_ROOT", str(tmp_path / "ws"))
    monkeypatch.setenv("KCAA_MCP_URL", "")
    monkeypatch.setenv("JWT_SECRET", "test-secret")
    monkeypatch.setenv("DATABASE_URL", f"sqlite+aiosqlite:///{(tmp_path / 'app.db').as_posix()}")

    from app import config
    from app.db import session as db_session

    config.get_settings.cache_clear()
    db_session._engine = None
    db_session._session_factory = None

    from app.main import app

    async with app.router.lifespan_context(app):
        async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as c:
            yield c
    config.get_settings.cache_clear()
    db_session._engine = None
    db_session._session_factory = None


async def _register(client: AsyncClient, name: str) -> dict[str, str]:
    r = await client.post("/api/auth/register", json={"username": name, "password": "secret123"})
    assert r.status_code == 200, r.text
    return {"Authorization": f"Bearer {r.json()['access_token']}"}


def _sse_events(response) -> list[dict]:
    return [
        json.loads(line[6:])
        for line in response.text.splitlines()
        if line.startswith("data: ")
    ]


@pytest.mark.asyncio
async def test_admin_flow(client: AsyncClient):
    pub = await client.get("/api/system/public")
    assert pub.status_code == 200 and pub.json()["allow_registration"] is True

    admin = await _register(client, f"admin_{uuid.uuid4().hex[:5]}")
    user_b = await _register(client, f"bob_{uuid.uuid4().hex[:5]}")

    # first user is admin, second is not
    assert (await client.get("/api/admin/overview", headers=user_b)).status_code == 403
    ov = await client.get("/api/admin/overview", headers=admin)
    assert ov.status_code == 200
    data = ov.json()
    assert data["users_total"] == 2 and data["users_admins"] == 1 and data["model"] == "mock:mock-kicad"

    users = (await client.get("/api/admin/users", headers=admin)).json()
    assert len(users) == 2
    bob = next(u for u in users if u["username"].startswith("bob_"))

    # promote / disable / reset password
    r = await client.patch(f"/api/admin/users/{bob['id']}", headers=admin, json={"role": "admin", "display_name": "Bob"})
    assert r.status_code == 200 and r.json()["role"] == "admin" and r.json()["display_name"] == "Bob"
    r = await client.patch(f"/api/admin/users/{bob['id']}", headers=admin, json={"role": "user", "is_active": False})
    assert r.status_code == 200 and r.json()["is_active"] is False
    assert (await client.post("/api/auth/login", json={"username": bob["username"], "password": "secret123"})).status_code == 403
    r = await client.post(f"/api/admin/users/{bob['id']}/reset-password", headers=admin, json={"password": "newpass123"})
    assert r.status_code == 204
    r = await client.patch(f"/api/admin/users/{bob['id']}", headers=admin, json={"is_active": True})
    assert (await client.post("/api/auth/login", json={"username": bob["username"], "password": "newpass123"})).status_code == 200

    # admin cannot demote / disable self
    me = next(u for u in users if u["role"] == "admin")
    assert (await client.patch(f"/api/admin/users/{me['id']}", headers=admin, json={"role": "user"})).status_code == 400

    # runtime settings: change model + toggle registration, agent hot-reloads
    r = await client.get("/api/admin/settings", headers=admin)
    assert r.status_code == 200 and r.json()["values"]["llm_provider"] == "mock"
    assert r.json()["values"]["llm_thinking"] == "off"
    assert "deep" in r.json()["thinking_modes"]
    r = await client.put(
        "/api/admin/settings",
        headers=admin,
        json={"values": {"llm_model": "mock-v2", "llm_api_key": "sk-test-1234567890", "llm_thinking": "deep", "llm_thinking_budget": 2048, "llm_thinking_style": "auto", "allow_registration": False, "llm_context_tokens": 64000, "app_footer": "自定义特性标语", "app_copyright": "© 2026 测试版权"}},
    )
    assert r.status_code == 200, r.text
    body = r.json()
    assert body["agent_reloaded"] is True and body["error"] is None
    assert body["values"]["llm_model"] == "mock-v2"
    assert body["values"]["llm_thinking"] == "deep"
    assert body["values"]["llm_thinking_budget"] == 2048
    assert body["values"]["app_footer"] == "自定义特性标语"
    assert body["values"]["app_copyright"] == "© 2026 测试版权"
    assert body["values"]["llm_api_key_set"] is True and "1234567890" not in body["values"]["llm_api_key"]
    assert body["runtime"]["model"] == "mock:mock-v2"
    pub_json = (await client.get("/api/system/public")).json()
    assert pub_json["allow_registration"] is False
    assert pub_json["app_footer"] == "自定义特性标语"
    assert pub_json["app_copyright"] == "© 2026 测试版权"
    assert (await client.post("/api/auth/register", json={"username": "x_new", "password": "secret123"})).status_code == 403

    # stored secret survives reload of overrides (encrypted at rest)
    from app.config import get_settings
    from app.services import settings_store

    stored = await settings_store.load_overrides(get_settings().jwt_secret)
    assert stored["llm_api_key"] == "sk-test-1234567890"
    from sqlalchemy import select

    from app.db import AppSetting, session_scope

    async with session_scope() as s:
        raw = (await s.execute(select(AppSetting).where(AppSetting.key == "llm_api_key"))).scalar_one()
        assert "sk-test" not in raw.value and raw.is_secret

    # test-llm with the mock provider
    r = await client.post("/api/admin/settings/test-llm", headers=admin, json={"llm_provider": "mock", "llm_model": "mock-kicad", "llm_thinking": "deep"})
    assert r.status_code == 200 and r.json()["ok"] is True
    assert r.json()["reasoning_chars"] > 0

    # project export: whole-project zip + per-file download (opens in KiCad)
    import io
    import zipfile

    proj = (await client.post("/api/projects/samples/power_module", headers=admin)).json()
    assert proj["design_constraints"]["min_clearance_mm"] == 0.2
    constraints = {
        **proj["design_constraints"],
        "min_clearance_mm": 0.25,
        "signal_track_width_mm": 0.3,
        "power_track_width_mm": 0.8,
        "notes": "USB 差分对 90Ω",
    }
    saved_constraints = await client.put(
        f"/api/projects/{proj['id']}/constraints", headers=admin, json=constraints
    )
    assert saved_constraints.status_code == 200
    assert saved_constraints.json()["power_track_width_mm"] == 0.8
    assert (await client.get(f"/api/projects/{proj['id']}/constraints", headers=admin)).json()["notes"] == "USB 差分对 90Ω"
    assert (await client.get(f"/api/projects/{proj['id']}/constraints", headers=user_b)).status_code == 404
    invalid_constraints = {**constraints, "via_diameter_mm": 0.3, "via_drill_mm": 0.4}
    assert (
        await client.put(f"/api/projects/{proj['id']}/constraints", headers=admin, json=invalid_constraints)
    ).status_code == 422
    review_response = await client.get(f"/api/projects/{proj['id']}/design-review", headers=admin)
    assert review_response.status_code == 200
    review = review_response.json()
    assert review["success"] is True
    assert review["placement"]["grade"] in ("A", "B", "C", "D")
    assert review["drc"]["engine"] in ("builtin-lite", "kicad-cli")
    assert review["erc"]["engine"] in ("builtin-lite", "kicad-cli")
    assert (await client.get(f"/api/projects/{proj['id']}/design-review", headers=user_b)).status_code == 404
    eco = await client.get(f"/api/projects/{proj['id']}/eco", headers=admin)
    assert eco.status_code == 200 and eco.json()["success"] is True
    assert eco.json()["summary"]["schematic_components"] == eco.json()["summary"]["pcb_footprints"]
    assert (await client.get(f"/api/projects/{proj['id']}/eco", headers=user_b)).status_code == 404
    bom = await client.get(f"/api/projects/{proj['id']}/bom", headers=admin)
    assert bom.status_code == 200 and bom.json()["summary"]["component_count"] == 9
    csv_resp = await client.get(f"/api/projects/{proj['id']}/bom.csv", headers=admin)
    assert csv_resp.status_code == 200 and csv_resp.text.lstrip("\ufeff").startswith("Item,Quantity")
    templates = await client.get("/api/projects/circuit-templates", headers=admin)
    assert templates.status_code == 200 and {t["id"] for t in templates.json()["templates"]} >= {"ldo_3v3", "led_indicator"}
    preview = await client.post(
        f"/api/projects/{proj['id']}/circuit-templates/preview",
        headers=admin,
        json={"template": "voltage_divider", "params": {"input_voltage": 12, "output_voltage": 3.3}},
    )
    assert preview.status_code == 200 and preview.json()["parts"][0]["value"] == "27k"
    assert (
        await client.post(f"/api/projects/{proj['id']}/circuit-templates/preview", headers=admin, json={"template": "led_indicator", "params": {"supply_voltage": 1.0}})
    ).status_code == 400
    files = (await client.get(f"/api/projects/{proj['id']}/files", headers=admin)).json()
    assert {f["kind"] for f in files} >= {"pcb", "schematic", "project"}
    design_files = [f for f in files if f["kind"] in ("pcb", "schematic")]
    assert design_files and {f["generator_version"] for f in design_files} == {"8.0"}
    assert {f["kicad_major"] for f in design_files} == {8}
    assert all(f["compatibility"] in ("compatible", "cli_unavailable") for f in design_files)
    selection_map = (await client.get(f"/api/projects/{proj['id']}/selection-map?mode=pcb", headers=admin)).json()
    assert selection_map["mode"] == "pcb"
    assert {item["reference"] for item in selection_map["elements"]} >= {"U1", "C3", "C4"}
    assert (await client.get(f"/api/projects/{proj['id']}/selection-map", headers=user_b)).status_code == 404

    # Full HTTP workflow: server validates the visual selection, pauses on one
    # batch plan, then executes only after approval from the checkpointed plan.
    conv = (
        await client.post(
            "/api/conversations",
            headers=admin,
            json={"title": "plan flow", "project_id": proj["id"]},
        )
    ).json()
    selection = {
        "project_id": proj["id"],
        "mode": "pcb",
        "references": ["U1", "C3", "C4"],
        "bounds": [0, 0, 1, 1],  # ignored; server recomputes real object bounds
    }
    planned = await client.post(
        f"/api/chat/{conv['id']}/stream",
        headers=admin,
        json={"content": "优化电源模块布局并检查 DRC", "project_id": proj["id"], "selection": selection},
    )
    plan_events = _sse_events(planned)
    pending = next(event for event in plan_events if event["type"] == "interrupt")
    plan_request = pending["interrupts"][0]["value"]["action_requests"][0]
    assert plan_request["name"] == "submit_change_plan"
    assert len(plan_request["args"]["actions"]) == 2

    executed = await client.post(
        f"/api/chat/{conv['id']}/resume",
        headers=admin,
        json={"decisions": [{"type": "approve"}], "selection": selection},
    )
    execution_events = _sse_events(executed)
    completed = {
        event["name"]: event for event in execution_events
        if event["type"] == "tool_result" and event["ok"]
    }
    assert {"submit_change_plan", "run_drc_check"} <= set(completed)
    # The approved actions ran server-side inside submit_change_plan.
    assert completed["submit_change_plan"]["data"]["executed"] == 2
    assert "set_footprint_position" not in completed
    assert all("seq" in event for event in execution_events), "events must be resumable"
    r = await client.get(f"/api/projects/{proj['id']}/archive", headers=admin)
    assert r.status_code == 200 and r.headers["content-type"] == "application/zip"
    names = zipfile.ZipFile(io.BytesIO(r.content)).namelist()
    assert any(n.endswith("power_module.kicad_pcb") for n in names) and any(n.endswith(".kicad_pro") for n in names)
    r = await client.get(f"/api/projects/{proj['id']}/download", headers=admin, params={"file": proj["pcb_file"]})
    assert r.status_code == 200 and r.content.lstrip().startswith(b"(kicad_pcb")
    assert (await client.get(f"/api/projects/{proj['id']}/archive", headers=user_b)).status_code == 404

    # delete user
    r = await client.delete(f"/api/admin/users/{bob['id']}", headers=admin)
    assert r.status_code == 204
    assert len((await client.get("/api/admin/users", headers=admin)).json()) == 1
    assert (await client.delete(f"/api/admin/users/{me['id']}", headers=admin)).status_code == 400
    assert os.environ.get("LLM_PROVIDER") == "mock"
