import clsx from "clsx";
import { AlertTriangle, ArrowDown, Brain, Check, ChevronDown, ChevronUp, Copy, ListChecks, Loader2, OctagonAlert, Play, RefreshCw, Smile, Sparkles, Square, ThumbsDown, ThumbsUp, WifiOff, X } from "lucide-react";
import { memo, useCallback, useEffect, useLayoutEffect, useMemo, useRef, useState } from "react";
import { Avatar, BotAvatar } from "@/components/ui/Avatar";
import { Markdown } from "@/components/ui/Markdown";
import { summarizeArgs, timeHM, toolLabel } from "@/lib/format";
import type { Message, RunError, Todo, ToolCall } from "@/lib/types";
import { useAuth } from "@/store/auth";
import { useChat } from "@/store/chat";
import { ChangePlanCard } from "./ChangePlanCard";
import { DesignReviewCard, isDesignReviewData } from "./DesignReviewCard";
import { DrcResultCard, isDrcData } from "./DrcResultCard";
import { FileEditCard, isEditCall } from "./FileEditCard";
import { InterruptCard } from "./InterruptCard";
import { ToolCallCard } from "./ToolCallCard";

/** Group consecutive assistant messages into one visual turn. */
interface Turn {
  key: string;
  role: "user" | "assistant" | "system";
  messages: Message[];
  at?: string | null;
}

function toTurns(messages: Message[]): Turn[] {
  const turns: Turn[] = [];
  for (const m of messages) {
    if (m.role === "tool") continue;
    const last = turns[turns.length - 1];
    if (m.role === "assistant" && last?.role === "assistant") {
      last.messages.push(m);
      last.at = m.created_at ?? last.at;
      continue;
    }
    turns.push({ key: m.id, role: m.role as Turn["role"], messages: [m], at: m.created_at });
  }
  return turns;
}

function sameMessages(a: Message[], b: Message[]): boolean {
  if (a.length !== b.length) return false;
  for (let i = 0; i < a.length; i++) if (a[i] !== b[i]) return false;
  return true;
}

const NEAR_BOTTOM_PX = 96;

export function MessageList({ onSuggestion }: { onSuggestion: (text: string) => void }) {
  const { messages, todos, running, reconnecting, pendingInterrupt, runError, loadingDetail, activeId } = useChat();
  const user = useAuth((s) => s.user);
  const scrollRef = useRef<HTMLDivElement>(null);
  const stickToBottom = useRef(true);
  const [showJump, setShowJump] = useState(false);
  const turns = useMemo(() => toTurns(messages), [messages]);

  const onScroll = useCallback(() => {
    const el = scrollRef.current;
    if (!el) return;
    const dist = el.scrollHeight - el.scrollTop - el.clientHeight;
    const near = dist < NEAR_BOTTOM_PX;
    stickToBottom.current = near;
    if (near) setShowJump(false);
  }, []);

  const jumpToBottom = useCallback((smooth = true) => {
    const el = scrollRef.current;
    if (!el) return;
    stickToBottom.current = true;
    setShowJump(false);
    el.scrollTo({ top: el.scrollHeight, behavior: smooth ? "smooth" : "auto" });
  }, []);

  // switching conversations: land at the bottom instantly
  useLayoutEffect(() => {
    stickToBottom.current = true;
    const el = scrollRef.current;
    if (el) el.scrollTop = el.scrollHeight;
  }, [activeId, loadingDetail]);

  // new content: follow only if the reader is already at the bottom
  useEffect(() => {
    const el = scrollRef.current;
    if (!el) return;
    if (stickToBottom.current) {
      // instant while tokens stream (smooth scrolling would lag behind), eased otherwise
      el.scrollTo({ top: el.scrollHeight, behavior: running ? "auto" : "smooth" });
    } else {
      setShowJump(true);
    }
  }, [messages, todos, pendingInterrupt, running, runError, reconnecting]);

  if (loadingDetail) {
    return (
      <div className="flex flex-1 items-center justify-center gap-2 text-sm text-slate-400">
        <span className="typing-dot h-1.5 w-1.5 rounded-full bg-slate-300" />
        <span className="typing-dot h-1.5 w-1.5 rounded-full bg-slate-300" />
        <span className="typing-dot h-1.5 w-1.5 rounded-full bg-slate-300" />
      </div>
    );
  }
  if (!activeId || turns.length === 0) {
    return <EmptyState onSuggestion={onSuggestion} />;
  }

  let lastAssistantIndex = -1;
  for (let i = turns.length - 1; i >= 0; i--) {
    if (turns[i].role === "assistant") {
      lastAssistantIndex = i;
      break;
    }
  }

  return (
    <div className="relative min-h-0 flex-1">
      <div ref={scrollRef} onScroll={onScroll} className="chat-canvas h-full overflow-y-auto px-4 py-4 [overflow-anchor:none]">
        <div className="mx-auto flex max-w-4xl flex-col gap-5">
          {turns.map((t, idx) =>
            t.role === "user" ? (
              <UserTurn key={t.key} message={t.messages[0]} name={user?.display_name ?? ""} color={user?.avatar_color} />
            ) : t.role === "system" ? (
              <div key={t.key} className="animate-fade-up mx-auto rounded-lg bg-rose-50 px-3 py-1.5 text-xs text-rose-600">
                {t.messages[0].content}
              </div>
            ) : (
              <AssistantTurn key={t.key} turn={t} isLastAssistant={idx === lastAssistantIndex} running={running} />
            ),
          )}
          {lastAssistantIndex === -1 && todos.length > 0 && (
            <div className="ml-[46px]">
              <TodoCard todos={todos} />
            </div>
          )}
          {pendingInterrupt && <InterruptCard interrupt={pendingInterrupt} />}
          {reconnecting && <ReconnectBanner />}
          {runError && !running && <RunErrorCard error={runError} />}
          {lastAssistantIndex === -1 && running && <Typing />}
          <div className="h-1" />
        </div>
      </div>
      <button
        onClick={() => jumpToBottom(true)}
        className={clsx(
          "absolute bottom-4 left-1/2 flex -translate-x-1/2 items-center gap-1.5 rounded-full border border-line bg-white/95 px-3 py-1.5 text-[11.5px] font-medium text-slate-600 shadow-[0_6px_20px_rgba(28,32,51,0.12)] backdrop-blur transition-all duration-200 hover:bg-white hover:text-ink",
          showJump ? "translate-y-0 opacity-100" : "pointer-events-none translate-y-2 opacity-0",
        )}
      >
        <ArrowDown size={13} /> 回到底部
      </button>
    </div>
  );
}

const UserTurn = memo(
  function UserTurn({ message, name, color }: { message: Message; name: string; color?: string }) {
    return (
      <div className="animate-fade-up flex items-start justify-end gap-2.5">
        <div className="flex max-w-[78%] flex-col items-end">
          <div className="rounded-2xl rounded-tr-md bg-[#eceefe] px-4 py-2.5 text-[13.5px] leading-relaxed text-ink whitespace-pre-wrap">{message.content}</div>
          <span className="mt-1 text-[11px] text-slate-400">{timeHM(message.created_at)}</span>
        </div>
        <Avatar name={name} color={color} size={34} />
      </div>
    );
  },
  (a, b) => a.message === b.message && a.name === b.name && a.color === b.color,
);

function extractTodosFromTurn(turn: Turn): Todo[] | null {
  for (let mi = turn.messages.length - 1; mi >= 0; mi--) {
    const m = turn.messages[mi];
    const calls = m.tool_calls ?? [];
    for (let ci = calls.length - 1; ci >= 0; ci--) {
      const c = calls[ci];
      if (c.name === "write_todos") {
        let args: any = c.args;
        if (typeof args === "string") {
          try {
            args = JSON.parse(args);
          } catch {
            args = null;
          }
        }
        const rawList = Array.isArray(args?.todos)
          ? args.todos
          : Array.isArray(args?.tasks)
          ? args.tasks
          : Array.isArray(args)
          ? args
          : null;
        if (rawList && rawList.length > 0) {
          const list: Todo[] = rawList
            .map((item: any) => {
              if (typeof item === "string") {
                return { content: item, status: "completed" as const };
              }
              const content = String(item.content || item.title || item.task || item.text || "").trim();
              const status: Todo["status"] =
                item.status === "in_progress" || item.status === "completed" ? item.status : "pending";
              return { content, status };
            })
            .filter((t: Todo) => t.content.length > 0);
          if (list.length > 0) return list;
        }
      }
    }
  }
  return null;
}

type Block =
  | { kind: "reasoning"; content: string; streaming: boolean; key: string }
  | { kind: "text"; content: string; streaming: boolean; key: string }
  | { kind: "tools"; calls: ToolCall[]; key: string }
  | { kind: "edit"; call: ToolCall; key: string }
  | { kind: "plan"; call: ToolCall; key: string }
  | { kind: "review"; call: ToolCall; key: string }
  | { kind: "check"; check: "DRC" | "ERC"; call: ToolCall; key: string };

function buildBlocks(turn: Turn): Block[] {
  // text, then tool calls in order: read-only calls fold into one compact list,
  // every file edit gets its own card, change plans get a checklist card, DRC
  // results get a result card. write_todos is rendered as a dedicated TodoCard.
  const blocks: Block[] = [];
  for (const m of turn.messages) {
    if (m.reasoning?.trim()) blocks.push({ kind: "reasoning", content: m.reasoning, streaming: !!m.streaming, key: `r:${m.id}` });
    if (m.content.trim()) blocks.push({ kind: "text", content: m.content, streaming: !!m.streaming, key: `t:${m.id}` });
    for (const c of m.tool_calls ?? []) {
      if (c.name === "write_todos") {
        continue;
      }
      if (c.name === "submit_change_plan") {
        blocks.push({ kind: "plan", call: c, key: `p:${c.id}` });
      } else if (isEditCall(c)) {
        blocks.push({ kind: "edit", call: c, key: `e:${c.id}` });
      } else {
        const last = blocks[blocks.length - 1];
        if (last && last.kind === "tools") last.calls.push(c);
        else blocks.push({ kind: "tools", calls: [c], key: `g:${c.id}` });
      }
      if (c.name === "run_drc_check" && isDrcData(c.data)) blocks.push({ kind: "check", check: "DRC", call: c, key: `d:${c.id}` });
      if (c.name === "run_erc_check" && isDrcData(c.data)) blocks.push({ kind: "check", check: "ERC", call: c, key: `e:${c.id}` });
      if (c.name === "run_design_review" && isDesignReviewData(c.data)) blocks.push({ kind: "review", call: c, key: `rpt:${c.id}` });
    }
  }
  return blocks;
}

function ReasoningBlock({ content, streaming }: { content: string; streaming: boolean }) {
  // Historical reasoning starts collapsed; a live stream opens immediately.
  const [collapsed, setCollapsed] = useState(!streaming);
  useEffect(() => {
    if (streaming) setCollapsed(false);
  }, [streaming]);

  return (
    <div className="w-full overflow-hidden rounded-xl border border-violet-200/70 bg-violet-50/45 text-violet-950">
      <button
        type="button"
        onClick={() => setCollapsed((v) => !v)}
        className="flex w-full items-center gap-2 px-3.5 py-2 text-left text-[11.5px] font-medium text-violet-700 transition hover:bg-violet-50/80"
      >
        {streaming ? <Loader2 size={13} className="shrink-0 animate-spin" /> : <Brain size={13} className="shrink-0" />}
        <span>{streaming ? "正在思考…" : "思考过程"}</span>
        <span className="ml-auto text-[10px] font-normal text-violet-400">{content.length.toLocaleString()} 字符</span>
        {collapsed ? <ChevronDown size={13} /> : <ChevronUp size={13} />}
      </button>
      {!collapsed && (
        <div className="max-h-72 overflow-y-auto border-t border-violet-200/50 px-3.5 py-2.5 text-[12px] leading-relaxed text-slate-600">
          <Markdown content={content} />
          {streaming && <Caret />}
        </div>
      )}
    </div>
  );
}

/** mm:ss since the run started; only rendered while running so the interval is short-lived. */
function Elapsed() {
  const startedAt = useChat((s) => s.runStartedAt);
  const [, tick] = useState(0);
  useEffect(() => {
    const t = setInterval(() => tick((n) => n + 1), 1000);
    return () => clearInterval(t);
  }, []);
  if (!startedAt) return null;
  const s = Math.max(0, Math.floor((Date.now() - startedAt) / 1000));
  if (s < 5) return null;
  return (
    <span className="shrink-0 font-mono text-[10.5px] tabular-nums text-slate-400" title="本轮已运行">
      {String(Math.floor(s / 60)).padStart(2, "0")}:{String(s % 60).padStart(2, "0")}
    </span>
  );
}

function LiveStatusCard({ runningCall }: { runningCall?: ToolCall | null }) {
  const summary = runningCall ? summarizeArgs(runningCall.name, runningCall.args) : "";
  const label = runningCall ? toolLabel(runningCall.name) : "";
  const executingPlan = runningCall?.name === "submit_change_plan" && runningCall.plan?.some((a) => a.status !== "pending");

  if (runningCall) {
    return (
      <div className="animate-fade-in flex items-center gap-2.5 rounded-xl border border-brand-200/80 bg-brand-50/80 px-3.5 py-2.5 text-xs text-brand-700 shadow-xs">
        <Loader2 size={14} className="animate-spin text-brand-600 shrink-0" />
        <div className="flex min-w-0 flex-1 items-center gap-1.5 overflow-hidden">
          <span className="font-semibold text-brand-900">{executingPlan ? "正在执行已批准的计划:" : runningCall.name === "submit_change_plan" ? "等待确认:" : "正在执行:"}</span>
          <span className="font-medium text-brand-700">{label}</span>
          {!executingPlan && <span className="font-mono text-[11px] text-brand-500/90">({runningCall.name})</span>}
          {executingPlan && (
            <span className="text-[11.5px] text-slate-500">
              {runningCall.plan!.filter((a) => a.status === "done").length}/{runningCall.plan!.length} 项完成
            </span>
          )}
          {summary && !executingPlan && (
            <span className="truncate text-slate-500 text-[11.5px] ml-1" title={summary}>
              - {summary}
            </span>
          )}
        </div>
        <Elapsed />
        <span className="flex items-center gap-1 shrink-0 ml-1">
          <span className="typing-dot h-1.5 w-1.5 rounded-full bg-brand-500" />
          <span className="typing-dot h-1.5 w-1.5 rounded-full bg-brand-500" />
          <span className="typing-dot h-1.5 w-1.5 rounded-full bg-brand-500" />
        </span>
      </div>
    );
  }

  return (
    <div className="animate-fade-in flex items-center gap-2.5 rounded-xl border border-slate-200/80 bg-slate-50/90 px-3.5 py-2.5 text-xs text-slate-600 shadow-xs">
      <Sparkles size={14} className="text-brand-500 shrink-0 animate-pulse" />
      <span className="font-medium text-slate-700">AI 正在思考并规划下一步电路操作...</span>
      <span className="ml-auto flex items-center gap-2">
        <Elapsed />
        <span className="flex items-center gap-1 shrink-0">
          <span className="typing-dot h-1.5 w-1.5 rounded-full bg-brand-400" />
          <span className="typing-dot h-1.5 w-1.5 rounded-full bg-brand-400" />
          <span className="typing-dot h-1.5 w-1.5 rounded-full bg-brand-400" />
        </span>
      </span>
    </div>
  );
}

const AssistantTurn = memo(
  function AssistantTurn({
    turn,
    isLastAssistant,
    running,
  }: {
    turn: Turn;
    isLastAssistant?: boolean;
    running?: boolean;
  }) {
    const { todos, dismissTodos } = useChat();
    const [dismissed, setDismissed] = useState(false);
    const liveTodos = isLastAssistant ? todos : undefined;
    const turnTodos = useMemo(() => extractTodosFromTurn(turn), [turn]);
    const effectiveTodos = liveTodos && liveTodos.length > 0 ? liveTodos : turnTodos;

    const blocks = buildBlocks(turn);
    const streaming = turn.messages.some((m) => m.streaming);
    const fullText = turn.messages.map((m) => m.content).filter(Boolean).join("\n\n");

    const activeRunningCall = useMemo(() => {
      if (!isLastAssistant || !running) return null;
      for (let mi = turn.messages.length - 1; mi >= 0; mi--) {
        const calls = turn.messages[mi].tool_calls ?? [];
        for (let ci = calls.length - 1; ci >= 0; ci--) {
          if (calls[ci].status === "running") return calls[ci];
        }
      }
      return null;
    }, [turn.messages, isLastAssistant, running]);

    return (
      <div className="animate-fade-up flex items-start gap-2.5">
        <BotAvatar size={34} />
        <div className="flex min-w-0 max-w-[82%] flex-col items-start">
          <div className="flex w-full flex-col gap-2">
            {blocks.map((b) =>
              b.kind === "reasoning" ? (
                <ReasoningBlock key={b.key} content={b.content} streaming={b.streaming} />
              ) : b.kind === "text" ? (
                <div key={b.key} className="rounded-2xl rounded-tl-md border border-line bg-white px-4 py-3 text-ink shadow-[0_1px_2px_rgba(0,0,0,0.03)]">
                  <Markdown content={b.content} />
                  {b.streaming && <Caret />}
                </div>
              ) : b.kind === "tools" ? (
                <ToolCallCard key={b.key} calls={b.calls} />
              ) : b.kind === "edit" ? (
                <FileEditCard key={b.key} call={b.call} />
              ) : b.kind === "plan" ? (
                <ChangePlanCard key={b.key} call={b.call} />
              ) : b.kind === "review" ? (
                <DesignReviewCard key={b.key} data={b.call.data as never} />
              ) : (
                <DrcResultCard key={b.key} data={b.call.data as never} at={turn.at} check={b.check} />
              ),
            )}
            {!dismissed && effectiveTodos && effectiveTodos.length > 0 && (
              <TodoCard
                todos={effectiveTodos}
                onDismiss={() => {
                  setDismissed(true);
                  if (isLastAssistant) dismissTodos();
                }}
              />
            )}
            {isLastAssistant && running && !streaming && (
              <LiveStatusCard runningCall={activeRunningCall} />
            )}
          </div>
          {!streaming && !running && fullText && <Actions text={fullText} at={turn.at} />}
        </div>
      </div>
    );
  },
  (a, b) =>
    a.running === b.running &&
    a.isLastAssistant === b.isLastAssistant &&
    a.turn.at === b.turn.at &&
    sameMessages(a.turn.messages, b.turn.messages),
);

function Actions({ text, at }: { text: string; at?: string | null }) {
  const [copied, setCopied] = useState(false);
  const [vote, setVote] = useState<"up" | "down" | null>(null);
  const copy = async () => {
    try {
      await navigator.clipboard.writeText(text);
      setCopied(true);
      setTimeout(() => setCopied(false), 1500);
    } catch {
      /* ignore */
    }
  };
  const btn = "rounded p-1 transition-colors hover:bg-slate-100 hover:text-ink";
  return (
    <div className="animate-fade-in mt-1.5 flex items-center gap-2 text-slate-400">
      <button onClick={copy} className={btn} title="复制">
        {copied ? <Check size={13} className="text-emerald-500" /> : <Copy size={13} />}
      </button>
      <button onClick={() => setVote(vote === "up" ? null : "up")} className={clsx(btn, vote === "up" && "text-brand-600")} title="有帮助">
        <ThumbsUp size={13} />
      </button>
      <button onClick={() => setVote(vote === "down" ? null : "down")} className={clsx(btn, vote === "down" && "text-rose-500")} title="没帮助">
        <ThumbsDown size={13} />
      </button>
      <button className={btn} title="表情">
        <Smile size={13} />
      </button>
      {at && <span className="ml-1 text-[11px]">{timeHM(at)}</span>}
    </div>
  );
}

const STOP_TITLES: Record<string, string> = {
  recursion_limit: "本轮已达到步数上限，先暂停一下",
  run_timeout: "本轮运行时间较长，已先暂停",
  token_budget: "本轮消耗已达预算上限，先暂停一下",
  cancelled: "本轮运行已取消",
};

function RunErrorCard({ error }: { error: RunError }) {
  const { continueRun, retryLast, dismissRunError, running } = useChat();
  const recoverable = error.recoverable || error.code in STOP_TITLES;
  const cancelled = error.code === "cancelled";
  const tone = cancelled ? "slate" : recoverable ? "amber" : "rose";
  const frame = { slate: "border-slate-200 bg-slate-50", amber: "border-amber-200 bg-amber-50", rose: "border-rose-200 bg-rose-50" }[tone];
  const head = { slate: "text-slate-700", amber: "text-amber-800", rose: "text-rose-800" }[tone];
  const body = { slate: "text-slate-600", amber: "text-amber-900/80", rose: "text-rose-900/80" }[tone];
  const rule = { slate: "border-slate-200/70", amber: "border-amber-200/70", rose: "border-rose-200/70" }[tone];
  return (
    <div className={clsx("animate-fade-up ml-[46px] w-full max-w-[520px] overflow-hidden rounded-xl border", frame)}>
      <div className={clsx("flex items-center gap-2 px-3.5 py-2.5 text-[13px] font-semibold", head)}>
        {cancelled ? <Square size={14} fill="currentColor" /> : recoverable ? <AlertTriangle size={16} /> : <OctagonAlert size={16} />}
        <span className="flex-1">{STOP_TITLES[error.code] ?? "本轮运行出错"}</span>
        <button onClick={dismissRunError} className="rounded p-0.5 text-current/60 hover:bg-black/5" title="关闭">
          <X size={14} />
        </button>
      </div>
      <p className={clsx("px-3.5 pb-3 text-[12.5px] leading-relaxed", body)}>{error.message}</p>
      <div className={clsx("flex items-center gap-2 border-t px-3.5 py-2.5", rule)}>
        {recoverable ? (
          <button disabled={running} onClick={continueRun} className="flex items-center gap-1.5 rounded-md bg-brand-500 px-3 py-1.5 text-xs font-medium text-white transition-colors hover:bg-brand-600 disabled:opacity-50">
            <Play size={12} fill="currentColor" /> 继续执行
          </button>
        ) : (
          <button disabled={running} onClick={retryLast} className="flex items-center gap-1.5 rounded-md bg-brand-500 px-3 py-1.5 text-xs font-medium text-white transition-colors hover:bg-brand-600 disabled:opacity-50">
            <RefreshCw size={12} /> 重试上一条
          </button>
        )}
        <span className="text-[11px] text-slate-500">{recoverable ? "进度已保存，继续会从中断处接着做" : "也可以换个说法重新描述任务"}</span>
      </div>
    </div>
  );
}

function ReconnectBanner() {
  return (
    <div className="animate-fade-up ml-[46px] flex w-full max-w-[520px] items-center gap-2.5 rounded-xl border border-amber-200 bg-amber-50 px-3.5 py-2.5 text-[12px] text-amber-800 shadow-xs">
      <WifiOff size={14} className="shrink-0" />
      <span className="flex-1">与服务器的连接中断，正在恢复实时输出… 任务仍在后台继续执行。</span>
      <Loader2 size={13} className="shrink-0 animate-spin" />
    </div>
  );
}

function TodoCard({ todos, onDismiss }: { todos: Todo[]; onDismiss?: () => void }) {
  const [collapsed, setCollapsed] = useState(false);
  const done = todos.filter((t) => t.status === "completed").length;

  return (
    <div className="animate-fade-up w-full max-w-[490px] overflow-hidden rounded-2xl border border-line bg-white shadow-[0_1px_2px_rgba(0,0,0,0.03)] transition-all">
      <div className="flex items-center gap-2 border-b border-line bg-[#f8f9fc] px-3.5 py-2 text-[12px] font-medium text-slate-600">
        <ListChecks size={13} className="text-brand-500" />
        <span className="font-semibold text-slate-700">任务清单</span>
        <span className="text-[11px] text-slate-500">
          ({done}/{todos.length})
        </span>
        <span className="ml-auto flex items-center gap-2">
          <span className="h-1.5 w-16 overflow-hidden rounded-full bg-slate-200">
            <span
              className="block h-full rounded-full bg-emerald-500 transition-[width] duration-500"
              style={{ width: `${todos.length ? (done / todos.length) * 100 : 0}%` }}
            />
          </span>
          <button
            type="button"
            onClick={() => setCollapsed((v) => !v)}
            className="rounded p-0.5 text-slate-400 hover:bg-slate-200/60 hover:text-slate-600 transition-colors"
            title={collapsed ? "展开任务清单" : "收起任务清单"}
          >
            {collapsed ? <ChevronDown size={13} /> : <ChevronUp size={13} />}
          </button>
          {onDismiss && (
            <button
              type="button"
              onClick={onDismiss}
              className="rounded p-0.5 text-slate-400 hover:bg-slate-200/60 hover:text-rose-500 transition-colors"
              title="关闭清单"
            >
              <X size={13} />
            </button>
          )}
        </span>
      </div>
      {!collapsed && (
        <ol className="space-y-1.5 px-3.5 py-2.5 text-[12px]">
          {todos.map((t, i) => (
            <li key={i} className="flex items-start gap-2.5 transition-colors duration-300">
              <span
                className={clsx(
                  "mt-0.5 flex h-3.5 w-3.5 shrink-0 items-center justify-center rounded-full border transition-colors duration-300",
                  t.status === "completed"
                    ? "border-emerald-500 bg-emerald-500 text-white"
                    : t.status === "in_progress"
                    ? "border-brand-500 bg-brand-50"
                    : "border-slate-300 bg-white",
                )}
              >
                {t.status === "completed" && <Check size={9} strokeWidth={3} />}
                {t.status === "in_progress" && <span className="h-1.5 w-1.5 animate-pulse rounded-full bg-brand-500" />}
              </span>
              <span
                className={clsx(
                  "leading-relaxed transition-colors duration-300",
                  t.status === "completed" ? "text-slate-400 line-through" : "text-slate-700",
                )}
              >
                {t.content}
              </span>
            </li>
          ))}
        </ol>
      )}
    </div>
  );
}

function Typing() {
  return (
    <div className="animate-fade-up flex items-start gap-2.5">
      <BotAvatar size={34} />
      <div className="flex items-center gap-1 rounded-2xl rounded-tl-md border border-line bg-white px-4 py-3">
        <span className="typing-dot h-1.5 w-1.5 rounded-full bg-brand-400" />
        <span className="typing-dot h-1.5 w-1.5 rounded-full bg-brand-400" />
        <span className="typing-dot h-1.5 w-1.5 rounded-full bg-brand-400" />
      </div>
    </div>
  );
}

function Caret() {
  return <span className="stream-caret" />;
}

const SUGGESTIONS: { title: string; text: string; tone: string; Icon: typeof Sparkles }[] = [
  {
    title: "优化电源布局",
    text: "帮我优化一下电源模块的布局，电容尽量靠近芯片的电源引脚，并检查是否有 DRC 问题。",
    tone: "bg-brand-50 text-brand-600",
    Icon: Sparkles,
  },
  {
    title: "读板级信息",
    text: "读取当前 PCB 的板级信息，列出所有元件的位置。",
    tone: "bg-sky-50 text-sky-600",
    Icon: ListChecks,
  },
  {
    title: "DRC 检查",
    text: "运行 DRC 检查并解释每一条违规。",
    tone: "bg-emerald-50 text-emerald-600",
    Icon: Check,
  },
  {
    title: "原理图审查",
    text: "检查原理图有没有未连接的引脚或参考号冲突。",
    tone: "bg-violet-50 text-violet-600",
    Icon: Brain,
  },
];

function EmptyState({ onSuggestion }: { onSuggestion: (t: string) => void }) {
  return (
    <div className="chat-canvas animate-fade-in flex flex-1 flex-col items-center justify-center px-6 text-center">
      <span className="relative">
        <span className="absolute inset-0 -m-3 rounded-full bg-brand-200/40 blur-xl" />
        <BotAvatar size={56} />
      </span>
      <h3 className="mt-5 text-base font-semibold text-ink">你好，我是 KiCad AI 助手</h3>
      <p className="mt-1 max-w-md text-[13px] leading-relaxed text-slate-500">
        我可以读取和编辑你的 KiCad 原理图与 PCB：优化布局、运行 DRC、分析网表。所有修改都会先整理成计划，经你批准后才会执行。先在右侧选择或导入一个工程，然后告诉我要做什么。
      </p>
      <div className="mt-6 grid w-full max-w-xl grid-cols-1 gap-2.5 sm:grid-cols-2">
        {SUGGESTIONS.map((s, i) => (
          <button
            key={s.text}
            onClick={() => onSuggestion(s.text)}
            style={{ animationDelay: `${i * 60}ms` }}
            className="animate-fade-up group flex items-start gap-3 rounded-xl border border-line bg-white px-3.5 py-3 text-left transition-all duration-200 hover:-translate-y-px hover:border-brand-200 hover:shadow-[0_8px_20px_rgba(99,102,241,0.10)]"
          >
            <span className={clsx("mt-0.5 flex h-7 w-7 shrink-0 items-center justify-center rounded-lg transition-transform duration-200 group-hover:scale-105", s.tone)}>
              <s.Icon size={14} />
            </span>
            <span className="min-w-0">
              <span className="block text-[12.5px] font-semibold text-ink">{s.title}</span>
              <span className="mt-0.5 block text-[12px] leading-relaxed text-slate-500">{s.text}</span>
            </span>
          </button>
        ))}
      </div>
    </div>
  );
}
