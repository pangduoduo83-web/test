<template>
  <view v-if="equip" class="page">
    <!-- 图片 -->
    <view class="hero">
      <image
        v-if="equip.imageUrl && !imgFailed"
        :src="fullUrl(equip.imageUrl)"
        class="hero-img"
        mode="aspectFill"
        @error="imgFailed = true"
      />
      <view v-else class="hero-img hero-fallback">
        <text class="hero-icon">{{ equip.icon || '🔧' }}</text>
      </view>
      <text class="badge status-badge" :class="statusBadge">{{ statusText }}</text>
    </view>

    <!-- 基本信息 -->
    <view class="card head-card">
      <view class="name-row">
        <text class="name">{{ equip.name }}</text>
        <text class="price">¥{{ equip.price }}</text>
      </view>
      <text class="model">{{ equip.model }} · {{ equip.manufacturer }}</text>
      <view class="quick-stats">
        <view class="qs-item">
          <text class="qs-num" :class="{ 'text-red': equip.availableCount === 0 }">
            {{ equip.availableCount }}/{{ equip.totalCount }}
          </text>
          <text class="qs-label">可借/总数</text>
        </view>
        <view class="qs-item">
          <text class="qs-num">★ {{ equip.rating }}</text>
          <text class="qs-label">评分</text>
        </view>
        <view class="qs-item">
          <text class="qs-num">{{ equip.borrowCount }}</text>
          <text class="qs-label">借出次数</text>
        </view>
        <view class="qs-item">
          <text class="qs-num qs-loc">{{ equip.location }}</text>
          <text class="qs-label">存放位置</text>
        </view>
      </view>
      <view v-if="asList(equip.tags).length" class="tags">
        <text v-for="t in asList(equip.tags)" :key="t" class="chip chip-blue">{{ t }}</text>
      </view>
    </view>

    <!-- 描述 -->
    <view class="card block">
      <text class="section-title">📋 设备描述</text>
      <text class="desc">{{ equip.description }}</text>
    </view>

    <!-- 技术规格 -->
    <view v-if="asList(equip.specs).length" class="card block">
      <text class="section-title">⚙️ 技术规格</text>
      <view class="spec-grid">
        <view v-for="s in asList(equip.specs)" :key="s" class="spec-item">
          <text class="spec-dot">•</text>
          <text class="spec-text">{{ s }}</text>
        </view>
      </view>
    </view>

    <!-- 适用项目 -->
    <view v-if="asList(equip.suitableProjects).length" class="card block">
      <text class="section-title">🎯 适用项目</text>
      <view v-for="(p, i) in asList(equip.suitableProjects)" :key="i" class="li-row">
        <text class="li-idx">{{ i + 1 }}</text>
        <text class="li-text">{{ p }}</text>
      </view>
    </view>

    <!-- 相关文档 -->
    <view v-if="asList(equip.docs).length" class="card block">
      <text class="section-title">📚 相关文档</text>
      <view v-for="(d, i) in asList(equip.docs)" :key="i" class="doc-row">
        <text class="doc-icon">📄</text>
        <text class="doc-name">{{ d }}</text>
        <text class="doc-tip muted">到馆查阅</text>
      </view>
    </view>

    <view class="bottom-gap" />

    <!-- 底部操作 -->
    <view class="action-bar">
      <view class="fav-btn" @click="onToggleWish">
        <text class="fav-icon" :class="{ faved: wished }">{{ wished ? '♥' : '♡' }}</text>
        <text class="fav-text">{{ wished ? '已心愿' : '心愿' }}</text>
      </view>
      <view class="stock-info">
        <text class="stock-num" :class="{ 'text-red': !canBorrow }">
          {{ canBorrow ? `可借 ${equip.availableCount} 台` : statusText }}
        </text>
        <text class="stock-loc">📍 {{ equip.location }}</text>
      </view>
      <button class="btn-gradient apply-btn" :disabled="!canBorrow" @click="goApply">
        {{ canBorrow ? '申请借阅' : '暂不可借' }}
      </button>
    </view>
  </view>

  <view v-else class="empty-box page-loading">
    <text class="empty-icon">⏳</text>
    <text>加载中...</text>
  </view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { fetchEquipmentDetail, fetchEquipmentFavorites, toggleEquipmentFavorite } from '@/api'
import { fullUrl } from '@/config'
import { asList } from '@/utils/format'

const equip = ref(null)
const imgFailed = ref(false)
const wished = ref(false)

const canBorrow = computed(
  () => equip.value && equip.value.status === 'AVAILABLE' && equip.value.availableCount > 0
)

const statusText = computed(() => {
  if (!equip.value) return ''
  if (equip.value.status === 'MAINTENANCE') return '维护中'
  return equip.value.availableCount > 0 ? '可借' : '已借完'
})

const statusBadge = computed(() => {
  if (!equip.value) return 'badge-gray'
  if (equip.value.status === 'MAINTENANCE') return 'badge-yellow'
  return equip.value.availableCount > 0 ? 'badge-green' : 'badge-red'
})

onLoad(async (options) => {
  equip.value = await fetchEquipmentDetail(options.id)
  fetchEquipmentFavorites()
    .then((ids) => {
      wished.value = (ids || []).includes(equip.value.id)
    })
    .catch(() => {})
})

const onToggleWish = async () => {
  try {
    const d = await toggleEquipmentFavorite(equip.value.id)
    wished.value = !!d.favorited
    uni.showToast({ title: wished.value ? '已加入心愿单' : '已移出心愿单', icon: 'none' })
  } catch (e) {
    // 已提示
  }
}

const goApply = () => {
  uni.navigateTo({ url: `/pages/borrow-apply/index?equipmentId=${equip.value.id}` })
}
</script>

<style lang="scss" scoped>
.page {
  padding-bottom: 40rpx;
}

.page-loading {
  padding-top: 240rpx;
}

.hero {
  position: relative;
}

.hero-img {
  width: 100%;
  height: 400rpx;
}

.hero-fallback {
  background: linear-gradient(135deg, #60a5fa, #a78bfa);
  display: flex;
  align-items: center;
  justify-content: center;
}

.hero-icon {
  font-size: 130rpx;
}

.status-badge {
  position: absolute;
  top: 24rpx;
  right: 24rpx;
}

.head-card {
  margin: -40rpx 24rpx 0;
  position: relative;
}

.name-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.name {
  font-size: 36rpx;
  font-weight: 700;
  flex: 1;
}

.price {
  font-size: 32rpx;
  font-weight: 700;
  color: $brand-blue;
}

.model {
  display: block;
  font-size: 26rpx;
  color: $text-sub;
  margin-top: 8rpx;
}

.quick-stats {
  display: flex;
  margin-top: 28rpx;
  background: $gray-bg;
  border-radius: 20rpx;
  padding: 20rpx 0;
}

.qs-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  overflow: hidden;
}

.qs-num {
  font-size: 28rpx;
  font-weight: 600;
}

.qs-loc {
  font-size: 24rpx;
}

.qs-label {
  font-size: 22rpx;
  color: $text-sub;
  margin-top: 4rpx;
}

.text-red {
  color: $red;
}

.tags {
  display: flex;
  gap: 12rpx;
  margin-top: 24rpx;
  flex-wrap: wrap;
}

.chip-blue {
  color: $brand-blue;
  background: $blue-bg;
}

.block {
  margin: 24rpx 24rpx 0;
}

.desc {
  display: block;
  font-size: 28rpx;
  color: $text-main;
  line-height: 1.8;
  margin-top: 20rpx;
}

.spec-grid {
  margin-top: 20rpx;
}

.spec-item {
  display: flex;
  gap: 14rpx;
  padding: 10rpx 0;
}

.spec-dot {
  color: $brand-blue;
  font-weight: 700;
}

.spec-text {
  font-size: 27rpx;
  flex: 1;
}

.li-row {
  display: flex;
  gap: 16rpx;
  margin-top: 20rpx;
  align-items: flex-start;
}

.li-idx {
  width: 36rpx;
  height: 36rpx;
  border-radius: 50%;
  background: $blue-bg;
  color: $brand-blue;
  font-size: 22rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  margin-top: 4rpx;
}

.li-text {
  font-size: 27rpx;
  flex: 1;
}

.doc-row {
  display: flex;
  align-items: center;
  gap: 16rpx;
  padding: 20rpx 24rpx;
  background: $gray-bg;
  border-radius: 16rpx;
  margin-top: 16rpx;
}

.doc-icon {
  font-size: 32rpx;
}

.doc-name {
  flex: 1;
  font-size: 27rpx;
}

.doc-tip {
  font-size: 22rpx;
}

.bottom-gap {
  height: 140rpx;
}

.action-bar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  background: #fff;
  display: flex;
  align-items: center;
  gap: 24rpx;
  padding: 16rpx 32rpx calc(16rpx + env(safe-area-inset-bottom));
  box-shadow: 0 -4rpx 20rpx rgba(17, 24, 39, 0.06);
}

.fav-btn {
  display: flex;
  flex-direction: column;
  align-items: center;
  width: 90rpx;
  flex-shrink: 0;
}

.fav-icon {
  font-size: 44rpx;
  line-height: 1.2;
  color: $text-light;

  &.faved {
    color: #ef4444;
  }
}

.fav-text {
  font-size: 20rpx;
  color: $text-sub;
}

.stock-info {
  display: flex;
  flex-direction: column;
  width: 200rpx;
}

.stock-num {
  font-size: 28rpx;
  font-weight: 600;
  color: $green;
}

.stock-loc {
  font-size: 22rpx;
  color: $text-light;
  margin-top: 4rpx;
}

.apply-btn {
  flex: 1;
}
</style>
