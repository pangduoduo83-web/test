import { KeyRound, Loader2, Plus, Search, ShieldCheck, ShieldOff, Trash2, UserCheck, UserX } from "lucide-react";
import { useCallback, useEffect, useState } from "react";
import { Avatar } from "@/components/ui/Avatar";
import { Modal } from "@/components/ui/Modal";
import { api } from "@/lib/api";
import { relativeDay } from "@/lib/format";
import type { AdminUser } from "@/lib/types";
import { useAuth } from "@/store/auth";

const inputCls = "w-full rounded-lg border border-line bg-white px-3 py-2 text-[13px] outline-none transition focus:border-brand-300 focus:shadow-[0_0_0_3px_rgba(99,102,241,0.12)]";

export function UsersPanel() {
  const me = useAuth((s) => s.user);
  const [users, setUsers] = useState<AdminUser[]>([]);
  const [q, setQ] = useState("");
  const [busy, setBusy] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [createOpen, setCreateOpen] = useState(false);
  const [resetFor, setResetFor] = useState<AdminUser | null>(null);
  const [deleteFor, setDeleteFor] = useState<AdminUser | null>(null);

  const load = useCallback(async (query = q) => {
    try {
      setUsers(await api.admin.users(query));
      setError(null);
    } catch (e) {
      setError((e as Error).message);
    }
  }, [q]);

  useEffect(() => {
    load("");
  }, [load]);

  const act = async (id: string, fn: () => Promise<unknown>) => {
    setBusy(id);
    setError(null);
    try {
      await fn();
      await load();
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(null);
    }
  };

  return (
    <div className="space-y-5">
      <header className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="text-[22px] font-semibold text-ink">用户管理</h1>
          <p className="text-[13px] text-slate-500">共 {users.length} 个账号。可调整角色、启停、重置密码或删除（删除会同时移除其工程与对话）。</p>
        </div>
        <div className="flex items-center gap-2">
          <div className="flex items-center gap-2 rounded-lg border border-line bg-white px-3 py-2">
            <Search size={14} className="text-slate-400" />
            <input
              value={q}
              onChange={(e) => setQ(e.target.value)}
              onKeyDown={(e) => e.key === "Enter" && load(q)}
              placeholder="搜索用户名 / 昵称 / 邮箱"
              className="w-56 bg-transparent text-[13px] outline-none"
            />
          </div>
          <button onClick={() => setCreateOpen(true)} className="flex items-center gap-1.5 rounded-lg bg-brand-500 px-3.5 py-2 text-[13px] font-medium text-white hover:bg-brand-600">
            <Plus size={15} /> 新建用户
          </button>
        </div>
      </header>

      {error && <p className="rounded-lg bg-rose-50 px-4 py-2.5 text-[13px] text-rose-700">{error}</p>}

      <div className="card overflow-hidden">
        <table className="w-full text-left text-[13px]">
          <thead className="bg-slate-50 text-[12px] text-slate-500">
            <tr>
              <th className="px-4 py-2.5 font-medium">用户</th>
              <th className="px-3 py-2.5 font-medium">角色</th>
              <th className="px-3 py-2.5 font-medium">状态</th>
              <th className="px-3 py-2.5 font-medium">工程 / 对话 / 工具调用</th>
              <th className="px-3 py-2.5 font-medium">注册</th>
              <th className="px-3 py-2.5 font-medium">最近登录</th>
              <th className="px-3 py-2.5 text-right font-medium">操作</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-line">
            {users.map((u) => {
              const self = u.id === me?.id;
              const loading = busy === u.id;
              return (
                <tr key={u.id} className="hover:bg-slate-50/60">
                  <td className="px-4 py-2.5">
                    <div className="flex items-center gap-2.5">
                      <Avatar name={u.display_name} color={u.avatar_color} size={30} />
                      <div className="min-w-0">
                        <div className="flex items-center gap-1.5 font-medium text-ink">
                          {u.display_name}
                          {self && <span className="rounded bg-slate-100 px-1 text-[10px] text-slate-500">我</span>}
                        </div>
                        <div className="text-[11.5px] text-slate-400">@{u.username}{u.email ? ` · ${u.email}` : ""}</div>
                      </div>
                    </div>
                  </td>
                  <td className="px-3 py-2.5">
                    <span className={`rounded-md px-1.5 py-0.5 text-[11.5px] font-medium ${u.role === "admin" ? "bg-brand-50 text-brand-700" : "bg-slate-100 text-slate-600"}`}>
                      {u.role === "admin" ? "管理员" : "普通用户"}
                    </span>
                  </td>
                  <td className="px-3 py-2.5">
                    <span className={`flex items-center gap-1 text-[12px] ${u.is_active ? "text-emerald-600" : "text-rose-500"}`}>
                      <span className={`h-1.5 w-1.5 rounded-full ${u.is_active ? "bg-emerald-500" : "bg-rose-500"}`} />
                      {u.is_active ? "启用" : "已禁用"}
                    </span>
                  </td>
                  <td className="px-3 py-2.5 text-slate-600">
                    {u.project_count} / {u.conversation_count} / {u.tool_call_count}
                  </td>
                  <td className="px-3 py-2.5 text-slate-500">{relativeDay(u.created_at)}</td>
                  <td className="px-3 py-2.5 text-slate-500">{u.last_login_at ? relativeDay(u.last_login_at) : "—"}</td>
                  <td className="px-3 py-2.5">
                    <div className="flex items-center justify-end gap-1">
                      {loading ? (
                        <Loader2 size={14} className="animate-spin text-slate-400" />
                      ) : (
                        <>
                          <IconBtn
                            title={u.role === "admin" ? "降为普通用户" : "设为管理员"}
                            disabled={self}
                            onClick={() => act(u.id, () => api.admin.updateUser(u.id, { role: u.role === "admin" ? "user" : "admin" }))}
                          >
                            {u.role === "admin" ? <ShieldOff size={14} /> : <ShieldCheck size={14} />}
                          </IconBtn>
                          <IconBtn title={u.is_active ? "禁用账号" : "启用账号"} disabled={self} onClick={() => act(u.id, () => api.admin.updateUser(u.id, { is_active: !u.is_active }))}>
                            {u.is_active ? <UserX size={14} /> : <UserCheck size={14} />}
                          </IconBtn>
                          <IconBtn title="重置密码" onClick={() => setResetFor(u)}>
                            <KeyRound size={14} />
                          </IconBtn>
                          <IconBtn title="删除用户" disabled={self} danger onClick={() => setDeleteFor(u)}>
                            <Trash2 size={14} />
                          </IconBtn>
                        </>
                      )}
                    </div>
                  </td>
                </tr>
              );
            })}
            {users.length === 0 && (
              <tr>
                <td colSpan={7} className="px-4 py-10 text-center text-[13px] text-slate-400">
                  没有匹配的用户
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>

      <CreateUserModal open={createOpen} onClose={() => setCreateOpen(false)} onCreated={() => load()} />
      <ResetPasswordModal user={resetFor} onClose={() => setResetFor(null)} />
      <Modal open={!!deleteFor} onClose={() => setDeleteFor(null)} title="删除用户" width="max-w-md">
        {deleteFor && (
          <div className="space-y-4 text-[13px] text-slate-600">
            <p>
              确定删除 <b>{deleteFor.display_name}</b>（@{deleteFor.username}）？其 {deleteFor.project_count} 个工程、{deleteFor.conversation_count} 个对话及工作区文件将被永久删除。
            </p>
            <div className="flex justify-end gap-2">
              <button onClick={() => setDeleteFor(null)} className="rounded-lg border border-line px-3 py-1.5 text-[13px] text-slate-600 hover:bg-slate-50">
                取消
              </button>
              <button
                onClick={() => {
                  const u = deleteFor;
                  setDeleteFor(null);
                  act(u.id, () => api.admin.deleteUser(u.id));
                }}
                className="rounded-lg bg-rose-500 px-3 py-1.5 text-[13px] font-medium text-white hover:bg-rose-600"
              >
                确认删除
              </button>
            </div>
          </div>
        )}
      </Modal>
    </div>
  );
}

function IconBtn({ children, title, onClick, disabled, danger }: { children: React.ReactNode; title: string; onClick: () => void; disabled?: boolean; danger?: boolean }) {
  return (
    <button
      title={title}
      disabled={disabled}
      onClick={onClick}
      className={`rounded-md p-1.5 transition disabled:opacity-30 ${danger ? "text-slate-400 hover:bg-rose-50 hover:text-rose-600" : "text-slate-400 hover:bg-slate-100 hover:text-ink"}`}
    >
      {children}
    </button>
  );
}

function CreateUserModal({ open, onClose, onCreated }: { open: boolean; onClose: () => void; onCreated: () => void }) {
  const [form, setForm] = useState({ username: "", password: "", display_name: "", email: "", role: "user" as "user" | "admin" });
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const submit = async () => {
    setBusy(true);
    setError(null);
    try {
      await api.admin.createUser({ ...form, display_name: form.display_name || undefined, email: form.email || undefined });
      setForm({ username: "", password: "", display_name: "", email: "", role: "user" });
      onCreated();
      onClose();
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  };

  return (
    <Modal open={open} onClose={onClose} title="新建用户" subtitle="关闭注册时，管理员可在此为成员创建账号。" width="max-w-md">
      <div className="space-y-3">
        <L label="用户名 *">
          <input value={form.username} onChange={(e) => setForm({ ...form, username: e.target.value })} className={inputCls} />
        </L>
        <L label="初始密码 *（≥ 6 位）">
          <input type="text" value={form.password} onChange={(e) => setForm({ ...form, password: e.target.value })} className={inputCls} />
        </L>
        <L label="显示名称">
          <input value={form.display_name} onChange={(e) => setForm({ ...form, display_name: e.target.value })} className={inputCls} />
        </L>
        <L label="邮箱">
          <input value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })} className={inputCls} />
        </L>
        <L label="角色">
          <select value={form.role} onChange={(e) => setForm({ ...form, role: e.target.value as "user" | "admin" })} className={inputCls}>
            <option value="user">普通用户</option>
            <option value="admin">管理员</option>
          </select>
        </L>
        {error && <p className="rounded-md bg-rose-50 px-3 py-2 text-[12.5px] text-rose-600">{error}</p>}
        <div className="flex justify-end gap-2 pt-1">
          <button onClick={onClose} className="rounded-lg border border-line px-3 py-1.5 text-[13px] text-slate-600 hover:bg-slate-50">
            取消
          </button>
          <button onClick={submit} disabled={busy || form.username.length < 2 || form.password.length < 6} className="flex items-center gap-1.5 rounded-lg bg-brand-500 px-3 py-1.5 text-[13px] font-medium text-white hover:bg-brand-600 disabled:opacity-50">
            {busy && <Loader2 size={14} className="animate-spin" />} 创建
          </button>
        </div>
      </div>
    </Modal>
  );
}

function ResetPasswordModal({ user, onClose }: { user: AdminUser | null; onClose: () => void }) {
  const [password, setPassword] = useState("");
  const [busy, setBusy] = useState(false);
  const [done, setDone] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    setPassword("");
    setDone(false);
    setError(null);
  }, [user]);

  const submit = async () => {
    if (!user) return;
    setBusy(true);
    setError(null);
    try {
      await api.admin.resetPassword(user.id, password);
      setDone(true);
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  };

  return (
    <Modal open={!!user} onClose={onClose} title={`重置密码 · ${user?.display_name ?? ""}`} width="max-w-md">
      <div className="space-y-3">
        <L label="新密码（≥ 6 位）">
          <input type="text" value={password} onChange={(e) => setPassword(e.target.value)} className={inputCls} autoFocus />
        </L>
        {error && <p className="rounded-md bg-rose-50 px-3 py-2 text-[12.5px] text-rose-600">{error}</p>}
        {done && <p className="rounded-md bg-emerald-50 px-3 py-2 text-[12.5px] text-emerald-700">已重置，请把新密码告知该用户。</p>}
        <div className="flex justify-end gap-2 pt-1">
          <button onClick={onClose} className="rounded-lg border border-line px-3 py-1.5 text-[13px] text-slate-600 hover:bg-slate-50">
            关闭
          </button>
          <button onClick={submit} disabled={busy || password.length < 6 || done} className="flex items-center gap-1.5 rounded-lg bg-brand-500 px-3 py-1.5 text-[13px] font-medium text-white hover:bg-brand-600 disabled:opacity-50">
            {busy && <Loader2 size={14} className="animate-spin" />} 重置
          </button>
        </div>
      </div>
    </Modal>
  );
}

function L({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <label className="block">
      <span className="mb-1 block text-[12.5px] font-medium text-slate-700">{label}</span>
      {children}
    </label>
  );
}
