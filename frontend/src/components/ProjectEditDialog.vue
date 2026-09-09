<template>
  <!-- 项目编辑弹窗:管理端与教师端共用。mode=teacher 时不显示讲师指派(归属由服务端保留) -->
  <el-dialog :model-value="modelValue" :title="form.id ? '编辑项目' : '新增项目'" width="860px" top="4vh"
             destroy-on-close @update:model-value="(v) => $emit('update:modelValue', v)" @open="reset">
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
            <el-form-item v-if="mode === 'admin'" label="指派讲师">
              <el-select v-model="form.mentorId" placeholder="选择讲师" clearable>
                <el-option v-for="t in teachers" :key="t.id" :label="t.name" :value="t.id" />
              </el-select>
            </el-form-item>
            <el-form-item label="作者"><el-input v-model="form.author" /></el-form-item>
            <el-form-item label="协议"><el-input v-model="form.license" /></el-form-item>
            <el-form-item label="PCB层数"><el-input-number v-model="form.layers" :min="0" /></el-form-item>
            <el-form-item label="PCB尺寸"><el-input v-model="form.pcbSize" placeholder="如: 45x30mm" /></el-form-item>
            <el-form-item label="预估成本"><el-input-number v-model="form.cost" :min="0" :step="5" /></el-form-item>
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
        <p class="json-tip">以下内容按行编辑,保存时自动生成 JSON;学生端「项目详情」与 AI 导师都按此展示与引导。</p>

        <div class="adv-section">
          <div class="adv-head">
            <h4>成果考核项
              <span class="weight-sum" :class="{ ok: assessWeightSum === 100 }">权重合计 {{ assessWeightSum }}/100</span>
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
            <el-select v-model="s.name" placeholder="选择技能维度(在「技能维度」中维护)" filterable class="grow">
              <el-option v-for="d in skillDimensionOptions" :key="d.name" :label="d.name" :value="d.name">
                <span>{{ d.name }}</span>
                <span v-if="!d.enabled" class="option-muted">(已停用)</span>
              </el-option>
            </el-select>
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
      <el-button @click="$emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="saving" @click="save">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { fetchEquipment, uploadDocFile } from '../api'
import ImageUploader from './ImageUploader.vue'
import RichEditor from './RichEditor.vue'
import { loadSiteConfig, siteConfig as site } from '../utils/siteConfig'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  /** 要编辑的项目对象;为 null 表示新增 */
  project: { type: Object, default: null },
  /** admin | teacher */
  mode: { type: String, default: 'admin' },
  /** 可指派的讲师(admin 模式) */
  teachers: { type: Array, default: () => [] },
  /** 技能维度 [{name, enabled}] */
  skillDimensions: { type: Array, default: () => [] },
  /** (id|null, payload) => Promise */
  saveFn: { type: Function, required: true }
})
const emit = defineEmits(['update:modelValue', 'saved'])

const editTab = ref('basic')
const saving = ref(false)
const categoryOptions = computed(() => site.projectCategories || [])
const equipmentOptions = ref([])
const resourceTypes = ['文档', '视频', '代码', '手册', '工具', '课件', '原理图', 'LAYOUT', '3D图']

const emptyForm = {
  id: null, title: '', summary: '', description: '', difficulty: '入门', duration: '2周',
  teamSize: '1人', category: '', icon: '🔌', coverUrl: '', mentorId: null, author: '', license: 'GPL-3.0',
  layers: 2, pcbSize: '', cost: 0, verified: false, status: 'PUBLISHED',
  tagsText: '', featuresText: '', goalsText: '', prereqText: '', equipNames: []
}
const form = reactive({ ...emptyForm })
const assessRows = ref([])
const assessWeightSum = computed(() => assessRows.value.reduce((s, a) => s + (Number(a.weight) || 0), 0))
const skillRows = ref([])
const syllabusRows = ref([])
const bomRows = ref([])
const resourceRows = ref([])

const skillDimensionOptions = computed(() => {
  const list = [...props.skillDimensions]
  skillRows.value.forEach((s) => {
    if (s.name && !list.some((d) => d.name === s.name)) list.push({ name: s.name, enabled: false })
  })
  return list
})

const splitText = (t) => t ? t.split(/[,，]/).map((s) => s.trim()).filter(Boolean) : []
const joinArr = (v) => Array.isArray(v) ? v.join(',') : ''
const arr = (v) => Array.isArray(v) ? v : []
const num = (v, fallback = 0) => (Number.isFinite(Number(v)) ? Number(v) : fallback)

const reset = () => {
  const row = props.project
  Object.assign(form, emptyForm)
  editTab.value = 'basic'
  assessRows.value = arr(row?.assessments).map((a) => ({ name: a.name || '', weight: num(a.weight), desc: a.desc || '' }))
  skillRows.value = arr(row?.skillRequirements).map((s) => ({ name: s.name || '', required: num(s.required) }))
  syllabusRows.value = arr(row?.syllabus).map((s) => ({ phase: s.phase || '', title: s.title || '', content: s.content || '', hours: num(s.hours) }))
  bomRows.value = arr(row?.bom).map((b) => ({ ref: b.ref || '', name: b.name || '', qty: num(b.qty, 1), footprint: b.footprint || '', price: num(b.price) }))
  resourceRows.value = arr(row?.resources).map((r) => ({ type: r.type || '文档', name: r.name || '', url: r.url || '', uploading: false }))
  if (row) {
    Object.assign(form, {
      id: row.id, title: row.title, summary: row.summary, description: row.description,
      difficulty: row.difficulty, duration: row.duration, teamSize: row.teamSize,
      category: row.category, icon: row.icon, coverUrl: row.coverUrl || '', mentorId: row.mentorId ?? null, author: row.author,
      license: row.license, layers: row.layers ?? 0, pcbSize: row.pcbSize || '', cost: row.cost ?? 0,
      verified: !!row.verified, status: row.status,
      tagsText: joinArr(row.tags), featuresText: joinArr(row.features),
      goalsText: joinArr(row.learningGoals), prereqText: joinArr(row.prerequisites),
      equipNames: [...arr(row.equipmentNames)]
    })
  }
}

const doUploadRes = async (opt, row) => {
  if (opt.file.size > 30 * 1024 * 1024) { ElMessage.warning('附件不能超过 30MB'); return }
  row.uploading = true
  try {
    const { url, name } = await uploadDocFile(opt.file)
    row.url = url
    if (!row.name) row.name = name
    ElMessage.success(`附件上传成功: ${name}`)
  } catch (e) { /* 已提示 */ } finally {
    row.uploading = false
  }
}

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
      if (/位号|名称|ref|name/i.test(cells[0] + (cells[1] || '')) && !Number.isFinite(Number(cells[2]))) continue
      if (!(cells[1] || '').trim()) continue
      rows.push({ ref: cells[0] || '', name: cells[1], qty: num(cells[2], 1), footprint: cells[3] || '', price: num(cells[4]) })
    }
    if (!rows.length) { ElMessage.warning('未解析到有效行,请使用「下载CSV模板」的格式(UTF-8 编码)'); return }
    bomRows.value = rows
    ElMessage.success(`已导入 ${rows.length} 行 BOM,保存后生效`)
  }
  reader.readAsText(opt.file, 'utf-8')
}

const buildSkills = () => skillRows.value.filter((s) => (s.name || '').trim())
  .map((s) => ({ name: s.name.trim(), required: Math.min(100, Math.max(0, num(s.required))) }))
const buildSyllabus = () => syllabusRows.value
  .filter((s) => (s.phase || '').trim() || (s.title || '').trim() || (s.content || '').trim())
  .map((s) => ({ phase: (s.phase || '').trim(), title: (s.title || '').trim(), content: (s.content || '').trim(), hours: num(s.hours) }))
const buildBom = () => bomRows.value.filter((b) => (b.name || '').trim())
  .map((b) => ({ ref: (b.ref || '').trim(), name: b.name.trim(), qty: num(b.qty, 1), footprint: (b.footprint || '').trim(), price: num(b.price) }))
const buildResources = () => resourceRows.value.filter((r) => (r.name || '').trim())
  .map((r) => ({ type: r.type || '文档', name: r.name.trim(), url: r.url || '' }))
const buildAssessments = () => assessRows.value.filter((a) => (a.name || '').trim())
  .map((a) => ({ name: a.name.trim(), weight: num(a.weight), desc: (a.desc || '').trim() }))

const save = async () => {
  if (!form.title.trim()) { ElMessage.warning('请填写项目标题'); return }
  const assessments = buildAssessments()
  if (assessments.length && assessments.reduce((s, a) => s + a.weight, 0) !== 100) {
    ElMessage.warning('成果考核项的权重合计必须等于 100')
    editTab.value = 'advanced'
    return
  }
  saving.value = true
  try {
    const mentorUser = props.teachers.find((t) => t.id === form.mentorId)
    const payload = {
      title: form.title, summary: form.summary, description: form.description,
      difficulty: form.difficulty, duration: form.duration, teamSize: form.teamSize,
      category: form.category, icon: form.icon, coverUrl: form.coverUrl || null,
      author: form.author, license: form.license, layers: form.layers, pcbSize: form.pcbSize || null, cost: form.cost,
      verified: form.verified, status: form.status,
      tags: JSON.stringify(splitText(form.tagsText)),
      features: JSON.stringify(splitText(form.featuresText)),
      learningGoals: JSON.stringify(splitText(form.goalsText)),
      prerequisites: JSON.stringify(splitText(form.prereqText)),
      equipmentNames: JSON.stringify(form.equipNames.filter(Boolean)),
      assessments: JSON.stringify(assessments),
      skillRequirements: JSON.stringify(buildSkills()),
      syllabus: JSON.stringify(buildSyllabus()),
      bom: JSON.stringify(buildBom()),
      resources: JSON.stringify(buildResources())
    }
    if (props.mode === 'admin') {
      payload.mentor = mentorUser ? mentorUser.name : null
      payload.mentorId = form.mentorId || null
    }
    await props.saveFn(form.id, payload)
    ElMessage.success('保存成功')
    emit('update:modelValue', false)
    emit('saved')
  } catch (e) { /* 已提示 */ } finally {
    saving.value = false
  }
}

onMounted(() => {
  loadSiteConfig()
  fetchEquipment({}).then((list) => { equipmentOptions.value = list.map((e) => e.name) }).catch(() => {})
})
</script>

<style scoped>
.field-tip { margin-top: 6px; font-size: 12px; color: var(--text-secondary); }
.form-2col { display: grid; grid-template-columns: 1fr 1fr; column-gap: 16px; }
:deep(.el-select) { width: 100%; }
.json-tip { font-size: 12px; color: #1d4ed8; background: #eff6ff; padding: 8px 12px; border-radius: 8px; }
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
.option-muted { margin-left: 6px; font-size: 12px; color: #9ca3af; }
.syllabus-item { padding: 10px 12px; background: #f9fafb; border-radius: 8px; margin-bottom: 10px; }
.empty-hint { font-size: 12px; color: #9ca3af; margin: 0; }
</style>
