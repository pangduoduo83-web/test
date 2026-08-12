/**
 * 生成 tabBar 图标(81x81 PNG,4x4 超采样抗锯齿),零第三方依赖。
 * 运行:node scripts/gen-tab-icons.mjs
 */
import { deflateSync } from 'node:zlib'
import { mkdirSync, writeFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const SIZE = 81
const SS = 4 // 超采样倍数
const OUT_DIR = join(dirname(fileURLToPath(import.meta.url)), '..', 'src', 'static', 'tab')

// ---------- PNG 编码 ----------
const crcTable = (() => {
  const t = new Int32Array(256)
  for (let n = 0; n < 256; n++) {
    let c = n
    for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1
    t[n] = c
  }
  return t
})()

function crc32(buf) {
  let c = -1
  for (let i = 0; i < buf.length; i++) c = crcTable[(c ^ buf[i]) & 0xff] ^ (c >>> 8)
  return (c ^ -1) >>> 0
}

function chunk(type, data) {
  const len = Buffer.alloc(4)
  len.writeUInt32BE(data.length)
  const body = Buffer.concat([Buffer.from(type, 'ascii'), data])
  const crc = Buffer.alloc(4)
  crc.writeUInt32BE(crc32(body))
  return Buffer.concat([len, body, crc])
}

function encodePng(rgba, w, h) {
  const sig = Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a])
  const ihdr = Buffer.alloc(13)
  ihdr.writeUInt32BE(w, 0)
  ihdr.writeUInt32BE(h, 4)
  ihdr[8] = 8 // bit depth
  ihdr[9] = 6 // RGBA
  const raw = Buffer.alloc(h * (w * 4 + 1))
  for (let y = 0; y < h; y++) {
    raw[y * (w * 4 + 1)] = 0 // filter none
    rgba.copy(raw, y * (w * 4 + 1) + 1, y * w * 4, (y + 1) * w * 4)
  }
  return Buffer.concat([
    sig,
    chunk('IHDR', ihdr),
    chunk('IDAT', deflateSync(raw, { level: 9 })),
    chunk('IEND', Buffer.alloc(0))
  ])
}

// ---------- 形状判定(坐标均为 81 空间) ----------
const roundRect = (x, y, w, h, r) => (px, py) => {
  if (px < x || px > x + w || py < y || py > y + h) return false
  const rr = Math.min(r, w / 2, h / 2)
  const cx = Math.max(x + rr, Math.min(px, x + w - rr))
  const cy = Math.max(y + rr, Math.min(py, y + h - rr))
  const dx = px - cx
  const dy = py - cy
  return dx * dx + dy * dy <= rr * rr
}

const circle = (cx, cy, r) => (px, py) => {
  const dx = px - cx
  const dy = py - cy
  return dx * dx + dy * dy <= r * r
}

const rect = (x, y, w, h) => (px, py) => px >= x && px <= x + w && py >= y && py <= y + h

// ---------- 图标定义:add 并集 / sub 差集 ----------
const icons = {
  // 项目:2x2 圆角方块(项目库)
  project: [
    { op: 'add', s: roundRect(15, 15, 22, 22, 7) },
    { op: 'add', s: roundRect(44, 15, 22, 22, 7) },
    { op: 'add', s: roundRect(15, 44, 22, 22, 7) },
    { op: 'add', s: roundRect(44, 44, 22, 22, 7) }
  ],
  // 设备:芯片(方体+引脚+内孔)
  equipment: [
    { op: 'add', s: roundRect(22, 22, 37, 37, 7) },
    { op: 'sub', s: roundRect(35, 35, 11, 11, 3) },
    { op: 'add', s: roundRect(27, 13, 5, 9, 2) },
    { op: 'add', s: roundRect(38, 13, 5, 9, 2) },
    { op: 'add', s: roundRect(49, 13, 5, 9, 2) },
    { op: 'add', s: roundRect(27, 59, 5, 9, 2) },
    { op: 'add', s: roundRect(38, 59, 5, 9, 2) },
    { op: 'add', s: roundRect(49, 59, 5, 9, 2) },
    { op: 'add', s: roundRect(13, 27, 9, 5, 2) },
    { op: 'add', s: roundRect(13, 38, 9, 5, 2) },
    { op: 'add', s: roundRect(13, 49, 9, 5, 2) },
    { op: 'add', s: roundRect(59, 27, 9, 5, 2) },
    { op: 'add', s: roundRect(59, 38, 9, 5, 2) },
    { op: 'add', s: roundRect(59, 49, 9, 5, 2) }
  ],
  // 借阅:剪贴板列表
  borrow: [
    { op: 'add', s: roundRect(17, 14, 47, 55, 9) },
    { op: 'sub', s: rect(25, 31, 31, 5.5) },
    { op: 'sub', s: rect(25, 43, 31, 5.5) },
    { op: 'sub', s: rect(25, 55, 21, 5.5) },
    { op: 'add', s: roundRect(31, 8, 19, 12, 5) }
  ],
  // 我的:人形(头+肩)
  mine: [
    { op: 'add', s: circle(40.5, 27, 14) },
    { op: 'add', s: roundRect(15, 46, 51, 52, 25.5) },
    { op: 'sub', s: rect(0, 72, 81, 12) }
  ]
}

const colors = {
  normal: [0x9c, 0xa3, 0xaf], // gray-400
  active: [0x25, 0x63, 0xeb] // brand blue
}

function coverage(defs, px, py) {
  let inside = false
  for (const { op, s } of defs) {
    if (op === 'add' && s(px, py)) inside = true
    if (op === 'sub' && s(px, py)) inside = false
  }
  return inside
}

function render(defs, rgb) {
  const buf = Buffer.alloc(SIZE * SIZE * 4)
  for (let y = 0; y < SIZE; y++) {
    for (let x = 0; x < SIZE; x++) {
      let hit = 0
      for (let sy = 0; sy < SS; sy++) {
        for (let sx = 0; sx < SS; sx++) {
          const px = x + (sx + 0.5) / SS
          const py = y + (sy + 0.5) / SS
          if (coverage(defs, px, py)) hit++
        }
      }
      const alpha = Math.round((hit / (SS * SS)) * 255)
      const i = (y * SIZE + x) * 4
      buf[i] = rgb[0]
      buf[i + 1] = rgb[1]
      buf[i + 2] = rgb[2]
      buf[i + 3] = alpha
    }
  }
  return buf
}

mkdirSync(OUT_DIR, { recursive: true })
for (const [name, defs] of Object.entries(icons)) {
  writeFileSync(join(OUT_DIR, `${name}.png`), encodePng(render(defs, colors.normal), SIZE, SIZE))
  writeFileSync(join(OUT_DIR, `${name}-active.png`), encodePng(render(defs, colors.active), SIZE, SIZE))
  console.log(`generated ${name}.png / ${name}-active.png`)
}
console.log(`done → ${OUT_DIR}`)
