from __future__ import annotations

import json
from pathlib import Path

import pytest

from app.agent.tools.native import (
    add_skill,
    append_to_skill,
    delete_skill,
    get_skill,
    list_skills,
)


def test_list_skills():
    res = list_skills.invoke({})
    assert 'Available workflow skills:' in res
    assert 'schematic-drawing' in res
    assert 'pcb-routing' in res
    assert 'drc-fix' in res
    assert 'pcb-power-layout' in res
    assert 'schematic-review' in res


def test_get_skill_success():
    res = get_skill.invoke({'name': 'schematic-drawing'})
    assert '原理图绘制' in res
    assert 'connect_pins_with_wire' in res

    # test underscore alias
    res_under = get_skill.invoke({'name': 'schematic_drawing'})
    assert res == res_under

    # test path-like alias
    res_path = get_skill.invoke({'name': 'schematic-drawing/SKILL.md'})
    assert res == res_path


def test_get_skill_not_found():
    res = get_skill.invoke({'name': 'unknown_skill_xyz'})
    data = json.loads(res)
    assert data['success'] is False
    assert 'not found' in data['error'].lower()
    assert 'schematic-drawing' in data['error']


def test_skill_lifecycle(tmp_path: Path, monkeypatch: pytest.MonkeyPatch):
    import app.agent.tools.native as native_mod
    from types import SimpleNamespace

    from app.agent.context import AgentContext

    skills_root = tmp_path / 'skills'
    skills_root.mkdir()
    monkeypatch.setattr(native_mod, 'SKILLS_DIR', skills_root)
    monkeypatch.setattr(native_mod, 'DELETED_DIR', skills_root / '.deleted')
    # Authoring tools are admin-only and receive the run context through ToolRuntime.
    admin = SimpleNamespace(context=AgentContext(user_id='admin1', role='admin'))

    # Initially empty
    assert 'No workflow skills' in list_skills.invoke({})

    # Add skill
    res_add = add_skill.func(
        name='custom-uart',
        description='UART 通信电路模块设计',
        content='# UART 接口设计指南\n\n使用 MAX3232 芯片。',
        priority=80,
        runtime=admin,
    )
    assert 'custom-uart' in res_add
    assert (skills_root / 'custom-uart' / 'SKILL.md').exists()

    # Read back
    content = get_skill.invoke({'name': 'custom-uart'})
    assert 'MAX3232' in content

    # Append
    res_app = append_to_skill.func(name='custom-uart', content='注意加 0.1uF 滤波电容。', runtime=admin)
    assert 'appended' in res_app.lower()
    updated = get_skill.invoke({'name': 'custom-uart'})
    assert 'MAX3232' in updated and '0.1uF' in updated

    # Delete (soft delete)
    res_del = delete_skill.func(name='custom-uart', runtime=admin)
    assert 'deleted' in res_del.lower()
    assert not (skills_root / 'custom-uart').exists()
    assert (skills_root / '.deleted' / 'custom-uart').exists()
