<template>
  <view class="page">
    <view class="search-box">
      <uni-icons type="search" size="17" color="#9ca3af" />
      <input v-model="keyword" class="search-input" placeholder="搜索姓名、邮箱、手机号、学号..." placeholder-class="ph" />
    </view>

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
      <view v-for="u in filtered" :key="u.id" class="card user-card" :class="{ disabled: u.enabled === false }">
        <view class="uc-head">
          <view class="uc-avatar" :class="`role-${u.role}`">{{ (u.name || '?')[0] }}</view>
          <view class="uc-info">
            <view class="uc-name-row">
              <text class="uc-name">{{ u.name }}</text>
              <text class="badge" :class="roleBadge(u.role)">{{ roleText(u.role) }}</text>
              <text v-if="u.enabled === false" class="badge badge-red">已禁用</text>
            </view>
            <text class="uc-sub muted">{{ u.email }}</text>
            <text class="uc-sub muted">
              {{ u.studentNo ? '学号 ' + u.studentNo + ' · ' : '' }}{{ u.major || '-' }} · 注册于 {{ relativeTime(u.createdAt) }}
            </text>
          </view>
        </view>
        <view v-if="u.id !== myId" class="uc-actions">
          <view class="act-btn" @click="changeRole(u)">角色</view>
          <view class="act-btn" @click="resetPassword(u)">重置密码</view>
          <view class="act-btn" :class="u.enabled === false ? 'ok' : 'danger'" @click="toggleEnabled(u)">
            {{ u.enabled === false ? '启用' : '禁用' }}
          </view>
          <view class="act-btn danger" @click="onDelete(u)">删除</view>
        </view>
        <view v-else class="self-tip muted">当前登录账号</view>
      </view>
      <view v-if="filtered.length === 0" class="empty-box">
        <uni-icons type="staff" size="40" color="#d1d5db" />
        <text>没有匹配的用户</text>
      </view>
      <view class="list-end muted">共 {{ filtered.length }} 人</view>
    </view>

    <view class="fab" @click="openCreate">+ 新增用户</view>
    <view class="bottom-gap" />

    <!-- 新增用户弹层 -->
    <view v-if="creating" class="mask" @click="creating = false">
      <view class="modal" @click.stop>
        <text class="modal-title">新增用户</text>

        <text class="field-label">角色</text>
        <view class="pill-row">
          <view
            v-for="r in roles"
            :key="r"
            class="pill"
            :class="{ active: createForm.role === r }"
            @click="createForm.role = r"
          >
            {{ roleNames[r] }}
          </view>
        </view>

        <text class="field-label">姓名 <text class="req">*</text></text>
        <input v-model="createForm.name" class="field-input" placeholder="真实姓名" placeholder-class="ph" />

        <text class="field-label">邮箱 <text class="req">*</text></text>
        <input v-model="createForm.email" class="field-input" placeholder="登录邮箱" placeholder-class="ph" />

        <text class="field-label">初始密码 <text class="req">*</text></text>
        <input v-model="createForm.password" class="field-input" placeholder="6-32位" placeholder-class="ph" />

        <view v-if="createForm.role === 'STUDENT'">
          <text class="field-label">学号(选填)</text>
          <input v-model="createForm.studentNo" class="field-input" placeholder="学号" placeholder-class="ph" />
        </view>

        <view class="btn-row">
          <button class="btn-plain half" @click="creating = false">取消</button>
          <button class="btn-gradient half" :disabled="saving" @click="confirmCreate">创建</button>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { onShow, onPullDownRefresh } from '@dcloudio/uni-app'
import {
  adminListUsers,
  adminUpdateUser,
  adminCreateUser,
  adminResetUserPassword,
  adminDeleteUser
} from '@/api'
import { relativeTime } from '@/utils/format'
import { useAuthStore } from '@/stores/auth'

const tabs = [
  { key: 'ALL', label: '全部' },
  { key: 'STUDENT', label: '学生' },
  { key: 'TEACHER', label: '教师' },
  { key: 'ADMIN', label: '管理员' }
]

const roles = ['STUDENT', 'TEACHER', 'ADMIN']
const roleNames = { STUDENT: '学生', TEACHER: '教师', ADMIN: '管理员' }

const authStore = useAuthStore()
const myId = computed(() => authStore.user?.id)

const keyword = ref('')
const activeTab = ref('ALL')
const items = ref([])
const loading = ref(true)
const creating = ref(false)
const saving = ref(false)
const createForm = reactive({ role: 'STUDENT', name: '', email: '', password: '', studentNo: '' })

const roleText = (r) => roleNames[r] || r
const roleBadge = (r) => (r === 'ADMIN' ? 'badge-purple' : r === 'TEACHER' ? 'badge-blue' : 'badge-gray')

const filtered = computed(() => {
  const k = keyword.value.trim().toLowerCase()
  return items.value.filter((u) => {
    if (activeTab.value !== 'ALL' && u.role !== activeTab.value) return false
    if (!k) return true
    return (
      (u.name || '').toLowerCase().includes(k) ||
      (u.email || '').toLowerCase().includes(k) ||
      (u.phone || '').includes(k) ||
      (u.studentNo || '').toLowerCase().includes(k)
    )
  })
})

const load = async () => {
  loading.value = true
  try {
    items.value = await adminListUsers()
  } catch (e) {
    // 已提示
  } finally {
    loading.value = false
  }
}

const changeRole = (u) => {
  const options = roles.filter((r) => r !== u.role)
  uni.showActionSheet({
    itemList: options.map((r) => `设为${roleNames[r]}`),
    success: async (res) => {
      const role = options[res.tapIndex]
      try {
        await adminUpdateUser(u.id, { role })
        uni.showToast({ title: `已设为${roleNames[role]}`, icon: 'success' })
        load()
      } catch (e) {
        // 已提示
      }
    }
  })
}

const resetPassword = (u) => {
  uni.showModal({
    title: `重置 ${u.name} 的密码`,
    editable: true,
    placeholderText: '输入新密码(6-32位)',
    success: async (res) => {
      if (!res.confirm) return
      const pwd = (res.content || '').trim()
      if (pwd.length < 6 || pwd.length > 32) {
        uni.showToast({ title: '密码长度需为6-32位', icon: 'none' })
        return
      }
      try {
        await adminResetUserPassword(u.id, pwd)
        uni.showToast({ title: '密码已重置', icon: 'success' })
      } catch (e) {
        // 已提示
      }
    }
  })
}

const onDelete = (u) => {
  uni.showModal({
    title: '删除用户',
    content: `确定删除 ${u.name}(${u.email})?其报名、借阅等数据将一并清理,不可恢复`,
    confirmColor: '#dc2626',
    success: async (res) => {
      if (!res.confirm) return
      try {
        await adminDeleteUser(u.id)
        uni.showToast({ title: '已删除', icon: 'success' })
        load()
      } catch (e) {
        // 已提示
      }
    }
  })
}

const openCreate = () => {
  Object.assign(createForm, { role: 'STUDENT', name: '', email: '', password: '', studentNo: '' })
  creating.value = true
}

const confirmCreate = async () => {
  if (!createForm.name.trim()) {
    uni.showToast({ title: '请填写姓名', icon: 'none' })
    return
  }
  if (!createForm.email.trim() || !createForm.email.includes('@')) {
    uni.showToast({ title: '请填写正确的邮箱', icon: 'none' })
    return
  }
  if (createForm.password.length < 6 || createForm.password.length > 32) {
    uni.showToast({ title: '密码长度需为6-32位', icon: 'none' })
    return
  }
  saving.value = true
  try {
    await adminCreateUser({
      name: createForm.name.trim(),
      email: createForm.email.trim(),
      password: createForm.password,
      role: createForm.role,
      studentNo: createForm.studentNo.trim() || undefined
    })
    uni.showToast({ title: '用户已创建', icon: 'success' })
    creating.value = false
    load()
  } catch (e) {
    // 已提示
  } finally {
    saving.value = false
  }
}

const toggleEnabled = (u) => {
  const next = u.enabled === false
  uni.showModal({
    title: next ? '启用账号' : '禁用账号',
    content: next
      ? `恢复 ${u.name} 的登录与使用权限?`
      : `禁用后 ${u.name} 将无法登录平台,确定?`,
    confirmColor: next ? '#16a34a' : '#dc2626',
    success: async (res) => {
      if (!res.confirm) return
      try {
        await adminUpdateUser(u.id, { enabled: next })
        uni.showToast({ title: next ? '已启用' : '已禁用', icon: 'success' })
        load()
      } catch (e) {
        // 已提示
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

.search-box {
  display: flex;
  align-items: center;
  background: #fff;
  border-radius: $radius-pill;
  padding: 16rpx 28rpx;
  gap: 16rpx;
  box-shadow: 0 2rpx 12rpx rgba(17, 24, 39, 0.04);
}

.search-input {
  flex: 1;
  font-size: 28rpx;
  height: 44rpx;
}

.ph {
  color: $text-light;
}

.tab-row {
  display: flex;
  gap: 16rpx;
  margin-top: 20rpx;
}

.list {
  display: flex;
  flex-direction: column;
  gap: 20rpx;
  margin-top: 24rpx;
}

.user-card {
  padding: 28rpx;

  &.disabled {
    opacity: 0.65;
  }
}

.uc-head {
  display: flex;
  gap: 22rpx;
}

.uc-avatar {
  width: 88rpx;
  height: 88rpx;
  border-radius: 50%;
  color: #fff;
  font-size: 34rpx;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  background: linear-gradient(135deg, #9ca3af, #6b7280);

  &.role-STUDENT {
    background: linear-gradient(135deg, #3b82f6, #8b5cf6);
  }

  &.role-TEACHER {
    background: linear-gradient(135deg, #34d399, #059669);
  }

  &.role-ADMIN {
    background: linear-gradient(135deg, #a855f7, #7c3aed);
  }
}

.uc-info {
  flex: 1;
  overflow: hidden;
}

.uc-name-row {
  display: flex;
  align-items: center;
  gap: 12rpx;
  flex-wrap: wrap;
}

.uc-name {
  font-size: 30rpx;
  font-weight: 600;
}

.uc-sub {
  display: block;
  font-size: 23rpx;
  margin-top: 6rpx;
}

.uc-actions {
  display: flex;
  gap: 16rpx;
  margin-top: 22rpx;
  padding-top: 22rpx;
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

  &.ok {
    color: $green;
    background: $green-bg;
  }
}

.self-tip {
  margin-top: 20rpx;
  padding-top: 20rpx;
  border-top: 2rpx solid $border-color;
  font-size: 23rpx;
  text-align: center;
}

.list-end {
  text-align: center;
  padding: 24rpx 0;
  font-size: 24rpx;
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
  max-height: 80vh;
  overflow-y: auto;
}

.modal-title {
  display: block;
  font-size: 32rpx;
  font-weight: 700;
  text-align: center;
  margin-bottom: 8rpx;
}

.pill-row {
  display: flex;
  gap: 14rpx;
  flex-wrap: wrap;
}

.field-label {
  display: block;
  font-size: 26rpx;
  font-weight: 500;
  margin: 24rpx 0 12rpx;
}

.req {
  color: $red;
}

.field-input {
  background: $gray-bg;
  border-radius: 16rpx;
  padding: 20rpx 24rpx;
  font-size: 28rpx;
  height: 88rpx;
  box-sizing: border-box;
  width: 100%;
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
