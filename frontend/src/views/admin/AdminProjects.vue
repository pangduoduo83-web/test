<template>
  <div>
    <div class="card">
      <div class="toolbar">
        <span class="hint">共 {{ items.length }} 个项目</span>
        <div class="tb-actions">
          <el-button plain @click="draftVisible = true"><Sparkles :size="14" style="margin-right:4px" />AI 起草项目</el-button>
          <el-button type="primary" @click="openEdit(null)">+ 新增项目</el-button>
        </div>
      </div>

      <el-table :data="pageItems" stripe>
        <el-table-column label="项目" min-width="220">
          <template #default="{ row }">
            {{ row.title }}
            <div class="sub-text">{{ row.category }} · {{ row.author }}</div>
          </template>
        </el-table-column>
        <el-table-column prop="difficulty" label="难度" width="70" />
        <el-table-column prop="duration" label="周期" width="70" />
        <el-table-column label="讲师" width="110">
          <template #default="{ row }">{{ row.mentor || '未指派' }}</template>
        </el-table-column>
        <el-table-column prop="enrolledCount" label="报名数" width="80" />
        <el-table-column label="完成率" width="80">
          <template #default="{ row }">{{ row.enrolledCount > 0 ? row.completionRate + '%' : '–' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <span class="badge" :class="row.status === 'PUBLISHED' ? 'badge-green' : 'badge-gray'">
              {{ row.status === 'PUBLISHED' ? '已发布' : '草稿' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" plain @click="viewStudents(row)">学生</el-button>
            <el-button size="small" @click="openEdit(row)">编辑</el-button>
            <el-button size="small" type="danger" plain @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager">
        <el-pagination v-model:current-page="page" :page-size="pageSize" :total="items.length"
                       layout="total, prev, pager, next" background />
      </div>
    </div>

    <ProjectEditDialog v-model="editVisible" :project="editing" mode="admin" :teachers="teachers"
                       :skill-dimensions="skillDimensions" :save-fn="saveProject" @saved="load" />
    <AiDraftDialog v-model="draftVisible" :skill-dimensions="skillDimensions" @drafted="(d) => { editing = d; editVisible = true }" />
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Sparkles } from 'lucide-vue-next'
import {
  adminCreateProject, adminDeleteProject, adminListProjects, adminListSkillDimensions, adminListUsers, adminUpdateProject
} from '../../api'
import ProjectEditDialog from '../../components/ProjectEditDialog.vue'
import AiDraftDialog from '../../components/AiDraftDialog.vue'

const router = useRouter()
const items = ref([])
const teachers = ref([])
const skillDimensions = ref([])
const editVisible = ref(false)
const editing = ref(null)
const draftVisible = ref(false)
const page = ref(1)
const pageSize = 10

const pageItems = computed(() => items.value.slice((page.value - 1) * pageSize, page.value * pageSize))

const load = async () => {
  items.value = await adminListProjects()
  page.value = 1
}

const loadTeachers = async () => {
  const users = await adminListUsers()
  teachers.value = users.filter((u) => u.role === 'TEACHER')
}

const openEdit = (row) => {
  editing.value = row
  editVisible.value = true
}

const saveProject = (id, payload) => (id ? adminUpdateProject(id, payload) : adminCreateProject(payload))

const remove = async (row) => {
  await ElMessageBox.confirm(`确定删除项目《${row.title}》?该操作不可恢复。`, '删除项目', { type: 'warning' })
  await adminDeleteProject(row.id)
  ElMessage.success('已删除')
  await load()
}

const viewStudents = (row) => {
  router.push({ path: '/admin/enrollments', query: { projectId: row.id } })
}

onMounted(() => {
  load()
  loadTeachers()
  adminListSkillDimensions().then((list) => { skillDimensions.value = list }).catch(() => {})
})
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.tb-actions { display: flex; gap: 8px; }
.hint { color: var(--text-secondary); font-size: 13px; }
.sub-text { font-size: 12px; color: var(--text-secondary); }
.pager { display: flex; justify-content: flex-end; margin-top: 14px; }
</style>
