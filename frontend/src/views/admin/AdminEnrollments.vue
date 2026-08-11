<template>
  <div>
    <div class="card">
      <div class="toolbar">
        <div class="filters">
          <el-select v-model="filters.projectId" placeholder="全部项目" clearable filterable
                     style="width:220px" @change="load">
            <el-option v-for="p in projects" :key="p.id" :label="p.title" :value="p.id" />
          </el-select>
          <el-select v-model="filters.userId" placeholder="全部学生" clearable filterable
                     style="width:180px" @change="load">
            <el-option v-for="u in students" :key="u.id" :label="studentLabel(u)" :value="u.id" />
          </el-select>
          <el-select v-model="filters.status" placeholder="全部状态" clearable
                     style="width:130px" @change="load">
            <el-option label="进行中" value="IN_PROGRESS" />
            <el-option label="已完成" value="COMPLETED" />
          </el-select>
          <el-button @click="load">刷新</el-button>
        </div>
        <el-button type="primary" @click="openCreate">+ 代报名</el-button>
      </div>

      <el-table :data="pageItems" stripe>
        <el-table-column label="学生" min-width="170">
          <template #default="{ row }">
            {{ userOf(row.userId)?.name || `用户 #${row.userId}` }}
            <div class="sub-text">
              {{ userOf(row.userId)?.studentNo || '-' }} · {{ userOf(row.userId)?.major || '未填写专业' }}
            </div>
          </template>
        </el-table-column>
        <el-table-column label="项目" min-width="200">
          <template #default="{ row }">{{ row.projectTitle || projectOf(row.projectId)?.title || '-' }}</template>
        </el-table-column>
        <el-table-column label="进度" width="170">
          <template #default="{ row }"><el-progress :percentage="row.progress || 0" /></template>
        </el-table-column>
        <el-table-column prop="currentTask" label="当前任务" min-width="180">
          <template #default="{ row }">{{ row.currentTask || '-' }}</template>
        </el-table-column>
        <el-table-column prop="deadline" label="截止日期" width="115">
          <template #default="{ row }">{{ row.deadline || '-' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <span class="badge" :class="row.status === 'COMPLETED' ? 'badge-green' : 'badge-blue'">
              {{ row.status === 'COMPLETED' ? '已完成' : '进行中' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="报名时间" width="145">
          <template #default="{ row }">{{ formatTime(row.enrolledAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="145" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="openEdit(row)">进度</el-button>
            <el-button size="small" type="danger" plain @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager">
        <el-pagination v-model:current-page="page" :page-size="pageSize" :total="items.length"
                       layout="total, prev, pager, next" background />
      </div>
    </div>

    <el-dialog v-model="createVisible" title="代学生报名" width="500px">
      <el-form :model="createForm" label-width="80px">
        <el-form-item label="学生" required>
          <el-select v-model="createForm.userId" filterable placeholder="选择学生">
            <el-option v-for="u in activeStudents" :key="u.id" :label="studentLabel(u)" :value="u.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="项目" required>
          <el-select v-model="createForm.projectId" filterable placeholder="选择项目">
            <el-option v-for="p in projects" :key="p.id" :label="p.title" :value="p.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="createEnrollment">确认报名</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="editVisible" title="编辑学习进度" width="520px">
      <el-form :model="editForm" label-width="90px">
        <el-form-item label="进度">
          <el-slider v-model="editForm.progress" :min="0" :max="100" show-input />
        </el-form-item>
        <el-form-item label="当前任务">
          <el-input v-model="editForm.currentTask" placeholder="当前学习或实践任务" />
        </el-form-item>
        <el-form-item label="截止日期">
          <el-date-picker v-model="editForm.deadline" type="date" value-format="YYYY-MM-DD"
                          placeholder="选择截止日期" clearable />
        </el-form-item>
        <el-alert v-if="editForm.progress === 100" type="success" :closable="false"
                  title="首次设为 100% 将完成项目并发放完成奖励" />
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveProgress">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  adminCreateEnrollment, adminDeleteEnrollment, adminListEnrollments, adminListProjects,
  adminListUsers, adminUpdateEnrollment
} from '../../api'

const route = useRoute()
const items = ref([])
const projects = ref([])
const users = ref([])
const filters = reactive({ projectId: null, userId: null, status: '' })
const createVisible = ref(false)
const editVisible = ref(false)
const saving = ref(false)
const createForm = reactive({ userId: null, projectId: null })
const editForm = reactive({ id: null, progress: 0, currentTask: '', deadline: null })
const page = ref(1)
const pageSize = 10

const students = computed(() => users.value.filter((u) => u.role === 'STUDENT'))
const activeStudents = computed(() => students.value.filter((u) => u.enabled))
const pageItems = computed(() =>
  items.value.slice((page.value - 1) * pageSize, page.value * pageSize))

const userOf = (id) => users.value.find((u) => u.id === id)
const projectOf = (id) => projects.value.find((p) => p.id === id)
const studentLabel = (u) => `${u.name}${u.studentNo ? ` (${u.studentNo})` : ''}`
const formatTime = (v) => (v || '').replace('T', ' ').slice(0, 16)

const load = async () => {
  items.value = await adminListEnrollments({
    projectId: filters.projectId || undefined,
    userId: filters.userId || undefined,
    status: filters.status || undefined
  })
  page.value = 1
}

const openCreate = () => {
  createForm.userId = null
  createForm.projectId = filters.projectId || null
  createVisible.value = true
}

const createEnrollment = async () => {
  if (!createForm.userId || !createForm.projectId) {
    ElMessage.warning('请选择学生和项目')
    return
  }
  saving.value = true
  try {
    await adminCreateEnrollment(createForm)
    ElMessage.success('代报名成功')
    createVisible.value = false
    await Promise.all([load(), refreshProjects()])
  } finally {
    saving.value = false
  }
}

const openEdit = (row) => {
  Object.assign(editForm, {
    id: row.id, progress: row.progress || 0, currentTask: row.currentTask || '',
    deadline: row.deadline || null
  })
  editVisible.value = true
}

const saveProgress = async () => {
  saving.value = true
  try {
    await adminUpdateEnrollment(editForm.id, {
      progress: editForm.progress,
      currentTask: editForm.currentTask,
      deadline: editForm.deadline
    })
    ElMessage.success('进度已更新')
    editVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

const remove = async (row) => {
  await ElMessageBox.confirm(
    `确定删除「${userOf(row.userId)?.name || row.userId}」在《${row.projectTitle}》的报名?已有成果时不可删除。`,
    '删除报名', { type: 'warning' })
  await adminDeleteEnrollment(row.id)
  ElMessage.success('报名已删除')
  await Promise.all([load(), refreshProjects()])
}

const refreshProjects = async () => {
  projects.value = await adminListProjects()
}

watch(() => route.query.projectId, async (value) => {
  filters.projectId = value ? Number(value) : null
  await load()
})

onMounted(async () => {
  const [userItems, projectItems] = await Promise.all([adminListUsers(), adminListProjects()])
  users.value = userItems
  projects.value = projectItems
  filters.projectId = route.query.projectId ? Number(route.query.projectId) : null
  await load()
})
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; align-items: center; gap: 12px; margin-bottom: 16px; }
.filters { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.sub-text { font-size: 12px; color: var(--text-secondary); }
.pager { display: flex; justify-content: flex-end; margin-top: 14px; }
:deep(.el-dialog .el-select), :deep(.el-dialog .el-date-editor) { width: 100%; }
</style>
