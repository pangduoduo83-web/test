<template>
  <view v-if="project" class="page">
    <!-- 封面 -->
    <view class="hero">
      <image
        v-if="project.coverUrl && !coverFailed"
        :src="fullUrl(project.coverUrl)"
        class="hero-img"
        mode="aspectFill"
        @error="coverFailed = true"
      />
      <view v-else class="hero-img hero-fallback">
        <text class="hero-icon">{{ project.icon || '📦' }}</text>
      </view>
      <view class="hero-badges">
        <text class="badge" :class="diffBadge(project.difficulty)">{{ project.difficulty }}</text>
        <text v-if="project.verified" class="badge badge-blue">✓ 硬件已验证</text>
        <text v-if="project.license" class="badge badge-gray">{{ project.license }}</text>
      </view>
    </view>

    <!-- 标题与统计 -->
    <view class="card head-card">
      <text class="title">{{ project.title }}</text>
      <text v-if="project.summary" class="summary">{{ project.summary }}</text>
      <view class="quick-stats">
        <view class="qs-item">
          <text class="qs-num">{{ project.duration }}</text>
          <text class="qs-label">周期</text>
        </view>
        <view class="qs-item">
          <text class="qs-num">{{ project.teamSize }}</text>
          <text class="qs-label">团队</text>
        </view>
        <view class="qs-item">
          <text class="qs-num">★ {{ project.rating }}</text>
          <text class="qs-label">评分</text>
        </view>
        <view class="qs-item">
          <text class="qs-num">{{ shortNum(project.views) }}</text>
          <text class="qs-label">浏览</text>
        </view>
      </view>
      <view class="counter-row">
        <text class="counter">♥ {{ shortNum(project.favoriteCount) }} 收藏</text>
        <text class="counter">⑂ {{ shortNum(project.forks) }} Fork</text>
        <text class="counter">↓ {{ shortNum(project.downloads) }} 下载</text>
        <text class="counter">¥{{ project.cost }} 成本</text>
      </view>
      <view class="mentor-row">
        <view class="mentor-avatar">{{ (project.mentor || '师')[0] }}</view>
        <view class="mentor-info">
          <text class="mentor-name">{{ project.mentor || '待指派' }} · 指导教师</text>
          <text class="mentor-sub">作者 {{ project.author || '-' }} · 更新于 {{ formatDate(project.updatedAt) }}</text>
        </view>
      </view>
    </view>

    <!-- 已报名进度 -->
    <view v-if="enrolled && enrollment" class="card enroll-card">
      <view class="enroll-head">
        <text class="section-title">📈 我的进度</text>
        <text class="badge" :class="enrollment.status === 'COMPLETED' ? 'badge-green' : 'badge-blue'">
          {{ enrollment.status === 'COMPLETED' ? '已完成' : '进行中' }}
        </text>
      </view>
      <view class="progress-track enroll-track">
        <view class="progress-fill" :style="{ width: (enrollment.progress || 0) + '%' }" />
      </view>
      <view class="enroll-meta">
        <text class="muted">{{ enrollment.progress || 0 }}% · {{ enrollment.currentTask || '尚未开始任务' }}</text>
        <text v-if="enrollment.deadline" class="muted">截止 {{ formatDate(enrollment.deadline) }}</text>
      </view>
      <button class="btn-plain submit-work-btn" @click="goSubmission">📤 提交项目成果 / 查看评分</button>
    </view>

    <!-- Tab 区 -->
    <view class="card tab-card">
      <scroll-view scroll-x class="tab-scroll" :show-scrollbar="false">
        <view class="tab-row">
          <view
            v-for="t in tabs"
            :key="t.key"
            class="tab-item"
            :class="{ active: activeTab === t.key }"
            @click="activeTab = t.key"
          >
            {{ t.label }}
          </view>
        </view>
      </scroll-view>

      <!-- 概览 -->
      <view v-if="activeTab === 'overview'" class="tab-body">
        <view class="desc-block">
          <text class="sub-title">项目描述</text>
          <template v-if="richDesc">
            <template v-for="(seg, i) in richDesc" :key="i">
              <rich-text v-if="seg.type === 'html'" class="rich-html" :nodes="seg.content" />
              <video v-else class="rich-video" :src="seg.src" controls />
            </template>
          </template>
          <text v-else class="full-desc">{{ project.description || project.summary || '暂无详细描述' }}</text>
        </view>
        <view v-if="features.length" class="sub-block">
          <text class="sub-title">项目特性</text>
          <view class="tags">
            <text v-for="f in features" :key="f" class="chip chip-blue">{{ f }}</text>
          </view>
        </view>
        <view v-if="learningGoals.length" class="sub-block">
          <text class="sub-title">学习目标</text>
          <view v-for="(g, i) in learningGoals" :key="i" class="li-row">
            <text class="li-dot ok">✓</text>
            <text class="li-text">{{ g }}</text>
          </view>
        </view>
        <view v-if="prerequisites.length" class="sub-block">
          <text class="sub-title">先修要求</text>
          <view v-for="(g, i) in prerequisites" :key="i" class="li-row">
            <text class="li-dot warn">!</text>
            <text class="li-text">{{ g }}</text>
          </view>
        </view>
      </view>

      <!-- 技能要求 -->
      <view v-else-if="activeTab === 'skills'" class="tab-body">
        <view v-if="skillRequirements.length === 0" class="empty-box">
          <text class="empty-icon">🎯</text>
          <text>本项目未设置技能要求</text>
        </view>
        <view v-for="req in skillRequirements" :key="req.name" class="skill-item">
          <view class="skill-head">
            <text class="skill-name">{{ req.name }}</text>
            <text class="skill-score" :class="mySkill(req.name) >= req.required ? 'ok-text' : 'warn-text'">
              我的 {{ mySkill(req.name) }} / 要求 {{ req.required }}
            </text>
          </view>
          <view class="progress-track skill-track">
            <view
              class="progress-fill"
              :class="{ 'fill-warn': mySkill(req.name) < req.required }"
              :style="{ width: Math.min(100, mySkill(req.name)) + '%' }"
            />
            <view class="require-mark" :style="{ left: req.required + '%' }" />
          </view>
        </view>
        <view v-if="skillRequirements.length" class="skill-tip muted">
          竖线为项目要求水平,不足的维度可先完成入门项目提升
        </view>
      </view>

      <!-- 所需设备 -->
      <view v-else-if="activeTab === 'equipment'" class="tab-body">
        <view v-if="equipmentNames.length === 0" class="empty-box">
          <text class="empty-icon">🧰</text>
          <text>本项目无需专用设备</text>
        </view>
        <view v-for="(name, i) in equipmentNames" :key="i" class="equip-row">
          <text class="equip-icon">🔧</text>
          <text class="equip-name">{{ name }}</text>
        </view>
        <button v-if="equipmentNames.length" class="btn-plain go-equip" @click="goEquipment">去设备图书馆借用</button>
      </view>

      <!-- 教学大纲 -->
      <view v-else-if="activeTab === 'syllabus'" class="tab-body">
        <view v-if="syllabus.length === 0" class="empty-box">
          <text class="empty-icon">📚</text>
          <text>教学大纲整理中</text>
        </view>
        <view v-for="(s, i) in syllabus" :key="i" class="syllabus-item">
          <view class="sy-left">
            <view class="sy-dot">{{ i + 1 }}</view>
            <view v-if="i < syllabus.length - 1" class="sy-line" />
          </view>
          <view class="sy-body">
            <view class="sy-head">
              <text class="sy-phase">{{ s.phase }}</text>
              <text class="chip">{{ s.hours }} 学时</text>
            </view>
            <text class="sy-title">{{ s.title }}</text>
            <text class="sy-content">{{ s.content }}</text>
          </view>
        </view>
      </view>

      <!-- BOM -->
      <view v-else-if="activeTab === 'bom'" class="tab-body">
        <view v-if="bom.length === 0" class="empty-box">
          <text class="empty-icon">🧾</text>
          <text>BOM 清单整理中</text>
        </view>
        <template v-else>
          <view class="bom-row bom-head">
            <text class="bom-ref">位号</text>
            <text class="bom-name">元件</text>
            <text class="bom-qty">数量</text>
            <text class="bom-price">单价</text>
          </view>
          <view v-for="(b, i) in bom" :key="i" class="bom-row">
            <text class="bom-ref">{{ b.ref }}</text>
            <view class="bom-name">
              <text class="bom-name-text">{{ b.name }}</text>
              <text class="bom-fp">{{ b.footprint }}</text>
            </view>
            <text class="bom-qty">×{{ b.qty }}</text>
            <text class="bom-price">¥{{ b.price }}</text>
          </view>
          <view class="bom-total">
            <text>合计(含数量)</text>
            <text class="bom-total-num">¥{{ bomTotal }}</text>
          </view>
        </template>
      </view>

      <!-- 学习资源 -->
      <view v-else-if="activeTab === 'resources'" class="tab-body">
        <view v-if="resources.length === 0" class="empty-box">
          <text class="empty-icon">📂</text>
          <text>暂无学习资源</text>
        </view>
        <view v-for="(r, i) in resources" :key="i" class="res-row" @click="openResource(r)">
          <text class="res-icon">{{ resIcon(r.type) }}</text>
          <view class="res-info">
            <text class="res-name ellipsis">{{ r.name }}</text>
            <text class="res-type">{{ r.type }}{{ r.url ? '' : ' · 资源准备中' }}</text>
          </view>
          <text class="res-action">{{ r.url ? '查看' : '' }}</text>
        </view>
      </view>

      <!-- 讨论 -->
      <view v-else-if="activeTab === 'discuss'" class="tab-body">
        <view v-if="discussions.length === 0" class="empty-box">
          <text class="empty-icon">💬</text>
          <text>还没有讨论,来发第一帖</text>
        </view>
        <view v-for="d in discussions" :key="d.item.id" class="disc-item">
          <view class="disc-avatar">{{ (d.item.userName || '?')[0] }}</view>
          <view class="disc-body">
            <view class="disc-head">
              <text class="disc-user">{{ d.item.userName }}</text>
              <text class="disc-time">{{ relativeTime(d.item.createdAt) }}</text>
            </view>
            <text class="disc-content">{{ d.item.content }}</text>
            <text class="disc-reply-btn" @click="startReply(d.item)">回复</text>
            <view v-for="r in d.replies" :key="r.id" class="disc-sub">
              <view class="disc-head">
                <text class="disc-user">{{ r.userName }}</text>
                <text class="disc-time">{{ relativeTime(r.createdAt) }}</text>
              </view>
              <text class="disc-content">{{ r.content }}</text>
            </view>
          </view>
        </view>

        <view class="disc-input-area">
          <view v-if="replyTarget" class="reply-tip">
            <text class="muted">回复 @{{ replyTarget.userName }}</text>
            <text class="reply-cancel" @click="cancelReply">取消</text>
          </view>
          <view class="disc-input-row">
            <input
              v-model="discussContent"
              class="disc-input"
              :placeholder="replyTarget ? `回复 ${replyTarget.userName}...` : '说点什么...'"
              placeholder-class="ph"
              confirm-type="send"
              @confirm="sendDiscussion"
            />
            <button class="btn-gradient disc-send" :disabled="posting" @click="sendDiscussion">发送</button>
          </view>
        </view>
      </view>
    </view>

    <view class="bottom-gap" />

    <!-- 底部操作栏 -->
    <view class="action-bar">
      <view class="fav-btn" @click="onToggleFavorite">
        <text class="fav-icon" :class="{ faved: favorited }">{{ favorited ? '♥' : '♡' }}</text>
        <text class="fav-text">{{ favorited ? '已收藏' : '收藏' }}</text>
      </view>
      <button v-if="!enrolled" class="btn-gradient enroll-btn" :disabled="enrolling" @click="onEnroll">
        立即报名参与
      </button>
      <button v-else class="btn-plain enroll-btn enrolled-btn" disabled>
        已报名 · 进度 {{ enrollment?.progress || 0 }}%
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
import { onLoad, onShareAppMessage } from '@dcloudio/uni-app'
import {
  fetchProjectDetail,
  enrollProject,
  toggleFavorite,
  fetchDiscussions,
  postDiscussion,
  fetchSkills
} from '@/api'
import { fullUrl } from '@/config'
import { asList, formatDate, relativeTime, shortNum } from '@/utils/format'
import { prepareRich } from '@/utils/rich'
import { getToken, ensureLogin } from '@/utils/auth'

const tabs = [
  { key: 'overview', label: '概览' },
  { key: 'skills', label: '技能要求' },
  { key: 'equipment', label: '所需设备' },
  { key: 'syllabus', label: '教学大纲' },
  { key: 'bom', label: 'BOM' },
  { key: 'resources', label: '学习资源' },
  { key: 'discuss', label: '讨论' }
]

const projectId = ref(null)
const project = ref(null)
const enrolled = ref(false)
const enrollment = ref(null)
const favorited = ref(false)
const coverFailed = ref(false)
const activeTab = ref('overview')
const mySkills = ref([])
const discussions = ref([])
const discussContent = ref('')
const replyTarget = ref(null)
const enrolling = ref(false)
const posting = ref(false)

const features = computed(() => asList(project.value?.features))
const learningGoals = computed(() => asList(project.value?.learningGoals))
const prerequisites = computed(() => asList(project.value?.prerequisites))
const skillRequirements = computed(() => asList(project.value?.skillRequirements))
const equipmentNames = computed(() => asList(project.value?.equipmentNames))
const syllabus = computed(() => asList(project.value?.syllabus))
const bom = computed(() => asList(project.value?.bom))
const resources = computed(() => asList(project.value?.resources))

const richDesc = computed(() => prepareRich(project.value?.description))

const bomTotal = computed(() =>
  bom.value.reduce((sum, b) => sum + (Number(b.price) || 0) * (Number(b.qty) || 1), 0).toFixed(1)
)

const diffBadge = (d) => (d === '入门' ? 'badge-green' : d === '进阶' ? 'badge-purple' : 'badge-red')

const resIcon = (type) =>
  ({ 文档: '📄', 视频: '🎬', 代码: '💻', 手册: '📖', 原理图: '📐', LAYOUT: '🧩', '3D图': '🧊' }[type] || '📎')

const mySkill = (name) => {
  const s = mySkills.value.find((x) => x.skillName === name)
  return s ? s.score : 0
}

const load = async () => {
  const data = await fetchProjectDetail(projectId.value)
  project.value = data.project
  enrolled.value = !!data.enrolled
  enrollment.value = data.enrollment
  favorited.value = !!data.favorited
}

const loadSkills = () => {
  // 我的技能是个人数据,游客浏览详情时不请求
  if (!getToken()) return
  fetchSkills()
    .then((d) => {
      mySkills.value = d.skills || []
    })
    .catch(() => {})
}

const loadDiscussions = () => {
  fetchDiscussions(projectId.value)
    .then((d) => {
      discussions.value = d || []
    })
    .catch(() => {})
}

onLoad((options) => {
  projectId.value = options.id
  load()
  loadSkills()
  loadDiscussions()
})

onShareAppMessage(() => ({
  title: project.value ? `${project.value.title} - AI未来实践中心` : 'AI未来实践中心',
  path: `/pages/project-detail/index?id=${projectId.value}`
}))

const onEnroll = () => {
  if (!ensureLogin()) return
  uni.showModal({
    title: '确认报名',
    content: `报名参与「${project.value.title}」,报名后可跟踪学习进度并获得经验值`,
    success: async (res) => {
      if (!res.confirm) return
      enrolling.value = true
      try {
        await enrollProject(projectId.value)
        uni.showToast({ title: '报名成功 +10 经验', icon: 'success' })
        await load()
      } catch (e) {
        // 已提示
      } finally {
        enrolling.value = false
      }
    }
  })
}

const onToggleFavorite = async () => {
  if (!ensureLogin()) return
  try {
    const d = await toggleFavorite(projectId.value)
    favorited.value = !!d.favorited
    if (project.value) {
      project.value.favoriteCount += favorited.value ? 1 : -1
    }
    uni.showToast({ title: favorited.value ? '已加入收藏' : '已取消收藏', icon: 'none' })
  } catch (e) {
    // 已提示
  }
}

const goEquipment = () => {
  uni.switchTab({ url: '/pages/equipment/index' })
}

const goSubmission = () => {
  if (!ensureLogin()) return
  uni.navigateTo({
    url: `/pages/submission/index?projectId=${projectId.value}&title=${encodeURIComponent(project.value.title)}`
  })
}

const openResource = (r) => {
  if (!r.url) {
    uni.showToast({ title: '附件待教师上传,上传后即可下载', icon: 'none' })
    return
  }
  const url = fullUrl(r.url)
  const ext = (url.split('.').pop() || '').toLowerCase()
  const docTypes = ['pdf', 'doc', 'docx', 'xls', 'xlsx', 'ppt', 'pptx']
  if (docTypes.includes(ext)) {
    uni.showLoading({ title: '下载中...' })
    uni.downloadFile({
      url,
      success: (res) => {
        uni.hideLoading()
        uni.openDocument({
          filePath: res.tempFilePath,
          showMenu: true,
          fail: () => copyLink(url)
        })
      },
      fail: () => {
        uni.hideLoading()
        copyLink(url)
      }
    })
  } else {
    copyLink(url)
  }
}

const copyLink = (url) => {
  uni.setClipboardData({
    data: url,
    success: () => uni.showToast({ title: '链接已复制,可在浏览器打开', icon: 'none' })
  })
}

const startReply = (item) => {
  replyTarget.value = item
}

const cancelReply = () => {
  replyTarget.value = null
}

const sendDiscussion = async () => {
  if (!ensureLogin()) return
  const content = discussContent.value.trim()
  if (!content) {
    uni.showToast({ title: '请输入内容', icon: 'none' })
    return
  }
  posting.value = true
  try {
    await postDiscussion(projectId.value, {
      content,
      parentId: replyTarget.value ? replyTarget.value.id : undefined
    })
    discussContent.value = ''
    replyTarget.value = null
    loadDiscussions()
  } catch (e) {
    // 已提示
  } finally {
    posting.value = false
  }
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
  height: 380rpx;
}

.hero-fallback {
  background: linear-gradient(135deg, #3b82f6, #8b5cf6);
  display: flex;
  align-items: center;
  justify-content: center;
}

.hero-icon {
  font-size: 120rpx;
}

.hero-badges {
  position: absolute;
  left: 24rpx;
  bottom: 24rpx;
  display: flex;
  gap: 12rpx;
}

.head-card {
  margin: -40rpx 24rpx 0;
  position: relative;
}

.title {
  font-size: 38rpx;
  font-weight: 700;
  display: block;
}

.summary {
  display: block;
  color: $text-sub;
  font-size: 26rpx;
  line-height: 1.5;
  margin-top: 12rpx;
}
.desc-block {
  margin-top: 0;
}
.full-desc {
  display: block;
  margin-top: 0;
  font-size: 28rpx;
  color: $text-main;
  line-height: 1.8;
  white-space: pre-wrap;
  word-break: break-word;
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
}

.qs-num {
  font-size: 28rpx;
  font-weight: 600;
}

.qs-label {
  font-size: 22rpx;
  color: $text-sub;
  margin-top: 4rpx;
}

.counter-row {
  display: flex;
  gap: 28rpx;
  margin-top: 24rpx;
  flex-wrap: wrap;
}

.counter {
  font-size: 24rpx;
  color: $text-sub;
}

.mentor-row {
  display: flex;
  align-items: center;
  gap: 20rpx;
  margin-top: 28rpx;
  padding-top: 24rpx;
  border-top: 2rpx solid $border-color;
}

.mentor-avatar {
  width: 72rpx;
  height: 72rpx;
  border-radius: 50%;
  background: linear-gradient(135deg, #3b82f6, #8b5cf6);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 30rpx;
  flex-shrink: 0;
}

.mentor-info {
  display: flex;
  flex-direction: column;
}

.mentor-name {
  font-size: 28rpx;
  font-weight: 600;
}

.mentor-sub {
  font-size: 22rpx;
  color: $text-light;
  margin-top: 4rpx;
}

.enroll-card {
  margin: 24rpx 24rpx 0;
}

.enroll-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.enroll-track {
  margin-top: 24rpx;
}

.enroll-meta {
  display: flex;
  justify-content: space-between;
  margin-top: 16rpx;
}

.submit-work-btn {
  margin-top: 24rpx;
  font-size: 27rpx;
  padding: 16rpx 0;
}

.tab-card {
  margin: 24rpx 24rpx 0;
  padding: 0;
}

.tab-scroll {
  white-space: nowrap;
  border-bottom: 2rpx solid $border-color;
}

.tab-row {
  display: inline-flex;
  padding: 0 16rpx;
}

.tab-item {
  padding: 26rpx 28rpx;
  font-size: 28rpx;
  color: $text-sub;
  position: relative;

  &.active {
    color: $brand-blue;
    font-weight: 600;

    &::after {
      content: '';
      position: absolute;
      left: 28rpx;
      right: 28rpx;
      bottom: 0;
      height: 6rpx;
      border-radius: 6rpx;
      background: linear-gradient(90deg, #2563eb, #9333ea);
    }
  }
}

.tab-body {
  padding: 32rpx;
}

.desc {
  font-size: 28rpx;
  color: $text-main;
  line-height: 1.8;
}

.rich-html {
  display: block;
  font-size: 28rpx;
  line-height: 1.8;
}

.rich-video {
  width: 100%;
  border-radius: 16rpx;
  margin: 16rpx 0;
}

.sub-block {
  margin-top: 36rpx;
}

.sub-title {
  display: block;
  font-size: 30rpx;
  font-weight: 600;
  margin-bottom: 20rpx;
}

.tags {
  display: flex;
  gap: 12rpx;
  flex-wrap: wrap;
}

.chip-blue {
  color: $brand-blue;
  background: $blue-bg;
}

.li-row {
  display: flex;
  gap: 16rpx;
  margin-bottom: 16rpx;
  align-items: flex-start;
}

.li-dot {
  width: 36rpx;
  height: 36rpx;
  border-radius: 50%;
  font-size: 22rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  margin-top: 4rpx;

  &.ok {
    color: $green;
    background: $green-bg;
  }

  &.warn {
    color: $yellow;
    background: $yellow-bg;
  }
}

.li-text {
  font-size: 27rpx;
  flex: 1;
}

.skill-item {
  margin-bottom: 32rpx;
}

.skill-head {
  display: flex;
  justify-content: space-between;
  margin-bottom: 14rpx;
}

.skill-name {
  font-size: 28rpx;
  font-weight: 500;
}

.skill-score {
  font-size: 24rpx;
}

.ok-text {
  color: $green;
}

.warn-text {
  color: $yellow;
}

.skill-track {
  position: relative;
  overflow: visible;

  .progress-fill {
    &.fill-warn {
      background: linear-gradient(90deg, #f59e0b, #f97316);
    }
  }
}

.require-mark {
  position: absolute;
  top: -6rpx;
  width: 4rpx;
  height: 26rpx;
  background: $text-main;
  border-radius: 2rpx;
}

.skill-tip {
  font-size: 22rpx;
}

.equip-row {
  display: flex;
  align-items: center;
  gap: 20rpx;
  padding: 22rpx 24rpx;
  background: $gray-bg;
  border-radius: 16rpx;
  margin-bottom: 16rpx;
}

.equip-icon {
  font-size: 32rpx;
}

.equip-name {
  font-size: 28rpx;
}

.go-equip {
  margin-top: 24rpx;
}

.syllabus-item {
  display: flex;
  gap: 24rpx;
}

.sy-left {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.sy-dot {
  width: 52rpx;
  height: 52rpx;
  border-radius: 50%;
  background: linear-gradient(135deg, #2563eb, #9333ea);
  color: #fff;
  font-size: 26rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.sy-line {
  width: 4rpx;
  flex: 1;
  background: $border-color;
  margin: 8rpx 0;
}

.sy-body {
  flex: 1;
  padding-bottom: 36rpx;
}

.sy-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.sy-phase {
  font-size: 24rpx;
  color: $brand-blue;
  font-weight: 600;
}

.sy-title {
  display: block;
  font-size: 30rpx;
  font-weight: 600;
  margin-top: 8rpx;
}

.sy-content {
  display: block;
  font-size: 26rpx;
  color: $text-sub;
  margin-top: 8rpx;
}

.bom-row {
  display: flex;
  align-items: center;
  padding: 18rpx 0;
  border-bottom: 2rpx solid $border-color;

  &.bom-head {
    color: $text-sub;
    font-size: 24rpx;
  }
}

.bom-ref {
  width: 100rpx;
  font-size: 24rpx;
  color: $text-sub;
}

.bom-name {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.bom-name-text {
  font-size: 27rpx;
}

.bom-fp {
  font-size: 22rpx;
  color: $text-light;
}

.bom-qty {
  width: 90rpx;
  text-align: right;
  font-size: 26rpx;
}

.bom-price {
  width: 120rpx;
  text-align: right;
  font-size: 26rpx;
}

.bom-total {
  display: flex;
  justify-content: space-between;
  padding: 24rpx 0 8rpx;
  font-size: 28rpx;
  font-weight: 600;
}

.bom-total-num {
  color: $brand-blue;
}

.res-row {
  display: flex;
  align-items: center;
  gap: 20rpx;
  padding: 24rpx;
  background: $gray-bg;
  border-radius: 16rpx;
  margin-bottom: 16rpx;
}

.res-icon {
  font-size: 40rpx;
}

.res-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.res-name {
  font-size: 28rpx;
}

.res-type {
  font-size: 22rpx;
  color: $text-light;
  margin-top: 4rpx;
}

.res-action {
  color: $brand-blue;
  font-size: 26rpx;
}

.disc-item {
  display: flex;
  gap: 20rpx;
  margin-bottom: 32rpx;
}

.disc-avatar {
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

.disc-body {
  flex: 1;
}

.disc-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.disc-user {
  font-size: 26rpx;
  font-weight: 600;
}

.disc-time {
  font-size: 22rpx;
  color: $text-light;
}

.disc-content {
  display: block;
  font-size: 27rpx;
  margin-top: 8rpx;
}

.disc-reply-btn {
  display: inline-block;
  font-size: 24rpx;
  color: $brand-blue;
  margin-top: 12rpx;
}

.disc-sub {
  margin-top: 20rpx;
  padding: 20rpx;
  background: $gray-bg;
  border-radius: 16rpx;
}

.disc-input-area {
  margin-top: 8rpx;
}

.reply-tip {
  display: flex;
  justify-content: space-between;
  margin-bottom: 12rpx;
}

.reply-cancel {
  font-size: 24rpx;
  color: $red;
}

.disc-input-row {
  display: flex;
  gap: 16rpx;
  align-items: center;
}

.disc-input {
  flex: 1;
  background: $gray-bg;
  border-radius: $radius-pill;
  padding: 18rpx 28rpx;
  font-size: 27rpx;
  height: 76rpx;
  box-sizing: border-box;
}

.disc-send {
  width: 140rpx;
  padding: 14rpx 0;
  font-size: 27rpx;
  flex-shrink: 0;
}

.ph {
  color: $text-light;
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
  width: 110rpx;
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

.enroll-btn {
  flex: 1;
}

.enrolled-btn {
  color: $green;
  border-color: $green-bg;
  background: $green-bg;
}
</style>
