import { Modal } from "@/components/ui/Modal";
import type { SystemInfo } from "@/lib/types";

export function ResourcesModal({ open, onClose, info }: { open: boolean; onClose: () => void; info: SystemInfo | null }) {
  return (
    <Modal open={open} onClose={onClose} title="系统信息" width="max-w-xl">
      <div className="space-y-4">
        <section>
          <h3 className="mb-2 text-[13px] font-semibold text-ink">运行环境</h3>
          <dl className="grid grid-cols-2 gap-x-6 gap-y-2 rounded-xl border border-line bg-slate-50/60 px-4 py-3 text-[12px]">
            <Item k="模型" v={info?.model ?? "—"} />
            <Item k="上下文窗口" v={info ? `${info.context_tokens.toLocaleString()} tokens` : "—"} />
            <Item k="工具数量" v={info ? `${info.tool_count} 个` : "—"} />
            <Item k="kicad-cli" v={info ? (info.kicad_cli ? info.kicad_cli_version ?? "已安装" : "内置引擎") : "—"} />
            <Item k="子代理" v={info ? (info.subagents ? "已启用" : "已关闭") : "—"} />
            <Item k="在线用户" v={info ? String(info.online) : "—"} />
            <Item k="版本" v={info?.version ?? "—"} />
          </dl>
        </section>
      </div>
    </Modal>
  );
}

function Item({ k, v }: { k: string; v: string }) {
  return (
    <div className="flex items-baseline justify-between gap-3">
      <dt className="text-slate-500">{k}</dt>
      <dd className="truncate text-right font-medium text-ink" title={v}>
        {v}
      </dd>
    </div>
  );
}
