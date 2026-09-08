<template>
  <div>
    <div class="pf-stats">
      <div class="pf-card pf-stat">
        <span class="pf-stat-icon" style="background:#fffbeb;color:#d97706"><Clock3 :size="20" /></span>
        <div><div class="pf-stat-value">{{ stats.pending ?? '–' }}</div><div class="pf-stat-label">待审核</div></div>
      </div>
      <div class="pf-card pf-stat">
        <span class="pf-stat-icon" style="background:#ecfdf5;color:#059669"><CircleCheckBig :size="20" /></span>
        <div><div class="pf-stat-value">{{ stats.approved ?? '–' }}</div><div class="pf-stat-label">已上架</div></div>
      </div>
      <div class="pf-card pf-stat">
        <span class="pf-stat-icon" style="background:#eef2ff;color:#4f46e5"><Building2 :size="20" /></span>
        <div><div class="pf-stat-value">{{ stats.tenants ?? '–' }}</div><div class="pf-stat-label">接入客户</div></div>
      </div>
      <div class="pf-card pf-stat">
        <span class="pf-stat-icon" style="background:#f0f9ff;color:#0284c7"><Download :size="20" /></span>
        <div><div class="pf-stat-value">{{ stats.installs ?? '–' }}</div><div class="pf-stat-label">累计安装</div></div>
      </div>
    </div>

    <div class="pf-card">
      <div class="pf-card-head">
        <div class="pf-seg">
          <button v-for="s in segments" :key="s.value" :class="{ active: status === s.value }" @click="status = s.value; load()">
            {{ s.label }}<span v-if="s.count !== undefined" class="count">{{ s.count }}</span>
          </button>
        </div>
        <div class="pf-toolbar">
          <el-input v-model="keyword" placeholder="搜索标题 / 发布方" clearable style="width:240px">
            <template #prefix><Search :size="14" /></template>
          </el-input>
          <el-button @click="load"><RefreshCw :size="14" style="margin-right:6px" />刷新</el-button>
        </div>
      </div>

      <el-table :data="filtered" @row-click="open" row-class-name="clickable">
        <el-table-column label="项目" min-width="300">
          <template #default="{ row }">
            <div class="pf-title-cell">
              <img v-if="row.coverUrl" :src="row.coverUrl" class="pf-thumb" alt="" />
              <span v-else class="pf-thumb">📦</span>
              <div>
                <div class="t">{{ row.title }}</div>
                <div class="s">#{{ row.id }} · {{ row.category || '未分类' }} · {{ (row.tags || []).slice(0, 3).join(' / ') }}</div>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="发布方" width="180">
          <template #default="{ row }">
            <div>{{ row.publisherTenantName }}</div>
            <div class="s2">{{ row.publisherUserName || '–' }}</div>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="120">
          <template #default="{ row }"><span class="pf-pill" :class="statusType(row.reviewStatus)">{{ statusText(row.reviewStatus) }}</span></template>
        </el-table-column>
        <el-table-column label="版本" width="130">
          <template #default="{ row }">
            <div>上架 <b>{{ row.currentVersionNo ? 'v' + row.currentVersionNo : '–' }}</b></div>
            <div class="s2">最新 v{{ row.latestVersionNo }}</div>
          </template>
        </el-table-column>
        <el-table-column label="可见范围" width="130">
          <template #default="{ row }"><span class="pf-pill" :class="row.visibility === 'PUBLIC' ? 'primary' : 'info'">{{ row.visibility === 'PUBLIC' ? '所有客户' : '指定客户' }}</span></template>
        </el-table-column>
        <el-table-column prop="installCount" label="安装" width="80" />
        <el-table-column label="更新时间" width="150">
          <template #default="{ row }"><span class="s2">{{ fmt(row.updatedAt) }}</span></template>
        </el-table-column>
        <el-table-column width="110" align="right">
          <template #default="{ row }"><el-button size="small" type="primary" plain @click.stop="open(row)">处理</el-button></template>
        </el-table-column>
        <template #empty>
          <div class="pf-empty">
            <div class="pf-empty-icon">🗂️</div>
            <div class="pf-empty-title">{{ status === 'PENDING' ? '没有待审核的条目' : '这里还没有条目' }}</div>
            <div>客户站点在「管理后台 → 项目商店 → 本站发布」提交的项目会出现在这里</div>
          </div>
        </template>
      </el-table>
    </div>

    <!-- 处理抽屉 -->
    <el-drawer v-model="visible" :title="detail ? detail.title : ''" size="680px" class="pf-root">
      <div v-if="detail" class="detail">
        <div class="detail-top">
          <img v-if="detail.coverUrl" :src="detail.coverUrl" class="detail-cover" alt="" />
          <div class="detail-meta">
            <div class="row"><span class="pf-pill" :class="statusType(detail.reviewStatus)">{{ statusText(detail.reviewStatus) }}</span>
              <span class="pf-pill" :class="detail.visibility === 'PUBLIC' ? 'primary' : 'info'">{{ detail.visibility === 'PUBLIC' ? '所有客户可见' : '仅指定客户可见' }}</span></div>
            <dl class="pf-kv">
              <dt>发布方</dt><dd>{{ detail.publisherTenantName }} · {{ detail.publisherUserName || '–' }}</dd>
              <dt>分类</dt><dd>{{ detail.category || '–' }}</dd>
              <dt>版本</dt><dd>上架 {{ detail.currentVersionNo ? 'v' + detail.currentVersionNo : '–' }} / 最新 v{{ detail.latestVersionNo }}</dd>
              <dt>简介</dt><dd>{{ detail.summary || '–' }}</dd>
            </dl>
          </div>
        </div>
        <div v-if="detail.reviewComment" class="pf-note warning" style="margin-top:14px"><MessageSquareText :size="15" />上次审核意见:{{ detail.reviewComment }}</div>

        <div class="pf-section-title">审核最新版本 v{{ detail.latestVersionNo }}</div>
        <div class="pf-card review-box">
          <el-input v-model="comment" type="textarea" :autosize="{ minRows: 2, maxRows: 5 }" placeholder="审核意见(驳回时必填,会展示给发布方)" />
          <div class="review-actions">
            <el-button text @click="preview"><Eye :size="14" style="margin-right:6px" />预览内容</el-button>
            <span class="spacer"></span>
            <el-button :loading="acting" @click="review('OFFLINE')" :disabled="detail.reviewStatus === 'OFFLINE'">下架</el-button>
            <el-button type="danger" plain :loading="acting" @click="review('REJECTED')">驳回</el-button>
            <el-button type="primary" :loading="acting" @click="review('APPROVED')">通过并上架</el-button>
          </div>
        </div>

        <div class="pf-section-title">可见范围</div>
        <div class="pf-card pf-card-body vis-box">
          <div class="pf-seg">
            <button :class="{ active: visibility === 'PUBLIC' }" @click="saveVisibility('PUBLIC')">所有客户可见</button>
            <button :class="{ active: visibility === 'RESTRICTED' }" @click="saveVisibility('RESTRICTED')">仅指定客户可见</button>
          </div>
          <div class="grant-row">
            <el-select v-model="grantIds" multiple filterable collapse-tags collapse-tags-tooltip placeholder="选择可见的客户(任何可见范围下都可额外指定)" style="flex:1">
              <el-option v-for="t in tenants" :key="t.id" :value="t.id" :label="`${t.name}(${t.code})`" />
            </el-select>
            <el-button type="primary" plain :loading="acting" @click="saveGrants">保存名单</el-button>
          </div>
          <div class="hint">{{ visibility === 'PUBLIC' ? '当前所有已接入客户都能浏览并安装;名单用于额外记录重点分享对象。' : '当前只有名单中的客户能在商店里看到此项目。' }}</div>
        </div>

        <div class="pf-section-title">版本与审核记录</div>
        <el-table :data="detail.versions" size="small">
          <el-table-column prop="versionNo" label="版本" width="70"><template #default="{ row }">v{{ row.versionNo }}</template></el-table-column>
          <el-table-column prop="changelog" label="说明" show-overflow-tooltip />
          <el-table-column prop="createdBy" label="提交人" width="130" show-overflow-tooltip />
          <el-table-column label="时间" width="140"><template #default="{ row }"><span class="s2">{{ fmt(row.createdAt) }}</span></template></el-table-column>
          <el-table-column width="80"><template #default="{ row }"><span v-if="row.current" class="pf-pill success">上架中</span></template></el-table-column>
        </el-table>
        <el-timeline class="logs">
          <el-timeline-item v-for="l in detail.reviewLogs" :key="l.id" :timestamp="fmt(l.reviewedAt)" placement="top">
            <b>{{ l.reviewer }}</b> {{ statusText(l.decision) }}<span v-if="l.comment"> · {{ l.comment }}</span>
          </el-timeline-item>
        </el-timeline>
      </div>
    </el-drawer>

    <el-dialog v-model="previewVisible" title="最新版本内容(JSON)" width="760px" class="pf-root">
      <pre class="json">{{ previewJson }}</pre>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Building2, CircleCheckBig, Clock3, Download, Eye, MessageSquareText, RefreshCw, Search } from 'lucide-vue-next'
import { hubGrants, hubItem, hubItemPayload, hubItems, hubReview, hubStats, hubTenants, hubVisibility } from '../../api/hub'

const emit = defineEmits(['refresh-stats'])
const status = ref('PENDING')
const keyword = ref('')
const items = ref([])
const stats = ref({})
const tenants = ref([])
const visible = ref(false)
const detail = ref(null)
const comment = ref('')
const visibility = ref('PUBLIC')
const grantIds = ref([])
const acting = ref(false)
const previewVisible = ref(false)
const previewJson = ref('')

const segments = computed(() => [
  { value: 'PENDING', label: '待审核', count: stats.value.pending },
  { value: 'APPROVED', label: '已上架', count: stats.value.approved },
  { value: 'REJECTED', label: '已驳回', count: stats.value.rejected },
  { value: 'OFFLINE', label: '已下架', count: stats.value.offline },
  { value: 'ALL', label: '全部' }
])
const filtered = computed(() => {
  const k = keyword.value.trim().toLowerCase()
  return items.value.filter((i) => !k || (i.title + (i.publisherTenantName || '')).toLowerCase().includes(k))
})

const load = async () => {
  const [list, s] = await Promise.all([hubItems(status.value), hubStats()])
  items.value = list
  stats.value = s
  emit('refresh-stats')
}

const open = async (row) => {
  detail.value = await hubItem(row.id)
  comment.value = ''
  visibility.value = detail.value.visibility
  grantIds.value = (detail.value.grants || []).map((g) => g.tenantId)
  if (tenants.value.length === 0) tenants.value = await hubTenants()
  visible.value = true
}

const review = async (decision) => {
  if (decision === 'REJECTED' && !comment.value.trim()) { ElMessage.warning('驳回请填写审核意见'); return }
  acting.value = true
  try {
    await hubReview(detail.value.id, { decision, comment: comment.value })
    ElMessage.success({ APPROVED: '已上架', REJECTED: '已驳回', OFFLINE: '已下架' }[decision])
    detail.value = await hubItem(detail.value.id)
    comment.value = ''
    await load()
  } finally {
    acting.value = false
  }
}

const saveVisibility = async (v) => {
  if (v === visibility.value) return
  await hubVisibility(detail.value.id, v)
  visibility.value = v
  ElMessage.success(v === 'PUBLIC' ? '已设为所有客户可见' : '已设为仅指定客户可见')
  await load()
}

const saveGrants = async () => {
  acting.value = true
  try {
    detail.value = await hubGrants(detail.value.id, grantIds.value)
    ElMessage.success('分享名单已保存')
  } finally {
    acting.value = false
  }
}

const preview = async () => {
  previewJson.value = JSON.stringify(await hubItemPayload(detail.value.id), null, 2)
  previewVisible.value = true
}

const statusText = (s) => ({ PENDING: '待审核', APPROVED: '已上架', REJECTED: '已驳回', OFFLINE: '已下架' }[s] || s)
const statusType = (s) => ({ PENDING: 'warning', APPROVED: 'success', REJECTED: 'danger', OFFLINE: 'info' }[s] || 'info')
const fmt = (t) => (t ? String(t).replace('T', ' ').slice(0, 16) : '–')

onMounted(load)
</script>

<style scoped>
.s2 { font-size: 12px; color: var(--pf-text-3); }
:deep(.clickable) { cursor: pointer; }
.detail-top { display: flex; gap: 18px; }
.detail-cover { width: 200px; height: 130px; object-fit: cover; border-radius: 12px; flex-shrink: 0; background: #eef0f6; }
.detail-meta { flex: 1; min-width: 0; }
.detail-meta .row { display: flex; gap: 8px; margin-bottom: 12px; }
.review-box { padding: 16px; display: flex; flex-direction: column; gap: 12px; }
.review-actions { display: flex; align-items: center; gap: 8px; }
.review-actions .spacer { flex: 1; }
.vis-box { display: flex; flex-direction: column; gap: 12px; }
.grant-row { display: flex; gap: 10px; align-items: center; }
.hint { font-size: 12px; color: var(--pf-text-3); }
.logs { margin-top: 16px; padding-left: 4px; }
.json { background: #0f172a; color: #e2e8f0; padding: 16px; border-radius: 12px; max-height: 60vh; overflow: auto; font-size: 12px; margin: 0; }
</style>
