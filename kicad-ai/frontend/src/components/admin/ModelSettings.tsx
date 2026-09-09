import { CheckCircle2, Cpu, Eye, EyeOff, Loader2, PlugZap, Save, XCircle } from "lucide-react";
import { useEffect, useState } from "react";
import { api } from "@/lib/api";
import type { LlmTestResult, ModelPreset, RuntimeSettings, ThinkingMode } from "@/lib/types";

const PROVIDERS = [
  { value: "openai", label: "OpenAI" },
  { value: "custom", label: "OpenAI 兼容接口（DeepSeek / Qwen / Moonshot / GLM / Ollama…）" },
  { value: "anthropic", label: "Anthropic (Claude)" },
  { value: "google_genai", label: "Google Gemini" },
  { value: "mock", label: "离线演示模型（不调用外部 API）" },
];

interface Form {
  llm_provider: string;
  llm_model: string;
  llm_api_key: string;
  llm_base_url: string;
  llm_temperature: number;
  llm_context_tokens: number;
  llm_max_tokens: number | "";
  llm_thinking: ThinkingMode;
  llm_thinking_budget: number;
  llm_thinking_style: string;
}

export function ModelSettings() {
  const [settings, setSettings] = useState<RuntimeSettings | null>(null);
  const [form, setForm] = useState<Form | null>(null);
  const [showKey, setShowKey] = useState(false);
  const [testing, setTesting] = useState(false);
  const [test, setTest] = useState<LlmTestResult | null>(null);
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState<{ tone: "ok" | "err"; text: string } | null>(null);

  useEffect(() => {
    api.admin.settings().then((s) => {
      setSettings(s);
      const v = s.values;
      setForm({
        llm_provider: String(v.llm_provider ?? "openai"),
        llm_model: String(v.llm_model ?? ""),
        llm_api_key: "",
        llm_base_url: String(v.llm_base_url ?? ""),
        llm_temperature: Number(v.llm_temperature ?? 0.2),
        llm_context_tokens: Number(v.llm_context_tokens ?? 128000),
        llm_max_tokens: v.llm_max_tokens == null ? "" : Number(v.llm_max_tokens),
        llm_thinking: String(v.llm_thinking ?? "off") as ThinkingMode,
        llm_thinking_budget: Number(v.llm_thinking_budget ?? 4096),
        llm_thinking_style: String(v.llm_thinking_style ?? "auto"),
      });
    });
  }, []);

  if (!settings || !form) return <p className="text-sm text-slate-400">加载中…</p>;

  const set = <K extends keyof Form>(k: K, v: Form[K]) => setForm({ ...form, [k]: v });

  const applyPreset = (p: ModelPreset) => {
    setForm({
      ...form,
      llm_provider: p.provider,
      llm_model: p.model,
      llm_base_url: p.base_url,
      llm_context_tokens: p.context_tokens ?? form.llm_context_tokens,
      llm_thinking_style: "auto",
    });
    setTest(null);
  };

  const runTest = async () => {
    setTesting(true);
    setTest(null);
    try {
      setTest(
        await api.admin.testLlm({
          llm_provider: form.llm_provider,
          llm_model: form.llm_model,
          llm_api_key: form.llm_api_key || undefined,
          llm_base_url: form.llm_base_url || undefined,
          llm_temperature: form.llm_temperature,
          llm_thinking: form.llm_thinking,
          llm_thinking_budget: form.llm_thinking_budget,
          llm_thinking_style: form.llm_thinking_style,
        }),
      );
    } catch (e) {
      setTest({ ok: false, latency_ms: 0, error: (e as Error).message, model: `${form.llm_provider}:${form.llm_model}` });
    } finally {
      setTesting(false);
    }
  };

  const save = async () => {
    setSaving(true);
    setMessage(null);
    try {
      const res = await api.admin.saveSettings({
        llm_provider: form.llm_provider,
        llm_model: form.llm_model,
        llm_api_key: form.llm_api_key, // empty → keep existing
        llm_base_url: form.llm_base_url,
        llm_temperature: form.llm_temperature,
        llm_context_tokens: form.llm_context_tokens,
        llm_max_tokens: form.llm_max_tokens === "" ? null : form.llm_max_tokens,
        llm_thinking: form.llm_thinking,
        llm_thinking_budget: form.llm_thinking_budget,
        llm_thinking_style: form.llm_thinking_style,
      });
      setSettings({ ...settings, values: res.values, runtime: { ...settings.runtime, ...res.runtime } });
      setForm({ ...form, llm_api_key: "" });
      setMessage(
        res.error
          ? { tone: "err", text: `已保存，但 Agent 重建失败：${res.error}` }
          : { tone: "ok", text: res.agent_reloaded ? `已保存并热重载，当前模型 ${res.runtime.model}，工具 ${res.runtime.tool_count} 个` : "已保存" },
      );
    } catch (e) {
      setMessage({ tone: "err", text: (e as Error).message });
    } finally {
      setSaving(false);
    }
  };

  const keySet = !!settings.values.llm_api_key_set;

  return (
    <div className="space-y-6">
      <header>
        <h1 className="text-[22px] font-semibold text-ink">模型配置</h1>
        <p className="text-[13px] text-slate-500">
          当前运行：<code className="rounded bg-slate-100 px-1.5 py-0.5 text-[12px]">{settings.runtime.model}</code>
          <span className="ml-2">工具 {settings.runtime.tool_count} 个</span>。保存后立即热重载，无需重启。
        </p>
      </header>

      <section className="card p-5">
        <h2 className="mb-3 text-[14px] font-semibold text-ink">快速选择</h2>
        <div className="flex flex-wrap gap-2">
          {settings.presets.map((p) => (
            <button
              key={p.name}
              onClick={() => applyPreset(p)}
              className={`rounded-lg border px-3 py-1.5 text-[12.5px] transition ${
                form.llm_provider === p.provider && form.llm_model === p.model ? "border-brand-300 bg-brand-50 text-brand-700" : "border-line bg-white text-slate-600 hover:bg-slate-50"
              }`}
            >
              {p.name}
            </button>
          ))}
        </div>
      </section>

      <section className="card p-5">
        <h2 className="mb-4 flex items-center gap-2 text-[14px] font-semibold text-ink">
          <Cpu size={16} className="text-brand-500" /> 连接参数
        </h2>
        <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
          <Field label="提供方">
            <select value={form.llm_provider} onChange={(e) => set("llm_provider", e.target.value)} className={inputCls}>
              {PROVIDERS.map((p) => (
                <option key={p.value} value={p.value}>
                  {p.label}
                </option>
              ))}
            </select>
          </Field>
          <Field label="模型名称" hint="如 gpt-5.6、claude-sonnet-5、deepseek-v4-flash、qwen3.7-plus、kimi-k3、glm-5.3">
            <input value={form.llm_model} onChange={(e) => set("llm_model", e.target.value)} className={inputCls} placeholder="gpt-5.6" />
          </Field>
          <Field label="API Key" hint={keySet ? `已保存（${settings.values.llm_api_key}）。留空表示保留现有密钥。` : "尚未配置密钥"}>
            <div className="relative">
              <input
                type={showKey ? "text" : "password"}
                value={form.llm_api_key}
                onChange={(e) => set("llm_api_key", e.target.value)}
                className={`${inputCls} pr-10`}
                placeholder={keySet ? "输入新密钥以替换" : "sk-..."}
                autoComplete="off"
              />
              <button type="button" onClick={() => setShowKey((v) => !v)} className="absolute right-2 top-1/2 -translate-y-1/2 rounded p-1 text-slate-400 hover:text-ink">
                {showKey ? <EyeOff size={15} /> : <Eye size={15} />}
              </button>
            </div>
          </Field>
          <Field label="Base URL" hint="OpenAI 兼容接口必填，例如 https://api.deepseek.com/v1；官方 OpenAI / Anthropic 留空">
            <input value={form.llm_base_url} onChange={(e) => set("llm_base_url", e.target.value)} className={inputCls} placeholder="https://api.deepseek.com/v1" />
          </Field>
          <Field label="温度 (temperature)" hint="0 ~ 1，工具调用任务建议 0.1 ~ 0.3">
            <input type="number" step="0.1" min="0" max="2" value={form.llm_temperature} onChange={(e) => set("llm_temperature", Number(e.target.value))} className={inputCls} />
          </Field>
          <Field label="上下文窗口 (tokens)" hint="大模型最大上下文（如 64k、128k），用于计算上下文占用百分比。建议 64000 ~ 128000">
            <input type="number" step="8000" min="16000" placeholder="128000" value={form.llm_context_tokens} onChange={(e) => set("llm_context_tokens", Number(e.target.value))} className={inputCls} />
          </Field>
          <Field label="单次最大输出 (max_tokens)" hint="留空使用提供方默认值">
            <input type="number" step="256" min="256" value={form.llm_max_tokens} onChange={(e) => set("llm_max_tokens", e.target.value === "" ? "" : Number(e.target.value))} className={inputCls} placeholder="默认" />
          </Field>
          <Field label="默认思考模式" hint="用户可在聊天输入框中临时覆盖；工具密集任务建议默认关闭。">
            <select value={form.llm_thinking} onChange={(e) => set("llm_thinking", e.target.value as ThinkingMode)} className={inputCls}>
              <option value="default">跟随模型默认</option>
              <option value="off">关闭思考（最快）</option>
              <option value="fast">快速思考</option>
              <option value="deep">深度思考</option>
            </select>
          </Field>
          <Field label="快速思考预算 (tokens)" hint="仅用于不支持 reasoning_effort 档位的兼容接口，如百炼；0 表示不设置预算。">
            <input type="number" step="512" min="0" value={form.llm_thinking_budget} onChange={(e) => set("llm_thinking_budget", Number(e.target.value))} className={inputCls} />
          </Field>
          <Field label="思考参数协议" hint="通常保持自动；自建 vLLM / SGLang 或特殊代理接口可手动指定。">
            <select value={form.llm_thinking_style} onChange={(e) => set("llm_thinking_style", e.target.value)} className={inputCls}>
              <option value="auto">自动检测</option>
              <option value="bailian">百炼 / DashScope (enable_thinking)</option>
              <option value="thinking_type">DeepSeek / GLM / Kimi (thinking.type)</option>
              <option value="chat_template_kwargs">vLLM / SGLang (chat_template_kwargs)</option>
              <option value="ollama">Ollama (think)</option>
              <option value="none">不注入兼容参数</option>
            </select>
          </Field>
        </div>

        <div className="mt-5 flex flex-wrap items-center gap-3 border-t border-line pt-4">
          <button onClick={runTest} disabled={testing || !form.llm_model} className="flex items-center gap-1.5 rounded-lg border border-line bg-white px-4 py-2 text-[13px] font-medium text-slate-700 hover:bg-slate-50 disabled:opacity-50">
            {testing ? <Loader2 size={15} className="animate-spin" /> : <PlugZap size={15} />} 测试连接
          </button>
          <button onClick={save} disabled={saving || !form.llm_model} className="flex items-center gap-1.5 rounded-lg bg-brand-500 px-4 py-2 text-[13px] font-medium text-white hover:bg-brand-600 disabled:opacity-50">
            {saving ? <Loader2 size={15} className="animate-spin" /> : <Save size={15} />} 保存并热重载
          </button>
          {test && (
            <span className={`flex items-center gap-1.5 text-[12.5px] ${test.ok ? "text-emerald-600" : "text-rose-600"}`}>
              {test.ok ? <CheckCircle2 size={15} /> : <XCircle size={15} />}
              {test.ok
                ? `连接成功 · ${test.latency_ms} ms · 回复「${(test.reply ?? "").slice(0, 40)}」${test.reasoning_chars ? ` · 思考 ${test.reasoning_chars} 字符` : ""}`
                : `连接失败：${test.error}`}
            </span>
          )}
        </div>
        {message && <p className={`mt-3 rounded-lg px-3 py-2 text-[12.5px] ${message.tone === "ok" ? "bg-emerald-50 text-emerald-700" : "bg-rose-50 text-rose-700"}`}>{message.text}</p>}
      </section>

      <section className="card p-5 text-[12.5px] text-slate-600">
        <h2 className="mb-2 text-[14px] font-semibold text-ink">说明</h2>
        <ul className="list-disc space-y-1 pl-5">
          <li>密钥加密存储在数据库中（基于服务器 JWT_SECRET 派生的密钥），界面只显示掩码。</li>
          <li>后台保存的配置优先于 <code className="rounded bg-slate-100 px-1">.env</code>；容器重启后依然生效。</li>
          <li>「测试连接」使用表单中的值（未填密钥时使用已保存的密钥）向模型发送一句 ping，不会写入配置。</li>
          <li>国内直连 OpenAI / Anthropic 可能超时，建议使用 DeepSeek、通义千问等国内 OpenAI 兼容接口。</li>
        </ul>
      </section>
    </div>
  );
}

const inputCls = "w-full rounded-lg border border-line bg-white px-3 py-2 text-[13px] outline-none transition focus:border-brand-300 focus:shadow-[0_0_0_3px_rgba(99,102,241,0.12)]";

export function Field({ label, hint, children }: { label: string; hint?: string; children: React.ReactNode }) {
  return (
    <label className="block">
      <span className="mb-1 block text-[12.5px] font-medium text-slate-700">{label}</span>
      {children}
      {hint && <span className="mt-1 block text-[11px] text-slate-400">{hint}</span>}
    </label>
  );
}
