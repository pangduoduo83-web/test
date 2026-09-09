<template>
  <div class="card">
    <div class="toolbar">
      <div class="filters">
        <el-select v-model="filters.status" placeholder="全部状态" clearable style="width:130px" @change="load">
          <el-option label="待评审" value="SUBMITTED" />
          <el-option label="已评分" value="GRADED" />
          <el-option label="已退回" value="RETURNED" />
        </el-select>
        <el-select v-model="filters.projectId" placeholder="全部项目" clearable filterable style="width:220px" @change="load">
          <el-option v-for="p in projects" :key="p.id" :label="p.title" :value="p.id" />
        </el-select>
        <el-select v-model="filters.userId" placeholder="全部学生" clearable filterable style="width:180px" @change="load">
          <el-option v-for="u in students" :key="u.id" :label="u.name" :value="u.id" />
        </el-select>
        <el-button @click="load">刷新</el-button>
        <el-button :disabled="!items.length" @click="exportCsv">导出 CSV</el-button>
      </div>
      <span class="hint">共 {{ items.length }} 条成果 · 管理员可评审全部项目;指导教师也可在教师工作台评审自己项目的成果</span>
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
          <el-tooltip :content="row.content" placement="top"><span class="ellipsis">{{ row.content }}</span></el-tooltip>
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
          <span class="badge" :class="{ GRADED: 'badge-green', RETURNED: 'badge-red' }[row.status] || 'badge-yellow'">{{ { GRADED: '已评分', RETURNED: '已退回' }[row.status] || '待评审' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="评分" width="80"><template #default="{ row }">{{ row.score ?? '-' }}</template></el-table-column>
      <el-table-column prop="feedback" label="评语" min-width="160"><template #default="{ row }">{{ row.feedback || '-' }}</template></el-table-column>
      <el-table-column label="提交时间" width="145"><template #default="{ row }">{{ formatTime(row.submittedAt) }}</template></el-table-column>
      <el-table-column label="操作" width="90" fixed="right">
        <template #default="{ row }">
          <el-button v-if="row.status === 'SUBMITTED'" size="small" type="primary" @click="openGrade(row)">评分</el-button>
          <span v-else class="sub-text">{{ row.graderName || '已评' }}</span>
        </template>
      </el-table-column>
    </el-table>

    <div class="pager">
      <el-pagination v-model:current-page="page" :page-size="pageSize" :total="items.length" layout="total, prev, pager, next" background />
    </div>

    <GradeDialog v-model="gradeVisible" :submission="grading" :project="gradingProject"
                 :ai-review-fn="adminAiReview" :grade-fn="adminGradeSubmission" :return-fn="adminReturnSubmission" @graded="load" />
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { adminAiReview, adminGradeSubmission, adminListProjects, adminListSubmissions, adminListUsers, adminReturnSubmission } from '../../api'
import GradeDialog from '../../components/GradeDialog.vue'
import { downloadCsv } from '../../utils/csv'

const items = ref([])
const users = ref([])
const projects = ref([])
const filters = reactive({ status: 'SUBMITTED', projectId: null, userId: null })
const gradeVisible = ref(false)
const grading = ref(null)
const page = ref(1)
const pageSize = 10

const students = computed(() => users.value.filter((u) => u.role === 'STUDENT'))
const pageItems = computed(() => items.value.slice((page.value - 1) * pageSize, page.value * pageSize))
const gradingProject = computed(() => projects.value.find((p) => p.id === grading.value?.projectId) || null)
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
  gradeVisible.value = true
}

const exportCsv = () => {
  const statusText = { SUBMITTED: '待评审', GRADED: '已评分', RETURNED: '已退回' }
  downloadCsv(`成果评审-${new Date().toISOString().slice(0, 10)}`,
    ['学生', '项目', '考核项', '状态', '分数', '评语', '评审人', '提交时间', '评审时间', '成果说明'],
    items.value.map((s) => [s.userName || userName(s.userId), s.projectTitle || projectName(s.projectId), s.assessmentName || '整体成果',
      statusText[s.status] || s.status, s.score ?? '', s.feedback || '', s.graderName || '', formatTime(s.submittedAt), formatTime(s.gradedAt), s.content]))
}

onMounted(async () => {
  const [userItems, projectItems] = await Promise.all([adminListUsers(), adminListProjects()])
  users.value = userItems
  projects.value = projectItems
  await load()
})
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; align-items: center; gap: 12px; margin-bottom: 16px; flex-wrap: wrap; }
.filters { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.hint, .sub-text { color: var(--text-secondary); font-size: 12px; }
.ellipsis { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.pager { display: flex; justify-content: flex-end; margin-top: 14px; }
</style>
