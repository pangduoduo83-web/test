<template>
  <view class="page">
    <!-- 发送通知 -->
    <view class="card send-card">
      <text class="section-title">📣 发送通知</text>

      <text class="field-label">发送对象</text>
      <view class="pill-row">
        <view
          v-for="t in targets"
          :key="t.key"
          class="pill"
          :class="{ active: target === t.key }"
          @click="target = t.key"
        >
          {{ t.label }}
        </view>
      </view>
      <picker
        v-if="target === 'USER'"
        mode="selector"
        :range="userRange"
        @change="form.userIdx = Number($event.detail.value)"
      >
        <view class="field-input picker" :class="{ placeholder: form.userIdx < 0 }">
          {{ form.userIdx >= 0 ? userRange[form.userIdx] : '选择接收用户' }}
        </view>
      </picker>

      <text class="field-label">通知类型</text>
      <view class="pill-row">
        <view
          v-for="t in types"
          :key="t.key"
          class="pill"
          :class="{ active: form.type === t.key }"
          @click="form.type = t.key"
        >
          {{ t.icon }} {{ t.label }}
        </view>
      </view>

      <text class="field-label">标题</text>
      <input v-model="form.title" class="field-input" placeholder="通知标题(100字内)" placeholder-class="ph" :maxlength="100" />

      <text class="field-label">内容</text>
      <textarea v-model="form.content" class="field-textarea" placeholder="通知内容(300字内,可留空)" placeholder-class="ph" :maxlength="300" />

      <button class="btn-gradient" :disabled="sending" @click="send">
        {{ sending ? '发送中...' : '发送通知' }}
      </button>
    </view>

    <!-- 通知记录 -->
    <view class="card block">
      <view class="block-head">
        <text class="section-title">🗂 通知记录</text>
        <view class="pill-row small">
          <view
            v-for="t in filterTypes"
            :key="t.key"
            class="pill"
            :class="{ active: filterType === t.key }"
            @click="changeFilter(t.key)"
          >
            {{ t.label }}
          </view>
        </view>
      </view>

      <view v-if="loading" class="empty-box small-empty">
        <text class="empty-icon">⏳</text>
        <text>加载中...</text>
      </view>
      <view v-else-if="items.length === 0" class="empty-box small-empty">
        <text class="empty-icon">🔕</text>
        <text>暂无通知记录</text>
      </view>
      <view v-for="n in items" :key="n.id" class="notice-row">
        <view class="n-icon">{{ typeIcon(n.type) }}</view>
        <view class="n-body">
          <view class="n-head">
            <text class="n-title ellipsis">{{ n.title }}</text>
            <text class="badge" :class="n.isRead ? 'badge-gray' : 'badge-blue'">
              {{ n.isRead ? '已读' : '未读' }}
            </text>
          </view>
          <text class="n-content ellipsis-2">{{ n.content }}</text>
          <text class="n-meta muted">收件人:{{ userName(n.userId) }} · {{ relativeTime(n.createdAt) }}</text>
        </view>
        <text class="n-del" @click="onDelete(n)">删除</text>
      </view>
      <view v-if="items.length" class="list-end muted">最近 {{ items.length }} 条</view>
    </view>
  </view>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { onShow, onPullDownRefresh } from '@dcloudio/uni-app'
import {
  adminListNotifications,
  adminSendNotification,
  adminDeleteNotification,
  adminListUsers
} from '@/api'
import { relativeTime } from '@/utils/format'

const targets = [
  { key: 'ALL', label: '全员' },
  { key: 'STUDENT', label: '全体学生' },
  { key: 'TEACHER', label: '全体教师' },
  { key: 'USER', label: '指定用户' }
]

const types = [
  { key: 'system', label: '系统', icon: '🔔' },
  { key: 'project', label: '项目', icon: '🚀' },
  { key: 'borrow', label: '借阅', icon: '📦' }
]

const filterTypes = [
  { key: '', label: '全部' },
  { key: 'system', label: '系统' },
  { key: 'project', label: '项目' },
  { key: 'borrow', label: '借阅' }
]

const target = ref('ALL')
const form = reactive({ userIdx: -1, type: 'system', title: '', content: '' })
const sending = ref(false)
const items = ref([])
const users = ref([])
const loading = ref(true)
const filterType = ref('')

const userRange = computed(() => users.value.map((u) => `${u.name}(${u.email})`))

const typeIcon = (t) => ({ borrow: '📦', project: '🚀', system: '🔔' }[t] || '📩')

const userName = (id) => {
  const u = users.value.find((x) => x.id === id)
  return u ? u.name : `用户#${id}`
}

const load = async () => {
  loading.value = true
  try {
    items.value = (await adminListNotifications({ type: filterType.value || undefined })).slice(0, 50)
  } catch (e) {
    // 已提示
  } finally {
    loading.value = false
  }
}

const loadUsers = async () => {
  try {
    users.value = await adminListUsers({})
  } catch (e) {
    // 静默
  }
}

const changeFilter = (k) => {
  filterType.value = k
  load()
}

const send = async () => {
  if (!form.title.trim()) {
    uni.showToast({ title: '请填写通知标题', icon: 'none' })
    return
  }
  if (target.value === 'USER' && form.userIdx < 0) {
    uni.showToast({ title: '请选择接收用户', icon: 'none' })
    return
  }
  sending.value = true
  try {
    const payload = {
      title: form.title.trim(),
      content: form.content.trim() || undefined,
      type: form.type
    }
    if (target.value === 'USER') {
      payload.userId = users.value[form.userIdx].id
    } else if (target.value !== 'ALL') {
      payload.role = target.value
    }
    const count = await adminSendNotification(payload)
    uni.showToast({ title: `已发送给 ${count} 人`, icon: 'success' })
    form.title = ''
    form.content = ''
    load()
  } catch (e) {
    // 已提示
  } finally {
    sending.value = false
  }
}

const onDelete = (n) => {
  uni.showModal({
    title: '删除通知',
    content: `删除发给 ${userName(n.userId)} 的「${n.title}」?`,
    confirmColor: '#dc2626',
    success: async (res) => {
      if (!res.confirm) return
      try {
        await adminDeleteNotification(n.id)
        uni.showToast({ title: '已删除', icon: 'success' })
        load()
      } catch (e) {
        // 已提示
      }
    }
  })
}

onShow(() => {
  load()
  loadUsers()
})

onPullDownRefresh(async () => {
  await load()
  uni.stopPullDownRefresh()
})
</script>

<style lang="scss" scoped>
.page {
  padding: 24rpx 24rpx 60rpx;
}

.send-card {
  padding-bottom: 40rpx;
}

.field-label {
  display: block;
  font-size: 26rpx;
  font-weight: 500;
  margin: 26rpx 0 12rpx;
}

.pill-row {
  display: flex;
  gap: 14rpx;
  flex-wrap: wrap;

  &.small {
    gap: 10rpx;
  }
}

.field-input {
  background: $gray-bg;
  border-radius: 16rpx;
  padding: 20rpx 24rpx;
  font-size: 28rpx;
  height: 88rpx;
  box-sizing: border-box;
  width: 100%;

  &.picker {
    display: flex;
    align-items: center;
    margin-top: 16rpx;
  }

  &.placeholder {
    color: $text-light;
  }
}

.field-textarea {
  background: $gray-bg;
  border-radius: 16rpx;
  padding: 20rpx 24rpx;
  font-size: 28rpx;
  width: 100%;
  height: 140rpx;
  box-sizing: border-box;
  margin-bottom: 32rpx;
}

.ph {
  color: $text-light;
}

.block {
  margin-top: 24rpx;
}

.block-head {
  display: flex;
  flex-direction: column;
  gap: 20rpx;
  margin-bottom: 8rpx;
}

.small-empty {
  padding: 60rpx 0;
}

.notice-row {
  display: flex;
  gap: 18rpx;
  padding: 24rpx 0;
  border-bottom: 2rpx solid $border-color;
  align-items: flex-start;

  &:last-of-type {
    border-bottom: none;
  }
}

.n-icon {
  width: 64rpx;
  height: 64rpx;
  border-radius: 16rpx;
  background: $gray-bg;
  font-size: 32rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
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
  font-size: 28rpx;
  font-weight: 600;
  flex: 1;
}

.n-content {
  display: block;
  font-size: 24rpx;
  color: $text-sub;
  margin-top: 6rpx;
}

.n-meta {
  display: block;
  font-size: 22rpx;
  margin-top: 8rpx;
}

.n-del {
  color: $red;
  font-size: 24rpx;
  flex-shrink: 0;
  padding-top: 4rpx;
}

.list-end {
  text-align: center;
  padding: 20rpx 0 0;
  font-size: 24rpx;
}
</style>
