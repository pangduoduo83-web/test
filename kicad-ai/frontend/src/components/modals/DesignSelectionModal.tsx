import { Check, Loader2, Minus, MousePointer2, Plus, ScanSearch, Trash2 } from "lucide-react";
import { useEffect, useMemo, useRef, useState, type PointerEvent as ReactPointerEvent } from "react";
import { Modal } from "@/components/ui/Modal";
import { api, fetchPreviewBlobUrl, fetchPreviewSchBlobUrl } from "@/lib/api";
import type { SelectionElement, SelectionMap } from "@/lib/types";
import { useProjects } from "@/store/projects";

type Point = { x: number; y: number };
type Drag = { start: Point; current: Point; additive: boolean };

function normalized(a: Point, b: Point): [number, number, number, number] {
  return [Math.min(a.x, b.x), Math.min(a.y, b.y), Math.max(a.x, b.x), Math.max(a.y, b.y)];
}

function intersects(a: readonly number[], b: readonly number[]) {
  return a[0] <= b[2] && a[2] >= b[0] && a[1] <= b[3] && a[3] >= b[1];
}

function contains(bbox: readonly number[], point: Point) {
  return point.x >= bbox[0] && point.x <= bbox[2] && point.y >= bbox[1] && point.y <= bbox[3];
}

export function DesignSelectionModal({ open, onClose }: { open: boolean; onClose: () => void }) {
  const { projects, activeProjectId, previewMode, selection, setSelection } = useProjects();
  const project = projects.find((p) => p.id === activeProjectId);
  const [map, setMap] = useState<SelectionMap | null>(null);
  const [url, setUrl] = useState<string | null>(null);
  const [selected, setSelected] = useState<string[]>([]);
  const [drag, setDrag] = useState<Drag | null>(null);
  const [zoom, setZoom] = useState(1);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const svgRef = useRef<SVGSVGElement>(null);

  useEffect(() => {
    if (!open || !project) {
      setMap(null);
      setSelected([]);
      setUrl((previous) => {
        if (previous) URL.revokeObjectURL(previous);
        return null;
      });
      return;
    }
    let cancelled = false;
    setLoading(true);
    setError(null);
    setZoom(1);
    const existing =
      selection?.project_id === project.id && selection.mode === previewMode ? selection.references : [];
    setSelected(existing);
    const preview =
      previewMode === "pcb"
        ? fetchPreviewBlobUrl(project.id, "builtin")
        : fetchPreviewSchBlobUrl(project.id, "builtin");
    Promise.all([api.selectionMap(project.id, previewMode), preview])
      .then(([nextMap, nextUrl]) => {
        if (cancelled) {
          URL.revokeObjectURL(nextUrl);
          return;
        }
        setMap(nextMap);
        setUrl((previous) => {
          if (previous) URL.revokeObjectURL(previous);
          return nextUrl;
        });
        setSelected((current) => current.filter((ref) => nextMap.elements.some((e) => e.reference === ref)));
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
  }, [open, project?.id, previewMode]); // selection is intentionally sampled only when opening

  useEffect(
    () => () => {
      if (url) URL.revokeObjectURL(url);
    },
    [url],
  );

  const selectedElements = useMemo(
    () => map?.elements.filter((element) => selected.includes(element.reference)) ?? [],
    [map, selected],
  );

  const pointFromEvent = (event: ReactPointerEvent<SVGSVGElement>): Point | null => {
    const svg = svgRef.current;
    const matrix = svg?.getScreenCTM();
    if (!svg || !matrix) return null;
    const point = svg.createSVGPoint();
    point.x = event.clientX;
    point.y = event.clientY;
    const transformed = point.matrixTransform(matrix.inverse());
    return { x: transformed.x, y: transformed.y };
  };

  const onPointerDown = (event: ReactPointerEvent<SVGSVGElement>) => {
    if (event.button !== 0) return;
    const point = pointFromEvent(event);
    if (!point) return;
    event.currentTarget.setPointerCapture(event.pointerId);
    setDrag({ start: point, current: point, additive: event.shiftKey || event.ctrlKey || event.metaKey });
  };

  const onPointerMove = (event: ReactPointerEvent<SVGSVGElement>) => {
    if (!drag) return;
    const point = pointFromEvent(event);
    if (point) setDrag({ ...drag, current: point });
  };

  const onPointerUp = (event: ReactPointerEvent<SVGSVGElement>) => {
    if (!drag || !map) return;
    const point = pointFromEvent(event) ?? drag.current;
    const rect = normalized(drag.start, point);
    const isClick = Math.hypot(point.x - drag.start.x, point.y - drag.start.y) < 4;
    let hits: SelectionElement[];
    if (isClick) {
      hits = map.elements
        .filter((element) => contains(element.bbox, point))
        .sort(
          (a, b) =>
            (a.bbox[2] - a.bbox[0]) * (a.bbox[3] - a.bbox[1]) -
            (b.bbox[2] - b.bbox[0]) * (b.bbox[3] - b.bbox[1]),
        )
        .slice(0, 1);
    } else {
      hits = map.elements.filter((element) => intersects(element.bbox, rect));
    }
    const hitRefs = hits.map((element) => element.reference);
    if (drag.additive) {
      setSelected((current) => {
        const next = new Set(current);
        for (const ref of hitRefs) {
          if (isClick && next.has(ref)) next.delete(ref);
          else next.add(ref);
        }
        return [...next];
      });
    } else {
      setSelected(hitRefs);
    }
    setDrag(null);
  };

  const confirm = () => {
    if (!project || !map || !selectedElements.length) return;
    const world = selectedElements.map((element) => element.world_bbox);
    const bounds: [number, number, number, number] = [
      Math.min(...world.map((bbox) => bbox[0])),
      Math.min(...world.map((bbox) => bbox[1])),
      Math.max(...world.map((bbox) => bbox[2])),
      Math.max(...world.map((bbox) => bbox[3])),
    ];
    setSelection({
      project_id: project.id,
      mode: map.mode,
      references: selectedElements.map((element) => element.reference),
      bounds,
    });
    onClose();
  };

  const dragRect = drag ? normalized(drag.start, drag.current) : null;

  return (
    <Modal
      open={open}
      onClose={onClose}
      title={`选择${previewMode === "pcb" ? " PCB 封装" : "原理图符号"}`}
      subtitle="单击选择一个对象；拖动框选多个对象；按住 Shift / Ctrl 可追加或取消选择。"
      width="max-w-6xl"
      scroll="inner"
      footer={
        <div className="flex items-center justify-between gap-3">
          <span className="text-xs text-slate-500">已选择 {selected.length} 个对象</span>
          <div className="flex items-center gap-2">
            <button
              type="button"
              onClick={() => setSelected([])}
              disabled={!selected.length}
              className="flex items-center gap-1.5 rounded-lg border border-line px-3 py-1.5 text-xs text-slate-600 hover:bg-slate-50 disabled:opacity-40"
            >
              <Trash2 size={13} /> 清空
            </button>
            <button
              type="button"
              onClick={confirm}
              disabled={!selected.length}
              className="flex items-center gap-1.5 rounded-lg bg-brand-500 px-4 py-1.5 text-xs font-medium text-white hover:bg-brand-600 disabled:opacity-40"
            >
              <Check size={13} /> 引用到对话
            </button>
          </div>
        </div>
      }
    >
      {!project ? (
        <div className="flex flex-1 items-center justify-center text-sm text-slate-400">请先选择工程</div>
      ) : loading ? (
        <div className="flex flex-1 items-center justify-center gap-2 text-sm text-slate-500">
          <Loader2 size={16} className="animate-spin" /> 正在加载可选对象…
        </div>
      ) : error || !map || !url ? (
        <div className="flex flex-1 items-center justify-center text-sm text-rose-500">{error ?? "预览不可用"}</div>
      ) : (
        <div className="flex min-h-0 flex-1 flex-col gap-3 md:flex-row">
          <div className="flex min-h-0 min-w-0 flex-1 flex-col overflow-hidden rounded-xl border border-line bg-slate-100">
            <div className="flex items-center justify-between border-b border-line bg-white px-3 py-2">
              <span className="flex items-center gap-1.5 text-xs font-medium text-slate-600">
                <ScanSearch size={14} className="text-brand-500" /> {map.project_name}
              </span>
              <div className="flex items-center gap-1">
                <button
                  type="button"
                  onClick={() => setZoom((value) => Math.max(1, value - 0.25))}
                  className="rounded p-1 text-slate-500 hover:bg-slate-100"
                  title="缩小"
                >
                  <Minus size={14} />
                </button>
                <span className="w-12 text-center text-[11px] text-slate-500">{Math.round(zoom * 100)}%</span>
                <button
                  type="button"
                  onClick={() => setZoom((value) => Math.min(3, value + 0.25))}
                  className="rounded p-1 text-slate-500 hover:bg-slate-100"
                  title="放大"
                >
                  <Plus size={14} />
                </button>
              </div>
            </div>
            <div className="min-h-0 flex-1 overflow-auto p-3">
              <div
                className="relative mx-auto select-none shadow-md"
                style={{
                  width: `${zoom * 100}%`,
                  minWidth: zoom > 1 ? `${zoom * 720}px` : undefined,
                  aspectRatio: `${map.canvas.width} / ${map.canvas.height}`,
                }}
              >
                <img src={url} alt="可选择的设计预览" draggable={false} className="absolute inset-0 h-full w-full" />
                <svg
                  ref={svgRef}
                  viewBox={`0 0 ${map.canvas.width} ${map.canvas.height}`}
                  className="absolute inset-0 h-full w-full touch-none cursor-crosshair"
                  onPointerDown={onPointerDown}
                  onPointerMove={onPointerMove}
                  onPointerUp={onPointerUp}
                  onPointerCancel={() => setDrag(null)}
                >
                  {selectedElements.map((element) => (
                    <rect
                      key={element.id}
                      x={element.bbox[0]}
                      y={element.bbox[1]}
                      width={Math.max(1, element.bbox[2] - element.bbox[0])}
                      height={Math.max(1, element.bbox[3] - element.bbox[1])}
                      rx={3}
                      fill="rgba(99,102,241,0.16)"
                      stroke="#6366f1"
                      strokeWidth={2}
                      vectorEffect="non-scaling-stroke"
                    />
                  ))}
                  {dragRect && (
                    <rect
                      x={dragRect[0]}
                      y={dragRect[1]}
                      width={dragRect[2] - dragRect[0]}
                      height={dragRect[3] - dragRect[1]}
                      fill="rgba(14,165,233,0.12)"
                      stroke="#0ea5e9"
                      strokeWidth={1.5}
                      strokeDasharray="6 4"
                      vectorEffect="non-scaling-stroke"
                    />
                  )}
                </svg>
              </div>
            </div>
          </div>
          <aside className="flex max-h-48 w-full shrink-0 flex-col overflow-hidden rounded-xl border border-line bg-white md:max-h-none md:w-64">
            <div className="flex items-center gap-2 border-b border-line px-3 py-2 text-xs font-medium text-slate-600">
              <MousePointer2 size={13} /> 已选对象
            </div>
            <div className="min-h-0 flex-1 overflow-y-auto p-2">
              {selectedElements.length ? (
                <ul className="space-y-1">
                  {selectedElements.map((element) => (
                    <li key={element.id} className="flex items-center justify-between gap-2 rounded-lg bg-brand-50 px-2.5 py-1.5 text-xs">
                      <span className="min-w-0 truncate text-brand-800" title={element.label}>{element.label}</span>
                      <button
                        type="button"
                        onClick={() => setSelected((current) => current.filter((ref) => ref !== element.reference))}
                        className="shrink-0 rounded px-1 text-brand-400 hover:bg-brand-100 hover:text-brand-700"
                        aria-label={`取消选择 ${element.reference}`}
                      >
                        ×
                      </button>
                    </li>
                  ))}
                </ul>
              ) : (
                <p className="px-2 py-6 text-center text-xs text-slate-400">尚未选择对象</p>
              )}
            </div>
          </aside>
        </div>
      )}
    </Modal>
  );
}
