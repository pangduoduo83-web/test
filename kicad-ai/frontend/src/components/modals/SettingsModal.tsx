import { Brain, LogOut, Save } from "lucide-react";
import { useEffect, useState } from "react";
import { Avatar } from "@/components/ui/Avatar";
import { Modal } from "@/components/ui/Modal";
import { api } from "@/lib/api";
import { useAuth } from "@/store/auth";

export function SettingsModal({ open, onClose }: { open: boolean; onClose: () => void }) {
  const { user, logout } = useAuth();
  const [memory, setMemory] = useState("");
  const [saved, setSaved] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (open)
      api
        .memory()
        .then((m) => setMemory(m.content))
        .catch((e) => setError((e as Error).message));
  }, [open]);

  const save = async () => {
    try {
      await api.saveMemory(memory);
      setSaved(true);
      setTimeout(() => setSaved(false), 1500);
    } catch (e) {
      setError((e as Error).message);
    }
  };

  return (
    <Modal
      open={open}
      onClose={onClose}
      title="设置"
      width="max-w-2xl"
      footer={
        <div className="flex items-center justify-between">
          <button onClick={logout} className="flex items-center gap-1.5 rounded-md px-3 py-1.5 text-xs text-rose-600 hover:bg-rose-50">
            <LogOut size={14} /> 退出登录
          </button>
          <button onClick={save} className="flex items-center gap-1.5 rounded-md bg-brand-500 px-3 py-1.5 text-xs font-medium text-white hover:bg-brand-600">
            <Save size={14} /> {saved ? "已保存" : "保存记忆"}
          </button>
        </div>
      }
    >
      <div className="space-y-5">
        <section className="flex items-center gap-3 rounded-xl border border-line bg-slate-50/60 px-4 py-3">
          <Avatar name={user?.display_name ?? "?"} color={user?.avatar_color} size={44} />
          <div>
            <div className="text-[14px] font-semibold text-ink">{user?.display_name}</div>
            <div className="text-[12px] text-slate-500">
              @{user?.username} · {user?.role === "admin" ? "管理员" : "普通用户"}
            </div>
          </div>
        </section>
        <section>
          <h3 className="mb-1 flex items-center gap-1.5 text-[13px] font-semibold text-ink">
            <Brain size={14} /> 长期记忆 <code className="text-[11px] font-normal text-slate-400">/memories/AGENTS.md</code>
          </h3>
          <p className="mb-2 text-[11.5px] text-slate-500">
            由 Deep Agents 内置记忆机制维护，仅对你可见，会在每次对话开始时注入代理的系统提示。代理会在你表达偏好时自动更新；你也可以直接编辑。
          </p>
          <textarea value={memory} onChange={(e) => setMemory(e.target.value)} rows={14} className="w-full rounded-xl border border-line bg-white px-3 py-2 font-mono text-[12px] leading-relaxed outline-none focus:border-brand-300" />
          {error && <p className="mt-1 text-xs text-rose-600">{error}</p>}
        </section>
      </div>
    </Modal>
  );
}
