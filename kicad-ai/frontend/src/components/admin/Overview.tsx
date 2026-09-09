import { Activity, AlertTriangle, Bot, Database, FolderKanban, MessageSquare, RefreshCw, Users, Wrench } from "lucide-react";
import { useCallback, useEffect, useState } from "react";
import { Avatar } from "@/components/ui/Avatar";
import { api } from "@/lib/api";
import { humanBytes, relativeDay } from "@/lib/format";
import type { AdminOverview } from "@/lib/types";

export function Overview() {
  const [data, setData] = useState<AdminOverview | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setData(await api.admin.overview());
      setError(null);
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
    const t = window.setInterval(load, 15000);
    return () => window.clearInterval(t);
  }, [load]);

  if (error) return <p className="rounded-lg bg-rose-50 px-4 py-3 text-sm text-rose-600">{error}</p>;
  if (!data) return <p className="text-sm text-slate-400">加载中…</p>;

  const uptime = Math.max(0, Date.now() - new Date(data.started_at).getTime());
  const hours = Math.floor(uptime / 3600000);
  const mins = Math.floor((uptime % 3600000) / 60000);

  return (
    <div className="space-y-6">
      <header className="flex items-start justify-between">
        <div>
          <h1 className="text-[22px] font-semibold text-ink">总览</h1>
          <p className="text-[13px] text-slate-500">系统运行状态与用量统计，每 15 秒自动刷新。</p>
        </div>
        <button onClick={load} className="flex items-center gap-1.5 rounded-lg border border-line bg-white px-3 py-1.5 text-[12.5px] text-slate-600 hover:bg-slate-50">
          <RefreshCw size={14} className={loading ? "animate-spin" : ""} /> 刷新
        </button>
      </header>

      {data.last_error && (
        <div className="flex items-start gap-2 rounded-lg border border-rose-200 bg-rose-50 px-4 py-3 text-[13px] text-rose-700">
          <AlertTriangle size={16} className="mt-0.5 shrink-0" />
          <div>
            <div className="font-medium">Agent 最近一次重建失败</div>
            <div className="font-mono text-[12px]">{data.last_error}</div>
          </div>
        </div>
      )}

      <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
        <Stat icon={Users} label="注册用户" value={data.users_total} sub={`启用 ${data.users_active} · 管理员 ${data.users_admins} · 近 7 天新增 ${data.users_new_7d}`} />
        <Stat icon={Activity} label="当前在线" value={data.online_users} sub={`24 小时内登录 ${data.logins_24h} · 正在处理 ${data.active_runs}`} tone="emerald" />
        <Stat icon={MessageSquare} label="对话 / 消息" value={`${data.conversations_total} / ${data.messages_total}`} sub={`工具调用 ${data.tool_calls_total.toLocaleString()} 次`} tone="violet" />
        <Stat icon={FolderKanban} label="工程数" value={data.projects_total} sub={`工作区占用 ${humanBytes(data.workspace_bytes)}`} tone="amber" />
      </div>

      <div className="grid grid-cols-1 gap-4 lg:grid-cols-3">
        <section className="card p-5 lg:col-span-2">
          <h2 className="mb-3 flex items-center gap-2 text-[14px] font-semibold text-ink">
            <Bot size={16} className="text-brand-500" /> Agent 运行状态
          </h2>
          <dl className="grid grid-cols-2 gap-x-6 gap-y-2.5 text-[13px]">
            <Row k="当前模型" v={<code className="rounded bg-slate-100 px-1.5 py-0.5 text-[12px]">{data.model}</code>} />
            <Row k="工具数量" v={`${data.tool_count}（内置 ${data.tool_count - Math.min(data.mcp_tool_count, data.tool_count)} · 开源插件并入 ${data.mcp_tool_count}）`} />
            <Row k="开源插件 (kcaa)" v={data.mcp_tool_count > 0 ? <span className="text-emerald-600">已加载 · 进程内直接调用</span> : <span className="text-slate-400">未加载</span>} />
            <Row k="kicad-cli" v={data.kicad_cli ? "可用（真实 DRC / 渲染）" : "未安装（内置轻量 DRC）"} />
            <Row k="子代理" v={data.subagents ? "已启用" : "已关闭"} />
            <Row k="运行时长" v={`${hours} 小时 ${mins} 分`} />
            <Row k="最近热重载" v={data.last_reload_at ? new Date(data.last_reload_at).toLocaleString() : "—"} />
            <Row k="Token 累计" v={`输入 ${data.input_tokens_total.toLocaleString()} · 输出 ${data.output_tokens_total.toLocaleString()}`} />
            <Row k="版本" v={data.version} />
          </dl>
        </section>

        <section className="card p-5">
          <h2 className="mb-3 flex items-center gap-2 text-[14px] font-semibold text-ink">
            <Database size={16} className="text-brand-500" /> 最近注册
          </h2>
          <ul className="space-y-2.5">
            {data.recent_users.map((u) => (
              <li key={u.id} className="flex items-center gap-2.5">
                <Avatar name={u.display_name} color={u.avatar_color} size={30} />
                <div className="min-w-0 flex-1">
                  <div className="flex items-center gap-1.5 text-[13px]">
                    <span className="truncate font-medium text-ink">{u.display_name}</span>
                    {u.role === "admin" && <span className="rounded bg-brand-50 px-1 text-[10px] text-brand-600">管理员</span>}
                    {!u.is_active && <span className="rounded bg-rose-50 px-1 text-[10px] text-rose-600">已禁用</span>}
                  </div>
                  <div className="text-[11px] text-slate-400">
                    @{u.username} · {u.conversation_count} 个对话 · {relativeDay(u.created_at)}
                  </div>
                </div>
              </li>
            ))}
            {data.recent_users.length === 0 && <li className="text-[12px] text-slate-400">暂无用户</li>}
          </ul>
        </section>
      </div>

      <section className="card p-5">
        <h2 className="mb-1 flex items-center gap-2 text-[14px] font-semibold text-ink">
          <Wrench size={16} className="text-brand-500" /> 运维提示
        </h2>
        <ul className="list-disc space-y-1 pl-5 text-[12.5px] text-slate-600">
          <li>修改「模型配置」或「系统设置」后会即时热重载 Agent，无需重启容器；正在进行的对话不受影响。</li>
          <li>数据（SQLite、用户工作区、快照）位于 Docker 卷 <code className="rounded bg-slate-100 px-1">/data</code>，备份命令见《使用说明》3.6。</li>
          <li>查看日志：<code className="rounded bg-slate-100 px-1">docker compose logs -f app</code>；MCP 服务：<code className="rounded bg-slate-100 px-1">docker compose logs -f kcaa</code>。</li>
        </ul>
      </section>
    </div>
  );
}

function Stat({ icon: Icon, label, value, sub, tone = "brand" }: { icon: React.ComponentType<{ size?: number }>; label: string; value: number | string; sub: string; tone?: "brand" | "emerald" | "violet" | "amber" }) {
  const tones = {
    brand: "bg-brand-50 text-brand-600",
    emerald: "bg-emerald-50 text-emerald-600",
    violet: "bg-violet-50 text-violet-600",
    amber: "bg-amber-50 text-amber-600",
  }[tone];
  return (
    <div className="card p-5">
      <div className="flex items-center justify-between">
        <span className="text-[12.5px] text-slate-500">{label}</span>
        <span className={`flex h-8 w-8 items-center justify-center rounded-lg ${tones}`}>
          <Icon size={16} />
        </span>
      </div>
      <div className="mt-2 text-[26px] font-semibold tracking-tight text-ink">{typeof value === "number" ? value.toLocaleString() : value}</div>
      <div className="mt-1 text-[11.5px] text-slate-400">{sub}</div>
    </div>
  );
}

function Row({ k, v }: { k: string; v: React.ReactNode }) {
  return (
    <div className="flex items-baseline justify-between gap-3 border-b border-line/60 pb-1.5">
      <dt className="shrink-0 text-slate-500">{k}</dt>
      <dd className="truncate text-right text-ink">{v}</dd>
    </div>
  );
}
