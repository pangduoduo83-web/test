"""Parametrised circuit templates built from the standard KiCad libraries.

A template expands into concrete parts (library symbol + footprint + grid
position), wires between pins and power connections. The expansion is pure and
deterministic so it can be shown to the user for approval before anything is
written; ``apply_template`` performs the placement through the upstream kcaa
schematic tools.
"""

from __future__ import annotations

import math
from typing import Any

GRID = 1.27
E24 = [1.0, 1.1, 1.2, 1.3, 1.5, 1.6, 1.8, 2.0, 2.2, 2.4, 2.7, 3.0, 3.3, 3.6, 3.9, 4.3, 4.7, 5.1, 5.6, 6.2, 6.8, 7.5, 8.2, 9.1]

R_0603 = "Resistor_SMD:R_0603_1608Metric"
C_0603 = "Capacitor_SMD:C_0603_1608Metric"
C_0805 = "Capacitor_SMD:C_0805_2012Metric"
LED_0603 = "LED_SMD:LED_0603_1608Metric"


def snap(value: float) -> float:
    return round(round(value / GRID) * GRID, 2)


def nearest_e24(value: float, prefer: str = "nearest") -> float:
    """E24 standard resistor value (ohms).

    ``prefer="up"`` picks the next value at or above *value* — used for LED
    current limiting so the real current never exceeds the requested one.
    """
    if value <= 0:
        return E24[0]
    exponent = math.floor(math.log10(value))
    candidates = [m * 10**exponent for m in E24] + [E24[0] * 10 ** (exponent + 1)]
    if prefer == "up":
        return min(c for c in candidates if c >= value - 1e-9)
    return min(candidates, key=lambda c: abs(c - value))


def format_ohms(value: float) -> str:
    if value >= 1_000_000:
        return f"{value / 1_000_000:g}M"
    if value >= 1_000:
        return f"{value / 1_000:g}k"
    return f"{value:g}"


def _part(pid: str, library: str, symbol: str, value: str, footprint: str, dx: float, dy: float, rotation: int = 0) -> dict[str, Any]:
    return {"id": pid, "library": library, "symbol": symbol, "value": value, "footprint": footprint, "dx": dx, "dy": dy, "rotation": rotation}


def _power(pid: str, symbol: str, dx: float, dy: float) -> dict[str, Any]:
    return {"id": pid, "library": "power", "symbol": symbol, "value": symbol, "footprint": "", "dx": dx, "dy": dy, "rotation": 0}


# ---------------------------------------------------------------------------
# Templates
# ---------------------------------------------------------------------------
def _ldo(params: dict[str, Any]) -> dict[str, Any]:
    vin = str(params.get("input_net") or "+5V")
    vout = str(params.get("output_net") or "+3V3")
    return {
        "parts": [
            _part("U", "Regulator_Linear", "AMS1117-3.3", "AMS1117-3.3", "Package_TO_SOT_SMD:SOT-223-3_TabPin2", 0, 0),
            _part("CIN", "Device", "C", "10uF", C_0805, -12.7, 7.62, 0),
            _part("COUT", "Device", "C", "22uF", C_0805, 12.7, 7.62, 0),
            _power("PIN", vin, -12.7, -7.62),
            _power("POUT", vout, 12.7, -7.62),
            _power("GND", "GND", 0, 15.24),
        ],
        # AMS1117: pin 1 GND, pin 2 VOUT, pin 3 VIN
        "wires": [("PIN.1", "U.3"), ("CIN.1", "U.3"), ("U.2", "POUT.1"), ("COUT.1", "U.2"), ("U.1", "GND.1"), ("CIN.2", "GND.1"), ("COUT.2", "GND.1")],
        "notes": [f"AMS1117-3.3 需要 {vin} 高于 4.75V；输入输出电容按数据手册 10uF/22uF。"],
    }


def _led(params: dict[str, Any]) -> dict[str, Any]:
    supply = float(params.get("supply_voltage") or 3.3)
    vf = float(params.get("led_forward_voltage") or 2.0)
    current_ma = float(params.get("led_current_ma") or 2.0)
    net = str(params.get("supply_net") or "+3V3")
    if supply <= vf:
        raise ValueError("电源电压必须高于 LED 正向压降")
    exact = (supply - vf) / (current_ma / 1000.0)
    resistor = nearest_e24(exact, prefer="up")
    actual_ma = (supply - vf) / resistor * 1000.0
    return {
        "parts": [
            _power("VCC", net, 0, -7.62),
            _part("R", "Device", "R", format_ohms(resistor), R_0603, 0, 0, 90),
            _part("D", "Device", "LED", "LED", LED_0603, 0, 10.16, 90),
            _power("GND", "GND", 0, 20.32),
        ],
        # Device:R pins 1/2; Device:LED pin 1 = K (cathode), pin 2 = A (anode)
        "wires": [("VCC.1", "R.1"), ("R.2", "D.2"), ("D.1", "GND.1")],
        "notes": [
            f"限流电阻计算 ({supply}V - {vf}V) / {current_ma}mA = {exact:.0f}Ω，取 E24 值 {format_ohms(resistor)}Ω，实际约 {actual_ma:.2f}mA。",
        ],
    }


def _divider(params: dict[str, Any]) -> dict[str, Any]:
    vin = float(params.get("input_voltage") or 12.0)
    vout = float(params.get("output_voltage") or 3.3)
    r2 = float(params.get("bottom_resistor_ohms") or 10_000)
    if not 0 < vout < vin:
        raise ValueError("输出电压必须介于 0 和输入电压之间")
    r1 = nearest_e24(r2 * (vin - vout) / vout)
    actual = vin * r2 / (r1 + r2)
    return {
        "parts": [
            _part("R1", "Device", "R", format_ohms(r1), R_0603, 0, 0, 90),
            _part("R2", "Device", "R", format_ohms(r2), R_0603, 0, 10.16, 90),
            _power("GND", "GND", 0, 20.32),
        ],
        "wires": [("R1.2", "R2.1"), ("R2.2", "GND.1")],
        "labels": [("R1.1", str(params.get("input_net") or "VIN")), ("R1.2", str(params.get("output_net") or "VSENSE"))],
        "notes": [f"R1={format_ohms(r1)}Ω, R2={format_ohms(r2)}Ω → 实际输出 {actual:.3f}V（目标 {vout}V）。"],
    }


def _decoupling(params: dict[str, Any]) -> dict[str, Any]:
    count = int(params.get("count") or 2)
    if not 1 <= count <= 8:
        raise ValueError("去耦电容数量需在 1~8 之间")
    net = str(params.get("power_net") or "+3V3")
    value = str(params.get("value") or "100nF")
    parts: list[dict[str, Any]] = []
    wires: list[tuple[str, str]] = []
    for index in range(count):
        cid = f"C{index + 1}"
        parts.append(_part(cid, "Device", "C", value, C_0603, index * 7.62, 0, 0))
        parts.append(_power(f"V{index + 1}", net, index * 7.62, -7.62))
        parts.append(_power(f"G{index + 1}", "GND", index * 7.62, 10.16))
        wires += [(f"V{index + 1}.1", f"{cid}.1"), (f"{cid}.2", f"G{index + 1}.1")]
    return {"parts": parts, "wires": wires, "notes": [f"{count} 个 {value} 去耦电容，PCB 上应紧贴 IC 电源引脚放置。"]}


def _usb_c_power(params: dict[str, Any]) -> dict[str, Any]:
    net = str(params.get("vbus_net") or "VBUS")
    return {
        "parts": [
            _part("J", "Connector", "USB_C_Receptacle_USB2.0_16P", "USB_C", "Connector_USB:USB_C_Receptacle_HRO_TYPE-C-31-M-12", 0, 0, 0),
            _part("RCC1", "Device", "R", "5.1k", R_0603, 22.86, -2.54, 0),
            _part("RCC2", "Device", "R", "5.1k", R_0603, 22.86, 2.54, 0),
            _power("VBUS", net, -15.24, -20.32),
            _power("GND", "GND", -15.24, 25.4),
        ],
        # KiCad 10 Connector:USB_C_Receptacle_USB2.0_16P — GND A1/A12/B1/B12, VBUS A4/A9/B4/B9, CC1 A5, CC2 B5, SHIELD SH
        "wires": [
            ("J.A4", "VBUS.1"), ("J.A9", "VBUS.1"), ("J.B4", "VBUS.1"), ("J.B9", "VBUS.1"),
            ("J.A1", "GND.1"), ("J.A12", "GND.1"), ("J.B1", "GND.1"), ("J.B12", "GND.1"), ("J.SH", "GND.1"),
            ("J.A5", "RCC1.1"), ("J.B5", "RCC2.1"), ("RCC1.2", "GND.1"), ("RCC2.2", "GND.1"),
        ],
        "notes": ["CC1/CC2 各接 5.1kΩ 下拉，声明为 UFP（受电设备），可获 5V 供电。", "D+/D- 与 SBU 引脚未连接；仅取电时按 USB 规范可悬空。"],
    }


def _rs485(params: dict[str, Any]) -> dict[str, Any]:
    vcc = str(params.get("supply_net") or "+3V3")
    return {
        "parts": [
            _part("U", "Interface_UART", "MAX3485", "MAX3485", "Package_SO:SOIC-8_3.9x4.9mm_P1.27mm", 0, 0, 0),
            _part("RT", "Device", "R", "120", R_0603, 20.32, 0, 90),
            _part("C", "Device", "C", "100nF", C_0603, -12.7, -7.62, 0),
            _part("J", "Connector", "Screw_Terminal_01x02", "RS485", "TerminalBlock_Phoenix:TerminalBlock_Phoenix_MKDS-1,5-2-5.08_1x02_P5.08mm_Horizontal", 30.48, 0, 0),
            _power("VCC", vcc, -12.7, -15.24),
            _power("GND", "GND", -12.7, 7.62),
        ],
        # MAX3485: 1 RO, 2 RE, 3 DE, 4 DI, 5 GND, 6 A, 7 B, 8 VCC
        "wires": [("VCC.1", "U.8"), ("C.1", "U.8"), ("C.2", "GND.1"), ("U.5", "GND.1"), ("U.6", "RT.1"), ("U.7", "RT.2"), ("U.6", "J.1"), ("U.7", "J.2")],
        "labels": [("U.1", "RS485_RO"), ("U.2", "RS485_RE"), ("U.3", "RS485_DE"), ("U.4", "RS485_DI")],
        "notes": ["总线端点处保留 120Ω 终端电阻；RE/DE 由 MCU 控制方向。"],
    }


TEMPLATES: dict[str, dict[str, Any]] = {
    "ldo_3v3": {
        "name": "3.3V LDO 电源 (AMS1117)",
        "description": "输入电容 + AMS1117-3.3 + 输出电容，输出 3.3V。",
        "category": "电源",
        "params": [
            {"key": "input_net", "label": "输入电源网络", "type": "select", "default": "+5V", "options": ["+5V", "+12V", "VBUS"]},
            {"key": "output_net", "label": "输出电源网络", "type": "select", "default": "+3V3", "options": ["+3V3", "VCC"]},
        ],
        "build": _ldo,
    },
    "led_indicator": {
        "name": "LED 指示灯",
        "description": "电源 → 限流电阻 → LED → GND，电阻按电流自动计算并取 E24 值。",
        "category": "指示",
        "params": [
            {"key": "supply_net", "label": "电源网络", "type": "select", "default": "+3V3", "options": ["+3V3", "+5V", "VBUS"]},
            {"key": "supply_voltage", "label": "电源电压 (V)", "type": "number", "default": 3.3, "min": 1.8, "max": 48},
            {"key": "led_forward_voltage", "label": "LED 正向压降 (V)", "type": "number", "default": 2.0, "min": 1.2, "max": 4.0},
            {"key": "led_current_ma", "label": "工作电流 (mA)", "type": "number", "default": 2.0, "min": 0.5, "max": 30},
        ],
        "build": _led,
    },
    "voltage_divider": {
        "name": "电阻分压采样",
        "description": "两个电阻组成分压，自动按 E24 选值并给出实际输出电压。",
        "category": "模拟",
        "params": [
            {"key": "input_voltage", "label": "输入电压 (V)", "type": "number", "default": 12.0, "min": 0.1, "max": 400},
            {"key": "output_voltage", "label": "目标输出 (V)", "type": "number", "default": 3.3, "min": 0.05, "max": 100},
            {"key": "bottom_resistor_ohms", "label": "下端电阻 (Ω)", "type": "number", "default": 10000, "min": 100, "max": 1_000_000},
            {"key": "input_net", "label": "输入网络名", "type": "text", "default": "VIN"},
            {"key": "output_net", "label": "输出网络名", "type": "text", "default": "VSENSE"},
        ],
        "build": _divider,
    },
    "decoupling_caps": {
        "name": "去耦电容组",
        "description": "N 个并联去耦电容，接在电源与 GND 之间。",
        "category": "电源",
        "params": [
            {"key": "count", "label": "电容数量", "type": "number", "default": 2, "min": 1, "max": 8},
            {"key": "value", "label": "电容值", "type": "select", "default": "100nF", "options": ["100nF", "1uF", "10uF"]},
            {"key": "power_net", "label": "电源网络", "type": "select", "default": "+3V3", "options": ["+3V3", "+5V", "+1V8", "VCC"]},
        ],
        "build": _decoupling,
    },
    "usb_c_power": {
        "name": "USB-C 取电接口",
        "description": "USB-C 母座 + CC1/CC2 5.1k 下拉，作为 5V 受电设备。",
        "category": "接口",
        "params": [{"key": "vbus_net", "label": "VBUS 网络", "type": "select", "default": "VBUS", "options": ["VBUS", "+5V"]}],
        "build": _usb_c_power,
    },
    "rs485_transceiver": {
        "name": "RS-485 收发器 (MAX3485)",
        "description": "MAX3485 + 去耦电容 + 120Ω 终端电阻 + 接线端子。",
        "category": "接口",
        "params": [{"key": "supply_net", "label": "电源网络", "type": "select", "default": "+3V3", "options": ["+3V3", "+5V"]}],
        "build": _rs485,
    },
}


def list_templates() -> list[dict[str, Any]]:
    return [
        {"id": key, "name": t["name"], "description": t["description"], "category": t["category"], "params": t["params"]}
        for key, t in TEMPLATES.items()
    ]


def _coerce_params(template: dict[str, Any], params: dict[str, Any]) -> dict[str, Any]:
    out: dict[str, Any] = {}
    for spec in template["params"]:
        raw = params.get(spec["key"], spec.get("default"))
        if spec["type"] == "number":
            try:
                value = float(raw)
            except (TypeError, ValueError) as exc:
                raise ValueError(f"参数 {spec['label']} 必须是数字") from exc
            if "min" in spec and value < spec["min"] or "max" in spec and value > spec["max"]:
                raise ValueError(f"参数 {spec['label']} 超出范围 {spec.get('min')}~{spec.get('max')}")
            out[spec["key"]] = value
        elif spec["type"] == "select":
            value = str(raw)
            if value not in spec["options"]:
                raise ValueError(f"参数 {spec['label']} 只能是 {', '.join(spec['options'])}")
            out[spec["key"]] = value
        else:
            value = str(raw or "").strip()
            if not value or len(value) > 32 or not all(ch.isalnum() or ch in "_+-." for ch in value):
                raise ValueError(f"参数 {spec['label']} 只能包含字母、数字、_ + - .")
            out[spec["key"]] = value
    return out


def expand_template(template_id: str, params: dict[str, Any], anchor_x: float, anchor_y: float) -> dict[str, Any]:
    """Resolve a template into absolute, grid-aligned parts, wires and labels."""
    template = TEMPLATES.get(template_id)
    if template is None:
        raise ValueError(f"未知电路模板：{template_id}")
    clean = _coerce_params(template, params or {})
    built = template["build"](clean)
    parts = [
        {**part, "x": snap(anchor_x + part["dx"]), "y": snap(anchor_y + part["dy"])}
        for part in built["parts"]
    ]
    return {
        "template": template_id,
        "name": template["name"],
        "params": clean,
        "anchor": {"x": snap(anchor_x), "y": snap(anchor_y)},
        "parts": parts,
        "wires": [{"from": a, "to": b} for a, b in built["wires"]],
        "labels": [{"pin": pin, "text": text} for pin, text in built.get("labels", [])],
        "notes": built.get("notes", []),
        "symbols": sorted({f"{p['library']}:{p['symbol']}" for p in parts}),
        "footprints": sorted({p["footprint"] for p in parts if p["footprint"]}),
    }


def bounding_size(expanded: dict[str, Any]) -> tuple[float, float]:
    xs = [p["x"] for p in expanded["parts"]]
    ys = [p["y"] for p in expanded["parts"]]
    return (max(xs) - min(xs) + 25.4, max(ys) - min(ys) + 25.4)


def check_library_availability(expanded: dict[str, Any]) -> dict[str, Any]:
    """Verify template symbols/footprints exist in the installed KiCad index."""
    try:
        from app.kicad.libraries import resolve_paths
        from kcaa.utils.config import ServerConfig
        from kcaa.utils.footprint_index_manager import FootprintIndexManager
        from kcaa.utils.symbol_index_manager import SymbolIndexManager
        from kcaa.utils.symbol_index_reader import SymbolIndexReader
    except Exception:  # noqa: BLE001
        return {"checked": False, "missing_symbols": [], "missing_footprints": []}
    paths = resolve_paths()
    if not paths.symbol_db.is_file() or not paths.footprint_db.is_file():
        return {"checked": False, "missing_symbols": [], "missing_footprints": []}
    missing_symbols: list[str] = []
    missing_footprints: list[str] = []
    sym_mgr = SymbolIndexManager(SymbolIndexReader(ServerConfig()), db_path=paths.symbol_db)
    try:
        for lib_id in expanded["symbols"]:
            library, name = lib_id.split(":", 1)
            if sym_mgr.get_symbol(library, name) is None:
                missing_symbols.append(lib_id)
    finally:
        sym_mgr.close()
    fp_mgr = FootprintIndexManager(db_path=paths.footprint_db)
    try:
        for lib_id in expanded["footprints"]:
            library, name = lib_id.split(":", 1)
            if fp_mgr.get_footprint(library, name) is None:
                missing_footprints.append(lib_id)
    finally:
        close = getattr(fp_mgr, "close", None)
        if close:
            close()
    return {"checked": True, "missing_symbols": missing_symbols, "missing_footprints": missing_footprints}
