<template>
  <div class="card">
    <div class="toolbar">
      <div class="filters">
        <el-select v-model="filters.projectId" placeholder="全部项目" clearable filterable
                   style="width:240px" @change="load">
          <el-option v-for="p in projects" :key="p.id" :label="p.title" :value="p.id" />
        </el-select>
        <el-input v-model="filters.keyword" placeholder="搜索内容 / 用户名" clearable
                  style="width:220px" @keyup.enter="load" @clear="load" />
        <el-button @click="load">刷新</el-button>
      </div>
      <span class="hint">删除主题帖会同时删除其全部回复</span>
    </div>

    <el-table :data="pageItems" stripe>
      <el-table-column label="项目" min-width="180">
        <template #default="{ row }">{{ projectTitle(row.projectId) }}</template>
      </el-table-column>
      <el-table-column prop="userName" label="用户" width="110" />
      <el-table-column label="类型" width="90">
        <template #default="{ row }">
          <span class="badge" :class="row.parentId ? 'badge-gray' : 'badge-blue'">
            {{ row.parentId ? '回复' : '主题帖' }}
          </span>
        </template>
      </el-table-column>
      <el-table-column prop="content" label="内容" min-width="300" show-overflow-tooltip />
      <el-table-column label="发布时间" width="145">
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
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminDeleteDiscussion, adminListDiscussions, adminListProjects } from '../../api'

const items = ref([])
const projects = ref([])
const filters = reactive({ projectId: null, keyword: '' })
const page = ref(1)
const pageSize = 10

const pageItems = computed(() =>
  items.value.slice((page.value - 1) * pageSize, page.value * pageSize))

const projectTitle = (id) => projects.value.find((p) => p.id === id)?.title || `项目#${id}`

const formatTime = (t) => (t || '').replace('T', ' ').slice(0, 16)

const load = async () => {
  items.value = await adminListDiscussions({
    projectId: filters.projectId || undefined,
    keyword: filters.keyword.trim() || undefined
  })
  page.value = 1
}

const remove = async (row) => {
  const tip = row.parentId
    ? `确定删除 ${row.userName} 的这条回复?`
    : `确定删除 ${row.userName} 的主题帖?其下所有回复将一并删除`
  try {
    await ElMessageBox.confirm(tip, '删除讨论', { type: 'warning', confirmButtonText: '删除', confirmButtonClass: 'el-button--danger' })
  } catch (e) {
    return
  }
  const removed = await adminDeleteDiscussion(row.id)
  ElMessage.success(`已删除 ${removed} 条讨论`)
  load()
}

onMounted(async () => {
  try {
    projects.value = await adminListProjects()
  } catch (e) { /* 已提示 */ }
  load()
})
</script>

<style scoped>
.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 14px;
  gap: 12px;
  flex-wrap: wrap;
}
.filters { display: flex; gap: 10px; flex-wrap: wrap; }
.hint { font-size: 12px; color: var(--text-secondary); }
.pager { display: flex; justify-content: flex-end; margin-top: 14px; }
</style>
