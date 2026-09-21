<template>
  <!-- 成果评分弹窗:管理端与教师端共用,评分/AI 预评审接口由调用方注入 -->
  <el-dialog :model-value="modelValue" title="成果评分" width="min(900px, calc(100vw - 24px))" class="review-dialog" @update:model-value="(v) => $emit('update:modelValue', v)" @open="reset">
    <template v-if="submission">
      <div class="sub-head">
        <div class="sub-who">
          <b>{{ submission.userName }}</b>
          <span class="muted">· {{ submission.projectTitle }}</span>
          <span v-if="submission.assessmentName" class="badge badge-purple">{{ submission.assessmentName }}</span>
          <span v-else class="badge badge-gray">整体成果</span>
        </div>
        <span class="muted">提交于 {{ fmt(submission.submittedAt) }}</span>
      </div>
      <div class="sub-content">{{ submission.content }}</div>
      <SubmissionAttachments :submission="submission" />

      <div class="skill-req-bar">
        <span class="muted">项目技能要求:</span>
        <template v-if="requirements.length">
          <span v-for="r in requirements" :key="r.name" class="badge badge-blue">{{ r.name }} ≥ {{ r.required }}</span>
          <span class="muted">评分后按要求自动校准学生对应维度的技能分</span>
        </template>
        <span v-else class="muted">未设置技能要求,可用 AI 预评审提取技能证据</span>
      </div>

      <div class="ai-review-bar">
        <el-button type="primary" plain :loading="aiReviewing" @click="runAiReview(!!aiResult)"><Sparkles v-if="!aiReviewing" :size="15" style="margin-right:6px" />{{ aiResult ? '重新评审' : 'AI 分析成果材料' }}</el-button>
        <el-button v-if="aiResult" :disabled="aiReviewing || reviewJob?.stale" @click="applySuggestion">填入AI建议</el-button>
        <span v-if="aiResult" class="ai-review-tip">建议 {{ aiResult.suggestedScore }} 分</span>
      </div>
      <div v-if="reviewJob && reviewJob.status !== 'IDLE'" class="ai-review-box">
        <div class="review-status-heading"><span class="status-dot" :class="{ running: aiReviewing, failed: reviewJob.status === 'FAILED' }"></span><b>{{ reviewJob.message }}</b></div>
        <el-progress v-if="aiReviewing" :percentage="reviewJob.progress || 0" />
        <div v-if="aiReviewing" class="muted">可以关闭页面，后台会继续处理；重新打开即可查看进度。</div>
        <el-alert v-if="reviewJob.stale" title="材料、评分标准或模型配置已变更，请重新评审。" type="warning" :closable="false" />
        <div v-for="material in reviewJob.materials || []" :key="material.id" class="material-status">
          <div class="material-status-head"><b>{{ material.name || '等待解析' }}</b><span class="badge" :class="material.status === 'DONE' ? 'badge-green' : material.status === 'FAILED' ? 'badge-red' : 'badge-yellow'">{{ { DONE: '分析完成', PARTIAL: '部分分析', FAILED: '未能分析', PARSING: '解析中' }[material.status] || '等待解析' }}</span><el-button v-if="['FAILED', 'PARTIAL'].includes(material.status)" text size="small" :disabled="aiReviewing" @click="runAiReview(true, material.url)">重试</el-button></div>
          <div v-for="(warning, i) in material.warnings" :key="i" class="ai-review-line bad">{{ warning }}</div>
        </div>
      </div>
      <div v-if="aiResult" class="ai-review-box">
        <div class="result-heading"><span><Sparkles :size="17" />AI 评审建议</span><b>{{ aiResult.suggestedScore }}<small> / 100 分</small></b></div>
        <div class="ai-review-summary">{{ aiResult.summary }}</div>
        <div v-for="row in aiResult.criteria || []" :key="row.name" class="criterion">
          <div class="criterion-head"><b>{{ row.name }}</b><el-tag v-if="row.needsConfirmation" type="warning" size="small">需要核实</el-tag><span>{{ row.score }} <small>/ {{ row.maxScore }} 分</small></span></div>
          <el-progress :percentage="row.maxScore ? Math.round(row.score / row.maxScore * 100) : 0" :show-text="false" :stroke-width="4" color="#818cf8" />
          <div class="criterion-reason">{{ row.reason }}</div>
          <details v-for="source in row.evidence || []" :key="source.id">
            <summary>{{ source.name || '成果说明' }} · {{ source.location }}</summary>
            <p class="source-text">{{ source.text }}</p>
            <a v-if="source.url" :href="sourceLink(source)" target="_blank" rel="noopener">打开原件{{ source.page ? '对应页' : source.seconds != null ? '对应时间' : '' }}</a>
          </details>
        </div>
        <div v-if="aiResult.strengths?.length" class="ai-review-line good">✓ {{ aiResult.strengths.join('；') }}</div>
        <div v-if="aiResult.weaknesses?.length" class="ai-review-line bad">△ {{ aiResult.weaknesses.join('；') }}</div>
        <div v-for="check in aiResult.pendingChecks || []" :key="check" class="ai-review-line bad">待核实：{{ check }}</div>
        <div class="ai-review-note">{{ aiResult.note }}</div>
      </div>

      <div class="grade-section-title"><ClipboardCheck :size="17" />教师确认<span>核对材料与建议后，填写最终评分</span></div>
      <el-form :model="gradeForm" label-width="70px">
        <el-form-item label="分数">
          <el-input-number v-model="gradeForm.score" :min="0" :max="100" />
          <span class="muted" style="margin-left:10px">≥60 分视为通过</span>
        </el-form-item>
        <el-form-item label="评语">
          <el-input v-model="gradeForm.feedback" type="textarea" :rows="4" placeholder="填写改进建议或评价,学生会收到通知" />
        </el-form-item>
        <el-form-item v-if="evidenceRows.length" label="技能证据">
          <div class="evidence-list">
            <div v-for="ev in evidenceRows" :key="ev.name" class="evidence-row">
              <el-checkbox v-model="ev.accepted">{{ ev.name }}</el-checkbox>
              <el-input-number v-model="ev.level" :min="0" :max="100" size="small" :disabled="!ev.accepted" />
              <span class="evidence-basis" :title="ev.basis">{{ ev.basis }}</span>
            </div>
            <div class="evidence-tip">勾选并确认后,这些维度水平会与项目要求一起计入学生技能画像;不勾选则忽略 AI 判断。</div>
          </div>
        </el-form-item>
      </el-form>
    </template>
    <template #footer>
      <el-button @click="$emit('update:modelValue', false)">取消</el-button>
      <el-button v-if="returnFn" type="warning" plain :loading="saving" @click="returnForRevision">退回修改</el-button>
      <el-button type="primary" :loading="saving" @click="submitGrade">提交评分</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, reactive, ref, watch, onBeforeUnmount } from 'vue'
import { Sparkles, ClipboardCheck } from 'lucide-vue-next'
import SubmissionAttachments from './SubmissionAttachments.vue'
import { ElMessage, ElMessageBox } from 'element-plus'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  submission: { type: Object, default: null },
  /** 所属项目(用于展示技能要求),可为空 */
  project: { type: Object, default: null },
  /** (submissionId) => Promise<aiResult> */
  aiReviewFn: { type: Function, required: true },
  aiStatusFn: { type: Function, required: true },
  /** (submissionId, body) => Promise */
  gradeFn: { type: Function, required: true },
  /** (submissionId, feedback) => Promise;不传则不显示「退回修改」 */
  returnFn: { type: Function, default: null }
})
const emit = defineEmits(['update:modelValue', 'graded'])

const gradeForm = reactive({ score: 80, feedback: '' })
const saving = ref(false)
const aiReviewing = ref(false)
const aiResult = ref(null)
const reviewJob = ref(null)
let pollTimer
let generation = 0
const evidenceRows = ref([])

const arr = (v) => {
  if (Array.isArray(v)) return v
  try { return JSON.parse(v || '[]') } catch (e) { return [] }
}
const requirements = computed(() => arr(props.project?.skillRequirements).filter((r) => r && r.name))
const fmt = (v) => (v || '').replace('T', ' ').slice(0, 16)
const isImage = (url) => /\.(png|jpe?g|gif|webp)(\?|$)/i.test(url || '')

const stopPolling = () => { generation++; clearTimeout(pollTimer); aiReviewing.value = false }
const active = state => ['QUEUED', 'PARSING', 'REVIEWING'].includes(state)
const sourceLink = source => {
  if (!/^\/uploads\/\d{6}\/[\w-]+\.[a-z0-9]+$/i.test(source.url || '')) return undefined
  return source.url + (source.page ? `#page=${source.page}` : source.seconds != null ? `#t=${source.seconds}` : '')
}
const applySuggestion = () => {
  if (!aiResult.value || reviewJob.value?.stale) return
  gradeForm.score = aiResult.value.suggestedScore
  gradeForm.feedback = aiResult.value.feedbackDraft || ''
  evidenceRows.value = (aiResult.value.skillEvidence || []).map(ev => ({ ...ev, accepted: true }))
  ElMessage.success('已填入AI建议，请核对后提交评分')
}
const receive = (job, token, id) => {
  if (token !== generation || !props.modelValue || props.submission?.id !== id) return
  reviewJob.value = job
  aiReviewing.value = active(job.status)
  if (job.result) aiResult.value = job.result
  if (aiReviewing.value) pollTimer = setTimeout(() => poll(token, id), 2500)
}
const poll = async (token, id) => {
  try { receive(await props.aiStatusFn(id), token, id) }
  catch { if (token === generation) { aiReviewing.value = false; reviewJob.value = { ...reviewJob.value, message: '进度获取失败，可重新打开查看；后台任务继续运行。' } } }
}
const reset = () => {
  stopPolling()
  gradeForm.score = 80; gradeForm.feedback = ''
  aiResult.value = null; reviewJob.value = null; evidenceRows.value = []
  if (props.submission) poll(generation, props.submission.id)
}
const runAiReview = async (force = false, retryAttachment) => {
  stopPolling()
  const token = generation, id = props.submission.id
  aiReviewing.value = true
  try {
    const job = await props.aiReviewFn(id, { force, retryAttachment })
    if (token === generation && !job.result) aiResult.value = null
    receive(job, token, id)
  } catch { if (token === generation) aiReviewing.value = false }
}
watch(() => props.modelValue, visible => { if (!visible) stopPolling() })
watch(() => props.submission?.id, () => { if (props.modelValue) reset() })
onBeforeUnmount(stopPolling)

const returnForRevision = async () => {
  if (!gradeForm.feedback.trim()) { ElMessage.warning('退回时请在评语里写明需要修改的地方'); return }
  try {
    await ElMessageBox.confirm('退回后不打分,学生会收到你的修改意见并可以重新提交。', '退回修改', { type: 'warning', confirmButtonText: '退回' })
  } catch (e) { return }
  saving.value = true
  try {
    await props.returnFn(props.submission.id, gradeForm.feedback.trim())
    ElMessage.success('已退回,学生已收到通知')
    emit('update:modelValue', false)
    emit('graded')
  } finally {
    saving.value = false
  }
}

const submitGrade = async () => {
  const accepted = evidenceRows.value.filter((ev) => ev.accepted)
  try {
    await ElMessageBox.confirm(
      `确认给该成果评 ${gradeForm.score} 分?评分后不可重复操作。` + (accepted.length ? `同时确认 ${accepted.length} 条技能证据计入学生画像。` : ''),
      '提交评分', { type: 'warning' })
  } catch (e) { return }
  saving.value = true
  try {
    await props.gradeFn(props.submission.id, {
      score: gradeForm.score,
      feedback: gradeForm.feedback,
      skillEvidence: accepted.map((ev) => ({ name: ev.name, level: ev.level }))
    })
    ElMessage.success('评分完成,学生已收到通知,技能画像已同步更新')
    emit('update:modelValue', false)
    emit('graded')
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
:global(.review-dialog.el-dialog) { --el-dialog-margin-top: 5vh; max-height: 90vh; display: flex; flex-direction: column; border-radius: 16px; }
:global(.review-dialog .el-dialog__header) { flex-shrink: 0; padding-bottom: 18px; }
:global(.review-dialog .el-dialog__body) { overflow-y: auto; min-height: 0; overscroll-behavior: contain; padding-right: 4px; }
:global(.review-dialog .el-dialog__footer) { flex-shrink: 0; padding-top: 16px; border-top: 1px solid #e5e7eb; margin-top: 12px; }
.review-status-heading { display: flex; align-items: center; gap: 9px; }.status-dot { width: 8px; height: 8px; border-radius: 50%; background: #22c55e; }.status-dot.running { background: var(--brand-blue); box-shadow: 0 0 0 4px #dbeafe; }.status-dot.failed { background: #ef4444; }
.material-status { background: rgba(255,255,255,.8); border: 1px solid #e5e7eb; border-radius: 10px; padding: 10px 12px !important; }.material-status-head { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }.material-status-head>b { flex: 1; min-width: 120px; overflow-wrap: anywhere; }.material-status .ai-review-line { margin-top: 6px; font-size: 12px; line-height: 1.6; }
.result-heading { display: flex; justify-content: space-between; gap: 12px; align-items: center; color: #6b21a8; }.result-heading>span { display: flex; gap: 7px; align-items: center; font-weight: 600; }.result-heading>b { font-size: 28px; font-variant-numeric: tabular-nums; }.result-heading small { font-size: 12px; font-weight: 400; color: var(--text-secondary); }
.criterion-head { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; margin-bottom: 8px; }.criterion-head>span:last-child { margin-left: auto; color: var(--brand-blue); font-weight: 600; font-size: 16px; }.criterion-head small { color: var(--text-secondary); font-weight: 400; font-size: 12px; }.criterion-reason { margin-top: 9px; color: #4b5563; }.criterion details { border-radius: 8px; background: #fff; padding: 8px 10px; }.criterion summary { font-size: 12px; }.grade-section-title { display: flex; align-items: center; gap: 8px; font-weight: 600; margin: 22px 0 16px; }.grade-section-title span { font-size: 12px; color: var(--text-secondary); font-weight: 400; margin-left: auto; }
@media(max-width:600px) { .grade-section-title { flex-wrap: wrap; }.grade-section-title span { width: 100%; margin-left: 0; }.evidence-row { flex-wrap: wrap; }.evidence-basis { flex-basis: 100% !important; white-space: normal !important; }.sub-head { align-items: flex-start; } }
.criterion { border-top: 1px solid #ddd6fe; padding: 10px 0; line-height: 1.7; }
.criterion b { margin-right: 8px; }.criterion details { margin-top: 6px; }
.criterion summary { cursor: pointer; color: #2563eb; }.source-text { white-space: pre-wrap; max-height: 180px; overflow: auto; }
.material-status { padding: 6px 0; }.ai-review-bar { flex-wrap: wrap; }

.muted { color: var(--text-secondary); font-size: 12px; }
.sub-head { display: flex; justify-content: space-between; align-items: center; gap: 12px; margin-bottom: 10px; flex-wrap: wrap; }
.sub-who { display: flex; align-items: center; gap: 8px; font-size: 14px; }
.sub-content { background: #f9fafb; border-radius: 10px; padding: 12px 14px; font-size: 13px; line-height: 1.7; white-space: pre-wrap; max-height: 220px; overflow: auto; }
.sub-attach { display: inline-block; margin-top: 10px; font-size: 13px; color: var(--brand-blue); }
.sub-attach img { max-width: 100%; max-height: 240px; border-radius: 10px; display: block; }
.skill-req-bar { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; margin: 14px 0 12px; font-size: 13px; }
.ai-review-bar { display: flex; align-items: center; gap: 12px; margin-bottom: 12px; }
.ai-review-tip { font-size: 12px; color: #9333ea; }
.ai-review-box {
  background: linear-gradient(to right, #faf5ff, #eff6ff); border: 1px solid #e9d5ff; border-radius: 10px;
  padding: 12px 14px; margin-bottom: 14px; font-size: 13px; display: flex; flex-direction: column; gap: 6px;
}
.ai-review-summary { color: #6b21a8; }
.ai-review-line.good { color: #16a34a; }
.ai-review-line.bad { color: #ca8a04; }
.ai-review-note { font-size: 11px; color: #9ca3af; }
.evidence-list { display: flex; flex-direction: column; gap: 8px; width: 100%; }
.evidence-row { display: flex; align-items: center; gap: 10px; }
.evidence-row :deep(.el-checkbox) { width: 96px; margin-right: 0; }
.evidence-basis { flex: 1; min-width: 0; font-size: 12px; color: var(--text-secondary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.evidence-tip { font-size: 11px; color: #9ca3af; line-height: 1.5; }
</style>
