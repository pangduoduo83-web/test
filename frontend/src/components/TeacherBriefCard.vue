<template>
  <!-- 教师工作台「AI 教学周报」:摘要 + 现象 + 建议动作,每个动作有直达按钮 -->
  <div class="card brief">
    <div class="card-head">
      <h3><span class="ic">📋</span> AI 教学周报</h3>
      <div class="head-right">
        <span class="muted">{{ at ? '生成于 ' + fmt(at) : '根据名下项目的报名、进度、待评成果与掉队名单生成' }}</span>
        <el-button size="small" text :loading="loading" @click="load(true)"><RefreshCw :size="13" style="margin-right:4px" />重新生成</el-button>
      </div>
    </div>
    <div v-if="loading && !brief" class="skeleton"><div class="sk w80"></div><div class="sk"></div><div class="sk w60"></div></div>
    <template v-else-if="brief">
      <p class="summary">{{ brief.summary }}</p>
      <div class="two">
        <div>
          <div class="sec-title">值得关注</div>
          <ul class="hl"><li v-for="(h, i) in brief.highlights" :key="i">{{ h }}</li></ul>
        </div>
        <div>
          <div class="sec-title">建议动作</div>
          <div v-for="(a, i) in brief.actions" :key="i" class="action">
            <div class="grow">
              <div class="ac-title">{{ a.title }}</div>
              <div class="ac-detail">{{ a.detail }}</div>
            </div>
            <el-button size="small" plain @click="$emit('action', a)">{{ actionText(a.kind) }}</el-button>
          </div>
        </div>
      </div>
    </template>
    <div v-else class="muted">{{ error ? 'AI 暂不可用:' + error : '点右上角生成本周教学摘要' }}</div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { RefreshCw } from 'lucide-vue-next'
import { runJsonSkillCached } from '../api/aiJson'

const props = defineProps({ userId: { type: [Number, String], required: true } })
defineEmits(['action'])

const brief = ref(null)
const loading = ref(false)
const error = ref('')
const at = ref(null)
const fmt = (t) => new Date(t).toLocaleString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })
const actionText = (k) => ({ grade: '去评审', remind: '看名单', announce: '发公告', adjust: '看项目' }[k] || '去看看')
const weekKey = () => {
  const d = new Date()
  const onejan = new Date(d.getFullYear(), 0, 1)
  return `${d.getFullYear()}-w${Math.ceil(((d - onejan) / 86400000 + onejan.getDay() + 1) / 7)}`
}

const load = async (force = false) => {
  loading.value = true
  error.value = ''
  try {
    const r = await runJsonSkillCached(`teacher-brief:${props.userId}:${weekKey()}`, 24 * 3600 * 1000, 'teacher-weekly-brief', '请生成本周教学周报', { scope: 'teacher' }, force)
    brief.value = r.data
    at.value = r.at
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
.brief { margin-bottom: 20px; background: linear-gradient(135deg, #faf5ff, #eff6ff); border: 1px solid #e9d5ff; }
.card-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px; gap: 12px; }
.card-head h3 { margin: 0; font-size: 16px; display: flex; align-items: center; gap: 8px; }
.ic { font-size: 18px; }
.head-right { display: flex; align-items: center; gap: 8px; }
.muted { font-size: 12px; color: var(--text-secondary); }
.grow { flex: 1; min-width: 0; }
.summary { font-size: 14px; color: #3b0764; line-height: 1.7; margin: 0 0 12px; }
.two { display: grid; grid-template-columns: 1fr 1fr; gap: 18px; }
@media (max-width: 1000px) { .two { grid-template-columns: 1fr; } }
.sec-title { font-size: 12px; font-weight: 700; color: #6b21a8; margin-bottom: 6px; }
.hl { margin: 0; padding-left: 18px; font-size: 13px; color: #374151; line-height: 1.8; }
.action { display: flex; align-items: center; gap: 10px; background: #fff; border-radius: 10px; padding: 8px 12px; margin-bottom: 8px; }
.ac-title { font-size: 13px; font-weight: 600; }
.ac-detail { font-size: 12px; color: var(--text-secondary); margin-top: 2px; }
.skeleton { display: flex; flex-direction: column; gap: 10px; padding: 6px 0; }
.sk { height: 12px; border-radius: 6px; background: linear-gradient(90deg, #ede9fe, #ddd6fe, #ede9fe); background-size: 200% 100%; animation: sk 1.2s infinite; }
.sk.w80 { width: 80%; } .sk.w60 { width: 60%; }
@keyframes sk { 0% { background-position: 200% 0; } 100% { background-position: -200% 0; } }
</style>
