import { ArrowLeft, Bell, HelpCircle, Maximize2, Minimize2 } from "lucide-react";
import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { Avatar, BrandMark } from "@/components/ui/Avatar";
import { IOEDU_HOME_URL } from "@/lib/api";
import { useAuth } from "@/store/auth";
import { useBranding } from "@/store/branding";
import { useChat } from "@/store/chat";

export function TopBar({ onOpenHelp, onOpenSettings }: { onOpenHelp: () => void; onOpenSettings: () => void }) {
  const user = useAuth((s) => s.user);
  const snapshots = useChat((s) => s.snapshotsThisSession);
  const { appName, logoUrl } = useBranding();
  const [fs, setFs] = useState(false);

  useEffect(() => {
    const onChange = () => setFs(!!document.fullscreenElement);
    document.addEventListener("fullscreenchange", onChange);
    return () => document.removeEventListener("fullscreenchange", onChange);
  }, []);

  const toggleFullscreen = () => {
    if (document.fullscreenElement) document.exitFullscreen();
    else document.documentElement.requestFullscreen().catch(() => undefined);
  };

  return (
    <header className="flex h-14 shrink-0 items-center justify-between px-5">
      <Link to="/" className="flex items-center gap-2.5">
        <BrandMark size={32} logoUrl={logoUrl} />
        <span className="text-[15px] font-semibold tracking-tight text-ink">{appName}</span>
        <span className="rounded-md bg-brand-50 px-1.5 py-0.5 text-[11px] font-medium text-brand-600">Beta</span>
      </Link>
      <div className="flex items-center gap-1">
        {user?.sso && (
          <a
            href={IOEDU_HOME_URL}
            className="mr-1 flex items-center gap-1 rounded-lg px-2.5 py-1.5 text-[12.5px] text-slate-500 transition hover:bg-white hover:text-ink hover:shadow-sm"
            title="回到教学平台"
          >
            <ArrowLeft size={15} /> 教学平台
          </a>
        )}
        <IconBtn onClick={toggleFullscreen} title={fs ? "退出全屏" : "全屏"}>
          {fs ? <Minimize2 size={17} /> : <Maximize2 size={17} />}
        </IconBtn>
        <IconBtn onClick={onOpenHelp} title="帮助">
          <HelpCircle size={17} />
        </IconBtn>
        <IconBtn onClick={onOpenSettings} title="通知">
          <Bell size={17} />
          {snapshots > 0 && (
            <span className="absolute -right-0.5 -top-0.5 flex h-4 min-w-4 items-center justify-center rounded-full bg-rose-500 px-1 text-[10px] font-semibold text-white ring-2 ring-surface">
              {snapshots > 9 ? "9+" : snapshots}
            </span>
          )}
        </IconBtn>
        <button onClick={onOpenSettings} className="ml-1.5 rounded-full ring-2 ring-white shadow-sm">
          <Avatar name={user?.display_name ?? "?"} color={user?.avatar_color} size={32} />
        </button>
      </div>
    </header>
  );
}

function IconBtn({ children, onClick, title }: { children: React.ReactNode; onClick?: () => void; title?: string }) {
  return (
    <button onClick={onClick} title={title} className="relative rounded-lg p-2 text-slate-500 transition hover:bg-white hover:text-ink hover:shadow-sm">
      {children}
    </button>
  );
}
