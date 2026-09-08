<template>
  <div>
    <div class="toolbar">
      <el-button type="primary" @click="createVisible = true">接入新客户</el-button>
      <span class="hint">每个客户站点用自己的 API Key 访问商店;把签发的 Key 交给该客户的管理员,填到其站点「项目商店 → 接入设置」。</span>
    </div>

    <el-table :data="tenants" stripe>
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="code" label="编码" width="140" />
      <el-table-column prop="name" label="客户名称" min-width="200" />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'" size="small">{{ row.status === 'ACTIVE' ? '正常' : '已停用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createdAt" label="接入时间" width="170" />
      <el-table-column label="操作" width="260">
        <template #default="{ row }">
          <el-button size="small" @click="rotate(row)">重新签发 Key</el-button>
          <el-button size="small" :type="row.status === 'ACTIVE' ? 'danger' : 'success'" @click="toggle(row)">
            {{ row.status === 'ACTIVE' ? '停用' : '恢复' }}
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="createVisible" title="接入新客户" width="460px">
      <el-form label-width="90px">
        <el-form-item label="客户编码">
          <el-input v-model="form.code" placeholder="与客户站点的租户编码一致,如 c001" />
        </el-form-item>
        <el-form-item label="客户名称">
          <el-input v-model="form.name" placeholder="某某学院" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="create">签发 API Key</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="keyVisible" title="API Key(只显示这一次)" width="560px" :close-on-click-modal="false">
      <el-alert type="warning" :closable="false" title="请立即复制并交给该客户的管理员,关闭后无法再次查看;遗失可重新签发(旧 Key 立即失效)。" />
      <el-input v-model="issuedKey" readonly class="key">
        <template #append><el-button @click="copyKey">复制</el-button></template>
      </el-input>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { hubCreateTenant, hubRotateKey, hubTenantStatus, hubTenants } from '../../api/hub'

const emit = defineEmits(['refresh-stats'])
const tenants = ref([])
const createVisible = ref(false)
const creating = ref(false)
const form = reactive({ code: '', name: '' })
const keyVisible = ref(false)
const issuedKey = ref('')

const load = async () => {
  tenants.value = await hubTenants()
  emit('refresh-stats')
}

const showKey = (key) => {
  issuedKey.value = key
  keyVisible.value = true
}

const create = async () => {
  creating.value = true
  try {
    const t = await hubCreateTenant({ code: form.code, name: form.name })
    createVisible.value = false
    form.code = ''
    form.name = ''
    await load()
    showKey(t.apiKey)
  } finally {
    creating.value = false
  }
}

const rotate = async (row) => {
  try {
    await ElMessageBox.confirm(`重新签发后「${row.name}」原来的 Key 立即失效,需要重新配置,是否继续?`, '重新签发', { type: 'warning' })
  } catch (e) { return }
  const t = await hubRotateKey(row.id)
  showKey(t.apiKey)
}

const toggle = async (row) => {
  const next = row.status === 'ACTIVE' ? 'SUSPENDED' : 'ACTIVE'
  await hubTenantStatus(row.id, next)
  ElMessage.success(next === 'ACTIVE' ? '已恢复' : '已停用')
  await load()
}

const copyKey = async () => {
  try {
    await navigator.clipboard.writeText(issuedKey.value)
    ElMessage.success('已复制')
  } catch (e) {
    ElMessage.warning('复制失败,请手动选择复制')
  }
}

onMounted(load)
</script>

<style scoped>
.toolbar { display: flex; gap: 14px; align-items: center; margin-bottom: 14px; }
.hint { font-size: 12px; color: #9ca3af; }
.key { margin-top: 14px; }
</style>
