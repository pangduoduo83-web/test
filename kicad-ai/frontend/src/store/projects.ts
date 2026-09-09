import { create } from "zustand";
import { api } from "@/lib/api";
import type { DesignConstraints, DesignSelection, Project } from "@/lib/types";

const ACTIVE_KEY = "kicad-ai.activeProject";

interface ProjectState {
  projects: Project[];
  activeProjectId: string | null;
  previewVersion: number;
  previewMode: "pcb" | "sch";
  selection: DesignSelection | null;
  loading: boolean;
  load: () => Promise<void>;
  setActive: (id: string | null) => void;
  setPreviewMode: (mode: "pcb" | "sch") => void;
  setSelection: (selection: DesignSelection | null) => void;
  bumpPreview: () => void;
  loadSample: (name: string) => Promise<Project>;
  importZip: (file: File, name?: string) => Promise<Project>;
  uploadFiles: (files: File[], name: string) => Promise<Project>;
  createBlank: (name: string, title?: string) => Promise<Project>;
  updateConstraints: (id: string, constraints: DesignConstraints) => Promise<DesignConstraints>;
  remove: (id: string) => Promise<void>;
}

export const useProjects = create<ProjectState>((set, get) => ({
  projects: [],
  activeProjectId: localStorage.getItem(ACTIVE_KEY),
  previewVersion: 0,
  previewMode: "pcb",
  selection: null,
  loading: false,

  async load() {
    set({ loading: true });
    try {
      const projects = await api.projects();
      let active = get().activeProjectId;
      if (!projects.some((p) => p.id === active)) active = projects[0]?.id ?? null;
      set({ projects, activeProjectId: active, loading: false });
      if (active) localStorage.setItem(ACTIVE_KEY, active);
    } catch {
      set({ loading: false });
    }
  },

  setActive(id) {
    set({ activeProjectId: id, previewVersion: get().previewVersion + 1, selection: null });
    if (id) localStorage.setItem(ACTIVE_KEY, id);
    else localStorage.removeItem(ACTIVE_KEY);
  },

  setPreviewMode(mode) {
    set({ previewMode: mode, selection: get().previewMode === mode ? get().selection : null });
  },

  setSelection(selection) {
    const active = get().activeProjectId;
    set({ selection: selection?.project_id === active ? selection : null });
  },

  bumpPreview() {
    set({ previewVersion: get().previewVersion + 1, selection: null });
  },

  async loadSample(name) {
    const p = await api.loadSample(name);
    set({ projects: [p, ...get().projects] });
    get().setActive(p.id);
    return p;
  },

  async importZip(file, name) {
    const p = await api.importZip(file, name);
    set({ projects: [p, ...get().projects] });
    get().setActive(p.id);
    return p;
  },

  async uploadFiles(files, name) {
    const p = await api.uploadFiles(files, name);
    set({ projects: [p, ...get().projects] });
    get().setActive(p.id);
    return p;
  },

  async createBlank(name, title) {
    const p = await api.createBlankProject(name, title);
    set({ projects: [p, ...get().projects] });
    get().setActive(p.id);
    return p;
  },

  async updateConstraints(id, constraints) {
    const saved = await api.updateConstraints(id, constraints);
    set({
      projects: get().projects.map((project) =>
        project.id === id ? { ...project, design_constraints: saved } : project,
      ),
    });
    return saved;
  },

  async remove(id) {
    await api.deleteProject(id);
    const projects = get().projects.filter((p) => p.id !== id);
    set({ projects });
    if (get().activeProjectId === id) get().setActive(projects[0]?.id ?? null);
  },
}));
