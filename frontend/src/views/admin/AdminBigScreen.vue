<template>
  <!-- 数据大屏:1920×1080 画布等比缩放;4 个分页自动轮播;数据按当前站点隔离 -->
  <div class="screen-root" :class="{ light: theme === 'light' }" @mousemove="touch" @keydown.left="prevPage" @keydown.right="nextPage" tabindex="0">
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
            <div class="title-row">
              <img v-if="data.site?.logoUrl" :src="data.site.logoUrl" class="site-logo" alt="" />
              <h1>{{ screenTitle }}</h1>
            </div>
            <div class="sub">{{ data.site?.screenSubtitle || 'REAL-TIME TEACHING DATA CENTER' }}</div>
          </div>
          <span class="deco r"></span>
          <span class="ring r"><i></i></span>
        </div>
        <div class="hd-right">
          <div class="pages">
            <button v-for="(p, i) in PAGES" :key="p.key" :class="{ on: pageIdx === i }" :title="p.name" @click="goPage(i)">{{ p.name }}</button>
          </div>
          <span class="week">{{ weekText }}</span>
          <span class="time">{{ timeText }}</span>
          <button class="icon-btn" :title="theme === 'dark' ? '切换浅色主题' : '切换深色主题'" @click="toggleTheme">{{ theme === 'dark' ? '☀' : '☾' }}</button>
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
        <div class="panel p-amber">
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

      <!-- 累计 · 同比 · AI 解读 -->
      <section class="strip">
        <div class="cum" v-for="c in cumulativeItems" :key="c.label">
          <span class="cum-label">{{ c.label }}</span>
          <b class="big"><CountUp :value="c.value" /></b>
          <span v-if="c.wow" class="wow" :class="wowClass(c.wow)">{{ wowText(c.wow) }}</span>
        </div>
        <div class="brief">
          <span class="brief-icon">✦</span>
          <div class="brief-text">
            <template v-if="brief">
              <b>{{ brief.headline }}</b>
              <span v-if="brief.highlights?.length" class="brief-more">{{ brief.highlights[briefIdx % brief.highlights.length] }}</span>
            </template>
            <span v-else class="dim">{{ briefLoading ? 'AI 正在解读本周数据…' : '本周数据解读生成中' }}</span>
          </div>
        </div>
      </section>

      <!-- 分页内容 -->
      <ScreenOverview v-if="pageIdx === 0" :key="'p0-' + dataVersion" :data="data" :palette="palette" :fmt-short="fmtShort" />
      <ScreenWorks v-else-if="pageIdx === 1" :data="data" />
      <ScreenRanks v-else-if="pageIdx === 2" :data="data" :palette="palette" />
      <ScreenDevices v-else :data="data" :palette="palette" />

      <footer class="ft">
        <div class="ticker">
          <span class="tk-label">⚠ 提醒</span>
          <div class="tk-track"><div class="tk-inner">{{ tickerText }}</div></div>
        </div>
        <div class="page-dots"><i v-for="(p, i) in PAGES" :key="p.key" :class="{ on: pageIdx === i }" @click="goPage(i)"></i><span class="auto" :class="{ off: !autoPlay }" @click="autoPlay = !autoPlay">{{ autoPlay ? '轮播中' : '已暂停' }}</span></div>
        <span class="ft-meta">每 30 秒自动刷新 · 更新于 {{ data.generatedAt ? fmtShort(data.generatedAt) : '–' }} · 仅本站点数据</span>
      </footer>
    </div>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { adminScreen } from '../../api'
import { runJsonSkillCached } from '../../api/aiJson'
import { loadSiteConfig, siteConfig as site } from '../../utils/siteConfig'
import CountUp from '../../components/CountUp.vue'
import ScreenOverview from '../../components/screen/ScreenOverview.vue'
import ScreenWorks from '../../components/screen/ScreenWorks.vue'
import ScreenRanks from '../../components/screen/ScreenRanks.vue'
import ScreenDevices from '../../components/screen/ScreenDevices.vue'
import '../../styles/screen.css'

const router = useRouter()
const PAGES = [{ key: 'overview', name: '总览' }, { key: 'works', name: '成果墙' }, { key: 'ranks', name: '班级与教师' }, { key: 'devices', name: '设备与 AI' }]
const empty = () => ({
  siteTitle: '', generatedAt: null, site: null, demo: false, kpi: [],
  users: { total: 0, students: 0, teachers: 0, activeToday: 0, active7d: 0, newThisWeek: 0 },
  projects: { total: 0, published: 0, enrollments: 0, ongoing: 0, completed: 0, completionRate: 0, overdue: 0, buckets: { notStarted: 0, p0_25: 0, p25_50: 0, p50_75: 0, p75_100: 0 } },
  grades: { submitted: 0, pending: 0, returned: 0, graded: 0, avgScore: 0, failing: 0, excellent: 0, passRate: 0 },
  equipment: { kinds: 0, totalUnits: 0, availableUnits: 0, utilization: 0, borrowing: 0, pending: 0, overdue: 0, outOfStock: 0 },
  ai: { runsToday: 0, runsMonth: 0, tokensMonth: 0 }, kicad: null,
  cumulative: { students: 0, completed: 0, actions: 0, submissions: 0, runningDays: 0, aiRunsMonth: 0 },
  wow: {}, works: [], classes: [], majors: [], skills: [], hours: [], equipmentTop: [], equipmentAssets: null, teachers: [],
  alerts: [], students: [], projectCards: [], trend: [], feed: []
})
const data = ref(empty())
const dataVersion = ref(0)
const pageIdx = ref(0)
const autoPlay = ref(true)
const theme = ref(localStorage.getItem('screen-theme') || 'dark')
const bgCanvas = ref(null)

// ---------- 主题与图表调色 ----------
const palette = computed(() => (theme.value === 'light'
  ? { text: '#0f172a', textDim: '#64748b', grid: 'rgba(11,116,209,.12)', border: 'rgba(11,116,209,.35)', chip: 'rgba(11,116,209,.08)', tooltipBg: 'rgba(255,255,255,.96)', pieBorder: '#ffffff' }
  : { text: '#dff6ff', textDim: '#9fd6ff', grid: 'rgba(34,225,255,.12)', border: 'rgba(34,225,255,.35)', chip: 'rgba(34,225,255,.12)', tooltipBg: 'rgba(6,18,41,.95)', pieBorder: '#061229' }))
const toggleTheme = () => { theme.value = theme.value === 'dark' ? 'light' : 'dark'; localStorage.setItem('screen-theme', theme.value) }

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
const screenTitle = computed(() => data.value.site?.screenTitle || `${data.value.siteTitle || site.title} · 实时数据看板`)

// ---------- 累计与同比 ----------
const cumulativeItems = computed(() => {
  const c = data.value.cumulative || {}
  const w = data.value.wow || {}
  return [
    { label: '累计培养学生', value: c.students || 0, wow: w.newUsers },
    { label: '累计完成项目', value: c.completed || 0, wow: w.completed },
    { label: '累计学习动作', value: c.actions || 0, wow: w.actions },
    { label: '本周活跃学生', value: data.value.users.active7d || 0, wow: w.activeUsers },
    { label: '平台运行天数', value: c.runningDays || 0 }
  ]
})
const wowClass = (w) => (w.pct == null ? 'flat' : w.pct > 0 ? 'up' : w.pct < 0 ? 'down' : 'flat')
const wowText = (w) => (w.pct == null ? (w.now > 0 ? '本周 +' + w.now : '—') : (w.pct > 0 ? '▲ ' : w.pct < 0 ? '▼ ' : '') + Math.abs(w.pct) + '% 周环比')

// ---------- AI 一句话解读 ----------
const brief = ref(null)
const briefLoading = ref(false)
const briefIdx = ref(0)
const loadBrief = async () => {
  const d = data.value
  const w = d.wow || {}
  const pct = (x) => (x && x.pct != null ? `${x.pct > 0 ? '+' : ''}${x.pct}%` : '无上周数据')
  const summary = [
    `学生 ${d.users.students} 人,教师 ${d.users.teachers} 人,本周活跃 ${d.users.active7d} 人(周环比 ${pct(w.activeUsers)}),今日活跃 ${d.users.activeToday} 人`,
    `报名 ${d.projects.enrollments} 个,已完成 ${d.projects.completed} 个,完成率 ${d.projects.completionRate}%,已过截止 ${d.projects.overdue} 个`,
    `本周学习动作 ${w.actions?.now ?? 0} 次(周环比 ${pct(w.actions)}),本周完成评审 ${w.completed?.now ?? 0} 份(周环比 ${pct(w.completed)})`,
    `待评审成果 ${d.grades.pending} 份,平均分 ${d.grades.avgScore},优秀 ${d.grades.excellent} 份,未及格 ${d.grades.failing} 份`,
    `设备利用率 ${d.equipment.utilization}%,借用中 ${d.equipment.borrowing},待审批 ${d.equipment.pending},逾期 ${d.equipment.overdue}`,
    `AI 辅导今日 ${d.ai.runsToday} 次,本月 ${d.ai.runsMonth} 次`,
    d.alerts.length ? `提醒:${d.alerts.map((a) => a.text).join(';')}` : '无待处理提醒'
  ].join('\n')
  briefLoading.value = true
  try {
    const key = `screen-brief:${d.siteTitle}:${new Date().toISOString().slice(0, 10)}:${d.demo ? 'demo' : 'real'}`
    const r = await runJsonSkillCached(key, 6 * 3600 * 1000, 'screen-brief', { summary })
    brief.value = r.data
  } catch (e) {
    brief.value = null
  } finally {
    briefLoading.value = false
  }
}

// ---------- 分页轮播 ----------
let lastTouch = 0
const touch = () => { lastTouch = Date.now() }
const goPage = (i) => { pageIdx.value = (i + PAGES.length) % PAGES.length; touch() }
const nextPage = () => goPage(pageIdx.value + 1)
const prevPage = () => goPage(pageIdx.value - 1)
const tickerText = computed(() => {
  const parts = data.value.alerts.length
    ? data.value.alerts.map((a) => a.text)
    : ['暂无需要处理的提醒,教学秩序正常', (data.value.users.activeToday || 0) + ' 名同学今天在学习']
  for (const h of brief.value?.highlights || []) parts.push('AI 观察:' + h)
  if (brief.value?.suggestion) parts.push('AI 建议:' + brief.value.suggestion)
  return parts.join('　　◆　　')
})

// ---------- 数据 ----------
const load = async () => {
  try {
    const hadBrief = !!brief.value
    data.value = { ...empty(), ...(await adminScreen()) }
    dataVersion.value++
    if (!hadBrief) loadBrief()
  } catch (e) { /* 拦截器已提示 */ }
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
    x: Math.random() * c.width, y: Math.random() * c.height, vx: (Math.random() - 0.5) * 0.35, vy: (Math.random() - 0.5) * 0.35, r: Math.random() * 1.6 + 0.6
  }))
  const tick = () => {
    const light = theme.value === 'light'
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
          ctx.strokeStyle = light ? `rgba(11,116,209,${(1 - Math.sqrt(d2) / 130) * 0.25})` : `rgba(34,225,255,${(1 - Math.sqrt(d2) / 130) * 0.22})`
          ctx.beginPath(); ctx.moveTo(a.x, a.y); ctx.lineTo(b.x, b.y); ctx.stroke()
        }
      }
    }
    ctx.fillStyle = light ? 'rgba(11,116,209,.6)' : 'rgba(120,235,255,.75)'
    for (const p of particles) { ctx.beginPath(); ctx.arc(p.x, p.y, p.r, 0, Math.PI * 2); ctx.fill() }
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
let pageTimer = null
let briefTimer = null
onMounted(async () => {
  fit()
  window.addEventListener('resize', fit)
  clockTimer = setInterval(() => { now.value = new Date() }, 1000)
  dataTimer = setInterval(load, 30000)
  // 20 秒翻页;有人在操作(鼠标移动)时等 60 秒再翻
  pageTimer = setInterval(() => { if (autoPlay.value && Date.now() - lastTouch > 60000) pageIdx.value = (pageIdx.value + 1) % PAGES.length }, 20000)
  briefTimer = setInterval(() => { briefIdx.value++ }, 10000)
  startParticles()
  loadSiteConfig().catch(() => {})
  await load()
})
onBeforeUnmount(() => {
  window.removeEventListener('resize', fit)
  clearInterval(clockTimer); clearInterval(dataTimer); clearInterval(pageTimer); clearInterval(briefTimer)
  cancelAnimationFrame(particleRaf)
})
</script>

<style scoped>
.screen-root:focus { outline: none; }

/* 头部 */
.hd { display: grid; grid-template-columns: auto 1fr auto; align-items: center; height: 84px; }
.hd-left, .hd-right { display: flex; align-items: center; gap: 14px; font-size: 18px; color: var(--txt-dim); white-space: nowrap; }
.hd-right { justify-content: flex-end; }
.hd-title { justify-self: center; min-width: 0; }
.live { display: inline-flex; align-items: center; gap: 6px; font-size: 12px; letter-spacing: 2px; color: var(--red); border: 1px solid rgba(255, 91, 110, .5); padding: 2px 8px; border-radius: 3px; }
.live i { width: 7px; height: 7px; border-radius: 50%; background: var(--red); box-shadow: 0 0 8px var(--red); animation: blink 1.2s infinite; }
@keyframes blink { 50% { opacity: .2; } }
.back, .icon-btn { width: 38px; height: 38px; border-radius: 50%; border: 1px solid var(--cy-dim); background: var(--cy-soft); color: var(--cy); font-size: 17px; cursor: pointer; display: grid; place-items: center; transition: box-shadow .2s; }
.back:hover, .icon-btn:hover { box-shadow: 0 0 14px var(--cy-dim); }
.time { font-size: 26px; color: var(--ink); letter-spacing: 1px; text-shadow: 0 0 12px var(--cy-dim); }
.pages { display: flex; gap: 4px; margin-right: 6px; }
.pages button { border: 1px solid var(--cy-dim); background: var(--chip); color: var(--txt-dim); font-size: 12px; padding: 4px 10px; border-radius: 4px; cursor: pointer; white-space: nowrap; }
.pages button.on { background: var(--cy); color: #04213f; font-weight: 700; box-shadow: 0 0 10px var(--cy-dim); }
.hd-title { display: flex; align-items: center; gap: 14px; }
.title-wrap { text-align: center; position: relative; padding: 4px 36px 6px; border-bottom: 2px solid var(--cy); }
.title-wrap::before, .title-wrap::after { content: ''; position: absolute; bottom: -2px; width: 12px; height: 12px; border: 2px solid var(--cy); border-top: 0; }
.title-wrap::before { left: -8px; border-right: 0; } .title-wrap::after { right: -8px; border-left: 0; }
.title-row { display: flex; align-items: center; justify-content: center; gap: 14px; }
.site-logo { height: 40px; max-width: 120px; object-fit: contain; filter: drop-shadow(0 0 8px var(--cy-dim)); }
.hd-title h1 { margin: 0; font-size: 30px; letter-spacing: 4px; font-weight: 800; white-space: nowrap; max-width: 820px; overflow: hidden; text-overflow: ellipsis; background: linear-gradient(90deg, var(--txt), var(--cy) 40%, var(--ink) 60%, var(--cy)); background-size: 200% 100%; -webkit-background-clip: text; background-clip: text; color: transparent; animation: shine 6s linear infinite; filter: drop-shadow(0 0 14px var(--cy-dim)); }
@keyframes shine { 0% { background-position: 0 0; } 100% { background-position: 200% 0; } }
.sub { margin-top: 2px; font-size: 11px; letter-spacing: 5px; color: var(--cy); opacity: .8; }
.deco { width: 150px; height: 3px; background: linear-gradient(90deg, transparent, var(--cy)); position: relative; flex-shrink: 0; }
.deco.r { background: linear-gradient(90deg, var(--cy), transparent); }
.deco::after { content: ''; position: absolute; top: 8px; height: 1px; width: 60%; background: var(--cy-dim); }
.deco.l::after { right: 0; } .deco.r::after { left: 0; }
.ring { position: relative; width: 44px; height: 44px; border-radius: 50%; border: 1px dashed var(--cy); opacity: .85; animation: spin 14s linear infinite; }
.ring i { position: absolute; inset: 7px; border-radius: 50%; border: 2px solid transparent; border-top-color: var(--cy); border-right-color: var(--cy); animation: spin 3s linear infinite reverse; }
.ring::after { content: ''; position: absolute; inset: 17px; border-radius: 50%; background: var(--cy); box-shadow: 0 0 10px var(--cy); }
@keyframes spin { to { transform: rotate(360deg); } }

/* 顶部统计 */
.stats { display: grid; grid-template-columns: 1.35fr 2.1fr 1.35fr 1.1fr; gap: 12px; height: 126px; }
.panel { position: relative; border: 1px solid var(--cy-dim); background: var(--panel); border-radius: 6px; padding: 10px 18px 8px; box-shadow: inset 0 0 30px var(--cy-soft); overflow: hidden; }
.panel::before { content: ''; position: absolute; left: 0; top: 0; right: 0; height: 3px; background: linear-gradient(90deg, transparent 0%, var(--accent, var(--cy)) 20%, #fff 50%, var(--accent, var(--cy)) 80%, transparent 100%); background-size: 200% 100%; animation: runlight 4s linear infinite; }
@keyframes runlight { 0% { background-position: 200% 0; } 100% { background-position: -200% 0; } }
.p-blue { --accent: #3b9dff; } .p-amber { --accent: var(--amb); } .p-purple { --accent: var(--pur); }
.p-red { --accent: var(--red); background: linear-gradient(180deg, rgba(74, 14, 34, .8), rgba(30, 8, 24, .92)); border-color: rgba(255, 91, 110, .45); }
.screen-root.light .p-red { background: linear-gradient(180deg, #fff1f2, #ffe4e6); }
.p-title { display: flex; align-items: center; gap: 8px; font-size: 16px; font-weight: 700; color: var(--ink); }
.p-title i { width: 8px; height: 8px; border-radius: 2px; background: var(--accent, var(--cy)); box-shadow: 0 0 8px var(--accent, var(--cy)); }
.p-title em { font-style: normal; font-size: 10px; letter-spacing: 2px; color: var(--txt-dim); opacity: .7; font-weight: 400; }
.p-title small { font-size: 12px; color: var(--txt-dim); font-weight: 400; margin-left: 6px; }
.nums { display: flex; justify-content: space-around; align-items: flex-end; margin-top: 8px; gap: 6px; }
.num { text-align: center; flex: 1; min-width: 0; }
.num b { display: block; font-size: 36px; line-height: 1; color: var(--cy); text-shadow: 0 0 14px var(--cy-dim); }
.num > span { display: block; margin-top: 6px; font-size: 12.5px; color: var(--txt-dim); white-space: nowrap; }
.num.hi b { color: var(--ink); } .num.ok b { color: var(--grn); text-shadow: 0 0 14px rgba(53, 227, 122, .6); }
.num.bad b { color: var(--red); text-shadow: 0 0 14px rgba(255, 91, 110, .6); animation: sc-pulse 1.6s ease-in-out infinite; }
.num.warn b { color: var(--amb); text-shadow: 0 0 14px rgba(247, 183, 49, .6); }
.alerts { display: flex; align-items: center; gap: 16px; margin-top: 6px; }
.alert-count { font-size: 50px; line-height: 1; color: var(--red); text-shadow: 0 0 16px rgba(255, 91, 110, .8); min-width: 56px; text-align: center; animation: sc-pulse 1.6s ease-in-out infinite; }
.alert-count.zero { color: var(--grn); text-shadow: 0 0 16px rgba(53, 227, 122, .6); animation: none; }
.alerts ul { margin: 0; padding: 0; list-style: none; font-size: 12.5px; line-height: 1.65; }
.alerts li::before { content: '● '; font-size: 9px; vertical-align: 2px; }
.alerts li.danger { color: #ff8a97; } .alerts li.warning { color: #ffd37a; } .alerts li.info { color: var(--txt-dim); }
.screen-root.light .alerts li.danger { color: #dc2626; } .screen-root.light .alerts li.warning { color: #b45309; }
.all-good { color: var(--grn); font-size: 15px; }

/* 累计 · 同比 · AI 解读 */
.strip { display: grid; grid-template-columns: repeat(5, 1fr) 2.6fr; gap: 12px; height: 62px; }
.cum { border: 1px solid var(--cy-dim); border-radius: 6px; background: var(--frame-bg); display: flex; align-items: center; gap: 10px; padding: 0 14px; }
.cum-label { font-size: 12px; color: var(--txt-dim); white-space: nowrap; }
.cum .big { font-size: 26px; color: var(--ink); text-shadow: 0 0 10px var(--cy-dim); margin-left: auto; }
.wow { font-size: 11px; white-space: nowrap; padding: 2px 6px; border-radius: 4px; }
.wow.up { color: var(--grn); background: rgba(53, 227, 122, .12); } .wow.down { color: var(--red); background: rgba(255, 91, 110, .12); } .wow.flat { color: var(--txt-dim); background: var(--chip); }
.brief { border: 1px solid rgba(185, 124, 255, .45); border-radius: 6px; background: linear-gradient(90deg, rgba(185, 124, 255, .14), var(--frame-bg)); display: flex; align-items: center; gap: 12px; padding: 0 16px; min-width: 0; }
.brief-icon { color: var(--pur); font-size: 20px; text-shadow: 0 0 10px var(--pur); }
.brief-text { min-width: 0; display: flex; flex-direction: column; gap: 2px; }
.brief-text b { font-size: 13.5px; line-height: 1.4; color: var(--ink); display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; font-family: inherit; }
.brief-more { display: none; }

/* 底部 */
.ft { display: flex; align-items: center; gap: 16px; height: 30px; font-size: 12px; color: var(--txt-dim); }
.ticker { flex: 1; display: flex; align-items: center; gap: 12px; min-width: 0; border: 1px solid rgba(255, 91, 110, .3); border-radius: 4px; background: rgba(74, 14, 34, .35); height: 28px; padding: 0 12px; }
.screen-root.light .ticker { background: #fff1f2; }
.tk-label { color: var(--red); font-weight: 700; white-space: nowrap; }
.tk-track { flex: 1; overflow: hidden; min-width: 0; }
.tk-inner { display: inline-block; white-space: nowrap; color: #ffd0d6; padding-left: 100%; animation: ticker 32s linear infinite; }
.screen-root.light .tk-inner { color: #9f1239; }
@keyframes ticker { to { transform: translateX(-100%); } }
.page-dots { display: flex; align-items: center; gap: 6px; }
.page-dots i { width: 22px; height: 5px; border-radius: 3px; background: var(--cy-dim); cursor: pointer; }
.page-dots i.on { background: var(--cy); box-shadow: 0 0 8px var(--cy); }
.page-dots .auto { margin-left: 6px; font-size: 11px; color: var(--cy); cursor: pointer; }
.page-dots .auto.off { color: var(--txt-dim); }
.ft-meta { white-space: nowrap; }
</style>
