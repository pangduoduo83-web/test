import type {
  AdminOverview,
  AdminUser,
  Conversation,
  ConversationDetail,
  DesignConstraints,
  DesignMode,
  BomReport,
  CircuitTemplate,
  DesignReview,
  EcoReport,
  LlmTestResult,
  Project,
  PublicInfo,
  RuntimeSettings,
  SettingsSaveResult,
  SelectionMap,
  Skill,
  Snapshot,
  StreamEvent,
  SystemInfo,
  TemplateExpansion,
  ToolCategory,
  User,
  ThinkingMode,
} from "./types";

const TOKEN_KEY = "kicad-ai.token";
/** 教学平台(IOEDU)的登录态,同域部署时直接复用 —— 学生 / 老师不用再登一次 */
const IOEDU_TOKEN_KEY = "ioedu_token";

/** 子路径前缀(如 /kicad),所有接口、SSE、WebSocket、静态资源都要带上 */
export const API_BASE = (import.meta.env.BASE_URL || "/").replace(/\/$/, "");
export const IOEDU_LOGIN_URL = "/auth";
export const IOEDU_HOME_URL = "/app/dashboard";

export function withBase(path: string): string {
  if (!path.startsWith("/") || path.startsWith(API_BASE + "/")) return path;
  return API_BASE + path;
}

export function getIoeduToken(): string | null {
  return localStorage.getItem(IOEDU_TOKEN_KEY) || sessionStorage.getItem(IOEDU_TOKEN_KEY);
}
export function getToken(): string | null {
  return getIoeduToken() || localStorage.getItem(TOKEN_KEY);
}
export function setToken(token: string | null) {
  if (token) localStorage.setItem(TOKEN_KEY, token);
  else localStorage.removeItem(TOKEN_KEY);
}
/** 是否正在用教学平台的登录态 */
export function isSsoSession(): boolean {
  return !!getIoeduToken();
}
/** 回教学平台登录页(带回跳地址) */
export function redirectToIoeduLogin() {
  const back = encodeURIComponent(location.pathname + location.search);
  location.href = `${IOEDU_LOGIN_URL}?redirect=${back}`;
}

export class ApiError extends Error {
  status: number;
  constructor(status: number, message: string) {
    super(message);
    this.status = status;
  }
}

export async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers);
  const token = getToken();
  if (token) headers.set("Authorization", `Bearer ${token}`);
  if (init.body && !(init.body instanceof FormData) && !headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json");
  }
  const res = await fetch(withBase(path), { ...init, headers });
  if (res.status === 204) return undefined as T;
  const text = await res.text();
  let data: unknown = null;
  try {
    data = text ? JSON.parse(text) : null;
  } catch {
    data = text;
  }
  if (!res.ok) {
    let detail = "";
    if (data && typeof data === "object") {
      if ("detail" in data) {
        const d = (data as { detail: unknown }).detail;
        if (typeof d === "string") {
          detail = d;
        } else if (Array.isArray(d)) {
          detail = d
            .map((item) => (typeof item === "object" && item && "msg" in item ? String((item as { msg: unknown }).msg) : JSON.stringify(item)))
            .join("; ");
        } else if (d !== null && d !== undefined) {
          detail = typeof d === "object" ? JSON.stringify(d) : String(d);
        }
      } else if ("message" in data) {
        detail = String((data as { message: unknown }).message);
      }
    }
    if (res.status === 401) {
      setToken(null);
      // 教学平台登录态过期:回平台重新登录,而不是停在本应用的登录页
      if (isSsoSession()) {
        localStorage.removeItem(IOEDU_TOKEN_KEY);
        sessionStorage.removeItem(IOEDU_TOKEN_KEY);
        redirectToIoeduLogin();
      }
    }
    throw new ApiError(res.status, detail || res.statusText || `HTTP ${res.status}`);
  }
  return data as T;
}

export const api = {
  // auth
  login: (username: string, password: string) =>
    request<{ access_token: string; user: User }>("/api/auth/login", {
      method: "POST",
      body: JSON.stringify({ username, password }),
    }),
  register: (username: string, password: string, display_name?: string) =>
    request<{ access_token: string; user: User }>("/api/auth/register", {
      method: "POST",
      body: JSON.stringify({ username, password, display_name }),
    }),
  me: () => request<User>("/api/auth/me"),

  // system
  systemInfo: () => request<SystemInfo>("/api/system/info"),
  publicInfo: () => request<PublicInfo>("/api/system/public"),

  // admin
  admin: {
    overview: () => request<AdminOverview>("/api/admin/overview"),
    users: (q = "") => request<AdminUser[]>(`/api/admin/users${q ? `?q=${encodeURIComponent(q)}` : ""}`),
    createUser: (body: { username: string; password: string; display_name?: string; email?: string; role: "user" | "admin" }) =>
      request<AdminUser>("/api/admin/users", { method: "POST", body: JSON.stringify(body) }),
    updateUser: (id: string, patch: Partial<Pick<AdminUser, "display_name" | "email" | "role" | "is_active">>) =>
      request<AdminUser>(`/api/admin/users/${id}`, { method: "PATCH", body: JSON.stringify(patch) }),
    resetPassword: (id: string, password: string) =>
      request<void>(`/api/admin/users/${id}/reset-password`, { method: "POST", body: JSON.stringify({ password }) }),
    deleteUser: (id: string) => request<void>(`/api/admin/users/${id}`, { method: "DELETE" }),
    settings: () => request<RuntimeSettings>("/api/admin/settings"),
    saveSettings: (values: Record<string, unknown>) =>
      request<SettingsSaveResult>("/api/admin/settings", { method: "PUT", body: JSON.stringify({ values }) }),
    testLlm: (body: {
      llm_provider: string;
      llm_model: string;
      llm_api_key?: string;
      llm_base_url?: string;
      llm_temperature?: number;
      llm_thinking?: ThinkingMode;
      llm_thinking_budget?: number;
      llm_thinking_style?: string;
    }) =>
      request<LlmTestResult>("/api/admin/settings/test-llm", { method: "POST", body: JSON.stringify(body) }),
    uploadLogo: (file: File) => {
      const fd = new FormData();
      fd.append("file", file);
      return request<{ app_name: string; app_tagline: string; logo_url: string | null }>("/api/admin/branding/logo", { method: "POST", body: fd });
    },
    deleteLogo: () => request<void>("/api/admin/branding/logo", { method: "DELETE" }),
  },

  // projects
  projects: () => request<Project[]>("/api/projects"),
  createBlankProject: (name: string, title?: string) =>
    request<Project>("/api/projects/blank", { method: "POST", body: JSON.stringify({ name, title }) }),
  samples: () => request<{ name: string; files: string[] }[]>("/api/projects/samples"),
  loadSample: (name: string) => request<Project>(`/api/projects/samples/${encodeURIComponent(name)}`, { method: "POST" }),
  importZip: (file: File, name?: string) => {
    const fd = new FormData();
    fd.append("file", file);
    if (name) fd.append("name", name);
    return request<Project>("/api/projects/import", { method: "POST", body: fd });
  },
  uploadFiles: (files: File[], name: string) => {
    const fd = new FormData();
    files.forEach((f) => fd.append("files", f));
    fd.append("name", name);
    return request<Project>("/api/projects/upload", { method: "POST", body: fd });
  },
  deleteProject: (id: string) => request<void>(`/api/projects/${id}`, { method: "DELETE" }),
  board: (id: string) => request<Record<string, unknown>>(`/api/projects/${id}/board`),
  constraints: (id: string) => request<DesignConstraints>(`/api/projects/${id}/constraints`),
  updateConstraints: (id: string, body: DesignConstraints) =>
    request<DesignConstraints>(`/api/projects/${id}/constraints`, { method: "PUT", body: JSON.stringify(body) }),
  designReview: (id: string) => request<DesignReview>(`/api/projects/${id}/design-review`),
  ecoReport: (id: string) => request<EcoReport>(`/api/projects/${id}/eco`),
  bomReport: (id: string) => request<BomReport>(`/api/projects/${id}/bom`),
  bomCsvUrl: (id: string) => withBase(`/api/projects/${id}/bom.csv`),
  circuitTemplates: () => request<{ templates: CircuitTemplate[] }>("/api/projects/circuit-templates"),
  previewCircuitTemplate: (id: string, body: { template: string; params: Record<string, string | number>; anchor_x?: number; anchor_y?: number }) =>
    request<TemplateExpansion>(`/api/projects/${id}/circuit-templates/preview`, { method: "POST", body: JSON.stringify(body) }),
  selectionMap: (id: string, mode: DesignMode) =>
    request<SelectionMap>(`/api/projects/${id}/selection-map?mode=${mode}`),
  snapshots: (id: string) => request<Snapshot[]>(`/api/projects/${id}/snapshots`),
  createSnapshot: (id: string, label: string) => {
    const fd = new FormData();
    fd.append("label", label);
    return request<Snapshot>(`/api/projects/${id}/snapshots`, { method: "POST", body: fd });
  },
  restoreSnapshot: (id: string, versionId: string, file: string) => {
    const fd = new FormData();
    fd.append("file", file);
    return request<unknown>(`/api/projects/${id}/snapshots/${versionId}/restore`, { method: "POST", body: fd });
  },
  previewUrl: (id: string, bust: number) => withBase(`/api/projects/${id}/preview.svg?t=${bust}`),
  previewSchUrl: (id: string, bust: number) => withBase(`/api/projects/${id}/preview_sch.svg?t=${bust}`),

  // conversations
  conversations: () => request<Conversation[]>("/api/conversations"),
  createConversation: (project_id?: string | null, title?: string) =>
    request<Conversation>("/api/conversations", { method: "POST", body: JSON.stringify({ project_id, title }) }),
  conversation: (id: string) => request<ConversationDetail>(`/api/conversations/${id}`),
  updateConversation: (id: string, patch: { title?: string; project_id?: string | null }) =>
    request<Conversation>(`/api/conversations/${id}`, { method: "PATCH", body: JSON.stringify(patch) }),
  deleteConversation: (id: string) => request<void>(`/api/conversations/${id}`, { method: "DELETE" }),
  cancel: (id: string) => request<{ cancelled: boolean }>(`/api/chat/${id}/cancel`, { method: "POST" }),
  runStatus: (id: string) =>
    request<{ active: boolean; run: { run_id: string; seq: number; done: boolean; cancelled: boolean } | null }>(`/api/chat/${id}/run`),

  // tools / skills / memory
  tools: () => request<ToolCategory[]>("/api/tools"),
  skills: () => request<Skill[]>("/api/skills"),
  memory: () => request<{ path: string; content: string }>("/api/memory"),
  saveMemory: (content: string) => request<{ ok: boolean }>("/api/memory", { method: "PUT", body: JSON.stringify({ content }) }),
};

/** Download a protected file (adds the bearer token) and trigger the browser save dialog. */
export async function downloadAuthenticated(url: string, filename: string): Promise<void> {
  const res = await fetch(withBase(url), { headers: { Authorization: `Bearer ${getToken() ?? ""}` } });
  if (!res.ok) throw new ApiError(res.status, "下载失败");
  const blob = await res.blob();
  const a = document.createElement("a");
  a.href = URL.createObjectURL(blob);
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  a.remove();
  URL.revokeObjectURL(a.href);
}

/** Fetch an SVG with auth headers and return an object URL. */
export async function fetchPreviewBlobUrl(projectId: string, engine = "auto"): Promise<string> {
  const res = await fetch(withBase(`/api/projects/${projectId}/preview.svg?t=${Date.now()}&engine=${encodeURIComponent(engine)}`), {
    headers: { Authorization: `Bearer ${getToken() ?? ""}` },
  });
  if (!res.ok) throw new ApiError(res.status, "预览生成失败");
  const blob = await res.blob();
  return URL.createObjectURL(blob);
}

/** Fetch a Schematic SVG with auth headers and return an object URL. */
export async function fetchPreviewSchBlobUrl(projectId: string, engine = "auto"): Promise<string> {
  const res = await fetch(withBase(`/api/projects/${projectId}/preview_sch.svg?t=${Date.now()}&engine=${encodeURIComponent(engine)}`), {
    headers: { Authorization: `Bearer ${getToken() ?? ""}` },
  });
  if (!res.ok) throw new ApiError(res.status, "原理图预览生成失败");
  const blob = await res.blob();
  return URL.createObjectURL(blob);
}


/**
 * POST + parse a Server-Sent-Events body. Calls onEvent for each event.
 * Returns when the stream ends; rejects on network failure.
 */
export async function streamSSE(
  path: string,
  body: unknown,
  onEvent: (ev: StreamEvent) => void,
  signal?: AbortSignal,
): Promise<void> {
  await consumeSSE(
    fetch(withBase(path), {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Accept: "text/event-stream",
        Authorization: `Bearer ${getToken() ?? ""}`,
      },
      body: JSON.stringify(body),
      signal,
    }),
    onEvent,
  );
}

/**
 * Re-attach to a conversation's background run and replay events with `seq > after`.
 * Resolves `false` when the server has no (recent) run for the conversation.
 */
export async function attachRunSSE(
  conversationId: string,
  after: number,
  onEvent: (ev: StreamEvent) => void,
  signal?: AbortSignal,
): Promise<boolean> {
  const res = await fetch(withBase(`/api/chat/${conversationId}/events?after=${Math.max(0, after)}`), {
    headers: { Accept: "text/event-stream", Authorization: `Bearer ${getToken() ?? ""}` },
    signal,
  });
  if (res.status === 204) return false;
  await consumeSSE(Promise.resolve(res), onEvent);
  return true;
}

async function consumeSSE(pending: Promise<Response>, onEvent: (ev: StreamEvent) => void): Promise<void> {
  const res = await pending;
  if (!res.ok || !res.body) {
    let detail = res.statusText;
    try {
      const j = await res.json();
      if (j?.detail) detail = String(j.detail);
    } catch {
      /* ignore */
    }
    throw new ApiError(res.status, detail);
  }
  const reader = res.body.getReader();
  const decoder = new TextDecoder();
  let buffer = "";
  for (;;) {
    const { value, done } = await reader.read();
    if (done) break;
    buffer += decoder.decode(value, { stream: true });
    let idx: number;
    while ((idx = buffer.indexOf("\n\n")) !== -1) {
      const raw = buffer.slice(0, idx);
      buffer = buffer.slice(idx + 2);
      const dataLines = raw
        .split("\n")
        .filter((l) => l.startsWith("data:"))
        .map((l) => l.slice(5).trimStart());
      if (!dataLines.length) continue;
      try {
        onEvent(JSON.parse(dataLines.join("\n")) as StreamEvent);
      } catch (e) {
        console.warn("bad SSE payload", e, raw);
      }
    }
  }
}

export function chatStreamPath(conversationId: string) {
  return `/api/chat/${conversationId}/stream`;
}
export function chatResumePath(conversationId: string) {
  return `/api/chat/${conversationId}/resume`;
}
