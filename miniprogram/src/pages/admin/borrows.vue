<template>
  <view class="page">
    <scroll-view scroll-x class="tab-scroll" :show-scrollbar="false">
      <view class="tab-row">
        <view
          v-for="t in tabs"
          :key="t.key"
          class="pill"
          :class="{ active: activeTab === t.key }"
          @click="changeTab(t.key)"
        >
          {{ t.label }}{{ t.key === 'PENDING' && pendingCount > 0 ? `(${pendingCount})` : '' }}
        </view>
      </view>
    </scroll-view>

    <view v-if="loading" class="empty-box">
      <text class="empty-icon">⏳</text>
      <text>加载中...</text>
    </view>
    <view v-else-if="items.length === 0" class="empty-box">
      <text class="empty-icon">✅</text>
      <text>暂无相关借阅单</text>
    </view>
    <view v-else class="list">
      <view v-for="b in items" :key="b.id" class="card borrow-card">
        <view class="bc-head">
          <text class="bc-no">{{ b.requestNo }}</text>
          <text class="badge" :class="statusMeta(b.status).badge">{{ statusMeta(b.status).text }}</text>
        </view>
        <view class="bc-title-row">
          <text class="bc-user">{{ b.userName }}</text>
          <text class="bc-equip ellipsis">{{ b.equipmentName }} ×{{ b.quantity }}</text>
        </view>
        <view class="bc-meta">
          <text class="bc-meta-item">用途:{{ b.purpose }}</text>
          <text v-if="b.projectName" class="bc-meta-item">项目:{{ b.projectName }}</text>
          <text class="bc-meta-item">借期:{{ b.startDate }} 起 {{ b.durationDays }} 天</text>
          <text class="bc-meta-item">申请于 {{ relativeTime(b.appliedAt) }}</text>
        </view>
        <view v-if="b.remark" class="remark-box muted">备注:{{ b.remark }}</view>
        <view v-if="b.status === 'REJECTED' && b.rejectReason" class="reject-box">
          拒绝原因:{{ b.rejectReason }}
        </view>

        <view v-if="b.status === 'PENDING'" class="bc-actions">
          <button class="action-btn reject" @click="onReject(b)">拒绝</button>
          <button class="action-btn approve" @click="onApprove(b)">批准</button>
        </view>
        <view v-else-if="b.status === 'RETURN_REQUESTED'" class="bc-actions">
          <button class="action-btn approve" @click="onConfirmReturn(b)">确认归还验收</button>
        </view>
      </view>
      <view class="list-end muted">共 {{ items.length }} 条</view>
    </view>
  </view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onShow, onPullDownRefresh } from '@dcloudio/uni-app'
import { adminListBorrows, adminDecideBorrow, adminConfirmReturn } from '@/api'
import { relativeTime } from '@/utils/format'

const tabs = [
  { key: 'PENDING', label: '待审批' },
  { key: 'RETURN_REQUESTED', label: '待验收' },
  { key: 'APPROVED', label: '借用中' },
  { key: 'ALL', label: '全部' }
]

const statusMap = {
  PENDING: { text: '待审批', badge: 'badge-yellow' },
  APPROVED: { text: '借用中', badge: 'badge-blue' },
  RETURN_REQUESTED: { text: '待验收', badge: 'badge-purple' },
  RETURNED: { text: '已归还', badge: 'badge-green' },
  REJECTED: { text: '已拒绝', badge: 'badge-red' },
  CANCELLED: { text: '已撤销', badge: 'badge-gray' }
}

const activeTab = ref('PENDING')
const items = ref([])
const loading = ref(true)

const pendingCount = computed(() =>
  activeTab.value === 'PENDING' ? items.value.length : 0
)

const statusMeta = (s) => statusMap[s] || { text: s, badge: 'badge-gray' }

const load = async () => {
  loading.value = true
  try {
    items.value = await adminListBorrows({ status: activeTab.value === 'ALL' ? '' : activeTab.value })
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

const onApprove = (b) => {
  uni.showModal({
    title: '批准借阅',
    content: `批准 ${b.userName} 借用「${b.equipmentName}」×${b.quantity}?批准后自动扣减库存`,
    success: async (res) => {
      if (!res.confirm) return
      try {
        await adminDecideBorrow(b.id, { action: 'approve' })
        uni.showToast({ title: '已批准', icon: 'success' })
        load()
      } catch (e) {
        // 已提示
      }
    }
  })
}

const onReject = (b) => {
  uni.showModal({
    title: '拒绝申请',
    editable: true,
    placeholderText: '请输入拒绝原因(必填)',
    confirmColor: '#dc2626',
    success: async (res) => {
      if (!res.confirm) return
      const reason = (res.content || '').trim()
      if (!reason) {
        uni.showToast({ title: '拒绝原因不能为空', icon: 'none' })
        return
      }
      try {
        await adminDecideBorrow(b.id, { action: 'reject', reason })
        uni.showToast({ title: '已拒绝', icon: 'none' })
        load()
      } catch (e) {
        // 已提示
      }
    }
  })
}

const onConfirmReturn = (b) => {
  uni.showModal({
    title: '归还验收',
    content: `确认「${b.equipmentName}」×${b.quantity} 已完好归还?验收后自动回补库存`,
    success: async (res) => {
      if (!res.confirm) return
      try {
        await adminConfirmReturn(b.id)
        uni.showToast({ title: '验收完成', icon: 'success' })
        load()
      } catch (e) {
        // 已提示
      }
    }
  })
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

.tab-scroll {
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
}

.bc-title-row {
  display: flex;
  align-items: center;
  gap: 16rpx;
  margin-top: 16rpx;
}

.bc-user {
  font-size: 30rpx;
  font-weight: 700;
  flex-shrink: 0;
}

.bc-equip {
  font-size: 28rpx;
  color: $text-main;
  flex: 1;
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

.remark-box {
  margin-top: 14rpx;
  font-size: 24rpx;
  background: $gray-bg;
  border-radius: 12rpx;
  padding: 14rpx 20rpx;
}

.reject-box {
  margin-top: 14rpx;
  background: $red-bg;
  color: $red;
  font-size: 24rpx;
  border-radius: 12rpx;
  padding: 14rpx 20rpx;
}

.bc-actions {
  margin-top: 20rpx;
  padding-top: 20rpx;
  border-top: 2rpx solid $border-color;
  display: flex;
  justify-content: flex-end;
  gap: 20rpx;
}

.action-btn {
  font-size: 26rpx;
  border-radius: $radius-pill;
  padding: 8rpx 36rpx;
  line-height: 1.7;
  margin: 0;

  &::after {
    border: none;
  }

  &.approve {
    background: linear-gradient(90deg, #2563eb, #9333ea);
    color: #fff;
  }

  &.reject {
    background: $red-bg;
    color: $red;
  }
}

.list-end {
  text-align: center;
  padding: 24rpx 0;
  font-size: 24rpx;
}
</style>
