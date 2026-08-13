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
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" plain @click="viewStudents(row)">学生</el-button>
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

    <!-- 编辑弹窗:基础信息 + 高级内容(结构化行编辑) -->
    <el-dialog v-model="editVisible" :title="form.id ? '编辑项目' : '新增项目'" width="860px" top="4vh"
               destroy-on-close>
      <el-tabs v-model="editTab">
        <el-tab-pane label="基础信息" name="basic">
          <el-form :model="form" label-width="90px">
            <el-form-item label="标题" required><el-input v-model="form.title" /></el-form-item>
            <el-form-item label="封面图">
              <ImageUploader v-model="form.coverUrl" />
            </el-form-item>
            <el-form-item label="简介">
              <el-input v-model="form.summary" type="textarea" :rows="2" maxlength="300" show-word-limit
                        placeholder="列表卡片上展示的一句话介绍,建议 80 字以内" />
            </el-form-item>
            <el-form-item label="详细描述">
              <RichEditor v-model="form.description" />
              <div class="field-tip">会完整显示在前台项目详情页,支持换行、图片和视频</div>
            </el-form-item>
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
              <el-form-item label="图标"><el-input v-model="form.icon" placeholder="emoji 图标,如: 🔌" /></el-form-item>
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
            <el-form-item label="所需设备">
              <el-select v-model="form.equipNames" multiple filterable allow-create default-first-option
                         placeholder="从设备库选择,也可输入自定义名称" style="width:100%">
                <el-option v-for="name in equipmentOptions" :key="name" :label="name" :value="name" />
              </el-select>
            </el-form-item>
          </el-form>
        </el-tab-pane>

        <el-tab-pane label="高级内容" name="advanced">
          <p class="json-tip">以下内容按行编辑,保存时自动生成 JSON;学生端「项目详情」按此展示。</p>

          <div class="adv-section">
            <div class="adv-head">
              <h4>成果考核项
                <span class="weight-sum" :class="{ ok: assessWeightSum === 100 }">
                  权重合计 {{ assessWeightSum }}/100
                </span>
              </h4>
              <el-button size="small" plain @click="assessRows.push({ name: '', weight: 0, desc: '' })">+ 添加考核项</el-button>
            </div>
            <p class="json-tip" style="margin-top:0">
              设置后学生按考核项分阶段提交成果,每项单独评分,全部评完自动按权重计算综合分(≥60 判定项目完成);留空则为整体单一成果。
            </p>
            <div v-for="(a, i) in assessRows" :key="i" class="adv-row">
              <el-input v-model="a.name" placeholder="考核项,如: 原理图设计" class="grow" />
              <span class="row-label">权重%</span>
              <el-input-number v-model="a.weight" :min="0" :max="100" :step="5" class="num-narrow" />
              <el-input v-model="a.desc" placeholder="要求说明(选填)" class="grow" />
              <el-button size="small" text type="danger" @click="assessRows.splice(i, 1)">删除</el-button>
            </div>
            <p v-if="!assessRows.length" class="empty-hint">未设置考核项,学生提交整体单一成果</p>
          </div>

          <div class="adv-section">
            <div class="adv-head">
              <h4>技能要求</h4>
              <el-button size="small" plain @click="skillRows.push({ name: '', required: 50 })">+ 添加技能</el-button>
            </div>
            <div v-for="(s, i) in skillRows" :key="i" class="adv-row">
              <el-input v-model="s.name" placeholder="技能名称,如: 嵌入式开发" class="grow" />
              <span class="row-label">掌握度</span>
              <el-input-number v-model="s.required" :min="0" :max="100" :step="5" class="num-narrow" />
              <el-button size="small" text type="danger" @click="skillRows.splice(i, 1)">删除</el-button>
            </div>
            <p v-if="!skillRows.length" class="empty-hint">暂无技能要求,点击右上角添加</p>
          </div>

          <div class="adv-section">
            <div class="adv-head">
              <h4>教学大纲</h4>
              <el-button size="small" plain @click="syllabusRows.push({ phase: '', title: '', content: '', hours: 4 })">+ 添加阶段</el-button>
            </div>
            <div v-for="(s, i) in syllabusRows" :key="i" class="syllabus-item">
              <div class="adv-row">
                <el-input v-model="s.phase" placeholder="阶段,如: 第1周" class="w-120" />
                <el-input v-model="s.title" placeholder="阶段标题" class="grow" />
                <span class="row-label">学时</span>
                <el-input-number v-model="s.hours" :min="0" :max="500" class="num-narrow" />
                <el-button size="small" text type="danger" @click="syllabusRows.splice(i, 1)">删除</el-button>
              </div>
              <el-input v-model="s.content" type="textarea" :rows="2" placeholder="阶段内容说明" />
            </div>
            <p v-if="!syllabusRows.length" class="empty-hint">暂无教学大纲,点击右上角添加</p>
          </div>

          <div class="adv-section">
            <div class="adv-head">
              <h4>BOM 清单</h4>
              <div class="bom-btns">
                <el-button size="small" plain @click="downloadBomTemplate">下载CSV模板</el-button>
                <el-upload :show-file-list="false" accept=".csv" :http-request="importBomCsv">
                  <el-button size="small" type="primary" plain>导入CSV</el-button>
                </el-upload>
                <el-button size="small" plain @click="bomRows.push({ ref: '', name: '', qty: 1, footprint: '', price: 0 })">+ 添加元件</el-button>
              </div>
            </div>
            <div v-for="(b, i) in bomRows" :key="i" class="adv-row">
              <el-input v-model="b.ref" placeholder="位号" class="w-80" />
              <el-input v-model="b.name" placeholder="元件名称/型号" class="grow" />
              <span class="row-label">数量</span>
              <el-input-number v-model="b.qty" :min="1" class="num-narrow" />
              <el-input v-model="b.footprint" placeholder="封装" class="w-120" />
              <span class="row-label">单价¥</span>
              <el-input-number v-model="b.price" :min="0" :step="0.1" :precision="2" class="num-narrow" />
              <el-button size="small" text type="danger" @click="bomRows.splice(i, 1)">删除</el-button>
            </div>
            <p v-if="!bomRows.length" class="empty-hint">暂无 BOM 清单,点击右上角添加</p>
          </div>

          <div class="adv-section">
            <div class="adv-head">
              <h4>学习资源</h4>
              <el-button size="small" plain @click="resourceRows.push({ type: '文档', name: '', url: '', uploading: false })">+ 添加资源</el-button>
            </div>
            <div v-for="(r, i) in resourceRows" :key="i" class="adv-row">
              <el-select v-model="r.type" class="res-type-sel">
                <el-option v-for="t in resourceTypes" :key="t" :label="t" :value="t" />
              </el-select>
              <el-input v-model="r.name" placeholder="资源名称,如: 项目开发指南.pdf" class="grow" />
              <el-upload :show-file-list="false" :http-request="(opt) => doUploadRes(opt, r)" accept="*">
                <el-button size="small" :type="r.url ? 'success' : 'primary'" plain :loading="r.uploading">
                  {{ r.url ? '已上传' : '上传附件' }}
                </el-button>
              </el-upload>
              <el-button size="small" text type="danger" @click="resourceRows.splice(i, 1)">删除</el-button>
            </div>
            <p v-if="!resourceRows.length" class="empty-hint">暂无学习资源,点击右上角添加</p>
          </div>
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
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  adminCreateProject, adminDeleteProject, adminListProjects, adminListUsers,
  adminUpdateProject, fetchEquipment, uploadDocFile
} from '../../api'
import ImageUploader from '../../components/ImageUploader.vue'
import RichEditor from '../../components/RichEditor.vue'
import { loadSiteConfig, siteConfig as site } from '../../utils/siteConfig'

const router = useRouter()
const items = ref([])
const teachers = ref([])
const editVisible = ref(false)
const editTab = ref('basic')
const saving = ref(false)
const page = ref(1)
const pageSize = 10

const pageItems = computed(() =>
  items.value.slice((page.value - 1) * pageSize, page.value * pageSize))

// 分类来自「站点设置」,可在后台自定义(下拉仍支持临时自建)
const categoryOptions = computed(() => site.projectCategories || [])
const equipmentOptions = ref([])

const resourceTypes = ['文档', '视频', '代码', '手册', '工具', '课件', '原理图', 'LAYOUT', '3D图']

const emptyForm = {
  id: null, title: '', summary: '', description: '', difficulty: '入门', duration: '2周',
  teamSize: '1人', category: '', icon: '🔌', coverUrl: '', mentorId: null, author: '', license: 'GPL-3.0',
  layers: 2, pcbSize: '', cost: 0, rating: 5.0, verified: false, status: 'PUBLISHED',
  tagsText: '', featuresText: '', goalsText: '', prereqText: '', equipNames: []
}
const form = reactive({ ...emptyForm })

// 高级内容:结构化行编辑,保存时组装为 JSON 数组
const assessRows = ref([])     // {name, weight, desc}
const assessWeightSum = computed(() =>
  assessRows.value.reduce((s, a) => s + (Number(a.weight) || 0), 0))
const skillRows = ref([])      // {name, required}
const syllabusRows = ref([])   // {phase, title, content, hours}
const bomRows = ref([])        // {ref, name, qty, footprint, price}
const resourceRows = ref([])   // {type, name, url}

const splitText = (t) => t ? t.split(/[,，]/).map((s) => s.trim()).filter(Boolean) : []
const joinArr = (v) => Array.isArray(v) ? v.join(',') : ''
const arr = (v) => Array.isArray(v) ? v : []
const num = (v, fallback = 0) => (Number.isFinite(Number(v)) ? Number(v) : fallback)

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
  assessRows.value = arr(row?.assessments).map((a) => ({
    name: a.name || '', weight: num(a.weight), desc: a.desc || ''
  }))
  skillRows.value = arr(row?.skillRequirements).map((s) => ({
    name: s.name || '', required: num(s.required)
  }))
  syllabusRows.value = arr(row?.syllabus).map((s) => ({
    phase: s.phase || '', title: s.title || '', content: s.content || '', hours: num(s.hours)
  }))
  bomRows.value = arr(row?.bom).map((b) => ({
    ref: b.ref || '', name: b.name || '', qty: num(b.qty, 1),
    footprint: b.footprint || '', price: num(b.price)
  }))
  resourceRows.value = arr(row?.resources).map((r) => ({
    type: r.type || '文档', name: r.name || '', url: r.url || '', uploading: false
  }))
  if (row) {
    Object.assign(form, {
      id: row.id, title: row.title, summary: row.summary, description: row.description,
      difficulty: row.difficulty, duration: row.duration, teamSize: row.teamSize,
      category: row.category, icon: row.icon, coverUrl: row.coverUrl || '', mentorId: row.mentorId ?? null, author: row.author,
      license: row.license, layers: row.layers ?? 0, pcbSize: row.pcbSize || '', cost: row.cost ?? 0,
      rating: row.rating, verified: !!row.verified, status: row.status,
      tagsText: joinArr(row.tags), featuresText: joinArr(row.features),
      goalsText: joinArr(row.learningGoals), prereqText: joinArr(row.prerequisites),
      equipNames: [...arr(row.equipmentNames)]
    })
  }
  editVisible.value = true
}

// el-upload 自定义上传:走 /api/upload/file,成功后把 url 写回该行(与教师端一致)
const doUploadRes = async (opt, row) => {
  if (opt.file.size > 30 * 1024 * 1024) {
    ElMessage.warning('附件不能超过 30MB')
    return
  }
  row.uploading = true
  try {
    const { url, name } = await uploadDocFile(opt.file)
    row.url = url
    if (!row.name) row.name = name
    ElMessage.success(`附件上传成功: ${name}`)
  } catch (e) { /* 错误已提示 */ } finally {
    row.uploading = false
  }
}

// ---------- BOM CSV 模板导入 ----------
const downloadBomTemplate = () => {
  const csv = '\ufeff位号,元件名称,数量,封装,单价\r\nU1,STM32F103C8T6,1,LQFP-48,11.5\r\nC1,100nF电容,4,0603,0.05\r\nR1,10K电阻,4,0603,0.02'
  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' })
  const a = document.createElement('a')
  a.href = URL.createObjectURL(blob)
  a.download = 'BOM导入模板.csv'
  a.click()
  URL.revokeObjectURL(a.href)
}

const importBomCsv = (opt) => {
  const reader = new FileReader()
  reader.onload = () => {
    const lines = String(reader.result).split(/\r?\n/).map((l) => l.trim()).filter(Boolean)
    const rows = []
    for (const line of lines) {
      const cells = line.split(/[,，\t]/).map((c) => c.trim().replace(/^"|"$/g, ''))
      if (/位号|名称|ref|name/i.test(cells[0] + (cells[1] || ''))
          && !Number.isFinite(Number(cells[2]))) {
        continue // 表头行
      }
      if (!(cells[1] || '').trim()) continue
      rows.push({
        ref: cells[0] || '', name: cells[1], qty: num(cells[2], 1),
        footprint: cells[3] || '', price: num(cells[4])
      })
    }
    if (!rows.length) {
      ElMessage.warning('未解析到有效行,请使用「下载CSV模板」的格式(UTF-8 编码)')
      return
    }
    bomRows.value = rows
    ElMessage.success(`已导入 ${rows.length} 行 BOM,保存后生效`)
  }
  reader.readAsText(opt.file, 'utf-8')
}

const buildSkills = () => skillRows.value
  .filter((s) => (s.name || '').trim())
  .map((s) => ({ name: s.name.trim(), required: Math.min(100, Math.max(0, num(s.required))) }))

const buildSyllabus = () => syllabusRows.value
  .filter((s) => (s.phase || '').trim() || (s.title || '').trim() || (s.content || '').trim())
  .map((s) => ({
    phase: (s.phase || '').trim(), title: (s.title || '').trim(),
    content: (s.content || '').trim(), hours: num(s.hours)
  }))

const buildBom = () => bomRows.value
  .filter((b) => (b.name || '').trim())
  .map((b) => ({
    ref: (b.ref || '').trim(), name: b.name.trim(), qty: num(b.qty, 1),
    footprint: (b.footprint || '').trim(), price: num(b.price)
  }))

// 过滤空 name;保留已有 url,避免抹掉教师上传的附件
const buildResources = () => resourceRows.value
  .filter((r) => (r.name || '').trim())
  .map((r) => ({ type: r.type || '文档', name: r.name.trim(), url: r.url || '' }))

const buildAssessments = () => assessRows.value
  .filter((a) => (a.name || '').trim())
  .map((a) => ({ name: a.name.trim(), weight: num(a.weight), desc: (a.desc || '').trim() }))

const save = async () => {
  if (!form.title.trim()) {
    ElMessage.warning('请填写项目标题')
    return
  }
  const assessments = buildAssessments()
  if (assessments.length && assessments.reduce((s, a) => s + a.weight, 0) !== 100) {
    ElMessage.warning('成果考核项的权重合计必须等于 100')
    editTab.value = 'advanced'
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
      equipmentNames: JSON.stringify(form.equipNames.filter(Boolean)),
      assessments: JSON.stringify(buildAssessments()),
      skillRequirements: JSON.stringify(buildSkills()),
      syllabus: JSON.stringify(buildSyllabus()),
      bom: JSON.stringify(buildBom()),
      resources: JSON.stringify(buildResources())
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

const viewStudents = (row) => {
  router.push({ path: '/admin/enrollments', query: { projectId: row.id } })
}

onMounted(() => {
  loadSiteConfig()
  load()
  loadTeachers()
  fetchEquipment({})
    .then((list) => {
      equipmentOptions.value = list.map((e) => e.name)
    })
    .catch(() => {})
})
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.hint { color: var(--text-secondary); font-size: 13px; }
.sub-text { font-size: 12px; color: var(--text-secondary); }
.field-tip { margin-top: 6px; font-size: 12px; color: var(--text-secondary); }
.form-2col { display: grid; grid-template-columns: 1fr 1fr; column-gap: 16px; }
:deep(.el-select) { width: 100%; }
.json-tip { font-size: 12px; color: #1d4ed8; background: #eff6ff; padding: 8px 12px; border-radius: 8px; }
.pager { display: flex; justify-content: flex-end; margin-top: 14px; }

/* 高级内容:结构化行编辑(布局参考教师端 .res-edit-row) */
.adv-section { margin-bottom: 20px; }
.adv-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px; }
.adv-head h4 { margin: 0; font-size: 14px; }
.bom-btns { display: flex; gap: 8px; align-items: center; }
.weight-sum { font-size: 12px; color: #ca8a04; font-weight: 400; margin-left: 8px; }
.weight-sum.ok { color: #16a34a; }
.adv-row { display: flex; gap: 8px; align-items: center; margin-bottom: 8px; }
.adv-row .grow { flex: 1; }
.adv-row .w-80 { width: 80px; flex-shrink: 0; }
.adv-row .w-120 { width: 120px; flex-shrink: 0; }
.adv-row .num-narrow { width: 110px; flex-shrink: 0; }
.adv-row .res-type-sel { width: 100px; flex-shrink: 0; }
.row-label { font-size: 12px; color: var(--text-secondary); flex-shrink: 0; }
.syllabus-item { padding: 10px 12px; background: #f9fafb; border-radius: 8px; margin-bottom: 10px; }
.empty-hint { font-size: 12px; color: #9ca3af; margin: 0; }
</style>
