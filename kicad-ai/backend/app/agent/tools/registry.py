"""Explicit tool policy registry (ported from upstream ``tool_registry.py``).

Policies drive framework behaviour (auto snapshot before mutations, workspace
path validation, UI categorisation) independent of where a tool comes from
(native implementation or the upstream ``kcaa`` MCP server).
"""

from __future__ import annotations

from dataclasses import dataclass
from typing import Literal

ToolKind = Literal["query", "file_mutation", "versioning", "ui_refresh", "ipc_action", "indexing", "harness"]

PATH_ARG_NAMES = ("pcb_path", "schematic_path", "project_path", "file_path", "path", "paths", "project_dir", "sheet_file")


@dataclass(frozen=True)
class ToolPolicy:
    kind: ToolKind
    category: str
    path_arg: str | None = None
    auto_snapshot: bool = False
    confirm: bool = False  # destructive → human-in-the-loop by default


TOOL_CATEGORIES: dict[str, dict[str, str]] = {
    "project": {"label": "工程管理", "description": "列出、导入、查看工程结构", "icon": "folder"},
    "pcb_query": {"label": "PCB 查询", "description": "查询元件、网络、走线等", "icon": "search"},
    "pcb_edit": {"label": "PCB 编辑", "description": "移动、旋转、删除元件等", "icon": "pen"},
    "pcb_place": {"label": "PCB 布局", "description": "对齐、分布、布局优化等", "icon": "layout"},
    "drc": {"label": "DRC 检查", "description": "设计规则检查和验证", "icon": "shield"},
    "sch_query": {"label": "原理图查询", "description": "符号、网表、连接关系", "icon": "cpu"},
    "sch_edit": {"label": "原理图编辑", "description": "移动符号、属性、导线、标签", "icon": "pencil"},
    "library": {"label": "元件库", "description": "符号与封装库检索", "icon": "library"},
    "version": {"label": "版本快照", "description": "保存、列出、恢复文件快照", "icon": "history"},
    "skill": {"label": "技能", "description": "按需加载的工作流指南", "icon": "sparkles"},
    "ipc": {"label": "KiCad IPC", "description": "与运行中的 KiCad 交互", "icon": "plug"},
    "harness": {"label": "内置能力", "description": "虚拟文件系统、任务规划、子代理", "icon": "box"},
}


def _q(cat: str, path_arg: str | None = None) -> ToolPolicy:
    return ToolPolicy(kind="query", category=cat, path_arg=path_arg)


def _m(cat: str, path_arg: str, confirm: bool = False) -> ToolPolicy:
    return ToolPolicy(kind="file_mutation", category=cat, path_arg=path_arg, auto_snapshot=True, confirm=confirm)


TOOL_POLICIES: dict[str, ToolPolicy] = {
    # ---- approval ----------------------------------------------------------
    "submit_change_plan": ToolPolicy(kind="harness", category="harness", confirm=True),
    # ---- project -----------------------------------------------------------
    "create_project": _q("project"),
    "switch_project": _q("project"),
    "list_projects": _q("project"),
    "get_project_structure": _q("project", "project_dir"),
    "open_project": _q("project"),
    # ---- PCB query ---------------------------------------------------------
    "get_board_info": _q("pcb_query", "pcb_path"),
    "list_footprints": _q("pcb_query", "pcb_path"),
    "get_footprint": _q("pcb_query", "pcb_path"),
    "get_footprint_bbox": _q("pcb_query", "pcb_path"),
    "get_board_bounding_box": _q("pcb_query", "pcb_path"),
    "list_nets": _q("pcb_query", "pcb_path"),
    "get_ratsnest": _q("pcb_query", "pcb_path"),
    "score_placement": _q("pcb_query", "pcb_path"),
    "suggest_placement_order": _q("pcb_query", "pcb_path"),
    "get_board_outline": _q("pcb_query", "pcb_path"),
    "list_tracks": _q("pcb_query", "pcb_path"),
    "list_vias": _q("pcb_query", "pcb_path"),
    "list_zones": _q("pcb_query", "pcb_path"),
    "list_footprint_groups": _q("pcb_query", "pcb_path"),
    "get_footprint_group": _q("pcb_query", "pcb_path"),
    "score_footprint_group": _q("pcb_query", "pcb_path"),
    # ---- PCB edit ----------------------------------------------------------
    "set_footprint_property": _m("pcb_edit", "pcb_path"),
    "flip_footprint": _m("pcb_edit", "pcb_path"),
    "clear_board_outline": _m("pcb_edit", "pcb_path", confirm=True),
    "add_board_outline_segment": _m("pcb_edit", "pcb_path"),
    "add_board_outline_arc": _m("pcb_edit", "pcb_path"),
    "set_board_outline_rect": _m("pcb_edit", "pcb_path"),
    "add_zone": _m("pcb_edit", "pcb_path"),
    "delete_zone": _m("pcb_edit", "pcb_path", confirm=True),
    "pcb_route_pad_to_pad": _m("pcb_edit", "pcb_path"),
    "pcb_add_vias": _m("pcb_edit", "pcb_path"),
    "pcb_delete_tracks": _m("pcb_edit", "pcb_path", confirm=True),
    "pcb_delete_vias": _m("pcb_edit", "pcb_path", confirm=True),
    # ---- PCB placement -----------------------------------------------------
    "set_footprint_position": _m("pcb_place", "pcb_path"),
    "move_footprints_by_delta": _m("pcb_place", "pcb_path"),
    "align_footprints": _m("pcb_place", "pcb_path"),
    "distribute_footprints": _m("pcb_place", "pcb_path"),
    "find_free_pcb_area": _q("pcb_place", "pcb_path"),
    "assign_footprints_to_group": _m("pcb_place", "pcb_path"),
    "place_footprint_group": _m("pcb_place", "pcb_path"),
    "move_footprint_group": _m("pcb_place", "pcb_path"),
    "rotate_footprint_group": _m("pcb_place", "pcb_path"),
    # ---- DRC ---------------------------------------------------------------
    "run_drc_check": _q("drc", "pcb_path"),
    "run_erc_check": _q("drc", "schematic_path"),
    "run_design_review": _q("drc"),
    "run_eco_check": _q("drc"),
    "get_effective_design_rules": _q("drc", "pcb_path"),
    "set_design_rules": _m("drc", "project_path"),
    "set_net_class_rules": _m("drc", "project_path"),
    "assign_nets_to_class": _m("drc", "project_path"),
    "remove_nets_from_class": _m("drc", "project_path"),
    "delete_net_class": _m("drc", "project_path", confirm=True),
    "add_custom_rule": _m("drc", "project_path"),
    "del_custom_rule": _m("drc", "project_path"),
    "analyze_bom": _q("drc", "pcb_path"),
    # ---- circuit templates -------------------------------------------------
    "list_circuit_templates": _q("sch_query"),
    "preview_circuit_template": _q("sch_query"),
    "apply_circuit_template": _m("sch_edit", "schematic_path"),
    "export_bom_csv": _q("drc", "pcb_path"),
    # ---- schematic query ---------------------------------------------------
    "get_schematic_sheet_info": _q("sch_query", "schematic_path"),
    "list_schematic_symbols": _q("sch_query", "schematic_path"),
    "get_schematic_symbol": _q("sch_query", "schematic_path"),
    "extract_schematic_netlist": _q("sch_query", "schematic_path"),
    "extract_project_netlist": _q("sch_query", "project_path"),
    "find_component_connections": _q("sch_query", "schematic_path"),
    "check_reference_conflicts": _q("sch_query", "schematic_path"),
    "list_labels_in_schematic": _q("sch_query", "schematic_path"),
    "list_symbol_properties": _q("sch_query", "schematic_path"),
    "find_free_area": _q("sch_query", "schematic_path"),
    "list_sheet_symbols": _q("sch_query", "schematic_path"),
    "get_sheet_hierarchy": _q("sch_query", "schematic_path"),
    "identify_circuit_patterns": _q("sch_query", "schematic_path"),
    "analyze_project_circuit_patterns": _q("sch_query", "project_path"),
    "validate_project": _q("sch_query", "project_path"),
    "list_symbol_groups": _q("sch_query", "schematic_path"),
    "get_symbol_group": _q("sch_query", "schematic_path"),
    "score_symbol_group": _q("sch_query", "schematic_path"),
    "assign_symbols_to_group": _m("sch_edit", "schematic_path"),
    "place_symbol_group": _m("sch_edit", "schematic_path"),
    "move_symbol_group": _m("sch_edit", "schematic_path"),
    "rotate_symbol_group": _m("sch_edit", "schematic_path"),
    # ---- schematic edit ----------------------------------------------------
    "add_symbol_to_schematic": _m("sch_edit", "schematic_path"),
    "place_symbol_relative": _m("sch_edit", "schematic_path"),
    "remove_symbol_from_schematic": _m("sch_edit", "schematic_path", confirm=True),
    "move_component": _m("sch_edit", "schematic_path"),
    "set_symbol_property": _m("sch_edit", "schematic_path"),
    "rename_symbol": _m("sch_edit", "schematic_path"),
    "delete_symbol_property": _m("sch_edit", "schematic_path"),
    "connect_points_with_wire": _m("sch_edit", "schematic_path"),
    "add_wire_to_schematic": _m("sch_edit", "schematic_path"),
    "connect_pins_with_wire": _m("sch_edit", "schematic_path"),
    "delete_wire_from_schematic": _m("sch_edit", "schematic_path"),
    "add_label_to_schematic": _m("sch_edit", "schematic_path"),
    "delete_label_from_schematic": _m("sch_edit", "schematic_path"),
    "add_sheet_symbol": _m("sch_edit", "schematic_path"),
    "remove_sheet_symbol": _m("sch_edit", "schematic_path", confirm=True),
    "update_sheet_symbol": _m("sch_edit", "schematic_path"),
    "add_sheet_pin": _m("sch_edit", "schematic_path"),
    "remove_sheet_pin": _m("sch_edit", "schematic_path"),
    # ---- libraries ---------------------------------------------------------
    "sync_symbol_index": ToolPolicy(kind="indexing", category="library"),
    "get_symbol_sync_status": _q("library"),
    "get_symbol_index_stats": _q("library"),
    "list_symbol_libraries": _q("library"),
    "search_symbols": _q("library"),
    "get_symbol": _q("library"),
    "get_library_symbols": _q("library"),
    "get_symbol_pins": _q("library"),
    "sync_footprint_index": ToolPolicy(kind="indexing", category="library"),
    "get_footprint_sync_status": _q("library"),
    "list_footprint_libraries": _q("library"),
    "search_footprints": _q("library"),
    "get_footprint_details": _q("library"),
    # ---- versioning --------------------------------------------------------
    "save_file_version": ToolPolicy(kind="versioning", category="version", path_arg="file_path"),
    "list_file_versions": ToolPolicy(kind="versioning", category="version", path_arg="file_path"),
    "restore_file_version": ToolPolicy(kind="versioning", category="version", path_arg="file_path", confirm=True),
    "generate_pcb_thumbnail": _q("version", "pcb_path"),
    "generate_project_thumbnail": _q("version", "project_path"),
    # ---- skills ------------------------------------------------------------
    # Skills are server-wide and loaded into every user's system prompt, so
    # authoring them is admin-only (see ADMIN_ONLY_TOOLS) and confirmed by hand.
    "list_skills": _q("skill"),
    "get_skill": _q("skill"),
    "add_skill": ToolPolicy(kind="query", category="skill", confirm=True),
    "append_to_skill": ToolPolicy(kind="query", category="skill", confirm=True),
    "delete_skill": ToolPolicy(kind="query", category="skill", confirm=True),
    # ---- IPC (upstream MCP, needs a running KiCad) -------------------------
    "check_kicad_ipc_connection": _q("ipc"),
    "save_document": ToolPolicy(kind="ipc_action", category="ipc", path_arg="file_path"),
    "refill_zones": ToolPolicy(kind="ipc_action", category="ipc"),
    "update_pcb_from_schematic": _m("pcb_edit", "pcb_path"),
    "reload_kicad": ToolPolicy(kind="ui_refresh", category="ipc", path_arg="paths"),
}

HARNESS_TOOLS = {"ls", "read_file", "write_file", "edit_file", "delete", "glob", "grep", "task", "write_todos", "execute"}

# Tools that mutate state shared by every tenant. They are hidden from
# non-admin model requests and refuse to run for non-admin contexts.
ADMIN_ONLY_TOOLS = {"add_skill", "append_to_skill", "delete_skill"}


def get_policy(tool_name: str) -> ToolPolicy:
    if tool_name in TOOL_POLICIES:
        return TOOL_POLICIES[tool_name]
    if tool_name in HARNESS_TOOLS:
        return ToolPolicy(kind="harness", category="harness")
    # Unknown tools (e.g. new upstream additions) are treated as queries but
    # still get path validation through PATH_ARG_NAMES.
    return ToolPolicy(kind="query", category="pcb_query")
