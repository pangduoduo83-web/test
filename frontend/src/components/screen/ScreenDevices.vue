<template>
  <!-- 设备排行 · 24 小时活跃分布 · 资产与 AI -->
  <section class="devices-page page-enter">
    <div class="frame equip">
      <div class="box-title">设备使用排行<small>· 累计借用次数 Top 10</small><em>EQUIPMENT</em></div>
      <div ref="equipEl" class="chart"></div>
    </div>
    <div class="frame hours">
      <div class="box-title">近 7 天 · 学习时段分布<small>· 哪个时段学生最活跃</small><em>HOURS</em></div>
      <div ref="hourEl" class="chart"></div>
      <div class="hour-foot">高峰时段 <b>{{ peak }}</b> · 夜间(22:00 后)占比 <b>{{ nightRate }}%</b></div>
    </div>
    <div class="frame assets">
      <div class="box-title">实验室资产<em>ASSETS</em></div>
      <div class="kv-grid">
        <div class="kv"><b>{{ money(assets.value) }}</b><span>设备资产总值</span></div>
        <div class="kv"><b>{{ assets.kinds }}</b><span>设备种类</span></div>
        <div class="kv"><b>{{ assets.units }}</b><span>设备总数(件)</span></div>
        <div class="kv ok"><b>{{ data.equipment.utilization }}%</b><span>当前在用率</span></div>
        <div class="kv"><b>{{ assets.borrowsMonth }}</b><span>近 30 天借用</span></div>
        <div class="kv"><b>{{ assets.borrowsTotal }}</b><span>累计借用</span></div>
      </div>
    </div>
    <div class="frame ai">
      <div class="box-title">AI 助教 · KiCad 设计助手<em>AI</em></div>
      <div class="kv-grid">
        <div class="kv"><b>{{ data.ai.runsToday }}</b><span>今日 AI 辅导</span></div>
        <div class="kv"><b>{{ data.ai.runsMonth }}</b><span>本月 AI 辅导</span></div>
        <div class="kv"><b>{{ fmtK(data.ai.tokensMonth) }}</b><span>本月 Token</span></div>
        <div class="kv" :class="{ ok: data.kicad }"><b>{{ data.kicad ? data.kicad.conversations : '–' }}</b><span>KiCad 设计对话</span></div>
        <div class="kv" :class="{ ok: data.kicad }"><b>{{ data.kicad ? fmtK(data.kicad.toolCalls) : '–' }}</b><span>KiCad 工具调用</span></div>
        <div class="kv" :class="{ ok: data.kicad }"><b>{{ data.kicad ? data.kicad.online : '–' }}</b><span>KiCad 在线</span></div>
      </div>
      <div class="ai-foot">
        <span class="tag grn">{{ data.kicad?.agentReady ? 'KiCad Agent 在线 · ' + data.kicad.toolCount + ' 个工具' : 'KiCad 助手未接入' }}</span>
        <span class="tag">AI 项目导师 · 今日建议 · 教学周报 · 项目起草</span>
      </div>
    </div>
  </section>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts'

const props = defineProps({ data: { type: Object, required: true }, palette: { type: Object, required: true } })
const equipEl = ref(null)
const hourEl = ref(null)
let equipChart = null
let hourChart = null
const assets = computed(() => props.data.equipmentAssets || { value: 0, kinds: 0, units: 0, borrowsMonth: 0, borrowsTotal: 0 })
const hours = computed(() => props.data.hours || [])
const peak = computed(() => {
  if (!hours.value.length) return '–'
  const i = hours.value.indexOf(Math.max(...hours.value))
  return `${String(i).padStart(2, '0')}:00 - ${String(i + 1).padStart(2, '0')}:00`
})
const nightRate = computed(() => {
  const total = hours.value.reduce((s, v) => s + v, 0)
  if (!total) return 0
  const night = hours.value.slice(22).reduce((s, v) => s + v, 0) + hours.value.slice(0, 6).reduce((s, v) => s + v, 0)
  return Math.round(night * 100 / total)
})
const fmtK = (n) => (n >= 1000000 ? (n / 1000000).toFixed(1) + 'M' : n >= 1000 ? (n / 1000).toFixed(n >= 100000 ? 0 : 1) + 'k' : String(n || 0))
const money = (n) => (n >= 10000 ? '¥' + (n / 10000).toFixed(n >= 1000000 ? 0 : 1) + '万' : '¥' + (n || 0))

const render = () => {
  const p = props.palette
  if (equipEl.value) {
    if (!equipChart) equipChart = echarts.init(equipEl.value)
    const top = [...(props.data.equipmentTop || [])].reverse()
    equipChart.setOption({
      backgroundColor: 'transparent', animationDuration: 1000,
      grid: { left: 8, right: 60, top: 10, bottom: 10, containLabel: true },
      tooltip: { trigger: 'axis', backgroundColor: p.tooltipBg, borderColor: p.border, textStyle: { color: p.text }, formatter: (ps) => { const d = top[ps[0].dataIndex]; return `${d.name}<br/>借用 ${d.borrowCount} 次 · 在库 ${d.available}/${d.total}` } },
      xAxis: { type: 'value', splitLine: { lineStyle: { color: p.grid } }, axisLabel: { color: p.textDim, fontSize: 11 } },
      yAxis: { type: 'category', data: top.map((e) => e.name), axisLabel: { color: p.text, fontSize: 12, width: 150, overflow: 'truncate' }, axisLine: { show: false }, axisTick: { show: false } },
      series: [{ type: 'bar', data: top.map((e) => e.borrowCount), barWidth: 14,
        itemStyle: { borderRadius: [0, 7, 7, 0], color: new echarts.graphic.LinearGradient(0, 0, 1, 0, [{ offset: 0, color: 'rgba(34,225,255,.3)' }, { offset: 1, color: '#22e1ff' }]) },
        label: { show: true, position: 'right', color: p.text, fontSize: 12, fontWeight: 700, formatter: '{c} 次' } }]
    })
  }
  if (hourEl.value) {
    if (!hourChart) hourChart = echarts.init(hourEl.value)
    const h = hours.value
    const max = Math.max(1, ...h)
    hourChart.setOption({
      backgroundColor: 'transparent', animationDuration: 1000,
      grid: { left: 36, right: 16, top: 16, bottom: 26 },
      tooltip: { trigger: 'axis', backgroundColor: p.tooltipBg, borderColor: p.border, textStyle: { color: p.text }, formatter: (ps) => `${ps[0].axisValue}:00 · ${ps[0].value} 次学习动作` },
      xAxis: { type: 'category', data: h.map((_, i) => String(i).padStart(2, '0')), axisLabel: { color: p.textDim, fontSize: 10, interval: 1 }, axisLine: { lineStyle: { color: p.border } }, axisTick: { show: false } },
      yAxis: { type: 'value', minInterval: 1, splitLine: { lineStyle: { color: p.grid } }, axisLabel: { color: p.textDim, fontSize: 11 } },
      series: [{ type: 'bar', data: h.map((v) => ({ value: v, itemStyle: { color: heat(v / max) } })), barWidth: '62%', itemStyle: { borderRadius: [3, 3, 0, 0] } }]
    })
  }
}
const heat = (t) => {
  // 冷→热:青 → 绿 → 黄 → 红
  const stops = [[34, 225, 255], [53, 227, 122], [247, 183, 49], [255, 91, 110]]
  const x = Math.max(0, Math.min(0.999, t)) * (stops.length - 1)
  const i = Math.floor(x)
  const f = x - i
  const c = stops[i].map((v, k) => Math.round(v + (stops[i + 1][k] - v) * f))
  return `rgb(${c[0]},${c[1]},${c[2]})`
}
onMounted(() => nextTick(render))
watch(() => [props.data, props.palette], () => nextTick(render), { deep: true })
onBeforeUnmount(() => { equipChart?.dispose(); hourChart?.dispose() })
</script>

<style scoped>
.devices-page { flex: 1; display: grid; grid-template-columns: 1.2fr 1fr; grid-template-rows: 1fr 230px; gap: 14px; min-height: 0; }
.equip, .hours, .assets, .ai { display: flex; flex-direction: column; position: relative; }
.hour-foot { padding: 0 16px 10px; font-size: 12px; color: var(--txt-dim); }
.hour-foot b { color: var(--cy); margin: 0 2px; }
.assets .kv-grid, .ai .kv-grid { flex: 1; }
.assets .kv b, .ai .kv b { font-size: 26px; }
.ai-foot { display: flex; gap: 8px; padding: 0 16px 12px; flex-wrap: wrap; }
</style>
