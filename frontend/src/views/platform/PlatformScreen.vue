<template>
  <!-- 平台总览大屏:全部客户站点的规模、活跃、AI 用量与商店运转,给平台方 / 多校区客户看 -->
  <div class="screen-root" :class="{ light: theme === 'light' }">
    <div class="canvas" :style="canvasStyle">
      <header class="hd">
        <div class="hd-left">
          <button class="back" title="返回平台控制台" @click="$router.push('/platform/home')">⟵</button>
          <span class="date">{{ dateText }}</span>
          <span class="live"><i></i>LIVE</span>
        </div>
        <div class="hd-title">
          <span class="deco l"></span>
          <div class="title-wrap"><h1>项目商店 · 平台总览大屏</h1><div class="sub">MULTI-SITE PLATFORM OVERVIEW</div></div>
          <span class="deco r"></span>
        </div>
        <div class="hd-right">
          <span class="time">{{ timeText }}</span>
          <button class="icon-btn" @click="theme = theme === 'dark' ? 'light' : 'dark'">{{ theme === 'dark' ? '☀' : '☾' }}</button>
          <button class="icon-btn" title="全屏" @click="toggleFullscreen">⛶</button>
        </div>
      </header>

      <section class="totals">
        <div class="tot" v-for="t in totals" :key="t.label" :style="{ '--accent': t.color }">
          <span class="tot-icon">{{ t.icon }}</span>
          <div><b class="big"><CountUp :value="t.value" /></b><span class="tot-label">{{ t.label }}</span></div>
          <small v-if="t.sub">{{ t.sub }}</small>
        </div>
      </section>

      <section class="main">
        <div class="frame sites">
          <div class="box-title">客户站点<small>· 按 7 天活跃排序</small><em>SITES</em></div>
          <div class="site-list">
            <div v-for="(s, i) in siteRows" :key="s.code" class="site-row page-enter" :style="{ animationDelay: (i * 50) + 'ms' }">
              <span class="rank-no" :class="'r' + (i + 1)">{{ i + 1 }}</span>
              <div class="site-name"><b>{{ s.name }}</b><span class="rank-sub">{{ s.code }}{{ s.plan ? ' · ' + s.plan : '' }}</span></div>
              <span class="tag" :class="s.status === 'ACTIVE' && !s.expired ? 'grn' : 'red'">{{ s.expired ? '已到期' : s.status === 'ACTIVE' ? '运行中' : '已停用' }}</span>
              <div class="metric"><span>用户</span><b>{{ s.users }}</b><small v-if="s.maxUsers">/ {{ s.maxUsers }}</small></div>
              <div class="metric"><span>7 天活跃</span><b class="grn">{{ s.activeUsers7d }}</b></div>
              <div class="metric"><span>项目 / 报名</span><b>{{ s.projects }} / {{ s.enrollments }}</b></div>
              <div class="metric"><span>AI 本月</span><b>{{ fmtK(s.aiTokensMonth) }}</b><small> · {{ s.aiRunsMonth }} 次</small></div>
              <div class="metric"><span>存储</span><b>{{ s.storageMb }} MB</b></div>
              <div class="bar-wrap" :title="`活跃率 ${s.activeRate}%`"><i :style="{ width: s.activeRate + '%' }"></i></div>
            </div>
            <div v-if="!siteRows.length" class="empty-hint">还没有客户站点</div>
          </div>
        </div>
        <aside class="side">
          <div class="frame">
            <div class="box-title">各站点用户与活跃<em>USERS</em></div>
            <div ref="userEl" class="chart"></div>
          </div>
          <div class="frame">
            <div class="box-title">各站点 AI 用量(本月 Token)<em>AI</em></div>
            <div ref="aiEl" class="chart"></div>
          </div>
          <div class="frame store">
            <div class="box-title">项目商店<em>STORE</em></div>
            <div class="kv-grid">
              <div class="kv"><b>{{ stats.published ?? 0 }}</b><span>已上架项目</span></div>
              <div class="kv" :class="{ warn: (stats.pending || 0) > 0 }"><b>{{ stats.pending ?? 0 }}</b><span>待审核</span></div>
              <div class="kv ok"><b>{{ stats.installs ?? 0 }}</b><span>累计安装</span></div>
              <div class="kv"><b>{{ stats.featured ?? 0 }}</b><span>推荐置顶</span></div>
              <div class="kv"><b>{{ stats.tenants ?? sites.length }}</b><span>接入客户</span></div>
              <div class="kv"><b>{{ stats.items ?? 0 }}</b><span>条目总数</span></div>
            </div>
          </div>
        </aside>
      </section>

      <footer class="ft">每 60 秒自动刷新 · 更新于 {{ updatedAt }} · 平台侧汇总,各站点数据互相隔离</footer>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import * as echarts from 'echarts'
import { hubSites, hubSitesUsage, hubStats } from '../../api/hub'
import CountUp from '../../components/CountUp.vue'
import '../../styles/screen.css'

const theme = ref(localStorage.getItem('screen-theme') || 'dark')
const sites = ref([])
const usage = ref([])
const stats = ref({})
const updatedAt = ref('–')
const userEl = ref(null)
const aiEl = ref(null)
let userChart = null
let aiChart = null

const scale = ref(1)
const canvasStyle = computed(() => ({ transform: `translate(-50%, -50%) scale(${scale.value})` }))
const fit = () => { scale.value = Math.min(window.innerWidth / 1920, window.innerHeight / 1080) }
const now = ref(new Date())
const pad = (n) => String(n).padStart(2, '0')
const dateText = computed(() => `${now.value.getFullYear()}-${pad(now.value.getMonth() + 1)}-${pad(now.value.getDate())}`)
const timeText = computed(() => `${pad(now.value.getHours())}:${pad(now.value.getMinutes())}:${pad(now.value.getSeconds())}`)
const fmtK = (n) => (n >= 1000000 ? (n / 1000000).toFixed(1) + 'M' : n >= 1000 ? (n / 1000).toFixed(0) + 'k' : String(n || 0))

const siteRows = computed(() => sites.value.map((s) => {
  const u = usage.value.find((x) => x.code === s.code) || {}
  const users = u.users || 0
  return { ...s, users, activeUsers7d: u.activeUsers7d || 0, projects: u.projects || 0, enrollments: u.enrollments || 0,
    aiTokensMonth: u.aiTokensMonth || 0, aiRunsMonth: u.aiRunsMonth || 0, storageMb: u.storageMb || 0,
    activeRate: users ? Math.round((u.activeUsers7d || 0) * 100 / users) : 0 }
}).sort((a, b) => b.activeUsers7d - a.activeUsers7d || b.users - a.users))
const sum = (k) => siteRows.value.reduce((s, r) => s + (r[k] || 0), 0)
const totals = computed(() => [
  { icon: '🏫', label: '客户站点', value: sites.value.length, sub: `${sites.value.filter((s) => s.status === 'ACTIVE' && !s.expired).length} 个运行中`, color: '#22e1ff' },
  { icon: '👥', label: '平台总用户', value: sum('users'), sub: `${sum('activeUsers7d')} 人 7 天活跃`, color: '#35e37a' },
  { icon: '🚀', label: '项目 / 报名', value: sum('enrollments'), sub: `${sum('projects')} 个项目`, color: '#f7b731' },
  { icon: '✦', label: '本月 AI 调用', value: sum('aiRunsMonth'), sub: `${fmtK(sum('aiTokensMonth'))} Token`, color: '#b97cff' },
  { icon: '💾', label: '存储占用 MB', value: sum('storageMb'), sub: `${stats.value.installs ?? 0} 次商店安装`, color: '#3b9dff' }
])

const palette = computed(() => (theme.value === 'light'
  ? { text: '#0f172a', textDim: '#64748b', grid: 'rgba(11,116,209,.12)', tooltipBg: 'rgba(255,255,255,.96)', border: 'rgba(11,116,209,.35)' }
  : { text: '#dff6ff', textDim: '#9fd6ff', grid: 'rgba(34,225,255,.12)', tooltipBg: 'rgba(6,18,41,.95)', border: 'rgba(34,225,255,.35)' }))
const render = () => {
  const p = palette.value
  const rows = siteRows.value.slice(0, 10)
  const base = (extra) => ({ backgroundColor: 'transparent', animationDuration: 1000, grid: { left: 8, right: 30, top: 28, bottom: 8, containLabel: true },
    tooltip: { trigger: 'axis', backgroundColor: p.tooltipBg, borderColor: p.border, textStyle: { color: p.text } },
    legend: { top: 0, right: 8, textStyle: { color: p.textDim, fontSize: 11 }, itemWidth: 12, itemHeight: 8 },
    xAxis: { type: 'value', splitLine: { lineStyle: { color: p.grid } }, axisLabel: { color: p.textDim, fontSize: 11 } },
    yAxis: { type: 'category', data: rows.map((r) => r.name).reverse(), axisLabel: { color: p.text, fontSize: 12, width: 110, overflow: 'truncate' }, axisLine: { show: false }, axisTick: { show: false } }, ...extra })
  if (userEl.value) {
    if (!userChart) userChart = echarts.init(userEl.value)
    userChart.setOption(base({ series: [
      { name: '用户', type: 'bar', data: rows.map((r) => r.users).reverse(), barWidth: 10, itemStyle: { color: '#22e1ff', borderRadius: [0, 5, 5, 0] } },
      { name: '7 天活跃', type: 'bar', data: rows.map((r) => r.activeUsers7d).reverse(), barWidth: 10, itemStyle: { color: '#35e37a', borderRadius: [0, 5, 5, 0] } }
    ] }), true)
  }
  if (aiEl.value) {
    if (!aiChart) aiChart = echarts.init(aiEl.value)
    aiChart.setOption(base({ legend: { show: false }, series: [
      { name: 'Token', type: 'bar', data: rows.map((r) => r.aiTokensMonth).reverse(), barWidth: 12, label: { show: true, position: 'right', color: p.text, fontSize: 11, formatter: (x) => fmtK(x.value) },
        itemStyle: { borderRadius: [0, 6, 6, 0], color: new echarts.graphic.LinearGradient(0, 0, 1, 0, [{ offset: 0, color: 'rgba(185,124,255,.3)' }, { offset: 1, color: '#b97cff' }]) } }
    ] }), true)
  }
}
const load = async () => {
  try {
    const [s, st] = await Promise.all([hubSites(), hubStats()])
    sites.value = s
    stats.value = st || {}
    usage.value = await hubSitesUsage()
    updatedAt.value = timeText.value
    await nextTick()
    render()
  } catch (e) { /* 已提示 */ }
}
const toggleFullscreen = () => { if (document.fullscreenElement) document.exitFullscreen(); else document.documentElement.requestFullscreen?.() }

let clock = null
let timer = null
onMounted(() => {
  fit(); window.addEventListener('resize', fit)
  clock = setInterval(() => { now.value = new Date() }, 1000)
  timer = setInterval(load, 60000)
  load()
})
onBeforeUnmount(() => { window.removeEventListener('resize', fit); clearInterval(clock); clearInterval(timer); userChart?.dispose(); aiChart?.dispose() })
</script>

<style scoped>
.hd { display: grid; grid-template-columns: 1fr auto 1fr; align-items: center; height: 84px; }
.hd-left, .hd-right { display: flex; align-items: center; gap: 14px; font-size: 18px; color: var(--txt-dim); }
.hd-right { justify-content: flex-end; }
.live { display: inline-flex; align-items: center; gap: 6px; font-size: 12px; letter-spacing: 2px; color: var(--red); border: 1px solid rgba(255, 91, 110, .5); padding: 2px 8px; border-radius: 3px; }
.live i { width: 7px; height: 7px; border-radius: 50%; background: var(--red); box-shadow: 0 0 8px var(--red); animation: blink 1.2s infinite; }
@keyframes blink { 50% { opacity: .2; } }
.back, .icon-btn { width: 38px; height: 38px; border-radius: 50%; border: 1px solid var(--cy-dim); background: var(--cy-soft); color: var(--cy); font-size: 17px; cursor: pointer; display: grid; place-items: center; }
.time { font-size: 26px; color: var(--ink); text-shadow: 0 0 12px var(--cy-dim); }
.hd-title { display: flex; align-items: center; gap: 14px; }
.title-wrap { text-align: center; padding: 4px 36px 6px; border-bottom: 2px solid var(--cy); }
.hd-title h1 { margin: 0; font-size: 32px; letter-spacing: 5px; font-weight: 800; color: var(--ink); text-shadow: 0 0 14px var(--cy-dim); white-space: nowrap; }
.sub { margin-top: 2px; font-size: 11px; letter-spacing: 5px; color: var(--cy); opacity: .8; }
.deco { width: 220px; height: 3px; background: linear-gradient(90deg, transparent, var(--cy)); }
.deco.r { background: linear-gradient(90deg, var(--cy), transparent); }
.totals { display: grid; grid-template-columns: repeat(5, 1fr); gap: 12px; height: 110px; }
.tot { position: relative; border: 1px solid var(--cy-dim); border-radius: 6px; background: var(--panel); display: flex; align-items: center; gap: 16px; padding: 0 22px; overflow: hidden; }
.tot::before { content: ''; position: absolute; left: 0; top: 0; bottom: 0; width: 4px; background: var(--accent); box-shadow: 0 0 12px var(--accent); }
.tot-icon { font-size: 30px; }
.tot .big { display: block; font-size: 38px; line-height: 1; color: var(--ink); text-shadow: 0 0 14px var(--cy-dim); }
.tot-label { display: block; font-size: 13px; color: var(--txt-dim); margin-top: 6px; }
.tot small { margin-left: auto; font-size: 12px; color: var(--accent); white-space: nowrap; }
.main { flex: 1; display: grid; grid-template-columns: 1fr 520px; gap: 12px; min-height: 0; }
.sites { display: flex; flex-direction: column; position: relative; }
.site-list { flex: 1; overflow: hidden; padding: 10px 16px 12px; display: flex; flex-direction: column; gap: 10px; }
.site-row { display: grid; grid-template-columns: 26px 1.4fr 70px repeat(5, 1fr) 120px; align-items: center; gap: 12px; padding: 10px 14px; border: 1px solid var(--cy-dim); border-radius: 8px; background: var(--card-bg); }
.site-name b { display: block; color: var(--ink); font-size: 15px; }
.metric { display: flex; flex-direction: column; gap: 2px; }
.metric span { font-size: 11px; color: var(--txt-dim); }
.metric b { font-size: 18px; color: var(--ink); }
.metric b.grn { color: var(--grn); }
.metric small { font-size: 11px; color: var(--txt-dim); }
.bar-wrap { height: 8px; border-radius: 4px; background: rgba(0, 0, 0, .35); overflow: hidden; }
.bar-wrap i { display: block; height: 100%; background: linear-gradient(90deg, var(--cy), var(--grn)); border-radius: 4px; }
.side { display: grid; grid-template-rows: 1fr 1fr 190px; gap: 12px; min-height: 0; }
.side .frame { display: flex; flex-direction: column; }
.store .kv-grid { flex: 1; }
.store .kv b { font-size: 26px; }
.ft { text-align: center; font-size: 12px; color: var(--txt-dim); height: 20px; }
</style>
