<template>
  <!-- 项目编辑弹窗: 管理端与教师端共用。扁平Tab架构，主次分明，无幼稚emoji -->
  <el-dialog :model-value="modelValue" :title="form.id ? '编辑项目' : '新增项目'"
             width="min(940px, calc(100vw - 32px))" class="project-edit-dialog"
             top="3.5vh" destroy-on-close @update:model-value="(v) => $emit('update:modelValue', v)" @open="reset">
    <el-tabs v-model="editTab" class="project-tabs">
      <!-- 1. 基本信息 -->
      <el-tab-pane label="基本信息" name="basic">
        <el-form :model="form" label-width="84px" class="dialog-form">
          <el-form-item label="项目标题" required>
            <el-input v-model="form.title" placeholder="输入项目名称，如：简易数字示波器 DIY套件" />
          </el-form-item>
          <el-form-item label="一句话简介">
            <el-input v-model="form.summary" type="textarea" :rows="2" maxlength="300" show-word-limit
                      placeholder="展示在列表卡片上的一句话介绍，建议 80 字以内" />
          </el-form-item>
          <div class="form-2col">
            <el-form-item label="分类">
              <el-select v-model="form.category" filterable allow-create default-first-option placeholder="选择或输入分类">
                <el-option v-for="c in categoryOptions" :key="c" :label="c" :value="c" />
              </el-select>
            </el-form-item>
            <el-form-item label="难度">
              <el-select v-model="form.difficulty">
                <el-option v-for="d in ['入门', '进阶', '挑战']" :key="d" :label="d" :value="d" />
              </el-select>
            </el-form-item>
            <el-form-item label="教学周期">
              <el-input v-model="form.duration" placeholder="例如: 2周 / 16课时" />
            </el-form-item>
            <el-form-item label="发布状态">
              <el-select v-model="form.status">
                <el-option label="已发布" value="PUBLISHED" />
                <el-option label="草稿" value="DRAFT" />
              </el-select>
            </el-form-item>
            <el-form-item label="团队规模">
              <el-input v-model="form.teamSize" placeholder="例如: 1-2人" />
            </el-form-item>
            <el-form-item v-if="mode === 'admin'" label="指派讲师">
              <el-select v-model="form.mentorId" placeholder="选择讲师" clearable>
                <el-option v-for="t in teachers" :key="t.id" :label="t.name" :value="t.id" />
              </el-select>
            </el-form-item>
            <el-form-item label="主讲作者">
              <el-input v-model="form.author" placeholder="作者或教研团队名称" />
            </el-form-item>
            <el-form-item label="开源协议">
              <el-input v-model="form.license" placeholder="例如: GPL-3.0 / MIT" />
            </el-form-item>
          </div>
          <el-form-item label="项目标签">
            <el-input v-model="form.tagsText" placeholder="项目关键词，多个用逗号隔开，例如：STM32, 示波器, 模拟电路" />
          </el-form-item>
          <el-form-item label="封面图片">
            <ImageUploader v-model="form.coverUrl" />
          </el-form-item>
        </el-form>
      </el-tab-pane>

      <!-- 2. 详情文档 -->
      <el-tab-pane label="详情文档" name="content">
        <div class="content-pane">
          <div class="section-notice">
            编写项目详细介绍或实验指导手册，将在前台学生项目详情页完整展示。支持富文本排版、高清图片与演示视频。
          </div>
          <RichEditor v-model="form.description" />
        </div>
      </el-tab-pane>

      <!-- 3. 考核与评分 -->
      <el-tab-pane label="考核与评分" name="assessment">
        <div class="assessment-pane">
          <!-- 成果提交要求 -->
          <div class="card-section">
            <div class="section-head">
              <h4 class="section-title">成果提交要求</h4>
            </div>
            <el-input v-model="form.submissionRequirements" type="textarea" :rows="2" maxlength="4000"
                      placeholder="例如：提交电路实物照片、运行演示视频、实验报告；说明成果中必须验证的关键指标。" />
          </div>

          <!-- 分阶段考核项 -->
          <div class="card-section">
            <div class="section-head">
              <div class="section-head-left">
                <h4 class="section-title">分阶段考核项</h4>
                <span class="weight-badge" :class="{ ok: assessWeightSum === 100 }">
                  权重合计: {{ assessWeightSum }}% / 100%
                </span>
              </div>
              <el-button size="small" type="primary" plain @click="addAssessment">+ 添加考核项</el-button>
            </div>
            <div class="section-subtext">
              设置后学生按考核项分阶段提交成果并单独计分；全部评完后按权重计算综合分（≥60 判定完成）。
            </div>

            <!-- 考核项卡片列表 -->
            <div class="assess-list">
              <div v-for="(a, i) in assessRows" :key="i" class="assess-card">
                <div class="assess-card-header">
                  <span class="assess-index">{{ i + 1 }}</span>
                  <el-input v-model="a.name" placeholder="考核项名称，如：焊接工艺" class="assess-name" />
                  <div class="assess-weight">
                    <span class="unit-text">权重</span>
                    <el-input-number v-model="a.weight" :min="0" :max="100" :step="5" class="num-compact" controls-position="right" />
                    <span class="unit-text">%</span>
                  </div>
                  <el-input v-model="a.desc" placeholder="提交要求，如：电路板实拍图" class="assess-desc" />

                  <div class="assess-tags">
                    <span class="status-tag" :class="{ active: !!(a.referenceAnswer && a.referenceAnswer.trim()) }">
                      {{ (a.referenceAnswer && a.referenceAnswer.trim()) ? '参考标准: 已填' : '参考标准: 未填' }}
                    </span>
                    <span class="status-tag" :class="{ active: !!(a.rubric && a.rubric.length) }">
                      {{ (a.rubric && a.rubric.length) ? `评分细则: 自定义(${a.rubric.length}项)` : '评分细则: 默认' }}
                    </span>
                  </div>

                  <div class="assess-actions">
                    <el-button size="small" text type="primary" @click="toggleAssessExpand(i)">
                      {{ isAssessExpanded(i) ? '收起配置' : '展开配置' }}
                    </el-button>
                    <el-button size="small" text type="danger" @click="removeAssessment(i)">删除</el-button>
                  </div>
                </div>

                <!-- 折叠区：标准答案与自定评分细则 -->
                <div v-show="isAssessExpanded(i)" class="assess-card-body">
                  <div class="assess-field-block">
                    <div class="field-title">教师参考标准 / 参考答案（选填，仅教师与 AI 评审可见）</div>
                    <el-input v-model="a.referenceAnswer" type="textarea" :rows="3" maxlength="6000" show-word-limit
                              placeholder="可填写该考核项的标准测试结果、关键参数、参考波形或代码审查要点" />
                  </div>
                  <div class="assess-field-block">
                    <ReviewRubricEditor v-model="a.rubric" fallback="项目默认评分标准" />
                  </div>
                </div>
              </div>

              <div v-if="!assessRows.length" class="empty-hint-card">
                未设置分阶段考核项，学生提交整体单一成果。如需多阶段考核请点击右上角「+ 添加考核项」。
              </div>
            </div>
          </div>

          <!-- 全局通用标准（未配置分阶段项时展示） -->
          <div v-if="!assessRows.length" class="card-section">
            <div class="section-head">
              <h4 class="section-title">教师标准答案 / 参考实现（选填）</h4>
            </div>
            <el-input v-model="form.referenceAnswer" type="textarea" :rows="4" maxlength="20000" show-word-limit
                      placeholder="可填写关键结果、参考实现、代码要点或评分时应核对的答案。留空则 AI 只按评分细则和提交材料判断。" />
            <div class="field-tip">仅教师和 AI 评审可见，不会展示给学生；AI 会将其作为参考标准。</div>
            <div style="margin-top: 14px">
              <ReviewRubricEditor v-model="reviewRubric" fallback="系统通用评分标准" />
            </div>
          </div>
        </div>
      </el-tab-pane>

      <!-- 4. 教学安排 -->
      <el-tab-pane label="教学安排" name="teaching">
        <div class="teaching-pane">
          <!-- 技能要求 -->
          <div class="card-section">
            <div class="section-head">
              <h4 class="section-title">技能维度要求</h4>
              <el-button size="small" plain @click="skillRows.push({ name: '', required: 50 })">+ 添加技能</el-button>
            </div>
            <div v-for="(s, i) in skillRows" :key="i" class="adv-row">
              <el-select v-model="s.name" placeholder="选择技能维度" filterable class="grow">
                <el-option v-for="d in skillDimensionOptions" :key="d.name" :label="d.name" :value="d.name">
                  <span>{{ d.name }}</span>
                  <span v-if="!d.enabled" class="option-muted">(已停用)</span>
                </el-option>
              </el-select>
              <span class="row-label">掌握度</span>
              <el-input-number v-model="s.required" :min="0" :max="100" :step="5" class="num-narrow" />
              <el-button size="small" text type="danger" @click="skillRows.splice(i, 1)">删除</el-button>
            </div>
            <p v-if="!skillRows.length" class="empty-hint">暂无技能要求，点击右上角添加</p>
          </div>

          <!-- 教学大纲 -->
          <div class="card-section">
            <div class="section-head">
              <h4 class="section-title">教学大纲阶段</h4>
              <el-button size="small" plain @click="syllabusRows.push({ phase: '', title: '', content: '', hours: 4 })">+ 添加阶段</el-button>
            </div>
            <div v-for="(s, i) in syllabusRows" :key="i" class="syllabus-item">
              <div class="adv-row">
                <el-input v-model="s.phase" placeholder="阶段，如：第1阶段" class="w-120" />
                <el-input v-model="s.title" placeholder="阶段标题，如：电路分析与焊接" class="grow" />
                <span class="row-label">学时</span>
                <el-input-number v-model="s.hours" :min="0" :max="500" class="num-narrow" />
                <el-button size="small" text type="danger" @click="syllabusRows.splice(i, 1)">删除</el-button>
              </div>
              <el-input v-model="s.content" type="textarea" :rows="2" placeholder="阶段教学内容与要求说明" />
            </div>
            <p v-if="!syllabusRows.length" class="empty-hint">暂无教学大纲阶段，点击右上角添加</p>
          </div>

          <!-- 学习目标与前置要求 -->
          <div class="card-section">
            <div class="section-head"><h4 class="section-title">学习目标与要求说明</h4></div>
            <el-form :model="form" label-width="84px">
              <el-form-item label="学习目标">
                <el-input v-model="form.goalsText" type="textarea" :rows="2" placeholder="项目培养目标，逗号分隔" />
              </el-form-item>
              <el-form-item label="前置要求">
                <el-input v-model="form.prereqText" type="textarea" :rows="2" placeholder="学生需掌握的基础知识或课程，逗号分隔" />
              </el-form-item>
              <el-form-item label="项目特性">
                <el-input v-model="form.featuresText" placeholder="项目核心亮点或特性，逗号分隔" />
              </el-form-item>
            </el-form>
          </div>
        </div>
      </el-tab-pane>

      <!-- 5. 物料与规格 -->
      <el-tab-pane label="物料与规格" name="materials">
        <div class="materials-pane">
          <!-- 硬件工程参数 -->
          <div class="card-section">
            <div class="section-head"><h4 class="section-title">硬件工程参数（选填）</h4></div>
            <el-form :model="form" label-width="84px">
              <div class="form-2col">
                <el-form-item label="PCB层数"><el-input-number v-model="form.layers" :min="0" /></el-form-item>
                <el-form-item label="PCB尺寸"><el-input v-model="form.pcbSize" placeholder="例如: 100x60mm" /></el-form-item>
                <el-form-item label="预估成本"><el-input-number v-model="form.cost" :min="0" :step="5" /></el-form-item>
                <el-form-item label="硬件验证"><el-switch v-model="form.verified" active-text="已实物验证" inactive-text="未验证" /></el-form-item>
              </div>
              <el-form-item label="所需设备">
                <el-select v-model="form.equipNames" multiple filterable allow-create default-first-option
                           placeholder="从设备库选择或输入自定义设备名称" style="width:100%">
                  <el-option v-for="name in equipmentOptions" :key="name" :label="name" :value="name" />
                </el-select>
              </el-form-item>
            </el-form>
          </div>

          <!-- BOM 清单 -->
          <div class="card-section">
            <div class="section-head">
              <h4 class="section-title">BOM 物料清单</h4>
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
              <el-input v-model="b.name" placeholder="元件型号" class="grow" />
              <span class="row-label">数量</span>
              <el-input-number v-model="b.qty" :min="1" class="num-narrow" />
              <el-input v-model="b.footprint" placeholder="封装" class="w-120" />
              <span class="row-label">单价¥</span>
              <el-input-number v-model="b.price" :min="0" :step="0.1" :precision="2" class="num-narrow" />
              <el-button size="small" text type="danger" @click="bomRows.splice(i, 1)">删除</el-button>
            </div>
            <p v-if="!bomRows.length" class="empty-hint">暂无 BOM 清单，可手动添加或导入 CSV</p>
          </div>

          <!-- 学习资源与课件附件 -->
          <div class="card-section">
            <div class="section-head">
              <h4 class="section-title">学习资料与附件</h4>
              <el-button size="small" plain @click="resourceRows.push({ type: '文档', name: '', url: '', uploading: false })">+ 添加附件</el-button>
            </div>
            <div v-for="(r, i) in resourceRows" :key="i" class="adv-row">
              <el-select v-model="r.type" class="res-type-sel">
                <el-option v-for="t in resourceTypes" :key="t" :label="t" :value="t" />
              </el-select>
              <el-input v-model="r.name" placeholder="资料名称，如：实验指导书.pdf" class="grow" />
              <el-upload :show-file-list="false" :http-request="(opt) => doUploadRes(opt, r)" accept="*">
                <el-button size="small" :type="r.url ? 'success' : 'primary'" plain :loading="r.uploading">
                  {{ r.url ? '已上传' : '上传附件' }}
                </el-button>
              </el-upload>
              <el-button size="small" text type="danger" @click="resourceRows.splice(i, 1)">删除</el-button>
            </div>
            <p v-if="!resourceRows.length" class="empty-hint">暂无学习资源附件，点击右上角添加</p>
          </div>
        </div>
      </el-tab-pane>
    </el-tabs>

    <template #footer>
      <el-button @click="$emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="saving" @click="save">保存项目</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { fetchEquipment, uploadDocFile } from '../api'
import ImageUploader from './ImageUploader.vue'
import RichEditor from './RichEditor.vue'
import ReviewRubricEditor from './ReviewRubricEditor.vue'
import { loadSiteConfig, siteConfig as site } from '../utils/siteConfig'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  /** 要编辑的项目对象; 为 null 表示新增 */
  project: { type: Object, default: null },
  /** admin | teacher */
  mode: { type: String, default: 'admin' },
  compact: { type: Boolean, default: false },
  /** 可指派的讲师 (admin 模式) */
  teachers: { type: Array, default: () => [] },
  /** 技能维度 [{name, enabled}] */
  skillDimensions: { type: Array, default: () => [] },
  saveFn: { type: Function, required: true },
  /** 读取/保存教师私有参考答案 */
  referenceAnswerFn: { type: Function, default: null },
  saveReferenceAnswerFn: { type: Function, default: null }
})
const emit = defineEmits(['update:modelValue', 'saved'])

const editTab = ref('basic')
const saving = ref(false)
const categoryOptions = computed(() => site.projectCategories || [])
const equipmentOptions = ref([])
const resourceTypes = ['文档', '视频', '代码', '手册', '工具', '课件', '原理图', 'LAYOUT', '3D图']

const emptyForm = {
  id: null, title: '', summary: '', description: '', difficulty: '入门', duration: '2周',
  teamSize: '1人', category: '', icon: '', coverUrl: '', mentorId: null, author: '', license: 'GPL-3.0',
  layers: 2, pcbSize: '', cost: 0, verified: false, status: 'PUBLISHED',
  tagsText: '', featuresText: '', goalsText: '', prereqText: '', equipNames: [], submissionRequirements: '', referenceAnswer: ''
}
const form = reactive({ ...emptyForm })
const assessRows = ref([])
const reviewRubric = ref([])
const expandedAssessments = ref([])
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

const isAssessExpanded = (index) => expandedAssessments.value.includes(index)
const toggleAssessExpand = (index) => {
  const pos = expandedAssessments.value.indexOf(index)
  if (pos >= 0) expandedAssessments.value.splice(pos, 1)
  else expandedAssessments.value.push(index)
}
const addAssessment = () => {
  assessRows.value.push({ name: '', weight: 0, desc: '', referenceAnswer: '', rubric: [] })
  expandedAssessments.value.push(assessRows.value.length - 1)
}
const removeAssessment = (index) => {
  assessRows.value.splice(index, 1)
  expandedAssessments.value = expandedAssessments.value
    .filter(i => i !== index)
    .map(i => (i > index ? i - 1 : i))
}

const reset = () => {
  const row = props.project
  Object.assign(form, emptyForm)
  editTab.value = 'basic'
  expandedAssessments.value = []
  assessRows.value = arr(row?.assessments).map((a) => ({
    name: a.name || '', weight: num(a.weight), desc: a.desc || '',
    referenceAnswer: a.referenceAnswer || '', rubric: arr(a.rubric).map(r => ({ ...r }))
  }))
  reviewRubric.value = arr(row?.reviewRubric).map(r => ({ ...r }))
  form.submissionRequirements = row?.submissionRequirements || ''
  form.referenceAnswer = ''
  skillRows.value = arr(row?.skillRequirements).map((s) => ({ name: s.name || '', required: num(s.required) }))
  syllabusRows.value = arr(row?.syllabus).map((s) => ({ phase: s.phase || '', title: s.title || '', content: s.content || '', hours: num(s.hours) }))
  bomRows.value = arr(row?.bom).map((b) => ({ ref: b.ref || '', name: b.name || '', qty: num(b.qty, 1), footprint: b.footprint || '', price: num(b.price) }))
  resourceRows.value = arr(row?.resources).map((r) => ({ type: r.type || '文档', name: r.name || '', url: r.url || '', uploading: false }))
  if (row) {
    Object.assign(form, {
      id: row.id, title: row.title, summary: row.summary, description: row.description,
      difficulty: row.difficulty, duration: row.duration, teamSize: row.teamSize,
      category: row.category, icon: row.icon || '', coverUrl: row.coverUrl || '', mentorId: row.mentorId ?? null, author: row.author,
      license: row.license, layers: row.layers ?? 0, pcbSize: row.pcbSize || '', cost: row.cost ?? 0,
      verified: !!row.verified, status: row.status,
      tagsText: joinArr(row.tags), featuresText: joinArr(row.features),
      goalsText: joinArr(row.learningGoals), prereqText: joinArr(row.prerequisites),
      equipNames: [...arr(row.equipmentNames)]
    })
  }
  if (row && props.referenceAnswerFn) {
    props.referenceAnswerFn(row.id).then((result) => {
      if (form.id === row.id) {
        const parsed = parseReferenceAnswers(result?.referenceAnswer || '')
        form.referenceAnswer = parsed.overall
        assessRows.value.forEach((a) => { a.referenceAnswer = parsed.items[a.name] || a.referenceAnswer || '' })
      }
    }).catch(() => {})
  }
}

const doUploadRes = async (opt, row) => {
  if (opt.file.size > 500 * 1024 * 1024) { ElMessage.warning('附件不能超过 500MB'); return }
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
  .map((a) => ({ name: a.name.trim(), weight: num(a.weight), desc: (a.desc || '').trim(), rubric: a.rubric || [] }))

const parseReferenceAnswers = (raw) => {
  if (!raw) return { overall: '', items: {} }
  try {
    const parsed = JSON.parse(raw)
    if (parsed && typeof parsed === 'object' && parsed.items && typeof parsed.items === 'object') {
      return { overall: String(parsed.overall || ''), items: parsed.items }
    }
  } catch (_) { /* 兼容旧版纯文本答案 */ }
  return { overall: raw, items: {} }
}
const buildReferenceAnswerPayload = () => {
  const items = {}
  assessRows.value.forEach((a) => {
    const name = (a.name || '').trim()
    const answer = (a.referenceAnswer || '').trim()
    if (name && answer) items[name] = answer
  })
  return Object.keys(items).length
    ? JSON.stringify({ version: 1, overall: (form.referenceAnswer || '').trim(), items })
    : (form.referenceAnswer || '').trim()
}

const save = async () => {
  if (!form.title.trim()) {
    ElMessage.warning('请填写项目标题')
    editTab.value = 'basic'
    return
  }
  const assessments = buildAssessments()
  if (assessments.length && assessments.reduce((s, a) => s + a.weight, 0) !== 100) {
    ElMessage.warning('成果考核项的权重合计必须等于 100')
    editTab.value = 'assessment'
    return
  }
  const rubricsToValidate = assessments.length ? assessments.map(a => a.rubric) : [reviewRubric.value]
  for (const rubric of rubricsToValidate) {
    if (rubric.length && (rubric.some(r => !r.name.trim()) || new Set(rubric.map(r => r.name.trim())).size !== rubric.length || rubric.reduce((sum, r) => sum + Number(r.points), 0) !== 100)) {
      ElMessage.warning('评分细则名称不能留空或重复，分值合计必须为100')
      editTab.value = 'assessment'
      return
    }
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
      reviewRubric: JSON.stringify(reviewRubric.value),
      submissionRequirements: form.submissionRequirements,
      skillRequirements: JSON.stringify(buildSkills()),
      syllabus: JSON.stringify(buildSyllabus()),
      bom: JSON.stringify(buildBom()),
      resources: JSON.stringify(buildResources())
    }
    if (props.mode === 'admin') {
      payload.mentor = mentorUser ? mentorUser.name : null
      payload.mentorId = form.mentorId || null
    }
    const saved = await props.saveFn(form.id, payload)
    const projectId = form.id || saved?.id
    if (projectId && props.saveReferenceAnswerFn) {
      await props.saveReferenceAnswerFn(projectId, buildReferenceAnswerPayload())
    }
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
/* 弹窗框架与滚动控制 */
:deep(.project-edit-dialog) { max-height: 94vh; margin-top: 3vh; margin-bottom: 3vh; border-radius: 14px; }
:deep(.project-edit-dialog .el-dialog__header) { padding: 16px 22px 12px; border-bottom: 1px solid var(--el-border-color-lighter); margin-right: 0; }
:deep(.project-edit-dialog .el-dialog__title) { font-size: 16px; font-weight: 600; color: #1e293b; }
:deep(.project-edit-dialog .el-dialog__body) { max-height: calc(94vh - 135px); overflow-y: auto; padding: 14px 22px; }
:deep(.project-edit-dialog .el-dialog__footer) { padding: 12px 22px 16px; border-top: 1px solid var(--el-border-color-lighter); }

.project-tabs :deep(.el-tabs__header) { margin-bottom: 16px; }
.project-tabs :deep(.el-tabs__item) { font-size: 14px; font-weight: 500; color: #64748b; }
.project-tabs :deep(.el-tabs__item.is-active) { color: var(--el-color-primary); font-weight: 600; }

.dialog-form { padding-top: 4px; }
.form-2col { display: grid; grid-template-columns: 1fr 1fr; column-gap: 20px; }
.section-notice { font-size: 13px; color: #475569; background: #f8fafc; border: 1px solid #e2e8f0; padding: 10px 14px; border-radius: 8px; margin-bottom: 14px; line-height: 1.6; }
.content-pane { display: flex; flex-direction: column; }

/* 卡片分区样式 */
.card-section { border: 1px solid #e2e8f0; border-radius: 10px; background: #fff; padding: 16px; margin-bottom: 16px; }
.section-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px; }
.section-head-left { display: flex; align-items: center; gap: 12px; }
.section-title { margin: 0; font-size: 14px; font-weight: 600; color: #1e293b; }
.section-subtext { font-size: 12px; color: #64748b; margin-bottom: 12px; line-height: 1.5; }
.field-tip { margin-top: 6px; font-size: 12px; color: #94a3b8; }

/* 考核项卡片 */
.weight-badge { display: inline-flex; align-items: center; font-size: 12px; padding: 2px 9px; border-radius: 6px; background: #fef3c7; color: #92400e; font-weight: 500; }
.weight-badge.ok { background: #dcfce7; color: #15803d; }
.assess-list { display: flex; flex-direction: column; gap: 10px; }
.assess-card { border: 1px solid #e2e8f0; border-radius: 8px; background: #ffffff; overflow: hidden; transition: border-color 0.2s, box-shadow 0.2s; }
.assess-card:hover { border-color: #cbd5e1; }
.assess-card-header { display: flex; align-items: center; gap: 10px; padding: 10px 14px; background: #f8fafc; flex-wrap: wrap; }
.assess-index { width: 22px; height: 22px; display: inline-flex; align-items: center; justify-content: center; background: #e2e8f0; color: #475569; font-size: 12px; font-weight: 600; border-radius: 5px; flex-shrink: 0; }
.assess-name { width: 170px; flex-shrink: 0; }
.assess-weight { display: flex; align-items: center; gap: 6px; flex-shrink: 0; }
.num-compact { width: 88px; }
.unit-text { font-size: 12px; color: #64748b; }
.assess-desc { flex: 1; min-width: 140px; }
.assess-tags { display: flex; align-items: center; gap: 6px; }
.status-tag { font-size: 11px; padding: 2px 7px; border-radius: 4px; background: #f1f5f9; color: #64748b; white-space: nowrap; }
.status-tag.active { background: #eff6ff; color: #2563eb; }
.assess-actions { display: flex; align-items: center; gap: 4px; margin-left: auto; }
.assess-card-body { padding: 14px 16px; border-top: 1px solid #e2e8f0; background: #fff; }
.assess-field-block { margin-bottom: 12px; }
.assess-field-block:last-child { margin-bottom: 0; }
.body-label { font-size: 13px; font-weight: 500; color: #334155; margin-bottom: 6px; }
.empty-hint-card { padding: 20px; text-align: center; color: #94a3b8; font-size: 13px; border: 1px dashed #cbd5e1; border-radius: 8px; }

/* 教学大纲与BOM通用 */
.adv-row { display: flex; gap: 8px; align-items: center; margin-bottom: 8px; }
.adv-row .grow { flex: 1; }
.adv-row .w-80 { width: 80px; flex-shrink: 0; }
.adv-row .w-120 { width: 120px; flex-shrink: 0; }
.adv-row .num-narrow { width: 110px; flex-shrink: 0; }
.adv-row .res-type-sel { width: 100px; flex-shrink: 0; }
.row-label { font-size: 12px; color: #64748b; flex-shrink: 0; }
.bom-btns { display: flex; gap: 8px; align-items: center; }
.syllabus-item { padding: 10px 12px; background: #f8fafc; border: 1px solid #e2e8f0; border-radius: 8px; margin-bottom: 10px; }
.empty-hint { font-size: 12px; color: #94a3b8; margin: 0; }
.option-muted { margin-left: 6px; font-size: 12px; color: #94a3b8; }

@media (max-width: 768px) {
  .form-2col { grid-template-columns: 1fr; }
  .assess-card-header { gap: 8px; }
  .assess-name { width: 100%; }
  .assess-desc { width: 100%; }
}
</style>
