<template>
  <view class="page">
    <view class="search-box">
      <text class="search-icon">⌕</text>
      <input v-model="keyword" class="search-input" placeholder="搜索设备名称、型号..." placeholder-class="ph" />
    </view>

    <view v-if="loading" class="empty-box">
      <text class="empty-icon">⏳</text>
      <text>加载中...</text>
    </view>
    <view v-else class="list">
      <view v-for="e in filtered" :key="e.id" class="card equip-card">
        <view class="ec-head">
          <view class="ec-icon">{{ e.icon || '🔧' }}</view>
          <view class="ec-info">
            <view class="ec-name-row">
              <text class="ec-name ellipsis">{{ e.name }}</text>
              <text class="badge" :class="e.status === 'MAINTENANCE' ? 'badge-yellow' : 'badge-green'">
                {{ e.status === 'MAINTENANCE' ? '维护中' : '可借阅' }}
              </text>
            </view>
            <text class="ec-sub muted">{{ e.model }} · {{ e.manufacturer || '-' }} · {{ e.location }}</text>
            <text class="ec-stock muted">
              库存 {{ e.availableCount }}/{{ e.totalCount }} · 借出 {{ e.borrowCount }} 次 · ¥{{ e.price }}
            </text>
          </view>
        </view>
        <view class="ec-actions">
          <view class="act-btn" @click="goEdit(e.id)">✏️ 编辑</view>
          <view class="act-btn danger" @click="onDelete(e)">🗑 删除</view>
        </view>
      </view>
      <view v-if="filtered.length === 0" class="empty-box">
        <text class="empty-icon">🔍</text>
        <text>没有匹配的设备</text>
      </view>
    </view>

    <view class="fab" @click="goEdit()">+ 新增设备</view>
    <view class="bottom-gap" />
  </view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onShow, onPullDownRefresh } from '@dcloudio/uni-app'
import { fetchEquipment, adminDeleteEquipment } from '@/api'

const keyword = ref('')
const items = ref([])
const loading = ref(true)

const filtered = computed(() => {
  const k = keyword.value.trim().toLowerCase()
  if (!k) return items.value
  return items.value.filter(
    (e) => (e.name || '').toLowerCase().includes(k) || (e.model || '').toLowerCase().includes(k)
  )
})

const load = async () => {
  loading.value = true
  try {
    items.value = await fetchEquipment({})
  } catch (e) {
    // 已提示
  } finally {
    loading.value = false
  }
}

const goEdit = (id) => {
  uni.navigateTo({ url: `/pages/admin/equipment-edit${id ? '?id=' + id : ''}` })
}

const onDelete = (e) => {
  uni.showModal({
    title: '删除设备',
    content: `确定删除「${e.name}」?存在进行中借阅的设备无法删除`,
    confirmColor: '#dc2626',
    success: async (res) => {
      if (!res.confirm) return
      try {
        await adminDeleteEquipment(e.id)
        uni.showToast({ title: '已删除', icon: 'success' })
        load()
      } catch (err) {
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

.search-icon {
  color: $text-light;
  font-size: 34rpx;
  font-weight: 700;
}

.search-input {
  flex: 1;
  font-size: 28rpx;
  height: 44rpx;
}

.ph {
  color: $text-light;
}

.list {
  display: flex;
  flex-direction: column;
  gap: 20rpx;
  margin-top: 24rpx;
}

.equip-card {
  padding: 28rpx;
}

.ec-head {
  display: flex;
  gap: 22rpx;
}

.ec-icon {
  width: 96rpx;
  height: 96rpx;
  border-radius: 20rpx;
  background: linear-gradient(135deg, #60a5fa, #a78bfa);
  font-size: 44rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.ec-info {
  flex: 1;
  overflow: hidden;
}

.ec-name-row {
  display: flex;
  align-items: center;
  gap: 14rpx;
}

.ec-name {
  font-size: 30rpx;
  font-weight: 600;
  flex: 1;
}

.ec-sub {
  display: block;
  font-size: 23rpx;
  margin-top: 8rpx;
}

.ec-stock {
  display: block;
  font-size: 23rpx;
  margin-top: 4rpx;
}

.ec-actions {
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
</style>
