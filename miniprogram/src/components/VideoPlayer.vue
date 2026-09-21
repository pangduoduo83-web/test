<template>
  <view class="video-player">
    <button v-if="!source" class="video-start" :disabled="loading" @click="start">
      <uni-icons type="videocam" size="32" color="#2563eb" />
      <text>{{ loading ? '正在准备视频…' : '点击播放视频' }}</text>
      <text class="hint">播放时才加载，节省流量</text>
    </button>
    <video v-else :key="source" :src="source" controls autoplay :show-center-play-btn="true" @error="failed" />
    <text v-if="notice" class="notice">{{ notice }}</text>
    <button v-if="failedOriginal" class="retry" @click="retry">重新加载</button>
  </view>
</template>
<script setup>
import { ref, watch, onBeforeUnmount } from 'vue'
import { BASE_URL, fullUrl } from '@/config'
import { get } from '@/utils/request'
import { getToken } from '@/utils/auth'
const props = defineProps({ src: { type: String, required: true } })
const source = ref('')
const loading = ref(false)
const notice = ref('')
const failedOriginal = ref(false)
let generation = 0
let hls = false
async function start() {
  if (loading.value) return
  const run = ++generation
  loading.value = true
  let playback
  const relative = props.src.startsWith(BASE_URL + '/') ? props.src.slice(BASE_URL.length) : props.src
  if (getToken() && /^\/uploads\/\d{6}\/[\w-]+\.(mp4|mov|webm)$/i.test(relative)) {
    try { playback = await get('/media/playback', { url: relative }, { silent: true }) } catch { /* Play the original when preparation is unavailable. */ }
  }
  if (run !== generation) return
  hls = !!playback?.hls
  source.value = fullUrl(playback?.hls || props.src)
  loading.value = false
}
function failed() {
  if (hls) {
    hls = false
    notice.value = '已切换原视频播放'
    source.value = fullUrl(props.src)
  } else { failedOriginal.value = true; notice.value = '视频加载失败，请重试' }
}
function reset() { generation++; source.value = ''; loading.value = false; notice.value = ''; failedOriginal.value = false; hls = false }
function retry() { reset(); start() }
watch(() => props.src, reset)
onBeforeUnmount(() => { generation++ })
</script>
<style scoped>
.video-player { width: 100%; overflow: hidden; border-radius: 16rpx; background: #f3f4f6; }
.video-start { min-height: 320rpx; margin: 0; border: 0; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 16rpx; color: #1f2937; background: linear-gradient(135deg,#eff6ff,#faf5ff); font-size: 27rpx; line-height: 1.6; }.video-start::after { border: 0; }
.hint,.notice { color: #6b7280; font-size: 23rpx; }.notice { display: block; padding: 16rpx; }video { display: block; width: 100%; }.retry { font-size: 24rpx; color: #2563eb; }
</style>
