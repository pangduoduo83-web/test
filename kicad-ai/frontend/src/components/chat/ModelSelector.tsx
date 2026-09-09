import clsx from "clsx";
import { Brain, Check, ChevronDown, Cpu, Sparkles } from "lucide-react";
import { useEffect, useRef, useState } from "react";
import type { ModelOption, ThinkingMode, ThinkingSelection } from "@/lib/types";
import { useChat } from "@/store/chat";

interface Props {
  presets?: ModelOption[];
  serverModel?: string;
}

export function ModelSelector({ presets = [], serverModel }: Props) {
  const { selectedModel, setSelectedModel } = useChat();
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const handleOutside = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) {
        setOpen(false);
      }
    };
    if (open) {
      document.addEventListener("mousedown", handleOutside);
      return () => document.removeEventListener("mousedown", handleOutside);
    }
  }, [open]);

  const isAuto = selectedModel === "auto";
  const activePreset = presets.find((p) => p.id === selectedModel);
  const displayLabel = isAuto ? "自动选择" : activePreset?.name.replace(/\s*\(.*?\)/, "") || selectedModel;

  return (
    <div className="relative inline-block" ref={ref}>
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        className={clsx(
          "flex items-center gap-1.5 rounded-lg border px-2 py-1 text-[11.5px] font-medium transition-all duration-150",
          isAuto
            ? "border-brand-200 bg-brand-50/60 text-brand-700 hover:bg-brand-50 hover:border-brand-300"
            : "border-line bg-white text-slate-700 hover:bg-slate-50 hover:border-slate-300",
        )}
        title={isAuto ? `当前自动调度（主模型：${serverModel || "默认"}）` : `指定模型：${selectedModel}`}
      >
        {isAuto ? <Sparkles size={12} className="text-brand-500" /> : <Cpu size={12} className="text-slate-500" />}
        <span>{displayLabel}</span>
        <ChevronDown size={11} className={clsx("text-slate-400 transition-transform duration-150", open && "rotate-180")} />
      </button>

      {open && (
        <div className="animate-fade-up absolute bottom-full left-0 z-50 mb-2 w-64 rounded-xl border border-line bg-white py-1.5 shadow-[0_8px_24px_rgba(0,0,0,0.12)]">
          <div className="px-3 py-1.5 text-[10.5px] font-semibold tracking-wider text-slate-400 uppercase">选择会话模型</div>

          <button
            type="button"
            onClick={() => {
              setSelectedModel("auto");
              setOpen(false);
            }}
            className={clsx(
              "flex w-full items-center justify-between px-3 py-2 text-left text-[12px] transition hover:bg-slate-50",
              isAuto ? "bg-brand-50/40 text-brand-700 font-medium" : "text-slate-700",
            )}
          >
            <div className="flex items-center gap-2">
              <span className="flex h-5 w-5 items-center justify-center rounded-md bg-brand-100 text-brand-600">
                <Sparkles size={11} />
              </span>
              <div>
                <div>自动选择 (Auto)</div>
                <div className="text-[10px] text-slate-400 font-normal">智能调度最优模型 · 推荐</div>
              </div>
            </div>
            {isAuto && <Check size={13} className="text-brand-600" />}
          </button>

          {presets.length > 0 && <div className="my-1 border-t border-line/60" />}

          <div className="max-h-56 overflow-y-auto">
            {presets.map((p) => {
              const selected = selectedModel === p.id;
              return (
                <button
                  key={p.id}
                  type="button"
                  onClick={() => {
                    setSelectedModel(p.id);
                    setOpen(false);
                  }}
                  className={clsx(
                    "flex w-full items-center justify-between px-3 py-1.5 text-left text-[12px] transition hover:bg-slate-50",
                    selected ? "bg-brand-50/40 text-brand-700 font-medium" : "text-slate-700",
                  )}
                >
                  <div className="flex items-center gap-2 min-w-0 pr-2">
                    <span className="flex h-5 w-5 shrink-0 items-center justify-center rounded-md bg-slate-100 text-slate-500">
                      <Cpu size={11} />
                    </span>
                    <div className="truncate">
                      <div className="truncate">{p.name}</div>
                      <div className="text-[10px] text-slate-400 font-normal font-mono">{p.id}</div>
                    </div>
                  </div>
                  {selected && <Check size={13} className="shrink-0 text-brand-600" />}
                </button>
              );
            })}
          </div>
        </div>
      )}
    </div>
  );
}

const THINKING_LABELS: Record<ThinkingSelection, string> = {
  auto: "自动",
  default: "模型默认",
  off: "关闭",
  fast: "快速",
  deep: "深度",
};

const THINKING_HELP: Record<ThinkingSelection, string> = {
  auto: "使用管理员配置的思考档位",
  default: "不发送开关，遵循模型自身默认",
  off: "关闭深度思考，工具任务响应最快",
  fast: "使用较低推理强度或较小 Token 预算",
  deep: "开启完整深度思考",
};

export function ThinkingSelector({
  presets = [],
  serverThinking = "off",
}: {
  presets?: ModelOption[];
  serverThinking?: ThinkingMode;
}) {
  const { selectedModel, selectedThinking, setSelectedThinking } = useChat();
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);
  const activeModel = selectedModel === "auto"
    ? presets.find((p) => p.default) ?? presets[0]
    : presets.find((p) => p.id === selectedModel);
  const capability = activeModel?.thinking;
  const unsupported = capability === "none";

  useEffect(() => {
    const handleOutside = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false);
    };
    if (open) {
      document.addEventListener("mousedown", handleOutside);
      return () => document.removeEventListener("mousedown", handleOutside);
    }
  }, [open]);

  const options: ThinkingSelection[] = ["auto", "default", "off", "fast", "deep"];
  const disabled = (mode: ThinkingSelection) =>
    (unsupported && mode !== "auto" && mode !== "default") ||
    (capability === "always" && mode === "off");

  return (
    <div className="relative inline-block" ref={ref}>
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        className={clsx(
          "flex items-center gap-1.5 rounded-lg border px-2 py-1 text-[11.5px] font-medium transition-all duration-150",
          selectedThinking === "off"
            ? "border-line bg-white text-slate-500 hover:bg-slate-50"
            : "border-violet-200 bg-violet-50/70 text-violet-700 hover:border-violet-300 hover:bg-violet-50",
        )}
        title={`思考模式：${selectedThinking === "auto" ? `服务端默认（${THINKING_LABELS[serverThinking]}）` : THINKING_LABELS[selectedThinking]}`}
      >
        <Brain size={12} />
        <span>思考：{THINKING_LABELS[selectedThinking]}</span>
        <ChevronDown size={11} className={clsx("text-slate-400 transition-transform duration-150", open && "rotate-180")} />
      </button>

      {open && (
        <div className="animate-fade-up absolute bottom-full left-0 z-50 mb-2 w-72 rounded-xl border border-line bg-white py-1.5 shadow-[0_8px_24px_rgba(0,0,0,0.12)]">
          <div className="px-3 py-1.5 text-[10.5px] font-semibold tracking-wider text-slate-400 uppercase">思考模式</div>
          {unsupported && (
            <div className="mx-2 mb-1 rounded-lg bg-slate-50 px-2.5 py-2 text-[10.5px] leading-relaxed text-slate-500">
              当前模型未声明深度思考能力。
            </div>
          )}
          {options.map((mode) => {
            const selected = selectedThinking === mode;
            const isDisabled = disabled(mode);
            return (
              <button
                key={mode}
                type="button"
                disabled={isDisabled}
                onClick={() => {
                  setSelectedThinking(mode);
                  setOpen(false);
                }}
                className={clsx(
                  "flex w-full items-center justify-between gap-3 px-3 py-2 text-left transition",
                  selected ? "bg-violet-50/60 text-violet-700" : "text-slate-700 hover:bg-slate-50",
                  isDisabled && "cursor-not-allowed opacity-40 hover:bg-white",
                )}
              >
                <div className="min-w-0">
                  <div className="text-[12px] font-medium">
                    {mode === "auto" ? `服务端默认（${THINKING_LABELS[serverThinking]}）` : THINKING_LABELS[mode]}
                  </div>
                  <div className="mt-0.5 text-[10px] font-normal text-slate-400">{THINKING_HELP[mode]}</div>
                </div>
                {selected && <Check size={13} className="shrink-0 text-violet-600" />}
              </button>
            );
          })}
        </div>
      )}
    </div>
  );
}
