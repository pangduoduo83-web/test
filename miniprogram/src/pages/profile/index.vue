<template>
  <view class="page">
    <view class="card">
      <!-- 头像 -->
      <view class="avatar-block" @click="chooseAvatar">
        <image v-if="avatarUrl" :src="fullUrl(avatarUrl)" class="avatar" mode="aspectFill" />
        <view v-else class="avatar avatar-fallback">{{ (form.name || '?')[0] }}</view>
        <text class="avatar-tip">点击更换头像</text>
      </view>

      <view class="field">
        <text class="field-label">姓名 <text class="req">*</text></text>
        <input v-model="form.name" class="field-input" placeholder="请输入姓名" placeholder-class="ph" />
      </view>

      <view class="field-row">
        <view class="field grow">
          <text class="field-label">专业</text>
          <picker mode="selector" :range="majors" @change="form.major = majors[$event.detail.value]">
            <view class="field-input picker" :class="{ placeholder: !form.major }">
              {{ form.major || '请选择专业' }}
            </view>
          </picker>
        </view>
        <view class="field grow">
          <text class="field-label">年级</text>
          <picker mode="selector" :range="grades" @change="form.grade = grades[$event.detail.value]">
            <view class="field-input picker" :class="{ placeholder: !form.grade }">
              {{ form.grade || '请选择年级' }}
            </view>
          </picker>
        </view>
      </view>

      <view class="field">
        <text class="field-label">邮箱(不可修改)</text>
        <view class="field-input readonly">{{ user?.email }}</view>
      </view>
      <view class="field">
        <text class="field-label">学号(不可修改)</text>
        <view class="field-input readonly">{{ user?.studentNo || '-' }}</view>
      </view>

      <button class="btn-gradient" :disabled="saving" @click="save">
        {{ saving ? '保存中...' : '保存修改' }}
      </button>
    </view>
  </view>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { updateProfile } from '@/api'
import { uploadImage } from '@/utils/request'
import { fullUrl } from '@/config'
import { useAuthStore } from '@/stores/auth'

const authStore = useAuthStore()
const user = computed(() => authStore.user)

const majors = ['电子信息工程', '通信工程', '自动化', '计算机科学', '物联网工程']
const grades = ['大一', '大二', '大三', '大四', '研究生']

const form = reactive({ name: '', major: '', grade: '' })
const avatarUrl = ref('')
const saving = ref(false)

onLoad(() => {
  const u = authStore.user || {}
  form.name = u.name || ''
  form.major = u.major || ''
  form.grade = u.grade || ''
  avatarUrl.value = u.avatarUrl || ''
})

const chooseAvatar = () => {
  uni.chooseImage({
    count: 1,
    sizeType: ['compressed'],
    success: async (res) => {
      uni.showLoading({ title: '上传中...' })
      try {
        const d = await uploadImage(res.tempFilePaths[0])
        avatarUrl.value = d.url
        uni.showToast({ title: '头像已上传,记得保存', icon: 'none' })
      } catch (e) {
        // 已提示
      } finally {
        uni.hideLoading()
      }
    }
  })
}

const save = async () => {
  if (!form.name.trim()) {
    uni.showToast({ title: '请输入姓名', icon: 'none' })
    return
  }
  saving.value = true
  try {
    const updated = await updateProfile({
      name: form.name.trim(),
      major: form.major || undefined,
      grade: form.grade || undefined,
      avatarUrl: avatarUrl.value || undefined
    })
    authStore.updateUser(updated)
    uni.showToast({ title: '保存成功', icon: 'success' })
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

.avatar-block {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 20rpx 0 40rpx;
}

.avatar {
  width: 160rpx;
  height: 160rpx;
  border-radius: 50%;
  border: 4rpx solid $border-color;
}

.avatar-fallback {
  background: linear-gradient(135deg, #3b82f6, #8b5cf6);
  color: #fff;
  font-size: 60rpx;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  box-sizing: border-box;
}

.avatar-tip {
  font-size: 24rpx;
  color: $brand-blue;
  margin-top: 16rpx;
}

.field {
  margin-bottom: 30rpx;
}

.field-row {
  display: flex;
  gap: 24rpx;

  .grow {
    flex: 1;
  }
}

.field-label {
  display: block;
  font-size: 26rpx;
  font-weight: 500;
  margin-bottom: 12rpx;
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

  &.picker {
    display: flex;
    align-items: center;
  }

  &.placeholder {
    color: $text-light;
  }

  &.readonly {
    color: $text-light;
    display: flex;
    align-items: center;
  }
}

.ph {
  color: $text-light;
}
</style>
