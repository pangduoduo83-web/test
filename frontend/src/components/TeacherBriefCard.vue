<template>
  <section class="ai-card" aria-labelledby="ai-reminder-title">
    <div class="ai-header">
      <div class="ai-heading">
        <span class="ai-icon"><Sparkles :size="20" /></span>
        <h2 id="ai-reminder-title">AI提醒</h2>
        <span class="ai-subtitle">基于项目数据的智能分析与建议</span>
      </div>
      <button class="ai-view-btn" type="button" :disabled="loading" @click="fullVisible = true">
        <span>{{ loading ? '分析中...' : '查看完整分析' }}</span>
        <ChevronRight :size="16" />
      </button>
    </div>

    <div v-if="loading && !brief" class="ai-loading">
      <AiLoadingStatus :phases="['正在汇总项目与提交数据…', '正在检查班级进度与风险…', '正在整理本周教学建议…']" hint="分析完成后会显示在这里" />
      <span /><span /><span />
    </div>
    <template v-else-if="brief">
      <div class="ai-discovery">
        <strong>AI发现：</strong>
        <span>{{ brief.summary }}</span>
      </div>
      <div v-if="brief.highlights?.length" class="ai-highlights">
        <span v-for="(highlight, index) in brief.highlights.slice(0, 2)" :key="index">{{ highlight }}</span>
      </div>
      <div v-if="brief.actions?.length" class="ai-actions">
        <span class="ai-actions-label">建议你：</span>
        <button v-for="(action, index) in brief.actions.slice(0, 3)" :key="index" type="button" class="ai-action" @click="$emit('action', action)">
          <span class="ai-action-number">{{ index + 1 }}</span>
          <span class="ai-action-title">{{ action.title }}</span>
          <ChevronRight :size="15" />
        </button>
      </div>
    </template>
    <div v-else class="ai-empty">
      <span>{{ error ? 'AI 暂不可用：' + error : '暂时没有生成教学提醒' }}</span>
      <button type="button" @click="load(false)"><RefreshCw :size="14" />重新生成</button>
    </div>

    <el-dialog v-model="fullVisible" title="AI 教学分析" width="min(680px, 94vw)">
      <template v-if="brief">
        <p class="full-summary">{{ brief.summary }}</p>
        <div v-if="brief.highlights?.length" class="full-section">
          <h3>值得关注</h3>
          <ul><li v-for="(highlight, index) in brief.highlights" :key="index">{{ highlight }}</li></ul>
        </div>
        <div v-if="brief.actions?.length" class="full-section">
          <h3>建议动作</h3>
          <button v-for="(action, index) in brief.actions" :key="index" type="button" class="full-action" @click="$emit('action', action); fullVisible = false">
            <span class="ai-action-number">{{ index + 1 }}</span>
            <span><b>{{ action.title }}</b><small>{{ action.detail }}</small></span>
            <ChevronRight :size="15" />
          </button>
        </div>
      </template>
      <div v-else class="full-empty">{{ error || '暂无分析结果' }}</div>
      <template #footer>
        <el-button :loading="loading" @click="load(true)">重新生成</el-button>
        <el-button type="primary" @click="fullVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ChevronRight, RefreshCw, Sparkles } from 'lucide-vue-next'
import { runJsonSkillCached } from '../api/aiJson'
import AiLoadingStatus from './ai/AiLoadingStatus.vue'

const props = defineProps({ userId: { type: [Number, String], required: true } })
defineEmits(['action'])
const brief = ref(null)
const loading = ref(false)
const error = ref('')
const fullVisible = ref(false)
const weekKey = () => {
  const date = new Date()
  const first = new Date(date.getFullYear(), 0, 1)
  return date.getFullYear() + '-w' + Math.ceil(((date - first) / 86400000 + first.getDay() + 1) / 7)
}
const load = async (force = false) => {
  loading.value = true
  error.value = ''
  try {
    const result = await runJsonSkillCached('teacher-brief:' + props.userId + ':' + weekKey(), 24 * 3600 * 1000, 'teacher-weekly-brief', '请生成本周教学周报', { scope: 'teacher' }, force)
    brief.value = result.data
  } catch (e) {
    brief.value = null
    error.value = e?.message || ''
  } finally {
    loading.value = false
  }
}
onMounted(() => load(false))
</script>

<style scoped>
.ai-card {
  padding: 22px 25px 20px;
  border: 1px solid #e6ebf7;
  border-radius: 17px;
  background: linear-gradient(112deg, #fbf9ff 0%, #f5f7ff 52%, #f7fbff 100%);
  box-shadow: 0 8px 22px rgba(70, 82, 135, .045);
}
.ai-header { display: flex; align-items: center; justify-content: space-between; gap: 18px; margin-bottom: 17px; }
.ai-heading { display: flex; align-items: center; gap: 11px; min-width: 0; }
.ai-icon { width: 32px; height: 32px; display: inline-flex; align-items: center; justify-content: center; border-radius: 10px; color: #7441e8; background: #eee8ff; }
.ai-heading h2 { margin: 0; color: #182757; font-size: 21px; font-weight: 800; }
.ai-subtitle { margin-left: 4px; overflow: hidden; color: #8b97b0; font-size: 12px; text-overflow: ellipsis; white-space: nowrap; }
.ai-view-btn { display: inline-flex; align-items: center; gap: 3px; height: 34px; padding: 0 11px; border: 1px solid #aab9f6; border-radius: 8px; color: #3866dc; background: transparent; font: inherit; font-size: 12px; cursor: pointer; }
.ai-view-btn:hover { border-color: #6b84ed; color: #254ed0; background: #f2f5ff; }
.ai-view-btn:disabled { cursor: wait; opacity: .65; }
.ai-discovery { display: flex; align-items: center; gap: 7px; min-height: 45px; padding: 0 14px; border-left: 4px solid #7d4bf2; border-radius: 7px; color: #57678d; background: rgba(236, 240, 252, .8); font-size: 13px; line-height: 1.65; }
.ai-discovery strong { flex-shrink: 0; color: #1e2d59; }
.ai-highlights { display: flex; flex-wrap: wrap; gap: 8px; margin: 11px 0 0 5px; color: #73819f; font-size: 12px; }
.ai-highlights span { padding: 4px 10px; border-radius: 999px; background: rgba(255, 255, 255, .72); }
.ai-actions { display: flex; align-items: center; gap: 10px; margin-top: 12px; }
.ai-actions-label { flex-shrink: 0; color: #5f6f91; font-size: 12px; font-weight: 700; }
.ai-action { min-width: 0; flex: 1; height: 40px; display: flex; align-items: center; gap: 9px; padding: 0 10px; border: 1px solid #e1e7f3; border-radius: 9px; color: #627293; background: rgba(255, 255, 255, .86); font: inherit; font-size: 12px; text-align: left; cursor: pointer; }
.ai-action:hover { border-color: #a9b9f2; color: #315fd8; background: #fff; }
.ai-action-number { width: 22px; height: 22px; display: inline-grid; place-items: center; flex-shrink: 0; border-radius: 50%; color: #fff; background: linear-gradient(135deg, #4c80f4, #7450ec); font-size: 11px; font-weight: 700; }
.ai-action-title { min-width: 0; overflow: hidden; flex: 1; text-overflow: ellipsis; white-space: nowrap; }
.ai-action > svg { flex-shrink: 0; color: #98a5bf; }
.ai-loading { display: flex; flex-direction: column; gap: 10px; padding: 6px 0; }
.ai-loading span { height: 12px; border-radius: 6px; background: linear-gradient(90deg, #e9e7fc, #d9d7f8, #e9e7fc); background-size: 200% 100%; animation: ai-loading 1.2s infinite; }
.ai-loading span:nth-child(1) { width: 83%; }
.ai-loading span:nth-child(2) { width: 96%; }
.ai-loading span:nth-child(3) { width: 68%; }
.ai-empty { display: flex; align-items: center; justify-content: space-between; gap: 12px; color: #7f8ba4; font-size: 13px; }
.ai-empty button { display: inline-flex; align-items: center; gap: 5px; border: 0; color: #4269dc; background: transparent; font: inherit; font-size: 12px; cursor: pointer; }
.full-summary { margin: 0; color: #35466e; font-size: 14px; line-height: 1.8; }
.full-section { margin-top: 19px; }
.full-section h3 { margin: 0 0 9px; color: #293a62; font-size: 13px; }
.full-section ul { margin: 0; padding-left: 19px; color: #667594; font-size: 13px; line-height: 1.9; }
.full-action { width: 100%; display: flex; align-items: center; gap: 10px; margin-top: 8px; padding: 10px 11px; border: 1px solid #e1e7f3; border-radius: 9px; color: #627293; background: #fbfcff; text-align: left; cursor: pointer; }
.full-action:hover { border-color: #a9b9f2; background: #f5f8ff; }
.full-action > span:nth-child(2) { display: flex; flex-direction: column; gap: 3px; flex: 1; min-width: 0; }
.full-action b { color: #2d3f68; font-size: 13px; }
.full-action small { color: #7f8ca5; font-size: 12px; }
.full-action > svg { flex-shrink: 0; color: #98a5bf; }
.full-empty { color: #7f8ba4; font-size: 13px; }
@keyframes ai-loading { 0% { background-position: 200% 0; } 100% { background-position: -200% 0; } }
@media (max-width: 900px) {
  .ai-actions { align-items: stretch; flex-direction: column; }
  .ai-actions-label { margin-bottom: 2px; }
  .ai-action { flex: initial; }
}
@media (max-width: 600px) {
  .ai-card { padding: 18px 16px; }
  .ai-header { align-items: flex-start; flex-direction: column; }
  .ai-view-btn { align-self: flex-end; }
  .ai-subtitle { display: none; }
  .ai-discovery { align-items: flex-start; flex-direction: column; gap: 0; padding-top: 8px; padding-bottom: 8px; }
}
</style>
