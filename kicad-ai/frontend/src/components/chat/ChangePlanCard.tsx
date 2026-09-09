import clsx from "clsx";
import { AlertCircle, Check, ChevronDown, ChevronRight, ClipboardCheck, ClipboardList, Hourglass, Loader2, ShieldCheck, X } from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import { toolLabel } from "@/lib/format";
import type { ChangePlan, FileDiff, PlanActionProgress, ToolCall } from "@/lib/types";
import { planFromResult } from "@/store/chat";
import { DiffBody, DiffPreview } from "./FileEditCard";

type Phase = "awaiting" | "executing" | "done" | "partial" | "rejected" | "unapproved" | "error";

function phaseOf(call: ToolCall, plan: PlanActionProgress[]): Phase {
  const data = call.data as { approved?: boolean; pending_approval?: boolean; failed?: number; executed?: number; success?: boolean } | undefined;
  if (call.status === "running" || call.status === "pending") {
    return plan.some((a) => a.status !== "pending") ? "executing" : "awaiting";
  }
  if (data?.pending_approval) return "unapproved";
  if (data?.approved) return (data.failed ?? 0) > 0 ? "partial" : "done";
  if (call.status === "error") {
    const text = (call.result ?? "").toLowerCase();
    return /拒绝|reject|denied|not approved/.test(text) ? "rejected" : "error";
  }
  return "done";
}

const PHASE_META: Record<Phase, { label: string; tone: string; head: string; Icon: typeof ClipboardCheck }> = {
  awaiting: { label: "等待你的确认", tone: "border-amber-200/90", head: "bg-amber-50/80 text-amber-900", Icon: Hourglass },
  executing: { label: "系统正在执行", tone: "border-brand-200 ring-1 ring-brand-100", head: "bg-brand-50/80 text-brand-900", Icon: Loader2 },
  done: { label: "已全部执行", tone: "border-emerald-200/80", head: "bg-emerald-50/80 text-emerald-900", Icon: ClipboardCheck },
  partial: { label: "执行中断", tone: "border-rose-200/80", head: "bg-rose-50/80 text-rose-900", Icon: AlertCircle },
  rejected: { label: "已被拒绝", tone: "border-slate-200", head: "bg-slate-50 text-slate-600", Icon: X },
  unapproved: { label: "未获批准，未执行", tone: "border-slate-200", head: "bg-slate-50 text-slate-600", Icon: ClipboardList },
  error: { label: "提交失败", tone: "border-rose-200/80", head: "bg-rose-50/80 text-rose-900", Icon: AlertCircle },
};

export function ChangePlanCard({ call }: { call: ToolCall }) {
  const args = (call.args ?? {}) as unknown as Partial<ChangePlan>;
  const plan = useMemo(() => call.plan ?? planFromResult(call) ?? [], [call]);
  const phase = phaseOf(call, plan);
  const meta = PHASE_META[phase];
  const total = Math.max(plan.length, args.actions?.length ?? 0);
  const finished = plan.filter((a) => a.status === "done").length;
  const [open, setOpen] = useState(phase !== "done");
  useEffect(() => {
    if (phase === "executing" || phase === "awaiting") setOpen(true);
  }, [phase]);

  const data = call.data as { note?: string; error?: string } | undefined;
  const footnote =
    phase === "unapproved" || phase === "error"
      ? data?.error
      : phase === "rejected"
        ? call.result?.slice(0, 200)
        : phase === "partial"
          ? data?.note
          : null;

  return (
    <div className={clsx("mt-2 w-full max-w-[560px] overflow-hidden rounded-2xl border bg-white text-[12px] shadow-[0_1px_2px_rgba(0,0,0,0.03)] transition-colors duration-300", meta.tone)}>
      <button onClick={() => setOpen((v) => !v)} className={clsx("flex w-full items-center gap-2.5 px-3.5 py-2.5 text-left", meta.head)}>
        <meta.Icon size={15} className={clsx("shrink-0", phase === "executing" && "animate-spin")} />
        <span className="min-w-0 flex-1">
          <span className="block truncate text-[13px] font-semibold">{args.title || "设计修改计划"}</span>
          <span className="block text-[11px] opacity-75">
            {meta.label}
            {total > 0 && (phase === "executing" || phase === "done" || phase === "partial") && ` · ${finished}/${total} 项`}
            {total > 0 && phase === "awaiting" && ` · ${total} 项修改，见下方确认卡片`}
          </span>
        </span>
        {(phase === "executing" || phase === "done" || phase === "partial") && total > 0 && (
          <span className="h-1.5 w-20 shrink-0 overflow-hidden rounded-full bg-black/10">
            <span
              className={clsx("block h-full rounded-full transition-[width] duration-500", phase === "partial" ? "bg-rose-500" : "bg-emerald-500")}
              style={{ width: `${(finished / total) * 100}%` }}
            />
          </span>
        )}
        {open ? <ChevronDown size={14} className="shrink-0 opacity-60" /> : <ChevronRight size={14} className="shrink-0 opacity-60" />}
      </button>
      {phase === "executing" && <div className="progress-bar h-[2px] w-full bg-brand-100" />}

      <div className={clsx("collapse-grid", open && "is-open")}>
        <div className="min-h-0 overflow-hidden">
          {args.summary && <p className="px-3.5 pt-2.5 text-[12px] leading-relaxed text-slate-600">{args.summary}</p>}
          {!!args.scope?.length && (
            <div className="flex flex-wrap items-center gap-1 px-3.5 pt-2">
              <span className="mr-1 text-[10.5px] text-slate-400">影响对象</span>
              {args.scope.map((reference) => (
                <span key={reference} className="rounded bg-brand-50 px-1.5 py-0.5 font-mono text-[10.5px] text-brand-700">{reference}</span>
              ))}
            </div>
          )}
          <ol className="space-y-1.5 px-3.5 py-2.5">
            {(plan.length ? plan : (args.actions ?? []).map((a, index) => ({ index, tool: a.tool, summary: a.summary, status: "pending" as const }))).map((action) => (
              <ActionRow key={action.index} action={action} args={args.actions?.[action.index]?.args} phase={phase} />
            ))}
          </ol>
          {!!args.verification?.length && (
            <div className="mx-3.5 mb-3 flex items-start gap-2 rounded-lg bg-emerald-50/70 px-3 py-2 text-[11.5px] text-emerald-700">
              <ShieldCheck size={13} className="mt-0.5 shrink-0" />
              <span>执行后验证：{args.verification.map((item) => item.summary || toolLabel(item.tool)).join("、")}</span>
            </div>
          )}
          {footnote && (
            <div className={clsx("border-t px-3.5 py-2 text-[11.5px] leading-relaxed", phase === "rejected" || phase === "unapproved" ? "border-slate-100 text-slate-500" : "border-rose-100 bg-rose-50/60 text-rose-700")}>
              {footnote}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

function ActionRow({ action, args, phase }: { action: PlanActionProgress; args?: Record<string, unknown>; phase: Phase }) {
  const [open, setOpen] = useState(false);
  const diff = action.diff ?? null;
  const hasDetail = !!diff?.hunks?.length || (args && Object.keys(args).length > 0) || !!action.error;
  const skipped = phase === "partial" && action.status === "pending";
  return (
    <li className={clsx("overflow-hidden rounded-xl border transition-colors duration-300", action.status === "running" ? "border-brand-200 bg-brand-50/30" : action.status === "error" ? "border-rose-200 bg-rose-50/40" : "border-line bg-white")}>
      <button onClick={() => hasDetail && setOpen((v) => !v)} className={clsx("flex w-full items-center gap-2.5 px-3 py-2 text-left", hasDetail && "hover:bg-slate-50/70")}>
        <StatusDot status={skipped ? "skipped" : action.status} index={action.index} />
        <span className="min-w-0 flex-1">
          <span className={clsx("block truncate text-[12.5px] font-medium", skipped ? "text-slate-400" : "text-slate-800")}>{action.summary || toolLabel(action.tool)}</span>
          <span className="flex items-center gap-1.5 text-[10.5px] text-slate-400">
            <span className="font-mono">{action.tool}</span>
            {skipped && <span>· 未执行</span>}
            {action.status === "running" && <span className="text-brand-600">· 执行中…</span>}
          </span>
        </span>
        {diff && action.status === "done" && (diff.changed || diff.additions + diff.deletions > 0 ? (
          <span className="shrink-0 font-mono text-[11px] tabular-nums">
            <span className="text-emerald-600">+{diff.additions}</span> <span className="text-rose-500">−{diff.deletions}</span>
          </span>
        ) : (
          <span className="shrink-0 rounded bg-slate-100 px-1.5 py-[1px] text-[10px] text-slate-500">无变化</span>
        ))}
        {hasDetail && (open ? <ChevronDown size={13} className="shrink-0 text-slate-400" /> : <ChevronRight size={13} className="shrink-0 text-slate-400" />)}
      </button>
      {!open && diff && diff.hunks?.length > 0 && action.status === "done" && <DiffPreview diff={diff} onExpand={() => setOpen(true)} />}
      <div className={clsx("collapse-grid", open && hasDetail && "is-open")}>
        <div className="min-h-0 overflow-hidden">
          {action.error && <div className="border-t border-rose-100 bg-rose-50/60 px-3 py-2 text-[11.5px] text-rose-700">{action.error}</div>}
          {args && Object.keys(args).length > 0 && (
            <div className="border-t border-line px-3 py-2">
              <div className="mb-0.5 text-[10.5px] text-slate-400">执行参数</div>
              <pre className="max-h-40 overflow-auto rounded-md bg-slate-50 p-2 font-mono text-[10.5px] text-slate-600 whitespace-pre-wrap break-all">{JSON.stringify(args, null, 2)}</pre>
            </div>
          )}
          {diff && diff.hunks?.length > 0 && <DiffBody diff={diff as FileDiff} />}
        </div>
      </div>
    </li>
  );
}

function StatusDot({ status, index }: { status: PlanActionProgress["status"] | "skipped"; index: number }) {
  if (status === "done")
    return (
      <span className="animate-pop flex h-5 w-5 shrink-0 items-center justify-center rounded-full bg-emerald-500 text-white">
        <Check size={11} strokeWidth={3} />
      </span>
    );
  if (status === "error")
    return (
      <span className="animate-pop flex h-5 w-5 shrink-0 items-center justify-center rounded-full bg-rose-500 text-white">
        <X size={11} strokeWidth={3} />
      </span>
    );
  if (status === "running")
    return (
      <span className="flex h-5 w-5 shrink-0 items-center justify-center rounded-full bg-brand-500 text-white">
        <Loader2 size={11} className="animate-spin" />
      </span>
    );
  return (
    <span className={clsx("flex h-5 w-5 shrink-0 items-center justify-center rounded-full text-[10px] font-semibold", status === "skipped" ? "bg-slate-100 text-slate-400" : "bg-brand-50 text-brand-700")}>
      {index + 1}
    </span>
  );
}
