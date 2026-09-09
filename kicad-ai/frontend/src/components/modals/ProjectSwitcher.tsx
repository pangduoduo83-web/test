import { Check, FolderOpen, Loader2, Plus, Trash2, Upload } from "lucide-react";
import { useEffect, useRef, useState } from "react";
import { Modal } from "@/components/ui/Modal";
import { api } from "@/lib/api";
import { useChat } from "@/store/chat";
import { useProjects } from "@/store/projects";

export function ProjectSwitcher({ open, onClose }: { open: boolean; onClose: () => void }) {
  const { projects, activeProjectId, setActive, loadSample, importZip, uploadFiles, createBlank, remove } = useProjects();
  const { activeId, conversations } = useChat();
  const [samples, setSamples] = useState<{ name: string; files: string[] }[]>([]);
  const [busy, setBusy] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [showNew, setShowNew] = useState(false);
  const [newName, setNewName] = useState("");
  const [newTitle, setNewTitle] = useState("");
  const zipRef = useRef<HTMLInputElement>(null);
  const filesRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (open) api.samples().then(setSamples).catch(() => setSamples([]));
  }, [open]);

  const choose = async (id: string) => {
    setActive(id);
    if (activeId && conversations.find((c) => c.id === activeId)?.project_id !== id) {
      api.updateConversation(activeId, { project_id: id }).catch(() => undefined);
    }
    onClose();
  };

  const run = async (label: string, fn: () => Promise<unknown>) => {
    setBusy(label);
    setError(null);
    try {
      await fn();
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(null);
    }
  };

  return (
    <Modal open={open} onClose={onClose} title="切换工程" subtitle="每个用户拥有独立的工作区；代理只能访问你自己的工程文件。" width="max-w-xl">
      <div className="space-y-4">
        <div className="flex flex-wrap gap-2">
          <button onClick={() => setShowNew((v) => !v)} disabled={!!busy} className="flex items-center gap-1.5 rounded-lg border border-emerald-300 bg-emerald-50 px-3 py-2 text-xs font-medium text-emerald-700 hover:bg-emerald-100 disabled:opacity-50">
            <Plus size={14} /> 新建空白工程
          </button>
          <button onClick={() => zipRef.current?.click()} disabled={!!busy} className="flex items-center gap-1.5 rounded-lg bg-brand-500 px-3 py-2 text-xs font-medium text-white hover:bg-brand-600 disabled:opacity-50">
            {busy === "zip" ? <Loader2 size={14} className="animate-spin" /> : <Upload size={14} />} 导入 ZIP 工程
          </button>
          <button onClick={() => filesRef.current?.click()} disabled={!!busy} className="flex items-center gap-1.5 rounded-lg border border-line px-3 py-2 text-xs font-medium text-slate-600 hover:bg-slate-50 disabled:opacity-50">
            <FolderOpen size={14} /> 上传 .kicad_pcb / .kicad_sch
          </button>
          {samples.map((s) => (
            <button key={s.name} onClick={() => run(`sample:${s.name}`, () => loadSample(s.name).then((p) => choose(p.id)))} disabled={!!busy} className="flex items-center gap-1.5 rounded-lg border border-dashed border-brand-200 bg-brand-50/50 px-3 py-2 text-xs font-medium text-brand-700 hover:bg-brand-50 disabled:opacity-50">
              {busy === `sample:${s.name}` ? <Loader2 size={14} className="animate-spin" /> : "✨"} 加载示例：{s.name}
            </button>
          ))}
          <input
            ref={zipRef}
            type="file"
            accept=".zip"
            className="hidden"
            onChange={(e) => {
              const f = e.target.files?.[0];
              if (f) run("zip", () => importZip(f).then((p) => choose(p.id)));
              e.target.value = "";
            }}
          />
          <input
            ref={filesRef}
            type="file"
            multiple
            accept=".kicad_pcb,.kicad_sch,.kicad_pro"
            className="hidden"
            onChange={(e) => {
              const files = Array.from(e.target.files ?? []);
              if (files.length) {
                const name = files[0].name.replace(/\.[^.]+$/, "");
                run("files", () => uploadFiles(files, name).then((p) => choose(p.id)));
              }
              e.target.value = "";
            }}
          />
        </div>
        {showNew && (
          <form
            onSubmit={(e) => {
              e.preventDefault();
              const n = newName.trim();
              if (!n) return;
              run("new", async () => {
                const p = await createBlank(n, newTitle.trim() || undefined);
                setNewName("");
                setNewTitle("");
                setShowNew(false);
                choose(p.id);
              });
            }}
            className="rounded-xl border border-emerald-200 bg-emerald-50/50 p-3 space-y-2.5"
          >
            <div className="flex items-center justify-between">
              <span className="text-xs font-semibold text-emerald-800">新建空白 KiCad 工程</span>
              <span className="text-[11px] text-emerald-600">将生成完整的 .kicad_pro / .kicad_sch / .kicad_pcb 文件</span>
            </div>
            <div className="flex gap-2">
              <input
                type="text"
                placeholder="工程英文标识 (如: esp32_sensor, buck_5v)"
                value={newName}
                onChange={(e) => setNewName(e.target.value)}
                className="flex-1 rounded-lg border border-line bg-white px-2.5 py-1.5 text-xs text-ink placeholder:text-slate-400 focus:border-brand-500 focus:outline-none"
                autoFocus
              />
              <input
                type="text"
                placeholder="工程标题 (可选，如: ESP32温湿度采集板)"
                value={newTitle}
                onChange={(e) => setNewTitle(e.target.value)}
                className="flex-1 rounded-lg border border-line bg-white px-2.5 py-1.5 text-xs text-ink placeholder:text-slate-400 focus:border-brand-500 focus:outline-none"
              />
            </div>
            <div className="flex items-center justify-end gap-2">
              <button
                type="button"
                onClick={() => {
                  setShowNew(false);
                  setNewName("");
                  setNewTitle("");
                }}
                className="rounded-lg border border-line bg-white px-2.5 py-1 text-xs text-slate-500 hover:bg-slate-50"
              >
                取消
              </button>
              <button
                type="submit"
                disabled={!newName.trim() || !!busy}
                className="flex items-center gap-1 rounded-lg bg-emerald-600 px-3 py-1 text-xs font-medium text-white hover:bg-emerald-700 disabled:opacity-50"
              >
                {busy === "new" ? <Loader2 size={12} className="animate-spin" /> : <Plus size={12} />} 创建并切换
              </button>
            </div>
          </form>
        )}
        {error && <p className="rounded-md bg-rose-50 px-3 py-2 text-xs text-rose-600">{error}</p>}

        <ul className="divide-y divide-line rounded-xl border border-line">
          {projects.length === 0 && <li className="px-4 py-8 text-center text-xs text-slate-400">还没有工程。导入一个 ZIP，或直接加载示例工程试试。</li>}
          {projects.map((p) => {
            const active = p.id === activeProjectId;
            return (
              <li key={p.id} className={`flex items-center gap-3 px-4 py-3 ${active ? "bg-brand-50/60" : ""}`}>
                <button onClick={() => choose(p.id)} className="flex min-w-0 flex-1 items-center gap-3 text-left">
                  <span className={`flex h-5 w-5 items-center justify-center rounded-full border ${active ? "border-brand-500 bg-brand-500 text-white" : "border-slate-300"}`}>{active && <Check size={12} strokeWidth={3} />}</span>
                  <div className="min-w-0">
                    <div className="truncate text-[13px] font-medium text-ink">{p.name}</div>
                    <div className="truncate text-[11px] text-slate-400">
                      /{p.rel_dir} · {[p.pcb_file && "PCB", p.schematic_file && "原理图", p.pro_file && "工程文件"].filter(Boolean).join(" / ") || "无 KiCad 文件"}
                    </div>
                  </div>
                </button>
                <button onClick={() => run(`del:${p.id}`, () => remove(p.id))} className="rounded p-1.5 text-slate-400 hover:bg-rose-50 hover:text-rose-500" title="移除工程（保留文件）">
                  {busy === `del:${p.id}` ? <Loader2 size={14} className="animate-spin" /> : <Trash2 size={14} />}
                </button>
              </li>
            );
          })}
        </ul>
      </div>
    </Modal>
  );
}
