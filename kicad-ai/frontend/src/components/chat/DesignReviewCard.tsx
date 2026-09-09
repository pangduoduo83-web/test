import { Activity, CheckCircle2, ChevronDown, ChevronRight, WandSparkles, XCircle } from "lucide-react";
import { useState } from "react";
import type { DesignReview } from "@/lib/types";

const SCORE_LABELS: Record<string, string> = {
  no_overlap: "无重叠",
  inside_outline: "板框安全",
  connection_compactness: "连接紧凑",
  grid_alignment: "栅格对齐",
};

export function isDesignReviewData(value: unknown): value is DesignReview {
  return !!value && typeof value === "object" && "quality_score" in value && "issues" in value;
}

export function DesignReviewCard({ data }: { data: DesignReview }) {
  const [open, setOpen] = useState(false);
  const errors = data.summary?.error_count ?? 0;
  const warnings = data.summary?.warning_count ?? 0;
  const fixes = data.summary?.auto_fixable_count ?? 0;
  const score = data.quality_score ?? 0;
  const passed = errors === 0;

  return (
    <div className={`mt-2 w-full max-w-[490px] overflow-hidden rounded-2xl border ${passed ? "border-emerald-200 bg-emerald-50/70" : "border-rose-200 bg-rose-50/70"}`}>
      <button type="button" onClick={() => setOpen((value) => !value)} className="flex w-full items-center gap-3 px-3.5 py-3 text-left">
        <span className={`flex h-9 w-9 shrink-0 items-center justify-center rounded-full ${passed ? "bg-emerald-500 text-white" : "bg-rose-100 text-rose-600"}`}>
          {passed ? <CheckCircle2 size={18} /> : <XCircle size={18} />}
        </span>
        <div className="min-w-0 flex-1">
          <div className="text-[13px] font-semibold text-ink">设计质量审查 · {score.toFixed(0)} 分（{data.grade}）</div>
          <div className="mt-0.5 text-[11px] text-slate-500">
            ERC/DRC：{errors} 错误 · {warnings} 警告
            {fixes > 0 && <span className="ml-2 text-emerald-700">可自动处理 {fixes} 项</span>}
          </div>
        </div>
        {open ? <ChevronDown size={14} /> : <ChevronRight size={14} />}
      </button>
      {open && (
        <div className="space-y-2 border-t border-black/5 px-3.5 py-3">
          {data.placement && (
            <div className="grid grid-cols-2 gap-1.5 text-[10.5px]">
              {Object.entries(data.placement.breakdown).map(([key, value]) => (
                <div key={key} className="flex items-center justify-between rounded bg-white/70 px-2 py-1.5">
                  <span className="text-slate-500">{SCORE_LABELS[key] ?? key}</span>
                  <span className="font-mono text-slate-700">{Number(value).toFixed(1)}</span>
                </div>
              ))}
            </div>
          )}
          {(data.issues ?? []).slice(0, 8).map((issue) => (
            <div key={issue.id} className="flex items-start gap-2 rounded bg-white/70 px-2 py-1.5 text-[10.5px]">
              <Activity size={11} className={`mt-0.5 shrink-0 ${issue.severity === "error" ? "text-rose-500" : "text-amber-500"}`} />
              <span className="min-w-0 flex-1 text-slate-600">{issue.description}</span>
              {issue.auto_fixable && <WandSparkles size={11} className="shrink-0 text-emerald-600" />}
            </div>
          ))}
          {(data.issues?.length ?? 0) > 8 && <p className="text-center text-[10.5px] text-slate-400">其余 {(data.issues?.length ?? 0) - 8} 项请在右侧“设计审查”查看</p>}
        </div>
      )}
    </div>
  );
}
