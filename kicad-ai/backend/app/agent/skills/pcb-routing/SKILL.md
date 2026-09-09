---
name: pcb-routing
description: PCB 走线、布线、打过孔与铺铜工作流：从板框设定、飞线网络分析、焊盘点对点布线、过孔换层、大面积铺地铜到 DRC 规则检查闭环。适用于「走线」「PCB布线」「连接焊盘」「铺铜」「打过孔」「DRC检查」。
---

# PCB 布线与覆铜设计指南

## 何时使用
当用户要求进行 PCB 走线、布线连接飞线、打接地过孔、绘制板框、铺设参考地铜皮或进行布线后 DRC 验证时使用。

## 标准执行流程

### 1. 读取现状与飞线网络
1. 调用 `get_board_info` 获取板层结构、尺寸和已有走线条数。
2. 调用 `list_nets` 查看所有网络名称与节点数量。
3. 调用 `get_ratsnest` 获取未布线的焊盘对列表（飞线）。
4. 确认板框：若板框缺失或需要重设尺寸，调用 `set_board_outline_rect(x1, y1, x2, y2)` 创建清晰的 Edge.Cuts 边界。

### 2. 点对点走线 (Pad to Pad Routing)
1. 优先走关键信号线和短飞线，调用 `pcb_route_pad_to_pad(from_ref, from_pad, to_ref, to_pad, layer="F.Cu", width=0.25)`。
2. 线宽选择：
   - 信号线（通用）：0.2 mm ~ 0.25 mm
   - 电源走线（大电流）：0.5 mm ~ 1.0 mm
3. 换层与过孔：若顶层受阻或连接到内层/底层，调用 `pcb_add_vias(position=[x, y], net_name="网络名", drill=0.3, size=0.6)`。
4. 走线修改：若发现走线重叠或需要优化布线路径，调用 `pcb_delete_tracks` / `pcb_delete_vias` 清除目标网络的走线，再重新调用 `pcb_route_pad_to_pad`。

### 3. 大面积铺地铜 (Copper Pour / Zone)
1. 信号走线完成后，调用 `add_zone(net_name="GND", layer="B.Cu", polygon_points=[[x1,y1], [x2,y2], [x3,y3], [x4,y4]])` 为底层铺设完整的 GND 地平面。
2. 铺铜后或修改走线后，调用 `refill_zones` 重新灌铜，确保避让间距正确。

### 4. DRC 闭环验证
1. 调用 `run_drc_check` 运行设计规则检查。
2. 核查结果中的 `error_count` 与 `unconnected_count`。
3. 若存在 clearance 违规，微调走线或移动封装；若有 unconnected 项，补全相应走线直至通过。

## 经验法则
- PCB 坐标单位为毫米，+Y 向下，必须对齐板级栅格（0.1 mm 或 0.05 mm）。
- 电源输入先经滤波电容再进入芯片引脚。
- 地回路优先通过大面积铺铜或就近打地孔连接到完整地平面。