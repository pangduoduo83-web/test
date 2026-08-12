<template>
  <view class="page">
    <!-- 统计 -->
    <view class="stats-grid">
      <view class="stat-card">
        <text class="stat-num">{{ stats.projectCount || 0 }}</text>
        <text class="stat-label">我的项目</text>
      </view>
      <view class="stat-card">
        <text class="stat-num text-blue">{{ stats.studentTotal || 0 }}</text>
        <text class="stat-label">报名学生</text>
      </view>
      <view class="stat-card">
        <text class="stat-num text-purple">{{ stats.resourceCount || 0 }}</text>
        <text class="stat-label">教学资源</text>
      </view>
      <view class="stat-card">
        <text class="stat-num text-yellow">★{{ stats.avgRating || 0 }}</text>
        <text class="stat-label">平均评分</text>
      </view>
    </view>

    <!-- 项目列表 -->
    <view v-if="loading" class="empty-box">
      <text class="empty-icon">⏳</text>
      <text>加载中...</text>
    </view>
    <view v-else-if="projects.length === 0" class="empty-box">
      <text class="empty-icon">📚</text>
      <text>暂无名下项目,请联系管理员指派</text>
    </view>
    <view v-else class="list">
      <view v-for="p in projects" :key="p.id" class="card proj-card">
        <view class="pc-head" @click="goDetail(p.id)">
          <view class="pc-cover">
            <image
              v-if="p.coverUrl && !failedImgs[p.id]"
              :src="fullUrl(p.coverUrl)"
              class="pc-img"
              mode="aspectFill"
              @error="failedImgs[p.id] = true"
            />
            <view v-else class="pc-img pc-fallback">{{ p.icon || '📦' }}</view>
          </view>
          <view class="pc-info">
            <view class="pc-title-row">
              <text class="pc-title ellipsis">{{ p.title }}</text>
              <text class="badge" :class="p.status === 'PUBLISHED' ? 'badge-green' : 'badge-gray'">
                {{ p.status === 'PUBLISHED' ? '已发布' : '草稿' }}
              </text>
            </view>
            <text class="pc-meta muted">
              {{ p.enrolledCount || 0 }} 人报名 · ★{{ p.rating }} · {{ resCount(p) }} 个资源 · {{ p.difficulty }}
            </text>
            <text class="pc-updated muted">更新于 {{ relativeTime(p.updatedAt) }}</text>
          </view>
        </view>
        <view class="pc-actions">
          <view class="act-btn" @click="goStudents(p)">👨‍🎓 学生进度</view>
          <view class="act-btn" @click="goResources(p)">📂 教学资源</view>
          <view class="act-btn" @click="changeCover(p)">🖼 更换封面</view>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onShow, onPullDownRefresh } from '@dcloudio/uni-app'
import { teacherStats, teacherProjects, teacherUpdateCover } from '@/api'
import { uploadImage } from '@/utils/request'
import { fullUrl } from '@/config'
import { asList, relativeTime } from '@/utils/format'

const stats = ref({})
const projects = ref([])
const loading = ref(true)
const failedImgs = ref({})

const resCount = (p) => asList(p.resources).length

const load = async () => {
  loading.value = true
  try {
    const [s, list] = await Promise.all([teacherStats(), teacherProjects()])
    stats.value = s
    projects.value = list
  } catch (e) {
    // 已提示
  } finally {
    loading.value = false
  }
}

const goDetail = (id) => uni.navigateTo({ url: `/pages/project-detail/index?id=${id}` })

const goStudents = (p) =>
  uni.navigateTo({ url: `/pages/teacher/students?projectId=${p.id}&title=${encodeURIComponent(p.title)}` })

const goResources = (p) =>
  uni.navigateTo({ url: `/pages/teacher/resources?projectId=${p.id}&title=${encodeURIComponent(p.title)}` })

const changeCover = (p) => {
  uni.chooseImage({
    count: 1,
    sizeType: ['compressed'],
    success: async (res) => {
      uni.showLoading({ title: '上传中...' })
      try {
        const d = await uploadImage(res.tempFilePaths[0])
        await teacherUpdateCover(p.id, d.url)
        uni.showToast({ title: '封面已更新', icon: 'success' })
        load()
      } catch (e) {
        // 已提示
      } finally {
        uni.hideLoading()
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
  font-size: 36rpx;
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

.list {
  display: flex;
  flex-direction: column;
  gap: 24rpx;
  margin-top: 24rpx;
}

.proj-card {
  padding: 28rpx;
}

.pc-head {
  display: flex;
  gap: 22rpx;
}

.pc-cover {
  flex-shrink: 0;
}

.pc-img {
  width: 140rpx;
  height: 140rpx;
  border-radius: 20rpx;
}

.pc-fallback {
  background: linear-gradient(135deg, #3b82f6, #8b5cf6);
  font-size: 56rpx;
  display: flex;
  align-items: center;
  justify-content: center;
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
  font-size: 31rpx;
  font-weight: 600;
  flex: 1;
}

.pc-meta {
  display: block;
  font-size: 24rpx;
  margin-top: 10rpx;
}

.pc-updated {
  display: block;
  font-size: 22rpx;
  margin-top: 6rpx;
}

.pc-actions {
  display: flex;
  gap: 16rpx;
  margin-top: 24rpx;
  padding-top: 24rpx;
  border-top: 2rpx solid $border-color;
}

.act-btn {
  flex: 1;
  text-align: center;
  font-size: 25rpx;
  color: $text-main;
  background: $gray-bg;
  border-radius: 14rpx;
  padding: 16rpx 0;
}
</style>
