<template>
  <div>
    <div class="card">
      <div class="toolbar">
        <div class="filters">
          <el-input v-model="filters.keyword" placeholder="姓名 / 邮箱 / 手机号 / 学号 / 专业" clearable
                    style="width:260px" @keyup.enter="load" @clear="load" />
          <el-select v-model="filters.role" placeholder="全部角色" clearable style="width:120px" @change="load">
            <el-option label="学生" value="STUDENT" />
            <el-option label="教师" value="TEACHER" />
            <el-option label="管理员" value="ADMIN" />
          </el-select>
          <el-select v-model="filters.enabled" placeholder="全部状态" clearable style="width:120px" @change="load">
            <el-option label="正常" :value="true" />
            <el-option label="禁用" :value="false" />
          </el-select>
          <el-button @click="load">查询</el-button>
        </div>
        <div class="toolbar-right">
          <el-button plain @click="downloadUserTemplate">下载导入模板</el-button>
          <el-upload :show-file-list="false" accept=".csv" :http-request="importUsersCsv">
            <el-button plain type="primary" :loading="importing">批量导入</el-button>
          </el-upload>
          <el-button type="primary" @click="openEdit()">+ 新增用户</el-button>
        </div>
      </div>

      <el-table :data="pageItems" stripe>
        <el-table-column label="用户" min-width="200">
          <template #default="{ row }">
            <div class="user-cell">
              <el-avatar :size="36" :src="row.avatarUrl">{{ (row.name || '?')[0] }}</el-avatar>
              <div>
                <div>{{ row.name }}</div>
                <div class="sub-text">{{ row.email }}</div>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="phone" label="手机号" width="130">
          <template #default="{ row }">{{ row.phone || '-' }}</template>
        </el-table-column>
        <el-table-column prop="studentNo" label="学号" width="120">
          <template #default="{ row }">{{ row.studentNo || '-' }}</template>
        </el-table-column>
        <el-table-column prop="major" label="专业" min-width="120">
          <template #default="{ row }">{{ row.major || '-' }}</template>
        </el-table-column>
        <el-table-column prop="grade" label="年级" width="90">
          <template #default="{ row }">{{ row.grade || '-' }}</template>
        </el-table-column>
        <el-table-column label="角色" width="100">
          <template #default="{ row }">
            <span class="badge" :class="roleBadge(row.role)">{{ roleText(row.role) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="exp" label="经验值" width="80" />
        <el-table-column prop="weeklyHours" label="本周学时" width="90" />
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <span class="badge" :class="row.enabled ? 'badge-green' : 'badge-red'">
              {{ row.enabled ? '正常' : '禁用' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="注册时间" width="110">
          <template #default="{ row }">{{ (row.createdAt || '').slice(0, 10) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="230" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="openEdit(row)">编辑</el-button>
            <el-button size="small" plain @click="resetPassword(row)">重置密码</el-button>
            <el-button size="small" type="danger" plain :disabled="row.id === authStore.user?.id"
                       @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager">
        <el-pagination v-model:current-page="page" :page-size="pageSize" :total="items.length"
                       layout="total, prev, pager, next" background />
      </div>
    </div>

    <el-dialog v-model="editVisible" :title="form.id ? '编辑用户' : '新增用户'" width="680px">
      <el-form :model="form" label-width="86px">
        <div class="form-2col">
          <el-form-item label="姓名" required><el-input v-model="form.name" /></el-form-item>
          <el-form-item label="邮箱" required><el-input v-model="form.email" /></el-form-item>
          <el-form-item v-if="!form.id" label="初始密码" required>
            <el-input v-model="form.password" type="password" show-password />
          </el-form-item>
          <el-form-item label="手机号">
            <el-input v-model="form.phone" placeholder="选填,可用于登录" />
          </el-form-item>
          <el-form-item label="学号"><el-input v-model="form.studentNo" /></el-form-item>
          <el-form-item label="专业"><el-input v-model="form.major" /></el-form-item>
          <el-form-item label="年级"><el-input v-model="form.grade" /></el-form-item>
          <el-form-item label="角色">
            <el-select v-model="form.role">
              <el-option label="学生" value="STUDENT" />
              <el-option label="教师" value="TEACHER" />
              <el-option label="管理员" value="ADMIN" />
            </el-select>
          </el-form-item>
          <el-form-item label="启用"><el-switch v-model="form.enabled" /></el-form-item>
        </div>
        <el-form-item label="头像">
          <ImageUploader v-model="form.avatarUrl" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  adminCreateUser, adminDeleteUser, adminListUsers, adminResetUserPassword, adminUpdateUser
} from '../../api'
import ImageUploader from '../../components/ImageUploader.vue'
import { useAuthStore } from '../../stores/auth'

// ---------- 用户 CSV 批量导入 ----------
const importing = ref(false)

const downloadUserTemplate = () => {
  const csv = '\ufeff姓名,邮箱,初始密码,角色(STUDENT/TEACHER/ADMIN),学号,专业,年级\r\n'
    + '李小明,lixm@stu.ioedu.cn,123456,STUDENT,2026101,电子信息工程,大一\r\n'
    + '王老师,wanglaoshi@ioedu.cn,123456,TEACHER,,,'
  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' })
  const a = document.createElement('a')
  a.href = URL.createObjectURL(blob)
  a.download = '用户导入模板.csv'
  a.click()
  URL.revokeObjectURL(a.href)
}

const importUsersCsv = (opt) => {
  const reader = new FileReader()
  reader.onload = async () => {
    const lines = String(reader.result).split(/\r?\n/).map((l) => l.trim()).filter(Boolean)
    const rows = []
    for (const line of lines) {
      const c = line.split(/[,，\t]/).map((v) => v.trim().replace(/^"|"$/g, ''))
      if (/姓名|name/i.test(c[0]) || !c[0] || !(c[1] || '').includes('@')) continue
      rows.push({
        name: c[0], email: c[1], password: c[2] || '123456',
        role: ['STUDENT', 'TEACHER', 'ADMIN'].includes((c[3] || '').toUpperCase()) ? c[3].toUpperCase() : 'STUDENT',
        studentNo: c[4] || undefined, major: c[5] || undefined, grade: c[6] || undefined
      })
    }
    if (!rows.length) {
      ElMessage.warning('未解析到有效行,请使用「下载导入模板」的格式(UTF-8 编码)')
      return
    }
    importing.value = true
    let ok = 0
    const failed = []
    for (const row of rows) {
      try {
        await adminCreateUser(row)
        ok++
      } catch (e) {
        failed.push(row.email)
      }
    }
    importing.value = false
    await load()
    if (failed.length) {
      ElMessageBox.alert(`成功 ${ok} 人,失败 ${failed.length} 人(多为邮箱已存在):\n${failed.join('、')}`, '导入结果')
    } else {
      ElMessage.success(`批量导入完成,共创建 ${ok} 个账号`)
    }
  }
  reader.readAsText(opt.file, 'utf-8')
}

const authStore = useAuthStore()
const items = ref([])
const filters = reactive({ keyword: '', role: '', enabled: null })
const editVisible = ref(false)
const saving = ref(false)
const page = ref(1)
const pageSize = 10
const emptyForm = {
  id: null, name: '', email: '', phone: '', password: '', studentNo: '', major: '', grade: '',
  avatarUrl: '', role: 'STUDENT', enabled: true
}
const form = reactive({ ...emptyForm })

const pageItems = computed(() =>
  items.value.slice((page.value - 1) * pageSize, page.value * pageSize))

const load = async () => {
  items.value = await adminListUsers({
    keyword: filters.keyword || undefined,
    role: filters.role || undefined,
    enabled: filters.enabled === null ? undefined : filters.enabled
  })
  page.value = 1
}

const roleText = (r) => r === 'ADMIN' ? '管理员' : r === 'TEACHER' ? '教师' : '学生'
const roleBadge = (r) => r === 'ADMIN' ? 'badge-purple' : r === 'TEACHER' ? 'badge-green' : 'badge-blue'

const openEdit = (row = null) => {
  Object.assign(form, emptyForm)
  if (row) {
    Object.assign(form, {
      id: row.id, name: row.name, email: row.email, phone: row.phone || '',
      studentNo: row.studentNo || '',
      major: row.major || '', grade: row.grade || '', avatarUrl: row.avatarUrl || '',
      role: row.role, enabled: !!row.enabled
    })
  }
  editVisible.value = true
}

const save = async () => {
  if (!form.name.trim() || !form.email.trim()) {
    ElMessage.warning('请填写姓名和邮箱')
    return
  }
  if (!form.id && form.password.length < 6) {
    ElMessage.warning('初始密码至少 6 位')
    return
  }
  if (form.phone && !/^1\d{10}$/.test(form.phone.trim())) {
    ElMessage.warning('手机号格式不正确')
    return
  }
  saving.value = true
  try {
    const payload = {
      name: form.name, email: form.email, phone: form.phone,
      studentNo: form.studentNo, major: form.major,
      grade: form.grade, avatarUrl: form.avatarUrl, role: form.role, enabled: form.enabled
    }
    if (form.id) await adminUpdateUser(form.id, payload)
    else await adminCreateUser({ ...payload, password: form.password })
    ElMessage.success('用户信息已保存')
    editVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

const resetPassword = async (row) => {
  const { value } = await ElMessageBox.prompt(`为「${row.name}」设置新密码`, '重置密码', {
    inputType: 'password',
    inputValidator: (v) => (v && v.length >= 6) || '密码至少 6 位',
    confirmButtonText: '确认重置'
  })
  await adminResetUserPassword(row.id, value)
  ElMessage.success('密码已重置')
}

const remove = async (row) => {
  await ElMessageBox.confirm(
    `确定删除用户「${row.name}」?存在借阅、报名、成果或导师项目时系统会拒绝删除。`,
    '删除用户', { type: 'warning' })
  await adminDeleteUser(row.id)
  ElMessage.success('用户已删除')
  await load()
}

onMounted(load)
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; gap: 12px; }
.toolbar-right { display: flex; align-items: center; gap: 10px; }
.filters { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.user-cell { display: flex; align-items: center; gap: 10px; }
.sub-text { font-size: 12px; color: var(--text-secondary); }
.pager { display: flex; justify-content: flex-end; margin-top: 14px; }
.form-2col { display: grid; grid-template-columns: 1fr 1fr; column-gap: 16px; }
:deep(.el-select) { width: 100%; }
</style>
