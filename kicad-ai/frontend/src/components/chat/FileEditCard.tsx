import clsx from "clsx";
import { AlertCircle, Check, ChevronDown, ChevronRight, CircuitBoard, Cpu, Download, File, FileCode2, FileText, Loader2, RotateCcw, Settings2, type LucideIcon } from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import { downloadAuthenticated } from "@/lib/api";
import { summarizeArgs, toolLabel } from "@/lib/format";
import type { DiffLine, FileDiff, ToolCall } from "@/lib/types";
import { useProjects } from "@/store/projects";

const TEXT_EDIT_TOOLS = new Set(["write_file", "edit_file"]);
const COLLAPSED_PREVIEW_LINES = 3;

/** Tool calls rendered as an edit card instead of a row in the tool list. */
export function isEditCall(call: ToolCall): boolean {
  if (call.name === "submit_change_plan") return false; // has its own ChangePlanCard
  if (call.diff) return true;
  if (TEXT_EDIT_TOOLS.has(call.name)) return true;
  if (call.kind === "file_mutation") return true;
  return call.name === "restore_file_version";
}

// ---------------------------------------------------------------------------
// Partial-JSON helpers: while the model streams tool arguments we only have a
// prefix of the JSON text. Pull out one string field (possibly unterminated)
// so the card can show the file content as it is being written.
// ---------------------------------------------------------------------------
function partialField(text: string | undefined, key: string): string | undefined {
  if (!text) return undefined;
  const idx = text.indexOf(`"${key}"`);
  if (idx < 0) return undefined;
  const colon = text.indexOf(":", idx + key.length + 2);
  if (colon < 0) return undefined;
  let i = colon + 1;
  while (i < text.length && /\s/.test(text[i])) i++;
  if (text[i] !== '"') return undefined;
  i++;
  let out = "";
  while (i < text.length) {
    const ch = text[i];
    if (ch === "\\") {
      const nxt = text[i + 1];
      if (nxt === undefined) break;
      if (nxt === "n") out += "\n";
      else if (nxt === "t") out += "\t";
      else if (nxt === "r") out += "";
      else if (nxt === "u") {
        const hex = text.slice(i + 2, i + 6);
        if (hex.length < 4) break;
        out += String.fromCharCode(parseInt(hex, 16));
        i += 6;
        continue;
      } else out += nxt;
      i += 2;
      continue;
    }
    if (ch === '"') break;
    out += ch;
    i++;
  }
  return out;
}

function fileNameOf(path: string | undefined): { name: string; dir: string } {
  if (!path) return { name: "", dir: "" };
  const norm = path.replace(/\\/g, "/");
  const parts = norm.split("/");
  const name = parts.pop() ?? norm;
  return { name, dir: parts.join("/") };
}

function iconFor(name: string): { Icon: LucideIcon; tone: string } {
  if (name.endsWith(".kicad_pcb")) return { Icon: CircuitBoard, tone: "bg-emerald-50 text-emerald-600" };
  if (name.endsWith(".kicad_sch")) return { Icon: Cpu, tone: "bg-violet-50 text-violet-600" };
  if (name.endsWith(".kicad_pro")) return { Icon: Settings2, tone: "bg-sky-50 text-sky-600" };
  if (name.endsWith(".md")) return { Icon: FileText, tone: "bg-amber-50 text-amber-600" };
  if (/\.(py|ts|tsx|js|json|yml|yaml|toml)$/.test(name)) return { Icon: FileCode2, tone: "bg-indigo-50 text-indigo-600" };
  return { Icon: File, tone: "bg-slate-100 text-slate-500" };
}

function num(v: unknown): string {
  return typeof v === "number" ? String(Math.round(v * 1000) / 1000) : String(v ?? "");
}

/** Second header line: what the edit does, in human terms. */
function describeEdit(call: ToolCall, argsText?: string): string {
  const a = (call.args ?? {}) as Record<string, unknown>;
  const n = call.name;
  const refs = Array.isArray(a.references) ? (a.references as unknown[]).join(", ") : undefined;
  if (n === "set_footprint_position" || n === "move_component") {
    if (a.reference === undefined) return "";
    const rot = a.rotation !== undefined ? ` ∠${num(a.rotation)}°` : "";
    return `${String(a.reference)} → (${num(a.x)}, ${num(a.y)})${rot}`;
  }
  if (n === "move_footprints_by_delta") return `${refs ?? ""} Δ(${num(a.dx)}, ${num(a.dy)})`;
  if (n === "align_footprints" || n === "distribute_footprints") return [refs, a.axis ?? a.direction ?? a.mode].filter(Boolean).join(" · ");
  if (n === "flip_footprint") return `${String(a.reference ?? "")} 翻转到另一层`;
  if (n === "set_footprint_property" || n === "set_symbol_property") return `${String(a.reference ?? "")}.${String(a.property ?? a.name ?? "")} = ${JSON.stringify(a.value ?? "")}`;
  if (n === "add_symbol_to_schematic") return `${String(a.lib_id ?? a.symbol ?? "")} @ (${num(a.x)}, ${num(a.y)})`;
  if (n === "add_wire_to_schematic" || n === "connect_points_with_wire") return `(${num(a.x1 ?? a.start_x)}, ${num(a.y1 ?? a.start_y)}) → (${num(a.x2 ?? a.end_x)}, ${num(a.y2 ?? a.end_y)})`;
  if (n === "connect_pins_with_wire") return `${String(a.from_reference ?? a.ref1 ?? "")}.${String(a.from_pin ?? a.pin1 ?? "")} → ${String(a.to_reference ?? a.ref2 ?? "")}.${String(a.to_pin ?? a.pin2 ?? "")}`;
  if (n === "add_label_to_schematic") return `标签 "${String(a.text ?? a.label ?? "")}" @ (${num(a.x)}, ${num(a.y)})`;
  if (n === "restore_file_version") return `恢复到快照 ${String(a.version_id ?? "")}`;
  if (n === "edit_file") {
    const oldS = (a.old_string as string | undefined) ?? partialField(argsText, "old_string");
    return oldS ? `替换 "${oldS.split("\n")[0].slice(0, 40)}${oldS.length > 40 ? "…" : ""}"` : "";
  }
  if (n === "write_file") return "写入文件";
  return summarizeArgs(n, call.args);
}

interface Props {
  call: ToolCall;
}

export function FileEditCard({ call }: Props) {
  const { projects, activeProjectId } = useProjects();
  const project = projects.find((p) => p.id === activeProjectId);
  const running = call.status === "running";
  const failed = call.status === "error";
  const [open, setOpen] = useState(false);
  // Collapse automatically when the edit finishes (unless the user opened it).
  const [userToggled, setUserToggled] = useState(false);
  useEffect(() => {
    if (!userToggled) setOpen(running);
  }, [running, userToggled]);

  const isText = TEXT_EDIT_TOOLS.has(call.name);
  const argsPath =
    (call.args?.file_path as string | undefined) ??
    (call.args?.pcb_path as string | undefined) ??
    (call.args?.schematic_path as string | undefined) ??
    (call.args?.project_path as string | undefined) ??
    partialField(call.argsText, "file_path") ??
    call.diff?.relpath;
  const fallbackPath = call.diff?.relpath ?? (call.category?.startsWith("sch") ? project?.schematic_file : call.category === "drc" ? project?.pro_file ?? project?.pcb_file : project?.pcb_file);
  const filePath = argsPath ?? fallbackPath;
  const { name, dir } = fileNameOf(filePath ?? undefined);
  const { Icon, tone } = iconFor(name);
  const description = describeEdit(call, call.argsText);
  const label = toolLabel(call.name);

  const [dlBusy, setDlBusy] = useState(false);
  const handleDownload = async (e: React.MouseEvent) => {
    e.stopPropagation();
    if (!project || !filePath || dlBusy) return;
    setDlBusy(true);
    try {
      const dlName = name || "file";
      await downloadAuthenticated(`/api/projects/${project.id}/download?file=${encodeURIComponent(filePath)}`, dlName);
    } catch {
      /* ignore */
    } finally {
      setDlBusy(false);
    }
  };

  // streaming body for text edits (deepagents write_file / edit_file)
  const streamed = useMemo(() => {
    if (!isText) return null;
    const a = (call.args ?? {}) as Record<string, string | undefined>;
    if (call.name === "write_file") {
      const content = a.content ?? partialField(call.argsText, "content");
      return content === undefined ? null : { removed: [] as string[], added: content.split("\n") };
    }
    const oldS = a.old_string ?? partialField(call.argsText, "old_string");
    const newS = a.new_string ?? partialField(call.argsText, "new_string");
    if (oldS === undefined && newS === undefined) return null;
    return { removed: (oldS ?? "").split("\n"), added: (newS ?? "").split("\n") };
  }, [isText, call.name, call.args, call.argsText]);

  const diff = call.diff;
  const counts = diff ? { add: diff.additions, del: diff.deletions } : streamed ? { add: streamed.added.filter(Boolean).length, del: streamed.removed.filter(Boolean).length } : null;
  const errorText = failed ? errorOf(call) : null;
  const hasBody = !!diff?.hunks.length || !!streamed || !!errorText || (!!call.result && !diff);

  return (
    <div
      className={clsx(
        "edit-card mt-2 w-full max-w-[560px] overflow-hidden rounded-2xl border bg-white text-[12px] shadow-[0_1px_2px_rgba(0,0,0,0.03)] transition-colors",
        failed ? "border-rose-200" : running ? "border-brand-200" : "border-line",
      )}
    >
      <button
        onClick={() => {
          setUserToggled(true);
          setOpen((v) => !v);
        }}
        className="flex w-full items-center gap-2.5 px-3 py-2 text-left hover:bg-slate-50/70"
      >
        <span className={clsx("flex h-7 w-7 shrink-0 items-center justify-center rounded-lg", tone)}>
          <Icon size={14} />
        </span>
        <span className="min-w-0 flex-1">
          <span className="flex items-baseline gap-1.5">
            <span className="truncate font-medium text-ink">{name || label}</span>
            {dir && <span className="truncate text-[10.5px] text-slate-400">{dir}</span>}
          </span>
          <span className="flex items-center gap-1.5 text-[11px] text-slate-500">
            <span className={clsx("rounded px-1 py-[1px] text-[10px] font-medium", failed ? "bg-rose-50 text-rose-600" : running ? "bg-brand-50 text-brand-600" : "bg-slate-100 text-slate-500")}>{label}</span>
            {description && <span className="truncate">{description}</span>}
          </span>
        </span>
        <span className="flex shrink-0 items-center gap-2">
          {running ? (
            <span className="flex items-center gap-1 text-[11px] text-brand-600">
              <Loader2 size={12} className="animate-spin" /> 编辑中…
            </span>
          ) : failed ? (
            <span className="flex items-center gap-1 text-[11px] text-rose-600">
              <AlertCircle size={13} /> 失败
            </span>
          ) : (
            <>
              {counts && (
                <span className="font-mono text-[11px] tabular-nums">
                  <span className="text-emerald-600">+{counts.add}</span> <span className="text-rose-500">−{counts.del}</span>
                </span>
              )}
              {project && filePath && (
                <span
                  role="button"
                  tabIndex={0}
                  onClick={handleDownload}
                  className="flex h-5 w-5 items-center justify-center rounded text-slate-400 hover:bg-slate-200/70 hover:text-brand-600 transition-colors"
                  title={`下载 ${name || "文件"}`}
                >
                  {dlBusy ? <Loader2 size={11} className="animate-spin" /> : <Download size={12} />}
                </span>
              )}
              <span className="flex h-4 w-4 items-center justify-center rounded-full bg-emerald-500 text-white">
                <Check size={10} strokeWidth={3} />
              </span>
            </>
          )}
          {hasBody && (open ? <ChevronDown size={14} className="text-slate-400" /> : <ChevronRight size={14} className="text-slate-400" />)}
        </span>
      </button>
      {running && <div className="progress-bar h-[2px] w-full bg-brand-100" />}

      {/* collapsed one-glance preview of the change */}
      {!open && !running && diff && diff.hunks.length > 0 && <DiffPreview diff={diff} onExpand={() => (setUserToggled(true), setOpen(true))} />}

      <div className={clsx("collapse-grid", open && hasBody && "is-open")}>
        <div className="min-h-0 overflow-hidden">
          {errorText && <div className="border-t border-rose-100 bg-rose-50/60 px-3 py-2 text-[11.5px] text-rose-700">{errorText}</div>}
          {streamed && (!diff || running) && <StreamedBody removed={streamed.removed} added={streamed.added} running={running} />}
          {diff && diff.hunks.length > 0 && <DiffBody diff={diff} />}
          {diff && diff.hunks.length === 0 && !diff.changed && <div className="border-t border-line px-3 py-2 text-[11.5px] text-slate-400">文件内容没有变化</div>}
          {diff?.note && <div className="px-3 pb-2 text-[11px] text-slate-400">{diff.note}</div>}
          {!diff && !streamed && call.result && !failed && <ResultBody call={call} />}
        </div>
      </div>
    </div>
  );
}

function errorOf(call: ToolCall): string {
  const d = call.data as { error?: string } | undefined;
  if (d && typeof d === "object" && typeof d.error === "string") return d.error;
  return call.result?.slice(0, 400) ?? "工具执行失败";
}

export function DiffPreview({ diff, onExpand }: { diff: FileDiff; onExpand: () => void }) {
  const changed: DiffLine[] = [];
  let total = 0;
  for (const h of diff.hunks) {
    for (const l of h.lines) {
      if (l.t === " ") continue;
      total++;
      if (changed.length < COLLAPSED_PREVIEW_LINES) changed.push(l);
    }
  }
  if (!changed.length) return null;
  const rest = total - changed.length + (diff.truncated ? diff.additions + diff.deletions - total : 0);
  return (
    <div className="border-t border-line bg-[#fafbfe] font-mono text-[11px] leading-[1.7]">
      {changed.map((l, i) => (
        <Row key={i} line={l} compact />
      ))}
      {rest > 0 && (
        <button onClick={onExpand} className="w-full px-3 py-1 text-left text-[10.5px] text-brand-600 hover:bg-brand-50/50">
          展开全部差异（还有 {rest} 行变更）
        </button>
      )}
    </div>
  );
}

export function DiffBody({ diff }: { diff: FileDiff }) {
  return (
    <div className="border-t border-line bg-[#fafbfe] font-mono text-[11px] leading-[1.7]">
      {diff.hunks.map((h, hi) => (
        <div key={hi}>
          {hi > 0 && <div className="px-3 py-0.5 text-[10px] text-slate-300 select-none">···</div>}
          {h.lines.map((l, li) => (
            <Row key={li} line={l} />
          ))}
        </div>
      ))}
      {(diff.truncated || diff.normalized) && (
        <div className="flex flex-wrap gap-x-3 px-3 py-1 font-sans text-[10.5px] text-slate-400">
          {diff.truncated && <span>差异过长，仅显示前 {diff.hunks.reduce((n, h) => n + h.lines.length, 0)} 行</span>}
          {diff.normalized && <span>按规范化格式比较，已忽略纯排版差异</span>}
        </div>
      )}
    </div>
  );
}

function Row({ line, compact }: { line: DiffLine; compact?: boolean }) {
  const tone = line.t === "+" ? "bg-emerald-50/80 text-emerald-900" : line.t === "-" ? "bg-rose-50/80 text-rose-900" : "text-slate-500";
  const marker = line.t === "+" ? "text-emerald-500" : line.t === "-" ? "text-rose-400" : "text-transparent";
  return (
    <div className={clsx("flex", tone)}>
      {!compact && (
        <>
          <span className="w-9 shrink-0 select-none pr-1 text-right text-[10px] text-slate-300 tabular-nums">{line.o ?? ""}</span>
          <span className="w-9 shrink-0 select-none pr-1 text-right text-[10px] text-slate-300 tabular-nums">{line.n ?? ""}</span>
        </>
      )}
      <span className={clsx("w-4 shrink-0 select-none text-center", marker, compact && "ml-2")}>{line.t === " " ? "" : line.t}</span>
      <span className="min-w-0 flex-1 whitespace-pre-wrap break-all pr-3">{line.s || " "}</span>
    </div>
  );
}

function StreamedBody({ removed, added, running }: { removed: string[]; added: string[]; running: boolean }) {
  const MAX = 60;
  const tail = (arr: string[]) => (arr.length > MAX ? arr.slice(arr.length - MAX) : arr);
  return (
    <div className="border-t border-line bg-[#fafbfe] font-mono text-[11px] leading-[1.7]">
      {removed.length > MAX && <div className="px-3 text-[10px] text-slate-300">… {removed.length - MAX} 行</div>}
      {tail(removed).map((s, i) => (
        <Row key={`r${i}`} line={{ t: "-", s }} compact />
      ))}
      {added.length > MAX && <div className="px-3 text-[10px] text-slate-300">… {added.length - MAX} 行</div>}
      {tail(added).map((s, i) => (
        <div key={`a${i}`} className="flex bg-emerald-50/80 text-emerald-900">
          <span className="ml-2 w-4 shrink-0 select-none text-center text-emerald-500">+</span>
          <span className="min-w-0 flex-1 whitespace-pre-wrap break-all pr-3">
            {s || " "}
            {running && i === Math.min(added.length, MAX) - 1 && <span className="stream-caret" />}
          </span>
        </div>
      ))}
    </div>
  );
}

function ResultBody({ call }: { call: ToolCall }) {
  const d = call.data as Record<string, unknown> | undefined;
  const before = d?.before as Record<string, number> | undefined;
  const after = d?.after as Record<string, number> | undefined;
  return (
    <div className="border-t border-line px-3 py-2 text-[11.5px] text-slate-600">
      {before && after ? (
        <span className="flex items-center gap-1.5 font-mono">
          <RotateCcw size={11} className="text-slate-400" />({num(before.x)}, {num(before.y)}) → ({num(after.x)}, {num(after.y)})
        </span>
      ) : (
        <pre className="max-h-40 overflow-auto whitespace-pre-wrap break-all font-mono text-[11px]">{pretty(call.result ?? "")}</pre>
      )}
    </div>
  );
}

function pretty(text: string): string {
  try {
    return JSON.stringify(JSON.parse(text), null, 2);
  } catch {
    return text;
  }
}
