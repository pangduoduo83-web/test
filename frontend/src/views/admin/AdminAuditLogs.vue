<template>
  <div class="card">
    <div class="toolbar">
      <div class="filters">
        <el-input v-model="path" placeholder="按接口路径过滤,如 users / equipment" clearable style="width:260px" @keyup.enter="reload" @clear="reload" />
        <el-input-number v-model="actorId" placeholder="操作人 id" :min="1" :controls="false" style="width:130px" />
        <el-button @click="reload">查询</el-button>
      </div>
      <span class="hint">记录管理端、教师端、平台接口的所有新增 / 修改 / 删除操作,共 {{ total }} 条</span>
    </div>

    <el-table :data="items" stripe size="small">
      <el-table-column label="时间" width="150"><template #default="{ row }">{{ fmt(row.createdAt) }}</template></el-table-column>
      <el-table-column label="操作人" width="160">
        <template #default="{ row }">
          <b>{{ row.actorName || '–' }}</b>
          <div class="sub-text">{{ roleText(row.actorRole) }}<span v-if="row.actorId"> · #{{ row.actorId }}</span></div>
        </template>
      </el-table-column>
      <el-table-column label="操作" min-width="220"><template #default="{ row }">{{ row.summary }}</template></el-table-column>
      <el-table-column label="接口" min-width="260">
        <template #default="{ row }"><span class="mono">{{ row.method }} {{ row.path }}</span></template>
      </el-table-column>
      <el-table-column label="结果" width="90">
        <template #default="{ row }"><span class="badge" :class="row.status < 400 ? 'badge-green' : 'badge-red'">{{ row.status }}</span></template>
      </el-table-column>
      <el-table-column prop="ip" label="IP" width="140" />
    </el-table>

    <div class="pager">
      <el-pagination layout="total, prev, pager, next" :total="total" :page-size="size" :current-page="page + 1" background
                     @current-change="(p) => { page = p - 1; load() }" />
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { adminAuditLogs } from '../../api'

const items = ref([])
const total = ref(0)
const page = ref(0)
const size = 20
const path = ref('')
const actorId = ref(null)

const fmt = (t) => (t ? String(t).replace('T', ' ').slice(0, 19) : '')
const roleText = (r) => ({ ADMIN: '管理员', TEACHER: '教师', LAB_ADMIN: '实验室管理员', STUDENT: '学生', PLATFORM: '平台' }[r] || r || '–')

const load = async () => {
  const r = await adminAuditLogs({ page: page.value, size, path: path.value || undefined, actorId: actorId.value || undefined })
  items.value = r.items
  total.value = r.total
}
const reload = () => { page.value = 0; load() }

onMounted(load)
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; align-items: center; gap: 12px; margin-bottom: 16px; flex-wrap: wrap; }
.filters { display: flex; gap: 10px; align-items: center; flex-wrap: wrap; }
.hint, .sub-text { color: var(--text-secondary); font-size: 12px; }
.mono { font-family: ui-monospace, Menlo, Consolas, monospace; font-size: 12px; }
.pager { display: flex; justify-content: flex-end; margin-top: 14px; }
</style>
