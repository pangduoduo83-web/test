import { Loader2, Ruler, Save } from "lucide-react";
import { useEffect, useState } from "react";
import { Modal } from "@/components/ui/Modal";
import type { DesignConstraints } from "@/lib/types";
import { useProjects } from "@/store/projects";

const DEFAULTS: DesignConstraints = {
  placement_grid_mm: 0.1,
  min_clearance_mm: 0.2,
  min_track_width_mm: 0.2,
  signal_track_width_mm: 0.25,
  power_track_width_mm: 0.5,
  via_diameter_mm: 0.6,
  via_drill_mm: 0.3,
  copper_edge_clearance_mm: 0.5,
  max_component_height_mm: null,
  notes: "",
};

export function DesignConstraintsModal({ open, onClose }: { open: boolean; onClose: () => void }) {
  const { projects, activeProjectId, updateConstraints } = useProjects();
  const project = projects.find((item) => item.id === activeProjectId);
  const [form, setForm] = useState<DesignConstraints>(DEFAULTS);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);

  useEffect(() => {
    if (!open) return;
    setForm({ ...DEFAULTS, ...(project?.design_constraints ?? {}) });
    setError(null);
    setSaved(false);
  }, [open, project?.id]); // Sample the latest project values when the modal opens.

  const setNumber = (key: keyof DesignConstraints, value: string, nullable = false) => {
    setSaved(false);
    setForm((current) => ({
      ...current,
      [key]: nullable && value === "" ? null : Number(value),
    }));
  };

  const save = async () => {
    if (!project) return;
    if (form.via_drill_mm >= form.via_diameter_mm) {
      setError("过孔钻孔必须小于过孔外径");
      return;
    }
    if (form.signal_track_width_mm < form.min_track_width_mm || form.power_track_width_mm < form.min_track_width_mm) {
      setError("信号线宽和电源线宽不能小于最小线宽");
      return;
    }
    setSaving(true);
    setError(null);
    try {
      const next = await updateConstraints(project.id, form);
      setForm(next);
      setSaved(true);
    } catch (reason) {
      setError((reason as Error).message);
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal
      open={open}
      onClose={onClose}
      title="项目设计约束"
      subtitle="规则会注入 Agent；线宽、间距、过孔等可直接判断的参数还会由服务器强制校验。"
      width="max-w-3xl"
      scroll="inner"
      footer={
        <div className="flex w-full items-center justify-between gap-3">
          <span className={`text-xs ${error ? "text-rose-600" : saved ? "text-emerald-600" : "text-slate-400"}`}>
            {error ?? (saved ? "设计约束已保存" : "单位均为毫米")}
          </span>
          <button
            type="button"
            disabled={!project || saving}
            onClick={save}
            className="flex items-center gap-1.5 rounded-lg bg-brand-500 px-4 py-2 text-xs font-medium text-white hover:bg-brand-600 disabled:opacity-40"
          >
            {saving ? <Loader2 size={14} className="animate-spin" /> : <Save size={14} />}
            保存约束
          </button>
        </div>
      }
    >
      {!project ? (
        <div className="py-10 text-center text-sm text-slate-400">请先选择一个工程</div>
      ) : (
        <div className="space-y-5">
          <section>
            <h3 className="mb-3 flex items-center gap-2 text-sm font-semibold text-ink">
              <Ruler size={15} className="text-brand-500" /> 布局与制造
            </h3>
            <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
              <NumberField label="布局栅格" value={form.placement_grid_mm} step={0.05} onChange={(v) => setNumber("placement_grid_mm", v)} />
              <NumberField label="最小电气间距" value={form.min_clearance_mm} step={0.05} onChange={(v) => setNumber("min_clearance_mm", v)} />
              <NumberField label="铜到板边间距" value={form.copper_edge_clearance_mm} step={0.05} onChange={(v) => setNumber("copper_edge_clearance_mm", v)} />
              <NumberField
                label="最大器件高度（留空表示不限）"
                value={form.max_component_height_mm}
                step={0.5}
                nullable
                onChange={(v) => setNumber("max_component_height_mm", v, true)}
              />
            </div>
          </section>

          <section>
            <h3 className="mb-3 text-sm font-semibold text-ink">走线与过孔</h3>
            <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
              <NumberField label="最小线宽" value={form.min_track_width_mm} step={0.05} onChange={(v) => setNumber("min_track_width_mm", v)} />
              <NumberField label="默认信号线宽" value={form.signal_track_width_mm} step={0.05} onChange={(v) => setNumber("signal_track_width_mm", v)} />
              <NumberField label="默认电源线宽" value={form.power_track_width_mm} step={0.1} onChange={(v) => setNumber("power_track_width_mm", v)} />
              <div />
              <NumberField label="过孔外径" value={form.via_diameter_mm} step={0.05} onChange={(v) => setNumber("via_diameter_mm", v)} />
              <NumberField label="过孔钻孔" value={form.via_drill_mm} step={0.05} onChange={(v) => setNumber("via_drill_mm", v)} />
            </div>
          </section>

          <label className="block">
            <span className="mb-1.5 block text-xs font-medium text-slate-700">补充要求</span>
            <textarea
              rows={4}
              maxLength={2000}
              value={form.notes}
              onChange={(event) => {
                setSaved(false);
                setForm({ ...form, notes: event.target.value });
              }}
              placeholder="例如：USB 差分对 90Ω、连接器保持在板边、J1 周围保留 3 mm 禁布区……"
              className="w-full resize-y rounded-lg border border-line px-3 py-2 text-[13px] leading-relaxed outline-none focus:border-brand-300 focus:ring-2 focus:ring-brand-100"
            />
            <span className="mt-1 block text-right text-[10.5px] text-slate-400">{form.notes.length}/2000</span>
          </label>
        </div>
      )}
    </Modal>
  );
}

function NumberField({
  label,
  value,
  step,
  nullable,
  onChange,
}: {
  label: string;
  value: number | null;
  step: number;
  nullable?: boolean;
  onChange: (value: string) => void;
}) {
  return (
    <label className="block">
      <span className="mb-1 block text-xs font-medium text-slate-600">{label}</span>
      <div className="relative">
        <input
          type="number"
          min={nullable ? undefined : step}
          max={100}
          step={step}
          value={value ?? ""}
          onChange={(event) => onChange(event.target.value)}
          className="w-full rounded-lg border border-line px-3 py-2 pr-10 text-[13px] outline-none focus:border-brand-300 focus:ring-2 focus:ring-brand-100"
        />
        <span className="pointer-events-none absolute right-3 top-1/2 -translate-y-1/2 text-[10.5px] text-slate-400">mm</span>
      </div>
    </label>
  );
}
