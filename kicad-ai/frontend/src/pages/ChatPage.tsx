import { X } from "lucide-react";
import { lazy, Suspense, useCallback, useEffect, useState } from "react";
import { ChatHeader } from "@/components/chat/ChatHeader";
import { Composer } from "@/components/chat/Composer";
import { MessageList } from "@/components/chat/MessageList";
import { Sidebar, type PanelKey } from "@/components/layout/Sidebar";
import { TopBar } from "@/components/layout/TopBar";
import { ProjectFilesModal } from "@/components/modals/ProjectFilesModal";
import { ProjectSwitcher } from "@/components/modals/ProjectSwitcher";
import { ResourcesModal } from "@/components/modals/ResourcesModal";
import { SettingsModal } from "@/components/modals/SettingsModal";
import { SkillsModal } from "@/components/modals/SkillsModal";
import { SnapshotsModal } from "@/components/modals/SnapshotsModal";
import { ToolsModal } from "@/components/modals/ToolsModal";
import { RightPanel } from "@/components/right/RightPanel";
import { api } from "@/lib/api";
import type { SystemInfo, ToolCategory } from "@/lib/types";
import { useChat } from "@/store/chat";
import { usePresence } from "@/store/presence";
import { useProjects } from "@/store/projects";

const DesignConstraintsModal = lazy(() =>
  import("@/components/modals/DesignConstraintsModal").then((module) => ({ default: module.DesignConstraintsModal })),
);
const DesignReviewModal = lazy(() =>
  import("@/components/modals/DesignReviewModal").then((module) => ({ default: module.DesignReviewModal })),
);
const DesignSelectionModal = lazy(() =>
  import("@/components/modals/DesignSelectionModal").then((module) => ({ default: module.DesignSelectionModal })),
);
const EcoReportModal = lazy(() =>
  import("@/components/modals/EcoReportModal").then((module) => ({ default: module.EcoReportModal })),
);
const PreviewViewerModal = lazy(() =>
  import("@/components/modals/PreviewViewerModal").then((module) => ({ default: module.PreviewViewerModal })),
);
const BomReportModal = lazy(() =>
  import("@/components/modals/BomReportModal").then((module) => ({ default: module.BomReportModal })),
);
const CircuitWizardModal = lazy(() =>
  import("@/components/modals/CircuitWizardModal").then((module) => ({ default: module.CircuitWizardModal })),
);

export function ChatPage() {
  const [panel, setPanel] = useState<PanelKey | null>(null);
  const [collapsed, setCollapsed] = useState(false);
  const [draft, setDraft] = useState("");
  const [selectionOpen, setSelectionOpen] = useState(false);
  const [constraintsOpen, setConstraintsOpen] = useState(false);
  const [reviewOpen, setReviewOpen] = useState(false);
  const [ecoOpen, setEcoOpen] = useState(false);
  const [bomOpen, setBomOpen] = useState(false);
  const [wizardOpen, setWizardOpen] = useState(false);
  const [previewMode, setPreviewMode] = useState<"pcb" | "sch" | null>(null);
  const [tools, setTools] = useState<ToolCategory[]>([]);
  const [info, setInfo] = useState<SystemInfo | null>(null);
  const { loadConversations, error, notice, dismissError, setContextTokens } = useChat();
  const loadProjects = useProjects((s) => s.load);
  const presence = usePresence();

  useEffect(() => {
    loadConversations().catch(() => undefined);
    loadProjects();
    api.tools().then(setTools).catch(() => undefined);
    api
      .systemInfo()
      .then((i) => {
        setInfo(i);
        setContextTokens(i.context_tokens);
      })
      .catch(() => undefined);
    presence.connect();
    return () => presence.disconnect();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const insert = useCallback((text: string) => setDraft((d) => (d ? `${d}\n${text}` : text)), []);
  const close = useCallback(() => setPanel(null), []);

  return (
    <div className="flex h-full flex-col">
      <TopBar onOpenHelp={() => setPanel("resources")} onOpenSettings={() => setPanel("settings")} />
      <div className="flex min-h-0 flex-1 gap-2.5 px-3 pb-3">
        <Sidebar collapsed={collapsed} onToggle={() => setCollapsed((v) => !v)} onOpenPanel={setPanel} />
        <main className="card flex min-w-0 flex-1 flex-col overflow-hidden">
          <ChatHeader online={presence.connected} onSkills={() => setPanel("skills")} onSnapshots={() => setPanel("snapshots")} onMore={() => setPanel("resources")} />
          {(error || notice) && (
            <div className={`flex items-center justify-between px-5 py-2 text-xs ${error ? "bg-rose-50 text-rose-700" : "bg-amber-50 text-amber-700"}`}>
              <span>{error ?? notice}</span>
              <button onClick={dismissError} className="rounded p-0.5 hover:bg-black/5">
                <X size={14} />
              </button>
            </div>
          )}
          <MessageList onSuggestion={(t) => setDraft(t)} />
          <Composer
            draft={draft}
            onDraftChange={setDraft}
            onOpenTools={() => setPanel("tools")}
            onAttach={() => setPanel("projects")}
            modelPresets={info?.model_presets}
            serverModel={info?.model}
            serverThinking={info?.thinking}
            onSelectDesign={() => setSelectionOpen(true)}
          />
        </main>
        <RightPanel
          tools={tools}
          onSwitchProject={() => setPanel("projects")}
          onAllTools={() => setPanel("tools")}
          onProjectFiles={() => setPanel("files")}
          onSelectDesign={() => setSelectionOpen(true)}
          onConstraints={() => setConstraintsOpen(true)}
          onReview={() => setReviewOpen(true)}
          onEco={() => setEcoOpen(true)}
          onBom={() => setBomOpen(true)}
          onWizard={() => setWizardOpen(true)}
          onEnlargePreview={(mode) => setPreviewMode(mode)}
        />
      </div>

      <ProjectSwitcher open={panel === "projects"} onClose={close} />
      <ProjectFilesModal open={panel === "files"} onClose={close} />
      <ToolsModal open={panel === "tools"} onClose={close} tools={tools} onInsert={insert} />
      <SkillsModal open={panel === "skills"} onClose={close} onUse={insert} />
      <SnapshotsModal open={panel === "snapshots"} onClose={close} />
      <ResourcesModal open={panel === "resources"} onClose={close} info={info} />
      <SettingsModal open={panel === "settings"} onClose={close} />
      <Suspense fallback={null}>
        {selectionOpen && <DesignSelectionModal open onClose={() => setSelectionOpen(false)} />}
        {constraintsOpen && <DesignConstraintsModal open onClose={() => setConstraintsOpen(false)} />}
        {reviewOpen && <DesignReviewModal open onClose={() => setReviewOpen(false)} />}
        {ecoOpen && <EcoReportModal open onClose={() => setEcoOpen(false)} />}
        {bomOpen && <BomReportModal open onClose={() => setBomOpen(false)} />}
        {wizardOpen && <CircuitWizardModal open onClose={() => setWizardOpen(false)} />}
        {previewMode && <PreviewViewerModal open initialMode={previewMode} onClose={() => setPreviewMode(null)} />}
      </Suspense>
    </div>
  );
}
