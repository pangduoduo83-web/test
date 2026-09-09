import { Activity, ArrowRightLeft, Cpu, Download, ExternalLink, FolderKanban, Layout, ListOrdered, Loader2, Maximize2, Pen, ScanSearch, Search, Shield, SlidersHorizontal, WandSparkles, Wrench, type LucideIcon } from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import { Avatar } from "@/components/ui/Avatar";
import { downloadAuthenticated, fetchPreviewBlobUrl, fetchPreviewSchBlobUrl } from "@/lib/api";
import { durationHMS } from "@/lib/format";
import type { ToolCategory } from "@/lib/types";
import { useChat } from "@/store/chat";
import { usePresence } from "@/store/presence";
import { useProjects } from "@/store/projects";

interface Props {
  tools: ToolCategory[];
  onSwitchProject: () => void;
  onAllTools: () => void;
  onProjectFiles: () => void;
  onSelectDesign: () => void;
  onConstraints: () => void;
  onReview: () => void;
  onEco: () => void;
  onBom: () => void;
  onWizard: () => void;
  onEnlargePreview: (mode: "pcb" | "sch") => void;
}

const CATEGORY_ICONS: Record<string, { icon: LucideIcon; bg: string; fg: string; badgeBg: string; badgeFg: string }> = {
  project: { icon: FolderKanban, bg: "bg-sky-100/80", fg: "text-sky-600", badgeBg: "bg-sky-50", badgeFg: "text-sky-600" },
  pcb_query: { icon: Search, bg: "bg-blue-100/80", fg: "text-blue-600", badgeBg: "bg-blue-50", badgeFg: "text-blue-600" },
  pcb_edit: { icon: Pen, bg: "bg-amber-100/80", fg: "text-amber-600", badgeBg: "bg-amber-50", badgeFg: "text-amber-600" },
  pcb_place: { icon: Layout, bg: "bg-emerald-100/80", fg: "text-emerald-600", badgeBg: "bg-emerald-50", badgeFg: "text-emerald-600" },
  drc: { icon: Shield, bg: "bg-purple-100/80", fg: "text-purple-600", badgeBg: "bg-purple-50", badgeFg: "text-purple-600" },
  sch_query: { icon: Cpu, bg: "bg-indigo-100/80", fg: "text-indigo-600", badgeBg: "bg-indigo-50", badgeFg: "text-indigo-600" },
  sch_edit: { icon: Pen, bg: "bg-violet-100/80", fg: "text-violet-600", badgeBg: "bg-violet-50", badgeFg: "text-violet-600" },
};

const CATEGORY_PRIORITY = ["pcb_query", "pcb_edit", "pcb_place", "drc", "sch_query", "sch_edit", "project"];

export function RightPanel({ tools, onSwitchProject, onAllTools, onProjectFiles, onSelectDesign, onConstraints, onReview, onEco, onBom, onWizard, onEnlargePreview }: Props) {
  return (
    <aside className="flex w-[22rem] shrink-0 flex-col gap-2.5 overflow-y-auto xl:w-[26rem] 2xl:w-[30rem]">
      <ProjectCard onSwitch={onSwitchProject} onFiles={onProjectFiles} onConstraints={onConstraints} />
      <PreviewCard onFiles={onProjectFiles} onSelect={onSelectDesign} onReview={onReview} onEco={onEco} onBom={onBom} onWizard={onWizard} onEnlarge={onEnlargePreview} />
      <ToolsCard tools={tools} onAll={onAllTools} />
      <SessionCard />
      <OnlineCard />
    </aside>
  );
}

function ProjectCard({ onSwitch, onFiles, onConstraints }: { onSwitch: () => void; onFiles: () => void; onConstraints: () => void }) {
  const { projects, activeProjectId } = useProjects();
  const project = projects.find((p) => p.id === activeProjectId);
  const file = project?.pcb_file ?? project?.schematic_file ?? project?.pro_file;
  const [downloading, setDownloading] = useState(false);
  const downloadProject = async () => {
    if (!project) return;
    setDownloading(true);
    try {
      await downloadAuthenticated(`/api/projects/${project.id}/archive`, `${project.name}.zip`);
    } finally {
      setDownloading(false);
    }
  };
  return (
    <section className="card p-3">
      <div className="flex items-center justify-between">
        <h4 className="text-xs font-medium text-slate-500">当前工程</h4>
        <div className="flex items-center gap-1">
          {project && (
            <button
              onClick={downloadProject}
              disabled={downloading}
              className="flex items-center gap-1 rounded-md border border-brand-200/80 bg-brand-50 px-1.5 py-0.5 text-[10.5px] font-medium text-brand-600 hover:bg-brand-100 disabled:opacity-50 transition-colors"
              title="导出当前工程 ZIP（解压后可在 KiCad 中直接打开）"
            >
              {downloading ? <Loader2 size={11} className="animate-spin" /> : <Download size={11} />} 导出
            </button>
          )}
          {project && (
            <button onClick={onFiles} className="flex items-center gap-1 rounded px-1.5 py-0.5 text-[10.5px] text-slate-500 hover:bg-slate-50 hover:text-slate-700" title="工程文件">
              <FolderKanban size={10} /> 文件
            </button>
          )}
          {project && (
            <button
              onClick={onConstraints}
              className="flex items-center gap-1 rounded px-1.5 py-0.5 text-[10.5px] text-slate-500 hover:bg-brand-50 hover:text-brand-600"
              title="设置此工程的布局、线宽、过孔和制造约束"
            >
              <SlidersHorizontal size={10} /> 约束
            </button>
          )}
        </div>
      </div>
      <div className="mt-2 flex items-start gap-2.5">
        <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-emerald-50 text-emerald-600">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8">
            <rect x="3" y="3" width="18" height="18" rx="3" />
            <path d="M8 8h3v3H8zM13 13h3v3h-3zM8 13h3M13 8h3M11 9.5h2M9.5 11v2" />
          </svg>
        </div>
        <div className="min-w-0 flex-1">
          {project ? (
            <>
              <button onClick={onFiles} className="block max-w-full truncate text-left text-[12.5px] font-medium text-ink hover:text-brand-600" title={file ?? ""}>
                {file?.split("/").pop() ?? project.name}
              </button>
              <div className="truncate text-[11px] text-slate-400" title={project.rel_dir}>
                /{project.rel_dir}
              </div>
            </>
          ) : (
            <div className="text-[12px] text-slate-400">未选择工程</div>
          )}
        </div>
        <button onClick={onSwitch} className="shrink-0 rounded-md border border-line px-2 py-1 text-[11px] text-slate-600 hover:bg-slate-50">
          切换工程
        </button>
      </div>
    </section>
  );
}

function PreviewCard({
  onFiles,
  onSelect,
  onReview,
  onEco,
  onBom,
  onWizard,
  onEnlarge,
}: {
  onFiles: () => void;
  onSelect: () => void;
  onReview: () => void;
  onEco: () => void;
  onBom: () => void;
  onWizard: () => void;
  onEnlarge: (mode: "pcb" | "sch") => void;
}) {
  const { projects, activeProjectId, previewVersion, previewMode, setPreviewMode } = useProjects();
  const project = projects.find((p) => p.id === activeProjectId);
  const viewMode = previewMode;
  const setViewMode = setPreviewMode;
  const [url, setUrl] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [changed, setChanged] = useState(false);
  const [refreshing, setRefreshing] = useState(false);

  // Keep showing the previous render until the new one has arrived, then swap
  // (and only then release the old blob) so the preview never flashes empty.
  useEffect(() => {
    const hasTargetFile = viewMode === "pcb" ? !!project?.pcb_file : (!!project?.schematic_file || !!project?.pro_file);
    if (!project || !hasTargetFile) {
      setUrl((prev) => {
        if (prev) URL.revokeObjectURL(prev);
        return null;
      });
      return;
    }
    let cancelled = false;
    setRefreshing(true);
    const fetcher = viewMode === "pcb" ? fetchPreviewBlobUrl(project.id) : fetchPreviewSchBlobUrl(project.id);
    fetcher
      .then((u) => {
        if (cancelled) {
          URL.revokeObjectURL(u);
          return;
        }
        setUrl((prev) => {
          if (prev) URL.revokeObjectURL(prev);
          return u;
        });
        setError(null);
        setChanged(previewVersion > 0);
      })
      .catch((e) => {
        if (!cancelled) setError((e as Error).message);
      })
      .finally(() => {
        if (!cancelled) setRefreshing(false);
      });
    return () => {
      cancelled = true;
    };
  }, [project?.id, project?.pcb_file, project?.schematic_file, previewVersion, viewMode]);

  const [downloading, setDownloading] = useState(false);
  const downloadProject = async () => {
    if (!project) return;
    setDownloading(true);
    try {
      await downloadAuthenticated(`/api/projects/${project.id}/archive`, `${project.name}.zip`);
    } catch {
      /* surfaced elsewhere */
    } finally {
      setDownloading(false);
    }
  };

  const isPcb = viewMode === "pcb";
  const emptyMessage = error ?? (
    !project
      ? "选择工程后显示预览"
      : isPcb
      ? "该工程没有 PCB 文件"
      : "该工程没有原理图文件"
  );

  return (
    <section className="card p-3">
      <div className="flex items-center justify-between mb-2">
        <div className="flex items-center rounded-md bg-slate-100 p-0.5 text-[11px] font-medium text-slate-500">
          <button
            type="button"
            onClick={() => setViewMode("pcb")}
            className={`rounded px-2 py-0.5 transition-all ${
              viewMode === "pcb" ? "bg-white text-slate-800 shadow-sm font-semibold" : "hover:text-slate-700"
            }`}
          >
            PCB 布局
          </button>
          <button
            type="button"
            onClick={() => setViewMode("sch")}
            className={`rounded px-2 py-0.5 transition-all ${
              viewMode === "sch" ? "bg-white text-slate-800 shadow-sm font-semibold" : "hover:text-slate-700"
            }`}
          >
            原理图
          </button>
        </div>
        <span className="text-[10px] text-slate-400 font-mono">
          {viewMode === "pcb" ? "PCB" : "SCH"}
        </span>
      </div>

      <div
        className={`group relative overflow-hidden rounded-lg transition-colors ${
          viewMode === "pcb"
            ? "bg-[#001d17]"
            : "bg-white border border-slate-200/90 shadow-[inset_0_1px_3px_rgba(0,0,0,0.03)]"
        }`}
        style={{ aspectRatio: "1 / 1" }}
      >
        {url ? (
          <button
            type="button"
            onClick={() => onEnlarge(viewMode)}
            title="点击放大查看"
            className="block h-full w-full cursor-zoom-in"
          >
            <img
              key={url}
              src={url}
              alt={viewMode === "pcb" ? "PCB preview" : "Schematic preview"}
              className="animate-fade-in h-full w-full object-contain p-0.5"
            />
            <span className="pointer-events-none absolute bottom-2 right-2 flex items-center gap-1 rounded-md bg-black/55 px-1.5 py-0.5 text-[10px] text-white opacity-0 transition-opacity group-hover:opacity-100">
              <Maximize2 size={10} /> 放大
            </span>
          </button>
        ) : (
          <div
            className={`flex h-full items-center justify-center px-4 text-center text-[11px] ${
              viewMode === "pcb" ? "text-emerald-200/70" : "text-slate-400"
            }`}
          >
            {emptyMessage}
          </div>
        )}
        {refreshing && <div className="progress-bar absolute inset-x-0 top-0 h-[2px] bg-brand-500/50" />}
        {url && (
          <span
            className={`absolute right-2 top-2 rounded-md px-1.5 py-0.5 text-[10px] font-medium backdrop-blur transition-colors ${
              viewMode === "pcb"
                ? "bg-black/55 text-white"
                : "bg-white/90 border border-slate-200 text-slate-700 shadow-sm"
            }`}
          >
            {refreshing ? "更新中…" : changed ? "已更新" : viewMode === "pcb" ? "PCB视图" : "原理图视图"}
          </span>
        )}
      </div>

      <div className="mt-2.5 flex flex-col gap-1.5">
        <div className="grid grid-cols-3 gap-1.5">
          <button
            onClick={onReview}
            disabled={!project}
            className="flex items-center justify-center gap-1 rounded-md bg-emerald-50 py-1.5 text-[11px] font-medium text-emerald-700 hover:bg-emerald-100 disabled:opacity-40"
          >
            <Activity size={12} /> 设计审查
          </button>
          <button
            onClick={onEco}
            disabled={!project?.pcb_file || !project?.schematic_file}
            className="flex items-center justify-center gap-1 rounded-md bg-blue-50 py-1.5 text-[11px] font-medium text-blue-700 hover:bg-blue-100 disabled:opacity-40"
            title="比较原理图和 PCB 的器件、封装、值与网络"
          >
            <ArrowRightLeft size={12} /> ECO
          </button>
          <button
            onClick={onBom}
            disabled={!project}
            className="flex items-center justify-center gap-1 rounded-md bg-amber-50 py-1.5 text-[11px] font-medium text-amber-700 hover:bg-amber-100 disabled:opacity-40"
            title="物料清单与可制造性检查，可导出 CSV"
          >
            <ListOrdered size={12} /> BOM/DFM
          </button>
        </div>
        <div className="grid grid-cols-2 gap-1.5">
          <button
            onClick={onWizard}
            disabled={!project?.schematic_file}
            className="flex items-center justify-center gap-1 rounded-md bg-violet-50 py-1.5 text-[11px] font-medium text-violet-700 hover:bg-violet-100 disabled:opacity-40"
            title="从标准库生成 LDO、LED、分压、去耦、USB-C、RS-485 等常用电路"
          >
            <WandSparkles size={12} /> 电路向导
          </button>
          <button
            onClick={downloadProject}
            disabled={!project || downloading}
            title="在 KiCad 中打开工程（导出 ZIP 并可在 KiCad 中直接双击打开）"
            className="flex items-center justify-center gap-1 rounded-md bg-slate-50 py-1.5 text-[11px] font-medium text-brand-600 hover:bg-slate-100 disabled:opacity-40"
          >
            {downloading ? <Loader2 size={12} className="animate-spin" /> : <ExternalLink size={11} />} 在 KiCad 中打开
          </button>
        </div>
        <div className="flex gap-1.5 pt-1 border-t border-line/60">
          <button
            onClick={onSelect}
            disabled={!project || !url}
            className="flex flex-1 items-center justify-center gap-1 rounded-md bg-brand-50 py-1 text-[11px] font-medium text-brand-600 hover:bg-brand-100 disabled:opacity-40"
            title={`选择${viewMode === "pcb" ? "封装" : "原理图符号"}并引用到对话`}
          >
            <ScanSearch size={11} /> 框选对象
          </button>
          <button
            onClick={downloadProject}
            disabled={!project || downloading}
            title="下载整套工程文件 (ZIP)，解压后双击 .kicad_pro 即可在 KiCad 中直接打开与编辑"
            className="flex items-center justify-center gap-1 rounded-md bg-slate-50 px-2 py-1 text-[11px] text-slate-600 hover:bg-slate-100 disabled:opacity-40"
          >
            <Download size={11} /> ZIP
          </button>
          <button onClick={onFiles} disabled={!project} className="flex items-center justify-center rounded-md border border-line px-2 py-1 text-slate-500 hover:bg-slate-50 disabled:opacity-40" title="工程文件列表">
            <FolderKanban size={11} />
          </button>
          <button onClick={() => onEnlarge(viewMode)} disabled={!url} className="flex items-center justify-center rounded-md border border-line px-2 py-1 text-slate-500 hover:bg-slate-50 disabled:opacity-40" title="放大查看（可在大图中打开新窗口）">
            <Maximize2 size={11} />
          </button>
        </div>
      </div>
    </section>
  );
}

function ToolsCard({ tools, onAll }: { tools: ToolCategory[]; onAll: () => void }) {
  const total = tools.reduce((n, c) => n + c.count, 0);
  const shown = [...tools]
    .filter((c) => c.key in CATEGORY_ICONS && c.count > 0)
    .sort((a, b) => CATEGORY_PRIORITY.indexOf(a.key) - CATEGORY_PRIORITY.indexOf(b.key))
    .slice(0, 4);
  return (
    <section className="card p-3">
      <div className="flex items-center justify-between">
        <h4 className="text-xs font-medium text-slate-500">可用工具 ({total})</h4>
        <button onClick={onAll} className="text-[11px] text-brand-600 hover:underline">
          全部工具
        </button>
      </div>
      <ul className="mt-2 space-y-1.5">
        {shown.map((c) => {
          const meta = CATEGORY_ICONS[c.key] ?? { icon: Wrench, bg: "bg-slate-50", fg: "text-slate-500", badgeBg: "bg-slate-100", badgeFg: "text-slate-600" };
          const Icon = meta.icon;
          return (
            <li key={c.key} className="flex items-center gap-2.5 rounded-lg bg-slate-50/70 px-2.5 py-1.5">
              <span className={`flex h-7 w-7 items-center justify-center rounded-md ${meta.bg} ${meta.fg}`}>
                <Icon size={14} />
              </span>
              <div className="min-w-0 flex-1">
                <div className="text-[12px] font-medium text-ink">{c.label}</div>
                <div className="truncate text-[10.5px] text-slate-400">{c.description}</div>
              </div>
              <span className={`rounded-full px-2 py-0.5 text-[10.5px] font-semibold ${meta.badgeBg ?? "bg-slate-100"} ${meta.badgeFg ?? "text-slate-600"}`}>
                {c.count}
              </span>
            </li>
          );
        })}
      </ul>
    </section>
  );
}

function SessionCard() {
  const { conversations, activeId, messages, contextUsage, contextTokens, running, runStartedAt } = useChat();
  const conv = conversations.find((c) => c.id === activeId);
  const [now, setNow] = useState(Date.now());
  useEffect(() => {
    if (!running) return;
    const t = window.setInterval(() => setNow(Date.now()), 1000);
    return () => window.clearInterval(t);
  }, [running]);

  const toolCalls = messages.reduce((n, m) => n + (m.tool_calls?.length ?? 0), 0);
  const msgCount = messages.filter((m) => m.role === "user" || (m.role === "assistant" && m.content.trim())).length;
  const pct = Math.min(100, Math.max(0, Math.round(contextUsage * 100)));
  const safeLimit = Math.max(16000, contextTokens || 128000);
  const approxTokens = Math.round(contextUsage * safeLimit);

  const durationText = useMemo(() => {
    if (running && runStartedAt) {
      return durationHMS(Math.max(0, now - runStartedAt));
    }
    if (messages.length >= 2) {
      const firstAt = messages[0].created_at;
      const lastAt = messages[messages.length - 1].created_at;
      const first = firstAt ? new Date(firstAt).getTime() : 0;
      const last = lastAt ? new Date(lastAt).getTime() : 0;
      if (first && last && last >= first) {
        return durationHMS(last - first);
      }
    }
    return "00:00:00";
  }, [running, runStartedAt, now, messages]);

  return (
    <section className="card p-3">
      <h4 className="text-xs font-medium text-slate-500">会话状态</h4>
      <dl className="mt-2 space-y-1.5 text-[12px]">
        <Row label="消息数量" value={String(Math.max(msgCount, conv?.message_count ?? 0))} />
        <Row label="工具调用" value={String(Math.max(toolCalls, conv?.tool_call_count ?? 0))} />
        <div
          className="flex items-center justify-between gap-3"
          title={`上下文使用率：${pct}%\n当前约 ${Math.round(approxTokens / 1000)}k / ${Math.round(safeLimit / 1000)}k tokens。\n包含系统提示词、116项EDA工具规范、当前工程与对话历史。接近 100% 时建议开启新对话。`}
        >
          <dt className="text-slate-500 cursor-help" title="反映当前会话的消息、工具调用与系统提示词占大模型上下文窗口的比例">上下文使用</dt>
          <dd className="flex items-center gap-2">
            <span className="h-1.5 w-20 overflow-hidden rounded-full bg-slate-100">
              <span className={`block h-full rounded-full ${pct > 80 ? "bg-rose-500" : "bg-brand-500"}`} style={{ width: `${Math.max(2, pct)}%` }} />
            </span>
            <span className="w-8 text-right font-medium text-ink">{pct}%</span>
          </dd>
        </div>
        <Row label="会话时长" value={running ? `运行中 ${durationText}` : durationText} mono />
      </dl>
    </section>
  );
}

function Row({ label, value, mono }: { label: string; value: string; mono?: boolean }) {
  return (
    <div className="flex items-center justify-between">
      <dt className="text-slate-500">{label}</dt>
      <dd className={`font-medium text-ink ${mono ? "font-mono" : ""}`}>{value}</dd>
    </div>
  );
}

function OnlineCard() {
  const { users, count } = usePresence();
  const shown = users.slice(0, 5);
  return (
    <section className="card p-3">
      <h4 className="text-xs font-medium text-slate-500">在线用户 ({count})</h4>
      <div className="mt-2 flex items-center">
        {shown.map((u, i) => (
          <div key={u.id} className="relative" style={{ marginLeft: i ? -8 : 0 }} title={`${u.display_name}${u.status === "busy" ? " · 处理中" : ""}`}>
            <Avatar name={u.display_name} color={u.avatar_color} size={30} ring />
            <span className={`absolute -bottom-0.5 -right-0.5 h-2.5 w-2.5 rounded-full ring-2 ring-white ${u.status === "busy" ? "bg-amber-400" : "bg-emerald-500"}`} />
          </div>
        ))}
        {count > shown.length && (
          <div className="ml-[-8px] flex h-[30px] w-[30px] items-center justify-center rounded-full bg-slate-100 text-[11px] font-medium text-slate-500 ring-2 ring-white">+{count - shown.length}</div>
        )}
        {count === 0 && <span className="text-[11px] text-slate-400">连接中…</span>}
      </div>
    </section>
  );
}
