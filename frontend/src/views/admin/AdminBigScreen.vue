<template>
  <!-- 数据大屏:1920×1080 画布按窗口等比缩放;数据来自当前站点(Host)的库,多商户天然隔离 -->
  <div class="screen-root">
    <canvas ref="bgCanvas" class="bg-particles"></canvas>
    <div class="scan"></div>

    <div class="canvas" :style="canvasStyle">
      <header class="hd">
        <div class="hd-left">
          <button class="back" title="返回管理后台" @click="back">⟵</button>
          <span class="date">{{ dateText }}</span>
          <span class="live"><i></i>LIVE</span>
        </div>
        <div class="hd-title">
          <span class="ring l"><i></i></span>
          <span class="deco l"></span>
          <div class="title-wrap">
            <h1>{{ data.siteTitle || site.title }} · 实时数据看板</h1>
            <div class="sub">REAL-TIME TEACHING DATA CENTER</div>
          </div>
          <span class="deco r"></span>
          <span class="ring r"><i></i></span>
        </div>
        <div class="hd-right">
          <span class="week">{{ weekText }}</span>
          <span class="time">{{ timeText }}</span>
          <button class="icon-btn" title="全屏" @click="toggleFullscreen">⛶</button>
        </div>
      </header>

      <!-- 顶部统计 -->
      <section class="stats">
        <div class="panel p-blue">
          <div class="p-title"><i></i>用户<em>USERS</em></div>
          <div class="nums">
            <div class="num"><b><CountUp :value="data.users.total" /></b><span>总用户</span></div>
            <div class="num"><b><CountUp :value="data.users.students" /></b><span>学生</span></div>
            <div class="num"><b><CountUp :value="data.users.teachers" /></b><span>教师</span></div>
            <div class="num hi"><b><CountUp :value="data.users.activeToday" /></b><span>今日活跃</span></div>
            <div class="num"><b><CountUp :value="data.users.active7d" /></b><span>7 天活跃</span></div>
          </div>
        </div>
        <div class="panel p-amber wide">
          <div class="p-title"><i></i>项目进度<em>PROGRESS</em><small>{{ data.projects.enrollments }} 个报名 · 完成率 {{ data.projects.completionRate }}%</small></div>
          <div class="nums">
            <div class="num"><b><CountUp :value="data.projects.buckets.notStarted" /></b><span>未开始</span></div>
            <div class="num"><b><CountUp :value="data.projects.buckets.p0_25" /></b><span>0%-25%</span></div>
            <div class="num"><b><CountUp :value="data.projects.buckets.p25_50" /></b><span>25%-50%</span></div>
            <div class="num"><b><CountUp :value="data.projects.buckets.p50_75" /></b><span>50%-75%</span></div>
            <div class="num"><b><CountUp :value="data.projects.buckets.p75_100" /></b><span>75%-100%</span></div>
            <div class="num ok"><b><CountUp :value="data.projects.completed" /></b><span>已完成</span></div>
            <div class="num" :class="{ bad: data.projects.overdue > 0 }"><b><CountUp :value="data.projects.overdue" /></b><span>已过截止</span></div>
          </div>
        </div>
        <div class="panel p-purple">
          <div class="p-title"><i></i>成果评审<em>REVIEW</em></div>
          <div class="nums">
            <div class="num" :class="{ warn: data.grades.pending > 0 }"><b><CountUp :value="data.grades.pending" /></b><span>待评审</span></div>
            <div class="num"><b><CountUp :value="data.grades.graded" /></b><span>已评审</span></div>
            <div class="num hi"><b><CountUp :value="data.grades.avgScore" /></b><span>平均分</span></div>
            <div class="num" :class="{ bad: data.grades.failing > 0 }"><b><CountUp :value="data.grades.failing" /></b><span>未及格</span></div>
            <div class="num ok"><b><CountUp :value="data.grades.excellent" /></b><span>优秀</span></div>
          </div>
        </div>
        <div class="panel p-red">
          <div class="p-title"><i></i>特别提醒<em>ALERTS</em></div>
          <div class="alerts">
            <b class="alert-count" :class="{ zero: !data.alerts.length }"><CountUp :value="data.alerts.length" /></b>
            <ul v-if="data.alerts.length">
              <li v-for="(a, i) in data.alerts.slice(0, 3)" :key="i" :class="a.level">{{ a.text }}</li>
            </ul>
            <span v-else class="all-good">一切正常</span>
          </div>
        </div>
      </section>

      <!-- 主体 -->
      <section class="main">
        <div class="frame grid-wrap">
          <div class="grid-tools">
            <div class="pager">
              <button v-for="p in pageCount" :key="p" :class="{ on: page === p - 1 }" @click="page = p - 1; resetRotate()">{{ rangeLabel(p) }}</button>
              <span v-if="!pageCount" class="pager-empty">暂无数据</span>
            </div>
            <div class="views">
              <button :class="{ on: view === 'students' }" title="学生视图" @click="switchView('students')">学生</button>
              <button :class="{ on: view === 'projects' }" title="项目视图" @click="switchView('projects')">项目</button>
              <button :class="{ on: autoPlay }" title="自动轮播" @click="autoPlay = !autoPlay">{{ autoPlay ? '⟳ 轮播中' : '⟳ 轮播' }}</button>
              <button title="刷新数据" @click="load">↻</button>
            </div>
          </div>

          <div class="cards" @mouseenter="hover = true" @mouseleave="hover = false">
            <template v-if="view === 'students'">
              <div v-for="(c, i) in pageItems" :key="page + '-' + c.userId" class="card enter" :class="{ overdue: c.overdue }" :style="{ animationDelay: (i * 45) + 'ms' }">
                <div class="c-head">
                  <span class="rank" :class="'r' + c.rank">{{ c.rank }}</span>
                  <span class="name" :title="c.studentNo || ''">{{ c.name }}</span>
                  <span class="pct" :class="c.trend">{{ c.progress }}%<i>{{ c.trend === 'up' ? '▲' : c.trend === 'down' ? '▼' : '—' }}</i></span>
                </div>
                <div class="c-row">
                  <span>项目: <b>{{ c.projects }}</b> 个 · 完成 <b>{{ c.completed }}</b></span>
                  <span>分数: <b>{{ c.score ?? '–' }}</b><small v-if="c.score != null"> 分</small></span>
                </div>
                <div class="bar"><div class="fill" :style="{ width: c.progress + '%' }"></div><span class="ticks"></span><i class="pin" :style="{ left: c.progress + '%' }"></i></div>
                <div class="c-foot">
                  <span>7 天活跃 {{ c.activeDays7d }} 天 · {{ c.actions7d }} 次动作</span>
                  <span v-if="c.overdue" class="tag-bad">已过截止</span>
                  <span v-else class="dim">{{ c.lastActiveAt ? '最近 ' + fmtShort(c.lastActiveAt) : '尚无动作' }}</span>
                </div>
              </div>
            </template>
            <template v-else>
              <div v-for="(c, i) in pageItems" :key="page + '-' + c.projectId" class="card enter" :class="{ overdue: c.overdue > 0 }" :style="{ animationDelay: (i * 45) + 'ms' }">
                <div class="c-head">
                  <span class="rank" :class="'r' + c.rank">{{ c.rank }}</span>
                  <span class="name" :title="c.title">{{ c.title }}</span>
                  <span class="pct" :class="c.completionRate >= 50 ? 'up' : 'flat'">{{ c.completionRate }}%<i>完成</i></span>
                </div>
                <div class="c-row">
                  <span>报名: <b>{{ c.enrolled }}</b> 人 · 完成 <b>{{ c.completed }}</b></span>
                  <span>待评: <b :class="{ warn: c.pendingSubmissions > 0 }">{{ c.pendingSubmissions }}</b></span>
                </div>
                <div class="bar"><div class="fill" :style="{ width: c.avgProgress + '%' }"></div><span class="ticks"></span><i class="pin" :style="{ left: c.avgProgress + '%' }"></i></div>
                <div class="c-foot">
                  <span>平均进度 {{ c.avgProgress }}% · {{ c.mentor || '未指派讲师' }}</span>
                  <span v-if="c.overdue" class="tag-bad">{{ c.overdue }} 人过期</span>
                  <span v-else class="dim">{{ c.difficulty }}</span>
                </div>
              </div>
            </template>
            <div v-if="!items.length" class="empty">{{ view === 'students' ? '还没有学生报名项目' : '还没有发布项目' }}</div>
          </div>
        </div>

        <aside class="side">
          <div class="frame chart-box">
            <div class="box-title">近 7 天学习活跃<em>TREND</em></div>
            <div ref="chartEl" class="chart"></div>
          </div>
          <div class="frame gauge-box">
            <div class="box-title">完成率 · 设备 · AI<em>OVERVIEW</em></div>
            <div class="gauge-row">
              <div ref="gaugeEl" class="gauges"></div>
              <div class="kv-grid">
                <div class="kv"><b><CountUp :value="data.equipment.borrowing" /></b><span>借用中</span></div>
                <div class="kv" :class="{ warn: data.equipment.pending > 0 }"><b><CountUp :value="data.equipment.pending" /></b><span>待审批</span></div>
                <div class="kv" :class="{ bad: data.equipment.overdue > 0 }"><b><CountUp :value="data.equipment.overdue" /></b><span>逾期未还</span></div>
                <div class="kv"><b><CountUp :value="data.ai.runsToday" /></b><span>今日 AI</span></div>
                <div class="kv"><b>{{ fmtK(data.ai.tokensMonth) }}</b><span>本月 Token</span></div>
                <div class="kv"><b><CountUp :value="data.users.newThisWeek" /></b><span>本周新用户</span></div>
              </div>
            </div>
          </div>
          <div class="frame feed-box">
            <div class="box-title">最近动态<em>ACTIVITY</em></div>
            <div class="feed-viewport" @mouseenter="feedPaused = true" @mouseleave="feedPaused = false">
              <ul class="feed" :class="{ scroll: data.feed.length > 7, paused: feedPaused }" :style="{ animationDuration: Math.max(12, data.feed.length * 2.2) + 's' }">
                <li v-for="(f, i) in feedLoop" :key="i">
                  <span class="f-time">{{ fmtShort(f.time) }}</span>
                  <span class="f-user">{{ f.user }}</span>
                  <span class="f-text">{{ f.title }}</span>
                </li>
                <li v-if="!data.feed.length" class="dim">暂无学习动作</li>
              </ul>
            </div>
          </div>
        </aside>
      </section>

      <footer class="ft">
        <div class="ticker">
          <span class="tk-label">⚠ 提醒</span>
          <div class="tk-track"><div class="tk-inner">{{ tickerText }}</div></div>
        </div>
        <span class="ft-meta">每 30 秒自动刷新 · 更新于 {{ data.generatedAt ? fmtShort(data.generatedAt) : '–' }} · 仅本站点数据</span>
      </footer>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import * as echarts from 'echarts'
import { adminScreen } from '../../api'
import { loadSiteConfig, siteConfig as site } from '../../utils/siteConfig'
import CountUp from '../../components/CountUp.vue'

const router = useRouter()
const PAGE_SIZE = 16
const empty = () => ({
  siteTitle: '', generatedAt: null,
  users: { total: 0, students: 0, teachers: 0, activeToday: 0, active7d: 0, newThisWeek: 0 },
  projects: { total: 0, published: 0, enrollments: 0, ongoing: 0, completed: 0, completionRate: 0, overdue: 0, buckets: { notStarted: 0, p0_25: 0, p25_50: 0, p50_75: 0, p75_100: 0 } },
  grades: { submitted: 0, pending: 0, returned: 0, graded: 0, avgScore: 0, failing: 0, excellent: 0, passRate: 0 },
  equipment: { kinds: 0, totalUnits: 0, availableUnits: 0, utilization: 0, borrowing: 0, pending: 0, overdue: 0, outOfStock: 0 },
  ai: { runsToday: 0, runsMonth: 0, tokensMonth: 0 },
  alerts: [], students: [], projectCards: [], trend: [], feed: []
})
const data = ref(empty())
const view = ref('students')
const page = ref(0)
const autoPlay = ref(true)
const hover = ref(false)
const feedPaused = ref(false)
const chartEl = ref(null)
const gaugeEl = ref(null)
const bgCanvas = ref(null)
let chart = null
let gauge = null

// ---------- 缩放画布 ----------
const scale = ref(1)
const canvasStyle = computed(() => ({ transform: `translate(-50%, -50%) scale(${scale.value})` }))
const fit = () => { scale.value = Math.min(window.innerWidth / 1920, window.innerHeight / 1080); resizeParticles() }

// ---------- 时钟 ----------
const now = ref(new Date())
const pad = (n) => String(n).padStart(2, '0')
const dateText = computed(() => `${now.value.getFullYear()}-${pad(now.value.getMonth() + 1)}-${pad(now.value.getDate())}`)
const weekText = computed(() => ['星期日', '星期一', '星期二', '星期三', '星期四', '星期五', '星期六'][now.value.getDay()])
const timeText = computed(() => `${pad(now.value.getHours())}:${pad(now.value.getMinutes())}:${pad(now.value.getSeconds())}`)
const fmtShort = (t) => {
  if (!t) return ''
  const d = new Date(String(t).replace(' ', 'T'))
  if (Number.isNaN(d.getTime())) return String(t).slice(5, 16)
  const sameDay = d.toDateString() === now.value.toDateString()
  return sameDay ? `${pad(d.getHours())}:${pad(d.getMinutes())}` : `${d.getMonth() + 1}/${d.getDate()} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}
const fmtK = (n) => (n >= 1000000 ? (n / 1000000).toFixed(1) + 'M' : n >= 1000 ? (n / 1000).toFixed(n >= 100000 ? 0 : 1) + 'k' : String(n || 0))

// ---------- 卡片分页与轮播 ----------
const items = computed(() => (view.value === 'students' ? data.value.students : data.value.projectCards))
const pageCount = computed(() => Math.ceil(items.value.length / PAGE_SIZE))
const pageItems = computed(() => items.value.slice(page.value * PAGE_SIZE, (page.value + 1) * PAGE_SIZE))
const rangeLabel = (p) => `${(p - 1) * PAGE_SIZE + 1}-${Math.min(p * PAGE_SIZE, items.value.length)}`
const switchView = (v) => { view.value = v; page.value = 0; resetRotate() }
let rotateTimer = null
const resetRotate = () => {
  clearInterval(rotateTimer)
  rotateTimer = setInterval(() => {
    if (!autoPlay.value || hover.value || pageCount.value <= 1) return
    page.value = (page.value + 1) % pageCount.value
  }, 8000)
}
watch(pageCount, (n) => { if (page.value >= n) page.value = 0 })

// 动态与提醒跑马灯
const feedLoop = computed(() => (data.value.feed.length > 7 ? [...data.value.feed, ...data.value.feed] : data.value.feed))
const tickerText = computed(() => (data.value.alerts.length
  ? data.value.alerts.map((a) => a.text).join('　　◆　　')
  : '暂无需要处理的提醒,教学秩序正常　　◆　　' + (data.value.users.activeToday || 0) + ' 名同学今天在学习'))

// ---------- 数据 ----------
const load = async () => {
  try {
    data.value = { ...empty(), ...(await adminScreen()) }
    await nextTick()
    renderChart()
    renderGauges()
  } catch (e) { /* 拦截器已提示 */ }
}
const renderChart = () => {
  if (!chartEl.value) return
  if (!chart) chart = echarts.init(chartEl.value)
  const t = data.value.trend || []
  const area = (c1, c2) => new echarts.graphic.LinearGradient(0, 0, 0, 1, [{ offset: 0, color: c1 }, { offset: 1, color: c2 }])
  chart.setOption({
    backgroundColor: 'transparent', animationDuration: 1200,
    grid: { left: 36, right: 16, top: 34, bottom: 26 },
    legend: { top: 2, left: 'center', textStyle: { color: '#9fd6ff', fontSize: 11 }, itemWidth: 12, itemHeight: 8, itemGap: 14 },
    tooltip: { trigger: 'axis', backgroundColor: 'rgba(6,18,41,.95)', borderColor: 'rgba(34,225,255,.4)', textStyle: { color: '#dff6ff', fontSize: 12 } },
    xAxis: { type: 'category', data: t.map((x) => x.label), axisLine: { lineStyle: { color: 'rgba(34,225,255,.3)' } }, axisLabel: { color: '#9fd6ff', fontSize: 11 }, axisTick: { show: false } },
    yAxis: { type: 'value', minInterval: 1, splitLine: { lineStyle: { color: 'rgba(34,225,255,.12)' } }, axisLabel: { color: '#9fd6ff', fontSize: 11 } },
    series: [
      { name: '学习动作', type: 'bar', data: t.map((x) => x.actions), barWidth: 14, itemStyle: { borderRadius: [3, 3, 0, 0], color: area('#22e1ff', 'rgba(34,225,255,.1)') } },
      { name: '进度与成果', type: 'line', smooth: true, data: t.map((x) => x.tasks), symbolSize: 6, lineStyle: { width: 2, color: '#35e37a', shadowColor: 'rgba(53,227,122,.6)', shadowBlur: 8 }, itemStyle: { color: '#35e37a' }, areaStyle: { color: area('rgba(53,227,122,.35)', 'rgba(53,227,122,0)') } },
      { name: '活跃人数', type: 'line', smooth: true, data: t.map((x) => x.activeUsers), symbolSize: 6, lineStyle: { width: 2, color: '#f7b731', shadowColor: 'rgba(247,183,49,.6)', shadowBlur: 8 }, itemStyle: { color: '#f7b731' } }
    ]
  })
}
const gaugeSeries = (name, value, center, color) => ({
  type: 'gauge', center, radius: '86%', startAngle: 220, endAngle: -40, min: 0, max: 100,
  progress: { show: true, width: 10, roundCap: true, itemStyle: { color, shadowColor: color, shadowBlur: 12 } },
  axisLine: { lineStyle: { width: 10, color: [[1, 'rgba(34,225,255,.12)']] } },
  axisTick: { show: false }, splitLine: { show: false }, axisLabel: { show: false }, pointer: { show: false },
  title: { show: true, offsetCenter: [0, '78%'], color: '#8fb6d6', fontSize: 12 },
  detail: { valueAnimation: true, offsetCenter: [0, '8%'], formatter: '{value}%', color: '#fff', fontSize: 24, fontWeight: 700, fontFamily: 'Bahnschrift, DIN Alternate, Segoe UI' },
  data: [{ value, name }]
})
const renderGauges = () => {
  if (!gaugeEl.value) return
  if (!gauge) gauge = echarts.init(gaugeEl.value)
  gauge.setOption({
    backgroundColor: 'transparent', animationDuration: 1400,
    series: [
      gaugeSeries('项目完成率', data.value.projects.completionRate || 0, ['26%', '52%'], '#35e37a'),
      gaugeSeries('设备利用率', data.value.equipment.utilization || 0, ['74%', '52%'], '#22e1ff')
    ]
  })
}

// ---------- 粒子背景 ----------
let particles = []
let particleRaf = null
const resizeParticles = () => {
  const c = bgCanvas.value
  if (!c) return
  c.width = window.innerWidth
  c.height = window.innerHeight
}
const startParticles = () => {
  const c = bgCanvas.value
  if (!c) return
  resizeParticles()
  const ctx = c.getContext('2d')
  const count = Math.round((c.width * c.height) / 22000)
  particles = Array.from({ length: Math.min(120, Math.max(40, count)) }, () => ({
    x: Math.random() * c.width, y: Math.random() * c.height,
    vx: (Math.random() - 0.5) * 0.35, vy: (Math.random() - 0.5) * 0.35, r: Math.random() * 1.6 + 0.6
  }))
  const tick = () => {
    ctx.clearRect(0, 0, c.width, c.height)
    for (const p of particles) {
      p.x += p.vx; p.y += p.vy
      if (p.x < 0 || p.x > c.width) p.vx *= -1
      if (p.y < 0 || p.y > c.height) p.vy *= -1
    }
    ctx.lineWidth = 1
    for (let i = 0; i < particles.length; i++) {
      for (let j = i + 1; j < particles.length; j++) {
        const a = particles[i], b = particles[j]
        const dx = a.x - b.x, dy = a.y - b.y
        const d2 = dx * dx + dy * dy
        if (d2 < 130 * 130) {
          ctx.strokeStyle = `rgba(34,225,255,${(1 - Math.sqrt(d2) / 130) * 0.22})`
          ctx.beginPath(); ctx.moveTo(a.x, a.y); ctx.lineTo(b.x, b.y); ctx.stroke()
        }
      }
    }
    for (const p of particles) {
      ctx.fillStyle = 'rgba(120,235,255,.75)'
      ctx.beginPath(); ctx.arc(p.x, p.y, p.r, 0, Math.PI * 2); ctx.fill()
    }
    particleRaf = requestAnimationFrame(tick)
  }
  tick()
}

// ---------- 其他 ----------
const back = () => router.push('/admin/dashboard')
const toggleFullscreen = () => {
  if (document.fullscreenElement) document.exitFullscreen()
  else document.documentElement.requestFullscreen?.()
}

let clockTimer = null
let dataTimer = null
onMounted(async () => {
  fit()
  window.addEventListener('resize', fit)
  clockTimer = setInterval(() => { now.value = new Date() }, 1000)
  dataTimer = setInterval(load, 30000)
  resetRotate()
  startParticles()
  loadSiteConfig().catch(() => {})
  await load()
})
onBeforeUnmount(() => {
  window.removeEventListener('resize', fit)
  clearInterval(clockTimer)
  clearInterval(dataTimer)
  clearInterval(rotateTimer)
  cancelAnimationFrame(particleRaf)
  chart?.dispose()
  gauge?.dispose()
})
</script>

<style scoped>
.screen-root {
  --bg: #04101f; --cy: #22e1ff; --cy-dim: rgba(34, 225, 255, .35); --cy-soft: rgba(34, 225, 255, .12);
  --grn: #35e37a; --amb: #f7b731; --red: #ff5b6e; --pur: #b97cff; --txt: #dff6ff; --txt-dim: #8fb6d6;
  position: fixed; inset: 0; overflow: hidden; color: var(--txt);
  background: radial-gradient(ellipse at 20% 0%, #0d2a5c 0%, transparent 55%), radial-gradient(ellipse at 80% 100%, #0b2b52 0%, transparent 50%), var(--bg);
  font-family: 'PingFang SC', 'Microsoft YaHei', 'Segoe UI', sans-serif;
}
.screen-root::before { content: ''; position: absolute; inset: 0; background: repeating-linear-gradient(0deg, rgba(34, 225, 255, .035) 0 1px, transparent 1px 44px), repeating-linear-gradient(90deg, rgba(34, 225, 255, .035) 0 1px, transparent 1px 44px); mask-image: radial-gradient(ellipse at center, #000 30%, transparent 85%); pointer-events: none; }
.bg-particles { position: absolute; inset: 0; width: 100%; height: 100%; pointer-events: none; opacity: .9; }
.scan { position: absolute; left: 0; right: 0; height: 160px; pointer-events: none; background: linear-gradient(180deg, transparent, rgba(34, 225, 255, .07) 60%, rgba(34, 225, 255, .28) 99%, transparent); animation: scan 9s linear infinite; }
@keyframes scan { 0% { top: -160px; } 100% { top: 100%; } }
.canvas { position: absolute; left: 50%; top: 50%; width: 1920px; height: 1080px; transform-origin: center; display: flex; flex-direction: column; padding: 16px 28px 10px; box-sizing: border-box; gap: 14px; }
b, .num b, .kv b, .alert-count, .time, .pct { font-family: 'Bahnschrift', 'DIN Alternate', 'Segoe UI', 'Arial Narrow', sans-serif; font-variant-numeric: tabular-nums; }

/* 头部 */
.hd { display: grid; grid-template-columns: 1fr auto 1fr; align-items: center; height: 84px; }
.hd-left, .hd-right { display: flex; align-items: center; gap: 16px; font-size: 18px; color: var(--txt-dim); }
.hd-right { justify-content: flex-end; }
.live { display: inline-flex; align-items: center; gap: 6px; font-size: 12px; letter-spacing: 2px; color: var(--red); border: 1px solid rgba(255, 91, 110, .5); padding: 2px 8px; border-radius: 3px; }
.live i { width: 7px; height: 7px; border-radius: 50%; background: var(--red); box-shadow: 0 0 8px var(--red); animation: blink 1.2s infinite; }
@keyframes blink { 50% { opacity: .2; } }
.back, .icon-btn { width: 40px; height: 40px; border-radius: 50%; border: 1px solid var(--cy-dim); background: var(--cy-soft); color: var(--cy); font-size: 18px; cursor: pointer; display: grid; place-items: center; transition: box-shadow .2s; }
.back:hover, .icon-btn:hover { background: rgba(34, 225, 255, .25); box-shadow: 0 0 14px rgba(34, 225, 255, .6); }
.time { font-size: 28px; color: #fff; letter-spacing: 1px; text-shadow: 0 0 12px rgba(34, 225, 255, .7); }
.hd-title { display: flex; align-items: center; gap: 14px; }
.title-wrap { text-align: center; position: relative; padding: 4px 36px 6px; border-bottom: 2px solid var(--cy); }
.title-wrap::before, .title-wrap::after { content: ''; position: absolute; bottom: -2px; width: 12px; height: 12px; border: 2px solid var(--cy); border-top: 0; }
.title-wrap::before { left: -8px; border-right: 0; } .title-wrap::after { right: -8px; border-left: 0; }
.hd-title h1 { margin: 0; font-size: 34px; letter-spacing: 6px; font-weight: 800; white-space: nowrap; background: linear-gradient(90deg, #dff6ff, #7be9ff 40%, #ffffff 60%, #7be9ff); background-size: 200% 100%; -webkit-background-clip: text; background-clip: text; color: transparent; animation: shine 6s linear infinite; filter: drop-shadow(0 0 14px rgba(34, 225, 255, .7)); }
@keyframes shine { 0% { background-position: 0 0; } 100% { background-position: 200% 0; } }
.sub { margin-top: 2px; font-size: 11px; letter-spacing: 5px; color: rgba(34, 225, 255, .75); }
.deco { width: 220px; height: 3px; background: linear-gradient(90deg, transparent, var(--cy)); position: relative; overflow: visible; }
.deco.r { background: linear-gradient(90deg, var(--cy), transparent); }
.deco::after { content: ''; position: absolute; top: 8px; height: 1px; width: 60%; background: var(--cy-dim); }
.deco.l::after { right: 0; } .deco.r::after { left: 0; }
.ring { position: relative; width: 44px; height: 44px; border-radius: 50%; border: 1px dashed rgba(34, 225, 255, .7); animation: spin 14s linear infinite; }
.ring i { position: absolute; inset: 7px; border-radius: 50%; border: 2px solid transparent; border-top-color: var(--cy); border-right-color: var(--cy); animation: spin 3s linear infinite reverse; }
.ring::after { content: ''; position: absolute; inset: 17px; border-radius: 50%; background: var(--cy); box-shadow: 0 0 10px var(--cy); }
@keyframes spin { to { transform: rotate(360deg); } }

/* 顶部统计 */
.stats { display: grid; grid-template-columns: 1.35fr 2.1fr 1.35fr 1.1fr; gap: 14px; height: 134px; }
.panel { position: relative; border: 1px solid var(--cy-dim); background: linear-gradient(180deg, rgba(10, 36, 78, .85), rgba(6, 20, 46, .92)); border-radius: 6px; padding: 12px 18px 10px; box-shadow: inset 0 0 30px rgba(34, 225, 255, .06), 0 0 0 1px rgba(0, 0, 0, .3); overflow: hidden; }
.panel::before { content: ''; position: absolute; left: 0; top: 0; right: 0; height: 3px; background: linear-gradient(90deg, transparent 0%, var(--accent, var(--cy)) 20%, #fff 50%, var(--accent, var(--cy)) 80%, transparent 100%); background-size: 200% 100%; animation: runlight 4s linear infinite; }
@keyframes runlight { 0% { background-position: 200% 0; } 100% { background-position: -200% 0; } }
.p-blue { --accent: #3b9dff; } .p-amber { --accent: var(--amb); } .p-purple { --accent: var(--pur); }
.p-red { --accent: var(--red); background: linear-gradient(180deg, rgba(74, 14, 34, .8), rgba(30, 8, 24, .92)); border-color: rgba(255, 91, 110, .45); }
.p-title { display: flex; align-items: center; gap: 8px; font-size: 17px; font-weight: 700; color: #fff; }
.p-title i { width: 8px; height: 8px; border-radius: 2px; background: var(--accent, var(--cy)); box-shadow: 0 0 8px var(--accent, var(--cy)); }
.p-title em { font-style: normal; font-size: 10px; letter-spacing: 2px; color: rgba(143, 182, 214, .6); font-weight: 400; }
.p-title small { font-size: 12px; color: var(--txt-dim); font-weight: 400; margin-left: 6px; }
.nums { display: flex; justify-content: space-around; align-items: flex-end; margin-top: 10px; gap: 6px; }
.num { text-align: center; flex: 1; min-width: 0; }
.num b { display: block; font-size: 38px; line-height: 1; color: var(--cy); text-shadow: 0 0 14px rgba(34, 225, 255, .6); }
.num > span { display: block; margin-top: 6px; font-size: 13px; color: var(--txt-dim); white-space: nowrap; }
.num.hi b { color: #fff; } .num.ok b { color: var(--grn); text-shadow: 0 0 14px rgba(53, 227, 122, .6); }
.num.bad b { color: var(--red); text-shadow: 0 0 14px rgba(255, 91, 110, .6); animation: pulse 1.6s ease-in-out infinite; }
.num.warn b { color: var(--amb); text-shadow: 0 0 14px rgba(247, 183, 49, .6); }
@keyframes pulse { 50% { opacity: .55; } }
.alerts { display: flex; align-items: center; gap: 16px; margin-top: 8px; }
.alert-count { font-size: 52px; line-height: 1; color: var(--red); text-shadow: 0 0 16px rgba(255, 91, 110, .8); min-width: 56px; text-align: center; animation: pulse 1.6s ease-in-out infinite; }
.alert-count.zero { color: var(--grn); text-shadow: 0 0 16px rgba(53, 227, 122, .6); animation: none; }
.alerts ul { margin: 0; padding: 0; list-style: none; font-size: 13px; line-height: 1.7; }
.alerts li::before { content: '● '; font-size: 9px; vertical-align: 2px; }
.alerts li.danger { color: #ff8a97; } .alerts li.warning { color: #ffd37a; } .alerts li.info { color: var(--txt-dim); }
.all-good { color: var(--grn); font-size: 15px; }

/* 主体 */
.main { flex: 1; display: grid; grid-template-columns: 1fr 440px; gap: 14px; min-height: 0; }
.frame { position: relative; border: 1px solid var(--cy-dim); background: rgba(8, 26, 58, .55); border-radius: 6px; box-shadow: inset 0 0 40px rgba(34, 225, 255, .05); backdrop-filter: blur(2px); }
.frame::before, .frame::after { content: ''; position: absolute; width: 16px; height: 16px; border: 2px solid var(--cy); filter: drop-shadow(0 0 4px var(--cy)); }
.frame::before { left: -1px; top: -1px; border-right: 0; border-bottom: 0; }
.frame::after { right: -1px; bottom: -1px; border-left: 0; border-top: 0; }
.grid-wrap { display: flex; flex-direction: column; padding: 12px 16px 14px; min-height: 0; }
.grid-tools { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
.pager, .views { display: flex; gap: 8px; align-items: center; }
.pager button, .views button { border: 1px solid var(--cy-dim); background: rgba(34, 225, 255, .06); color: var(--txt-dim); font-size: 13px; padding: 4px 12px; border-radius: 4px; cursor: pointer; transition: all .2s; }
.pager button.on, .views button.on { background: var(--cy); color: #04213f; font-weight: 700; border-color: var(--cy); box-shadow: 0 0 12px rgba(34, 225, 255, .6); }
.pager button:hover, .views button:hover { color: #fff; border-color: var(--cy); }
.pager-empty { font-size: 13px; color: var(--txt-dim); }
.cards { flex: 1; display: grid; grid-template-columns: repeat(4, 1fr); grid-template-rows: repeat(4, 1fr); gap: 14px; min-height: 0; position: relative; }
.card { position: relative; border: 1px solid rgba(34, 225, 255, .3); background: linear-gradient(180deg, rgba(14, 44, 92, .8), rgba(8, 26, 58, .92)); border-radius: 8px; padding: 12px 16px 10px; display: flex; flex-direction: column; justify-content: space-between; overflow: hidden; transition: transform .2s, box-shadow .2s, border-color .2s; }
.card:hover { transform: translateY(-3px); border-color: var(--cy); box-shadow: 0 8px 24px rgba(34, 225, 255, .25); }
.card::after { content: ''; position: absolute; right: -1px; top: -1px; width: 26px; height: 26px; background: linear-gradient(225deg, var(--bg) 50%, rgba(34, 225, 255, .6) 50%, rgba(34, 225, 255, .6) calc(50% + 1px), transparent calc(50% + 1px)); }
.card.enter { animation: enter .5s cubic-bezier(.2, .8, .2, 1) both; }
@keyframes enter { from { opacity: 0; transform: translateY(16px) scale(.97); } to { opacity: 1; transform: none; } }
.card.overdue { border-color: rgba(255, 91, 110, .55); background: linear-gradient(180deg, rgba(74, 20, 40, .8), rgba(30, 10, 26, .92)); }
.c-head { display: flex; align-items: center; gap: 10px; }
.rank { width: 26px; height: 26px; border-radius: 50%; border: 1px solid var(--cy-dim); color: var(--cy); font-size: 12px; font-weight: 700; display: grid; place-items: center; flex-shrink: 0; background: rgba(34, 225, 255, .08); }
.rank.r1 { background: linear-gradient(135deg, #ffe27a, #d19a00); color: #3b2a00; border-color: #ffd54a; box-shadow: 0 0 12px rgba(255, 213, 74, .8); }
.rank.r2 { background: linear-gradient(135deg, #f1f5f9, #94a3b8); color: #1e293b; border-color: #e2e8f0; box-shadow: 0 0 10px rgba(226, 232, 240, .6); }
.rank.r3 { background: linear-gradient(135deg, #f0b27a, #b45309); color: #3b1a00; border-color: #f59e0b; box-shadow: 0 0 10px rgba(245, 158, 11, .6); }
.name { flex: 1; font-size: 17px; font-weight: 700; color: #fff; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.pct { font-size: 20px; font-weight: 800; display: flex; align-items: baseline; gap: 4px; }
.pct i { font-style: normal; font-size: 11px; }
.pct.up { color: var(--grn); text-shadow: 0 0 10px rgba(53, 227, 122, .6); } .pct.down { color: var(--red); text-shadow: 0 0 10px rgba(255, 91, 110, .6); } .pct.flat { color: var(--amb); }
.c-row { display: flex; justify-content: space-between; font-size: 13px; color: var(--txt-dim); margin-top: 8px; }
.c-row b { color: #fff; font-size: 17px; margin: 0 2px; }
.c-row b.warn { color: var(--amb); }
.bar { position: relative; height: 10px; border-radius: 5px; background: rgba(0, 0, 0, .45); margin-top: 10px; overflow: visible; }
.bar .fill { position: relative; height: 100%; border-radius: 5px; background: linear-gradient(90deg, #35e37a, #b7f34d 70%, #f7b731); box-shadow: 0 0 10px rgba(53, 227, 122, .5); transition: width .8s ease; overflow: hidden; }
.bar .fill::after { content: ''; position: absolute; top: 0; bottom: 0; width: 40px; background: linear-gradient(90deg, transparent, rgba(255, 255, 255, .8), transparent); animation: shimmer 2.4s linear infinite; }
@keyframes shimmer { from { left: -40px; } to { left: 100%; } }
.bar .ticks { position: absolute; inset: 0; background: repeating-linear-gradient(90deg, transparent 0 calc(10% - 2px), var(--bg) calc(10% - 2px) 10%); border-radius: 5px; pointer-events: none; }
.bar .pin { position: absolute; top: 12px; width: 8px; height: 8px; margin-left: -4px; border-radius: 50% 50% 50% 0; transform: rotate(-135deg); background: #fff; box-shadow: 0 0 6px #fff; transition: left .8s ease; }
.c-foot { display: flex; justify-content: space-between; font-size: 12px; color: var(--txt-dim); margin-top: 14px; }
.dim { color: var(--txt-dim); }
.tag-bad { color: var(--red); font-weight: 700; }
.empty { position: absolute; inset: 0; display: grid; place-items: center; font-size: 18px; color: var(--txt-dim); }

/* 右侧 */
.side { display: grid; grid-template-rows: 290px 232px 1fr; gap: 14px; min-height: 0; }
.box-title { font-size: 15px; font-weight: 700; color: #fff; padding: 10px 16px 0; display: flex; align-items: center; gap: 8px; }
.box-title::before { content: ''; width: 4px; height: 14px; background: var(--cy); box-shadow: 0 0 8px var(--cy); }
.box-title em { font-style: normal; font-size: 10px; letter-spacing: 2px; color: rgba(143, 182, 214, .6); font-weight: 400; margin-left: auto; padding-right: 30px; background: repeating-linear-gradient(-45deg, rgba(34, 225, 255, .35) 0 2px, transparent 2px 6px) right center / 24px 10px no-repeat; }
.chart-box { display: flex; flex-direction: column; }
.chart { flex: 1; min-height: 0; margin: 4px 8px 6px; }
.gauge-row { display: grid; grid-template-columns: 1fr 190px; height: 186px; }
.gauges { width: 100%; height: 100%; }
.kv-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 6px 4px; padding: 10px 12px 8px 0; align-content: center; }
.kv { text-align: center; }
.kv b { display: block; font-size: 24px; line-height: 1; color: var(--cy); }
.kv > span { display: block; margin-top: 4px; font-size: 11px; color: var(--txt-dim); }
.kv.warn b { color: var(--amb); } .kv.bad b { color: var(--red); animation: pulse 1.6s infinite; }
.feed-box { min-height: 0; display: flex; flex-direction: column; }
.feed-viewport { flex: 1; min-height: 0; overflow: hidden; margin: 6px 0 8px; mask-image: linear-gradient(180deg, transparent, #000 8%, #000 92%, transparent); }
.feed { list-style: none; margin: 0; padding: 0 16px; }
.feed.scroll { animation: feedscroll linear infinite; }
.feed.paused { animation-play-state: paused; }
@keyframes feedscroll { from { transform: translateY(0); } to { transform: translateY(-50%); } }
.feed li { display: flex; gap: 10px; font-size: 13px; line-height: 1.6; padding: 5px 0; border-bottom: 1px dashed rgba(34, 225, 255, .12); }
.f-time { color: var(--cy); width: 74px; flex-shrink: 0; font-variant-numeric: tabular-nums; }
.f-user { color: #fff; width: 64px; flex-shrink: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.f-text { color: var(--txt-dim); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

/* 底部跑马灯 */
.ft { display: flex; align-items: center; gap: 20px; height: 30px; font-size: 12px; color: rgba(143, 182, 214, .8); }
.ticker { flex: 1; display: flex; align-items: center; gap: 12px; min-width: 0; border: 1px solid rgba(255, 91, 110, .3); border-radius: 4px; background: rgba(74, 14, 34, .35); height: 28px; padding: 0 12px; }
.tk-label { color: var(--red); font-weight: 700; white-space: nowrap; }
.tk-track { flex: 1; overflow: hidden; min-width: 0; }
.tk-inner { display: inline-block; white-space: nowrap; color: #ffd0d6; padding-left: 100%; animation: ticker 28s linear infinite; }
@keyframes ticker { to { transform: translateX(-100%); } }
.ft-meta { white-space: nowrap; }
</style>
