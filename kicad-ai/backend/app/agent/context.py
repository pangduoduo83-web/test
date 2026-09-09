"""Per-run runtime context passed to the agent (``context_schema``).

Everything user- or project-specific flows through here so the compiled graph
stays a process-wide singleton that can serve many users concurrently.
"""

from __future__ import annotations

import json
from dataclasses import dataclass, field
from pathlib import Path


@dataclass
class AgentContext:
    user_id: str
    username: str = ""
    display_name: str = ""
    role: str = "user"  # "admin" unlocks tools that change shared server state (skills)
    conversation_id: str = ""
    # Active project (may be empty when the user has not selected one)
    project_id: str | None = None
    project_name: str | None = None
    project_dir: str | None = None  # relative to the user's workspace
    schematic_path: str | None = None  # absolute path
    pcb_path: str | None = None  # absolute path
    pro_path: str | None = None
    workspace_root: str | None = None  # absolute path to the user's workspace
    selection: dict | None = None  # server-validated PCB footprints / schematic symbols
    design_constraints: dict = field(default_factory=dict)
    extra: dict = field(default_factory=dict)

    def context_block(self) -> str:
        lines = ["# 当前上下文 (由系统注入，每次请求可能不同)"]
        lines.append(f"- user_id: {self.user_id}")
        if self.display_name or self.username:
            lines.append(f"- 用户: {self.display_name or self.username}")
        if self.workspace_root:
            lines.append(f"- workspace_root: {self.workspace_root}")
        if self.project_name:
            lines.append(f"- active_project: {self.project_name}")
        if self.project_dir:
            lines.append(f"- project_dir: {self.project_dir}")
        if self.pro_path:
            lines.append(f"- active_project_file: {self.pro_path}")
        if self.schematic_path:
            lines.append(f"- active_schematic: {self.schematic_path}")
        if self.pcb_path:
            lines.append(f"- active_pcb: {self.pcb_path}")
        if self.selection:
            lines.append(
                "- current_design_selection: "
                + json.dumps(self.selection, ensure_ascii=False, separators=(",", ":"))
            )
            lines.append("- 用户要求涉及「这些/这里/选中对象」时，仅指 current_design_selection 中的对象。")
        if self.design_constraints:
            lines.append(
                "- project_design_constraints: "
                + json.dumps(self.design_constraints, ensure_ascii=False, separators=(",", ":"))
            )
            lines.append("- 所有布局、布线、过孔及制造参数不得低于 project_design_constraints。")
        if not (self.pcb_path or self.schematic_path):
            lines.append("- (未选择工程：请先让用户在右侧「当前工程」中选择或导入 KiCad 工程，"
                         "或调用 list_projects 查看可用工程)")
        return "\n".join(lines)

    @property
    def is_admin(self) -> bool:
        return self.role == "admin"

    def resolve_pcb(self, arg: str | None) -> str | None:
        return arg or self.pcb_path

    def resolve_sch(self, arg: str | None) -> str | None:
        return arg or self.schematic_path

    @property
    def workspace(self) -> Path | None:
        return Path(self.workspace_root) if self.workspace_root else None
