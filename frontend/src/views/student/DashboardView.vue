<template>
  <div v-if="data">
    <!-- 欢迎横幅 -->
    <div class="welcome">
      <div>
        <h2 class="welcome-title">欢迎回来，{{ data.user.name }}! 👋</h2>
        <p class="welcome-sub">本周已完成 {{ data.weeklyHours }} 小时实践学习，继续保持！</p>
      </div>
      <div class="welcome-level">
        <div class="wl-num">Lv.{{ data.level }}</div>
        <div class="wl-label">实践等级</div>
      </div>
    </div>

    <!-- 统计卡 -->
    <div class="stat-grid">
      <div v-for="s in statCards" :key="s.label" class="ref-stat-card">
        <div class="ref-stat-icon" :style="{ background: s.bg }">
          <component :is="s.icon" :size="22" color="#fff" />
        </div>
        <div>
          <div class="ref-stat-value">{{ s.value }}</div>
          <div class="ref-stat-label">{{ s.label }}</div>
        </div>
      </div>
    </div>

    <div class="two-col">
      <!-- 学习进度趋势 -->
      <div class="card">
        <div class="card-head">
          <h3>学习进度趋势</h3>
          <div class="trend-toggle">
            <span class="pill" :class="{ active: trendMode === 'week' }" @click="trendMode = 'week'">本周</span>
            <span class="pill" :class="{ active: trendMode === 'month' }" @click="trendMode = 'month'">本月</span>
          </div>
        </div>
        <div ref="trendRef" class="trend-chart"></div>
      </div>

      <!-- 技能掌握度 -->
      <div class="card">
        <div class="card-head">
          <h3>技能掌握度</h3>
          <router-link to="/app/skills" class="more-link">查看详情 →</router-link>
        </div>
        <div v-for="(s, i) in topSkills" :key="s.skillName" class="skill-row">
          <div class="skill-row-head">
            <span>{{ s.skillName }}</span>
            <b>{{ s.score }}%</b>
          </div>
          <div class="skill-row-bar">
            <div class="skill-row-inner"
                 :style="{ width: s.score + '%', background: skillColors[i % skillColors.length] }"></div>
          </div>
        </div>
      </div>
    </div>

    <div class="two-col bottom">
      <!-- 进行中的项目 -->
      <div class="card">
        <div class="card-head">
          <h3>进行中的项目</h3>
          <router-link to="/app/projects" class="more-link">浏览更多项目 →</router-link>
        </div>
        <el-empty v-if="data.ongoingProjects.length === 0" description="还没有进行中的项目,去项目中心报名吧!" />
        <div v-for="p in data.ongoingProjects" :key="p.id" class="ongoing-item">
          <div class="ongoing-head">
            <span class="ongoing-title" @click="$router.push(`/app/projects/${p.projectId}`)">
              {{ p.projectTitle }}
            </span>
            <span class="ongoing-deadline">截止: {{ p.deadline || '未设置' }}</span>
          </div>
          <div class="ongoing-task">任务: {{ p.currentTask || '推进中' }}</div>
          <div class="ongoing-bar-row">
            <div class="ongoing-bar">
              <div class="ongoing-bar-inner" :style="{ width: p.progress + '%' }"></div>
            </div>
            <b class="ongoing-pct">{{ p.progress }}%</b>
            <el-button size="small" type="primary" plain @click="advance(p)">推进 +10%</el-button>
          </div>
        </div>
      </div>

      <!-- 我的成就 -->
      <div class="card">
        <div class="card-head"><h3>我的成就</h3></div>
        <div class="achievements">
          <div v-for="a in data.achievements" :key="a.name" class="ach-item"
               :class="{ locked: !a.unlocked }">
            <span class="ach-icon" :class="{ gray: !a.unlocked }">
              <component :is="achIcon(a.name)" :size="22" />
            </span>
            <div class="ach-text">
              <div class="ach-name">{{ a.name }}</div>
              <div class="ach-desc">{{ a.desc }}</div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts'
import { ElMessage } from 'element-plus'
import {
  Award, Clock, GraduationCap, Package, Rocket, Sprout, TrendingUp, Trophy
} from 'lucide-vue-next'
import { fetchDashboard, fetchSkills, updateProgress } from '../../api'
import { useAuthStore } from '../../stores/auth'

const authStore = useAuthStore()
const data = ref(null)
const skills = ref([])
const trendMode = ref('week')
const trendRef = ref(null)
let chart = null

const skillColors = ['#3b82f6', '#22c55e', '#eab308', '#9333ea', '#06b6d4', '#f97316']

const statCards = computed(() => data.value ? [
  { icon: GraduationCap, label: '完成项目', value: data.value.completedProjects, bg: 'linear-gradient(135deg,#60a5fa,#2563eb)' },
  { icon: Clock, label: '实践时长(小时)', value: data.value.weeklyHours, bg: 'linear-gradient(135deg,#4ade80,#16a34a)' },
  { icon: Trophy, label: '获得成就', value: data.value.achievementCount, bg: 'linear-gradient(135deg,#facc15,#f97316)' },
  { icon: TrendingUp, label: '技能掌握度', value: data.value.skillAvg + '%', bg: 'linear-gradient(135deg,#c084fc,#9333ea)' }
] : [])

// 成就图标:按成就名映射线性图标(后端 emoji 字段仅作兜底语义)
const achIconMap = { '初出茅庐': Sprout, '借阅达人': Package, '项目先锋': Rocket, '技术大牛': Trophy }
const achIcon = (name) => achIconMap[name] || Award

const topSkills = computed(() => skills.value.slice(0, 4))

const renderTrend = () => {
  if (!trendRef.value || !data.value) return
  if (!chart) chart = echarts.init(trendRef.value)
  const isWeek = trendMode.value === 'week'
  const hours = isWeek ? data.value.weekTrend : data.value.monthTrend
  const tasks = isWeek ? (data.value.weekTaskTrend || []) : (data.value.monthTaskTrend || [])
  const labels = isWeek
    ? ['周一', '周二', '周三', '周四', '周五', '周六', '周日']
    : hours.map((_, i) => `${i + 1}日`)
  chart.setOption({
    grid: { left: 36, right: 16, top: 34, bottom: 28 },
    tooltip: { trigger: 'axis' },
    legend: { data: ['学习时长', '完成任务'], top: 0, right: 0, itemWidth: 14, textStyle: { color: '#6b7280', fontSize: 12 } },
    xAxis: { type: 'category', data: labels, axisLine: { lineStyle: { color: '#e5e7eb' } }, axisLabel: { color: '#6b7280' } },
    yAxis: { type: 'value', splitLine: { lineStyle: { color: '#f3f4f6' } } },
    series: [
      {
        name: '学习时长',
        type: 'line', data: hours, smooth: true, symbolSize: 6,
        lineStyle: { width: 3, color: '#3b82f6' }, itemStyle: { color: '#3b82f6' },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(59,130,246,.2)' },
            { offset: 1, color: 'rgba(59,130,246,0)' }
          ])
        }
      },
      {
        name: '完成任务',
        type: 'line', data: tasks, smooth: true, symbolSize: 6,
        lineStyle: { width: 3, color: '#22c55e' }, itemStyle: { color: '#22c55e' }
      }
    ]
  })
}

watch(trendMode, renderTrend)

const load = async () => {
  data.value = await fetchDashboard()
  authStore.updateUser(data.value.user)
  try {
    const s = await fetchSkills()
    skills.value = s.skills
  } catch (e) { /* 技能条加载失败不阻塞页面 */ }
  await nextTick()
  renderTrend()
}

const advance = async (p) => {
  const next = Math.min(100, p.progress + 10)
  await updateProgress(p.projectId, { progress: next, currentTask: p.currentTask })
  ElMessage.success(next >= 100 ? '恭喜完成项目!' : `进度已更新到 ${next}%`)
  await load()
}

onMounted(load)
window.addEventListener('resize', () => chart && chart.resize())
</script>

<style scoped>
.welcome {
  background: linear-gradient(to right, #2563eb, #7c3aed, #9333ea);
  color: #fff;
  border-radius: 16px;
  padding: 30px 34px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  box-shadow: 0 10px 15px -3px rgba(79,70,229,.25);
}
.welcome-title { margin: 0 0 8px; font-size: 28px; font-weight: 700; }
.welcome-sub { margin: 0; color: rgba(255,255,255,.85); font-size: 14px; }
.welcome-level { text-align: right; }
.wl-num { font-size: 40px; font-weight: 800; line-height: 1.1; }
.wl-label { font-size: 13px; opacity: .85; }

.stat-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 20px;
}
@media (max-width: 1100px) { .stat-grid { grid-template-columns: repeat(2, 1fr); } }

.two-col {
  display: grid;
  grid-template-columns: 3fr 2fr;
  gap: 16px;
  margin-bottom: 20px;
}
.two-col.bottom { grid-template-columns: 3fr 2fr; }
@media (max-width: 1100px) { .two-col, .two-col.bottom { grid-template-columns: 1fr; } }

.card-head {
  display: flex; justify-content: space-between; align-items: center;
  margin-bottom: 14px;
}
.card-head h3 { margin: 0; font-size: 16px; }
.more-link { color: var(--brand-blue); font-size: 13px; }
.trend-toggle { display: flex; gap: 6px; }
.trend-toggle .pill { padding: 4px 12px; font-size: 12px; }

.trend-chart { height: 260px; }

/* 技能掌握度进度条 */
.skill-row { margin-bottom: 18px; }
.skill-row-head {
  display: flex; justify-content: space-between;
  font-size: 13px; color: #374151; margin-bottom: 6px;
}
.skill-row-head b { color: #111827; }
.skill-row-bar { height: 8px; border-radius: 999px; background: #f3f4f6; overflow: hidden; }
.skill-row-inner { height: 100%; border-radius: 999px; transition: width .3s; }

/* 进行中的项目 */
.ongoing-item { padding: 12px 0; border-bottom: 1px solid var(--border); }
.ongoing-item:last-child { border-bottom: none; }
.ongoing-head { display: flex; justify-content: space-between; align-items: center; gap: 12px; }
.ongoing-title { font-weight: 600; cursor: pointer; font-size: 14px; }
.ongoing-title:hover { color: var(--brand-blue); }
.ongoing-deadline { font-size: 12px; color: #9ca3af; flex-shrink: 0; }
.ongoing-task { font-size: 12px; color: var(--text-secondary); margin: 6px 0 8px; }
.ongoing-bar-row { display: flex; align-items: center; gap: 10px; }
.ongoing-bar { flex: 1; height: 8px; border-radius: 999px; background: #f3f4f6; overflow: hidden; }
.ongoing-bar-inner {
  height: 100%; border-radius: 999px;
  background: linear-gradient(to right, #3b82f6, #6366f1);
  transition: width .3s;
}
.ongoing-pct { font-size: 13px; color: #111827; width: 38px; text-align: right; }

/* 成就 */
.achievements { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
.ach-item {
  display: flex; align-items: center; gap: 10px;
  padding: 14px; border-radius: 12px;
  background: linear-gradient(135deg, #fefce8, #fef9c3);
  border: 1px solid #fde68a;
}
.ach-item.locked {
  background: #f9fafb; border-color: var(--border); opacity: .6;
  filter: grayscale(.6);
}
.ach-icon {
  width: 40px; height: 40px; border-radius: 10px; flex-shrink: 0;
  background: #fde68a; color: #b45309;
  display: flex; align-items: center; justify-content: center;
}
.ach-icon.gray { background: #e5e7eb; color: #9ca3af; }
.ach-text { min-width: 0; }
.ach-name { font-size: 13px; font-weight: 600; color: #111827; }
.ach-desc { font-size: 11px; color: var(--text-secondary); margin-top: 2px; }
</style>
