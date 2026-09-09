import { create } from "zustand";
import { IOEDU_HOME_URL, api, getToken, isSsoSession, setToken } from "@/lib/api";
import type { User } from "@/lib/types";

interface AuthState {
  user: User | null;
  token: string | null;
  loading: boolean;
  error: string | null;
  bootstrap: () => Promise<void>;
  login: (username: string, password: string) => Promise<void>;
  register: (username: string, password: string, displayName?: string) => Promise<void>;
  logout: () => void;
}

export const useAuth = create<AuthState>((set) => ({
  user: null,
  token: getToken(),
  loading: !!getToken(),
  error: null,

  async bootstrap() {
    const token = getToken();
    if (!token) {
      set({ loading: false, user: null, token: null });
      return;
    }
    try {
      const user = await api.me();
      set({ user, token, loading: false });
    } catch {
      setToken(null);
      set({ user: null, token: null, loading: false });
    }
  },

  async login(username, password) {
    set({ error: null });
    try {
      const res = await api.login(username, password);
      setToken(res.access_token);
      set({ user: res.user, token: res.access_token });
    } catch (e) {
      set({ error: (e as Error).message });
      throw e;
    }
  },

  async register(username, password, displayName) {
    set({ error: null });
    try {
      const res = await api.register(username, password, displayName);
      setToken(res.access_token);
      set({ user: res.user, token: res.access_token });
    } catch (e) {
      set({ error: (e as Error).message });
      throw e;
    }
  },

  logout() {
    setToken(null);
    set({ user: null, token: null });
    // 教学平台登录态由平台管理:这里的"退出"只是回到平台首页,不清平台的 token
    if (isSsoSession()) location.href = IOEDU_HOME_URL;
  },
}));
