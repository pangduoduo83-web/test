export function timeHM(iso?: string | null): string {
  if (!iso) return "";
  const d = new Date(iso);
  return `${String(d.getHours()).padStart(2, "0")}:${String(d.getMinutes()).padStart(2, "0")}`;
}

export function relativeDay(iso: string): string {
  const d = new Date(iso);
  const now = new Date();
  const startToday = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime();
  const diffDays = Math.floor((startToday - new Date(d.getFullYear(), d.getMonth(), d.getDate()).getTime()) / 86400000);
  if (diffDays <= 0) return timeHM(iso);
  if (diffDays === 1) return "昨天";
  if (diffDays < 7) return `${diffDays} 天前`;
  return `${d.getMonth() + 1}/${d.getDate()}`;
}

export function durationHMS(ms: number): string {
  const s = Math.max(0, Math.floor(ms / 1000));
  const h = Math.floor(s / 3600);
  const m = Math.floor((s % 3600) / 60);
  const sec = s % 60;
  return [h, m, sec].map((v) => String(v).padStart(2, "0")).join(":");
}

export function initials(name: string): string {
  const t = name.trim();
  if (!t) return "?";
  if (/[\u4e00-\u9fa5]/.test(t)) return t.slice(-2);
  const parts = t.split(/\s+/);
  return (parts[0][0] + (parts[1]?.[0] ?? "")).toUpperCase();
}

export function humanBytes(n: number): string {
  if (n < 1024) return `${n} B`;
  if (n < 1024 * 1024) return `${(n / 1024).toFixed(1)} KB`;
  return `${(n / 1024 / 1024).toFixed(1)} MB`;
}

export const TOOL_LABELS: Record<string, string> = {
  submit_change_plan: "提交整批修改计划",
  get_board_info: "获取 PCB 板信息",
  list_footprints: "列出封装位置",
  get_footprint: "获取封装详情",
  get_footprint_bbox: "获取封装边界框",
  get_board_bounding_box: "获取板级边界框",
  list_nets: "列出网络",
  get_ratsnest: "获取飞线连接",
  score_placement: "评估布局质量",
  get_board_outline: "读取板框",
  find_free_pcb_area: "寻找空闲区域",
  get_effective_design_rules: "读取设计规则",
  suggest_placement_order: "推荐放置顺序",
  set_footprint_position: "移动元件",
  move_footprints_by_delta: "批量平移元件",
  align_footprints: "对齐元件",
  distribute_footprints: "等距分布元件",
  flip_footprint: "翻转元件到另一层",
  set_footprint_property: "设置封装属性",
  set_board_outline_rect: "设置矩形板框",
  clear_board_outline: "清除板框",
  run_drc_check: "运行 DRC 检查",
  run_erc_check: "运行 ERC 检查",
  run_design_review: "运行设计质量审查",
  run_eco_check: "检查原理图与 PCB 一致性",
  list_circuit_templates: "列出电路模板",
  preview_circuit_template: "预览电路模板",
  apply_circuit_template: "放置电路模块",
  analyze_bom: "分析物料清单",
  get_schematic_sheet_info: "读取图纸信息",
  list_schematic_symbols: "列出原理图符号",
  get_schematic_symbol: "获取符号详情",
  extract_schematic_netlist: "提取网表",
  find_component_connections: "查找元件连接",
  check_reference_conflicts: "检查参考号冲突",
  list_labels_in_schematic: "列出网络标签",
  move_component: "移动原理图符号",
  set_symbol_property: "设置符号属性",
  add_wire_to_schematic: "添加导线",
  add_label_to_schematic: "添加网络标签",
  save_file_version: "保存版本快照",
  list_file_versions: "列出版本快照",
  restore_file_version: "恢复版本快照",
  create_project: "新建空白工程",
  list_projects: "列出工程",
  get_project_structure: "读取工程结构",
  write_todos: "更新任务清单",
  task: "委派子代理",
  read_file: "读取文件",
  write_file: "写入文件",
  edit_file: "编辑文件",
  ls: "列出目录",
  glob: "匹配文件",
  grep: "搜索文件内容",
  // ---- upstream KiCad-AI-Assistant tools -------------------------------------
  open_project: "在 KiCad 中打开工程",
  sync_symbol_index: "重建符号库索引",
  get_symbol_sync_status: "符号索引进度",
  get_symbol_index_stats: "符号索引统计",
  list_symbol_libraries: "列出符号库",
  search_symbols: "搜索符号",
  get_symbol: "获取符号定义",
  get_library_symbols: "列出库内符号",
  get_symbol_pins: "获取符号引脚",
  add_symbol_to_schematic: "放置符号",
  place_symbol_relative: "相对放置符号",
  remove_symbol_from_schematic: "删除符号",
  rename_symbol: "重命名参考号",
  list_symbol_properties: "列出符号属性",
  delete_symbol_property: "删除符号属性",
  connect_points_with_wire: "两点智能布线",
  connect_pins_with_wire: "引脚间连线",
  delete_wire_from_schematic: "删除导线",
  delete_label_from_schematic: "删除网络标签",
  find_free_area: "寻找原理图空闲区域",
  extract_project_netlist: "提取工程网表",
  identify_circuit_patterns: "识别电路模式",
  analyze_project_circuit_patterns: "分析工程电路模式",
  validate_project: "校验工程",
  validate_project_boundaries: "校验元件边界",
  generate_validation_report: "生成校验报告",
  list_sheet_symbols: "列出层次图纸",
  get_sheet_hierarchy: "获取图纸层次树",
  add_sheet_symbol: "添加层次图纸",
  remove_sheet_symbol: "删除层次图纸",
  update_sheet_symbol: "更新层次图纸",
  add_sheet_pin: "添加图纸引脚",
  remove_sheet_pin: "删除图纸引脚",
  assign_symbols_to_group: "符号分组",
  list_symbol_groups: "列出符号分组",
  get_symbol_group: "获取符号分组",
  score_symbol_group: "评估符号分组",
  place_symbol_group: "放置符号分组",
  move_symbol_group: "移动符号分组",
  rotate_symbol_group: "旋转符号分组",
  sync_footprint_index: "重建封装库索引",
  get_footprint_sync_status: "封装索引进度",
  list_footprint_libraries: "列出封装库",
  search_footprints: "搜索封装",
  get_footprint_details: "获取封装库详情",
  get_board_outline_items: "读取板框图元",
  add_board_outline_segment: "添加板框线段",
  add_board_outline_arc: "添加板框圆弧",
  update_pcb_from_schematic: "从原理图更新 PCB",
  assign_footprints_to_group: "封装分组",
  list_footprint_groups: "列出封装分组",
  get_footprint_group: "获取封装分组",
  score_footprint_group: "评估封装分组",
  place_footprint_group: "放置封装分组",
  move_footprint_group: "移动封装分组",
  rotate_footprint_group: "旋转封装分组",
  list_zones: "列出铜皮区域",
  add_zone: "添加铜皮区域",
  delete_zone: "删除铜皮区域",
  refill_zones: "重新填充铜皮",
  list_tracks: "列出走线",
  list_vias: "列出过孔",
  pcb_route_pad_to_pad: "焊盘间布线",
  pcb_add_vias: "添加过孔",
  pcb_delete_tracks: "删除走线",
  pcb_delete_vias: "删除过孔",
  set_design_rules: "设置板级规则",
  set_net_class_rules: "设置网络类规则",
  assign_nets_to_class: "网络加入网络类",
  remove_nets_from_class: "网络移出网络类",
  delete_net_class: "删除网络类",
  add_custom_rule: "添加自定义 DRC 规则",
  del_custom_rule: "删除自定义 DRC 规则",
  export_bom_csv: "导出 BOM CSV",
  generate_pcb_thumbnail: "生成 PCB 缩略图",
  generate_project_thumbnail: "生成工程缩略图",
  list_skills: "列出插件技能",
  get_skill: "读取插件技能",
  add_skill: "新建插件技能",
  append_to_skill: "追加插件技能",
  delete_skill: "删除插件技能",
  check_kicad_ipc_connection: "检查 KiCad 连接",
  save_document: "在 KiCad 中保存",
  reload_kicad: "刷新 KiCad 视图",
};

export const SOURCE_LABELS: Record<string, string> = {
  native: "内置",
  kcaa: "开源插件",
  mcp: "MCP",
  harness: "框架",
};

export function toolLabel(name: string): string {
  return TOOL_LABELS[name] ?? name.replace(/_/g, " ");
}

/** Short human summary of tool args for the tool-call card. */
export function summarizeArgs(name: string, args?: Record<string, unknown>): string {
  if (!args) return "";
  const a = args as Record<string, unknown>;
  const refs = (a.references as string[] | undefined)?.join(", ");
  if (name === "set_footprint_position" || name === "move_component") {
    const parts = [a.reference, a.x !== undefined ? `x=${a.x}` : "", a.y !== undefined ? `y=${a.y}` : "", a.rotation !== undefined ? `∠${a.rotation}` : ""];
    return `移动 ${parts.filter(Boolean).join(" ")}`;
  }
  if (refs) return `${refs}`;
  if (a.reference) return String(a.reference);
  if (a.subagent_type) return `${a.subagent_type}: ${String(a.description ?? "").slice(0, 60)}`;
  if (a.file_path) return String(a.file_path).split(/[\\/]/).pop() ?? "";
  const keys = Object.keys(a).filter((k) => !k.endsWith("_path"));
  if (!keys.length) return "";
  return keys
    .slice(0, 3)
    .map((k) => `${k}=${JSON.stringify(a[k])}`)
    .join(" ")
    .slice(0, 80);
}
