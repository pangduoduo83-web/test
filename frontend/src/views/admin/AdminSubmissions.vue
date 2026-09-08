<template>
  <div class="card">
    <div class="toolbar">
      <div class="filters">
        <el-select v-model="filters.status" placeholder="全部状态" clearable style="width:130px" @change="load">
          <el-option label="待评审" value="SUBMITTED" />
          <el-option label="已评分" value="GRADED" />
        </el-select>
        <el-select v-model="filters.projectId" placeholder="全部项目" clearable filterable
                   style="width:220px" @change="load">
          <el-option v-for="p in projects" :key="p.id" :label="p.title" :value="p.id" />
        </el-select>
        <el-select v-model="filters.userId" placeholder="全部学生" clearable filterable
                   style="width:180px" @change="load">
          <el-option v-for="u in students" :key="u.id" :label="u.name" :value="u.id" />
        </el-select>
        <el-button @click="load">刷新</el-button>
      </div>
      <span class="hint">共 {{ items.length }} 条成果</span>
    </div>

    <el-table :data="pageItems" stripe>
      <el-table-column label="学生" width="130">
        <template #default="{ row }">{{ row.userName || userName(row.userId) }}</template>
      </el-table-column>
      <el-table-column label="项目" min-width="180">
        <template #default="{ row }">{{ row.projectTitle || projectName(row.projectId) }}</template>
      </el-table-column>
      <el-table-column label="成果说明" min-width="240">
        <template #default="{ row }">
          <el-tooltip :content="row.content" placement="top">
            <span class="ellipsis">{{ row.content }}</span>
          </el-tooltip>
        </template>
      </el-table-column>
      <el-table-column label="考核项" width="110">
        <template #default="{ row }">
          <span v-if="row.assessmentName" class="badge badge-purple">{{ row.assessmentName }}</span>
          <span v-else class="sub-text">整体成果</span>
        </template>
      </el-table-column>
      <el-table-column label="附件" width="80">
        <template #default="{ row }">
          <el-link v-if="row.attachmentUrl" :href="row.attachmentUrl" target="_blank" type="primary">查看</el-link>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <span class="badge" :class="row.status === 'GRADED' ? 'badge-green' : 'badge-yellow'">
            {{ row.status === 'GRADED' ? '已评分' : '待评审' }}
          </span>
        </template>
      </el-table-column>
      <el-table-column label="评分" width="80">
        <template #default="{ row }">{{ row.score ?? '-' }}</template>
      </el-table-column>
      <el-table-column prop="feedback" label="评语" min-width="160">
        <template #default="{ row }">{{ row.feedback || '-' }}</template>
      </el-table-column>
      <el-table-column label="提交时间" width="145">
        <template #default="{ row }">{{ formatTime(row.submittedAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="90" fixed="right">
        <template #default="{ row }">
          <el-button v-if="row.status === 'SUBMITTED'" size="small" type="primary" @click="openGrade(row)">
            评分
          </el-button>
          <span v-else class="sub-text">{{ row.graderName || '已评' }}</span>
        </template>
      </el-table-column>
    </el-table>

    <div class="pager">
      <el-pagination v-model:current-page="page" :page-size="pageSize" :total="items.length"
                     layout="total, prev, pager, next" background />
    </div>

    <el-dialog v-model="gradeVisible" title="成果评分" width="560px">
      <el-descriptions :column="1" border class="submission-info">
        <el-descriptions-item label="学生">{{ grading?.userName }}</el-descriptions-item>
        <el-descriptions-item label="项目">{{ grading?.projectTitle }}</el-descriptions-item>
        <el-descriptions-item label="成果">{{ grading?.content }}</el-descriptions-item>
      </el-descriptions>
      <div class="skill-req-bar">
        <span class="skill-req-label">项目技能要求:</span>
        <template v-if="gradingRequirements.length">
          <span v-for="r in gradingRequirements" :key="r.name" class="badge badge-blue">
            {{ r.name }} ≥ {{ r.required }}
          </span>
          <span class="sub-text">评分后将按要求自动校准学生对应维度的技能分</span>
        </template>
        <span v-else class="sub-text">该项目未设置技能要求,评分不会自动影响技能画像;可用 AI 预评审提取技能证据</span>
      </div>
      <div class="ai-review-bar">
        <el-button size="small" :loading="aiReviewing" @click="runAiReview">
          ✨ AI 预评审(建议分 + 评语草稿 + 技能证据)
        </el-button>
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
        </el-form-item>
        <el-form-item label="评语">
          <el-input v-model="gradeForm.feedback" type="textarea" :rows="4" placeholder="填写改进建议或评价" />
        </el-form-item>
        <el-form-item v-if="evidenceRows.length" label="技能证据">
          <div class="evidence-list">
            <div v-for="ev in evidenceRows" :key="ev.name" class="evidence-row">
              <el-checkbox v-model="ev.accepted">{{ ev.name }}</el-checkbox>
              <el-input-number v-model="ev.level" :min="0" :max="100" size="small" :disabled="!ev.accepted" />
              <span class="evidence-basis" :title="ev.basis">{{ ev.basis }}</span>
            </div>
            <div class="evidence-tip">
              勾选并确认后,这些维度水平会与项目要求一起计入学生技能画像;不勾选则忽略 AI 判断。
            </div>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="gradeVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitGrade">提交评分</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  adminAiReview, adminGradeSubmission, adminListProjects, adminListSubmissions, adminListUsers
} from '../../api'

const items = ref([])
const users = ref([])
const projects = ref([])
const filters = reactive({ status: 'SUBMITTED', projectId: null, userId: null })
const gradeVisible = ref(false)
const grading = ref(null)
const gradeForm = reactive({ score: 80, feedback: '' })
const saving = ref(false)
const aiReviewing = ref(false)
const aiResult = ref(null)
const evidenceRows = ref([])

const arr = (v) => {
  if (Array.isArray(v)) return v
  try { return JSON.parse(v || '[]') } catch (e) { return [] }
}
const gradingRequirements = computed(() => {
  const p = projects.value.find((x) => x.id === grading.value?.projectId)
  return arr(p?.skillRequirements).filter((r) => r && r.name)
})

const runAiReview = async () => {
  aiReviewing.value = true
  try {
    const res = await adminAiReview(grading.value.id)
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
const page = ref(1)
const pageSize = 10

const students = computed(() => users.value.filter((u) => u.role === 'STUDENT'))
const pageItems = computed(() =>
  items.value.slice((page.value - 1) * pageSize, page.value * pageSize))
const userName = (id) => users.value.find((u) => u.id === id)?.name || `用户 #${id}`
const projectName = (id) => projects.value.find((p) => p.id === id)?.title || `项目 #${id}`
const formatTime = (v) => (v || '').replace('T', ' ').slice(0, 16)

const load = async () => {
  items.value = await adminListSubmissions({
    status: filters.status || undefined,
    projectId: filters.projectId || undefined,
    userId: filters.userId || undefined
  })
  page.value = 1
}

const openGrade = (row) => {
  grading.value = row
  gradeForm.score = 80
  gradeForm.feedback = ''
  aiResult.value = null
  evidenceRows.value = []
  gradeVisible.value = true
}

const submitGrade = async () => {
  const accepted = evidenceRows.value.filter((ev) => ev.accepted)
  await ElMessageBox.confirm(
    `确认给该成果评 ${gradeForm.score} 分?评分后不可重复操作。`
      + (accepted.length ? `同时确认 ${accepted.length} 条技能证据计入学生画像。` : ''),
    '提交评分', { type: 'warning' })
  saving.value = true
  try {
    await adminGradeSubmission(grading.value.id, {
      score: gradeForm.score,
      feedback: gradeForm.feedback,
      skillEvidence: accepted.map((ev) => ({ name: ev.name, level: ev.level }))
    })
    ElMessage.success('评分完成,学生已收到通知,技能画像已同步更新')
    gradeVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  const [userItems, projectItems] = await Promise.all([adminListUsers(), adminListProjects()])
  users.value = userItems
  projects.value = projectItems
  await load()
})
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; align-items: center; gap: 12px; margin-bottom: 16px; }
.filters { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.hint, .sub-text { color: var(--text-secondary); font-size: 12px; }
.ellipsis { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.pager { display: flex; justify-content: flex-end; margin-top: 14px; }
.submission-info { margin-bottom: 20px; }
.ai-review-bar { display: flex; align-items: center; gap: 12px; margin-bottom: 12px; }
.ai-review-tip { font-size: 12px; color: #9333ea; }
.ai-review-box {
  background: linear-gradient(to right, #faf5ff, #eff6ff);
  border: 1px solid #e9d5ff; border-radius: 10px;
  padding: 12px 14px; margin-bottom: 14px; font-size: 13px;
  display: flex; flex-direction: column; gap: 6px;
}
.ai-review-summary { color: #6b21a8; }
.ai-review-line.good { color: #16a34a; }
.ai-review-line.bad { color: #ca8a04; }
.ai-review-note { font-size: 11px; color: #9ca3af; }
.skill-req-bar { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; margin-bottom: 12px; font-size: 13px; }
.skill-req-label { color: var(--text-secondary); }
.evidence-list { display: flex; flex-direction: column; gap: 8px; width: 100%; }
.evidence-row { display: flex; align-items: center; gap: 10px; }
.evidence-row :deep(.el-checkbox) { width: 96px; margin-right: 0; }
.evidence-basis {
  flex: 1; min-width: 0; font-size: 12px; color: var(--text-secondary);
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.evidence-tip { font-size: 11px; color: #9ca3af; line-height: 1.5; }
</style>
