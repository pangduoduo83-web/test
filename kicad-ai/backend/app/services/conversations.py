"""Conversation / project helpers shared by the routers."""

from __future__ import annotations

import json
from typing import Any

from fastapi import HTTPException, status
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.agent.context import AgentContext
from app.db import Conversation, Project, User
from app.kicad import workspace as ws
from app.kicad.pcb import Board
from app.kicad.sch import Schematic
from app.schemas import DesignConstraints, DesignSelection


async def get_owned_conversation(session: AsyncSession, user: User, conversation_id: str) -> Conversation:
    conv = await session.get(Conversation, conversation_id)
    if conv is None or conv.owner_id != user.id:
        raise HTTPException(status.HTTP_404_NOT_FOUND, "对话不存在")
    return conv


async def get_owned_project(session: AsyncSession, user: User, project_id: str | None) -> Project | None:
    if not project_id:
        return None
    proj = await session.get(Project, project_id)
    if proj is None or proj.owner_id != user.id:
        raise HTTPException(status.HTTP_404_NOT_FOUND, "工程不存在")
    return proj


async def list_projects(session: AsyncSession, user: User) -> list[Project]:
    result = await session.execute(select(Project).where(Project.owner_id == user.id).order_by(Project.updated_at.desc()))
    return list(result.scalars())


def build_context(user: User, conversation: Conversation, project: Project | None) -> AgentContext:
    root = ws.user_root(user.id).resolve()

    def abs_(rel: str | None) -> str | None:
        return str((root / rel).resolve()) if rel else None

    constraints: dict[str, Any] = {}
    if project is not None:
        try:
            raw = json.loads(project.design_constraints or "{}")
            constraints = DesignConstraints.model_validate(raw if isinstance(raw, dict) else {}).model_dump()
        except (json.JSONDecodeError, TypeError, ValueError):
            constraints = DesignConstraints().model_dump()

    # 技能库等"全站生效"的管理员工具只开放给平台管理员(默认站点的管理员);
    # 其他站点的管理员在 Agent 眼里就是普通用户,避免一个客户改动影响所有客户
    from app.auth import is_platform_admin

    return AgentContext(
        user_id=user.id,
        username=user.username,
        display_name=user.display_name,
        role="admin" if is_platform_admin(user) else "user",
        conversation_id=conversation.id,
        project_id=project.id if project else None,
        project_name=project.name if project else None,
        project_dir=project.rel_dir if project else None,
        schematic_path=abs_(project.schematic_file) if project else None,
        pcb_path=abs_(project.pcb_file) if project else None,
        pro_path=abs_(project.pro_file) if project else None,
        workspace_root=str(root),
        design_constraints=constraints,
    )


def validate_design_selection(
    user: User, project: Project | None, selection: DesignSelection | None
) -> dict[str, Any] | None:
    """Resolve references against the owned project; never trust client geometry."""
    if selection is None:
        return None
    if project is None:
        raise HTTPException(status.HTTP_400_BAD_REQUEST, "选择了设计对象，但当前没有活动工程")
    if selection.project_id != project.id:
        raise HTTPException(status.HTTP_400_BAD_REQUEST, "设计选区不属于当前活动工程，请重新选择")

    if selection.mode == "pcb":
        if not project.pcb_file:
            raise HTTPException(status.HTTP_400_BAD_REQUEST, "当前工程没有 PCB，无法使用该选区")
        board = Board.load(ws.resolve(user.id, project.pcb_file))
        elements = board.selection_map()["elements"]
    else:
        if not project.schematic_file:
            raise HTTPException(status.HTTP_400_BAD_REQUEST, "当前工程没有原理图，无法使用该选区")
        schematic = Schematic.load(ws.resolve(user.id, project.schematic_file))
        elements = schematic.selection_map()["elements"]

    by_ref = {item["reference"].lower(): item for item in elements}
    resolved = []
    invalid = []
    seen: set[str] = set()
    for raw in selection.references:
        item = by_ref.get(raw.strip().lower())
        if item is None:
            invalid.append(raw)
            continue
        key = item["reference"].lower()
        if key not in seen:
            resolved.append(item)
            seen.add(key)
    if invalid:
        shown = "、".join(invalid[:8])
        raise HTTPException(status.HTTP_400_BAD_REQUEST, f"选区包含不存在的对象：{shown}")
    if not resolved:
        raise HTTPException(status.HTTP_400_BAD_REQUEST, "选区中没有有效对象")

    # Geometry is derived from the server-owned design file. The client's drag
    # rectangle is only a UI hint and must never become authoritative context.
    boxes = [item["world_bbox"] for item in resolved]
    bounds = [
        min(box[0] for box in boxes),
        min(box[1] for box in boxes),
        max(box[2] for box in boxes),
        max(box[3] for box in boxes),
    ]
    return {
        "mode": selection.mode,
        "references": [item["reference"] for item in resolved],
        "bounds": [round(value, 4) for value in bounds],
        "objects": [
            {
                "reference": item["reference"],
                "value": item["value"],
                "kind": item["kind"],
                "position": item["position"],
            }
            for item in resolved
        ],
    }


def sync_project_from_disk(user: User, project: Project) -> None:
    """Refresh file names if the user replaced files on disk."""
    for entry in ws.scan_projects(user.id):
        if entry["dir"] == project.rel_dir:
            project.pro_file = entry["pro"]
            project.schematic_file = entry["sch"]
            project.pcb_file = entry["pcb"]
            if entry["pro"]:
                project.name = entry["name"]
            return
