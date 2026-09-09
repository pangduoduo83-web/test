import { ArrowRightLeft, CheckCircle2, Loader2, RefreshCw, TriangleAlert, WandSparkles } from "lucide-react";
import { useCallback, useEffect, useState } from "react";
import { Modal } from "@/components/ui/Modal";
import { api } from "@/lib/api";
import type { EcoReport } from "@/lib/types";
import { useChat } from "@/store/chat";
import { useProjects } from "@/store/projects";

export function EcoReportModal({ open, onClose }: { open: boolean; onClose: () => void }) {
  const { projects, activeProjectId, previewVersion } = useProjects();
  const project = projects.find((item) => item.id === activeProjectId);
  const send = useChat((state) => state.send);
  const running = useChat((state) => state.running);
  const [report, setReport] = useState<EcoReport | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    if (!project) return;
    setLoading(true);
    setError(null);
    try {
      setReport(await api.ecoReport(project.id));
    } catch (reason) {
      setError((reason as Error).message);
    } finally {
      setLoading(false);
    }
  }, [project?.id]);

  useEffect(() => {
    if (open) void load();
  }, [open, load, previewVersion]);

  const requestSync = async () => {
    if (!project || running || !report?.suggested_actions.length) return;
    onClose();
    await send(
      "请同步当前工程的原理图与 PCB。先调用 run_eco_check 获取最新差异，"
      + "仅采用报告中的 suggested_actions，并通过 submit_change_plan 展示完整 ECO 同步计划。"
      + "等待我整批批准后再执行；完成后重新运行 run_eco_check 和 run_drc_check，"
      + "保留 PCB 多余封装和封装型号差异供人工决定。",
    );
  };

  const synchronized = report?.status === "synchronized";
  return (
    <Modal
      open={open}
      onClose={onClose}
      title="原理图 ↔ PCB 一致性（ECO）"
      subtitle="比较器件、值、封装和焊盘网络；不会自动删除 PCB 独有对象或替换封装型号。"
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
            <RefreshCw size={13} className={loading ? "animate-spin" : ""} /> 重新比较
          </button>
          <button
            type="button"
            onClick={requestSync}
            disabled={!report?.suggested_actions.length || running || loading}
            className="flex items-center gap-1.5 rounded-lg bg-brand-500 px-4 py-2 text-xs font-medium text-white hover:bg-brand-600 disabled:opacity-40"
          >
            <WandSparkles size={14} /> 生成 ECO 同步计划
          </button>
        </div>
      }
    >
      {!project ? (
        <div className="py-12 text-center text-sm text-slate-400">请先选择一个同时包含原理图和 PCB 的工程</div>
      ) : loading && !report ? (
        <div className="flex min-h-72 items-center justify-center gap-2 text-sm text-slate-500">
          <Loader2 size={17} className="animate-spin" /> 正在比较原理图和 PCB…
        </div>
      ) : error ? (
        <div className="rounded-xl bg-rose-50 px-4 py-3 text-sm text-rose-600">{error}</div>
      ) : report ? (
        <div className="space-y-4">
          <section className={`flex items-center gap-4 rounded-2xl border p-4 ${synchronized ? "border-emerald-200 bg-emerald-50/70" : "border-amber-200 bg-amber-50/70"}`}>
            <span className={`flex h-11 w-11 shrink-0 items-center justify-center rounded-full ${synchronized ? "bg-emerald-500 text-white" : "bg-amber-100 text-amber-700"}`}>
              {synchronized ? <CheckCircle2 size={22} /> : <ArrowRightLeft size={21} />}
            </span>
            <div className="min-w-0 flex-1">
              <h3 className="text-sm font-semibold text-ink">{synchronized ? "原理图与 PCB 已同步" : `发现 ${report.summary.drift_count} 类/项差异`}</h3>
              <p className="mt-1 text-[11.5px] text-slate-600">
                原理图 {report.summary.schematic_components} 个器件 · PCB {report.summary.pcb_footprints} 个封装 ·
                可自动同步 {report.summary.auto_sync_count} 批 · 需人工判断 {report.summary.manual_count} 项
              </p>
            </div>
          </section>

          <div className="grid grid-cols-1 gap-3 lg:grid-cols-2">
            <DiffSection
              title="原理图有、PCB 缺失"
              items={report.missing_on_pcb.map((item) => ({
                key: item.reference,
                title: `${item.reference} · ${item.value}`,
                detail: item.footprint || "未分配封装",
                badge: item.syncable ? "可同步" : "需先分配封装",
              }))}
            />
            <DiffSection
              title="PCB 独有封装"
              items={report.extra_on_pcb.map((item) => ({
                key: item.reference,
                title: `${item.reference} · ${item.value}`,
                detail: item.footprint,
                badge: "保留/人工确认",
              }))}
            />
            <DiffSection
              title="元件值差异"
              items={report.value_mismatches.map((item) => ({
                key: item.reference,
                title: item.reference,
                detail: `原理图 ${item.schematic} → PCB ${item.pcb}`,
                badge: "可同步",
              }))}
            />
            <DiffSection
              title="封装型号差异"
              items={report.footprint_mismatches.map((item) => ({
                key: item.reference,
                title: item.reference,
                detail: `原理图 ${item.schematic} / PCB ${item.pcb}`,
                badge: "人工确认",
              }))}
            />
          </div>

          <section className="overflow-hidden rounded-2xl border border-line bg-white">
            <div className="border-b border-line px-4 py-3 text-sm font-semibold text-ink">网络差异（{report.net_mismatches.length}）</div>
            {report.net_mismatches.length ? (
              <ul className="max-h-64 divide-y divide-line overflow-y-auto">
                {report.net_mismatches.map((item) => (
                  <li key={item.net} className="px-4 py-2.5">
                    <div className="font-mono text-[12px] font-medium text-brand-700">{item.net}</div>
                    {!!item.missing_on_pcb.length && <p className="mt-1 text-[10.5px] text-emerald-700">PCB 缺少：{item.missing_on_pcb.join(", ")}</p>}
                    {!!item.extra_on_pcb.length && <p className="mt-1 text-[10.5px] text-amber-700">PCB 多出：{item.extra_on_pcb.join(", ")}</p>}
                  </li>
                ))}
              </ul>
            ) : (
              <div className="py-8 text-center text-xs text-emerald-600">已命名网络的焊盘连接一致</div>
            )}
          </section>

          {!!report.notes.length && (
            <div className="rounded-xl bg-slate-50 px-4 py-3">
              {report.notes.map((note) => (
                <p key={note} className="flex items-start gap-2 py-0.5 text-[10.5px] leading-relaxed text-slate-500">
                  <TriangleAlert size={11} className="mt-0.5 shrink-0 text-amber-500" /> {note}
                </p>
              ))}
            </div>
          )}
        </div>
      ) : null}
    </Modal>
  );
}

function DiffSection({
  title,
  items,
}: {
  title: string;
  items: { key: string; title: string; detail: string; badge: string }[];
}) {
  return (
    <section className="overflow-hidden rounded-2xl border border-line bg-white">
      <div className="border-b border-line px-4 py-2.5 text-[12.5px] font-semibold text-ink">{title}（{items.length}）</div>
      {items.length ? (
        <ul className="max-h-48 divide-y divide-line overflow-y-auto">
          {items.map((item) => (
            <li key={item.key} className="flex items-start gap-2 px-4 py-2">
              <div className="min-w-0 flex-1">
                <div className="text-[11.5px] font-medium text-slate-700">{item.title}</div>
                <div className="truncate text-[10px] text-slate-400" title={item.detail}>{item.detail}</div>
              </div>
              <span className="shrink-0 rounded bg-slate-100 px-1.5 py-0.5 text-[9.5px] text-slate-500">{item.badge}</span>
            </li>
          ))}
        </ul>
      ) : (
        <div className="py-6 text-center text-[11px] text-slate-400">无差异</div>
      )}
    </section>
  );
}
