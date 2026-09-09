import { create } from "zustand";
import { API_BASE, getToken } from "@/lib/api";
import type { PresenceUser } from "@/lib/types";

interface PresenceState {
  users: PresenceUser[];
  count: number;
  connected: boolean;
  connect: () => void;
  disconnect: () => void;
}

let socket: WebSocket | null = null;
let retryTimer: number | null = null;
let retryDelay = 1000;

export const usePresence = create<PresenceState>((set, get) => ({
  users: [],
  count: 0,
  connected: false,

  connect() {
    const token = getToken();
    if (!token || socket) return;
    const proto = location.protocol === "https:" ? "wss" : "ws";
    const ws = new WebSocket(`${proto}://${location.host}${API_BASE}/ws/presence?token=${encodeURIComponent(token)}`);
    socket = ws;
    ws.onopen = () => {
      retryDelay = 1000;
      set({ connected: true });
    };
    ws.onmessage = (ev) => {
      try {
        const msg = JSON.parse(ev.data);
        if (msg.type === "presence") set({ users: msg.users, count: msg.count });
        if (msg.type === "ping") ws.send(JSON.stringify({ type: "pong" }));
      } catch {
        /* ignore */
      }
    };
    ws.onclose = () => {
      socket = null;
      set({ connected: false });
      if (getToken()) {
        retryTimer = window.setTimeout(() => get().connect(), retryDelay);
        retryDelay = Math.min(retryDelay * 2, 15000);
      }
    };
    ws.onerror = () => ws.close();
  },

  disconnect() {
    if (retryTimer) window.clearTimeout(retryTimer);
    retryTimer = null;
    socket?.close();
    socket = null;
    set({ connected: false, users: [], count: 0 });
  },
}));

export function sendPresenceStatus(status: "idle" | "busy") {
  if (socket && socket.readyState === WebSocket.OPEN) {
    socket.send(JSON.stringify({ type: "status", status }));
  }
}
