import { create } from "zustand";
import { api, ApiError, attachRunSSE, chatResumePath, chatStreamPath, streamSSE } from "@/lib/api";
import type { Conversation, FileDiff, Message, PendingInterrupt, PlanActionProgress, RunError, StreamEvent, ThinkingSelection, Todo, ToolCall } from "@/lib/types";
import { useProjects } from "./projects";
import { sendPresenceStatus } from "./presence";

export interface SubStep {
  id: string;
  name: string;
  status: ToolCall["status"];
}

interface ChatState {
  conversations: Conversation[];
  activeId: string | null;
  messages: Message[];
  todos: Todo[];
  pendingInterrupt: PendingInterrupt | null;
  running: boolean;
  runStartedAt: number | null;
  /** True while the client has lost the event stream of an active run and is re-attaching. */
  reconnecting: boolean;
  contextUsage: number;
  contextTokens: number;
  sessionInputTokens: number;
  sessionOutputTokens: number;
  snapshotsThisSession: number;
  /** Transport / HTTP level failure (shown in the top banner). */
  error: string | null;
  /** Agent run failure (recursion limit, model error) rendered inline in the thread. */
  runError: RunError | null;
  notice: string | null;
  loadingDetail: boolean;

  loadConversations: () => Promise<void>;
  newConversation: (projectId?: string | null) => Promise<Conversation>;
  openConversation: (id: string) => Promise<void>;
  deleteConversation: (id: string) => Promise<void>;
  renameConversation: (id: string, title: string) => Promise<void>;
  send: (content: string) => Promise<void>;
  resume: (decisions: { type: "approve" | "reject" | "edit"; message?: string }[]) => Promise<void>;
  /** After a recursion-limit stop: ask the agent to pick up where it left off. */
  continueRun: () => Promise<void>;
  /** After a failed run: re-send the last user message. */
  retryLast: () => Promise<void>;
  cancel: () => Promise<void>;
  setContextTokens: (n: number) => void;
  dismissError: () => void;
  dismissRunError: () => void;
  dismissTodos: () => void;
  selectedModel: string;
  setSelectedModel: (m: string) => void;
  selectedThinking: ThinkingSelection;
  setSelectedThinking: (mode: ThinkingSelection) => void;
}

let abortController: AbortController | null = null;
/** Sequence number of the last event applied for the active run (for `?after=` re-attach). */
let lastSeq = 0;
const ACTIVE_CONV_KEY = "kicad-ai.activeConversation";
const RECONNECT_DELAYS_MS = [800, 1500, 3000, 5000, 8000];
export const CONTINUE_PROMPT = "继续执行上面的任务，从刚才中断的地方接着做；先用一句话说明接下来要做什么。";

function rememberActive(id: string | null) {
  if (id) localStorage.setItem(ACTIVE_CONV_KEY, id);
  else localStorage.removeItem(ACTIVE_CONV_KEY);
}

function newId(prefix = "m") {
  return `${prefix}_${Math.random().toString(36).slice(2, 10)}`;
}

function firstDiff(diff: FileDiff | FileDiff[] | null | undefined): FileDiff | undefined {
  if (!diff) return undefined;
  return Array.isArray(diff) ? diff[0] : diff;
}

/** Rebuild per-action plan progress from a persisted submit_change_plan result. */
export function planFromResult(call: ToolCall): PlanActionProgress[] | undefined {
  if (call.name !== "submit_change_plan") return undefined;
  const data = call.data as { results?: unknown[]; actions?: unknown[] } | undefined;
  const planned = ((call.args?.actions as { tool?: string; summary?: string }[] | undefined) ?? []).map((a, index) => ({
    index,
    tool: String(a.tool ?? ""),
    summary: String(a.summary ?? a.tool ?? ""),
    status: "pending" as const,
  }));
  if (!Array.isArray(data?.results)) return planned.length ? planned : undefined;
  const merged: PlanActionProgress[] = planned.length ? planned : [];
  for (const raw of data.results as Array<Record<string, unknown>>) {
    const index = Number(raw.index ?? merged.length);
    const entry: PlanActionProgress = {
      index,
      tool: String(raw.tool ?? merged[index]?.tool ?? ""),
      summary: String(raw.summary ?? merged[index]?.summary ?? raw.tool ?? ""),
      status: raw.success ? "done" : "error",
      error: (raw.error as string | undefined) ?? null,
      diff: (raw.diff as FileDiff | undefined) ?? null,
      result: raw.result,
    };
    if (merged[index]) merged[index] = entry;
    else merged.push(entry);
  }
  return merged;
}

/** Attach tool results from history `tool` messages onto assistant tool_calls. */
function foldHistory(raw: Message[]): Message[] {
  const out: Message[] = [];
  const byCallId = new Map<string, ToolCall>();
  for (const m of raw) {
    if (m.role === "tool") {
      const tc = m.tool_call_id ? byCallId.get(m.tool_call_id) : undefined;
      if (tc) {
        tc.status = m.status === "error" ? "error" : "done";
        tc.result = m.content;
        tc.diff = firstDiff(m.diff);
        try {
          tc.data = m.content?.trim().startsWith("{") || m.content?.trim().startsWith("[") ? JSON.parse(m.content) : undefined;
        } catch {
          tc.data = undefined;
        }
        tc.plan = planFromResult(tc);
      }
      continue;
    }
    if (m.role === "assistant") {
      const calls: ToolCall[] = (m.tool_calls ?? []).map((tc) => ({
        id: tc.id,
        name: tc.name,
        args: tc.args,
        kind: tc.kind,
        category: tc.category,
        // calls that never got a result (e.g. the run was cut off) stay "pending"
        status: "pending",
      }));
      calls.forEach((c) => byCallId.set(c.id, c));
      out.push({ ...m, tool_calls: calls, streaming: false });
      continue;
    }
    out.push(m);
  }
  return out;
}

export const useChat = create<ChatState>((set, get) => ({
  conversations: [],
  activeId: null,
  messages: [],
  todos: [],
  pendingInterrupt: null,
  running: false,
  runStartedAt: null,
  reconnecting: false,
  contextUsage: 0,
  contextTokens: 128000,
  sessionInputTokens: 0,
  sessionOutputTokens: 0,
  snapshotsThisSession: 0,
  error: null,
  runError: null,
  notice: null,
  loadingDetail: false,
  selectedModel: localStorage.getItem("kicad-ai.selectedModel") || "auto",
  selectedThinking: (localStorage.getItem("kicad-ai.selectedThinking") as ThinkingSelection | null) || "auto",

  setSelectedModel(m) {
    localStorage.setItem("kicad-ai.selectedModel", m);
    set({ selectedModel: m });
  },

  setSelectedThinking(mode) {
    localStorage.setItem("kicad-ai.selectedThinking", mode);
    set({ selectedThinking: mode });
  },

  setContextTokens(n) {
    set({ contextTokens: n });
  },

  dismissError() {
    set({ error: null, notice: null });
  },

  dismissRunError() {
    set({ runError: null });
  },

  dismissTodos() {
    set({ todos: [] });
  },

  async loadConversations() {
    const conversations = await api.conversations();
    set({ conversations });
    const { activeId } = get();
    if (activeId && !conversations.some((c) => c.id === activeId)) {
      set({ activeId: null, messages: [] });
      rememberActive(null);
    }
    if (!get().activeId && conversations.length) {
      const remembered = localStorage.getItem(ACTIVE_CONV_KEY);
      const target = conversations.find((c) => c.id === remembered) ?? conversations[0];
      await get().openConversation(target.id);
    }
  },

  async newConversation(projectId) {
    // Only the event subscription is dropped; a run in the previous conversation keeps going server-side.
    detachStream();
    resetPendingDeltas();
    const pid = projectId === undefined ? useProjects.getState().activeProjectId : projectId;
    const conv = await api.createConversation(pid ?? null);
    rememberActive(conv.id);
    set({
      conversations: [conv, ...get().conversations],
      activeId: conv.id,
      messages: [],
      todos: [],
      pendingInterrupt: null,
      contextUsage: 0,
      sessionInputTokens: 0,
      sessionOutputTokens: 0,
      snapshotsThisSession: 0,
      error: null,
      runError: null,
    });
    return conv;
  },

  async openConversation(id) {
    detachStream();
    resetPendingDeltas();
    rememberActive(id);
    set({ activeId: id, loadingDetail: true, error: null, runError: null, running: false, runStartedAt: null, reconnecting: false });
    try {
      const detail = await api.conversation(id);
      if (get().activeId !== id) return; // user moved on while we were loading
      const pendingTodos = (detail.todos || []).some((t) => t.status !== "completed") ? detail.todos : [];
      set({
        messages: foldHistory(detail.messages),
        todos: pendingTodos,
        pendingInterrupt: detail.pending_interrupt,
        contextUsage: detail.context_usage,
        sessionInputTokens: detail.conversation.input_tokens,
        sessionOutputTokens: detail.conversation.output_tokens,
        running: Boolean(detail.is_running),
        loadingDetail: false,
      });
      if (detail.conversation.project_id) useProjects.getState().setActive(detail.conversation.project_id);
      if (detail.is_running) {
        // History stops where the run started; replay the run's events on top.
        void attachToRun(id, 0, set, get);
      }
    } catch (e) {
      set({ loadingDetail: false, error: (e as Error).message });
    }
  },

  async deleteConversation(id) {
    await api.deleteConversation(id);
    const conversations = get().conversations.filter((c) => c.id !== id);
    set({ conversations });
    if (get().activeId === id) {
      rememberActive(null);
      set({ activeId: null, messages: [], todos: [], pendingInterrupt: null, runError: null });
    }
  },

  async renameConversation(id, title) {
    const conv = await api.updateConversation(id, { title });
    set({ conversations: get().conversations.map((c) => (c.id === id ? conv : c)) });
  },

  async send(content) {
    let { activeId } = get();
    if (!activeId) {
      const conv = await get().newConversation();
      activeId = conv.id;
    }
    const projectState = useProjects.getState();
    const projectId = projectState.activeProjectId;
    const selection = projectState.selection?.project_id === projectId ? projectState.selection : null;
    const userMsg: Message = { id: newId("u"), role: "user", content, created_at: new Date().toISOString() };
    set({ messages: [...get().messages, userMsg], todos: [], error: null, notice: null });
    const { selectedModel, selectedThinking } = get();
    await runStream(
      chatStreamPath(activeId),
      {
        content,
        project_id: projectId,
        model: selectedModel,
        thinking: selectedThinking === "auto" ? undefined : selectedThinking,
        selection: selection ?? undefined,
      },
      set,
      get,
    );
  },

  async resume(decisions) {
    const { activeId } = get();
    if (!activeId) return;
    set({ pendingInterrupt: null, notice: null });
    const { selectedModel, selectedThinking } = get();
    const selection = useProjects.getState().selection;
    await runStream(
      chatResumePath(activeId),
      {
        decisions,
        model: selectedModel,
        thinking: selectedThinking === "auto" ? undefined : selectedThinking,
        selection: selection ?? undefined,
      },
      set,
      get,
    );
  },

  async continueRun() {
    if (get().running) return;
    await get().send(CONTINUE_PROMPT);
  },

  async retryLast() {
    if (get().running) return;
    const last = [...get().messages].reverse().find((m) => m.role === "user");
    if (!last) return;
    await get().send(last.content);
  },

  async cancel() {
    const { activeId } = get();
    if (!activeId) return;
    // Ask the server to stop the run; the stream then delivers the cancellation
    // error and run_end, which is what ends the local "running" state.
    try {
      await api.cancel(activeId);
    } catch {
      /* ignore */
    }
    if (!abortController) set({ running: false, reconnecting: false });
  },
}));

type Set = (partial: Partial<ChatState> | ((s: ChatState) => Partial<ChatState>)) => void;
type Get = () => ChatState;

function detachStream() {
  abortController?.abort();
  abortController = null;
}

function sleep(ms: number, signal?: AbortSignal): Promise<void> {
  return new Promise((resolve) => {
    const t = setTimeout(resolve, ms);
    signal?.addEventListener("abort", () => {
      clearTimeout(t);
      resolve();
    });
  });
}

/** Start a run (POST stream/resume) and follow its events until it ends, re-attaching if the connection drops. */
async function runStream(path: string, body: unknown, set: Set, get: Get) {
  detachStream();
  const controller = (abortController = new AbortController());
  const conversationId = get().activeId;
  lastSeq = 0;
  set({ running: true, runStartedAt: Date.now(), pendingInterrupt: null, runError: null, notice: null, reconnecting: false });
  sendPresenceStatus("busy");
  let ended = false;
  const onEvent = (ev: StreamEvent) => {
    if (typeof ev.seq === "number") lastSeq = ev.seq;
    if (ev.type === "run_end") ended = true;
    applyEvent(ev, set, get);
  };
  try {
    await streamSSE(path, body, onEvent, controller.signal);
    if (!ended && !controller.signal.aborted && conversationId) {
      await reattachLoop(conversationId, controller, onEvent, set, get);
    }
  } catch (e) {
    if ((e as Error).name === "AbortError") {
      /* detached on purpose */
    } else if (e instanceof ApiError || !conversationId) {
      set({ error: (e as Error).message });
    } else {
      // Transport failure mid-run: the run is still alive server-side.
      await reattachLoop(conversationId, controller, onEvent, set, get);
    }
  } finally {
    if (abortController === controller) finishStream(controller, set, get);
  }
}

/** Follow an already running background run (after reload / switching conversations). */
async function attachToRun(conversationId: string, after: number, set: Set, get: Get) {
  detachStream();
  const controller = (abortController = new AbortController());
  lastSeq = after;
  set({ running: true, runStartedAt: get().runStartedAt ?? Date.now(), reconnecting: false, runError: null });
  sendPresenceStatus("busy");
  const onEvent = (ev: StreamEvent) => {
    if (typeof ev.seq === "number") lastSeq = ev.seq;
    applyEvent(ev, set, get);
  };
  try {
    const attached = await attachRunSSE(conversationId, after, onEvent, controller.signal);
    if (!attached && !controller.signal.aborted) {
      // The run finished and expired while we were away; history already has everything.
      await reloadDetail(conversationId, set, get);
    }
  } catch (e) {
    if ((e as Error).name !== "AbortError") await reattachLoop(conversationId, controller, onEvent, set, get);
  } finally {
    if (abortController === controller) finishStream(controller, set, get);
  }
}

async function reattachLoop(
  conversationId: string,
  controller: AbortController,
  onEvent: (ev: StreamEvent) => void,
  set: Set,
  get: Get,
) {
  set({ reconnecting: true });
  for (const delay of RECONNECT_DELAYS_MS) {
    if (controller.signal.aborted || get().activeId !== conversationId) return;
    await sleep(delay, controller.signal);
    if (controller.signal.aborted) return;
    try {
      const attached = await attachRunSSE(conversationId, lastSeq, onEvent, controller.signal);
      set({ reconnecting: false });
      if (!attached) await reloadDetail(conversationId, set, get);
      return;
    } catch (e) {
      if ((e as Error).name === "AbortError") return;
      if (e instanceof ApiError && e.status !== 0 && e.status < 500 && e.status !== 408 && e.status !== 429) {
        set({ error: e.message, reconnecting: false });
        return;
      }
    }
  }
  set({ reconnecting: false, error: "与服务器的连接已断开，无法恢复实时输出；任务可能仍在后台运行，稍后刷新查看结果。" });
}

async function reloadDetail(conversationId: string, set: Set, get: Get) {
  try {
    const detail = await api.conversation(conversationId);
    if (get().activeId !== conversationId) return;
    set({
      messages: foldHistory(detail.messages),
      pendingInterrupt: detail.pending_interrupt,
      contextUsage: detail.context_usage,
      sessionInputTokens: detail.conversation.input_tokens,
      sessionOutputTokens: detail.conversation.output_tokens,
    });
  } catch {
    /* keep what we have */
  }
}

function finishStream(controller: AbortController, set: Set, get: Get) {
  flushDeltas(set, get);
  if (abortController === controller) abortController = null;
  set((s) => ({
    running: false,
    reconnecting: false,
    messages: s.messages.map((m) => (m.streaming ? { ...m, streaming: false } : m)),
  }));
  sendPresenceStatus("idle");
  const aid = get().activeId;
  if (aid) {
    api.conversation(aid).then((det) => {
      if (get().activeId !== aid) return;
      set({ contextUsage: det.context_usage });
      if (det.conversation?.project_id && det.conversation.project_id !== useProjects.getState().activeProjectId) {
        useProjects.getState().load().then(() => {
          useProjects.getState().setActive(det.conversation.project_id!);
        }).catch(() => undefined);
      }
    }).catch(() => undefined);
  }
  get().loadConversations().catch(() => undefined);
}

// ---------------------------------------------------------------------------
// Delta batching: tokens arrive faster than React can (or should) re-render a
// Markdown tree. Text and tool-argument deltas are coalesced and flushed on a
// short timer (~2 frames); every non-delta event flushes first so ordering
// between deltas and structural events is preserved.
// ---------------------------------------------------------------------------
const FLUSH_MS = 32;
const pendingText = new Map<string, { source: string; text: string }>();
const pendingReasoning = new Map<string, { source: string; text: string }>();
const pendingArgs = new Map<string, string>();
let flushTimer: ReturnType<typeof setTimeout> | null = null;

function scheduleFlush(set: Set, get: Get) {
  if (flushTimer !== null) return;
  flushTimer = setTimeout(() => {
    flushTimer = null;
    flushDeltas(set, get);
  }, FLUSH_MS);
}

function flushDeltas(set: Set, get: Get) {
  if (flushTimer !== null) {
    clearTimeout(flushTimer);
    flushTimer = null;
  }
  if (pendingText.size === 0 && pendingReasoning.size === 0 && pendingArgs.size === 0) return;
  let messages = get().messages;
  for (const [id, { source, text }] of pendingReasoning) {
    const r = ensureAssistant(messages, id, source);
    const next = [...r.messages];
    next[r.index] = { ...next[r.index], reasoning: (next[r.index].reasoning ?? "") + text, streaming: true };
    messages = next;
  }
  pendingReasoning.clear();
  for (const [id, { source, text }] of pendingText) {
    const r = ensureAssistant(messages, id, source);
    const next = [...r.messages];
    next[r.index] = { ...next[r.index], content: next[r.index].content + text, streaming: true };
    messages = next;
  }
  pendingText.clear();
  for (const [id, text] of pendingArgs) {
    const loc = findToolCall(messages, id);
    if (!loc) continue;
    const next = [...messages];
    const calls = [...(next[loc.mi].tool_calls ?? [])];
    calls[loc.ti] = { ...calls[loc.ti], argsText: (calls[loc.ti].argsText ?? "") + text };
    next[loc.mi] = { ...next[loc.mi], tool_calls: calls };
    messages = next;
  }
  pendingArgs.clear();
  set({ messages });
}

function resetPendingDeltas() {
  if (flushTimer !== null) {
    clearTimeout(flushTimer);
    flushTimer = null;
  }
  pendingText.clear();
  pendingReasoning.clear();
  pendingArgs.clear();
}

function ensureAssistant(messages: Message[], id: string | undefined, source: string): { messages: Message[]; index: number } {
  if (id) {
    const idx = messages.findIndex((m) => m.id === id);
    if (idx !== -1) return { messages, index: idx };
  }
  // reuse the last streaming assistant message from the same source
  for (let i = messages.length - 1; i >= 0; i--) {
    const m = messages[i];
    if (m.role === "assistant" && m.streaming && (m.source ?? "main") === source) return { messages, index: i };
    if (m.role === "user") break;
  }
  const msg: Message = { id: id ?? newId("a"), role: "assistant", content: "", tool_calls: [], streaming: true, source, created_at: new Date().toISOString() };
  return { messages: [...messages, msg], index: messages.length };
}

function findToolCall(messages: Message[], id: string): { mi: number; ti: number } | null {
  for (let mi = messages.length - 1; mi >= 0; mi--) {
    const calls = messages[mi].tool_calls;
    if (!calls) continue;
    const ti = calls.findIndex((t) => t.id === id);
    if (ti !== -1) return { mi, ti };
  }
  return null;
}

function lastRunningTask(messages: Message[]): { mi: number; ti: number } | null {
  for (let mi = messages.length - 1; mi >= 0; mi--) {
    const calls = messages[mi].tool_calls;
    if (!calls) continue;
    for (let ti = calls.length - 1; ti >= 0; ti--) {
      if (calls[ti].name === "task" && calls[ti].status === "running") return { mi, ti };
    }
  }
  return null;
}

function patchToolCall(messages: Message[], id: string, patch: (tc: ToolCall) => ToolCall): Message[] | null {
  const loc = findToolCall(messages, id);
  if (!loc) return null;
  const next = [...messages];
  const calls = [...(next[loc.mi].tool_calls ?? [])];
  calls[loc.ti] = patch(calls[loc.ti]);
  next[loc.mi] = { ...next[loc.mi], tool_calls: calls };
  return next;
}

function applyEvent(ev: StreamEvent, set: Set, get: Get) {
  // deltas are coalesced; everything else flushes first to keep ordering
  if (ev.type === "reasoning_delta") {
    if (ev.source !== "main") return;
    const cur = pendingReasoning.get(ev.id);
    if (cur) cur.text += ev.delta;
    else pendingReasoning.set(ev.id, { source: "main", text: ev.delta });
    scheduleFlush(set, get);
    return;
  }
  if (ev.type === "text_delta") {
    if (ev.source !== "main") return;
    const cur = pendingText.get(ev.id);
    if (cur) cur.text += ev.delta;
    else pendingText.set(ev.id, { source: "main", text: ev.delta });
    scheduleFlush(set, get);
    return;
  }
  if (ev.type === "tool_call_delta") {
    if (ev.source !== "main") return;
    pendingArgs.set(ev.id, (pendingArgs.get(ev.id) ?? "") + ev.delta);
    scheduleFlush(set, get);
    return;
  }
  flushDeltas(set, get);

  const s = get();
  switch (ev.type) {
    case "run_start":
      return;

    case "user_message": {
      // Present when (re)attaching to a run; the sending tab already appended the bubble.
      const last = s.messages[s.messages.length - 1];
      if (last?.role === "user" && last.content === ev.content) return;
      set({ messages: [...s.messages, { id: newId("u"), role: "user", content: ev.content, created_at: new Date().toISOString() }] });
      return;
    }

    case "run_resync": {
      // We fell too far behind the server's event buffer: reload history, keep following.
      if (s.activeId) void reloadDetail(s.activeId, set, get);
      return;
    }

    case "message_start": {
      if (ev.source !== "main") return;
      const { messages } = ensureAssistant(s.messages, ev.id, "main");
      set({ messages });
      return;
    }

    case "tool_call_start": {
      if (/^(add_symbol|remove_symbol|rename_symbol|set_symbol|add_wire|delete_wire|add_label|delete_label|connect_pins|connect_points|move_component|list_schematic|get_schematic|extract_schematic|find_component|check_reference)/.test(ev.name)) {
        useProjects.getState().setPreviewMode("sch");
      } else if (/^(pcb_|set_footprint|move_footprint|align_footprint|distribute_footprint|flip_footprint|set_board|clear_board|add_board|list_footprint|get_board|list_zones|add_zone|delete_zone|refill_zones|list_tracks|list_vias)/.test(ev.name)) {
        useProjects.getState().setPreviewMode("pcb");
      }
      if (ev.source !== "main") {
        // subagent tool call → attach as sub-step of the running task
        const loc = lastRunningTask(s.messages);
        if (!loc) return;
        const next = [...s.messages];
        const calls = [...(next[loc.mi].tool_calls ?? [])];
        const task = { ...calls[loc.ti] } as ToolCall & { substeps?: SubStep[] };
        task.substeps = [...(task.substeps ?? []), { id: ev.id, name: ev.name, status: "running" }];
        calls[loc.ti] = task;
        next[loc.mi] = { ...next[loc.mi], tool_calls: calls };
        set({ messages: next });
        return;
      }
      if (findToolCall(s.messages, ev.id)) return;
      const { messages, index } = ensureAssistant(s.messages, ev.message_id, "main");
      const next = [...messages];
      next[index] = {
        ...next[index],
        tool_calls: [...(next[index].tool_calls ?? []), { id: ev.id, name: ev.name, kind: ev.kind, category: ev.category, status: "running" }],
      };
      set({ messages: next });
      return;
    }

    case "tool_call_args": {
      if (ev.source !== "main") return;
      const next = patchToolCall(s.messages, ev.id, (tc) => ({ ...tc, args: ev.args, kind: ev.kind ?? tc.kind, category: ev.category ?? tc.category, argsText: undefined }));
      if (next) set({ messages: next });
      return;
    }

    case "tool_result": {
      if (ev.name === "create_project" || ev.name === "switch_project" || (ev.data as any)?.project_id) {
        const pid = (ev.data as any)?.project_id;
        useProjects.getState().load().then(() => {
          if (pid) {
            useProjects.getState().setActive(pid);
          }
        }).catch(() => undefined);
        if (ev.name === "create_project") {
          useProjects.getState().setPreviewMode("sch");
        }
      }
      if (ev.ok && /^(add_|set_|move_|delete_|remove_|rename_|connect_|place_|flip_|clear_|pcb_|update_pcb|write_|edit_|refill_|create_project)/.test(ev.name)) {
        useProjects.getState().bumpPreview();
      }
      if (ev.source !== "main") {
        const loc = lastRunningTask(s.messages);
        if (!loc) return;
        const next = [...s.messages];
        const calls = [...(next[loc.mi].tool_calls ?? [])];
        const task = { ...calls[loc.ti] } as ToolCall & { substeps?: SubStep[] };
        task.substeps = (task.substeps ?? []).map((st) => (st.id === ev.id ? { ...st, status: ev.ok ? "done" : "error" } : st));
        calls[loc.ti] = task;
        next[loc.mi] = { ...next[loc.mi], tool_calls: calls };
        set({ messages: next });
        return;
      }
      const next = patchToolCall(s.messages, ev.id, (tc) => {
        const patched: ToolCall = {
          ...tc,
          status: ev.ok ? "done" : "error",
          result: ev.content,
          data: ev.data,
          diff: ev.diff ?? tc.diff,
          argsText: undefined,
        };
        if (tc.name === "submit_change_plan") patched.plan = planFromResult(patched) ?? tc.plan;
        return patched;
      });
      if (next) set({ messages: next });
      return;
    }

    case "message_end": {
      if (ev.source !== "main") return;
      const targetId = ev.stream_id ?? ev.id;
      const next = [...s.messages];
      let idx = next.findIndex((m) => m.id === targetId);
      if (idx === -1) idx = next.findIndex((m) => m.id === ev.id);
      if (idx === -1) {
        const r = ensureAssistant(next, undefined, "main");
        idx = r.index;
        next.splice(0, next.length, ...r.messages);
      }
      const existing = next[idx];
      const calls = [...(existing.tool_calls ?? [])];
      for (const tc of ev.tool_calls) {
        const i = calls.findIndex((c) => c.id === tc.id);
        if (i === -1) calls.push({ id: tc.id, name: tc.name, args: tc.args, kind: tc.kind, category: tc.category, status: "running" });
        else calls[i] = { ...calls[i], args: tc.args, kind: tc.kind ?? calls[i].kind, category: tc.category ?? calls[i].category, argsText: undefined };
      }
      next[idx] = {
        ...existing,
        id: ev.id ?? existing.id,
        content: ev.content || existing.content,
        reasoning: ev.reasoning || existing.reasoning,
        tool_calls: calls,
        streaming: false,
      };
      set({ messages: next });
      return;
    }

    case "todos":
      set({ todos: ev.todos });
      return;

    case "custom": {
      if (ev.event === "file_changed") useProjects.getState().bumpPreview();
      else if (ev.event === "snapshot") set({ snapshotsThisSession: s.snapshotsThisSession + 1 });
      else if (ev.event === "plan_action") {
        // Server-side execution of an approved plan: one event per action transition.
        const e = ev as unknown as { plan_call_id: string; index: number; tool: string; summary?: string; status: "running" | "done" | "error"; error?: string | null; diff?: FileDiff | null };
        const next = patchToolCall(s.messages, e.plan_call_id, (tc) => {
          const planned = tc.plan ?? planFromResult(tc) ?? [];
          const list = [...planned];
          const entry: PlanActionProgress = {
            index: e.index,
            tool: e.tool,
            summary: e.summary ?? list[e.index]?.summary ?? e.tool,
            status: e.status,
            error: e.error ?? null,
            diff: e.diff ?? list[e.index]?.diff ?? null,
          };
          if (list[e.index]) list[e.index] = { ...list[e.index], ...entry };
          else list.push(entry);
          return { ...tc, plan: list };
        });
        if (next) set({ messages: next });
      } else if (ev.event === "file_diff") {
        const { event: _e, type: _t, source: _s, tool_call_id, ...diff } = ev as unknown as { event: string; type: string; source: string; tool_call_id?: string } & FileDiff;
        void _e;
        void _t;
        void _s;
        if (tool_call_id) {
          // "<plan call id>:<index>" diffs belong to one action of a server-executed plan.
          const sep = tool_call_id.lastIndexOf(":");
          const planId = sep > 0 ? tool_call_id.slice(0, sep) : null;
          const actionIndex = sep > 0 ? Number(tool_call_id.slice(sep + 1)) : NaN;
          const next =
            planId && Number.isInteger(actionIndex) && findToolCall(s.messages, planId)
              ? patchToolCall(s.messages, planId, (tc) => {
                  const list = [...(tc.plan ?? planFromResult(tc) ?? [])];
                  if (list[actionIndex]) list[actionIndex] = { ...list[actionIndex], diff: diff as FileDiff };
                  return { ...tc, plan: list };
                })
              : patchToolCall(s.messages, tool_call_id, (tc) => ({ ...tc, diff: diff as FileDiff }));
          if (next) set({ messages: next });
        }
      }
      return;
    }

    case "interrupt": {
      const first = ev.interrupts[0] ?? null;
      set({ pendingInterrupt: first });
      return;
    }

    case "usage": {
      const input = ev.input_tokens ?? 0;
      const output = ev.output_tokens ?? 0;
      const total = ev.total_tokens ?? input + output;
      const contextLimit = Math.max(16000, s.contextTokens || 128000);
      set({
        sessionInputTokens: s.sessionInputTokens + input,
        sessionOutputTokens: s.sessionOutputTokens + output,
        contextUsage: Math.min(1, total / contextLimit),
      });
      return;
    }

    case "error":
      set({
        runError: { code: ev.code ?? "run_failed", message: ev.message, recoverable: ev.recoverable ?? ev.code === "recursion_limit", limit: ev.limit },
      });
      return;

    case "run_end":
      if (ev.interrupted) set({ notice: "代理正在等待你的确认" });
      else if (ev.cancelled) set({ notice: "本次运行已取消" });
      else set({ notice: null });
      return;
  }
}
