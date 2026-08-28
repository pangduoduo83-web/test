<template>
  <view class="page">
    <view class="card head-card">
      <text class="p-title">{{ title }}</text>
      <text class="muted">学生可在项目详情「学习资源」中查看下载,带附件的资源支持直接打开</text>
    </view>

    <view class="card block">
      <view class="block-head">
        <text class="section-title">资源列表({{ list.length }})</text>
        <view class="add-btn" @click="openAdd">+ 添加</view>
      </view>

      <view v-if="list.length === 0" class="empty-box small-empty">
        <uni-icons type="folder-add" size="40" color="#d1d5db" />
        <text>暂无资源,点右上角添加</text>
      </view>

      <view v-for="(r, i) in list" :key="i" class="res-row">
        <uni-icons :type="resIcon(r.type)" size="20" color="#2563eb" />
        <view class="res-info">
          <text class="res-name ellipsis">{{ r.name }}</text>
          <text class="res-type muted">{{ r.type }}{{ r.url ? ' · 已上传附件' : ' · 无附件' }}</text>
        </view>
        <text class="res-del" @click="removeItem(i)">删除</text>
      </view>

      <button class="btn-gradient save-btn" :disabled="saving" @click="save">
        {{ saving ? '保存中...' : '保存资源列表' }}
      </button>
    </view>

    <!-- 添加弹层 -->
    <view v-if="addVisible" class="mask" @click="addVisible = false">
      <view class="modal" @click.stop>
        <text class="modal-title">添加教学资源</text>

        <text class="field-label">资源类型</text>
        <view class="type-row">
          <view
            v-for="t in types"
            :key="t"
            class="pill"
            :class="{ active: draft.type === t }"
            @click="draft.type = t"
          >
            {{ t }}
          </view>
        </view>

        <text class="field-label">资源名称</text>
        <input v-model="draft.name" class="field-input" placeholder="如:项目开发指南.pdf" placeholder-class="ph" />

        <text class="field-label">附件(选其一,可留空)</text>
        <view class="upload-row">
          <button class="btn-plain up-btn" @click="pickFile">从聊天选文件</button>
          <button class="btn-plain up-btn" @click="pickImage">上传图片</button>
        </view>
        <input v-model="draft.url" class="field-input" placeholder="或直接粘贴资源链接" placeholder-class="ph" />

        <view class="btn-row">
          <button class="btn-plain half" @click="addVisible = false">取消</button>
          <button class="btn-gradient half" @click="confirmAdd">添加</button>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { fetchProjectDetail, teacherUpdateResources } from '@/api'
import { uploadDocFile, uploadImage } from '@/utils/request'
import { asList } from '@/utils/format'

const types = ['文档', '视频', '代码', '手册', '原理图', 'LAYOUT', '3D图', '其他']

const projectId = ref(null)
const title = ref('')
const list = ref([])
const saving = ref(false)
const addVisible = ref(false)
const draft = reactive({ type: '文档', name: '', url: '' })

// 资源类型 → uni-icons 图标名
const resIcon = (type) =>
  ({ 文档: 'paperclip', 视频: 'videocam', 代码: 'gear', 手册: 'list', 原理图: 'map', LAYOUT: 'tune', '3D图': 'image' }[type] || 'paperclip')

onLoad(async (options) => {
  projectId.value = options.projectId
  title.value = decodeURIComponent(options.title || '教学资源')
  try {
    const d = await fetchProjectDetail(projectId.value)
    list.value = asList(d.project?.resources)
  } catch (e) {
    // 已提示
  }
})

const openAdd = () => {
  draft.type = '文档'
  draft.name = ''
  draft.url = ''
  addVisible.value = true
}

const pickFile = () => {
  uni.chooseMessageFile({
    count: 1,
    type: 'file',
    success: async (res) => {
      const f = res.tempFiles[0]
      if (f.size > 30 * 1024 * 1024) {
        uni.showToast({ title: '附件不能超过 30MB', icon: 'none' })
        return
      }
      uni.showLoading({ title: '上传中...' })
      try {
        const d = await uploadDocFile(f.path)
        draft.url = d.url
        if (!draft.name) draft.name = f.name || d.name
        uni.showToast({ title: '附件已上传', icon: 'none' })
      } catch (e) {
        // 已提示
      } finally {
        uni.hideLoading()
      }
    }
  })
}

const pickImage = () => {
  uni.chooseImage({
    count: 1,
    success: async (res) => {
      uni.showLoading({ title: '上传中...' })
      try {
        const d = await uploadImage(res.tempFilePaths[0])
        draft.url = d.url
        uni.showToast({ title: '图片已上传', icon: 'none' })
      } catch (e) {
        // 已提示
      } finally {
        uni.hideLoading()
      }
    }
  })
}

const confirmAdd = () => {
  if (!draft.name.trim()) {
    uni.showToast({ title: '请填写资源名称', icon: 'none' })
    return
  }
  list.value.push({ type: draft.type, name: draft.name.trim(), url: draft.url.trim() || undefined })
  addVisible.value = false
}

const removeItem = (i) => {
  uni.showModal({
    title: '删除资源',
    content: `确定删除「${list.value[i].name}」?保存后生效`,
    confirmColor: '#dc2626',
    success: (res) => {
      if (res.confirm) list.value.splice(i, 1)
    }
  })
}

const save = async () => {
  saving.value = true
  try {
    await teacherUpdateResources(projectId.value, JSON.stringify(list.value))
    uni.showToast({ title: '已保存', icon: 'success' })
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

.p-title {
  display: block;
  font-size: 32rpx;
  font-weight: 700;
  margin-bottom: 8rpx;
}

.block {
  margin-top: 24rpx;
}

.block-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12rpx;
}

.add-btn {
  font-size: 26rpx;
  color: $brand-blue;
  background: $blue-bg;
  border-radius: $radius-pill;
  padding: 8rpx 28rpx;
}

.small-empty {
  padding: 60rpx 0;
}

.res-row {
  display: flex;
  align-items: center;
  gap: 20rpx;
  padding: 24rpx;
  background: $gray-bg;
  border-radius: 16rpx;
  margin-top: 16rpx;
}

.res-info {
  flex: 1;
  overflow: hidden;
}

.res-name {
  display: block;
  font-size: 28rpx;
}

.res-type {
  display: block;
  font-size: 22rpx;
  margin-top: 4rpx;
}

.res-del {
  color: $red;
  font-size: 25rpx;
}

.save-btn {
  margin-top: 32rpx;
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
  margin-bottom: 28rpx;
}

.field-label {
  display: block;
  font-size: 26rpx;
  font-weight: 500;
  margin: 24rpx 0 12rpx;
}

.type-row {
  display: flex;
  gap: 14rpx;
  flex-wrap: wrap;
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

.upload-row {
  display: flex;
  gap: 16rpx;
  margin-bottom: 16rpx;
}

.up-btn {
  flex: 1;
  font-size: 25rpx;
  padding: 14rpx 0;
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
