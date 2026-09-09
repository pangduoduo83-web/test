<template>
  <!-- 任务面板:左侧条件表单,右侧结构化结果;不是聊天框 -->
  <div class="task" :style="{ '--accent': accent }">
    <div class="task-form">
      <div class="tf-head">
        <span class="tf-icon">{{ icon }}</span>
        <div>
          <div class="tf-title">{{ title }}</div>
          <div class="tf-intro">{{ intro }}</div>
        </div>
      </div>
      <div class="tf-body">
        <slot name="form" :run="run" :running="running" />
        <div v-if="lastInputSummary" class="tf-last">上次条件:{{ lastInputSummary }}</div>
      </div>
    </div>

    <div class="task-result">
      <div class="tr-head">
        <span class="tr-title">{{ running ? '正在生成' : (result || raw) ? '结果' : '结果会出现在这里' }}</span>
        <span v-if="!running && conversationId" class="tr-sub">可继续追问 · 已存入最近任务</span>
      </div>
      <template v-if="running">
        <div class="steps-live">
          <div class="spinner"></div>
          <div>
            <div class="sl-title">{{ hints[hintIdx % hints.length] }}</div>
            <div class="sl-sub">AI 正在调用平台数据,通常 10~30 秒</div>
          </div>
        </div>
        <div class="skeleton"><div class="sk w60"></div><div class="sk"></div><div class="sk w80"></div><div class="sk w40"></div><div class="sk"></div></div>
      </template>
      <template v-else-if="error">
        <div class="empty">
          <div class="empty-icon">⚠️</div>
          <div class="empty-title">这次没有成功</div>
          <div class="empty-sub">{{ error }}</div>
          <el-button size="small" @click="retry">再试一次</el-button>
        </div>
      </template>
      <template v-else-if="result || raw">
        <div class="trace" v-if="trace.length">
          <span class="trace-label">AI 做了</span>
          <span v-for="(t, i) in trace" :key="i" class="trace-chip" :class="{ fail: t.ok === false }">{{ t.label }}<b v-if="t.count > 1"> ×{{ t.count }}</b></span>
          <span class="trace-time">{{ elapsed }}s</span>
        </div>
        <slot name="result" :result="result" :raw="raw" :ask="ask" />
        <div v-if="followUps.length" class="follow">
          <span class="follow-label">继续深入</span>
          <button v-for="q in followUps" :key="q" class="follow-chip" @click="ask(q)">{{ q }}</button>
        </div>
      </template>
      <template v-else>
        <div class="empty">
          <div class="empty-icon">{{ icon }}</div>
          <div class="empty-title">{{ emptyTitle }}</div>
          <div class="empty-sub">{{ emptySub }}</div>
        </div>
      </template>
    </div>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { aiChat } from '../../api'
import { parseJson } from '../../api/aiJson'

const props = defineProps({
  skillKey: { type: String, required: true },
  icon: { type: String, default: '✦' },
  title: { type: String, required: true },
  intro: { type: String, default: '' },
  /** 主题色,用于表单头部与结果标题 */
  accent: { type: String, default: '#6366f1' },
  emptyTitle: { type: String, default: '填好左侧条件,点开始' },
  emptySub: { type: String, default: '结果会以卡片形式出现在这里' },
  /** 结果里用于"继续深入"的字段名 */
  followUpField: { type: String, default: 'followUps' },
  /** 是否把结果当 JSON 解析(自定义技能是纯文本) */
  json: { type: Boolean, default: true },
  /** 从历史记录重新打开 */
  initial: { type: Object, default: null }
})
const emit = defineEmits(['done'])

const running = ref(false)
const error = ref('')
const result = ref(null)
const raw = ref('')
const trace = ref([])
const elapsed = ref(0)
const conversationId = ref(null)
const lastInput = ref(null)
const hintIdx = ref(0)
const hints = ['正在理解你的需求…', '正在读取平台数据…', '正在检索项目与设备…', '正在整理结构化结果…']
const TOOL_LABELS = {
  'project.search': '检索项目库', 'project.get': '读取项目详情', 'skill.my_scores': '读取你的技能画像', 'enrollment.my_list': '查看你的报名',
  'equipment.search': '检索设备库', 'equipment.get': '查看设备详情', 'submission.my_list': '查看你的成果'
}
const followUps = computed(() => (result.value && Array.isArray(result.value[props.followUpField]) ? result.value[props.followUpField].slice(0, 3) : []))
const lastInputSummary = computed(() => {
  if (!lastInput.value) return ''
  if (typeof lastInput.value === 'string') return lastInput.value.slice(0, 60)
  return Object.values(lastInput.value).filter((v) => v !== '' && v != null).map(String).join(' · ').slice(0, 80)
})

let hintTimer = null
const summarizeTrace = (tools) => {
  const m = new Map()
  for (const t of tools || []) {
    const label = TOOL_LABELS[t.name] || t.name
    const e = m.get(label) || { label, count: 0, ok: true }
    e.count++
    if (t.ok === false) e.ok = false
    m.set(label, e)
  }
  return [...m.values()]
}

const execute = async (body) => {
  running.value = true
  error.value = ''
  hintIdx.value = 0
  hintTimer = setInterval(() => { hintIdx.value++ }, 4000)
  const t0 = Date.now()
  try {
    const r = await aiChat(body)
    elapsed.value = Math.round((Date.now() - t0) / 1000)
    trace.value = summarizeTrace(r.tools)
    conversationId.value = r.conversationId || conversationId.value
    raw.value = r.content || ''
    if (props.json) {
      try { result.value = parseJson(r.content) } catch (e) { result.value = null }
    } else {
      result.value = null
    }
    emit('done', { conversationId: conversationId.value, result: result.value, raw: raw.value })
  } catch (e) {
    error.value = e?.response?.data?.message || e?.message || 'AI 暂不可用'
  } finally {
    clearInterval(hintTimer)
    running.value = false
  }
}
/** 新任务:重置会话,用表单输入运行;title 用作历史记录标题 */
const run = (input, title) => {
  lastInput.value = input
  conversationId.value = null
  result.value = null
  raw.value = ''
  const t = title || (typeof input === 'string' ? input : Object.values(input).find((v) => typeof v === 'string' && v.trim() && v !== '无') || '')
  return execute({ skillKey: props.skillKey, input, title: String(t).slice(0, 40) })
}
/** 追问:沿用会话上下文 */
const ask = (text) => execute({ skillKey: props.skillKey, conversationId: conversationId.value, input: text })
const retry = () => (conversationId.value ? ask(typeof lastInput.value === 'string' ? lastInput.value : '请重新给出结果') : run(lastInput.value))

const applyInitial = (init) => {
  if (!init) return
  conversationId.value = init.conversationId || null
  raw.value = init.raw || ''
  result.value = init.result || null
  trace.value = []
  lastInput.value = init.input || null
}
onMounted(() => applyInitial(props.initial))
watch(() => props.initial, (v) => applyInitial(v))
onBeforeUnmount(() => clearInterval(hintTimer))
defineExpose({ run, ask })
</script>

<style scoped>
.task { display: grid; grid-template-columns: 360px minmax(0, 1fr); gap: 18px; align-items: start; }
@media (max-width: 1000px) { .task { grid-template-columns: 1fr; } }
.task-form { position: sticky; top: 16px; background: #fff; border-radius: 18px; border: 1px solid var(--border); overflow: hidden; box-shadow: var(--shadow-card); }
.tf-head { display: flex; gap: 12px; align-items: flex-start; padding: 18px 20px 16px; color: #fff; background: linear-gradient(135deg, var(--accent), color-mix(in srgb, var(--accent) 70%, #1e1b4b)); }
.tf-icon { width: 46px; height: 46px; border-radius: 13px; background: rgba(255, 255, 255, .2); display: grid; place-items: center; font-size: 24px; flex-shrink: 0; }
.tf-title { font-weight: 800; font-size: 17px; }
.tf-intro { font-size: 12.5px; color: rgba(255, 255, 255, .88); margin-top: 4px; line-height: 1.6; }
.tf-body { padding: 18px 20px 16px; }
.tf-last { margin-top: 12px; font-size: 12px; color: #9ca3af; border-top: 1px dashed var(--border); padding-top: 10px; }
.task-result { min-height: 460px; background: #fff; border-radius: 18px; border: 1px solid var(--border); padding: 18px 22px 22px; box-shadow: var(--shadow-card); }
.tr-head { display: flex; align-items: baseline; gap: 10px; margin-bottom: 14px; padding-bottom: 12px; border-bottom: 1px solid var(--border); }
.tr-title { font-size: 15px; font-weight: 800; display: flex; align-items: center; gap: 8px; }
.tr-title::before { content: ''; width: 4px; height: 16px; border-radius: 2px; background: var(--accent); }
.tr-sub { font-size: 12px; color: #9ca3af; margin-left: auto; }
.steps-live { display: flex; align-items: center; gap: 14px; margin-bottom: 18px; }
.spinner { width: 34px; height: 34px; border-radius: 50%; border: 3px solid #e0e7ff; border-top-color: #4f46e5; animation: spin 1s linear infinite; flex-shrink: 0; }
@keyframes spin { to { transform: rotate(360deg); } }
.sl-title { font-weight: 700; font-size: 14px; }
.sl-sub { font-size: 12px; color: var(--text-secondary); margin-top: 2px; }
.skeleton { display: flex; flex-direction: column; gap: 12px; }
.sk { height: 14px; border-radius: 7px; background: linear-gradient(90deg, #f3f4f6, #e5e7eb, #f3f4f6); background-size: 200% 100%; animation: sk 1.2s infinite; }
.sk.w60 { width: 60%; } .sk.w80 { width: 80%; } .sk.w40 { width: 40%; }
@keyframes sk { 0% { background-position: 200% 0; } 100% { background-position: -200% 0; } }
.empty { min-height: 360px; display: flex; flex-direction: column; align-items: center; justify-content: center; text-align: center; gap: 8px; background: radial-gradient(circle at 50% 30%, color-mix(in srgb, var(--accent) 10%, #fff), #fff 70%); border-radius: 14px; }
.empty-icon { font-size: 54px; filter: drop-shadow(0 8px 16px rgba(15, 23, 42, .12)); }
.empty-title { font-weight: 700; font-size: 15px; }
.empty-sub { font-size: 13px; color: var(--text-secondary); max-width: 360px; line-height: 1.6; }
.trace { display: flex; align-items: center; gap: 6px; flex-wrap: wrap; margin-bottom: 14px; font-size: 12px; }
.trace-label { color: #9ca3af; }
.trace-chip { background: #f3f4f6; color: #374151; padding: 3px 10px; border-radius: 999px; }
.trace-chip.fail { background: #fef2f2; color: #dc2626; }
.trace-chip b { color: #6366f1; }
.trace-time { margin-left: auto; color: #9ca3af; }
.follow { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; margin-top: 18px; padding-top: 14px; border-top: 1px dashed var(--border); }
.follow-label { font-size: 12px; color: #9ca3af; }
.follow-chip { border: 1px solid #c7d2fe; background: #eef2ff; color: #3730a3; font-size: 13px; padding: 6px 12px; border-radius: 999px; cursor: pointer; }
.follow-chip:hover { background: #e0e7ff; }
</style>
