<template>
  <!-- 总览页:学生 / 项目卡片墙 + KPI 仪表 + 7 天趋势 + 最近动态 -->
  <section class="overview page-enter">
    <div class="frame grid-wrap">
      <div class="grid-tools">
        <div class="pager">
          <button v-for="p in pageCount" :key="p" :class="{ on: page === p - 1 }" @click="page = p - 1">{{ rangeLabel(p) }}</button>
          <span v-if="!pageCount" class="dim" style="font-size:13px">暂无数据</span>
        </div>
        <div class="views">
          <button :class="{ on: view === 'students' }" @click="view = 'students'; page = 0">学生</button>
          <button :class="{ on: view === 'projects' }" @click="view = 'projects'; page = 0">项目</button>
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
        <div v-if="!items.length" class="empty-hint">{{ view === 'students' ? '还没有学生报名项目' : '还没有发布项目' }}</div>
      </div>
    </div>

    <aside class="side">
      <div class="frame kpi-box">
        <div class="box-title">学期 KPI 完成度<em>TARGETS</em></div>
        <div v-if="!kpiShown.length" class="kpi-empty">在「站点设置 → 数据大屏」填写学期目标后,这里显示目标完成度仪表</div>
        <div v-else ref="kpiEl" class="chart"></div>
      </div>
      <div class="frame chart-box">
        <div class="box-title">近 7 天学习活跃<em>TREND</em></div>
        <div ref="trendEl" class="chart"></div>
      </div>
      <div class="frame feed-box">
        <div class="box-title">最近动态<em>ACTIVITY</em></div>
        <div class="feed-viewport" @mouseenter="feedPaused = true" @mouseleave="feedPaused = false">
          <ul class="feed" :class="{ scroll: feed.length > 6, paused: feedPaused }" :style="{ animationDuration: Math.max(12, feed.length * 2.2) + 's' }">
            <li v-for="(f, i) in feedLoop" :key="i">
              <span class="f-time">{{ fmtShort(f.time) }}</span>
              <span class="f-user">{{ f.user }}</span>
              <span class="f-text">{{ f.title }}</span>
            </li>
            <li v-if="!feed.length" class="dim">暂无学习动作</li>
          </ul>
        </div>
      </div>
    </aside>
  </section>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts'

const props = defineProps({ data: { type: Object, required: true }, palette: { type: Object, required: true }, fmtShort: { type: Function, required: true } })
const PAGE_SIZE = 16
const view = ref('students')
const page = ref(0)
const hover = ref(false)
const feedPaused = ref(false)
const kpiEl = ref(null)
const trendEl = ref(null)
let kpiChart = null
let trendChart = null

const items = computed(() => (view.value === 'students' ? props.data.students || [] : props.data.projectCards || []))
const pageCount = computed(() => Math.ceil(items.value.length / PAGE_SIZE))
const pageItems = computed(() => items.value.slice(page.value * PAGE_SIZE, (page.value + 1) * PAGE_SIZE))
const rangeLabel = (p) => `${(p - 1) * PAGE_SIZE + 1}-${Math.min(p * PAGE_SIZE, items.value.length)}`
const feed = computed(() => props.data.feed || [])
const feedLoop = computed(() => (feed.value.length > 6 ? [...feed.value, ...feed.value] : feed.value))
const kpiShown = computed(() => (props.data.kpi || []).filter((k) => k.target > 0))
watch(pageCount, (n) => { if (page.value >= n) page.value = 0 })

const gauge = (k, idx, total) => {
  const colors = ['#35e37a', '#22e1ff', '#f7b731', '#b97cff']
  const cx = ((idx + 0.5) / total) * 100
  const color = colors[idx % colors.length]
  return {
    type: 'gauge', center: [cx + '%', '54%'], radius: total > 2 ? '80%' : '92%', startAngle: 225, endAngle: -45, min: 0, max: 100,
    progress: { show: true, width: 9, roundCap: true, itemStyle: { color, shadowColor: color, shadowBlur: 10 } },
    axisLine: { lineStyle: { width: 9, color: [[1, props.palette.chip]] } },
    axisTick: { show: false }, splitLine: { show: false }, axisLabel: { show: false }, pointer: { show: false },
    title: { show: true, offsetCenter: [0, '68%'], color: props.palette.textDim, fontSize: 11 },
    detail: { valueAnimation: true, offsetCenter: [0, '6%'], formatter: () => `${k.percent}%`, color: props.palette.text, fontSize: 20, fontWeight: 700, fontFamily: 'Bahnschrift, DIN Alternate, Segoe UI' },
    data: [{ value: k.percent, name: `${k.name}\n${k.actual}${k.unit} / ${k.target}${k.unit}` }]
  }
}
const render = () => {
  const p = props.palette
  if (kpiEl.value && kpiShown.value.length) {
    if (!kpiChart) kpiChart = echarts.init(kpiEl.value)
    kpiChart.setOption({ backgroundColor: 'transparent', animationDuration: 1200, series: kpiShown.value.map((k, i) => gauge(k, i, kpiShown.value.length)) }, true)
  }
  if (trendEl.value) {
    if (!trendChart) trendChart = echarts.init(trendEl.value)
    const t = props.data.trend || []
    const area = (c1, c2) => new echarts.graphic.LinearGradient(0, 0, 0, 1, [{ offset: 0, color: c1 }, { offset: 1, color: c2 }])
    trendChart.setOption({
      backgroundColor: 'transparent', animationDuration: 1200,
      grid: { left: 36, right: 16, top: 34, bottom: 26 },
      legend: { top: 2, left: 'center', textStyle: { color: p.textDim, fontSize: 11 }, itemWidth: 12, itemHeight: 8, itemGap: 14 },
      tooltip: { trigger: 'axis', backgroundColor: p.tooltipBg, borderColor: p.border, textStyle: { color: p.text, fontSize: 12 } },
      xAxis: { type: 'category', data: t.map((x) => x.label), axisLine: { lineStyle: { color: p.border } }, axisLabel: { color: p.textDim, fontSize: 11 }, axisTick: { show: false } },
      yAxis: { type: 'value', minInterval: 1, splitLine: { lineStyle: { color: p.grid } }, axisLabel: { color: p.textDim, fontSize: 11 } },
      series: [
        { name: '学习动作', type: 'bar', data: t.map((x) => x.actions), barWidth: 14, itemStyle: { borderRadius: [3, 3, 0, 0], color: area('#22e1ff', 'rgba(34,225,255,.1)') } },
        { name: '进度与成果', type: 'line', smooth: true, data: t.map((x) => x.tasks), symbolSize: 6, lineStyle: { width: 2, color: '#35e37a', shadowColor: 'rgba(53,227,122,.6)', shadowBlur: 8 }, itemStyle: { color: '#35e37a' }, areaStyle: { color: area('rgba(53,227,122,.35)', 'rgba(53,227,122,0)') } },
        { name: '活跃人数', type: 'line', smooth: true, data: t.map((x) => x.activeUsers), symbolSize: 6, lineStyle: { width: 2, color: '#f7b731', shadowColor: 'rgba(247,183,49,.6)', shadowBlur: 8 }, itemStyle: { color: '#f7b731' } }
      ]
    })
  }
}
let rotate = null
onMounted(() => {
  nextTick(render)
  rotate = setInterval(() => { if (!hover.value && pageCount.value > 1) page.value = (page.value + 1) % pageCount.value }, 8000)
})
watch(() => [props.data, props.palette], () => nextTick(render), { deep: true })
onBeforeUnmount(() => { clearInterval(rotate); kpiChart?.dispose(); trendChart?.dispose() })
</script>

<style scoped>
.overview { flex: 1; display: grid; grid-template-columns: 1fr 440px; gap: 14px; min-height: 0; }
.grid-wrap { display: flex; flex-direction: column; padding: 12px 16px 14px; }
.grid-tools { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
.pager, .views { display: flex; gap: 8px; align-items: center; }
.pager button, .views button { border: 1px solid var(--cy-dim); background: var(--chip); color: var(--txt-dim); font-size: 13px; padding: 4px 12px; border-radius: 4px; cursor: pointer; transition: all .2s; }
.pager button.on, .views button.on { background: var(--cy); color: #04213f; font-weight: 700; border-color: var(--cy); box-shadow: 0 0 12px var(--cy-dim); }
.pager button:hover, .views button:hover { color: var(--ink); border-color: var(--cy); }
.cards { flex: 1; display: grid; grid-template-columns: repeat(4, 1fr); grid-template-rows: repeat(4, 1fr); gap: 12px; min-height: 0; position: relative; }
.card { position: relative; border: 1px solid var(--cy-dim); background: var(--card-bg); border-radius: 8px; padding: 10px 14px 8px; display: flex; flex-direction: column; justify-content: space-between; overflow: hidden; transition: transform .2s, box-shadow .2s, border-color .2s; }
.card:hover { transform: translateY(-3px); border-color: var(--cy); box-shadow: 0 8px 24px var(--cy-soft); }
.card::after { content: ''; position: absolute; right: -1px; top: -1px; width: 24px; height: 24px; background: linear-gradient(225deg, var(--bg) 50%, var(--cy-dim) 50%, var(--cy-dim) calc(50% + 1px), transparent calc(50% + 1px)); }
.card.enter { animation: sc-enter .5s cubic-bezier(.2, .8, .2, 1) both; }
.card.overdue { border-color: rgba(255, 91, 110, .55); background: linear-gradient(180deg, rgba(74, 20, 40, .8), rgba(30, 10, 26, .92)); }
.screen-root.light .card.overdue { background: linear-gradient(180deg, #fff1f2, #ffe4e6); }
.c-head { display: flex; align-items: center; gap: 10px; }
.rank { width: 24px; height: 24px; border-radius: 50%; border: 1px solid var(--cy-dim); color: var(--cy); font-size: 12px; font-weight: 700; display: grid; place-items: center; flex-shrink: 0; background: var(--chip); }
.rank.r1 { background: linear-gradient(135deg, #ffe27a, #d19a00); color: #3b2a00; border-color: #ffd54a; box-shadow: 0 0 12px rgba(255, 213, 74, .8); }
.rank.r2 { background: linear-gradient(135deg, #f1f5f9, #94a3b8); color: #1e293b; border-color: #e2e8f0; }
.rank.r3 { background: linear-gradient(135deg, #f0b27a, #b45309); color: #3b1a00; border-color: #f59e0b; }
.name { flex: 1; font-size: 16px; font-weight: 700; color: var(--ink); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.pct { font-size: 19px; font-weight: 800; display: flex; align-items: baseline; gap: 4px; }
.pct i { font-style: normal; font-size: 11px; }
.pct.up { color: var(--grn); } .pct.down { color: var(--red); } .pct.flat { color: var(--amb); }
.c-row { display: flex; justify-content: space-between; font-size: 12.5px; color: var(--txt-dim); margin-top: 6px; }
.c-row b { color: var(--ink); font-size: 16px; margin: 0 2px; }
.c-row b.warn { color: var(--amb); }
.bar { position: relative; height: 9px; border-radius: 5px; background: rgba(0, 0, 0, .45); margin-top: 8px; overflow: visible; }
.screen-root.light .bar { background: #dbe4f0; }
.bar .fill { position: relative; height: 100%; border-radius: 5px; background: linear-gradient(90deg, #35e37a, #b7f34d 70%, #f7b731); box-shadow: 0 0 10px rgba(53, 227, 122, .5); transition: width .8s ease; overflow: hidden; }
.bar .fill::after { content: ''; position: absolute; top: 0; bottom: 0; width: 40px; background: linear-gradient(90deg, transparent, rgba(255, 255, 255, .8), transparent); animation: shimmer 2.4s linear infinite; }
@keyframes shimmer { from { left: -40px; } to { left: 100%; } }
.bar .ticks { position: absolute; inset: 0; background: repeating-linear-gradient(90deg, transparent 0 calc(10% - 2px), var(--bg) calc(10% - 2px) 10%); border-radius: 5px; pointer-events: none; }
.bar .pin { position: absolute; top: 11px; width: 8px; height: 8px; margin-left: -4px; border-radius: 50% 50% 50% 0; transform: rotate(-135deg); background: var(--ink); box-shadow: 0 0 6px var(--ink); transition: left .8s ease; }
.c-foot { display: flex; justify-content: space-between; font-size: 11.5px; color: var(--txt-dim); margin-top: 12px; }
.tag-bad { color: var(--red); font-weight: 700; }
.side { display: grid; grid-template-rows: 200px 1fr 1fr; gap: 12px; min-height: 0; }
.kpi-box, .chart-box, .feed-box { display: flex; flex-direction: column; }
.kpi-empty { flex: 1; display: grid; place-items: center; font-size: 12px; color: var(--txt-dim); text-align: center; padding: 0 20px; line-height: 1.7; }
.feed-viewport { flex: 1; min-height: 0; overflow: hidden; margin: 6px 0 8px; mask-image: linear-gradient(180deg, transparent, #000 8%, #000 92%, transparent); }
.feed { list-style: none; margin: 0; padding: 0 16px; }
.feed.scroll { animation: feedscroll linear infinite; }
.feed.paused { animation-play-state: paused; }
@keyframes feedscroll { from { transform: translateY(0); } to { transform: translateY(-50%); } }
.feed li { display: flex; gap: 10px; font-size: 13px; line-height: 1.6; padding: 5px 0; border-bottom: 1px dashed var(--grid-line); }
.f-time { color: var(--cy); width: 74px; flex-shrink: 0; font-variant-numeric: tabular-nums; }
.f-user { color: var(--ink); width: 64px; flex-shrink: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.f-text { color: var(--txt-dim); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
</style>
