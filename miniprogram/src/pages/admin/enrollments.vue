<template>
  <view class="page">
    <!-- 筛选 -->
    <view class="filter-row">
      <picker mode="selector" :range="projectRange" @change="changeProject($event.detail.value)">
        <view class="pill" :class="{ active: !!projectId }">
          {{ projectLabel }}
        </view>
      </picker>
      <view
        v-for="t in statusTabs"
        :key="t.key"
        class="pill"
        :class="{ active: status === t.key }"
        @click="changeStatus(t.key)"
      >
        {{ t.label }}
      </view>
    </view>

    <view v-if="loading" class="empty-box">
      <uni-icons type="spinner-cycle" size="40" color="#d1d5db" />
      <text>加载中...</text>
    </view>
    <view v-else-if="items.length === 0" class="empty-box">
      <uni-icons type="list" size="40" color="#d1d5db" />
      <text>暂无报名记录</text>
    </view>
    <view v-else class="list">
      <view v-for="e in items" :key="e.id" class="card enr-card">
        <view class="enr-head">
          <view class="enr-info">
            <text class="enr-user">{{ userName(e.userId) }}</text>
            <text class="enr-proj muted ellipsis">《{{ e.projectTitle }}》</text>
          </view>
          <text class="badge" :class="e.status === 'COMPLETED' ? 'badge-green' : 'badge-blue'">
            {{ e.status === 'COMPLETED' ? '已完成' : '进行中' }}
          </text>
        </view>
        <view class="progress-row">
          <view class="progress-track enr-track">
            <view class="progress-fill" :style="{ width: (e.progress || 0) + '%' }" />
          </view>
          <text class="progress-num">{{ e.progress || 0 }}%</text>
        </view>
        <view class="enr-meta">
          <text class="muted ellipsis">当前:{{ e.currentTask || '尚未开始' }}</text>
          <text class="muted">
            报名 {{ relativeTime(e.enrolledAt) }}{{ e.deadline ? ' · 截止 ' + formatDate(e.deadline) : '' }}
          </text>
        </view>
        <view class="enr-actions">
          <view class="act-btn" @click="openEdit(e)">调整进度</view>
          <view class="act-btn danger" @click="onDelete(e)">删除报名</view>
        </view>
      </view>
      <view class="list-end muted">共 {{ items.length }} 条</view>
    </view>

    <view class="fab" @click="openCreate">+ 新增报名</view>
    <view class="bottom-gap" />

    <!-- 调整进度弹层 -->
    <view v-if="editing" class="mask" @click="editing = null">
      <view class="modal" @click.stop>
        <text class="modal-title">调整进度</text>
        <text class="modal-sub muted">{{ userName(editing.userId) }} · 《{{ editing.projectTitle }}》</text>

        <view class="score-row">
          <text class="score-num">{{ editForm.progress }}%</text>
          <text class="score-tip">{{ editForm.progress >= 100 ? '将判定为已完成' : '进行中' }}</text>
        </view>
        <slider
          :value="editForm.progress"
          :min="0"
          :max="100"
          :step="5"
          activeColor="#2563eb"
          backgroundColor="#e5e7eb"
          block-size="22"
          @change="editForm.progress = $event.detail.value"
          @changing="editForm.progress = $event.detail.value"
        />

        <text class="field-label">当前任务</text>
        <input v-model="editForm.currentTask" class="field-input" placeholder="如:PCB布线中" placeholder-class="ph" />

        <text class="field-label">截止日期</text>
        <picker mode="date" :value="editForm.deadline" @change="editForm.deadline = $event.detail.value">
          <view class="field-input picker" :class="{ placeholder: !editForm.deadline }">
            {{ editForm.deadline || '不修改' }}
          </view>
        </picker>

        <view class="btn-row">
          <button class="btn-plain half" @click="editing = null">取消</button>
          <button class="btn-gradient half" :disabled="saving" @click="confirmEdit">保存</button>
        </view>
      </view>
    </view>

    <!-- 新增报名弹层 -->
    <view v-if="creating" class="mask" @click="creating = false">
      <view class="modal" @click.stop>
        <text class="modal-title">新增报名(代报名)</text>

        <text class="field-label">学生</text>
        <picker mode="selector" :range="studentRange" @change="createForm.userIdx = Number($event.detail.value)">
          <view class="field-input picker" :class="{ placeholder: createForm.userIdx < 0 }">
            {{ createForm.userIdx >= 0 ? studentRange[createForm.userIdx] : '选择学生' }}
          </view>
        </picker>

        <text class="field-label">项目</text>
        <picker mode="selector" :range="projectNames" @change="createForm.projectIdx = Number($event.detail.value)">
          <view class="field-input picker" :class="{ placeholder: createForm.projectIdx < 0 }">
            {{ createForm.projectIdx >= 0 ? projectNames[createForm.projectIdx] : '选择项目' }}
          </view>
        </picker>

        <view class="btn-row">
          <button class="btn-plain half" @click="creating = false">取消</button>
          <button class="btn-gradient half" :disabled="saving" @click="confirmCreate">确认报名</button>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { onShow, onPullDownRefresh } from '@dcloudio/uni-app'
import {
  adminListEnrollments,
  adminCreateEnrollment,
  adminUpdateEnrollment,
  adminDeleteEnrollment,
  adminListProjects,
  adminListUsers
} from '@/api'
import { formatDate, relativeTime } from '@/utils/format'

const statusTabs = [
  { key: '', label: '全部' },
  { key: 'IN_PROGRESS', label: '进行中' },
  { key: 'COMPLETED', label: '已完成' }
]

const items = ref([])
const projects = ref([])
const users = ref([])
const loading = ref(true)
const projectId = ref(null)
const status = ref('')
const editing = ref(null)
const creating = ref(false)
const saving = ref(false)

const editForm = reactive({ progress: 0, currentTask: '', deadline: '' })
const createForm = reactive({ userIdx: -1, projectIdx: -1 })

const projectRange = computed(() => ['全部项目', ...projects.value.map((p) => p.title)])
const projectNames = computed(() => projects.value.map((p) => p.title))
const projectLabel = computed(() => {
  if (!projectId.value) return '全部项目'
  const p = projects.value.find((x) => x.id === projectId.value)
  return p ? p.title : '全部项目'
})

const students = computed(() => users.value.filter((u) => u.role === 'STUDENT'))
const studentRange = computed(() => students.value.map((u) => `${u.name}(${u.studentNo || u.email})`))

const userName = (id) => {
  const u = users.value.find((x) => x.id === id)
  return u ? u.name : `用户#${id}`
}

const load = async () => {
  loading.value = true
  try {
    items.value = await adminListEnrollments({
      projectId: projectId.value || undefined,
      status: status.value || undefined
    })
  } catch (e) {
    // 已提示
  } finally {
    loading.value = false
  }
}

const loadMeta = async () => {
  try {
    const [ps, us] = await Promise.all([adminListProjects(), adminListUsers({})])
    projects.value = ps
    users.value = us
  } catch (e) {
    // 静默
  }
}

const changeProject = (idx) => {
  const i = Number(idx)
  projectId.value = i > 0 ? projects.value[i - 1].id : null
  load()
}

const changeStatus = (k) => {
  status.value = k
  load()
}

const openEdit = (e) => {
  editing.value = e
  editForm.progress = e.progress || 0
  editForm.currentTask = e.currentTask || ''
  editForm.deadline = ''
}

const confirmEdit = async () => {
  saving.value = true
  try {
    await adminUpdateEnrollment(editing.value.id, {
      progress: editForm.progress,
      currentTask: editForm.currentTask.trim() || undefined,
      deadline: editForm.deadline || undefined
    })
    uni.showToast({ title: '已更新', icon: 'success' })
    editing.value = null
    load()
  } catch (e) {
    // 已提示
  } finally {
    saving.value = false
  }
}

const openCreate = () => {
  createForm.userIdx = -1
  createForm.projectIdx = -1
  creating.value = true
}

const confirmCreate = async () => {
  if (createForm.userIdx < 0 || createForm.projectIdx < 0) {
    uni.showToast({ title: '请选择学生和项目', icon: 'none' })
    return
  }
  saving.value = true
  try {
    await adminCreateEnrollment({
      userId: students.value[createForm.userIdx].id,
      projectId: projects.value[createForm.projectIdx].id
    })
    uni.showToast({ title: '报名成功', icon: 'success' })
    creating.value = false
    load()
  } catch (e) {
    // 已提示
  } finally {
    saving.value = false
  }
}

const onDelete = (e) => {
  uni.showModal({
    title: '删除报名',
    content: `确定删除 ${userName(e.userId)} 在《${e.projectTitle}》的报名记录?`,
    confirmColor: '#dc2626',
    success: async (res) => {
      if (!res.confirm) return
      try {
        await adminDeleteEnrollment(e.id)
        uni.showToast({ title: '已删除', icon: 'success' })
        load()
      } catch (err) {
        // 已提示
      }
    }
  })
}

onShow(() => {
  load()
  loadMeta()
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
  flex-wrap: wrap;
}

.list {
  display: flex;
  flex-direction: column;
  gap: 20rpx;
  margin-top: 24rpx;
}

.enr-card {
  padding: 28rpx;
}

.enr-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16rpx;
}

.enr-info {
  flex: 1;
  overflow: hidden;
}

.enr-user {
  font-size: 30rpx;
  font-weight: 600;
}

.enr-proj {
  display: block;
  font-size: 23rpx;
  margin-top: 4rpx;
}

.progress-row {
  display: flex;
  align-items: center;
  gap: 20rpx;
  margin-top: 22rpx;
}

.enr-track {
  flex: 1;
}

.progress-num {
  font-size: 26rpx;
  font-weight: 700;
  color: $brand-blue;
  width: 80rpx;
  text-align: right;
}

.enr-meta {
  display: flex;
  flex-direction: column;
  gap: 6rpx;
  margin-top: 14rpx;
  font-size: 23rpx;
}

.enr-actions {
  display: flex;
  gap: 16rpx;
  margin-top: 20rpx;
  padding-top: 20rpx;
  border-top: 2rpx solid $border-color;
}

.act-btn {
  flex: 1;
  text-align: center;
  font-size: 25rpx;
  background: $gray-bg;
  border-radius: 14rpx;
  padding: 14rpx 0;

  &.danger {
    color: $red;
    background: $red-bg;
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

.list-end {
  text-align: center;
  padding: 24rpx 0;
  font-size: 24rpx;
}

.mask {
  position: fixed;
  inset: 0;
  background: rgba(17, 24, 39, 0.5);
  z-index: 100;
  display: flex;
  align-items: center;
  justify-content: center;
}

.modal {
  width: 640rpx;
  background: #fff;
  border-radius: 28rpx;
  padding: 40rpx 36rpx;
}

.modal-title {
  display: block;
  font-size: 32rpx;
  font-weight: 700;
  text-align: center;
}

.modal-sub {
  display: block;
  text-align: center;
  font-size: 24rpx;
  margin: 8rpx 0 20rpx;
}

.score-row {
  display: flex;
  align-items: baseline;
  gap: 20rpx;
}

.score-num {
  font-size: 56rpx;
  font-weight: 700;
  color: $brand-blue;
}

.score-tip {
  font-size: 24rpx;
  color: $text-sub;
}

.field-label {
  display: block;
  font-size: 26rpx;
  font-weight: 500;
  margin: 24rpx 0 12rpx;
}

.field-input {
  background: $gray-bg;
  border-radius: 16rpx;
  padding: 20rpx 24rpx;
  font-size: 28rpx;
  height: 88rpx;
  box-sizing: border-box;
  width: 100%;

  &.picker {
    display: flex;
    align-items: center;
  }

  &.placeholder {
    color: $text-light;
  }
}

.ph {
  color: $text-light;
}

.btn-row {
  display: flex;
  gap: 24rpx;
  margin-top: 36rpx;

  .half {
    flex: 1;
  }
}
</style>
