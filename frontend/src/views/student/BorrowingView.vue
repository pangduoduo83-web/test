<template>
  <div>
    <h2 class="page-title">借阅管理</h2>
    <p class="page-subtitle">查看和管理你的设备借阅申请</p>

    <!-- 统计卡 -->
    <div class="stat-grid">
      <div class="ref-stat-card">
        <div class="ref-stat-icon" style="background:#dbeafe">
          <ClipboardList :size="22" color="#2563eb" />
        </div>
        <div>
          <div class="ref-stat-value">{{ stats.total }}</div>
          <div class="ref-stat-label">累计借阅</div>
        </div>
      </div>
      <div class="ref-stat-card">
        <div class="ref-stat-icon" style="background:#fef9c3">
          <Clock :size="22" color="#ca8a04" />
        </div>
        <div>
          <div class="ref-stat-value">{{ stats.pending }}</div>
          <div class="ref-stat-label">审批中</div>
        </div>
      </div>
      <div class="ref-stat-card">
        <div class="ref-stat-icon" style="background:#dcfce7">
          <Package :size="22" color="#16a34a" />
        </div>
        <div>
          <div class="ref-stat-value">{{ stats.borrowing }}</div>
          <div class="ref-stat-label">借用中</div>
        </div>
      </div>
      <div class="ref-stat-card">
        <div class="ref-stat-icon" style="background:#ccfbf1">
          <CheckCircle :size="22" color="#0d9488" />
        </div>
        <div>
          <div class="ref-stat-value">{{ stats.returned }}</div>
          <div class="ref-stat-label">已归还</div>
        </div>
      </div>
    </div>

    <!-- 记录卡片(含状态 tab) -->
    <div class="card records-card">
      <el-tabs v-model="tab">
        <el-tab-pane label="全部记录" name="ALL" />
        <el-tab-pane label="审批中" name="PENDING" />
        <el-tab-pane label="借用中" name="APPROVED" />
        <el-tab-pane label="已归还" name="RETURNED" />
        <el-tab-pane label="已拒绝" name="REJECTED" />
      </el-tabs>

      <div v-if="tabItems.length === 0" class="empty-box">
        <div class="empty-icon"><Inbox :size="44" color="#d1d5db" /></div>
        <h3>暂无记录</h3>
        <p>该状态下没有借阅记录</p>
      </div>

      <div v-for="b in pageItems" :key="b.id" class="borrow-item">
        <div class="bi-main">
          <div class="bi-no-row">
            <span class="bi-no">{{ b.requestNo }}</span>
            <span class="badge" :class="statusBadge(b.status)">{{ statusText(b.status) }}</span>
          </div>
          <div class="bi-name">{{ b.equipmentName }}</div>
          <div class="bi-purpose">用途: {{ b.purpose }}{{ b.projectName ? ' · ' + b.projectName : '' }}</div>
          <div class="bi-grid">
            <div class="bi-cell">
              <span class="bi-cell-label">申请日期</span>
              <span class="bi-cell-value">{{ (b.appliedAt || '').slice(0, 10) }}</span>
            </div>
            <div class="bi-cell">
              <span class="bi-cell-label">借用数量</span>
              <span class="bi-cell-value">{{ b.quantity }}件</span>
            </div>
            <div class="bi-cell">
              <span class="bi-cell-label">借用周期</span>
              <span class="bi-cell-value strong">{{ b.startDate }} 至 {{ endDate(b) }}</span>
            </div>
            <div class="bi-cell">
              <span class="bi-cell-label">审批人</span>
              <span class="bi-cell-value">{{ b.approverName || '-' }}</span>
            </div>
          </div>
          <div v-if="b.rejectReason" class="bi-reject">拒绝原因: {{ b.rejectReason }}</div>
        </div>
        <div class="bi-actions">
          <el-button v-if="b.status === 'PENDING'" @click="doCancel(b)">撤销申请</el-button>
          <el-button v-if="b.status === 'APPROVED'" type="primary" @click="doReturn(b)">申请归还</el-button>
          <span v-if="b.status === 'RETURN_REQUESTED'" class="bi-wait">等待归还验收...</span>
        </div>
      </div>

      <!-- 分页 -->
      <div v-if="tabItems.length > pageSize" class="pager">
        <el-pagination v-model:current-page="page" :page-size="pageSize" :total="tabItems.length"
                       layout="prev, pager, next" background />
      </div>
    </div>

    <!-- 去借设备横幅 -->
    <div class="cta-banner">
      <div>
        <div class="cta-title">需要新的设备?</div>
        <div class="cta-sub">浏览设备图书馆，找到你需要的开发板和工具</div>
      </div>
      <button class="cta-btn" @click="$router.push('/app/equipment')">去借设备</button>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { CheckCircle, ClipboardList, Clock, Inbox, Package } from 'lucide-vue-next'
import { cancelBorrow, fetchMyBorrows, requestReturn } from '../../api'

const tab = ref('ALL')
const items = ref([])
const page = ref(1)
const pageSize = 5

const statusMap = {
  PENDING: ['审批中', 'badge-yellow'],
  APPROVED: ['借用中', 'badge-green'],
  REJECTED: ['已拒绝', 'badge-red'],
  RETURN_REQUESTED: ['归还中', 'badge-blue'],
  RETURNED: ['已归还', 'badge-gray'],
  CANCELLED: ['已撤销', 'badge-gray']
}
const statusText = (s) => statusMap[s]?.[0] || s
const statusBadge = (s) => statusMap[s]?.[1] || 'badge-gray'

const stats = computed(() => ({
  total: items.value.length,
  pending: items.value.filter((b) => b.status === 'PENDING').length,
  borrowing: items.value.filter((b) => b.status === 'APPROVED' || b.status === 'RETURN_REQUESTED').length,
  returned: items.value.filter((b) => b.status === 'RETURNED').length
}))

const tabItems = computed(() => {
  if (tab.value === 'ALL') return items.value
  if (tab.value === 'APPROVED') {
    return items.value.filter((b) => b.status === 'APPROVED' || b.status === 'RETURN_REQUESTED')
  }
  return items.value.filter((b) => b.status === tab.value)
})

const pageItems = computed(() =>
  tabItems.value.slice((page.value - 1) * pageSize, page.value * pageSize))

watch(tab, () => { page.value = 1 })

const endDate = (b) => {
  if (!b.startDate) return '-'
  const d = new Date(b.startDate)
  d.setDate(d.getDate() + (b.durationDays || 0))
  return d.toISOString().slice(0, 10)
}

const load = async () => {
  items.value = await fetchMyBorrows({ status: 'ALL' })
}

const doCancel = async (b) => {
  await ElMessageBox.confirm(`确定撤销对《${b.equipmentName}》的借阅申请吗?`, '撤销申请', { type: 'warning' })
  await cancelBorrow(b.id)
  ElMessage.success('申请已撤销')
  await load()
}

const doReturn = async (b) => {
  await ElMessageBox.confirm(`确认对《${b.equipmentName}》发起归还申请?归还时需通过功能测试验收。`, '申请归还')
  await requestReturn(b.id)
  ElMessage.success('归还申请已提交,请将设备送回实验室')
  await load()
}

onMounted(load)
</script>

<style scoped>
.stat-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 16px; margin-bottom: 16px; }
@media (max-width: 900px) { .stat-grid { grid-template-columns: repeat(2, 1fr); } }

.records-card { padding-top: 12px; }

.empty-box { text-align: center; padding: 40px 0 30px; }
.empty-icon { display: flex; justify-content: center; }
.empty-box h3 { margin: 10px 0 4px; }
.empty-box p { color: var(--text-secondary); margin: 0; font-size: 13px; }

.borrow-item {
  display: flex; justify-content: space-between; align-items: center; gap: 16px;
  border: 1px solid var(--border); border-radius: 14px;
  padding: 16px 18px; margin-bottom: 12px;
  transition: box-shadow .15s;
}
.borrow-item:hover { box-shadow: var(--shadow-lg); }
.bi-main { flex: 1; min-width: 0; }
.bi-no-row { display: flex; align-items: center; gap: 10px; margin-bottom: 6px; }
.bi-no { font-size: 12px; color: #9ca3af; font-family: monospace; }
.bi-name { font-weight: 700; font-size: 16px; color: #111827; }
.bi-purpose { font-size: 13px; color: var(--text-secondary); margin: 4px 0 12px; }
.bi-grid {
  display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px;
}
@media (max-width: 900px) { .bi-grid { grid-template-columns: repeat(2, 1fr); } }
.bi-cell { display: flex; flex-direction: column; gap: 2px; }
.bi-cell-label { font-size: 11px; color: #9ca3af; }
.bi-cell-value { font-size: 13px; color: #374151; }
.bi-cell-value.strong { font-weight: 600; color: #111827; }
.bi-reject { font-size: 12px; color: #dc2626; margin-top: 8px; }
.bi-actions { flex-shrink: 0; }
.bi-wait { font-size: 12px; color: var(--text-secondary); }

.cta-banner {
  margin-top: 16px;
  background: linear-gradient(to right, #7c3aed, #9333ea);
  border-radius: 16px;
  padding: 22px 26px;
  color: #fff;
  display: flex; justify-content: space-between; align-items: center; gap: 16px;
  box-shadow: 0 10px 15px -3px rgba(124,58,237,.3);
}
.cta-title { font-size: 17px; font-weight: 700; margin-bottom: 4px; }
.cta-sub { font-size: 13px; opacity: .85; }
.cta-btn {
  background: #fff; color: #7c3aed;
  border: none; border-radius: 10px;
  padding: 10px 22px; font-size: 14px; font-weight: 600; cursor: pointer;
  transition: transform .15s;
}
.cta-btn:hover { transform: scale(1.04); }

.pager { display: flex; justify-content: center; margin-top: 14px; }
</style>
