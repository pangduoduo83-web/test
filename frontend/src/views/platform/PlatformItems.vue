<template>
  <div>
    <div class="toolbar">
      <el-radio-group v-model="status" @change="load">
        <el-radio-button value="PENDING">待审核</el-radio-button>
        <el-radio-button value="APPROVED">已上架</el-radio-button>
        <el-radio-button value="REJECTED">已驳回</el-radio-button>
        <el-radio-button value="OFFLINE">已下架</el-radio-button>
        <el-radio-button value="ALL">全部</el-radio-button>
      </el-radio-group>
      <el-button @click="load">刷新</el-button>
    </div>

    <el-table :data="items" stripe>
      <el-table-column prop="id" label="条目" width="70" />
      <el-table-column label="封面" width="90">
        <template #default="{ row }">
          <img v-if="row.coverUrl" :src="row.coverUrl" class="thumb" alt="" />
          <span v-else class="thumb placeholder">📦</span>
        </template>
      </el-table-column>
      <el-table-column prop="title" label="标题" min-width="200" show-overflow-tooltip />
      <el-table-column prop="publisherTenantName" label="发布方" width="140" show-overflow-tooltip />
      <el-table-column prop="category" label="分类" width="110" />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="statusType(row.reviewStatus)" size="small">{{ statusText(row.reviewStatus) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="版本" width="130">
        <template #default="{ row }">上架 v{{ row.currentVersionNo ?? '-' }} / 最新 v{{ row.latestVersionNo }}</template>
      </el-table-column>
      <el-table-column label="可见" width="100">
        <template #default="{ row }">{{ row.visibility === 'PUBLIC' ? '公开' : '定向' }}</template>
      </el-table-column>
      <el-table-column prop="installCount" label="安装" width="70" />
      <el-table-column prop="updatedAt" label="更新时间" width="165" />
      <el-table-column label="操作" width="120" fixed="right">
        <template #default="{ row }">
          <el-button size="small" type="primary" @click="open(row)">审核 / 分享</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-drawer v-model="visible" :title="detail ? `#${detail.id} ${detail.title}` : ''" size="640px">
      <div v-if="detail" class="detail">
        <el-descriptions :column="2" size="small" border>
          <el-descriptions-item label="发布方">{{ detail.publisherTenantName }} / {{ detail.publisherUserName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="分类">{{ detail.category || '-' }}</el-descriptions-item>
          <el-descriptions-item label="状态"><el-tag :type="statusType(detail.reviewStatus)" size="small">{{ statusText(detail.reviewStatus) }}</el-tag></el-descriptions-item>
          <el-descriptions-item label="版本">上架 v{{ detail.currentVersionNo ?? '-' }} / 最新 v{{ detail.latestVersionNo }}</el-descriptions-item>
          <el-descriptions-item label="简介" :span="2">{{ detail.summary || '-' }}</el-descriptions-item>
          <el-descriptions-item label="标签" :span="2">
            <el-tag v-for="t in detail.tags" :key="t" size="small" effect="plain" class="tag">{{ t }}</el-tag>
          </el-descriptions-item>
        </el-descriptions>

        <h4>审核</h4>
        <div class="row">
          <el-input v-model="comment" placeholder="审核意见(驳回时必填)" style="flex:1" />
          <el-button type="success" :loading="acting" @click="review('APPROVED')">通过并上架 v{{ detail.latestVersionNo }}</el-button>
          <el-button type="danger" :loading="acting" @click="review('REJECTED')">驳回</el-button>
          <el-button :loading="acting" @click="review('OFFLINE')">下架</el-button>
        </div>
        <el-button text size="small" @click="preview">预览最新版本内容(JSON)</el-button>

        <h4>可见范围与定向分享</h4>
        <div class="row">
          <el-radio-group v-model="visibility" @change="saveVisibility">
            <el-radio-button value="PUBLIC">所有客户可见</el-radio-button>
            <el-radio-button value="RESTRICTED">仅指定客户可见</el-radio-button>
          </el-radio-group>
        </div>
        <div class="row">
          <el-select v-model="grantIds" multiple filterable placeholder="选择可见的客户(任何可见范围下都可以额外指定)" style="flex:1">
            <el-option v-for="t in tenants" :key="t.id" :value="t.id" :label="`${t.name}(${t.code})`" />
          </el-select>
          <el-button type="primary" :loading="acting" @click="saveGrants">保存分享名单</el-button>
        </div>

        <h4>版本记录</h4>
        <el-table :data="detail.versions" size="small">
          <el-table-column prop="versionNo" label="版本" width="70" />
          <el-table-column prop="changelog" label="说明" show-overflow-tooltip />
          <el-table-column prop="createdBy" label="提交人" width="140" />
          <el-table-column prop="createdAt" label="时间" width="165" />
          <el-table-column label="上架中" width="80">
            <template #default="{ row }"><el-tag v-if="row.current" size="small" type="success">是</el-tag></template>
          </el-table-column>
        </el-table>

        <h4>审核记录</h4>
        <el-timeline>
          <el-timeline-item v-for="l in detail.reviewLogs" :key="l.id" :timestamp="l.reviewedAt">
            {{ l.reviewer }} · {{ statusText(l.decision) }} {{ l.comment ? '· ' + l.comment : '' }}
          </el-timeline-item>
        </el-timeline>
      </div>
    </el-drawer>

    <el-dialog v-model="previewVisible" title="最新版本内容" width="720px">
      <pre class="json">{{ previewJson }}</pre>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { hubGrants, hubItem, hubItemPayload, hubItems, hubReview, hubTenants, hubVisibility } from '../../api/hub'

const emit = defineEmits(['refresh-stats'])
const status = ref('PENDING')
const items = ref([])
const tenants = ref([])
const visible = ref(false)
const detail = ref(null)
const comment = ref('')
const visibility = ref('PUBLIC')
const grantIds = ref([])
const acting = ref(false)
const previewVisible = ref(false)
const previewJson = ref('')

const load = async () => {
  items.value = await hubItems(status.value)
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
  acting.value = true
  try {
    await hubReview(detail.value.id, { decision, comment: comment.value })
    ElMessage.success({ APPROVED: '已上架', REJECTED: '已驳回', OFFLINE: '已下架' }[decision])
    detail.value = await hubItem(detail.value.id)
    await load()
  } finally {
    acting.value = false
  }
}

const saveVisibility = async (v) => {
  await hubVisibility(detail.value.id, v)
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
const statusType = (s) => ({ PENDING: 'warning', APPROVED: 'success', REJECTED: 'danger', OFFLINE: 'info' }[s] || '')

onMounted(load)
</script>

<style scoped>
.toolbar { display: flex; gap: 12px; align-items: center; margin-bottom: 14px; }
.thumb { width: 56px; height: 40px; object-fit: cover; border-radius: 6px; display: inline-flex; align-items: center; justify-content: center; background: #f3f4f6; }
.placeholder { font-size: 20px; }
.detail h4 { margin: 18px 0 10px; }
.row { display: flex; gap: 10px; align-items: center; margin-bottom: 10px; flex-wrap: wrap; }
.tag { margin-right: 6px; }
.json { background: #0f172a; color: #e2e8f0; padding: 14px; border-radius: 10px; max-height: 60vh; overflow: auto; font-size: 12px; }
</style>
