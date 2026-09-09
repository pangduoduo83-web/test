<template>
  <!-- 成果评分弹窗:管理端与教师端共用,评分/AI 预评审接口由调用方注入 -->
  <el-dialog :model-value="modelValue" title="成果评分" width="600px" @update:model-value="(v) => $emit('update:modelValue', v)" @open="reset">
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
      <a v-if="submission.attachmentUrl" :href="submission.attachmentUrl" target="_blank" class="sub-attach">
        <img v-if="isImage(submission.attachmentUrl)" :src="submission.attachmentUrl" alt="附件" />
        <span v-else>查看附件</span>
      </a>

      <div class="skill-req-bar">
        <span class="muted">项目技能要求:</span>
        <template v-if="requirements.length">
          <span v-for="r in requirements" :key="r.name" class="badge badge-blue">{{ r.name }} ≥ {{ r.required }}</span>
          <span class="muted">评分后按要求自动校准学生对应维度的技能分</span>
        </template>
        <span v-else class="muted">未设置技能要求,可用 AI 预评审提取技能证据</span>
      </div>

      <div class="ai-review-bar">
        <el-button size="small" :loading="aiReviewing" @click="runAiReview">✨ AI 预评审(建议分 + 评语草稿 + 技能证据)</el-button>
        <span v-if="aiResult" class="ai-review-tip">建议 {{ aiResult.suggestedScore }} 分,已填入下方,可修改</span>
      </div>
      <div v-if="aiResult" class="ai-review-box">
        <div class="ai-review-summary">{{ aiResult.summary }}</div>
        <div v-if="aiResult.strengths?.length" class="ai-review-line good">✓ {{ aiResult.strengths.join(';') }}</div>
        <div v-if="aiResult.weaknesses?.length" class="ai-review-line bad">△ {{ aiResult.weaknesses.join(';') }}</div>
        <div class="ai-review-note">{{ aiResult.note }}</div>
      </div>

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
import { computed, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  submission: { type: Object, default: null },
  /** 所属项目(用于展示技能要求),可为空 */
  project: { type: Object, default: null },
  /** (submissionId) => Promise<aiResult> */
  aiReviewFn: { type: Function, required: true },
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
const evidenceRows = ref([])

const arr = (v) => {
  if (Array.isArray(v)) return v
  try { return JSON.parse(v || '[]') } catch (e) { return [] }
}
const requirements = computed(() => arr(props.project?.skillRequirements).filter((r) => r && r.name))
const fmt = (v) => (v || '').replace('T', ' ').slice(0, 16)
const isImage = (url) => /\.(png|jpe?g|gif|webp)(\?|$)/i.test(url || '')

const reset = () => {
  gradeForm.score = 80
  gradeForm.feedback = ''
  aiResult.value = null
  evidenceRows.value = []
}

const runAiReview = async () => {
  aiReviewing.value = true
  try {
    const res = await props.aiReviewFn(props.submission.id)
    aiResult.value = res
    gradeForm.score = res.suggestedScore
    if (res.feedbackDraft) gradeForm.feedback = res.feedbackDraft
    evidenceRows.value = (res.skillEvidence || []).map((ev) => ({ ...ev, accepted: true }))
    ElMessage.success(evidenceRows.value.length
      ? `AI 预评审完成,建议已填入,并提取到 ${evidenceRows.value.length} 条技能证据,请核对`
      : 'AI 预评审完成,建议已填入,可自行调整')
  } catch (e) { /* 已提示 */ } finally {
    aiReviewing.value = false
  }
}

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
