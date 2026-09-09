import { AlertTriangle, CheckCircle2, Download, Factory, Loader2, RefreshCw, XCircle } from "lucide-react";
import { useCallback, useEffect, useState } from "react";
import { Modal } from "@/components/ui/Modal";
import { api, downloadAuthenticated } from "@/lib/api";
import type { BomReport } from "@/lib/types";
import { useProjects } from "@/store/projects";

export function BomReportModal({ open, onClose }: { open: boolean; onClose: () => void }) {
  const { projects, activeProjectId, previewVersion } = useProjects();
  const project = projects.find((item) => item.id === activeProjectId);
  const [report, setReport] = useState<BomReport | null>(null);
  const [loading, setLoading] = useState(false);
  const [downloading, setDownloading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    if (!project) return;
    setLoading(true);
    setError(null);
    try {
      setReport(await api.bomReport(project.id));
    } catch (reason) {
      setError((reason as Error).message);
    } finally {
      setLoading(false);
    }
  }, [project?.id]);

  useEffect(() => {
    if (open) void load();
  }, [open, load, previewVersion]);

  const exportCsv = async () => {
    if (!project) return;
    setDownloading(true);
    try {
      await downloadAuthenticated(api.bomCsvUrl(project.id), `${project.name}-bom.csv`);
    } catch (reason) {
      setError((reason as Error).message);
    } finally {
      setDownloading(false);
    }
  };

  const summary = report?.summary;
  const tone = report?.status === "errors" ? "rose" : report?.status === "warnings" ? "amber" : "emerald";

  return (
    <Modal
      open={open}
      onClose={onClose}
      title="BOM 与可制造性 (DFM)"
      subtitle="按值 + 封装归并物料，并检查缺失封装/值、重复位号、封装库、超小封装与装配工艺。"
      width="max-w-5xl"
      scroll="inner"
      footer={
        <div className="flex w-full items-center justify-between gap-3">
          <button type="button" onClick={load} disabled={!project || loading} className="flex items-center gap-1.5 rounded-lg border border-line px-3 py-1.5 text-xs text-slate-600 hover:bg-slate-50 disabled:opacity-40">
            <RefreshCw size={13} className={loading ? "animate-spin" : ""} /> 重新分析
          </button>
          <button type="button" onClick={exportCsv} disabled={!report?.success || downloading} className="flex items-center gap-1.5 rounded-lg bg-brand-500 px-4 py-2 text-xs font-medium text-white hover:bg-brand-600 disabled:opacity-40">
            {downloading ? <Loader2 size={14} className="animate-spin" /> : <Download size={14} />} 导出 BOM (CSV)
          </button>
        </div>
      }
    >
      {!project ? (
        <div className="py-12 text-center text-sm text-slate-400">请先选择一个工程</div>
      ) : loading && !report ? (
        <div className="flex min-h-64 items-center justify-center gap-2 text-sm text-slate-500"><Loader2 size={17} className="animate-spin" /> 正在分析物料…</div>
      ) : error ? (
        <div className="rounded-xl bg-rose-50 px-4 py-3 text-sm text-rose-600">{error}</div>
      ) : report && !report.success ? (
        <div className="rounded-xl bg-amber-50 px-4 py-3 text-sm text-amber-700">{report.error}</div>
      ) : report && summary ? (
        <div className="space-y-4">
          <section className={`flex items-center gap-4 rounded-2xl border p-4 ${tone === "emerald" ? "border-emerald-200 bg-emerald-50/70" : tone === "amber" ? "border-amber-200 bg-amber-50/70" : "border-rose-200 bg-rose-50/70"}`}>
            <span className={`flex h-11 w-11 shrink-0 items-center justify-center rounded-full ${tone === "emerald" ? "bg-emerald-500 text-white" : tone === "amber" ? "bg-amber-100 text-amber-700" : "bg-rose-100 text-rose-600"}`}>
              {tone === "emerald" ? <CheckCircle2 size={22} /> : tone === "amber" ? <AlertTriangle size={21} /> : <XCircle size={22} />}
            </span>
            <div className="min-w-0 flex-1">
              <h3 className="text-sm font-semibold text-ink">
                {summary.line_count} 种物料 · {summary.component_count} 个元件
              </h3>
              <p className="mt-1 text-[11.5px] text-slate-600">
                DFM：{summary.error_count} 个错误 · {summary.warning_count} 个警告
                {report.library_index_available === false && " · 未检测到 KiCad 封装库索引，跳过封装存在性校验"}
              </p>
            </div>
            {report.assembly && "smd_count" in report.assembly && (
              <div className="hidden text-right text-[11px] text-slate-500 sm:block">
                <div>贴片 {String(report.assembly.smd_count)} · 插件 {String(report.assembly.tht_count)}</div>
                <div>顶层 {String(report.assembly.top_side_count)} · 底层 {String(report.assembly.bottom_side_count)}</div>
              </div>
            )}
          </section>

          {!!report.issues?.length && (
            <section className="overflow-hidden rounded-2xl border border-line bg-white">
              <div className="flex items-center gap-2 border-b border-line px-4 py-2.5 text-sm font-semibold text-ink"><Factory size={15} className="text-brand-500" /> 可制造性问题（{report.issues.length}）</div>
              <ul className="max-h-52 divide-y divide-line overflow-y-auto">
                {report.issues.map((issue, index) => (
                  <li key={`${issue.type}-${issue.reference}-${index}`} className="flex items-start gap-2 px-4 py-2 text-[11.5px]">
                    {issue.severity === "error" ? <XCircle size={13} className="mt-0.5 shrink-0 text-rose-500" /> : <AlertTriangle size={13} className={`mt-0.5 shrink-0 ${issue.severity === "warning" ? "text-amber-500" : "text-slate-400"}`} />}
                    <span className="text-slate-700">{issue.description}</span>
                    <span className="ml-auto shrink-0 font-mono text-[9.5px] text-slate-400">{issue.type}</span>
                  </li>
                ))}
              </ul>
            </section>
          )}

          <section className="overflow-hidden rounded-2xl border border-line bg-white">
            <table className="w-full text-[11.5px]">
              <thead className="bg-slate-50 text-left text-[10.5px] uppercase tracking-wide text-slate-500">
                <tr>
                  <th className="px-3 py-2">#</th>
                  <th className="px-3 py-2">数量</th>
                  <th className="px-3 py-2">位号</th>
                  <th className="px-3 py-2">值</th>
                  <th className="px-3 py-2">封装</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-line">
                {report.lines?.map((line, index) => (
                  <tr key={`${line.value}-${line.footprint}`} className="hover:bg-slate-50/60">
                    <td className="px-3 py-2 text-slate-400">{index + 1}</td>
                    <td className="px-3 py-2 font-semibold text-ink">{line.quantity}</td>
                    <td className="px-3 py-2 font-mono text-[10.5px] text-brand-700">{line.references.join(", ")}</td>
                    <td className="px-3 py-2 text-slate-700">{line.value}</td>
                    <td className="max-w-[260px] truncate px-3 py-2 font-mono text-[10.5px] text-slate-500" title={line.footprint}>{line.footprint || <span className="text-rose-500">未分配</span>}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </section>
        </div>
      ) : null}
    </Modal>
  );
}
