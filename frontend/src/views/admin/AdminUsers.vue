<template>
  <div>
    <div class="card">
      <div class="toolbar">
        <div class="filters">
          <el-input v-model="filters.keyword" placeholder="姓名 / 邮箱 / 手机号 / 学号 / 教师工号 / 专业" clearable
                    style="width:260px" @keyup.enter="load" @clear="load" />
          <el-select v-model="filters.role" placeholder="全部角色" clearable style="width:140px" @change="load">
            <el-option label="学生" value="STUDENT" />
            <el-option label="教师" value="TEACHER" />
            <el-option label="实验室管理员" value="LAB_ADMIN" />
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
          <el-upload :show-file-list="false" accept=".csv,.txt" :disabled="reading || importing" :http-request="importUsersCsv">
            <el-button plain type="primary" :loading="reading || importing">批量导入</el-button>
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
        <el-table-column prop="teacherNo" label="教师工号" width="120">
          <template #default="{ row }">{{ row.teacherNo || '-' }}</template>
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

    <el-dialog v-model="importVisible" title="批量导入用户" width="min(1000px, 95vw)"
               :close-on-click-modal="false" :close-on-press-escape="!importing" :show-close="!importing"
               @closed="clearImport">
      <el-alert type="info" :closable="false" title="仅创建当前分站的用户，不会覆盖已有账号。支持 UTF-8 CSV，每次最多 500 人、1 MB。">
        必填姓名、邮箱、初始密码和角色；学号、教师工号请在 Excel 中设为文本以保留前导零。
        旧版七列表格也可使用，教师所在行的学号会作为工号导入。请先核对预览。
      </el-alert>
      <p v-if="!importFinished">共 {{ importRows.length }} 行，{{ invalidRows }} 行校验失败。请修正所有错误后重新选择文件。</p>
      <p v-else>已创建 {{ importRows.filter(r => r.status === '成功').length }} 人；其余行请查看结果。成功账号不会自动回滚。</p>
      <el-progress v-if="importing || importFinished" :percentage="importProgress" />
      <el-table :data="importRows" max-height="430" stripe>
        <el-table-column prop="line" label="文件行" width="75" />
        <el-table-column prop="data.name" label="姓名" width="100" />
        <el-table-column prop="data.email" label="邮箱" min-width="180" />
        <el-table-column label="角色" width="105"><template #default="{ row }">{{ row.data.role ? roleText(row.data.role) : '无效' }}</template></el-table-column>
        <el-table-column label="学号 / 工号" min-width="130"><template #default="{ row }">{{ row.data.studentNo || row.data.teacherNo || '未填写' }}</template></el-table-column>
        <el-table-column prop="status" label="状态" width="95" />
        <el-table-column prop="reason" label="原因" min-width="240" />
      </el-table>
      <template #footer>
        <el-button v-if="importRows.some(r => r.reason)" @click="downloadImportReport">下载问题明细</el-button>
        <el-button :disabled="importing" @click="importVisible = false">关闭</el-button>
        <el-button v-if="!importFinished" type="primary" :loading="importing"
                   :disabled="invalidRows > 0 || !importRows.length" @click="runImport">确认创建 {{ importRows.length }} 个账号</el-button>
      </template>
    </el-dialog>

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
          <el-form-item label="教师工号"><el-input v-model="form.teacherNo" placeholder="教师账号可用工号登录" /></el-form-item>
          <el-form-item label="专业"><el-input v-model="form.major" /></el-form-item>
          <el-form-item label="年级"><el-input v-model="form.grade" /></el-form-item>
          <el-form-item label="角色">
            <el-select v-model="form.role">
              <el-option label="学生" value="STUDENT" />
              <el-option label="教师" value="TEACHER" />
              <el-option label="实验室管理员(只管设备与借阅)" value="LAB_ADMIN" />
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
import { downloadCsv } from '../../utils/csv'
import { prepareUserImport, decodeUserCsv } from '../../utils/userImport.mjs'

// ---------- 用户 CSV 批量导入 ----------
const importing = ref(false)
const reading = ref(false)
const importVisible = ref(false)
const importFinished = ref(false)
const importProgress = ref(0)
const importRows = ref([])
const invalidRows = computed(() => importRows.value.filter((r) => r.errors?.length).length)

const downloadUserTemplate = () => {
  const headers = ['姓名', '邮箱', '初始密码', '角色(STUDENT/TEACHER/ADMIN/LAB_ADMIN)', '学号', '教师工号', '专业', '年级', '手机号']
  downloadCsv('用户导入模板.csv', headers, [
    ['李小明', 'lixm@stu.ioedu.cn', '123456', 'STUDENT', '2026101', '', '电子信息工程', '大一', ''],
    ['王老师', 'wanglaoshi@ioedu.cn', '123456', 'TEACHER', '', 'T1001', '电子信息工程', '教师', '']
  ])
}

const clearImport = () => {
  if (!importing.value) {
    importRows.value = []
    importFinished.value = false
    importProgress.value = 0
  }
}

const importUsersCsv = async (opt) => {
  reading.value = true
  try {
    const text = decodeUserCsv(await opt.file.arrayBuffer())
    const users = await adminListUsers({})
    importRows.value = prepareUserImport(text, users)
    importFinished.value = false
    importVisible.value = true
  } catch (e) {
    ElMessage.error(e.message || 'CSV 文件解析失败')
  } finally {
    reading.value = false
  }
}

const runImport = async () => {
  importing.value = true
  importProgress.value = 0
  for (let i = 0; i < importRows.value.length; i++) {
    const row = importRows.value[i]
    try {
      await adminCreateUser(row.data, { silentError: true })
      row.status = '成功'
      row.reason = ''
    } catch (e) {
      row.status = '失败'
      row.reason = e.message || '服务器拒绝创建'
    }
    importProgress.value = Math.round(((i + 1) / importRows.value.length) * 100)
  }
  importing.value = false
  importFinished.value = true
  await load()
}

const downloadImportReport = () => {
  downloadCsv('用户导入结果.csv', ['文件行', '姓名', '邮箱', '状态', '原因'], importRows.value.map((r) =>
    [r.line, r.data.name, r.data.email, r.status, r.reason || '']))
}
const authStore = useAuthStore()
const items = ref([])
const filters = reactive({ keyword: '', role: '', enabled: null })
const editVisible = ref(false)
const saving = ref(false)
const page = ref(1)
const pageSize = 10
const emptyForm = {
  id: null, name: '', email: '', phone: '', password: '', studentNo: '', teacherNo: '', major: '', grade: '',
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

const roleText = (r) => ({ ADMIN: '管理员', TEACHER: '教师', LAB_ADMIN: '实验室管理员' }[r] || '学生')
const roleBadge = (r) => ({ ADMIN: 'badge-purple', TEACHER: 'badge-green', LAB_ADMIN: 'badge-yellow' }[r] || 'badge-blue')

const openEdit = (row = null) => {
  Object.assign(form, emptyForm)
  if (row) {
    Object.assign(form, {
      id: row.id, name: row.name, email: row.email, phone: row.phone || '',
      studentNo: row.studentNo || '',
      teacherNo: row.teacherNo || '',
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
      studentNo: form.studentNo, teacherNo: form.teacherNo, major: form.major,
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
