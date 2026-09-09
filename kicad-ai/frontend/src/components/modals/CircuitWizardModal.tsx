import { AlertTriangle, CheckCircle2, CircuitBoard, Loader2, WandSparkles } from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import { Modal } from "@/components/ui/Modal";
import { api } from "@/lib/api";
import type { CircuitTemplate, TemplateExpansion } from "@/lib/types";
import { useChat } from "@/store/chat";
import { useProjects } from "@/store/projects";

export function CircuitWizardModal({ open, onClose }: { open: boolean; onClose: () => void }) {
  const { projects, activeProjectId } = useProjects();
  const project = projects.find((item) => item.id === activeProjectId);
  const send = useChat((state) => state.send);
  const running = useChat((state) => state.running);
  const [templates, setTemplates] = useState<CircuitTemplate[]>([]);
  const [selected, setSelected] = useState<string | null>(null);
  const [params, setParams] = useState<Record<string, string | number>>({});
  const [preview, setPreview] = useState<TemplateExpansion | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!open) return;
    api.circuitTemplates()
      .then((response) => {
        setTemplates(response.templates);
        if (!selected && response.templates[0]) chooseTemplate(response.templates[0]);
      })
      .catch((reason) => setError((reason as Error).message));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open]);

  const template = useMemo(() => templates.find((item) => item.id === selected) ?? null, [templates, selected]);

  const chooseTemplate = (next: CircuitTemplate) => {
    setSelected(next.id);
    setParams(Object.fromEntries(next.params.map((param) => [param.key, param.default])));
    setPreview(null);
    setError(null);
  };

  useEffect(() => {
    if (!open || !project || !template) return;
    let cancelled = false;
    setLoading(true);
    const timer = window.setTimeout(() => {
      api.previewCircuitTemplate(project.id, { template: template.id, params })
        .then((next) => {
          if (!cancelled) {
            setPreview(next);
            setError(null);
          }
        })
        .catch((reason) => {
          if (!cancelled) {
            setPreview(null);
            setError((reason as Error).message);
          }
        })
        .finally(() => {
          if (!cancelled) setLoading(false);
        });
    }, 250);
    return () => {
      cancelled = true;
      window.clearTimeout(timer);
    };
  }, [open, project?.id, template?.id, params]);

  const missing = (preview?.library.missing_symbols.length ?? 0) + (preview?.library.missing_footprints.length ?? 0);
  const canApply = !!preview && !running && missing === 0 && !!project?.schematic_file;

  const requestPlacement = async () => {
    if (!preview || !project) return;
    onClose();
    const action = {
      tool: "apply_circuit_template",
      args: { template: preview.template, params: preview.params },
      summary: `放置「${preview.name}」电路模块`,
      reason: preview.notes.join(" "),
    };
    await send(
      `请在当前原理图中添加「${preview.name}」电路模块。先调用 preview_circuit_template 复核，`
      + `然后调用 submit_change_plan 提交且只提交下面这个动作（参数必须完全一致），等待我批准后执行：\n`
      + "```json\n" + JSON.stringify(action, null, 2) + "\n```\n"
      + "执行后调用 run_erc_check 复查，并汇报新增的位号与网络。",
    );
  };

  return (
    <Modal
      open={open}
      onClose={onClose}
      title="常用电路向导"
      subtitle="从 KiCad 标准库生成常见电路模块；参数实时计算，放置前需在对话中整批批准。"
      width="max-w-5xl"
      scroll="inner"
      footer={
        <div className="flex w-full items-center justify-between gap-3">
          <span className="text-xs text-slate-400">{project?.schematic_file ? "将放置到当前工程原理图的空闲区域" : "当前工程没有原理图"}</span>
          <button type="button" onClick={requestPlacement} disabled={!canApply} className="flex items-center gap-1.5 rounded-lg bg-brand-500 px-4 py-2 text-xs font-medium text-white hover:bg-brand-600 disabled:opacity-40">
            <WandSparkles size={14} /> 生成放置计划
          </button>
        </div>
      }
    >
      <div className="flex min-h-0 flex-1 flex-col gap-3 md:flex-row">
        <aside className="flex w-full shrink-0 flex-col gap-1 overflow-y-auto md:w-56">
          {templates.map((item) => (
            <button
              key={item.id}
              type="button"
              onClick={() => chooseTemplate(item)}
              className={`rounded-xl border px-3 py-2.5 text-left transition ${item.id === selected ? "border-brand-300 bg-brand-50" : "border-line bg-white hover:bg-slate-50"}`}
            >
              <div className="text-[12.5px] font-medium text-ink">{item.name}</div>
              <div className="mt-0.5 text-[10.5px] text-slate-400">{item.category} · {item.description}</div>
            </button>
          ))}
        </aside>

        <div className="flex min-h-0 min-w-0 flex-1 flex-col gap-3 overflow-y-auto">
          {template && (
            <section className="rounded-2xl border border-line bg-white p-4">
              <h3 className="mb-3 text-sm font-semibold text-ink">参数</h3>
              <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
                {template.params.map((param) => (
                  <label key={param.key} className="block">
                    <span className="mb-1 block text-xs font-medium text-slate-600">{param.label}</span>
                    {param.type === "select" ? (
                      <select value={String(params[param.key] ?? param.default)} onChange={(event) => setParams({ ...params, [param.key]: event.target.value })} className="w-full rounded-lg border border-line px-3 py-2 text-[13px] outline-none focus:border-brand-300">
                        {param.options?.map((option) => <option key={option} value={option}>{option}</option>)}
                      </select>
                    ) : (
                      <input
                        type={param.type === "number" ? "number" : "text"}
                        min={param.min}
                        max={param.max}
                        step="any"
                        value={String(params[param.key] ?? param.default)}
                        onChange={(event) => setParams({ ...params, [param.key]: param.type === "number" ? Number(event.target.value) : event.target.value })}
                        className="w-full rounded-lg border border-line px-3 py-2 text-[13px] outline-none focus:border-brand-300"
                      />
                    )}
                  </label>
                ))}
              </div>
            </section>
          )}

          <section className="rounded-2xl border border-line bg-white p-4">
            <h3 className="mb-3 flex items-center gap-2 text-sm font-semibold text-ink">
              <CircuitBoard size={15} className="text-brand-500" /> 展开预览
              {loading && <Loader2 size={13} className="animate-spin text-slate-400" />}
            </h3>
            {error ? (
              <p className="rounded-lg bg-rose-50 px-3 py-2 text-xs text-rose-600">{error}</p>
            ) : preview ? (
              <div className="space-y-3">
                {preview.notes.map((note) => <p key={note} className="rounded-lg bg-brand-50/70 px-3 py-2 text-[11.5px] text-brand-800">{note}</p>)}
                <table className="w-full text-[11.5px]">
                  <thead className="text-left text-[10.5px] uppercase tracking-wide text-slate-400">
                    <tr><th className="py-1">器件</th><th className="py-1">值</th><th className="py-1">封装</th></tr>
                  </thead>
                  <tbody className="divide-y divide-line">
                    {preview.parts.map((part) => (
                      <tr key={part.id}>
                        <td className="py-1.5 font-mono text-[10.5px] text-slate-600">{part.library}:{part.symbol}</td>
                        <td className="py-1.5 text-ink">{part.value}</td>
                        <td className="max-w-[220px] truncate py-1.5 font-mono text-[10px] text-slate-400" title={part.footprint}>{part.footprint || "—"}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
                <p className="text-[10.5px] text-slate-400">{preview.wires.length} 条连线 · {preview.labels.length} 个网络标签</p>
                {preview.library.checked ? (
                  missing ? (
                    <p className="flex items-start gap-1.5 rounded-lg bg-amber-50 px-3 py-2 text-[11px] text-amber-700">
                      <AlertTriangle size={13} className="mt-0.5 shrink-0" /> KiCad 库缺少：{[...preview.library.missing_symbols, ...preview.library.missing_footprints].join("、")}
                    </p>
                  ) : (
                    <p className="flex items-center gap-1.5 text-[11px] text-emerald-600"><CheckCircle2 size={13} /> 所有符号与封装均存在于服务器 KiCad 库</p>
                  )
                ) : (
                  <p className="text-[11px] text-slate-400">服务器未安装 KiCad 库索引，放置时再校验。</p>
                )}
              </div>
            ) : (
              <p className="text-xs text-slate-400">选择模板并填写参数后显示预览</p>
            )}
          </section>
        </div>
      </div>
    </Modal>
  );
}
