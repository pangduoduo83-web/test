import { X } from "lucide-react";
import { useEffect, type ReactNode } from "react";
import clsx from "clsx";

interface Props {
  open: boolean;
  onClose: () => void;
  title: string;
  subtitle?: string;
  children: ReactNode;
  width?: string;
  footer?: ReactNode;
  /**
   * "body" (default): the whole body scrolls as one block.
   * "inner": the body is a fixed-height flex column and children own their
   * scroll areas (lets a side nav / search bar stay put while a list scrolls).
   */
  scroll?: "body" | "inner";
}

export function Modal({ open, onClose, title, subtitle, children, width = "max-w-2xl", footer, scroll = "body" }: Props) {
  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => e.key === "Escape" && onClose();
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [open, onClose]);

  if (!open) return null;
  return (
    <div className="animate-fade-in fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 p-4 backdrop-blur-[2px]" onMouseDown={onClose}>
      <div className={clsx("card animate-modal-in flex max-h-[92vh] w-full flex-col overflow-hidden", width)} onMouseDown={(e) => e.stopPropagation()}>
        <div className="flex items-start justify-between border-b border-line px-5 py-4">
          <div>
            <h2 className="text-base font-semibold text-ink">{title}</h2>
            {subtitle && <p className="mt-0.5 text-xs text-muted">{subtitle}</p>}
          </div>
          <button onClick={onClose} className="rounded-lg p-1.5 text-muted hover:bg-slate-100 hover:text-ink" aria-label="关闭">
            <X size={18} />
          </button>
        </div>
        <div className={clsx("min-h-0 flex-1 px-5 py-4", scroll === "inner" ? "flex flex-col overflow-hidden" : "overflow-y-auto")}>{children}</div>
        {footer && <div className="border-t border-line px-5 py-3">{footer}</div>}
      </div>
    </div>
  );
}
