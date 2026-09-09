import clsx from "clsx";
import {
  Camera,
  ChevronRight,
  FolderOpen,
  Info,
  MessageSquare,
  MessageSquareText,
  Plus,
  Settings,
  ShieldCheck,
  Sparkles,
  Trash2,
  Wrench,
} from "lucide-react";
import { useState } from "react";
import { Link } from "react-router-dom";
import { Avatar } from "@/components/ui/Avatar";
import { relativeDay } from "@/lib/format";
import { useAuth } from "@/store/auth";
import { useChat } from "@/store/chat";

export type PanelKey = "projects" | "files" | "skills" | "tools" | "resources" | "snapshots" | "settings";

interface Props {
  collapsed: boolean;
  onToggle: () => void;
  onOpenPanel: (key: PanelKey) => void;
}

const NAV: { key: PanelKey | "chat"; label: string; icon: React.ComponentType<{ size?: number; className?: string }> }[] = [
  { key: "chat", label: "对话", icon: MessageSquare },
  { key: "projects", label: "工程", icon: FolderOpen },
  { key: "skills", label: "技能 (Skills)", icon: Sparkles },
  { key: "tools", label: "工具", icon: Wrench },
  { key: "resources", label: "系统信息", icon: Info },
  { key: "snapshots", label: "会话快照", icon: Camera },
  { key: "settings", label: "设置", icon: Settings },
];

export function Sidebar({ collapsed, onToggle, onOpenPanel }: Props) {
  const user = useAuth((s) => s.user);
  const { conversations, activeId, openConversation, newConversation, deleteConversation, running } = useChat();
  const [confirmId, setConfirmId] = useState<string | null>(null);

  return (
    <aside className={clsx("flex shrink-0 flex-col gap-3 transition-[width] duration-200", collapsed ? "w-16" : "w-56")}>
      <nav className="card flex flex-col gap-0.5 p-2">
        {NAV.map(({ key, label, icon: Icon }) => {
          const active = key === "chat";
          return (
            <button
              key={key}
              onClick={() => key !== "chat" && onOpenPanel(key)}
              title={label}
              className={clsx(
                "flex items-center gap-2.5 rounded-lg px-2.5 py-2 text-[13px] transition",
                active ? "bg-brand-50 font-medium text-brand-600" : "text-slate-600 hover:bg-slate-50 hover:text-ink",
                collapsed && "justify-center px-0",
              )}
            >
              <Icon size={16} className="shrink-0" />
              {!collapsed && <span className="truncate">{label}</span>}
            </button>
          );
        })}
      </nav>

      <section className="card flex min-h-0 flex-1 flex-col">
        <div className={clsx("flex items-center justify-between px-3 pt-3 pb-2", collapsed && "justify-center px-0")}>
          {!collapsed && <span className="text-xs font-medium text-slate-500">对话历史</span>}
          <button
            onClick={() => newConversation()}
            disabled={running}
            className="flex items-center gap-1 rounded-full border border-line bg-white px-2 py-0.5 text-xs font-medium text-brand-600 shadow-xs transition hover:bg-brand-50 hover:border-brand-200 disabled:opacity-50"
            title="新建对话"
          >
            <Plus size={13} />
            {!collapsed && "新建对话"}
          </button>
        </div>
        <ul className="min-h-0 flex-1 space-y-1.5 overflow-y-auto px-2 pb-2">
          {conversations.length === 0 && !collapsed && (
            <li className="px-2 py-6 text-center text-xs text-slate-400">还没有对话，点击「新建对话」开始</li>
          )}
          {conversations.map((c) => {
            const active = c.id === activeId;
            return (
              <li key={c.id} className="group relative">
                <button
                  onClick={() => openConversation(c.id)}
                  title={c.title}
                  className={clsx(
                    "flex w-full items-start gap-2.5 rounded-xl p-2 text-left transition-all duration-200",
                    active
                      ? "bg-brand-50/80 border border-brand-200/80 shadow-xs"
                      : "border border-transparent hover:bg-slate-50/90",
                    collapsed && "justify-center px-0",
                  )}
                >
                  <span
                    className={clsx(
                      "relative mt-0.5 flex h-7 w-7 shrink-0 items-center justify-center rounded-lg transition-all duration-200",
                      active ? "bg-brand-500 text-white shadow-[0_2px_6px_rgba(99,102,241,0.35)]" : "bg-slate-100 text-slate-500 group-hover:bg-slate-200/70",
                    )}
                  >
                    <MessageSquareText size={14} />
                    {c.is_running && (
                      <span className="absolute -right-0.5 -top-0.5 flex h-2.5 w-2.5" title="后台运行中">
                        <span className="absolute inline-flex h-full w-full animate-ping rounded-full bg-brand-400 opacity-60" />
                        <span className="relative inline-flex h-2.5 w-2.5 rounded-full border-2 border-white bg-brand-500" />
                      </span>
                    )}
                  </span>
                  {!collapsed && (
                    <div className="min-w-0 flex-1">
                      <div className="flex items-center justify-between gap-2">
                        <span className={clsx("truncate text-[13px] font-medium", active ? "text-brand-700" : "text-ink")}>{c.title}</span>
                        <span className="shrink-0 text-[11px] text-slate-400">{c.is_running ? <span className="font-medium text-brand-600">运行中</span> : relativeDay(c.updated_at)}</span>
                      </div>
                      <p className="mt-0.5 truncate text-[11px] text-slate-400">{c.preview || `${c.message_count} 条消息 · ${c.tool_call_count} 次工具调用`}</p>
                    </div>
                  )}
                </button>
                {!collapsed && (
                  <button
                    onClick={(e) => {
                      e.stopPropagation();
                      setConfirmId(c.id);
                    }}
                    className="absolute right-1.5 bottom-1.5 hidden rounded p-1 text-slate-400 hover:bg-rose-50 hover:text-rose-500 group-hover:block"
                    title="删除对话"
                  >
                    <Trash2 size={13} />
                  </button>
                )}
                {confirmId === c.id && (
                  <div className="absolute inset-0 z-10 flex items-center justify-between gap-2 rounded-lg bg-white/95 px-2 text-xs shadow">
                    <span className="text-slate-600">删除该对话？</span>
                    <div className="flex gap-1">
                      <button className="rounded px-2 py-1 text-slate-500 hover:bg-slate-100" onClick={() => setConfirmId(null)}>
                        取消
                      </button>
                      <button
                        className="rounded bg-rose-500 px-2 py-1 text-white hover:bg-rose-600"
                        onClick={() => {
                          deleteConversation(c.id);
                          setConfirmId(null);
                        }}
                      >
                        删除
                      </button>
                    </div>
                  </div>
                )}
              </li>
            );
          })}
        </ul>
      </section>

      <section className="card p-3">
        {!collapsed && (
          <div className="mb-2 flex items-center justify-between text-xs font-medium text-slate-500">
            <span>当前用户</span>
            <span className="text-[11px] text-slate-400 hover:text-ink cursor-pointer">︿</span>
          </div>
        )}
        <div className={clsx("flex items-center gap-2.5", collapsed && "justify-center")}>
          <Avatar name={user?.display_name ?? "?"} color={user?.avatar_color} size={34} />
          {!collapsed && (
            <div className="min-w-0 flex-1">
              <span className="block truncate text-[13px] font-medium text-ink">{user?.display_name || "张小明"}</span>
              <p className="truncate text-[11px] text-slate-400">{user?.email || (user?.username ? `${user.username}@demo.com` : "zhangxiaoming@demo.com")}</p>
              <span className="mt-1 inline-block rounded bg-slate-100 px-1.5 py-0.5 text-[10px] text-slate-500">{user?.role === "admin" ? "管理员" : "普通用户"}</span>
            </div>
          )}
        </div>
        {user?.role === "admin" && (
          <Link
            to="/admin"
            title="后台管理"
            className={clsx(
              "mt-2.5 flex items-center gap-2 rounded-lg border border-dashed border-brand-200 bg-brand-50/50 px-2.5 py-1.5 text-[12px] font-medium text-brand-700 hover:bg-brand-50",
              collapsed && "justify-center px-0",
            )}
          >
            <ShieldCheck size={14} />
            {!collapsed && "后台管理"}
          </Link>
        )}
      </section>

      <button onClick={onToggle} className="card flex items-center justify-center gap-2 px-3 py-2 text-xs text-slate-500 hover:text-ink">
        {collapsed ? (
          <ChevronRight size={14} />
        ) : (
          <>
            <span className="text-[13px] font-mono leading-none">⇥</span>
            <span>收起侧边栏</span>
          </>
        )}
      </button>
    </aside>
  );
}
