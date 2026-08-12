<template>
  <view class="page">
    <view class="card head-card">
      <text class="p-title">{{ title }}</text>
      <text class="muted">共 {{ items.length }} 名学生报名 · 平均进度 {{ avgProgress }}%</text>
    </view>

    <view v-if="loading" class="empty-box">
      <text class="empty-icon">⏳</text>
      <text>加载中...</text>
    </view>
    <view v-else-if="items.length === 0" class="empty-box">
      <text class="empty-icon">👨‍🎓</text>
      <text>还没有学生报名该项目</text>
    </view>
    <view v-else class="list">
      <view v-for="s in items" :key="s.enrollmentId" class="card stu-card">
        <view class="stu-head">
          <view class="stu-avatar">{{ (s.studentName || '?')[0] }}</view>
          <view class="stu-info">
            <text class="stu-name">{{ s.studentName }}</text>
            <text class="stu-sub muted">{{ s.studentNo }} · {{ s.major }}</text>
          </view>
          <text class="badge" :class="s.status === 'COMPLETED' ? 'badge-green' : 'badge-blue'">
            {{ s.status === 'COMPLETED' ? '已完成' : '进行中' }}
          </text>
        </view>
        <view class="progress-row">
          <view class="progress-track stu-track">
            <view class="progress-fill" :style="{ width: (s.progress || 0) + '%' }" />
          </view>
          <text class="progress-num">{{ s.progress || 0 }}%</text>
        </view>
        <view class="stu-meta">
          <text class="muted ellipsis">当前:{{ s.currentTask || '尚未开始' }}</text>
          <text class="muted">报名 {{ relativeTime(s.enrolledAt) }}{{ s.deadline ? ' · 截止 ' + formatDate(s.deadline) : '' }}</text>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onLoad, onPullDownRefresh } from '@dcloudio/uni-app'
import { teacherProjectStudents } from '@/api'
import { formatDate, relativeTime } from '@/utils/format'

const projectId = ref(null)
const title = ref('')
const items = ref([])
const loading = ref(true)

const avgProgress = computed(() => {
  if (!items.value.length) return 0
  return Math.round(items.value.reduce((s, x) => s + (x.progress || 0), 0) / items.value.length)
})

const load = async () => {
  loading.value = true
  try {
    items.value = await teacherProjectStudents(projectId.value)
  } catch (e) {
    // 已提示
  } finally {
    loading.value = false
  }
}

onLoad((options) => {
  projectId.value = options.projectId
  title.value = decodeURIComponent(options.title || '学生进度')
  load()
})

onPullDownRefresh(async () => {
  await load()
  uni.stopPullDownRefresh()
})
</script>

<style lang="scss" scoped>
.page {
  padding: 24rpx 24rpx 40rpx;
}

.p-title {
  display: block;
  font-size: 32rpx;
  font-weight: 700;
  margin-bottom: 8rpx;
}

.list {
  display: flex;
  flex-direction: column;
  gap: 20rpx;
  margin-top: 24rpx;
}

.stu-card {
  padding: 28rpx;
}

.stu-head {
  display: flex;
  align-items: center;
  gap: 20rpx;
}

.stu-avatar {
  width: 72rpx;
  height: 72rpx;
  border-radius: 50%;
  background: linear-gradient(135deg, #3b82f6, #8b5cf6);
  color: #fff;
  font-size: 30rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.stu-info {
  flex: 1;
  overflow: hidden;
}

.stu-name {
  display: block;
  font-size: 30rpx;
  font-weight: 600;
}

.stu-sub {
  display: block;
  font-size: 23rpx;
  margin-top: 4rpx;
}

.progress-row {
  display: flex;
  align-items: center;
  gap: 20rpx;
  margin-top: 24rpx;
}

.stu-track {
  flex: 1;
}

.progress-num {
  font-size: 26rpx;
  font-weight: 700;
  color: $brand-blue;
  width: 80rpx;
  text-align: right;
}

.stu-meta {
  display: flex;
  flex-direction: column;
  gap: 6rpx;
  margin-top: 16rpx;
  font-size: 23rpx;
}
</style>
