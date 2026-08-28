<template>
  <view class="page">
    <view class="card">
      <view class="tip-box muted">
        小程序端支持编辑项目基本信息;教学大纲、BOM、技能要求等复杂结构请在网页管理端维护。
      </view>

      <view class="field">
        <text class="field-label">项目名称 <text class="req">*</text></text>
        <input v-model="form.title" class="field-input" placeholder="如:STM32F103C8T6核心板" placeholder-class="ph" />
      </view>
      <view class="field">
        <text class="field-label">一句话简介</text>
        <textarea v-model="form.summary" class="field-textarea short" placeholder="项目卡片上的摘要..." placeholder-class="ph" :maxlength="200" />
      </view>
      <view class="field">
        <text class="field-label">详细描述</text>
        <textarea v-model="form.description" class="field-textarea" placeholder="项目详情页的完整介绍..." placeholder-class="ph" :maxlength="2000" />
      </view>
      <view class="field-row">
        <view class="field grow">
          <text class="field-label">难度</text>
          <picker mode="selector" :range="difficulties" @change="form.difficulty = difficulties[$event.detail.value]">
            <view class="field-input picker">{{ form.difficulty }}</view>
          </picker>
        </view>
        <view class="field grow">
          <text class="field-label">分类</text>
          <input v-model="form.category" class="field-input" placeholder="如:物联网应用" placeholder-class="ph" />
        </view>
      </view>
      <view class="field-row">
        <view class="field grow">
          <text class="field-label">周期</text>
          <input v-model="form.duration" class="field-input" placeholder="如:2周" placeholder-class="ph" />
        </view>
        <view class="field grow">
          <text class="field-label">团队规模</text>
          <input v-model="form.teamSize" class="field-input" placeholder="如:1-2人" placeholder-class="ph" />
        </view>
      </view>
      <view class="field-row">
        <view class="field grow">
          <text class="field-label">成本(元)</text>
          <input v-model="form.cost" type="digit" class="field-input" placeholder="0" placeholder-class="ph" />
        </view>
        <view class="field grow">
          <text class="field-label">开源协议</text>
          <input v-model="form.license" class="field-input" placeholder="GPL-3.0" placeholder-class="ph" />
        </view>
      </view>
      <view class="field">
        <text class="field-label">作者</text>
        <input v-model="form.author" class="field-input" placeholder="如:开源电子" placeholder-class="ph" />
      </view>
      <view class="field">
        <text class="field-label">标签(逗号分隔)</text>
        <input v-model="tagsText" class="field-input" placeholder="开发板, 入门, 开源" placeholder-class="ph" />
      </view>
      <view class="field">
        <text class="field-label">封面图</text>
        <view class="img-row">
          <image v-if="form.coverUrl" :src="fullUrl(form.coverUrl)" class="img-preview" mode="aspectFill" @click="chooseImg" />
          <view v-else class="img-add" @click="chooseImg">
            <text class="img-plus">+</text>
          </view>
          <text v-if="form.coverUrl" class="img-del" @click="form.coverUrl = ''">移除封面</text>
        </view>
      </view>
      <view class="field">
        <text class="field-label">状态</text>
        <view class="status-row">
          <view class="pill" :class="{ active: form.status === 'PUBLISHED' }" @click="form.status = 'PUBLISHED'">
            发布(学生可见)
          </view>
          <view class="pill" :class="{ active: form.status === 'DRAFT' }" @click="form.status = 'DRAFT'">
            草稿
          </view>
        </view>
      </view>
      <view class="field">
        <view class="check-row" @click="form.verified = !form.verified">
          <view class="checkbox" :class="{ checked: form.verified }">{{ form.verified ? '✓' : '' }}</view>
          <text class="check-text">硬件已验证</text>
        </view>
      </view>

      <button class="btn-gradient" :disabled="saving" @click="save">
        {{ saving ? '保存中...' : isEdit ? '保存修改' : '创建项目' }}
      </button>
    </view>
  </view>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { adminListProjects, adminCreateProject, adminUpdateProject } from '@/api'
import { uploadImage } from '@/utils/request'
import { fullUrl } from '@/config'
import { asList } from '@/utils/format'

const difficulties = ['入门', '进阶', '挑战']

const isEdit = ref(false)
const saving = ref(false)
const original = ref({})

const form = reactive({
  title: '',
  summary: '',
  description: '',
  difficulty: '入门',
  category: '',
  duration: '2周',
  teamSize: '1人',
  cost: '',
  license: 'GPL-3.0',
  author: '',
  // 图标值仅随数据保留(网页管理端可编辑),小程序端不提供输入
  icon: '🔌',
  coverUrl: '',
  status: 'PUBLISHED',
  verified: false
})

const tagsText = ref('')

onLoad(async (options) => {
  if (!options.id) return
  isEdit.value = true
  try {
    const list = await adminListProjects()
    const p = list.find((x) => String(x.id) === String(options.id))
    if (!p) {
      uni.showToast({ title: '项目不存在', icon: 'none' })
      return
    }
    original.value = p
    Object.assign(form, {
      title: p.title || '',
      summary: p.summary || '',
      description: p.description || '',
      difficulty: p.difficulty || '入门',
      category: p.category || '',
      duration: p.duration || '2周',
      teamSize: p.teamSize || '1人',
      cost: p.cost != null ? String(p.cost) : '',
      license: p.license || 'GPL-3.0',
      author: p.author || '',
      icon: p.icon || '🔌',
      coverUrl: p.coverUrl || '',
      status: p.status || 'PUBLISHED',
      verified: !!p.verified
    })
    tagsText.value = asList(p.tags).join(', ')
  } catch (e) {
    // 已提示
  }
})

const chooseImg = () => {
  uni.chooseImage({
    count: 1,
    sizeType: ['compressed'],
    success: async (res) => {
      uni.showLoading({ title: '上传中...' })
      try {
        const d = await uploadImage(res.tempFilePaths[0])
        form.coverUrl = d.url
      } catch (e) {
        // 已提示
      } finally {
        uni.hideLoading()
      }
    }
  })
}

const save = async () => {
  if (!form.title.trim()) {
    uni.showToast({ title: '请填写项目名称', icon: 'none' })
    return
  }
  const tags = tagsText.value
    .split(/[,，、]/)
    .map((s) => s.trim())
    .filter(Boolean)

  const payload = {
    ...original.value,
    title: form.title.trim(),
    summary: form.summary.trim(),
    description: form.description.trim(),
    difficulty: form.difficulty,
    category: form.category.trim(),
    duration: form.duration.trim() || '2周',
    teamSize: form.teamSize.trim() || '1人',
    cost: form.cost === '' ? null : Number(form.cost),
    license: form.license.trim() || 'GPL-3.0',
    author: form.author.trim(),
    icon: form.icon.trim() || '🔌',
    coverUrl: form.coverUrl || null,
    status: form.status,
    verified: form.verified,
    tags
  }

  saving.value = true
  try {
    if (isEdit.value) {
      await adminUpdateProject(original.value.id, payload)
    } else {
      await adminCreateProject(payload)
    }
    uni.showToast({ title: '已保存', icon: 'success' })
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
  padding: 24rpx 24rpx 60rpx;
}

.tip-box {
  background: $blue-bg;
  color: $brand-blue;
  font-size: 24rpx;
  border-radius: 16rpx;
  padding: 18rpx 24rpx;
  margin-bottom: 32rpx;
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
  width: 100%;

  &.picker {
    display: flex;
    align-items: center;
  }
}

.field-textarea {
  background: $gray-bg;
  border-radius: 16rpx;
  padding: 20rpx 24rpx;
  font-size: 28rpx;
  width: 100%;
  height: 200rpx;
  box-sizing: border-box;

  &.short {
    height: 120rpx;
  }
}

.ph {
  color: $text-light;
}

.img-row {
  display: flex;
  align-items: center;
  gap: 24rpx;
}

.img-preview {
  width: 240rpx;
  height: 150rpx;
  border-radius: 16rpx;
}

.img-add {
  width: 240rpx;
  height: 150rpx;
  border: 2rpx dashed $border-color;
  border-radius: 16rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}

.img-plus {
  font-size: 56rpx;
  color: $text-light;
}

.img-del {
  font-size: 24rpx;
  color: $red;
}

.status-row {
  display: flex;
  gap: 16rpx;
}

.check-row {
  display: flex;
  align-items: center;
  gap: 16rpx;
}

.checkbox {
  width: 36rpx;
  height: 36rpx;
  border-radius: 8rpx;
  border: 2rpx solid $border-color;
  background: #fff;
  color: #fff;
  font-size: 24rpx;
  display: flex;
  align-items: center;
  justify-content: center;

  &.checked {
    background: $brand-blue;
    border-color: $brand-blue;
  }
}

.check-text {
  font-size: 27rpx;
}
</style>
