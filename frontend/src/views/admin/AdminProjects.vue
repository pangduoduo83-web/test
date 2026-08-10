<template>
  <div>
    <div class="card">
      <div class="toolbar">
        <span class="hint">共 {{ items.length }} 个项目</span>
        <el-button type="primary" @click="openEdit(null)">+ 新增项目</el-button>
      </div>

      <el-table :data="pageItems" stripe>
        <el-table-column label="项目" min-width="220">
          <template #default="{ row }">
            {{ row.title }}
            <div class="sub-text">{{ row.category }} · {{ row.author }}</div>
          </template>
        </el-table-column>
        <el-table-column prop="difficulty" label="难度" width="70" />
        <el-table-column prop="duration" label="周期" width="70" />
        <el-table-column prop="enrolledCount" label="报名数" width="80" />
        <el-table-column prop="rating" label="评分" width="70" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <span class="badge" :class="row.status === 'PUBLISHED' ? 'badge-green' : 'badge-gray'">
              {{ row.status === 'PUBLISHED' ? '已发布' : '草稿' }}
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

    <!-- 编辑弹窗:基础信息 + 高级 JSON -->
    <el-dialog v-model="editVisible" :title="form.id ? '编辑项目' : '新增项目'" width="720px" top="4vh">
      <el-tabs v-model="editTab">
        <el-tab-pane label="基础信息" name="basic">
          <el-form :model="form" label-width="90px">
            <el-form-item label="标题" required><el-input v-model="form.title" /></el-form-item>
            <el-form-item label="封面图">
              <ImageUploader v-model="form.coverUrl" />
            </el-form-item>
            <el-form-item label="简介"><el-input v-model="form.summary" type="textarea" :rows="2" /></el-form-item>
            <el-form-item label="详细描述"><el-input v-model="form.description" type="textarea" :rows="3" /></el-form-item>
            <div class="form-2col">
              <el-form-item label="难度">
                <el-select v-model="form.difficulty">
                  <el-option v-for="d in ['入门', '进阶', '挑战']" :key="d" :label="d" :value="d" />
                </el-select>
              </el-form-item>
              <el-form-item label="周期"><el-input v-model="form.duration" placeholder="如: 2周" /></el-form-item>
              <el-form-item label="团队规模"><el-input v-model="form.teamSize" placeholder="如: 1-2人" /></el-form-item>
              <el-form-item label="分类">
                <el-select v-model="form.category" filterable allow-create default-first-option>
                  <el-option v-for="c in categoryOptions" :key="c" :label="c" :value="c" />
                </el-select>
              </el-form-item>
              <el-form-item label="指派讲师">
                <el-select v-model="form.mentorId" placeholder="选择讲师" clearable>
                  <el-option v-for="t in teachers" :key="t.id" :label="t.name" :value="t.id" />
                </el-select>
              </el-form-item>
              <el-form-item label="作者"><el-input v-model="form.author" /></el-form-item>
              <el-form-item label="协议"><el-input v-model="form.license" /></el-form-item>
              <el-form-item label="PCB层数"><el-input-number v-model="form.layers" :min="0" /></el-form-item>
              <el-form-item label="PCB尺寸"><el-input v-model="form.pcbSize" placeholder="如: 45x30mm" /></el-form-item>
              <el-form-item label="预估成本"><el-input-number v-model="form.cost" :min="0" :step="5" /></el-form-item>
              <el-form-item label="评分"><el-input-number v-model="form.rating" :min="0" :max="5" :step="0.1" /></el-form-item>
              <el-form-item label="硬件验证"><el-switch v-model="form.verified" /></el-form-item>
              <el-form-item label="状态">
                <el-select v-model="form.status">
                  <el-option label="已发布" value="PUBLISHED" />
                  <el-option label="草稿" value="DRAFT" />
                </el-select>
              </el-form-item>
            </div>
            <el-form-item label="标签"><el-input v-model="form.tagsText" placeholder="逗号分隔" /></el-form-item>
            <el-form-item label="项目特性"><el-input v-model="form.featuresText" placeholder="逗号分隔" /></el-form-item>
            <el-form-item label="学习目标"><el-input v-model="form.goalsText" type="textarea" :rows="2" placeholder="逗号分隔" /></el-form-item>
            <el-form-item label="前置要求"><el-input v-model="form.prereqText" type="textarea" :rows="2" placeholder="逗号分隔" /></el-form-item>
            <el-form-item label="所需设备"><el-input v-model="form.equipText" placeholder="逗号分隔的设备名称" /></el-form-item>
          </el-form>
        </el-tab-pane>

        <el-tab-pane label="高级内容(JSON)" name="advanced">
          <p class="json-tip">以下字段为 JSON 数组,直接编辑;格式错误会导致学生端展示异常。</p>
          <el-form label-width="90px">
            <el-form-item label="技能要求">
              <el-input v-model="form.skillsJson" type="textarea" :rows="4"
                        placeholder='[{"name":"嵌入式开发","required":40}]' />
            </el-form-item>
            <el-form-item label="教学大纲">
              <el-input v-model="form.syllabusJson" type="textarea" :rows="6"
                        placeholder='[{"phase":"第1周","title":"...","content":"...","hours":8}]' />
            </el-form-item>
            <el-form-item label="BOM清单">
              <el-input v-model="form.bomJson" type="textarea" :rows="6"
                        placeholder='[{"ref":"U1","name":"...","qty":1,"footprint":"...","price":1.0}]' />
            </el-form-item>
            <el-form-item label="学习资源">
              <el-input v-model="form.resourcesJson" type="textarea" :rows="4"
                        placeholder='[{"type":"文档","name":"..."}]' />
            </el-form-item>
          </el-form>
        </el-tab-pane>
      </el-tabs>

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
import {
  adminCreateProject, adminDeleteProject, adminListProjects, adminListUsers, adminUpdateProject
} from '../../api'
import ImageUploader from '../../components/ImageUploader.vue'

const items = ref([])
const teachers = ref([])
const editVisible = ref(false)
const editTab = ref('basic')
const saving = ref(false)
const page = ref(1)
const pageSize = 10

const pageItems = computed(() =>
  items.value.slice((page.value - 1) * pageSize, page.value * pageSize))

const categoryOptions = [
  '开发板/评估板', '物联网应用', '电源管理', '电机控制',
  '测量仪器', '通信模块', 'FPGA/EDA', '消费电子'
]

const emptyForm = {
  id: null, title: '', summary: '', description: '', difficulty: '入门', duration: '2周',
  teamSize: '1人', category: '', icon: '🔌', coverUrl: '', mentorId: null, author: '', license: 'GPL-3.0',
  layers: 2, pcbSize: '', cost: 0, rating: 5.0, verified: false, status: 'PUBLISHED',
  tagsText: '', featuresText: '', goalsText: '', prereqText: '', equipText: '',
  skillsJson: '[]', syllabusJson: '[]', bomJson: '[]', resourcesJson: '[]'
}
const form = reactive({ ...emptyForm })

const splitText = (t) => t ? t.split(/[,，]/).map((s) => s.trim()).filter(Boolean) : []
const joinArr = (v) => Array.isArray(v) ? v.join(',') : ''
const toJsonText = (v) => JSON.stringify(v ?? [], null, 0)

const load = async () => {
  items.value = await adminListProjects()
  page.value = 1
}

const loadTeachers = async () => {
  const users = await adminListUsers()
  teachers.value = users.filter((u) => u.role === 'TEACHER')
}

const openEdit = (row) => {
  Object.assign(form, emptyForm)
  editTab.value = 'basic'
  if (row) {
    Object.assign(form, {
      id: row.id, title: row.title, summary: row.summary, description: row.description,
      difficulty: row.difficulty, duration: row.duration, teamSize: row.teamSize,
      category: row.category, icon: row.icon, coverUrl: row.coverUrl || '', mentorId: row.mentorId ?? null, author: row.author,
      license: row.license, layers: row.layers ?? 0, pcbSize: row.pcbSize || '', cost: row.cost ?? 0,
      rating: row.rating, verified: !!row.verified, status: row.status,
      tagsText: joinArr(row.tags), featuresText: joinArr(row.features),
      goalsText: joinArr(row.learningGoals), prereqText: joinArr(row.prerequisites),
      equipText: joinArr(row.equipmentNames),
      skillsJson: toJsonText(row.skillRequirements),
      syllabusJson: toJsonText(row.syllabus),
      bomJson: toJsonText(row.bom),
      resourcesJson: toJsonText(row.resources)
    })
  }
  editVisible.value = true
}

const validJson = (text, label) => {
  try {
    JSON.parse(text || '[]')
    return true
  } catch (e) {
    ElMessage.error(`「${label}」不是合法 JSON,请检查`)
    return false
  }
}

const save = async () => {
  if (!form.title.trim()) {
    ElMessage.warning('请填写项目标题')
    return
  }
  if (!validJson(form.skillsJson, '技能要求') || !validJson(form.syllabusJson, '教学大纲')
    || !validJson(form.bomJson, 'BOM清单') || !validJson(form.resourcesJson, '学习资源')) {
    return
  }
  saving.value = true
  try {
    const mentorUser = teachers.value.find((t) => t.id === form.mentorId)
    const payload = {
      title: form.title, summary: form.summary, description: form.description,
      difficulty: form.difficulty, duration: form.duration, teamSize: form.teamSize,
      category: form.category, icon: form.icon, coverUrl: form.coverUrl || null,
      mentor: mentorUser ? mentorUser.name : null, mentorId: form.mentorId || null, author: form.author,
      license: form.license, layers: form.layers, pcbSize: form.pcbSize || null, cost: form.cost, rating: form.rating,
      verified: form.verified, status: form.status,
      tags: JSON.stringify(splitText(form.tagsText)),
      features: JSON.stringify(splitText(form.featuresText)),
      learningGoals: JSON.stringify(splitText(form.goalsText)),
      prerequisites: JSON.stringify(splitText(form.prereqText)),
      equipmentNames: JSON.stringify(splitText(form.equipText)),
      skillRequirements: form.skillsJson || '[]',
      syllabus: form.syllabusJson || '[]',
      bom: form.bomJson || '[]',
      resources: form.resourcesJson || '[]'
    }
    if (form.id) await adminUpdateProject(form.id, payload)
    else await adminCreateProject(payload)
    ElMessage.success('保存成功')
    editVisible.value = false
    await load()
  } catch (e) { /* 已提示 */ } finally {
    saving.value = false
  }
}

const remove = async (row) => {
  await ElMessageBox.confirm(`确定删除项目《${row.title}》?该操作不可恢复。`, '删除项目', { type: 'warning' })
  await adminDeleteProject(row.id)
  ElMessage.success('已删除')
  await load()
}

onMounted(() => {
  load()
  loadTeachers()
})
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.hint { color: var(--text-secondary); font-size: 13px; }
.sub-text { font-size: 12px; color: var(--text-secondary); }
.form-2col { display: grid; grid-template-columns: 1fr 1fr; column-gap: 16px; }
:deep(.el-select) { width: 100%; }
.json-tip { font-size: 12px; color: #ca8a04; background: #fefce8; padding: 8px 12px; border-radius: 8px; }
.pager { display: flex; justify-content: flex-end; margin-top: 14px; }
</style>
