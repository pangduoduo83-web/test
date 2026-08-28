<template>
  <view class="page">
    <!-- 游客提示:借阅记录为登录后功能,不强制登录 -->
    <view v-if="!loggedIn" class="empty-box guest-box">
      <text class="empty-icon">🔐</text>
      <text>登录后可查看和管理你的借阅记录</text>
      <button class="btn-gradient go-btn" @click="goLogin">去登录</button>
      <button class="btn-plain go-btn" @click="goEquipment">先去设备馆逛逛</button>
    </view>

    <template v-else>
    <!-- 统计卡 -->
    <view class="stats-grid">
      <view class="stat-card">
        <text class="stat-num">{{ stats.total }}</text>
        <text class="stat-label">累计申请</text>
      </view>
      <view class="stat-card">
        <text class="stat-num text-yellow">{{ stats.pending }}</text>
        <text class="stat-label">审批中</text>
      </view>
      <view class="stat-card">
        <text class="stat-num text-blue">{{ stats.borrowing }}</text>
        <text class="stat-label">借用中</text>
      </view>
      <view class="stat-card">
        <text class="stat-num text-green">{{ stats.returned }}</text>
        <text class="stat-label">已归还</text>
      </view>
    </view>

    <!-- 状态 Tab -->
    <scroll-view scroll-x class="tab-scroll" :show-scrollbar="false">
      <view class="tab-row">
        <view
          v-for="t in tabs"
          :key="t.key"
          class="pill"
          :class="{ active: activeTab === t.key }"
          @click="activeTab = t.key"
        >
          {{ t.label }}
        </view>
      </view>
    </scroll-view>

    <!-- 列表 -->
    <view v-if="loading" class="empty-box">
      <text class="empty-icon">⏳</text>
      <text>加载中...</text>
    </view>
    <view v-else-if="filtered.length === 0" class="empty-box">
      <text class="empty-icon">📋</text>
      <text>暂无相关借阅记录</text>
      <button class="btn-gradient go-btn" @click="goEquipment">去设备馆逛逛</button>
    </view>
    <view v-else class="list">
      <view v-for="b in filtered" :key="b.id" class="card borrow-card">
        <view class="bc-head">
          <text class="bc-no">{{ b.requestNo }}</text>
          <view class="bc-badges">
            <text v-if="b.renewed" class="badge badge-gray">已续借</text>
            <text class="badge" :class="statusMeta(b.status).badge">{{ statusMeta(b.status).text }}</text>
          </view>
        </view>
        <view class="bc-title-row">
          <text class="bc-equip">{{ b.equipmentName }}</text>
          <text class="bc-qty">×{{ b.quantity }}</text>
        </view>
        <view class="bc-meta">
          <text class="bc-meta-item">用途:{{ b.purpose }}</text>
          <text v-if="b.projectName" class="bc-meta-item">项目:{{ b.projectName }}</text>
          <text class="bc-meta-item">借期:{{ b.startDate }} 起 {{ b.durationDays }} 天</text>
          <text class="bc-meta-item">申请于 {{ relativeTime(b.appliedAt) }}</text>
          <text v-if="b.approverName" class="bc-meta-item">审批人:{{ b.approverName }}</text>
        </view>
        <view v-if="b.status === 'REJECTED' && b.rejectReason" class="reject-box">
          拒绝原因:{{ b.rejectReason }}
        </view>
        <view v-if="b.status === 'APPROVED'" class="due-box" :class="{ overdue: daysLeft(b) < 0 }">
          {{ daysLeft(b) < 0 ? `已逾期 ${-daysLeft(b)} 天,请尽快归还` : `${dueDate(b)} 到期 · 剩 ${daysLeft(b)} 天` }}
        </view>
        <view v-if="b.status === 'PENDING' || b.status === 'APPROVED' || b.status === 'RETURN_REQUESTED'" class="bc-actions">
          <button v-if="b.status === 'PENDING'" class="action-btn cancel" @click="onCancel(b)">撤销申请</button>
          <template v-else-if="b.status === 'APPROVED'">
            <button v-if="canRenew(b)" class="action-btn renew" @click="onRenew(b)">续借</button>
            <button class="action-btn return" @click="onReturn(b)">申请归还</button>
          </template>
          <text v-else class="waiting muted">已提交归还,等待管理员验收</text>
        </view>
      </view>
      <view class="list-end muted">共 {{ filtered.length }} 条记录</view>
    </view>
    </template>
  </view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onShow, onPullDownRefresh } from '@dcloudio/uni-app'
import { fetchMyBorrows, cancelBorrow, requestReturn, renewBorrow } from '@/api'
import { relativeTime, formatDate } from '@/utils/format'
import { getToken } from '@/utils/auth'

const tabs = [
  { key: 'ALL', label: '全部' },
  { key: 'PENDING', label: '审批中' },
  { key: 'BORROWING', label: '借用中' },
  { key: 'RETURNED', label: '已归还' },
  { key: 'REJECTED', label: '已拒绝' }
]

const statusMap = {
  PENDING: { text: '审批中', badge: 'badge-yellow' },
  APPROVED: { text: '借用中', badge: 'badge-blue' },
  RETURN_REQUESTED: { text: '归还中', badge: 'badge-purple' },
  RETURNED: { text: '已归还', badge: 'badge-green' },
  REJECTED: { text: '已拒绝', badge: 'badge-red' },
  CANCELLED: { text: '已撤销', badge: 'badge-gray' }
}

const activeTab = ref('ALL')
const items = ref([])
const loading = ref(true)
const loggedIn = ref(true)

const statusMeta = (s) => statusMap[s] || { text: s, badge: 'badge-gray' }

const filtered = computed(() => {
  if (activeTab.value === 'ALL') return items.value
  if (activeTab.value === 'BORROWING') {
    return items.value.filter((b) => b.status === 'APPROVED' || b.status === 'RETURN_REQUESTED')
  }
  return items.value.filter((b) => b.status === activeTab.value)
})

const stats = computed(() => ({
  total: items.value.length,
  pending: items.value.filter((b) => b.status === 'PENDING').length,
  borrowing: items.value.filter((b) => b.status === 'APPROVED' || b.status === 'RETURN_REQUESTED').length,
  returned: items.value.filter((b) => b.status === 'RETURNED').length
}))

const load = async () => {
  loading.value = true
  try {
    items.value = await fetchMyBorrows({ status: 'ALL' })
  } catch (e) {
    // 已提示
  } finally {
    loading.value = false
  }
}

const onCancel = (b) => {
  uni.showModal({
    title: '撤销申请',
    content: `确定撤销「${b.equipmentName}」的借阅申请吗?`,
    confirmColor: '#dc2626',
    success: async (res) => {
      if (!res.confirm) return
      try {
        await cancelBorrow(b.id)
        uni.showToast({ title: '已撤销', icon: 'success' })
        load()
      } catch (e) {
        // 已提示
      }
    }
  })
}

// ---------- 续借:到期前 3 天内可续借一次 ----------
const dueDate = (b) => {
  const d = new Date(String(b.startDate).replace(/-/g, '/'))
  d.setDate(d.getDate() + b.durationDays)
  return formatDate(d)
}

const daysLeft = (b) => {
  const due = new Date(String(b.startDate).replace(/-/g, '/'))
  due.setDate(due.getDate() + b.durationDays)
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  return Math.round((due - today) / 86400000)
}

const canRenew = (b) => b.status === 'APPROVED' && !b.renewed && daysLeft(b) >= 0 && daysLeft(b) <= 3

const onRenew = (b) => {
  const extend = Math.min(b.durationDays, 14)
  uni.showModal({
    title: '续借',
    content: `确认续借「${b.equipmentName}」?将延长 ${extend} 天,每单仅可续借一次`,
    success: async (res) => {
      if (!res.confirm) return
      try {
        await renewBorrow(b.id)
        uni.showToast({ title: '续借成功', icon: 'success' })
        load()
      } catch (e) {
        // 已提示
      }
    }
  })
}

const onReturn = (b) => {
  uni.showModal({
    title: '申请归还',
    content: `确认归还「${b.equipmentName}」?提交后请将设备送回存放地点,等待管理员验收`,
    success: async (res) => {
      if (!res.confirm) return
      try {
        await requestReturn(b.id)
        uni.showToast({ title: '已提交归还申请', icon: 'success' })
        load()
      } catch (e) {
        // 已提示
      }
    }
  })
}

const goEquipment = () => {
  uni.switchTab({ url: '/pages/equipment/index' })
}

const goLogin = () => {
  uni.navigateTo({ url: '/pages/auth/index' })
}

onShow(() => {
  loggedIn.value = !!getToken()
  if (!loggedIn.value) {
    loading.value = false
    items.value = []
    return
  }
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

.stats-grid {
  display: flex;
  gap: 16rpx;
}

.stat-card {
  flex: 1;
  background: #fff;
  border-radius: 24rpx;
  padding: 26rpx 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  box-shadow: 0 2rpx 12rpx rgba(17, 24, 39, 0.04);
}

.stat-num {
  font-size: 40rpx;
  font-weight: 700;
}

.stat-label {
  font-size: 22rpx;
  color: $text-sub;
  margin-top: 6rpx;
}

.text-yellow {
  color: $yellow;
}

.text-blue {
  color: $blue;
}

.text-green {
  color: $green;
}

.tab-scroll {
  margin-top: 24rpx;
  white-space: nowrap;
}

.tab-row {
  display: inline-flex;
  gap: 16rpx;
}

.list {
  display: flex;
  flex-direction: column;
  gap: 24rpx;
  margin-top: 24rpx;
}

.borrow-card {
  padding: 28rpx 32rpx;
}

.bc-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.bc-no {
  font-size: 22rpx;
  color: $text-light;
  letter-spacing: 1rpx;
}

.bc-title-row {
  display: flex;
  align-items: center;
  gap: 16rpx;
  margin-top: 16rpx;
}

.bc-equip {
  font-size: 32rpx;
  font-weight: 600;
}

.bc-qty {
  font-size: 26rpx;
  color: $text-sub;
}

.bc-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8rpx 28rpx;
  margin-top: 16rpx;
}

.bc-meta-item {
  font-size: 24rpx;
  color: $text-sub;
}

.reject-box {
  margin-top: 16rpx;
  background: $red-bg;
  color: $red;
  font-size: 24rpx;
  border-radius: 12rpx;
  padding: 14rpx 20rpx;
}

.bc-badges {
  display: flex;
  gap: 10rpx;
}

.due-box {
  margin-top: 16rpx;
  background: $yellow-bg;
  color: $yellow;
  font-size: 24rpx;
  border-radius: 12rpx;
  padding: 14rpx 20rpx;

  &.overdue {
    background: $red-bg;
    color: $red;
    font-weight: 600;
  }
}

.bc-actions {
  margin-top: 20rpx;
  padding-top: 20rpx;
  border-top: 2rpx solid $border-color;
  display: flex;
  justify-content: flex-end;
  align-items: center;
}

.action-btn {
  font-size: 26rpx;
  border-radius: $radius-pill;
  padding: 8rpx 32rpx;
  line-height: 1.7;
  margin: 0;

  &::after {
    border: none;
  }

  &.cancel {
    background: $red-bg;
    color: $red;
  }

  &.return {
    background: linear-gradient(90deg, #2563eb, #9333ea);
    color: #fff;
  }

  &.renew {
    background: $blue-bg;
    color: $blue;
  }
}

.waiting {
  font-size: 24rpx;
}

.go-btn {
  margin-top: 24rpx;
  width: 320rpx;
  font-size: 26rpx;
  padding: 14rpx 0;
}

.list-end {
  text-align: center;
  padding: 24rpx 0;
  font-size: 24rpx;
}
</style>
