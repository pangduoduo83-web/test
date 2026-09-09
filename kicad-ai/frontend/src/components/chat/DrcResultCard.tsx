import { AlertTriangle, Check, CheckCircle2, ChevronDown, ChevronRight, XCircle } from "lucide-react";
import { useState } from "react";

interface Violation {
  type: string;
  severity: string;
  description: string;
  items?: { description: string; pos?: { x: number; y: number } }[];
}

export interface DrcData {
  success?: boolean;
  engine?: string;
  passed?: boolean;
  error_count?: number;
  warning_count?: number;
  unconnected_count?: number;
  violations?: Violation[];
  unconnected_items?: { net?: string; from?: string; to?: string[]; description?: string }[];
  note?: string;
}

export function isDrcData(data: unknown): data is DrcData {
  return !!data && typeof data === "object" && "violations" in (data as object) && "error_count" in (data as object);
}

export function DrcResultCard({ data, at, check = "DRC" }: { data: DrcData; at?: string | null; check?: "DRC" | "ERC" }) {
  const [open, setOpen] = useState(false);
  const errors = data.error_count ?? 0;
  const warnings = data.warning_count ?? 0;
  const unconnected = data.unconnected_count ?? 0;
  const passed = errors === 0;
  const tone = passed ? (warnings ? "amber" : "emerald") : "rose";
  const palette = {
    emerald: "border-[#b9ead2] bg-[#eafaf1] text-[#116b45]",
    amber: "border-amber-200 bg-amber-50 text-amber-800",
    rose: "border-rose-200 bg-rose-50 text-rose-800",
  }[tone];
  const Icon = passed ? (warnings ? AlertTriangle : CheckCircle2) : XCircle;
  const title = passed ? `${check} 检查通过` : `${check} 发现 ${errors} 个错误`;
  const subtitle = [
    errors ? `${errors} 个错误` : `未发现任何 ${check} 错误`,
    warnings ? `${warnings} 个警告` : "",
    unconnected ? `${unconnected} 个未连接` : "",
  ]
    .filter(Boolean)
    .join(" · ");

  return (
    <div className={`mt-2 w-full max-w-[490px] rounded-2xl border shadow-[0_1px_2px_rgba(0,0,0,0.02)] ${palette}`}>
      <button onClick={() => setOpen((v) => !v)} className="flex w-full items-center gap-3 px-3.5 py-3 text-left">
        <span className={`flex h-8 w-8 shrink-0 items-center justify-center rounded-full ${passed && !warnings ? "bg-emerald-500 text-white shadow-xs" : "bg-white/70"}`}>
          {passed && !warnings ? <Check size={17} strokeWidth={3} /> : <Icon size={22} className="shrink-0" />}
        </span>
        <div className="min-w-0 flex-1">
          <div className="text-[13px] font-semibold">{title}</div>
          <div className="text-[11.5px] opacity-80">
            {subtitle}
            {data.engine && <span className="ml-1 opacity-70">({data.engine})</span>}
          </div>
        </div>
        {at && <span className="text-[11px] opacity-60 tabular-nums">{at.length === 5 ? at : at.slice(11, 16)}</span>}
        {((data.violations?.length ?? 0) > 0 || (data.unconnected_items?.length ?? 0) > 0) && (open ? <ChevronDown size={14} /> : <ChevronRight size={14} />)}
      </button>
      {open && (
        <div className="space-y-1.5 border-t border-black/5 px-3.5 py-2.5 text-[11.5px]">
          {data.violations?.map((v, i) => (
            <div key={i} className="rounded-md bg-white/70 px-2 py-1.5">
              <span className={`mr-1.5 rounded px-1 text-[10px] font-medium uppercase ${v.severity === "error" ? "bg-rose-100 text-rose-700" : "bg-amber-100 text-amber-700"}`}>{v.severity}</span>
              <span className="font-mono text-[10.5px] text-slate-500">{v.type}</span>
              <div className="mt-0.5 text-slate-700">{v.description}</div>
              {v.items?.map((it, j) => (
                <div key={j} className="text-[10.5px] text-slate-500">
                  · {it.description}
                  {it.pos && ` @ (${Number(it.pos.x).toFixed(2)}, ${Number(it.pos.y).toFixed(2)}) mm`}
                </div>
              ))}
            </div>
          ))}
          {data.unconnected_items?.map((u, i) => (
            <div key={`u${i}`} className="rounded-md bg-white/70 px-2 py-1.5 text-slate-700">
              <span className="mr-1.5 rounded bg-slate-100 px-1 text-[10px] font-medium text-slate-600">未连接</span>
              {u.description ?? `${u.net}: ${u.from} → ${(u.to ?? []).join(", ")}`}
            </div>
          ))}
          {data.note && <p className="pt-1 text-[10.5px] opacity-70">{data.note}</p>}
        </div>
      )}
    </div>
  );
}
