import clsx from "clsx";
import { Bot, Check, Circle, FileText, Loader2, Pencil, Search, ShieldCheck, Wrench, X } from "lucide-react";
import { useState } from "react";
import { summarizeArgs, toolLabel } from "@/lib/format";
import type { ToolCall } from "@/lib/types";
import type { SubStep } from "@/store/chat";

type Call = ToolCall & { substeps?: SubStep[] };

export function ToolCallCard({ calls }: { calls: Call[] }) {
  const done = calls.filter((c) => c.status === "done" || c.status === "error").length;
  const running = calls.some((c) => c.status === "running");
  const title = running ? `正在调用工具 (${done}/${calls.length})` : `工具调用完成 (${done}/${calls.length})`;
  return (
    <div className={clsx("mt-2 w-full max-w-[490px] overflow-hidden rounded-2xl border bg-white shadow-[0_1px_2px_rgba(0,0,0,0.03)] transition-colors duration-300", running ? "border-brand-200/80 ring-1 ring-brand-100" : "border-line")}>
      <div className="flex items-center gap-2 border-b border-line bg-[#f8f9fc] px-3.5 py-2 text-[12px] font-medium text-slate-600">
        {running ? <Loader2 size={13} className="animate-spin text-brand-500" /> : <Wrench size={13} className="text-brand-500" />}
        <span className="transition-opacity duration-200">{title}</span>
      </div>
      <ul className="divide-y divide-line/70">
        {calls.map((c) => (
          <ToolRow key={c.id} call={c} />
        ))}
      </ul>
    </div>
  );
}

function rowIcon(call: Call) {
  const name = call.name;
  if (name === "task") return Bot;
  if (/drc|rule|validate/.test(name)) return ShieldCheck;
  if (call.kind === "file_mutation" || /^(set_|move_|align|distribute|flip|add_|remove_|delete_|place_|rotate|assign|rename|clear_|connect|pcb_route|pcb_add|pcb_delete|restore|update|write|edit)/.test(name)) return Pencil;
  if (call.kind === "query" || /^(get_|list_|find_|search|extract|score|check|analyze|identify|read|ls|glob|grep|suggest)/.test(name)) return Search;
  return FileText;
}

interface TruncationInfo {
  total_chars?: number;
  full_result?: string;
  fields?: Record<string, { shown: number; total: number }>;
}

function truncationOf(call: Call): TruncationInfo | null {
  const d = call.data as { _truncated?: TruncationInfo } | undefined;
  return d && typeof d === "object" && d._truncated ? d._truncated : null;
}

function ToolRow({ call }: { call: Call }) {
  const [open, setOpen] = useState(false);
  const hasDetail = !!call.result || (call.args && Object.keys(call.args).length > 0) || (call.substeps?.length ?? 0) > 0;
  const summary = summarizeArgs(call.name, call.args);
  const Icon = rowIcon(call);
  const truncated = truncationOf(call);
  const truncatedField = truncated?.fields ? Object.entries(truncated.fields)[0] : undefined;
  return (
    <li className="animate-fade-in">
      <button onClick={() => hasDetail && setOpen((v) => !v)} className={clsx("flex w-full items-center gap-2.5 px-3 py-2 text-left transition-colors", hasDetail && "hover:bg-slate-50/80")}>
        <span className={clsx("flex h-6 w-6 shrink-0 items-center justify-center rounded-md transition-colors duration-300", call.status === "running" ? "bg-brand-50 text-brand-500" : "bg-slate-100 text-slate-500")}>
          <Icon size={13} />
        </span>
        <span className={clsx("truncate text-[12px] font-medium transition-colors duration-300", call.status === "running" ? "text-brand-600 font-semibold" : "text-slate-700")}>
          {toolLabel(call.name)}
        </span>
        <span className="font-mono text-[11px] text-slate-400">({call.name})</span>
        {summary && <span className="min-w-0 flex-1 truncate text-[11.5px] text-slate-500">{summary}</span>}
        {!summary && <span className="flex-1" />}
        {truncated && (
          <span className="shrink-0 rounded bg-amber-50 px-1.5 py-[1px] text-[10px] font-medium text-amber-700" title="结果过大，已按预算裁剪；完整结果保存在工作区">
            {truncatedField ? `已截断 ${truncatedField[1].shown}/${truncatedField[1].total}` : "已截断"}
          </span>
        )}
        <StatusIcon status={call.status} />
      </button>
      <div className={clsx("collapse-grid", open && "is-open")}>
        <div className="min-h-0 overflow-hidden">
          <div className="space-y-2 bg-slate-50/60 px-3 pb-3 pt-1 text-[11px]">
            {truncated && (
              <div className="rounded-md border border-amber-100 bg-amber-50/70 px-2 py-1.5 text-[11px] text-amber-800">
                结果过大（{(truncated.total_chars ?? 0).toLocaleString()} 字符），已裁剪后交给模型
                {truncated.full_result && (
                  <>
                    ；完整结果已保存到 <span className="font-mono">{truncated.full_result}</span>
                  </>
                )}
                。
              </div>
            )}
            {call.args && Object.keys(call.args).length > 0 && (
              <div>
                <div className="mb-0.5 text-slate-400">参数</div>
                <pre className="max-h-40 overflow-auto rounded-md bg-white p-2 font-mono text-[11px] text-slate-600">{JSON.stringify(call.args, null, 2)}</pre>
              </div>
            )}
            {call.substeps && call.substeps.length > 0 && (
              <div>
                <div className="mb-0.5 text-slate-400">子代理步骤</div>
                <ul className="space-y-0.5">
                  {call.substeps.map((s) => (
                    <li key={s.id} className="flex items-center gap-2 font-mono text-slate-600">
                      <StatusIcon status={s.status} small /> {s.name}
                    </li>
                  ))}
                </ul>
              </div>
            )}
            {call.result && (
              <div>
                <div className="mb-0.5 text-slate-400">结果</div>
                <pre className="max-h-56 overflow-auto rounded-md bg-white p-2 font-mono text-[11px] text-slate-600 whitespace-pre-wrap break-all">{pretty(call.result)}</pre>
              </div>
            )}
          </div>
        </div>
      </div>
    </li>
  );
}

function pretty(text: string): string {
  try {
    return JSON.stringify(JSON.parse(text), null, 2);
  } catch {
    return text;
  }
}

export function StatusIcon({ status, small }: { status: ToolCall["status"]; small?: boolean }) {
  const size = small ? 11 : 13;
  if (status === "done")
    return (
      <span className="animate-pop flex h-4 w-4 shrink-0 items-center justify-center rounded-full bg-emerald-500 text-white shadow-xs">
        <Check size={size - 3} strokeWidth={3} />
      </span>
    );
  if (status === "error")
    return (
      <span className="animate-pop flex h-4 w-4 shrink-0 items-center justify-center rounded-full bg-rose-500 text-white shadow-xs">
        <X size={size - 3} strokeWidth={3} />
      </span>
    );
  if (status === "running")
    return (
      <span className="flex shrink-0 items-center gap-1 font-medium text-[11px] text-brand-600">
        执行中… <span className="h-1.5 w-1.5 rounded-full bg-brand-500 animate-pulse" />
      </span>
    );
  return <Circle size={size + 1} className="shrink-0 text-slate-300" strokeWidth={1.8} />;
}
