import { ExternalLink, Loader2, Minus, Plus, RefreshCw } from "lucide-react";
import { useEffect, useState } from "react";
import { Modal } from "@/components/ui/Modal";
import { fetchPreviewBlobUrl, fetchPreviewSchBlobUrl } from "@/lib/api";
import type { DesignMode } from "@/lib/types";
import { useProjects } from "@/store/projects";

export function PreviewViewerModal({
  open,
  onClose,
  initialMode,
}: {
  open: boolean;
  onClose: () => void;
  initialMode: DesignMode;
}) {
  const { projects, activeProjectId, previewVersion } = useProjects();
  const project = projects.find((item) => item.id === activeProjectId);
  const [mode, setMode] = useState<DesignMode>(initialMode);
  const [url, setUrl] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [zoom, setZoom] = useState(1);
  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    if (open) {
      setMode(initialMode);
      setZoom(1);
    }
  }, [open, initialMode]);

  useEffect(() => {
    if (!open || !project) return;
    const hasFile = mode === "pcb" ? !!project.pcb_file : !!(project.schematic_file || project.pro_file);
    if (!hasFile) {
      setUrl(null);
      setError(mode === "pcb" ? "该工程没有 PCB 文件" : "该工程没有原理图文件");
      return;
    }
    let cancelled = false;
    setLoading(true);
    setError(null);
    const fetcher = mode === "pcb" ? fetchPreviewBlobUrl(project.id) : fetchPreviewSchBlobUrl(project.id);
    fetcher
      .then((next) => {
        if (cancelled) {
          URL.revokeObjectURL(next);
          return;
        }
        setUrl((previous) => {
          if (previous) URL.revokeObjectURL(previous);
          return next;
        });
      })
      .catch((reason) => {
        if (!cancelled) setError((reason as Error).message);
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [open, project?.id, project?.pcb_file, project?.schematic_file, project?.pro_file, mode, previewVersion, reloadKey]);

  useEffect(
    () => () => {
      if (url) URL.revokeObjectURL(url);
    },
    [url],
  );

  // Blob URLs only exist inside this tab, so a plain window.open on them fails.
  // Write a small standalone page into the new window and embed the SVG there.
  const openInNewWindow = async () => {
    if (!url || !project) return;
    const svg = await (await fetch(url)).text();
    const popup = window.open("", "_blank");
    if (!popup) return;
    const title = `${project.name} · ${mode === "pcb" ? "PCB" : "原理图"}预览`;
    popup.document.open();
    popup.document.write(
      `<!doctype html><html lang="zh-CN"><head><meta charset="utf-8"><title>${escapeHtml(title)}</title>`
      + `<style>html,body{margin:0;height:100%;background:${mode === "pcb" ? "#001d17" : "#f8fafc"};}`
      + `body{display:flex;align-items:center;justify-content:center;}`
      + `svg{max-width:100vw;max-height:100vh;width:auto;height:auto;}</style></head><body>${svg}</body></html>`,
    );
    popup.document.close();
  };

  return (
    <Modal
      open={open}
      onClose={onClose}
      title={project ? `${project.name} · 设计预览` : "设计预览"}
      subtitle="放大查看当前工程；Agent 修改文件后会自动刷新。"
      width="max-w-[96vw]"
      scroll="inner"
      footer={
        <div className="flex w-full flex-wrap items-center justify-between gap-2">
          <div className="flex items-center rounded-lg bg-slate-100 p-0.5 text-xs">
            {(["pcb", "sch"] as const).map((value) => (
              <button
                key={value}
                type="button"
                onClick={() => setMode(value)}
                className={`rounded-md px-3 py-1 ${mode === value ? "bg-white font-medium text-ink shadow-sm" : "text-slate-500"}`}
              >
                {value === "pcb" ? "PCB 布局" : "原理图"}
              </button>
            ))}
          </div>
          <div className="flex items-center gap-1.5">
            <button type="button" onClick={() => setZoom((value) => Math.max(0.5, value - 0.25))} className="rounded-md border border-line p-1.5 text-slate-600 hover:bg-slate-50" title="缩小">
              <Minus size={14} />
            </button>
            <span className="w-14 text-center text-xs text-slate-500">{Math.round(zoom * 100)}%</span>
            <button type="button" onClick={() => setZoom((value) => Math.min(4, value + 0.25))} className="rounded-md border border-line p-1.5 text-slate-600 hover:bg-slate-50" title="放大">
              <Plus size={14} />
            </button>
            <button type="button" onClick={() => setReloadKey((value) => value + 1)} className="rounded-md border border-line p-1.5 text-slate-600 hover:bg-slate-50" title="刷新预览">
              <RefreshCw size={14} className={loading ? "animate-spin" : ""} />
            </button>
            <button type="button" onClick={openInNewWindow} disabled={!url} className="flex items-center gap-1.5 rounded-md bg-brand-500 px-3 py-1.5 text-xs font-medium text-white hover:bg-brand-600 disabled:opacity-40">
              <ExternalLink size={13} /> 新窗口打开
            </button>
          </div>
        </div>
      }
    >
      <div className={`flex min-h-[70vh] flex-1 items-center justify-center overflow-auto rounded-xl ${mode === "pcb" ? "bg-[#001d17]" : "bg-slate-50"}`}>
        {loading && !url ? (
          <div className="flex items-center gap-2 text-sm text-slate-400">
            <Loader2 size={16} className="animate-spin" /> 正在生成预览…
          </div>
        ) : error ? (
          <div className="text-sm text-rose-500">{error}</div>
        ) : url ? (
          <img
            src={url}
            alt={mode === "pcb" ? "PCB 预览" : "原理图预览"}
            style={{ width: `${zoom * 100}%`, maxWidth: zoom <= 1 ? "100%" : undefined }}
            className="max-h-full object-contain"
          />
        ) : null}
      </div>
    </Modal>
  );
}

function escapeHtml(value: string) {
  return value.replace(/[&<>"']/g, (char) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[char] ?? char);
}
