<template>
  <view class="page">
    <view class="card">
      <view class="field">
        <text class="field-label">设备名称 <text class="req">*</text></text>
        <input v-model="form.name" class="field-input" placeholder="如:数字示波器" placeholder-class="ph" />
      </view>
      <view class="field-row">
        <view class="field grow">
          <text class="field-label">型号</text>
          <input v-model="form.model" class="field-input" placeholder="如:RIGOL DS1054Z" placeholder-class="ph" />
        </view>
        <view class="field grow">
          <text class="field-label">厂商</text>
          <input v-model="form.manufacturer" class="field-input" placeholder="如:RIGOL" placeholder-class="ph" />
        </view>
      </view>
      <view class="field-row">
        <view class="field grow">
          <text class="field-label">分类</text>
          <input v-model="form.category" class="field-input" placeholder="如:测试仪表/开发板" placeholder-class="ph" />
        </view>
        <view class="field grow">
          <text class="field-label">存放位置</text>
          <input v-model="form.location" class="field-input" placeholder="如:B栋1楼" placeholder-class="ph" />
        </view>
      </view>
      <view class="field-row">
        <view class="field grow">
          <text class="field-label">状态</text>
          <picker
            mode="selector"
            :range="statusLabels"
            @change="form.status = statusValues[$event.detail.value]"
          >
            <view class="field-input picker">{{ form.status === 'MAINTENANCE' ? '维护中' : '可借阅' }}</view>
          </picker>
        </view>
        <view class="field grow">
          <text class="field-label">参考价(元)</text>
          <input v-model="form.price" type="digit" class="field-input" placeholder="0" placeholder-class="ph" />
        </view>
      </view>
      <view class="field-row">
        <view class="field grow">
          <text class="field-label">总数量 <text class="req">*</text></text>
          <input v-model="form.totalCount" type="number" class="field-input" placeholder="1" placeholder-class="ph" />
        </view>
        <view class="field grow">
          <text class="field-label">可借数量</text>
          <input v-model="form.availableCount" type="number" class="field-input" placeholder="留空=同总数" placeholder-class="ph" />
        </view>
      </view>
      <view class="field">
        <text class="field-label">设备图片</text>
        <view class="img-row">
          <image v-if="form.imageUrl" :src="fullUrl(form.imageUrl)" class="img-preview" mode="aspectFill" @click="chooseImg" />
          <view v-else class="img-add" @click="chooseImg">
            <text class="img-plus">+</text>
          </view>
          <text v-if="form.imageUrl" class="img-del" @click="form.imageUrl = ''">移除图片</text>
        </view>
      </view>
      <view class="field">
        <text class="field-label">设备描述</text>
        <textarea v-model="form.description" class="field-textarea" placeholder="一句话说明用途与亮点..." placeholder-class="ph" :maxlength="500" />
      </view>
      <view class="field">
        <text class="field-label">技术规格(逗号分隔)</text>
        <textarea v-model="specsText" class="field-textarea short" placeholder="50MHz带宽, 4通道, 1GSa/s采样率" placeholder-class="ph" />
      </view>
      <view class="field">
        <text class="field-label">标签(逗号分隔)</text>
        <input v-model="tagsText" class="field-input" placeholder="调试, 测量, 高精度" placeholder-class="ph" />
      </view>
      <view class="field">
        <text class="field-label">相关文档(逗号分隔)</text>
        <input v-model="docsText" class="field-input" placeholder="用户手册, 编程手册" placeholder-class="ph" />
      </view>
      <view class="field">
        <text class="field-label">适用项目(逗号分隔)</text>
        <input v-model="suitableText" class="field-input" placeholder="通信协议分析, 模拟电路调试" placeholder-class="ph" />
      </view>

      <button class="btn-gradient" :disabled="saving" @click="save">
        {{ saving ? '保存中...' : isEdit ? '保存修改' : '创建设备' }}
      </button>
    </view>
  </view>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { fetchEquipmentDetail, adminCreateEquipment, adminUpdateEquipment } from '@/api'
import { uploadImage } from '@/utils/request'
import { fullUrl } from '@/config'
import { asList } from '@/utils/format'

const statusLabels = ['可借阅', '维护中']
const statusValues = ['AVAILABLE', 'MAINTENANCE']

const isEdit = ref(false)
const saving = ref(false)
const original = ref({})

const form = reactive({
  name: '',
  model: '',
  manufacturer: '',
  category: '',
  location: '',
  status: 'AVAILABLE',
  price: '',
  totalCount: '1',
  availableCount: '',
  // 图标值仅随数据保留(网页管理端可编辑),小程序端不提供输入
  icon: '🔧',
  imageUrl: '',
  description: ''
})

const specsText = ref('')
const tagsText = ref('')
const docsText = ref('')
const suitableText = ref('')

onLoad(async (options) => {
  if (!options.id) return
  isEdit.value = true
  try {
    const e = await fetchEquipmentDetail(options.id)
    original.value = e
    Object.assign(form, {
      name: e.name || '',
      model: e.model || '',
      manufacturer: e.manufacturer || '',
      category: e.category || '',
      location: e.location || '',
      status: e.status || 'AVAILABLE',
      price: e.price != null ? String(e.price) : '',
      totalCount: String(e.totalCount ?? 1),
      availableCount: e.availableCount != null ? String(e.availableCount) : '',
      icon: e.icon || '🔧',
      imageUrl: e.imageUrl || '',
      description: e.description || ''
    })
    specsText.value = asList(e.specs).join(', ')
    tagsText.value = asList(e.tags).join(', ')
    docsText.value = asList(e.docs).join(', ')
    suitableText.value = asList(e.suitableProjects).join(', ')
  } catch (err) {
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
        form.imageUrl = d.url
      } catch (e) {
        // 已提示
      } finally {
        uni.hideLoading()
      }
    }
  })
}

const splitList = (text) =>
  text
    .split(/[,，、]/)
    .map((s) => s.trim())
    .filter(Boolean)

const save = async () => {
  if (!form.name.trim()) {
    uni.showToast({ title: '请填写设备名称', icon: 'none' })
    return
  }
  const total = parseInt(form.totalCount, 10)
  if (!total || total < 1) {
    uni.showToast({ title: '总数量至少为 1', icon: 'none' })
    return
  }
  const avail = form.availableCount === '' ? null : parseInt(form.availableCount, 10)
  if (avail != null && (avail < 0 || avail > total)) {
    uni.showToast({ title: '可借数量需在 0~总数 之间', icon: 'none' })
    return
  }

  const payload = {
    ...original.value,
    name: form.name.trim(),
    model: form.model.trim(),
    manufacturer: form.manufacturer.trim(),
    category: form.category.trim(),
    location: form.location.trim(),
    status: form.status,
    price: form.price === '' ? null : Number(form.price),
    totalCount: total,
    availableCount: avail,
    icon: form.icon.trim() || '🔧',
    imageUrl: form.imageUrl || null,
    description: form.description.trim(),
    specs: splitList(specsText.value),
    tags: splitList(tagsText.value),
    docs: splitList(docsText.value),
    suitableProjects: splitList(suitableText.value)
  }
  if (payload.rating == null) payload.rating = 5.0

  saving.value = true
  try {
    if (isEdit.value) {
      await adminUpdateEquipment(original.value.id, payload)
    } else {
      await adminCreateEquipment(payload)
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
  height: 160rpx;
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
  width: 180rpx;
  height: 180rpx;
  border-radius: 16rpx;
}

.img-add {
  width: 180rpx;
  height: 180rpx;
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
</style>
