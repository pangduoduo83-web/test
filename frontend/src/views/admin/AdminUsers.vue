<template>
  <div>
    <div class="card">
      <div class="toolbar">
        <el-input v-model="keyword" placeholder="搜索姓名 / 邮箱 / 学号..." clearable style="width:280px" />
        <span class="hint">共 {{ filtered.length }} 位用户</span>
      </div>

      <el-table :data="pageItems" stripe>
        <el-table-column label="用户" min-width="180">
          <template #default="{ row }">
            <div class="user-cell">
              <span class="avatar">{{ (row.name || '?')[0] }}</span>
              <div>
                <div>{{ row.name }}</div>
                <div class="sub-text">{{ row.email }}</div>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="studentNo" label="学号" width="120" />
        <el-table-column prop="major" label="专业" width="130" />
        <el-table-column prop="grade" label="年级" width="80" />
        <el-table-column label="角色" width="110">
          <template #default="{ row }">
            <span class="badge" :class="roleBadge(row.role)">{{ roleText(row.role) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="exp" label="经验值" width="80" />
        <el-table-column label="注册时间" width="120">
          <template #default="{ row }">{{ (row.createdAt || '').slice(0, 10) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <span class="badge" :class="row.enabled ? 'badge-green' : 'badge-red'">
              {{ row.enabled ? '正常' : '禁用' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <el-button size="small" :type="row.enabled ? 'danger' : 'success'" plain
                       :disabled="row.id === authStore.user?.id" @click="toggleEnabled(row)">
              {{ row.enabled ? '禁用' : '启用' }}
            </el-button>
            <el-select :model-value="row.role" size="small" class="role-sel"
                       :disabled="row.id === authStore.user?.id"
                       @change="(v) => changeRole(row, v)">
              <el-option label="学生" value="STUDENT" />
              <el-option label="教师" value="TEACHER" />
              <el-option label="管理员" value="ADMIN" />
            </el-select>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager">
        <el-pagination v-model:current-page="page" :page-size="pageSize" :total="filtered.length"
                       layout="total, prev, pager, next" background />
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminListUsers, adminUpdateUser } from '../../api'
import { useAuthStore } from '../../stores/auth'

const authStore = useAuthStore()
const items = ref([])
const keyword = ref('')
const page = ref(1)
const pageSize = 10

const filtered = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  if (!kw) return items.value
  return items.value.filter((u) =>
    [u.name, u.email, u.studentNo].some((f) => (f || '').toLowerCase().includes(kw)))
})

const pageItems = computed(() =>
  filtered.value.slice((page.value - 1) * pageSize, page.value * pageSize))

watch(keyword, () => { page.value = 1 })

const load = async () => {
  items.value = await adminListUsers()
}

const roleText = (r) => r === 'ADMIN' ? '管理员' : r === 'TEACHER' ? '教师' : '学生'
const roleBadge = (r) => r === 'ADMIN' ? 'badge-purple' : r === 'TEACHER' ? 'badge-green' : 'badge-blue'

const toggleEnabled = async (row) => {
  const action = row.enabled ? '禁用' : '启用'
  await ElMessageBox.confirm(`确定${action}用户「${row.name}」?`, `${action}用户`, { type: 'warning' })
  await adminUpdateUser(row.id, { enabled: !row.enabled })
  ElMessage.success(`已${action}`)
  await load()
}

const changeRole = async (row, target) => {
  if (target === row.role) return
  await ElMessageBox.confirm(`确定将「${row.name}」角色改为${roleText(target)}?`, '变更角色', { type: 'warning' })
  await adminUpdateUser(row.id, { role: target })
  ElMessage.success('角色已更新')
  await load()
}

onMounted(load)
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.hint { color: var(--text-secondary); font-size: 13px; }
.user-cell { display: flex; align-items: center; gap: 10px; }
.avatar {
  width: 32px; height: 32px; border-radius: 50%;
  background: var(--brand-gradient); color: #fff;
  display: flex; align-items: center; justify-content: center; font-size: 13px; flex-shrink: 0;
}
.sub-text { font-size: 12px; color: var(--text-secondary); }
.pager { display: flex; justify-content: flex-end; margin-top: 14px; }
.role-sel { width: 96px; margin-left: 10px; }
</style>
