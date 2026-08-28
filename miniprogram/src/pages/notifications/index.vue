<template>
  <view class="page">
    <view class="head-row">
      <text class="muted">{{ unread > 0 ? `${unread} 条未读通知` : '全部已读' }}</text>
      <text v-if="unread > 0" class="read-all" @click="onReadAll">全部标为已读</text>
    </view>

    <view v-if="loading" class="empty-box">
      <uni-icons type="spinner-cycle" size="40" color="#d1d5db" />
      <text>加载中...</text>
    </view>
    <view v-else-if="items.length === 0" class="empty-box">
      <uni-icons type="notification" size="40" color="#d1d5db" />
      <text>暂无通知</text>
    </view>
    <view v-else class="list">
      <view
        v-for="n in items"
        :key="n.id"
        class="card notice-card"
        :class="{ unread: !n.isRead }"
        @click="onRead(n)"
      >
        <view class="n-icon" :class="`type-${n.type}`">
          <uni-icons :type="typeIcon(n.type)" size="18" :color="typeColor(n.type)" />
        </view>
        <view class="n-body">
          <view class="n-head">
            <text class="n-title" :class="{ bold: !n.isRead }">{{ n.title }}</text>
            <view v-if="!n.isRead" class="dot" />
          </view>
          <text class="n-content">{{ n.content }}</text>
          <text class="n-time">{{ relativeTime(n.createdAt) }}</text>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onShow, onPullDownRefresh } from '@dcloudio/uni-app'
import { fetchNotifications, markNotificationRead, markAllNotificationsRead } from '@/api'
import { relativeTime } from '@/utils/format'

const items = ref([])
const unread = ref(0)
const loading = ref(true)

const typeIcon = (t) => ({ borrow: 'cart', project: 'flag', system: 'notification' }[t] || 'email')
const typeColor = (t) => ({ borrow: '#2563eb', project: '#9333ea', system: '#d97706' }[t] || '#6b7280')

const load = async () => {
  try {
    const d = await fetchNotifications()
    items.value = d?.items || []
    unread.value = d?.unread || 0
  } catch (e) {
    // 静默
  } finally {
    loading.value = false
  }
}

const onRead = async (n) => {
  if (n.isRead) return
  n.isRead = true
  unread.value = Math.max(0, unread.value - 1)
  try {
    await markNotificationRead(n.id)
  } catch (e) {
    // 忽略
  }
}

const onReadAll = async () => {
  try {
    await markAllNotificationsRead()
    items.value.forEach((n) => (n.isRead = true))
    unread.value = 0
    uni.showToast({ title: '已全部标为已读', icon: 'none' })
  } catch (e) {
    // 已提示
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

.head-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8rpx 8rpx 24rpx;
}

.read-all {
  font-size: 26rpx;
  color: $brand-blue;
}

.list {
  display: flex;
  flex-direction: column;
  gap: 20rpx;
}

.notice-card {
  display: flex;
  gap: 22rpx;
  padding: 28rpx;

  &.unread {
    border-left: 6rpx solid $brand-blue;
  }
}

.n-icon {
  width: 72rpx;
  height: 72rpx;
  border-radius: 20rpx;
  font-size: 36rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;

  &.type-borrow {
    background: $blue-bg;
  }

  &.type-project {
    background: $purple-bg;
  }

  &.type-system {
    background: $yellow-bg;
  }
}

.n-body {
  flex: 1;
  overflow: hidden;
}

.n-head {
  display: flex;
  align-items: center;
  gap: 12rpx;
}

.n-title {
  font-size: 29rpx;
  flex: 1;

  &.bold {
    font-weight: 700;
  }
}

.dot {
  width: 14rpx;
  height: 14rpx;
  border-radius: 50%;
  background: #ef4444;
  flex-shrink: 0;
}

.n-content {
  display: block;
  font-size: 25rpx;
  color: $text-sub;
  margin-top: 8rpx;
}

.n-time {
  display: block;
  font-size: 22rpx;
  color: $text-light;
  margin-top: 12rpx;
}
</style>
