import { ClipboardCheck, ShieldAlert } from "lucide-react";
import { useState } from "react";
import { summarizeArgs, toolLabel } from "@/lib/format";
import type { ChangePlan, PendingInterrupt } from "@/lib/types";
import { useChat } from "@/store/chat";

export function InterruptCard({ interrupt }: { interrupt: PendingInterrupt }) {
  const resume = useChat((s) => s.resume);
  const running = useChat((s) => s.running);
  const [reason, setReason] = useState("");
  const requests = interrupt.value?.action_requests ?? [];
  if (!requests.length) return null;
  const planRequest = requests.find((request) => request.name === "submit_change_plan");
  const plan = planRequest?.args as unknown as ChangePlan | undefined;
  const isPlan = !!plan && Array.isArray(plan.actions);

  const decide = (type: "approve" | "reject") =>
    resume(requests.map(() => (type === "reject" ? { type, message: reason || "用户拒绝了该操作" } : { type })));

  return (
    <div className="ml-[46px] w-full max-w-[490px] overflow-hidden rounded-2xl border border-amber-200/90 bg-[#fffdfa] shadow-xs">
      <div className="flex items-center gap-2 border-b border-amber-200/80 bg-amber-50/80 px-3.5 py-2.5 text-[13px] font-semibold text-amber-900">
        {isPlan ? <ClipboardCheck size={16} className="shrink-0 text-brand-600" /> : <ShieldAlert size={16} className="text-amber-600 shrink-0" />}
        <span>{isPlan ? "整批修改计划待确认" : "需要你的确认：以下操作可能不可逆"}</span>
      </div>
      {isPlan ? (
        <div className="px-3.5 py-3">
          <h4 className="text-[13px] font-semibold text-ink">{plan.title || "设计修改计划"}</h4>
          {plan.summary && <p className="mt-1 text-[12px] leading-relaxed text-slate-600">{plan.summary}</p>}
          {!!plan.scope?.length && (
            <div className="mt-2 flex flex-wrap items-center gap-1">
              <span className="mr-1 text-[10.5px] text-slate-400">影响对象</span>
              {plan.scope.map((reference) => (
                <span key={reference} className="rounded bg-brand-50 px-1.5 py-0.5 font-mono text-[10.5px] text-brand-700">{reference}</span>
              ))}
            </div>
          )}
          <ol className="mt-3 space-y-2">
            {plan.actions.map((action, index) => (
              <li key={`${action.tool}-${index}`} className="rounded-xl border border-amber-100 bg-white p-3 shadow-2xs">
                <div className="flex items-start gap-2">
                  <span className="flex h-5 w-5 shrink-0 items-center justify-center rounded-full bg-brand-50 text-[10px] font-semibold text-brand-700">{index + 1}</span>
                  <div className="min-w-0 flex-1">
                    <div className="flex flex-wrap items-center gap-1.5">
                      <span className="text-[12.5px] font-medium text-slate-800">{action.summary || toolLabel(action.tool)}</span>
                      <span className="rounded bg-slate-100 px-1.5 py-0.5 font-mono text-[10px] text-slate-500">{action.tool}</span>
                    </div>
                    {action.reason && <p className="mt-1 text-[11.5px] leading-relaxed text-slate-500">{action.reason}</p>}
                    <details className="mt-1.5">
                      <summary className="cursor-pointer text-[10.5px] text-slate-400 hover:text-slate-600">查看执行参数</summary>
                      <pre className="mt-1.5 max-h-40 overflow-auto rounded-lg bg-slate-50 p-2 font-mono text-[10.5px] text-slate-600 whitespace-pre-wrap break-all">
                        {JSON.stringify(action.args, null, 2)}
                      </pre>
                    </details>
                  </div>
                </div>
              </li>
            ))}
          </ol>
          {!!plan.verification?.length && (
            <div className="mt-3 rounded-lg bg-emerald-50/70 px-3 py-2 text-[11.5px] text-emerald-700">
              执行后验证：{plan.verification.map((item) => item.summary || toolLabel(item.tool)).join("、")}
            </div>
          )}
          <p className="mt-2.5 text-[11px] leading-relaxed text-slate-400">
            批准后由系统按顺序自动执行以上 {plan.actions.length} 项（每步修改前自动快照，可在「会话快照」中回滚），助手只负责复查与汇报。
          </p>
        </div>
      ) : (
        <ul className="space-y-2.5 px-3.5 py-3">
          {requests.map((r, i) => {
          const isGenericDesc = !r.description || r.description.startsWith("Tool execution requires approval");
          const friendlySummary = summarizeArgs(r.name, r.args);
          return (
            <li key={i} className="overflow-hidden rounded-xl border border-amber-100 bg-white p-3 text-[12.5px] shadow-2xs">
              <div className="flex items-center justify-between gap-2">
                <div className="flex items-center gap-2 min-w-0">
                  <span className="font-mono text-[12.5px] font-semibold text-brand-700 truncate">{r.name}</span>
                  <span className="rounded bg-slate-100 px-1.5 py-0.5 text-[11px] text-slate-500 shrink-0">{toolLabel(r.name)}</span>
                </div>
              </div>
              {friendlySummary && <p className="mt-1 text-[12px] font-medium text-slate-700 break-all">{friendlySummary}</p>}
              {!isGenericDesc && r.description && (
                <p className="mt-1 text-[12px] text-slate-600 break-all leading-relaxed">{r.description}</p>
              )}
              {r.args && Object.keys(r.args).length > 0 && (
                <pre className="mt-2 max-h-40 overflow-auto rounded-lg border border-slate-100 bg-slate-50/90 p-2.5 font-mono text-[11px] text-slate-600 whitespace-pre-wrap break-all">
                  {JSON.stringify(r.args, null, 2)}
                </pre>
              )}
            </li>
          );
          })}
        </ul>
      )}
      <div className="flex items-center gap-2 border-t border-amber-200/60 bg-amber-50/40 px-3.5 py-2.5">
        <input
          value={reason}
          onChange={(e) => setReason(e.target.value)}
          placeholder="拒绝原因（可选）"
          className="min-w-0 flex-1 rounded-lg border border-amber-200/80 bg-white px-3 py-1.5 text-xs text-ink outline-none placeholder:text-slate-400 focus:border-amber-400 focus:ring-1 focus:ring-amber-200"
        />
        <button
          disabled={running}
          onClick={() => decide("reject")}
          className="rounded-lg border border-slate-200 bg-white px-3 py-1.5 text-xs font-medium text-slate-600 hover:bg-slate-50 active:scale-95 disabled:opacity-50 transition"
        >
          拒绝
        </button>
        <button
          disabled={running}
          onClick={() => decide("approve")}
          className="rounded-lg bg-brand-500 px-3.5 py-1.5 text-xs font-medium text-white shadow-xs hover:bg-brand-600 active:scale-95 disabled:opacity-50 transition"
        >
          {isPlan ? `批准并执行（${plan.actions.length} 项）` : "批准执行"}
        </button>
      </div>
    </div>
  );
}
