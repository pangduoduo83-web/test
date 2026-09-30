<template>
  <div class="ai-loading-status" role="status" aria-live="polite">
    <span class="ai-loading-spinner" aria-hidden="true"></span>
    <div class="ai-loading-copy">
      <b>{{ phases[index % phases.length] || label }}</b>
      <span>已等待 {{ elapsed }} 秒 · {{ hint }}</span>
    </div>
  </div>
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'

defineProps({
  label: { type: String, default: 'AI 正在分析' },
  phases: { type: Array, default: () => [] },
  hint: { type: String, default: '结果生成后会自动显示' }
})

const elapsed = ref(0)
const index = ref(0)
let elapsedTimer
let phaseTimer

onMounted(() => {
  elapsedTimer = setInterval(() => { elapsed.value++ }, 1000)
  phaseTimer = setInterval(() => { index.value++ }, 4000)
})
onBeforeUnmount(() => {
  clearInterval(elapsedTimer)
  clearInterval(phaseTimer)
})
</script>

<style scoped>
.ai-loading-status { display: flex; align-items: center; gap: 10px; margin: 8px 0; color: #475569; }
.ai-loading-spinner { width: 18px; height: 18px; flex: 0 0 18px; border: 2px solid #dbeafe; border-top-color: #4f46e5; border-radius: 50%; animation: ai-loading-spin .9s linear infinite; }
.ai-loading-copy { display: flex; flex-direction: column; gap: 2px; min-width: 0; }
.ai-loading-copy b { color: #334155; font-size: 13px; font-weight: 600; }
.ai-loading-copy span { color: #94a3b8; font-size: 12px; line-height: 1.5; }
@keyframes ai-loading-spin { to { transform: rotate(360deg); } }
</style>
