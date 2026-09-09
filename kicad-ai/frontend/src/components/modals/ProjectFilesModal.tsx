import { CircuitBoard, Cpu, Download, FileArchive, FileText, Loader2, RefreshCw } from "lucide-react";
import { useCallback, useEffect, useState } from "react";
import { Modal } from "@/components/ui/Modal";
import { downloadAuthenticated, request } from "@/lib/api";
import { humanBytes } from "@/lib/format";
import { useProjects } from "@/store/projects";

interface Entry {
  path: string;
  size: number;
  kind: "pcb" | "schematic" | "project" | "other";
  modified_at: string | null;
  generator_version?: string | null;
  format_version?: number | null;
  kicad_major?: number | null;
  compatibility?: "compatible" | "incompatible" | "cli_unavailable" | "unknown" | "not_applicable" | null;
  compatible?: boolean | null;
  compatibility_message?: string | null;
  runtime_version?: string | null;
}

const KIND_LABEL: Record<Entry["kind"], string> = { pcb: "PCB", schematic: "原理图", project: "工程文件", other: "其他" };

export function ProjectFilesModal({ open, onClose }: { open: boolean; onClose: () => void }) {
  const { projects, activeProjectId, previewVersion } = useProjects();
  const project = projects.find((p) => p.id === activeProjectId);
  const [files, setFiles] = useState<Entry[]>([]);
  const [busy, setBusy] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    if (!project) return;
    try {
      setFiles(await request<Entry[]>(`/api/projects/${project.id}/files`));
      setError(null);
    } catch (e) {
      setError((e as Error).message);
    }
  }, [project]);

  useEffect(() => {
    if (open) load();
  }, [open, load, previewVersion]);

  const dl = async (key: string, url: string, filename: string) => {
    setBusy(key);
    try {
      await downloadAuthenticated(url, filename);
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(null);
    }
  };

  return (
    <Modal
      open={open}
      onClose={onClose}
      title="工程文件"
      subtitle="Agent 修改的就是这些真实的 KiCad 文件。下载整个工程后解压，双击 .kicad_pro 即可在 KiCad 中打开。"
      width="max-w-2xl"
      footer={
        project && (
          <div className="flex items-center justify-between">
            <button onClick={load} className="flex items-center gap-1.5 rounded-lg px-3 py-1.5 text-[12.5px] text-slate-500 hover:bg-slate-100">
              <RefreshCw size={14} /> 刷新
            </button>
            <button
              onClick={() => dl("zip", `/api/projects/${project.id}/archive`, `${project.name}.zip`)}
              disabled={busy === "zip"}
              className="flex items-center gap-1.5 rounded-lg bg-brand-500 px-4 py-2 text-[13px] font-medium text-white hover:bg-brand-600 disabled:opacity-50"
            >
              {busy === "zip" ? <Loader2 size={15} className="animate-spin" /> : <FileArchive size={15} />} 下载整个工程 (ZIP)
            </button>
          </div>
        )
      }
    >
      {!project ? (
        <p className="py-8 text-center text-[13px] text-slate-400">请先选择一个工程</p>
      ) : (
        <div className="space-y-3">
          {error && <p className="rounded-lg bg-rose-50 px-3 py-2 text-[12.5px] text-rose-600">{error}</p>}
          <div className="flex items-center justify-between rounded-lg bg-slate-50 px-3 py-2 text-[11.5px] text-slate-500">
            <span>工程格式会在运行 DRC、渲染和导出前自动校验</span>
            <span className="font-mono">
              服务器 kicad-cli：{files.find((f) => f.runtime_version)?.runtime_version ?? "未安装"}
            </span>
          </div>
          <ul className="divide-y divide-line rounded-xl border border-line">
            {files.map((f) => {
              const name = f.path.split("/").pop() ?? f.path;
              const Icon = f.kind === "pcb" ? CircuitBoard : f.kind === "schematic" ? Cpu : FileText;
              const isDesign = f.kind === "pcb" || f.kind === "schematic";
              const compatibilityLabel =
                f.compatibility === "compatible"
                  ? "服务器兼容"
                  : f.compatibility === "incompatible"
                    ? "版本不兼容"
                    : f.compatibility === "cli_unavailable"
                      ? "CLI 不可用"
                      : "版本未知";
              return (
                <li key={f.path} className="flex items-center gap-3 px-4 py-2.5">
                  <span className={`flex h-8 w-8 items-center justify-center rounded-lg ${f.kind === "pcb" ? "bg-emerald-50 text-emerald-600" : f.kind === "schematic" ? "bg-violet-50 text-violet-600" : "bg-slate-100 text-slate-500"}`}>
                    <Icon size={15} />
                  </span>
                  <div className="min-w-0 flex-1">
                    <div className="flex items-center gap-2 text-[13px]">
                      <span className="truncate font-medium text-ink">{name}</span>
                      <span className="rounded bg-slate-100 px-1.5 py-0.5 text-[10.5px] text-slate-500">{KIND_LABEL[f.kind]}</span>
                      {isDesign && f.kicad_major && (
                        <span className="rounded bg-blue-50 px-1.5 py-0.5 text-[10.5px] text-blue-600">KiCad {f.kicad_major}</span>
                      )}
                      {isDesign && (
                        <span
                          title={f.compatibility_message ?? undefined}
                          className={`rounded px-1.5 py-0.5 text-[10.5px] ${
                            f.compatible === true
                              ? "bg-emerald-50 text-emerald-600"
                              : f.compatible === false
                                ? "bg-rose-50 text-rose-600"
                                : "bg-amber-50 text-amber-600"
                          }`}
                        >
                          {compatibilityLabel}
                        </span>
                      )}
                    </div>
                    <div className="text-[11px] text-slate-400">
                      {humanBytes(f.size)}
                      {f.modified_at && ` · 修改于 ${new Date(f.modified_at).toLocaleString()}`}
                      {isDesign && f.generator_version && ` · 生成器 ${f.generator_version}`}
                    </div>
                    {isDesign && f.compatible !== true && f.compatibility_message && (
                      <div className={`mt-1 text-[10.5px] ${f.compatible === false ? "text-rose-500" : "text-amber-500"}`}>
                        {f.compatibility_message}
                      </div>
                    )}
                  </div>
                  <button
                    onClick={() => dl(f.path, `/api/projects/${project.id}/download?file=${encodeURIComponent(f.path)}`, name)}
                    disabled={busy === f.path}
                    className="flex items-center gap-1 rounded-md border border-line px-2.5 py-1 text-[12px] text-slate-600 hover:bg-slate-50 disabled:opacity-50"
                  >
                    {busy === f.path ? <Loader2 size={13} className="animate-spin" /> : <Download size={13} />} 下载
                  </button>
                </li>
              );
            })}
            {files.length === 0 && <li className="px-4 py-8 text-center text-[13px] text-slate-400">工程目录为空</li>}
          </ul>
          <p className="text-[11.5px] text-slate-400">ZIP 不包含自动快照（`.versions/`）与 KiCad 备份目录；如需回滚请使用「会话快照」。</p>
        </div>
      )}
    </Modal>
  );
}
