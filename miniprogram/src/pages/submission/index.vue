<template>
  <view class="page">
    <view class="card head-card">
      <text class="p-title">{{ projectTitle }}</text>
      <text class="p-sub muted">完成项目后在此提交成果,管理员评分 ≥60 分即判定项目完成并获得经验值</text>
    </view>

    <!-- 最近一次提交 -->
    <view v-if="last" class="card block">
      <view class="last-head">
        <text class="section-title">📄 最近一次提交</text>
        <text class="badge" :class="last.status === 'GRADED' ? 'badge-green' : 'badge-yellow'">
          {{ last.status === 'GRADED' ? '已评分' : '评审中' }}
        </text>
      </view>

      <view v-if="last.status === 'GRADED'" class="grade-box" :class="{ pass: last.score >= 60 }">
        <text class="grade-score">{{ last.score }}</text>
        <view class="grade-info">
          <text class="grade-result">{{ last.score >= 60 ? '恭喜,评审通过!' : '未达标,可修改后再次提交' }}</text>
          <text v-if="last.feedback" class="grade-feedback">评语:{{ last.feedback }}</text>
          <text class="grade-meta muted">{{ last.graderName }} 评于 {{ relativeTime(last.gradedAt) }}</text>
        </view>
      </view>

      <text class="last-content">{{ last.content }}</text>
      <image
        v-if="last.attachmentUrl"
        :src="fullUrl(last.attachmentUrl)"
        class="last-img"
        mode="widthFix"
        @click="previewLast"
      />
      <text class="last-time muted">提交于 {{ relativeTime(last.submittedAt) }}</text>
    </view>

    <!-- 提交表单 -->
    <view v-if="canSubmit" class="card block">
      <text class="section-title">✍️ {{ last ? '再次提交' : '提交成果' }}</text>
      <textarea
        v-model="content"
        class="field-textarea"
        placeholder="描述你的实现思路、完成情况、遇到的问题与解决办法..."
        placeholder-class="ph"
        :maxlength="1000"
      />
      <view class="attach-row">
        <view v-if="attachmentUrl" class="attach-preview">
          <image :src="fullUrl(attachmentUrl)" class="attach-img" mode="aspectFill" @click="preview" />
          <text class="attach-del" @click="attachmentUrl = ''">×</text>
        </view>
        <view v-else class="attach-add" @click="chooseShot">
          <text class="attach-plus">+</text>
          <text class="attach-text">成果截图(选填)</text>
        </view>
      </view>
      <button class="btn-gradient" :disabled="submitting" @click="submit">
        {{ submitting ? '提交中...' : '提交成果' }}
      </button>
    </view>

    <view v-else-if="last && last.status === 'SUBMITTED'" class="card block waiting-card">
      <text class="waiting-icon">⏳</text>
      <text class="waiting-text">成果评审中,请耐心等待管理员评分</text>
      <text class="muted">评分结果将通过站内通知告知</text>
    </view>
  </view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { fetchMySubmission, submitWork } from '@/api'
import { uploadImage } from '@/utils/request'
import { fullUrl } from '@/config'
import { relativeTime } from '@/utils/format'

const projectId = ref(null)
const projectTitle = ref('')
const last = ref(null)
const content = ref('')
const attachmentUrl = ref('')
const submitting = ref(false)

const canSubmit = computed(() => !last.value || last.value.status === 'GRADED')

const load = async () => {
  try {
    last.value = await fetchMySubmission(projectId.value)
  } catch (e) {
    // 静默
  }
}

onLoad((options) => {
  projectId.value = options.projectId
  projectTitle.value = decodeURIComponent(options.title || '项目成果')
  load()
})

const chooseShot = () => {
  uni.chooseImage({
    count: 1,
    sizeType: ['compressed'],
    success: async (res) => {
      uni.showLoading({ title: '上传中...' })
      try {
        const d = await uploadImage(res.tempFilePaths[0])
        attachmentUrl.value = d.url
      } catch (e) {
        // 已提示
      } finally {
        uni.hideLoading()
      }
    }
  })
}

const preview = () => {
  uni.previewImage({ urls: [fullUrl(attachmentUrl.value)] })
}

const previewLast = () => {
  uni.previewImage({ urls: [fullUrl(last.value.attachmentUrl)] })
}

const submit = async () => {
  if (!content.value.trim()) {
    uni.showToast({ title: '请填写成果说明', icon: 'none' })
    return
  }
  submitting.value = true
  try {
    await submitWork(projectId.value, {
      content: content.value.trim(),
      attachmentUrl: attachmentUrl.value || undefined
    })
    uni.showToast({ title: '提交成功,等待评审', icon: 'success' })
    content.value = ''
    attachmentUrl.value = ''
    load()
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

.p-title {
  font-size: 34rpx;
  font-weight: 700;
  display: block;
}

.p-sub {
  display: block;
  font-size: 24rpx;
  margin-top: 10rpx;
}

.block {
  margin-top: 24rpx;
}

.last-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24rpx;
}

.grade-box {
  display: flex;
  gap: 24rpx;
  background: $red-bg;
  border-radius: 20rpx;
  padding: 24rpx;
  margin-bottom: 24rpx;
  align-items: center;

  &.pass {
    background: $green-bg;
  }
}

.grade-score {
  font-size: 64rpx;
  font-weight: 700;
  color: $text-main;
  flex-shrink: 0;
}

.grade-info {
  flex: 1;
}

.grade-result {
  display: block;
  font-size: 28rpx;
  font-weight: 600;
}

.grade-feedback {
  display: block;
  font-size: 25rpx;
  color: $text-sub;
  margin-top: 6rpx;
}

.grade-meta {
  display: block;
  font-size: 22rpx;
  margin-top: 6rpx;
}

.last-content {
  display: block;
  font-size: 27rpx;
  line-height: 1.8;
  background: $gray-bg;
  border-radius: 16rpx;
  padding: 20rpx 24rpx;
}

.last-img {
  width: 100%;
  border-radius: 16rpx;
  margin-top: 20rpx;
}

.last-time {
  display: block;
  font-size: 22rpx;
  margin-top: 16rpx;
}

.field-textarea {
  background: $gray-bg;
  border-radius: 16rpx;
  padding: 20rpx 24rpx;
  font-size: 28rpx;
  width: 100%;
  height: 220rpx;
  box-sizing: border-box;
  margin-top: 20rpx;
}

.ph {
  color: $text-light;
}

.attach-row {
  margin: 24rpx 0 32rpx;
}

.attach-add {
  width: 200rpx;
  height: 200rpx;
  border: 2rpx dashed $border-color;
  border-radius: 16rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8rpx;
}

.attach-plus {
  font-size: 56rpx;
  color: $text-light;
  line-height: 1;
}

.attach-text {
  font-size: 22rpx;
  color: $text-light;
}

.attach-preview {
  position: relative;
  width: 200rpx;
}

.attach-img {
  width: 200rpx;
  height: 200rpx;
  border-radius: 16rpx;
}

.attach-del {
  position: absolute;
  top: -14rpx;
  right: -14rpx;
  width: 40rpx;
  height: 40rpx;
  border-radius: 50%;
  background: rgba(17, 24, 39, 0.7);
  color: #fff;
  font-size: 28rpx;
  text-align: center;
  line-height: 38rpx;
}

.waiting-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12rpx;
  padding: 60rpx 32rpx;
}

.waiting-icon {
  font-size: 64rpx;
}

.waiting-text {
  font-size: 29rpx;
  font-weight: 600;
}
</style>
