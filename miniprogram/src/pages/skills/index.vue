<template>
  <view class="page">
    <!-- 综合评分 + 雷达图 -->
    <view class="card overall-card">
      <view class="overall-head">
        <view>
          <text class="overall-label">综合能力评分</text>
          <view class="overall-num-row">
            <text class="overall-num">{{ overall }}</text>
            <text class="badge" :class="levelBadge(overall)">{{ levelText(overall) }}</text>
          </view>
        </view>
        <button class="btn-gradient assess-btn" @click="goAssess">开始测评</button>
      </view>
      <canvas type="2d" id="radar" class="radar-canvas" />
    </view>

    <!-- 学习建议 -->
    <view v-if="suggestions.length" class="card block">
      <text class="section-title">💡 个性化学习建议</text>
      <view v-for="(s, i) in suggestions" :key="i" class="sug-row">
        <text class="sug-idx">{{ i + 1 }}</text>
        <text class="sug-text">{{ s }}</text>
      </view>
    </view>

    <!-- AI 学习规划师 -->
    <view class="card block ai-card">
      <view class="ai-head">
        <text class="section-title">✨ AI 学习规划师</text>
        <button class="ai-gen-btn" :disabled="aiLoading" @click="genPlan">
          {{ aiLoading ? '生成中...' : plan ? '重新生成' : '生成学习路线' }}
        </button>
      </view>

      <view class="ai-goal-row">
        <input
          v-model="aiGoal"
          class="ai-goal-input"
          placeholder="学习目标(选填),如:想做物联网作品"
          placeholder-class="ph"
          :maxlength="100"
        />
        <picker mode="selector" :range="hourLabels" @change="aiHours = hourValues[$event.detail.value]">
          <view class="ai-hour-pick">每周{{ aiHours }}h</view>
        </picker>
      </view>

      <view v-if="!plan && !aiLoading" class="ai-empty muted">
        基于六维技能画像与全部实战项目,AI 将规划「基础补强 → 综合实践 → 挑战提升」三阶段路线
      </view>
      <view v-else-if="aiLoading" class="ai-empty muted">正在分析技能画像与项目库,大约需要 10~20 秒...</view>

      <template v-if="plan && !aiLoading">
        <view class="ai-summary">{{ plan.summary }}</view>

        <view v-if="plan.focusSkills && plan.focusSkills.length" class="ai-focus-list">
          <view v-for="f in plan.focusSkills" :key="f.name" class="ai-focus">
            <view class="ai-focus-top">
              <text class="ai-focus-name">{{ f.name }}</text>
              <text class="ai-focus-score">{{ f.currentScore }} → {{ f.targetScore }}</text>
            </view>
            <text class="ai-focus-reason muted">{{ f.reason }}</text>
          </view>
        </view>

        <view class="ai-stages">
          <view v-for="(p, i) in plan.recommendedProjects" :key="p.projectId" class="ai-stage">
            <view class="ai-rail">
              <view class="ai-dot">{{ p.stage }}</view>
              <view v-if="i < plan.recommendedProjects.length - 1" class="ai-line" />
            </view>
            <view class="ai-proj" @click="goProject(p.projectId)">
              <text class="ai-stage-name">{{ stageName(p.stage) }}</text>
              <view class="ai-proj-head">
                <text class="ai-proj-title ellipsis">{{ p.title }}</text>
                <text class="badge" :class="diffBadge(p.difficulty)">{{ p.difficulty }}</text>
              </view>
              <text class="ai-match">匹配度 {{ p.matchScore }}%</text>
              <view v-for="(r, ri) in p.reasons" :key="ri" class="ai-reason-row">
                <text class="ai-reason-dot">·</text>
                <text class="ai-reason-text">{{ r }}</text>
              </view>
              <view v-if="p.skillGaps && p.skillGaps.length" class="ai-gaps">
                待补齐: {{ p.skillGaps.join(' / ') }}
              </view>
              <view class="ai-next-row">
                <text class="ai-next muted ellipsis-2">下一步: {{ p.nextAction }}</text>
                <text class="ai-go">查看 ›</text>
              </view>
            </view>
          </view>
        </view>

        <text class="ai-meta muted">
          {{ plan.source === 'AI' ? '✨ 由 AI 结合智能匹配生成' : '⚙️ AI 未启用,已使用智能匹配结果' }}
          · {{ plan.generatedAt }}{{ plan.cached ? ' (缓存)' : '' }}
        </text>
      </template>
    </view>

    <!-- 技能明细 -->
    <view class="card block">
      <text class="section-title">🎯 技能维度明细</text>
      <view v-for="s in skills" :key="s.skillName" class="skill-item">
        <view class="skill-head">
          <view class="skill-name-row">
            <text class="skill-name">{{ s.skillName }}</text>
            <text class="badge" :class="levelBadge(s.score)">{{ levelText(s.score) }}</text>
          </view>
          <text class="skill-score">{{ s.score }}</text>
        </view>
        <view class="progress-track">
          <view class="progress-fill" :style="{ width: s.score + '%' }" />
        </view>
        <text class="skill-recommend muted">推荐:{{ recommend(s.skillName) }}</text>
      </view>
      <view v-if="skills.length === 0" class="empty-box">
        <text class="empty-icon">🎯</text>
        <text>先完成一次能力测评,生成你的技能画像</text>
      </view>
    </view>
  </view>
</template>

<script setup>
import { getCurrentInstance, nextTick, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { fetchSkills, fetchAiPlan, generateAiPlan } from '@/api'

const instance = getCurrentInstance()
const skills = ref([])
const overall = ref(0)
const suggestions = ref([])

// AI 学习规划师
const plan = ref(null)
const aiGoal = ref('')
const aiHours = ref(6)
const aiLoading = ref(false)
const hourValues = [4, 6, 8, 10, 15]
const hourLabels = hourValues.map((h) => `每周 ${h} 小时`)
let planLoaded = false

const stageName = (s) => (s === 1 ? '基础补强' : s === 2 ? '综合实践' : '挑战提升')
const diffBadge = (d) => (d === '入门' ? 'badge-green' : d === '进阶' ? 'badge-purple' : 'badge-red')

const goProject = (id) => uni.navigateTo({ url: `/pages/project-detail/index?id=${id}` })

const loadPlan = () => {
  fetchAiPlan()
    .then((d) => {
      if (d) plan.value = d
    })
    .catch(() => {})
}

const genPlan = async () => {
  aiLoading.value = true
  try {
    plan.value = await generateAiPlan({
      goal: aiGoal.value.trim() || undefined,
      weeklyHours: aiHours.value
    })
    if (plan.value && plan.value.source === 'RULE_FALLBACK') {
      uni.showToast({ title: 'AI 未启用,已用智能匹配结果', icon: 'none' })
    }
  } catch (e) {
    // 已提示
  } finally {
    aiLoading.value = false
  }
}

const recommendMap = {
  嵌入式开发: '智能温湿度监测系统、无人机飞控系统、智能家居中控',
  编程能力: 'C语言进阶、Python数据分析、算法竞赛入门',
  通信技术: '无线通信原理、物联网通信技术、LoRa组网实战',
  PCB设计: '两层板设计入门、开关电源layout、高速PCB设计',
  信号处理: '数字信号处理、MATLAB信号分析、简易示波器DIY',
  硬件调试: '模拟电路调试、通信协议分析、示波器使用进阶'
}
const recommend = (name) => recommendMap[name] || '项目中心相关实战项目'

const levelText = (v) => (v >= 80 ? '精通' : v >= 60 ? '熟练' : v >= 40 ? '进阶' : '入门')
const levelBadge = (v) => (v >= 80 ? 'badge-purple' : v >= 60 ? 'badge-green' : v >= 40 ? 'badge-blue' : 'badge-gray')

const load = async () => {
  try {
    const d = await fetchSkills()
    skills.value = d.skills || []
    overall.value = d.overall || 0
    suggestions.value = d.suggestions || []
    nextTick(() => setTimeout(drawRadar, 60))
  } catch (e) {
    // 已提示
  }
}

const drawRadar = () => {
  const query = uni.createSelectorQuery().in(instance.proxy)
  query
    .select('#radar')
    .fields({ node: true, size: true })
    .exec((res) => {
      if (!res || !res[0] || !res[0].node) return
      const { node: canvas, width, height } = res[0]
      const dpr = uni.getSystemInfoSync().pixelRatio || 2
      canvas.width = width * dpr
      canvas.height = height * dpr
      const ctx = canvas.getContext('2d')
      ctx.scale(dpr, dpr)
      ctx.clearRect(0, 0, width, height)

      const list = skills.value
      if (!list.length) return
      const n = list.length
      const cx = width / 2
      const cy = height / 2
      const radius = Math.min(width, height) / 2 - 42
      const angleAt = (i) => -Math.PI / 2 + (i * 2 * Math.PI) / n
      const pointAt = (i, r) => [cx + Math.cos(angleAt(i)) * r, cy + Math.sin(angleAt(i)) * r]

      // 网格(4 层)
      ctx.strokeStyle = '#e5e7eb'
      ctx.lineWidth = 1
      for (let level = 1; level <= 4; level++) {
        const r = (radius * level) / 4
        ctx.beginPath()
        for (let i = 0; i <= n; i++) {
          const [x, y] = pointAt(i % n, r)
          i === 0 ? ctx.moveTo(x, y) : ctx.lineTo(x, y)
        }
        ctx.stroke()
      }

      // 轴线
      for (let i = 0; i < n; i++) {
        const [x, y] = pointAt(i, radius)
        ctx.beginPath()
        ctx.moveTo(cx, cy)
        ctx.lineTo(x, y)
        ctx.stroke()
      }

      // 数据多边形
      ctx.beginPath()
      for (let i = 0; i <= n; i++) {
        const idx = i % n
        const r = (radius * Math.min(100, list[idx].score)) / 100
        const [x, y] = pointAt(idx, r)
        i === 0 ? ctx.moveTo(x, y) : ctx.lineTo(x, y)
      }
      ctx.closePath()
      ctx.fillStyle = 'rgba(37, 99, 235, 0.18)'
      ctx.fill()
      ctx.strokeStyle = '#2563eb'
      ctx.lineWidth = 2
      ctx.stroke()

      // 数据点
      ctx.fillStyle = '#2563eb'
      for (let i = 0; i < n; i++) {
        const r = (radius * Math.min(100, list[i].score)) / 100
        const [x, y] = pointAt(i, r)
        ctx.beginPath()
        ctx.arc(x, y, 3, 0, Math.PI * 2)
        ctx.fill()
      }

      // 维度标签
      ctx.fillStyle = '#6b7280'
      ctx.font = '11px sans-serif'
      for (let i = 0; i < n; i++) {
        const [x, y] = pointAt(i, radius + 18)
        const cos = Math.cos(angleAt(i))
        const sin = Math.sin(angleAt(i))
        ctx.textAlign = cos > 0.3 ? 'left' : cos < -0.3 ? 'right' : 'center'
        ctx.textBaseline = sin > 0.3 ? 'top' : sin < -0.3 ? 'bottom' : 'middle'
        ctx.fillText(`${list[i].skillName} ${list[i].score}`, x, y)
      }
    })
}

const goAssess = () => {
  uni.navigateTo({ url: '/pages/skills/assess' })
}

onShow(() => {
  load()
  if (!planLoaded) {
    planLoaded = true
    loadPlan()
  }
})
</script>

<style lang="scss" scoped>
.page {
  padding: 24rpx 24rpx 40rpx;
}

.overall-card {
  padding-bottom: 16rpx;
}

.overall-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.overall-label {
  font-size: 26rpx;
  color: $text-sub;
}

.overall-num-row {
  display: flex;
  align-items: center;
  gap: 16rpx;
  margin-top: 8rpx;
}

.overall-num {
  font-size: 64rpx;
  font-weight: 700;
  background: linear-gradient(90deg, #2563eb, #9333ea);
  -webkit-background-clip: text;
  color: transparent;
  line-height: 1.2;
}

.assess-btn {
  width: 220rpx;
  font-size: 27rpx;
  padding: 16rpx 0;
}

.radar-canvas {
  width: 100%;
  height: 480rpx;
  margin-top: 16rpx;
}

.block {
  margin-top: 24rpx;
}

.sug-row {
  display: flex;
  gap: 16rpx;
  margin-top: 22rpx;
  align-items: flex-start;
}

.sug-idx {
  width: 36rpx;
  height: 36rpx;
  border-radius: 50%;
  background: $purple-bg;
  color: $purple;
  font-size: 22rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  margin-top: 4rpx;
}

.sug-text {
  flex: 1;
  font-size: 27rpx;
}

.skill-item {
  margin-top: 32rpx;
}

.skill-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 14rpx;
}

.skill-name-row {
  display: flex;
  align-items: center;
  gap: 14rpx;
}

.skill-name {
  font-size: 29rpx;
  font-weight: 600;
}

.skill-score {
  font-size: 30rpx;
  font-weight: 700;
  color: $brand-blue;
}

.skill-recommend {
  display: block;
  font-size: 23rpx;
  margin-top: 12rpx;
}

/* ---- AI 学习规划师 ---- */
.ai-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.ai-gen-btn {
  background: linear-gradient(90deg, #2563eb, #9333ea);
  color: #fff;
  font-size: 24rpx;
  border-radius: $radius-pill;
  padding: 8rpx 30rpx;
  line-height: 1.7;
  margin: 0;

  &::after {
    border: none;
  }

  &[disabled] {
    opacity: 0.6;
    color: #fff;
  }
}

.ai-goal-row {
  display: flex;
  gap: 16rpx;
  margin-top: 24rpx;
}

.ai-goal-input {
  flex: 1;
  background: $gray-bg;
  border-radius: 14rpx;
  padding: 14rpx 22rpx;
  font-size: 25rpx;
  height: 68rpx;
  box-sizing: border-box;
}

.ai-hour-pick {
  background: $gray-bg;
  border-radius: 14rpx;
  padding: 14rpx 22rpx;
  font-size: 25rpx;
  height: 68rpx;
  box-sizing: border-box;
  display: flex;
  align-items: center;
  color: $text-sub;
}

.ph {
  color: $text-light;
}

.ai-empty {
  display: block;
  background: linear-gradient(90deg, #faf5ff, #eff6ff);
  border-radius: 16rpx;
  padding: 26rpx;
  font-size: 24rpx;
  text-align: center;
  margin-top: 20rpx;
  line-height: 1.7;
}

.ai-summary {
  display: block;
  background: linear-gradient(90deg, #faf5ff, #eff6ff);
  border-radius: 16rpx;
  padding: 22rpx 26rpx;
  font-size: 26rpx;
  color: #6b21a8;
  line-height: 1.7;
  margin-top: 20rpx;
}

.ai-focus-list {
  display: flex;
  flex-direction: column;
  gap: 14rpx;
  margin-top: 18rpx;
}

.ai-focus {
  background: $gray-bg;
  border-radius: 14rpx;
  padding: 18rpx 22rpx;
}

.ai-focus-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.ai-focus-name {
  font-size: 27rpx;
  font-weight: 600;
}

.ai-focus-score {
  font-size: 26rpx;
  font-weight: 700;
  color: $purple;
}

.ai-focus-reason {
  display: block;
  font-size: 22rpx;
  margin-top: 6rpx;
}

.ai-stages {
  margin-top: 24rpx;
}

.ai-stage {
  display: flex;
  gap: 20rpx;
}

.ai-rail {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.ai-dot {
  width: 48rpx;
  height: 48rpx;
  border-radius: 50%;
  background: linear-gradient(135deg, #2563eb, #9333ea);
  color: #fff;
  font-size: 24rpx;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.ai-line {
  width: 4rpx;
  flex: 1;
  background: $border-color;
  margin: 6rpx 0;
}

.ai-proj {
  flex: 1;
  background: $gray-bg;
  border-radius: 18rpx;
  padding: 22rpx 24rpx;
  margin-bottom: 22rpx;
  overflow: hidden;
}

.ai-stage-name {
  font-size: 22rpx;
  color: $purple;
  font-weight: 600;
}

.ai-proj-head {
  display: flex;
  align-items: center;
  gap: 14rpx;
  margin-top: 8rpx;
}

.ai-proj-title {
  font-size: 29rpx;
  font-weight: 600;
  flex: 1;
}

.ai-match {
  display: block;
  font-size: 23rpx;
  color: $brand-blue;
  font-weight: 600;
  margin-top: 8rpx;
}

.ai-reason-row {
  display: flex;
  gap: 10rpx;
  margin-top: 8rpx;
}

.ai-reason-dot {
  color: $brand-blue;
  font-weight: 700;
}

.ai-reason-text {
  flex: 1;
  font-size: 24rpx;
  color: $text-main;
}

.ai-gaps {
  margin-top: 12rpx;
  background: $yellow-bg;
  color: $yellow;
  font-size: 22rpx;
  border-radius: 10rpx;
  padding: 8rpx 16rpx;
  display: inline-block;
}

.ai-next-row {
  display: flex;
  align-items: center;
  gap: 14rpx;
  margin-top: 14rpx;
}

.ai-next {
  flex: 1;
  font-size: 23rpx;
}

.ai-go {
  color: $brand-blue;
  font-size: 24rpx;
  flex-shrink: 0;
}

.ai-meta {
  display: block;
  font-size: 22rpx;
  text-align: right;
  margin-top: 8rpx;
}
</style>
