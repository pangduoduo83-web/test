---
name: pcb-power-layout
description: 电源模块 / 去耦电容布局优化流程：把去耦电容移到芯片电源引脚附近、缩短大电流回路、保持占位不重叠，并用 DRC 验证。适用于「优化电源布局」「电容靠近芯片」「整理 LDO/DC-DC 周边」。
---

# 电源模块布局优化

## 何时使用
用户要求优化电源部分的布局、让去耦电容靠近芯片电源引脚、缩短回路面积，或在布局后检查 DRC。

## 标准流程
1. **读取现状**：`get_board_info` → `list_footprints` → 对目标芯片 `get_footprint(reference)` 取得每个焊盘的绝对坐标与网络。
2. **识别电源引脚**：焊盘网络名包含 `VCC` / `VDD` / `3V3` / `5V` / `VIN` / `VOUT` / `+` 的引脚即电源引脚；`GND` 为参考地。
3. **匹配去耦电容**：`list_footprints(reference_prefix="C")`，通过 `get_footprint` 查看电容两端网络，一端为电源、一端为 GND 的即去耦电容。
4. **计算目标位置**：
   - 目标：电容中心到电源焊盘中心距离 **< 2 mm**（0402/0603），大容量 (≥10 µF) 可放 3~5 mm。
   - 电容长轴与芯片边缘平行，GND 端朝向最近的地过孔/地平面方向。
   - 坐标对齐到 **0.1 mm** 栅格。
5. **校验空间**：移动前 `get_footprint_bbox` / `find_free_pcb_area(width, height)`；确认新位置不与其它封装的 bbox 重叠且位于板框内（`get_board_outline`）。
6. **执行移动**：`set_footprint_position(reference, x, y, rotation)`；多个电容可用 `align_footprints` 保持整齐。
7. **验证**：`score_placement` → `run_drc_check`；若出现间距/重叠违规，微调后复查。
8. **汇报**：列出「参考号：旧坐标 → 新坐标（距离 x.x mm）」、评分变化、DRC 结果、遗留建议。

## 经验法则
- 先放大电流路径上的器件（电感、输入/输出电容），再放小去耦电容。
- 不要把电容放在芯片正下方（除非双面布局且用户同意翻到底层）。
- 每次只移动一个封装并校验，避免连锁重叠。
- 用户未指定时，不要改变芯片本身的位置。
