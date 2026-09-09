import { create } from "zustand";
import { api, withBase } from "@/lib/api";
import type { PublicInfo } from "@/lib/types";

interface BrandingState {
  info: PublicInfo | null;
  appName: string;
  tagline: string;
  logoUrl: string | null;
  footer: string;
  copyright: string;
  load: () => Promise<void>;
  set: (patch: Partial<Pick<BrandingState, "appName" | "tagline" | "logoUrl" | "footer" | "copyright">>) => void;
}

export const useBranding = create<BrandingState>((set) => ({
  info: null,
  appName: "硬件设计助手",
  tagline: "AI 帮你画原理图、布 PCB、跑 DRC、审 BOM",
  logoUrl: null,
  footer: "🔒 用户工作区隔离，越界路径拒绝  📷 修改前自动快照  🌐 支持主流大模型  ⚡ 智能 EDA 闭环",
  copyright: "",
  async load() {
    try {
      const info = await api.publicInfo();
      set({
        info,
        appName: info.app_name,
        tagline: info.app_tagline,
        logoUrl: info.logo_url ? withBase(info.logo_url) : null,
        footer: info.app_footer ?? "",
        copyright: info.app_copyright ?? "",
      });
      document.title = info.app_name;
    } catch {
      /* keep defaults */
    }
  },
  set(patch) {
    set({ ...patch, ...(patch.logoUrl ? { logoUrl: withBase(patch.logoUrl) } : {}) });
    if (patch.appName) document.title = patch.appName;
  },
}));
