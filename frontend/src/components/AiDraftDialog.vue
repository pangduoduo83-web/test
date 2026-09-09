<template>
  <!-- AI 起草项目:一句话题目 → 完整项目草稿 → 交给项目编辑器修改后保存 -->
  <el-dialog :model-value="modelValue" title="AI 起草项目" width="640px" @update:model-value="(v) => $emit('update:modelValue', v)">
    <template v-if="!draft">
      <p class="tip">告诉 AI 题目和学生水平,它会生成完整草稿:简介、学习目标、按周的教学大纲、考核项、技能要求、BOM 与所需设备。生成后在编辑器里改一改再保存,默认为草稿状态。</p>
      <el-form label-position="top">
        <el-form-item label="项目题目 / 方向" required>
          <el-input v-model="form.topic" maxlength="100" placeholder="如:基于 STM32 的智能温湿度监测节点(带 OLED 显示与 WiFi 上传)" />
        </el-form-item>
        <div class="two">
          <el-form-item label="目标学生水平" required>
            <el-select v-model="form.level" allow-create filterable default-first-option style="width:100%">
              <el-option v-for="l in levels" :key="l" :label="l" :value="l" />
            </el-select>
          </el-form-item>
          <el-form-item label="周期(周)" required>
            <el-input-number v-model="form.weeks" :min="1" :max="8" style="width:100%" />
          </el-form-item>
        </div>
        <el-form-item label="希望重点训练(选填)">
          <el-input v-model="form.focus" maxlength="100" placeholder="如:PCB 设计与焊接、传感器驱动、低功耗" />
        </el-form-item>
      </el-form>
      <div class="muted">会参考本站的项目分类({{ categories.length }} 个)、技能维度({{ dims.length }} 个)和设备库({{ equipment.length }} 件),让草稿贴合你们实验室的条件。</div>
    </template>

    <template v-else>
      <div class="preview">
        <div class="pv-title">{{ draft.title }} <span class="badge badge-blue">{{ draft.difficulty }}</span> <span class="badge badge-gray">{{ draft.duration }}</span></div>
        <div class="pv-summary">{{ draft.summary }}</div>
        <div class="pv-grid">
          <div><b>教学大纲</b><ol><li v-for="(s, i) in draft.syllabus" :key="i">{{ s.phase }} {{ s.title }}<small v-if="s.hours"> · {{ s.hours }} 学时</small></li></ol></div>
          <div>
            <b>考核项</b><ul><li v-for="(a, i) in draft.assessments" :key="i">{{ a.name }} · {{ a.weight }}%</li></ul>
            <b>技能要求</b><ul><li v-for="(s, i) in draft.skillRequirements" :key="i">{{ s.name }} ≥ {{ s.required }}</li></ul>
          </div>
        </div>
        <div class="pv-row"><b>BOM</b> {{ (draft.bom || []).length }} 项,预估 ¥{{ draft.cost || '-' }} · <b>设备</b> {{ (draft.equipmentNames || []).join('、') || '无' }}</div>
        <div class="pv-row"><b>标签</b> {{ (draft.tags || []).join(' / ') }}</div>
      </div>
      <p class="muted">这只是草稿。点「在编辑器中打开」后可以逐项修改,满意再保存;默认保存为草稿,不会立刻对学生可见。</p>
    </template>

    <template #footer>
      <el-button @click="$emit('update:modelValue', false)">取消</el-button>
      <template v-if="!draft">
        <el-button type="primary" :loading="loading" @click="generate">{{ loading ? '生成中,约 20~40 秒…' : '生成草稿' }}</el-button>
      </template>
      <template v-else>
        <el-button :loading="loading" @click="generate">重新生成</el-button>
        <el-button type="primary" @click="open">在编辑器中打开</el-button>
      </template>
    </template>
  </el-dialog>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { fetchEquipment } from '../api'
import { runJsonSkill } from '../api/aiJson'
import { loadSiteConfig, siteConfig as site } from '../utils/siteConfig'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  /** 技能维度 [{name}] */
  skillDimensions: { type: Array, default: () => [] }
})
const emit = defineEmits(['update:modelValue', 'drafted'])

const levels = ['大一,学过 C 语言基础,没接触过硬件', '大二,学过 C 语言和数字电路,没做过 PCB', '大三,做过单片机课程设计,会画简单 PCB', '研究生 / 竞赛队员,有完整项目经验']
const form = reactive({ topic: '', level: levels[1], weeks: 4, focus: '' })
const loading = ref(false)
const draft = ref(null)
const equipment = ref([])
const categories = ref([])
const dims = ref([])

const generate = async () => {
  if (!form.topic.trim()) { ElMessage.warning('请填写项目题目'); return }
  loading.value = true
  try {
    draft.value = await runJsonSkill('project-drafter', {
      topic: form.topic.trim(), level: form.level, weeks: form.weeks, focus: form.focus || '无特别要求',
      categories: categories.value.join(','), skillDimensions: dims.value.join(','), equipment: equipment.value.join(',')
    })
  } catch (e) {
    ElMessage.error(e?.message || '生成失败,请重试')
  } finally {
    loading.value = false
  }
}

/** 草稿 → 项目编辑器能直接吃的对象(与后端 Project 字段一致,数组保持数组) */
const open = () => {
  const d = draft.value
  const paragraphs = String(d.description || '').split(/\n{2,}/).map((p) => p.trim()).filter(Boolean)
  emit('drafted', {
    id: null,
    title: d.title, summary: d.summary,
    description: paragraphs.map((p) => `<p>${p.replace(/</g, '&lt;')}</p>`).join(''),
    difficulty: ['入门', '进阶', '挑战'].includes(d.difficulty) ? d.difficulty : '进阶',
    duration: d.duration || `${form.weeks}周`, teamSize: d.teamSize || '1人', category: d.category || categories.value[0] || '',
    icon: '🔌', coverUrl: '', author: '', license: 'GPL-3.0', layers: 2, pcbSize: '', cost: Number(d.cost) || 0, verified: false, status: 'DRAFT',
    tags: d.tags || [], features: d.features || [], learningGoals: d.learningGoals || [], prerequisites: d.prerequisites || [],
    equipmentNames: d.equipmentNames || [], assessments: d.assessments || [], skillRequirements: d.skillRequirements || [],
    syllabus: d.syllabus || [], bom: d.bom || [], resources: []
  })
  emit('update:modelValue', false)
  draft.value = null
}

onMounted(async () => {
  await loadSiteConfig()
  categories.value = site.projectCategories || []
  dims.value = props.skillDimensions.map((d) => d.name)
  fetchEquipment({}).then((list) => { equipment.value = list.map((e) => e.name) }).catch(() => {})
})
</script>

<style scoped>
.tip { font-size: 13px; color: var(--text-secondary); line-height: 1.7; margin: 0 0 14px; }
.two { display: grid; grid-template-columns: 1fr 160px; gap: 14px; }
.muted { font-size: 12px; color: #9ca3af; line-height: 1.6; margin-top: 8px; }
.preview { background: #f9fafb; border: 1px solid var(--border); border-radius: 12px; padding: 14px 16px; font-size: 13px; }
.pv-title { font-size: 16px; font-weight: 700; display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.pv-summary { color: #374151; margin: 8px 0 12px; line-height: 1.6; }
.pv-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 14px; }
.pv-grid ol, .pv-grid ul { margin: 4px 0 8px; padding-left: 18px; line-height: 1.7; color: #4b5563; }
.pv-row { margin-top: 8px; color: #4b5563; }
</style>
