<template>
  <view class="page">
    <view class="card intro-card">
      <text class="intro-title">能力自测评</text>
      <text class="intro-desc">请根据自己的真实水平拖动滑块打分(0-100),提交后将更新技能画像与学习建议。</text>
    </view>

    <view class="card block">
      <view v-for="(item, i) in form" :key="item.name" class="assess-item">
        <view class="ai-head">
          <text class="ai-name">{{ item.name }}</text>
          <text class="ai-score">{{ item.score }} 分 · {{ levelText(item.score) }}</text>
        </view>
        <slider
          :value="item.score"
          :min="0"
          :max="100"
          :step="1"
          activeColor="#2563eb"
          backgroundColor="#e5e7eb"
          block-size="22"
          @change="onSlide(i, $event)"
          @changing="onSlide(i, $event)"
        />
        <view class="ai-scale">
          <text class="scale-text">入门</text>
          <text class="scale-text">进阶</text>
          <text class="scale-text">熟练</text>
          <text class="scale-text">精通</text>
        </view>
      </view>
    </view>

    <button class="btn-gradient submit-btn" :disabled="submitting" @click="submit">
      {{ submitting ? '提交中...' : '提交测评' }}
    </button>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { fetchSkills, submitAssessment } from '@/api'

const defaultDims = ['嵌入式开发', '编程能力', '通信技术', 'PCB设计', '信号处理', '硬件调试']
const form = ref(defaultDims.map((name) => ({ name, score: 30 })))
const submitting = ref(false)

const levelText = (v) => (v >= 80 ? '精通' : v >= 60 ? '熟练' : v >= 40 ? '进阶' : '入门')

onLoad(async () => {
  try {
    const d = await fetchSkills()
    if (d.skills && d.skills.length) {
      form.value = d.skills.map((s) => ({ name: s.skillName, score: s.score }))
    }
  } catch (e) {
    // 用默认维度
  }
})

const onSlide = (i, e) => {
  form.value[i].score = e.detail.value
}

const submit = async () => {
  submitting.value = true
  try {
    const scores = {}
    form.value.forEach((item) => {
      scores[item.name] = item.score
    })
    await submitAssessment(scores)
    uni.showToast({ title: '测评完成,画像已更新', icon: 'success' })
    setTimeout(() => uni.navigateBack(), 600)
  } catch (e) {
    // 已提示
  } finally {
    submitting.value = false
  }
}
</script>

<style lang="scss" scoped>
.page {
  padding: 24rpx 24rpx 60rpx;
}

.intro-card {
  background: linear-gradient(135deg, #eff6ff, #f5f3ff);
}

.intro-title {
  display: block;
  font-size: 32rpx;
  font-weight: 700;
}

.intro-desc {
  display: block;
  font-size: 25rpx;
  color: $text-sub;
  margin-top: 12rpx;
}

.block {
  margin-top: 24rpx;
}

.assess-item {
  margin-bottom: 44rpx;

  &:last-child {
    margin-bottom: 8rpx;
  }
}

.ai-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 6rpx;
}

.ai-name {
  font-size: 29rpx;
  font-weight: 600;
}

.ai-score {
  font-size: 25rpx;
  color: $brand-blue;
  font-weight: 600;
}

.ai-scale {
  display: flex;
  justify-content: space-between;
  padding: 0 12rpx;
}

.scale-text {
  font-size: 21rpx;
  color: $text-light;
}

.submit-btn {
  margin-top: 32rpx;
}
</style>
