<template>
  <div class="service-test">
    <div class="test-action">
      <el-button :loading="loading" @click="$emit('test')"><Zap :size="14" />{{ label }}</el-button>
      <span>{{ hint }}</span>
    </div>
    <div v-if="result" class="test-feedback" :class="result.ok ? 'ok' : 'fail'" role="status" aria-live="polite">
      <CircleCheckBig v-if="result.ok" :size="16" /><CircleX v-else :size="16" />
      <div><b>{{ result.ok ? result.summary : result.error }}</b><small v-if="result.latencyMs != null">耗时 {{ result.latencyMs }} ms</small><p v-if="result.ok && result.reply">{{ result.reply }}</p></div>
    </div>
  </div>
</template>

<script setup>
import { CircleCheckBig, CircleX, Zap } from 'lucide-vue-next'
defineProps({ label: String, hint: String, loading: Boolean, result: Object })
defineEmits(['test'])
</script>

<style scoped>
.service-test { margin-top: 18px; padding-top: 14px; border-top: 1px solid var(--border, #e5e7eb); }
.test-action { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
.test-action :deep(.el-button > span) { display: flex; align-items: center; gap: 5px; }
.test-action > span { color: var(--text-secondary, #6b7280); font-size: 12px; line-height: 1.6; }
.test-feedback { display: flex; align-items: flex-start; gap: 7px; padding: 12px; margin-top: 12px; border-radius: 8px; font-size: 13px; line-height: 1.6; overflow-wrap: anywhere; }
.test-feedback > svg { flex-shrink: 0; margin-top: 2px; }
.test-feedback.ok { color: #15803d; background: #f0fdf4; }
.test-feedback.fail { color: #b91c1c; background: #fef2f2; }
.test-feedback small { display: block; opacity: .75; }
.test-feedback p { margin: 6px 0 0; color: var(--text-main, #374151); white-space: pre-wrap; }
</style>
