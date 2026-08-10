<template>
  <div>
    <div class="card">
      <div class="toolbar">
        <el-input v-model="keyword" placeholder="搜索设备..." clearable style="width:260px"
                  @keyup.enter="load" @clear="load" />
        <el-button type="primary" @click="openEdit(null)">+ 新增设备</el-button>
      </div>

      <el-table :data="pageItems" stripe>
        <el-table-column label="设备" min-width="200">
          <template #default="{ row }">
            {{ row.name }}
            <div class="sub-text">{{ row.model }} · {{ row.manufacturer }}</div>
          </template>
        </el-table-column>
        <el-table-column prop="category" label="分类" width="90" />
        <el-table-column prop="location" label="位置" width="90" />
        <el-table-column label="库存" width="100">
          <template #default="{ row }">{{ row.availableCount }} / {{ row.totalCount }}</template>
        </el-table-column>
        <el-table-column prop="rating" label="评分" width="70" />
        <el-table-column prop="borrowCount" label="借出次数" width="90" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <span class="badge" :class="row.status === 'AVAILABLE' ? 'badge-green' : 'badge-yellow'">
              {{ row.status === 'AVAILABLE' ? '可借阅' : '维护中' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="openEdit(row)">编辑</el-button>
            <el-button size="small" type="danger" plain @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager">
        <el-pagination v-model:current-page="page" :page-size="pageSize" :total="items.length"
                       layout="total, prev, pager, next" background />
      </div>
    </div>

    <!-- 编辑弹窗 -->
    <el-dialog v-model="editVisible" :title="form.id ? '编辑设备' : '新增设备'" width="640px">
      <el-form :model="form" label-width="90px">
        <div class="form-2col">
          <el-form-item label="名称" required><el-input v-model="form.name" /></el-form-item>
          <el-form-item label="型号"><el-input v-model="form.model" /></el-form-item>
          <el-form-item label="分类">
            <el-select v-model="form.category">
              <el-option v-for="c in ['开发板', '测试仪表', '通信模块', '传感器', '工具']" :key="c" :label="c" :value="c" />
            </el-select>
          </el-form-item>
          <el-form-item label="位置"><el-input v-model="form.location" placeholder="如:A栋3楼" /></el-form-item>
          <el-form-item label="制造商"><el-input v-model="form.manufacturer" /></el-form-item>
          <el-form-item label="总数量"><el-input-number v-model="form.totalCount" :min="1" /></el-form-item>
          <el-form-item label="可借数量"><el-input-number v-model="form.availableCount" :min="0" :max="form.totalCount" /></el-form-item>
          <el-form-item label="参考价格"><el-input-number v-model="form.price" :min="0" :step="10" /></el-form-item>
          <el-form-item label="评分"><el-input-number v-model="form.rating" :min="0" :max="5" :step="0.1" /></el-form-item>
          <el-form-item label="状态">
            <el-select v-model="form.status">
              <el-option label="可借阅" value="AVAILABLE" />
              <el-option label="维护中" value="MAINTENANCE" />
            </el-select>
          </el-form-item>
        </div>
        <el-form-item label="设备图片">
          <ImageUploader v-model="form.imageUrl" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="技术规格">
          <el-input v-model="form.specsText" placeholder="逗号分隔,如: 50MHz带宽,4通道" />
        </el-form-item>
        <el-form-item label="标签">
          <el-input v-model="form.tagsText" placeholder="逗号分隔,如: 调试,测量" />
        </el-form-item>
        <el-form-item label="参考文档">
          <el-input v-model="form.docsText" placeholder="逗号分隔,如: 用户手册" />
        </el-form-item>
        <el-form-item label="适用项目">
          <el-input v-model="form.projectsText" placeholder="逗号分隔的项目名称" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminCreateEquipment, adminDeleteEquipment, adminUpdateEquipment, fetchEquipment } from '../../api'
import ImageUploader from '../../components/ImageUploader.vue'

const items = ref([])
const keyword = ref('')
const editVisible = ref(false)
const saving = ref(false)
const page = ref(1)
const pageSize = 10

const pageItems = computed(() =>
  items.value.slice((page.value - 1) * pageSize, page.value * pageSize))

const emptyForm = {
  id: null, name: '', model: '', category: '开发板', location: '', icon: '🔧', imageUrl: '',
  manufacturer: '', totalCount: 1, availableCount: 1, price: 0, rating: 5.0,
  status: 'AVAILABLE', description: '',
  specsText: '', tagsText: '', docsText: '', projectsText: ''
}
const form = reactive({ ...emptyForm })

const splitText = (t) => t ? t.split(/[,，]/).map((s) => s.trim()).filter(Boolean) : []
const joinArr = (v) => Array.isArray(v) ? v.join(',') : ''

const load = async () => {
  items.value = await fetchEquipment({ keyword: keyword.value || undefined })
  page.value = 1
}

const openEdit = (row) => {
  Object.assign(form, emptyForm)
  if (row) {
    Object.assign(form, {
      id: row.id, name: row.name, model: row.model, category: row.category,
      location: row.location, icon: row.icon, imageUrl: row.imageUrl || '', manufacturer: row.manufacturer,
      totalCount: row.totalCount, availableCount: row.availableCount,
      price: row.price, rating: row.rating, status: row.status, description: row.description,
      specsText: joinArr(row.specs), tagsText: joinArr(row.tags),
      docsText: joinArr(row.docs), projectsText: joinArr(row.suitableProjects)
    })
  }
  editVisible.value = true
}

const save = async () => {
  if (!form.name.trim()) {
    ElMessage.warning('请填写设备名称')
    return
  }
  saving.value = true
  try {
    // JSON 文本字段:后端按原始 JSON 字符串存储
    const payload = {
      name: form.name, model: form.model, category: form.category, location: form.location,
      icon: form.icon, imageUrl: form.imageUrl || null, manufacturer: form.manufacturer, totalCount: form.totalCount,
      availableCount: form.availableCount, price: form.price, rating: form.rating,
      status: form.status, description: form.description,
      specs: JSON.stringify(splitText(form.specsText)),
      tags: JSON.stringify(splitText(form.tagsText)),
      docs: JSON.stringify(splitText(form.docsText)),
      suitableProjects: JSON.stringify(splitText(form.projectsText))
    }
    if (form.id) await adminUpdateEquipment(form.id, payload)
    else await adminCreateEquipment(payload)
    ElMessage.success('保存成功')
    editVisible.value = false
    await load()
  } catch (e) { /* 已提示 */ } finally {
    saving.value = false
  }
}

const remove = async (row) => {
  await ElMessageBox.confirm(`确定删除设备《${row.name}》?该操作不可恢复。`, '删除设备', { type: 'warning' })
  await adminDeleteEquipment(row.id)
  ElMessage.success('已删除')
  await load()
}

onMounted(load)
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; margin-bottom: 16px; }
.sub-text { font-size: 12px; color: var(--text-secondary); }
.pager { display: flex; justify-content: flex-end; margin-top: 14px; }
.form-2col { display: grid; grid-template-columns: 1fr 1fr; column-gap: 16px; }
:deep(.el-select) { width: 100%; }
</style>
