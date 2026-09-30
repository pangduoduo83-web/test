<template>
  <div class="pf-feedbacks">
    <div class="intro"><div><div class="eyebrow">CROSS-TENANT SUPPORT</div><h2>问题反馈中心</h2><p>来自所有客户站点的反馈会汇总到这里，方便统一定位、分派和跟进。</p></div><div class="summary"><b>{{ items.length }}</b><span>当前筛选结果</span></div></div>
    <div class="panel">
      <div class="toolbar">
        <div class="filters">
          <el-select v-model="filters.tenantCode" clearable filterable placeholder="全部分站" style="width: 180px" @change="load"><el-option v-for="site in sites" :key="site.code" :label="site.name + '（' + site.code + '）'" :value="site.code" /></el-select>
          <el-select v-model="filters.status" clearable placeholder="全部状态" style="width: 130px" @change="load"><el-option label="待处理" value="OPEN" /><el-option label="处理中" value="IN_PROGRESS" /><el-option label="已解决" value="RESOLVED" /><el-option label="已关闭" value="CLOSED" /></el-select>
          <el-select v-model="filters.category" clearable placeholder="全部类型" style="width: 130px" @change="load"><el-option label="流程问题" value="PROCESS" /><el-option label="操作问题" value="OPERATION" /><el-option label="优化建议" value="SUGGESTION" /><el-option label="其他" value="OTHER" /></el-select>
          <el-input v-model="filters.keyword" clearable placeholder="搜索标题、用户或分站" style="width: 220px" @keyup.enter="load" @clear="load" />
          <el-button @click="load">查询</el-button>
        </div>
      </div>
      <el-table :data="pageItems" stripe @row-click="openDetail">
        <el-table-column label="反馈" min-width="280"><template #default="{ row }"><div class="title"><span class="dot" :class="'dot-' + row.category.toLowerCase()"></span><strong>{{ row.title }}</strong></div><div class="preview">{{ row.content }}</div></template></el-table-column>
        <el-table-column label="分站" width="165"><template #default="{ row }"><div>{{ row.tenantName }}</div><small>{{ row.tenantCode }}</small></template></el-table-column>
        <el-table-column label="提交人" width="135"><template #default="{ row }"><div>{{ row.userName }}</div><small>{{ roleText(row.userRole) }}</small></template></el-table-column>
        <el-table-column label="类型" width="100"><template #default="{ row }">{{ categoryText(row.category) }}</template></el-table-column>
        <el-table-column label="状态" width="100"><template #default="{ row }"><span class="status" :class="'status-' + row.status.toLowerCase()">{{ statusText(row.status) }}</span></template></el-table-column>
        <el-table-column label="时间" width="145"><template #default="{ row }">{{ formatTime(row.createdAt) }}</template></el-table-column>
        <el-table-column label="操作" width="80" fixed="right"><template #default="{ row }"><el-button size="small" type="primary" plain @click.stop="openDetail(row)">处理</el-button></template></el-table-column>
      </el-table>
      <div class="pager"><el-pagination v-model:current-page="page" :page-size="pageSize" :total="items.length" layout="total, prev, pager, next" background /></div>
    </div>

    <el-drawer v-model="detailVisible" title="反馈详情" size="540px">
      <template v-if="detail">
        <div class="detail-meta"><span class="tenant-label">{{ detail.tenantName }} · {{ detail.tenantCode }}</span><span class="status" :class="'status-' + detail.status.toLowerCase()">{{ statusText(detail.status) }}</span><span class="time">{{ formatTime(detail.createdAt) }}</span></div>
        <h3 class="detail-title">{{ detail.title }}</h3>
        <div class="author">{{ detail.userName }} · {{ roleText(detail.userRole) }} · {{ categoryText(detail.category) }}<span v-if="detail.pageUrl"> · {{ detail.pageUrl }}</span></div>
        <div class="content">{{ detail.content }}</div>
        <div v-if="detailFiles.length" class="images"><a v-for="file in detailFiles" :key="file.url" :href="assetUrl(detail, file.url)" target="_blank" rel="noopener"><el-image :src="assetUrl(detail, file.url)" fit="cover" /><span>{{ file.name }}</span></a></div>
        <el-divider />
        <el-form :model="editForm" label-position="top"><el-form-item label="处理状态"><el-select v-model="editForm.status" style="width:100%"><el-option label="待处理" value="OPEN" /><el-option label="处理中" value="IN_PROGRESS" /><el-option label="已解决" value="RESOLVED" /><el-option label="已关闭" value="CLOSED" /></el-select></el-form-item><el-form-item label="内部处理备注 / 回复"><el-input v-model="editForm.adminReply" type="textarea" :rows="7" maxlength="5000" show-word-limit placeholder="记录排查结论、解决方案或后续安排" /></el-form-item></el-form>
        <div class="drawer-foot"><el-button @click="detailVisible = false">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存</el-button></div>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { hubFeedbacks, hubSites, hubTenants, hubUpdateFeedback } from '../../api/hub'

const items = ref([]); const sites = ref([]); const page = ref(1); const pageSize = 10; const detail = ref(null); const detailVisible = ref(false); const saving = ref(false)
const filters = reactive({ tenantCode: '', status: '', category: '', keyword: '' }); const editForm = reactive({ status: 'OPEN', adminReply: '' })
const pageItems = computed(() => items.value.slice((page.value - 1) * pageSize, page.value * pageSize))
const categoryText = (v) => ({ PROCESS: '流程问题', OPERATION: '操作问题', SUGGESTION: '优化建议', OTHER: '其他' }[v] || v)
const statusText = (v) => ({ OPEN: '待处理', IN_PROGRESS: '处理中', RESOLVED: '已解决', CLOSED: '已关闭' }[v] || v)
const roleText = (v) => ({ STUDENT: '学生', TEACHER: '教师', ADMIN: '分站管理员', LAB_ADMIN: '实验室管理员' }[v] || v)
const formatTime = (v) => (v || '').replace('T', ' ').slice(0, 16)
const parseFiles = (item) => { try { return item?.attachments ? JSON.parse(item.attachments) : [] } catch { return [] } }
const detailFiles = computed(() => parseFiles(detail.value))
const siteByCode = computed(() => Object.fromEntries(sites.value.map(s => [s.code, s])))
const assetUrl = (row, url) => { if (!url) return ''; if (/^https?:\/\//i.test(url)) return url; const base = siteByCode.value[row.tenantCode]?.siteUrl; return base ? base.replace(/\/$/, '') + url : url }
const load = async () => { items.value = await hubFeedbacks({ tenantCode: filters.tenantCode || undefined, status: filters.status || undefined, category: filters.category || undefined, keyword: filters.keyword.trim() || undefined }); page.value = 1 }
const openDetail = (row) => { detail.value = row; Object.assign(editForm, { status: row.status, adminReply: row.adminReply || '' }); detailVisible.value = true }
const save = async () => { saving.value = true; try { const updated = await hubUpdateFeedback(detail.value.id, editForm); const index = items.value.findIndex(i => i.id === updated.id); if (index >= 0) items.value[index] = updated; detailVisible.value = false; ElMessage.success('处理结果已保存') } finally { saving.value = false } }
watch(() => detailVisible.value, open => { if (!open) detail.value = null })
onMounted(async () => {
  try { sites.value = await hubSites() } catch (e) {
    try { sites.value = await hubTenants() } catch (ignored) { /* 反馈列表本身仍可用 */ }
  }
  await load()
})
</script>

<style scoped>
.pf-feedbacks { max-width: 1360px; margin: 0 auto; }.intro { display: flex; justify-content: space-between; align-items: flex-end; gap: 16px; margin-bottom: 18px; }.eyebrow { color: #38bdf8; font-size: 10px; letter-spacing: .14em; font-weight: 700; }.intro h2 { margin: 5px 0 6px; color: #f8fafc; font-size: 25px; }.intro p { margin: 0; color: #94a3b8; font-size: 13px; }.summary { display: flex; flex-direction: column; align-items: flex-end; color: #94a3b8; font-size: 11px; }.summary b { color: #e2e8f0; font-size: 24px; }.panel { background: #fff; border-radius: 14px; padding: 18px; box-shadow: 0 10px 30px rgba(15,23,42,.12); }.toolbar { margin-bottom: 13px; }.filters { display: flex; gap: 9px; flex-wrap: wrap; }.title { display: flex; align-items: center; gap: 8px; }.preview { color: #94a3b8; font-size: 12px; margin: 5px 0 0 16px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }small { color: #94a3b8; font-size: 11px; }.dot { width: 8px; height: 8px; border-radius: 50%; }.dot-process { background: #f59e0b; }.dot-operation { background: #3b82f6; }.dot-suggestion { background: #8b5cf6; }.dot-other { background: #94a3b8; }.status { font-size: 11px; border-radius: 999px; padding: 4px 8px; white-space: nowrap; }.status-open { color: #b45309; background: #fffbeb; }.status-in_progress { color: #2563eb; background: #eff6ff; }.status-resolved { color: #15803d; background: #f0fdf4; }.status-closed { color: #64748b; background: #f1f5f9; }.pager { display: flex; justify-content: flex-end; margin-top: 14px; }.detail-meta { display: flex; align-items: center; gap: 9px; }.tenant-label { color: #0369a1; background: #e0f2fe; border-radius: 999px; padding: 4px 8px; font-size: 11px; }.time { margin-left: auto; color: #94a3b8; font-size: 12px; }.detail-title { color: #1e293b; font-size: 20px; margin: 18px 0 7px; }.author { color: #64748b; font-size: 12px; }.content { margin-top: 16px; padding: 12px; background: #f8fafc; border-radius: 9px; color: #334155; line-height: 1.75; white-space: pre-wrap; }.images { display: grid; grid-template-columns: repeat(3, 1fr); gap: 8px; margin-top: 14px; }.images a { color: #64748b; font-size: 11px; text-decoration: none; overflow: hidden; }.images :deep(.el-image) { display: block; width: 100%; height: 100px; border-radius: 7px; margin-bottom: 4px; }.images span { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }.drawer-foot { display: flex; justify-content: flex-end; gap: 9px; margin-top: 24px; }
</style>
