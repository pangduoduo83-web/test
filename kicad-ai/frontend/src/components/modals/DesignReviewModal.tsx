import { Activity, AlertTriangle, CheckCircle2, Loader2, RefreshCw, ShieldCheck, WandSparkles, XCircle } from "lucide-react";
import { useCallback, useEffect, useMemo, useState } from "react";
import { Modal } from "@/components/ui/Modal";
import { api } from "@/lib/api";
import type { DesignReview, ReviewIssue } from "@/lib/types";
import { useChat } from "@/store/chat";
import { useProjects } from "@/store/projects";

const BREAKDOWN = [
  { key: "no_overlap", label: "无占位重叠", max: 35 },
  { key: "inside_outline", label: "板框安全区", max: 20 },
  { key: "connection_compactness", label: "连接紧凑度", max: 30 },
  { key: "grid_alignment", label: "栅格对齐", max: 15 },
] as const;

export function DesignReviewModal({ open, onClose }: { open: boolean; onClose: () => void }) {
  const { projects, activeProjectId, previewVersion } = useProjects();
  const project = projects.find((item) => item.id === activeProjectId);
  const send = useChat((state) => state.send);
  const running = useChat((state) => state.running);
  const [review, setReview] = useState<DesignReview | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [filter, setFilter] = useState<"all" | "drc" | "erc" | "placement">("all");

  const load = useCallback(async () => {
    if (!project) return;
    setLoading(true);
    setError(null);
    try {
      setReview(await api.designReview(project.id));
    } catch (reason) {
      setError((reason as Error).message);
    } finally {
      setLoading(false);
    }
  }, [project?.id]);

  useEffect(() => {
    if (open) void load();
  }, [open, load, previewVersion]);

  const issues = useMemo(
    () => (review?.issues ?? []).filter((issue) => filter === "all" || issue.source === filter),
    [review, filter],
  );
  const autoFixCount = review?.summary?.auto_fixable_count ?? 0;

  const requestFix = async () => {
    if (!project || running || autoFixCount === 0) return;
    onClose();
    await send(
      "请修复当前工程设计审查中所有标记为可自动处理的问题。"
      + "先调用 run_design_review 获取最新报告，只采用 suggested_actions 中仍然适用的动作；"
      + "然后调用 submit_change_plan 展示完整修复计划，等待我整批批准。"
      + "批准后执行并重新运行 run_design_review，对比修复前后的 ERC、DRC 和布局评分。",
    );
  };

  const score = review?.quality_score ?? 0;
  const scoreTone = score >= 90 ? "text-emerald-600" : score >= 75 ? "text-blue-600" : score >= 60 ? "text-amber-600" : "text-rose-600";

  return (
    <Modal
      open={open}
      onClose={onClose}
      title="设计质量审查"
      subtitle="统一运行 KiCad ERC、DRC 与布局评分；自动修复仍需整批计划确认。"
      width="max-w-5xl"
      scroll="inner"
      footer={
        <div className="flex w-full items-center justify-between gap-3">
          <button
            type="button"
            onClick={load}
            disabled={!project || loading}
            className="flex items-center gap-1.5 rounded-lg border border-line px-3 py-1.5 text-xs text-slate-600 hover:bg-slate-50 disabled:opacity-40"
          >
            <RefreshCw size={13} className={loading ? "animate-spin" : ""} /> 重新检查
          </button>
          <button
            type="button"
            onClick={requestFix}
            disabled={!project || running || loading || autoFixCount === 0}
            className="flex items-center gap-1.5 rounded-lg bg-brand-500 px-4 py-2 text-xs font-medium text-white hover:bg-brand-600 disabled:opacity-40"
          >
            <WandSparkles size={14} /> 生成整批修复计划（{autoFixCount} 项）
          </button>
        </div>
      }
    >
      {!project ? (
        <div className="py-12 text-center text-sm text-slate-400">请先选择一个工程</div>
      ) : loading && !review ? (
        <div className="flex min-h-72 items-center justify-center gap-2 text-sm text-slate-500">
          <Loader2 size={17} className="animate-spin" /> 正在运行 ERC、DRC 与布局评分…
        </div>
      ) : error ? (
        <div className="rounded-xl bg-rose-50 px-4 py-3 text-sm text-rose-600">{error}</div>
      ) : review?.success === false ? (
        <div className="rounded-xl bg-amber-50 px-4 py-3 text-sm text-amber-700">{review.error}</div>
      ) : review ? (
        <div className="space-y-4">
          <div className="grid grid-cols-1 gap-3 md:grid-cols-[180px_1fr]">
            <section className="flex flex-col items-center justify-center rounded-2xl border border-line bg-white p-5">
              <div className={`text-5xl font-semibold tabular-nums ${scoreTone}`}>{score.toFixed(0)}</div>
              <div className="mt-1 text-xs text-slate-400">综合质量分 / 100</div>
              <div className={`mt-3 rounded-full bg-slate-100 px-3 py-1 text-xs font-semibold ${scoreTone}`}>等级 {review.grade}</div>
            </section>
            <section className="rounded-2xl border border-line bg-white p-4">
              <h3 className="mb-3 flex items-center gap-2 text-sm font-semibold text-ink">
                <Activity size={15} className="text-brand-500" /> 布局评分
              </h3>
              {review.placement ? (
                <div className="space-y-3">
                  {BREAKDOWN.map((item) => {
                    const value = review.placement!.breakdown[item.key];
                    return (
                      <div key={item.key}>
                        <div className="mb-1 flex items-center justify-between text-[11.5px]">
                          <span className="text-slate-600">{item.label}</span>
                          <span className="font-mono text-slate-500">{value.toFixed(1)} / {item.max}</span>
                        </div>
                        <div className="h-1.5 overflow-hidden rounded-full bg-slate-100">
                          <div className="h-full rounded-full bg-brand-500" style={{ width: `${Math.max(0, Math.min(100, value / item.max * 100))}%` }} />
                        </div>
                      </div>
                    );
                  })}
                  <div className="flex flex-wrap gap-x-4 gap-y-1 border-t border-line pt-2 text-[10.5px] text-slate-400">
                    <span>飞线估算 {review.placement.estimated_ratsnest_length_mm.toFixed(1)} mm</span>
                    <span>平均连接跨度 {review.placement.average_connection_span_mm.toFixed(1)} mm</span>
                    {review.predicted_placement_score != null && autoFixCount > 0 && (
                      <span className="text-emerald-600">自动修复后布局预计 {review.predicted_placement_score.toFixed(0)} 分</span>
                    )}
                  </div>
                </div>
              ) : (
                <p className="text-xs text-slate-400">该工程没有 PCB，跳过布局评分。</p>
              )}
            </section>
          </div>

          <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
            <CheckSummary title="DRC" report={review.drc} />
            <CheckSummary title="ERC" report={review.erc} />
          </div>

          <section className="overflow-hidden rounded-2xl border border-line bg-white">
            <div className="flex flex-wrap items-center justify-between gap-2 border-b border-line px-4 py-3">
              <h3 className="text-sm font-semibold text-ink">问题清单（{issues.length}）</h3>
              <div className="flex rounded-lg bg-slate-100 p-0.5 text-[10.5px]">
                {(["all", "drc", "erc", "placement"] as const).map((value) => (
                  <button
                    key={value}
                    type="button"
                    onClick={() => setFilter(value)}
                    className={`rounded-md px-2 py-1 ${filter === value ? "bg-white font-medium text-ink shadow-sm" : "text-slate-500"}`}
                  >
                    {value === "all" ? "全部" : value.toUpperCase()}
                  </button>
                ))}
              </div>
            </div>
            <div className="max-h-80 overflow-y-auto p-3">
              {issues.length ? (
                <ul className="space-y-2">
                  {issues.map((issue) => <IssueRow key={issue.id} issue={issue} />)}
                </ul>
              ) : (
                <div className="flex items-center justify-center gap-2 py-10 text-sm text-emerald-600">
                  <CheckCircle2 size={17} /> 当前筛选下没有问题
                </div>
              )}
            </div>
          </section>
        </div>
      ) : null}
    </Modal>
  );
}

function CheckSummary({ title, report }: { title: string; report: DesignReview["drc"] }) {
  if (!report) {
    return <div className="rounded-xl border border-line bg-slate-50 p-3 text-xs text-slate-400">{title}：无对应设计文件</div>;
  }
  const passed = report.error_count === 0;
  return (
    <div className={`rounded-xl border p-3 ${passed ? "border-emerald-200 bg-emerald-50/60" : "border-rose-200 bg-rose-50/60"}`}>
      <div className="flex items-center gap-2">
        {passed ? <ShieldCheck size={16} className="text-emerald-600" /> : <XCircle size={16} className="text-rose-600" />}
        <span className="text-sm font-semibold text-ink">{title}</span>
        <span className="ml-auto text-[10.5px] text-slate-400">{report.engine}</span>
      </div>
      <p className="mt-1 text-[11.5px] text-slate-600">
        {report.error_count} 个错误 · {report.warning_count} 个警告
        {report.unconnected_count ? ` · ${report.unconnected_count} 个未连接` : ""}
      </p>
    </div>
  );
}

function IssueRow({ issue }: { issue: ReviewIssue }) {
  const isError = issue.severity === "error";
  return (
    <li className="rounded-xl border border-line bg-slate-50/70 px-3 py-2.5">
      <div className="flex items-start gap-2">
        {isError ? <XCircle size={14} className="mt-0.5 shrink-0 text-rose-500" /> : <AlertTriangle size={14} className="mt-0.5 shrink-0 text-amber-500" />}
        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-center gap-1.5">
            <span className="text-[12px] font-medium text-slate-800">{issue.description}</span>
            <span className="rounded bg-white px-1.5 py-0.5 font-mono text-[9.5px] text-slate-400">{issue.source.toUpperCase()} · {issue.type}</span>
            {issue.auto_fixable && <span className="rounded bg-emerald-100 px-1.5 py-0.5 text-[9.5px] font-medium text-emerald-700">可自动处理</span>}
          </div>
          {!!issue.items?.length && (
            <p className="mt-1 truncate text-[10.5px] text-slate-400">
              {issue.items.map((item) => item.description).join(" · ")}
            </p>
          )}
        </div>
      </div>
    </li>
  );
}
