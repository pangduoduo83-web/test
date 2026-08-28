<template>
  <view class="page">
    <!-- 核心统计 -->
    <view class="stats-grid">
      <view class="stat-card">
        <text class="stat-num">{{ stats.studentCount || 0 }}</text>
        <text class="stat-label">注册学生</text>
      </view>
      <view class="stat-card">
        <text class="stat-num text-blue">{{ stats.equipmentCount || 0 }}</text>
        <text class="stat-label">设备种类</text>
      </view>
      <view class="stat-card">
        <text class="stat-num text-purple">{{ stats.projectCount || 0 }}</text>
        <text class="stat-label">教学项目</text>
      </view>
    </view>
    <view class="stats-grid second">
      <view class="stat-card warn" @click="goBorrows">
        <text class="stat-num text-yellow">{{ stats.pendingBorrows || 0 }}</text>
        <text class="stat-label">待审批</text>
      </view>
      <view class="stat-card" @click="goBorrows">
        <text class="stat-num text-blue">{{ stats.activeBorrows || 0 }}</text>
        <text class="stat-label">借用中</text>
      </view>
      <view class="stat-card" @click="goBorrows">
        <text class="stat-num text-green">{{ stats.returnRequests || 0 }}</text>
        <text class="stat-label">待验收</text>
      </view>
    </view>

    <!-- 最新待审批 -->
    <view v-if="recentPending.length" class="card block">
      <view class="block-head">
        <text class="section-title">最新待审批</text>
        <text class="more" @click="goBorrows">去审批 ›</text>
      </view>
      <view v-for="b in recentPending" :key="b.id" class="pending-row" @click="goBorrows">
        <view class="pending-info">
          <text class="pending-name">{{ b.userName }} · {{ b.equipmentName }} ×{{ b.quantity }}</text>
          <text class="muted">{{ b.purpose }} · {{ relativeTime(b.appliedAt) }}</text>
        </view>
        <text class="badge badge-yellow">待审批</text>
      </view>
    </view>

    <!-- 30天趋势 -->
    <view class="card block">
      <view class="block-head">
        <text class="section-title">近30天借阅趋势</text>
        <view class="seg-group">
          <view class="seg" :class="{ active: series === 'applied' }" @click="series = 'applied'">申请</view>
          <view class="seg" :class="{ active: series === 'returned' }" @click="series = 'returned'">归还</view>
        </view>
      </view>
      <view class="chart dense">
        <view v-for="(v, i) in seriesData" :key="i" class="bar-col">
          <view class="bar" :class="{ 'bar-green': series === 'returned' }" :style="{ height: barHeight(v) }" />
        </view>
      </view>
      <view class="chart-axis">
        <text class="axis-label">{{ trends.days?.[0] || '' }}</text>
        <text class="axis-label">{{ trends.days?.[trends.days.length - 1] || '' }}</text>
      </view>
      <text class="chart-tip muted">30天累计{{ series === 'applied' ? '申请' : '归还' }} {{ seriesSum }} 单</text>
    </view>

    <!-- 设备利用率 -->
    <view class="card block">
      <text class="section-title">设备利用率 Top{{ utilization.length }}</text>
      <view v-for="u in utilization" :key="u.name" class="util-item">
        <view class="util-head">
          <text class="util-name ellipsis">{{ u.name }}</text>
          <text class="util-rate">{{ u.inUse }}/{{ u.total }} 在用 · {{ u.inUseRate }}%</text>
        </view>
        <view class="progress-track">
          <view class="progress-fill" :style="{ width: u.inUseRate + '%' }" />
        </view>
        <text class="util-borrow muted">累计借出 {{ u.borrowCount }} 次</text>
      </view>
      <view v-if="utilization.length === 0" class="empty-box small-empty">
        <uni-icons type="bars" size="40" color="#d1d5db" />
        <text>暂无数据</text>
      </view>
    </view>
  </view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onShow, onPullDownRefresh } from '@dcloudio/uni-app'
import { adminStats, adminTrends } from '@/api'
import { relativeTime } from '@/utils/format'

const stats = ref({})
const trends = ref({})
const series = ref('applied')

const recentPending = computed(() => stats.value.recentPending || [])
const utilization = computed(() => trends.value.utilization || [])
const seriesData = computed(() => trends.value[series.value] || [])
const seriesMax = computed(() => Math.max(1, ...seriesData.value))
const seriesSum = computed(() => seriesData.value.reduce((a, b) => a + b, 0))

const barHeight = (v) => `${Math.max(4, Math.round((v / seriesMax.value) * 100))}%`

const load = async () => {
  try {
    const [s, t] = await Promise.all([adminStats(), adminTrends()])
    stats.value = s
    trends.value = t
  } catch (e) {
    // 已提示
  }
}

const goBorrows = () => uni.navigateTo({ url: '/pages/admin/borrows' })

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

.stats-grid {
  display: flex;
  gap: 16rpx;

  &.second {
    margin-top: 16rpx;
  }
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

  &.warn {
    background: $yellow-bg;
  }
}

.stat-num {
  font-size: 38rpx;
  font-weight: 700;
}

.stat-label {
  font-size: 22rpx;
  color: $text-sub;
  margin-top: 6rpx;
}

.text-blue { color: $blue; }
.text-purple { color: $purple; }
.text-yellow { color: $yellow; }
.text-green { color: $green; }

.block {
  margin-top: 24rpx;
}

.block-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.more {
  font-size: 24rpx;
  color: $brand-blue;
}

.pending-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 22rpx 0;
  border-bottom: 2rpx solid $border-color;

  &:last-child {
    border-bottom: none;
  }
}

.pending-info {
  flex: 1;
  overflow: hidden;
}

.pending-name {
  display: block;
  font-size: 27rpx;
  font-weight: 500;
}

.seg-group {
  display: inline-flex;
  background: $gray-bg;
  border-radius: 14rpx;
  padding: 4rpx;
}

.seg {
  padding: 8rpx 24rpx;
  font-size: 24rpx;
  color: $text-sub;
  border-radius: 12rpx;

  &.active {
    background: #fff;
    color: $brand-blue;
    font-weight: 600;
    box-shadow: 0 2rpx 8rpx rgba(17, 24, 39, 0.06);
  }
}

.chart {
  display: flex;
  align-items: flex-end;
  gap: 4rpx;
  height: 220rpx;
  margin-top: 28rpx;
}

.bar-col {
  flex: 1;
  height: 100%;
  display: flex;
  align-items: flex-end;
}

.bar {
  width: 100%;
  background: linear-gradient(180deg, #3b82f6, #8b5cf6);
  border-radius: 6rpx 6rpx 0 0;
  min-height: 4rpx;

  &.bar-green {
    background: linear-gradient(180deg, #34d399, #059669);
  }
}

.chart-axis {
  display: flex;
  justify-content: space-between;
  margin-top: 12rpx;
}

.axis-label {
  font-size: 20rpx;
  color: $text-light;
}

.chart-tip {
  display: block;
  margin-top: 14rpx;
  font-size: 24rpx;
}

.util-item {
  margin-top: 28rpx;
}

.util-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12rpx;
  gap: 16rpx;
}

.util-name {
  font-size: 27rpx;
  font-weight: 500;
  flex: 1;
}

.util-rate {
  font-size: 23rpx;
  color: $text-sub;
  flex-shrink: 0;
}

.util-borrow {
  display: block;
  font-size: 22rpx;
  margin-top: 8rpx;
}

.small-empty {
  padding: 60rpx 0;
}
</style>
