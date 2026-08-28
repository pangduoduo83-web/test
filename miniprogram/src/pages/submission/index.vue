<template>
  <view class="page">
    <view class="card head-card">
      <text class="p-title">{{ projectTitle }}</text>
      <text class="p-sub muted">完成项目后在此提交成果,管理员评分 ≥60 分即判定项目完成并获得经验值</text>
    </view>

    <!-- 分阶段考核模式 -->
    <template v-if="assessments.length">
      <view class="card block">
        <view class="last-head">
          <text class="section-title">分阶段考核({{ assessments.length }} 项)</text>
          <text v-if="overallScore !== null" class="badge" :class="overallScore >= 60 ? 'badge-green' : 'badge-red'">
            综合 {{ overallScore }} 分
          </text>
        </view>
        <text class="muted assess-tip">每项单独评分,全部评完自动按权重计算综合分,≥60 判定项目完成</text>

        <view v-for="a in assessments" :key="a.name" class="assess-item">
          <view class="assess-head">
            <text class="assess-name">{{ a.name }}</text>
            <text class="chip">权重 {{ a.weight }}%</text>
            <text v-if="latestFor(a.name)" class="badge"
                  :class="latestFor(a.name).status === 'GRADED'
                    ? (latestFor(a.name).score >= 60 ? 'badge-green' : 'badge-red') : 'badge-yellow'">
              {{ latestFor(a.name).status === 'GRADED' ? `已评 ${latestFor(a.name).score}` : '评审中' }}
            </text>
            <text v-else class="badge badge-gray">未提交</text>
          </view>
          <text v-if="a.desc" class="assess-desc muted">{{ a.desc }}</text>
          <view v-if="latestFor(a.name) && latestFor(a.name).feedback" class="assess-feedback">
            评语:{{ latestFor(a.name).feedback }}
          </view>
          <button v-if="canSubmitFor(a.name)" class="assess-submit-btn"
                  @click="toggleForm(a.name)">
            {{ activeAssessment === a.name ? '收起' : latestFor(a.name) ? '再次提交' : '提交该项成果' }}
          </button>

          <view v-if="activeAssessment === a.name" class="assess-form">
            <textarea
              v-model="content"
              class="field-textarea"
              :placeholder="`描述「${a.name}」的完成情况...`"
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
            <button class="btn-gradient" :disabled="submitting" @click="submit(a.name)">
              {{ submitting ? '提交中...' : `提交「${a.name}」` }}
            </button>
          </view>
        </view>
      </view>
    </template>

    <!-- 最近一次提交 -->
    <view v-if="!assessments.length && last" class="card block">
      <view class="last-head">
        <text class="section-title">最近一次提交</text>
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

    <!-- 提交表单(整体单一成果模式) -->
    <view v-if="!assessments.length && canSubmit" class="card block">
      <text class="section-title">{{ last ? '再次提交' : '提交成果' }}</text>
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
      <button class="btn-gradient" :disabled="submitting" @click="submit('')">
        {{ submitting ? '提交中...' : '提交成果' }}
      </button>
    </view>

    <view v-else-if="!assessments.length && last && last.status === 'SUBMITTED'" class="card block waiting-card">
      <uni-icons type="spinner-cycle" size="36" color="#d1d5db" />
      <text class="waiting-text">成果评审中,请耐心等待管理员评分</text>
      <text class="muted">评分结果将通过站内通知告知</text>
    </view>
  </view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { fetchMySubmission, fetchMySubmissions, fetchProjectDetail, submitWork } from '@/api'
import { uploadImage } from '@/utils/request'
import { fullUrl } from '@/config'
import { asList, relativeTime } from '@/utils/format'

const projectId = ref(null)
const projectTitle = ref('')
const last = ref(null)
const subs = ref([])
const assessments = ref([])
const activeAssessment = ref('')
const content = ref('')
const attachmentUrl = ref('')
const submitting = ref(false)

const canSubmit = computed(() => !last.value || last.value.status === 'GRADED')

const latestFor = (name) => subs.value.find((s) => (s.assessmentName || '') === name) || null

const canSubmitFor = (name) => {
  const latest = latestFor(name)
  return !latest || latest.status === 'GRADED'
}

const overallScore = computed(() => {
  if (!assessments.value.length) return null
  let total = 0
  for (const a of assessments.value) {
    const graded = subs.value.find(
      (s) => (s.assessmentName || '') === a.name && s.status === 'GRADED')
    if (!graded) return null
    total += graded.score * a.weight / 100
  }
  return Math.round(total)
})

const toggleForm = (name) => {
  activeAssessment.value = activeAssessment.value === name ? '' : name
  content.value = ''
  attachmentUrl.value = ''
}

const load = async () => {
  try {
    last.value = await fetchMySubmission(projectId.value)
    subs.value = (await fetchMySubmissions(projectId.value)) || []
  } catch (e) {
    // 静默
  }
}

onLoad((options) => {
  projectId.value = options.projectId
  projectTitle.value = decodeURIComponent(options.title || '项目成果')
  load()
  fetchProjectDetail(options.projectId)
    .then((d) => {
      assessments.value = asList(d.project?.assessments)
    })
    .catch(() => {})
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

const submit = async (assessName) => {
  if (!content.value.trim()) {
    uni.showToast({ title: '请填写成果说明', icon: 'none' })
    return
  }
  submitting.value = true
  try {
    await submitWork(projectId.value, {
      content: content.value.trim(),
      attachmentUrl: attachmentUrl.value || undefined,
      assessmentName: assessName || undefined
    })
    uni.showToast({ title: '提交成功,等待评审', icon: 'success' })
    content.value = ''
    attachmentUrl.value = ''
    activeAssessment.value = ''
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

/* ---- 分阶段考核 ---- */
.assess-tip {
  display: block;
  font-size: 23rpx;
  margin-top: 10rpx;
}

.assess-item {
  border: 2rpx solid $border-color;
  border-radius: 18rpx;
  padding: 22rpx;
  margin-top: 20rpx;
}

.assess-head {
  display: flex;
  align-items: center;
  gap: 12rpx;
  flex-wrap: wrap;
}

.assess-name {
  font-size: 29rpx;
  font-weight: 600;
}

.assess-desc {
  display: block;
  font-size: 23rpx;
  margin-top: 8rpx;
}

.assess-feedback {
  margin-top: 12rpx;
  background: $gray-bg;
  border-radius: 12rpx;
  padding: 14rpx 18rpx;
  font-size: 24rpx;
  color: $text-sub;
}

.assess-submit-btn {
  margin-top: 16rpx;
  background: $blue-bg;
  color: $blue;
  font-size: 25rpx;
  border-radius: 14rpx;
  padding: 12rpx 0;
  line-height: 1.6;

  &::after {
    border: none;
  }
}

.assess-form {
  margin-top: 16rpx;
}

.waiting-text {
  font-size: 29rpx;
  font-weight: 600;
}
</style>
