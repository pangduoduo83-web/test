<template>
  <div>
    <div class="head-row">
      <div>
        <h2 class="page-title">技能评估与提升</h2>
        <p class="page-subtitle">全面评估你的专业技能，获取个性化学习建议</p>
      </div>
      <button class="btn-gradient assess-btn" @click="openAssess">
        <Zap :size="15" /> 开始能力测评
      </button>
    </div>

    <div class="grid-2">
      <!-- 雷达图 -->
      <div class="card">
        <div class="card-head">
          <h3>综合能力雷达</h3>
          <div class="overall">综合评分 <b class="gradient-text">{{ data.overall }}</b></div>
        </div>
        <div ref="radarRef" class="chart"></div>
      </div>

      <!-- 成长曲线 -->
      <div class="card">
        <div class="card-head"><h3>学习成长曲线</h3></div>
        <div ref="lineRef" class="chart"></div>
      </div>
    </div>

    <!-- 技能详情 -->
    <div class="card">
      <div class="card-head"><h3>技能详情分析</h3></div>
      <div v-for="s in data.skills" :key="s.id" class="skill-block">
        <div class="skill-head">
          <span class="skill-icon" :style="{ background: meta(s.skillName).bg }">
            <component :is="meta(s.skillName).icon" :size="20" color="#fff" />
          </span>
          <div class="skill-title-box">
            <div class="skill-title-row">
              <span class="skill-name">{{ s.skillName }}</span>
              <span class="skill-level badge" :class="levelBadge(s.score)">{{ levelText(s.score) }}</span>
            </div>
            <div class="skill-desc">{{ meta(s.skillName).desc }}</div>
          </div>
          <div class="skill-score-box">
            <b>{{ s.score }}%</b>
            <span>掌握度</span>
          </div>
        </div>
        <div class="skill-bar">
          <div class="skill-bar-inner" :style="{ width: s.score + '%' }"></div>
        </div>
        <div class="sub-grid">
          <div v-for="sub in subSkills(s)" :key="sub.name" class="sub-box">
            <b>{{ sub.score }}%</b>
            <span>{{ sub.name }}</span>
          </div>
        </div>
        <div class="skill-suggest">
          <BookOpen :size="14" /> 推荐项目:
          <span class="suggest-links">{{ recommend(s.skillName) }}</span>
        </div>
      </div>
    </div>

    <!-- 学习建议 -->
    <div class="card suggest-card">
      <h3 class="suggest-head"><Target :size="17" color="#2563eb" /> 个性化学习建议</h3>
      <p class="suggest-sub">根据你的技能评估，我们为你制定了专属的学习计划</p>
      <div class="suggest-list">
        <div v-for="(s, i) in data.suggestions" :key="i" class="suggest-item">{{ s }}</div>
      </div>
    </div>

    <!-- 测评弹窗 -->
    <el-dialog v-model="assessVisible" title="能力测评" width="520px">
      <p class="assess-tip">请根据你的实际情况,拖动滑块自评各项技能掌握程度(0-100)。</p>
      <div v-for="(v, name) in assessForm" :key="name" class="assess-row">
        <span class="assess-name">{{ name }}</span>
        <el-slider v-model="assessForm[name]" :max="100" show-input :show-input-controls="false" size="small" />
      </div>
      <template #footer>
        <el-button @click="assessVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitAssess">提交测评</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { nextTick, onMounted, reactive, ref } from 'vue'
import * as echarts from 'echarts'
import { ElMessage } from 'element-plus'
import {
  Activity, BookOpen, CircuitBoard, Code, Cpu, Radio, Target, Wrench, Zap
} from 'lucide-vue-next'
import { fetchSkills, submitAssessment } from '../../api'

const data = reactive({ skills: [], overall: 0, suggestions: [] })
const radarRef = ref(null)
const lineRef = ref(null)
const assessVisible = ref(false)
const submitting = ref(false)
const assessForm = reactive({})
let radarChart = null
let lineChart = null

// 技能维度静态元数据:图标 / 说明 / 子技能拆解
const skillMeta = {
  '嵌入式开发': {
    icon: Cpu, bg: 'linear-gradient(135deg,#60a5fa,#2563eb)',
    desc: '掌握微控制器编程、外设驱动开发，实时操作系统等核心技能',
    subs: ['MCU编程', 'RTOS应用', '驱动开发', '调试技巧']
  },
  'PCB设计': {
    icon: CircuitBoard, bg: 'linear-gradient(135deg,#a78bfa,#7c3aed)',
    desc: '电路原理图设计、PCB布局布线、信号完整性分析',
    subs: ['原理图设计', 'PCB布局', '高速设计', '仿真分析']
  },
  '编程能力': {
    icon: Code, bg: 'linear-gradient(135deg,#4ade80,#16a34a)',
    desc: 'C/C++、Python等编程语言，数据结构与算法基础',
    subs: ['C/C++', 'Python', '数据结构', '算法设计']
  },
  '通信技术': {
    icon: Radio, bg: 'linear-gradient(135deg,#22d3ee,#0891b2)',
    desc: '有线/无线通信协议栈、组网与协议分析能力',
    subs: ['串口协议', 'SPI/I2C', '无线通信', '网络协议']
  },
  '信号处理': {
    icon: Activity, bg: 'linear-gradient(135deg,#facc15,#f59e0b)',
    desc: '信号采集、数字滤波、频谱分析与算法实现',
    subs: ['采样理论', '滤波器设计', 'FFT分析', 'MATLAB']
  },
  '硬件调试': {
    icon: Wrench, bg: 'linear-gradient(135deg,#fb923c,#ea580c)',
    desc: '仪器仪表使用、电路故障定位与焊接工艺',
    subs: ['仪器使用', '故障定位', '焊接工艺', '测试方案']
  }
}
const defaultMeta = {
  icon: BookOpen, bg: 'linear-gradient(135deg,#94a3b8,#64748b)',
  desc: '专业技能维度', subs: ['基础理论', '工程实践', '工具使用', '综合应用']
}
const meta = (name) => skillMeta[name] || defaultMeta

// 子技能分数:以总分为基线做固定偏移,仅用于展示拆解结构
const subOffsets = [8, -5, -10, 3]
const subSkills = (s) => meta(s.skillName).subs.map((name, i) => ({
  name,
  score: Math.max(5, Math.min(100, s.score + subOffsets[i % subOffsets.length]))
}))

const recommendMap = {
  '嵌入式开发': '智能温湿度监测系统、无人机飞控系统、智能家居中控',
  '编程能力': 'C语言进阶、Python数据分析、算法竞赛入门',
  '通信技术': '无线通信原理、物联网通信技术、LoRa组网实战',
  'PCB设计': '两层板设计入门、开关电源layout、高速PCB设计',
  '信号处理': '数字信号处理、MATLAB信号分析、简易示波器DIY',
  '硬件调试': '模拟电路调试、通信协议分析、示波器使用进阶'
}
const recommend = (name) => recommendMap[name] || '项目中心相关实战项目'

const levelText = (v) => v >= 80 ? '精通' : v >= 60 ? '熟练' : v >= 40 ? '进阶' : '入门'
const levelBadge = (v) => v >= 80 ? 'badge-purple' : v >= 60 ? 'badge-green' : v >= 40 ? 'badge-blue' : 'badge-gray'

const renderCharts = () => {
  if (radarRef.value) {
    if (!radarChart) radarChart = echarts.init(radarRef.value)
    radarChart.setOption({
      radar: {
        indicator: data.skills.map((s) => ({ name: s.skillName, max: 100 })),
        radius: '65%',
        axisName: { color: '#6b7280', fontSize: 12 },
        splitArea: { areaStyle: { color: ['#fafafa', '#f3f4f6'] } }
      },
      series: [{
        type: 'radar',
        data: [{
          value: data.skills.map((s) => s.score),
          name: '当前水平',
          areaStyle: { color: 'rgba(59,130,246,.3)' },
          lineStyle: { color: '#3b82f6', width: 2 },
          itemStyle: { color: '#3b82f6' }
        }]
      }]
    })
  }
  if (lineRef.value) {
    if (!lineChart) lineChart = echarts.init(lineRef.value)
    const base = Math.max(10, data.overall - 25)
    const growth = Array.from({ length: 6 }, (_, i) =>
      Math.min(100, Math.round(base + (data.overall - base) * (i / 5))))
    lineChart.setOption({
      grid: { left: 36, right: 16, top: 20, bottom: 28 },
      tooltip: { trigger: 'axis' },
      xAxis: { type: 'category', data: ['第1月', '第2月', '第3月', '第4月', '第5月', '第6月'], axisLabel: { color: '#6b7280' } },
      yAxis: { type: 'value', max: 100, splitLine: { lineStyle: { color: '#f3f4f6' } } },
      series: [{
        type: 'line', data: growth, smooth: true,
        lineStyle: { width: 3, color: '#10b981' }, itemStyle: { color: '#10b981' },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(16,185,129,.18)' },
            { offset: 1, color: 'rgba(16,185,129,0)' }
          ])
        }
      }]
    })
  }
}

const load = async () => {
  const res = await fetchSkills()
  Object.assign(data, res)
  await nextTick()
  renderCharts()
}

const openAssess = () => {
  Object.keys(assessForm).forEach((k) => delete assessForm[k])
  data.skills.forEach((s) => { assessForm[s.skillName] = s.score })
  assessVisible.value = true
}

const submitAssess = async () => {
  submitting.value = true
  try {
    const res = await submitAssessment({ ...assessForm })
    Object.assign(data, res)
    renderCharts()
    assessVisible.value = false
    ElMessage.success('测评完成,技能画像已更新!')
  } catch (e) { /* 已提示 */ } finally {
    submitting.value = false
  }
}

onMounted(load)
window.addEventListener('resize', () => { radarChart?.resize(); lineChart?.resize() })
</script>

<style scoped>
.head-row { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 4px; }

.grid-2 { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; margin-bottom: 16px; }
@media (max-width: 1100px) { .grid-2 { grid-template-columns: 1fr; } }

.card-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px; }
.card-head h3 { margin: 0; font-size: 16px; }
.overall { font-size: 13px; color: var(--text-secondary); }
.overall b { font-size: 22px; margin-left: 4px; }

.chart { height: 280px; }

.skill-block {
  padding: 18px 0; border-bottom: 1px solid var(--border);
}
.skill-block:last-child { border-bottom: none; padding-bottom: 4px; }
.skill-head { display: flex; align-items: flex-start; gap: 12px; margin-bottom: 10px; }
.skill-icon {
  width: 42px; height: 42px; border-radius: 10px; flex-shrink: 0;
  display: flex; align-items: center; justify-content: center; font-size: 20px;
  box-shadow: 0 8px 12px -3px rgba(0,0,0,.15);
}
.skill-title-box { flex: 1; min-width: 0; }
.skill-title-row { display: flex; align-items: center; gap: 10px; }
.skill-name { font-weight: 700; font-size: 15px; color: #111827; }
.skill-desc { font-size: 12px; color: var(--text-secondary); margin-top: 3px; }
.skill-score-box { text-align: right; flex-shrink: 0; }
.skill-score-box b { display: block; font-size: 20px; color: #111827; line-height: 1.1; }
.skill-score-box span { font-size: 11px; color: #9ca3af; }

.skill-bar { height: 8px; border-radius: 999px; background: #f3f4f6; overflow: hidden; margin-bottom: 12px; }
.skill-bar-inner {
  height: 100%; border-radius: 999px;
  background: linear-gradient(to right, #3b82f6, #6366f1);
  transition: width .3s;
}

.sub-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 10px; margin-bottom: 10px; }
@media (max-width: 900px) { .sub-grid { grid-template-columns: repeat(2, 1fr); } }
.sub-box {
  background: #f9fafb; border-radius: 10px;
  padding: 12px 8px; text-align: center;
  display: flex; flex-direction: column; gap: 2px;
}
.sub-box b { font-size: 16px; color: #111827; }
.sub-box span { font-size: 12px; color: var(--text-secondary); }

.skill-suggest {
  font-size: 13px; color: var(--text-secondary);
  display: flex; align-items: center; gap: 6px; flex-wrap: wrap;
}
.suggest-links { color: var(--brand-blue); }

.assess-btn { display: inline-flex; align-items: center; gap: 6px; }

.suggest-card { margin-top: 16px; }
.suggest-card h3 { margin: 0 0 4px; }
.suggest-head { display: flex; align-items: center; gap: 8px; }
.suggest-sub { color: var(--text-secondary); font-size: 13px; margin: 0 0 14px; }
.suggest-list { display: grid; gap: 10px; }
.suggest-item {
  background: linear-gradient(to right, #eff6ff, #eef2ff);
  border: 1px solid #dbeafe;
  border-radius: 12px; padding: 14px 16px; font-size: 14px; color: #1e40af;
}

.assess-tip { font-size: 13px; color: var(--text-secondary); margin: 0 0 16px; }
.assess-row { display: flex; align-items: center; gap: 14px; margin-bottom: 8px; }
.assess-name { width: 80px; flex-shrink: 0; font-size: 13px; }
.assess-row :deep(.el-slider) { flex: 1; }
</style>
