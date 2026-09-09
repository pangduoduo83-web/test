import { Camera, MoreVertical, Sparkles } from "lucide-react";
import { useEffect, useState } from "react";
import { BotAvatar } from "@/components/ui/Avatar";
import { durationHMS } from "@/lib/format";
import { useBranding } from "@/store/branding";
import { useChat } from "@/store/chat";

interface Props {
  online: boolean;
  onSkills: () => void;
  onSnapshots: () => void;
  onMore: () => void;
}

function useElapsed(startedAt: number | null, active: boolean): string {
  const [, tick] = useState(0);
  useEffect(() => {
    if (!active) return;
    const t = setInterval(() => tick((n) => n + 1), 1000);
    return () => clearInterval(t);
  }, [active]);
  if (!active || !startedAt) return "";
  return durationHMS(Date.now() - startedAt);
}

export function ChatHeader({ online, onSkills, onSnapshots, onMore }: Props) {
  const tagline = useBranding((s) => s.tagline);
  const running = useChat((s) => s.running);
  const reconnecting = useChat((s) => s.reconnecting);
  const startedAt = useChat((s) => s.runStartedAt);
  const elapsed = useElapsed(startedAt, running);

  const status = reconnecting
    ? { dot: "bg-amber-500 animate-pulse", text: "text-amber-600", label: "重新连接中" }
    : running
      ? { dot: "bg-brand-500 animate-pulse", text: "text-brand-600", label: `处理中${elapsed ? ` · ${elapsed}` : ""}` }
      : online
        ? { dot: "bg-emerald-500", text: "text-emerald-600", label: "在线" }
        : { dot: "bg-slate-300", text: "text-slate-400", label: "连接中" };

  return (
    <div className="flex items-center justify-between border-b border-line px-5 py-3.5">
      <div className="flex items-center gap-3">
        <span className="relative">
          <BotAvatar size={42} />
          {running && <span className="absolute -right-0.5 -bottom-0.5 h-3 w-3 rounded-full border-2 border-white bg-brand-500 animate-pulse" />}
        </span>
        <div>
          <div className="flex items-center gap-2">
            <span className="text-[15px] font-semibold text-ink">KiCad AI 助手</span>
            <span className={`flex items-center gap-1 text-[11px] font-medium tabular-nums transition-colors ${status.text}`}>
              <span className={`h-1.5 w-1.5 rounded-full ${status.dot}`} />
              {status.label}
            </span>
          </div>
          <p className="text-[12px] text-slate-400">{tagline}</p>
        </div>
      </div>
      <div className="flex items-center gap-2">
        <button onClick={onSkills} className="btn-outline">
          <Sparkles size={14} /> 技能 (Skill)
        </button>
        <button onClick={onSnapshots} className="btn-outline">
          <Camera size={14} /> 会话快照
        </button>
        <button onClick={onMore} className="btn-outline px-2" title="更多">
          <MoreVertical size={15} />
        </button>
      </div>
    </div>
  );
}
