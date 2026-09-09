import { Sparkles } from "lucide-react";
import { useEffect, useState } from "react";
import { Markdown } from "@/components/ui/Markdown";
import { Modal } from "@/components/ui/Modal";
import { api } from "@/lib/api";
import type { Skill } from "@/lib/types";

export function SkillsModal({ open, onClose, onUse }: { open: boolean; onClose: () => void; onUse: (text: string) => void }) {
  const [skills, setSkills] = useState<Skill[]>([]);
  const [selected, setSelected] = useState<Skill | null>(null);

  useEffect(() => {
    if (open)
      api
        .skills()
        .then((s) => {
          setSkills(s);
          setSelected((cur) => cur ?? s[0] ?? null);
        })
        .catch(() => setSkills([]));
  }, [open]);

  return (
    <Modal open={open} onClose={onClose} title="技能 (Skills)" subtitle="技能是按需加载的工作流指南（SKILL.md）。代理会在任务匹配时自动读取；你也可以显式要求它使用某个技能。" width="max-w-4xl">
      <div className="grid grid-cols-1 gap-4 md:grid-cols-[240px_1fr]">
        <ul className="space-y-1.5">
          {skills.map((s) => (
            <li key={s.name}>
              <button onClick={() => setSelected(s)} className={`w-full rounded-lg border px-3 py-2.5 text-left ${selected?.name === s.name ? "border-brand-300 bg-brand-50" : "border-line hover:bg-slate-50"}`}>
                <div className="flex items-center gap-1.5 text-[13px] font-medium text-ink">
                  <Sparkles size={13} className="text-brand-500" /> {s.name}
                </div>
                <p className="mt-0.5 line-clamp-3 text-[11px] text-slate-500">{s.description}</p>
              </button>
            </li>
          ))}
          {skills.length === 0 && <li className="text-xs text-slate-400">暂无技能</li>}
        </ul>
        <div className="min-h-0 rounded-xl border border-line bg-slate-50/50 p-4">
          {selected ? (
            <>
              <div className="mb-3 flex items-center justify-between gap-3">
                <code className="text-[11px] text-slate-400">{selected.path}</code>
                <button
                  onClick={() => {
                    onUse(`请使用「${selected.name}」技能：`);
                    onClose();
                  }}
                  className="rounded-md bg-brand-500 px-3 py-1.5 text-xs font-medium text-white hover:bg-brand-600"
                >
                  在对话中使用
                </button>
              </div>
              <Markdown content={(selected.content ?? "").replace(/^---[\s\S]*?---\s*/, "")} />
            </>
          ) : (
            <p className="text-xs text-slate-400">选择左侧的技能查看详情</p>
          )}
        </div>
      </div>
    </Modal>
  );
}
