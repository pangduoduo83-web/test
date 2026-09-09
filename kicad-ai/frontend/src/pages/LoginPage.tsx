import { ArrowLeft, Loader2 } from "lucide-react";
import { useEffect, useState, type FormEvent } from "react";
import { Link, Navigate, useNavigate, useSearchParams } from "react-router-dom";
import { BrandMark } from "@/components/ui/Avatar";
import { api } from "@/lib/api";
import type { PublicInfo } from "@/lib/types";
import { useAuth } from "@/store/auth";
import { useBranding } from "@/store/branding";

export function LoginPage() {
  const { login, register, error, user, loading: authLoading } = useAuth();
  const branding = useBranding();
  const nav = useNavigate();
  const [params] = useSearchParams();
  const [info, setInfo] = useState<PublicInfo | null>(null);
  const [mode, setMode] = useState<"login" | "register">(params.get("mode") === "register" ? "register" : "login");
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [displayName, setDisplayName] = useState("");
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    api
      .publicInfo()
      .then((i) => {
        setInfo(i);
        if (!i.allow_registration) setMode("login");
      })
      .catch(() => undefined);
  }, []);

  if (!authLoading && user) return <Navigate to={user.role === "admin" && params.get("next") === "admin" ? "/admin" : "/app"} replace />;

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setBusy(true);
    try {
      if (mode === "login") await login(username, password);
      else await register(username, password, displayName || undefined);
      nav("/app", { replace: true });
    } catch {
      /* error shown from store */
    } finally {
      setBusy(false);
    }
  };

  const canRegister = info?.allow_registration !== false;

  return (
    <div className="flex min-h-full items-center justify-center bg-[radial-gradient(ellipse_at_top,_#eef2ff,_#f4f5fa_60%)] p-6">
      <div className="w-full max-w-sm">
        <Link to="/" className="mb-4 inline-flex items-center gap-1 text-[12.5px] text-slate-500 hover:text-ink">
          <ArrowLeft size={14} /> 返回首页
        </Link>
        <div className="card p-7">
          <div className="mb-6 flex items-center gap-2.5">
            <BrandMark size={38} logoUrl={branding.logoUrl} />
            <div>
              <div className="text-[15px] font-semibold text-ink">{branding.appName}</div>
              <div className="text-[11px] text-slate-400">{branding.tagline}</div>
            </div>
          </div>

          {canRegister ? (
            <div className="mb-5 grid grid-cols-2 rounded-lg bg-slate-100 p-1 text-xs font-medium">
              {(["login", "register"] as const).map((m) => (
                <button key={m} type="button" onClick={() => setMode(m)} className={`rounded-md py-1.5 transition ${mode === m ? "bg-white text-ink shadow-sm" : "text-slate-500"}`}>
                  {m === "login" ? "登录" : "注册"}
                </button>
              ))}
            </div>
          ) : (
            <p className="mb-5 rounded-lg bg-slate-50 px-3 py-2 text-[12px] text-slate-500">当前未开放注册，请联系管理员获取账号。</p>
          )}

          <form onSubmit={submit} className="space-y-3">
            <Field label="用户名" value={username} onChange={setUsername} autoFocus />
            {mode === "register" && <Field label="显示名称（可选）" value={displayName} onChange={setDisplayName} />}
            <Field label="密码" type="password" value={password} onChange={setPassword} />
            {error && <p className="rounded-md bg-rose-50 px-3 py-2 text-xs text-rose-600">{error}</p>}
            <button type="submit" disabled={busy || !username || password.length < 6} className="flex w-full items-center justify-center gap-2 rounded-lg bg-brand-500 py-2.5 text-sm font-medium text-white transition hover:bg-brand-600 disabled:opacity-50">
              {busy && <Loader2 size={16} className="animate-spin" />}
              {mode === "login" ? "登录" : "创建账号"}
            </button>
          </form>
          <p className="mt-4 text-center text-[11px] text-slate-400">
            {mode === "register" ? "密码至少 6 位。第一个注册的用户自动成为管理员。" : "忘记密码请联系管理员在后台重置。"}
          </p>
        </div>
      </div>
    </div>
  );
}

function Field({ label, value, onChange, type = "text", autoFocus }: { label: string; value: string; onChange: (v: string) => void; type?: string; autoFocus?: boolean }) {
  return (
    <label className="block">
      <span className="mb-1 block text-xs font-medium text-slate-600">{label}</span>
      <input
        type={type}
        value={value}
        autoFocus={autoFocus}
        autoComplete={type === "password" ? "current-password" : "username"}
        onChange={(e) => onChange(e.target.value)}
        className="w-full rounded-lg border border-line px-3 py-2 text-sm outline-none transition focus:border-brand-300 focus:shadow-[0_0_0_3px_rgba(99,102,241,0.12)]"
      />
    </label>
  );
}
