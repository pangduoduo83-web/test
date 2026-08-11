<template>
  <div class="card">
    <div class="toolbar">
      <div class="filters">
        <el-select v-model="filters.userId" placeholder="全部用户" clearable filterable
                   style="width:190px" @change="load">
          <el-option v-for="u in users" :key="u.id" :label="`${u.name} (${roleText(u.role)})`" :value="u.id" />
        </el-select>
        <el-select v-model="filters.type" placeholder="全部类型" clearable style="width:130px" @change="load">
          <el-option label="系统" value="system" />
          <el-option label="项目" value="project" />
          <el-option label="借阅" value="borrow" />
        </el-select>
        <el-select v-model="filters.read" placeholder="全部状态" clearable style="width:120px" @change="load">
          <el-option label="未读" :value="false" />
          <el-option label="已读" :value="true" />
        </el-select>
        <el-button @click="load">刷新</el-button>
      </div>
      <el-button type="primary" @click="openSend">+ 发送通知</el-button>
    </div>

    <el-table :data="pageItems" stripe>
      <el-table-column label="接收用户" min-width="150">
        <template #default="{ row }">{{ userName(row.userId) }}</template>
      </el-table-column>
      <el-table-column label="类型" width="90">
        <template #default="{ row }">
          <span class="badge" :class="typeBadge(row.type)">{{ typeText(row.type) }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="title" label="标题" min-width="180" />
      <el-table-column prop="content" label="内容" min-width="260">
        <template #default="{ row }">{{ row.content || '-' }}</template>
      </el-table-column>
      <el-table-column label="阅读状态" width="90">
        <template #default="{ row }">
          <span class="badge" :class="row.isRead ? 'badge-gray' : 'badge-yellow'">
            {{ row.isRead ? '已读' : '未读' }}
          </span>
        </template>
      </el-table-column>
      <el-table-column label="发送时间" width="145">
        <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="80" fixed="right">
        <template #default="{ row }">
          <el-button size="small" type="danger" plain @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pager">
      <el-pagination v-model:current-page="page" :page-size="pageSize" :total="items.length"
                     layout="total, prev, pager, next" background />
    </div>

    <el-dialog v-model="sendVisible" title="发送站内通知" width="580px">
      <el-form :model="form" label-width="86px">
        <el-form-item label="发送范围">
          <el-radio-group v-model="form.target">
            <el-radio value="user">指定用户</el-radio>
            <el-radio value="role">按角色群发</el-radio>
            <el-radio value="all">全体用户</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="form.target === 'user'" label="接收用户" required>
          <el-select v-model="form.userId" filterable placeholder="选择用户">
            <el-option v-for="u in users" :key="u.id" :label="`${u.name} (${roleText(u.role)})`" :value="u.id" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="form.target === 'role'" label="接收角色" required>
          <el-select v-model="form.role">
            <el-option label="学生" value="STUDENT" />
            <el-option label="教师" value="TEACHER" />
            <el-option label="管理员" value="ADMIN" />
          </el-select>
        </el-form-item>
        <el-form-item label="通知类型" required>
          <el-select v-model="form.type" filterable allow-create default-first-option>
            <el-option label="系统" value="system" />
            <el-option label="项目" value="project" />
            <el-option label="借阅" value="borrow" />
          </el-select>
        </el-form-item>
        <el-form-item label="标题" required><el-input v-model="form.title" maxlength="100" show-word-limit /></el-form-item>
        <el-form-item label="内容">
          <el-input v-model="form.content" type="textarea" :rows="4" maxlength="300" show-word-limit />
        </el-form-item>
        <el-alert v-if="form.target !== 'user'" type="warning" :closable="false"
                  title="群发会为每位目标用户创建一条独立通知" />
      </el-form>
      <template #footer>
        <el-button @click="sendVisible = false">取消</el-button>
        <el-button type="primary" :loading="sending" @click="send">发送</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  adminDeleteNotification, adminListNotifications, adminListUsers, adminSendNotification
} from '../../api'

const items = ref([])
const users = ref([])
const filters = reactive({ userId: null, type: '', read: null })
const sendVisible = ref(false)
const sending = ref(false)
const form = reactive({
  target: 'user', userId: null, role: 'STUDENT', type: 'system', title: '', content: ''
})
const page = ref(1)
const pageSize = 10

const pageItems = computed(() =>
  items.value.slice((page.value - 1) * pageSize, page.value * pageSize))
const roleText = (role) => role === 'ADMIN' ? '管理员' : role === 'TEACHER' ? '教师' : '学生'
const userName = (id) => users.value.find((u) => u.id === id)?.name || `用户 #${id}`
const formatTime = (v) => (v || '').replace('T', ' ').slice(0, 16)
const typeText = (type) => ({ system: '系统', project: '项目', borrow: '借阅' }[type] || type)
const typeBadge = (type) => ({ system: 'badge-purple', project: 'badge-blue', borrow: 'badge-green' }[type] || 'badge-gray')

const load = async () => {
  items.value = await adminListNotifications({
    userId: filters.userId || undefined,
    type: filters.type || undefined,
    read: filters.read === null ? undefined : filters.read
  })
  page.value = 1
}

const openSend = () => {
  Object.assign(form, {
    target: 'user', userId: null, role: 'STUDENT', type: 'system', title: '', content: ''
  })
  sendVisible.value = true
}

const send = async () => {
  if (!form.title.trim() || !form.type.trim()) {
    ElMessage.warning('请填写通知类型和标题')
    return
  }
  if (form.target === 'user' && !form.userId) {
    ElMessage.warning('请选择接收用户')
    return
  }
  sending.value = true
  try {
    const count = await adminSendNotification({
      userId: form.target === 'user' ? form.userId : null,
      role: form.target === 'role' ? form.role : null,
      title: form.title,
      content: form.content,
      type: form.type
    })
    ElMessage.success(`通知已发送给 ${count} 位用户`)
    sendVisible.value = false
    await load()
  } finally {
    sending.value = false
  }
}

const remove = async (row) => {
  await ElMessageBox.confirm(`确定删除通知「${row.title}」?`, '删除通知', { type: 'warning' })
  await adminDeleteNotification(row.id)
  ElMessage.success('通知已删除')
  await load()
}

onMounted(async () => {
  users.value = await adminListUsers()
  await load()
})
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; align-items: center; gap: 12px; margin-bottom: 16px; }
.filters { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.pager { display: flex; justify-content: flex-end; margin-top: 14px; }
:deep(.el-dialog .el-select) { width: 100%; }
</style>
