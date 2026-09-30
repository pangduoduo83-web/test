<template>
  <div class="feedback-admin">
    <div class="page-heading"><div><h2>问题反馈</h2><p>集中查看和跟进本站用户提交的流程问题、操作问题与优化建议。</p></div><el-button @click="load">刷新</el-button></div>
    <div class="card">
      <div class="toolbar">
        <div class="filters">
          <el-select v-model="filters.status" clearable placeholder="全部状态" style="width: 140px" @change="load">
            <el-option label="待处理" value="OPEN" /><el-option label="处理中" value="IN_PROGRESS" /><el-option label="已解决" value="RESOLVED" /><el-option label="已关闭" value="CLOSED" />
          </el-select>
          <el-select v-model="filters.category" clearable placeholder="全部类型" style="width: 140px" @change="load">
            <el-option label="流程问题" value="PROCESS" /><el-option label="操作问题" value="OPERATION" /><el-option label="优化建议" value="SUGGESTION" /><el-option label="其他" value="OTHER" />
          </el-select>
          <el-input v-model="filters.keyword" clearable placeholder="搜索标题、描述或用户" style="width: 240px" @keyup.enter="load" @clear="load" />
          <el-button @click="load">查询</el-button>
        </div>
        <span class="count">共 {{ items.length }} 条</span>
      </div>
      <el-table :data="pageItems" stripe @row-click="openDetail">
        <el-table-column label="反馈" min-width="280"><template #default="{ row }"><div class="feedback-title"><span class="category-dot" :class="'dot-' + row.category.toLowerCase()"></span><strong>{{ row.title }}</strong></div><div class="feedback-preview">{{ row.content }}</div></template></el-table-column>
        <el-table-column label="类型" width="105"><template #default="{ row }">{{ categoryText(row.category) }}</template></el-table-column>
        <el-table-column label="提交人" width="135"><template #default="{ row }"><div>{{ row.userName }}</div><small>{{ roleText(row.userRole) }}</small></template></el-table-column>
        <el-table-column label="状态" width="100"><template #default="{ row }"><span class="status" :class="'status-' + row.status.toLowerCase()">{{ statusText(row.status) }}</span></template></el-table-column>
        <el-table-column label="提交时间" width="150"><template #default="{ row }">{{ formatTime(row.createdAt) }}</template></el-table-column>
        <el-table-column label="操作" width="90" fixed="right"><template #default="{ row }"><el-button size="small" type="primary" plain @click.stop="openDetail(row)">处理</el-button></template></el-table-column>
      </el-table>
      <div class="pager"><el-pagination v-model:current-page="page" :page-size="pageSize" :total="items.length" layout="total, prev, pager, next" background /></div>
    </div>

    <el-drawer v-model="detailVisible" title="反馈详情" size="520px">
      <template v-if="detail">
        <div class="detail-meta"><span class="category-label">{{ categoryText(detail.category) }}</span><span class="status" :class="'status-' + detail.status.toLowerCase()">{{ statusText(detail.status) }}</span><span class="detail-time">{{ formatTime(detail.createdAt) }}</span></div>
        <h3 class="detail-title">{{ detail.title }}</h3>
        <div class="detail-author">提交人：{{ detail.userName }}（{{ roleText(detail.userRole) }}）<span v-if="detail.pageUrl"> · 页面：{{ detail.pageUrl }}</span></div>
        <div class="detail-content">{{ detail.content }}</div>
        <div v-if="detailFiles.length" class="detail-images"><el-image v-for="file in detailFiles" :key="file.url" :src="file.url" fit="cover" :preview-src-list="detailFiles.map(a => a.url)" /><small v-for="file in detailFiles" :key="file.url + '-name'">{{ file.name }}</small></div>
        <el-divider />
        <el-form :model="editForm" label-position="top"><el-form-item label="处理状态"><el-select v-model="editForm.status" style="width:100%"><el-option label="待处理" value="OPEN" /><el-option label="处理中" value="IN_PROGRESS" /><el-option label="已解决" value="RESOLVED" /><el-option label="已关闭" value="CLOSED" /></el-select></el-form-item><el-form-item label="回复用户"><el-input v-model="editForm.adminReply" type="textarea" :rows="6" maxlength="5000" show-word-limit placeholder="可填写处理结果、临时方案或后续安排" /></el-form-item></el-form>
        <div class="drawer-foot"><el-button @click="detailVisible = false">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存处理结果</el-button></div>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { adminListFeedbacks, adminUpdateFeedback } from '../../api'

const items = ref([]); const page = ref(1); const pageSize = 10; const detailVisible = ref(false); const detail = ref(null); const saving = ref(false)
const filters = reactive({ status: '', category: '', keyword: '' }); const editForm = reactive({ status: 'OPEN', adminReply: '' })
const pageItems = computed(() => items.value.slice((page.value - 1) * pageSize, page.value * pageSize))
const categoryText = (v) => ({ PROCESS: '流程问题', OPERATION: '操作问题', SUGGESTION: '优化建议', OTHER: '其他' }[v] || v)
const statusText = (v) => ({ OPEN: '待处理', IN_PROGRESS: '处理中', RESOLVED: '已解决', CLOSED: '已关闭' }[v] || v)
const roleText = (v) => ({ STUDENT: '学生', TEACHER: '教师', ADMIN: '管理员', LAB_ADMIN: '实验室管理员' }[v] || v)
const formatTime = (v) => (v || '').replace('T', ' ').slice(0, 16)
const parseFiles = (item) => { try { return item?.attachments ? JSON.parse(item.attachments) : [] } catch { return [] } }
const detailFiles = computed(() => parseFiles(detail.value))
const load = async () => { items.value = await adminListFeedbacks({ status: filters.status || undefined, category: filters.category || undefined, keyword: filters.keyword.trim() || undefined }); page.value = 1 }
const openDetail = (row) => { detail.value = row; Object.assign(editForm, { status: row.status, adminReply: row.adminReply || '' }); detailVisible.value = true }
const save = async () => { saving.value = true; try { const updated = await adminUpdateFeedback(detail.value.id, editForm); const index = items.value.findIndex(i => i.id === updated.id); if (index >= 0) items.value[index] = updated; detail.value = updated; ElMessage.success('处理结果已保存'); detailVisible.value = false } finally { saving.value = false } }
watch(() => detailVisible.value, (open) => { if (!open) detail.value = null })
onMounted(load)
</script>

<style scoped>
.feedback-admin { max-width: 1280px; margin: 0 auto; }.page-heading { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 18px; }.page-heading h2 { margin: 0 0 6px; font-size: 24px; }.page-heading p { margin: 0; color: #64748b; font-size: 13px; }.toolbar { display: flex; justify-content: space-between; align-items: center; gap: 12px; flex-wrap: wrap; margin-bottom: 14px; }.filters { display: flex; gap: 9px; flex-wrap: wrap; }.count { color: #94a3b8; font-size: 12px; }.feedback-title { display: flex; align-items: center; gap: 8px; }.feedback-preview { color: #94a3b8; font-size: 12px; margin: 5px 0 0 16px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }.category-dot { width: 8px; height: 8px; border-radius: 50%; flex: 0 0 auto; }.dot-process { background: #f59e0b; }.dot-operation { background: #3b82f6; }.dot-suggestion { background: #8b5cf6; }.dot-other { background: #94a3b8; }small { color: #94a3b8; font-size: 11px; }.status { font-size: 11px; border-radius: 999px; padding: 4px 8px; white-space: nowrap; }.status-open { color: #b45309; background: #fffbeb; }.status-in_progress { color: #2563eb; background: #eff6ff; }.status-resolved { color: #15803d; background: #f0fdf4; }.status-closed { color: #64748b; background: #f1f5f9; }.pager { display: flex; justify-content: flex-end; margin-top: 14px; }.detail-meta { display: flex; align-items: center; gap: 9px; }.category-label { color: #2563eb; background: #eff6ff; border-radius: 999px; padding: 4px 8px; font-size: 11px; }.detail-time { margin-left: auto; color: #94a3b8; font-size: 12px; }.detail-title { margin: 18px 0 7px; font-size: 20px; color: #1e293b; }.detail-author { color: #64748b; font-size: 12px; }.detail-content { white-space: pre-wrap; line-height: 1.75; color: #334155; background: #f8fafc; border-radius: 9px; padding: 12px; margin-top: 16px; }.detail-images { display: grid; grid-template-columns: repeat(3, 1fr); gap: 8px; margin-top: 14px; }.detail-images :deep(.el-image) { width: 100%; height: 100px; border-radius: 7px; }.detail-images small { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }.drawer-foot { display: flex; justify-content: flex-end; gap: 9px; margin-top: 24px; }
</style>
