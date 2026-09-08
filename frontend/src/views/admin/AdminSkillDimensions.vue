<template>
  <div class="card">
    <div class="toolbar">
      <div class="intro">
        <b>技能维度决定学生"综合能力雷达"的顶点</b>
        <span class="hint">
          项目的技能要求、学生自评与评审实证都按维度名关联。改名会自动同步到所有学生分数与项目要求;
          停用只是隐藏,历史分数保留;仍被项目引用的维度不能删除。
        </span>
      </div>
      <el-button type="primary" @click="openEdit(null)">+ 新增维度</el-button>
    </div>

    <el-table :data="items" stripe>
      <el-table-column label="排序" width="70" prop="sortOrder" />
      <el-table-column label="维度名称" width="160">
        <template #default="{ row }">
          <b>{{ row.name }}</b>
        </template>
      </el-table-column>
      <el-table-column label="说明" min-width="260">
        <template #default="{ row }">
          <span :class="{ 'sub-text': !row.description }">{{ row.description || '未填写说明' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-switch :model-value="row.enabled" size="small" @change="(v) => toggle(row, v)" />
        </template>
      </el-table-column>
      <el-table-column label="项目引用" width="90" align="center">
        <template #default="{ row }">
          <span :class="row.projectCount ? '' : 'sub-text'">{{ row.projectCount }}</span>
        </template>
      </el-table-column>
      <el-table-column label="学生记录" width="90" align="center">
        <template #default="{ row }">{{ row.userCount }}</template>
      </el-table-column>
      <el-table-column label="更新时间" width="145">
        <template #default="{ row }">{{ formatTime(row.updatedAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="140" fixed="right">
        <template #default="{ row }">
          <el-button size="small" text type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button size="small" text type="danger" :disabled="row.projectCount > 0" @click="remove(row)">
            删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="editVisible" :title="form.id ? '编辑技能维度' : '新增技能维度'" width="480px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="名称" required>
          <el-input v-model="form.name" maxlength="30" show-word-limit placeholder="如: FPGA开发" />
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="form.description" type="textarea" :rows="3" maxlength="200" show-word-limit
                    placeholder="展示给学生的维度说明,如涵盖的知识点与能力" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sortOrder" :min="0" :max="999" />
          <span class="form-hint">数字越小越靠前</span>
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="form.enabled" />
        </el-form-item>
      </el-form>
      <div v-if="form.id && form.name !== originalName" class="rename-warn">
        改名后,所有学生在「{{ originalName }}」上的分数、变动记录以及引用它的项目技能要求都会同步改为「{{ form.name }}」。
      </div>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  adminCreateSkillDimension, adminDeleteSkillDimension, adminListSkillDimensions, adminUpdateSkillDimension
} from '../../api'

const items = ref([])
const editVisible = ref(false)
const saving = ref(false)
const originalName = ref('')
const form = reactive({ id: null, name: '', description: '', sortOrder: 0, enabled: true })

const formatTime = (v) => (v || '').replace('T', ' ').slice(0, 16)

const load = async () => {
  items.value = await adminListSkillDimensions()
}

const openEdit = (row) => {
  const nextOrder = items.value.reduce((m, d) => Math.max(m, d.sortOrder || 0), 0) + 1
  Object.assign(form, row
    ? { id: row.id, name: row.name, description: row.description || '', sortOrder: row.sortOrder, enabled: row.enabled }
    : { id: null, name: '', description: '', sortOrder: nextOrder, enabled: true })
  originalName.value = row ? row.name : ''
  editVisible.value = true
}

const save = async () => {
  if (!form.name.trim()) {
    ElMessage.warning('请填写维度名称')
    return
  }
  saving.value = true
  try {
    const payload = {
      name: form.name.trim(), description: form.description, sortOrder: form.sortOrder, enabled: form.enabled
    }
    if (form.id) {
      await adminUpdateSkillDimension(form.id, payload)
    } else {
      await adminCreateSkillDimension(payload)
    }
    ElMessage.success('已保存')
    editVisible.value = false
    await load()
  } catch (e) { /* 已提示 */ } finally {
    saving.value = false
  }
}

const toggle = async (row, enabled) => {
  try {
    await adminUpdateSkillDimension(row.id, { enabled })
    ElMessage.success(enabled ? `「${row.name}」已启用` : `「${row.name}」已停用,学生雷达图不再显示该维度`)
    await load()
  } catch (e) { /* 已提示 */ }
}

const remove = async (row) => {
  await ElMessageBox.confirm(
    `删除「${row.name}」将同时清除 ${row.userCount} 名学生在该维度上的分数与变动记录,且不可恢复。确认删除?`,
    '删除技能维度', { type: 'warning' })
  try {
    await adminDeleteSkillDimension(row.id)
    ElMessage.success('已删除')
    await load()
  } catch (e) { /* 已提示 */ }
}

onMounted(load)
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; align-items: flex-start; gap: 16px; margin-bottom: 16px; }
.intro { display: flex; flex-direction: column; gap: 4px; font-size: 14px; }
.hint, .sub-text { color: var(--text-secondary); font-size: 12px; line-height: 1.6; }
.form-hint { margin-left: 10px; font-size: 12px; color: var(--text-secondary); }
.rename-warn {
  background: #fefce8; border: 1px solid #fde68a; border-radius: 8px;
  padding: 10px 12px; font-size: 12px; color: #a16207; line-height: 1.6;
}
</style>
