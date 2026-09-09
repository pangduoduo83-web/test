"""Native KiCad tools (no KiCad installation required).

Every tool receives the LangChain ``ToolRuntime`` so it can read the per-run
:class:`AgentContext` (user id, active project files) and stay inside the
caller's private workspace. Results are JSON strings following the upstream
``{"success": bool, ...}`` convention so the model can react to failures.
"""

from __future__ import annotations

import asyncio
import functools
import json
import logging
import re
import shutil
from pathlib import Path
from typing import Any, Literal

from langchain.tools import ToolRuntime, tool
from typing_extensions import NotRequired, TypedDict

from app.agent.change_plan import validate_change_plan
from app.agent.context import AgentContext
from app.agent.tools.kcaa_direct import call_kcaa, kcaa_tool_available
from app.kicad import cli as kicad_cli
from app.kicad import workspace as ws
from app.kicad.bom import build_bom_report
from app.kicad.eco import build_eco_report
from app.kicad.pcb import Board
from app.kicad.review import run_design_review as review_project
from app.kicad.sch import Schematic
from app.kicad.wizard import bounding_size, check_library_availability, expand_template, list_templates

log = logging.getLogger(__name__)


class PlannedAction(TypedDict):
    tool: str
    args: dict[str, Any]
    summary: str
    reason: NotRequired[str]


# ---------------------------------------------------------------------------
# helpers
# ---------------------------------------------------------------------------
def _dumps(data: Any) -> str:
    return json.dumps(data, ensure_ascii=False, default=str)


def _ok(**data: Any) -> str:
    return _dumps({"success": True, **data})


def _err(message: str, **extra: Any) -> str:
    return _dumps({"success": False, "error": message, **extra})


def _ctx(runtime: ToolRuntime[AgentContext]) -> AgentContext:
    ctx = runtime.context
    if ctx is None:
        raise RuntimeError("AgentContext missing from runtime")
    return ctx


def _resolve(ctx: AgentContext, path: str) -> Path:
    return ws.resolve(ctx.user_id, path)


def _pcb(runtime: ToolRuntime[AgentContext], pcb_path: str | None) -> tuple[AgentContext, Board, Path]:
    ctx = _ctx(runtime)
    target = ctx.resolve_pcb(pcb_path)
    if not target:
        raise FileNotFoundError("未指定 pcb_path，且当前工程没有 PCB 文件。请先选择工程或传入 pcb_path。")
    p = _resolve(ctx, target)
    if not p.is_file():
        raise FileNotFoundError(f"PCB 文件不存在：{target}")
    return ctx, Board.load(p), p


def _sch(runtime: ToolRuntime[AgentContext], schematic_path: str | None) -> tuple[AgentContext, Schematic, Path]:
    ctx = _ctx(runtime)
    target = ctx.resolve_sch(schematic_path)
    if not target:
        raise FileNotFoundError("未指定 schematic_path，且当前工程没有原理图文件。")
    p = _resolve(ctx, target)
    if not p.is_file():
        raise FileNotFoundError(f"原理图文件不存在：{target}")
    return ctx, Schematic.load(p), p


def _guard(fn):
    """Convert exceptions into ``{"success": false}`` payloads.

    ``functools.wraps`` keeps ``__wrapped__`` so LangChain's ``@tool`` still
    infers the argument schema (and the injected ``ToolRuntime``) from the
    original signature.
    """
    if asyncio.iscoroutinefunction(fn):
        @functools.wraps(fn)
        async def async_wrapper(*args, **kwargs):
            try:
                return await fn(*args, **kwargs)
            except (FileNotFoundError, KeyError, ValueError, PermissionError, ws.WorkspaceError) as exc:
                return _err(str(exc).strip("'"))
            except Exception as exc:  # noqa: BLE001 — surfaced to the model
                return _err(f"{type(exc).__name__}: {exc}")

        return async_wrapper

    @functools.wraps(fn)
    def sync_wrapper(*args, **kwargs):
        try:
            return fn(*args, **kwargs)
        except (FileNotFoundError, KeyError, ValueError, PermissionError, ws.WorkspaceError) as exc:
            return _err(str(exc).strip("'"))
        except Exception as exc:  # noqa: BLE001 — surfaced to the model
            return _err(f"{type(exc).__name__}: {exc}")

    return sync_wrapper


@tool
@_guard
async def submit_change_plan(
    title: str,
    summary: str,
    actions: list[PlannedAction],
    runtime: ToolRuntime[AgentContext],
    scope: list[str] | None = None,
    verification: list[PlannedAction] | None = None,
) -> str:
    """提交一份设计修改计划；用户批准后由系统自动、按顺序执行全部动作。

    这是修改 KiCad 文件的唯一途径：先用只读工具取得现状并算出最终参数，再把
    每个修改写成具体、可直接执行的 ``tool + args``（不允许占位符）。用户会一次
    性查看并批准/拒绝整个计划；批准后**不要**再自行调用修改工具——本工具会
    返回每个动作的执行结果，你只需运行 ``verification`` 中的只读检查并汇报。
    某个动作失败时其后动作不会执行；分析原因后可重新提交只含剩余动作的计划。

    ``verification`` 只列执行后要运行的只读检查（如 ``run_drc_check``）。
    ``scope`` 填受影响的参考号，优先使用当前可视选区中的对象。
    """
    from app.agent.executor import execute_plan, plan_matches

    ctx = _ctx(runtime)  # Require a real agent run; the plan is tied to its context.
    plan, error = validate_change_plan(
        {
            "title": title,
            "summary": summary,
            "scope": scope or [],
            "actions": actions,
            "verification": verification or [],
        }
    )
    if error or plan is None:
        return _err(error or "修改计划格式无效")
    approved = ctx.extra.get("approved_change_plan")
    if not plan_matches(approved, plan):
        return _err(
            "该计划尚未获得用户批准，或与用户批准的内容不一致；未执行任何修改。"
            "请等待用户在页面上批准计划，系统会在批准后自动执行。",
            pending_approval=True,
            title=plan["title"],
            actions=plan["actions"],
        )
    report = await execute_plan(plan, runtime)
    return _dumps(
        {
            "success": report["failed"] == 0,
            "approved": True,
            "executed_by": "server",
            "title": plan["title"],
            "summary": plan["summary"],
            "scope": plan["scope"],
            "verification": plan["verification"],
            **report,
        }
    )


async def _sync_project_to_db(
    ctx: AgentContext,
    name: str,
    rel_dir: str,
    pro_file: str,
    sch_file: str,
    pcb_file: str,
) -> str | None:
    try:
        from app.db.models import Conversation, Project
        from app.db.session import get_session_factory
        from sqlalchemy import select

        session_factory = get_session_factory()
        async with session_factory() as session:
            res = await session.execute(
                select(Project).where(Project.owner_id == ctx.user_id, Project.rel_dir == rel_dir)
            )
            proj = res.scalar_one_or_none()
            if not proj:
                proj = Project(
                    owner_id=ctx.user_id,
                    name=name,
                    rel_dir=rel_dir,
                    pro_file=pro_file,
                    schematic_file=sch_file,
                    pcb_file=pcb_file,
                )
                session.add(proj)
                await session.flush()
            else:
                proj.name = name
                proj.pro_file = pro_file
                proj.schematic_file = sch_file
                proj.pcb_file = pcb_file

            proj_id = proj.id
            if ctx.conversation_id:
                conv = await session.get(Conversation, ctx.conversation_id)
                if conv and conv.owner_id == ctx.user_id:
                    conv.project_id = proj_id
            await session.commit()
            return proj_id
    except Exception as exc:
        log.warning("Could not sync project %s to database: %s", name, exc)
        return None


# ---------------------------------------------------------------------------
# project tools
# ---------------------------------------------------------------------------
@tool
@_guard
def list_projects(runtime: ToolRuntime[AgentContext]) -> str:
    """列出当前用户工作区中的所有 KiCad 工程（含原理图 / PCB 文件路径）。"""
    ctx = _ctx(runtime)
    projects = ws.scan_projects(ctx.user_id)
    root = ws.user_root(ctx.user_id)
    for p in projects:
        for key in ("pro", "sch", "pcb"):
            if p[key]:
                p[key + "_abs"] = str((root / p[key]).resolve())
    return _ok(projects=projects, count=len(projects), active_project=ctx.project_name)


@tool
@_guard
async def create_project(name: str, title: str | None = None, runtime: ToolRuntime[AgentContext] = None) -> str:
    """创建全新的空白 KiCad 工程并自动切换为当前活动工程。
    
    当用户要求「从0到1设计」、「新建工程」或设计一个与现有电路无关的全新硬件时必须调用此工具。
    生成标准空白原理图 (.kicad_sch)、空白 PCB (.kicad_pcb) 和工程配置 (.kicad_pro)，
    实现工程级物理隔离，避免在现有旧工程中覆盖或破坏已有电路。
    """
    ctx = _ctx(runtime)
    dest = ws.create_blank_project(ctx.user_id, name, title)
    clean_name = dest.name
    rel_dir = ws.relpath(ctx.user_id, dest)
    sch_file = f"{rel_dir}/{clean_name}.kicad_sch"
    pcb_file = f"{rel_dir}/{clean_name}.kicad_pcb"
    pro_file = f"{rel_dir}/{clean_name}.kicad_pro"

    root = ws.user_root(ctx.user_id).resolve()
    ctx.project_name = clean_name
    ctx.project_dir = rel_dir
    ctx.schematic_path = str((root / sch_file).resolve())
    ctx.pcb_path = str((root / pcb_file).resolve())
    ctx.pro_path = str((root / pro_file).resolve())

    proj_id = await _sync_project_to_db(ctx, clean_name, rel_dir, pro_file, sch_file, pcb_file)
    if proj_id:
        ctx.project_id = proj_id

    return _ok(
        project_id=ctx.project_id,
        project_name=clean_name,
        project_dir=rel_dir,
        schematic_file=sch_file,
        pcb_file=pcb_file,
        schematic_path=ctx.schematic_path,
        pcb_path=ctx.pcb_path,
        message=f"已成功创建空白 KiCad 工程 '{clean_name}'（包含空白原理图与 PCB），当前活动上下文与会话已自动绑定至新工程。可以开始从 0 到 1 放置元件与布线设计！",
    )


@tool
@_guard
async def switch_project(project_name: str, runtime: ToolRuntime[AgentContext] = None) -> str:
    """切换当前会话绑定的活动工程。

    当工作区中有多个工程，且需要切换到另一个已有工程进行查看、设计或校验时调用。
    会自动更新当前会话的上下文路径（原理图、PCB、工程配置），避免在错误工程上操作。
    """
    ctx = _ctx(runtime)
    projects = ws.scan_projects(ctx.user_id)
    target = next(
        (p for p in projects if p["name"] == project_name or p["dir"] == project_name or p["dir"].endswith(f"/{project_name}")),
        None,
    )
    if not target:
        available = [p["name"] for p in projects]
        raise ValueError(f"未找到工程 '{project_name}'。当前可用工程：{available}")

    root = ws.user_root(ctx.user_id).resolve()
    clean_name = target["name"]
    rel_dir = target["dir"]
    pro_file = target["pro"]
    sch_file = target["sch"]
    pcb_file = target["pcb"]

    ctx.project_name = clean_name
    ctx.project_dir = rel_dir
    ctx.schematic_path = str((root / sch_file).resolve()) if sch_file else None
    ctx.pcb_path = str((root / pcb_file).resolve()) if pcb_file else None
    ctx.pro_path = str((root / pro_file).resolve()) if pro_file else None

    proj_id = await _sync_project_to_db(ctx, clean_name, rel_dir, pro_file or "", sch_file or "", pcb_file or "")
    if proj_id:
        ctx.project_id = proj_id

    return _ok(
        project_id=ctx.project_id,
        project_name=clean_name,
        project_dir=rel_dir,
        schematic_path=ctx.schematic_path,
        pcb_path=ctx.pcb_path,
        message=f"已成功切换当前活动工程至 '{clean_name}'，后续原理图与 PCB 操作均将针对该工程执行。",
    )


@tool
@_guard
def get_project_structure(project_dir: str, runtime: ToolRuntime[AgentContext]) -> str:
    """获取某个工程目录下的文件列表（相对工作区的目录路径）。"""
    ctx = _ctx(runtime)
    files = ws.list_files(ctx.user_id, project_dir)
    return _ok(project_dir=project_dir, files=files, count=len(files))


# ---------------------------------------------------------------------------
# PCB query tools
# ---------------------------------------------------------------------------
@tool
@_guard
def get_board_info(runtime: ToolRuntime[AgentContext], pcb_path: str | None = None) -> str:
    """获取 PCB 基本信息：尺寸、层数、封装/网络/走线数量、板框、设计规则。不传 pcb_path 时使用当前工程。"""
    _, board, _ = _pcb(runtime, pcb_path)
    return _ok(**board.info())


def _page(items: list[Any], limit: int | None, offset: int) -> tuple[list[Any], dict[str, Any]]:
    """Slice *items* and describe the page so the model knows what it did not see."""
    total = len(items)
    start = max(int(offset or 0), 0)
    if limit is None or int(limit) <= 0:
        page = items[start:]
    else:
        page = items[start : start + int(limit)]
    meta: dict[str, Any] = {"count": len(page), "total": total, "offset": start}
    if start + len(page) < total:
        meta["next_offset"] = start + len(page)
        meta["note"] = f"仅返回 {len(page)}/{total} 条；用 offset={start + len(page)} 继续读取。"
    return page, meta


@tool
@_guard
def list_footprints(
    runtime: ToolRuntime[AgentContext],
    pcb_path: str | None = None,
    layer: Literal["F.Cu", "B.Cu"] | None = None,
    reference_prefix: str | None = None,
    limit: int | None = 200,
    offset: int = 0,
) -> str:
    """列出 PCB 上已放置的封装（参考号、值、封装、位置、旋转、边界框）。

    可按层或参考号前缀过滤；大板请用 limit/offset 分页（默认每页 200，返回 total 与 next_offset）。
    """
    _, board, _ = _pcb(runtime, pcb_path)
    fps = board.footprints
    if layer:
        fps = [f for f in fps if f.layer == layer]
    if reference_prefix:
        fps = [f for f in fps if f.ref.upper().startswith(reference_prefix.upper())]
    page, meta = _page(fps, limit, offset)
    return _ok(footprints=[f.to_dict() for f in page], **meta)


@tool
@_guard
def get_footprint(reference: str, runtime: ToolRuntime[AgentContext], pcb_path: str | None = None) -> str:
    """获取单个封装的详细信息，包括每个焊盘的绝对坐标、尺寸和所属网络。"""
    _, board, _ = _pcb(runtime, pcb_path)
    f = board.get_footprint(reference)
    if f is None:
        return _err(f"未找到封装 {reference}")
    return _ok(footprint=f.to_dict(include_pads=True))


@tool
@_guard
def get_footprint_bbox(reference: str, runtime: ToolRuntime[AgentContext], pcb_path: str | None = None) -> str:
    """获取封装的占位边界框 (x1, y1, x2, y2) 与尺寸，用于校验是否重叠。"""
    _, board, _ = _pcb(runtime, pcb_path)
    f = board.get_footprint(reference)
    if f is None:
        return _err(f"未找到封装 {reference}")
    return _ok(reference=f.ref, bbox=[round(v, 4) for v in f.bbox], width=round(f.width, 3), height=round(f.height, 3))


@tool
@_guard
def get_board_bounding_box(runtime: ToolRuntime[AgentContext], pcb_path: str | None = None) -> str:
    """获取所有封装的并集边界框以及板框 (Edge.Cuts) 边界框。"""
    _, board, _ = _pcb(runtime, pcb_path)
    return _ok(footprints_bbox=board.board_bbox(), outline_bbox=board.outline_bbox())


@tool
@_guard
def list_nets(
    runtime: ToolRuntime[AgentContext],
    pcb_path: str | None = None,
    name_contains: str | None = None,
    limit: int | None = 300,
    offset: int = 0,
) -> str:
    """列出 PCB 上的网络及其焊盘、走线段数量。可按名称片段过滤，大板请用 limit/offset 分页。"""
    _, board, _ = _pcb(runtime, pcb_path)
    nets = board.list_nets()
    if name_contains:
        needle = name_contains.lower()
        nets = [n for n in nets if needle in str(n.get("name", "")).lower()]
    page, meta = _page(nets, limit, offset)
    return _ok(nets=page, **meta)


@tool
@_guard
def get_ratsnest(runtime: ToolRuntime[AgentContext], pcb_path: str | None = None) -> str:
    """获取未布线的飞线连接（近似：存在多个焊盘但没有任何走线的网络）。"""
    _, board, _ = _pcb(runtime, pcb_path)
    r = board.ratsnest()
    return _ok(unrouted=r, count=len(r))


@tool
@_guard
def score_placement(runtime: ToolRuntime[AgentContext], pcb_path: str | None = None) -> str:
    """对当前布局质量打分 (0-100)：重叠、板框、连接紧凑度与栅格对齐。"""
    ctx, board, _ = _pcb(runtime, pcb_path)
    grid = float((ctx.design_constraints or {}).get("placement_grid_mm", 0.1))
    return _ok(**board.placement_score(grid))


@tool
@_guard
def get_board_outline(runtime: ToolRuntime[AgentContext], pcb_path: str | None = None) -> str:
    """读取 Edge.Cuts 板框图元及其边界框。"""
    _, board, _ = _pcb(runtime, pcb_path)
    return _ok(items=board.outline_items(), bbox=board.outline_bbox())


@tool
@_guard
def find_free_pcb_area(
    width: float, height: float, runtime: ToolRuntime[AgentContext], pcb_path: str | None = None, margin: float = 0.5
) -> str:
    """在板框内寻找可容纳 width x height (mm) 的空闲区域，返回候选中心坐标。"""
    _, board, _ = _pcb(runtime, pcb_path)
    cands = board.find_free_area(width, height, margin=margin)
    return _ok(candidates=cands, count=len(cands))


@tool
@_guard
def get_effective_design_rules(runtime: ToolRuntime[AgentContext], pcb_path: str | None = None) -> str:
    """获取当前生效的板级设计规则（最小间距、线宽、过孔、板边间距等）。"""
    _, board, _ = _pcb(runtime, pcb_path)
    return _ok(rules=board.design_rules(), layers=board.layers())


@tool
@_guard
def suggest_placement_order(runtime: ToolRuntime[AgentContext], pcb_path: str | None = None) -> str:
    """推荐封装放置顺序：先连接器/大器件与主芯片，再去耦电容，最后小阻容。"""
    _, board, _ = _pcb(runtime, pcb_path)

    def rank(f):
        ref = f.ref.upper()
        if ref.startswith(("J", "P", "X", "CONN")):
            return 0
        if ref.startswith(("U", "Q", "IC")):
            return 1
        if ref.startswith(("Y", "L", "T", "D")):
            return 2
        if ref.startswith("C") and f.val.lower().replace("µ", "u") in ("100n", "100nf", "0.1u", "0.1uf", "1u", "1uf", "10u", "10uf"):
            return 3
        return 4

    order = [f.to_dict() | {"priority": rank(f)} for f in sorted(board.footprints, key=lambda f: (rank(f), -f.width * f.height))]
    return _ok(order=order)


# ---------------------------------------------------------------------------
# PCB edit / placement tools
# ---------------------------------------------------------------------------
@tool
@_guard
def set_footprint_position(
    reference: str,
    runtime: ToolRuntime[AgentContext],
    x: float | None = None,
    y: float | None = None,
    rotation: float | None = None,
    pcb_path: str | None = None,
) -> str:
    """移动和/或旋转单个封装（毫米，+Y 向下，旋转 CCW 角度）。未给出的参数保持不变。"""
    _, board, p = _pcb(runtime, pcb_path)
    before = board.get_footprint(reference)
    if before is None:
        return _err(f"未找到封装 {reference}")
    old = {"x": before.x, "y": before.y, "rotation": before.rotation}
    after = board.set_footprint_position(reference, x, y, rotation)
    board.save()
    overlaps = [pair for pair in board.courtyard_overlaps() if after.ref in pair]
    return _ok(reference=after.ref, before=old, after={"x": after.x, "y": after.y, "rotation": after.rotation}, overlaps=overlaps, file=str(p))


@tool
@_guard
def move_footprints_by_delta(
    references: list[str], dx: float, dy: float, runtime: ToolRuntime[AgentContext], pcb_path: str | None = None
) -> str:
    """将多个封装整体平移 (dx, dy) 毫米。"""
    _, board, p = _pcb(runtime, pcb_path)
    moved = []
    for ref in references:
        f = board.get_footprint(ref)
        if f is None:
            return _err(f"未找到封装 {ref}")
        nf = board.set_footprint_position(ref, f.x + dx, f.y + dy, None)
        moved.append({"reference": nf.ref, "x": nf.x, "y": nf.y})
    board.save()
    return _ok(moved=moved, file=str(p))


@tool
@_guard
def align_footprints(
    references: list[str],
    axis: Literal["x", "y"],
    runtime: ToolRuntime[AgentContext],
    value: float | None = None,
    pcb_path: str | None = None,
) -> str:
    """将多个封装对齐到同一 X 或 Y 坐标（默认对齐到第一个封装的坐标）。"""
    _, board, p = _pcb(runtime, pcb_path)
    first = board.get_footprint(references[0]) if references else None
    if first is None:
        return _err("references 为空或第一个封装不存在")
    target = value if value is not None else (first.x if axis == "x" else first.y)
    out = []
    for ref in references:
        f = board.get_footprint(ref)
        if f is None:
            return _err(f"未找到封装 {ref}")
        nf = board.set_footprint_position(ref, target if axis == "x" else None, target if axis == "y" else None, None)
        out.append({"reference": nf.ref, "x": nf.x, "y": nf.y})
    board.save()
    return _ok(aligned=out, axis=axis, value=target, file=str(p))


@tool
@_guard
def distribute_footprints(
    references: list[str],
    axis: Literal["x", "y"],
    runtime: ToolRuntime[AgentContext],
    start: float | None = None,
    end: float | None = None,
    pcb_path: str | None = None,
) -> str:
    """沿 X 或 Y 轴等距分布多个封装（默认在首尾封装之间分布）。"""
    _, board, p = _pcb(runtime, pcb_path)
    fps = [board.get_footprint(r) for r in references]
    if any(f is None for f in fps) or len(fps) < 2:
        return _err("至少需要两个存在的封装")
    fps.sort(key=lambda f: f.x if axis == "x" else f.y)  # type: ignore[union-attr]
    a = start if start is not None else (fps[0].x if axis == "x" else fps[0].y)  # type: ignore[union-attr]
    b = end if end is not None else (fps[-1].x if axis == "x" else fps[-1].y)  # type: ignore[union-attr]
    step = (b - a) / (len(fps) - 1)
    out = []
    for i, f in enumerate(fps):
        v = round(a + i * step, 3)
        nf = board.set_footprint_position(f.ref, v if axis == "x" else None, v if axis == "y" else None, None)  # type: ignore[union-attr]
        out.append({"reference": nf.ref, "x": nf.x, "y": nf.y})
    board.save()
    return _ok(distributed=out, axis=axis, file=str(p))


@tool
@_guard
def flip_footprint(reference: str, runtime: ToolRuntime[AgentContext], pcb_path: str | None = None) -> str:
    """将封装在顶层 (F.Cu) 与底层 (B.Cu) 之间翻转。"""
    _, board, p = _pcb(runtime, pcb_path)
    if board.get_footprint(reference) is None:
        return _err(f"未找到封装 {reference}")
    f = board.flip_footprint(reference)
    board.save()
    return _ok(reference=f.ref, layer=f.layer, file=str(p))


@tool
@_guard
def set_footprint_property(
    reference: str, name: str, value: str, runtime: ToolRuntime[AgentContext], pcb_path: str | None = None
) -> str:
    """设置封装的属性字段（如 Value、Datasheet、MPN）。"""
    _, board, p = _pcb(runtime, pcb_path)
    if board.get_footprint(reference) is None:
        return _err(f"未找到封装 {reference}")
    f = board.set_footprint_property(reference, name, value)
    board.save()
    return _ok(reference=f.ref, property=name, value=value, file=str(p))


def _find_footprint_mod(fp_name: str) -> Path | None:
    fp_base_dirs = [
        Path("/usr/share/kicad/footprints"),
        Path("/root/.local/share/kicad/8.0/footprints"),
        Path("/app/backend/data/libraries/footprints"),
    ]
    if ":" in fp_name:
        lib, mod = fp_name.split(":", 1)
    else:
        lib, mod = "", fp_name
    for base in fp_base_dirs:
        if not base.is_dir():
            continue
        if lib:
            p = base / f"{lib}.pretty" / f"{mod}.kicad_mod"
            if p.is_file():
                return p
        for cand in base.glob(f"*.pretty/{mod}.kicad_mod"):
            return cand
    return None


@tool
@_guard
def update_pcb_from_schematic(
    runtime: ToolRuntime[AgentContext],
    schematic_path: str | None = None,
    pcb_path: str | None = None,
    sync_values: bool = True,
    sync_nets: bool = True,
) -> str:
    """将原理图中的元器件与封装同步到 PCB（无头原生模式）。

    从当前工程的原理图读取所有元器件及其封装定义，从 KiCad 标准封装库加载对应的封装模型，
    将其按间距排布放置到 PCB 中，并保存 PCB 文件。原理图完成后调用此工具将元器件同步至 PCB。
    """
    ctx, sch, sch_p = _sch(runtime, schematic_path)
    _, board, pcb_p = _pcb(runtime, pcb_path)

    outline = board.outline_bbox()
    cx = (outline[0] + outline[2]) / 2 if outline else 115.0
    cy = (outline[1] + outline[3]) / 2 if outline else 72.5

    from app.kicad import sexpr

    existing = {fp.ref: fp for fp in board.footprints}
    pin_to_net = {
        pin: net["name"]
        for net in sch.netlist()["nets"]
        for pin in net["pins"]
    }
    net_codes = {name: code for code, name in board.nets.items() if name}
    next_net_code = max(board.nets.keys(), default=0) + 1

    def ensure_net(name: str) -> int:
        nonlocal next_net_code
        if name not in net_codes:
            net_codes[name] = next_net_code
            board.tree.append([sexpr.Sym("net"), next_net_code, sexpr.Str(name)])
            next_net_code += 1
        return net_codes[name]

    def sync_pad_nets(node: list, reference: str) -> list[dict[str, Any]]:
        changed = []
        if not sync_nets:
            return changed
        for pad in sexpr.children(node, "pad"):
            number = str(pad[1]) if len(pad) > 1 else ""
            net_name = pin_to_net.get(f"{reference}.{number}")
            if not net_name:
                continue
            code = ensure_net(net_name)
            current = sexpr.child(pad, "net")
            current_code = int(current[1]) if current and len(current) > 1 else 0
            current_name = str(current[2]) if current and len(current) > 2 else ""
            if current_code == code and current_name == net_name:
                continue
            sexpr.set_child(pad, "net", [code, sexpr.Str(net_name)])
            changed.append({"pad": number, "net": net_name, "net_code": code})
        return changed

    spacing = 15.0
    x_cur = cx - 20.0
    y_cur = cy

    added_components = []
    updated_components = []
    skipped_components = []

    for sym in sch.symbols:
        if sym.ref.startswith("#"):
            continue
        footprint = existing.get(sym.ref)
        if footprint is not None:
            changes: dict[str, Any] = {}
            if sync_values and sym.val != footprint.val:
                value_property = next(
                    (
                        prop
                        for prop in sexpr.children(footprint.node, "property")
                        if len(prop) > 2 and str(prop[1]) == "Value"
                    ),
                    None,
                )
                if value_property is not None:
                    value_property[2] = sexpr.Str(sym.val)
                else:
                    footprint.node.append(
                        [sexpr.Sym("property"), sexpr.Str("Value"), sexpr.Str(sym.val)]
                    )
                changes["value"] = {"before": footprint.val, "after": sym.val}
            changed_nets = sync_pad_nets(footprint.node, sym.ref)
            if changed_nets:
                changes["nets"] = changed_nets
            if changes:
                updated_components.append({"reference": sym.ref, **changes})
            continue
        if not sym.footprint:
            skipped_components.append({"reference": sym.ref, "reason": "未在原理图中指定封装 (footprint)"})
            continue

        mod_path = _find_footprint_mod(sym.footprint)
        if not mod_path:
            skipped_components.append({"reference": sym.ref, "footprint": sym.footprint, "reason": "未在库中找到对应封装文件"})
            continue

        raw_fp = sexpr.load(mod_path)
        for prop in sexpr.children(raw_fp, "property"):
            if len(prop) > 2 and prop[1] == "Reference":
                prop[2] = sym.ref
            elif len(prop) > 2 and prop[1] == "Value":
                prop[2] = sym.val

        at_node = sexpr.child(raw_fp, "at")
        if at_node:
            at_node[1] = round(x_cur, 2)
            at_node[2] = round(y_cur, 2)
        else:
            raw_fp.append(["at", round(x_cur, 2), round(y_cur, 2), 0])

        assigned_nets = sync_pad_nets(raw_fp, sym.ref)
        board.tree.append(raw_fp)
        added_components.append(
            {
                "reference": sym.ref,
                "value": sym.val,
                "footprint": sym.footprint,
                "x": round(x_cur, 2),
                "y": round(y_cur, 2),
                "nets": assigned_nets,
            }
        )
        x_cur += spacing

    if added_components or updated_components:
        board.save(pcb_p)
    return _ok(
        message=(
            f"原理图同步完成：新增 {len(added_components)} 个封装，"
            f"更新 {len(updated_components)} 个现有封装。"
        ),
        added=added_components,
        updated=updated_components,
        skipped=skipped_components,
        total_footprints=len(board.footprints) + len(added_components),
        file=str(pcb_p),
    )


@tool
@_guard
def set_board_outline_rect(
    x1: float, y1: float, x2: float, y2: float, runtime: ToolRuntime[AgentContext], pcb_path: str | None = None
) -> str:
    """用矩形替换板框 (Edge.Cuts)。"""
    _, board, p = _pcb(runtime, pcb_path)
    board.set_outline_rect(x1, y1, x2, y2)
    board.save()
    return _ok(outline_bbox=board.outline_bbox(), file=str(p))


@tool
@_guard
def clear_board_outline(runtime: ToolRuntime[AgentContext], pcb_path: str | None = None) -> str:
    """清除板框 (Edge.Cuts) 上的所有图元。破坏性操作，需要用户确认。"""
    _, board, p = _pcb(runtime, pcb_path)
    n = board.clear_outline()
    board.save()
    return _ok(removed=n, file=str(p))


# ---------------------------------------------------------------------------
# DRC / BOM
# ---------------------------------------------------------------------------
@tool
@_guard
def run_drc_check(runtime: ToolRuntime[AgentContext], pcb_path: str | None = None) -> str:
    """运行设计规则检查 (DRC)。安装了 kicad-cli 时使用 KiCad 引擎，否则使用内置轻量 DRC。返回违规、未连接项与统计。"""
    _, board, p = _pcb(runtime, pcb_path)
    if kicad_cli.available():
        try:
            result = kicad_cli.run_drc(p)
            return _ok(**result)
        except Exception as exc:  # noqa: BLE001
            lite = board.drc_lite()
            lite["note"] = f"kicad-cli DRC 失败 ({exc})，已回退到内置 DRC"
            return _ok(**lite)
    result = board.drc_lite()
    result["note"] = "未检测到 kicad-cli，使用内置轻量 DRC（间距 / 占位重叠 / 板边 / 未连接）"
    return _ok(**result)


@tool
@_guard
def run_erc_check(runtime: ToolRuntime[AgentContext], schematic_path: str | None = None) -> str:
    """运行电气规则检查 (ERC)。优先使用 KiCad 10 CLI，失败时回退到内置轻量检查。"""
    _, schematic, path = _sch(runtime, schematic_path)
    if kicad_cli.available():
        try:
            return _ok(**kicad_cli.run_erc(path))
        except Exception as exc:  # noqa: BLE001
            result = schematic.erc_lite()
            result["note"] = f"kicad-cli ERC 失败 ({exc})，已回退到内置 ERC"
            return _ok(**result)
    return _ok(**schematic.erc_lite())


@tool
@_guard
def run_design_review(runtime: ToolRuntime[AgentContext]) -> str:
    """统一运行 ERC、DRC 与布局质量评分，并返回问题分类和可自动修复动作建议。"""
    ctx = _ctx(runtime)
    pcb = _resolve(ctx, ctx.pcb_path) if ctx.pcb_path else None
    schematic = _resolve(ctx, ctx.schematic_path) if ctx.schematic_path else None
    return _dumps(review_project(pcb, schematic, ctx.design_constraints))


@tool
@_guard
def run_eco_check(runtime: ToolRuntime[AgentContext]) -> str:
    """比较原理图与 PCB 的器件、值、封装和网络，生成 ECO 差异与安全同步建议。"""
    ctx = _ctx(runtime)
    if not ctx.schematic_path or not ctx.pcb_path:
        return _err("ECO 检查需要当前工程同时包含原理图和 PCB")
    return _dumps(
        build_eco_report(
            _resolve(ctx, ctx.schematic_path),
            _resolve(ctx, ctx.pcb_path),
        )
    )


@tool
@_guard
def analyze_bom(runtime: ToolRuntime[AgentContext], pcb_path: str | None = None) -> str:
    """生成 BOM 并做可制造性 (DFM) 检查：归并物料、缺失封装/值、重复位号、封装库校验、超小封装、双面贴装等。"""
    ctx, _, p = _pcb(runtime, pcb_path)
    schematic = _resolve(ctx, ctx.schematic_path) if ctx.schematic_path else None
    return _dumps(build_bom_report(p, schematic, ctx.design_constraints))


@tool
@_guard
def list_circuit_templates(runtime: ToolRuntime[AgentContext]) -> str:
    """列出可用的常用电路模板（LDO 电源、LED 指示、分压、去耦、USB-C 取电、RS-485）及其参数。"""
    _ctx(runtime)
    return _ok(templates=list_templates())


@tool
@_guard
def preview_circuit_template(
    template: str,
    runtime: ToolRuntime[AgentContext],
    params: dict[str, Any] | None = None,
    anchor_x: float | None = None,
    anchor_y: float | None = None,
) -> str:
    """只读预览电路模板展开结果：器件、封装、坐标、连线与计算说明，并校验符号/封装是否在 KiCad 库中。"""
    ctx, sch, _ = _sch(runtime, None)
    ax, ay = _template_anchor(sch, template, params or {}, anchor_x, anchor_y)
    expanded = expand_template(template, params or {}, ax, ay)
    availability = check_library_availability(expanded)
    return _ok(**expanded, library=availability)


def _template_anchor(sch: Schematic, template: str, params: dict[str, Any], x: float | None, y: float | None) -> tuple[float, float]:
    """Pick a free spot on the sheet for the template (or honour an explicit anchor)."""
    if x is not None and y is not None:
        return float(x), float(y)
    probe = expand_template(template, params, 0.0, 0.0)
    width, height = bounding_size(probe)
    min_dx = min(p["dx"] for p in probe["parts"])
    min_dy = min(p["dy"] for p in probe["parts"])
    area = sch.sheet_info()["recommended_drawing_area"]
    margin = 5.08
    occupied = []
    for symbol in sch.symbols:
        xs = [p.x for p in symbol.pins] or [symbol.x]
        ys = [p.y for p in symbol.pins] or [symbol.y]
        occupied.append((min(xs) - margin, min(ys) - margin, max(xs) + margin, max(ys) + margin))
    step = 12.7
    top = area["y1"]
    while top + height <= area["y2"]:
        left = area["x1"]
        while left + width <= area["x2"]:
            rect = (left, top, left + width, top + height)
            if not any(rect[0] < o[2] and rect[2] > o[0] and rect[1] < o[3] and rect[3] > o[1] for o in occupied):
                return left - min_dx + 12.7, top - min_dy + 12.7
            left += step
        top += step
    # Sheet is full: fall back to the right of everything already placed.
    right = max((o[2] for o in occupied), default=area["x1"])
    return right - min_dx + 12.7, area["y1"] - min_dy + 12.7


@tool
@_guard
async def apply_circuit_template(
    template: str,
    runtime: ToolRuntime[AgentContext],
    params: dict[str, Any] | None = None,
    anchor_x: float | None = None,
    anchor_y: float | None = None,
    schematic_path: str | None = None,
) -> str:
    """按模板在原理图中放置标准库器件、设置封装、连线并标注网络。需先经 submit_change_plan 批准。"""
    ctx, sch, path = _sch(runtime, schematic_path)
    ax, ay = _template_anchor(sch, template, params or {}, anchor_x, anchor_y)
    expanded = expand_template(template, params or {}, ax, ay)
    availability = check_library_availability(expanded)
    if availability["missing_symbols"] or availability["missing_footprints"]:
        return _err(
            "KiCad 库中缺少模板所需的符号或封装",
            missing_symbols=availability["missing_symbols"],
            missing_footprints=availability["missing_footprints"],
        )
    if not kcaa_tool_available("add_symbol_to_schematic"):
        return _err("放置符号需要上游 kcaa 工具（KCAA_MODE=direct）")

    references: dict[str, str] = {}
    warnings: list[str] = []
    for part in expanded["parts"]:
        placed = await call_kcaa(
            "add_symbol_to_schematic",
            schematic_path=str(path),
            library_name=part["library"],
            symbol_name=part["symbol"],
            x=part["x"],
            y=part["y"],
            rotation=int(part["rotation"]),
            value=part["value"],
        )
        if not placed.get("success", True) or placed.get("error"):
            return _err(f"放置 {part['library']}:{part['symbol']} 失败：{placed.get('error')}", placed=references)
        reference = str(placed.get("reference_assigned") or "")
        references[part["id"]] = reference
        if part["footprint"]:
            result = await call_kcaa(
                "set_symbol_property",
                schematic_path=str(path),
                reference=reference,
                property_name="Footprint",
                property_value=part["footprint"],
            )
            if result.get("error"):
                warnings.append(f"{reference} 封装设置失败：{result['error']}")

    wires: list[dict[str, Any]] = []
    for wire in expanded["wires"]:
        from_id, from_pin = wire["from"].split(".", 1)
        to_id, to_pin = wire["to"].split(".", 1)
        result = await call_kcaa(
            "connect_pins_with_wire",
            schematic_path=str(path),
            from_ref=references[from_id],
            from_pin=from_pin,
            to_ref=references[to_id],
            to_pin=to_pin,
        )
        entry = {"from": f"{references[from_id]}.{from_pin}", "to": f"{references[to_id]}.{to_pin}", "ok": not result.get("error")}
        if result.get("error"):
            entry["error"] = result["error"]
            warnings.append(f"连线 {entry['from']} → {entry['to']} 失败：{result['error']}")
        wires.append(entry)

    labels: list[dict[str, Any]] = []
    if expanded["labels"]:
        current = Schematic.load(path)
        for label in expanded["labels"]:
            part_id, pin_number = label["pin"].split(".", 1)
            symbol = current.get_symbol(references[part_id])
            pin = next((p for p in (symbol.pins if symbol else []) if p.number == pin_number), None)
            if pin is None:
                warnings.append(f"标签 {label['text']} 未找到引脚 {label['pin']}")
                continue
            result = await call_kcaa(
                "add_label_to_schematic",
                schematic_path=str(path),
                text=label["text"],
                x=pin.x,
                y=pin.y,
                angle=0,
                label_type="global",
            )
            labels.append({"text": label["text"], "pin": f"{references[part_id]}.{pin_number}", "ok": not result.get("error")})
            if result.get("error"):
                warnings.append(f"标签 {label['text']} 添加失败：{result['error']}")

    return _ok(
        template=expanded["template"],
        name=expanded["name"],
        params=expanded["params"],
        anchor=expanded["anchor"],
        placed=[{"id": pid, "reference": ref, "symbol": f"{p['library']}:{p['symbol']}", "value": p["value"], "footprint": p["footprint"]} for p, (pid, ref) in zip(expanded["parts"], references.items(), strict=False)],
        wires=wires,
        labels=labels,
        notes=expanded["notes"],
        warnings=warnings,
        file=str(path),
    )


# ---------------------------------------------------------------------------
# schematic tools
# ---------------------------------------------------------------------------
@tool
@_guard
def get_schematic_sheet_info(runtime: ToolRuntime[AgentContext], schematic_path: str | None = None) -> str:
    """获取原理图纸张大小、栅格、推荐绘图区域及统计信息。"""
    _, sch, _ = _sch(runtime, schematic_path)
    return _ok(**sch.sheet_info())


@tool
@_guard
def list_schematic_symbols(
    runtime: ToolRuntime[AgentContext],
    schematic_path: str | None = None,
    reference_prefix: str | None = None,
    limit: int | None = 200,
    offset: int = 0,
) -> str:
    """列出原理图中已放置的符号（参考号、值、封装、位置、旋转）。可按参考号前缀过滤，大图请用 limit/offset 分页。"""
    _, sch, _ = _sch(runtime, schematic_path)
    symbols = list(sch.symbols)
    if reference_prefix:
        symbols = [s for s in symbols if s.ref.upper().startswith(reference_prefix.upper())]
    page, meta = _page(symbols, limit, offset)
    return _ok(symbols=[s.to_dict() for s in page], **meta)


@tool
@_guard
def get_schematic_symbol(reference: str, runtime: ToolRuntime[AgentContext], schematic_path: str | None = None) -> str:
    """获取单个原理图符号详情，包括引脚的绝对坐标。"""
    _, sch, _ = _sch(runtime, schematic_path)
    s = sch.get_symbol(reference)
    if s is None:
        return _err(f"未找到符号 {reference}")
    return _ok(symbol=s.to_dict(include_pins=True))


@tool
@_guard
def extract_schematic_netlist(runtime: ToolRuntime[AgentContext], schematic_path: str | None = None) -> str:
    """从原理图提取网表（基于导线端点、引脚与标签的连通性近似）。"""
    _, sch, _ = _sch(runtime, schematic_path)
    return _ok(**sch.netlist())


@tool
@_guard
def find_component_connections(reference: str, runtime: ToolRuntime[AgentContext], schematic_path: str | None = None) -> str:
    """查找某个元件每个引脚所连接的网络与对端引脚。"""
    _, sch, _ = _sch(runtime, schematic_path)
    if sch.get_symbol(reference) is None:
        return _err(f"未找到符号 {reference}")
    prefix = reference.upper() + "."
    out = []
    for net in sch.netlist()["nets"]:
        mine = [p for p in net["pins"] if p.upper().startswith(prefix)]
        if mine:
            out.append({"net": net["name"], "pins": mine, "connected_to": [p for p in net["pins"] if not p.upper().startswith(prefix)]})
    return _ok(reference=reference, connections=out)


@tool
@_guard
def check_reference_conflicts(runtime: ToolRuntime[AgentContext], schematic_path: str | None = None) -> str:
    """检查原理图中重复的参考号。"""
    _, sch, _ = _sch(runtime, schematic_path)
    dup = sch.reference_conflicts()
    return _ok(conflicts=dup, has_conflicts=bool(dup))


@tool
@_guard
def list_labels_in_schematic(runtime: ToolRuntime[AgentContext], schematic_path: str | None = None) -> str:
    """列出原理图中的所有网络标签（本地 / 全局 / 层次 / 电源）。"""
    _, sch, _ = _sch(runtime, schematic_path)
    labels = sch.labels()
    return _ok(labels=labels, count=len(labels))


@tool
@_guard
def move_component(
    reference: str,
    runtime: ToolRuntime[AgentContext],
    x: float | None = None,
    y: float | None = None,
    rotation: float | None = None,
    schematic_path: str | None = None,
) -> str:
    """移动和/或旋转原理图符号（自动吸附 1.27 mm 栅格，旋转仅 0/90/180/270）。"""
    _, sch, p = _sch(runtime, schematic_path)
    if sch.get_symbol(reference) is None:
        return _err(f"未找到符号 {reference}")
    s = sch.move_symbol(reference, x, y, rotation)
    sch.save()
    return _ok(reference=s.ref, x=s.x, y=s.y, rotation=s.rotation, file=str(p))


@tool
@_guard
def set_symbol_property(
    reference: str, name: str, value: str, runtime: ToolRuntime[AgentContext], schematic_path: str | None = None
) -> str:
    """设置原理图符号的属性（Value、Footprint、Datasheet 等）。"""
    _, sch, p = _sch(runtime, schematic_path)
    if sch.get_symbol(reference) is None:
        return _err(f"未找到符号 {reference}")
    s = sch.set_property(reference, name, value)
    sch.save()
    return _ok(reference=s.ref, property=name, value=value, file=str(p))


@tool
@_guard
def add_wire_to_schematic(
    x1: float, y1: float, x2: float, y2: float, runtime: ToolRuntime[AgentContext], schematic_path: str | None = None
) -> str:
    """按两个端点添加一段导线（自动吸附栅格）。"""
    _, sch, p = _sch(runtime, schematic_path)
    w = sch.add_wire(x1, y1, x2, y2)
    sch.save()
    return _ok(wire=w, file=str(p))


@tool
@_guard
def add_label_to_schematic(
    text: str, x: float, y: float, runtime: ToolRuntime[AgentContext], rotation: float = 0, schematic_path: str | None = None
) -> str:
    """在指定位置添加本地网络标签。"""
    _, sch, p = _sch(runtime, schematic_path)
    l = sch.add_label(text, x, y, rotation)
    sch.save()
    return _ok(label=l, file=str(p))


# ---------------------------------------------------------------------------
# versioning
# ---------------------------------------------------------------------------
@tool
@_guard
def save_file_version(file_path: str, runtime: ToolRuntime[AgentContext], label: str = "") -> str:
    """为文件保存一个可回滚的版本快照。"""
    ctx = _ctx(runtime)
    return _ok(**ws.save_version(ctx.user_id, file_path, label))


@tool
@_guard
def list_file_versions(file_path: str, runtime: ToolRuntime[AgentContext]) -> str:
    """列出文件已保存的版本快照。"""
    ctx = _ctx(runtime)
    versions = ws.list_versions(ctx.user_id, file_path)
    return _ok(versions=versions, count=len(versions))


@tool
@_guard
def restore_file_version(file_path: str, version_id: str, runtime: ToolRuntime[AgentContext]) -> str:
    """将文件恢复到指定快照（恢复前会自动再保存一份当前版本）。破坏性操作，需要用户确认。"""
    ctx = _ctx(runtime)
    return _ok(**ws.restore_version(ctx.user_id, file_path, version_id))


# ---------------------------------------------------------------------------
# skill tools (native EDA workflow guidance)
# ---------------------------------------------------------------------------
SKILLS_DIR = Path(__file__).resolve().parent.parent / "skills"
DELETED_DIR = SKILLS_DIR / ".deleted"
_VALID_NAME_RE = re.compile(r"^[a-z0-9-_]+$", re.IGNORECASE)


def _normalize_skill_name(name: str) -> str:
    clean = name.strip().lower()
    if clean.endswith("/skill.md") or clean.endswith("\\skill.md"):
        clean = clean[:-9]
    elif clean.endswith(".md"):
        clean = clean[:-3]
    return clean.replace("_", "-").replace(" ", "-").strip("/")


def _parse_skill_front_matter(text: str) -> tuple[dict[str, str], str]:
    if not text.startswith("---"):
        return {}, text
    end = text.find("\n---", 3)
    if end == -1:
        return {}, text
    front_matter_str = text[3:end].strip()
    body = text[end + 4 :].lstrip("\n")
    meta: dict[str, str] = {}
    for line in front_matter_str.splitlines():
        if ":" in line:
            k, _, v = line.partition(":")
            meta[k.strip()] = v.strip().strip('"').strip("'")
    return meta, body


def _load_all_skills() -> list[dict[str, Any]]:
    if not SKILLS_DIR.exists():
        return []
    skills: dict[str, dict[str, Any]] = {}
    # 1. Directory-based skills (<name>/SKILL.md)
    for skill_md in sorted(SKILLS_DIR.glob("*/SKILL.md")):
        if skill_md.parent.name.startswith((".", "_")) or skill_md.parent.name == ".deleted":
            continue
        try:
            meta, body = _parse_skill_front_matter(skill_md.read_text(encoding="utf-8"))
            name = meta.get("name") or skill_md.parent.name
            skills[name] = {
                "name": name,
                "description": meta.get("description", ""),
                "priority": int(meta.get("priority", 50)),
                "path": skill_md,
                "body": body,
            }
        except Exception as exc:  # noqa: BLE001
            log.warning("Failed to read skill file %s: %s", skill_md, exc)

    # 2. Flat skills (<name>.md)
    for md_file in sorted(SKILLS_DIR.glob("*.md")):
        if md_file.name.startswith((".", "_")):
            continue
        try:
            meta, body = _parse_skill_front_matter(md_file.read_text(encoding="utf-8"))
            name = meta.get("name") or md_file.stem
            if name not in skills:
                skills[name] = {
                    "name": name,
                    "description": meta.get("description", ""),
                    "priority": int(meta.get("priority", 50)),
                    "path": md_file,
                    "body": body,
                }
        except Exception as exc:  # noqa: BLE001
            log.warning("Failed to read flat skill file %s: %s", md_file, exc)

    return sorted(skills.values(), key=lambda s: (-s["priority"], s["name"]))


def _find_skill(name: str) -> dict[str, Any] | None:
    all_skills = _load_all_skills()
    norm = _normalize_skill_name(name)
    for s in all_skills:
        if _normalize_skill_name(s["name"]) == norm:
            return s
        p: Path = s["path"]
        if _normalize_skill_name(p.parent.name) == norm or _normalize_skill_name(p.stem) == norm:
            return s
    return None


@tool
@_guard
def list_skills() -> str:
    """列出所有可用的 EDA 工作流技能与规范（如原理图绘制、审查、PCB走线、电源布局、DRC修复等）。"""
    skills = _load_all_skills()
    if not skills:
        return "No workflow skills are currently available."
    lines = [f"- {s['name']}: {s['description']}" for s in skills]
    return (
        "Available workflow skills:\n"
        + "\n".join(lines)
        + "\n\nCall get_skill(name) to load the full guidance for a specific skill."
    )


@tool
@_guard
def get_skill(name: str) -> str:
    """按技能名称加载详细的工作流指导（如 'schematic-drawing', 'schematic-review', 'pcb-routing', 'pcb-power-layout', 'drc-fix'）。

    Args:
        name: 技能名称（支持中划线或下划线，如 'schematic-drawing'）。
    """
    norm = _normalize_skill_name(name)
    skill = _find_skill(norm)
    if skill is not None:
        return skill["body"]

    available = [s["name"] for s in _load_all_skills()]
    if available:
        raise ValueError(f"Skill '{name}' not found. Available skills: {', '.join(available)}")
    raise ValueError(f"Skill '{name}' not found. No skills are currently available.")


ADMIN_ONLY_MESSAGE = (
    "技能库是全站共享的，只有管理员可以修改。"
    "如需记录个人的工作流偏好，请改用 edit_file 更新 /memories/AGENTS.md。"
)


def _require_admin(runtime: ToolRuntime[AgentContext]) -> None:
    ctx = _ctx(runtime)
    if not ctx.is_admin:
        raise PermissionError(ADMIN_ONLY_MESSAGE)


@tool
@_guard
def add_skill(
    name: str,
    description: str,
    content: str,
    runtime: ToolRuntime[AgentContext],
    priority: int = 50,
) -> str:
    """创建新的工作流技能（仅管理员；技能对所有用户生效）。

    Args:
        name: 技能标识名（小写字母、数字与中划线，如 'custom-routing'）。
        description: 技能的一句话简要描述。
        content: 技能正文（Markdown 格式的操作流程与经验指导）。
        priority: 显示优先级（默认 50，数值越大越靠前）。
    """
    _require_admin(runtime)
    norm = _normalize_skill_name(name)
    if not _VALID_NAME_RE.match(norm):
        raise ValueError(f"Invalid skill name '{name}'. Names must use letters, digits, and hyphens.")

    if _find_skill(norm) is not None:
        raise ValueError(f"A skill named '{name}' already exists. Use append_to_skill() or delete_skill() first.")

    target_dir = SKILLS_DIR / norm
    target_dir.mkdir(parents=True, exist_ok=True)
    target_file = target_dir / "SKILL.md"
    blob = f'---\nname: {norm}\npriority: {priority}\ndescription: "{description}"\n---\n\n'
    target_file.write_text(blob + content.strip() + "\n", encoding="utf-8")
    return f"Skill '{norm}' created at {target_file.name}"


@tool
@_guard
def append_to_skill(name: str, content: str, runtime: ToolRuntime[AgentContext]) -> str:
    """向现有技能追加补充指南或操作流程（仅管理员；技能对所有用户生效）。

    Args:
        name: 技能标识名。
        content: 要追加的 Markdown 内容。
    """
    _require_admin(runtime)
    skill = _find_skill(name)
    if skill is None:
        available = [s["name"] for s in _load_all_skills()]
        hint = f" Available skills: {', '.join(available)}" if available else ""
        raise ValueError(f"Skill '{name}' not found.{hint}")

    path: Path = skill["path"]
    current = path.read_text(encoding="utf-8")
    new_body = current.rstrip() + "\n\n" + content.strip() + "\n"
    path.write_text(new_body, encoding="utf-8")
    return f"Content appended to skill '{skill['name']}'."


@tool
@_guard
def delete_skill(name: str, runtime: ToolRuntime[AgentContext]) -> str:
    """软删除指定技能（仅管理员；移动至 .deleted/ 目录，对所有用户生效）。

    Args:
        name: 要删除的技能标识名。
    """
    _require_admin(runtime)
    skill = _find_skill(name)
    if skill is None:
        available = [s["name"] for s in _load_all_skills()]
        hint = f" Available skills: {', '.join(available)}" if available else ""
        raise ValueError(f"Skill '{name}' not found.{hint}")

    path: Path = skill["path"]
    DELETED_DIR.mkdir(parents=True, exist_ok=True)
    target_name = path.parent.name if path.name == "SKILL.md" else path.name
    dest = DELETED_DIR / target_name
    if dest.exists():
        counter = 1
        while dest.exists():
            dest = DELETED_DIR / f"{target_name}-{counter}"
            counter += 1

    to_move = path.parent if path.name == "SKILL.md" else path
    shutil.move(str(to_move), str(dest))
    return f"Skill '{skill['name']}' moved to deleted directory: {dest.name}"


NATIVE_TOOLS = [
    submit_change_plan,
    create_project,
    switch_project,
    list_projects,
    get_project_structure,
    get_board_info,
    list_footprints,
    get_footprint,
    get_footprint_bbox,
    get_board_bounding_box,
    list_nets,
    get_ratsnest,
    score_placement,
    get_board_outline,
    find_free_pcb_area,
    get_effective_design_rules,
    suggest_placement_order,
    set_footprint_position,
    move_footprints_by_delta,
    align_footprints,
    distribute_footprints,
    flip_footprint,
    set_footprint_property,
    update_pcb_from_schematic,
    set_board_outline_rect,
    clear_board_outline,
    run_drc_check,
    run_erc_check,
    run_design_review,
    run_eco_check,
    analyze_bom,
    list_circuit_templates,
    preview_circuit_template,
    apply_circuit_template,
    get_schematic_sheet_info,
    list_schematic_symbols,
    get_schematic_symbol,
    extract_schematic_netlist,
    find_component_connections,
    check_reference_conflicts,
    list_labels_in_schematic,
    move_component,
    set_symbol_property,
    add_wire_to_schematic,
    add_label_to_schematic,
    save_file_version,
    list_file_versions,
    restore_file_version,
    list_skills,
    get_skill,
    add_skill,
    append_to_skill,
    delete_skill,
]
