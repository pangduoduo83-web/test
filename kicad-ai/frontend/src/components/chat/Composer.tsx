import { Code2, Crosshair, Image, Loader2, Paperclip, Quote, Send, Square, Wrench, X } from "lucide-react";
import { useEffect, useRef, useState, type KeyboardEvent } from "react";
import type { ModelOption, ThinkingMode } from "@/lib/types";
import { useChat } from "@/store/chat";
import { useProjects } from "@/store/projects";
import { ModelSelector, ThinkingSelector } from "./ModelSelector";

interface Props {
  draft: string;
  onDraftChange: (v: string) => void;
  onOpenTools: () => void;
  onAttach: () => void;
  modelPresets?: ModelOption[];
  serverModel?: string;
  serverThinking?: ThinkingMode;
  onSelectDesign: () => void;
}

export function Composer({ draft, onDraftChange, onOpenTools, onAttach, modelPresets, serverModel, serverThinking, onSelectDesign }: Props) {
  const { send, cancel, running, reconnecting, pendingInterrupt } = useChat();
  const selection = useProjects((s) => s.selection);
  const setSelection = useProjects((s) => s.setSelection);
  const ref = useRef<HTMLTextAreaElement>(null);
  const [focused, setFocused] = useState(false);
  // "Stop" now asks the server; the run ends when its cancellation event arrives.
  const [stopping, setStopping] = useState(false);
  useEffect(() => {
    if (!running) setStopping(false);
  }, [running]);

  useEffect(() => {
    const el = ref.current;
    if (!el) return;
    el.style.height = "auto";
    el.style.height = `${Math.min(180, el.scrollHeight)}px`;
  }, [draft]);

  const submit = () => {
    const text = draft.trim();
    if (!text || running) return;
    onDraftChange("");
    send(text);
  };

  const onKey = (e: KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === "Enter" && !e.shiftKey && !e.nativeEvent.isComposing) {
      e.preventDefault();
      submit();
    }
  };

  const wrap = (before: string, after = before) => {
    const el = ref.current;
    if (!el) return;
    const { selectionStart: s, selectionEnd: e } = el;
    const sel = draft.slice(s, e) || "";
    onDraftChange(draft.slice(0, s) + before + sel + after + draft.slice(e));
    requestAnimationFrame(() => el.setSelectionRange(s + before.length, s + before.length + sel.length));
  };

  return (
    <div className="border-t border-line px-5 py-3.5">
      <div className={`rounded-2xl border bg-white transition-[border-color,box-shadow] duration-200 ${focused ? "border-brand-300 shadow-[0_0_0_3px_rgba(99,102,241,0.12)]" : "border-line"}`}>
        {selection && (
          <div className="flex items-center gap-2 border-b border-brand-100 bg-brand-50/60 px-3.5 py-2 text-[11.5px] text-brand-700">
            <Crosshair size={13} className="shrink-0" />
            <button type="button" onClick={onSelectDesign} className="font-medium hover:underline">
              已选 {selection.mode === "pcb" ? "PCB 封装" : "原理图符号"} {selection.references.length} 个
            </button>
            <div className="flex min-w-0 flex-1 gap-1 overflow-hidden">
              {selection.references.slice(0, 8).map((reference) => (
                <span key={reference} className="rounded bg-white/90 px-1.5 py-0.5 font-mono text-[10.5px] text-brand-600">
                  {reference}
                </span>
              ))}
              {selection.references.length > 8 && <span className="py-0.5 text-[10.5px]">+{selection.references.length - 8}</span>}
            </div>
            <button
              type="button"
              onClick={() => setSelection(null)}
              className="shrink-0 rounded p-0.5 text-brand-400 hover:bg-brand-100 hover:text-brand-700"
              aria-label="清除设计选区"
            >
              <X size={13} />
            </button>
          </div>
        )}
        <textarea
          ref={ref}
          value={draft}
          onChange={(e) => onDraftChange(e.target.value)}
          onKeyDown={onKey}
          onFocus={() => setFocused(true)}
          onBlur={() => setFocused(false)}
          rows={1}
          placeholder={
            pendingInterrupt
              ? "代理正在等待你批准上方的操作…"
              : reconnecting
                ? "连接中断，正在恢复… 任务仍在后台运行"
                : running
                  ? "AI 正在执行设计任务（刷新或切换对话不会中断），点击右侧 ■ 可停止…"
                  : "输入消息，按 Enter 发送，Shift + Enter 换行"
          }
          className="block w-full resize-none bg-transparent px-4 pt-3.5 pb-2 text-[13.5px] leading-relaxed text-ink outline-none placeholder:text-slate-400"
        />
        <div className="flex items-center justify-between px-3 pb-2.5">
          <div className="flex flex-wrap items-center gap-1.5 text-slate-500">
            <ModelSelector presets={modelPresets} serverModel={serverModel} />
            <ThinkingSelector presets={modelPresets} serverThinking={serverThinking} />
            <span className="mx-0.5 h-3.5 w-[1px] bg-line" />
            <ToolBtn icon={<Paperclip size={14} />} label="附件" onClick={onAttach} />
            <ToolBtn icon={<Image size={14} />} label="图片" onClick={() => onDraftChange(draft + (draft ? "\n" : "") + "请重点分析此区域电路与封装...")} />
            <ToolBtn icon={<Code2 size={14} />} label="代码" onClick={() => wrap("```\n", "\n```")} />
            <ToolBtn icon={<Quote size={14} />} label="引用" onClick={() => wrap("> ", "")} />
            <ToolBtn icon={<Wrench size={14} />} label="工具" onClick={onOpenTools} />
          </div>
          {running ? (
            <button
              onClick={() => {
                setStopping(true);
                void cancel();
              }}
              disabled={stopping}
              className={`animate-pop flex h-9 items-center justify-center gap-1.5 rounded-xl px-3 text-white transition-colors ${stopping ? "bg-slate-500" : "bg-slate-800 hover:bg-slate-700"}`}
              title={stopping ? "正在停止" : "停止"}
            >
              {stopping ? <Loader2 size={14} className="animate-spin" /> : <Square size={14} fill="currentColor" />}
              <span className="text-[12px] font-medium">{stopping ? "停止中…" : "停止"}</span>
            </button>
          ) : (
            <button onClick={submit} disabled={!draft.trim()} className="flex h-9 w-9 items-center justify-center rounded-xl bg-brand-500 text-white transition-all duration-200 hover:bg-brand-600 active:scale-95 disabled:opacity-40" title="发送">
              <Send size={15} />
            </button>
          )}
        </div>
      </div>
    </div>
  );
}

function ToolBtn({ icon, label, onClick }: { icon: React.ReactNode; label: string; onClick?: () => void }) {
  return (
    <button onClick={onClick} className="flex items-center gap-1 rounded-md px-2 py-1 text-[12px] hover:bg-slate-100 hover:text-ink">
      {icon}
      {label}
    </button>
  );
}
