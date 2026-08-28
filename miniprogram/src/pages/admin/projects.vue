<template>
  <view class="page">
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

    <view v-if="loading" class="empty-box">
      <uni-icons type="spinner-cycle" size="40" color="#d1d5db" />
      <text>加载中...</text>
    </view>
    <view v-else class="list">
      <view v-for="p in filtered" :key="p.id" class="card proj-card">
        <view class="pc-head" @click="goDetail(p.id)">
          <view class="pc-icon">{{ p.icon || '📦' }}</view>
          <view class="pc-info">
            <view class="pc-title-row">
              <text class="pc-title ellipsis">{{ p.title }}</text>
              <text class="badge" :class="p.status === 'PUBLISHED' ? 'badge-green' : 'badge-gray'">
                {{ p.status === 'PUBLISHED' ? '已发布' : '草稿' }}
              </text>
            </view>
            <text class="pc-sub muted">
              {{ p.difficulty }} · 讲师 {{ p.mentor || '未指派' }} · {{ p.enrolledCount || 0 }} 人报名
            </text>
            <text class="pc-sub muted">更新于 {{ relativeTime(p.updatedAt) }}</text>
          </view>
        </view>
        <view class="pc-actions">
          <view class="act-btn" @click="goEdit(p.id)">编辑</view>
          <view class="act-btn" @click="goStudents(p)">学生</view>
          <view class="act-btn" @click="assignMentor(p)">讲师</view>
          <view class="act-btn" @click="toggleStatus(p)">
            {{ p.status === 'PUBLISHED' ? '下架' : '发布' }}
          </view>
          <view class="act-btn danger" @click="onDelete(p)">删除</view>
        </view>
      </view>
      <view v-if="filtered.length === 0" class="empty-box">
        <uni-icons type="folder-add" size="40" color="#d1d5db" />
        <text>暂无项目</text>
      </view>
    </view>

    <view class="fab" @click="goEdit()">+ 新建项目</view>
    <view class="bottom-gap" />
  </view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onShow, onPullDownRefresh } from '@dcloudio/uni-app'
import { adminListProjects, adminUpdateProject, adminDeleteProject, adminListUsers } from '@/api'
import { relativeTime } from '@/utils/format'

const tabs = [
  { key: 'ALL', label: '全部' },
  { key: 'PUBLISHED', label: '已发布' },
  { key: 'DRAFT', label: '草稿' }
]

const activeTab = ref('ALL')
const items = ref([])
const teachers = ref([])
const loading = ref(true)

const filtered = computed(() =>
  activeTab.value === 'ALL' ? items.value : items.value.filter((p) => p.status === activeTab.value)
)

const load = async () => {
  loading.value = true
  try {
    items.value = await adminListProjects()
  } catch (e) {
    // 已提示
  } finally {
    loading.value = false
  }
}

const loadTeachers = async () => {
  try {
    const users = await adminListUsers()
    teachers.value = users.filter((u) => u.role === 'TEACHER' && u.enabled !== false)
  } catch (e) {
    // 静默
  }
}

const goDetail = (id) => uni.navigateTo({ url: `/pages/project-detail/index?id=${id}` })
const goEdit = (id) => uni.navigateTo({ url: `/pages/admin/project-edit${id ? '?id=' + id : ''}` })
const goStudents = (p) =>
  uni.navigateTo({ url: `/pages/teacher/students?projectId=${p.id}&title=${encodeURIComponent(p.title)}` })

const toggleStatus = (p) => {
  const next = p.status === 'PUBLISHED' ? 'DRAFT' : 'PUBLISHED'
  uni.showModal({
    title: next === 'PUBLISHED' ? '发布项目' : '下架项目',
    content: `确定将「${p.title}」${next === 'PUBLISHED' ? '发布上架,学生端可见' : '下架为草稿,学生端不可见'}?`,
    success: async (res) => {
      if (!res.confirm) return
      try {
        await adminUpdateProject(p.id, { ...p, status: next })
        uni.showToast({ title: next === 'PUBLISHED' ? '已发布' : '已下架', icon: 'success' })
        load()
      } catch (e) {
        // 已提示
      }
    }
  })
}

const assignMentor = async (p) => {
  if (!teachers.value.length) await loadTeachers()
  if (!teachers.value.length) {
    uni.showToast({ title: '暂无可指派的教师账号', icon: 'none' })
    return
  }
  uni.showActionSheet({
    itemList: teachers.value.map((t) => `${t.name}(${t.email})`).slice(0, 6),
    success: async (res) => {
      const t = teachers.value[res.tapIndex]
      try {
        await adminUpdateProject(p.id, { ...p, mentorId: t.id, mentor: t.name })
        uni.showToast({ title: `已指派给 ${t.name}`, icon: 'success' })
        load()
      } catch (e) {
        // 已提示
      }
    }
  })
}

const onDelete = (p) => {
  uni.showModal({
    title: '删除项目',
    content: `确定删除「${p.title}」?此操作不可恢复`,
    confirmColor: '#dc2626',
    success: async (res) => {
      if (!res.confirm) return
      try {
        await adminDeleteProject(p.id)
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
  loadTeachers()
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

.tab-row {
  display: flex;
  gap: 16rpx;
}

.list {
  display: flex;
  flex-direction: column;
  gap: 20rpx;
  margin-top: 24rpx;
}

.proj-card {
  padding: 28rpx;
}

.pc-head {
  display: flex;
  gap: 22rpx;
}

.pc-icon {
  width: 96rpx;
  height: 96rpx;
  border-radius: 20rpx;
  background: linear-gradient(135deg, #3b82f6, #8b5cf6);
  font-size: 44rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.pc-info {
  flex: 1;
  overflow: hidden;
}

.pc-title-row {
  display: flex;
  align-items: center;
  gap: 14rpx;
}

.pc-title {
  font-size: 30rpx;
  font-weight: 600;
  flex: 1;
}

.pc-sub {
  display: block;
  font-size: 23rpx;
  margin-top: 6rpx;
}

.pc-actions {
  display: flex;
  gap: 14rpx;
  margin-top: 22rpx;
  padding-top: 22rpx;
  border-top: 2rpx solid $border-color;
}

.act-btn {
  flex: 1;
  text-align: center;
  font-size: 24rpx;
  background: $gray-bg;
  border-radius: 14rpx;
  padding: 14rpx 0;

  &.danger {
    color: $red;
    background: $red-bg;
    flex: 0 0 100rpx;
  }
}

.fab {
  position: fixed;
  right: 32rpx;
  bottom: calc(48rpx + env(safe-area-inset-bottom));
  background: linear-gradient(90deg, #2563eb, #9333ea);
  color: #fff;
  font-size: 28rpx;
  font-weight: 600;
  border-radius: $radius-pill;
  padding: 22rpx 44rpx;
  box-shadow: 0 8rpx 24rpx rgba(37, 99, 235, 0.35);
  z-index: 50;
}

.bottom-gap {
  height: 120rpx;
}
</style>
