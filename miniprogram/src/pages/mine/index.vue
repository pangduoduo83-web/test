<template>
  <view class="page">
    <!-- 游客提示:个人中心为登录后功能,不强制登录 -->
    <view v-if="!loggedIn" class="guest-card card">
      <view class="avatar avatar-fallback guest-avatar">?</view>
      <text class="guest-title">未登录</text>
      <text class="guest-sub">登录后可查看学习数据、成就徽章与个人设置</text>
      <button class="btn-gradient guest-btn" @click="goLogin">登录 / 注册</button>
    </view>

    <template v-else>
    <!-- 用户头卡 -->
    <view class="user-card">
      <view class="user-row">
        <view class="avatar-wrap" @click="goProfile">
          <image v-if="user?.avatarUrl" :src="fullUrl(user.avatarUrl)" class="avatar" mode="aspectFill" />
          <view v-else class="avatar avatar-fallback">{{ (user?.name || '?')[0] }}</view>
        </view>
        <view class="user-info">
          <text class="user-name">{{ user?.name || '未登录' }}</text>
          <text class="user-sub">{{ user?.major || '-' }} · {{ user?.grade || '-' }} · 学号 {{ user?.studentNo || '-' }}</text>
        </view>
        <view class="bell" @click="goNotifications">
          <uni-icons type="notification-filled" size="22" color="#ffffff" />
          <view v-if="unread > 0" class="bell-badge">{{ unread > 99 ? '99+' : unread }}</view>
        </view>
      </view>
      <view class="level-row">
        <text class="level-badge">Lv.{{ dash.level || 1 }} 实践者</text>
        <view class="level-track">
          <view class="level-fill" :style="{ width: (dash.levelProgress || 0) + '%' }" />
        </view>
        <text class="level-text">{{ dash.levelProgress || 0 }}/100</text>
      </view>
      <view class="week-row">
        <text class="week-text">本周实践 {{ dash.weeklyHours || 0 }} 小时,继续加油</text>
      </view>
    </view>

    <!-- 统计四宫格 -->
    <view class="stats-grid">
      <view class="stat-card">
        <uni-icons type="flag" size="19" color="#2563eb" />
        <text class="stat-num">{{ dash.completedProjects || 0 }}</text>
        <text class="stat-label">完成项目</text>
      </view>
      <view class="stat-card">
        <uni-icons type="calendar" size="19" color="#16a34a" />
        <text class="stat-num">{{ dash.weeklyHours || 0 }}h</text>
        <text class="stat-label">本周时长</text>
      </view>
      <view class="stat-card">
        <uni-icons type="medal" size="19" color="#f59e0b" />
        <text class="stat-num">{{ dash.achievementCount || 0 }}</text>
        <text class="stat-label">成就徽章</text>
      </view>
      <view class="stat-card">
        <uni-icons type="bars" size="19" color="#9333ea" />
        <text class="stat-num">{{ dash.skillAvg || 0 }}%</text>
        <text class="stat-label">技能掌握</text>
      </view>
    </view>

    <!-- 学习趋势 -->
    <view class="card block">
      <view class="block-head">
        <text class="section-title">学习趋势</text>
        <view class="seg-group">
          <view class="seg" :class="{ active: trendRange === 'week' }" @click="trendRange = 'week'">本周</view>
          <view class="seg" :class="{ active: trendRange === 'month' }" @click="trendRange = 'month'">本月</view>
        </view>
      </view>
      <view class="seg-group series-seg">
        <view class="seg" :class="{ active: trendSeries === 'hours' }" @click="trendSeries = 'hours'">学习时长</view>
        <view class="seg" :class="{ active: trendSeries === 'tasks' }" @click="trendSeries = 'tasks'">完成任务</view>
      </view>
      <view class="chart" :class="{ dense: trendRange === 'month' }">
        <view
          v-for="(v, i) in trendData"
          :key="i"
          class="bar-col"
        >
          <view class="bar" :style="{ height: barHeight(v) }" />
        </view>
      </view>
      <view class="chart-axis">
        <text class="axis-label">{{ trendRange === 'week' ? '7天前' : '30天前' }}</text>
        <text class="axis-label">今天</text>
      </view>
      <text class="chart-tip muted">
        {{ trendRange === 'week' ? '近7天' : '近30天' }}累计{{ trendSeries === 'hours' ? '学习' : '完成任务' }}
        {{ trendSum }}{{ trendSeries === 'hours' ? ' 小时' : ' 个' }}
      </text>
    </view>

    <!-- 进行中项目 -->
    <view class="card block">
      <view class="block-head">
        <text class="section-title">进行中的项目</text>
        <text class="more" @click="goProjects">去项目中心 ›</text>
      </view>
      <view v-if="ongoing.length === 0" class="empty-box small-empty">
        <uni-icons type="flag" size="40" color="#d1d5db" />
        <text>还没有进行中的项目,去报名一个吧</text>
      </view>
      <view v-for="e in ongoing" :key="e.id" class="ongoing-item" @click="goProjectDetail(e.projectId)">
        <view class="og-head">
          <text class="og-title ellipsis">{{ e.projectTitle }}</text>
          <text class="og-progress">{{ e.progress || 0 }}%</text>
        </view>
        <view class="progress-track og-track">
          <view class="progress-fill" :style="{ width: (e.progress || 0) + '%' }" />
        </view>
        <view class="og-foot">
          <text class="og-task muted ellipsis">{{ e.currentTask || '尚未开始任务' }}</text>
          <view class="push-btn" @click.stop="pushProgress(e)">推进 +10%</view>
        </view>
        <text v-if="e.deadline" class="og-deadline">截止 {{ formatDate(e.deadline) }}</text>
      </view>
    </view>

    <!-- 成就徽章 -->
    <view class="card block">
      <text class="section-title">成就徽章</text>
      <view class="ach-grid">
        <view v-for="a in achievements" :key="a.name" class="ach-item" :class="{ locked: !a.unlocked }">
          <uni-icons :type="achIconType(a.name)" size="26" :color="a.unlocked ? '#f59e0b' : '#9ca3af'" />
          <text class="ach-name">{{ a.name }}</text>
          <text class="ach-desc">{{ a.desc }}</text>
          <text class="badge" :class="a.unlocked ? 'badge-green' : 'badge-gray'">
            {{ a.unlocked ? '已解锁' : '未解锁' }}
          </text>
        </view>
      </view>
    </view>

    <!-- 教师功能 -->
    <view v-if="isTeacher || isAdmin" class="card block menu-card">
      <view class="menu-group-title">教师功能</view>
      <view class="menu-item" @click="goPage('/pages/teacher/workbench')">
        <uni-icons type="home" size="17" color="#4b5563" />
        <text class="menu-text">教学工作台</text>
        <text class="menu-arrow">›</text>
      </view>
    </view>

    <!-- 管理功能 -->
    <view v-if="isAdmin" class="card block menu-card">
      <view class="menu-group-title">管理控制台</view>
      <view class="menu-item" @click="goPage('/pages/admin/dashboard')">
        <uni-icons type="bars" size="17" color="#4b5563" />
        <text class="menu-text">数据看板</text>
        <text class="menu-arrow">›</text>
      </view>
      <view class="menu-item" @click="goPage('/pages/admin/borrows')">
        <uni-icons type="checkbox" size="17" color="#4b5563" />
        <text class="menu-text">借阅审批</text>
        <text class="menu-arrow">›</text>
      </view>
      <view class="menu-item" @click="goPage('/pages/admin/submissions')">
        <uni-icons type="compose" size="17" color="#4b5563" />
        <text class="menu-text">成果评分</text>
        <text class="menu-arrow">›</text>
      </view>
      <view class="menu-item" @click="goPage('/pages/admin/enrollments')">
        <uni-icons type="personadd" size="17" color="#4b5563" />
        <text class="menu-text">报名管理</text>
        <text class="menu-arrow">›</text>
      </view>
      <view class="menu-item" @click="goPage('/pages/admin/notifications')">
        <uni-icons type="sound" size="17" color="#4b5563" />
        <text class="menu-text">通知管理</text>
        <text class="menu-arrow">›</text>
      </view>
      <view class="menu-item" @click="goPage('/pages/admin/discussions')">
        <uni-icons type="chatboxes" size="17" color="#4b5563" />
        <text class="menu-text">讨论管理</text>
        <text class="menu-arrow">›</text>
      </view>
      <view class="menu-item" @click="goPage('/pages/admin/ai-settings')">
        <uni-icons type="tune" size="17" color="#4b5563" />
        <text class="menu-text">AI 设置</text>
        <text class="menu-arrow">›</text>
      </view>
      <view class="menu-item" @click="goPage('/pages/admin/equipment')">
        <uni-icons type="gear" size="17" color="#4b5563" />
        <text class="menu-text">设备管理</text>
        <text class="menu-arrow">›</text>
      </view>
      <view class="menu-item" @click="goPage('/pages/admin/projects')">
        <uni-icons type="folder-add" size="17" color="#4b5563" />
        <text class="menu-text">项目管理</text>
        <text class="menu-arrow">›</text>
      </view>
      <view class="menu-item" @click="goPage('/pages/admin/users')">
        <uni-icons type="staff" size="17" color="#4b5563" />
        <text class="menu-text">用户管理</text>
        <text class="menu-arrow">›</text>
      </view>
    </view>

    <!-- 功能菜单 -->
    <view class="card block menu-card">
      <view class="menu-item" @click="goSkills">
        <uni-icons type="medal" size="17" color="#4b5563" />
        <text class="menu-text">技能评估</text>
        <text class="menu-arrow">›</text>
      </view>
      <view class="menu-item" @click="goNotifications">
        <uni-icons type="notification" size="17" color="#4b5563" />
        <text class="menu-text">站内通知</text>
        <view v-if="unread > 0" class="menu-badge">{{ unread }}</view>
        <text class="menu-arrow">›</text>
      </view>
      <view class="menu-item" @click="goProfile">
        <uni-icons type="person" size="17" color="#4b5563" />
        <text class="menu-text">编辑资料</text>
        <text class="menu-arrow">›</text>
      </view>
      <view class="menu-item" @click="goPassword">
        <uni-icons type="locked" size="17" color="#4b5563" />
        <text class="menu-text">修改密码</text>
        <text class="menu-arrow">›</text>
      </view>
      <view class="menu-item" @click="onLogout">
        <uni-icons type="undo" size="17" color="#dc2626" />
        <text class="menu-text logout-text">退出登录</text>
        <text class="menu-arrow">›</text>
      </view>
      <view class="menu-item" @click="onDeleteAccount">
        <uni-icons type="trash" size="17" color="#9ca3af" />
        <text class="menu-text muted-text">注销账号</text>
        <text class="menu-arrow">›</text>
      </view>
    </view>
    </template>

    <view class="footer muted">AI未来实践中心 · 项目驱动教学实验平台</view>
  </view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onShow, onPullDownRefresh } from '@dcloudio/uni-app'
import { fetchDashboard, fetchNotifications, updateProgress, deleteAccount } from '@/api'
import { fullUrl } from '@/config'
import { formatDate } from '@/utils/format'
import { getToken } from '@/utils/auth'
import { useAuthStore } from '@/stores/auth'

const authStore = useAuthStore()
const dash = ref({})
const unread = ref(0)
const trendRange = ref('week')
const trendSeries = ref('hours')
const loggedIn = ref(true)

const user = computed(() => dash.value.user || authStore.user)
const ongoing = computed(() => dash.value.ongoingProjects || [])
const achievements = computed(() => dash.value.achievements || [])
const isTeacher = computed(() => user.value?.role === 'TEACHER')
const isAdmin = computed(() => user.value?.role === 'ADMIN')

const trendData = computed(() => {
  const key = trendRange.value === 'week'
    ? trendSeries.value === 'hours' ? 'weekTrend' : 'weekTaskTrend'
    : trendSeries.value === 'hours' ? 'monthTrend' : 'monthTaskTrend'
  return dash.value[key] || []
})

const trendMax = computed(() => Math.max(1, ...trendData.value))
const trendSum = computed(() => trendData.value.reduce((a, b) => a + b, 0))

const barHeight = (v) => `${Math.max(6, Math.round((v / trendMax.value) * 100))}%`

// 后端成就 icon 为 emoji(网页端沿用),小程序侧按成就名映射为 uni-icons 图标
const achIconType = (name) => ({
  初出茅庐: 'flag',
  借阅达人: 'cart',
  项目先锋: 'fire',
  技术大牛: 'medal'
}[name] || 'medal')

const load = async () => {
  try {
    dash.value = await fetchDashboard()
    if (dash.value.user) authStore.updateUser(dash.value.user)
  } catch (e) {
    // 已提示
  }
  fetchNotifications()
    .then((d) => {
      unread.value = d?.unread || 0
    })
    .catch(() => {})
}

const pushProgress = (e) => {
  const next = Math.min(100, (e.progress || 0) + 10)
  uni.showModal({
    title: '推进进度',
    content: next >= 100 ? `将进度推进到 100%,完成「${e.projectTitle}」?` : `将「${e.projectTitle}」进度推进到 ${next}%?`,
    success: async (res) => {
      if (!res.confirm) return
      try {
        await updateProgress(e.projectId, { progress: next, currentTask: e.currentTask })
        uni.showToast({ title: next >= 100 ? '恭喜完成项目 +50经验' : '进度已更新', icon: 'success' })
        load()
      } catch (err) {
        // 已提示
      }
    }
  })
}

const goPage = (url) => uni.navigateTo({ url })
const goProfile = () => uni.navigateTo({ url: '/pages/profile/index' })
const goPassword = () => uni.navigateTo({ url: '/pages/profile/password' })
const goSkills = () => uni.navigateTo({ url: '/pages/skills/index' })
const goNotifications = () => uni.navigateTo({ url: '/pages/notifications/index' })
const goProjects = () => uni.switchTab({ url: '/pages/projects/index' })
const goProjectDetail = (id) => uni.navigateTo({ url: `/pages/project-detail/index?id=${id}` })

const goLogin = () => {
  uni.navigateTo({ url: '/pages/auth/index' })
}

const onLogout = () => {
  uni.showModal({
    title: '退出登录',
    content: '确定退出当前账号吗?',
    confirmColor: '#dc2626',
    success: (res) => {
      if (!res.confirm) return
      authStore.logout()
      // 退出后回到可游客浏览的项目页,而非强制停留登录页
      uni.reLaunch({ url: '/pages/projects/index' })
    }
  })
}

// 账号注销:两次确认 + 密码验证,数据不可恢复(小程序审核要求提供注销途径)
const onDeleteAccount = () => {
  uni.showModal({
    title: '注销账号',
    content: '注销后账号及全部数据(报名进度、借阅记录、成果、收藏、讨论等)将被永久删除且无法恢复。如有未归还设备需先归还。确定继续吗?',
    confirmText: '继续注销',
    confirmColor: '#dc2626',
    success: (res) => {
      if (!res.confirm) return
      uni.showModal({
        title: '身份验证',
        editable: true,
        placeholderText: '请输入当前登录密码',
        confirmText: '确认注销',
        confirmColor: '#dc2626',
        success: async (r2) => {
          if (!r2.confirm) return
          const pwd = (r2.content || '').trim()
          if (!pwd) {
            uni.showToast({ title: '请输入密码', icon: 'none' })
            return
          }
          try {
            await deleteAccount(pwd)
            authStore.logout()
            uni.showToast({ title: '账号已注销', icon: 'none' })
            setTimeout(() => uni.reLaunch({ url: '/pages/projects/index' }), 600)
          } catch (e) {
            // 请求层已提示(密码错误/有未归还设备等)
          }
        }
      })
    }
  })
}

onShow(() => {
  loggedIn.value = !!getToken()
  if (!loggedIn.value) {
    dash.value = {}
    unread.value = 0
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

.user-card {
  background: linear-gradient(135deg, #2563eb, #9333ea);
  border-radius: $radius-card;
  padding: 36rpx;
}

.user-row {
  display: flex;
  align-items: center;
  gap: 24rpx;
}

.avatar-wrap {
  flex-shrink: 0;
}

.avatar {
  width: 110rpx;
  height: 110rpx;
  border-radius: 50%;
  border: 4rpx solid rgba(255, 255, 255, 0.4);
}

.avatar-fallback {
  background: rgba(255, 255, 255, 0.25);
  color: #fff;
  font-size: 44rpx;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  box-sizing: border-box;
}

.user-info {
  flex: 1;
  overflow: hidden;
}

.user-name {
  display: block;
  color: #fff;
  font-size: 36rpx;
  font-weight: 700;
}

.user-sub {
  display: block;
  color: rgba(255, 255, 255, 0.75);
  font-size: 23rpx;
  margin-top: 8rpx;
}

.bell {
  position: relative;
  padding: 8rpx;
}

.bell-badge {
  position: absolute;
  top: -6rpx;
  right: -10rpx;
  background: #ef4444;
  color: #fff;
  font-size: 20rpx;
  border-radius: $radius-pill;
  padding: 0 10rpx;
  min-width: 32rpx;
  height: 32rpx;
  line-height: 32rpx;
  text-align: center;
}

.level-row {
  display: flex;
  align-items: center;
  gap: 20rpx;
  margin-top: 32rpx;
}

.level-badge {
  color: #fff;
  font-size: 24rpx;
  font-weight: 600;
  background: rgba(255, 255, 255, 0.2);
  border-radius: $radius-pill;
  padding: 4rpx 20rpx;
  flex-shrink: 0;
}

.level-track {
  flex: 1;
  height: 14rpx;
  background: rgba(255, 255, 255, 0.25);
  border-radius: $radius-pill;
  overflow: hidden;
}

.level-fill {
  height: 100%;
  background: #fff;
  border-radius: $radius-pill;
}

.level-text {
  color: rgba(255, 255, 255, 0.85);
  font-size: 22rpx;
  flex-shrink: 0;
}

.week-row {
  margin-top: 20rpx;
}

.week-text {
  color: rgba(255, 255, 255, 0.85);
  font-size: 24rpx;
}

.stats-grid {
  display: flex;
  gap: 16rpx;
  margin-top: 24rpx;
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
  font-size: 34rpx;
  font-weight: 700;
  margin-top: 8rpx;
}

.stat-label {
  font-size: 22rpx;
  color: $text-sub;
  margin-top: 4rpx;
}

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

.seg-group {
  display: inline-flex;
  background: $gray-bg;
  border-radius: 14rpx;
  padding: 4rpx;
}

.series-seg {
  margin-top: 20rpx;
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
  gap: 12rpx;
  height: 240rpx;
  margin-top: 28rpx;
  padding: 0 4rpx;

  &.dense {
    gap: 4rpx;
  }
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
  border-radius: 8rpx 8rpx 0 0;
  min-height: 6rpx;
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
  margin-top: 16rpx;
  font-size: 24rpx;
}

.small-empty {
  padding: 60rpx 0;
}

.ongoing-item {
  margin-top: 28rpx;
  padding: 24rpx;
  background: $gray-bg;
  border-radius: 20rpx;
}

.og-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16rpx;
}

.og-title {
  font-size: 29rpx;
  font-weight: 600;
  flex: 1;
}

.og-progress {
  font-size: 26rpx;
  font-weight: 700;
  color: $brand-blue;
}

.og-track {
  margin-top: 16rpx;
  background: #fff;
}

.og-foot {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 16rpx;
  gap: 16rpx;
}

.og-task {
  flex: 1;
  font-size: 24rpx;
}

.push-btn {
  font-size: 24rpx;
  color: $brand-blue;
  background: $blue-bg;
  border-radius: $radius-pill;
  padding: 6rpx 24rpx;
  flex-shrink: 0;
}

.og-deadline {
  display: block;
  font-size: 22rpx;
  color: $text-light;
  margin-top: 12rpx;
}

.ach-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 16rpx;
  margin-top: 24rpx;
}

.ach-item {
  width: calc(50% - 8rpx);
  background: $gray-bg;
  border-radius: 20rpx;
  padding: 28rpx 20rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  box-sizing: border-box;

  &.locked {
    opacity: 0.55;
  }
}

.ach-name {
  font-size: 28rpx;
  font-weight: 600;
  margin-top: 12rpx;
}

.ach-desc {
  font-size: 22rpx;
  color: $text-sub;
  margin-top: 6rpx;
  margin-bottom: 14rpx;
  text-align: center;
}

.menu-card {
  padding: 8rpx 32rpx;
}

.menu-group-title {
  font-size: 26rpx;
  font-weight: 700;
  color: $text-sub;
  padding: 24rpx 0 6rpx;
}

.menu-item {
  display: flex;
  align-items: center;
  gap: 20rpx;
  padding: 30rpx 0;
  border-bottom: 2rpx solid $border-color;

  &:last-child {
    border-bottom: none;
  }
}

.menu-text {
  flex: 1;
  font-size: 29rpx;
}

.logout-text {
  color: $red;
}

.muted-text {
  color: $text-sub;
}

.menu-badge {
  background: #ef4444;
  color: #fff;
  font-size: 20rpx;
  border-radius: $radius-pill;
  padding: 0 12rpx;
  height: 32rpx;
  line-height: 32rpx;
}

.menu-arrow {
  color: $text-light;
  font-size: 34rpx;
}

.footer {
  text-align: center;
  padding: 40rpx 0 20rpx;
  font-size: 22rpx;
}

.guest-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16rpx;
  padding: 80rpx 32rpx;
}

.guest-avatar {
  width: 120rpx;
  height: 120rpx;
  border-radius: 50%;
  background: $gray-bg;
  color: $text-light;
  font-size: 52rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}

.guest-title {
  font-size: 34rpx;
  font-weight: 600;
}

.guest-sub {
  color: $text-sub;
  font-size: 26rpx;
}

.guest-btn {
  margin-top: 16rpx;
  width: 360rpx;
}
</style>
