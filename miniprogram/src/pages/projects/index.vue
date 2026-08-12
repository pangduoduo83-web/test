<template>
  <view class="page">
    <!-- 搜索 + 排序 -->
    <view class="toolbar">
      <view class="search-box">
        <text class="search-icon">⌕</text>
        <input
          v-model="keyword"
          class="search-input"
          placeholder="搜索项目名称、标签..."
          placeholder-class="ph"
          confirm-type="search"
          @confirm="reload"
        />
        <text v-if="keyword" class="clear-btn" @click="clearKeyword">×</text>
      </view>
      <view class="sort-row">
        <view
          v-for="s in sorts"
          :key="s.value"
          class="pill"
          :class="{ active: sort === s.value }"
          @click="changeSort(s.value)"
        >
          {{ s.label }}
        </view>
      </view>
      <view class="sort-row">
        <view
          v-for="d in difficulties"
          :key="d"
          class="pill"
          :class="{ active: difficulty === d }"
          @click="difficulty = d"
        >
          {{ d === '全部' ? '全部难度' : d }}
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
    </view>

    <!-- 项目列表 -->
    <view v-if="loading" class="empty-box">
      <text class="empty-icon">⏳</text>
      <text>加载中...</text>
    </view>
    <view v-else-if="filtered.length === 0" class="empty-box">
      <text class="empty-icon">📭</text>
      <text>没有找到符合条件的项目</text>
    </view>
    <view v-else class="list">
      <view v-for="p in filtered" :key="p.id" class="card project-card" @click="goDetail(p.id)">
        <view class="cover-wrap">
          <image
            v-if="p.coverUrl && !failedCovers[p.id]"
            :src="fullUrl(p.coverUrl)"
            class="cover"
            mode="aspectFill"
            @error="failedCovers[p.id] = true"
          />
          <view v-else class="cover cover-fallback">
            <text class="fallback-icon">{{ p.icon || '📦' }}</text>
          </view>
          <view class="badge cover-diff" :class="diffBadge(p.difficulty)">{{ p.difficulty }}</view>
          <view class="cover-rating">★ {{ p.rating }}</view>
        </view>
        <view class="body">
          <view class="title-row">
            <text class="title ellipsis">{{ p.title }}</text>
            <text v-if="p.verified" class="badge badge-blue verified">✓ 已验证</text>
          </view>
          <text class="summary ellipsis-2">{{ p.summary }}</text>
          <view class="tags">
            <text v-for="t in asList(p.tags).slice(0, 3)" :key="t" class="chip">{{ t }}</text>
          </view>
          <view class="meta-row">
            <text class="meta">👁 {{ shortNum(p.views) }}</text>
            <text class="meta">♥ {{ shortNum(p.favoriteCount) }}</text>
            <text class="meta">⏱ {{ p.duration }}</text>
            <text class="meta">👥 {{ p.enrolledCount }}人</text>
          </view>
          <view class="footer-row">
            <text class="mentor">{{ p.mentor || p.author }}</text>
            <text class="cost">成本 ¥{{ p.cost }}</text>
          </view>
        </view>
      </view>
      <view class="list-end muted">共 {{ filtered.length }} 个项目</view>
    </view>
  </view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onShow, onPullDownRefresh } from '@dcloudio/uni-app'
import { fetchProjects } from '@/api'
import { fullUrl } from '@/config'
import { asList, shortNum } from '@/utils/format'
import { getToken } from '@/utils/auth'

const sorts = [
  { label: '综合热度', value: 'popular' },
  { label: '评分最高', value: 'rating' },
  { label: '最新发布', value: 'newest' },
  { label: '下载最多', value: 'downloads' }
]
const difficulties = ['全部', '入门', '进阶', '挑战']

const keyword = ref('')
const sort = ref('popular')
const difficulty = ref('全部')
const category = ref('全部')
const items = ref([])
const loading = ref(true)
const failedCovers = ref({})
let loadedOnce = false

const categories = computed(() => {
  const set = new Set(items.value.map((p) => p.category).filter(Boolean))
  return ['全部', ...set]
})

const filtered = computed(() =>
  items.value.filter((p) => {
    if (difficulty.value !== '全部' && p.difficulty !== difficulty.value) return false
    if (category.value !== '全部' && p.category !== category.value) return false
    return true
  })
)

const diffBadge = (d) => (d === '入门' ? 'badge-green' : d === '进阶' ? 'badge-purple' : 'badge-red')

const load = async () => {
  loading.value = true
  try {
    items.value = await fetchProjects({ keyword: keyword.value.trim(), sort: sort.value })
  } catch (e) {
    // 请求层已提示
  } finally {
    loading.value = false
  }
}

const reload = () => load()

const clearKeyword = () => {
  keyword.value = ''
  load()
}

const changeSort = (v) => {
  sort.value = v
  load()
}

const goDetail = (id) => {
  uni.navigateTo({ url: `/pages/project-detail/index?id=${id}` })
}

onShow(() => {
  if (!getToken()) {
    uni.reLaunch({ url: '/pages/auth/index' })
    return
  }
  if (!loadedOnce) {
    loadedOnce = true
    load()
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

.toolbar {
  margin-bottom: 24rpx;
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

.sort-row {
  display: flex;
  gap: 16rpx;
  margin-top: 20rpx;
  flex-wrap: wrap;
}

.cate-scroll {
  margin-top: 20rpx;
  white-space: nowrap;
}

.cate-row {
  display: inline-flex;
  gap: 16rpx;
}

.list {
  display: flex;
  flex-direction: column;
  gap: 24rpx;
}

.project-card {
  padding: 0;
  overflow: hidden;
}

.cover-wrap {
  position: relative;
  height: 300rpx;
}

.cover {
  width: 100%;
  height: 300rpx;
}

.cover-fallback {
  background: linear-gradient(135deg, #3b82f6, #8b5cf6);
  display: flex;
  align-items: center;
  justify-content: center;
}

.fallback-icon {
  font-size: 88rpx;
}

.cover-diff {
  position: absolute;
  top: 20rpx;
  left: 20rpx;
}

.cover-rating {
  position: absolute;
  top: 20rpx;
  right: 20rpx;
  background: rgba(17, 24, 39, 0.6);
  color: #facc15;
  font-size: 22rpx;
  padding: 4rpx 18rpx;
  border-radius: $radius-pill;
}

.body {
  padding: 28rpx;
}

.title-row {
  display: flex;
  align-items: center;
  gap: 16rpx;
}

.title {
  font-size: 32rpx;
  font-weight: 600;
  flex: 1;
}

.verified {
  flex-shrink: 0;
}

.summary {
  display: block;
  color: $text-sub;
  font-size: 26rpx;
  margin-top: 12rpx;
}

.tags {
  display: flex;
  gap: 12rpx;
  margin-top: 20rpx;
  flex-wrap: wrap;
}

.meta-row {
  display: flex;
  gap: 28rpx;
  margin-top: 20rpx;
}

.meta {
  color: $text-light;
  font-size: 24rpx;
}

.footer-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 20rpx;
  padding-top: 20rpx;
  border-top: 2rpx solid $border-color;
}

.mentor {
  color: $text-sub;
  font-size: 24rpx;
}

.cost {
  color: $brand-blue;
  font-size: 24rpx;
  font-weight: 600;
}

.list-end {
  text-align: center;
  padding: 24rpx 0;
  font-size: 24rpx;
}
</style>
