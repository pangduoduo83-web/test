export interface User {
  id: string;
  username: string;
  display_name: string;
  email: string | null;
  role: string;
  avatar_color: string;
  created_at: string;
  /** 所属教学平台站点(多商户) */
  tenant?: string;
  /** 平台管理员才能改模型 / 系统设置 */
  platform_admin?: boolean;
  /** 由教学平台单点登录同步的账号 */
  sso?: boolean;
}

export interface Project {
  id: string;
  name: string;
  rel_dir: string;
  pro_file: string | null;
  schematic_file: string | null;
  pcb_file: string | null;
  design_constraints: DesignConstraints;
  created_at: string;
  updated_at: string;
}

export interface DesignConstraints {
  placement_grid_mm: number;
  min_clearance_mm: number;
  min_track_width_mm: number;
  signal_track_width_mm: number;
  power_track_width_mm: number;
  via_diameter_mm: number;
  via_drill_mm: number;
  copper_edge_clearance_mm: number;
  max_component_height_mm: number | null;
  notes: string;
}

export interface ReviewIssue {
  id: string;
  source: "drc" | "erc" | "placement";
  type: string;
  severity: "error" | "warning" | "info" | string;
  description: string;
  items: { description: string; pos?: { x: number; y: number } | null }[];
  sheet?: string | null;
  auto_fixable: boolean;
}

export interface ReviewCheck {
  engine: string;
  passed: boolean;
  error_count: number;
  warning_count: number;
  unconnected_count?: number;
  violations: ReviewIssue[];
  note?: string;
}

export interface PlacementQuality {
  score: number;
  grade: "A" | "B" | "C" | "D";
  breakdown: {
    no_overlap: number;
    inside_outline: number;
    connection_compactness: number;
    grid_alignment: number;
  };
  overlapping_pairs: string[][];
  outside_outline: string[];
  outline_present: boolean;
  estimated_ratsnest_length_mm: number;
  average_connection_span_mm: number;
  grid_mm: number;
  misaligned_to_grid: string[];
}

export interface DesignReview {
  success: boolean;
  status: "passed" | "warnings" | "errors" | "no_design_files";
  error?: string;
  quality_score?: number;
  grade?: "A" | "B" | "C" | "D";
  generated_at?: string;
  summary?: {
    error_count: number;
    warning_count: number;
    issue_count: number;
    auto_fixable_count: number;
  };
  placement?: PlacementQuality | null;
  predicted_placement_score?: number | null;
  drc?: ReviewCheck | null;
  erc?: ReviewCheck | null;
  issues?: ReviewIssue[];
  suggested_actions?: (PlannedChangeAction & { issue_types?: string[] })[];
  verification?: PlannedChangeAction[];
}

export interface EcoReport {
  success: boolean;
  status: "synchronized" | "drift";
  summary: {
    schematic_components: number;
    pcb_footprints: number;
    drift_count: number;
    auto_sync_count: number;
    manual_count: number;
  };
  missing_on_pcb: { reference: string; value: string; footprint: string; syncable: boolean }[];
  extra_on_pcb: { reference: string; value: string; footprint: string }[];
  value_mismatches: { reference: string; schematic: string; pcb: string }[];
  footprint_mismatches: { reference: string; schematic: string; pcb: string }[];
  net_mismatches: { net: string; missing_on_pcb: string[]; extra_on_pcb: string[] }[];
  suggested_actions: PlannedChangeAction[];
  verification: PlannedChangeAction[];
  notes: string[];
}

export interface BomLine {
  value: string;
  footprint: string;
  quantity: number;
  references: string[];
  prefix: string;
}

export interface BomIssue {
  type: string;
  severity: "error" | "warning" | "info" | string;
  reference: string | null;
  description: string;
}

export interface BomReport {
  success: boolean;
  error?: string;
  status?: "ready" | "warnings" | "errors";
  library_index_available?: boolean;
  summary?: { line_count: number; component_count: number; error_count: number; warning_count: number; issue_count: number };
  lines?: BomLine[];
  issues?: BomIssue[];
  assembly?: Record<string, number | boolean | string>;
  by_prefix?: Record<string, number>;
}

export interface TemplateParam {
  key: string;
  label: string;
  type: "number" | "select" | "text";
  default: string | number;
  min?: number;
  max?: number;
  options?: string[];
}

export interface CircuitTemplate {
  id: string;
  name: string;
  description: string;
  category: string;
  params: TemplateParam[];
}

export interface TemplateExpansion {
  template: string;
  name: string;
  params: Record<string, string | number>;
  anchor: { x: number; y: number };
  parts: { id: string; library: string; symbol: string; value: string; footprint: string; x: number; y: number; rotation: number }[];
  wires: { from: string; to: string }[];
  labels: { pin: string; text: string }[];
  notes: string[];
  symbols: string[];
  footprints: string[];
  library: { checked: boolean; missing_symbols: string[]; missing_footprints: string[] };
}

export type DesignMode = "pcb" | "sch";

export interface SelectionElement {
  id: string;
  reference: string;
  label: string;
  value: string;
  kind: "footprint" | "symbol";
  position: [number, number];
  bbox: [number, number, number, number];
  world_bbox: [number, number, number, number];
}

export interface SelectionMap {
  project_id: string;
  project_name: string;
  mode: DesignMode;
  canvas: { width: number; height: number };
  world_bounds: [number, number, number, number];
  elements: SelectionElement[];
}

export interface DesignSelection {
  project_id: string;
  mode: DesignMode;
  references: string[];
  bounds?: [number, number, number, number] | null;
}

export interface Conversation {
  id: string;
  title: string;
  preview: string;
  project_id: string | null;
  message_count: number;
  tool_call_count: number;
  input_tokens: number;
  output_tokens: number;
  created_at: string;
  updated_at: string;
  /** An agent run is executing for this conversation right now (server-side). */
  is_running?: boolean;
}

/** One line of a unified diff hunk: t = " " | "+" | "-", o/n = old/new line numbers. */
export interface DiffLine {
  t: " " | "+" | "-";
  s: string;
  o?: number;
  n?: number;
}

export interface FileDiff {
  relpath: string;
  tool?: string;
  additions: number;
  deletions: number;
  hunks: { header: string; lines: DiffLine[] }[];
  truncated: boolean;
  changed: boolean;
  /** Both sides were re-serialised in canonical form so only semantic changes show. */
  normalized?: boolean;
  size?: number;
  note?: string;
}

export type ToolKind = "query" | "file_mutation" | "versioning" | "ui_refresh" | "ipc_action" | "indexing" | "harness";

/** Live/persisted execution state of one action of an approved change plan. */
export interface PlanActionProgress {
  index: number;
  tool: string;
  summary: string;
  status: "pending" | "running" | "done" | "error";
  error?: string | null;
  diff?: FileDiff | null;
  result?: unknown;
}

export interface ToolCall {
  id: string;
  name: string;
  args?: Record<string, unknown>;
  /** Raw JSON text of the arguments while the model is still streaming them. */
  argsText?: string;
  kind?: ToolKind;
  category?: string;
  status: "pending" | "running" | "done" | "error";
  result?: string;
  data?: unknown;
  diff?: FileDiff;
  source?: string;
  /** For submit_change_plan: per-action progress while the server executes the approved plan. */
  plan?: PlanActionProgress[];
}

export interface Message {
  id: string;
  role: "user" | "assistant" | "tool" | "system";
  content: string;
  /** Model's reasoning / thinking text (when the selected model exposes it). */
  reasoning?: string | null;
  tool_calls?: ToolCall[];
  tool_call_id?: string | null;
  name?: string | null;
  status?: string | null;
  diff?: FileDiff | FileDiff[] | null;
  created_at?: string | null;
  streaming?: boolean;
  source?: string;
}

export interface RunError {
  code: "recursion_limit" | "run_failed" | string;
  message: string;
  recoverable: boolean;
  limit?: number;
}

export interface Todo {
  content: string;
  status: "pending" | "in_progress" | "completed";
}

export interface ActionRequest {
  name: string;
  args: Record<string, unknown>;
  description?: string;
}

export interface PlannedChangeAction {
  tool: string;
  args: Record<string, unknown>;
  summary: string;
  reason?: string;
}

export interface ChangePlan {
  title: string;
  summary: string;
  scope?: string[];
  actions: PlannedChangeAction[];
  verification?: PlannedChangeAction[];
}

export interface InterruptValue {
  action_requests?: ActionRequest[];
  review_configs?: { action_name: string; allowed_decisions: string[] }[];
}

export interface PendingInterrupt {
  id: string | null;
  value: InterruptValue | null;
}

export interface ConversationDetail {
  conversation: Conversation;
  messages: Message[];
  todos: Todo[];
  pending_interrupt: PendingInterrupt | null;
  context_usage: number;
  is_running?: boolean;
  /** Active run, if any. `messages` then stops where the run started; replay its events on top. */
  run_id?: string | null;
  run_seq?: number | null;
}

export interface ToolInfo {
  name: string;
  description: string;
  category: string;
  category_label: string;
  kind: string;
  source: string;
}

export interface ToolCategory {
  key: string;
  label: string;
  description: string;
  icon: string;
  count: number;
  tools: ToolInfo[];
}

export interface Skill {
  name: string;
  description: string;
  path: string;
  content?: string | null;
}

export interface Snapshot {
  version_id: string;
  file: string;
  created_at: string;
  label: string;
  size: number;
}

export interface PresenceUser {
  id: string;
  display_name: string;
  username: string;
  avatar_color: string;
  status: "idle" | "busy";
  since: string;
}

export interface SystemInfo {
  app_name: string;
  version: string;
  model: string;
  provider: string;
  context_tokens: number;
  thinking: ThinkingMode;
  thinking_modes: ThinkingMode[];
  tool_count: number;
  mcp_tool_count: number;
  mcp_url: string | null;
  kicad_cli: boolean;
  kicad_cli_version: string | null;
  subagents: boolean;
  active_runs: number;
  online: number;
  model_presets?: ModelOption[];
}

export type ThinkingMode = "default" | "off" | "fast" | "deep";
export type ThinkingSelection = "auto" | ThinkingMode;

export interface ModelOption {
  id: string;
  name: string;
  provider: string;
  default?: boolean;
  thinking?: "hybrid" | "always" | "none" | "native";
  thinking_default?: boolean;
  efforts?: string[];
  preserve_thinking?: boolean;
  context_tokens?: number | null;
}

export interface PublicInfo {
  app_name: string;
  app_tagline: string;
  logo_url: string | null;
  app_footer?: string;
  app_copyright?: string;
  version: string;
  allow_registration: boolean;
  agent_ready: boolean;
  tool_count: number;
  upstream_tool_count: number;
  upstream_source: string | null;
  online: number;
  demo_mode: boolean;
}

// ---- admin ---------------------------------------------------------------------
export interface AdminUser {
  id: string;
  username: string;
  display_name: string;
  email: string | null;
  role: "user" | "admin";
  avatar_color: string;
  is_active: boolean;
  created_at: string;
  last_login_at: string | null;
  project_count: number;
  conversation_count: number;
  message_count: number;
  tool_call_count: number;
}

export interface AdminOverview {
  users_total: number;
  users_active: number;
  users_admins: number;
  users_new_7d: number;
  logins_24h: number;
  projects_total: number;
  conversations_total: number;
  messages_total: number;
  tool_calls_total: number;
  input_tokens_total: number;
  output_tokens_total: number;
  online_users: number;
  active_runs: number;
  model: string;
  tool_count: number;
  mcp_tool_count: number;
  mcp_url: string | null;
  kicad_cli: boolean;
  subagents: boolean;
  started_at: string;
  last_reload_at: string | null;
  last_error: string | null;
  workspace_bytes: number;
  version: string;
  recent_users: AdminUser[];
}

export interface SettingsField {
  key: string;
  kind: "str" | "int" | "float" | "bool";
  secret: boolean;
  group: "llm" | "agent" | "system" | "branding";
  reload_agent: boolean;
}

export interface ModelPreset {
  name: string;
  provider: string;
  model: string;
  base_url: string;
  thinking?: "hybrid" | "always" | "none" | "native";
  thinking_default?: boolean;
  efforts?: string[];
  preserve_thinking?: boolean;
  context_tokens?: number;
}

export interface RuntimeSettings {
  values: Record<string, unknown> & { llm_api_key?: string; llm_api_key_set?: boolean };
  fields: SettingsField[];
  presets: ModelPreset[];
  thinking_modes?: ThinkingMode[];
  thinking_styles?: string[];
  runtime: { model: string; tool_count: number; mcp_tool_count: number; last_reload_at: string | null; last_error: string | null };
}

export interface SettingsSaveResult {
  saved: string[];
  changed: string[];
  agent_reloaded: boolean;
  error: string | null;
  values: RuntimeSettings["values"];
  runtime: { model: string; tool_count: number; mcp_tool_count: number };
}

export interface LlmTestResult {
  ok: boolean;
  latency_ms: number;
  reply?: string;
  error?: string;
  model: string;
  thinking?: ThinkingMode;
  reasoning_chars?: number;
}

// ---- SSE stream events -------------------------------------------------------
export interface ToolCallView {
  id: string;
  name: string;
  args: Record<string, unknown>;
  kind?: ToolKind;
  category?: string;
}

/** Every event carries the run's sequence number so a dropped connection can resume with `?after=seq`. */
export type StreamEvent = { seq?: number; run_id?: string } & (
  | { type: "run_start"; run_id: string }
  | { type: "user_message"; content: string }
  | { type: "run_resync"; conversation_id: string }
  | { type: "message_start"; id: string; source: string }
  | { type: "reasoning_delta"; id: string; source: string; delta: string }
  | { type: "text_delta"; id: string; source: string; delta: string }
  | { type: "message_end"; id: string; stream_id: string | null; source: string; content: string; reasoning?: string; tool_calls: ToolCallView[] }
  | { type: "tool_call_start"; id: string; name: string; source: string; message_id?: string; kind?: ToolKind; category?: string }
  | { type: "tool_call_delta"; id: string; source: string; delta: string }
  | { type: "tool_call_args"; id: string; name: string; args: Record<string, unknown>; source: string; kind?: ToolKind; category?: string }
  | { type: "tool_result"; id: string; name: string; ok: boolean; content: string; data: unknown; diff?: FileDiff; source: string }
  | { type: "todos"; todos: Todo[] }
  | { type: "custom"; event: string; source: string; [k: string]: unknown }
  | { type: "interrupt"; interrupts: PendingInterrupt[] }
  | { type: "usage"; input_tokens?: number; output_tokens?: number; total_tokens?: number }
  | { type: "error"; message: string; code?: string; recoverable?: boolean; limit?: number }
  | { type: "run_end"; run_id: string; interrupted: boolean; cancelled?: boolean; usage: { input_tokens: number; output_tokens: number } }
);
