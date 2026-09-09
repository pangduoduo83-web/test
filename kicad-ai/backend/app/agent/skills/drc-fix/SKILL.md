---
name: drc-fix
description: DRC 错误诊断与修复流程：运行 DRC、按类型归类违规（间距、占位重叠、板边、未连接、丝印）、给出修复动作并复查。适用于「检查 DRC」「修复 DRC 错误」「为什么 DRC 报错」。
---

# DRC 诊断与修复

## 流程
1. 优先调用 `run_design_review`，同时取得 ERC、DRC、布局分项评分、`issues[]` 与 `suggested_actions[]`；仅检查 PCB 时可单独调用 `run_drc_check`。
2. 按 `type` 归类并排序：先 error 再 warning；先影响制造的（clearance、courtyards_overlap、copper_edge_clearance、track_width、via_size）再外观类（silk_overlap、text）。
3. 对每条违规读取 `items[].pos` 与描述，定位涉及的封装（`get_footprint`）。
4. 修复策略：
   - **courtyards_overlap** → 用 `get_footprint_bbox` 计算最小平移量，`set_footprint_position` 或 `move_footprints_by_delta` 拉开 ≥ 0.25 mm。
   - **clearance**（焊盘/走线间距）→ 移动较小的器件；若是走线问题且无布线工具，说明需在 KiCad 中手动改线。
   - **copper_edge_clearance / 超出板框** → 向板内平移，保持 ≥ 0.5 mm 边距；或与用户确认是否扩大板框（`set_board_outline_rect`）。
   - **unconnected_items** → 说明这是尚未布线的飞线，属于布线阶段任务；如用户只关心布局，标记为「待布线」。
   - **missing_outline** → 提示用户定义板框，可用 `set_board_outline_rect`。
5. 自动处理前，将仍适用的 `suggested_actions` 整理为 `submit_change_plan`，等待用户整批批准；批准后系统会自动执行全部动作并返回结果，不要再自行调用修改工具。
6. 修复后重新 `run_design_review`，对比前后 ERC/DRC 数量与布局评分。
7. 汇报格式：
   - ✅/❌ DRC 结果（错误 N，警告 M，未连接 K）
   - 已修复：类型 → 动作 → 涉及对象
   - 未修复 / 需人工：原因与建议

## 注意
- `builtin-lite` 引擎只覆盖间距、占位重叠、板边、未连接四类，不等价于 KiCad 完整 DRC；汇报时要说明。
- 绝不为了「通过 DRC」而删除元件或走线，除非用户明确要求（删除类工具需要用户确认）。
