<template>
  <view class="page">
    <!-- 顶部统计 + 搜索 -->
    <view class="head-banner">
      <view class="hb-text">
        <text class="hb-title">设备图书馆</text>
        <text class="hb-sub">当前可借 {{ availableTotal }} 台 · 共 {{ items.length }} 种设备</text>
      </view>
      <text class="hb-icon">🔬</text>
    </view>

    <view class="search-box">
      <text class="search-icon">⌕</text>
      <input
        v-model="keyword"
        class="search-input"
        placeholder="搜索设备名称、型号..."
        placeholder-class="ph"
        confirm-type="search"
        @confirm="load"
      />
      <text v-if="keyword" class="clear-btn" @click="clearKeyword">×</text>
    </view>

    <!-- 筛选 -->
    <view class="filter-row">
      <view
        v-for="s in statusOptions"
        :key="s.value"
        class="pill"
        :class="{ active: status === s.value }"
        @click="changeStatus(s.value)"
      >
        {{ s.label }}
      </view>
      <picker mode="selector" :range="locationRange" @change="changeLocation($event.detail.value)">
        <view class="pill" :class="{ active: !!location }">📍 {{ location || '全部位置' }}</view>
      </picker>
      <view class="pill" :class="{ active: minRating > 0 }" @click="toggleRating">
        ★ {{ minRating > 0 ? minRating + '+' : '评分' }}
      </view>
      <view class="pill" :class="{ active: onlyWish }" @click="onlyWish = !onlyWish">
        {{ onlyWish ? '♥' : '♡' }} 心愿({{ wishIds.length }})
      </view>
    </view>

    <scroll-view v-if="categories.length > 1" scroll-x class="cate-scroll" :show-scrollbar="false">
      <view class="cate-row">
        <view
          v-for="c in categories"
          :key="c"
          class="pill"
          :class="{ active: category === c }"
          @click="category = c"
        >
          {{ c }}
        </view>
      </view>
    </scroll-view>

    <!-- 设备列表 -->
    <view v-if="loading" class="empty-box">
      <text class="empty-icon">⏳</text>
      <text>加载中...</text>
    </view>
    <view v-else-if="filtered.length === 0" class="empty-box">
      <text class="empty-icon">🔍</text>
      <text>没有找到符合条件的设备</text>
    </view>
    <view v-else class="list">
      <view v-for="e in filtered" :key="e.id" class="card equip-card" @click="goDetail(e.id)">
        <view class="thumb-wrap">
          <image
            v-if="e.imageUrl && !failedImgs[e.id]"
            :src="fullUrl(e.imageUrl)"
            class="thumb"
            mode="aspectFill"
            @error="failedImgs[e.id] = true"
          />
          <view v-else class="thumb thumb-fallback">
            <text class="thumb-icon">{{ e.icon || '🔧' }}</text>
          </view>
          <text class="badge status-badge" :class="statusBadge(e)">{{ statusText(e) }}</text>
          <view class="wish-btn" @click.stop="onToggleWish(e)">
            <text class="wish-icon" :class="{ wished: wishIds.includes(e.id) }">
              {{ wishIds.includes(e.id) ? '♥' : '♡' }}
            </text>
          </view>
        </view>
        <view class="body">
          <view class="name-row">
            <text class="name ellipsis">{{ e.name }}</text>
            <text class="stock" :class="{ 'stock-none': e.availableCount === 0 }">
              余 {{ e.availableCount }}/{{ e.totalCount }}
            </text>
          </view>
          <text class="model ellipsis">{{ e.model }} · {{ e.manufacturer }}</text>
          <text class="desc ellipsis-2">{{ stripHtml(e.description) }}</text>
          <view class="specs">
            <text v-for="s in asList(e.specs).slice(0, 3)" :key="s" class="chip">{{ s }}</text>
          </view>
          <view class="foot-row">
            <view class="foot-meta">
              <text class="meta">📍 {{ e.location }}</text>
              <text class="meta">★ {{ e.rating }}</text>
              <text class="meta">{{ e.borrowCount }}次借出</text>
            </view>
            <button
              class="borrow-btn"
              :class="{ disabled: !canBorrow(e) }"
              :disabled="!canBorrow(e)"
              @click.stop="goApply(e)"
            >
              {{ canBorrow(e) ? '申请借阅' : e.status === 'MAINTENANCE' ? '维护中' : '已借完' }}
            </button>
          </view>
        </view>
      </view>
      <view class="list-end muted">共 {{ filtered.length }} 种设备</view>
    </view>
  </view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onShow, onPullDownRefresh } from '@dcloudio/uni-app'
import { fetchEquipment, fetchLocations, fetchEquipmentFavorites, toggleEquipmentFavorite } from '@/api'
import { fullUrl } from '@/config'
import { asList } from '@/utils/format'
import { stripHtml } from '@/utils/rich'
import { getToken, ensureLogin } from '@/utils/auth'

const statusOptions = [
  { label: '全部', value: '' },
  { label: '可借', value: 'AVAILABLE' },
  { label: '维护中', value: 'MAINTENANCE' }
]
const ratingSteps = [0, 4.0, 4.5]

const keyword = ref('')
const status = ref('')
const location = ref('')
const minRating = ref(0)
const category = ref('全部')
const items = ref([])
const locations = ref([])
const loading = ref(true)
const failedImgs = ref({})
const wishIds = ref([])
const onlyWish = ref(false)
let loadedOnce = false

const locationRange = computed(() => ['全部位置', ...locations.value])

const categories = computed(() => {
  const set = new Set(items.value.map((e) => e.category).filter(Boolean))
  return ['全部', ...set]
})

const filtered = computed(() =>
  items.value.filter((e) => {
    if (category.value !== '全部' && e.category !== category.value) return false
    if (onlyWish.value && !wishIds.value.includes(e.id)) return false
    return true
  })
)

const availableTotal = computed(() =>
  items.value.reduce((sum, e) => (e.status === 'AVAILABLE' ? sum + (e.availableCount || 0) : sum), 0)
)

const canBorrow = (e) => e.status === 'AVAILABLE' && e.availableCount > 0

const statusText = (e) =>
  e.status === 'MAINTENANCE' ? '维护中' : e.availableCount > 0 ? '可借' : '已借完'

const statusBadge = (e) =>
  e.status === 'MAINTENANCE' ? 'badge-yellow' : e.availableCount > 0 ? 'badge-green' : 'badge-red'

const load = async () => {
  loading.value = true
  try {
    items.value = await fetchEquipment({
      keyword: keyword.value.trim(),
      status: status.value,
      location: location.value,
      minRating: minRating.value > 0 ? minRating.value : undefined
    })
  } catch (e) {
    // 已提示
  } finally {
    loading.value = false
  }
}

const clearKeyword = () => {
  keyword.value = ''
  load()
}

const changeStatus = (v) => {
  status.value = v
  load()
}

const changeLocation = (idx) => {
  const i = Number(idx)
  location.value = i > 0 ? locations.value[i - 1] : ''
  load()
}

const toggleRating = () => {
  const i = ratingSteps.indexOf(minRating.value)
  minRating.value = ratingSteps[(i + 1) % ratingSteps.length]
  load()
}

const goDetail = (id) => {
  uni.navigateTo({ url: `/pages/equipment-detail/index?id=${id}` })
}

const goApply = (e) => {
  if (!ensureLogin()) return
  uni.navigateTo({ url: `/pages/borrow-apply/index?equipmentId=${e.id}` })
}

let wishLoaded = false
const loadWishlist = () => {
  // 心愿单是个人数据,游客不请求;登录后再次进入时补拉
  if (!getToken()) return
  wishLoaded = true
  fetchEquipmentFavorites()
    .then((ids) => {
      wishIds.value = ids || []
    })
    .catch(() => {})
}

const onToggleWish = async (e) => {
  if (!ensureLogin()) return
  try {
    const d = await toggleEquipmentFavorite(e.id)
    if (d.favorited) {
      wishIds.value = [...wishIds.value, e.id]
    } else {
      wishIds.value = wishIds.value.filter((id) => id !== e.id)
    }
  } catch (err) {
    // 已提示
  }
}

// 设备列表游客可浏览(审核要求不强制登录)
onShow(() => {
  if (!wishLoaded) loadWishlist()
  if (!loadedOnce) {
    loadedOnce = true
    load()
    fetchLocations()
      .then((d) => {
        locations.value = d || []
      })
      .catch(() => {})
  }
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

.head-banner {
  background: linear-gradient(135deg, #2563eb, #9333ea);
  border-radius: $radius-card;
  padding: 36rpx 40rpx;
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24rpx;
}

.hb-title {
  display: block;
  color: #fff;
  font-size: 36rpx;
  font-weight: 700;
}

.hb-sub {
  display: block;
  color: rgba(255, 255, 255, 0.8);
  font-size: 24rpx;
  margin-top: 8rpx;
}

.hb-icon {
  font-size: 72rpx;
}

.search-box {
  display: flex;
  align-items: center;
  background: #fff;
  border-radius: $radius-pill;
  padding: 16rpx 28rpx;
  gap: 16rpx;
  box-shadow: 0 2rpx 12rpx rgba(17, 24, 39, 0.04);
}

.search-icon {
  color: $text-light;
  font-size: 34rpx;
  font-weight: 700;
}

.search-input {
  flex: 1;
  font-size: 28rpx;
  height: 44rpx;
}

.ph {
  color: $text-light;
}

.clear-btn {
  color: $text-light;
  font-size: 36rpx;
  padding: 0 8rpx;
}

.filter-row {
  display: flex;
  gap: 16rpx;
  margin-top: 20rpx;
  flex-wrap: wrap;
}

.cate-scroll {
  margin-top: 20rpx;
  white-space: nowrap;
  margin-bottom: 24rpx;
}

.cate-row {
  display: inline-flex;
  gap: 16rpx;
}

.list {
  display: flex;
  flex-direction: column;
  gap: 24rpx;
  margin-top: 8rpx;
}

.equip-card {
  padding: 0;
  overflow: hidden;
  display: flex;
}

.thumb-wrap {
  position: relative;
  width: 220rpx;
  flex-shrink: 0;
}

.thumb {
  width: 220rpx;
  height: 100%;
  min-height: 300rpx;
}

.thumb-fallback {
  background: linear-gradient(135deg, #60a5fa, #a78bfa);
  display: flex;
  align-items: center;
  justify-content: center;
}

.thumb-icon {
  font-size: 64rpx;
}

.status-badge {
  position: absolute;
  top: 16rpx;
  left: 16rpx;
}

.wish-btn {
  position: absolute;
  bottom: 12rpx;
  left: 12rpx;
  width: 56rpx;
  height: 56rpx;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.9);
  display: flex;
  align-items: center;
  justify-content: center;
}

.wish-icon {
  font-size: 32rpx;
  color: $text-light;
  line-height: 1;

  &.wished {
    color: #ef4444;
  }
}

.body {
  flex: 1;
  padding: 24rpx 28rpx;
  overflow: hidden;
}

.name-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12rpx;
}

.name {
  font-size: 30rpx;
  font-weight: 600;
  flex: 1;
}

.stock {
  font-size: 22rpx;
  color: $green;
  flex-shrink: 0;

  &.stock-none {
    color: $red;
  }
}

.model {
  display: block;
  font-size: 24rpx;
  color: $text-light;
  margin-top: 6rpx;
}

.desc {
  display: block;
  font-size: 24rpx;
  color: $text-sub;
  margin-top: 12rpx;
}

.specs {
  display: flex;
  gap: 10rpx;
  margin-top: 16rpx;
  flex-wrap: wrap;
}

.foot-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 18rpx;
}

.foot-meta {
  display: flex;
  flex-direction: column;
  gap: 4rpx;
}

.meta {
  font-size: 22rpx;
  color: $text-light;
}

.borrow-btn {
  background: linear-gradient(90deg, #2563eb, #9333ea);
  color: #fff;
  font-size: 24rpx;
  border-radius: $radius-pill;
  padding: 10rpx 28rpx;
  line-height: 1.6;
  margin: 0;
  flex-shrink: 0;

  &::after {
    border: none;
  }

  &.disabled {
    background: $gray-bg;
    color: $text-light;
  }
}

.list-end {
  text-align: center;
  padding: 24rpx 0;
  font-size: 24rpx;
}
</style>
