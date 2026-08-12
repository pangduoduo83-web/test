<template>
  <view class="page">
    <view class="card">
      <view class="field">
        <text class="field-label">原密码</text>
        <input v-model="form.oldPassword" class="field-input" password placeholder="请输入原密码" placeholder-class="ph" />
      </view>
      <view class="field">
        <text class="field-label">新密码</text>
        <input v-model="form.newPassword" class="field-input" password placeholder="6-32位新密码" placeholder-class="ph" />
      </view>
      <view class="field">
        <text class="field-label">确认新密码</text>
        <input v-model="form.confirm" class="field-input" password placeholder="再次输入新密码" placeholder-class="ph" />
      </view>
      <button class="btn-gradient" :disabled="saving" @click="save">
        {{ saving ? '提交中...' : '确认修改' }}
      </button>
      <text class="tip muted">修改成功后当前登录状态保持有效;忘记原密码请联系管理员重置。</text>
    </view>
  </view>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { changePassword } from '@/api'

const form = reactive({ oldPassword: '', newPassword: '', confirm: '' })
const saving = ref(false)

const save = async () => {
  if (!form.oldPassword) {
    uni.showToast({ title: '请输入原密码', icon: 'none' })
    return
  }
  if (!form.newPassword || form.newPassword.length < 6 || form.newPassword.length > 32) {
    uni.showToast({ title: '新密码长度需为6-32位', icon: 'none' })
    return
  }
  if (form.newPassword !== form.confirm) {
    uni.showToast({ title: '两次输入的新密码不一致', icon: 'none' })
    return
  }
  saving.value = true
  try {
    await changePassword({ oldPassword: form.oldPassword, newPassword: form.newPassword })
    uni.showToast({ title: '密码修改成功', icon: 'success' })
    setTimeout(() => uni.navigateBack(), 600)
  } catch (e) {
    // 已提示
  } finally {
    saving.value = false
  }
}
</script>

<style lang="scss" scoped>
.page {
  padding: 24rpx;
}

.field {
  margin-bottom: 30rpx;
}

.field-label {
  display: block;
  font-size: 26rpx;
  font-weight: 500;
  margin-bottom: 12rpx;
}

.field-input {
  background: $gray-bg;
  border-radius: 16rpx;
  padding: 20rpx 24rpx;
  font-size: 28rpx;
  height: 88rpx;
  box-sizing: border-box;
}

.ph {
  color: $text-light;
}

.tip {
  display: block;
  margin-top: 24rpx;
  font-size: 23rpx;
  text-align: center;
}
</style>
