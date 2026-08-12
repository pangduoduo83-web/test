<template>
  <view class="page">
    <!-- 筛选 -->
    <view class="filter-row">
      <picker mode="selector" :range="projectRange" @change="changeProject($event.detail.value)">
        <view class="pill" :class="{ active: !!projectId }">📁 {{ projectLabel }}</view>
      </picker>
      <view class="search-box">
        <text class="search-icon">⌕</text>
        <input
          v-model="keyword"
          class="search-input"
          placeholder="搜索内容/用户名"
          placeholder-class="ph"
          confirm-type="search"
          @confirm="load"
        />
      </view>
    </view>

    <view v-if="loading" class="empty-box">
      <text class="empty-icon">⏳</text>
      <text>加载中...</text>
    </view>
    <view v-else-if="items.length === 0" class="empty-box">
      <text class="empty-icon">💬</text>
      <text>暂无讨论</text>
    </view>
    <view v-else class="list">
      <view v-for="d in items" :key="d.id" class="card disc-card">
        <view class="dc-head">
          <view class="dc-avatar">{{ (d.userName || '?')[0] }}</view>
          <view class="dc-info">
            <view class="dc-name-row">
              <text class="dc-user">{{ d.userName }}</text>
              <text class="badge" :class="d.parentId ? 'badge-gray' : 'badge-blue'">
                {{ d.parentId ? '回复' : '主题帖' }}
              </text>
            </view>
            <text class="dc-proj muted ellipsis">《{{ projectTitle(d.projectId) }}》· {{ relativeTime(d.createdAt) }}</text>
          </view>
          <text class="dc-del" @click="onDelete(d)">删除</text>
        </view>
        <text class="dc-content">{{ d.content }}</text>
      </view>
      <view class="list-end muted">共 {{ items.length }} 条</view>
    </view>
  </view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onShow, onPullDownRefresh } from '@dcloudio/uni-app'
import { adminListDiscussions, adminDeleteDiscussion, adminListProjects } from '@/api'
import { relativeTime } from '@/utils/format'

const items = ref([])
const projects = ref([])
const loading = ref(true)
const projectId = ref(null)
const keyword = ref('')

const projectRange = computed(() => ['全部项目', ...projects.value.map((p) => p.title)])
const projectLabel = computed(() => {
  if (!projectId.value) return '全部项目'
  const p = projects.value.find((x) => x.id === projectId.value)
  return p ? p.title : '全部项目'
})

const projectTitle = (id) => projects.value.find((p) => p.id === id)?.title || `项目#${id}`

const load = async () => {
  loading.value = true
  try {
    items.value = await adminListDiscussions({
      projectId: projectId.value || undefined,
      keyword: keyword.value.trim() || undefined
    })
  } catch (e) {
    // 已提示
  } finally {
    loading.value = false
  }
}

const loadProjects = async () => {
  try {
    projects.value = await adminListProjects()
  } catch (e) {
    // 静默
  }
}

const changeProject = (idx) => {
  const i = Number(idx)
  projectId.value = i > 0 ? projects.value[i - 1].id : null
  load()
}

const onDelete = (d) => {
  uni.showModal({
    title: '删除讨论',
    content: d.parentId
      ? `确定删除 ${d.userName} 的这条回复?`
      : `确定删除 ${d.userName} 的主题帖?其下所有回复将一并删除`,
    confirmColor: '#dc2626',
    success: async (res) => {
      if (!res.confirm) return
      try {
        const removed = await adminDeleteDiscussion(d.id)
        uni.showToast({ title: `已删除 ${removed} 条`, icon: 'success' })
        load()
      } catch (e) {
        // 已提示
      }
    }
  })
}

onShow(() => {
  load()
  loadProjects()
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

.filter-row {
  display: flex;
  gap: 16rpx;
  align-items: center;
  flex-wrap: wrap;
}

.search-box {
  flex: 1;
  min-width: 320rpx;
  display: flex;
  align-items: center;
  background: #fff;
  border-radius: $radius-pill;
  padding: 12rpx 24rpx;
  gap: 12rpx;
  box-shadow: 0 2rpx 12rpx rgba(17, 24, 39, 0.04);
}

.search-icon {
  color: $text-light;
  font-size: 32rpx;
  font-weight: 700;
}

.search-input {
  flex: 1;
  font-size: 27rpx;
  height: 44rpx;
}

.ph {
  color: $text-light;
}

.list {
  display: flex;
  flex-direction: column;
  gap: 20rpx;
  margin-top: 24rpx;
}

.disc-card {
  padding: 28rpx;
}

.dc-head {
  display: flex;
  align-items: center;
  gap: 18rpx;
}

.dc-avatar {
  width: 64rpx;
  height: 64rpx;
  border-radius: 50%;
  background: linear-gradient(135deg, #3b82f6, #8b5cf6);
  color: #fff;
  font-size: 26rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.dc-info {
  flex: 1;
  overflow: hidden;
}

.dc-name-row {
  display: flex;
  align-items: center;
  gap: 12rpx;
}

.dc-user {
  font-size: 28rpx;
  font-weight: 600;
}

.dc-proj {
  display: block;
  font-size: 22rpx;
  margin-top: 4rpx;
}

.dc-del {
  color: $red;
  font-size: 25rpx;
  flex-shrink: 0;
}

.dc-content {
  display: block;
  font-size: 27rpx;
  line-height: 1.7;
  background: $gray-bg;
  border-radius: 14rpx;
  padding: 18rpx 22rpx;
  margin-top: 18rpx;
}

.list-end {
  text-align: center;
  padding: 24rpx 0;
  font-size: 24rpx;
}
</style>
