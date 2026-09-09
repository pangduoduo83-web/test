<template>
  <div>
    <div class="stat-grid">
      <div class="ref-stat-card">
        <div class="ref-stat-icon" style="background:linear-gradient(135deg,#facc15,#f59e0b)"><Clock :size="22" color="#fff" /></div>
        <div><div class="ref-stat-value">{{ pendingCount }}</div><div class="ref-stat-label">待评审</div></div>
      </div>
      <div class="ref-stat-card">
        <div class="ref-stat-icon" style="background:linear-gradient(135deg,#4ade80,#16a34a)"><CircleCheckBig :size="22" color="#fff" /></div>
        <div><div class="ref-stat-value">{{ gradedCount }}</div><div class="ref-stat-label">已评分</div></div>
      </div>
      <div class="ref-stat-card">
        <div class="ref-stat-icon" style="background:linear-gradient(135deg,#60a5fa,#2563eb)"><BookOpen :size="22" color="#fff" /></div>
        <div><div class="ref-stat-value">{{ projects.length }}</div><div class="ref-stat-label">涉及项目</div></div>
      </div>
    </div>

    <div class="card">
      <div class="toolbar">
        <div class="filters">
          <div class="seg">
            <button :class="{ active: filters.status === 'SUBMITTED' }" @click="filters.status = 'SUBMITTED'; load()">待评审</button>
            <button :class="{ active: filters.status === 'GRADED' }" @click="filters.status = 'GRADED'; load()">已评分</button>
            <button :class="{ active: filters.status === 'RETURNED' }" @click="filters.status = 'RETURNED'; load()">已退回</button>
            <button :class="{ active: filters.status === 'ALL' }" @click="filters.status = 'ALL'; load()">全部</button>
          </div>
          <el-select v-model="filters.projectId" placeholder="全部项目" clearable filterable style="width:240px" @change="load">
            <el-option v-for="p in projects" :key="p.id" :label="p.title" :value="p.id" />
          </el-select>
          <el-button @click="load">刷新</el-button>
        </div>
        <span class="hint">评分 ≥60 判定通过;有考核项的项目全部评完后按权重算综合分。评分后学生收到通知,技能画像自动校准。</span>
      </div>

      <el-empty v-if="items.length === 0" :description="filters.status === 'SUBMITTED' ? '没有待评审的成果,学生提交后会出现在这里' : '暂无记录'" />
      <div v-else class="sub-list">
        <div v-for="s in pageItems" :key="s.id" class="sub-item">
          <div class="sub-main">
            <div class="sub-top">
              <b>{{ s.userName }}</b>
              <span class="muted">· {{ s.projectTitle }}</span>
              <span v-if="s.assessmentName" class="badge badge-purple">{{ s.assessmentName }}</span>
              <span v-else class="badge badge-gray">整体成果</span>
              <span class="badge" :class="{ GRADED: 'badge-green', RETURNED: 'badge-red' }[s.status] || 'badge-yellow'">{{ s.status === 'GRADED' ? `已评 ${s.score} 分` : s.status === 'RETURNED' ? '已退回修改' : '待评审' }}</span>
              <span class="muted time">{{ fmt(s.submittedAt) }}</span>
            </div>
            <div class="sub-content">{{ s.content }}</div>
            <div class="sub-foot">
              <a v-if="s.attachmentUrl" :href="s.attachmentUrl" target="_blank" class="link">查看附件</a>
              <span v-if="s.status !== 'SUBMITTED'" class="muted">{{ s.status === 'RETURNED' ? '退回意见' : '评语' }}:{{ s.feedback || '无' }} · {{ s.graderName }} · {{ fmt(s.gradedAt) }}</span>
            </div>
          </div>
          <div class="sub-actions">
            <el-button v-if="s.status === 'SUBMITTED'" type="primary" @click="openGrade(s)">评分</el-button>
            <el-button size="small" text @click="$router.push(`/app/projects/${s.projectId}`)">看项目</el-button>
          </div>
        </div>
      </div>

      <div v-if="items.length > pageSize" class="pager">
        <el-pagination v-model:current-page="page" :page-size="pageSize" :total="items.length" layout="prev, pager, next" background />
      </div>
    </div>

    <GradeDialog v-model="gradeVisible" :submission="grading" :project="gradingProject"
                 :ai-review-fn="teacherAiReview" :grade-fn="teacherGradeSubmission" :return-fn="teacherReturnSubmission" @graded="onGraded" />
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { BookOpen, CircleCheckBig, Clock } from 'lucide-vue-next'
import { teacherAiReview, teacherGradeSubmission, teacherListSubmissions, teacherProjects, teacherReturnSubmission } from '../../api'
import GradeDialog from '../../components/GradeDialog.vue'

const emit = defineEmits(['refresh-pending'])
const items = ref([])
const projects = ref([])
const filters = reactive({ status: 'SUBMITTED', projectId: null })
const gradeVisible = ref(false)
const grading = ref(null)
const page = ref(1)
const pageSize = 8
const pendingCount = ref(0)
const gradedCount = ref(0)

const pageItems = computed(() => items.value.slice((page.value - 1) * pageSize, page.value * pageSize))
const gradingProject = computed(() => projects.value.find((p) => p.id === grading.value?.projectId) || null)
const fmt = (v) => (v || '').replace('T', ' ').slice(0, 16)

const load = async () => {
  const all = await teacherListSubmissions({ status: 'ALL', projectId: filters.projectId || undefined })
  pendingCount.value = all.filter((s) => s.status === 'SUBMITTED').length
  gradedCount.value = all.filter((s) => s.status === 'GRADED').length
  items.value = filters.status === 'ALL' ? all : all.filter((s) => s.status === filters.status)
  page.value = 1
}

const openGrade = (row) => {
  grading.value = row
  gradeVisible.value = true
}

const onGraded = async () => {
  await load()
  emit('refresh-pending')
}

onMounted(async () => {
  projects.value = await teacherProjects()
  await load()
})
</script>

<style scoped>
.stat-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 16px; margin-bottom: 20px; }
.toolbar { display: flex; justify-content: space-between; align-items: center; gap: 12px; margin-bottom: 16px; flex-wrap: wrap; }
.filters { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.seg { display: inline-flex; background: #e9ebf3; border-radius: 10px; padding: 3px; gap: 2px; }
.seg button { border: none; background: transparent; padding: 7px 14px; border-radius: 8px; font-size: 13px; color: #4b5563; cursor: pointer; }
.seg button.active { background: #fff; color: #111827; font-weight: 600; box-shadow: 0 1px 3px rgba(15, 23, 42, .12); }
.hint, .muted { color: var(--text-secondary); font-size: 12px; }
.sub-list { display: flex; flex-direction: column; gap: 12px; }
.sub-item { display: flex; gap: 16px; padding: 14px 16px; border: 1px solid var(--border); border-radius: 12px; }
.sub-main { flex: 1; min-width: 0; }
.sub-top { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; font-size: 14px; }
.sub-top .time { margin-left: auto; }
.sub-content { margin-top: 8px; font-size: 13px; line-height: 1.7; color: #374151; white-space: pre-wrap; display: -webkit-box; -webkit-line-clamp: 3; -webkit-box-orient: vertical; overflow: hidden; }
.sub-foot { margin-top: 8px; display: flex; gap: 12px; align-items: center; flex-wrap: wrap; }
.link { font-size: 13px; color: var(--brand-blue); }
.sub-actions { display: flex; flex-direction: column; gap: 6px; align-items: flex-end; justify-content: center; flex-shrink: 0; }
.pager { display: flex; justify-content: flex-end; margin-top: 14px; }
</style>
