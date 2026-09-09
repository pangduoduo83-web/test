---
name: schematic-review
description: 原理图审查流程：检查参考号冲突、未连接引脚、缺失封装/值、电源标签一致性，并整理符号位置。适用于「检查原理图」「审查电路」「补全封装」。
---

# 原理图审查

## 流程
1. `get_schematic_sheet_info` 确认纸张、栅格与推荐绘图区域。
2. `list_schematic_symbols` → 检查：
   - 参考号以 `?` 结尾或重复（`check_reference_conflicts`）。
   - `value` 为空或为库默认值（如 `R`、`C`、`~`）。
   - `footprint` 为空 → 记录为「缺失封装」，按值给出常用封装建议（0603 电阻电容、SOT-23、SOIC 等），需用户确认后用 `set_symbol_property(reference, "Footprint", ...)` 补全。
3. `extract_schematic_netlist` → 查看 `unconnected_pins`；电源/地标签不一致（`GND` vs `GNDREF`、`+3V3` vs `3V3`）用 `list_labels_in_schematic` 核对。
4. 关键元件用 `find_component_connections` 逐引脚核对（如 LDO 的 IN/OUT/GND、MCU 的电源与复位）。
5. 若需整理位置：坐标吸附 1.27 mm，旋转 0/90/180/270，用 `move_component`；不要改变连接关系。
6. 汇报：问题清单（严重 → 一般）、已修复项、建议项（需用户确认）。

## 注意
- 内置网表是近似算法（导线端点 + 引脚坐标 + 标签），若与用户认知不符，请提示在 KiCad 中运行 ERC 复核。
- 添加导线/标签前必须先拿到精确的引脚坐标（`get_schematic_symbol(include pins)`）。
