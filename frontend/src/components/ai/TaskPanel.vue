<template>
  <!-- 任务面板:左侧条件表单,右侧结构化结果 -->
  <div class="task" :style="{ '--accent': accent }">
    <div class="task-form">
      <div class="tf-head">
        <span class="tf-icon" :style="{ background: accentBg || 'rgba(37, 99, 235, 0.08)', color: accent }">
          <slot name="header-icon">{{ icon }}</slot>
        </span>
        <div class="tf-head-info">
          <div class="tf-title">{{ title }}</div>
          <div v-if="intro" class="tf-intro">{{ intro }}</div>
        </div>
      </div>
      <div class="tf-body">
        <slot name="form" :run="run" :running="running" />
        <div v-if="lastInputSummary" class="tf-last">上次条件: {{ lastInputSummary }}</div>
      </div>
    </div>

    <div class="task-result">
      <div class="tr-head">
        <div class="tr-title-group">
          <span class="tr-star">✨</span>
          <span class="tr-title">{{ running ? '正在生成结果…' : (resultTitle || (title ? `AI 为你推荐的${title.replace('推荐', '')}` : 'AI 推荐结果')) }}</span>
        </div>
        <div class="tr-actions">
          <button v-if="showRefresh" class="btn-refresh" :disabled="running" @click="$emit('refresh')">
            <RotateCcw :size="13" /> 换一批
          </button>
          <span v-if="!running && conversationId" class="tr-sub">可继续追问 · 已存入最近任务</span>
        </div>
      </div>

      <template v-if="running">
        <div class="steps-live">
          <div class="spinner"></div>
          <div>
            <div class="sl-title">{{ hints[hintIdx % hints.length] }}</div>
            <div class="sl-sub">AI 正在调用平台数据与模型分析，通常需 10~30 秒</div>
          </div>
        </div>
        <div class="skeleton">
          <div class="sk w60"></div>
          <div class="sk"></div>
          <div class="sk w80"></div>
          <div class="sk w40"></div>
          <div class="sk"></div>
        </div>
      </template>

      <template v-else-if="error">
        <div class="empty">
          <div class="empty-icon-wrap">⚠️</div>
          <div class="empty-title">这次生成未成功</div>
          <div class="empty-sub">{{ error }}</div>
          <el-button size="small" type="primary" plain style="margin-top: 8px;" @click="retry">再试一次</el-button>
        </div>
      </template>

      <template v-else-if="result || raw">
        <div class="trace" v-if="trace.length">
          <span class="trace-label">AI 调用了:</span>
          <span v-for="(t, i) in trace" :key="i" class="trace-chip" :class="{ fail: t.ok === false }">
            {{ t.label }}<b v-if="t.count > 1"> ×{{ t.count }}</b>
          </span>
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
          <img :src="emptyIllustration" class="empty-illustration" alt="暂无推荐结果" />
          <div class="empty-title">{{ emptyTitle }}</div>
          <div class="empty-sub">{{ emptySub }}</div>
        </div>
      </template>
    </div>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { RotateCcw } from 'lucide-vue-next'
import { aiChat } from '../../api'
import { parseJson } from '../../api/aiJson'
import emptyIllustration from '../../assets/empty-illustration.png'

const props = defineProps({
  skillKey: { type: String, required: true },
  icon: { type: String, default: '✦' },
  title: { type: String, required: true },
  resultTitle: { type: String, default: '' },
  intro: { type: String, default: '' },
  /** 主题色 */
  accent: { type: String, default: '#2563eb' },
  accentBg: { type: String, default: '' },
  emptyTitle: { type: String, default: '还没有推荐结果' },
  emptySub: { type: String, default: '请填写左侧的需求信息，AI 将为你推荐最合适的学习项目' },
  showRefresh: { type: Boolean, default: true },
  /** 结果里用于"继续深入"的字段名 */
  followUpField: { type: String, default: 'followUps' },
  /** 是否把结果当 JSON 解析(自定义技能是纯文本) */
  json: { type: Boolean, default: true },
  /** 从历史记录重新打开 */
  initial: { type: Object, default: null }
})
const emit = defineEmits(['done', 'refresh'])

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
.task {
  display: grid;
  grid-template-columns: 370px minmax(0, 1fr);
  gap: 18px;
  align-items: start;
}
@media (max-width: 1060px) {
  .task { grid-template-columns: 1fr; }
}

/* 左侧表单容器:干净白底 + 浅边框 */
.task-form {
  position: sticky;
  top: 16px;
  background: #ffffff;
  border-radius: 16px;
  border: 1px solid var(--border);
  overflow: hidden;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.03);
}

.tf-head {
  display: flex;
  gap: 10px;
  align-items: center;
  padding: 16px 20px;
  border-bottom: 1px solid #f1f5f9;
  background: #ffffff;
}

.tf-icon {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  display: grid;
  place-items: center;
  font-size: 16px;
  flex-shrink: 0;
}

.tf-head-info {
  flex: 1;
  min-width: 0;
}

.tf-title {
  font-weight: 700;
  font-size: 15px;
  color: #1e293b;
}

.tf-intro {
  font-size: 12px;
  color: #64748b;
  margin-top: 2px;
  line-height: 1.45;
}

.tf-body {
  padding: 18px 20px 20px;
}

.tf-last {
  margin-top: 12px;
  font-size: 12px;
  color: #94a3b8;
  border-top: 1px dashed var(--border);
  padding-top: 10px;
}

/* 右侧结果区:高品质白底容器 */
.task-result {
  min-height: 480px;
  background: #ffffff;
  border-radius: 16px;
  border: 1px solid var(--border);
  padding: 18px 22px 22px;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.03);
}

.tr-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
  padding-bottom: 14px;
  border-bottom: 1px solid #f1f5f9;
}

.tr-title-group {
  display: flex;
  align-items: center;
  gap: 8px;
}

.tr-star {
  color: #2563eb;
  font-size: 16px;
}

.tr-title {
  font-size: 15px;
  font-weight: 700;
  color: #1e293b;
}

.tr-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.btn-refresh {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  background: transparent;
  border: none;
  font-size: 13px;
  color: #2563eb;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 6px;
  transition: all 0.15s;
}
.btn-refresh:hover:not(:disabled) {
  background: #eff6ff;
}
.btn-refresh:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.tr-sub {
  font-size: 12px;
  color: #94a3b8;
}

/* 生成中动画与骨架屏 */
.steps-live {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 18px;
  padding: 12px 16px;
  background: #f8fafc;
  border-radius: 10px;
}

.spinner {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  border: 2.5px solid #dbeafe;
  border-top-color: #2563eb;
  animation: spin 0.9s linear infinite;
  flex-shrink: 0;
}
@keyframes spin { to { transform: rotate(360deg); } }

.sl-title {
  font-weight: 600;
  font-size: 13.5px;
  color: #1e293b;
}
.sl-sub {
  font-size: 12px;
  color: #64748b;
  margin-top: 2px;
}

.skeleton {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.sk {
  height: 14px;
  border-radius: 7px;
  background: linear-gradient(90deg, #f1f5f9, #e2e8f0, #f1f5f9);
  background-size: 200% 100%;
  animation: sk 1.2s infinite;
}
.sk.w60 { width: 60%; }
.sk.w80 { width: 80%; }
.sk.w40 { width: 40%; }
@keyframes sk {
  0% { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}

/* 空状态插画 */
.empty {
  min-height: 380px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  padding: 40px 20px;
}

.empty-illustration {
  width: 170px;
  height: auto;
  margin-bottom: 14px;
  filter: drop-shadow(0 4px 12px rgba(0, 0, 0, 0.04));
  user-select: none;
}

.empty-icon-wrap {
  font-size: 40px;
  margin-bottom: 8px;
}

.empty-title {
  font-weight: 700;
  font-size: 15px;
  color: #334155;
  margin-bottom: 6px;
}

.empty-sub {
  font-size: 13px;
  color: #94a3b8;
  max-width: 380px;
  line-height: 1.6;
}

/* 工具追踪与追问 */
.trace {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
  margin-bottom: 14px;
  font-size: 12px;
}
.trace-label { color: #94a3b8; }
.trace-chip {
  background: #f1f5f9;
  color: #475569;
  padding: 3px 10px;
  border-radius: 999px;
}
.trace-chip.fail {
  background: #fef2f2;
  color: #dc2626;
}
.trace-chip b { color: #2563eb; }
.trace-time {
  margin-left: auto;
  color: #94a3b8;
}

.follow {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin-top: 18px;
  padding-top: 14px;
  border-top: 1px dashed var(--border);
}
.follow-label { font-size: 12px; color: #94a3b8; }
.follow-chip {
  border: 1px solid #bfdbfe;
  background: #eff6ff;
  color: #1d4ed8;
  font-size: 12.5px;
  padding: 5px 12px;
  border-radius: 999px;
  cursor: pointer;
  transition: all 0.15s;
}
.follow-chip:hover {
  background: #dbeafe;
}
</style>
