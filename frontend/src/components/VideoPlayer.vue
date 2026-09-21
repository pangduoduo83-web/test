<template>
  <div class="video-player">
    <button v-if="!started" class="video-start" type="button" :disabled="loading" @click="start">
      <span class="play-icon" aria-hidden="true">▶</span>
      <span>{{ loading ? '正在准备视频…' : '点击播放视频' }}</span>
      <small>播放时才加载，节省流量</small>
    </button>
    <video v-else ref="video" controls playsinline preload="none" :aria-label="title || '视频'" @error="failed" @pause="pause" @play="resume" />
    <div v-if="notice" class="video-notice" role="status">{{ notice }} <a :href="src" target="_blank" rel="noopener">打开原视频</a></div>
  </div>
</template>

<script setup>
import { ref, nextTick, onBeforeUnmount, watch } from 'vue'
import { fetchVideoPlayback } from '../api'
import { getToken } from '../utils/authStorage'

const props = defineProps({ src: { type: String, required: true }, title: { type: String, default: '' } })
const video = ref(null)
const started = ref(false)
const loading = ref(false)
const notice = ref('')
let hls = null
let generation = 0
let usingHls = false

function release() {
  hls?.destroy()
  hls = null
  usingHls = false
  if (video.value) {
    video.value.pause()
    video.value.removeAttribute('src')
    video.value.load()
  }
}

function play() {
  video.value?.play()?.catch(() => { /* Some browsers require a second tap after async preparation. */ })
}

function original(message = '') {
  const position = video.value?.currentTime || 0
  release()
  notice.value = message
  if (!video.value) return
  video.value.src = props.src
  if (position > 0) video.value.addEventListener('loadedmetadata', () => {
    if (video.value) video.value.currentTime = position
  }, { once: true })
  play()
}

async function start() {
  if (loading.value) return
  const run = ++generation
  loading.value = true
  notice.value = ''
  let playback
  // Public rich-text pages still play the original file without requesting a login-only API.
  if (getToken() && /^\/uploads\/\d{6}\/[\w-]+\.(mp4|mov|webm)$/i.test(props.src)) {
    try { playback = await fetchVideoPlayback(props.src) } catch { /* The original remains available. */ }
  }
  if (run !== generation) return
  started.value = true
  await nextTick()
  if (run !== generation || !video.value) return
  loading.value = false
  if (!playback?.hls) return original()
  usingHls = true
  if (video.value.canPlayType('application/vnd.apple.mpegurl')) {
    video.value.src = playback.hls
    play()
    return
  }
  try {
    const { default: Hls } = await import('hls.js')
    if (run !== generation || !video.value) return
    if (!Hls.isSupported()) return original()
    hls = new Hls({ startLevel: 0, capLevelToPlayerSize: true, maxBufferLength: 10, maxMaxBufferLength: 20, backBufferLength: 15 })
    hls.on(Hls.Events.ERROR, (_, data) => { if (data.fatal && run === generation) failed() })
    hls.on(Hls.Events.MANIFEST_PARSED, () => { if (run === generation) play() })
    hls.loadSource(playback.hls)
    hls.attachMedia(video.value)
  } catch { if (run === generation) original('暂时无法分段播放，已切换原视频。') }
}

function failed() {
  if (usingHls) original('暂时无法分段播放，已切换原视频。')
  else notice.value = '视频加载失败，可重试或打开原视频。'
}
function pause() { hls?.stopLoad() }
function resume() { hls?.startLoad(-1) }
watch(() => props.src, () => { generation++; release(); started.value = false; loading.value = false; notice.value = '' })
onBeforeUnmount(() => { generation++; release() })
</script>

<style scoped>
.video-player { width: 100%; overflow: hidden; border-radius: 10px; background: #f3f4f6; }
.video-start { width: 100%; min-height: 200px; padding: 28px 16px; border: 0; cursor: pointer; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 10px; color: var(--text-main, #1f2937); background: linear-gradient(135deg, #eff6ff, #faf5ff); font: inherit; }
.video-start:focus-visible { outline: 2px solid var(--brand-blue, #2563eb); outline-offset: -3px; }
.play-icon { display: grid; place-items: center; width: 48px; height: 48px; border-radius: 50%; background: #fff; color: var(--brand-blue, #2563eb); box-shadow: 0 3px 14px #2563eb18; }
.video-start small, .video-notice { color: var(--text-secondary, #6b7280); font-size: 12px; }
video { display: block; width: 100%; max-height: 480px; min-height: 200px; background: #111827; }
.video-notice { padding: 10px 12px; line-height: 1.6; }.video-notice a { color: var(--brand-blue, #2563eb); white-space: nowrap; }
</style>
