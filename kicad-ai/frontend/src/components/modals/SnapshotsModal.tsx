import { Camera, History, Loader2, RotateCcw } from "lucide-react";
import { useCallback, useEffect, useState } from "react";
import { Modal } from "@/components/ui/Modal";
import { api } from "@/lib/api";
import { humanBytes } from "@/lib/format";
import type { Snapshot } from "@/lib/types";
import { useProjects } from "@/store/projects";

export function SnapshotsModal({ open, onClose }: { open: boolean; onClose: () => void }) {
  const { projects, activeProjectId, bumpPreview } = useProjects();
  const project = projects.find((p) => p.id === activeProjectId);
  const [items, setItems] = useState<Snapshot[]>([]);
  const [busy, setBusy] = useState<string | null>(null);
  const [label, setLabel] = useState("");
  const [error, setError] = useState<string | null>(null);

  const reload = useCallback(async () => {
    if (!project) return setItems([]);
    try {
      setItems(await api.snapshots(project.id));
    } catch (e) {
      setError((e as Error).message);
    }
  }, [project]);

  useEffect(() => {
    if (open) reload();
  }, [open, reload]);

  const create = async () => {
    if (!project) return;
    setBusy("create");
    try {
      await api.createSnapshot(project.id, label || "manual snapshot");
      setLabel("");
      await reload();
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(null);
    }
  };

  const restore = async (s: Snapshot) => {
    if (!project || !confirm(`确定要把 ${s.file.split("/").pop()} 恢复到快照 ${s.version_id} 吗？当前版本会先自动备份。`)) return;
    setBusy(s.version_id);
    try {
      await api.restoreSnapshot(project.id, s.version_id, s.file);
      bumpPreview();
      await reload();
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(null);
    }
  };

  return (
    <Modal open={open} onClose={onClose} title="会话快照" subtitle="每次代理修改文件前都会自动保存快照，你也可以手动创建并随时回滚。" width="max-w-2xl">
      {!project ? (
        <p className="py-8 text-center text-xs text-slate-400">请先选择一个工程</p>
      ) : (
        <div className="space-y-4">
          <div className="flex gap-2">
            <input value={label} onChange={(e) => setLabel(e.target.value)} placeholder="快照说明（可选）" className="flex-1 rounded-lg border border-line px-3 py-2 text-sm outline-none focus:border-brand-300" />
            <button onClick={create} disabled={!!busy} className="flex items-center gap-1.5 rounded-lg bg-brand-500 px-3 py-2 text-xs font-medium text-white hover:bg-brand-600 disabled:opacity-50">
              {busy === "create" ? <Loader2 size={14} className="animate-spin" /> : <Camera size={14} />} 立即快照
            </button>
          </div>
          {error && <p className="rounded-md bg-rose-50 px-3 py-2 text-xs text-rose-600">{error}</p>}
          <ul className="divide-y divide-line rounded-xl border border-line">
            {items.length === 0 && <li className="px-4 py-8 text-center text-xs text-slate-400">还没有快照</li>}
            {items.map((s) => (
              <li key={`${s.file}-${s.version_id}`} className="flex items-center gap-3 px-4 py-2.5">
                <History size={15} className="shrink-0 text-slate-400" />
                <div className="min-w-0 flex-1">
                  <div className="flex items-center gap-2 text-[12.5px]">
                    <span className="font-medium text-ink">{s.label}</span>
                    <span className="truncate text-slate-400">{s.file.split("/").pop()}</span>
                  </div>
                  <div className="text-[11px] text-slate-400">
                    {new Date(s.created_at).toLocaleString()} · {humanBytes(s.size)} · <span className="font-mono">{s.version_id}</span>
                  </div>
                </div>
                <button onClick={() => restore(s)} disabled={!!busy} className="flex items-center gap-1 rounded-md border border-line px-2 py-1 text-[11px] text-slate-600 hover:bg-slate-50 disabled:opacity-50">
                  {busy === s.version_id ? <Loader2 size={12} className="animate-spin" /> : <RotateCcw size={12} />} 恢复
                </button>
              </li>
            ))}
          </ul>
        </div>
      )}
    </Modal>
  );
}
