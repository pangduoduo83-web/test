<template>
  <div>
    <div class="card">
      <div class="toolbar">
        <el-radio-group v-model="status" @change="load">
          <el-radio-button value="ALL">全部</el-radio-button>
          <el-radio-button value="PENDING">待审批</el-radio-button>
          <el-radio-button value="APPROVED">借用中</el-radio-button>
          <el-radio-button value="RETURN_REQUESTED">待归还验收</el-radio-button>
          <el-radio-button value="RETURNED">已归还</el-radio-button>
          <el-radio-button value="REJECTED">已拒绝</el-radio-button>
        </el-radio-group>
        <el-button @click="load">刷新</el-button>
      </div>

      <el-table :data="pageItems" stripe>
        <el-table-column prop="requestNo" label="申请编号" width="150" />
        <el-table-column prop="userName" label="申请人" width="100" />
        <el-table-column prop="equipmentName" label="设备" min-width="150" />
        <el-table-column prop="quantity" label="数量" width="65" />
        <el-table-column prop="purpose" label="用途" width="100" />
        <el-table-column label="期限" width="150">
          <template #default="{ row }">{{ row.startDate }} 起 {{ row.durationDays }}天</template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <span class="badge" :class="statusBadge(row.status)">{{ statusText(row.status) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="申请时间" width="150">
          <template #default="{ row }">{{ (row.appliedAt || '').replace('T', ' ').slice(0, 16) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <template v-if="row.status === 'PENDING'">
              <el-button size="small" type="success" @click="approve(row)">批准</el-button>
              <el-button size="small" type="danger" plain @click="reject(row)">拒绝</el-button>
            </template>
            <el-button v-else-if="row.status === 'RETURN_REQUESTED' || row.status === 'APPROVED'"
                       size="small" type="primary" plain @click="confirmReturn(row)">归还验收</el-button>
            <el-tooltip v-else-if="row.rejectReason" :content="row.rejectReason">
              <span class="reason-hint">拒绝原因</span>
            </el-tooltip>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager">
        <el-pagination v-model:current-page="page" :page-size="pageSize" :total="items.length"
                       layout="total, prev, pager, next" background />
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminConfirmReturn, adminDecideBorrow, adminListBorrows } from '../../api'

const emit = defineEmits(['refresh-pending'])
const status = ref('PENDING')
const items = ref([])
const page = ref(1)
const pageSize = 10

const pageItems = computed(() =>
  items.value.slice((page.value - 1) * pageSize, page.value * pageSize))

const statusMap = {
  PENDING: ['待审批', 'badge-yellow'],
  APPROVED: ['借用中', 'badge-green'],
  REJECTED: ['已拒绝', 'badge-red'],
  RETURN_REQUESTED: ['待归还验收', 'badge-blue'],
  RETURNED: ['已归还', 'badge-gray'],
  CANCELLED: ['已撤销', 'badge-gray']
}
const statusText = (s) => statusMap[s]?.[0] || s
const statusBadge = (s) => statusMap[s]?.[1] || 'badge-gray'

const load = async () => {
  items.value = await adminListBorrows({ status: status.value })
  page.value = 1
  emit('refresh-pending')
}

const approve = async (row) => {
  await ElMessageBox.confirm(
    `批准 ${row.userName} 借用《${row.equipmentName}》× ${row.quantity}?批准后将扣减库存。`,
    '批准借阅', { type: 'success', confirmButtonText: '批准' })
  await adminDecideBorrow(row.id, { action: 'approve' })
  ElMessage.success('已批准,已通知申请人')
  await load()
}

const reject = async (row) => {
  const { value } = await ElMessageBox.prompt('请输入拒绝原因(将通知申请人)', '拒绝申请', {
    confirmButtonText: '确认拒绝', inputPlaceholder: '如:库存紧张,请改期申请'
  })
  await adminDecideBorrow(row.id, { action: 'reject', reason: value })
  ElMessage.success('已拒绝该申请')
  await load()
}

const confirmReturn = async (row) => {
  await ElMessageBox.confirm(
    `确认《${row.equipmentName}》已通过功能测试验收并归还?库存将回补 ${row.quantity} 件。`,
    '归还验收', { type: 'warning', confirmButtonText: '验收通过' })
  await adminConfirmReturn(row.id)
  ElMessage.success('归还验收完成')
  await load()
}

onMounted(load)
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; margin-bottom: 16px; flex-wrap: wrap; gap: 10px; }
.reason-hint { font-size: 12px; color: #dc2626; cursor: help; }
.pager { display: flex; justify-content: flex-end; margin-top: 14px; }
</style>
