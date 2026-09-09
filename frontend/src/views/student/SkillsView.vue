<template>
  <div>
    <div class="head-row">
      <div>
        <h2 class="page-title">技能评估与提升</h2>
        <p class="page-subtitle">画像来自两类实证:老师对项目成果的评审,和 AI 出题的客观测评</p>
      </div>
      <button class="btn-gradient assess-btn" @click="openQuiz(null)">
        <Zap :size="15" /> AI 能力测评
      </button>
    </div>

    <div class="grid-2">
      <!-- 雷达图 -->
      <div class="card">
        <div class="card-head">
          <h3>综合能力雷达</h3>
          <div class="overall">
            综合评分 <b class="gradient-text">{{ data.overall }}</b>
          </div>
        </div>
        <div v-if="data.skills.length" ref="radarRef" class="chart"></div>
        <div v-else class="chart-empty">管理员尚未配置技能维度</div>
        <div class="chart-foot">
          <span class="legend-dot solid"></span>综合掌握度
          <span class="chart-foot-right">
            {{ data.evidenceTotal > 0 ? `已计入 ${data.evidenceTotal} 次实证(项目评审 + AI 测评)` : '尚无实证:做一次 AI 测评或完成项目评审后自动校准' }}
          </span>
        </div>
      </div>

      <!-- 成长曲线 -->
      <div class="card">
        <div class="card-head"><h3>学习成长曲线</h3></div>
        <div v-if="data.history.length >= 2" ref="lineRef" class="chart"></div>
        <div v-else class="chart-empty">
          暂无成长记录<br />
          <small>完成 AI 测评或项目评审后,综合评分的每次变化都会记录在这里</small>
        </div>
      </div>
    </div>

    <!-- 技能详情 -->
    <div class="card">
      <div class="card-head"><h3>技能详情分析</h3></div>
      <div v-for="(s, i) in data.skills" :key="s.skillName" class="skill-block">
        <div class="skill-head">
          <span class="skill-icon" :style="{ background: meta(s.skillName, i).bg }">
            <component :is="meta(s.skillName, i).icon" :size="20" color="#fff" />
          </span>
          <div class="skill-title-box">
            <div class="skill-title-row">
              <span class="skill-name">{{ s.skillName }}</span>
              <span class="skill-level badge" :class="levelBadge(s.score)">{{ levelText(s.score) }}</span>
              <span v-if="s.evidenceCount > 0" class="badge badge-green">实证 {{ s.evidenceCount }} 次</span>
              <span v-else class="badge badge-gray">尚无实证</span>
            </div>
            <div class="skill-desc">{{ s.description || '专业技能维度' }}</div>
          </div>
          <div class="skill-score-box">
            <b>{{ s.score }}%</b>
            <span>综合掌握度</span>
          </div>
        </div>
        <div class="skill-bar">
          <div class="skill-bar-inner" :style="{ width: s.score + '%' }"></div>
        </div>
        <div class="skill-facts">
          <span v-if="lastEvent(s.skillName)" class="skill-last">最近:{{ lastEvent(s.skillName).note }}</span>
          <span v-else class="skill-last">还没有实证记录</span>
          <el-button size="small" text type="primary" class="quiz-btn" @click="openQuiz(s.skillName)">测一测这个维度 →</el-button>
        </div>
      </div>
    </div>

    <!-- 学习建议 -->
    <div class="card suggest-card">
      <h3 class="suggest-head"><Target :size="17" color="#2563eb" /> 个性化学习建议</h3>
      <p class="suggest-sub">根据你的技能画像与实证情况,我们为你制定了专属的学习计划</p>
      <div class="suggest-list">
        <div v-for="(s, i) in data.suggestions" :key="i" class="suggest-item">{{ s }}</div>
      </div>
    </div>

    <!-- 变动记录 -->
    <div v-if="data.events.length" class="card events-card">
      <h3 class="suggest-head"><History :size="17" color="#0891b2" /> 画像变动记录</h3>
      <div class="event-list">
        <div v-for="(e, i) in data.events" :key="i" class="event-item">
          <span class="badge" :class="e.source === 'PROJECT' ? 'badge-green' : 'badge-blue'">
            {{ sourceText(e.source) }}
          </span>
          <span class="event-skill">{{ e.skillName }}</span>
          <span class="event-delta" :class="deltaClass(e)">
            {{ e.beforeScore }} → {{ e.afterScore }}
          </span>
          <span class="event-note">{{ e.note }}</span>
          <span class="event-time">{{ formatTime(e.createdAt) }}</span>
        </div>
      </div>
    </div>

    <!-- AI 学习规划师 -->
    <div class="card ai-card">
      <div class="ai-head">
        <h3 class="suggest-head"><Sparkles :size="17" color="#9333ea" /> AI 学习规划师</h3>
        <div class="ai-form">
          <el-input v-model="aiGoal" placeholder="学习目标(选填),如:想做一个物联网作品" maxlength="100"
                    style="width:280px" size="small" />
          <el-select v-model="aiHours" size="small" style="width:132px">
            <el-option v-for="h in [4, 6, 8, 10, 15]" :key="h" :label="`每周 ${h} 小时`" :value="h" />
          </el-select>
          <el-button type="primary" size="small" :loading="aiLoading" @click="genPlan">
            {{ plan ? '重新生成' : '生成 AI 学习路线' }}
          </el-button>
        </div>
      </div>

      <div class="ai-chips">
        <span class="ai-chips-label">快捷目标:</span>
        <span v-for="k in goalChips" :key="k" class="goal-chip" @click="addGoalChip(k)">{{ k }}</span>
      </div>

      <div v-if="!plan && !aiLoading" class="ai-empty">
        基于你的技能画像与全部实战项目,AI 将为你规划「基础补强 → 综合实践 → 挑战提升」三阶段学习路线。
        推荐依据:技能短板覆盖、能力匹配度、难度递进、报名状态与你的学习目标。
      </div>
      <div v-else-if="aiLoading" class="ai-empty">正在分析技能画像与项目库,大约需要 10~20 秒...</div>

      <template v-if="plan && !aiLoading">
        <div class="ai-summary">{{ plan.summary }}</div>

        <div v-if="plan.focusSkills?.length" class="ai-focus-row">
          <div v-for="f in plan.focusSkills" :key="f.name" class="ai-focus">
            <div class="ai-focus-top">
              <b>{{ f.name }}</b>
              <span class="ai-focus-score">{{ f.currentScore }} → {{ f.targetScore }}</span>
            </div>
            <span class="ai-focus-reason">{{ f.reason }}</span>
          </div>
        </div>

        <div class="ai-stages">
          <div v-for="p in plan.recommendedProjects" :key="p.projectId" class="ai-stage">
            <div class="ai-stage-rail">
              <span class="ai-stage-dot">{{ p.stage }}</span>
              <span class="ai-stage-line" />
            </div>
            <div class="ai-proj">
              <div class="ai-proj-head">
                <span class="ai-stage-name">{{ stageName(p.stage) }}</span>
                <b class="ai-proj-title">{{ p.title }}</b>
                <span class="badge" :class="diffBadge(p.difficulty)">{{ p.difficulty }}</span>
                <span class="ai-match">匹配度 {{ p.matchScore }}%</span>
              </div>
              <ul class="ai-reasons">
                <li v-for="(r, i) in p.reasons" :key="i">{{ r }}</li>
              </ul>
              <div v-if="p.skillGaps?.length" class="ai-gaps">待补齐: {{ p.skillGaps.join(' / ') }}</div>
              <div class="ai-next">
                <span class="ai-next-text">下一步: {{ p.nextAction }}</span>
                <el-button size="small" type="primary" plain @click="goProject(p.projectId)">查看项目</el-button>
              </div>
            </div>
          </div>
        </div>

        <div class="ai-meta">
          {{ plan.source === 'AI' ? '✨ 由 AI 结合智能匹配生成' : '⚙️ AI 服务未启用,已使用智能匹配结果' }}
          · {{ plan.generatedAt }}{{ plan.cached ? ' (缓存)' : '' }} · 结果仅供学习参考
        </div>
      </template>
    </div>

    <!-- AI 能力测评 -->
    <el-dialog v-model="quizVisible" :title="quiz.step === 'result' ? '测评结果' : 'AI 能力测评'" width="640px" :close-on-click-modal="false" @closed="resetQuiz">
      <!-- 选维度 -->
      <template v-if="quiz.step === 'pick'">
        <p class="assess-tip">选一个维度,AI 会按你当前的水平出 6 道单选题(约 1 分钟)。答题得分会作为一次实证计入综合掌握度,做得越好涨得越快;做得差也只会小幅回调,放心测。</p>
        <div class="dim-grid">
          <button v-for="(s, i) in data.skills" :key="s.skillName" class="dim" :class="{ on: quiz.skillName === s.skillName }" @click="quiz.skillName = s.skillName">
            <span class="dim-icon" :style="{ background: meta(s.skillName, i).bg }"><component :is="meta(s.skillName, i).icon" :size="16" color="#fff" /></span>
            <span class="dim-text"><b>{{ s.skillName }}</b><small>当前 {{ s.score }} · {{ s.evidenceCount }} 次实证</small></span>
          </button>
        </div>
      </template>
      <!-- 出题中 -->
      <div v-else-if="quiz.step === 'loading'" class="quiz-loading">
        <div class="spinner"></div>
        <div><b>AI 正在为「{{ quiz.skillName }}」出题…</b><div class="muted">按你当前 {{ quiz.currentScore }} 分的水平配置难度,约 10 秒</div></div>
      </div>
      <!-- 答题 -->
      <template v-else-if="quiz.step === 'answer'">
        <div class="quiz-progress">
          <span>{{ quiz.skillName }} · 第 {{ quiz.index + 1 }} / {{ quiz.questions.length }} 题</span>
          <span class="badge badge-blue">{{ quiz.questions[quiz.index].difficulty || '基础' }}</span>
        </div>
        <div class="q-text">{{ quiz.questions[quiz.index].q }}</div>
        <div class="q-options">
          <button v-for="(opt, oi) in quiz.questions[quiz.index].options" :key="oi" class="q-opt" :class="{ on: quiz.answers[quiz.index] === oi }" @click="quiz.answers[quiz.index] = oi">
            <span class="q-letter">{{ 'ABCD'[oi] }}</span>{{ opt }}
          </button>
        </div>
        <div class="q-dots"><i v-for="(q, qi) in quiz.questions" :key="qi" :class="{ done: quiz.answers[qi] != null, cur: qi === quiz.index }" @click="quiz.index = qi"></i></div>
      </template>
      <!-- 结果 -->
      <template v-else-if="quiz.step === 'result'">
        <div class="quiz-result">
          <div class="qr-score" :class="quiz.result.score >= 80 ? 'good' : quiz.result.score >= 50 ? 'mid' : 'bad'"><b>{{ quiz.result.score }}</b><span>答对 {{ quiz.result.correct }} / {{ quiz.result.total }}</span></div>
          <div class="qr-text">
            <div class="qr-title">「{{ quiz.result.skillName }}」综合掌握度 {{ quiz.result.before }} → <b>{{ quiz.result.after }}</b>
              <span class="badge" :class="quiz.result.after > quiz.result.before ? 'badge-green' : quiz.result.after < quiz.result.before ? 'badge-red' : 'badge-gray'">{{ quiz.result.after > quiz.result.before ? '+' : '' }}{{ quiz.result.after - quiz.result.before }}</span>
            </div>
            <div class="muted">已作为一次 AI 测评实证计入画像;想再涨就去做相关项目,评审通过后权重更高。</div>
          </div>
        </div>
        <div class="qr-detail">
          <div v-for="d in quiz.result.detail" :key="d.index" class="qr-item" :class="{ ok: d.correct }">
            <div class="qr-q"><span class="qr-mark">{{ d.correct ? '✓' : '✕' }}</span>{{ quiz.questions[d.index].q }}</div>
            <div class="qr-ans">正确答案:{{ 'ABCD'[d.answer] }}. {{ quiz.questions[d.index].options[d.answer] }}<span v-if="!d.correct && d.given != null" class="qr-given"> · 你选了 {{ 'ABCD'[d.given] }}</span></div>
            <div v-if="d.explain" class="qr-explain">{{ d.explain }}</div>
          </div>
        </div>
      </template>
      <template #footer>
        <template v-if="quiz.step === 'pick'">
          <el-button @click="quizVisible = false">取消</el-button>
          <el-button type="primary" :disabled="!quiz.skillName" @click="startQuiz">开始出题</el-button>
        </template>
        <template v-else-if="quiz.step === 'answer'">
          <el-button :disabled="quiz.index === 0" @click="quiz.index--">上一题</el-button>
          <el-button v-if="quiz.index < quiz.questions.length - 1" type="primary" :disabled="quiz.answers[quiz.index] == null" @click="quiz.index++">下一题</el-button>
          <el-button v-else type="primary" :loading="submitting" :disabled="quiz.answers.some((a) => a == null)" @click="submitQuiz">交卷</el-button>
        </template>
        <template v-else-if="quiz.step === 'result'">
          <el-button @click="openQuiz(null)">再测一个维度</el-button>
          <el-button type="primary" @click="quizVisible = false">完成</el-button>
        </template>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { nextTick, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import * as echarts from 'echarts'
import { ElMessage } from 'element-plus'
import {
  Activity, BookOpen, Braces, CircuitBoard, Code, Cpu, History, Layers, Radio, Sparkles, Target, Wrench, Zap
} from 'lucide-vue-next'
import { fetchAiPlan, fetchSkills, generateAiPlan, skillQuizStart, skillQuizSubmit } from '../../api'

const router = useRouter()
const data = reactive({
  skills: [], overall: 0, selfOverall: null, selfComplete: false, evidenceTotal: 0,
  suggestions: [], history: [], events: []
})
const radarRef = ref(null)
const lineRef = ref(null)
const submitting = ref(false)

// ---------- AI 能力测评 ----------
const quizVisible = ref(false)
const quiz = reactive({ step: 'pick', skillName: '', quizId: '', questions: [], answers: [], index: 0, currentScore: 0, result: null })
const resetQuiz = () => { Object.assign(quiz, { step: 'pick', quizId: '', questions: [], answers: [], index: 0, result: null }) }
const openQuiz = (skillName) => {
  resetQuiz()
  quiz.skillName = skillName || data.skills[0]?.skillName || ''
  quizVisible.value = true
}
const startQuiz = async () => {
  quiz.step = 'loading'
  quiz.currentScore = data.skills.find((s) => s.skillName === quiz.skillName)?.score || 0
  try {
    const r = await skillQuizStart(quiz.skillName)
    quiz.quizId = r.quizId
    quiz.questions = r.questions
    quiz.answers = r.questions.map(() => null)
    quiz.index = 0
    quiz.step = 'answer'
  } catch (e) {
    quiz.step = 'pick'
  }
}
const submitQuiz = async () => {
  submitting.value = true
  try {
    const r = await skillQuizSubmit(quiz.quizId, quiz.answers)
    quiz.result = r
    quiz.step = 'result'
    plan.value = null
    await apply(r.summary)
  } catch (e) { /* 已提示 */ } finally {
    submitting.value = false
  }
}
let radarChart = null
let lineChart = null

// AI 学习规划师
const plan = ref(null)
const aiGoal = ref('')
const aiHours = ref(6)
const aiLoading = ref(false)

const goalChips = ['嵌入式开发', 'PCB设计', '物联网作品', '边缘AI应用', '信号处理', '备战电赛']

const addGoalChip = (k) => {
  if (aiGoal.value.includes(k)) return
  aiGoal.value = aiGoal.value ? `${aiGoal.value}、${k}` : `想提升${k}`
}

const stageName = (s) => (s === 1 ? '基础补强' : s === 2 ? '综合实践' : '挑战提升')
const diffBadge = (d) => (d === '入门' ? 'badge-green' : d === '进阶' ? 'badge-purple' : 'badge-red')

const goProject = (id) => router.push(`/app/projects/${id}`)

const loadPlan = async () => {
  try {
    plan.value = await fetchAiPlan()
  } catch (e) { /* 静默 */ }
}

const genPlan = async () => {
  aiLoading.value = true
  try {
    plan.value = await generateAiPlan({
      goal: aiGoal.value.trim() || undefined,
      weeklyHours: aiHours.value
    })
    if (plan.value?.source === 'RULE_FALLBACK') {
      ElMessage.info('AI 服务未启用或繁忙,已使用智能匹配结果')
    }
  } catch (e) { /* 已提示 */ } finally {
    aiLoading.value = false
  }
}

// 维度是后台可配置的:已知名称用专属图标,其余按顺序轮换配色
const knownMeta = {
  '嵌入式开发': { icon: Cpu, bg: 'linear-gradient(135deg,#60a5fa,#2563eb)' },
  'PCB设计': { icon: CircuitBoard, bg: 'linear-gradient(135deg,#a78bfa,#7c3aed)' },
  '编程能力': { icon: Code, bg: 'linear-gradient(135deg,#4ade80,#16a34a)' },
  '通信技术': { icon: Radio, bg: 'linear-gradient(135deg,#22d3ee,#0891b2)' },
  '信号处理': { icon: Activity, bg: 'linear-gradient(135deg,#facc15,#f59e0b)' },
  '硬件调试': { icon: Wrench, bg: 'linear-gradient(135deg,#fb923c,#ea580c)' }
}
const palette = [
  { icon: Layers, bg: 'linear-gradient(135deg,#f472b6,#db2777)' },
  { icon: Braces, bg: 'linear-gradient(135deg,#34d399,#059669)' },
  { icon: BookOpen, bg: 'linear-gradient(135deg,#94a3b8,#64748b)' },
  { icon: Target, bg: 'linear-gradient(135deg,#818cf8,#4f46e5)' }
]
const meta = (name, i) => knownMeta[name] || palette[i % palette.length]

const levelText = (v) => v >= 80 ? '精通' : v >= 60 ? '熟练' : v >= 40 ? '进阶' : '入门'
const levelBadge = (v) => v >= 80 ? 'badge-purple' : v >= 60 ? 'badge-green' : v >= 40 ? 'badge-blue' : 'badge-gray'

const lastEvent = (name) => data.events.find((e) => e.skillName === name)
const sourceText = (src) => ({ PROJECT: '项目实证', QUIZ: 'AI 测评', SELF: '自评(旧)', INIT: '初始' }[src] || src)
const deltaClass = (e) => (e.afterScore > e.beforeScore ? 'up' : e.afterScore < e.beforeScore ? 'down' : '')
const formatTime = (v) => (v || '').replace('T', ' ').slice(0, 16)

const renderCharts = () => {
  if (radarRef.value && data.skills.length) {
    if (!radarChart) radarChart = echarts.init(radarRef.value)
    const series = [{
      value: data.skills.map((s) => s.score),
      name: '综合掌握度',
      areaStyle: { color: 'rgba(59,130,246,.3)' },
      lineStyle: { color: '#3b82f6', width: 2 },
      itemStyle: { color: '#3b82f6' }
    }]
    radarChart.setOption({
      tooltip: { trigger: 'item' },
      radar: {
        indicator: data.skills.map((s) => ({ name: s.skillName, max: 100 })),
        radius: '65%',
        axisName: { color: '#6b7280', fontSize: 12 },
        splitArea: { areaStyle: { color: ['#fafafa', '#f3f4f6'] } }
      },
      series: [{ type: 'radar', data: series }]
    }, true)
  }
  if (lineRef.value && data.history.length >= 2) {
    if (!lineChart) lineChart = echarts.init(lineRef.value)
    lineChart.setOption({
      grid: { left: 36, right: 16, top: 20, bottom: 28 },
      tooltip: {
        trigger: 'axis',
        formatter: (params) => {
          const p = params[0]
          const h = data.history[p.dataIndex]
          return `${formatTime(h.time)}<br/>综合评分 <b>${h.overall}</b> · ${sourceText(h.source)}`
        }
      },
      xAxis: {
        type: 'category',
        data: data.history.map((h) => formatTime(h.time).slice(5, 10)),
        axisLabel: { color: '#6b7280' }
      },
      yAxis: { type: 'value', max: 100, splitLine: { lineStyle: { color: '#f3f4f6' } } },
      series: [{
        type: 'line', data: data.history.map((h) => h.overall), smooth: true,
        lineStyle: { width: 3, color: '#10b981' }, itemStyle: { color: '#10b981' },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(16,185,129,.18)' },
            { offset: 1, color: 'rgba(16,185,129,0)' }
          ])
        }
      }]
    }, true)
  }
}

const apply = async (res) => {
  Object.assign(data, res)
  await nextTick()
  renderCharts()
}

const load = async () => {
  await apply(await fetchSkills())
}

onMounted(() => {
  load()
  loadPlan()
})
window.addEventListener('resize', () => { radarChart?.resize(); lineChart?.resize() })
</script>

<style scoped>
.head-row { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 4px; }

.grid-2 { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; margin-bottom: 16px; }
@media (max-width: 1100px) { .grid-2 { grid-template-columns: 1fr; } }

.card-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px; }
.card-head h3 { margin: 0; font-size: 16px; }
.overall { font-size: 13px; color: var(--text-secondary); display: flex; align-items: baseline; gap: 4px; }
.overall b { font-size: 22px; }
.overall-self { margin-left: 10px; font-size: 12px; color: #9333ea; }

.chart { height: 280px; }
.chart-empty {
  height: 280px; display: flex; flex-direction: column; align-items: center; justify-content: center;
  color: #9ca3af; font-size: 14px; text-align: center; line-height: 1.8;
}
.chart-empty small { font-size: 12px; }
.chart-foot {
  display: flex; align-items: center; gap: 6px; flex-wrap: wrap;
  font-size: 12px; color: var(--text-secondary); margin-top: 4px;
}
.chart-foot-right { margin-left: auto; color: #9ca3af; }
.legend-dot { display: inline-block; width: 18px; height: 0; margin-left: 8px; }
.legend-dot.solid { border-top: 2px solid #3b82f6; }
.legend-dot.dashed { border-top: 2px dashed #9333ea; }

.skill-block { padding: 18px 0; border-bottom: 1px solid var(--border); }
.skill-block:last-child { border-bottom: none; padding-bottom: 4px; }
.skill-head { display: flex; align-items: flex-start; gap: 12px; margin-bottom: 10px; }
.skill-icon {
  width: 42px; height: 42px; border-radius: 10px; flex-shrink: 0;
  display: flex; align-items: center; justify-content: center; font-size: 20px;
  box-shadow: 0 8px 12px -3px rgba(0,0,0,.15);
}
.skill-title-box { flex: 1; min-width: 0; }
.skill-title-row { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.skill-name { font-weight: 700; font-size: 15px; color: #111827; }
.skill-desc { font-size: 12px; color: var(--text-secondary); margin-top: 3px; }
.skill-score-box { text-align: right; flex-shrink: 0; }
.skill-score-box b { display: block; font-size: 20px; color: #111827; line-height: 1.1; }
.skill-score-box span { font-size: 11px; color: #9ca3af; }

.skill-bar {
  position: relative; height: 8px; border-radius: 999px; background: #f3f4f6; margin-bottom: 10px;
}
.skill-bar-inner {
  height: 100%; border-radius: 999px;
  background: linear-gradient(to right, #3b82f6, #6366f1);
  transition: width .3s;
}
.skill-bar-self {
  position: absolute; top: -4px; width: 2px; height: 16px; margin-left: -1px;
  background: #9333ea; border-radius: 1px;
}
.skill-facts {
  display: flex; align-items: center; gap: 14px; flex-wrap: wrap;
  font-size: 12px; color: var(--text-secondary);
}
.skill-facts b { color: #111827; }
.gap-ok { color: #16a34a; }
.gap-over { color: #ca8a04; }
.gap-under { color: #2563eb; }
.skill-last { color: #9ca3af; }

.assess-btn { display: inline-flex; align-items: center; gap: 6px; }

.suggest-card, .events-card { margin-top: 16px; }
.suggest-card h3, .events-card h3 { margin: 0 0 4px; }
.suggest-head { display: flex; align-items: center; gap: 8px; }
.suggest-sub { color: var(--text-secondary); font-size: 13px; margin: 0 0 14px; }
.suggest-list { display: grid; gap: 10px; }
.suggest-item {
  background: linear-gradient(to right, #eff6ff, #eef2ff);
  border: 1px solid #dbeafe;
  border-radius: 12px; padding: 14px 16px; font-size: 14px; color: #1e40af;
}

.event-list { display: flex; flex-direction: column; margin-top: 10px; }
.event-item {
  display: flex; align-items: center; gap: 10px; flex-wrap: wrap;
  padding: 10px 0; border-bottom: 1px solid var(--border); font-size: 13px;
}
.event-item:last-child { border-bottom: none; }
.event-skill { font-weight: 600; color: #111827; min-width: 72px; }
.event-delta { font-variant-numeric: tabular-nums; color: var(--text-secondary); min-width: 64px; }
.event-delta.up { color: #16a34a; }
.event-delta.down { color: #dc2626; }
.event-note { flex: 1; min-width: 200px; color: #4b5563; }
.event-time { font-size: 12px; color: #9ca3af; }

.ai-card { margin-top: 16px; }
.ai-card h3 { margin: 0; }
.ai-head { display: flex; justify-content: space-between; align-items: center; gap: 14px; flex-wrap: wrap; margin-bottom: 14px; }
.ai-form { display: flex; gap: 10px; align-items: center; flex-wrap: wrap; }
.ai-empty {
  background: linear-gradient(to right, #faf5ff, #eff6ff);
  border: 1px dashed #e9d5ff;
  border-radius: 12px; padding: 18px; font-size: 13px; color: #7e22ce; text-align: center;
}
.ai-chips { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; margin-bottom: 12px; }
.ai-chips-label { font-size: 12px; color: var(--text-secondary); }
.goal-chip {
  font-size: 12px; color: #6b21a8; background: #faf5ff; border: 1px solid #e9d5ff;
  border-radius: 999px; padding: 3px 12px; cursor: pointer; transition: all .15s;
}
.goal-chip:hover { background: #9333ea; color: #fff; }
.ai-summary {
  background: linear-gradient(to right, #faf5ff, #eff6ff);
  border: 1px solid #e9d5ff;
  border-radius: 12px; padding: 14px 16px; font-size: 14px; color: #6b21a8; line-height: 1.7;
}
.ai-focus-row { display: flex; gap: 10px; margin-top: 12px; flex-wrap: wrap; }
.ai-focus {
  flex: 1; min-width: 200px; background: #f9fafb; border: 1px solid var(--border);
  border-radius: 12px; padding: 12px 14px; font-size: 13px;
}
.ai-focus-top { display: flex; justify-content: space-between; margin-bottom: 4px; }
.ai-focus-score { color: #9333ea; font-weight: 700; }
.ai-focus-reason { color: var(--text-secondary); font-size: 12px; }
.ai-stages { margin-top: 16px; display: flex; flex-direction: column; }
.ai-stage { display: flex; gap: 14px; }
.ai-stage-rail { display: flex; flex-direction: column; align-items: center; }
.ai-stage-dot {
  width: 26px; height: 26px; border-radius: 50%; flex-shrink: 0;
  background: linear-gradient(135deg, #2563eb, #9333ea); color: #fff;
  font-size: 13px; font-weight: 700; display: flex; align-items: center; justify-content: center;
}
.ai-stage-line { width: 2px; flex: 1; background: var(--border); margin: 4px 0; }
.ai-stage:last-child .ai-stage-line { display: none; }
.ai-proj { flex: 1; padding-bottom: 18px; }
.ai-proj-head { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.ai-stage-name { font-size: 12px; color: #9333ea; font-weight: 600; }
.ai-proj-title { font-size: 15px; }
.ai-match { font-size: 12px; color: var(--brand-blue); font-weight: 600; margin-left: auto; }
.ai-reasons { margin: 8px 0 0; padding-left: 18px; font-size: 13px; color: #374151; }
.ai-reasons li { margin-bottom: 2px; }
.ai-gaps {
  margin-top: 8px; font-size: 12px; color: #ca8a04;
  background: #fefce8; border-radius: 8px; padding: 6px 10px; display: inline-block;
}
.ai-next { display: flex; align-items: center; gap: 12px; margin-top: 10px; }
.ai-next-text { font-size: 13px; color: var(--text-secondary); flex: 1; }
.ai-meta { margin-top: 8px; font-size: 12px; color: #9ca3af; text-align: right; }

.assess-tip { font-size: 13px; color: var(--text-secondary); margin: 0 0 16px; line-height: 1.7; }
.quiz-btn { margin-left: auto; }
.muted { font-size: 12px; color: var(--text-secondary); }
.dim-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; }
.dim { display: flex; align-items: center; gap: 10px; text-align: left; padding: 10px 12px; border-radius: 12px; border: 1px solid var(--border); background: #fff; cursor: pointer; }
.dim.on { border-color: #6366f1; box-shadow: 0 0 0 3px #e0e7ff; background: #f5f3ff; }
.dim-icon { width: 32px; height: 32px; border-radius: 9px; display: grid; place-items: center; flex-shrink: 0; }
.dim-text { display: flex; flex-direction: column; }
.dim-text b { font-size: 14px; }
.dim-text small { font-size: 11.5px; color: var(--text-secondary); }
.quiz-loading { display: flex; align-items: center; gap: 16px; padding: 30px 10px; }
.spinner { width: 38px; height: 38px; border-radius: 50%; border: 3px solid #e0e7ff; border-top-color: #4f46e5; animation: spin 1s linear infinite; flex-shrink: 0; }
@keyframes spin { to { transform: rotate(360deg); } }
.quiz-progress { display: flex; justify-content: space-between; align-items: center; font-size: 13px; color: var(--text-secondary); margin-bottom: 12px; }
.q-text { font-size: 16px; font-weight: 700; line-height: 1.6; margin-bottom: 14px; }
.q-options { display: flex; flex-direction: column; gap: 8px; }
.q-opt { display: flex; align-items: center; gap: 12px; text-align: left; padding: 12px 14px; border-radius: 12px; border: 1px solid var(--border); background: #fff; cursor: pointer; font-size: 14px; line-height: 1.5; transition: all .15s; }
.q-opt:hover { border-color: #a5b4fc; background: #fafaff; }
.q-opt.on { border-color: #6366f1; background: #eef2ff; box-shadow: 0 0 0 3px #e0e7ff; }
.q-letter { width: 26px; height: 26px; border-radius: 50%; background: #f3f4f6; display: grid; place-items: center; font-weight: 700; font-size: 12px; flex-shrink: 0; }
.q-opt.on .q-letter { background: #6366f1; color: #fff; }
.q-dots { display: flex; gap: 6px; justify-content: center; margin-top: 16px; }
.q-dots i { width: 24px; height: 6px; border-radius: 3px; background: #e5e7eb; cursor: pointer; }
.q-dots i.done { background: #a5b4fc; } .q-dots i.cur { background: #4f46e5; }
.quiz-result { display: flex; gap: 16px; align-items: center; margin-bottom: 14px; }
.qr-score { width: 96px; height: 96px; border-radius: 20px; display: flex; flex-direction: column; align-items: center; justify-content: center; color: #fff; flex-shrink: 0; }
.qr-score b { font-size: 34px; line-height: 1; } .qr-score span { font-size: 11px; opacity: .9; margin-top: 4px; }
.qr-score.good { background: linear-gradient(135deg, #22c55e, #15803d); } .qr-score.mid { background: linear-gradient(135deg, #f59e0b, #b45309); } .qr-score.bad { background: linear-gradient(135deg, #ef4444, #991b1b); }
.qr-title { font-size: 15px; font-weight: 700; display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.qr-title b { color: #4f46e5; font-size: 18px; }
.qr-detail { display: flex; flex-direction: column; gap: 8px; max-height: 320px; overflow: auto; }
.qr-item { padding: 10px 12px; border-radius: 10px; background: #fef2f2; border: 1px solid #fecaca; }
.qr-item.ok { background: #f0fdf4; border-color: #bbf7d0; }
.qr-q { font-size: 13.5px; font-weight: 600; display: flex; gap: 8px; }
.qr-mark { font-weight: 800; color: #dc2626; } .qr-item.ok .qr-mark { color: #16a34a; }
.qr-ans { font-size: 12.5px; color: #374151; margin-top: 4px; }
.qr-given { color: #dc2626; }
.qr-explain { font-size: 12px; color: var(--text-secondary); margin-top: 4px; line-height: 1.6; }
</style>
