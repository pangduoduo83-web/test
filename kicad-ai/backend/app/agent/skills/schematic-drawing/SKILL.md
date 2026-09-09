---
name: schematic-drawing
description: 原理图绘制与电路设计流程：从符号检索、安全区域规划、放置符号、指定封装与值、引脚间连线、添加网络标签到网表验证与同步至 PCB。适用于「画原理图」「添加电路」「绘制LDO电源」「连接引脚」「画导线」。
---

# 原理图绘制与电路设计指南

## 何时使用
当用户要求新建电路、在现有原理图上添加模块（如电源模块、按键电路、传感器接口、LED 指示等）、绘制导线或连接元件引脚时使用此工作流。

## 标准执行流程

### 1. 勘测图纸与规划区域
1. 调用 `get_schematic_sheet_info` 获取图纸尺寸（如 A4: 297x210 mm）及扣除标题栏后的推荐安全绘图区域。
2. 调用 `list_schematic_symbols` 掌握现有元件的位置与位号（如已有 R1~R5, C1~C4，则新阻容从 R6, C5 开始命名）。
3. 调用 `find_free_area(width, height)` 在图纸上寻找无重叠的安全矩形空间作为新模块的基准坐标 `(base_x, base_y)`。

### 2. 检索符号与放置元件
1. 若不确定符号库名，调用 `search_symbols(query="...")` 查询标准库（如 `Device:R`, `Device:C`, `Regulator_Linear:AMS1117-3.3`, `LED:LED`）。
2. 调用 `get_symbol_pins(symbol_name, lib_id)` 获知引脚编号、名称与极性。
3. 调用 `add_symbol_to_schematic(lib_id, reference, value, position=[x, y], rotation=0)` 放置主芯片/核心器件。
4. 调用 `place_symbol_relative(reference, relative_to_ref, direction="right"|"left"|"above"|"below", spacing=15.0)` 相对主芯片整齐排布输入电容、输出电容或外围阻容。
5. 调用 `set_symbol_property(reference, "Footprint", footprint_name)` 为所有元件明确指定真实 PCB 封装（如 `Resistor_SMD:R_0805_2012Metric`, `Capacitor_SMD:C_0805_2012Metric`, `Package_TO_SOT_SMD:SOT-223-3_TabPin2`）。

### 3. 引脚连接与网络规划
1. **就近引脚连接**：
   - 优先使用 `connect_pins_with_wire(start_symbol, start_pin, end_symbol, end_pin)`；
   - 例如：LDO 的 OUT 引脚直连滤波电容的正极：`connect_pins_with_wire("U1", "2", "C2", "1")`。
2. **电源、地与长距离信号**：
   - 避免跨图拉过长的杂乱实线；
   - 在关键节点引脚末端调用 `add_label_to_schematic(text="GND", x=..., y=...)` 或 `add_label_to_schematic(text="VCC_3V3", x=..., y=...)`。
   - 所有标有相同文本的网络标签在 KiCad 中会自动连通。

### 4. 网表校验与同步
1. 调用 `check_reference_conflicts` 确保原理图中无重复参考号。
2. 调用 `extract_schematic_netlist` 检查网络完整性，确认无意外遗漏的 `unconnected_pins`。
3. 原理图与工程文件自动保存在工作区，并与当前会话绑定。告知用户设计已完成，可在前端右侧面板实时查看原理图 SVG 预览，或一键导出/下载工程包并在本地 KiCad 中按 F8（从原理图更新 PCB）一键同步封装到 PCB。

## 经验法则
- 原理图坐标系 +Y 向下，必须严格对齐 **1.27 mm (50 mil)** 栅格（KiCad 导线只有在引脚端点精确重合时才能形成电气连接）。
- 旋转只允许 0, 90, 180, 270 度逆时针。
- 电阻、电容、二极管等被动器件必须带有明确的 `Value`（如 `10k`, `10uF`, `0.1uF`）和 `Footprint`。