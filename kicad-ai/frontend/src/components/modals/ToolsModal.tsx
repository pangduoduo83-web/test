import clsx from "clsx";
import { ChevronDown, ChevronRight, Cpu, FolderKanban, History, Layout, Library, Pen, Pencil, Plug, Search, Shield, Sparkles, Wrench, type LucideIcon } from "lucide-react";
import { useEffect, useMemo, useRef, useState } from "react";
import { Modal } from "@/components/ui/Modal";
import { SOURCE_LABELS, toolLabel } from "@/lib/format";
import type { ToolCategory, ToolInfo } from "@/lib/types";

const ICONS: Record<string, LucideIcon> = {
  project: FolderKanban,
  pcb_query: Search,
  pcb_edit: Pen,
  pcb_place: Layout,
  drc: Shield,
  sch_query: Cpu,
  sch_edit: Pencil,
  library: Library,
  version: History,
  skill: Sparkles,
  ipc: Plug,
  harness: Wrench,
};

export function ToolsModal({ open, onClose, tools, onInsert }: { open: boolean; onClose: () => void; tools: ToolCategory[]; onInsert: (text: string) => void }) {
  const [q, setQ] = useState("");
  const [active, setActive] = useState<string>("all");
  const [expanded, setExpanded] = useState<string | null>(null);
  const listRef = useRef<HTMLDivElement>(null);
  useEffect(() => {
    listRef.current?.scrollTo({ top: 0 });
  }, [active, q]);
  const total = tools.reduce((n, c) => n + c.count, 0);
  const upstream = tools.reduce((n, c) => n + c.tools.filter((t) => t.source !== "native").length, 0);

  const visible = useMemo(() => {
    const needle = q.trim().toLowerCase();
    const cats = active === "all" ? tools : tools.filter((c) => c.key === active);
    return cats
      .map((c) => ({
        ...c,
        tools: c.tools.filter((t) => !needle || t.name.includes(needle) || toolLabel(t.name).toLowerCase().includes(needle) || t.description.toLowerCase().includes(needle)),
      }))
      .filter((c) => c.tools.length);
  }, [q, active, tools]);

  return (
    <Modal open={open} onClose={onClose} title={`可用工具 (${total})`} subtitle={`内置 ${total - upstream} 个 · 开源插件 KiCad-AI-Assistant ${upstream} 个（进程内直接调用）。点击工具名可插入到输入框。`} width="max-w-4xl" scroll="inner">
      <div className="grid h-[70vh] min-h-0 grid-cols-[180px_1fr] gap-4">
        <nav className="min-h-0 space-y-0.5 overflow-y-auto pr-1">
          <CatBtn active={active === "all"} onClick={() => setActive("all")} icon={Wrench} label="全部" count={total} />
          {tools
            .filter((c) => c.count > 0)
            .map((c) => (
              <CatBtn key={c.key} active={active === c.key} onClick={() => setActive(c.key)} icon={ICONS[c.key] ?? Wrench} label={c.label} count={c.count} />
            ))}
        </nav>

        <div className="flex min-h-0 min-w-0 flex-col">
          <div className="mb-3 flex shrink-0 items-center gap-2 rounded-lg border border-line bg-white px-3 py-2">
            <Search size={14} className="text-slate-400" />
            <input value={q} onChange={(e) => setQ(e.target.value)} placeholder="搜索工具名称或说明…" className="flex-1 bg-transparent text-[13px] outline-none" />
          </div>
          <div ref={listRef} className="min-h-0 flex-1 space-y-5 overflow-y-auto pr-1">
            {visible.map((c) => (
              <section key={c.key}>
                <h3 className="mb-1.5 flex items-center gap-2 px-1 text-[12px] font-semibold uppercase tracking-wide text-slate-400">
                  {c.label} <span className="font-normal normal-case text-slate-400">· {c.description}</span>
                </h3>
                <ul className="overflow-hidden rounded-xl border border-line bg-white">
                  {c.tools.map((t) => (
                    <ToolRow key={t.name} tool={t} expanded={expanded === t.name} onToggle={() => setExpanded(expanded === t.name ? null : t.name)} onInsert={onInsert} />
                  ))}
                </ul>
              </section>
            ))}
            {visible.length === 0 && <p className="py-10 text-center text-[13px] text-slate-400">没有匹配的工具</p>}
          </div>
        </div>
      </div>
    </Modal>
  );
}

function CatBtn({ active, onClick, icon: Icon, label, count }: { active: boolean; onClick: () => void; icon: LucideIcon; label: string; count: number }) {
  return (
    <button onClick={onClick} className={clsx("flex w-full items-center gap-2 rounded-lg px-2.5 py-1.5 text-left text-[12.5px] transition", active ? "bg-brand-50 font-medium text-brand-700" : "text-slate-600 hover:bg-slate-50")}>
      <Icon size={14} className={active ? "text-brand-500" : "text-slate-400"} />
      <span className="flex-1 truncate">{label}</span>
      <span className={clsx("rounded-full px-1.5 text-[10.5px]", active ? "bg-white text-brand-600" : "bg-slate-100 text-slate-500")}>{count}</span>
    </button>
  );
}

function ToolRow({ tool, expanded, onToggle, onInsert }: { tool: ToolInfo; expanded: boolean; onToggle: () => void; onInsert: (text: string) => void }) {
  const label = toolLabel(tool.name);
  const isWrite = tool.kind === "file_mutation" || tool.kind === "versioning";
  return (
    <li className="border-b border-line/70 last:border-b-0">
      <div className="flex items-center gap-3 px-3 py-2 hover:bg-slate-50/70">
        <button onClick={onToggle} className="shrink-0 text-slate-300 hover:text-slate-500">
          {expanded ? <ChevronDown size={13} /> : <ChevronRight size={13} />}
        </button>
        <button onClick={() => onInsert(`请使用 ${tool.name} 工具：`)} className="min-w-0 flex-1 text-left" title="插入到输入框">
          <span className="flex items-center gap-2">
            <span className="truncate text-[13px] font-medium text-ink">{label}</span>
            <code className="truncate rounded bg-slate-100 px-1.5 py-0.5 font-mono text-[11px] text-slate-500">{tool.name}</code>
          </span>
        </button>
        <span className="flex shrink-0 items-center gap-1">
          <Tag tone={isWrite ? "amber" : "slate"}>{isWrite ? "写入" : "只读"}</Tag>
          <Tag tone={tool.source === "native" ? "emerald" : "violet"}>{SOURCE_LABELS[tool.source] ?? tool.source}</Tag>
        </span>
      </div>
      {expanded && <p className="border-t border-line/60 bg-slate-50/50 px-10 py-2 text-[12px] leading-relaxed text-slate-600">{tool.description || "（无说明）"}</p>}
    </li>
  );
}

function Tag({ children, tone }: { children: React.ReactNode; tone: "amber" | "violet" | "emerald" | "slate" }) {
  const cls = {
    amber: "bg-amber-50 text-amber-700",
    violet: "bg-violet-50 text-violet-700",
    emerald: "bg-emerald-50 text-emerald-700",
    slate: "bg-slate-100 text-slate-500",
  }[tone];
  return <span className={`rounded px-1.5 py-0.5 text-[10.5px] font-medium ${cls}`}>{children}</span>;
}
