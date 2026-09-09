import {
  ArrowRight,
  Bot,
  Layers,
  MessageSquareText,
  ShieldCheck,
  Sparkles,
  Users,
  Wrench,
} from "lucide-react";
import { useEffect, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { BrandMark } from "@/components/ui/Avatar";
import { api } from "@/lib/api";
import type { PublicInfo } from "@/lib/types";
import { useAuth } from "@/store/auth";
import { useBranding } from "@/store/branding";

const FEATURES = [
  {
    icon: MessageSquareText,
    title: "自然语言操作 KiCad",
    desc: "「把去耦电容移到芯片电源引脚 2 mm 内并检查 DRC」—— 一句话完成查询、移动、对齐、DRC。",
  },
  {
    icon: Wrench,
    title: "100+ 工程工具",
    desc: "内置 38 个 PCB / 原理图工具，并在进程内直接调用开源 KiCad-AI-Assistant 的全部工具，覆盖布线、铜皮、层次图纸与元件库检索。",
  },
  {
    icon: ShieldCheck,
    title: "每一步可回滚",
    desc: "修改文件前自动创建快照，破坏性操作需要人工确认；DRC 结果、工具调用过程全部可见。",
  },
  {
    icon: Users,
    title: "多用户在线协作",
    desc: "独立账号、独立工程工作区、独立对话与长期记忆；实时显示在线成员与处理状态。",
  },
  {
    icon: Layers,
    title: "布局实时预览",
    desc: "右侧面板即时渲染当前 PCB，Agent 每次改动后自动刷新，随时下载文件在 KiCad 中打开。",
  },
  {
    icon: Sparkles,
    title: "技能与记忆",
    desc: "内置电源布局、DRC 修复、原理图审查等技能；Agent 记住你的栅格、单位与命名习惯。",
  },
];

const STEPS = [
  { n: "01", title: "导入工程", desc: "上传 KiCad 工程 ZIP 或单个 .kicad_pcb / .kicad_sch，或直接加载示例板。" },
  { n: "02", title: "描述任务", desc: "用中文说明目标，Agent 先给出计划，再逐步调用工具执行并展示过程。" },
  { n: "03", title: "确认与回滚", desc: "查看 DRC 卡片与布局预览，不满意就一键恢复快照，满意就下载到 KiCad。" },
];

export function LandingPage() {
  const user = useAuth((s) => s.user);
  const nav = useNavigate();
  const { appName, logoUrl, footer, copyright } = useBranding();
  const [info, setInfo] = useState<PublicInfo | null>(null);

  useEffect(() => {
    api.publicInfo().then(setInfo).catch(() => undefined);
  }, []);

  const primary = user ? "进入工作台" : "免费注册";
  const goPrimary = () => nav(user ? "/app" : info?.allow_registration === false ? "/login" : "/login?mode=register");

  return (
    <div className="min-h-full bg-[radial-gradient(1100px_520px_at_50%_-10%,_#e0e7ff_0%,_#f4f5fa_60%)] text-ink">
      {/* nav */}
      <header className="mx-auto flex max-w-6xl items-center justify-between px-6 py-5">
        <div className="flex items-center gap-2.5">
          <BrandMark size={36} logoUrl={logoUrl} className="rounded-xl" />
          <span className="text-[16px] font-semibold">{appName}</span>
          <span className="rounded-md bg-brand-50 px-1.5 py-0.5 text-[11px] font-medium text-brand-600">Beta</span>
        </div>
        <nav className="hidden items-center gap-7 text-[13.5px] text-slate-600 md:flex">
          <a href="#features" className="hover:text-ink">功能</a>
          <a href="#how" className="hover:text-ink">工作流程</a>
        </nav>
        <div className="flex items-center gap-2">
          {user ? (
            <Link to="/app" className="rounded-lg bg-brand-500 px-4 py-2 text-[13px] font-medium text-white shadow-sm hover:bg-brand-600">
              进入工作台
            </Link>
          ) : (
            <>
              <Link to="/login" className="rounded-lg px-3.5 py-2 text-[13px] font-medium text-slate-700 hover:bg-white">
                登录
              </Link>
              {info?.allow_registration !== false && (
                <Link to="/login?mode=register" className="rounded-lg bg-brand-500 px-4 py-2 text-[13px] font-medium text-white shadow-sm hover:bg-brand-600">
                  免费注册
                </Link>
              )}
            </>
          )}
        </div>
      </header>

      {/* hero */}
      <section className="mx-auto max-w-6xl px-6 pb-10 pt-10 text-center md:pt-16">
        <div className="mx-auto mb-5 inline-flex items-center gap-2 rounded-full border border-brand-100 bg-white/80 px-3 py-1 text-[12px] text-brand-700 shadow-sm">
          <Bot size={14} />
          基于 LangGraph · Deep Agents 的 KiCad 智能助手
          {info && (
            <span className="ml-1 rounded-full bg-emerald-50 px-2 py-0.5 text-[11px] text-emerald-700">
              {info.agent_ready ? `在线 · ${info.tool_count} 个工具${info.upstream_tool_count ? `（含开源插件 ${info.upstream_tool_count} 个）` : ""}` : "启动中"}
            </span>
          )}
        </div>
        <h1 className="mx-auto max-w-3xl text-[40px] font-bold leading-[1.15] tracking-tight md:text-[52px]">
          让 AI 帮你完成
          <span className="bg-gradient-to-r from-brand-600 to-violet-500 bg-clip-text text-transparent"> PCB 布局与 DRC 检查</span>
        </h1>
        <p className="mx-auto mt-5 max-w-2xl text-[15.5px] leading-relaxed text-slate-600">
          上传 KiCad 工程，用中文描述目标。Agent 会读取板级信息、规划步骤、移动元件、运行设计规则检查，
          并在每一步修改前自动保存快照——你只需要审阅与确认。
        </p>
        <div className="mt-8 flex flex-wrap items-center justify-center gap-3">
          <button onClick={goPrimary} className="flex items-center gap-2 rounded-xl bg-brand-500 px-6 py-3 text-[14.5px] font-semibold text-white shadow-[0_8px_24px_rgba(99,102,241,0.35)] transition hover:bg-brand-600">
            {primary} <ArrowRight size={16} />
          </button>
          {!user && (
            <Link to="/login" className="rounded-xl border border-line bg-white px-6 py-3 text-[14.5px] font-medium text-slate-700 hover:bg-slate-50">
              已有账号，登录
            </Link>
          )}
        </div>
        {info?.demo_mode && (
          <p className="mt-3 text-[12px] text-amber-600">当前为离线演示模型，管理员可在后台「模型配置」中接入真实大模型。</p>
        )}

        {/* product shot */}
        <div className="mx-auto mt-12 max-w-5xl">
          <div className="overflow-hidden rounded-2xl border border-line bg-white shadow-[0_30px_80px_-20px_rgba(30,41,59,0.35)]">
            <div className="flex items-center gap-1.5 border-b border-line bg-slate-50 px-4 py-2.5">
              <span className="h-2.5 w-2.5 rounded-full bg-rose-400" />
              <span className="h-2.5 w-2.5 rounded-full bg-amber-400" />
              <span className="h-2.5 w-2.5 rounded-full bg-emerald-400" />
              <span className="ml-3 rounded-md bg-white px-3 py-0.5 text-[11px] text-slate-400">{appName} — 优化电源模块布局</span>
            </div>
            <img src="/screenshot.png" alt="KiCad AI Assistant 界面" className="block w-full" loading="eager" />
          </div>
        </div>
      </section>

      {/* features */}
      <section id="features" className="mx-auto max-w-6xl px-6 py-16">
        <h2 className="text-center text-[28px] font-bold tracking-tight">为硬件工程师设计的 Agent</h2>
        <p className="mx-auto mt-2 max-w-xl text-center text-[14px] text-slate-500">不是聊天机器人，而是能真正读写你的原理图与 PCB 文件、并对每一步负责的工程助手。</p>
        <div className="mt-10 grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-3">
          {FEATURES.map(({ icon: Icon, title, desc }) => (
            <div key={title} className="card p-5 transition hover:-translate-y-0.5 hover:shadow-md">
              <div className="mb-3 flex h-10 w-10 items-center justify-center rounded-xl bg-brand-50 text-brand-600">
                <Icon size={20} />
              </div>
              <h3 className="text-[15px] font-semibold">{title}</h3>
              <p className="mt-1.5 text-[13px] leading-relaxed text-slate-500">{desc}</p>
            </div>
          ))}
        </div>
      </section>

      {/* how it works */}
      <section id="how" className="bg-white/70 py-16">
        <div className="mx-auto max-w-6xl px-6">
          <h2 className="text-center text-[28px] font-bold tracking-tight">三步开始</h2>
          <div className="mt-10 grid grid-cols-1 gap-6 md:grid-cols-3">
            {STEPS.map((s) => (
              <div key={s.n} className="relative rounded-2xl border border-line bg-white p-6">
                <span className="text-[13px] font-semibold text-brand-500">{s.n}</span>
                <h3 className="mt-2 text-[16px] font-semibold">{s.title}</h3>
                <p className="mt-1.5 text-[13px] leading-relaxed text-slate-500">{s.desc}</p>
              </div>
            ))}
          </div>
          <div className="mx-auto mt-10 max-w-3xl rounded-2xl border border-line bg-slate-900 p-5 text-left font-mono text-[12.5px] leading-relaxed text-slate-200 shadow-lg">
            <div className="text-slate-400">用户</div>
            <div className="mb-3">帮我优化一下电源模块的布局，电容尽量靠近芯片的电源引脚，并检查是否有 DRC 问题。</div>
            <div className="text-slate-400">KiCad AI 助手</div>
            <div>1. get_board_info → 2. list_footprints → 3. set_footprint_position(C3, C4) → 4. run_drc_check</div>
            <div className="mt-1 text-emerald-400">✔ DRC 检查通过 · C3/C4 已移至 U1 电源引脚 1.7 mm 处 · 已自动保存 2 个快照</div>
          </div>
        </div>
      </section>

      {/* Bottom feature badges (dynamically configured in admin) */}
      {footer ? (
        <section className="mx-auto max-w-5xl px-6 py-12">
          <div className="flex flex-wrap items-center justify-center gap-6 text-[12.5px] text-slate-500">
            {footer.split(/\s{2,}|\s*[|·]\s*/).filter(Boolean).map((item: string, i: number) => (
              <span key={i} className="inline-flex items-center gap-1.5 font-medium">
                {item}
              </span>
            ))}
          </div>
        </section>
      ) : null}

      <footer className="border-t border-line py-8 text-center text-[12px] text-slate-400">
        {copyright || `${appName} · v${info?.version ?? "0.1.0"}`}
      </footer>
    </div>
  );
}
