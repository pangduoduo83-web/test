<template>
  <view class="page">
    <view class="tab-row">
      <view
        v-for="t in tabs"
        :key="t.key"
        class="pill"
        :class="{ active: activeTab === t.key }"
        @click="changeTab(t.key)"
      >
        {{ t.label }}
      </view>
    </view>

    <view v-if="loading" class="empty-box">
      <text class="empty-icon">⏳</text>
      <text>加载中...</text>
    </view>
    <view v-else-if="items.length === 0" class="empty-box">
      <text class="empty-icon">📝</text>
      <text>暂无成果提交</text>
    </view>
    <view v-else class="list">
      <view v-for="s in items" :key="s.id" class="card sub-card">
        <view class="sc-head">
          <view class="sc-user-row">
            <view class="sc-avatar">{{ (s.userName || '?')[0] }}</view>
            <view class="sc-info">
              <text class="sc-user">{{ s.userName }}</text>
              <text class="sc-proj muted ellipsis">《{{ s.projectTitle }}》</text>
            </view>
          </view>
          <text class="badge" :class="s.status === 'GRADED' ? 'badge-green' : 'badge-yellow'">
            {{ s.status === 'GRADED' ? `${s.score} 分` : '待评分' }}
          </text>
        </view>

        <text class="sc-content">{{ s.content }}</text>
        <image
          v-if="s.attachmentUrl"
          :src="fullUrl(s.attachmentUrl)"
          class="sc-img"
          mode="widthFix"
          @click="preview(s)"
        />
        <text class="sc-time muted">提交于 {{ relativeTime(s.submittedAt) }}</text>

        <view v-if="s.status === 'GRADED'" class="graded-box">
          <text class="graded-text">{{ s.feedback || '无评语' }}</text>
          <text class="muted">{{ s.graderName }} 评于 {{ relativeTime(s.gradedAt) }}</text>
        </view>
        <view v-else class="sc-actions">
          <button class="action-btn grade" @click="openGrade(s)">评分</button>
        </view>
      </view>
      <view class="list-end muted">共 {{ items.length }} 条</view>
    </view>

    <!-- 评分弹层 -->
    <view v-if="grading" class="mask" @click="grading = null">
      <view class="modal" @click.stop>
        <text class="modal-title">成果评分</text>
        <text class="modal-sub muted">{{ grading.userName }} · 《{{ grading.projectTitle }}》</text>

        <button class="ai-btn" :disabled="aiReviewing" @click="runAiReview">
          {{ aiReviewing ? 'AI 分析中...' : '✨ AI 预评审(建议分+评语草稿)' }}
        </button>
        <view v-if="aiResult" class="ai-box">
          <text class="ai-box-summary">{{ aiResult.summary }}</text>
          <text v-if="aiResult.strengths && aiResult.strengths.length" class="ai-box-line good">✓ {{ aiResult.strengths.join(';') }}</text>
          <text v-if="aiResult.weaknesses && aiResult.weaknesses.length" class="ai-box-line bad">△ {{ aiResult.weaknesses.join(';') }}</text>
          <text class="ai-box-note muted">{{ aiResult.note }}</text>
        </view>

        <view class="score-row">
          <text class="score-num" :class="{ pass: score >= 60 }">{{ score }}</text>
          <text class="score-tip">{{ score >= 60 ? '及格,项目将判定完成' : '不及格,学生可再次提交' }}</text>
        </view>
        <slider
          :value="score"
          :min="0"
          :max="100"
          :step="5"
          activeColor="#2563eb"
          backgroundColor="#e5e7eb"
          block-size="22"
          @change="score = $event.detail.value"
          @changing="score = $event.detail.value"
        />

        <textarea
          v-model="feedback"
          class="field-textarea"
          placeholder="评语(选填):肯定亮点,指出改进方向..."
          placeholder-class="ph"
          :maxlength="300"
        />

        <view class="btn-row">
          <button class="btn-plain half" @click="grading = null">取消</button>
          <button class="btn-gradient half" :disabled="submitting" @click="confirmGrade">
            {{ submitting ? '提交中...' : '确认评分' }}
          </button>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onShow, onPullDownRefresh } from '@dcloudio/uni-app'
import { adminListSubmissions, adminGradeSubmission, adminAiReview } from '@/api'
import { fullUrl } from '@/config'
import { relativeTime } from '@/utils/format'

const tabs = [
  { key: 'SUBMITTED', label: '待评分' },
  { key: 'GRADED', label: '已评分' },
  { key: 'ALL', label: '全部' }
]

const activeTab = ref('SUBMITTED')
const items = ref([])
const loading = ref(true)
const grading = ref(null)
const score = ref(80)
const feedback = ref('')
const submitting = ref(false)

const load = async () => {
  loading.value = true
  try {
    items.value = await adminListSubmissions({ status: activeTab.value === 'ALL' ? '' : activeTab.value })
  } catch (e) {
    // 已提示
  } finally {
    loading.value = false
  }
}

const changeTab = (k) => {
  activeTab.value = k
  load()
}

const preview = (s) => {
  uni.previewImage({ urls: [fullUrl(s.attachmentUrl)] })
}

const aiReviewing = ref(false)
const aiResult = ref(null)

const openGrade = (s) => {
  grading.value = s
  score.value = 80
  feedback.value = ''
  aiResult.value = null
}

const runAiReview = async () => {
  aiReviewing.value = true
  try {
    const res = await adminAiReview(grading.value.id)
    aiResult.value = res
    score.value = res.suggestedScore
    if (res.feedbackDraft) feedback.value = res.feedbackDraft
    uni.showToast({ title: '建议已填入,可调整', icon: 'none' })
  } catch (e) {
    // 已提示
  } finally {
    aiReviewing.value = false
  }
}

const confirmGrade = async () => {
  submitting.value = true
  try {
    await adminGradeSubmission(grading.value.id, {
      score: score.value,
      feedback: feedback.value.trim() || undefined
    })
    uni.showToast({ title: '评分完成', icon: 'success' })
    grading.value = null
    load()
  } catch (e) {
    // 已提示
  } finally {
    submitting.value = false
  }
}

onShow(load)

onPullDownRefresh(async () => {
  await load()
  uni.stopPullDownRefresh()
})
</script>

<style lang="scss" scoped>
.page {
  padding: 24rpx 24rpx 40rpx;
}

.tab-row {
  display: flex;
  gap: 16rpx;
}

.list {
  display: flex;
  flex-direction: column;
  gap: 24rpx;
  margin-top: 24rpx;
}

.sub-card {
  padding: 28rpx 32rpx;
}

.sc-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16rpx;
}

.sc-user-row {
  display: flex;
  align-items: center;
  gap: 16rpx;
  flex: 1;
  overflow: hidden;
}

.sc-avatar {
  width: 64rpx;
  height: 64rpx;
  border-radius: 50%;
  background: linear-gradient(135deg, #3b82f6, #8b5cf6);
  color: #fff;
  font-size: 26rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.sc-info {
  flex: 1;
  overflow: hidden;
}

.sc-user {
  display: block;
  font-size: 29rpx;
  font-weight: 600;
}

.sc-proj {
  display: block;
  font-size: 23rpx;
}

.sc-content {
  display: block;
  font-size: 27rpx;
  line-height: 1.7;
  background: $gray-bg;
  border-radius: 16rpx;
  padding: 20rpx 24rpx;
  margin-top: 20rpx;
}

.sc-img {
  width: 100%;
  border-radius: 16rpx;
  margin-top: 16rpx;
}

.sc-time {
  display: block;
  font-size: 22rpx;
  margin-top: 14rpx;
}

.graded-box {
  margin-top: 16rpx;
  background: $green-bg;
  border-radius: 16rpx;
  padding: 20rpx 24rpx;
  display: flex;
  flex-direction: column;
  gap: 8rpx;
}

.graded-text {
  font-size: 26rpx;
}

.sc-actions {
  margin-top: 20rpx;
  display: flex;
  justify-content: flex-end;
}

.action-btn {
  font-size: 26rpx;
  border-radius: $radius-pill;
  padding: 8rpx 40rpx;
  line-height: 1.7;
  margin: 0;

  &::after {
    border: none;
  }

  &.grade {
    background: linear-gradient(90deg, #2563eb, #9333ea);
    color: #fff;
  }
}

.list-end {
  text-align: center;
  padding: 24rpx 0;
  font-size: 24rpx;
}

.mask {
  position: fixed;
  inset: 0;
  background: rgba(17, 24, 39, 0.5);
  z-index: 100;
  display: flex;
  align-items: center;
  justify-content: center;
}

.modal {
  width: 640rpx;
  background: #fff;
  border-radius: 28rpx;
  padding: 40rpx 36rpx;
}

.modal-title {
  display: block;
  font-size: 32rpx;
  font-weight: 700;
  text-align: center;
}

.modal-sub {
  display: block;
  text-align: center;
  font-size: 24rpx;
  margin: 8rpx 0 24rpx;
}

.ai-btn {
  background: linear-gradient(90deg, #7c3aed, #9333ea);
  color: #fff;
  font-size: 25rpx;
  border-radius: 14rpx;
  padding: 14rpx 0;
  line-height: 1.6;
  margin-bottom: 20rpx;

  &::after {
    border: none;
  }

  &[disabled] {
    opacity: 0.6;
    color: #fff;
  }
}

.ai-box {
  background: linear-gradient(90deg, #faf5ff, #eff6ff);
  border-radius: 14rpx;
  padding: 18rpx 22rpx;
  margin-bottom: 20rpx;
  display: flex;
  flex-direction: column;
  gap: 8rpx;
}

.ai-box-summary {
  font-size: 24rpx;
  color: #6b21a8;
  line-height: 1.6;
}

.ai-box-line {
  font-size: 23rpx;

  &.good {
    color: $green;
  }

  &.bad {
    color: $yellow;
  }
}

.ai-box-note {
  font-size: 20rpx;
}

.score-row {
  display: flex;
  align-items: baseline;
  gap: 20rpx;
  margin-bottom: 8rpx;
}

.score-num {
  font-size: 64rpx;
  font-weight: 700;
  color: $red;

  &.pass {
    color: $green;
  }
}

.score-tip {
  font-size: 24rpx;
  color: $text-sub;
}

.field-textarea {
  background: $gray-bg;
  border-radius: 16rpx;
  padding: 20rpx 24rpx;
  font-size: 27rpx;
  width: 100%;
  height: 160rpx;
  box-sizing: border-box;
  margin-top: 24rpx;
}

.ph {
  color: $text-light;
}

.btn-row {
  display: flex;
  gap: 24rpx;
  margin-top: 32rpx;

  .half {
    flex: 1;
  }
}
</style>
