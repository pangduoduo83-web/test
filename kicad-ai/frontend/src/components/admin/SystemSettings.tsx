import { ImagePlus, Loader2, Palette, Save, Settings2, Trash2 } from "lucide-react";
import { useEffect, useRef, useState } from "react";
import { BrandMark } from "@/components/ui/Avatar";
import { api } from "@/lib/api";
import type { RuntimeSettings } from "@/lib/types";
import { useBranding } from "@/store/branding";
import { Field } from "./ModelSettings";

interface Form {
  app_name: string;
  app_tagline: string;
  app_footer: string;
  app_copyright: string;
  allow_registration: boolean;
  enable_subagents: boolean;
  agent_recursion_limit: number;
  agent_run_timeout_seconds: number;
  agent_run_token_budget: number;
  tool_result_max_chars: number;
  kcaa_mode: string;
  kcaa_profile: string;
  kcaa_mcp_url: string;
}

const inputCls = "w-full rounded-lg border border-line bg-white px-3 py-2 text-[13px] outline-none transition focus:border-brand-300 focus:shadow-[0_0_0_3px_rgba(99,102,241,0.12)]";

export function SystemSettings() {
  const branding = useBranding();
  const [settings, setSettings] = useState<RuntimeSettings | null>(null);
  const [form, setForm] = useState<Form | null>(null);
  const [saving, setSaving] = useState(false);
  const [logoBusy, setLogoBusy] = useState(false);
  const [message, setMessage] = useState<{ tone: "ok" | "err"; text: string } | null>(null);
  const fileRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    api.admin.settings().then((s) => {
      setSettings(s);
      const v = s.values;
      setForm({
        app_name: String(v.app_name ?? "硬件设计助手"),
        app_tagline: String(v.app_tagline ?? ""),
        app_footer: String(v.app_footer ?? "🔒 用户工作区隔离，越界路径拒绝  📷 修改前自动快照  🌐 支持主流大模型  ⚡ 智能 EDA 闭环"),
        app_copyright: String(v.app_copyright ?? ""),
        allow_registration: Boolean(v.allow_registration),
        enable_subagents: Boolean(v.enable_subagents),
        agent_recursion_limit: Number(v.agent_recursion_limit ?? 400),
        agent_run_timeout_seconds: Number(v.agent_run_timeout_seconds ?? 1800),
        agent_run_token_budget: Number(v.agent_run_token_budget ?? 3_000_000),
        tool_result_max_chars: Number(v.tool_result_max_chars ?? 24_000),
        kcaa_mode: String(v.kcaa_mode ?? "direct"),
        kcaa_profile: String(v.kcaa_profile ?? "full"),
        kcaa_mcp_url: String(v.kcaa_mcp_url ?? ""),
      });
    });
  }, []);

  if (!settings || !form) return <p className="text-sm text-slate-400">加载中…</p>;

  const save = async () => {
    setSaving(true);
    setMessage(null);
    try {
      const res = await api.admin.saveSettings({ ...form });
      setSettings({ ...settings, values: res.values, runtime: { ...settings.runtime, ...res.runtime } });
      branding.set({
        appName: form.app_name,
        tagline: form.app_tagline,
        footer: form.app_footer,
        copyright: form.app_copyright,
      });
      setMessage(
        res.error
          ? { tone: "err", text: `已保存，但 Agent 重建失败：${res.error}` }
          : { tone: "ok", text: res.agent_reloaded ? `已保存并热重载（工具 ${res.runtime.tool_count} 个，其中开源插件 ${res.runtime.mcp_tool_count} 个）` : "已保存" },
      );
    } catch (e) {
      setMessage({ tone: "err", text: (e as Error).message });
    } finally {
      setSaving(false);
    }
  };

  const uploadLogo = async (file: File) => {
    setLogoBusy(true);
    setMessage(null);
    try {
      const res = await api.admin.uploadLogo(file);
      branding.set({ logoUrl: res.logo_url });
      setMessage({ tone: "ok", text: "Logo 已更新，所有页面即时生效" });
    } catch (e) {
      setMessage({ tone: "err", text: (e as Error).message });
    } finally {
      setLogoBusy(false);
    }
  };

  const removeLogo = async () => {
    setLogoBusy(true);
    try {
      await api.admin.deleteLogo();
      branding.set({ logoUrl: null });
      setMessage({ tone: "ok", text: "已恢复默认 Logo" });
    } catch (e) {
      setMessage({ tone: "err", text: (e as Error).message });
    } finally {
      setLogoBusy(false);
    }
  };

  return (
    <div className="space-y-6">
      <header>
        <h1 className="text-[22px] font-semibold text-ink">系统设置</h1>
        <p className="text-[13px] text-slate-500">品牌信息、注册策略、Agent 能力开关与开源插件集成方式。</p>
      </header>

      <section className="card p-5">
        <h2 className="mb-4 flex items-center gap-2 text-[14px] font-semibold text-ink">
          <Palette size={16} className="text-brand-500" /> 品牌
        </h2>
        <div className="grid grid-cols-1 gap-5 md:grid-cols-[auto_1fr]">
          <div className="flex flex-col items-center gap-3 rounded-xl border border-dashed border-line bg-slate-50/60 px-6 py-5">
            <BrandMark size={72} logoUrl={branding.logoUrl} className="rounded-2xl bg-white shadow-sm" />
            <div className="flex gap-2">
              <button onClick={() => fileRef.current?.click()} disabled={logoBusy} className="flex items-center gap-1.5 rounded-lg bg-brand-500 px-3 py-1.5 text-[12px] font-medium text-white hover:bg-brand-600 disabled:opacity-50">
                {logoBusy ? <Loader2 size={13} className="animate-spin" /> : <ImagePlus size={13} />} 上传 Logo
              </button>
              {branding.logoUrl && (
                <button onClick={removeLogo} disabled={logoBusy} className="flex items-center gap-1.5 rounded-lg border border-line bg-white px-3 py-1.5 text-[12px] text-slate-600 hover:bg-slate-50 disabled:opacity-50">
                  <Trash2 size={13} /> 恢复默认
                </button>
              )}
            </div>
            <p className="text-center text-[11px] text-slate-400">PNG / SVG / JPG / WebP，≤ 2 MB，建议正方形</p>
            <input
              ref={fileRef}
              type="file"
              accept=".png,.jpg,.jpeg,.svg,.webp,.ico"
              className="hidden"
              onChange={(e) => {
                const f = e.target.files?.[0];
                if (f) uploadLogo(f);
                e.target.value = "";
              }}
            />
          </div>
          <div className="grid grid-cols-1 gap-4 self-start">
            <Field label="站点名称" hint="显示在顶部栏、首页、登录页与浏览器标题">
              <input value={form.app_name} onChange={(e) => setForm({ ...form, app_name: e.target.value })} className={inputCls} />
            </Field>
            <Field label="副标题" hint="显示在对话窗口助手名称下方">
              <input value={form.app_tagline} onChange={(e) => setForm({ ...form, app_tagline: e.target.value })} className={inputCls} placeholder="AI 帮你画原理图、布 PCB、跑 DRC、审 BOM" />
            </Field>
            <Field label="首页底栏特性标语 (Footer 特性条)" hint="显示在首页最底部的特性提示条（支持多个短语以空格或 · 分隔，留空则隐藏）">
              <input
                value={form.app_footer}
                onChange={(e) => setForm({ ...form, app_footer: e.target.value })}
                className={inputCls}
                placeholder="🔒 用户工作区隔离，越界路径拒绝  📷 修改前自动快照  🌐 支持主流大模型  ⚡ 智能 EDA 闭环"
              />
            </Field>
            <Field label="页脚版权说明 (Copyright)" hint="自定义底部版权文字（例如 © 2026 硬件设计助手，留空则默认显示站点名称与版本）">
              <input
                value={form.app_copyright}
                onChange={(e) => setForm({ ...form, app_copyright: e.target.value })}
                className={inputCls}
                placeholder="留空则默认"
              />
            </Field>
          </div>
        </div>
      </section>

      <section className="card p-5">
        <h2 className="mb-4 flex items-center gap-2 text-[14px] font-semibold text-ink">
          <Settings2 size={16} className="text-brand-500" /> 运行参数
        </h2>
        <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
          <Field label="开源插件 (KiCad-AI-Assistant) 集成方式" hint="direct：把插件作为库装在本进程内直接调用（默认，无需 MCP）；mcp：连接独立运行的 MCP 服务器；off：只用内置工具">
            <select value={form.kcaa_mode} onChange={(e) => setForm({ ...form, kcaa_mode: e.target.value })} className={inputCls}>
              <option value="direct">direct · 进程内直接调用（推荐）</option>
              <option value="mcp">mcp · 连接外部 MCP 服务器</option>
              <option value="off">off · 仅内置工具</option>
            </select>
          </Field>
          <Field label="插件工具集" hint="full：全部工具；plugin：不依赖 kicad-cli 的精简集">
            <select value={form.kcaa_profile} onChange={(e) => setForm({ ...form, kcaa_profile: e.target.value })} className={inputCls}>
              <option value="full">full · 全部工具</option>
              <option value="plugin">plugin · 精简集</option>
            </select>
          </Field>
          {form.kcaa_mode === "mcp" && (
            <Field label="MCP 服务器地址" hint="仅 mcp 模式使用，例如 http://kcaa:8765/mcp">
              <input value={form.kcaa_mcp_url} onChange={(e) => setForm({ ...form, kcaa_mcp_url: e.target.value })} className={inputCls} placeholder="http://kcaa:8765/mcp" />
            </Field>
          )}
          <Field label="Agent 最大步数 (recursion_limit)" hint="单次请求的 LangGraph 步数上限；每轮「模型调用 + 工具执行」约消耗 4 步，400 ≈ 100 轮。达到上限时对话会暂停并可一键继续">
            <input type="number" min={20} max={4000} step={20} value={form.agent_recursion_limit} onChange={(e) => setForm({ ...form, agent_recursion_limit: Number(e.target.value) })} className={inputCls} />
          </Field>
          <Field label="单次运行时长上限（秒）" hint="超时后运行暂停、进度保留，可一键继续；0 表示不限制。运行在服务端后台执行，刷新页面不会中断">
            <input type="number" min={0} max={86400} step={60} value={form.agent_run_timeout_seconds} onChange={(e) => setForm({ ...form, agent_run_timeout_seconds: Number(e.target.value) })} className={inputCls} />
          </Field>
          <Field label="单次运行 token 预算" hint="一轮内所有模型调用的 token 总量上限，防止无效循环烧钱；超出后暂停并可继续（新一轮预算）。0 表示不限制">
            <input type="number" min={0} step={100000} value={form.agent_run_token_budget} onChange={(e) => setForm({ ...form, agent_run_token_budget: Number(e.target.value) })} className={inputCls} />
          </Field>
          <Field label="工具结果字符上限" hint="超过的结果会自动裁剪列表并把完整内容存到工作区 .agent/tool_results，模型可按需分页读取；最小 4000，0 表示不限制">
            <input type="number" min={0} step={2000} value={form.tool_result_max_chars} onChange={(e) => setForm({ ...form, tool_result_max_chars: Number(e.target.value) })} className={inputCls} />
          </Field>
          <div className="space-y-3 pt-1 md:col-span-2">
            <Toggle label="开放注册" hint="关闭后仅管理员可在「用户管理」中创建账号" checked={form.allow_registration} onChange={(v) => setForm({ ...form, allow_registration: v })} />
            <Toggle label="启用子代理" hint="pcb-layout-agent / drc-agent / schematic-agent，隔离长任务上下文" checked={form.enable_subagents} onChange={(v) => setForm({ ...form, enable_subagents: v })} />
          </div>
        </div>
        <div className="mt-5 flex items-center gap-3 border-t border-line pt-4">
          <button onClick={save} disabled={saving} className="flex items-center gap-1.5 rounded-lg bg-brand-500 px-4 py-2 text-[13px] font-medium text-white hover:bg-brand-600 disabled:opacity-50">
            {saving ? <Loader2 size={15} className="animate-spin" /> : <Save size={15} />} 保存
          </button>
          <span className="text-[12px] text-slate-400">
            当前：工具 {settings.runtime.tool_count} 个 · 开源插件 {settings.runtime.mcp_tool_count} 个
          </span>
        </div>
        {message && <p className={`mt-3 rounded-lg px-3 py-2 text-[12.5px] ${message.tone === "ok" ? "bg-emerald-50 text-emerald-700" : "bg-rose-50 text-rose-700"}`}>{message.text}</p>}
      </section>
    </div>
  );
}

function Toggle({ label, hint, checked, onChange }: { label: string; hint?: string; checked: boolean; onChange: (v: boolean) => void }) {
  return (
    <label className="flex cursor-pointer items-start gap-3">
      <button
        type="button"
        role="switch"
        aria-checked={checked}
        onClick={() => onChange(!checked)}
        className={`relative mt-0.5 h-5 w-9 shrink-0 rounded-full transition ${checked ? "bg-brand-500" : "bg-slate-300"}`}
      >
        <span className={`absolute top-0.5 h-4 w-4 rounded-full bg-white shadow transition ${checked ? "left-[18px]" : "left-0.5"}`} />
      </button>
      <span>
        <span className="block text-[13px] font-medium text-slate-700">{label}</span>
        {hint && <span className="block text-[11px] text-slate-400">{hint}</span>}
      </span>
    </label>
  );
}
