"""Project management: list / import zip / load sample / preview / files."""

from __future__ import annotations

import asyncio
import io
import json
import shutil
import tempfile
import zipfile
from datetime import datetime
from pathlib import Path
from typing import Any, Literal
from urllib.parse import quote

from pydantic import BaseModel, Field

from fastapi import (
    APIRouter,
    Depends,
    File,
    Form,
    HTTPException,
    Response,
    UploadFile,
    status,
)
from fastapi.responses import FileResponse, StreamingResponse
from sqlalchemy.ext.asyncio import AsyncSession

from app.auth import get_current_user
from app.config import get_settings
from app.db import Project, User, get_session
from app.kicad import cli as kicad_cli
from app.kicad import workspace as ws
from app.kicad.bom import bom_csv, build_bom_report
from app.kicad.compat import file_compatibility
from app.kicad.eco import build_eco_report
from app.kicad.pcb import Board
from app.kicad.review import run_design_review as review_project
from app.kicad.sch import Schematic
from app.kicad.wizard import check_library_availability, expand_template, list_templates
from app.schemas import DesignConstraints, ProjectCreateBlank, ProjectFileEntry, ProjectOut, SnapshotOut
from app.services.conversations import (
    get_owned_project,
    list_projects,
    sync_project_from_disk,
)

router = APIRouter(prefix="/api/projects", tags=["projects"])


def _constraints(p: Project) -> DesignConstraints:
    try:
        value = json.loads(p.design_constraints or "{}")
        return DesignConstraints.model_validate(value if isinstance(value, dict) else {})
    except (json.JSONDecodeError, ValueError, TypeError):
        return DesignConstraints()


def _out(p: Project) -> ProjectOut:
    return ProjectOut(
        id=p.id,
        name=p.name,
        rel_dir=p.rel_dir,
        pro_file=p.pro_file,
        schematic_file=p.schematic_file,
        pcb_file=p.pcb_file,
        design_constraints=_constraints(p),
        created_at=p.created_at,
        updated_at=p.updated_at,
    )


async def _register_dir(session: AsyncSession, user: User, rel_dir: str) -> Project:
    entries = [e for e in ws.scan_projects(user.id) if e["dir"] == rel_dir or e["dir"].startswith(rel_dir.rstrip("/") + "/")]
    if not entries:
        raise HTTPException(status.HTTP_400_BAD_REQUEST, "目录中没有找到 KiCad 文件 (.kicad_pro/.kicad_sch/.kicad_pcb)")
    entry = next((e for e in entries if e["dir"] == rel_dir), entries[0])
    project = Project(
        owner_id=user.id,
        name=entry["name"],
        rel_dir=entry["dir"],
        pro_file=entry["pro"],
        schematic_file=entry["sch"],
        pcb_file=entry["pcb"],
    )
    session.add(project)
    await session.commit()
    await session.refresh(project)
    return project


@router.get("", response_model=list[ProjectOut])
async def list_(user: User = Depends(get_current_user), session: AsyncSession = Depends(get_session)):
    projects = await list_projects(session, user)
    existing_dirs = {p.rel_dir for p in projects}
    for entry in ws.scan_projects(user.id):
        if entry["dir"] not in existing_dirs and entry.get("pro"):
            p = Project(
                owner_id=user.id,
                name=entry["name"],
                rel_dir=entry["dir"],
                pro_file=entry["pro"],
                schematic_file=entry["sch"],
                pcb_file=entry["pcb"],
            )
            session.add(p)
            projects.append(p)
            existing_dirs.add(entry["dir"])
    for p in projects:
        sync_project_from_disk(user, p)
    await session.commit()
    for p in projects:
        try:
            await session.refresh(p)
        except Exception:
            pass
    return [_out(p) for p in sorted(projects, key=lambda x: x.created_at, reverse=True)]


@router.post("/blank", response_model=ProjectOut)
async def create_blank(
    payload: ProjectCreateBlank,
    user: User = Depends(get_current_user),
    session: AsyncSession = Depends(get_session),
):
    dest = ws.create_blank_project(user.id, payload.name, payload.title)
    return _out(await _register_dir(session, user, ws.relpath(user.id, dest)))


@router.get("/circuit-templates")
async def circuit_templates(user: User = Depends(get_current_user)):
    """Static route: must be registered before ``/{project_id}``."""
    return {"templates": list_templates()}


@router.get("/samples")
async def samples():
    root = get_settings().samples_dir
    if not root.exists():
        return []
    return [
        {"name": d.name, "files": sorted(f.name for f in d.iterdir() if f.is_file())}
        for d in sorted(root.iterdir())
        if d.is_dir()
    ]


@router.post("/samples/{name}", response_model=ProjectOut)
async def load_sample(name: str, user: User = Depends(get_current_user), session: AsyncSession = Depends(get_session)):
    src = get_settings().samples_dir / name
    if not src.is_dir() or ".." in name:
        raise HTTPException(status.HTTP_404_NOT_FOUND, "示例工程不存在")
    dest = ws.copy_sample(user.id, src, f"projects/{name}")
    return _out(await _register_dir(session, user, ws.relpath(user.id, dest)))


@router.post("/import", response_model=ProjectOut)
async def import_zip(
    file: UploadFile = File(...),
    name: str | None = Form(default=None),
    user: User = Depends(get_current_user),
    session: AsyncSession = Depends(get_session),
):
    if not file.filename or not file.filename.lower().endswith(".zip"):
        raise HTTPException(status.HTTP_400_BAD_REQUEST, "请上传 .zip 压缩包")
    target = (name or Path(file.filename).stem).strip().replace("..", "").replace("/", "_") or "project"
    with tempfile.NamedTemporaryFile(delete=False, suffix=".zip") as tmp:
        shutil.copyfileobj(file.file, tmp)
        tmp_path = Path(tmp.name)
    try:
        dest = ws.import_zip(user.id, tmp_path, f"projects/{target}")
    finally:
        tmp_path.unlink(missing_ok=True)
    return _out(await _register_dir(session, user, ws.relpath(user.id, dest)))


@router.post("/upload", response_model=ProjectOut)
async def upload_files(
    files: list[UploadFile] = File(...),
    name: str = Form(...),
    user: User = Depends(get_current_user),
    session: AsyncSession = Depends(get_session),
):
    """Upload individual KiCad files (.kicad_pro / .kicad_sch / .kicad_pcb) into a new project dir."""
    target = name.strip().replace("..", "").replace("/", "_") or "project"
    dest = ws.resolve(user.id, f"projects/{target}")
    dest.mkdir(parents=True, exist_ok=True)
    for f in files:
        fname = Path(f.filename or "file").name
        with open(dest / fname, "wb") as out:
            shutil.copyfileobj(f.file, out)
    return _out(await _register_dir(session, user, ws.relpath(user.id, dest)))


@router.get("/{project_id}", response_model=ProjectOut)
async def get_project(project_id: str, user: User = Depends(get_current_user), session: AsyncSession = Depends(get_session)):
    project = await get_owned_project(session, user, project_id)
    assert project
    sync_project_from_disk(user, project)
    await session.commit()
    return _out(project)


@router.get("/{project_id}/constraints", response_model=DesignConstraints)
async def get_constraints(
    project_id: str,
    user: User = Depends(get_current_user),
    session: AsyncSession = Depends(get_session),
):
    project = await get_owned_project(session, user, project_id)
    assert project
    return _constraints(project)


@router.put("/{project_id}/constraints", response_model=DesignConstraints)
async def update_constraints(
    project_id: str,
    payload: DesignConstraints,
    user: User = Depends(get_current_user),
    session: AsyncSession = Depends(get_session),
):
    project = await get_owned_project(session, user, project_id)
    assert project
    project.design_constraints = json.dumps(payload.model_dump(), ensure_ascii=False, separators=(",", ":"))
    await session.commit()
    return payload


@router.delete("/{project_id}", status_code=204)
async def delete_project(
    project_id: str,
    delete_files: bool = False,
    user: User = Depends(get_current_user),
    session: AsyncSession = Depends(get_session),
):
    project = await get_owned_project(session, user, project_id)
    assert project
    if delete_files:
        d = ws.resolve(user.id, project.rel_dir)
        if d.exists():
            shutil.rmtree(d, ignore_errors=True)
    await session.delete(project)
    await session.commit()
    return Response(status_code=204)


@router.get("/{project_id}/files", response_model=list[ProjectFileEntry])
async def project_files(project_id: str, user: User = Depends(get_current_user), session: AsyncSession = Depends(get_session)):
    project = await get_owned_project(session, user, project_id)
    assert project
    root = ws.user_root(user.id)
    out = []
    runtime_version = kicad_cli.version()
    for e in ws.list_files(user.id, project.rel_dir):
        p = root / e["path"]
        compatibility = file_compatibility(p, runtime_version)
        out.append(
            ProjectFileEntry(
                **e,
                modified_at=datetime.fromtimestamp(p.stat().st_mtime).isoformat() if p.exists() else None,
                generator_version=compatibility["generator_version"],
                format_version=compatibility["format_version"],
                kicad_major=compatibility["major"],
                compatibility=compatibility["status"],
                compatible=compatibility["compatible"],
                compatibility_message=compatibility["message"],
                runtime_version=runtime_version,
            )
        )
    return out


@router.get("/{project_id}/board")
async def board_summary(project_id: str, user: User = Depends(get_current_user), session: AsyncSession = Depends(get_session)):
    project = await get_owned_project(session, user, project_id)
    assert project
    if not project.pcb_file:
        raise HTTPException(status.HTTP_404_NOT_FOUND, "该工程没有 PCB 文件")
    board = Board.load(ws.resolve(user.id, project.pcb_file))
    info = board.info()
    info["footprints"] = [f.to_dict() for f in board.footprints]
    info["kicad_cli"] = kicad_cli.available()
    return info


@router.get("/{project_id}/design-review")
async def design_review(
    project_id: str,
    user: User = Depends(get_current_user),
    session: AsyncSession = Depends(get_session),
):
    """Run KiCad ERC/DRC and placement scoring without blocking the event loop."""
    project = await get_owned_project(session, user, project_id)
    assert project
    sync_project_from_disk(user, project)
    pcb = ws.resolve(user.id, project.pcb_file) if project.pcb_file else None
    schematic = ws.resolve(user.id, project.schematic_file) if project.schematic_file else None
    return await asyncio.to_thread(
        review_project,
        pcb,
        schematic,
        _constraints(project).model_dump(),
    )


def _design_paths(user: User, project: Project) -> tuple[Path | None, Path | None]:
    pcb = ws.resolve(user.id, project.pcb_file) if project.pcb_file else None
    schematic = ws.resolve(user.id, project.schematic_file) if project.schematic_file else None
    return pcb, schematic


@router.get("/{project_id}/bom")
async def bom_report(
    project_id: str,
    user: User = Depends(get_current_user),
    session: AsyncSession = Depends(get_session),
):
    project = await get_owned_project(session, user, project_id)
    assert project
    sync_project_from_disk(user, project)
    pcb, schematic = _design_paths(user, project)
    return await asyncio.to_thread(build_bom_report, pcb, schematic, _constraints(project).model_dump())


@router.get("/{project_id}/bom.csv")
async def bom_report_csv(
    project_id: str,
    user: User = Depends(get_current_user),
    session: AsyncSession = Depends(get_session),
):
    project = await get_owned_project(session, user, project_id)
    assert project
    sync_project_from_disk(user, project)
    pcb, schematic = _design_paths(user, project)
    report = await asyncio.to_thread(build_bom_report, pcb, schematic, _constraints(project).model_dump())
    if not report.get("success"):
        raise HTTPException(status.HTTP_400_BAD_REQUEST, report.get("error", "无法生成 BOM"))
    filename = quote(f"{project.name}-bom.csv")
    return Response(
        content="\ufeff" + bom_csv(report),
        media_type="text/csv; charset=utf-8",
        headers={"Content-Disposition": f"attachment; filename*=UTF-8''{filename}"},
    )


class TemplatePreviewRequest(BaseModel):
    template: str
    params: dict[str, Any] = Field(default_factory=dict)
    anchor_x: float | None = None
    anchor_y: float | None = None


@router.post("/{project_id}/circuit-templates/preview")
async def circuit_template_preview(
    project_id: str,
    payload: TemplatePreviewRequest,
    user: User = Depends(get_current_user),
    session: AsyncSession = Depends(get_session),
):
    """Expand a template for approval without touching any file."""
    project = await get_owned_project(session, user, project_id)
    assert project
    if not project.schematic_file:
        raise HTTPException(status.HTTP_400_BAD_REQUEST, "当前工程没有原理图")
    try:
        anchor_x = payload.anchor_x if payload.anchor_x is not None else 50.8
        anchor_y = payload.anchor_y if payload.anchor_y is not None else 50.8
        expanded = expand_template(payload.template, payload.params, anchor_x, anchor_y)
    except ValueError as exc:
        raise HTTPException(status.HTTP_400_BAD_REQUEST, str(exc)) from exc
    availability = await asyncio.to_thread(check_library_availability, expanded)
    return {**expanded, "library": availability}


@router.get("/{project_id}/eco")
async def eco_report(
    project_id: str,
    user: User = Depends(get_current_user),
    session: AsyncSession = Depends(get_session),
):
    project = await get_owned_project(session, user, project_id)
    assert project
    sync_project_from_disk(user, project)
    if not project.schematic_file or not project.pcb_file:
        raise HTTPException(status.HTTP_400_BAD_REQUEST, "ECO 检查需要工程同时包含原理图和 PCB")
    return await asyncio.to_thread(
        build_eco_report,
        ws.resolve(user.id, project.schematic_file),
        ws.resolve(user.id, project.pcb_file),
    )


@router.get("/{project_id}/selection-map")
async def selection_map(
    project_id: str,
    mode: Literal["pcb", "sch"] = "pcb",
    user: User = Depends(get_current_user),
    session: AsyncSession = Depends(get_session),
):
    """Selectable objects in the exact coordinate system of the built-in SVG."""
    project = await get_owned_project(session, user, project_id)
    assert project
    if mode == "pcb":
        if not project.pcb_file:
            raise HTTPException(status.HTTP_404_NOT_FOUND, "该工程没有 PCB 文件")
        result = Board.load(ws.resolve(user.id, project.pcb_file)).selection_map()
    else:
        if not project.schematic_file:
            sync_project_from_disk(user, project)
        if not project.schematic_file:
            raise HTTPException(status.HTTP_404_NOT_FOUND, "该工程没有原理图文件")
        result = Schematic.load(ws.resolve(user.id, project.schematic_file)).selection_map()
    return {"project_id": project.id, "project_name": project.name, **result}


@router.get("/{project_id}/preview.svg")
async def preview_svg(
    project_id: str,
    engine: str = "auto",
    user: User = Depends(get_current_user),
    session: AsyncSession = Depends(get_session),
):
    project = await get_owned_project(session, user, project_id)
    assert project
    if not project.pcb_file:
        raise HTTPException(status.HTTP_404_NOT_FOUND, "该工程没有 PCB 文件")
    path = ws.resolve(user.id, project.pcb_file)
    svg: str | None = None
    if engine in ("auto", "kicad") and kicad_cli.available():
        try:
            svg = kicad_cli.export_svg(path)
        except Exception:  # noqa: BLE001
            svg = None
    if svg is None:
        svg = Board.load(path).render_svg()
    return Response(content=svg, media_type="image/svg+xml", headers={"Cache-Control": "no-store"})


@router.get("/{project_id}/preview_sch.svg")
async def preview_sch_svg(
    project_id: str,
    engine: str = "auto",
    user: User = Depends(get_current_user),
    session: AsyncSession = Depends(get_session),
):
    project = await get_owned_project(session, user, project_id)
    assert project
    sch_file = project.schematic_file
    if not sch_file:
        sync_project_from_disk(user, project)
        sch_file = project.schematic_file
    if not sch_file:
        p_dir = ws.resolve(user.id, project.rel_dir)
        sch_candidates = list(p_dir.glob("*.kicad_sch"))
        if sch_candidates:
            sch_file = ws.relpath(user.id, sch_candidates[0])
            project.schematic_file = sch_file
    if not sch_file:
        raise HTTPException(status.HTTP_404_NOT_FOUND, "该工程没有原理图文件")
    path = ws.resolve(user.id, sch_file)
    if not path.is_file():
        raise HTTPException(status.HTTP_404_NOT_FOUND, "原理图文件不存在")
    svg: str | None = None
    if engine in ("auto", "kicad") and kicad_cli.available():
        try:
            svg = kicad_cli.export_sch_svg(path)
        except Exception:  # noqa: BLE001
            svg = None
    if svg is None:
        svg = Schematic.load(path).render_svg()
    return Response(content=svg, media_type="image/svg+xml", headers={"Cache-Control": "no-store"})


@router.get("/{project_id}/download")
async def download_file(
    project_id: str,
    file: str,
    user: User = Depends(get_current_user),
    session: AsyncSession = Depends(get_session),
):
    project = await get_owned_project(session, user, project_id)
    assert project
    path = ws.resolve(user.id, file)
    if not path.is_file() or not path.resolve().is_relative_to(ws.resolve(user.id, project.rel_dir)):
        raise HTTPException(status.HTTP_404_NOT_FOUND, "文件不存在")
    return FileResponse(path, filename=path.name, media_type="application/octet-stream")


@router.get("/{project_id}/archive")
async def download_archive(
    project_id: str,
    user: User = Depends(get_current_user),
    session: AsyncSession = Depends(get_session),
):
    """Zip the whole project directory (without snapshots/backups) for opening in KiCad."""
    project = await get_owned_project(session, user, project_id)
    assert project
    root = ws.resolve(user.id, project.rel_dir)
    if not root.is_dir():
        raise HTTPException(status.HTTP_404_NOT_FOUND, "工程目录不存在")
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, "w", zipfile.ZIP_DEFLATED) as zf:
        for p in sorted(root.rglob("*")):
            if not p.is_file():
                continue
            parts = p.relative_to(root).parts
            if ".versions" in parts or any(part.endswith("-backups") for part in parts) or p.suffix.lower() in (".bak", ".lck"):
                continue
            zf.write(p, arcname=(Path(root.name) / p.relative_to(root)).as_posix())
    buf.seek(0)
    stamp = datetime.now().strftime("%Y%m%d-%H%M")
    filename = f"{project.name}-{stamp}.zip"
    return StreamingResponse(
        buf,
        media_type="application/zip",
        headers={"Content-Disposition": f"attachment; filename*=UTF-8''{quote(filename)}"},
    )


@router.get("/{project_id}/snapshots", response_model=list[SnapshotOut])
async def snapshots(project_id: str, user: User = Depends(get_current_user), session: AsyncSession = Depends(get_session)):
    project = await get_owned_project(session, user, project_id)
    assert project
    out: list[SnapshotOut] = []
    for rel in (project.pcb_file, project.schematic_file, project.pro_file):
        if rel:
            out.extend(SnapshotOut(**v) for v in ws.list_versions(user.id, rel))
    out.sort(key=lambda s: s.created_at, reverse=True)
    return out


@router.post("/{project_id}/snapshots", response_model=SnapshotOut)
async def create_snapshot(
    project_id: str,
    label: str = Form(default="manual snapshot"),
    user: User = Depends(get_current_user),
    session: AsyncSession = Depends(get_session),
):
    project = await get_owned_project(session, user, project_id)
    assert project
    target = project.pcb_file or project.schematic_file
    if not target:
        raise HTTPException(status.HTTP_400_BAD_REQUEST, "工程没有可快照的文件")
    return SnapshotOut(**ws.save_version(user.id, target, label))


@router.post("/{project_id}/snapshots/{version_id}/restore")
async def restore_snapshot(
    project_id: str,
    version_id: str,
    file: str = Form(...),
    user: User = Depends(get_current_user),
    session: AsyncSession = Depends(get_session),
):
    project = await get_owned_project(session, user, project_id)
    assert project
    if file not in (project.pcb_file, project.schematic_file, project.pro_file):
        raise HTTPException(status.HTTP_400_BAD_REQUEST, "文件不属于该工程")
    return ws.restore_version(user.id, file, version_id)
