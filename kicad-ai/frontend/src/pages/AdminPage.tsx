import clsx from "clsx";
import { ArrowLeft, Cpu, LayoutDashboard, LogOut, Settings2, Users } from "lucide-react";
import { useState } from "react";
import { Link, Navigate } from "react-router-dom";
import { ModelSettings } from "@/components/admin/ModelSettings";
import { Overview } from "@/components/admin/Overview";
import { SystemSettings } from "@/components/admin/SystemSettings";
import { UsersPanel } from "@/components/admin/UsersPanel";
import { Avatar, BrandMark } from "@/components/ui/Avatar";
import { useAuth } from "@/store/auth";
import { useBranding } from "@/store/branding";

type Section = "overview" | "model" | "users" | "system";

const SECTIONS: { key: Section; label: string; icon: React.ComponentType<{ size?: number }>; desc: string }[] = [
  { key: "overview", label: "总览", icon: LayoutDashboard, desc: "用量统计与运行状态" },
  { key: "model", label: "模型配置", icon: Cpu, desc: "大模型提供方、密钥与参数" },
  { key: "users", label: "用户管理", icon: Users, desc: "账号、角色、启停与密码" },
  { key: "system", label: "系统设置", icon: Settings2, desc: "品牌 Logo、注册、插件集成" },
];

export function AdminPage() {
  const { user, logout } = useAuth();
  const { appName, logoUrl } = useBranding();
  const [section, setSection] = useState<Section>("overview");

  if (user && user.role !== "admin") return <Navigate to="/app" replace />;
  // 站点管理员只管本站用户与用量;模型 / 系统设置由平台管理员(默认站点)统一维护
  const platformAdmin = user?.platform_admin !== false;
  const sections = platformAdmin ? SECTIONS : SECTIONS.filter((s) => s.key === "overview" || s.key === "users");
  const visibleSection = sections.some((s) => s.key === section) ? section : "overview";

  return (
    <div className="flex h-full">
      <aside className="flex w-60 shrink-0 flex-col border-r border-line bg-white">
        <div className="flex items-center gap-2.5 px-5 py-5">
          <BrandMark size={34} logoUrl={logoUrl} />
          <div>
            <div className="text-[14px] font-semibold text-ink">后台管理</div>
            <div className="truncate text-[11px] text-slate-400">{appName}</div>
          </div>
        </div>
        <nav className="flex-1 space-y-0.5 px-3">
          {sections.map(({ key, label, icon: Icon, desc }) => (
            <button
              key={key}
              onClick={() => setSection(key)}
              className={clsx(
                "flex w-full items-start gap-3 rounded-lg px-3 py-2.5 text-left transition",
                visibleSection === key ? "bg-brand-50 text-brand-700" : "text-slate-600 hover:bg-slate-50",
              )}
            >
              <Icon size={17} />
              <span>
                <span className="block text-[13.5px] font-medium">{label}</span>
                <span className={clsx("block text-[11px]", visibleSection === key ? "text-brand-500/80" : "text-slate-400")}>{desc}</span>
              </span>
            </button>
          ))}
          {!platformAdmin && (
            <p className="mt-3 rounded-lg bg-slate-50 px-3 py-2 text-[11.5px] leading-relaxed text-slate-500">
              模型与系统设置由平台统一管理;本站管理员可查看本站用量并管理本站用户。
            </p>
          )}
        </nav>
        <div className="border-t border-line p-3">
          <Link to="/app" className="flex items-center gap-2 rounded-lg px-3 py-2 text-[13px] text-slate-600 hover:bg-slate-50">
            <ArrowLeft size={15} /> 返回工作台
          </Link>
          {user?.sso && (
            <a href="/admin/dashboard" className="flex items-center gap-2 rounded-lg px-3 py-2 text-[13px] text-slate-600 hover:bg-slate-50">
              <ArrowLeft size={15} /> 返回教学平台后台
            </a>
          )}
          <div className="mt-1 flex items-center gap-2.5 rounded-lg px-3 py-2">
            <Avatar name={user?.display_name ?? "?"} color={user?.avatar_color} size={30} />
            <div className="min-w-0 flex-1">
              <div className="truncate text-[13px] font-medium text-ink">{user?.display_name}</div>
              <div className="text-[11px] text-slate-400">管理员</div>
            </div>
            <button onClick={logout} title="退出登录" className="rounded p-1.5 text-slate-400 hover:bg-rose-50 hover:text-rose-500">
              <LogOut size={15} />
            </button>
          </div>
        </div>
      </aside>

      <main className="min-w-0 flex-1 overflow-y-auto">
        <div className="mx-auto max-w-6xl px-8 py-8">
          {visibleSection === "overview" && <Overview />}
          {visibleSection === "model" && platformAdmin && <ModelSettings />}
          {visibleSection === "users" && <UsersPanel />}
          {visibleSection === "system" && platformAdmin && <SystemSettings />}
        </div>
      </main>
    </div>
  );
}
