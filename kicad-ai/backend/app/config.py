"""Application settings (environment driven, `.env` aware)."""

from __future__ import annotations

from functools import lru_cache
from pathlib import Path

from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict

BACKEND_DIR = Path(__file__).resolve().parent.parent
PROJECT_ROOT = BACKEND_DIR.parent


class Settings(BaseSettings):
    # Single source of truth: <repo>/.env (copy from .env.example). Real
    # environment variables always take precedence over the file.
    model_config = SettingsConfigDict(
        env_file=PROJECT_ROOT / ".env",
        env_file_encoding="utf-8",
        extra="ignore",
    )

    # ---- server -----------------------------------------------------------
    app_name: str = "KiCad AI Assistant"
    host: str = "0.0.0.0"
    port: int = 8000
    debug: bool = False
    cors_origins: list[str] = Field(
        default_factory=lambda: ["http://localhost:5173", "http://127.0.0.1:5173"]
    )

    # ---- auth -------------------------------------------------------------
    jwt_secret: str = "change-me-in-production"
    jwt_algorithm: str = "HS256"
    jwt_expire_minutes: int = 60 * 24 * 7
    allow_registration: bool = True

    # ---- storage ----------------------------------------------------------
    data_dir: Path = BACKEND_DIR / "data"
    database_url: str = ""  # default: sqlite+aiosqlite:///<data_dir>/app.db
    checkpoint_db: str = ""  # default: <data_dir>/checkpoints.sqlite
    store_db: str = ""  # default: <data_dir>/store.sqlite
    workspace_root: Path = BACKEND_DIR / "data" / "workspaces"
    samples_dir: Path = BACKEND_DIR / "samples"

    # ---- LLM --------------------------------------------------------------
    llm_provider: str = "openai"  # openai | anthropic | custom(openai-compatible) | <any init_chat_model provider>
    llm_model: str = "gpt-5.6"
    llm_api_key: str = ""
    llm_base_url: str = ""
    llm_temperature: float = 0.2
    llm_context_tokens: int = 128_000
    llm_max_tokens: int | None = None
    # Default thinking mode for agent runs (users may override per conversation):
    #   default – send nothing, keep the vendor default (Qwen3.5+ / DeepSeek V4 think by default)
    #   off     – disable thinking (fastest; recommended for tool-heavy agent loops)
    #   fast    – think with a small budget / low effort
    #   deep    – think with the model's default budget / effort
    llm_thinking: str = "off"
    # Reasoning-token budget used by "fast" on models without reasoning_effort levels
    llm_thinking_budget: int = 4096
    # How thinking is switched on the endpoint: auto | bailian | thinking_type | chat_template_kwargs | ollama | none
    llm_thinking_style: str = "auto"
    # mock provider only: artificial latency per model call, for realistic demos
    llm_mock_delay_ms: int = 0

    # ---- agent ------------------------------------------------------------
    # LangGraph super-steps per request. One model→tools round costs ~4 steps in
    # the deep agent graph (model + after_model hooks + tools), so 400 ≈ 100 rounds.
    agent_recursion_limit: int = 400
    # Wall-clock ceiling for one chat turn (0 = unlimited). Progress is
    # checkpointed, so a timed-out run can be continued like a recursion stop.
    agent_run_timeout_seconds: int = 1800
    # Total tokens (all model calls of one turn) after which the run is stopped
    # (0 = unlimited). Cost guard against runaway loops the step limit misses.
    agent_run_token_budget: int = 3_000_000
    # Tool results above this many characters are shrunk (largest list cut,
    # full payload spilled to the user's workspace) before reaching the model.
    tool_result_max_chars: int = 24_000
    enable_subagents: bool = True
    hitl_tools: list[str] = Field(
        default_factory=lambda: [
            "restore_file_version",
            "clear_board_outline",
            "remove_symbol_from_schematic",
            "remove_sheet_symbol",
            "delete_zone",
            "pcb_delete_tracks",
            "pcb_delete_vias",
        ]
    )

    # ---- upstream KiCad-AI-Assistant (kcaa) integration ---------------------
    # direct: import the kcaa package and call its tools in-process (default)
    # mcp:    connect to a separately running kcaa MCP server (kcaa_mcp_url)
    # off:    native tools only
    kcaa_mode: str = "direct"
    kcaa_profile: str = "full"  # full | plugin (tool subset without kicad-cli deps)
    kcaa_mcp_url: str = ""  # e.g. http://127.0.0.1:8765/mcp (mcp mode only)
    kcaa_mcp_transport: str = "http"  # http | sse
    mcp_connect_timeout: float = 10.0
    mcp_startup_retries: int = 6
    mcp_startup_wait: float = 5.0

    # ---- KiCad ------------------------------------------------------------
    kicad_cli: str = "kicad-cli"
    kicad_version: str = "10.0"  # required by kcaa's versioned configuration directory
    kicad_app_path: str = ""  # optional: KiCad install dir for system libraries

    # ---- IOEDU 教学平台集成(多商户 SSO) -------------------------------------
    # 接受 IOEDU 后端签发的 JWT(HS256,声明 sub=用户 id、role、tid=站点编码),首次登录自动建本地账号
    ioedu_jwt_secret: str = ""
    # IOEDU 后端内网地址,如 http://backend:8080;首次 SSO 登录时拉取姓名 / 邮箱
    ioedu_api_url: str = ""
    # 与 IOEDU 的 PLATFORM_TOKEN 相同:调用 IOEDU 平台接口取用户资料,也用于校验 IOEDU 来拉大屏统计
    ioedu_internal_token: str = ""
    # IOEDU 的默认站点编码;只有该站点的管理员算平台管理员,可改模型 / 系统设置 / 技能库
    ioedu_default_tenant: str = "default"
    # 用户登出 / 未登录时跳回 IOEDU 的地址(相对路径即可,同域部署)
    ioedu_login_url: str = "/auth"

    # ---- branding ---------------------------------------------------------
    app_tagline: str = "基于 LangGraph · Deep Agents 的 KiCad 智能助手"
    app_logo: str = ""  # relative file name under <data_dir>/branding (set via admin upload)
    app_footer: str = "🔒 用户工作区隔离，越界路径拒绝  📷 修改前自动快照  🌐 支持主流大模型  ⚡ 智能 EDA 闭环"
    app_copyright: str = ""

    # ------------------------------------------------------------------------
    @property
    def resolved_database_url(self) -> str:
        if self.database_url:
            return self.database_url
        return f"sqlite+aiosqlite:///{(self.data_dir / 'app.db').as_posix()}"

    @property
    def resolved_checkpoint_db(self) -> str:
        return self.checkpoint_db or (self.data_dir / "checkpoints.sqlite").as_posix()

    @property
    def resolved_store_db(self) -> str:
        return self.store_db or (self.data_dir / "store.sqlite").as_posix()

    def ensure_dirs(self) -> None:
        self.data_dir.mkdir(parents=True, exist_ok=True)
        self.workspace_root.mkdir(parents=True, exist_ok=True)


@lru_cache
def get_settings() -> Settings:
    settings = Settings()
    settings.ensure_dirs()
    return settings
