"""Pydantic request / response models."""

from __future__ import annotations

from datetime import datetime, timezone
from typing import Annotated, Any, Literal

from pydantic import AfterValidator, BaseModel, Field, model_validator


def _ensure_utc(value: datetime) -> datetime:
    """SQLite drops tzinfo; mark naive timestamps as UTC so clients parse them correctly."""
    return value.replace(tzinfo=timezone.utc) if value.tzinfo is None else value


UtcDateTime = Annotated[datetime, AfterValidator(_ensure_utc)]


# ---- auth -------------------------------------------------------------------
class RegisterRequest(BaseModel):
    username: str = Field(min_length=2, max_length=64)
    password: str = Field(min_length=6, max_length=128)
    display_name: str | None = Field(default=None, max_length=64)
    email: str | None = None


class LoginRequest(BaseModel):
    username: str
    password: str


class UserOut(BaseModel):
    id: str
    username: str
    display_name: str
    email: str | None
    role: str
    avatar_color: str
    created_at: UtcDateTime
    # 多商户:所属站点;platform_admin 才能改模型 / 系统设置 / 技能库
    tenant: str = "default"
    platform_admin: bool = False
    sso: bool = False


class TokenResponse(BaseModel):
    access_token: str
    token_type: str = "bearer"
    user: UserOut


# ---- projects ---------------------------------------------------------------
class ProjectCreateBlank(BaseModel):
    name: str
    title: str | None = None


class DesignConstraints(BaseModel):
    """Project-wide electrical, manufacturing and placement constraints."""

    placement_grid_mm: float = Field(default=0.1, gt=0, le=10)
    min_clearance_mm: float = Field(default=0.2, ge=0, le=10)
    min_track_width_mm: float = Field(default=0.2, gt=0, le=20)
    signal_track_width_mm: float = Field(default=0.25, gt=0, le=20)
    power_track_width_mm: float = Field(default=0.5, gt=0, le=50)
    via_diameter_mm: float = Field(default=0.6, gt=0, le=20)
    via_drill_mm: float = Field(default=0.3, gt=0, le=10)
    copper_edge_clearance_mm: float = Field(default=0.5, ge=0, le=20)
    max_component_height_mm: float | None = Field(default=None, gt=0, le=100)
    notes: str = Field(default="", max_length=2000)

    @model_validator(mode="after")
    def validate_relationships(self):
        if self.via_drill_mm >= self.via_diameter_mm:
            raise ValueError("过孔钻孔必须小于过孔外径")
        if self.signal_track_width_mm < self.min_track_width_mm:
            raise ValueError("信号线宽不能小于最小线宽")
        if self.power_track_width_mm < self.min_track_width_mm:
            raise ValueError("电源线宽不能小于最小线宽")
        return self


class ProjectOut(BaseModel):
    id: str
    name: str
    rel_dir: str
    pro_file: str | None
    schematic_file: str | None
    pcb_file: str | None
    design_constraints: DesignConstraints = Field(default_factory=DesignConstraints)
    created_at: UtcDateTime
    updated_at: UtcDateTime


class ProjectFileEntry(BaseModel):
    path: str
    size: int
    kind: str  # pcb | schematic | project | other
    modified_at: str | None = None
    generator_version: str | None = None
    format_version: int | None = None
    kicad_major: int | None = None
    compatibility: str | None = None
    compatible: bool | None = None
    compatibility_message: str | None = None
    runtime_version: str | None = None


# ---- conversations ----------------------------------------------------------
class ConversationCreate(BaseModel):
    title: str | None = None
    project_id: str | None = None


class ConversationUpdate(BaseModel):
    title: str | None = None
    project_id: str | None = None


class ConversationOut(BaseModel):
    id: str
    title: str
    preview: str
    project_id: str | None
    message_count: int
    tool_call_count: int
    input_tokens: int
    output_tokens: int
    created_at: UtcDateTime
    updated_at: UtcDateTime
    is_running: bool = False


class MessageOut(BaseModel):
    id: str
    role: Literal["user", "assistant", "tool", "system"]
    content: str
    reasoning: str | None = None  # model's thinking (assistant messages of reasoning models)
    tool_calls: list[dict[str, Any]] = Field(default_factory=list)
    tool_call_id: str | None = None
    name: str | None = None
    status: str | None = None
    diff: dict[str, Any] | list[dict[str, Any]] | None = None
    created_at: datetime | None = None


class ConversationDetail(BaseModel):
    conversation: ConversationOut
    messages: list[MessageOut]
    todos: list[dict[str, Any]] = Field(default_factory=list)
    pending_interrupt: dict[str, Any] | None = None
    context_usage: float = 0.0
    is_running: bool = False
    # Active run (if any). While a run is active `messages` stops at the point
    # the run started; the client replays the run's events on top of it.
    run_id: str | None = None
    run_seq: int | None = None


# ---- chat -------------------------------------------------------------------
SelectionReference = Annotated[str, Field(min_length=1, max_length=64)]


class DesignSelection(BaseModel):
    project_id: str = Field(min_length=1, max_length=32)
    mode: Literal["pcb", "sch"]
    references: list[SelectionReference] = Field(min_length=1, max_length=200)
    bounds: tuple[float, float, float, float] | None = None


class ChatRequest(BaseModel):
    content: str = Field(min_length=1)
    project_id: str | None = None
    # Per-conversation overrides; ``None`` / ``"auto"`` = server default.
    model: str | None = None
    thinking: Literal["default", "off", "fast", "deep"] | None = None
    selection: DesignSelection | None = None


class ResumeDecision(BaseModel):
    type: Literal["approve", "reject", "edit"]
    message: str | None = None
    edited_action: dict[str, Any] | None = None


class ResumeRequest(BaseModel):
    decisions: list[ResumeDecision]
    model: str | None = None
    thinking: Literal["default", "off", "fast", "deep"] | None = None
    selection: DesignSelection | None = None


# ---- tools / skills ---------------------------------------------------------
class ToolOut(BaseModel):
    name: str
    description: str
    category: str
    category_label: str
    kind: str
    source: str  # native | mcp | harness


class ToolCategoryOut(BaseModel):
    key: str
    label: str
    description: str
    icon: str
    count: int
    tools: list[ToolOut]


class SkillOut(BaseModel):
    name: str
    description: str
    path: str
    content: str | None = None


class SnapshotOut(BaseModel):
    version_id: str
    file: str
    created_at: str
    label: str
    size: int


# ---- admin ------------------------------------------------------------------
class AdminUserOut(BaseModel):
    id: str
    username: str
    display_name: str
    email: str | None
    role: str
    avatar_color: str
    is_active: bool
    tenant: str = "default"
    sso: bool = False
    created_at: UtcDateTime
    last_login_at: UtcDateTime | None = None
    project_count: int = 0
    conversation_count: int = 0
    message_count: int = 0
    tool_call_count: int = 0


class AdminUserCreate(BaseModel):
    username: str = Field(min_length=2, max_length=64)
    password: str = Field(min_length=6, max_length=128)
    display_name: str | None = Field(default=None, max_length=64)
    email: str | None = None
    role: Literal["user", "admin"] = "user"


class AdminUserUpdate(BaseModel):
    display_name: str | None = Field(default=None, max_length=64)
    email: str | None = None
    role: Literal["user", "admin"] | None = None
    is_active: bool | None = None


class ResetPasswordRequest(BaseModel):
    password: str = Field(min_length=6, max_length=128)


class SettingsUpdate(BaseModel):
    values: dict[str, Any]


class LlmTestRequest(BaseModel):
    llm_provider: str
    llm_model: str
    llm_api_key: str | None = None  # empty → use the stored key
    llm_base_url: str | None = None
    llm_temperature: float | None = None
    llm_thinking: Literal["default", "off", "fast", "deep"] | None = None
    llm_thinking_budget: int | None = None
    llm_thinking_style: str | None = None


class AdminOverview(BaseModel):
    users_total: int
    users_active: int
    users_admins: int
    users_new_7d: int
    logins_24h: int
    projects_total: int
    conversations_total: int
    messages_total: int
    tool_calls_total: int
    input_tokens_total: int
    output_tokens_total: int
    online_users: int
    active_runs: int
    model: str
    tool_count: int
    mcp_tool_count: int
    mcp_url: str | None
    kicad_cli: bool
    subagents: bool
    started_at: UtcDateTime
    last_reload_at: UtcDateTime | None
    last_error: str | None
    workspace_bytes: int
    version: str
    recent_users: list[AdminUserOut]
